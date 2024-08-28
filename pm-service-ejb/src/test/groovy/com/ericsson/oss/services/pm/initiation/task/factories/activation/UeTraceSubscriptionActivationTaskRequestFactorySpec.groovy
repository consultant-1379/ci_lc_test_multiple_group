package com.ericsson.oss.services.pm.initiation.task.factories.activation

import spock.lang.Unroll

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus
import com.ericsson.oss.pmic.dto.subscription.UETraceSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache

class UeTraceSubscriptionActivationTaskRequestFactorySpec extends SkeletonSpec {

    @ObjectUnderTest
    UeTraceSubscriptionActivationTaskRequestFactory objectUnderTest

    @Inject
    private PMICInitiationTrackerCache pmicInitiationTrackerCache

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
    def "will create activation tasks and track requests for all nodes that have #pmJobStatus pmjob"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").neType(NetworkElementType.SGSNMME).neConfigurationManagerState(neConfigurationManagerState).build()
        def nodeMO2 = nodeUtil.builder("2").neType(NetworkElementType.SGSNMME).neConfigurationManagerState(neConfigurationManagerState).build()
        def subMo = ueTraceSubscriptionBuilder.name("Test").administrativeState(AdministrationState.ACTIVATING).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as UETraceSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)
        then:
        tasks.size() == taskNumber
        if (taskNumber > 0) {
            tasks.get(0).getJobId().contains("SubscriptionActivationTaskRequest|nodeFdn=" + nodeMO1.getFdn() + "|subscriptionId=" + sub.getIdAsString() + "|subscriptionType=UETRACE")
            tasks.get(1).getJobId().contains("SubscriptionActivationTaskRequest|nodeFdn=" + nodeMO2.getFdn() + "|subscriptionId=" + sub.getIdAsString() + "|subscriptionType=UETRACE")
        }
        if (trackerCalls != 0) {
            pmicInitiationTrackerCache.getTracker(sub.getIdAsString()).getNodesFdnsToBeActivated() == [nodeMO1.getFdn(), nodeMO2.getFdn()] as Set
        } else {
            pmicInitiationTrackerCache.getTracker(sub.getIdAsString()) == null
        }
        where:
        neConfigurationManagerState          | taskNumber | trackerCalls | pmJobStatus
        NeConfigurationManagerState.ENABLED  | 2          | 1            | PmJobStatus.UNKNOWN
        NeConfigurationManagerState.ENABLED  | 2          | 1            | PmJobStatus.INACTIVE
        NeConfigurationManagerState.DISABLED | 0          | 0            | PmJobStatus.UNKNOWN
    }

    def "will not create any activation tasks if there are no nodes"() {
        given:
        def subMo = ueTraceSubscriptionBuilder.name("Test").administrativeState(AdministrationState.ACTIVATING).build()

        def sub = subscriptionDao.findOneById(subMo.getPoId()) as UETraceSubscription
        when:
        def tasks = objectUnderTest.createMediationTaskRequests([], sub, true)
        then:
        tasks.size() == 0
        pmicInitiationTrackerCache.getTracker(sub.getIdAsString()) == null
    }
}
