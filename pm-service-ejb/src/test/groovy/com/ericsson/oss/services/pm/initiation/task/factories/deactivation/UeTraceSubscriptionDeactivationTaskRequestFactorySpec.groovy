package com.ericsson.oss.services.pm.initiation.task.factories.deactivation

import spock.lang.Unroll

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.subscription.UETraceSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache

class UeTraceSubscriptionDeactivationTaskRequestFactorySpec extends SkeletonSpec {

    @ObjectUnderTest
    UeTraceSubscriptionDeactivationTaskRequestFactory objectUnderTest

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
    def "will create deactivation tasks and track requests for all nodes that have active scanner"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.SGSNMME).neConfigurationManagerState(neConfigurationManagerState).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.SGSNMME).neConfigurationManagerState(neConfigurationManagerState).build()
        def subMo = ueTraceSubscriptionBuilder.name("Test").administrativeState(AdministrationState.DEACTIVATING).build()
        pmJobBuilder.nodeName(nodeMO1).processType(ProcessType.UETRACE).subscriptionId(subMo).status(PmJobStatus.ACTIVE).build()
        pmJobBuilder.nodeName(nodeMO2).processType(ProcessType.UETRACE).subscriptionId(subMo).status(PmJobStatus.ACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as UETraceSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)
        then:
        tasks.size() == taskNumber
        if (taskNumber > 0) {
            tasks.get(0).getJobId().contains("SubscriptionDeactivationTaskRequest|nodeFdn=" + nodeMO1.getFdn() + "|subscriptionId=" + sub.getIdAsString() + "|subscriptionType=UETRACE")
            tasks.get(1).getJobId().contains("SubscriptionDeactivationTaskRequest|nodeFdn=" + nodeMO2.getFdn() + "|subscriptionId=" + sub.getIdAsString() + "|subscriptionType=UETRACE")
        }
        if (trackerCalls != 0) {
            1 * pmicInitiationTrackerCache.startTrackingDeactivation(
                    String.valueOf(sub.getId()), sub.getAdministrationState().name(), [(nodeMO1.getFdn()): "SGSN-MME", (nodeMO2.getFdn()): "SGSN-MME"])
        } else {
            0 * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _)
        }
        where:
        neConfigurationManagerState          || taskNumber | trackerCalls
        NeConfigurationManagerState.ENABLED  || 2          | 1
        NeConfigurationManagerState.DISABLED || 0          | 0
    }

    def "will not create any deactivation tasks if nodes do not exist"() {
        given:
        def subMo = ueTraceSubscriptionBuilder.name("Test").administrativeState(AdministrationState.DEACTIVATING).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as UETraceSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)
        then:
        tasks.size() == 0
        0 * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _)
    }
}
