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

import static com.ericsson.oss.pmic.cdi.test.util.Constants.*
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.NO_EVENTS_EXISTS_FOR_ACTIVATION
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.NO_PROCESSES_WITH_STATUS_INACTIVE
import static com.ericsson.oss.services.pm.initiation.task.factories.activation.EbmSubscriptionActivationTaskRequestFactory.PREDEF_EBMLOG_SCANNER

import spock.lang.Unroll

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.EbmSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState
import com.ericsson.oss.services.pm.common.logging.PMICLog
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache

class EbmSubscriptionActivationTaskRequestFactorySpec extends SkeletonSpec {

    @ObjectUnderTest
    EbmSubscriptionActivationTaskRequestFactory ebmSubscriptionActivationTaskRequestFactory

    @Inject
    private PMICInitiationTrackerCache pmicInitiationTrackerCache

    @Inject
    private SubscriptionDao subscriptionDao

    @Inject
    private SystemRecorderWrapperLocal systemRecorder

    @Override
    def autoAllocateFrom() {
        def packages = super.autoAllocateFrom()
        packages.addAll(['com.ericsson.oss.pmic.dao', 'com.ericsson.oss.pmic.dto'])
        return packages
    }

    ManagedObject subscriptionMO
    ManagedObject nodeMO1, nodeMO2
    EbmSubscription subscription

    def events = [new EventInfo("L_ATTACH", "SgsnFunction")]

    def "createMediationTaskRequests should return no tasks when no events and log system recorder"() {
        given:
        createNode()
        createEbmSubscription()
        when:
        List<MediationTaskRequest> tasks = ebmSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.getNodes(), subscription, true)
        then:
        tasks == []
        pmicInitiationTrackerCache.getAllTrackers() == []
        1 * systemRecorder.error(NO_EVENTS_EXISTS_FOR_ACTIVATION, "EbmTestSubscription", "Activation failed", PMICLog.Operation.ACTIVATION)

    }

    def "createMediationTaskRequests should return no tasks when no nodes in subscription and should not add to cache"() {
        given:
        createNode()
        createEbmSubscription([] as ManagedObject[], events)
        when:
        List<MediationTaskRequest> tasks = ebmSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.getNodes(), subscription, true)
        then:
        tasks == []
        pmicInitiationTrackerCache.getAllTrackers() == []
        0 * systemRecorder.error(_)
    }

    @Unroll
    def "createMediationTaskRequests should return size 2 tasks when there are 2 nodes with inactive scanner"() {
        given:
        createNode(neConfigurationManagerState)
        createEbmSubscription([nodeMO1, nodeMO2] as ManagedObject[], events)

        scannerUtil.builder(PREDEF_EBMLOG_SCANNER, SGSN_NODE_NAME_1).processType(ProcessType.EVENTJOB).subscriptionId(0L).build()
        scannerUtil.builder(PREDEF_EBMLOG_SCANNER, SGSN_NODE_NAME_2).processType(ProcessType.EVENTJOB).subscriptionId(0L).build()
        when:
        List<MediationTaskRequest> tasks = ebmSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.getNodes(), subscription, true)
        then:
        tasks.size() == taskNumber
        if (taskNumber > 0) {
            assert tasks.collect { it.getNodeAddress() }.sort() == [NE_PREFIX + SGSN_NODE_NAME_1, NE_PREFIX + SGSN_NODE_NAME_2].sort()
        }
        and:
        if (trackDeactivatingCalls != 0) {
            pmicInitiationTrackerCache.getAllTrackers().size() == 1
            pmicInitiationTrackerCache.getTracker(subscription.getIdAsString()).getTotalAmountOfExpectedNotifications() == 2
            pmicInitiationTrackerCache.getTracker(subscription.getIdAsString()).getUnprocessedNodesAndTypes() == [(NE_PREFIX + SGSN_NODE_NAME_1): NetworkElementType.SGSNMME.name(), (NE_PREFIX + SGSN_NODE_NAME_2): NetworkElementType.SGSNMME.name()]
        } else {
            pmicInitiationTrackerCache.getAllTrackers().size() == 0
        }
        where:
        neConfigurationManagerState          || taskNumber | trackDeactivatingCalls
        NeConfigurationManagerState.ENABLED  || 2          | 1
        NeConfigurationManagerState.DISABLED || 0          | 0
    }

    def "createMediationTaskRequests should return size 2 tasks when there are 2 nodes with unassigned scanner with mixed states"() {
        given:
        createNode()
        createEbmSubscription([nodeMO1, nodeMO2] as ManagedObject[], events)

        scannerUtil.builder(PREDEF_EBMLOG_SCANNER, SGSN_NODE_NAME_1).processType(ProcessType.EVENTJOB).status(ScannerStatus.INACTIVE).subscriptionId(0L).build()
        scannerUtil.builder(PREDEF_EBMLOG_SCANNER, SGSN_NODE_NAME_2).processType(ProcessType.EVENTJOB).status(ScannerStatus.ACTIVE).subscriptionId(0L).build()
        when:
        List<MediationTaskRequest> tasks = ebmSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.getNodes(), subscription, true)
        then:
        tasks.size() == 2
        tasks.collect { it.getNodeAddress() }.sort() == [NE_PREFIX + SGSN_NODE_NAME_1, NE_PREFIX + SGSN_NODE_NAME_2].sort()

        pmicInitiationTrackerCache.getAllTrackers().size() == 1
        pmicInitiationTrackerCache.getTracker(subscription.getIdAsString()).getTotalAmountOfExpectedNotifications() == 2
        pmicInitiationTrackerCache.getTracker(subscription.getIdAsString()).getUnprocessedNodesAndTypes() == [(NE_PREFIX + SGSN_NODE_NAME_1): NetworkElementType.SGSNMME.name(), (NE_PREFIX + SGSN_NODE_NAME_2): NetworkElementType.SGSNMME.name()]

    }

    def "createActivationTasks should return 0 tasks when there are nodes and events but no free scanner on all nodes and should not add to cache"() {
        given:
        createNode()
        createEbmSubscription([nodeMO1, nodeMO2] as ManagedObject[], events)

        scannerUtil.builder(PREDEF_EBMLOG_SCANNER, SGSN_NODE_NAME_1).processType(ProcessType.EVENTJOB).status(ScannerStatus.ACTIVE).subscriptionId(123L).build()
        scannerUtil.builder(PREDEF_EBMLOG_SCANNER, SGSN_NODE_NAME_2).processType(ProcessType.EVENTJOB).status(ScannerStatus.ACTIVE).subscriptionId(123L).build()
        when:
        List<MediationTaskRequest> tasks = ebmSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.getNodes(), subscription, true)
        then:
        tasks == []
        pmicInitiationTrackerCache.getAllTrackers() == []

        1 * systemRecorder.error(NO_PROCESSES_WITH_STATUS_INACTIVE, "EbmTestSubscription",
                "Failed to activate on node NetworkElement=SGSN-16A-V1-CP0001", PMICLog.Operation.ACTIVATION)
        1 * systemRecorder.error(NO_PROCESSES_WITH_STATUS_INACTIVE, "EbmTestSubscription",
                "Failed to activate on node NetworkElement=SGSN-16A-V1-CP0002", PMICLog.Operation.ACTIVATION)
    }

    def createNode(NeConfigurationManagerState neConfigurationManagerState = NeConfigurationManagerState.ENABLED) {
        nodeMO1 = nodeUtil.builder(SGSN_NODE_NAME_1).neType(NetworkElementType.SGSNMME.name()).neConfigurationManagerState(neConfigurationManagerState).build()
        nodeMO2 = nodeUtil.builder(SGSN_NODE_NAME_2).neType(NetworkElementType.SGSNMME.name()).neConfigurationManagerState(neConfigurationManagerState).build()
    }

    def createEbmSubscription(ManagedObject[] nodes, List<EventInfo> events = [], boolean ebsEnabled = false) {
        subscriptionMO = ebmSubscriptionBuilder.setEbsEnabled(ebsEnabled).name("EbmTestSubscription").administrativeState(AdministrationState.ACTIVATING).build()
        dpsUtils.addAssociation(subscriptionMO, "nodes", nodes)
        subscription = subscriptionDao.findOneById(subscriptionMO.getPoId(), true) as EbmSubscription
        subscription.setEvents(events)
    }
}
