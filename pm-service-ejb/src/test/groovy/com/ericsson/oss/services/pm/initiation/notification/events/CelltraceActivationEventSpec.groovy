/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2016
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */
package com.ericsson.oss.services.pm.initiation.notification.events

import static com.ericsson.oss.pmic.cdi.test.util.Constants.*
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.*

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
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.SubscriptionBuilder
import com.ericsson.oss.pmic.dao.PmSubScannerDao
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.enums.*
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.generic.PmSubScannerService
import com.ericsson.oss.services.pm.initiation.ejb.GroovyTestUtils
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper

class CelltraceActivationEventSpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    ActivationEvent objectUnderTest
    @Inject
    PmSubScannerService pmSubScannerService
    @Inject
    PmSubScannerDao pmSubScannerDao
    @Inject
    GroovyTestUtils testUtils
    @Inject
    EventSender<MediationTaskRequest> eventSender
    @Inject
    SubscriptionOperationExecutionTrackingCacheWrapper subscriptionInitiationCacheWrapper
    @MockedImplementation
    TimerService timerService
    @ImplementationInstance
    TimeGenerator timer = Mockito.mock(TimeGenerator)

    def eventA = new EventInfo('event1', 'groupName1')
    def eventB = new EventInfo('event2', 'groupName2')

    def setup() {
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
    }

    def 'When activated, subscription will go ACTIVE/ERROR if nodes have no scanners'() {
        given: 'two nodes with no available scanners attached to one subscription'
            def nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build()] as ManagedObject[]
            def subscriptionMo = createSubscription('Subscription1', nodes, AdministrationState.ACTIVATING, eventA)

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the Status will be ACTIVE/ERROR'
            ACTIVE == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'the node associations will be 2'
            2 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)

        and: 'nothing will be added to the tracker cache'
            0 == testUtils.totalNodesToBeActivated as Integer
    }

    def 'When activated, CellTrace subscription will go ACTIVE/ERROR if nodes have no EBS scanners'() {
        given: 'two nodes with no available scanners attached to one subscription'
            def nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build()] as ManagedObject[]
            def subscriptionMo = createSubscription('Subscription1', nodes, AdministrationState.ACTIVATING, [] as EventInfo[], eventA, CellTraceCategory.EBSL_STREAM)

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the Status will be ACTIVE/ERROR'
            ACTIVE == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'the node associations will be 2'
            2 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)

        and: 'nothing will be added to the tracker cache'
            0 == testUtils.totalNodesToBeActivated as Integer
    }

    def 'When activated, CellTrace subscription will go ACTIVE/ERROR if a node has events and ebsEvents but no EBS scanner'() {
        given: 'one node with events, no available EBS scanner attached'
            def node = nodeUtil.builder(NODE_NAME_1).build()
            def subscriptionMo = createSubscription('Subscription1', node, AdministrationState.ACTIVATING, eventA, eventA, CellTraceCategory.CELLTRACE_AND_EBSL_STREAM)

            createScanner(NODE_NAME_1, ScannerStatus.INACTIVE, 0L)

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the status will be ACTIVE/ERROR'
            ACTIVATING == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'the node associations will be 1'
            1 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)

        and: '1 node will be added to the tracker cache'
            1 == testUtils.totalNodesToBeActivated as Integer
    }

    def 'When activated, CellTrace subscription will go ACTIVE/ERROR if a node has events but no normal scanner, EBS Stream Cluster is deployed'() {
        given: 'one node with events, no available normal scanner attached'
            def node = nodeUtil.builder(NODE_NAME_1).build()
            def subscriptionMo = createSubscription('Subscription1', node, AdministrationState.ACTIVATING, eventA, eventA, CellTraceCategory.CELLTRACE_AND_EBSL_STREAM)

            createEbsScanner(NODE_NAME_1, ScannerStatus.INACTIVE)

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the Status will be ACTIVE/ERROR'
            ACTIVATING == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'the node associations will be 1'
            1 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)

        and: '1 node will be added to the tracker cache'
            1 == testUtils.totalNodesToBeActivated as Integer
    }

    def 'When activated, subscription will stay in current state if nodes have available scanners'() {
        given: 'two nodes with available scanners attached to one subscription'
            setFileAndStreamingOutputAdditionalAttributes(cellTraceSubscriptionBuilder)
            def nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build()] as ManagedObject[]
            def subscriptionMo = createSubscription('Subscription1', nodes, AdministrationState.ACTIVATING, eventA)

            createScanner(NODE_NAME_1, ScannerStatus.INACTIVE, 0L)
            createScanner(NODE_NAME_2, ScannerStatus.INACTIVE, 0L)

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the Status will not be changed'
            ACTIVATING == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            OK == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'the node associations will be 2'
            2 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)

        and: 'both nodes will be added to the tracker cache'
            testUtils.totalNodesToBeActivated == 2
    }

    def 'When activated, CELLTRACE_AND_EBSL_STREAM subscription will stay in current state if nodes have available scanners'() {
        given: 'a node with available scanners attached to the subscription'
            def node = nodeUtil.builder(NODE_NAME_1).build()
            def subscriptionMo = createSubscription('Subscription1', node, AdministrationState.ACTIVATING, eventA, [eventA, eventB] as EventInfo[], CellTraceCategory.CELLTRACE_AND_EBSL_STREAM)

            createScanner(NODE_NAME_1, ScannerStatus.INACTIVE, 0L)
            createEbsScanner(NODE_NAME_1, ScannerStatus.INACTIVE)

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the status will not be changed'
            ACTIVATING == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            OK == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'the node associations will be 1'
            1 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)

        and: 'a node will be added to the tracker cache with 2 notifications'
            2 == testUtils.totalNodesToBeActivated as Integer
    }

    @Unroll
    def 'When activated, NRAN_AND_EBSN_STREAM subscription will stay in current state if nodes have available scanners'() {
        given: 'a node with available scanners attached to the subscription containing ebs events for #ebsEventProducers event producers'
            def node = nodeUtil.builder(NODE_NAME_1).build()
            def subscriptionMo = createSubscription('Subscription1', node, AdministrationState.ACTIVATING, [] as EventInfo[],
                                                    createEventsForEventProducers(ebsEventProducers), CellTraceCategory.NRAN_EBSN_STREAM)

        and: 'ebs scanners with status #ebsScannerStatus for event producers #ebsEventProducers'
            ebsEventProducers.forEach{eventProducerId ->
                createSubScanner(createEbsScanner(node.name, ebsScannerStatus, "PREDEF.${eventProducerId}.10004.CELLTRACE"), subscriptionMo)
            }

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the status will not be changed'
            ACTIVATING == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            OK == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'the node associations will be 1'
            1 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)

        and: 'a node will be added to the tracker cache with #expectedNotifications notifications'
            expectedNotifications == testUtils.totalNodesToBeActivated as Integer

        where:
            ebsEventProducers       | ebsScannerStatus      || expectedNotifications
            ['DU']                  | ScannerStatus.ACTIVE  || 1
            ['DU', 'CUCP']          | ScannerStatus.ACTIVE  || 2
            ['DU', 'CUCP', 'CUUP']  | ScannerStatus.ACTIVE  || 3
            ['DU']                  | ScannerStatus.INACTIVE|| 1
            ['DU', 'CUCP']          | ScannerStatus.INACTIVE|| 2
            ['DU', 'CUCP', 'CUUP']  | ScannerStatus.INACTIVE|| 3
            ['DU']                  | ScannerStatus.ERROR   || 1
            ['DU', 'CUCP']          | ScannerStatus.ERROR   || 2
            ['DU', 'CUCP', 'CUUP']  | ScannerStatus.ERROR   || 3
    }

    @Unroll
    def 'When activated, CELLTRACE_NRAN_AND_EBSN_STREAM subscription will stay in current state if nodes have available scanners'() {
        given: 'a node with available scanners attached to the subscription containing ebs events for #ebsEventProducers event producers and standard events for #eventProducers event producers'
            def node = nodeUtil.builder(NODE_NAME_1).build()
            def subscriptionMo = createSubscription('Subscription1', node, AdministrationState.ACTIVATING, createEventsForEventProducers(eventProducers),
                    createEventsForEventProducers(ebsEventProducers), CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM)

        and: 'ebs scanners with status #ebsScannerStatus for event producers #ebsEventProducers'
            eventProducers.forEach{eventProducerId ->
                createScanner(node.name, scannerStatus, subscriptionMo.poId, "PREDEF.${eventProducerId}.10001.CELLTRACE")
            }
            ebsEventProducers.forEach{eventProducerId ->
                createSubScanner(createEbsScanner(node.name, ebsScannerStatus, "PREDEF.${eventProducerId}.10004.CELLTRACE"), subscriptionMo)
            }

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the status will not be changed'
            ACTIVATING == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            OK == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'the node associations will be 1'
            1 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)

        and: 'a node will be added to the tracker cache with #expectedNotifications notifications'
            expectedNotifications == testUtils.totalNodesToBeActivated as Integer

        where:
            eventProducers  | ebsEventProducers       | scannerStatus           | ebsScannerStatus      || expectedNotifications
            ['CUCP']        | ['DU']                  | ScannerStatus.INACTIVE  | ScannerStatus.ACTIVE  || 2
            ['DU', 'CUCP']  | ['CUUCP']               | ScannerStatus.INACTIVE  | ScannerStatus.ACTIVE  || 3
            ['DU']          | ['DU']                  | ScannerStatus.INACTIVE  | ScannerStatus.ACTIVE  || 2
            ['CUCP']        | ['DU']                  | ScannerStatus.INACTIVE  | ScannerStatus.INACTIVE|| 2
            ['DU', 'CUCP']  | ['CUUCP']               | ScannerStatus.INACTIVE  | ScannerStatus.INACTIVE|| 3
            ['DU']          | ['DU']                  | ScannerStatus.INACTIVE  | ScannerStatus.INACTIVE|| 2
    }

    @Unroll
    def 'When activated, subscription NRAN_AND_EBSN_STREAM will go ACTIVATING/ERROR if nodes have scanners assigned to another subscription'() {
        given: 'a node with available scanners attached to the subscription containing ebs events for #ebsEventProducers event producers and standard events for #eventProducers event producers'
            def node = dps.node().name(NODE_NAME_1).neType('RadioNode').technologyDomain(['5GS']).build()
            def subscriptionMo = createSubscription('Subscription1', node, AdministrationState.ACTIVATING, createEventsForEventProducers(eventProducers),
                    createEventsForEventProducers(ebsEventProducers), CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM)

        and: 'ebs scanners with status #ebsScannerStatus for event producers #ebsEventProducers'
            eventProducers.forEach{eventProducerId ->
                createScanner(node.name, scannerStatus, nonExistentSubId, "PREDEF.${eventProducerId}.10001.CELLTRACE")
            }
            ebsEventProducers.forEach{eventProducerId ->
                createSubScanner(createEbsScanner(node.name, ebsScannerStatus, "PREDEF.${eventProducerId}.10004.CELLTRACE"), subscriptionMo)
            }

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the task status will be error but subscription status ACTIVATING as the ebs scanners still need to be activated'
            ACTIVATING == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'the node associations will be 1'
            1 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)

        and: 'a node will be added to the tracker cache with 1 notification for the ebs scanner'
            1 == testUtils.totalNodesToBeActivated as Integer

        where:
            eventProducers  | ebsEventProducers       | scannerStatus           | ebsScannerStatus      || expectedNotifications
            ['CUCP']        | ['DU']                  | ScannerStatus.INACTIVE  | ScannerStatus.ACTIVE  || 1
            ['DU', 'CUCP']  | ['CUUCP']               | ScannerStatus.INACTIVE  | ScannerStatus.ACTIVE  || 1
            ['DU']          | ['DU']                  | ScannerStatus.INACTIVE  | ScannerStatus.ACTIVE  || 1
            ['CUCP']        | ['DU']                  | ScannerStatus.INACTIVE  | ScannerStatus.INACTIVE|| 1
            ['DU', 'CUCP']  | ['CUUCP']               | ScannerStatus.INACTIVE  | ScannerStatus.INACTIVE|| 1
            ['DU']          | ['DU']                  | ScannerStatus.INACTIVE  | ScannerStatus.INACTIVE|| 1
            ['CUCP']        | ['DU']                  | ScannerStatus.ACTIVE    | ScannerStatus.ACTIVE  || 1
            ['DU', 'CUCP']  | ['CUUCP']               | ScannerStatus.ACTIVE    | ScannerStatus.ACTIVE  || 1
            ['DU']          | ['DU']                  | ScannerStatus.ACTIVE    | ScannerStatus.ACTIVE  || 1
            ['CUCP']        | ['DU']                  | ScannerStatus.ACTIVE    | ScannerStatus.INACTIVE|| 1
            ['DU', 'CUCP']  | ['CUUCP']               | ScannerStatus.ACTIVE    | ScannerStatus.INACTIVE|| 1
            ['DU']          | ['DU']                  | ScannerStatus.ACTIVE    | ScannerStatus.INACTIVE|| 1
    }

    def createEventsForEventProducers(eventProducerIds) {
        def events = []
        eventProducerIds.forEach { eventProducerId ->
            events.add(new EventInfo("${eventProducerId}TestEvent", 'group', eventProducerId))
        }
        events.toArray(new EventInfo[events.size()])
    }

    def 'When activated, subscription will stay in current state if the scanners already belong to this subscription'() {
        given: 'two nodes with available scanners attached to one subscription'
            setFileAndStreamingOutputAdditionalAttributes(cellTraceSubscriptionBuilder)
            def nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build()] as ManagedObject[]
            def subscriptionMo = createSubscription('Subscription1', nodes, AdministrationState.ACTIVATING, eventA)
            dpsUtils.addAssociation(subscriptionMo, 'nodes', nodes[0], nodes[1])
            createScanner(NODE_NAME_1, ScannerStatus.ACTIVE, subscriptionMo.poId)
            createScanner(NODE_NAME_2, ScannerStatus.ACTIVE, subscriptionMo.poId)

        when: 'Subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)
        
        then: 'Status will not be changed'
            ACTIVATING == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            OK == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        
        and: 'The node associations will be 2'
            2 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)
        
        and: 'Both nodes will be added to the tracker cache'
            testUtils.totalNodesToBeActivated == 2
    }

    def 'When activated, subscription will stay in current admin state if the scanners already belong to this subscription, EBS Stream Cluster is deployed'() {
        given: 'a node with 2 available scanners attached to one subscription'
            def node = nodeUtil.builder(NODE_NAME_1).build()
            def subscriptionMo = createSubscription('Subscription1', node, AdministrationState.ACTIVATING, eventA, [eventA, eventB] as EventInfo[], CellTraceCategory.CELLTRACE_AND_EBSL_STREAM)
            createScanner(NODE_NAME_1, ScannerStatus.ACTIVE, subscriptionMo.poId)
            createEbsScanner()

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)
        
        then: 'the Status will not be changed'
            ACTIVATING == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            OK == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        
        and: 'the node associations will be 1'
            1 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)
        
        and: 'a node will be added to the tracker cache with 2 notifications'
            2 == testUtils.totalNodesToBeActivated as Integer
    }

    def 'When activated, subscription will go ACTIVE/ERROR if nodes have scanners assigned to another subscription'() {
        given: 'two nodes with scanners attached to another subscription'
            def nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build()] as ManagedObject[]
            def subscriptionMo = createSubscription('Subscription1', nodes, AdministrationState.ACTIVATING, eventA)

            createScanner(NODE_NAME_1, ScannerStatus.INACTIVE, nonExistentSubId)
            createScanner(NODE_NAME_2, ScannerStatus.INACTIVE, nonExistentSubId)

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)
        
        then: 'the Status will be ACTIVE/ERROR'
            ACTIVE == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        
        and: 'the node associations will be 2'
            2 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)
        
        and: 'nothing will be added to the tracker cache'
            0 == testUtils.totalNodesToBeActivated as Integer
    }

    def 'When activated, subscription will go ACTIVATING/ERROR if nodes have scanners assigned to another subscription'() {
        given: 'a node with scanners attached to one subscription'
            def node = nodeUtil.builder(NODE_NAME_1).build()
            def subscriptionMo = createSubscription('Subscription1', node, AdministrationState.ACTIVATING, eventA, eventB, CellTraceCategory.CELLTRACE_AND_EBSL_STREAM)

            createScanner(NODE_NAME_1, ScannerStatus.ACTIVE, nonExistentSubId)
            createEbsScanner()

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)
        
        then: 'the Status will be ACTIVE/ERROR'
            ACTIVATING == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        
        and: 'the node associations will be 1'
            1 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)
        
        and: 'nothing will be added to the tracker cache'
            1 == testUtils.totalNodesToBeActivated as Integer
    }

    def 'When EBS Subscription is ACTIVATED when there is no other EBS Subscription ACTIVE then MTR should be sent'() {
        given: 'an EBS Subscription with node, ebsEvents and no events'
            def node = nodeUtil.builder(NODE_NAME_1).build()
            def subscriptionMo = createSubscription('Subscription1', node, AdministrationState.ACTIVATING, [] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.EBSL_STREAM)

            createEbsScanner(NODE_NAME_1, ScannerStatus.INACTIVE)

        when: 'subscription1 is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the Status will not be changed'
            ACTIVATING == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            OK == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String
        
        and: 'PMICSubScannerInfo should not be created for nodes with already activated events'
           pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node, subscriptionMo)) == null
        
        and: 'the Initiation Tracker Cache should not be empty'
            with(testUtils.getTrackerEntry(subscriptionMo.poId as String)) {
                subscriptionId == subscriptionMo.poId as String
                subscriptionAdministrationState == AdministrationState.ACTIVATING.name()
                totalAmountOfExpectedNotifications == 1
            }
        
        and: 'the Subscription Initiation Cache should be empty'
            subscriptionInitiationCacheWrapper.allEntries.empty
        
        and: 'one Activation MediationTaskRequest should be sent'
            1 * eventSender.send({it.subscriptionId == subscriptionMo.poId as String && it.nodeAddress == node.fdn} as MediationTaskRequest)
    }

    def 'When Two EBS Subscription with same Nodes and same Events then MTR should not be sent and PMICSubscannerInfo are created'() {
        given: 'subscription1 is already active and Subscription2 is activting on same nodes for same events'
            def nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build()] as ManagedObject[]
            def subscriptionMo1 = createSubscription('Subscription1', nodes, AdministrationState.ACTIVE, [] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.EBSL_STREAM)
            def subscriptionMo = createSubscription('Subscription2', nodes, AdministrationState.ACTIVATING, [] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.EBSL_STREAM)

            createSubScanner(createEbsScanner(), subscriptionMo1)
            createSubScanner(createEbsScanner(NODE_NAME_2), subscriptionMo1)

        when: 'subscription2 is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the status will be changed'
            ACTIVE == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            OK == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String
        
        and: 'PMICSubScannerInfo should be created for nodes with already activated events'
            with(pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodes[0], subscriptionMo))) {
                name == subscriptionMo.name
                subscriptionId == subscriptionMo.poId
            }
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodes[0], subscriptionMo1)).subscriptionId == subscriptionMo1.poId
            with(pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodes[1], subscriptionMo))) {
                name == subscriptionMo.name
                subscriptionId == subscriptionMo.poId
            }
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodes[1], subscriptionMo1)).subscriptionId == subscriptionMo1.poId

        and: 'the Initiation Tracker Cache should be empty'
            0L == testUtils.totalNodesToBeActivated
        
        and: 'the Subscription Initiation Cache should be empty'
            subscriptionInitiationCacheWrapper.allEntries.empty
        
        and: 'no Activation Mediation Task Request should be sent'
            0 * eventSender.send(_)
    }

    def 'When Two EBS Subscription have common Events and one Node in common, Subscription1 is ACTIVE but Subscription2 has uncommon node then MTR should be send for uncommon node'() {
        given: 'Subscription1 and Subscription2 have one Node common and both Subscription Events are the same'
            def nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build()] as ManagedObject[]
            def subscriptionMo1 = createSubscription('Subscription1', nodes[0], AdministrationState.ACTIVE, [] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.EBSL_STREAM)
            def subscriptionMo = createSubscription('Subscription2', nodes, AdministrationState.ACTIVATING, [] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.EBSL_STREAM)

            createSubScanner(createEbsScanner(NODE_NAME_1, ScannerStatus.INACTIVE), subscriptionMo1)
            createSubScanner(createEbsScanner(NODE_NAME_2, ScannerStatus.INACTIVE), subscriptionMo1)

        when: 'CellTraceSubscription Subscription2 is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'Status will not be changed'
            ACTIVATING == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            OK == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String
        
        and: 'should create PMICSubScannerInfo for nodes with already activated events'
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodes[0], subscriptionMo1)).subscriptionId == subscriptionMo1.poId
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodes[1], subscriptionMo1)).subscriptionId == subscriptionMo1.poId
            with(pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodes[0], subscriptionMo))) {
                name == subscriptionMo.name
                subscriptionId == subscriptionMo.poId
            }

        and: 'should not create PMICSubScannerInfo for node with not already activated events'
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodes[1], subscriptionMo)) == null

        and: 'Initiation Tracker Cache should not be empty'
            def trackerEntry = testUtils.getTrackerEntry(subscriptionMo.poId as String)
            with(trackerEntry) {
                subscriptionId == subscriptionMo.poId as String
                subscriptionAdministrationState == AdministrationState.ACTIVATING.name()
                totalAmountOfExpectedNotifications == 1
            }
        
        and: 'Subscription Initiation Cache should be empty'
            subscriptionInitiationCacheWrapper.allEntries.empty
        
        and: 'One Activation MediationTaskRequest should be sent'
            1 * eventSender.send({it.subscriptionId == subscriptionMo.poId as String && it.nodeAddress == nodes[1].fdn} as MediationTaskRequest)
    }

    def 'When three EBS Subscription Subscription1 and Subscription2 have combine EbsEvents presents in Subscription3 then MTR should not be sent'() {
        given: 'subscription1 and subscription2 combined EbsEvents are present in Subscription3'
            def node = nodeUtil.builder(NODE_NAME_1).build()
            def subscriptionMo1 = createSubscription('Subscription1', node, AdministrationState.ACTIVE, [] as EventInfo[], eventA, CellTraceCategory.EBSL_STREAM)
            def subscriptionMo2 = createSubscription('Subscription2', node, AdministrationState.ACTIVE, [] as EventInfo[], eventB, CellTraceCategory.EBSL_STREAM)
            def  subscriptionMo = createSubscription('Subscription3', node, AdministrationState.ACTIVATING, [] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.EBSL_STREAM)

            def scanner = createEbsScanner()
            createSubScanner(scanner, subscriptionMo1)
            createSubScanner(scanner, subscriptionMo2)

        when: 'subscription3 is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the status will be changed'
            ACTIVE == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            OK == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String
        
        and: 'PMICSubScannerInfo should be created for nodes with already activated events'
            with(pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node, subscriptionMo))) {
                subscriptionId == subscriptionMo.poId 
                name == subscriptionMo.name
            }
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node, subscriptionMo1)).subscriptionId == subscriptionMo1.poId
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node, subscriptionMo2)).subscriptionId == subscriptionMo2.poId
        
        and: 'the Initiation Tracker Cache should be empty'
            0L == testUtils.totalNodesToBeActivated
        
        and: 'the subscription Initiation Cache should be empty'
            subscriptionInitiationCacheWrapper.allEntries.empty
        
        and: 'no Activation MediationTaskRequest should be sent'
            0 * eventSender.send(_ as MediationTaskRequest)
    }

    def 'When Two EBS Subscription with same Node and same Events, Subscription1 is ACTIVE-ERROR, Subscription2 is ACTIVATED then MTR should not be sent and PMICSubscannerInfo is created'() {
        given: 'subscription1 is already active and Subscription2 is activting on same nodes for same events'
            def node = nodeUtil.builder(NODE_NAME_1).build()
    
            def subscriptionMo1 = createSubscription('Subscription1', node, AdministrationState.ACTIVE, [] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.EBSL_STREAM)
            def subscriptionMo = createSubscription('Subscription2', node, AdministrationState.ACTIVATING, [] as EventInfo[], [eventA, eventB] as EventInfo[], CellTraceCategory.EBSL_STREAM)

            createSubScanner(createEbsScanner(), subscriptionMo1)

        when: 'subscription2 is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the status will be changed'
            ACTIVE == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            OK == subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String
        
        and: 'PMICSubScannerInfo are created for nodes with already activated events'
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node, subscriptionMo)).subscriptionId == subscriptionMo.poId
            pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(node, subscriptionMo1)).subscriptionId == subscriptionMo1.poId
        
        and: 'the Initiation Tracker Cache should not be empty'
            def trackerEntry = testUtils.getTrackerEntry(subscriptionMo.poId as String)
            trackerEntry == null
        
        and: 'the subscription Initiation Cache should be empty'
            subscriptionInitiationCacheWrapper.allEntries.empty
        
        and: 'no MediationTaskRequest should be sent'
            0 * eventSender.send(_ as MediationTaskRequest)
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

    def setFileAndStreamingOutputAdditionalAttributes(final SubscriptionBuilder subscriptionBuilder) {
        subscriptionBuilder.setAdditionalAttributes(['outputMode' : OutputModeType.FILE_AND_STREAMING.name()])
    }

    def buildPmicSubScannerInfoFdn(node, subscriptionUnderTest, scannerName = EBS_CELLTRACE_SCANNER) {
        pmSubScannerService.buildPmSubScannerFdn(node.fdn, scannerName, subscriptionUnderTest.name)
    }
}
