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

import static com.ericsson.oss.pmic.cdi.test.util.Constants.CONTINUOUS_CELLTRACE_SCANNER_PREFIX
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_1
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_2
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_3
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_10000_CELLTRACE_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_10001_CELLTRACE_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_10003_CELLTRACE_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_20000_CTR_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_20001_CTR_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_30000_GPEH_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_30001_GPEH_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_EBMLOG_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_STATS_SCANNER_PREFIX
import static com.ericsson.oss.pmic.cdi.test.util.Constants.USER_DEF_SCANNER_PREFIX
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.CTR
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.EVENTJOB
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.HIGH_PRIORITY_CELLTRACE
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.NORMAL_PRIORITY_CELLTRACE
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.REGULAR_GPEH
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.STATS
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.UETR

import org.mockito.Mockito
import spock.lang.Unroll

import javax.ejb.TimerConfig
import javax.ejb.TimerService
import javax.inject.Inject
import java.text.SimpleDateFormat

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.SubscriptionBuilder
import com.ericsson.oss.pmic.dao.ScannerDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.Entity
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.scanner.Scanner
import com.ericsson.oss.pmic.dto.subscription.ResSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.MoTypeInstanceInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.dto.subscription.enums.UeType
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.services.pm.common.scheduling.IntervalTimerInfo
import com.ericsson.oss.services.pm.initiation.common.RopUtil
import com.ericsson.oss.services.pm.initiation.config.listener.SubscriptionAuditIntervalConfigurationChangeListener
import com.ericsson.oss.services.pm.initiation.model.metadata.res.PmResLookUp
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionActivationTaskRequest
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionDeactivationTaskRequest
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService
import com.ericsson.oss.services.pm.time.TimeGenerator

class SubscriptionAuditorTimerSpec extends AuditorParentSpec {

    private static final String TIMER_NAME = 'Subscription_Auditor_Timer'
    private static final SimpleDateFormat SDF = new SimpleDateFormat('dd/MM/yyyy HH:mm:ss')
    private static final long INITIAL_TIME = SDF.parse('22/09/2000 12:00:00').getTime()

    private Map<SubscriptionType, SubscriptionBuilder> builderMap = new HashMap<>()

    @ObjectUnderTest
    SubscriptionAuditorTimer subscriptionAuditorTimer

    @ImplementationInstance
    TimerService timerService = Mock(TimerService)

    @Inject
    RopUtil ropUtil

    @ImplementationInstance
    TimeGenerator timeGenerator = Mock(TimeGenerator)

    @ImplementationInstance
    com.ericsson.oss.pmic.util.TimeGenerator timer = Mockito.mock(com.ericsson.oss.pmic.util.TimeGenerator)

    @ImplementationInstance
    MembershipListener listener = Mock(MembershipListener)

    @ImplementationInstance
    PmResLookUp pmResLookup = Mock(PmResLookUp)

    @ImplementationInstance
    SubscriptionAuditIntervalConfigurationChangeListener intervalConfigurationChangeListener = Mock(SubscriptionAuditIntervalConfigurationChangeListener)

    @Inject
    EventSender<MediationTaskRequest> eventSender

    @Inject
    private ScannerDao scannerDao

    @Inject
    private SubscriptionDao subscriptionDao

    @Inject
    SubscriptionReadOperationService subscriptionReadOperationService

    private ManagedObject subscriptionMO

    def setup() {
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())

        builderMap.put(SubscriptionType.CELLTRACE, cellTraceSubscriptionBuilder)
        builderMap.put(SubscriptionType.STATISTICAL, statisticalSubscriptionBuilder)
        builderMap.put(SubscriptionType.CELLTRAFFIC, cellTrafficSubscriptionBuilder)
        builderMap.put(SubscriptionType.EBM, ebmSubscriptionBuilder)
        builderMap.put(SubscriptionType.GPEH, ebmSubscriptionBuilder)
    }

    def 'create timer start 3th min before 15min ROP'() {
        intervalConfigurationChangeListener.getSubscriptionAuditScheduleInterval() >> 15
        long intervalMillis = 15 * 60000L
        long time = INITIAL_TIME
        given: 'The current time set'
            timeGenerator.currentTimeMillis() >> time
            timerService.getTimers() >> []
            final Date initialExpiration = ropUtil.getInitialExpirationTime(3);

        when: 'The timer is created'
            subscriptionAuditorTimer.scheduleSubscriptionAudit()

        then:
            1 * timerService.createIntervalTimer(initialExpiration, intervalMillis,
                { new TimerConfig(new IntervalTimerInfo(TIMER_NAME, intervalMillis), false) } as TimerConfig)
    }

    @Unroll
    def 'Extra scanner which does not belong to current subscription of type #subscriptionType should be deleted'() {
        listener.isMaster() >> true
        given: 'A subscription with two nodes, one extra scanner'
            def subscriptionName = 'TEST'
            def node1 = buildNode(NODE_NAME_1, neType)
            def node2 = buildNode(NODE_NAME_2, neType)

            subscriptionMO = dps.subscription().
                type(subscriptionType).
                name(subscriptionName).
                administrationState(AdministrationState.ACTIVE).
                userType(UserType.USER_DEF).
                taskStatus(TaskStatus.OK).
                nodes(node1, node2).
                build()
            buildScanner(node1.name, "USER_DEF-${subscriptionName}.Cont.Y.STATS", subscriptionMO, true, STATS)
            buildScanner(node2.name, "USER_DEF-${subscriptionName}.Cont.Y.STATS", subscriptionMO, true, STATS)
            buildScanner(NODE_NAME_3, "USER_DEF-${subscriptionName}.Cont.Y.STATS", subscriptionMO, true, STATS)

        when: 'The subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        then: 'Extra scanner should be deleted'
            scannerDao.countBySubscriptionId(subscriptionMO.getPoId()) == 2

        where:
            subscriptionType              | neType
            SubscriptionType.STATISTICAL  | 'ERBS'
            SubscriptionType.RES          | 'RNC'
            SubscriptionType.CELLRELATION | 'RNC'
    }

    @Unroll
    def 'Subscription audit for UETR with missing scanners #missingScanners'() {
        listener.isMaster() >> true
        given: 'A subscription with two nodes, one with a missing scanner'
            def ueinfoList = new ArrayList<UeInfo>(imsi)
            for (int i = 0; i < imsi; i++) {
                ueinfoList.add(new UeInfo(UeType.IMEI, '214256543' + i))
            }

            def node = buildNode(NODE_NAME_1, 'RNC', ['EPS'], pmEnabled)
            subscriptionMO = dps.subscription().
                type(SubscriptionType.UETR).
                name('subscription').
                administrationState(adminState).
                operationalState(OperationalState.RUNNING).
                userType(UserType.USER_DEF).
                taskStatus(TaskStatus.OK).
                events(new EventInfo('group', 'name')).
                ueInfoList(ueinfoList).
                nodes(node).
                build()
            for (int i = 0; i < available; i++) {
                buildScanner(node.name, "PREDEF.1000${i}.UETR", subscriptionMO, false, UETR, Scanner.PmicScannerType.PMICUeScannerInfo)
            }
            for (int i = 0; i < (imsi - missingScanners); i++) {
                buildScanner(node.name, "PREDEF.1000${(available + i)}.UETR", subscriptionMO, true, UETR, Scanner.PmicScannerType.PMICUeScannerInfo)
                available++
            }

        when: 'The subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        then: 'The expected number of activation tasks are sent for the missing node(s)'
            tasks * eventSender.send(
                { request -> request.getSubscriptionId() == Long.toString(subscriptionMO.getPoId()) } as SubscriptionActivationTaskRequest)

        and: 'A scanner has the subscription ID assigned before sending the task'
            if (adminState == AdministrationState.ACTIVE) {
                scannerDao.countBySubscriptionId(subscriptionMO.getPoId()) == available > imsi ? (imsi - missingScanners) : (imsi - available)
            } else {
                scannerDao.countBySubscriptionId(subscriptionMO.getPoId()) == imsi - missingScanners + tasks
            }

        where:
            tasks | imsi | missingScanners | available | pmEnabled | adminState
            1     | 2    | 1               | 17        | true      | AdministrationState.ACTIVE
            1     | 10   | 10              | 2         | true      | AdministrationState.ACTIVE
            1     | 2    | 2               | 1         | true      | AdministrationState.ACTIVE     //Only one scanner available
            1     | 10   | 5               | 3         | true      | AdministrationState.ACTIVE
            1     | 16   | 10              | 8         | true      | AdministrationState.ACTIVE     //Only 83 scanners available
            0     | 234  | 100             | 100       | true      | AdministrationState.INACTIVE   //Not active so ignored
            0     | 234  | 100             | 100       | true      | AdministrationState.ACTIVATING //Not active so ignored
            0     | 10   | 6               | 6         | false     | AdministrationState.ACTIVE     //PmFunction off
    }

    @Unroll
    def 'No Deactivation events are sent for nodes with duplicate scanners UETR, #tasks, Scanner Master will cleanup duplicate scanner'() {
        listener.isMaster() >> true
        given: 'A subscription with two nodes, one with a missing scanner'
            def node = buildNode(NODE_NAME_1, 'RNC')
            subscriptionMO = dps.subscription().
                type(SubscriptionType.UETR).
                name('subscription').
                administrationState(adminState).
                operationalState(OperationalState.RUNNING).
                userType(UserType.USER_DEF).
                taskStatus(TaskStatus.OK).
                events(new EventInfo('group', 'name')).
                ueInfoList(new UeInfo(UeType.IMEI, '2142565432')).
                nodes(node).
                build()
            for (int i = 0; i < allocatedScanner; i++) {
                buildScanner(node.name, "PREDEF.1000${(i + 2)}.UETR", subscriptionMO, true, UETR, Scanner.PmicScannerType.PMICUeScannerInfo)
            }

        when: 'The subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        then: 'The expected number of activation tasks are sent for the missing node(s)'
            0 * eventSender.send(
                { request -> request.getSubscriptionId() == Long.toString(subscriptionMO.getPoId()) } as SubscriptionActivationTaskRequest)

        where:
            tasks | nodes | allocatedScanner | pmEnabled | adminState                     | processName
            1     | 1     | 3                | true      | AdministrationState.ACTIVE     | 'PREDEF.10000.UETR'
            2     | 2     | 2                | true      | AdministrationState.ACTIVE     | 'PREDEF.10000.UETR'
            1     | 2     | 1                | true      | AdministrationState.ACTIVE     | 'PREDEF.10000.UETR'//Only one scanner available
            3     | 10    | 3                | true      | AdministrationState.ACTIVE     | 'PREDEF.10000.UETR'
            8     | 16    | 8                | true      | AdministrationState.ACTIVE     | 'PREDEF.10000.UETR'//Only 83 scanners available
            0     | 234   | 100              | true      | AdministrationState.INACTIVE   | 'PREDEF.10000.UETR'//Not active so ignored
            0     | 234   | 100              | true      | AdministrationState.ACTIVATING | 'PREDEF.10000.UETR'//Not active so ignored
            0     | 10    | 6                | false     | AdministrationState.ACTIVE     | 'PREDEF.10001.UETR'//PmFunction off
    }

    @Unroll
    def 'Activation events are sent for nodes with missing scanners UETR, #tasks and Taskvalidation should called'() {
        listener.isMaster() >> true
        given: 'A subscription with two nodes, one with a missing scanner'
            subscriptionMO = dps.subscription().
                type(SubscriptionType.UETR).
                name('subscription').
                administrationState(adminState).
                operationalState(OperationalState.RUNNING).
                userType(UserType.USER_DEF).
                taskStatus(TaskStatus.OK).
                events(new EventInfo('group', 'name')).
                ueInfoList(new UeInfo(UeType.IMEI, '2142565432')).
                build()
            for (int i = 0; i < nodes; i++) {
                def node = buildNode(NODE_NAME_1 + i,'RNC', ['EPS'], pmEnabled)
                if (i < missingScanners) {
                    if (i < available) {
                        buildScanner(node.name, "PREDEF.1000${(i + 2)}.UETR", subscriptionMO, false, UETR, Scanner.PmicScannerType.PMICUeScannerInfo)
                    }
                } else {
                    buildScanner(node.name, processName, subscriptionMO, true, UETR, Scanner.PmicScannerType.PMICUeScannerInfo)
                }
                subscriptionMO.addAssociation('nodes', node)
            }

        when: 'The subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        then: 'The expected number of activation tasks are sent for the missing node(s)'
            tasks * eventSender.send(
                { request -> request.getSubscriptionId() == Long.toString(subscriptionMO.getPoId()) } as SubscriptionActivationTaskRequest)

        and: 'A scanner has the subscription ID assigned before sending the task'
            scannerDao.countBySubscriptionId(subscriptionMO.getPoId()) == nodes - missingScanners + tasks

        where:
            tasks | nodes | missingScanners | available | pmEnabled | adminState                     | processName
            1     | 2     | 1               | 1         | true      | AdministrationState.ACTIVE     | 'PREDEF.10000.UETR'
            2     | 2     | 2               | 2         | true      | AdministrationState.ACTIVE     | 'PREDEF.10000.UETR'
            1     | 2     | 2               | 1         | true      | AdministrationState.ACTIVE     | 'PREDEF.10000.UETR'//Only one scanner available
            3     | 10    | 5               | 3         | true      | AdministrationState.ACTIVE     | 'PREDEF.10000.UETR'
            8     | 16    | 10              | 8         | true      | AdministrationState.ACTIVE     | 'PREDEF.10000.UETR'//Only 83 scanners available
            0     | 234   | 100             | 100       | true      | AdministrationState.INACTIVE   | 'PREDEF.10000.UETR'//Not active so ignored
            0     | 234   | 100             | 100       | true      | AdministrationState.ACTIVATING | 'PREDEF.10000.UETR'//Not active so ignored
            0     | 10    | 6               | 6         | false     | AdministrationState.ACTIVE     | 'PREDEF.10001.UETR'//PmFunction off
    }

    def 'Res Audit will remove an attached node with PmFunction OFF'() {
        listener.isMaster() >> true
        given: 'explicit and attached nodes'
            def rncNodeMo = buildNode('RNCNode', 'RNC')
            def rbsNode1 = buildNode('RBSNode1', 'RBS')
            def rbsNode2 = buildNode('RBSNode2', 'RBS', ['EPS'], false)

        and: 'an active RES subscription and associated scanners'
            def subAttributes = [resSpreadingFactor: ['SF_32'],
                                 applyOnAllCells   : true]
            subscriptionMO = dps.subscription().type(SubscriptionType.RES).
                name('res_sub').
                administrationState(AdministrationState.ACTIVE).
                operationalState(OperationalState.RUNNING).
                userType(UserType.USER_DEF).
                taskStatus(TaskStatus.OK).
                attributes(subAttributes).
                nodes(rncNodeMo).
                attachedNodes(rbsNode1, rbsNode2).
                build()
            long subId = subscriptionMO.getPoId()
            buildScanner(rncNodeMo.name, 'USERDEF-res_sub.Cont.Y.STATS', subscriptionMO, true, STATS)
            buildScanner(rbsNode1.name, 'USERDEF-res_sub.Cont.Y.STATS', subscriptionMO, true, STATS)
            buildScanner(rbsNode2.name, 'USERDEF-res_sub.Cont.Y.STATS', subscriptionMO, true, STATS)

        and: 'Mock for fetchAttachedNodes'
            List<Node> newAttachedNodes = new ArrayList<>()
            newAttachedNodes.add(new Node(new Entity(rbsNode1.getType(), rbsNode1.getPoId(), rbsNode1.getFdn())))
            pmResLookup.fetchAttachedNodes(_, _, _, _) >>> [newAttachedNodes, []]

        when: 'On first Audit run'
            subscriptionAuditorTimer.onTimeout()

        then: 'node is removed from subscription and deactivation sent'
            ((ResSubscription) subscriptionReadOperationService.findOneById(subId, true)).getAttachedNodesFdn().size() == 1
            1 * eventSender.send(
                { request -> request.getSubscriptionId() == Long.toString(subscriptionMO.getPoId()) } as SubscriptionDeactivationTaskRequest)
    }

    @Unroll
    def 'Activation events are sent for nodes with missing scanners #subscriptionType, #tasks and #adminState'() {
        listener.isMaster() >> true
        given: 'A subscription with two nodes, one with a missing scanner'
            subscriptionMO = dps.subscription().type(subscriptionType).
                name('subscription').
                administrationState(adminState).
                operationalState(OperationalState.RUNNING).
                userType(userType).
                taskStatus(TaskStatus.OK).
                events(new EventInfo('group', 'name')).
                counters(new CounterInfo('group', 'name')).
                cells(getCells(nodes)).
                moTypeInstances(getMoTypeInstances(nodes)).
                build()
            for (int i = 0; i < nodes; i++) {
                def node = buildNode(NODE_NAME_1 + i, neType, ['EPS'], pmEnabled)
                if (i < missingScanners) {
                    if (i < available) {
                        buildScanner(node.name, processName, subscriptionMO, false, processType)
                    }
                } else {
                    buildScanner(node.name, processName, subscriptionMO, true, processType)
                }
                subscriptionMO.addAssociation('nodes', node)
            }

        when: 'The subscription audit runs'
            subscriptionAuditorTimer.onTimeout()
            final List<Scanner> scanners = scannerDao.findAllBySubscriptionId(subscriptionMO.getPoId())

        then: 'The expected number of activation tasks are sent for the missing node(s)'
            tasks * eventSender.send(
                { request -> request.getSubscriptionId() == Long.toString(subscriptionMO.getPoId()) } as SubscriptionActivationTaskRequest)

        and: 'A scanner has the subscription ID assigned before sending the task, except for STATS'
            (SubscriptionType.STATISTICAL == subscriptionType) || (SubscriptionType.RES == subscriptionType) || (SubscriptionType.CELLRELATION ==
                    subscriptionType) || scanners.size() == nodes - missingScanners + tasks

        where:
            subscriptionType              | tasks | nodes | missingScanners | available | neType     | pmEnabled | adminState                     | processName                         | processType               | userType
            SubscriptionType.GPEH         | 1     | 2     | 1               | 1         | 'RNC'      | true      | AdministrationState.ACTIVE     | PREDEF_30000_GPEH_SCANNER           | REGULAR_GPEH              | UserType.USER_DEF
            SubscriptionType.GPEH         | 2     | 2     | 2               | 2         | 'RNC'      | true      | AdministrationState.ACTIVE     | PREDEF_30000_GPEH_SCANNER           | REGULAR_GPEH              | UserType.USER_DEF
            SubscriptionType.GPEH         | 1     | 2     | 2               | 1         | 'RNC'      | true      | AdministrationState.ACTIVE     | PREDEF_30000_GPEH_SCANNER           | REGULAR_GPEH              | UserType.USER_DEF//Only one scanner available
            SubscriptionType.GPEH         | 100   | 234   | 100             | 100       | 'RNC'      | true      | AdministrationState.ACTIVE     | PREDEF_30000_GPEH_SCANNER           | REGULAR_GPEH              | UserType.USER_DEF
            SubscriptionType.GPEH         | 83    | 234   | 100             | 83        | 'RNC'      | true      | AdministrationState.ACTIVE     | PREDEF_30000_GPEH_SCANNER           | REGULAR_GPEH              | UserType.USER_DEF//Only 83 scanners available
            SubscriptionType.GPEH         | 0     | 234   | 100             | 100       | 'RNC'      | true      | AdministrationState.INACTIVE   | PREDEF_30000_GPEH_SCANNER           | REGULAR_GPEH              | UserType.USER_DEF//Not active so ignored
            SubscriptionType.GPEH         | 0     | 234   | 100             | 100       | 'RNC'      | true      | AdministrationState.ACTIVATING | PREDEF_30000_GPEH_SCANNER           | REGULAR_GPEH              | UserType.USER_DEF//Not active so ignored
            SubscriptionType.GPEH         | 0     | 10    | 6               | 6         | 'RNC'      | false     | AdministrationState.ACTIVE     | PREDEF_30001_GPEH_SCANNER           | REGULAR_GPEH              | UserType.USER_DEF//PmFunction off
            SubscriptionType.CELLTRAFFIC  | 1     | 2     | 1               | 1         | 'RNC'      | true      | AdministrationState.ACTIVE     | PREDEF_20000_CTR_SCANNER            | CTR                       | UserType.USER_DEF
            SubscriptionType.CELLTRAFFIC  | 2     | 2     | 2               | 2         | 'RNC'      | true      | AdministrationState.ACTIVE     | PREDEF_20000_CTR_SCANNER            | CTR                       | UserType.USER_DEF
            SubscriptionType.CELLTRAFFIC  | 1     | 2     | 2               | 1         | 'RNC'      | true      | AdministrationState.ACTIVE     | PREDEF_20000_CTR_SCANNER            | CTR                       | UserType.USER_DEF//Only one scanner available
            SubscriptionType.CELLTRAFFIC  | 100   | 234   | 100             | 100       | 'RNC'      | true      | AdministrationState.ACTIVE     | PREDEF_20000_CTR_SCANNER            | CTR                       | UserType.USER_DEF
            SubscriptionType.CELLTRAFFIC  | 83    | 234   | 100             | 83        | 'RNC'      | true      | AdministrationState.ACTIVE     | PREDEF_20000_CTR_SCANNER            | CTR                       | UserType.USER_DEF//Only 83 scanners available
            SubscriptionType.CELLTRAFFIC  | 0     | 234   | 100             | 100       | 'RNC'      | true      | AdministrationState.INACTIVE   | PREDEF_20000_CTR_SCANNER            | CTR                       | UserType.USER_DEF//Not active so ignored
            SubscriptionType.CELLTRAFFIC  | 0     | 234   | 100             | 100       | 'RNC'      | true      | AdministrationState.ACTIVATING | PREDEF_20000_CTR_SCANNER            | CTR                       | UserType.USER_DEF//Not active so ignored
            SubscriptionType.CELLTRAFFIC  | 0     | 10    | 6               | 6         | 'RNC'      | false     | AdministrationState.ACTIVE     | PREDEF_20001_CTR_SCANNER            | CTR                       | UserType.USER_DEF//PmFunction off
            SubscriptionType.CELLTRACE    | 1     | 2     | 1               | 1         | 'ERBS'     | true      | AdministrationState.ACTIVE     | PREDEF_10000_CELLTRACE_SCANNER      | NORMAL_PRIORITY_CELLTRACE | UserType.USER_DEF
            SubscriptionType.CELLTRACE    | 2     | 2     | 2               | 2         | 'ERBS'     | true      | AdministrationState.ACTIVE     | PREDEF_10000_CELLTRACE_SCANNER      | NORMAL_PRIORITY_CELLTRACE | UserType.USER_DEF
            SubscriptionType.CELLTRACE    | 1     | 2     | 2               | 1         | 'ERBS'     | true      | AdministrationState.ACTIVE     | PREDEF_10000_CELLTRACE_SCANNER      | NORMAL_PRIORITY_CELLTRACE | UserType.USER_DEF//Only one scanner available
            SubscriptionType.CELLTRACE    | 100   | 234   | 100             | 100       | 'ERBS'     | true      | AdministrationState.ACTIVE     | PREDEF_10000_CELLTRACE_SCANNER      | NORMAL_PRIORITY_CELLTRACE | UserType.USER_DEF
            SubscriptionType.CELLTRACE    | 83    | 234   | 100             | 83        | 'ERBS'     | true      | AdministrationState.ACTIVE     | PREDEF_10000_CELLTRACE_SCANNER      | NORMAL_PRIORITY_CELLTRACE | UserType.USER_DEF//Only 83 scanners available
            SubscriptionType.CELLTRACE    | 0     | 234   | 100             | 100       | 'ERBS'     | true      | AdministrationState.INACTIVE   | PREDEF_10000_CELLTRACE_SCANNER      | NORMAL_PRIORITY_CELLTRACE | UserType.USER_DEF//Not active so ignored
            SubscriptionType.CELLTRACE    | 0     | 234   | 100             | 100       | 'ERBS'     | true      | AdministrationState.ACTIVATING | PREDEF_10000_CELLTRACE_SCANNER      | NORMAL_PRIORITY_CELLTRACE | UserType.USER_DEF//Not active so ignored
            SubscriptionType.CELLTRACE    | 0     | 10    | 6               | 6         | 'ERBS'     | false     | AdministrationState.ACTIVE     | PREDEF_10001_CELLTRACE_SCANNER      | NORMAL_PRIORITY_CELLTRACE | UserType.USER_DEF//PmFunction off
            SubscriptionType.CELLTRACE    | 0     | 5     | 5               | 5         | 'ERBS'     | true      | AdministrationState.ACTIVE     | CONTINUOUS_CELLTRACE_SCANNER_PREFIX | HIGH_PRIORITY_CELLTRACE   | UserType.USER_DEF//Only high priority exist
            SubscriptionType.STATISTICAL  | 2     | 3     | 2               | 0         | 'ERBS'     | true      | AdministrationState.ACTIVE     | USER_DEF_SCANNER_PREFIX             | STATS                     | UserType.USER_DEF//'available scanners' doesn't apply for STATS
            SubscriptionType.STATISTICAL  | 55    | 60    | 55              | 0         | 'ERBS'     | true      | AdministrationState.ACTIVE     | USER_DEF_SCANNER_PREFIX             | STATS                     | UserType.USER_DEF
            SubscriptionType.STATISTICAL  | 0     | 3     | 2               | 0         | 'ERBS'     | true      | AdministrationState.ACTIVE     | PREDEF_STATS_SCANNER_PREFIX         | STATS                     | UserType.SYSTEM_DEF//ignored system defined Statistical Subscription
            SubscriptionType.STATISTICAL  | 0     | 234   | 100             | 0         | 'ERBS'     | true      | AdministrationState.INACTIVE   | USER_DEF_SCANNER_PREFIX             | STATS                     | UserType.USER_DEF//Not active so ignored
            SubscriptionType.STATISTICAL  | 0     | 234   | 100             | 0         | 'ERBS'     | true      | AdministrationState.ACTIVATING | USER_DEF_SCANNER_PREFIX             | STATS                     | UserType.USER_DEF//Not active so ignored
            SubscriptionType.CELLRELATION | 2     | 3     | 2               | 0         | 'RNC'      | true      | AdministrationState.ACTIVE     | USER_DEF_SCANNER_PREFIX             | STATS                     | UserType.USER_DEF
            SubscriptionType.CELLRELATION | 55    | 60    | 55              | 0         | 'RNC'      | true      | AdministrationState.ACTIVE     | USER_DEF_SCANNER_PREFIX             | STATS                     | UserType.USER_DEF
            SubscriptionType.CELLRELATION | 0     | 234   | 100             | 0         | 'RNC'      | true      | AdministrationState.INACTIVE   | USER_DEF_SCANNER_PREFIX             | STATS                     | UserType.USER_DEF//Not active so ignored
            SubscriptionType.CELLRELATION | 0     | 234   | 100             | 0         | 'RNC'      | true      | AdministrationState.ACTIVATING | USER_DEF_SCANNER_PREFIX             | STATS                     | UserType.USER_DEF//Not active so ignored
            SubscriptionType.RES          | 2     | 3     | 2               | 0         | 'RNC'      | true      | AdministrationState.ACTIVE     | USER_DEF_SCANNER_PREFIX             | STATS                     | UserType.USER_DEF//'available scanners' doesn't apply for RES
            SubscriptionType.RES          | 0     | 234   | 100             | 0         | 'RNC'      | true      | AdministrationState.INACTIVE   | USER_DEF_SCANNER_PREFIX             | STATS                     | UserType.USER_DEF//Not active so ignored
            SubscriptionType.RES          | 0     | 234   | 100             | 0         | 'RNC'      | true      | AdministrationState.ACTIVATING | USER_DEF_SCANNER_PREFIX             | STATS                     | UserType.USER_DEF//Not active so ignored
            SubscriptionType.EBM          | 10    | 10    | 10              | 10        | 'SGSN-MME' | true      | AdministrationState.ACTIVE     | PREDEF_EBMLOG_SCANNER               | EVENTJOB                  | UserType.USER_DEF
            SubscriptionType.EBM          | 6     | 10    | 6               | 6         | 'SGSN-MME' | true      | AdministrationState.ACTIVE     | PREDEF_EBMLOG_SCANNER               | EVENTJOB                  | UserType.USER_DEF
            SubscriptionType.EBM          | 1     | 2     | 1               | 1         | 'SGSN-MME' | true      | AdministrationState.ACTIVE     | PREDEF_EBMLOG_SCANNER               | EVENTJOB                  | UserType.USER_DEF
            SubscriptionType.EBM          | 2     | 2     | 2               | 2         | 'SGSN-MME' | true      | AdministrationState.ACTIVE     | PREDEF_EBMLOG_SCANNER               | EVENTJOB                  | UserType.USER_DEF
            SubscriptionType.EBM          | 100   | 234   | 100             | 100       | 'SGSN-MME' | true      | AdministrationState.ACTIVE     | PREDEF_EBMLOG_SCANNER               | EVENTJOB                  | UserType.USER_DEF
            SubscriptionType.EBM          | 0     | 234   | 100             | 100       | 'SGSN-MME' | true      | AdministrationState.INACTIVE   | PREDEF_EBMLOG_SCANNER               | EVENTJOB                  | UserType.USER_DEF//Not active so ignored
            SubscriptionType.EBM          | 0     | 234   | 100             | 100       | 'SGSN-MME' | true      | AdministrationState.ACTIVATING | PREDEF_EBMLOG_SCANNER               | EVENTJOB                  | UserType.USER_DEF//Not active so ignored
            SubscriptionType.EBM          | 0     | 10    | 6               | 6         | 'SGSN-MME' | false     | AdministrationState.ACTIVE     | PREDEF_EBMLOG_SCANNER               | EVENTJOB                  | UserType.USER_DEF//PmFunction off
            SubscriptionType.EBM          | 0     | 5     | 5               | 0         | 'SGSN-MME' | true      | AdministrationState.ACTIVE     | PREDEF_EBMLOG_SCANNER               | EVENTJOB                  | UserType.USER_DEF//No available scanners
    }

    @Unroll
    def 'Deactivation events are sent for nodes with duplicate scanners #subscriptionType, #tasks'() {
        listener.isMaster() >> true
        given: 'A subscription with two nodes, one with a duplicated scanner'
            subscriptionMO = builderMap.get(subscriptionType).
                name('subscription').
                administrativeState(adminState).
                operationalState(OperationalState.RUNNING).
                setUserType(userType.name()).
                taskStatus(TaskStatus.OK).
                addEvent('group', 'name').
                build()
            for (int i = 0; i < nodes; i++) {
                def node = buildNode(NODE_NAME_1 + i, neType)
                if (i < duplicateScanners) {
                    buildScanner(node.name, processName1, subscriptionMO, true, processType)
                    buildScanner(node.name, processName2, subscriptionMO, true, processType)
                } else {
                    buildScanner(node.name, processName1, subscriptionMO, true, processType)
                }
                dpsUtils.addAssociation(subscriptionMO, 'nodes', node)
            }

        when: 'The subscription audit runs'
            subscriptionAuditorTimer.onTimeout()
            final List<Scanner> scanners = scannerDao.findAllBySubscriptionId(subscriptionMO.poId)

        then: 'The expected number of activation tasks are sent for the missing node(s)'
            tasks * eventSender.send(_ as SubscriptionDeactivationTaskRequest)

        and: 'A scanner has the subscription ID assigned before sending the task, except for STATS'
            scanners.size() == expectedScanners

        where:
            subscriptionType             | tasks | nodes | duplicateScanners | expectedScanners | neType | pmEnabled | adminState                     | processName1                   | processName2                   | processType               | userType
            SubscriptionType.CELLTRACE   | 1     | 2     | 1                 | 3                | 'ERBS' | true      | AdministrationState.ACTIVE     | PREDEF_10000_CELLTRACE_SCANNER | PREDEF_10001_CELLTRACE_SCANNER | NORMAL_PRIORITY_CELLTRACE | UserType.USER_DEF
            SubscriptionType.CELLTRACE   | 2     | 2     | 2                 | 4                | 'ERBS' | true      | AdministrationState.ACTIVE     | PREDEF_10000_CELLTRACE_SCANNER | PREDEF_10001_CELLTRACE_SCANNER | NORMAL_PRIORITY_CELLTRACE | UserType.USER_DEF
            SubscriptionType.CELLTRACE   | 100   | 234   | 100               | 334              | 'ERBS' | true      | AdministrationState.ACTIVE     | PREDEF_10000_CELLTRACE_SCANNER | PREDEF_10001_CELLTRACE_SCANNER | NORMAL_PRIORITY_CELLTRACE | UserType.USER_DEF
            SubscriptionType.CELLTRACE   | 0     | 234   | 100               | 334              | 'ERBS' | true      | AdministrationState.INACTIVE   | PREDEF_10000_CELLTRACE_SCANNER | PREDEF_10001_CELLTRACE_SCANNER | NORMAL_PRIORITY_CELLTRACE | UserType.USER_DEF//Not active so ignored
            SubscriptionType.CELLTRACE   | 0     | 234   | 100               | 334              | 'ERBS' | true      | AdministrationState.ACTIVATING | PREDEF_10000_CELLTRACE_SCANNER | PREDEF_10001_CELLTRACE_SCANNER | NORMAL_PRIORITY_CELLTRACE | UserType.USER_DEF//Not active so ignored
            SubscriptionType.CELLTRACE   | 6     | 10    | 6                 | 16               | 'ERBS' | false     | AdministrationState.ACTIVE     | PREDEF_10001_CELLTRACE_SCANNER | PREDEF_10003_CELLTRACE_SCANNER | NORMAL_PRIORITY_CELLTRACE | UserType.USER_DEF//PmFunction off
            SubscriptionType.CELLTRAFFIC | 2     | 2     | 1                 | 3                | 'ERBS' | true      | AdministrationState.ACTIVE     | PREDEF_20000_CTR_SCANNER       | PREDEF_20001_CTR_SCANNER       | CTR                       | UserType.USER_DEF
            SubscriptionType.CELLTRAFFIC | 4     | 2     | 2                 | 4                | 'ERBS' | true      | AdministrationState.ACTIVE     | PREDEF_20000_CTR_SCANNER       | PREDEF_20001_CTR_SCANNER       | CTR                       | UserType.USER_DEF
            SubscriptionType.CELLTRAFFIC | 200   | 234   | 100               | 334              | 'ERBS' | true      | AdministrationState.ACTIVE     | PREDEF_20000_CTR_SCANNER       | PREDEF_20001_CTR_SCANNER       | CTR                       | UserType.USER_DEF
            SubscriptionType.CELLTRAFFIC | 0     | 234   | 100               | 334              | 'ERBS' | true      | AdministrationState.INACTIVE   | PREDEF_20000_CTR_SCANNER       | PREDEF_20001_CTR_SCANNER       | CTR                       | UserType.USER_DEF//Not active so ignored
            SubscriptionType.CELLTRAFFIC | 0     | 234   | 100               | 334              | 'ERBS' | true      | AdministrationState.ACTIVATING | PREDEF_20000_CTR_SCANNER       | PREDEF_20001_CTR_SCANNER       | CTR                       | UserType.USER_DEF//Not active so ignored
            SubscriptionType.CELLTRAFFIC | 12    | 10    | 6                 | 16               | 'ERBS' | false     | AdministrationState.ACTIVE     | PREDEF_20001_CTR_SCANNER       | PREDEF_20000_CTR_SCANNER       | CTR                       | UserType.USER_DEF//PmFunction off
    }

    @Unroll
    def 'Activation events are only sent on the master node: isMaster #isMaster'() {
        given: 'A subscription with two nodes, one with a missing scanner'
            listener.isMaster() >> isMaster
            subscriptionMO = builderMap.get(SubscriptionType.CELLTRACE).
                name('subscription').
                administrativeState(AdministrationState.ACTIVE).
                operationalState(OperationalState.RUNNING).
                setUserType(UserType.USER_DEF.name()).
                taskStatus(TaskStatus.OK).
                addEvent('group', 'name').
                build()
            0.upto(1) {
                def node = buildNode(NODE_NAME_1 + it, 'ERBS')
                buildScanner(node.name, PREDEF_10000_CELLTRACE_SCANNER, subscriptionMO, false, NORMAL_PRIORITY_CELLTRACE)
                dpsUtils.addAssociation(subscriptionMO, 'nodes', node)
            }

        when: 'The subscription audit runs'
            subscriptionAuditorTimer.onTimeout()
            final List<Scanner> scanners = scannerDao.findAllBySubscriptionId(subscriptionMO.getPoId())

        then: 'The expected number of activation tasks are sent for the missing node(s)'
            expectedTasks * eventSender.send(
                { request -> request.getSubscriptionId() == Long.toString(subscriptionMO.getPoId()) } as SubscriptionActivationTaskRequest)

        and: 'A scanner has the subscription ID assigned before sending the task'
            scanners.size() == expectedTasks

        where:
            isMaster | expectedTasks
            true     | 2
            false    | 0
    }

    @Unroll
    def 'Stats system defined subscription #subscriptionName audit should not be call and should not call task validator'() {
        given:
            subscriptionMO = dps.subscription().
                type(SubscriptionType.STATISTICAL).
                name(subscriptionName).
                administrationState(AdministrationState.ACTIVE).
                userType(UserType.SYSTEM_DEF).
                taskStatus(TaskStatus.OK).
                build()

        when:
            subscriptionAuditorTimer.onTimeout()

        then: 'system defined statistical Subscription status should not validator from subscription audit'
            subscriptionDao.findOneById(subscriptionMO.poId).taskStatus == TaskStatus.OK

        where:
            subscriptionName << ['ERBS System Defined Statistical Subscription', 'RBS System Defined Statistical Subscription',
                                 'RNC Primary System Defined Statistical Subscription', 'RNC Secondary System Defined Statistical Subscription',
                                 'RadioNode System Defined Statistical Subscription']
    }

    def 'Subscription Audit for RES will send activation/deactivation based updated attached node list'() {
        listener.isMaster() >> true
        given: 'explicit and attached nodes'
            def rncNodeMo = buildNode('RNCNode', 'RNC')
            def rbsNode1 = buildNode('RBSNode1', 'RBS')
            def rbsNode2 = buildNode('RBSNode2', 'RBS')

        and: 'an active RES subscription with RBSNode1 as attached and associated scanners'
            def subAttributes = [resSpreadingFactor: ['SF_32'],
                                 applyOnAllCells   : true]
            subscriptionMO = dps.subscription().type(SubscriptionType.RES).
                name('res_sub').
                administrationState(AdministrationState.ACTIVE).
                operationalState(OperationalState.RUNNING).
                userType(UserType.USER_DEF).
                taskStatus(TaskStatus.OK).
                attributes(subAttributes).
                nodes(rncNodeMo).
                attachedNodes(rbsNode1).
                build()
            long subId = subscriptionMO.getPoId()
            buildScanner(rncNodeMo.name, 'USERDEF-res_sub.Cont.Y.STATS', subscriptionMO, true, STATS)
            buildScanner(rbsNode1.name, 'USERDEF-res_sub.Cont.Y.STATS', subscriptionMO, true, STATS)

        and: 'new set of attached nodes contains only RBSNode2'
            List<Node> newAttachedNodes = new ArrayList<>()
            newAttachedNodes.add(new Node(new Entity(rbsNode2.getType(), rbsNode2.getPoId(), rbsNode2.getFdn())))
            pmResLookup.fetchAttachedNodes(_, _, _, _) >>> [newAttachedNodes, [], []]

        when: 'Audit run'
            subscriptionAuditorTimer.onTimeout()

        then: 'one activation and one deactivation sent'
            1 * eventSender.send(
                { request -> request.getSubscriptionId() == Long.toString(subscriptionMO.getPoId()) } as SubscriptionActivationTaskRequest)
            1 * eventSender.send(
                { request -> request.getSubscriptionId() == Long.toString(subscriptionMO.getPoId()) } as SubscriptionDeactivationTaskRequest)

        and: 'subscription attached nodes updated'
            Set<String> updatedAttachedNodes = ((ResSubscription) subscriptionReadOperationService.findOneById(subId, true)).getAttachedNodesFdn()
            updatedAttachedNodes.equals(Collections.singleton(rbsNode2.getFdn()))
    }

    def 'task status for subscription will update to OK from ERROR if scanner count is the same as subscription node count'() {
        listener.isMaster() >> true
        given: 'an active subscription with 2 nodes and 2 scanners'
            def subscriptionName = 'TEST'
            def node1 = buildNode('NODE_1', 'RadioNode')
            def node2 = buildNode('NODE_2', 'RadioNode')

            subscriptionMO = dps.subscription().
                type(SubscriptionType.CELLTRACE).
                name(subscriptionName).
                administrationState(AdministrationState.ACTIVE).
                userType(UserType.USER_DEF).
                taskStatus(TaskStatus.ERROR).
                nodes(node1, node2).
                build()
            buildScanner(node1.name, 'PREDEF.10002.CELLTRACE', subscriptionMO, true, NORMAL_PRIORITY_CELLTRACE)
            buildScanner(node2.name, 'PREDEF.10002.CELLTRACE', subscriptionMO, true, NORMAL_PRIORITY_CELLTRACE)

        when: 'The subscription audit runs'
            subscriptionAuditorTimer.onTimeout()

        then: 'the subscriptions task status should be set to OK during subscription audit'
            subscriptionDao.findOneById(subscriptionMO.poId).taskStatus == TaskStatus.OK
    }

    List<MoTypeInstanceInfo> getMoTypeInstances(final int nodeCount) {
        List<MoTypeInstanceInfo> moTypeInstanceInfos = new ArrayList<>()
        for (int i = 0; i < nodeCount; i++) {
            moTypeInstanceInfos.add(new MoTypeInstanceInfo(NODE_NAME_1 + i, Arrays.asList(NODE_NAME_1 + i + 'moTypeInstance')));
        }
        return moTypeInstanceInfos
    }

    List<CellInfo> getCells(final int nodeCount) {
        List<CellInfo> cells = new ArrayList<>()
        for (int i = 0; i < nodeCount; i++) {
            cells.add(new CellInfo(NODE_NAME_1 + i, NODE_NAME_1 + i + 'cell'),)
        }
        return cells
    }

}
