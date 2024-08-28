package com.ericsson.oss.services.pm.initiation.task.factories.validation

import static com.ericsson.oss.pmic.cdi.test.util.Constants.EBS_CELLTRACE_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_10000_CELLTRACE_SCANNER
import static com.ericsson.oss.pmic.dto.node.enums.NetworkElementType.ERBS
import static com.ericsson.oss.pmic.dto.node.enums.NetworkElementType.RADIONODE
import static com.ericsson.oss.pmic.dto.node.enums.NetworkElementType.RNC
import static com.ericsson.oss.pmic.dto.node.enums.NetworkElementType.SGSNMME

import javax.inject.Inject

import spock.lang.Shared
import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.*
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo
import com.ericsson.oss.pmic.dto.subscription.enums.*
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.initiation.task.factories.validator.SubscriptionTaskStatusValidator

class TaskStatusValidatorSpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    SubscriptionTaskStatusValidator objectUnderTest

    @Inject
    SubscriptionDao subscriptionDao

    @Override
    def autoAllocateFrom() {
        def packages = super.autoAllocateFrom()
        packages.addAll(['com.ericsson.oss.pmic.dao', 'com.ericsson.oss.pmic.dto'])
        return packages
    }

    @Shared
    def erbsSysDefStats = 'ERBS System Defined Statistical Subscription'

    @Shared
    def radioSysDefStats = 'RadioNode System Defined Statistical Subscription'

    def eventA = new EventInfo('normEvent1', 'normGroupName1')
    def eventB = new EventInfo('normEvent2', 'normGroupName2')
    def events = [eventA, eventB]

    @Unroll
    def 'get task status for subscription will return OK if active scanner count is the same as subscription node count'() {
        given: 'a subscription with 2 nodes and associated scanners'
            def nodeMo1 = nodeUtil.builder('1').neType(neType).build()
            def nodeMo2 = nodeUtil.builder('2').neType(neType).build()
            def subMo = createSubscription(subscriptionType, [nodeMo1, nodeMo2], userType, name)
            if (subscriptionType == SubscriptionType.UETR) {
                subMo.ueInfoList([new UeInfo(UeType.IMEI, '1234567')]).outputMode(OutputModeType.FILE)
            }
            subMo = subMo.build()
            createScanner(scannerName, '1', nodeMo1, subMo, processType, ScannerStatus.ACTIVE)
            createScanner(scannerName, '2', nodeMo2, subMo, processType, ScannerStatus.ACTIVE)
            def sub = subscriptionDao.findOneById(subMo.poId, true)

        expect: 'the subscriptions task status is set to OK'
            objectUnderTest.getTaskStatus(sub) == TaskStatus.OK

        where:
            subscriptionType            | userType              | name              | neType        | scannerName                   | processType
            SubscriptionType.STATISTICAL| UserType.USER_DEF     | 'Test'            | ERBS          | 'USERDEF-Test.Cont.Y.STATS'   | ProcessType.STATS
            SubscriptionType.STATISTICAL| UserType.SYSTEM_DEF   | erbsSysDefStats   | ERBS          | 'PREDEF.STATS'                | ProcessType.STATS
            SubscriptionType.STATISTICAL| UserType.SYSTEM_DEF   | radioSysDefStats  | RADIONODE     | 'PREDEF.STATS'                | ProcessType.STATS
            SubscriptionType.MOINSTANCE | UserType.USER_DEF     | 'Test'            | ERBS          | 'USERDEF-Test.Cont.Y.STATS'   | ProcessType.STATS
            SubscriptionType.EBM        | UserType.USER_DEF     | 'Test'            | SGSNMME       | 'PREDEF.EBMLOG.EBM'           | ProcessType.NORMAL_PRIORITY_CELLTRACE
            SubscriptionType.CELLTRAFFIC| UserType.USER_DEF     | 'Test'            | RNC           | 'PREDEF.20001.CTR'            | ProcessType.CTR
            SubscriptionType.GPEH       | UserType.USER_DEF     | 'Test'            | RNC           | 'PREDEF.30021.GPEH'           | ProcessType.REGULAR_GPEH
            SubscriptionType.UETR       | UserType.USER_DEF     | 'Test'            | RNC           | 'PREDEF.10000.UETR'           | ProcessType.UETR
            SubscriptionType.CELLTRACE  | UserType.USER_DEF     | 'Test'            | ERBS          | PREDEF_10000_CELLTRACE_SCANNER| ProcessType.NORMAL_PRIORITY_CELLTRACE
    }

    @Unroll
    def 'get task status for subscription will return OK if active pmJob count is the same as node count'() {
        given: 'a UE Trace subscription with 2 nodes and assoiciated Pm Jobs'
            def nodeMo1 = nodeUtil.builder('1').neType(SGSNMME).build()
            def nodeMo2 = nodeUtil.builder('2').neType(SGSNMME).build()
            def subMo = createSubscription(subscriptionType, []).build()
            createPmJob(nodeMo1, subMo, procesType, PmJobStatus.ACTIVE)
            createPmJob(nodeMo2, subMo, procesType, PmJobStatus.ACTIVE)
            def sub = subscriptionDao.findOneById(subMo.poId, true)

        expect: 'the subscriptions task status is set to OK'
            objectUnderTest.getTaskStatus(sub) == TaskStatus.OK

        where:
            subscriptionType            | name   | procesType
            SubscriptionType.UETRACE    | 'Test' | ProcessType.UETRACE
            SubscriptionType.CTUM       | 'Test' | ProcessType.CTUM
    }

    def 'get task status for CellTraceSubscription will return OK if active scanner count is twice as subscription node count, EBS Stream Cluster is deployed'() {
        given: 'a Cell Trace / EBS-L subscription with 1 node and associated scanners normal and EBS scanner'
            def nodeMo1 = nodeUtil.builder('1').build()
            def subMo = createCellTraceSubscription([nodeMo1], CellTraceCategory.CELLTRACE_AND_EBSL_STREAM, events, events)
            createScanner(PREDEF_10000_CELLTRACE_SCANNER, '1', nodeMo1, subMo, ProcessType.NORMAL_PRIORITY_CELLTRACE, ScannerStatus.ACTIVE)
            createScanner(EBS_CELLTRACE_SCANNER, '1', nodeMo1, subMo, ProcessType.HIGH_PRIORITY_CELLTRACE, ScannerStatus.ACTIVE)

            def sub = subscriptionDao.findOneById(subMo.poId, true)

        expect: 'that the subscription task status is set to OK'
            objectUnderTest.getTaskStatus(sub) == TaskStatus.OK
    }

    def 'get task status for CellTraceSubscription will return OK if active scanner count is the same as node count when only ebsEvents are selected, EBS Stream Cluster is deployed'() {
        given: 'a subscription with 1 node and associated EBS scanner'
            def nodeMO1 = nodeUtil.builder('1').build()
            def subMo = createCellTraceSubscription([nodeMO1], CellTraceCategory.EBSL_STREAM, [], [eventA])
            createScanner(EBS_CELLTRACE_SCANNER, '1', nodeMO1, subMo, ProcessType.HIGH_PRIORITY_CELLTRACE, ScannerStatus.ACTIVE)
            def sub = subscriptionDao.findOneById(subMo.poId, true) as CellTraceSubscription

        expect: 'the subscriptions task status is set to OK'
            objectUnderTest.getTaskStatus(sub) == TaskStatus.OK
    }

    def 'get task status for UETR will return OK if active scanner count is the same as subscription node count and output mode is file and streaming'() {
        given: 'a UETR subscription with 2 nodes and associated scanners'
            def nodeMo1 = nodeUtil.builder('1').neType(RNC).build()
            def nodeMo2 = nodeUtil.builder('2').neType(RNC).build()
            def subMo = createSubscription(SubscriptionType.UETR, [nodeMo1, nodeMo2]).ueInfoList([new UeInfo(UeType.IMEI, '1234567')])
                                                                                     .outputMode(OutputModeType.FILE_AND_STREAMING)
                                                                                     .build()
            createScanner('PREDEF.10000.UETR', '1', nodeMo1, subMo, ProcessType.UETR, ScannerStatus.ACTIVE)
            createScanner('PREDEF.10000.UETR', '2', nodeMo2, subMo, ProcessType.UETR, ScannerStatus.ACTIVE)
            createScanner('PREDEF.10001.UETR', '1', nodeMo1, subMo, ProcessType.UETR, ScannerStatus.ACTIVE)
            createScanner('PREDEF.10001.UETR', '2', nodeMo2, subMo, ProcessType.UETR, ScannerStatus.ACTIVE)
            def sub = subscriptionDao.findOneById(subMo.poId, true)

        expect: 'the subscriptions task statis is set to OK'
            objectUnderTest.getTaskStatus(sub) == TaskStatus.OK
    }

    @Unroll
    def 'get task status for a subscription will return ERROR if active scanner count is not the same as subscription node count'() {
        given: 'a #subscriptionType subscription 2 nodes and associated scanners, one in ERROR'
            def nodeMo1 = nodeUtil.builder('1').neType(neType).build()
            def nodeMo2 = nodeUtil.builder('2').build()
            def subMo = createSubscription(subscriptionType, [nodeMo1, nodeMo2], userType, name)
            if (subscriptionType == SubscriptionType.UETR) {
                subMo.ueInfoList([new UeInfo(UeType.IMEI, '1234567')]).outputMode(OutputModeType.FILE)
            }
            subMo = subMo.build()
            createScanner(scannerName, '1', nodeMo1, subMo, ProcessType.UETR, ScannerStatus.ACTIVE)
            createScanner(scannerName, '2', nodeMo2, subMo, ProcessType.UETR, ScannerStatus.ERROR)
            def sub = subscriptionDao.findOneById(subMo.poId, true)

        expect: 'the subscriptions task statis is set to ERROR'
            objectUnderTest.getTaskStatus(sub) == TaskStatus.ERROR

        where:
            subscriptionType            | userType              | name              | neType        | scannerName                   | procesType
            SubscriptionType.STATISTICAL| UserType.USER_DEF     | 'Test'            | ERBS          | 'USERDEF-Test.Cont.Y.STATS'   | ProcessType.STATS
            SubscriptionType.STATISTICAL| UserType.SYSTEM_DEF   | erbsSysDefStats   | ERBS          | 'PREDEF.STATS'                | ProcessType.STATS
            SubscriptionType.STATISTICAL| UserType.SYSTEM_DEF   | radioSysDefStats  | RADIONODE     | 'PREDEF.STATS'                | ProcessType.STATS
            SubscriptionType.MOINSTANCE | UserType.USER_DEF     | 'Test'            | ERBS          | 'USERDEF-Test.Cont.Y.STATS'   | ProcessType.STATS
            SubscriptionType.EBM        | UserType.USER_DEF     | 'Test'            | SGSNMME       | 'PREDEF.EBMLOG.EBM'           | ProcessType.NORMAL_PRIORITY_CELLTRACE
            SubscriptionType.CELLTRAFFIC| UserType.USER_DEF     | 'Test'            | RNC           | 'PREDEF.20001.CTR'            | ProcessType.CTR
            SubscriptionType.GPEH       | UserType.USER_DEF     | 'Test'            | RNC           | 'PREDEF.30021.GPEH'           | ProcessType.REGULAR_GPEH
            SubscriptionType.UETR       | UserType.USER_DEF     | 'Test'            | RNC           | 'PREDEF.10000.UETR'           | ProcessType.UETR
            SubscriptionType.CELLTRACE  | UserType.USER_DEF     | 'Test'            | ERBS          | PREDEF_10000_CELLTRACE_SCANNER| ProcessType.NORMAL_PRIORITY_CELLTRACE
    }

    @Unroll
    def 'get task status for subscription will return ERROR if active pmJob count is not the same as node count'() {
        given:
            def nodeMo1 = nodeUtil.builder('1').neType(SGSNMME).build()
            def nodeMo2 = nodeUtil.builder('2').neType(SGSNMME).build()
            def subMo = createSubscription(subscriptionType).build()
            createPmJob(nodeMo1, subMo, procesType, PmJobStatus.ACTIVE)
            createPmJob(nodeMo2, subMo, procesType, PmJobStatus.ERROR)
            def sub = subscriptionDao.findOneById(subMo.poId, true)

        expect: 'the subscriptions task statis is set to ERROR'
            objectUnderTest.getTaskStatus(sub) == TaskStatus.ERROR

        where:
            subscriptionType            | procesType
            SubscriptionType.UETRACE    | ProcessType.UETRACE
            SubscriptionType.CTUM       | ProcessType.CTUM
    }

    def 'get task status for CellTraceSubscription will return ERROR if active scanner count is not twice as subscription node count, EBS Stream Cluster is deployed'() {
        given: 'a Cell Trace / EBS-L subscription with one node and associated EBS scanner in ERROR'
            def nodeMo1 = nodeUtil.builder('1').build()
            def subMo = createCellTraceSubscription([nodeMo1], CellTraceCategory.CELLTRACE_AND_EBSL_STREAM, events, events)
            createScanner(PREDEF_10000_CELLTRACE_SCANNER, '1', nodeMo1, subMo, ProcessType.HIGH_PRIORITY_CELLTRACE, ScannerStatus.ACTIVE)
            createScanner(EBS_CELLTRACE_SCANNER, '1', nodeMo1, subMo, ProcessType.HIGH_PRIORITY_CELLTRACE, ScannerStatus.ERROR)
            def sub = subscriptionDao.findOneById(subMo.poId, true)

        expect: 'the subscriptions task statis is set to ERROR'
            objectUnderTest.getTaskStatus(sub) == TaskStatus.ERROR
    }

    def 'get task status for CellTraceSubscription will return ERROR if active scanner count is not the same as node count when only ebsEvents are selected, EBS Stream Cluster is deployed'() {
        given: 'a Cell Trace / EBS-L subscription with node and associated EBS scanner in error'
            def nodeMo1 = nodeUtil.builder('1').build()
            def subMo = createCellTraceSubscription([nodeMo1], CellTraceCategory.EBSL_STREAM, [], events)
            createScanner(EBS_CELLTRACE_SCANNER, '1', nodeMo1, subMo, ProcessType.HIGH_PRIORITY_CELLTRACE, ScannerStatus.ERROR)
            def sub = subscriptionDao.findOneById(subMo.poId, true)

        expect: 'the subscriptions task statis is set to ERROR'
            objectUnderTest.getTaskStatus(sub) == TaskStatus.ERROR
    }

    def 'get task status for UETR will return ERROR if active scanner count is not the same as subscription node count and output mode is file and streaming'() {
        given: 'an UETR subscription with 2 nodes and one associciated scanner in ERROR'
            def nodeMo1 = nodeUtil.builder('1').neType(RNC).build()
            def nodeMo2 = nodeUtil.builder('2').neType(RNC).build()
            def subMo = uetrSubscriptionBuilder.ueInfoList([new UeInfo(UeType.IMEI, '1234567')]).outputMode(OutputModeType.FILE_AND_STREAMING).nodes(nodeMo1, nodeMo2).build()
            createScanner('PREDEF.10000.UETR', '1', nodeMo1, subMo, ProcessType.UETR, ScannerStatus.ACTIVE)
            createScanner('PREDEF.10000.UETR', '2', nodeMo2, subMo, ProcessType.UETR, ScannerStatus.ACTIVE)
            createScanner('PREDEF.10001.UETR', '1', nodeMo1, subMo, ProcessType.UETR, ScannerStatus.ACTIVE)
            createScanner('PREDEF.10001.UETR', '2', nodeMo2, subMo, ProcessType.UETR, ScannerStatus.ERROR)
            def sub = subscriptionDao.findOneById(subMo.poId, true)

        expect: 'the subscriptions task statis is set to ERROR'
            objectUnderTest.getTaskStatus(sub) == TaskStatus.ERROR
    }

    //#######################################################validateSubscriptionStatus############################################//

    def 'validateTaskStatusAndAdminState for a subscription will update task status to OK if active scanner count is the same as subscription node count'() {
        given: 'a subscription with 2 nodes and associated scanners'
            def nodeMo1 = nodeUtil.builder('1').build()
            def nodeMo2 = nodeUtil.builder('2').build()
            def subMo = createSubscription(subscriptionType, [nodeMo1, nodeMo2], userType, name)
            if (subscriptionType == SubscriptionType.UETR) {
                subMo.ueInfoList([new UeInfo(UeType.IMEI, '1234567')]).outputMode(OutputModeType.FILE)
            }
            subMo = subMo.build()
            createScanner(scannerName, '1', nodeMo1, subMo, procesType, ScannerStatus.ACTIVE)
            createScanner(scannerName, '2', nodeMo2, subMo, procesType, ScannerStatus.ACTIVE)
            def sub = subscriptionDao.findOneById(subMo.poId, true)

        when: 'validator is executed'
            objectUnderTest.validateTaskStatusAndAdminState(sub)

        then: 'the subscriptions task status is set to OK'
            subMo.getAttribute('taskStatus') == 'OK'

        where:
            subscriptionType            | userType              | name              | neType        | scannerName                   | procesType
            SubscriptionType.STATISTICAL| UserType.USER_DEF     | 'Test'            | ERBS          | 'USERDEF-Test.Cont.Y.STATS'   | ProcessType.STATS
            SubscriptionType.STATISTICAL| UserType.SYSTEM_DEF   | erbsSysDefStats   | ERBS          | 'PREDEF.STATS'                | ProcessType.STATS
            SubscriptionType.STATISTICAL| UserType.SYSTEM_DEF   | radioSysDefStats  | RADIONODE     | 'PREDEF.STATS'                | ProcessType.STATS
            SubscriptionType.MOINSTANCE | UserType.USER_DEF     | 'Test'            | ERBS          | 'USERDEF-Test.Cont.Y.STATS'   | ProcessType.STATS
            SubscriptionType.EBM        | UserType.USER_DEF     | 'Test'            | SGSNMME       | 'PREDEF.EBMLOG.EBM'           | ProcessType.NORMAL_PRIORITY_CELLTRACE
            SubscriptionType.CELLTRAFFIC| UserType.USER_DEF     | 'Test'            | RNC           | 'PREDEF.20001.CTR'            | ProcessType.CTR
            SubscriptionType.GPEH       | UserType.USER_DEF     | 'Test'            | RNC           | 'PREDEF.30021.GPEH'           | ProcessType.REGULAR_GPEH
            SubscriptionType.UETR       | UserType.USER_DEF     | 'Test'            | RNC           | 'PREDEF.10000.UETR'           | ProcessType.UETR
            SubscriptionType.CELLTRACE  | UserType.USER_DEF     | 'Test'            | ERBS          | PREDEF_10000_CELLTRACE_SCANNER| ProcessType.NORMAL_PRIORITY_CELLTRACE
    }

    def 'validateTaskStatusAndAdminState for UETR will update task status to OK if active scanner count is twice of imsi per node and output mode is file and streaming'() {
        given: 'a UETR subscription with Streaming output mode and one node with assoicated scanners'
            def nodeMo1 = nodeUtil.builder('1').neType(RNC).build()
            def subMo = createSubscription(SubscriptionType.UETR, [nodeMo1]).ueInfoList([new UeInfo(UeType.IMEI, '1234567')])
                                                                            .outputMode(OutputModeType.FILE_AND_STREAMING)
                                                                            .taskStatus(TaskStatus.ERROR)
                                                                            .build()
            createScanner('PREDEF.10000.UETR', '1', nodeMo1, subMo, ProcessType.UETR, ScannerStatus.ACTIVE)
            createScanner('PREDEF.10001.UETR', '1', nodeMo1, subMo, ProcessType.UETR, ScannerStatus.ACTIVE)
            def sub = subscriptionDao.findOneById(subMo.poId, true)

        when: 'validator is executed'
            objectUnderTest.validateTaskStatusAndAdminState(sub)

        then: 'the subscriptions task statis is set to OK'
            subMo.getAttribute('taskStatus') == 'OK'
    }

    @Unroll
    def 'validateTaskStatusAndAdminState for subscription will update task status to ERROR if active scanner count is not the same as subscription node count'() {
        given: 'a subscription with 2 nodes and one associated scanenr in error'
            def nodeMo1 = nodeUtil.builder('1').build()
            def nodeMo2 = nodeUtil.builder('2').build()
            def subMo = createSubscription(subscriptionType, [nodeMo1, nodeMo2], userType, name)
            if (subscriptionType == SubscriptionType.UETR) {
                subMo.ueInfoList([new UeInfo(UeType.IMEI, '1234567')]).outputMode(OutputModeType.FILE)
            }
            subMo = subMo.build()
            createScanner(scannerName, '1', nodeMo1, subMo, procesType, ScannerStatus.ACTIVE)
            createScanner(scannerName, '2', nodeMo2, subMo, procesType, ScannerStatus.ERROR)
            def sub = subscriptionDao.findOneById(subMo.poId, true)

        when: 'validator is executed'
            objectUnderTest.validateTaskStatusAndAdminState(sub)

        then: 'the subscriptions task statis is set to ERROR'
            subMo.getAttribute('taskStatus') == 'ERROR'

        where:
            subscriptionType            | userType              | name              | neType        | scannerName                   | procesType
            SubscriptionType.STATISTICAL| UserType.USER_DEF     | 'Test'            | ERBS          | 'USERDEF-Test.Cont.Y.STATS'   | ProcessType.STATS
            SubscriptionType.STATISTICAL| UserType.SYSTEM_DEF   | erbsSysDefStats   | ERBS          | 'PREDEF.STATS'                | ProcessType.STATS
            SubscriptionType.STATISTICAL| UserType.SYSTEM_DEF   | radioSysDefStats  | RADIONODE     | 'PREDEF.STATS'                | ProcessType.STATS
            SubscriptionType.MOINSTANCE | UserType.USER_DEF     | 'Test'            | ERBS          | 'USERDEF-Test.Cont.Y.STATS'   | ProcessType.STATS
            SubscriptionType.EBM        | UserType.USER_DEF     | 'Test'            | SGSNMME       | 'PREDEF.EBMLOG.EBM'           | ProcessType.NORMAL_PRIORITY_CELLTRACE
            SubscriptionType.CELLTRAFFIC| UserType.USER_DEF     | 'Test'            | RNC           | 'PREDEF.20001.CTR'            | ProcessType.CTR
            SubscriptionType.GPEH       | UserType.USER_DEF     | 'Test'            | RNC           | 'PREDEF.30021.GPEH'           | ProcessType.REGULAR_GPEH
            SubscriptionType.UETR       | UserType.USER_DEF     | 'Test'            | RNC           | 'PREDEF.10000.UETR'           | ProcessType.UETR
            SubscriptionType.CELLTRACE  | UserType.USER_DEF     | 'Test'            | ERBS          | PREDEF_10000_CELLTRACE_SCANNER| ProcessType.NORMAL_PRIORITY_CELLTRACE
    }

    @Unroll
    def 'validateTaskStatusAndAdminState for subscription and nodes will update task status to ERROR if active scanner count is not the same as subscription node count'() {
        given: 'a subscription with 2 nodes and one associated scanenr in error'
            def nodeMo1 = nodeUtil.builder('1').build()
            def nodeMo2 = nodeUtil.builder('2').build()
            def subMo = createSubscription(subscriptionType, [nodeMo1, nodeMo2], userType, name)
            if (subscriptionType == SubscriptionType.UETR) {
                subMo.ueInfoList([new UeInfo(UeType.IMEI, '1234567')]).outputMode(OutputModeType.FILE)
            }
            subMo = subMo.build()
            createScanner(scannerName, '1', nodeMo1, subMo, procesType, ScannerStatus.ACTIVE)
            createScanner(scannerName, '2', nodeMo2, subMo, procesType, ScannerStatus.ERROR)
            def sub = subscriptionDao.findOneById(subMo.poId, true)

        when: 'validator is executed'
            objectUnderTest.validateTaskStatusAndAdminState(sub, nodeDao.findAll()[0].fdn)

        then: 'the subscriptions task statis is set to ERROR'
            subMo.getAttribute('taskStatus') == 'ERROR'

        where:
            subscriptionType            | userType              | name              | neType        | scannerName                   | procesType
            SubscriptionType.STATISTICAL| UserType.USER_DEF     | 'Test'            | ERBS          | 'USERDEF-Test.Cont.Y.STATS'   | ProcessType.STATS
            SubscriptionType.STATISTICAL| UserType.SYSTEM_DEF   | erbsSysDefStats   | ERBS          | 'PREDEF.STATS'                | ProcessType.STATS
            SubscriptionType.STATISTICAL| UserType.SYSTEM_DEF   | radioSysDefStats  | RADIONODE     | 'PREDEF.STATS'                | ProcessType.STATS
            SubscriptionType.MOINSTANCE | UserType.USER_DEF     | 'Test'            | ERBS          | 'USERDEF-Test.Cont.Y.STATS'   | ProcessType.STATS
            SubscriptionType.EBM        | UserType.USER_DEF     | 'Test'            | SGSNMME       | 'PREDEF.EBMLOG.EBM'           | ProcessType.NORMAL_PRIORITY_CELLTRACE
            SubscriptionType.CELLTRAFFIC| UserType.USER_DEF     | 'Test'            | RNC           | 'PREDEF.20001.CTR'            | ProcessType.CTR
            SubscriptionType.GPEH       | UserType.USER_DEF     | 'Test'            | RNC           | 'PREDEF.30021.GPEH'           | ProcessType.REGULAR_GPEH
            SubscriptionType.UETR       | UserType.USER_DEF     | 'Test'            | RNC           | 'PREDEF.10000.UETR'           | ProcessType.UETR
            SubscriptionType.CELLTRACE  | UserType.USER_DEF     | 'Test'            | ERBS          | PREDEF_10000_CELLTRACE_SCANNER| ProcessType.NORMAL_PRIORITY_CELLTRACE
    }

    def 'Should throw UnsupportedOperationException if there is no validator for the given subscription type'() {
        given: 'some unsupported subscription type'
            def subscription = new ResourceSubscription(){}

        when: 'the validator is called'
            objectUnderTest.getTaskStatus(subscription)

        then: 'an UnsupportedOperationException is thrown because there is no validator for the base type ResourceSubscription'
            UnsupportedOperationException exception = thrown()
            exception.message == "Subscription Type: ${subscription.getClass().simpleName} from Subscription : ${subscription.name} is not currently supported"
    }

    def createScanner(name, nodeName, node, subMo, processType, status) {
        scannerUtil.builder(name, nodeName)
                   .node(node)
                   .subscriptionId(subMo)
                   .status(status)
                   .processType(processType)
                   .build()
    }

    def createPmJob(node, subMo, processType, status) {
        pmJobBuilder.nodeName(node)
                    .processType(processType)
                    .subscriptionId(subMo)
                    .status(status)
                    .build()
    }

    def createCellTraceSubscription(nodes = [], category = CellTraceCategory.CELLTRACE, events = [], ebsEvents = []) {
        createSubscription(SubscriptionType.CELLTRACE, nodes).cellTraceCategory(category)
                                                             .events(events)
                                                             .ebsEvents(ebsEvents)
                                                             .taskStatus(TaskStatus.OK)
                                                             .build()
    }

    def createSubscription(type, nodes = [], userType = UserType.USER_DEF, name = 'test') {
        dps.subscription()
                .type(type)
                .name(name)
                .userType(userType)
                .nodes(nodes)
                .administrationState(AdministrationState.ACTIVE)
    }
}
