/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.task.factories.activation

import static com.ericsson.oss.pmic.api.constants.ModelConstants.ScannerConstants.SCANNER_ATTR_SUBSCRIPTION_ID
import static com.ericsson.oss.pmic.cdi.test.util.Constants.ACTIVATING
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE

import spock.lang.Unroll

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.ContinuousCellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache

class CCTRSubscriptionActivationTaskRequestFactorySpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    CCTRSubscriptionActivationTaskRequestFactory cctrSubscriptionActivationTaskRequestFactory

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

    ManagedObject subscriptionMO

    ManagedObject radioNodeMO, fiveGRadioNodeMO
    ContinuousCellTraceSubscription subscription

    def events = [new EventInfo('INTERNAL_EVENT_ADMISSION_BLOCKING_STARTED', 'SESSION_ESTABLISHMENT_EVALUATION')]

    def eventsWithEventProducer = [new EventInfo("INTERNAL_EVENT_ADMISSION_BLOCKING_STARTED", "SESSION_ESTABLISHMENT_EVALUATION", "DU"),
                                   new EventInfo("EXTERNAL_EVENT_ADMISSION_BLOCKING_STARTED", "SESSION_ESTABLISHMENT_EVALUATION", "CUUP"),
                                   new EventInfo("INTERNAL_EVENT_ADMISSION_BLOCKING_STOPPED", "SESSION_ESTABLISHMENT_EVALUATION", "CUCP"),
                                   new EventInfo("INTERNAL_EVENT_ADMISSION_BLOCKING_STOPPED", "SESSION_ESTABLISHMENT_EVALUATION")]

    @Unroll
    def 'createActivationTasks should return #expectedNumberOfTasks task(s) for ContinuousCellTraceSubscription when there is 1 node with events for scanner #scannerName'() {
        given: 'one RadioNode'
            createNode()
        and: 'CCTR subscription ContinuousCellTraceSubscription for Lrat with 1 RadioNode and has 2 events with no event producerId'
            createCctrSubscription(events, 'ContinuousCellTraceSubscription', [radioNodeMO])
            def expectedSubscriptionId = expecedNumberOfNotifications == 0 ? 0 : subscription.id
        and: 'one INACTIVE HIGH_PRIORITY_CELLTRACE scanner'
            scannerUtil.builder(scannerName, radioNodeMO.getName())
                .status(ScannerStatus.INACTIVE)
                .processType(ProcessType.HIGH_PRIORITY_CELLTRACE).build()

        when: 'createMediationTaskRequests is invoked'
            def tasks = cctrSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.nodes, subscription, true)
        then: '#expectedNumberOfTasks mediation tasks created'
            expectedNumberOfTasks == tasks.size()
        and: 'Verify size of the tracker cache is 1'
            expectedTrackerCacheSize == pmicInitiationTrackerCache.allTrackers.size()
        and: 'Verify there is one notification for every scanner to be activated'
            expecedNumberOfNotifications == (pmicInitiationTrackerCache.allTrackers.size() > 0
                ? pmicInitiationTrackerCache.getTracker(subscription.idAsString).totalAmountOfExpectedNotifications : 0)
        and: 'Scanner assigned correct subscription id'
            expectedSubscriptionId == scannerDao.findAll()[0].subscriptionId
        where:
            scannerName                 || expectedNumberOfTasks | expecedNumberOfNotifications | expectedTrackerCacheSize
            'PREDEF.10005.CELLTRACE'    || 1                     | 1                            | 1
            'PREDEF.DU.10005.CELLTRACE' || 0                     | 0                            | 0
    }

    def 'createActivationTasks should assign subscription id to Lran scanners only'() {
        given: 'one RadioNode'
            createNode()
        and: 'CCTR subscription ContinuousCellTraceSubscription for Lrat with 1 RadioNode and has 2 events with no event producerId'
            createCctrSubscription(events, 'ContinuousCellTraceSubscription', [radioNodeMO])
        and: '3 INACTIVE scanners one for Lran and two for Nran'
            scannerUtil.builder("PREDEF.10005.CELLTRACE", radioNodeMO.getName())
                .status(ScannerStatus.INACTIVE)
                .processType(ProcessType.HIGH_PRIORITY_CELLTRACE)
                .build()
            scannerUtil.builder("PREDEF.DU.10005.CELLTRACE", radioNodeMO.getName())
                .status(ScannerStatus.INACTIVE)
                .processType(ProcessType.HIGH_PRIORITY_CELLTRACE)
                .build()
            scannerUtil.builder("PREDEF.CUCP.10005.CELLTRACE", radioNodeMO.getName())
                .status(ScannerStatus.INACTIVE)
                .processType(ProcessType.HIGH_PRIORITY_CELLTRACE)
                .build()

        when: 'createMediationTaskRequests is invoked'
            def tasks = cctrSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.nodes, subscription, true)
        then: 'Verify one mediation task request created'
            1 == tasks.size()
        and: 'Verify size of the tracker cache is 1'
            1 == pmicInitiationTrackerCache.allTrackers.size()
        and: 'Verify there is one notification for every scanner to be activated'
            1 == pmicInitiationTrackerCache.getTracker(subscription.idAsString).totalAmountOfExpectedNotifications
        and: 'Only Lrat Scanner has assigned subscription id'
            1 == getNumberOfScannersAssignedToId(subscription.id)
    }

    @Unroll
    def 'createActivationTasks for continuous cellTrace Nran subscription #scannerStatus  and  #associatedSubscriptionId when it has nodes with neType 5GRadioNode and RadioNode'() {
        given: 'one RadioNode and one 5GRadioNode'
            createNode()
        and: 'a continuous celltrace subscription with 2 nodes and 3 events associated to CUCP, DU and CUUP event producers respectively'
            createCctrSubscription(eventsWithEventProducer, 'ContinuousCellTraceSubscriptionNran', [radioNodeMO, fiveGRadioNodeMO])
            List<ManagedObject> scannerMos = createScannersFor5GRadioNode(scannerStatus, associatedSubscriptionId, 'NONE')
        when: 'createMediationTaskRequests is invoked'
            List<MediationTaskRequest> tasks = cctrSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.getNodes(), subscription, true)
        then: 'Verify one mediation task request created for every node'
            tasks.size() == tasksize
        and: 'Verify size of the tracker cache'
            pmicInitiationTrackerCache.getAllTrackers().size() == tasksize
        and: 'Verify total number of scanners updated with subscription Ids in the scanner MO'
            getTotalSelectedScanners(scannerMos, subscription.getId().toString()) == 3
        and: 'Verify there is one notification for every scanner to be activated'
            getExpectedNotifications() == 3 * tasksize
        and: 'Verify subscription administration state should be ACTIVATING'
            subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == administrationState
        where:
            scannerStatus | associatedSubscriptionId | tasksize | administrationState
            'INACTIVE'    | '0'                      | 1        | ACTIVATING
            'INACTIVE'    | '123'                    | 1        | ACTIVATING
            'ACTIVE'      | '0'                      | 0        | ACTIVATING
            'ACTIVE'      | '123'                    | 1        | ACTIVATING
    }

    @Unroll
    def 'createActivationTasks for continuous cellTrace Nran subscription when it has only nodes with neType 5GRadioNode'() {
        given: 'one 5GRadioNode'
            createNode()
        and: 'a continuous celltrace subscription with 1 node and 3 events associated to CUCP, DU and CUUP event producers respectively'
            createCctrSubscription(eventsWithEventProducer, 'ContinuousCellTraceSubscriptionNran', [fiveGRadioNodeMO])
            List<ManagedObject> scannerMos = createScanners('INACTIVE', '0', missingScannersFromNode)
        when: 'createMediationTaskRequests is invoked'
            List<MediationTaskRequest> tasks = cctrSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.getNodes(), subscription, true)
        then: 'Verify one mediation task request created for every node'
            tasks.size() == 1
        and: 'Verify size of the tracker cache'
            pmicInitiationTrackerCache.getAllTrackers().size() == 1
        and: 'Verify total number of scanners updated with subscription Ids in the scanner MO'
            getTotalSelectedScanners(scannerMos, subscription.getId().toString()) == totalSelectedScanners
        and: 'Verify there is one notification for every scanner to be activated'
            getExpectedNotifications() == expectedNumberOfNotifications
        and: 'Verify subscription task status (OK/ERROR) and administration state should be ACTIVATING'
            subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == TaskStatus.OK.name()
            subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == ACTIVATING
        where:
            missingScannersFromNode       || expectedNumberOfNotifications | totalSelectedScanners
            'NONE'                        || 3                             | 3
            'PREDEF.CUCP.10005.CELLTRACE' || 2                             | 2
    }

    @Unroll
    def 'createActivationTasks for continuous cellTrace Nran subscription when it has only nodes with neType RadioNode'() {
        given: 'one RadioNode'
            createNode()
        and: 'a continuous celltrace subscription with 1 node and 3 events associated to CUCP, DU and CUUP event producers respectively'
            createCctrSubscription(eventsWithEventProducer, 'ContinuousCellTraceSubscriptionNran', [radioNodeMO])
            List<ManagedObject> scannerMos = createScanners('INACTIVE', '0', missingScannersFromNode)
        when: 'createMediationTaskRequests is invoked'
            List<MediationTaskRequest> tasks = cctrSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.getNodes(), subscription, true)
        then: 'Verify one mediation task request created for every node'
            tasks.size() == 1
        and: 'Verify size of the tracker cache'
            pmicInitiationTrackerCache.getAllTrackers().size() == 1
        and: 'Verify total number of scanners updated with subscription Ids in the scanner MO'
            getTotalSelectedScanners(scannerMos, subscription.getId().toString()) == totalSelectedScanners
        and: 'Verify there is one notification for every scanner to be activated'
            getExpectedNotifications() == expectedNumberOfNotifications
        and: 'Verify subscription task status (OK/ERROR) and administration state should be ACTIVATING'
            subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == TaskStatus.OK.name()
            subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == ACTIVATING
        where:
            missingScannersFromNode       || expectedNumberOfNotifications | totalSelectedScanners
            'NONE'                        || 3                             | 3
            'PREDEF.CUCP.10005.CELLTRACE' || 2                             | 2
    }

    def 'No activation task should be created for continuous cellTrace Nran subscription when no scanners are found on nodes'() {
        given: 'one RadioNode and one 5GRadioNode'
            createNode()
        and: 'a continuous celltrace subscription with 2 nodes and 3 events associated to CUCP, DU and CUUP event producers respectively'
            createCctrSubscription(eventsWithEventProducer, 'ContinuousCellTraceSubscriptionNran', [radioNodeMO, fiveGRadioNodeMO])
            createScanners('INACTIVE', '0', 'ALL')
        when: 'createMediationTaskRequests is invoked'
            List<MediationTaskRequest> tasks = cctrSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.getNodes(), subscription, true)
        then: 'No mediation task request is created'
            tasks.size() == 0
        and: 'No request added to the tracker cache'
            pmicInitiationTrackerCache.getAllTrackers().size() == 0
        and: 'No scanners and there should not be any notifications'
            getExpectedNotifications() == 0
        and: 'Subscription task status should be in ERROR and administration state should be ACTIVATING'
            subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == TaskStatus.ERROR.name()
            subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == ACTIVATING
    }

    int getNumberOfScannersAssignedToId(final long subscriptionId) {
        int count
        scannerDao.findAll().each {
            count += it.subscriptionId == subscriptionId ? 1 : 0
        }
        return count
    }

    def createNode(NeConfigurationManagerState neConfigurationManagerState = NeConfigurationManagerState.ENABLED) {
        radioNodeMO = nodeUtil.builder('RadioNode001').neType("RadioNode").neConfigurationManagerState(neConfigurationManagerState).build()
        fiveGRadioNodeMO = nodeUtil.builder('5GRadioNode001').neType("5GRadioNode").neConfigurationManagerState(neConfigurationManagerState).build()
    }

    def createCctrSubscription(List<EventInfo> events = [], String name = 'ContinuousCellTraceSubscription',
                               List<ManagedObject> nodes = [radioNodeMO, fiveGRadioNodeMO]) {
        subscriptionMO = cctrSubscriptionBuilder.outputMode(OutputModeType.FILE.name())
            .name(name)
            .administrativeState(AdministrationState.ACTIVATING)
            .build()
        dpsUtils.addAssociation(subscriptionMO, 'nodes', nodes as ManagedObject[])
        subscription = subscriptionDao.findOneById(subscriptionMO.getPoId(), true) as CellTraceSubscription
        subscription.setEvents(events)
        subscription.setCellTraceCategory(CellTraceCategory.CELLTRACE)
    }

    List<ManagedObject> createScanners(String scannerStatus, String associatedSubscriptionId, String missingScannersFromNode) {
        List<ManagedObject> scannerMos = []
        if (missingScannersFromNode != 'ALL') {
            scannerMos.addAll(createScannersFor5GRadioNode(scannerStatus, associatedSubscriptionId, missingScannersFromNode))
            scannerMos.addAll(createScannersForRadioNode(scannerStatus, associatedSubscriptionId, missingScannersFromNode))
        }
        scannerMos
    }

    def createScannersFor5GRadioNode(String scannerStatus, String associatedSubscriptionId, String missingScannersFromNode) {
        List scannerNamesFor5GRadioNode = ['PREDEF.DU.10005.CELLTRACE',
                                           'PREDEF.CUUP.10005.CELLTRACE',
                                           'PREDEF.CUCP.10005.CELLTRACE',
                                           'PREDEF.DU.10000.CELLTRACE']
        if (missingScannersFromNode != 'NONE') {
            scannerNamesFor5GRadioNode.remove(missingScannersFromNode)
        }
        createScanners(scannerNamesFor5GRadioNode, scannerStatus, '5GRadioNode001', associatedSubscriptionId)
    }

    def createScannersForRadioNode(String scannerStatus, String associatedSubscriptionId, String missingScannersFromNode) {
        List scannerNamesForRadioNode = ['PREDEF.10005.CELLTRACE',
                                         'PREDEF.DU.10005.CELLTRACE',
                                         'PREDEF.CUUP.10005.CELLTRACE',
                                         'PREDEF.CUCP.10005.CELLTRACE',
                                         'PREDEF.DU.10000.CELLTRACE',
                                         'PREDEF.PU.10005.CELLTRACE']
        if (missingScannersFromNode != 'NONE') {
            scannerNamesForRadioNode.remove(missingScannersFromNode)
        }
        createScanners(scannerNamesForRadioNode, scannerStatus, 'RadioNode001', associatedSubscriptionId)
    }

    List<ManagedObject> createScanners(List scannerNames, String scannerStatus, String nodeName, String associatedSubscriptionId) {
        List<ManagedObject> scannerMos = []
        scannerNames.each { scannerName ->
            scannerMos.add(scannerUtil.builder(scannerName, nodeName).status(scannerStatus)
                .subscriptionId(associatedSubscriptionId == 'subId' ? subscription.getId().toString() : associatedSubscriptionId).processType(ProcessType.HIGH_PRIORITY_CELLTRACE).build())
        }
        scannerMos
    }

    def getTotalSelectedScanners(List<ManagedObject> scannerMos, String subscriptionId) {
        int selectedScannersCount = 0
        scannerMos.each { scannerMo ->
            if (scannerMo.getAttribute(SCANNER_ATTR_SUBSCRIPTION_ID).equals(subscriptionId))
                selectedScannersCount++
        }
        selectedScannersCount
    }

    def getExpectedNotifications() {
        int expectedNotifications = 0
        if (pmicInitiationTrackerCache.getTracker(subscription.getIdAsString()) != null)
            expectedNotifications = pmicInitiationTrackerCache.getTracker(subscription.getIdAsString()).getTotalAmountOfExpectedNotifications()
        expectedNotifications
    }
}
