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

import static com.ericsson.oss.pmic.api.constants.ModelConstants.ScannerConstants.SCANNER_ATTR_SUBSCRIPTION_ID
import static com.ericsson.oss.pmic.cdi.test.util.Constants.ACTIVATING
import static com.ericsson.oss.pmic.cdi.test.util.Constants.EBS_CELLTRACE_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NETWORK_ELEMENT_1
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NETWORK_ELEMENT_2
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_1
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_2
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_10000_CELLTRACE_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_10001_CELLTRACE_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_10003_CELLTRACE_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE

import spock.lang.Unroll

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache

class CellTraceSubscriptionActivationTaskRequestFactorySpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    CellTraceSubscriptionActivationTaskRequestFactory cellTraceSubscriptionActivationTaskRequestFactory

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

    ManagedObject subscriptionMo
    ManagedObject nodeMo1, nodeMo2
    CellTraceSubscription subscription

    def events = [new EventInfo('INTERNAL_EVENT_ADMISSION_BLOCKING_STARTED', 'SESSION_ESTABLISHMENT_EVALUATION')] as EventInfo []
    def eventsWithEventProducer = [new EventInfo('INTERNAL_EVENT_ADMISSION_BLOCKING_STARTED', 'SESSION_ESTABLISHMENT_EVALUATION', 'DU'),
                                   new EventInfo('EXTERNAL_EVENT_ADMISSION_BLOCKING_STARTED', 'SESSION_ESTABLISHMENT_EVALUATION', 'CUUP')] as EventInfo []
    def ebsEvents = [new EventInfo('Ebs Event', 'Ebs Event Group')] as EventInfo []

    def 'createActivationTasks should return size 0 tasks when no nodes and should not add to cache'() {
        given:
            createNode()
            createCellTraceSubscription()

        when:
            def tasks = cellTraceSubscriptionActivationTaskRequestFactory.createMediationTaskRequests([], subscription, true)

        then:
            tasks == []
            pmicInitiationTrackerCache.allTrackers == []
    }

    def 'createActivationTasks should return 0 tasks when no events'() {
        given:
            createNode()
            createCellTraceSubscription([nodeMo1, nodeMo2] as ManagedObject[])

        when:
            def tasks = cellTraceSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.nodes, subscription, true)

        then:
            tasks == []
            pmicInitiationTrackerCache.allTrackers == []
    }

    def 'createActivationTasks should return size 1 tasks when there is 1 node with events'() {
        given:
            createNode()
            createCellTraceSubscription([nodeMo1] as ManagedObject[], events)
            scannerUtil.builder(PREDEF_10003_CELLTRACE_SCANNER, NODE_NAME_1).status(ScannerStatus.INACTIVE).subscriptionId(subscription.id).processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build()

        when:
            def tasks = cellTraceSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.nodes, subscription, true)

        then:
            tasks.size() == 1
            pmicInitiationTrackerCache.allTrackers.size() == 1
            pmicInitiationTrackerCache.getTracker(subscription.idAsString).totalAmountOfExpectedNotifications == 1
    }

    @Unroll
    def 'createActivationTasks should return size 2 tasks when there are 2 nodes'() {
        given:
            createNode(neConfigurationManagerState)
            createCellTraceSubscription([nodeMo1, nodeMo2] as ManagedObject[], events)
    
            scannerUtil.builder(PREDEF_10000_CELLTRACE_SCANNER, NODE_NAME_1).status(ScannerStatus.INACTIVE).subscriptionId('0').processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build()
            scannerUtil.builder(PREDEF_10001_CELLTRACE_SCANNER, NODE_NAME_1).status(ScannerStatus.INACTIVE).subscriptionId(subscription.id).processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build()
            scannerUtil.builder(PREDEF_10001_CELLTRACE_SCANNER, NODE_NAME_2).status(ScannerStatus.INACTIVE).subscriptionId(subscription.id).processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build()

        when:
            def tasks = cellTraceSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.nodes, subscription, true)

        then:
            tasks.size() == taskNumber
            if (taskNumber > 0) {
                tasks.collect { it.getNodeAddress() }.sort() == [NETWORK_ELEMENT_1, NETWORK_ELEMENT_2].sort()
            }

        and:
            if (trackDeactivatingCalls != 0) {
                pmicInitiationTrackerCache.allTrackers.size() == 1
                pmicInitiationTrackerCache.getTracker(subscription.idAsString).totalAmountOfExpectedNotifications == 2
                pmicInitiationTrackerCache.getTracker(subscription.idAsString).unprocessedNodesAndTypes == [(NETWORK_ELEMENT_1): 'ERBS', (NETWORK_ELEMENT_2): 'ERBS']
            } else {
                pmicInitiationTrackerCache.allTrackers.size() == 0
            }

        where:
            neConfigurationManagerState          || taskNumber | trackDeactivatingCalls
            NeConfigurationManagerState.ENABLED  || 2          | 1
            NeConfigurationManagerState.DISABLED || 0          | 0
    }

    def 'createActivationTasks should set subscription status to error when there are 2 nodes and events but no free scanner for one node and should add to cache'() {
        given:
            createNode()
            createCellTraceSubscription([nodeMo1, nodeMo2] as ManagedObject[], events)

            scannerUtil.builder(PREDEF_10000_CELLTRACE_SCANNER, NODE_NAME_1).status(ScannerStatus.INACTIVE).subscriptionId('0').processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build()
            scannerUtil.builder(PREDEF_10003_CELLTRACE_SCANNER, NODE_NAME_2).status(ScannerStatus.INACTIVE).subscriptionId('123567').processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build()

        when:
            def tasks = cellTraceSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.nodes, subscription, true)

        then:
            tasks.size() == 1
            tasks.collect { it.getNodeAddress() } == [NETWORK_ELEMENT_1]

            pmicInitiationTrackerCache.allTrackers.size() == 1
            pmicInitiationTrackerCache.getTracker(subscription.idAsString).totalAmountOfExpectedNotifications == 1
            pmicInitiationTrackerCache.getTracker(subscription.idAsString).unprocessedNodesAndTypes == [(NETWORK_ELEMENT_1): 'ERBS']

            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == TaskStatus.ERROR.name()
    }

    def 'createActivationTasks should return size 0 tasks when there are nodes and events but no free scanner on all nodes and should not add to cache'() {
        given:
            createNode()
            createCellTraceSubscription([nodeMo1, nodeMo2] as ManagedObject[], events)

            scannerUtil.builder(PREDEF_10000_CELLTRACE_SCANNER, NODE_NAME_1).status(ScannerStatus.ACTIVE).subscriptionId('0').processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build()
            scannerUtil.builder(PREDEF_10003_CELLTRACE_SCANNER, NODE_NAME_2).status(ScannerStatus.INACTIVE).subscriptionId('123567').processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build()

        when:
            def tasks = cellTraceSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.nodes, subscription, true)

        then:
            tasks.size() == 0
            pmicInitiationTrackerCache.allTrackers.size() == 0
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == TaskStatus.ERROR.name()
    }

    def 'createActivationTasks should return size 1 tasks when there are nodes and events and add to tracker cache for EBS-L File'() {
        given:
            createNode()
            createCellTraceSubscription([nodeMo1] as ManagedObject[], events, [] as EventInfo[], CellTraceCategory.CELLTRACE_AND_EBSL_FILE)

            def normalPriorityScanner = scannerUtil.builder(PREDEF_10000_CELLTRACE_SCANNER, NODE_NAME_1).status(ScannerStatus.INACTIVE).subscriptionId('0').processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build()
            def ebsScanner = scannerUtil.builder(EBS_CELLTRACE_SCANNER, NODE_NAME_1).status(ScannerStatus.INACTIVE).subscriptionId('0').processType(ProcessType.HIGH_PRIORITY_CELLTRACE).build()

        when:
            def tasks = cellTraceSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.nodes, subscription, true)

        then:
            tasks.size() == 1
            pmicInitiationTrackerCache.allTrackers.size() == 1
            normalPriorityScanner.getAttribute(SCANNER_ATTR_SUBSCRIPTION_ID) == subscription.idAsString
            ebsScanner.getAttribute(SCANNER_ATTR_SUBSCRIPTION_ID) == '0'
            pmicInitiationTrackerCache.getTracker(subscription.idAsString).totalAmountOfExpectedNotifications == 1
    }

    def 'createActivationTasks should return size 1 tasks when there is a node with ebsEvents and add to tracker cache for EBS-L Stream'() {
        given:
            createNode()
            createCellTraceSubscription([nodeMo1] as ManagedObject[], events, ebsEvents, CellTraceCategory.EBSL_STREAM)

            def normalPriorityScanner = scannerUtil.builder(PREDEF_10000_CELLTRACE_SCANNER, NODE_NAME_1).status(ScannerStatus.INACTIVE).subscriptionId('0').processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build()
            def ebsScanner = scannerUtil.builder(EBS_CELLTRACE_SCANNER, NODE_NAME_1).status(ScannerStatus.INACTIVE).subscriptionId('0').processType(ProcessType.HIGH_PRIORITY_CELLTRACE).build()

        when:
            def tasks = cellTraceSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.nodes, subscription, true)

        then:
            tasks.size() == 1
            pmicInitiationTrackerCache.allTrackers.size() == 1
            normalPriorityScanner.getAttribute(SCANNER_ATTR_SUBSCRIPTION_ID) == '0'
            ebsScanner.getAttribute(SCANNER_ATTR_SUBSCRIPTION_ID) == '0'
            pmicInitiationTrackerCache.getTracker(subscription.idAsString).totalAmountOfExpectedNotifications == 1
    }

    def 'createActivationTasks should return size 1 tasks when there are nodes and events and add to tracker cache for Cell Trace and EBS-L Stream'() {
        given:
            createNode()
            createCellTraceSubscription([nodeMo1] as ManagedObject[], events, ebsEvents, CellTraceCategory.CELLTRACE_AND_EBSL_STREAM)

            def normalPriorityScanner = scannerUtil.builder(PREDEF_10000_CELLTRACE_SCANNER, NODE_NAME_1).status(ScannerStatus.INACTIVE).subscriptionId('0').processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build()
            def ebsScanner = scannerUtil.builder(EBS_CELLTRACE_SCANNER, NODE_NAME_1).status(ScannerStatus.INACTIVE).subscriptionId('0').processType(ProcessType.HIGH_PRIORITY_CELLTRACE).build()


        when:
            def tasks = cellTraceSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.nodes, subscription, true)

        then:
            tasks.size() == 1
            pmicInitiationTrackerCache.allTrackers.size() == 1
            normalPriorityScanner.getAttribute(SCANNER_ATTR_SUBSCRIPTION_ID) == subscription.idAsString
            ebsScanner.getAttribute(SCANNER_ATTR_SUBSCRIPTION_ID) == '0'
            pmicInitiationTrackerCache.getTracker(subscription.idAsString).totalAmountOfExpectedNotifications == 2
    }

    @Unroll
    def 'createActivationTasks should return size 1 tasks when there is 1 node #normalScannerStatus and #ebsScannerStatus with events and only 1 scanner is available for Cell Trace and EBS-L Stream'() {
        given:
            createNode()
            createCellTraceSubscription([nodeMo1] as ManagedObject[], events, ebsEvents, CellTraceCategory.CELLTRACE_AND_EBSL_STREAM)

            def normalPriorityScanner = scannerUtil.builder(PREDEF_10000_CELLTRACE_SCANNER, NODE_NAME_1).status(normalScannerStatus).subscriptionId(normalScannerSubId).processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build()
            def ebsScanner = scannerUtil.builder(EBS_CELLTRACE_SCANNER, NODE_NAME_1).status(ebsScannerStatus).subscriptionId('0').processType(ProcessType.HIGH_PRIORITY_CELLTRACE).build()


        when:
            def tasks = cellTraceSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.nodes, subscription, true)

        then:
            tasks.size() == tasksNumber
            pmicInitiationTrackerCache.allTrackers.size() == trackerCacheSize
            normalPriorityScanner.getAttribute(SCANNER_ATTR_SUBSCRIPTION_ID) == normalSubId
            ebsScanner.getAttribute(SCANNER_ATTR_SUBSCRIPTION_ID) == '0'
            pmicInitiationTrackerCache.getTracker(subscription.idAsString).totalAmountOfExpectedNotifications == notificationsNumber
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == taskStatus
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == 'ACTIVATING'

        where:
            normalScannerStatus    | normalScannerSubId | ebsScannerStatus       | ebsScannerSubId || tasksNumber | notificationsNumber | trackerCacheSize | normalSubId | ebsSubId | taskStatus
            ScannerStatus.ACTIVE   | '123'              | ScannerStatus.INACTIVE | '0'             || 1           | 1                   | 1                | '123'       | '9'      | TaskStatus.ERROR.name()
            ScannerStatus.INACTIVE | '0'                | ScannerStatus.ACTIVE   | '123'           || 1           | 2                   | 1                | '9'         | '123'    | TaskStatus.OK.name()
            ScannerStatus.INACTIVE | '0'                | ScannerStatus.ERROR    | '9'             || 1           | 2                   | 1                | '9'         | '9'      | TaskStatus.OK.name()
            ScannerStatus.ERROR    | '9'                | ScannerStatus.INACTIVE | '0'             || 1           | 2                   | 1                | '9'         | '9'      | TaskStatus.OK.name()
            ScannerStatus.ERROR    | '9'                | ScannerStatus.ACTIVE   | '9'             || 1           | 2                   | 1                | '9'         | '9'      | TaskStatus.OK.name()
            ScannerStatus.ACTIVE   | '9'                | ScannerStatus.ACTIVE   | '9'             || 1           | 2                   | 1                | '9'         | '9'      | TaskStatus.OK.name()
            ScannerStatus.INACTIVE | '0'                | ScannerStatus.INACTIVE | '0'             || 1           | 2                   | 1                | '9'         | '9'      | TaskStatus.OK.name()
            ScannerStatus.ERROR    | '9'                | ScannerStatus.ERROR    | '9'             || 1           | 2                   | 1                | '9'         | '9'      | TaskStatus.OK.name()
    }

    @Unroll
    def 'createActivationTasks for cellTrace subscription with category CELLTRACE_NRAN when the node has scanners with event producers and without event producers'() {
        given: 'a celltrace subscription with 2 events associated to DU and CUUP event producers respectively'
            createNode()
            createCellTraceSubscription([nodeMo1] as ManagedObject[], eventsWithEventProducer, [] as EventInfo[], CellTraceCategory.CELLTRACE_NRAN)
            def scannerMos = createScanners(scannerStatusByScannerName, associatedSubscriptionId)

        when: 'createMediationTaskRequests is invoked'
            def tasks = cellTraceSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.nodes, subscription, true)

        then: 'Verify total mediation task request created for node'
            tasks.size() == expectedNumberOfTasks

        and: 'Verify size of the tracker cache'
            pmicInitiationTrackerCache.allTrackers.size() == expectedTrackerCacheSize

        and: 'Verify updated subscription Ids in the scanner MO'
            getSubscriptionIdsFromScannerMos(scannerMos) == updatedSubscriptionIdInScanner.collect { s -> s == 'subId' ? subscription.id.toString() : s }

        and: 'Verify there is one notification for every scanner to be activated'
            getExpectedNotifications() == expecedNumberOfNotifications

        and: 'Verify subscription task status (OK/ERROR) and administration state should be ACTIVATING'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == expectedTaskStatus
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == 'ACTIVATING'

        where:
            scannerStatusByScannerName                  | associatedSubscriptionId || expectedNumberOfTasks | expecedNumberOfNotifications | expectedTrackerCacheSize | expectedTaskStatus      | updatedSubscriptionIdInScanner
            ['PREDEF.10000.CELLTRACE'     : 'INACTIVE',
             'PREDEF.DU.10000.CELLTRACE'  : 'INACTIVE',
             'PREDEF.CUUP.10000.CELLTRACE': 'INACTIVE'] | '0'                      || 1                     | 2                            | 1                        | TaskStatus.OK.name()    | ['0', 'subId', 'subId']
            ['PREDEF.CUUP.10005.CELLTRACE': 'INACTIVE',
             'PREDEF.DU.10000.CELLTRACE'  : 'INACTIVE',
             'PREDEF.CUUP.10000.CELLTRACE': 'INACTIVE'] | '0'                      || 1                     | 2                            | 1                        | TaskStatus.OK.name()    | ['0', 'subId', 'subId']
            ['PREDEF.10000.CELLTRACE'     : 'INACTIVE',
             'PREDEF.CUUP.10000.CELLTRACE': 'INACTIVE'] | '0'                      || 1                     | 1                            | 1                        | TaskStatus.ERROR.name() | ['0', 'subId']
            ['PREDEF.DU.10000.CELLTRACE'  : 'INACTIVE',
             'PREDEF.CUUP.10000.CELLTRACE': 'INACTIVE'] | 'subId'                  || 1                     | 2                            | 1                        | TaskStatus.OK.name()    | ['subId', 'subId']
            ['PREDEF.DU.10000.CELLTRACE'  : 'ACTIVE',
             'PREDEF.CUUP.10000.CELLTRACE': 'ACTIVE']   | 'subId'                  || 1                     | 2                            | 1                        | TaskStatus.OK.name()    | ['subId', 'subId']
            ['PREDEF.DU.10000.CELLTRACE'  : 'INACTIVE',
             'PREDEF.CUUP.10000.CELLTRACE': 'INACTIVE'] | '123'                    || 0                     | 0                            | 0                        | TaskStatus.ERROR.name() | ['123', '123']
    }

    @Unroll
    def 'createActivationTasks for a Cell Trace NRAN subscription with EBS-N Streaming only'() {
        given: 'a celltrace ebs-n streaming subscription with ebs events for #eventProducersForEbsEvents event producers'
            createNode()
            createCellTraceSubscription([nodeMo1] as ManagedObject [], [] as EventInfo[], createEventsForEventProducers(eventProducersForEbsEvents), CellTraceCategory.NRAN_EBSN_STREAM)

        and: 'ebs streaming scanners for #eventProducersForScanners event producers'
            eventProducersForScanners.forEach { eventProducerId ->
                createScanner("PREDEF.${eventProducerId}.10004.CELLTRACE", nodeMo1.name, Subscription.UNKNOWN_SUBSCRIPTION_ID, ProcessType.HIGH_PRIORITY_CELLTRACE, ebsScannerStatus)
            }

        when: 'createMediationTaskRequests is invoked'
            def tasks = cellTraceSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.nodes, subscription, true)

        then: '1 task should be created and 1 entry in the tracker cache'
            tasks.size() == (eventProducersForScanners.empty ? 0 : 1)
            pmicInitiationTrackerCache.allTrackers.size() == (eventProducersForScanners.empty ? 0 : 1)
            tasks[0].every{task -> task.subscriptionId == subscriptionMo.poId as String && task.nodeAddress == nodeMo1.fdn}

        and: 'there is 1 expected notificaiton for each scanner'
            getExpectedNotifications() == expecedNumberOfNotifications

        and: 'the subscriptions task status and administration state is correct'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == expectedTaskStatus
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == ACTIVATING

        where:
            eventProducersForScanners   | eventProducersForEbsEvents  | ebsScannerStatus        || expecedNumberOfNotifications | expectedTaskStatus
            ['DU']                      | ['DU']                      | ScannerStatus.ACTIVE    || 1                            | TaskStatus.OK.name()
            ['CUCP']                    | ['CUCP']                    | ScannerStatus.ACTIVE    || 1                            | TaskStatus.OK.name()
            ['CUUP']                    | ['CUUP']                    | ScannerStatus.ACTIVE    || 1                            | TaskStatus.OK.name()
            ['DU', 'CUCP', 'CUUP']      | ['DU', 'CUCP', 'CUUP']      | ScannerStatus.ACTIVE    || 3                            | TaskStatus.OK.name()
            ['CUCP', 'CUUP']            | ['CUCP', 'CUUP']            | ScannerStatus.ACTIVE    || 2                            | TaskStatus.OK.name()
            []                          | ['CUCP', 'CUUP']            | ScannerStatus.ACTIVE    || 0                            | TaskStatus.ERROR.name()
            ['CUCP']                    | ['CUCP', 'CUUP']            | ScannerStatus.ACTIVE    || 1                            | TaskStatus.ERROR.name()
            ['DU']                      | ['DU']                      | ScannerStatus.INACTIVE  || 1                            | TaskStatus.OK.name()
            ['CUCP']                    | ['CUCP']                    | ScannerStatus.INACTIVE  || 1                            | TaskStatus.OK.name()
            ['CUUP']                    | ['CUUP']                    | ScannerStatus.INACTIVE  || 1                            | TaskStatus.OK.name()
            ['DU', 'CUCP', 'CUUP']      | ['DU', 'CUCP', 'CUUP']      | ScannerStatus.INACTIVE  || 3                            | TaskStatus.OK.name()
            ['CUCP', 'CUUP']            | ['CUCP', 'CUUP']            | ScannerStatus.INACTIVE  || 2                            | TaskStatus.OK.name()
            []                          | ['CUCP', 'CUUP']            | ScannerStatus.INACTIVE  || 0                            | TaskStatus.ERROR.name()
            ['CUCP']                    | ['CUCP', 'CUUP']            | ScannerStatus.INACTIVE  || 1                            | TaskStatus.ERROR.name()
    }

    @Unroll
    def 'createActivationTasks for a Cell Trace NRAN with EBS-N Streaming also'() {
        given: 'a celltrace ebs-n streaming subscription with ebs events for #eventProducersForEbsEvents event producers'
            createNode()
            createCellTraceSubscription([nodeMo1] as ManagedObject [], createEventsForEventProducers(eventProducersForEvents), createEventsForEventProducers(eventProducersForEbsEvents), CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM)

        and: 'ebs streaming scanners for #eventProducersForEbsScanners event producers and standard scanners for #eventProducersForScanners event producers'
            eventProducersForEbsScanners.forEach { eventProducerId ->
                createScanner("PREDEF.${eventProducerId}.10004.CELLTRACE", nodeMo1.name, Subscription.UNKNOWN_SUBSCRIPTION_ID, ProcessType.HIGH_PRIORITY_CELLTRACE, ebsScannerStatus)
            }
            eventProducersForScanners.forEach { eventProducerId ->
                createScanner("PREDEF.${eventProducerId}.10001.CELLTRACE", nodeMo1.name, subscriptionMo.poId)
            }

        when: 'createMediationTaskRequests is invoked'
            def tasks = cellTraceSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.nodes, subscription, true)

        then: '1 task should be created and 1 entry in the tracker cache'
            tasks.size() == (eventProducersForScanners.empty && eventProducersForEbsScanners.empty ? 0 : 1)
            pmicInitiationTrackerCache.allTrackers.size() == (eventProducersForScanners.empty && eventProducersForEbsScanners.empty ? 0 : 1)
            tasks.every{task -> task.subscriptionId == subscriptionMo.poId as String && task.nodeAddress == nodeMo1.fdn}

        and: 'there is 1 expected notificaiton for each scanner'
            getExpectedNotifications() == expecedNumberOfNotifications

        and: 'the subscriptions task status and administration state is correct'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == expectedTaskStatus
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == ACTIVATING

        where:
            eventProducersForEbsScanners    | eventProducersForEbsEvents  | eventProducersForScanners   | eventProducersForEvents   | ebsScannerStatus        || expecedNumberOfNotifications | expectedTaskStatus
            ['DU']                          | ['DU']                      | ['DU']                      | ['DU']                    | ScannerStatus.ACTIVE    || 2                            | TaskStatus.OK.name()
            ['CUCP']                        | ['CUCP']                    | ['DU']                      | ['DU']                    | ScannerStatus.ACTIVE    || 2                            | TaskStatus.OK.name()
            ['CUUP']                        | ['CUUP']                    | ['DU']                      | ['DU']                    | ScannerStatus.ACTIVE    || 2                            | TaskStatus.OK.name()
            ['DU', 'CUCP', 'CUUP']          | ['DU', 'CUCP', 'CUUP']      | ['DU']                      | ['DU']                    | ScannerStatus.ACTIVE    || 4                            | TaskStatus.OK.name()
            ['CUCP', 'CUUP']                | ['CUCP', 'CUUP']            | ['DU']                      | ['DU']                    | ScannerStatus.ACTIVE    || 3                            | TaskStatus.OK.name()
            []                              | ['CUCP', 'CUUP']            | ['DU']                      | ['DU']                    | ScannerStatus.ACTIVE    || 1                            | TaskStatus.ERROR.name()
            ['DU']                          | ['DU']                      | ['DU']                      | ['DU']                    | ScannerStatus.INACTIVE  || 2                            | TaskStatus.OK.name()
            ['CUCP']                        | ['CUCP']                    | ['DU']                      | ['DU']                    | ScannerStatus.INACTIVE  || 2                            | TaskStatus.OK.name()
            ['CUUP']                        | ['CUUP']                    | ['DU']                      | ['DU']                    | ScannerStatus.INACTIVE  || 2                            | TaskStatus.OK.name()
            ['DU', 'CUCP', 'CUUP']          | ['DU', 'CUCP', 'CUUP']      | ['DU']                      | ['DU']                    | ScannerStatus.INACTIVE  || 4                            | TaskStatus.OK.name()
            ['CUCP', 'CUUP']                | ['CUCP', 'CUUP']            | ['DU']                      | ['DU']                    | ScannerStatus.INACTIVE  || 3                            | TaskStatus.OK.name()
            []                              | ['CUCP', 'CUUP']            | ['DU']                      | ['DU']                    | ScannerStatus.INACTIVE  || 1                            | TaskStatus.ERROR.name()
            ['DU']                          | ['DU']                      | ['CUCP']                    | ['CUCP']                  | ScannerStatus.ACTIVE    || 2                            | TaskStatus.OK.name()
            ['DU']                          | ['DU']                      | ['CUUP']                    | ['CUUP']                  | ScannerStatus.ACTIVE    || 2                            | TaskStatus.OK.name()
            ['DU']                          | ['DU']                      | ['DU', 'CUCP', 'CUUP']      | ['DU', 'CUCP', 'CUUP']    | ScannerStatus.ACTIVE    || 4                            | TaskStatus.OK.name()
            ['DU']                          | ['DU']                      | ['CUCP', 'CUUP']            | ['CUCP', 'CUUP']          | ScannerStatus.ACTIVE    || 3                            | TaskStatus.OK.name()
            ['DU']                          | ['DU']                      | []                          | ['CUCP', 'CUUP']          | ScannerStatus.ACTIVE    || 1                            | TaskStatus.ERROR.name()
            []                              | ['DU']                      | []                          | ['DU']                    | ScannerStatus.INACTIVE  || 0                            | TaskStatus.ERROR.name()
            []                              | ['DU']                      | []                          | ['DU']                    | ScannerStatus.ACTIVE    || 0                            | TaskStatus.ERROR.name()
    }

    def createEventsForEventProducers(eventProducerIds) {
        def events = []
        eventProducerIds.forEach { eventProducerId ->
            events.add(new EventInfo("${eventProducerId}TestEvent", 'group', eventProducerId))
        }
        events.toArray(new EventInfo[events.size()])
    }

    @Unroll
    def 'createActivationTasks for cellTrace subscription with category CELLTRACE when the node has scanners with event producers and without event producers'() {
        given: 'a celltrace subscription with 2 events'
            createNode()
            createCellTraceSubscription([nodeMo1] as ManagedObject[], events)
            def scannerMos = createScanners(scannerStatusByScannerName, associatedSubscriptionId)

        when: 'createMediationTaskRequests is invoked'
            def tasks = cellTraceSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.nodes, subscription, true)

        then: 'Verify total mediation task request created for node'
            tasks.size() == expectedNumberOfTasks

        and: 'Verify size of the tracker cache'
            pmicInitiationTrackerCache.allTrackers.size() == expectedTrackerCacheSize

        and: 'Verify updated subscription Ids in the scanner MO'
            getSubscriptionIdsFromScannerMos(scannerMos) == updatedSubscriptionIdInScanner.collect { s -> s == 'subId' ? subscription.id.toString() : s }

        and: 'Verify there is one notification for every scanner to be activated'
            getExpectedNotifications() == expecedNumberOfNotifications

        and: 'Verify subscription task status (OK/ERROR) and administration state should be ACTIVATING'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == expectedTaskStatus
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == 'ACTIVATING'

        where:
            scannerStatusByScannerName                  | associatedSubscriptionId || expectedNumberOfTasks | expecedNumberOfNotifications | expectedTrackerCacheSize | expectedTaskStatus      | updatedSubscriptionIdInScanner
            ['PREDEF.10000.CELLTRACE'     : 'INACTIVE',
             'PREDEF.10003.CELLTRACE'     : 'INACTIVE',
             'PREDEF.CUUP.10000.CELLTRACE': 'INACTIVE'] | '0'                      || 1                     | 1                            | 1                        | TaskStatus.OK.name()    | ['subId', '0', '0']
            ['PREDEF.10000.CELLTRACE'     : 'ACTIVE',
             'PREDEF.CUUP.10005.CELLTRACE': 'INACTIVE',
             'PREDEF.10003.CELLTRACE'     : 'INACTIVE'] | '0'                      || 1                     | 1                            | 1                        | TaskStatus.OK.name()    | ['0', '0', 'subId']
            ['PREDEF.10000.CELLTRACE': 'INACTIVE',
             'PREDEF.10005.CELLTRACE': 'INACTIVE']      | '0'                      || 1                     | 1                            | 1                        | TaskStatus.OK.name()    | ['subId', '0']
            ['PREDEF.10000.CELLTRACE'     : 'INACTIVE',
             'PREDEF.CUUP.10000.CELLTRACE': 'INACTIVE'] | 'subId'                  || 1                     | 1                            | 1                        | TaskStatus.OK.name()    | ['subId', 'subId']
            ['PREDEF.DU.10000.CELLTRACE': 'ACTIVE',
             'PREDEF.10003.CELLTRACE'   : 'ACTIVE']     | 'subId'                  || 1                     | 1                            | 1                        | TaskStatus.OK.name()    | ['subId', 'subId']
            ['PREDEF.10003.CELLTRACE'     : 'INACTIVE',
             'PREDEF.CUUP.10000.CELLTRACE': 'INACTIVE'] | '123'                    || 0                     | 0                            | 0                        | TaskStatus.ERROR.name() | ['123', '123']
    }

    def createNode(NeConfigurationManagerState neConfigurationManagerState = NeConfigurationManagerState.ENABLED) {
        nodeMo1 = nodeUtil.builder(NODE_NAME_1).neConfigurationManagerState(neConfigurationManagerState).build()
        nodeMo2 = nodeUtil.builder(NODE_NAME_2).neConfigurationManagerState(neConfigurationManagerState).build()
    }

    def createCellTraceSubscription(nodes = [] as ManagedObject[], events = [] as EventInfo[], ebsEvents = [] as EventInfo[], cellTraceCategory = CellTraceCategory.CELLTRACE) {
        subscriptionMo = cellTraceSubscriptionBuilder.outputMode(OutputModeType.FILE.name())
                                                     .ebsEvents(ebsEvents)
                                                     .cellTraceCategory(cellTraceCategory)
                                                     .name('AthloneArea')
                                                     .events(events)
                                                     .administrativeState(AdministrationState.ACTIVATING)
                                                     .build()
        dpsUtils.addAssociation(subscriptionMo, 'nodes', nodes)
        subscription = subscriptionDao.findOneById(subscriptionMo.poId, true) as CellTraceSubscription
    }

    List<ManagedObject> createScanners(Map scannerToScannerStatusMap, String associatedSubscriptionId) {
        List<ManagedObject> scannerMos = []
        scannerToScannerStatusMap.each { scannerName, scannerStatus ->
            scannerMos.add(scannerUtil.builder(scannerName, NODE_NAME_1).status(scannerStatus)
                    .subscriptionId(associatedSubscriptionId == 'subId' ? subscription.id.toString() : associatedSubscriptionId).processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build())
        }
        scannerMos
    }

    def getSubscriptionIdsFromScannerMos(List<ManagedObject> scannerMos) {
        List scannerSubscriptionIds = []
        scannerMos.each { scannerMo -> scannerSubscriptionIds.add(scannerMo.getAttribute(SCANNER_ATTR_SUBSCRIPTION_ID)) }
        scannerSubscriptionIds
    }

    def getExpectedNotifications() {
        int expectedNotifications = 0
        if (pmicInitiationTrackerCache.getTracker(subscription.idAsString) != null)
            expectedNotifications = pmicInitiationTrackerCache.getTracker(subscription.idAsString).totalAmountOfExpectedNotifications
        expectedNotifications
    }

    def createScanner(scannerName, nodeName, subscriptionId, scannerType = ProcessType.NORMAL_PRIORITY_CELLTRACE, scannerStatus = ScannerStatus.INACTIVE) {
        dps.scanner()
           .nodeName(nodeName)
           .name(scannerName)
           .processType(scannerType)
           .subscriptionId(subscriptionId)
           .status(scannerStatus)
           .build()
    }
}
