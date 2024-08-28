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

package com.ericsson.oss.services.pm.initiation.schedulers

import static com.ericsson.oss.pmic.cdi.test.util.Constants.EBS_CELLTRACE_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_1
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_2
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_10000_CELLTRACE_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_10001_CELLTRACE_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.HIGH_PRIORITY_CELLTRACE
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.NORMAL_PRIORITY_CELLTRACE

import spock.lang.Unroll

import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.dao.ScannerDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.services.pm.generic.PmSubScannerService
import com.ericsson.oss.services.pm.initiation.tasks.ScannerResumptionTaskRequest
import com.ericsson.oss.services.pm.initiation.tasks.ScannerSuspensionTaskRequest
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionActivationTaskRequest
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionDeactivationTaskRequest
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionSelectiveDeactivationTaskRequest
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService
import com.ericsson.oss.services.pm.time.TimeGenerator

/**
 * Test class for validating functionality of subscription auditor for Cell Trace subscription types.
 */
class CellTraceSubscriptionAuditorSpec extends AuditorParentSpec {

    public static final boolean IS_ACTIVE = true

    @ObjectUnderTest
    SubscriptionAuditorTimer subscriptionAuditorTimer
    @MockedImplementation
    TimerService timerService
    @MockedImplementation
    TimeGenerator timeGenerator
    @MockedImplementation
    TimeGenerator timer
    @MockedImplementation
    MembershipListener listener
    @Inject
    EventSender<MediationTaskRequest> eventSender

    @Inject
    private PmSubScannerService subScannerService

    @Inject
    ScannerDao scannerDao

    @Inject
    SubscriptionDao subscriptionDao

    @Inject
    SubscriptionReadOperationService subscriptionReadOperationService

    def eventsForLran = [new EventInfo('INTERNAL_EVENT_ADMISSION_BLOCKING_STARTED', 'SESSION_ESTABLISHMENT_EVALUATION', 'Lrat'),
                         new EventInfo('INTERNAL_EVENT_ADMISSION_BLOCKING_STARTED', 'SESSION_ESTABLISHMENT_EVALUATION', 'VTFrat'),
                         new EventInfo('INTERNAL_EVENT_ADMISSION_BLOCKING_STARTED', 'SESSION_ESTABLISHMENT_EVALUATION', null)]

    def nranEventsDuAndCuup = [new EventInfo("INTERNAL_EVENT_ADMISSION_BLOCKING_STARTED", "SESSION_ESTABLISHMENT_EVALUATION", "DU"),
                               new EventInfo("EXTERNAL_EVENT_ADMISSION_BLOCKING_STARTED", "SESSION_ESTABLISHMENT_EVALUATION", "CUUP")]

    def nranEbsCountersDuAndCuup = [new CounterInfo("DU_EbsCounter", "SESSION_ESTABLISHMENT_EVALUATION"),
                                    new CounterInfo("CUUP_EbsCounter", "SESSION_ESTABLISHMENT_EVALUATION")]

    def setup() {
        listener.isMaster() >> true
        timer.currentTimeMillis() >> System.currentTimeMillis()
    }

    /*
    Cell Trace (LRAN) / LEGACY NODES RadioNode and ERBS
     */

    @Unroll
    def 'Activation MTR sent for nodes with missing Scanners for Cell Trace (LRAN)'() {
        given: 'an active subscription with #totalNumberOfActiveScanners active scanners across 3 #neType nodes'
            def subscriptionMo = buildSubscription('Subscription', TaskStatus.ERROR, eventsForLran, SubscriptionType.CELLTRACE)
            (1..3).each { it ->
                def node = buildNode(NODE_NAME_1 + it, neType, technologyDomain)
                def activeScanner = it <= totalNumberOfActiveScanners
                buildScanner(node.name, 'PREDEF.1000' + it + '.CELLTRACE', subscriptionMo, activeScanner, NORMAL_PRIORITY_CELLTRACE)
                dpsUtils.addAssociation(subscriptionMo, 'nodes', node)
            }

        when: 'the subscription auditor runs'
            subscriptionAuditorTimer.onTimeout()

        then: '#expectedNumberOfMtrs subscription activation MTRs are sent for the subscription'
            expectedNumberOfMtrs * eventSender.send({ request -> request.subscriptionId == Long.toString(subscriptionMo.poId) } as SubscriptionActivationTaskRequest)
            0 * eventSender.send(_ as ScannerResumptionTaskRequest)
            0 * eventSender.send(_ as SubscriptionDeactivationTaskRequest)
            0 * eventSender.send(_ as ScannerSuspensionTaskRequest)

        and: '3 scanners are associated with the subscription'
            3 == findTotalNumberOfScannersForSubscription(subscriptionMo.poId)

        where:
            neType      | technologyDomain | totalNumberOfActiveScanners || expectedNumberOfMtrs
            'RadioNode' | ['EPS']          | 1                           || 2
            'ERBS'      | ['EPS']          | 2                           || 1
    }

    @Unroll
    def 'Deactivation MTR sent for nodes with duplicate Scanners for Cell Trace (LRAN)'() {
        given: 'an active subscription with #totalNumberOfActiveScanners active scanners across 3 #neType nodes'
            def subscriptionMo = buildSubscription('Subscription', TaskStatus.ERROR, eventsForLran, SubscriptionType.CELLTRACE)
            (0..2).each { it ->
                def node = buildNode(NODE_NAME_1 + it, neType, technologyDomain)
                def duplicateScanner = it < totalNumberOfDuplicateScanners
                buildScanner(node.name, 'PREDEF.1000' + it + '.CELLTRACE', subscriptionMo, IS_ACTIVE, NORMAL_PRIORITY_CELLTRACE)
                buildScanner(node.name, 'PREDEF.1000' + (it + 1) + '.CELLTRACE', subscriptionMo, duplicateScanner, NORMAL_PRIORITY_CELLTRACE)
                dpsUtils.addAssociation(subscriptionMo, 'nodes', node)
            }

        when: 'the subscription auditor runs'
            subscriptionAuditorTimer.onTimeout()

        then: '#expectedNumberOfMtrs subscription deactivation MTRs are sent for the subscription'
            expectedNumberOfMtrs * eventSender.send({ request -> request.subscriptionId == Long.toString(subscriptionMo.poId) } as SubscriptionDeactivationTaskRequest)
            0 * eventSender.send(_ as ScannerResumptionTaskRequest)
            0 * eventSender.send(_ as SubscriptionActivationTaskRequest)
            0 * eventSender.send(_ as ScannerSuspensionTaskRequest)

        and: '#expectedAssociatedScanners scanners are associated with the subscription'
            expectedAssociatedScanners == findTotalNumberOfScannersForSubscription(subscriptionMo.poId)

        where:
            neType      | technologyDomain | totalNumberOfDuplicateScanners || expectedNumberOfMtrs | expectedAssociatedScanners
            'RadioNode' | ['EPS']          | 1                              || 1                    | 4
            'ERBS'      | ['EPS']          | 2                              || 2                    | 5
    }

    @Unroll
    def 'Task status set for nodes with missing or duplicate scanners for Cell Trace (LRAN)'() {
        given: 'an active subscription with 2 nodes'
            def subscriptionMo = buildSubscription('Subscription', TaskStatus.OK, eventsForLran, SubscriptionType.CELLTRACE)
            def nodeA = buildNode(NODE_NAME_1, 'RadioNode', ['EPS'])
            def nodeB = buildNode(NODE_NAME_2, 'ERBS', ['EPS'])
            dpsUtils.addAssociation(subscriptionMo, 'nodes', nodeA, nodeB)

        and: 'the subscription is associated with #duplicateScanner duplicate scanners on 1 node'
            buildScanner(nodeA.name, 'PREDEF.10003.CELLTRACE', firstScannerStatus, Long.valueOf(subscriptionMo.poId), NORMAL_PRIORITY_CELLTRACE)
            if (duplicateScanner == 1) {
                buildScanner(nodeA.name, 'PREDEF.10002.CELLTRACE', subscriptionMo, IS_ACTIVE, NORMAL_PRIORITY_CELLTRACE)
            }

        and: 'the subscription is missing #missingScanner scanners for 1 node'
            def subIdForScannerOnNodeB = missingScanner == 1 ? Subscription.UNKNOWN_SUBSCRIPTION_ID : Long.valueOf(subscriptionMo.poId)
            buildScanner(nodeB.name, 'PREDEF.10003.CELLTRACE', missingScannerStatus, subIdForScannerOnNodeB, NORMAL_PRIORITY_CELLTRACE)

        when: 'the subscription auditor runs'
            subscriptionAuditorTimer.onTimeout()

        then: '#activationMtrs subscription activation MTRs are sent for the subscription'
            activationMtrs * eventSender.send({ request -> request.subscriptionId == Long.toString(subscriptionMo.poId) } as SubscriptionActivationTaskRequest)

        and: '#duplicateScanner subscription deactivation MTRs are sent for the subscription'
            duplicateScanner * eventSender.send({ request -> request.subscriptionId == Long.toString(subscriptionMo.poId) } as SubscriptionDeactivationTaskRequest)

        and: '#expectedAssociatedScanners scanners are associated with the subscription'
            expectedAssociatedScanners == findTotalNumberOfScannersForSubscription(subscriptionMo.poId)

        where:
            duplicateScanner | missingScanner | missingScannerStatus   | firstScannerStatus     || expectedAssociatedScanners | activationMtrs
            0                | 0              | ScannerStatus.ACTIVE   | ScannerStatus.ACTIVE   || 2                          | 0
            0                | 0              | ScannerStatus.ACTIVE   | ScannerStatus.ACTIVE   || 2                          | 0
            0                | 0              | ScannerStatus.ACTIVE   | ScannerStatus.ERROR    || 2                          | 0
            0                | 1              | ScannerStatus.INACTIVE | ScannerStatus.INACTIVE || 2                          | 1
            0                | 1              | ScannerStatus.ACTIVE   | ScannerStatus.UNKNOWN  || 1                          | 0
            0                | 1              | ScannerStatus.ERROR    | ScannerStatus.ERROR    || 1                          | 0
            0                | 1              | ScannerStatus.UNKNOWN  | ScannerStatus.ACTIVE   || 2                          | 1
            1                | 0              | ScannerStatus.ACTIVE   | ScannerStatus.ACTIVE   || 3                          | 0
            1                | 0              | ScannerStatus.ACTIVE   | ScannerStatus.ACTIVE   || 3                          | 0
    }

    @Unroll
    def 'Activation sent for Cell Trace (LRAN) with cellTraceCategory=CELLTRACE active on 10005 scanner'() {
        given: 'an active subscription with 1 active scanner on 1 #neType node'
            def subscriptionMo = buildSubscription('Subscription', TaskStatus.OK, eventsForLran, SubscriptionType.CELLTRACE)
            def node = buildNode(NODE_NAME_1, neType, technologyDomain)
            buildScanner(node.name, 'PREDEF.10005.CELLTRACE', subscriptionMo, IS_ACTIVE)
            buildScanner(node.name, 'PREDEF.10003.CELLTRACE', ScannerStatus.INACTIVE, Subscription.UNKNOWN_SUBSCRIPTION_ID, NORMAL_PRIORITY_CELLTRACE)
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)

        when: 'the subscription auditor runs'
            subscriptionAuditorTimer.onTimeout()

        then: '1 subscription activation MTRs are sent for the subscription'
            1 * eventSender.send({ request -> request.subscriptionId == Long.toString(subscriptionMo.poId) } as SubscriptionActivationTaskRequest)
            0 * eventSender.send(_ as ScannerResumptionTaskRequest)
            0 * eventSender.send(_ as SubscriptionDeactivationTaskRequest)
            0 * eventSender.send(_ as ScannerSuspensionTaskRequest)

        and: 'only 1 scanners associated with the subscription after audit runs'
            1 == findTotalNumberOfScannersForSubscription(subscriptionMo.poId)

        where:
            neType      | technologyDomain
            'RadioNode' | ['EPS']
            'ERBS'      | ['EPS']
    }

    @Unroll
    def 'Deactivation sent for Cell Trace (LRAN) with cellTraceCategory=CELLTRACE active on 10004 scanner'() {
        given: 'an active subscription with 1 active scanner across 1 #neType node'
            def subscriptionMo = buildSubscription('Subscription', TaskStatus.OK, eventsForLran, SubscriptionType.CELLTRACE)
            def node = buildNode(NODE_NAME_1, neType, technologyDomain)
            buildScanner(node.name, 'PREDEF.10004.CELLTRACE', ScannerStatus.ACTIVE)
            buildSubScanner(node, subscriptionMo)
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)

        when: 'the subscription auditor runs'
            subscriptionAuditorTimer.onTimeout()

        then: '1 subscription deactivation MTRs are sent for the subscription'
            1 * eventSender.send({ request -> request.subscriptionId == Long.toString(subscriptionMo.poId) } as SubscriptionSelectiveDeactivationTaskRequest)
            0 * eventSender.send(_ as ScannerResumptionTaskRequest)
            0 * eventSender.send(_ as SubscriptionActivationTaskRequest)
            0 * eventSender.send(_ as ScannerSuspensionTaskRequest)

        and: '1 scanner is associated with the subscription'
            1 == findTotalNumberOfScannersForSubscription(subscriptionMo.poId)

        and: 'the subscription task status is set to ERROR'

        where:
            neType      | technologyDomain
            'RadioNode' | ['EPS']
            'ERBS'      | ['EPS']
    }

    @Unroll
    def 'Subscription audit audits ASR subscription sends correct activation MTRs for nodes with missing scanners'() {
        given: 'a subscription with administrationState #adminState'
            def subscriptionMo = buildAsrSubscription(adminState, OperationalState.RUNNING)

        and: '#nodes nodes with pmEnabled=#pmEnabled, #missingScanners nodes with a missing scanner'
            (1..nodes).each { it ->
                def node = buildNode(NODE_NAME_1 + it, 'ERBS', ['EPS'], pmEnabled)
                buildScanner(node.name, EBS_CELLTRACE_SCANNER, ScannerStatus.ACTIVE)
                if (it > missingScanners) {
                    buildSubScanner(node, subscriptionMo)
                }
                dpsUtils.addAssociation(subscriptionMo, 'nodes', node)
            }

        when: 'the subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        then: 'the expected number of activation tasks are sent for the missing node(s)'
            tasks * eventSender.send(
                { request -> request.getSubscriptionId() == Long.toString(subscriptionMo.poId) } as SubscriptionActivationTaskRequest)

        where:
            tasks | nodes | missingScanners | pmEnabled | adminState
            1     | 2     | 1               | true      | AdministrationState.ACTIVE
            2     | 2     | 2               | true      | AdministrationState.ACTIVE
            100   | 234   | 100             | true      | AdministrationState.ACTIVE
            0     | 234   | 100             | true      | AdministrationState.INACTIVE //Not active so ignored
            0     | 234   | 100             | true      | AdministrationState.ACTIVATING //Not active so ignored
            0     | 10    | 6               | false     | AdministrationState.ACTIVE //PmFunction off
    }

    def 'No Deactivation MTRs are sent for nodes with normal and ebs scanners for CellTrace, CellTraceCategory.CELLTRACE_AND_EBSL_STREAM'() {
        given: 'an active Cell Trace Subscription with cellTraceCategory CELLTRACE_AND_EBSL_STREAM'
            def subscriptionMo = buildCellTraceEbslSubscription(AdministrationState.ACTIVE, OperationalState.RUNNING)

        and: 'a node with associated active scanners'
            def node = buildNode(NODE_NAME_1, 'ERBS', ['EPS'])
            buildScanner(node.name, PREDEF_10000_CELLTRACE_SCANNER, subscriptionMo, IS_ACTIVE, NORMAL_PRIORITY_CELLTRACE)
            buildScanner(node.name, EBS_CELLTRACE_SCANNER, ScannerStatus.ACTIVE)
            buildSubScanner(node, subscriptionMo)
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)

        when: 'the subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        then: 'no MTRs are sent'
            0 * eventSender.send(_)

        and: '2 scanners are associated with the subscription'
            2 == findTotalNumberOfScannersForSubscription(subscriptionMo.poId)
    }

    def 'No Activation MTRs are sent for nodes with ebs scanners only for CellTrace, CellTraceCategory.EBSL_STREAM'() {
        given: 'an active subscription'
            def subscriptionMo = buildEbslStreamOnlySubscription(AdministrationState.ACTIVE, OperationalState.RUNNING)

        and: 'an associated node with associated active ebs scanner'
            def node = buildNode(NODE_NAME_1, 'ERBS', ['EPS'])
            buildScanner(node.name, EBS_CELLTRACE_SCANNER, ScannerStatus.ACTIVE)
            buildSubScanner(node, subscriptionMo)
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)

        when: 'the subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        then: 'no MTRs are sent for the subscription'
            0 * eventSender.send(_)

        and: '1 scanner is associated with the subscription'
            1 == findTotalNumberOfScannersForSubscription(subscriptionMo.poId)
    }

    def 'Deactivation MTRs are sent for nodes with duplicate scanners for CellTrace, CellTraceCategory.CELLTRACE_AND_EBSL_STREAM'() {
        given: 'an active subscription'
            def subscriptionMo = buildCellTraceEbslSubscription(AdministrationState.ACTIVE, OperationalState.RUNNING)

        and: 'one node with one duplicate associated scanner'
            def node = buildNode(NODE_NAME_1, 'ERBS', ['EPS'])
            buildScanner(node.name, PREDEF_10000_CELLTRACE_SCANNER, ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
            buildScanner(node.name, PREDEF_10001_CELLTRACE_SCANNER, ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
            buildScanner(node.name, EBS_CELLTRACE_SCANNER, ScannerStatus.ACTIVE, Subscription.UNKNOWN_SUBSCRIPTION_ID)
            buildSubScanner(node, subscriptionMo)
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)

        when: 'the subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        then: '1 selective deactivation task request is sent for the node'
            1 * eventSender.send(
                { request -> request.subscriptionId == Long.toString(subscriptionMo.poId) } as SubscriptionSelectiveDeactivationTaskRequest)

        and: '3 scanners are associated with the subscription'
            3 == findTotalNumberOfScannersForSubscription(subscriptionMo.poId)
    }

    def 'Activation MTRs are sent for nodes with missing ebs scanner for CellTrace, CellTraceCategory.CELLTRACE_AND_EBSL_STREAM'() {
        given: 'an active subscription'
            def subscriptionMo = buildCellTraceEbslSubscription(AdministrationState.ACTIVE, OperationalState.RUNNING)

        and: 'one associated node with one missing scanner'
            def node = buildNode(NODE_NAME_1, 'ERBS', ['EPS'])
            buildScanner(node.name, PREDEF_10000_CELLTRACE_SCANNER, ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
            buildScanner(node.name, EBS_CELLTRACE_SCANNER, ScannerStatus.INACTIVE)
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)

        when: 'the subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        then: '1 activation MTR is sent for the node with missing scanner'
            1 * eventSender.send(
                { request -> request.getSubscriptionId() == Long.toString(subscriptionMo.poId) } as SubscriptionActivationTaskRequest)

        and: '1 scanner is associated with the subscription'
            1 == findTotalNumberOfScannersForSubscription(subscriptionMo.poId)
    }

    def 'No MTRs are sent for nodes with an INACTIVE ebs scanner and an ACTIVE subscription for CellTrace, CellTraceCategory.CELLTRACE_AND_EBSL_STREAM'() {
        given: 'an active subscription'
            def subscriptionMo = buildCellTraceEbslSubscription(AdministrationState.ACTIVE, OperationalState.RUNNING)

        and: 'one associated node with one INACTIVE ebs scanner and one ACTIVE Normal Priority Scanner'
            def node = buildNode(NODE_NAME_1, 'ERBS', ['EPS'])
            buildScanner(node.name, PREDEF_10000_CELLTRACE_SCANNER, ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
            buildScanner(node.name, EBS_CELLTRACE_SCANNER, ScannerStatus.INACTIVE)
            buildSubScanner(node, subscriptionMo)
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)

        when: 'the subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        then: 'No MTR is sent for the node'
            0 * eventSender.send(_)

        and: '2 scanners are associated with the subscription'
            2 == findTotalNumberOfScannersForSubscription(subscriptionMo.poId)

    }

    def 'Deactivation MTRs are sent for nodes with duplicate normal scanners and no high priority scanners for CellTrace, CellTraceCategory.CELLTRACE_AND_EBSL_STREAM'() {
        given: 'an active subscription'
            def subscriptionMo = buildCellTraceEbslSubscription(AdministrationState.ACTIVE, OperationalState.RUNNING)

        and: 'an associated node with duplicate normal priority scanner'
            def node = buildNode(NODE_NAME_1, 'ERBS', ['EPS'])
            buildScanner(node.name, PREDEF_10000_CELLTRACE_SCANNER, ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
            buildScanner(node.name, PREDEF_10001_CELLTRACE_SCANNER, ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)

        when: 'the subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        then: '1 deactivation MTR is sent for the node'
            1 * eventSender.send(
                { request -> request.getSubscriptionId() == Long.toString(subscriptionMo.poId) } as SubscriptionDeactivationTaskRequest)

        and: '2 scanners are associated with the subscription'
            2 == findTotalNumberOfScannersForSubscription(subscriptionMo.poId)
    }

    def 'Deactivation MTRs are sent for nodes with normal scanner and no high priority scanners for CellTrace, CellTraceCategory.EBSL_STREAM'() {
        given: 'an active subscription with one ERBS node with one duplicate scanner'
            def subscriptionMo = buildEbslStreamOnlySubscription(AdministrationState.ACTIVE, OperationalState.RUNNING)

        and: 'a node with an associated active scanner'
            def node = buildNode(NODE_NAME_1, 'ERBS', ['EPS'])
            buildScanner(node.name, PREDEF_10000_CELLTRACE_SCANNER, ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)

        when: "The subscription audit runs"
            subscriptionAuditorTimer.onTimeout()

        then: '1 deactivation MTR is sent for the duplicate scanner node for the subscription'
            1 * eventSender.send(
                { request -> request.getSubscriptionId() == Long.toString(subscriptionMo.poId) } as SubscriptionDeactivationTaskRequest)

        and: '1 scanner is associated with the subscription'
            1 == findTotalNumberOfScannersForSubscription(subscriptionMo.poId)
    }

    def 'Deactivation MTRs are sent for nodes with high priority scanner for CellTrace, CellTraceCategory.CELLTRACE'() {
        given: 'an active subscription'
            def subscriptionMo = buildSubscription('Subscription', TaskStatus.OK, eventsForLran, SubscriptionType.CELLTRACE)

        and: 'one ERBS node with one active duplicate scanner associated with the subscription'
            def node = buildNode(NODE_NAME_1, 'ERBS', ['EPS'])
            buildScanner(node.name, EBS_CELLTRACE_SCANNER, ScannerStatus.ACTIVE, Subscription.UNKNOWN_SUBSCRIPTION_ID, HIGH_PRIORITY_CELLTRACE)
            buildSubScanner(node, subscriptionMo)
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)

        when: 'the subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        then: '1 selective deactivation task request sent for the node for the subscription'
            1 * eventSender.send(
                { request -> request.getSubscriptionId() == Long.toString(subscriptionMo.poId) } as SubscriptionSelectiveDeactivationTaskRequest)

        and: '1 scanner associated with the subscription'
            1 == findTotalNumberOfScannersForSubscription(subscriptionMo.poId)
    }

    def 'Subscription id should be removed for scanner not included in subscription'() {
        given: 'an active subscription with 1 node'
            def subscriptionMo = buildSubscription('Subscription', TaskStatus.ERROR, eventsForLran, SubscriptionType.CELLTRACE)
            def nodeA = buildNode(NODE_NAME_1, neType, technologyDomain)

        and: 'two scanners associated with the subscription, with only one node associated'
            buildScanner(nodeA.name, 'PREDEF.10001.CELLTRACE', subscriptionMo, true, NORMAL_PRIORITY_CELLTRACE)
            dpsUtils.addAssociation(subscriptionMo, 'nodes', nodeA)
            def nodeB = buildNode(NODE_NAME_2, neType, technologyDomain)
            buildScanner(nodeB.name, 'PREDEF.10001.CELLTRACE', subscriptionMo, true, NORMAL_PRIORITY_CELLTRACE)

        when: 'the subscription auditor runs'
            subscriptionAuditorTimer.onTimeout()

        then: 'no MTR is sent'
            0 * eventSender.send(_ as SubscriptionSelectiveDeactivationTaskRequest)
            0 * eventSender.send(_ as ScannerResumptionTaskRequest)
            0 * eventSender.send(_ as ScannerSuspensionTaskRequest)

        and: '1 scanners are associated with the subscription'
            1 == findTotalNumberOfScannersForSubscription(subscriptionMo.poId)

        where:
            neType      | technologyDomain
            'RadioNode' | ['EPS']
            'ERBS'      | ['EPS']
    }

    /*
    Cell Trace (LRAN) / RadioNode[EPS] with multiple event producers
    */

    @Unroll
    def 'Deactivation MTR sent for nodes with duplicate Scanners for Cell Trace (LRAN) on RadioNode with multiple event producers'() {
        given: 'an active subscription'
            def subscriptionMo = buildSubscription('Subscription', TaskStatus.ERROR, eventsForLran, SubscriptionType.CELLTRACE)

        and: '3 nodes in the subscription with associated NRAN scanners that should not be active'
            (1..3).each { it ->
                def node = buildNode(NODE_NAME_1 + it, neType, technologyDomain)
                buildScanner(node.name, 'PREDEF.DU.1000' + it + '.CELLTRACE', ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
                dpsUtils.addAssociation(subscriptionMo, 'nodes', node)
            }

        when: 'the subscription auditor runs'
            subscriptionAuditorTimer.onTimeout()

        then: '3  subscription activation MTRs are sent for the subscription'
            3 * eventSender.send({ request -> request.subscriptionId == Long.toString(subscriptionMo.poId) } as SubscriptionDeactivationTaskRequest)
            0 * eventSender.send(_ as ScannerResumptionTaskRequest)
            0 * eventSender.send(_ as SubscriptionActivationTaskRequest)
            0 * eventSender.send(_ as ScannerSuspensionTaskRequest)

        and: '3 scanners are associated with the subscription'
            3 == findTotalNumberOfScannersForSubscription(subscriptionMo.poId)
        where:
            neType      | technologyDomain
            'RadioNode' | ['EPS']
            'ERBS'      | ['EPS']
    }

    /*
    Cell Trace (NRAN)
    */

    @Unroll
    def 'SubscriptionAuditor test for Celltrace NRAN : activation task should be sent when a CUUP scanner is missing'() {
        given: 'an active Cell Trace (NRAN) subscription with events for Event Producers CUUP and DU'
            def subscriptionMo = buildSubscription('Subscription', TaskStatus.ERROR, nranEventsDuAndCuup,
                SubscriptionType.CELLTRACE, CellTraceCategory.CELLTRACE_NRAN)

        and: 'an associated node with missing CUUP scanner for subscription'
            def node = buildNode(NODE_NAME_1, 'RadioNode', ['5GS'])
            buildScanner(node.name, 'PREDEF.DU.10003.CELLTRACE', ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
            buildScanner(node.name, 'PREDEF.CUUP.10003.CELLTRACE', ScannerStatus.INACTIVE, Subscription.UNKNOWN_SUBSCRIPTION_ID, NORMAL_PRIORITY_CELLTRACE)
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)

        when: 'the subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        and: 'delayed mediation task processing times out'
            timer.currentTimeMillis() >> System.currentTimeMillis() + 8000

        then: '1 activation MTR is sent for the subscription'
            1 * eventSender.send(
                { request -> request.subscriptionId == Long.toString(subscriptionMo.poId) } as SubscriptionActivationTaskRequest)
    }

    @Unroll
    def 'SubscriptionAuditor test for Celltrace NRAN : deactivation task should be sent when extra CUUP scanner is found'() {
        given: 'an active Cell Trace (NRAN) subscription with events for Event Producers CUUP and DU'
            def subscriptionMo = buildSubscription('Subscription', TaskStatus.ERROR, nranEventsDuAndCuup,
                SubscriptionType.CELLTRACE, CellTraceCategory.CELLTRACE_NRAN)

        and: 'an associated node with duplicate scanner on 1 event producer'
            def node = buildNode(NODE_NAME_1, 'RadioNode', ['5GS'])
            buildScanner(node.name, 'PREDEF.10003.CELLTRACE', ScannerStatus.ACTIVE, Subscription.UNKNOWN_SUBSCRIPTION_ID, NORMAL_PRIORITY_CELLTRACE)
            buildScanner(node.name, 'PREDEF.DU.10003.CELLTRACE', ScannerStatus.ACTIVE, Subscription.UNKNOWN_SUBSCRIPTION_ID, NORMAL_PRIORITY_CELLTRACE)
            buildScanner(node.name, 'PREDEF.CUUP.10003.CELLTRACE', ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
            buildScanner(node.name, 'PREDEF.CUUP.10002.CELLTRACE', ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)

        when: 'the subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        and: 'delayed mediation task processing times out'
            timer.currentTimeMillis() >> System.currentTimeMillis() + 8000

        then: '1 deactivation MTR sent for the node with duplicate scanner'
            1 * eventSender.send(
                { request -> request.getSubscriptionId() == Long.toString(subscriptionMo.poId) } as SubscriptionDeactivationTaskRequest)
    }

    @Unroll
    def 'SubscriptionAuditor test for Celltrace NRAN : deactivation MTR sent when active on LRAN scanners'() {
        given: 'an active Cell Trace (NRAN) subscription with events for Event Producers CUUP and DU'
            def subscriptionMo = buildSubscription('Subscription', TaskStatus.ERROR, nranEventsDuAndCuup,
                SubscriptionType.CELLTRACE, CellTraceCategory.CELLTRACE_NRAN)

        and: 'subscription contains one node with lran scanners active and no nran scanners assoicated'
            def node = buildNode(NODE_NAME_1, 'RadioNode', ['5GS'])
            buildScanner(node.name, 'PREDEF.10003.CELLTRACE', ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
            buildScanner(node.name, 'PREDEF.10002.CELLTRACE', ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
            buildScanner(node.name, 'PREDEF.DU.10003.CELLTRACE', ScannerStatus.INACTIVE, Subscription.UNKNOWN_SUBSCRIPTION_ID, NORMAL_PRIORITY_CELLTRACE)
            buildScanner(node.name, 'PREDEF.CUUP.10002.CELLTRACE', ScannerStatus.INACTIVE, Subscription.UNKNOWN_SUBSCRIPTION_ID, NORMAL_PRIORITY_CELLTRACE)
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)

        when: 'the subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        and: 'delayed mediation task processing times out'
            timer.currentTimeMillis() >> System.currentTimeMillis() + 8000

        then: '1 deactivation MTR sent for the node with incorrect scanner'
            1 * eventSender.send({ request -> request.subscriptionId == Long.toString(subscriptionMo.poId) } as SubscriptionDeactivationTaskRequest)
    }

    @Unroll
    def 'SubscriptionAuditor test for Celltrace NRAN : no activation or deactivation task sent when no missing or extra scanners for the subscription'() {
        given: 'an active Cell Trace (NRAN) subscription with events for Event Producers CUUP and DU'
            def subscriptionMo = buildSubscription('Subscription', TaskStatus.OK, nranEventsDuAndCuup,
                SubscriptionType.CELLTRACE, CellTraceCategory.CELLTRACE_NRAN)

        and: 'the subscription contains one node with all necessary scanners active and assoicated'
            def node = buildNode(NODE_NAME_1, 'RadioNode', ['5GS'])
            buildScanner(node.name, 'PREDEF.CUUP.10003.CELLTRACE', ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
            buildScanner(node.name, 'PREDEF.DU.10002.CELLTRACE', ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)

        when: 'the subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        and: 'delayed mediation task processing times out'
            timer.currentTimeMillis() >> System.currentTimeMillis() + 8000

        then: 'no MTRs are sent'
            0 * eventSender.send(_)
    }

    @Unroll
    def 'Cell Trace NRAN subscription marked as error if no scanners found in dps'() {
        given: 'an active Cell Trace (NRAN) subscription with events for Event Producers CUUP and DU'
            def subscriptionMo = buildSubscription('Subscription', TaskStatus.ERROR, nranEventsDuAndCuup,
                SubscriptionType.CELLTRACE, CellTraceCategory.CELLTRACE_NRAN)

        and: 'the subscription contains one node with no scanners in dps'
            def node = buildNode(NODE_NAME_1, 'RadioNode', ['5GS'])
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)

        when: 'the subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        and: 'delayed mediation task processing times out'
            timer.currentTimeMillis() >> System.currentTimeMillis() + 8000

        then: 'no MTRs are sent'
            0 * eventSender.send(_)
    }

    @Unroll
    def 'Cell Trace NRAN subscription is not marked as error if expected scanner not scanners found in dps'() {
        given: 'an active Cell Trace (NRAN) subscription with events for Event Producers CUUP and DU'
            def subscriptionMo = buildSubscription('Subscription', TaskStatus.OK, nranEventsDuAndCuup,
                SubscriptionType.CELLTRACE, CellTraceCategory.CELLTRACE_NRAN)

        and: 'the subscription contains one node with missing event producer scanners in dps for DU'
            def node = buildNode(NODE_NAME_1, 'RadioNode', ['5GS'])
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)
            buildScanner(node.name, 'PREDEF.CUUP.10003.CELLTRACE', ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)

        when: 'the subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        and: 'delayed mediation task processing times out'
            timer.currentTimeMillis() >> System.currentTimeMillis() + 8000

        then: 'no MTRs are sent'
            0 * eventSender.send(_)
    }

    /*
        Cell Trace (NRAN) & EBSN Stream
     */

    @Unroll
    def 'SubscriptionAuditor test for Celltrace NRAN & EBSN STREAM: #testCase task should be sent #condiction'() {
        given: 'an active Cell Trace (NRAN)/EBSN STREAM subscription with events for Event Producers  high priority 10004 DU'
            def subscriptionMo = buildCellTraceEbsnSubscription(AdministrationState.ACTIVE, OperationalState.RUNNING, cellTraceCategory, taskStatus)

        and: 'an associated node with scanners'
            createAssociationBetweenEbsnSubsciptionAndNode(subscriptionMo, cellTraceCategory, testCase)

        when: 'the subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        and: 'delayed mediation task processing times out'
            timer.currentTimeMillis() >> System.currentTimeMillis() + 8000

        then: '#numberOfMTR #testCase MTR is sent for the subscription'
            if (testCase == 'deactivation') {
                numberOfMTR * eventSender.send(
                    { request -> request.subscriptionId == Long.toString(subscriptionMo.poId) } as SubscriptionSelectiveDeactivationTaskRequest)
            } else if (testCase == 'activation') {
                numberOfMTR * eventSender.send(
                    { request -> request.subscriptionId == Long.toString(subscriptionMo.poId) } as SubscriptionActivationTaskRequest)
            } else {
                numberOfMTR * eventSender.send(_)
            }

        and: 'TaskStatue is set to OK'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == TaskStatus.OK.name()

        where:
            testCase       | condiction                                  | cellTraceCategory                                | taskStatus       | numberOfMTR
            'deactivation' | 'when extra 10004 CUCP scanner is existed ' | CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM | TaskStatus.OK    | 1
            'activation'   | 'when 10004 CUUP scanner is missing'        | CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM | TaskStatus.OK    | 1
            'non'          | 'when no scanner is missing or extra'       | CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM | TaskStatus.OK    | 0
            'non'          | 'when TaskStatue is error'                  | CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM | TaskStatus.ERROR | 0
            'deactivation' | 'when extra 10004 CUCP scanner is existed ' | CellTraceCategory.NRAN_EBSN_STREAM               | TaskStatus.OK    | 1
            'activation'   | 'when 10004 CUUP scanner is missing'        | CellTraceCategory.NRAN_EBSN_STREAM               | TaskStatus.OK    | 1
            'non'          | 'when no scanner is missing or extra'       | CellTraceCategory.NRAN_EBSN_STREAM               | TaskStatus.OK    | 0
            'non'          | 'when TaskStatue is error'                  | CellTraceCategory.NRAN_EBSN_STREAM               | TaskStatus.ERROR | 0
    }

    private void createAssociationBetweenEbsnSubsciptionAndNode(ManagedObject subscriptionMo, CellTraceCategory cellTraceCategory, String testCase) {
        def node = buildNode(NODE_NAME_1, 'RadioNode', ['5GS'])
        if (cellTraceCategory == CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM) {
            buildScanner(node.name, 'PREDEF.CUUP.10003.CELLTRACE', ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
            buildScanner(node.name, 'PREDEF.DU.10002.CELLTRACE', ScannerStatus.ACTIVE, subscriptionMo.poId, NORMAL_PRIORITY_CELLTRACE)
        }
        buildScanner(node.name, 'PREDEF.DU.10004.CELLTRACE', ScannerStatus.ACTIVE, Subscription.UNKNOWN_SUBSCRIPTION_ID, HIGH_PRIORITY_CELLTRACE)
        buildScanner(node.name, 'PREDEF.CUUP.10004.CELLTRACE', ScannerStatus.ACTIVE, Subscription.UNKNOWN_SUBSCRIPTION_ID, HIGH_PRIORITY_CELLTRACE)
        buildSubScannerForMultipleEventProducerIds(node, subscriptionMo, 'DU')
        if (testCase.equals('activation')) {
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)
        } else if (testCase.equals('deactivation')) {
            buildScanner(node.name, 'PREDEF.CUCP.10004.CELLTRACE', ScannerStatus.ACTIVE, Subscription.UNKNOWN_SUBSCRIPTION_ID, HIGH_PRIORITY_CELLTRACE)
            buildSubScannerForMultipleEventProducerIds(node, subscriptionMo, 'CUUP')
            buildSubScannerForMultipleEventProducerIds(node, subscriptionMo, 'CUCP')
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)
        } else {
            buildSubScannerForMultipleEventProducerIds(node, subscriptionMo, 'CUUP')
            dpsUtils.addAssociation(subscriptionMo, 'nodes', node)
        }
    }

    /*
        ASR subscription
     */

    @Unroll
    def 'Subscription audit audits ASR subscription, sends Activation events for nodes with missing scanners, #tasks and #adminState'() {
        listener.isMaster() >> true
        given: 'A subscription with two nodes, one with a missing scanner'
            def subscriptionMO = buildAsrSubscription(adminState, OperationalState.RUNNING)
            for (int i = 0; i < nodes; i++) {
                def node = buildNode(NODE_NAME_1 + i, 'ERBS', ['EPS'], pmEnabled)
                buildScanner(node.name, EBS_CELLTRACE_SCANNER, subscriptionMO, true, HIGH_PRIORITY_CELLTRACE)
                if (i >= missingScanners) {
                    buildSubScanner(node, subscriptionMO)
                }
                subscriptionMO.addAssociation('nodes', node)
            }
        when: 'The subscription audit runs'
            subscriptionAuditorTimer.onTimeout()
        then: 'The expected number of activation tasks are sent for the missing node(s)'
            tasks * eventSender.send(
                { request -> request.getSubscriptionId() == Long.toString(subscriptionMO.getPoId()) } as SubscriptionActivationTaskRequest)
        where:
            tasks | nodes | missingScanners | pmEnabled | adminState
            1     | 2     | 1               | true      | AdministrationState.ACTIVE
            2     | 2     | 2               | true      | AdministrationState.ACTIVE
            100   | 234   | 100             | true      | AdministrationState.ACTIVE
            0     | 234   | 100             | true      | AdministrationState.INACTIVE //Not active so ignored
            0     | 234   | 100             | true      | AdministrationState.ACTIVATING //Not active so ignored
            0     | 10    | 6               | false     | AdministrationState.ACTIVE //PmFunction off
    }

    @Unroll
    def 'Performance Test - Subscription audit :Activation MTR sent for nodes with missing Scanners for Cell Trace (LRAN)'() {
        given: 'an active subscription with #totalNumberOfActiveScanners active scanners across 3 #neType nodes'
            def subscriptionMo = buildSubscription('Subscription', TaskStatus.ERROR, eventsForLran, SubscriptionType.CELLTRACE)
            (1..20000).each { it ->
                def node = buildNode(NODE_NAME_1 + it, neType, technologyDomain)
                buildScanner(node.name, 'PREDEF.10000.CELLTRACE', subscriptionMo, true, NORMAL_PRIORITY_CELLTRACE)
                dpsUtils.addAssociation(subscriptionMo, 'nodes', node)
            }

        when: 'the subscription auditor runs'
            def start_time = System.currentTimeMillis()
            subscriptionAuditorTimer.onTimeout()
            def end_time = System.currentTimeMillis()

        then: 'Test execution should not be more than 5sec'
            (end_time - start_time) / 1000 < 5

        and: 'Total number scanners are associated with the subscription'
            20000 == findTotalNumberOfScannersForSubscription(subscriptionMo.poId)

        where:
            neType      | technologyDomain
            'RadioNode' | ['EPS']
            'ERBS'      | ['EPS']
    }

    ManagedObject buildCellTraceEbslSubscription(final AdministrationState adminState, final OperationalState opState,
                                                 final TaskStatus taskStatus = TaskStatus.OK) {
        def subscription = getStreamingSubscription(adminState, opState, CellTraceCategory.CELLTRACE_AND_EBSL_STREAM, taskStatus)
        subscription.events(new EventInfo("event", "eventGroup"))
        subscription.ebsCounters(new CounterInfo("ebsCounter", "ebsGroup"))
        return subscription.build()
    }

    ManagedObject buildCellTraceEbsnSubscription(final AdministrationState adminState, final OperationalState opState,
                                                 final CellTraceCategory cellTraceCategory = CellTraceCategory.NRAN_EBSN_STREAM,
                                                 final TaskStatus taskStatus = TaskStatus.OK) {
        def subscription = getStreamingSubscription(adminState, opState, cellTraceCategory, taskStatus)
        if (cellTraceCategory == CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM) {
            subscription.events(nranEventsDuAndCuup)
        }
        return subscription.ebsEvents(nranEventsDuAndCuup)
            .ebsCounters(nranEbsCountersDuAndCuup)
            .build()
    }

    ManagedObject buildEbslStreamOnlySubscription(final AdministrationState adminState, final OperationalState opState, final TaskStatus taskStatus = TaskStatus.OK) {
        def subscription = getStreamingSubscription(adminState, opState, CellTraceCategory.EBSL_STREAM, taskStatus)
        subscription.ebsCounters(new CounterInfo("ebsCounter", "ebsGroup"))
        return subscription.build()
    }

    ManagedObject buildAsrSubscription(final AdministrationState adminState, final OperationalState opState, final TaskStatus taskStatus = TaskStatus.OK) {
        def subscription = getStreamingSubscription(adminState, opState, CellTraceCategory.ASR, taskStatus)
        subscription.userType(UserType.SYSTEM_DEF)
        return subscription.build()
    }

    def getStreamingSubscription(final AdministrationState adminState, final OperationalState opState,
                                 final CellTraceCategory category, final TaskStatus taskStatus = TaskStatus.Ok) {
        return createAndGetCellTraceSubscription(adminState, opState, category, taskStatus).ebsEvents(new EventInfo("ebsEvent", "ebsGroup"))
    }

    def createAndGetCellTraceSubscription(final AdministrationState adminState, final OperationalState opState,
                                          final CellTraceCategory category, final TaskStatus taskStatus = TaskStatus.OK) {
        dps.subscription()
            .type(SubscriptionType.CELLTRACE)
            .name('subscription')
            .administrationState(adminState)
            .operationalState(opState)
            .cellTraceCategory(category)
            .taskStatus(taskStatus)
            .userType(UserType.USER_DEF)
    }

    int findTotalNumberOfScannersForSubscription(final Long subscriptionId) {
        def allScanners = scannerDao.findAllBySubscriptionId(subscriptionId)
        def allSubScanners = subScannerService.findAllParentScannerBySubscriptionIdInReadTx(subscriptionId)
        return allScanners.size() + allSubScanners.size()
    }

    ManagedObject buildSubscription(final String name, final TaskStatus status, final List<EventInfo> events,
                                    final SubscriptionType subscriptionType = SubscriptionType.CONTINUOUSCELLTRACE,
                                    final CellTraceCategory cellTraceCategory = CellTraceCategory.CELLTRACE,
                                    final UserType userType = UserType.USER_DEF) {
        dps.subscription()
            .type(subscriptionType)
            .cellTraceCategory(cellTraceCategory)
            .name(name)
            .administrationState(AdministrationState.ACTIVE)
            .operationalState(OperationalState.RUNNING)
            .userType(userType)
            .taskStatus(status)
            .events(events)
            .build()
    }

    void buildSubScanner(final ManagedObject node, final ManagedObject subscritpionMo) {
        dps.subScanner()
            .subscriptionId(subscritpionMo.poId)
            .fdn(node.fdn + ',PMICScannerInfo=PREDEF.10004.CELLTRACE,PMICSubScannerInfo=' + subscritpionMo.name)
            .build()
    }

    void buildSubScannerForMultipleEventProducerIds(final ManagedObject node, final ManagedObject subscriptionMo, final String eventProducerId) {
        dps.subScanner()
            .subscriptionId(subscriptionMo.poId)
            .fdn(node.fdn + ',PMICScannerInfo=PREDEF.' + eventProducerId + '.10004.CELLTRACE,PMICSubScannerInfo=' + subscriptionMo.name)
            .build()
    }

}
