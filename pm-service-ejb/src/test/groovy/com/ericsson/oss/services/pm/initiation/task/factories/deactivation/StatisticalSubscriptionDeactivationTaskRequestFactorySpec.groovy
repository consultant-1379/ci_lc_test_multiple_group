package com.ericsson.oss.services.pm.initiation.task.factories.deactivation

import spock.lang.Unroll

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.tasks.ScannerSuspensionTaskRequest
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionDeactivationTaskRequest

class StatisticalSubscriptionDeactivationTaskRequestFactorySpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    StatisticalSubscriptionDeactivationTaskRequestFactory objectUnderTest

    @ImplementationInstance
    PMICInitiationTrackerCache pmicInitiationTrackerCache = Mock(PMICInitiationTrackerCache)

    @Inject
    NodeDao nodeDao

    @Inject
    SubscriptionDao subscriptionDao

    @Override
    def autoAllocateFrom() {
        def packages = super.autoAllocateFrom()
        packages.addAll(['com.ericsson.oss.pmic.dao', 'com.ericsson.oss.pmic.dto'])
        return packages
    }

    @Unroll
    def "Will create tasks for all nodes if each node has USERDEF scanner with subscription name"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.ERBS).neConfigurationManagerState(neConfigurationManagerState).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.ERBS).neConfigurationManagerState(neConfigurationManagerState).build()
        def subMo = statisticalSubscriptionBuilder.name("Test").addCounter("a", "b").nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.DEACTIVATING).build()
        scannerUtil.builder("USERDEF-Test.Cont.Y.STATS", "1").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()
        scannerUtil.builder("USERDEF-Test.Cont.Y.STATS", "2").node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as StatisticalSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)
        then:
        tasks.size() == taskNumber
        if (taskNumber > 0) {
            tasks.each {
                assert it.getJobId().contains("subscriptionId=" + sub.getId())
                assert (it.getNodeAddress().contains(nodeMO1.getFdn()) || it.getNodeAddress().contains(nodeMO2.getFdn()))
                assert (it instanceof SubscriptionDeactivationTaskRequest)
            }
        }
        if (trackerCalls != 0) {
            1 * pmicInitiationTrackerCache.startTrackingDeactivation(
                    String.valueOf(sub.getId()), sub.getAdministrationState().name(), [(nodeMO1.getFdn()): "ERBS", (nodeMO2.getFdn()): "ERBS"],
                    [(nodeMO1.getFdn()): 1, (nodeMO2.getFdn()): 1])
        } else {
            0 * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _, _)
        }
        where:
        neConfigurationManagerState          || taskNumber | trackerCalls
        NeConfigurationManagerState.ENABLED  || 2          | 1
        NeConfigurationManagerState.DISABLED || 0          | 0
    }

    def "Will not create any tasks if USERDEF scanners do not exist for a USERDEF stat subscription"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.ERBS).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.ERBS).build()
        def subMo = statisticalSubscriptionBuilder.name("Test").addCounter("a", "b").nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.DEACTIVATING).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as StatisticalSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)
        then:
        tasks.size() == 0
        0 * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _, _)
    }

    def "Will create tasks for all nodes if each node has USERDEF scanner with subscription name but will not track"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.ERBS).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.ERBS).build()
        def subMo = statisticalSubscriptionBuilder.name("Test").addCounter("a", "b").nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.DEACTIVATING).build()
        scannerUtil.builder("USERDEF-Test.Cont.Y.STATS", "1").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()
        scannerUtil.builder("USERDEF-Test.Cont.Y.STATS", "2").node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as StatisticalSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, false)
        then:
        tasks.size() == 2
        tasks.each {
            assert it.getJobId().contains("subscriptionId=" + sub.getId())
            assert (it.getNodeAddress().contains(nodeMO1.getFdn()) || it.getNodeAddress().contains(nodeMO2.getFdn()))
            assert (it instanceof SubscriptionDeactivationTaskRequest)
        }
        0 * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _, _)
    }

    def "Will create tasks for all nodes if each node has USERDEF scanner with subscription name regardless of state of scanner"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.ERBS).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.ERBS).build()
        def subMo = statisticalSubscriptionBuilder.name("Test").addCounter("a", "b").nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.DEACTIVATING).build()
        scannerUtil.builder("USERDEF-Test.Cont.Y.STATS", "1").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.ERROR).build()
        scannerUtil.builder("USERDEF-Test.Cont.Y.STATS", "2").node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.INACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as StatisticalSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)
        then:
        tasks.size() == 2
        tasks.each {
            assert it.getJobId().contains("subscriptionId=" + sub.getId())
            assert (it.getNodeAddress().contains(nodeMO1.getFdn()) || it.getNodeAddress().contains(nodeMO2.getFdn()))
            assert (it instanceof SubscriptionDeactivationTaskRequest)
        }
        1 * pmicInitiationTrackerCache.startTrackingDeactivation(
                String.valueOf(sub.getId()), sub.getAdministrationState().name(), [(nodeMO1.getFdn()): "ERBS", (nodeMO2.getFdn()): "ERBS"],
                [(nodeMO1.getFdn()): 1, (nodeMO2.getFdn()): 1])
    }

    def "Will create tasks for all nodes if each node has USERDEF scanner with subscription name regardless of state of scanner even if subscription id is not associated"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.ERBS).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.ERBS).build()
        def subMo = statisticalSubscriptionBuilder.name("Test").addCounter("a", "b").nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.ACTIVATING).build()
        scannerUtil.builder("USERDEF-Test.Cont.Y.STATS", "1").node(nodeMO1).status(ScannerStatus.ERROR).build()
        scannerUtil.builder("USERDEF-Test.Cont.Y.STATS", "2").node(nodeMO2).status(ScannerStatus.INACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as StatisticalSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)
        then:
        tasks.size() == 2
        tasks.each {
            assert it.getJobId().contains("subscriptionId=" + sub.getId())
            assert (it.getNodeAddress().contains(nodeMO1.getFdn()) || it.getNodeAddress().contains(nodeMO2.getFdn()))
            assert (it instanceof SubscriptionDeactivationTaskRequest)
        }
        and: "will not track because subscription is not DEACTIVATING or UPDATING"
        0 * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _, _)
    }

    def "Will create suspension tasks for all nodes if each node has Active PREDEF scanner"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.ERBS).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.ERBS).build()
        def subMo = statisticalSubscriptionBuilder.name("ERBS System Defined Statistical Subscription").addCounter("a", "b")
                .nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.DEACTIVATING).userType(UserType.SYSTEM_DEF).build()
        scannerUtil.builder("PREDEF.STATS", "1").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()
        scannerUtil.builder("PREDEF.STATS", "2").node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as StatisticalSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)
        then:
        tasks.size() == 2
        tasks.get(0).getJobId().contains("ScannerSuspensionTaskRequest|nodeFdn=" + nodeMO1.getFdn())
        tasks.get(1).getJobId().contains("ScannerSuspensionTaskRequest|nodeFdn=" + nodeMO2.getFdn())
        tasks.each { assert it instanceof ScannerSuspensionTaskRequest }
        1 * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _, _)
    }

    def "Will not create suspension tasks for PREDEF scanners that are already INACTIVE"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.ERBS).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.ERBS).build()
        def subMo = statisticalSubscriptionBuilder.name("ERBS System Defined Statistical Subscription").addCounter("a", "b")
                .nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.DEACTIVATING).userType(UserType.SYSTEM_DEF).build()
        scannerUtil.builder("PREDEF.STATS", "1").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()
        scannerUtil.builder("PREDEF.STATS", "2").node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.INACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as StatisticalSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)
        then:
        tasks.size() == 1
        tasks.get(0).getJobId().contains("ScannerSuspensionTaskRequest|nodeFdn=" + nodeMO1.getFdn())
        tasks.each { assert it instanceof ScannerSuspensionTaskRequest }
        1 * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _, _)
    }

    def "On ERBS should not create mtr but only clean subscriptionId if the scanner is not active but still associated to a subscription"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.ERBS).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.ERBS).build()
        def subMo = statisticalSubscriptionBuilder.name("ERBS System Defined Statistical Subscription").addCounter("a", "b")
                .nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.DEACTIVATING).userType(UserType.SYSTEM_DEF).build()
        def scannerMO1 = scannerUtil.builder("PREDEF.STATS", "1").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()
        def scannerMO2 = scannerUtil.builder("PREDEF.STATS", "2").node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.ERROR).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as StatisticalSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)
        then:
        tasks.size() == 1
        tasks.get(0).getJobId().contains("ScannerSuspensionTaskRequest|nodeFdn=" + nodeMO1.getFdn())
        tasks.each { assert it instanceof ScannerSuspensionTaskRequest }
        1 * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _, _)
        scannerMO1.getAttribute("status") == ScannerStatus.ACTIVE.name()
        scannerMO1.getAttribute("subscriptionId") == subMo.getPoId() as String
        scannerMO2.getAttribute("status") == ScannerStatus.UNKNOWN.name()
        scannerMO2.getAttribute("subscriptionId") == "0"
    }

    def "On RadioNode should not create mtr but only clean subscriptionId if the scanner is not active but still associated to a subscription"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.RADIONODE).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.RADIONODE).build()
        def subMo = statisticalSubscriptionBuilder.name("RadioNode System Defined Statistical Subscription").addCounter("a", "b")
                .nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.DEACTIVATING).userType(UserType.SYSTEM_DEF).build()
        def scannerMO1 = scannerUtil.builder("PREDEF.Wrat.STATS", "1").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()
        def scannerMO2 = scannerUtil.builder("PREDEF.Wrat.STATS", "2").node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.ERROR).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as StatisticalSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)
        then:
        tasks.size() == 1
        tasks.get(0).getJobId().contains("ScannerSuspensionTaskRequest|nodeFdn=" + nodeMO1.getFdn())
        tasks.each { assert it instanceof ScannerSuspensionTaskRequest }
        1 * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _, _)
        scannerMO1.getAttribute("status") == ScannerStatus.ACTIVE.name()
        scannerMO1.getAttribute("subscriptionId") == subMo.getPoId() as String
        scannerMO2.getAttribute("status") == ScannerStatus.UNKNOWN.name()
        scannerMO2.getAttribute("subscriptionId") == "0"
    }
}
