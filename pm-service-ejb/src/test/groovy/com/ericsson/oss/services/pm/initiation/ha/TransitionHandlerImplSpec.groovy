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

package com.ericsson.oss.services.pm.initiation.ha

import org.mockito.Mockito
import spock.lang.Unroll

import javax.ejb.AsyncResult
import javax.ejb.Timer
import javax.ejb.TimerConfig
import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.configuration.InjectionProperties
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.*
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener
import com.ericsson.oss.services.pm.initiation.notification.model.InitiationScheduleModel
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener

class TransitionHandlerImplSpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    TransitionHandlerImpl handler

    @ImplementationInstance
    ConfigurationChangeListener configurationChangeListener = Mock()
    @Inject
    EventSender<MediationTaskRequest> eventSender
    @Inject
    PMICInitiationTrackerCache cache
    @ImplementationInstance
    MembershipListener membershipListener = mock(MembershipListener)


    @ImplementationInstance
    TimerService timerService = [
            getTimers              : { timers },
            createIntervalTimer    : { a, b, c -> timersCreated += c; null },
            createTimer            : { a, b -> timersCreated += b; null },
            createSingleActionTimer: { a, b -> timersCreated += b; null }
    ] as TimerService

    @ImplementationInstance
    Timer timer = Mock()

    @ImplementationInstance
    TimeGenerator timeGenerator = Mockito.mock(TimeGenerator)

    static final String initiationHaTimerName = "PM_SERVICE_INITIATION_HA_TIMER";

    def timersCreated = []
    def timers = []

    List<ManagedObject> subscriptions = []
    def subscriptionIds = []

    def builders = [StatisticalSubscriptionBuilder.class, EbmSubscriptionBuilder.class, CellTraceSubscriptionBuilder.class, CctrSubscriptionBuilder.class]

    def sgsnNodes = ['SGSN-16A-V1-CP0201', 'SGSN-16A-V1-CP0202']
    def erbsNodes = ['LTE01ERBS0001', 'LTE01ERBS0002']

    List<ManagedObject> nodes = []

    def setup() {
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(System.currentTimeMillis())
        membershipListener.isMaster() >> true
    }

    @Override
    def addAdditionalInjectionProperties(InjectionProperties injectionProperties) {
        super.addAdditionalInjectionProperties(injectionProperties)
        injectionProperties.autoLocateFrom('com.ericsson.oss.services.pm')
    }

    def "When create Timer is called should create timer in timer service for Membership Check"() {
        when: "When createTimer is called"
        AsyncResult<Boolean> result = handler.createTimerForMembershipCheck()
        TimerConfig timerConfig = timersCreated.get(timersCreated.size() - 1) as TimerConfig

        then: "Timer created should have correct info and result should be true"
        result.get()
        timerConfig.info == initiationHaTimerName
        !timerConfig.persistent
    }

    def "On Membership Check should create timers for activation and deactivation for all subscriptions not in PMICInitiationTrackerCache"() {
        boolean counter = true
        given: "Subscriptions exist in dps"
        addSubscriptions(AdministrationState.ACTIVATING)

        when: "Membership check is called"
        handler.checkMembership()

        then: "should create activation and deactivation timers for all subscriptions"
        timersCreated.size() == old(timersCreated.size() + (subscriptions.size() * 2))
        timersCreated.remove(0)
        timersCreated.each { timerConfig ->
            timerConfig.getInfo().each { initiationScheduleModel ->
                (initiationScheduleModel.subscriptionId as Long) in subscriptionIds;
                (counter) ? initiationScheduleModel.eventType == AdministrationState.ACTIVATING : initiationScheduleModel.eventType == AdministrationState.DEACTIVATING
                counter = !counter
            } as InitiationScheduleModel
        }
    }

    def "On Membership Check should deactivate subscriptions with UPDATING state that have no nodes for all subscriptions not in PMICInitiationTrackerCache"() {
        given: "Subscriptions exist in dps"
        addSubscriptions(AdministrationState.UPDATING)

        when: "Membership check is called"
        handler.checkMembership()

        then: "should deactivate subscriptions"
        subscriptions.each {
            it.getAttribute("administrationState") == "INACTIVE"
            it.getAttribute("taskStatus") == "OK"
            cache.getTracker(it.getPoId() as String) == null
        }
        and:
        0 * eventSender.send(_ as MediationTaskRequest)
    }


    def "On Membership Check should cancel timers for  deactivation and create deactivation requests for all subscriptions not in PMICInitiationTrackerCache"() {
        given: "Subscriptions exist in dps"
        addSubscriptions(AdministrationState.DEACTIVATING)
        addNodes()
        addTimers()

        when: "Membership check is called"
        handler.checkMembership()

        then: "should create activation and deactivation timers for all subscriptions"
        4 * timer.cancel()
        4 * eventSender.send({ request -> request.toString().contains('SubscriptionDeactivationTaskRequest') } as MediationTaskRequest)
    }

    @Unroll
    def "On membership change, update Stats subscription status to #expectedState and task status to #expectedTaskStatus in there is a scanner with state #scanner1Status and another scanner with state #scanner2Status"() {
        given:
        ManagedObject nodeMO = nodeUtil.builder("LTE01ERBS0001").neType("ERBS").build()
        ManagedObject nodeMO1 = nodeUtil.builder("LTE01ERBS0002").neType("ERBS").build()
        ManagedObject subscriptionMO = statisticalSubscriptionBuilder.name("Test").administrativeState(AdministrationState.UPDATING).taskStatus(TaskStatus.OK).build()
        dpsUtils.addAssociation(subscriptionMO, "nodes", nodeMO, nodeMO1)
        scannerUtil.builder("USERDEF-Test.Cont.Y.STATS", "LTE01ERBS0001").subscriptionId(subscriptionMO.getPoId()).status(scanner1Status).processType(ProcessType.STATS).build()
        scannerUtil.builder("USERDEF-Test.Cont.Y.STATS", "LTE01ERBS0002").subscriptionId(subscriptionMO.getPoId()).status(scanner2Status).processType(ProcessType.STATS).build()
        when: "Membership check is called"
        handler.checkMembership()
        then: "should update admin state and task status subscription"
        subscriptionMO.getAttribute("administrationState") == expectedState
        subscriptionMO.getAttribute("taskStatus") == expectedTaskStatus
        cache.getTracker(subscriptionMO.getPoId() as String) == null
        and:
        0 * eventSender.send(_ as MediationTaskRequest)
        where:
        scanner1Status         | scanner2Status         | expectedState | expectedTaskStatus
        ScannerStatus.INACTIVE | ScannerStatus.INACTIVE | "INACTIVE"    | "OK"
        ScannerStatus.ACTIVE   | ScannerStatus.ACTIVE   | "ACTIVE"      | "OK"
        ScannerStatus.ACTIVE   | ScannerStatus.INACTIVE | "ACTIVE"      | "ERROR"
        ScannerStatus.ACTIVE   | ScannerStatus.ERROR    | "ACTIVE"      | "ERROR"
        ScannerStatus.ACTIVE   | ScannerStatus.UNKNOWN  | "ACTIVE"      | "ERROR"
    }

    @Unroll
    def "On membership change, update Celltrace subscription status to #expectedState and task status to #expectedTaskStatus in there is a scanner with state #scanner1Status and another scanner with state #scanner2Status"() {
        given:
        ManagedObject nodeMO = nodeUtil.builder("LTE01ERBS0001").neType("ERBS").build()
        ManagedObject nodeMO1 = nodeUtil.builder("LTE01ERBS0002").neType("ERBS").build()
        ManagedObject subscriptionMO = cellTraceSubscriptionBuilder.name("Test").administrativeState(AdministrationState.UPDATING).taskStatus(TaskStatus.OK).build()
        dpsUtils.addAssociation(subscriptionMO, "nodes", nodeMO, nodeMO1)
        scannerUtil.builder('PREDEF.10001.CELLTRACE', "LTE01ERBS0001").subscriptionId(subscriptionMO.getPoId()).status(scanner1Status).processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build()
        scannerUtil.builder('PREDEF.10001.CELLTRACE', "LTE01ERBS0002").subscriptionId(subscriptionMO.getPoId()).status(scanner2Status).processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build()
        when: "Membership check is called"
        handler.checkMembership()
        then: "should update admin state and task status subscription"
        subscriptionMO.getAttribute("administrationState") == expectedState
        subscriptionMO.getAttribute("taskStatus") == expectedTaskStatus
        cache.getTracker(subscriptionMO.getPoId() as String) == null
        and:
        0 * eventSender.send(_ as MediationTaskRequest)
        where:
        scanner1Status         | scanner2Status         | expectedState | expectedTaskStatus
        ScannerStatus.INACTIVE | ScannerStatus.INACTIVE | "INACTIVE"    | "OK"
        ScannerStatus.ACTIVE   | ScannerStatus.ACTIVE   | "ACTIVE"      | "OK"
        ScannerStatus.ACTIVE   | ScannerStatus.INACTIVE | "ACTIVE"      | "ERROR"
        ScannerStatus.ACTIVE   | ScannerStatus.ERROR    | "ACTIVE"      | "ERROR"
        ScannerStatus.ACTIVE   | ScannerStatus.UNKNOWN  | "ACTIVE"      | "ERROR"
    }

    @Unroll
    def "On membership change, update ContinuousCelltrace subscription status to #expectedState and task status to #expectedTaskStatus in there is a scanner with state #scanner1Status and another scanner with state #scanner2Status"() {
        given:
        ManagedObject nodeMO = nodeUtil.builder("LTE01ERBS0001").neType("ERBS").build()
        ManagedObject nodeMO1 = nodeUtil.builder("LTE01ERBS0002").neType("ERBS").build()
        ManagedObject subscriptionMO = cctrSubscriptionBuilder.name('ContinuousCellTraceSubscription').administrativeState(AdministrationState.UPDATING).taskStatus(TaskStatus.OK).build()
        dpsUtils.addAssociation(subscriptionMO, "nodes", nodeMO, nodeMO1)
        scannerUtil.builder('PREDEF.10005.CELLTRACE', "LTE01ERBS0001").subscriptionId(subscriptionMO.getPoId()).status(scanner1Status).processType(ProcessType.HIGH_PRIORITY_CELLTRACE).build()
        scannerUtil.builder('PREDEF.10005.CELLTRACE', "LTE01ERBS0002").subscriptionId(subscriptionMO.getPoId()).status(scanner2Status).processType(ProcessType.HIGH_PRIORITY_CELLTRACE).build()
        when: "Membership check is called"
        handler.checkMembership()
        then: "should update admin state and task status subscription"
        subscriptionMO.getAttribute("administrationState") == expectedState
        subscriptionMO.getAttribute("taskStatus") == expectedTaskStatus
        cache.getTracker(subscriptionMO.getPoId() as String) == null
        and:
        0 * eventSender.send(_ as MediationTaskRequest)
        where:
        scanner1Status         | scanner2Status         | expectedState | expectedTaskStatus
        ScannerStatus.INACTIVE | ScannerStatus.INACTIVE | "INACTIVE"    | "OK"
        ScannerStatus.ACTIVE   | ScannerStatus.ACTIVE   | "ACTIVE"      | "OK"
        ScannerStatus.ACTIVE   | ScannerStatus.INACTIVE | "ACTIVE"      | "ERROR"
        ScannerStatus.ACTIVE   | ScannerStatus.ERROR    | "ACTIVE"      | "ERROR"
        ScannerStatus.ACTIVE   | ScannerStatus.UNKNOWN  | "ACTIVE"      | "ERROR"
    }

    @Unroll
    def "On membership change, update Ebm subscription status to #expectedState and task status to #expectedTaskStatus in there is a scanner with state #scanner1Status and another scanner with state #scanner2Status"() {
        given:
        ManagedObject nodeMO = nodeUtil.builder("MySgsnNode").neType("SGSN-MME").build()
        ManagedObject nodeMO1 = nodeUtil.builder("MySgsnNode1").neType("SGSN-MME").build()
        ManagedObject subscriptionMO = ebmSubscriptionBuilder.name("Test").administrativeState(AdministrationState.UPDATING).taskStatus(TaskStatus.OK).build()
        dpsUtils.addAssociation(subscriptionMO, "nodes", nodeMO, nodeMO1)
        scannerUtil.builder('PREDEF.EBMLOG.EBM', "MySgsnNode").subscriptionId(subscriptionMO.getPoId()).status(scanner1Status).processType(ProcessType.EVENTJOB).build()
        scannerUtil.builder('PREDEF.EBMLOG.EBM', "MySgsnNode1").subscriptionId(subscriptionMO.getPoId()).status(scanner2Status).processType(ProcessType.EVENTJOB).build()
        when: "Membership check is called"
        handler.checkMembership()
        then: "should update admin state and task status subscription"
        subscriptionMO.getAttribute("administrationState") == expectedState
        subscriptionMO.getAttribute("taskStatus") == expectedTaskStatus
        cache.getTracker(subscriptionMO.getPoId() as String) == null
        and:
        0 * eventSender.send(_ as MediationTaskRequest)
        where:
        scanner1Status         | scanner2Status         | expectedState | expectedTaskStatus
        ScannerStatus.INACTIVE | ScannerStatus.INACTIVE | "INACTIVE"    | "OK"
        ScannerStatus.ACTIVE   | ScannerStatus.ACTIVE   | "ACTIVE"      | "OK"
        ScannerStatus.ACTIVE   | ScannerStatus.INACTIVE | "ACTIVE"      | "ERROR"
        ScannerStatus.ACTIVE   | ScannerStatus.ERROR    | "ACTIVE"      | "ERROR"
        ScannerStatus.ACTIVE   | ScannerStatus.UNKNOWN  | "ACTIVE"      | "ERROR"
    }

    @Unroll
    def "On membership change, update EuTrace subscription status to #expectedState and task status to #expectedTaskStatus in there is a scanner with state #scanner1Status and another scanner with state #scanner2Status"() {
        given:
        ManagedObject nodeMO = nodeUtil.builder("MySgsnNode").neType("SGSN-MME").build()
        ManagedObject nodeMO1 = nodeUtil.builder("MySgsnNode1").neType("SGSN-MME").build()
        ManagedObject subscriptionMO = ueTraceSubscriptionBuilder.name("Test").administrativeState(AdministrationState.UPDATING).taskStatus(TaskStatus.OK).build()
        pmJobBuilder.nodeName(nodeMO).processType(subscriptionMO).subscriptionId(subscriptionMO).status(scanner1Status.name()).build()
        pmJobBuilder.nodeName(nodeMO1).processType(subscriptionMO).subscriptionId(subscriptionMO).status(scanner2Status.name()).build()
        when: "Membership check is called"
        handler.checkMembership()
        then: "should update admin state and task status subscription"
        subscriptionMO.getAttribute("administrationState") == expectedState
        subscriptionMO.getAttribute("taskStatus") == expectedTaskStatus
        cache.getTracker(subscriptionMO.getPoId() as String) == null
        and:
        0 * eventSender.send(_ as MediationTaskRequest)
        where:
        scanner1Status         | scanner2Status         | expectedState | expectedTaskStatus
        ScannerStatus.INACTIVE | ScannerStatus.INACTIVE | "INACTIVE"    | "OK"
        ScannerStatus.ACTIVE   | ScannerStatus.ACTIVE   | "ACTIVE"      | "OK"
        ScannerStatus.ACTIVE   | ScannerStatus.INACTIVE | "ACTIVE"      | "ERROR"
        ScannerStatus.ACTIVE   | ScannerStatus.ERROR    | "ACTIVE"      | "ERROR"
        ScannerStatus.ACTIVE   | ScannerStatus.UNKNOWN  | "ACTIVE"      | "ERROR"
    }

    @Unroll
    def "On membership change, update Ctum subscription status to #expectedState and task status to #expectedTaskStatus in there is a scanner with state #scanner1Status and another scanner with state #scanner2Status"() {
        given:
        ManagedObject nodeMO = nodeUtil.builder("MySgsnNode").neType("SGSN-MME").build()
        ManagedObject nodeMO1 = nodeUtil.builder("MySgsnNode1").neType("SGSN-MME").build()
        ManagedObject subscriptionMO = ctumSubscriptionBuilder.name("Test").administrativeState(AdministrationState.UPDATING).taskStatus(TaskStatus.OK).build()
        pmJobBuilder.nodeName(nodeMO).processType(subscriptionMO).subscriptionId(subscriptionMO).status(scanner1Status.name()).build()
        pmJobBuilder.nodeName(nodeMO1).processType(subscriptionMO).subscriptionId(subscriptionMO).status(scanner2Status.name()).build()
        when: "Membership check is called"
        handler.checkMembership()
        then: "should update admin state and task status subscription"
        subscriptionMO.getAttribute("administrationState") == expectedState
        subscriptionMO.getAttribute("taskStatus") == expectedTaskStatus
        cache.getTracker(subscriptionMO.getPoId() as String) == null
        and:
        0 * eventSender.send(_ as MediationTaskRequest)
        where:
        scanner1Status         | scanner2Status         | expectedState | expectedTaskStatus
        ScannerStatus.INACTIVE | ScannerStatus.INACTIVE | "INACTIVE"    | "OK"
        ScannerStatus.ACTIVE   | ScannerStatus.ACTIVE   | "ACTIVE"      | "OK"
        ScannerStatus.ACTIVE   | ScannerStatus.INACTIVE | "ACTIVE"      | "ERROR"
        ScannerStatus.ACTIVE   | ScannerStatus.ERROR    | "ACTIVE"      | "ERROR"
        ScannerStatus.ACTIVE   | ScannerStatus.UNKNOWN  | "ACTIVE"      | "ERROR"
    }

    def addNodes() {
        def allNodes = []
        allNodes = sgsnNodes.collect {
            nodeUtil.builder(it).pmEnabled(true).attributes(['neType': 'SGSN-MME']).build()
        }
        allNodes += erbsNodes.collect {
            nodeUtil.builder(it).pmEnabled(true).build()
        }
        (0..(allNodes.size() - 1)).each {
            dpsUtils.addAssociation(subscriptions.get(it), 'nodes', allNodes.get(it))
            createScanner(subscriptionIds.get(it), allNodes.get(it))
        }
    }

    def createScanner(
            final long subId, final ManagedObject node) {
        def scannerNameProcessTypes = [1L: [ProcessType.STATS, 'USERDEF-subscription.Cont.Y.STATS'], 2L: [ProcessType.EVENTJOB, 'PREDEF.EBMLOG.EBM'],
                                       3L: [ProcessType.NORMAL_PRIORITY_CELLTRACE, 'PREDEF.10001.CELLTRACE'],
                                       4L: [ProcessType.HIGH_PRIORITY_CELLTRACE, 'PREDEF.10005.CELLTRACE']]
        ProcessType processType = scannerNameProcessTypes.get(subId).get(0)
        String scannerName = scannerNameProcessTypes.get(subId).get(1)
        ManagedObject scannerMO = scannerUtil.builder(scannerName, node.name).subscriptionId(subId).status(ScannerStatus.ACTIVE)
                .processType(processType).build()
        dpsUtils.addAssociation(node, "scanners", scannerMO)
    }

    def addSubscriptions(AdministrationState administrationState = AdministrationState.ACTIVE) {
        def (Date startTime, Date endTime) = (administrationState == AdministrationState.SCHEDULED) ? getStartAndEndTime(true) : getStartAndEndTime()
        subscriptions = builders.collect {
            SubscriptionBuilder builder = it.newInstance(dpsUtils)
            builder.name("subscription").administrativeState(administrationState).operationalState(OperationalState.RUNNING)
                    .taskStatus(TaskStatus.OK).scheduleInfo(startTime, endTime).build()
        }
        subscriptionIds = subscriptions.collect {
            it.poId
        }
    }

    def createTimerServiceMocks() {
        timersCreated.each {
            timer.getInfo() >> it.info
            timers.add(timer)
        }
    }

    private static List getStartAndEndTime(Boolean scheduled = false) {
        Calendar calendar = Calendar.getInstance()
        (scheduled) ? calendar.add(Calendar.MINUTE, 60) : null
        Date startTime = calendar.getTime()
        calendar.add(Calendar.MINUTE, 30)
        Date endTime = calendar.getTime()
        [startTime, endTime]
    }

    def addTimers() {
        def modelList = []
        subscriptions.each {
            InitiationScheduleModel initiationScheduleModel = new InitiationScheduleModel(it.poId, AdministrationState.DEACTIVATING)
            modelList.add(initiationScheduleModel)
            timers.add(timer)
        }
        timer.getInfo() >>> [modelList.get(0), modelList.get(0), modelList.get(1), modelList.get(1), modelList.get(2), modelList.get(2),
                             modelList.get(3), modelList.get(3), modelList.get(0), modelList.get(0), modelList.get(1), modelList.get(1),
                             modelList.get(2), modelList.get(2), modelList.get(3), modelList.get(3), modelList.get(0), modelList.get(0),
                             modelList.get(1), modelList.get(1), modelList.get(2), modelList.get(2), modelList.get(3)]
    }

}
