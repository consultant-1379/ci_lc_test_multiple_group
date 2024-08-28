package com.ericsson.oss.services.pm.initiation.task.factories.deactivation

import spock.lang.Unroll

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.EbmSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache

class EbmSubscriptionDeactivationTaskRequestFactorySpec extends SkeletonSpec {

    @ObjectUnderTest
    EbmSubscriptionDeactivationTaskRequestFactory objectUnderTest

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
    def "Will create tasks for all nodes if each node has scanner with subscription ID"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.SGSNMME).neConfigurationManagerState(neConfigurationManagerState).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.SGSNMME).neConfigurationManagerState(neConfigurationManagerState).build()
        def subMo = ebmSubscriptionBuilder.name("Test").addEvent("a", "b").nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.DEACTIVATING).build()
        def scannerMO1 = scannerUtil.builder("PREDEF.EBMLOG.EBM", "1").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()
        def scannerMO2 = scannerUtil.builder("PREDEF.EBMLOG.EBM", "2").node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as EbmSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)
        then:
        tasks.size() == taskNumber
        if (taskNumber > 0) {
            tasks.each {
                assert it.getJobId().contains("subscriptionId=" + sub.getId())
                assert (it.getNodeAddress().contains(nodeMO1.getFdn()) || it.getNodeAddress().contains(nodeMO2.getFdn()))
            }
        }
        if (trackerCalls > 0) {
            1 * pmicInitiationTrackerCache.startTrackingDeactivation(
                    String.valueOf(sub.getId()), sub.getAdministrationState().name(), [(nodeMO1.getFdn()): "SGSN-MME", (nodeMO2.getFdn()): "SGSN-MME"])
        } else {
            0 * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _)
        }
        then: "scanners will not be cleaned"
        scannerMO1.getAttribute("status") == "ACTIVE"
        scannerMO1.getAttribute("subscriptionId") == subMo.getPoId() as String
        scannerMO2.getAttribute("status") == "ACTIVE"
        scannerMO2.getAttribute("subscriptionId") == subMo.getPoId() as String
        where:
        neConfigurationManagerState          || taskNumber | trackerCalls
        NeConfigurationManagerState.ENABLED  || 2          | 1
        NeConfigurationManagerState.DISABLED || 0          | 0
    }

    def "Will create tasks for all nodes if each node has scanner with subscription ID but will not track"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.SGSNMME).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.SGSNMME).build()
        def subMo = ebmSubscriptionBuilder.name("Test").addEvent("a", "b").nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.DEACTIVATING).build()
        def scannerMO1 = scannerUtil.builder("PREDEF.EBMLOG.EBM", "1").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()
        def scannerMO2 = scannerUtil.builder("PREDEF.EBMLOG.EBM", "2").node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as EbmSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, false)
        then:
        tasks.size() == 2
        tasks.each {
            assert it.getJobId().contains("subscriptionId=" + sub.getId())
            assert (it.getNodeAddress().contains(nodeMO1.getFdn()) || it.getNodeAddress().contains(nodeMO2.getFdn()))
        }
        0 * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _)
        then: "scanners will not be cleaned"
        scannerMO1.getAttribute("status") == "ACTIVE"
        scannerMO1.getAttribute("subscriptionId") == subMo.getPoId() as String
        scannerMO2.getAttribute("status") == "ACTIVE"
        scannerMO2.getAttribute("subscriptionId") == subMo.getPoId() as String
    }

    def "Will create tasks only for nodes that have scanners with subscription id"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.SGSNMME).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.SGSNMME).build()
        def subMo = ebmSubscriptionBuilder.name("Test").addEvent("a", "b").nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.DEACTIVATING).build()
        def scannerMO1 = scannerUtil.builder("PREDEF.EBMLOG.EBM", "1").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()
        def scannerMO2 = scannerUtil.builder("PREDEF.EBMLOG.EBM", "2").node(nodeMO2).status(ScannerStatus.ACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as EbmSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, false)
        then:
        tasks.size() == 1
        tasks.each {
            assert it.getJobId().contains("subscriptionId=" + sub.getId())
            assert it.getNodeAddress().contains(nodeMO1.getFdn())
        }
        then: "scanners will not be cleaned"
        scannerMO1.getAttribute("status") == "ACTIVE"
        scannerMO1.getAttribute("subscriptionId") == subMo.getPoId() as String
        scannerMO2.getAttribute("status") == "ACTIVE"
        scannerMO2.getAttribute("subscriptionId") == null
    }

    def "Will create tasks only for nodes that have scanners"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.SGSNMME).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.SGSNMME).build()
        def subMo = ebmSubscriptionBuilder.name("Test").addEvent("a", "b").nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.DEACTIVATING).build()
        scannerUtil.builder("PREDEF.EBMLOG.EBM", "1").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as EbmSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, false)
        then:
        tasks.size() == 1
        tasks.each {
            assert it.getJobId().contains("subscriptionId=" + sub.getId())
            assert it.getNodeAddress().contains(nodeMO1.getFdn())
        }
    }

    @Unroll
    def "Will not track if subscription's admin state is not DEACTIVATING or UPDATING, but will create tasks."() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.SGSNMME).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.SGSNMME).build()
        def subMo = ebmSubscriptionBuilder.name("Test").addEvent("a", "b").nodes(nodeMO1, nodeMO2).administrativeState(adminstate).build()
        scannerUtil.builder("PREDEF.EBMLOG.EBM", "1").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()
        scannerUtil.builder("PREDEF.EBMLOG.EBM", "2").node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as EbmSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)
        then:
        tasks.size() == 2
        track * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _)
        where:
        adminstate << [AdministrationState.DEACTIVATING, AdministrationState.UPDATING, AdministrationState.ACTIVE, AdministrationState.ACTIVATING, AdministrationState.INACTIVE, AdministrationState.SCHEDULED]
        track << [1, 1, 0, 0, 0, 0]
    }

    def "Will not create tasks and clean scanners if the scanner is not active but associated to subscription"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.SGSNMME).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.SGSNMME).build()
        def subMo = ebmSubscriptionBuilder.name("Test").addEvent("a", "b").nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.DEACTIVATING).build()
        def scannerMO1 = scannerUtil.builder("PREDEF.EBMLOG.EBM", "1").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()
        def scannerMO2 = scannerUtil.builder("PREDEF.EBMLOG.EBM", "2").node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.ERROR).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as EbmSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)
        then:
        tasks.size() == 1
        tasks.each {
            assert it.getJobId().contains("subscriptionId=" + sub.getId())
            assert it.getNodeAddress().contains(nodeMO1.getFdn())
        }
        1 * pmicInitiationTrackerCache.startTrackingDeactivation(
                String.valueOf(sub.getId()), sub.getAdministrationState().name(), [(nodeMO1.getFdn()): "SGSN-MME"])
        then: "scanners will not be cleaned"
        scannerMO1.getAttribute("status") == "ACTIVE"
        scannerMO1.getAttribute("subscriptionId") == subMo.getPoId() as String
        scannerMO2.getAttribute("status") == "UNKNOWN"
        scannerMO2.getAttribute("subscriptionId") == "0"
    }
}
