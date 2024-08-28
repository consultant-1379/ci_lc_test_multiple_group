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
package com.ericsson.oss.services.pm.bdd.collection.recovery

import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_LAST_COLLECTION_START_TIME
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_LAST_COLLECTION_START_TIME_HOLDER_FDN
import static com.ericsson.oss.services.pm.model.PMCapability.SupportedRecoveryTypes.RECOVERY_ON_STARTUP

import javax.ejb.AsyncResult
import javax.ejb.SessionContext
import javax.ejb.Timer
import javax.ejb.TimerConfig
import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.rule.SpyImplementation
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper
import com.ericsson.oss.services.pm.collection.recovery.StartupRecovery
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionDeltaRecoveryTaskRequest
import com.ericsson.oss.services.pm.generic.NodeServiceImpl
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener
import com.ericsson.oss.services.pm.initiation.pmjobs.recovery.PmJobStartupRecovery
import com.ericsson.oss.services.pm.initiation.util.RopTime

class StartUpRecoverySpec extends SkeletonSpec {
    static final String STARTUP_RECOVERY_TIMER = "PM_SERVICE_STARTUP_RECOVERY_SCHEDULER_TIMER";

    @ObjectUnderTest
    StartupRecovery startupRecovery

    @ImplementationInstance
    TimerService timerService = [
        getTimers              : { timers },
        createIntervalTimer    : { a, b, c ->
            timersCreated += c; timer
        },
        createTimer            : { a, b ->
            timersCreated += b; null
        },
        createSingleActionTimer: { a, b ->
            timersCreated += b; null
        }
    ] as TimerService

    @ImplementationInstance
    Timer timer = Mock()

    @Inject
    @Modeled
    private EventSender<MediationTaskRequest> eventSender

    @Inject
    private FileCollectionActiveTaskCacheWrapper fileCollectionActiveTasksCache

    @Inject
    private PmJobStartupRecovery pmJobStartupRecovery

    @ImplementationInstance
    private ConfigurationChangeListener configurationChangeListener = Mock()

    @ImplementationInstance
    private SessionContext sessionContext = Mock()
    @SpyImplementation
    private NodeServiceImpl nodeService

    @ImplementationInstance
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus = Mock()

    def timersCreated = []
    def timers = []

    Map<String, Object> attributes = new HashMap<>();
    def erbsNodeMo, rncNodeMo

    def setup() {
        rncNodeMo = dps.node().name("RNC03RBS01").neType("RNC").build()
        erbsNodeMo = dps.node().name("LTE01ERBS00001").neType("ERBS").build()

        def subscriptionMO = dps.subscription().
            type(SubscriptionType.STATISTICAL).
            name("StatisticalSubscription").
            administrationState(AdministrationState.INACTIVE).
            nodes(rncNodeMo).
            build()

        dps.subscription().
            type(SubscriptionType.CELLTRACE).
            name("CellTraceFileSubscription").
            administrationState(AdministrationState.INACTIVE).
            nodes(erbsNodeMo).
            build()

        def celltraceStreamSubscriptionMO = dps.subscription().
            type(SubscriptionType.CELLTRACE).
            name("CellTraceStreamSubscription").
            administrationState(AdministrationState.ACTIVE).
            nodes(erbsNodeMo).
            outputMode(OutputModeType.STREAMING).
            build()

        dps.scanner().
            nodeName(rncNodeMo).
            name("USERDEF.StatisticalSubscription.Cont.Y.STATS").
            processType(ProcessType.STATS).
            status(ScannerStatus.ACTIVE).
            fileCollectionEnabled(true).
            subscriptionId(subscriptionMO).
            build()
        dps.scanner().
            nodeName(erbsNodeMo).
            name("PREDEF.10003.CELLTRACE").
            processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
            subscriptionId(celltraceStreamSubscriptionMO).
            status(ScannerStatus.ACTIVE).
            fileCollectionEnabled(false).
            build()
        dps.scanner().
            nodeName(erbsNodeMo).
            name("PREDEF.10002.CELLTRACE").
            processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
            subscriptionId(celltraceStreamSubscriptionMO).
            status(ScannerStatus.INACTIVE).
            fileCollectionEnabled(true).
            build()

        nodeService.isMediationAutonomyEnabled(_ as String) >> false
        nodeService.isRecoveryTypeSupported(_ as String, RECOVERY_ON_STARTUP.name()) >> true
    }
// commented due to sonar qube job instability
 /*   def "StartupRecovery sends a FileRecovery task with 96 ROPs for 24 hours recovery when lastCollectionStartedTime is set with 0"() {
        given: "lastCollectionStartedTime is set with 0 and the PIB 'fileRecoveryHoursInfo' is set with 24"
            attributes.put(PMIC_ATT_LAST_COLLECTION_START_TIME, new Long(0));
            dpsUtils.createMoInDPSWithAttributes(PMIC_LAST_COLLECTION_START_TIME_HOLDER_FDN, "pmic_stat_subscription", "2.1.0",
                "LastCollectionStartTimeHolder", attributes);
            configurationChangeListener.getStartupFileRecoveryHoursInfo() >> 24
        and: "Mock Interactions are set"
            dpsAvailabilityStatus.isAvailable() >> true
        when: "start-up recovery method is called"
            startupRecovery.startRecovery()

        then:
            "File Collection Task cache should be added with a FileCollectionDeltaRecoveryTaskRequest event which will be sent out with the " +
                "eventSender"
            1 * eventSender.send({ request ->
                request.numberOfRops == (96 + 1);
                request.ropStartTime == new RopTime(System.currentTimeMillis(), 900).getCurrentRopStartTimeInMilliSecs()
            } as FileCollectionDeltaRecoveryTaskRequest)
    }

    def "StartupRecovery sends a FileRecovery task with 96 ROPs for 24 hours recovery on 2nd attempt, if an Unknown Exception occurred in Ist attempt"() {
        given: "lastCollectionStartedTime is set with 0"
            attributes.put(PMIC_ATT_LAST_COLLECTION_START_TIME, new Long(0));
            dpsUtils.createMoInDPSWithAttributes(PMIC_LAST_COLLECTION_START_TIME_HOLDER_FDN, "pmic_stat_subscription", "2.1.0",
                "LastCollectionStartTimeHolder", attributes);

        and: "PIB 'fileRecoveryHoursInfo' is set with 24"
            configurationChangeListener.getStartupFileRecoveryHoursInfo() >> 24

        and: "Mock Interactions first scenario throws an exception and second scenario is successful"
            dpsAvailabilityStatus.isAvailable() >> false >> true
            sessionContext.getBusinessObject(PmJobStartupRecovery.class) >> pmJobStartupRecovery >> pmJobStartupRecovery

        when: "Start-up recovery method is called on Ist Timeout"
            startupRecovery.checkMembership() // Ist TimeOut

        then: "File Collection Task cache should not be added with a FileCollectionDeltaRecoveryTaskRequest event"
            0 * eventSender.send(*_)
            fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size())

        expect: "start-up one attempts and recovery not to be completed"
            with(startupRecovery) {
                assert attempts == 1
                assert isRecoveryDone == false
            }

        when: "Start-up recovery method is called on 2nd Timeout"
            startupRecovery.checkMembership() // 2nd TimeOut

        then: "File Collection Task cache should be added with a FileCollectionDeltaRecoveryTaskRequest event which is sent by eventSender"
            1 * eventSender.send({ request ->
                request.numberOfRops == (96 + 1);
                request.ropStartTime == new RopTime(System.currentTimeMillis(), 900).getCurrentRopStartTimeInMilliSecs()
            } as FileCollectionDeltaRecoveryTaskRequest)
            fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) + 1

        expect: "Start-up two attempts and recovery to be completed"
            with(startupRecovery) {
                assert attempts == 2
                assert isRecoveryDone == true
            }
    }

    def "StartupRecovery sends a FileRecovery task with 4 ROPs for 1 hour downtime recovery when lastCollectionStartedTime is set with 1 hour from current time"() {
        given: "lastCollectionStartedTime is set with 1 hour from the current time  and the PIB 'fileRecoveryHoursInfo' is set with 24"
            attributes.put(PMIC_ATT_LAST_COLLECTION_START_TIME, ((Long) System.currentTimeMillis() - 3600000));
            dpsUtils.createMoInDPSWithAttributes(PMIC_LAST_COLLECTION_START_TIME_HOLDER_FDN, "pmic_stat_subscription", "2.1.0",
                "LastCollectionStartTimeHolder", attributes);

            configurationChangeListener.getStartupFileRecoveryHoursInfo() >> 24
        and: "Mock Interactions are set"
            dpsAvailabilityStatus.isAvailable() >> true
        when: "start-up recovery method is called"
            startupRecovery.startRecovery()

        then:
            "File Collection Task cache should be added with a FileCollectionDeltaRecoveryTaskRequest event which will be sent out with the " +
                "eventSender"
            1 * eventSender.send({ request ->
                request.numberOfRops == (4 + 1);
                request.ropStartTime == new RopTime(System.currentTimeMillis(), 900).getCurrentRopStartTimeInMilliSecs()
            } as FileCollectionDeltaRecoveryTaskRequest)
    }

    def "StartupRecovery will remove the extra tasks from cache when cache is not empty and have extra tasks."() {
        given: "lastCollectionStartedTime is set with 1 hour from the current time  and the PIB 'fileRecoveryHoursInfo' is set with 24"
            attributes.put(PMIC_ATT_LAST_COLLECTION_START_TIME, ((Long) System.currentTimeMillis() - 3600000));
            dpsUtils.createMoInDPSWithAttributes(PMIC_LAST_COLLECTION_START_TIME_HOLDER_FDN, "pmic_stat_subscription", "2.1.0",
                "LastCollectionStartTimeHolder", attributes);
            configurationChangeListener.getStartupFileRecoveryHoursInfo() >> 24
        and: "Mock Interactions are set"
            dpsAvailabilityStatus.isAvailable() >> true
        and: "Add Process Request to fileCollectionActiveTasksCache"
            final ProcessRequestVO processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(erbsNodeMo.getFdn(), 900,
                ProcessType.NORMAL_PRIORITY_CELLTRACE.name()).build();
            fileCollectionActiveTasksCache.addProcessRequest(processRequestVO)

            processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(rncNodeMo.getFdn(), 900, ProcessType.STATS.name()).build();
            fileCollectionActiveTasksCache.addProcessRequest(processRequestVO)

        when: "start-up recovery method is called"
            startupRecovery.checkMembership()

        then: "File Collection task request should be removed from the fileCollectionActiveTasksCache"
            fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) - 1

        expect: "start-up one attempts and recovery to be completed"
            with(startupRecovery) {
                assert attempts == 1
                assert isRecoveryDone == true
            }
    }

    def "StartupRecovery will send recovery tasks anyway if fileCollectionActiveTasksCache has been found empty at least once"() {
        given: "lastCollectionStartedTime is set with 1 hour from the current time  and the PIB 'fileRecoveryHoursInfo' is set with 24"
            attributes.put(PMIC_ATT_LAST_COLLECTION_START_TIME, ((Long) System.currentTimeMillis() - 3600000));
            dpsUtils.createMoInDPSWithAttributes(PMIC_LAST_COLLECTION_START_TIME_HOLDER_FDN, "pmic_stat_subscription", "2.1.0",
                "LastCollectionStartTimeHolder", attributes);
            configurationChangeListener.getStartupFileRecoveryHoursInfo() >> 24
        and: "Mock Interactions first scenario throws an exception and second scenario is successful"
            dpsAvailabilityStatus.isAvailable() >> false >> true
            sessionContext.getBusinessObject(PmJobStartupRecovery.class) >> pmJobStartupRecovery >> pmJobStartupRecovery

        when: "Start-up recovery method is called on Ist Timeout"
            startupRecovery.checkMembership() // Ist TimeOut

        then: "File Collection Task cache should not be added with a FileCollectionDeltaRecoveryTaskRequest event"
            0 * eventSender.send(*_)
            fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size())

        expect: "start-up one attempts and recovery not to be completed"
            with(startupRecovery) {
                assert attempts == 1
                assert isRecoveryDone == false
            }

        when: "Add Process Requests to fileCollectionActiveTasksCache"
            final ProcessRequestVO processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(rncNodeMo.getFdn(), 900, ProcessType.STATS.name()).
                build();
            fileCollectionActiveTasksCache.addProcessRequest(processRequestVO)
        and: "Start-up recovery method is called on 2nd Timeout"
            startupRecovery.checkMembership() // 2nd TimeOut

        then: "File Collection Task cache should be added with a FileCollectionDeltaRecoveryTaskRequest event which is sent by eventSender"
            1 * eventSender.send({ request ->
                request.numberOfRops == (96 + 1);
                request.ropStartTime == new RopTime(System.currentTimeMillis(), 900).getCurrentRopStartTimeInMilliSecs()
            } as FileCollectionDeltaRecoveryTaskRequest)
            fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) + 1

        expect: "Start-up two attempts and recovery to be completed"
            with(startupRecovery) {
                assert attempts == 2
                assert isRecoveryDone == true
            }
    }
*/
    def "StartupRecovery will not remove PmJob based process requests when cache is not empty"() {
        given: "lastCollectionStartedTime is set with 1 hour from the current time  and the PIB 'fileRecoveryHoursInfo' is set with 24"
            attributes.put(PMIC_ATT_LAST_COLLECTION_START_TIME, ((Long) System.currentTimeMillis() - 3600000));
            dpsUtils.createMoInDPSWithAttributes(PMIC_LAST_COLLECTION_START_TIME_HOLDER_FDN, "pmic_stat_subscription", "2.1.0",
                "LastCollectionStartTimeHolder", attributes);

            configurationChangeListener.getStartupFileRecoveryHoursInfo() >> 24
        and: "Mock Interactions are set"
            dpsAvailabilityStatus.isAvailable() >> true
        and: "Add Process Requests to fileCollectionActiveTasksCache"
            final ProcessRequestVO processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(rncNodeMo.getFdn(), 900, ProcessType.STATS.name()).
                build();
            fileCollectionActiveTasksCache.addProcessRequest(processRequestVO)

            processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(rncNodeMo.getFdn(), 900, ProcessType.CTUM.name()).build();
            fileCollectionActiveTasksCache.addProcessRequest(processRequestVO)

        when: "start-up recovery method is called"
            startupRecovery.startRecovery()

        then: "File Collection task request for CTUM should not be removed"
            fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size())
    }


    def "When create Timer is called should create timer in timer service for Membership Check"() {
        when: "When createTimer is called"
            timersCreated = []
            AsyncResult<Boolean> result = startupRecovery.createTimerForMembershipCheck()
            TimerConfig timerConfig = timersCreated.get(0) as TimerConfig

        then: "Timer created should have correct info and result should be true"
            result.get()
            timerConfig.info == STARTUP_RECOVERY_TIMER
            !timerConfig.persistent

        when: "Timer Service mocks are set"
            createTimerServiceMocks()
        and: "When reset Timer is called"
            startupRecovery.cancelTimerForMembershipCheck()

        then: "Timer created should have correct info and result should be true"
            1 * timer.cancel()
    }

    def createTimerServiceMocks() {
        timersCreated.each {
            timer.getInfo() >> it.info
            timers.add(timer)
        }
    }
}
