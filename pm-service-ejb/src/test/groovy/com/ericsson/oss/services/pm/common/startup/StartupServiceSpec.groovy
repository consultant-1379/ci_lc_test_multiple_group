/*******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.common.startup

import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_PMIC_SUPPORTED_ROP_PERIODS
import static com.ericsson.oss.pmic.cdi.test.util.Constants.*
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.*
import static com.ericsson.oss.services.pm.initiation.common.Constants.PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME
import static com.ericsson.oss.services.pm.initiation.common.Constants.PMIC_CTUM_SUBSCRIPTION_NAME
import static com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants.ONE_MINUTE_IN_MILLISECONDS

import spock.lang.Unroll

import javax.ejb.TimerConfig
import javax.ejb.TimerService
import javax.inject.Inject
import java.lang.reflect.Field

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.PmBaseSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.*
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.pmic.impl.counters.PmCountersLifeCycleResolverImpl
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilityReaderImpl
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.initiation.config.event.ConfigurationParameterUpdateEvent
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener
import com.ericsson.oss.services.pm.initiation.ejb.CounterConflictServiceImpl
import com.ericsson.oss.services.pm.initiation.model.subscription.JobStatus
import com.ericsson.oss.services.pm.initiation.rest.response.ConflictingCounterGroup
import com.ericsson.oss.services.pm.initiation.rest.response.ConflictingNodeCounterInfo
import com.ericsson.oss.services.pm.initiation.schedulers.SubscriptionInitiationManager
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes
import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo
import com.ericsson.oss.services.pm.collection.schedulers.FileCollectionTaskSenderBean.TaskSenderTimerConfig

class StartupServiceSpec extends PmBaseSpec {

    static final String STARTUP_RECOVERY_TIMER = "PM_SERVICE_STARTUP_RECOVERY_SCHEDULER_TIMER"
    static final String SUBSRIPTION_SCHEDULER_INIT_TIMER_NAME = "PM_SERVICE_SUBSCRIPTION_SCHEDULER_TIMER"
    static final String INITIATION_HA_TIMER_NAME = "PM_SERVICE_INITIATION_HA_TIMER"
    static final long INTERVAL_TIME_IN_MILLISECONDS = 20000

    @ObjectUnderTest
    StartupService startupService

    def timersCreated = []

    @ImplementationInstance
    TimerService timerService = [
            getTimers          : { [] },
            createIntervalTimer: { a, b, c -> timersCreated += c; null },
            createTimer        : { a, b ->
                timersCreated += b; null
            }
    ] as TimerService

    @ImplementationInstance
    SupportedRopTimes supportedRopTime = [
        getRopTime : {a -> return new RopTimeInfo(a, a)}
    ] as SupportedRopTimes

    @ImplementationInstance
    MembershipListener listener = Mock()

    @ImplementationInstance
    SubscriptionInitiationManager subscriptionInitiationManager = Mock(SubscriptionInitiationManager)

    @ImplementationClasses
    def implementationClasses = [PmCapabilityReaderImpl.class, PmCountersLifeCycleResolverImpl.class]

    @Inject
    ConfigurationChangeListener configurationChangeListener

    @Inject
    CounterConflictServiceImpl counterConflictCacheService

    @Inject
    SubscriptionDao subscriptionDao

    @Inject
    PmFunctionEnabledWrapper pmFunctionCache

    ManagedObject migratedSubscription = Mock(ManagedObject)

    TimerConfig fifteenMinuteConfig = new TimerConfig(900, false)
    TimerConfig fifteenMinuteConfig2 = new TimerConfig(900, false)
    TimerConfig twentyHourConfig = new TimerConfig(84600, false)
    TimerConfig oneMinuteConfig = new TimerConfig(60, false)
    TimerConfig subscriptionSchedulerConfig = new TimerConfig(SUBSRIPTION_SCHEDULER_INIT_TIMER_NAME, false)
    TimerConfig transitionConfig = new TimerConfig(INITIATION_HA_TIMER_NAME, false)
    TimerConfig intervalConfig = new TimerConfig(INTERVAL_TIME_IN_MILLISECONDS, false)
    TimerConfig pollingSchedulerConfig
    TimerConfig systemDefinedAuditConfig

    def timersInfoList

    def erbsnodeNames = [NODE_NAME_1, NODE_NAME_2, NODE_NAME_3]
    def erbsnodes = []
    def sgsnnodeNames = [
            SGSN_NODE_NAME_1,
            SGSN_NODE_NAME_2,
            SGSN_NODE_NAME_3
    ]
    def sgsnnodes = []

    def subscriptionBuilders = [
            StatisticalSubscriptionBuilder.class,
            CellTraceSubscriptionBuilder.class,
            EbmSubscriptionBuilder.class,
            UeTraceSubscriptionBuilder.class
    ]


    def setup() {
        pollingSchedulerConfig = new TimerConfig(configurationChangeListener.getScannerPollingIntervalMinutes() * ONE_MINUTE_IN_MILLISECONDS, false)
        systemDefinedAuditConfig = new TimerConfig(configurationChangeListener.getSysDefSubscriptionAuditInterval()
                * ONE_MINUTE_IN_MILLISECONDS, false)
        timersInfoList = [
                STARTUP_RECOVERY_TIMER,
                fifteenMinuteConfig,
                fifteenMinuteConfig2,
                twentyHourConfig,
                oneMinuteConfig,
                subscriptionSchedulerConfig,
                transitionConfig,
                intervalConfig,
                pollingSchedulerConfig,
                systemDefinedAuditConfig
        ]
    }

    def "physical dps not ready"() {
        given:
        TimerService mockTimerService = Mock()
        InstrumentationTimer mockInstrumentationTimer = Mock()

        mockClassAttribute("dataPersistenceService", null)
        mockClassAttribute("timerService", mockTimerService)
        mockClassAttribute("instrumentationTimer", mockInstrumentationTimer)
        when:
        startupService.startAllTasks()
        then:
        1 * mockTimerService.createSingleActionTimer(_, _)
        0 * mockInstrumentationTimer.createTimers()
    }

    @Unroll
    def "On Timeout with data set [is master :#isMaster, pmFunction on :#pmEnabled, systemDefinedSubscriptions created :#createsystemDefined ]"() {
        given: "Subscriptions exists in dps"
        createSubscriptions(createsystemDefined)
        addNodes(pmEnabled)
        and: "Membership Listener is set"
        listener.isMaster() >> isMaster
        when: "Startup service is started"
        startupService.startAllTasks()
        then:
        erbsnodes.each {
            pmFunctionCache.containsFdn(it.fdn) == pmEnabled
        }
        sgsnnodes.each {
            pmFunctionCache.containsFdn(it.fdn) == pmEnabled
        }

        and:
        1 * subscriptionInitiationManager.createTimerOnStartup()
        where:
        isMaster | pmEnabled | createsystemDefined
        false    | false     | false
        false    | true      | false
        true     | false     | false
        true     | true      | false
        false    | false     | true
        false    | true      | true
        true     | false     | true
        true     | true      | true
    }

    @Unroll
    def "counters will be added for #subType.getSimpleName() subscriptions"() {
        given: "3 nodes in DPS and Active statistical subscription with 2 counters"
        ManagedObject nodeMO1 = nodeUtil.builder(NODE_NAME_1).neType(NetworkElementType.ERBS).ossModelIdentity("18.Q2-J.1.280").build()
        ManagedObject nodeMO2 = nodeUtil.builder(NODE_NAME_2).
                neType(NetworkElementType.ERBS).
                ossModelIdentity("18.Q2-J.1.280").
                pmEnabled(false).
                build()
        ManagedObject nodeMO3 = nodeUtil.builder(NODE_NAME_3).neType(NetworkElementType.ERBS).ossModelIdentity("18.Q2-J.1.280").build()

        List<CounterInfo> counters = [
                new CounterInfo("pmLicDlCapActual", "BbProcessingResource"),
                new CounterInfo("pmLicDlCapDistr", "BbProcessingResource")
        ]
        def subName = "Test"
        def subMO = subBuilder.newInstance(dpsUtils)
                .counters(counters)
                .nodes(nodeMO1, nodeMO2, nodeMO3).taskStatus(TaskStatus.OK)
                .administrativeState(AdministrationState.ACTIVE)
                .userType(UserType.USER_DEF)
                .build()
        Subscription subscription = subscriptionDao.findOneById(subMO.getPoId(), true)

        scannerUtil.builder("USERDEF-" + subName + ".Cont.Y.STATS", NODE_NAME_1).status(ScannerStatus.ACTIVE).processType(ProcessType.STATS)
                .node(nodeMO1).subscriptionId(subscription.getId()).build()
        scannerUtil.builder("USERDEF-" + subName + ".Cont.Y.STATS", NODE_NAME_2).status(ScannerStatus.ACTIVE).processType(ProcessType.STATS)
                .node(nodeMO2).subscriptionId(subscription.getId()).build()
        scannerUtil.builder("USERDEF-" + subName + ".Cont.Y.STATS", NODE_NAME_3).status(ScannerStatus.ACTIVE).processType(ProcessType.STATS)
                .node(nodeMO3).subscriptionId(subscription.getId()).build()

        listener.isMaster() >> true
        and: "for testing purpose, we update the conflict service with mirroring subscription"
        counterConflictCacheService.addNodesAndCounters([nodeMO1.getFdn(), nodeMO2.getFdn(), nodeMO3.getFdn()] as Set, counters, "ActiveSub")
        when:
        startupService.startAllTasks()
        then: "counter conflict cache has correct values for nodes and counters"
        ConflictingNodeCounterInfo result = counterConflictCacheService.getConflictingCountersInSubscription(subscription as StatisticalSubscription)
        result.getNodes() == [nodeMO1.getFdn(), nodeMO2.getFdn(), nodeMO3.getFdn()] as Set
        result.getCounterEventInfo() == [
                new ConflictingCounterGroup("BbProcessingResource", ["pmLicDlCapActual", "pmLicDlCapDistr"] as Set)
        ]
        where:
        subBuilder << [StatisticalSubscriptionBuilder, MoinstanceSubscriptionBuilder]
    }

    @Unroll
    def "pmicSupportedRopPeriods change event is received"() {
        given: "FIVE_MIN ropPeriod has been enabled"
        and: "ONE_MIN ropPeriod has been disabled"
            def Set<Long> oldSupported = [ 60L, 900L, 86400L ]
            def Set<Long> newSupported = [ 300L, 900L, 86400L ]
            def created = 0
        when: "change event is not received yet"
            timersCreated.each {if(it.getInfo().equals(300)) {created++}}
        then: ""
            assert created == 0
        when: "change event is received"
            startupService.configParamUpdateEventObserver(new ConfigurationParameterUpdateEvent(PROP_PMIC_SUPPORTED_ROP_PERIODS, oldSupported, newSupported))
            timersCreated.each {
                if(it.getInfo() instanceof TaskSenderTimerConfig) {
                    if(it.getInfo().ropPeriodInSeconds.equals(300)) created++
                    } else {
                        if(it.getInfo().equals(300)) created++
                    }
                }
        then: ""
            assert created == 3
    }

    def validateAfterMigration(Boolean systemDefinedCreated, Boolean pmEnabled) {
        boolean valid = true
        timersInfoList.each {
            timersCreated.contains(it) ? { valid &= true timersCreated.remove(it) } : { valid = false }
        }
        valid &= (listener.isMaster() && systemDefinedCreated) ? validateSubscriptionAudits(pmEnabled) : valid
        timersCreated.contains(systemDefinedAuditConfig) ? { valid &= true timersCreated.remove(systemDefinedAuditConfig) } : { valid = false }
        return valid
    }

    boolean validateSubscriptionAudits(Boolean pmEnabled) {
        return validateCtumAudit(pmEnabled) && validateCctrAudit(pmEnabled)
    }

    boolean validateCctrAudit(Boolean pmEnabled) {
        String subscriptionId = subscriptionDao.findIdByExactName(PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME)
        return pmEnabled ? subscriptionId != '0' : subscriptionId == '0'
    }

    boolean validateCtumAudit(Boolean pmEnabled) {
        String subscriptionId = subscriptionDao.findIdByExactName('CTUM')
        return pmEnabled ? subscriptionId != '0' : subscriptionId == '0'
    }

    def createSubscriptions(Boolean createSystemDefined) {
        subscriptionBuilders.each {
            SubscriptionBuilder builder = it.newInstance(dpsUtils)
            String name = builder.getClass().getSimpleName().replaceAll('Builder', '')
            ManagedObject subscriptionMO = builder.name(name).administrativeState(AdministrationState.ACTIVE).taskStatus(TaskStatus.OK).build()
            subscriptionMO.getAttribute('type') == 'UETRACE' ? addPmJobs(true, subscriptionMO) : erbsnodes.each {
                dpsUtils.addAssociation(subscriptionMO, 'nodes', it)
            }
        }
        createSystemDefined ? createSystemDefinedSubscriptions() : null
    }

    def createSubscriptionForMigration() {
        Map<String, Object> attributes = [(PMIC_ATT_SUBSCRIPTION_NAME)            : 'migrate',
                                          (PMIC_ATT_SUBSCRIPTION_ADMINSTATE)      : AdministrationState.ACTIVE.value(),
                                          ('jobStatus')                           : JobStatus.OK.name(),
                                          (PMIC_ATT_SUBSCRIPTION_TYPE)            : SubscriptionType.STATISTICAL.value(),
                                          (PMIC_ATT_SUBSCRIPTION_ROP_INFO)        : 900,
                                          (PMIC_ATT_SUBSCRIPTION_DESCRIPTION)     : "",
                                          (PMIC_ATT_SUBSCRIPTION_OWNER)           : "",
                                          (PMIC_ATT_SUBSCRIPTION_PERSISTENCE_TIME): new Date()]
        migratedSubscription = dpsUtils.createMoInDPSWithAttributes('StatisticalSubscription=migrate', 'pmic_stat_subscription', '1.0.0',
                'StatisticalSubscription', attributes)
    }

    def createSystemDefinedSubscriptions() {
        SubscriptionBuilder builder = new CctrSubscriptionBuilder(dpsUtils)
        builder.name(PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME).administrativeState(AdministrationState.ACTIVE).taskStatus(TaskStatus.OK).build()
        builder = new CtumSubscriptionBuilder(dpsUtils)
        builder.name(PMIC_CTUM_SUBSCRIPTION_NAME).administrativeState(AdministrationState.ACTIVE).taskStatus(TaskStatus.OK).build()
    }

    def addPmJobs(Boolean active = true, ManagedObject subscriptionMO) {
        String status = active ? 'ACTIVE' : 'INACTIVE'
        sgsnnodes.each {
            pmJobBuilder.nodeName(it as ManagedObject).processType(subscriptionMO).subscriptionId(subscriptionMO).status(status).build()
        }
    }

    def addNodes(Boolean pmEnabled) {
        erbsnodes = erbsnodeNames.collect {
            nodeUtil.builder(it).pmEnabled(pmEnabled).ossModelIdentity("18.Q2-J.1.280").build()
        }
        sgsnnodes = sgsnnodeNames.collect {
            nodeUtil.builder(it).pmEnabled(pmEnabled).attributes(['neType': 'SGSN-MME']).ossModelIdentity("16A-CP02").build()
        }
    }

    def <T> void mockClassAttribute(final String name, final T mock) {
        Field field = startupService.getClass().getDeclaredField(name)
        field.setAccessible(true)
        field.set(startupService, mock)
    }
}
