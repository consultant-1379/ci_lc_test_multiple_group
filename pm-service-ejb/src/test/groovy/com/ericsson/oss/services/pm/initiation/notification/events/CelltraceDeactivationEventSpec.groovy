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

package com.ericsson.oss.services.pm.initiation.notification.events

import static com.ericsson.oss.pmic.cdi.test.util.Constants.DEACTIVATING
import static com.ericsson.oss.pmic.cdi.test.util.Constants.EBS_CELLTRACE_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.INACTIVE
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_1
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_2
import static com.ericsson.oss.pmic.cdi.test.util.Constants.OK
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_10000_CELLTRACE_SCANNER
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE

import org.mockito.Mockito
import spock.lang.Unroll

import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.Constants
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.generic.PmSubScannerService
import com.ericsson.oss.services.pm.initiation.ejb.GroovyTestUtils
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper

class CelltraceDeactivationEventSpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    DeactivationEvent objectUnderTest
    @Inject
    SubscriptionOperationExecutionTrackingCacheWrapper subscriptionInitiationCacheWrapper
    @Inject
    EventSender<MediationTaskRequest> eventSender
    @Inject
    PmSubScannerService pmSubScannerService
    @Inject
    GroovyTestUtils testUtils
    @MockedImplementation
    TimerService timerService
    @ImplementationInstance
    TimeGenerator timer = Mockito.mock(TimeGenerator)

    def eventA = new EventInfo('event1', 'groupName1')
    def eventB = new EventInfo('event2', 'groupName2')
    def node1 = nodeUtil.builder(NODE_NAME_1).build()
    
    def setup() {
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
    }

    def 'When EBS Subscription is DEACTIVATED when there is no other EBS Subscription ACTIVE then MTR should be sent'() {
        given: 'an EBSL_STREAM Subscription with node, ebsEvents and no events'
            def subscriptionMo = createSubscription('Subscription1', node1, AdministrationState.DEACTIVATING, [] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.EBSL_STREAM)
            createSubScanner(createEbsScanner(), subscriptionMo)

        when: 'CellTraceSubscription Subscription1 is deactivated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'should not delete PMICSubScannerInfo for nodes with already activated events'
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node1, subscriptionMo)).subscriptionId == subscriptionMo.poId

        and: 'Initiation Tracker Cache should not be empty'
            with(testUtils.getTrackerEntry(subscriptionMo.poId as String)) {
                subscriptionId == subscriptionMo.poId as String
                subscriptionAdministrationState == AdministrationState.DEACTIVATING.name()
                totalAmountOfExpectedNotifications == 1
            }

        and: 'Subscription Initiation Cache should be empty'
            subscriptionInitiationCacheWrapper.allEntries.empty

        and: 'One Deactivation MediationTaskRequest should be sent'
            1 * eventSender.send({it.subscriptionId == subscriptionMo.poId as String && it.nodeAddress == node1.fdn} as MediationTaskRequest)
       
        and: 'Status will not be changed'
            DEACTIVATING == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            OK == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String
    }

    @Unroll
    def 'When CELLTRACE_AND_EBSL_STREAM Subscription is Deactivated when NP Scanner status is #NP_Scanner_Status and 10004 scanner status is #EBS_Scanner_Status then #numberOfNotifications should be received'() {
        given: 'a CELLTRACE_AND_EBSL_STREAM Subscription with node, ebsEvents and events'
            def subscriptionMo = createSubscription('Subscription1', node1, AdministrationState.DEACTIVATING, eventA, [eventA, eventB] as EventInfo[], CellTraceCategory.CELLTRACE_AND_EBSL_STREAM)
            createScanner(NODE_NAME_1, NP_Scanner_Status, subscriptionMo.poId)
            createSubScanner(createEbsScanner(NODE_NAME_1, EBS_Scanner_Status), subscriptionMo)

        when: 'the subscription is deactivated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the PMICSubScannerInfo for nodes with already activated events should not be deleted'
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node1, subscriptionMo)).subscriptionId == subscriptionMo.poId

        and: 'Initiation Tracker Cache should not be empty'
            with(testUtils.getTrackerEntry(subscriptionMo.poId as String)) {
                subscriptionId == subscriptionMo.poId as String
                subscriptionAdministrationState == AdministrationState.DEACTIVATING.name()
                totalAmountOfExpectedNotifications == numberOfNotifications
            }

        and: 'the Subscription Initiation Cache should be empty'
            subscriptionInitiationCacheWrapper.allEntries.empty

        and: '1 Deactivation MediationTaskRequest should be sent'
            1 * eventSender.send({it.subscriptionId == subscriptionMo.poId as String && it.nodeAddress == node1.fdn} as MediationTaskRequest)

        and: 'the Status will not be changed'
            DEACTIVATING == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            OK == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String

        where:
            NP_Scanner_Status      | EBS_Scanner_Status   | numberOfNotifications
            ScannerStatus.ACTIVE   | ScannerStatus.ACTIVE | 2
            ScannerStatus.INACTIVE | ScannerStatus.ACTIVE | 1
            ScannerStatus.ERROR    | ScannerStatus.ACTIVE | 1
            ScannerStatus.UNKNOWN  | ScannerStatus.ACTIVE | 1
    }

    @Unroll
    def 'When CELLTRACE_AND_EBSL_STREAM subscription is DEACTIVATED when NP Scanner status is #NP_Scanner_Status and 10004 scanner status is #EBS_Scanner_Status then MTR should be sent and sub scanner should be removed'() {
        given: 'a CELLTRACE_AND_EBSL_STREAM Subscription with node, ebsEvents and events'
            def subscriptionMo = createSubscription('Subscription1', node1, AdministrationState.DEACTIVATING, [eventA, eventB] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.CELLTRACE_AND_EBSL_STREAM)
            createScanner(NODE_NAME_1, NP_Scanner_Status, subscriptionMo.poId)
            createSubScanner(createEbsScanner(NODE_NAME_1, EBS_Scanner_Status), subscriptionMo)

        when: 'the CellTraceSubscription Subscription1 is deactivated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the PMICSubScannerInfo should be deleted'
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node1, subscriptionMo)) == null

        and: 'the Initiation Tracker Cache should not be empty'
            with(testUtils.getTrackerEntry(subscriptionMo.poId as String)) {
                subscriptionId == subscriptionMo.poId as String
                subscriptionAdministrationState == AdministrationState.DEACTIVATING.name()
                totalAmountOfExpectedNotifications == 1
            }
        
        and: 'the Subscription Initiation Cache should be empty'
            subscriptionInitiationCacheWrapper.allEntries.empty
        
        and: 'a Deactivation MediationTaskRequest should be sent'
            1 * eventSender.send({it.subscriptionId == subscriptionMo.poId as String && it.nodeAddress == node1.fdn} as MediationTaskRequest)
        
        and: 'the Status will not be changed'
            DEACTIVATING == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            OK == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String
        
        where:
            NP_Scanner_Status    | EBS_Scanner_Status
            ScannerStatus.ACTIVE | ScannerStatus.INACTIVE
            ScannerStatus.ACTIVE | ScannerStatus.ERROR
            ScannerStatus.ACTIVE | ScannerStatus.UNKNOWN
    }

    @Unroll
    def 'When EBS Subscription with 2 scanners is DEACTIVATED and 10004 scanner is in #Ebs_Scanner state then no MTR should be sent'() {
        given: 'an EBS Subscription with a node, ebsEvents and normal events'
            def subscriptionMo = createSubscription('Subscription1', node1, AdministrationState.DEACTIVATING, eventA, [eventA, eventB] as EventInfo[], CellTraceCategory.CELLTRACE_AND_EBSL_STREAM)
            createScanner(NODE_NAME_1, NP_scanner, subscriptionMo.poId)
            createSubScanner(createEbsScanner(NODE_NAME_1, EBS_scanner), subscriptionMo)

        when: 'the CellTraceSubscription Subscription1 is deactivated'
            objectUnderTest.execute(subscriptionMo.poId)
        
        then: 'the PMICSubScannerInfo should be deleted'
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node1, subscriptionMo)) == null

        and: 'the Initiation Tracker Cache is empty'
            testUtils.getTrackerEntry(subscriptionMo.poId as String) == null

        and: 'the Subscription Initiation Cache should be empty'
            subscriptionInitiationCacheWrapper.allEntries.empty
        
        and: 'no MediationTaskRequest should be sent'
            0 * eventSender.send(_)
        
        and: 'the Status will be changed'
            INACTIVE == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            OK == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String
        
        where:
            NP_scanner             | EBS_scanner
            ScannerStatus.INACTIVE | ScannerStatus.INACTIVE
            ScannerStatus.ERROR    | ScannerStatus.INACTIVE
            ScannerStatus.UNKNOWN  | ScannerStatus.INACTIVE
            ScannerStatus.INACTIVE | ScannerStatus.ERROR
            ScannerStatus.ERROR    | ScannerStatus.ERROR
            ScannerStatus.UNKNOWN  | ScannerStatus.ERROR
            ScannerStatus.INACTIVE | ScannerStatus.UNKNOWN
            ScannerStatus.ERROR    | ScannerStatus.UNKNOWN
            ScannerStatus.UNKNOWN  | ScannerStatus.UNKNOWN
    }

    @Unroll
    def 'When Two EBS-L Stream Subscriptions with same Node and same Events, Subscription2 is Deactivated then MTR should not be sent for 10004 scanner and PMICSubScannerInfo is deleted'() {
        given: 'two EBS Subscriptions with a node, ebsEvents and normal events'
            def subscriptionMo1 = createSubscription('Subscription1', node1, AdministrationState.ACTIVE, eventA, [eventA, eventB] as EventInfo[], CellTraceCategory.CELLTRACE_AND_EBSL_STREAM)
            def subscriptionMo2 = createSubscription('Subscription2', node1, AdministrationState.DEACTIVATING, eventA, [eventA, eventB] as EventInfo[], CellTraceCategory.CELLTRACE_AND_EBSL_STREAM)

            createScanner(NODE_NAME_1, NP_scanner, subscriptionMo2.poId)
            def scanner = createEbsScanner(NODE_NAME_1, EBS_scanner)
            createSubScanner(scanner, subscriptionMo1)
            createSubScanner(scanner, subscriptionMo2)

        when: 'CellTraceSubscription Subscription2 is deactivated'
            objectUnderTest.execute(subscriptionMo2.poId)
        
        then: 'PMICSubScannerInfo should be deleted for inactive subscription and remain for a node with activated events'
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node1, subscriptionMo1)).subscriptionId == subscriptionMo1.poId
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node1, subscriptionMo2)) == null

        and: 'Initiation Tracker Cache is empty'
            testUtils.getTrackerEntry(subscriptionMo1.poId as String) == null
            testUtils.getTrackerEntry(subscriptionMo1.poId as String) == null

        and: 'the Subscription Initiation Cache should be empty'
            subscriptionInitiationCacheWrapper.allEntries.empty
        
        and: 'no MediationTaskRequest should be sent'
            0 * eventSender.send(_)
        
        and: 'the Status will be changed for Subscription2 and remain the same for Subscription1'
            Constants.ACTIVE == subscriptionMo1.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            OK == subscriptionMo1.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String
            INACTIVE == subscriptionMo2.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            OK == subscriptionMo2.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String
        
        where:
            NP_scanner             | EBS_scanner
            ScannerStatus.INACTIVE | ScannerStatus.INACTIVE
            ScannerStatus.ERROR    | ScannerStatus.INACTIVE
            ScannerStatus.UNKNOWN  | ScannerStatus.INACTIVE
            ScannerStatus.INACTIVE | ScannerStatus.ERROR
            ScannerStatus.ERROR    | ScannerStatus.ERROR
            ScannerStatus.UNKNOWN  | ScannerStatus.ERROR
            ScannerStatus.INACTIVE | ScannerStatus.UNKNOWN
            ScannerStatus.ERROR    | ScannerStatus.UNKNOWN
            ScannerStatus.UNKNOWN  | ScannerStatus.UNKNOWN
    }

    def 'When Two EBS Subscription with same Nodes and same Events, Subscription2 is DEACTIVATED then MTR should not be sent and PMICSubscannerInfo are deleted'() {
        given: 'Subscription1 is already active and Subscription2 is activting on same nodes for same events'
            def nodes = [node1, nodeUtil.builder(NODE_NAME_2).build()] as ManagedObject[]

            def subscriptionMo1 = createSubscription('Subscription1', nodes, AdministrationState.ACTIVE, [] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.EBSL_STREAM)
            def subscriptionMo = createSubscription('Subscription2', nodes, AdministrationState.DEACTIVATING, [] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.EBSL_STREAM)

            def scanner = createEbsScanner()
            createSubScanner(scanner, subscriptionMo)
            createSubScanner(scanner, subscriptionMo1)

            def scanner2 = createEbsScanner(NODE_NAME_2)
            createSubScanner(scanner2, subscriptionMo)
            createSubScanner(scanner2, subscriptionMo1)

        when: 'CellTraceSubscription Subscription2 is deactivated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'should not delete PMICSubScannerInfo for nodes with already activated events'
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodes[0], subscriptionMo)) == null
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodes[0], subscriptionMo1)).subscriptionId == subscriptionMo1.poId
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodes[1], subscriptionMo)) == null
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodes[1], subscriptionMo1)).subscriptionId == subscriptionMo1.poId


        and: 'the Initiation Tracker Cache should be empty'
            testUtils.getTrackerEntry(subscriptionMo.poId as String) == null
        
        and: 'the Subscription Initiation Cache should be empty'
            subscriptionInitiationCacheWrapper.allEntries.empty
        
        and: 'no Deactivation Mediation Task Request should be sent'
            0 * eventSender.send(_ as MediationTaskRequest)
        
        and: 'the Status will be changed'
            INACTIVE == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            OK == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String
    }

    def 'When Two EBS Subscription have common Events and one Node in common, Subscription1 is ACTIVE but Subscription2 is DEACTIVATED has uncommon node then MTR should be send for uncommon node'() {
        given: 'two subscriptions Subscription1 and Subscription2 have one Node common and both Subscription Events are the same'
            def nodes = [node1, nodeUtil.builder(NODE_NAME_2).build()] as ManagedObject[]
    
            def subscriptionMo1 = createSubscription('Subscription1', node1, AdministrationState.ACTIVE, [] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.EBSL_STREAM)
            def subscriptionMo = createSubscription('Subscription2', nodes, AdministrationState.DEACTIVATING, [] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.EBSL_STREAM)
            def scanner = createEbsScanner()
            createSubScanner(scanner, subscriptionMo)
            createSubScanner(scanner, subscriptionMo1)
            createSubScanner(createEbsScanner(NODE_NAME_2), subscriptionMo)

        when: 'the CellTraceSubscription Subscription2 is deactivated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'PMICSubScannerInfo for nodes with already activated events should not be deleted'
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodes[0], subscriptionMo)) == null
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodes[0], subscriptionMo1)).subscriptionId == subscriptionMo1.poId
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodes[1], subscriptionMo)).subscriptionId == subscriptionMo.poId
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodes[1], subscriptionMo1)) == null

        and: 'the Initiation Tracker Cache should be empty'
            with(testUtils.getTrackerEntry(subscriptionMo.poId as String)) {
                subscriptionId == subscriptionMo.poId as String
                subscriptionAdministrationState == AdministrationState.DEACTIVATING.name()
                totalAmountOfExpectedNotifications == 1
            }

        and: 'the Subscription Initiation Cache should be empty'
            subscriptionInitiationCacheWrapper.allEntries.empty

        and: 'one Deactivation MediationTaskRequest should be sent'
            1 * eventSender.send({it.subscriptionId == subscriptionMo.poId as String && it.nodeAddress == nodes[1].fdn} as MediationTaskRequest)

        and: 'the Status will not be changed'
            DEACTIVATING == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            OK == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String
    }

    def 'When Three EBS Subscription Subscription1 and Subscription2 have combine EbsEvents presents in Subscription3 then MTR should not be sent'() {
        given: 'Subscription1 and Subscription2 combined EbsEvents are present in Subscription3'
            def subscriptionMo = createSubscription('Subscription3', node1, AdministrationState.ACTIVE, [] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.EBSL_STREAM)
            def subscriptionMo1 = createSubscription('Subscription1', node1, AdministrationState.ACTIVE, [] as EventInfo[], eventA, CellTraceCategory.EBSL_STREAM)
            def subscriptionMo2 = createSubscription('Subscription2', node1, AdministrationState.ACTIVE, [] as EventInfo[], eventB, CellTraceCategory.EBSL_STREAM)
    
            def scanner = createEbsScanner()
            createSubScanner(scanner, subscriptionMo)
            createSubScanner(scanner, subscriptionMo1)
            createSubScanner(scanner, subscriptionMo2)
            
        when: 'the CellTraceSubscription Subscription2 is deactivated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'PMICSubScannerInfo for nodes with already activated events should not be deleted'
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node1, subscriptionMo)) == null
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node1, subscriptionMo1)).subscriptionId == subscriptionMo1.poId
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node1, subscriptionMo2)).subscriptionId == subscriptionMo2.poId

        and: 'the Initiation Tracker Cache should be empty'
            testUtils.getTrackerEntry(subscriptionMo.poId as String) == null

        and: 'the Subscription Initiation Cache should be empty'
            subscriptionInitiationCacheWrapper.allEntries.empty
        
        and: 'No Deactivation MediationTaskRequest should be sent'
            0 * eventSender.send(_ as MediationTaskRequest)

        and: 'the status will be changed'
            INACTIVE == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            OK == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String
    }

    def 'When Two EBS Subscription with same Node and same Events,  Subscription1 is ACTIVE-ERROR, Subscription2 is DEACTIVATED then MTR should be sent and PMICSubscannerInfo is not deleted'() {
        given: 'Subscription1 is already active and Subscription2 is activting on same nodes for same events'
            def subscriptionMo1 = createSubscription('Subscription1', node1, AdministrationState.ACTIVE, [] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.EBSL_STREAM)
            def subscriptionMo = createSubscription('Subscription2', node1, AdministrationState.DEACTIVATING, [] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.EBSL_STREAM)

            def scanner = createEbsScanner()
            createSubScanner(scanner, subscriptionMo)
            createSubScanner(scanner, subscriptionMo1)

        when: 'CellTraceSubscription Subscription2 is deactivated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'PMICSubScannerInfo for nodes with already activated events should not be deleted'
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node1, subscriptionMo)) == null
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node1, subscriptionMo1)).subscriptionId == subscriptionMo1.poId

        and: 'the Initiation Tracker Cache should not be empty'
            testUtils.getTrackerEntry(subscriptionMo.poId as String) == null

        and: 'Subscription Initiation Cache should be empty'
            subscriptionInitiationCacheWrapper.allEntries.empty
        
        and: 'no MTRs should be sent'
            0 * eventSender.send(_)

        and: 'the status will not be changed'
            INACTIVE == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            OK == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String
    }

    @Unroll
    def 'If 2 EBS-N Stream Only subscriptions with the same node and events activated, no deactivation MTR should be sent for the 10004 scanner and PMICSubScannerInfo should be deleted'() {
        given: 'a node with available scanners attached to the subscription containing ebs events for #ebsEventProducers event producers'
            def ebsEvents = createEventsForEventProducers(ebsEventProducers)
            def subscriptionMo1 = createSubscription('Subscription1', node1, AdministrationState.ACTIVE, [] as EventInfo[], ebsEvents, CellTraceCategory.NRAN_EBSN_STREAM)
            def subscriptionMo2 = createSubscription('Subscription2', node1, AdministrationState.DEACTIVATING, [] as EventInfo[], ebsEvents, CellTraceCategory.NRAN_EBSN_STREAM)

        and: 'ebs scanners with status #ebsScannerStatus for event producers #ebsEventProducers'
            ebsEventProducers.forEach{eventProducerId ->
                def scanner = createEbsScanner(node1.name, ScannerStatus.ACTIVE, "PREDEF.${eventProducerId}.10004.CELLTRACE")
                createSubScanner(scanner, subscriptionMo1)
                createSubScanner(scanner, subscriptionMo2)
            }

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo2.poId)

        then: 'the sub scanners for the deactivating subscription should be deleted'
            ebsEventProducers.every {eventProducerId ->
                pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node1, subscriptionMo2, "PREDEF.${eventProducerId}.10004.CELLTRACE")) == null
                pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node1, subscriptionMo1, "PREDEF.${eventProducerId}.10004.CELLTRACE")).subscriptionId == subscriptionMo1.poId
            }

        and: 'no Deactivation Mediation Task Request should be sent'
            0 * eventSender.send(_ as MediationTaskRequest)

        and: 'the status of the subscription will be changed'
            INACTIVE == (String) subscriptionMo2.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            OK == (String) subscriptionMo2.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)


        and: 'the Subscription Initiation Cache should be empty'
            subscriptionInitiationCacheWrapper.allEntries.empty

        where:
            ebsEventProducers << [['DU'], ['DU', 'CUCP'], ['DU', 'CUCP', 'CUUP']]
    }

    @Unroll
    def 'If 2 Cell Trace and EBS-N Stream subscriptions with the same node and events activated, deactivation MTR should be sent for the NP scanner and PMICSubScannerInfo should be deleted for HP'() {
        given: 'a node with available scanners attached to the subscription containing ebs events for #ebsEventProducers event producers'
            def events = createEventsForEventProducers(eventProducers)
            def ebsEvents = createEventsForEventProducers(ebsEventProducers)
            def subscriptionMo1 = createSubscription('Subscription1', node1, AdministrationState.ACTIVE, events, ebsEvents, CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM)
            def subscriptionMo2 = createSubscription('Subscription2', node1, AdministrationState.DEACTIVATING, events, ebsEvents, CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM)

        and: 'ebs scanners with status #ebsScannerStatus for event producers #ebsEventProducers'
            ebsEventProducers.forEach{eventProducerId ->
                def scanner = createEbsScanner(node1.name, ScannerStatus.ACTIVE, "PREDEF.${eventProducerId}.10004.CELLTRACE")
                createSubScanner(scanner, subscriptionMo1)
                createSubScanner(scanner, subscriptionMo2)
            }
            eventProducers.forEach{eventProducerId ->
                createScanner(node1.name, ScannerStatus.ACTIVE, subscriptionMo1.poId, "PREDEF.${eventProducerId}.10000.CELLTRACE")
                createScanner(node1.name, ScannerStatus.ACTIVE, subscriptionMo2.poId, "PREDEF.${eventProducerId}.10001.CELLTRACE")
            }

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo2.poId)

        then: 'should not delete PMICSubScannerInfo for nodes with already activated events'
            ebsEventProducers.every {eventProducerId ->
                pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node1, subscriptionMo2, "PREDEF.${eventProducerId}.10004.CELLTRACE")) == null
                pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node1, subscriptionMo1, "PREDEF.${eventProducerId}.10004.CELLTRACE")).subscriptionId == subscriptionMo1.poId
            }

        and: 'no Deactivation Mediation Task Request should be sent'
            1 * eventSender.send(_ as MediationTaskRequest)

        and: 'the subscription status will not be changed'
            DEACTIVATING == (String) subscriptionMo2.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            OK == (String) subscriptionMo2.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)


        and: 'the Subscription Initiation Cache should be empty'
            subscriptionInitiationCacheWrapper.allEntries.empty

        where:
            ebsEventProducers       | eventProducers        | expectedNotifications
            ['DU']                  | ['DU', 'CUCP']        | 2
            ['DU', 'CUCP']          | ['DU']                | 1
            ['DU', 'CUCP', 'CUUP']  | ['DU', 'CUCP', 'CUUP']| 3
            ['CUCP', 'CUUP']        | ['DU', 'CUCP']        | 2
    }

    def createEventsForEventProducers(eventProducerIds) {
        def events = []
        eventProducerIds.forEach { eventProducerId ->
            events.add(new EventInfo("${eventProducerId}TestEvent", 'group', eventProducerId))
        }
        events.toArray(new EventInfo[events.size()])
    }

    def createEbsScanner(nodeName = NODE_NAME_1, status = ScannerStatus.ACTIVE, name = EBS_CELLTRACE_SCANNER) {
        createScanner(nodeName, status, 0L, name, false, 900, ProcessType.HIGH_PRIORITY_CELLTRACE)
    }

    def createSubScanner(scanner, subscription) {
        dps.subScanner()
           .fdn("${scanner.fdn},PMICSubScannerInfo=${subscription.name}")
           .subscriptionId(subscription.poId)
           .build()
    }

    def createScanner(nodeName = NODE_NAME_1, status = ScannerStatus.ACTIVE, subscriptionId = 0L, name = PREDEF_10000_CELLTRACE_SCANNER, fileCollectionEnabled = true, rop = 900, processType = ProcessType.NORMAL_PRIORITY_CELLTRACE) {
        dps.scanner()
           .nodeName(nodeName)
           .name(name)
           .processType(processType)
           .status(status)
           .subscriptionId(subscriptionId)
           .fileCollectionEnabled(fileCollectionEnabled)
           .ropPeriod(rop)
           .build()
    }

    def createSubscription(name, nodes, administrationState, events = [] as EventInfo[], ebsEvents = [] as EventInfo[], cellTraceCategory = CellTraceCategory.CELLTRACE) {
        cellTraceSubscriptionBuilder.cellTraceCategory(cellTraceCategory)
                                    .ebsEvents(ebsEvents)
                                    .events(events)
                                    .name(name)
                                    .nodes(nodes)
                                    .administrativeState(administrationState)
                                    .build()
    }

    def buildPmicSubScannerInfoFdn(node, subscriptionUnderTest, scannerName = EBS_CELLTRACE_SCANNER) {
        pmSubScannerService.buildPmSubScannerFdn(node.fdn, scannerName, subscriptionUnderTest.name)
    }
}