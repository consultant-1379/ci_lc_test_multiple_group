/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.task.factories.activation

import spock.lang.Unroll

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.rule.SpyImplementation
import com.ericsson.oss.mediation.pm.router.policy.tasks.PmConfigTaskRequest
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.api.cache.PmFunctionData
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.eventSender.PmEventSender
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.tasks.ScannerResumptionTaskRequest
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionActivationTaskRequest

class StatsSubscriptionActivationTaskRequestFactorySpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    StatisticalSubscriptionActivationTaskRequestFactory statsSubscriptionActivationTaskRequestFactory

    @Inject
    PMICInitiationTrackerCache pmicInitiationTrackerCache

    @Inject
    NodeDao nodeDao

    @Inject
    SubscriptionDao subscriptionDao

    @Inject
    PmFunctionEnabledWrapper pmFunctionCache

    @SpyImplementation
    PmEventSender pmEventSender

    static final boolean trackResponse = true

    @Override
    def autoAllocateFrom() {
        def packages = super.autoAllocateFrom()
        packages.addAll(['com.ericsson.oss.pmic.dao', 'com.ericsson.oss.pmic.dto'])
        return packages
    }

    def 'createActivationTasks should return size 0 tasks when no nodes and should not add to cache'() {
        given:
        def subMo = statisticalSubscriptionBuilder.name('Test').addCounter('a', 'b').administrativeState(AdministrationState.ACTIVATING).build()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as StatisticalSubscription
        when:
        List<MediationTaskRequest> tasks = statsSubscriptionActivationTaskRequestFactory.createMediationTaskRequests([], sub, trackResponse)
        then:
        tasks == []
        pmicInitiationTrackerCache.getAllTrackers() == []
    }

    @Unroll
    def 'createActivationTasks should return size 1 tasks when there are nodes and counters and add to tracker cache for WMG Node'() {
        given:
        def nodeMO1 = nodeUtil.builder('WMG01').neType(NetworkElementType.ERBS).neConfigurationManagerState(neConfigurationManagerState).build()
        def nodeMO2 = nodeUtil.builder('WMG02').neType(NetworkElementType.ERBS).neConfigurationManagerState(neConfigurationManagerState).build()
        def subMo = statisticalSubscriptionBuilder.name('WMGvWMGStatisticalSubscription').addCounter('a', 'b').nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.ACTIVATING).build()
        scannerUtil.builder('PREDEF_STATS_SCANNER', 'WMG01').node(nodeMO1).status(ScannerStatus.INACTIVE).build()
        scannerUtil.builder('PREDEF_STATS_SCANNER', 'WMG02').node(nodeMO2).status(ScannerStatus.INACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as StatisticalSubscription
        when:
        List<MediationTaskRequest> tasks = statsSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, trackResponse)
        then:
        tasks.size() == taskNumber
        if (taskNumber > 0) {
            tasks.each { assert it instanceof SubscriptionActivationTaskRequest }
        }
        if (trackerCalls != 0) {
            pmicInitiationTrackerCache.getTracker(sub.getIdAsString()).getNodesFdnsToBeActivated() == [nodeMO1.getFdn(), nodeMO2.getFdn()] as Set
        } else {
            pmicInitiationTrackerCache.getTracker(sub.getIdAsString()) == null
        }
        where:
        neConfigurationManagerState          || taskNumber | trackerCalls
        NeConfigurationManagerState.ENABLED  || 2          | 1
        NeConfigurationManagerState.DISABLED || 0          | 0
    }

    def 'will create tasks and track USERDEF subscription'() {
        given:
        def nodeMO1 = nodeUtil.builder('1').neType(NetworkElementType.ERBS).build()
        def nodeMO2 = nodeUtil.builder('2').neType(NetworkElementType.ERBS).build()
        def subMo = statisticalSubscriptionBuilder.name('Test').addCounter('a', 'b').nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.ACTIVATING).build()
        scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', '1').node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.INACTIVE).build()
        scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', '2').node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.INACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as StatisticalSubscription
        when:
        List<MediationTaskRequest> tasks = statsSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, trackResponse)
        then:
        tasks.size() == 2
        pmicInitiationTrackerCache.getTracker(sub.getIdAsString()).getNodesFdnsToBeActivated() == [nodeMO1.getFdn(), nodeMO2.getFdn()] as Set
        tasks.each { assert it instanceof SubscriptionActivationTaskRequest }
    }

    def 'createMediationTaskRequests for an ERBS node should create resume tasks for the given sys def subscription and add to cache'() {
        given:
        def nodeMO1 = nodeUtil.builder('LTE01').neType(NetworkElementType.ERBS).ossModelIdentity('5783-904-386').build()
        def nodeMO2 = nodeUtil.builder('LTE02').neType(NetworkElementType.ERBS).ossModelIdentity('5783-904-386').build()
        def subMo = statisticalSubscriptionBuilder.name('ERBS System Defined Statistical Subscription').addCounter('a', 'b').nodes(nodeMO1, nodeMO2)
                .administrativeState(AdministrationState.ACTIVATING)
                .userType(UserType.SYSTEM_DEF).build()
        scannerUtil.builder('PREDEF.STATS', 'LTE01').node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.INACTIVE).build()
        scannerUtil.builder('PREDEF.STATS', 'LTE02').node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.INACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as StatisticalSubscription
        when:
        List<MediationTaskRequest> tasks = statsSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, trackResponse)
        then:
        tasks.size() == 2
        pmicInitiationTrackerCache.getTracker(sub.getIdAsString()).getNodesFdnsToBeActivated() == [nodeMO1.getFdn(), nodeMO2.getFdn()] as Set
        tasks.each { assert it instanceof ScannerResumptionTaskRequest }
    }

    def 'createMediationTaskRequests for an ERBS node should not create a resume task for the given sys def subscription and not add to cache if scanners are already active'() {
        given:
        def nodeMO1 = nodeUtil.builder('LTE01').neType(NetworkElementType.ERBS).ossModelIdentity('5783-904-386').build()
        def nodeMO2 = nodeUtil.builder('LTE02').neType(NetworkElementType.ERBS).ossModelIdentity('5783-904-386').build()
        def subMo = statisticalSubscriptionBuilder.name('ERBS System Defined Statistical Subscription').addCounter('a', 'b').nodes(nodeMO1, nodeMO2)
                .administrativeState(AdministrationState.ACTIVATING)
                .userType(UserType.SYSTEM_DEF).build()
        scannerUtil.builder('PREDEF.STATS', 'LTE01').node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()
        scannerUtil.builder('PREDEF.STATS', 'LTE02').node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as StatisticalSubscription
        when:
        List<MediationTaskRequest> tasks = statsSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, trackResponse)
        then:
        tasks.size() == 0
        pmicInitiationTrackerCache.getAllTrackers().size() == 0
    }

    def 'Creating Mediation Task Requests for a node with Mediation Autonomy and active scanners'() {
        given: 'a Mediation Autonomy enabled node'
        def node = dps.node().name('Node1').neType('5GRadioNode').build()
        and: 'an active subscription and scanner'
        def subscriptionMo = dps.subscription().type(SubscriptionType.STATISTICAL).name('5GRadioNode System Defined Statistical Subscription').nodes(node)
                .administrationState(AdministrationState.ACTIVATING)
                .userType(UserType.SYSTEM_DEF).build()
        dps.scanner().nodeName(node).name('PREDEF.STATS').processType(ProcessType.STATS).status(ScannerStatus.ACTIVE).subscriptionId(subscriptionMo).build()
        def nodes = nodeDao.findAll()
        def subscription = subscriptionDao.findOneById(subscriptionMo.getPoId()) as StatisticalSubscription
        and: 'PmFunction state added to the cache'
        PmFunctionData pmFunctionData = nodeDao.findPmFunctionDataByNodeFdn(node.getFdn())
        pmFunctionCache.addEntry(node.getFdn(), pmFunctionData)
        when: 'the factory method to create mediation task requests is executed'
        List<MediationTaskRequest> mediationTaskRequests = statsSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, subscription, trackResponse)
        then: 'no mediation task requests are created'
        mediationTaskRequests.size() == 0
        and: 'no trackers are created'
        pmicInitiationTrackerCache.getAllTrackers().size() == 0
        and: 'a PmConfigTaskRequest to enable (schedule) file collection is sent'
        1 * pmEventSender.sendPmEvent({ ((PmConfigTaskRequest) it).jobId.contains('ENABLE_FILE_COLLECTION') }, trackResponse)
    }

    def 'createMediationTaskRequests should return size 2 tasks when there are nodes without counters and add to tracker cache for BSC nodes'() {
        given:
        def nodeMO1 = nodeUtil.builder('BSC173').neType(NetworkElementType.BSC).pmEnabled(true).build()
        def nodeMO2 = nodeUtil.builder('BSC174').neType(NetworkElementType.BSC).pmEnabled(true).build()
        def subMo = statisticalSubscriptionBuilder.name('BSCStatisticalSubscription').nodes(nodeMO1, nodeMO2).administrativeState(AdministrationState.ACTIVATING).build()
        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as StatisticalSubscription
        when:
        List<MediationTaskRequest> tasks = statsSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, trackResponse)
        then:
        tasks.size() == 2
        pmicInitiationTrackerCache.getTracker(sub.getIdAsString()).getNodesFdnsToBeActivated() == [nodeMO1.getFdn(), nodeMO2.getFdn()] as Set
        tasks.each { assert it instanceof SubscriptionActivationTaskRequest }
    }
}
