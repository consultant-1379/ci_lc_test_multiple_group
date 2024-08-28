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
package com.ericsson.oss.services.pm.bdd.collection.initiation.task.factories.validator

import spock.lang.Shared
import spock.lang.Unroll

import java.util.regex.Pattern

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.builder.TestDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.node.TestNetworkElementDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.scanner.TestScannerDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.CellTraceSubscriptionBuilder
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.enums.*
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.initiation.task.factories.validator.CellTraceTaskStatusValidator

class CellTraceTaskStatusValidatorSpec extends PmServiceEjbSkeletonSpec {

    private static final NODE_NAME_1 = 'LTE01ERBS0001'
    private static final NODE_NAME_2 = 'LTE01ERBS0002'
    public static final Pattern MULTIPLE_EVENT_PRODUCER_NORMAL_PRIORITY_SCANNER_PATTERN = Pattern.compile('PREDEF\\.(\\w+)\\.1000[0-3]\\.CELLTRACE')

    def testDpsUtils = new TestDpsUtils(configurableDps)

    def node = new TestNetworkElementDpsUtils(configurableDps)

    def scanner = new TestScannerDpsUtils(configurableDps)

    def builder = new CellTraceSubscriptionBuilder(testDpsUtils)

    @ObjectUnderTest
    private CellTraceTaskStatusValidator objectUnderTest

    private List<ManagedObject> nodes
    private ManagedObject subscriptionMO

    @Shared
    def eventWithEventProcuerDu = new EventInfo('INTERNAL_EVENT_ADMISSION_BLOCKING_STARTED', 'SESSION_ESTABLISHMENT_EVALUATION', 'DU')

    @Shared
    def eventWithEventProducerCuUp = new EventInfo('EXTERNAL_EVENT_ADMISSION_BLOCKING_STARTED', 'SESSION_ESTABLISHMENT_EVALUATION', 'CUUP')

    @Shared
    def eventWithEventProducerCuCp = new EventInfo('EXTERNAL_EVENT_ADMISSION_BLOCKING_STARTED', 'SESSION_ESTABLISHMENT_EVALUATION', 'CUCP')

    @Shared
    def events = [new EventInfo('INTERNAL_EVENT_ADMISSION_BLOCKING_STARTED', 'SESSION_ESTABLISHMENT_EVALUATION')]

    @Shared
    def ebsEvents = [new EventInfo('Ebs Event', 'Ebs Event Group')]

    @Shared
    def eventsWithEventProducer = [eventWithEventProcuerDu, eventWithEventProducerCuUp]

    @Shared
    def ebsEventsWithEventProducer = [eventWithEventProcuerDu, eventWithEventProducerCuCp]

    def setup() {
        subscriptionMO = builder.name('CellTraceSubscription').administrativeState(AdministrationState.ACTIVATING).taskStatus(TaskStatus.OK).build()
        nodes = [node.builder(NODE_NAME_1).build(), node.builder(NODE_NAME_2).build()]
        testDpsUtils.addAssociation(subscriptionMO, 'nodes', nodes.get(0), nodes.get(1))
    }

    def 'getTaskStatus will return OK status if active scanner count is equal with node count'() {
        given: 'two active scanners exist in dps for 2 nodes attached to one subscription'
            scanner.builder('PREDEF.10001.CELLTRACE', NODE_NAME_1).subscriptionId(subscriptionMO.poId).status(ScannerStatus.ACTIVE)
                    .processType(ProcessType.NORMAL_PRIORITY_CELLTRACE.name()).build()
            scanner.builder('PREDEF.10003.CELLTRACE', NODE_NAME_2).subscriptionId(subscriptionMO.poId).status(ScannerStatus.ACTIVE)
                    .processType(ProcessType.NORMAL_PRIORITY_CELLTRACE.name()).build()

        when: 'getTaskStatus is called'
            def taskStatus = objectUnderTest.getTaskStatus(subscriptionDao.findOneById(subscriptionMO.poId))

        then: 'task status will be OK'
            taskStatus == TaskStatus.OK
    }

    @Unroll
    def 'getTaskStatus will return ERROR if 2 nodes with ACTIVE and #status scanners exist for subscription'(String status) {
        given: 'one active scanner and one #status scanner exists in dps for 2 nodes attached to one subscription'
            scanner.builder('PREDEF.10001.CELLTRACE', NODE_NAME_1).subscriptionId(subscriptionMO.poId).status(ScannerStatus.ACTIVE)
                    .processType(ProcessType.NORMAL_PRIORITY_CELLTRACE.name()).build()
            scanner.builder('PREDEF.10003.CELLTRACE', NODE_NAME_2).subscriptionId(subscriptionMO.poId).status(status)
                    .processType(ProcessType.NORMAL_PRIORITY_CELLTRACE.name()).build()

        when: 'getTaskStatus is called'
            def taskStatus = objectUnderTest.getTaskStatus(subscriptionDao.findOneById(subscriptionMO.poId))

        then: 'task status will be ERROR'
            taskStatus == TaskStatus.ERROR

        where: 'The second scanner is not ACTIVE'
            status << ['INACTIVE', 'ERROR', 'UNKNOWN']
    }

    def 'getTaskStatus will return ERROR status when there are less active scanners than nodes'() {
        when: 'getTaskStatus is called'
            def taskStatus = objectUnderTest.getTaskStatus(subscriptionDao.findOneById(subscriptionMO.poId))

        then: 'task status will be ERROR'
            taskStatus == TaskStatus.ERROR
    }

    def 'getTaskStatus will return OK status if active scanner count is equal with node count based on Subscription object'() {
        given: 'two active scanners exist in dps for 2 nodes attached to one subscription'
            scanner.builder('PREDEF.10001.CELLTRACE', NODE_NAME_1).subscriptionId(subscriptionMO.poId).status(ScannerStatus.ACTIVE)
                    .processType(ProcessType.NORMAL_PRIORITY_CELLTRACE.name()).build()
            scanner.builder('PREDEF.10003.CELLTRACE', NODE_NAME_2).subscriptionId(subscriptionMO.poId).status(ScannerStatus.ACTIVE)
                    .processType(ProcessType.NORMAL_PRIORITY_CELLTRACE.name()).build()

        when: 'getTaskStatus is called'
            final Subscription subscription = new CellTraceSubscription()
            subscription.setId(subscriptionMO.poId)
            subscription.setType(SubscriptionType.CELLTRACE)
            subscription.setUserType(UserType.SYSTEM_DEF)
            final TaskStatus taskStatus = objectUnderTest.getTaskStatus(subscription)

        then: 'task status will be OK'
            taskStatus == TaskStatus.OK
    }

    @Unroll
    def 'getTaskStatus will return #expTaskStatus when scanner status is #scannerStatus for ebs stream subscription'() {
        given: 'a cell trace subscription with #subscriptionEvents and #subscriptionEbsEvents'
            def subscription = createCellTraceSubscription(subscriptionEvents, cellTraceCategory, subscriptionEbsEvents)

        and: 'four active scanners exist in dps for two nodes attached to one subscription'
            scanner.builder('PREDEF.10001.CELLTRACE', NODE_NAME_1).subscriptionId(subscriptionMO.poId).status(ScannerStatus.ACTIVE)
                    .processType(ProcessType.NORMAL_PRIORITY_CELLTRACE.name()).build()
            scanner.builder('PREDEF.10004.CELLTRACE', NODE_NAME_1).subscriptionId(subscriptionMO.poId).status(ScannerStatus.ACTIVE)
                    .processType(ProcessType.HIGH_PRIORITY_CELLTRACE.name()).build()
            scanner.builder('PREDEF.10003.CELLTRACE', NODE_NAME_2).subscriptionId(subscriptionMO.poId).status(scannerStatus)
                    .processType(ProcessType.NORMAL_PRIORITY_CELLTRACE.name()).build()
            scanner.builder('PREDEF.10004.CELLTRACE', NODE_NAME_2).subscriptionId(subscriptionMO.poId).status(scannerStatus)
                    .processType(ProcessType.HIGH_PRIORITY_CELLTRACE.name()).build()

        when: 'getTaskStatus is called for a ebs stream subscription'
            def taskStatus = objectUnderTest.getTaskStatus(subscription)

        then: 'task status is #expectedTaskStatus'
            taskStatus == expectedTaskStatus

        where:
            subscriptionEvents | subscriptionEbsEvents | cellTraceCategory                           | scannerStatus          || expectedTaskStatus
            events             | ebsEvents             | CellTraceCategory.CELLTRACE_AND_EBSL_STREAM | ScannerStatus.ACTIVE   || TaskStatus.OK
            events             | ebsEvents             | CellTraceCategory.CELLTRACE_AND_EBSL_STREAM | ScannerStatus.INACTIVE || TaskStatus.ERROR
            events             | ebsEvents             | CellTraceCategory.CELLTRACE_AND_EBSL_STREAM | ScannerStatus.ERROR    || TaskStatus.ERROR
            events             | ebsEvents             | CellTraceCategory.CELLTRACE_AND_EBSL_STREAM | ScannerStatus.UNKNOWN  || TaskStatus.ERROR
            events             | []                    | CellTraceCategory.CELLTRACE                 | ScannerStatus.ACTIVE   || TaskStatus.ERROR
            []                 | ebsEvents             | CellTraceCategory.EBSL_STREAM               | ScannerStatus.ACTIVE   || TaskStatus.ERROR
            []                 | []                    | null                                        | ScannerStatus.ACTIVE   || TaskStatus.ERROR
    }

    @Unroll
    def 'getTaskStatus should return #expectedTaskStatus when scanner status is #scannerStatusByScannerName for cellTrace subscription with category CELLTRACE_NRAN'() {
        given: 'a celltrace subscription with 2 nodes and 2 events associated to DU and CUUP event producers respectively'
            def subscription = createCellTraceSubscription(eventsWithEventProducer, CellTraceCategory.CELLTRACE_NRAN)
            createScanners(subscription.idAsString, scannerStatusByScannerName)

        when: 'getTaskStatus is invoked'
            def taskStatus = objectUnderTest.getTaskStatus(subscription)

        then: 'task status is #expectedTaskStatus'
            taskStatus == expectedTaskStatus

        where:
            scannerStatusByScannerName                  || expectedTaskStatus
            ['PREDEF.10000.CELLTRACE'     : 'INACTIVE',
             'PREDEF.DU.10000.CELLTRACE'  : 'INACTIVE',
             'PREDEF.CUUP.10000.CELLTRACE': 'INACTIVE'] || TaskStatus.ERROR
            ['PREDEF.10000.CELLTRACE'     : 'INACTIVE',
             'PREDEF.DU.10000.CELLTRACE'  : 'ACTIVE',
             'PREDEF.CUUP.10000.CELLTRACE': 'ACTIVE']   || TaskStatus.OK
            [:]                                         || TaskStatus.ERROR

    }

    @Unroll
    def 'getTaskStatus will return #expTaskStatus when scanner status is #scannerStatus for cell trace subscription'() {
        given: 'a cell trace subscription with #subscriptionEvents and #subscriptionEbsEvents'
            def subscription = createCellTraceSubscription(subscriptionEvents, CellTraceCategory.CELLTRACE, subscriptionEbsEvents)

        and: 'two active scanners exist in dps for two nodes attached to one subscription'
            scanner.builder('PREDEF.10001.CELLTRACE', NODE_NAME_1).subscriptionId(subscriptionMO.poId).status(ScannerStatus.ACTIVE)
                    .processType(ProcessType.NORMAL_PRIORITY_CELLTRACE.name()).build()
            scanner.builder('PREDEF.10003.CELLTRACE', NODE_NAME_2).subscriptionId(subscriptionMO.poId).status(scannerStatus)
                    .processType(ProcessType.NORMAL_PRIORITY_CELLTRACE.name()).build()

        when: 'getTaskStatus is called for a ebs stream subscription'
            def taskStatus = objectUnderTest.getTaskStatus(subscription)

        then: 'task status is #expectedTaskStatus'
            taskStatus == expectedTaskStatus

        where:
            subscriptionEvents | subscriptionEbsEvents | scannerStatus          || expectedTaskStatus
            events             | ebsEvents             | ScannerStatus.ACTIVE   || TaskStatus.OK
            events             | []                    | ScannerStatus.ACTIVE   || TaskStatus.OK
            []                 | ebsEvents             | ScannerStatus.ACTIVE   || TaskStatus.OK
            []                 | []                    | ScannerStatus.ACTIVE   || TaskStatus.OK
            events             | ebsEvents             | ScannerStatus.INACTIVE || TaskStatus.ERROR
            events             | ebsEvents             | ScannerStatus.ERROR    || TaskStatus.ERROR
            events             | ebsEvents             | ScannerStatus.UNKNOWN  || TaskStatus.ERROR
    }

    @Unroll
    def 'getTaskStatus will return #expTaskStatus when scanner status is #scannerStatus for CellTrace NRAN stream only OR file & stream subscription'() {
        given: 'a cell trace subscription with #subscriptionEvents and #subscriptionEbsEvents'
            def subscription = createCellTraceSubscription(subscriptionEvents, cellTraceCategory, subscriptionEbsEvents)

        and: 'scanners exist in dps for two nodes attached to this subscription'
            if (normalPriorityScanner) {
                createScanner('PREDEF.DU.10001.CELLTRACE', NODE_NAME_1, subscriptionMO.poId)
                createScanner('PREDEF.CUUP.10001.CELLTRACE', NODE_NAME_2, subscriptionMO.poId,
                        ProcessType.NORMAL_PRIORITY_CELLTRACE, scannerStatus)
            }
            def scanner = createScanner('PREDEF.DU.10004.CELLTRACE', NODE_NAME_1, Subscription.UNKNOWN_SUBSCRIPTION_ID, ProcessType.HIGH_PRIORITY_CELLTRACE, scannerStatus)
            createSubScanner(scanner, subscription)
            scanner = createScanner('PREDEF.DU.10004.CELLTRACE', NODE_NAME_2, Subscription.UNKNOWN_SUBSCRIPTION_ID, ProcessType.HIGH_PRIORITY_CELLTRACE, scannerStatus)
            createSubScanner(scanner, subscription)
            scanner = createScanner('PREDEF.CUCP.10004.CELLTRACE', NODE_NAME_1, Subscription.UNKNOWN_SUBSCRIPTION_ID, ProcessType.HIGH_PRIORITY_CELLTRACE, scannerStatus)
            createSubScanner(scanner, subscription)
            scanner = createScanner('PREDEF.CUCP.10004.CELLTRACE', NODE_NAME_2, Subscription.UNKNOWN_SUBSCRIPTION_ID, ProcessType.HIGH_PRIORITY_CELLTRACE, scannerStatus)
            createSubScanner(scanner, subscription)

        when: 'getTaskStatus is called for a ebs stream subscription'
            def taskStatus = objectUnderTest.getTaskStatus(subscription)

        then: 'task status is #expectedTaskStatus'
            taskStatus == expectedTaskStatus

        where:
            subscriptionEvents        | subscriptionEbsEvents      | cellTraceCategory                                | normalPriorityScanner | scannerStatus          || expectedTaskStatus
            [eventWithEventProcuerDu] | ebsEventsWithEventProducer | CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM | true                  | ScannerStatus.ACTIVE   || TaskStatus.OK
            [eventWithEventProcuerDu] | ebsEventsWithEventProducer | CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM | true                  | ScannerStatus.INACTIVE || TaskStatus.ERROR
            [eventWithEventProcuerDu] | ebsEventsWithEventProducer | CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM | false                 | ScannerStatus.ACTIVE   || TaskStatus.ERROR
            [eventWithEventProcuerDu] | ebsEventsWithEventProducer | CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM | false                 | ScannerStatus.INACTIVE || TaskStatus.ERROR
            [eventWithEventProcuerDu] | ebsEventsWithEventProducer | CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM | true                  | ScannerStatus.INACTIVE || TaskStatus.ERROR
            [eventWithEventProcuerDu] | ebsEventsWithEventProducer | CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM | true                  | ScannerStatus.UNKNOWN  || TaskStatus.ERROR
            [eventWithEventProcuerDu] | ebsEventsWithEventProducer | CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM | true                  | ScannerStatus.ERROR    || TaskStatus.ERROR
            []                        | ebsEventsWithEventProducer | CellTraceCategory.NRAN_EBSN_STREAM               | false                 | ScannerStatus.ACTIVE   || TaskStatus.OK
            []                        | ebsEventsWithEventProducer | CellTraceCategory.NRAN_EBSN_STREAM               | false                 | ScannerStatus.INACTIVE || TaskStatus.ERROR
            []                        | ebsEventsWithEventProducer | CellTraceCategory.NRAN_EBSN_STREAM               | false                 | ScannerStatus.UNKNOWN  || TaskStatus.ERROR
            []                        | ebsEventsWithEventProducer | CellTraceCategory.NRAN_EBSN_STREAM               | false                 | ScannerStatus.ERROR    || TaskStatus.ERROR
    }

    def createCellTraceSubscription(events = [], cellTraceCategory, ebsEvents = []) {
        final Subscription subscription = new CellTraceSubscription()
        subscription.setId(subscriptionMO.poId)
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setUserType(UserType.USER_DEF)
        subscription.setEvents(events)
        subscription.setEbsEvents(ebsEvents)
        subscription.setCellTraceCategory(cellTraceCategory)
        subscription.setName('CellTraceSubscription')
        subscription
    }

    def createScanner(scannerName, nodeName, subscriptionId, scannerType = ProcessType.NORMAL_PRIORITY_CELLTRACE, scannerStatus = ScannerStatus.ACTIVE) {
        scanner.builder(scannerName, nodeName)
               .subscriptionId(subscriptionId)
               .status(scannerStatus)
               .processType(scannerType)
               .build()
    }

    def createSubScanner(scanner, subscription) {
        dps.subScanner()
           .fdn("${scanner.fdn},PMICSubScannerInfo=${subscription.name}")
           .subscriptionId(subscription.id)
           .build()
    }

    List<ManagedObject> createScanners(String subscriptionId, Map scannerToScannerStatusMap) {
        List<ManagedObject> scannerMos = []
        scannerToScannerStatusMap.each { scannerName, scannerStatus ->
            [NODE_NAME_1, NODE_NAME_2].each { nodeName ->
                scannerMos.add(scannerUtil.builder(scannerName, nodeName).status(scannerStatus)
                        .subscriptionId(MULTIPLE_EVENT_PRODUCER_NORMAL_PRIORITY_SCANNER_PATTERN.matcher(scannerName).matches() ? subscriptionId : '0')
                        .processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build())
            }
        }
        scannerMos
    }
}
