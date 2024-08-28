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

import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_FILE_RECOVERY_HOURS
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_SCHEDULED_RECOVERY_TIME
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.CONFIGURATION_CHANGE_NOTIFICATION

import com.ericsson.cds.cdi.support.rule.MockedImplementation

import javax.ejb.AsyncResult
import javax.ejb.Timer
import javax.ejb.TimerConfig
import javax.ejb.TimerService
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.node.TestNetworkElementDpsUtils
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper
import com.ericsson.oss.services.pm.collection.cache.FileCollectionScheduledRecoveryCacheWrapper
import com.ericsson.oss.services.pm.collection.recovery.ScheduledRecovery
import com.ericsson.oss.services.pm.collection.recovery.util.DstUtil
import com.ericsson.oss.services.pm.common.constants.TimeConstants
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener
import com.ericsson.oss.services.pm.time.TimeGenerator

class ScheduledRecoverySpec extends SkeletonSpec {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    private static final SimpleDateFormat fileRecoveryTimeSDF = new SimpleDateFormat("HH:mm:ss")
    private static final long INITIAL_TIME = SDF.parse("28/03/2010 02:00:00").getTime()

    @ObjectUnderTest
    ScheduledRecovery scheduledRecovery = Spy(ScheduledRecovery)

    @Inject
    protected FileCollectionActiveTaskCacheWrapper fileCollectionActiveTasksCache

    @Inject
    private FileCollectionScheduledRecoveryCacheWrapper fileCollectionScheduledRecoveryCache

    @Inject
    @Modeled
    private EventSender<MediationTaskRequest> eventSender

    @MockedImplementation
    SystemRecorderWrapperLocal systemRecorder
    @MockedImplementation
    private DstUtil dstUtil

    @ImplementationInstance
    TimerService timerService = [
            getTimers              : { timers },
            createIntervalTimer    : { a, b, c -> timersDelay += a; timersCreated += c; timerPeriod+=b; timer },
            createTimer            : { a, b -> timersCreated += b; null },
            createSingleActionTimer: { a, b -> timersCreated += b; null }
    ] as TimerService

    @ImplementationInstance
    Timer timer = Mock()
    @ImplementationInstance
    TimeGenerator timeGenerator = Mock()

    @ImplementationInstance
    MembershipListener membershipListener = Mock(MembershipListener)

    def timersDelay = []
    def timersCreated = []
    def timers = []
    def timerPeriod = []

    TestNetworkElementDpsUtils node = new TestNetworkElementDpsUtils(configurableDps)

    static final String NODE_NAME1 = 'LTE01ERBS0001'
    static final String NODE_NAME2 = 'LTE01ERBS0002'
    static final String SCHEDULED_RECOVERY_TIMER_NAME = 'Scheduled recovery timer'

    def "When create Timer is called should create timer in timer service for Membership Check"() {
        when: "When createTimer is called"
        scheduledRecovery.scheduledRecoveryTime = recoveryTime
        scheduledRecovery.fileRecoveryHoursInfo = recoveryHours
        scheduledRecovery.intermediateScheduledRecoveryTime= intermediateRecoveryTime
        timersCreated = []
        timersDelay = []
        timerPeriod = []
        scheduledRecovery.initialize()
        AsyncResult<Boolean> result = scheduledRecovery.createRecoveryScheduler()
        TimerConfig timerConfig = timersCreated.get(0) as TimerConfig
        Date delay = timersDelay.get(0)
        long period = timerPeriod.get(0)
        membershipListener.isMaster() >> true

        then: "Timer created should have correct info and result should be true"
        result.get()
        timerConfig.info == SCHEDULED_RECOVERY_TIMER_NAME
        period == TimeUnit.HOURS.toMillis(scheduledRecovery.intermediateRecoveryTimePeriodInHour)
        delay == scheduledRecovery.getCalendarWithInitialExpirationForRecovery().getTime()
        !timerConfig.persistent

        when: "Timer Service mocks are set"
        createTimerServiceMocks()
        and: "When reset Timer is called"
        scheduledRecovery.resetRecoveryTimer()

        then: "Timer created should have correct info and result should be true"
        2 * timer.cancel()
     
        where:
        recoveryHours | recoveryTime | intermediateRecoveryTime
        24            | "00:00:00"   | 24
        24            | "00:00:00"   | 23
        24            | "00:00:00"   | 12
        24            | "00:00:00"   | 5
        24            | "00:00:00"   | 1
        10            | "00:00:00"   | 24
        10            | "00:00:00"   | 23
        10            | "00:00:00"   | 12
        10            | "00:00:00"   | 5
        10            | "00:00:00"   | 1
        1             | "00:00:00"   | 24
        1             | "00:00:00"   | 23
        1             | "00:00:00"   | 12
        1             | "00:00:00"   | 5
        1             | "00:00:00"   | 1
    }

    def "When create Timer is called with old Time, on DST change should reset timer"() {
        long time = INITIAL_TIME
        given: "When createTimer is called"
        timersCreated = []
        scheduledRecovery.createRecoveryScheduler()
        membershipListener.isMaster() >> true
        and: "The current time set"
        timeGenerator.currentTimeMillis() >> time
        timerService.getTimers() >> []
        dstUtil.observesDSTChange(_) >> true

        when: "Recovery is started"
        scheduledRecovery.startRecovery()

        then: "Old Timer should cancel and create new Timer"
        1 * timer.cancel()
        and:""
    }

    def "When create Timer is called and PIB parameter changes should reset timer"() {
        long time = INITIAL_TIME
        given: "When createTimer is called"
        timersCreated = []
        timerPeriod = []
        scheduledRecovery.fileRecoveryHoursInfo = recoveryHours
        scheduledRecovery.createRecoveryScheduler()
        membershipListener.isMaster() >> true
        and: "The current time set"
        timeGenerator.currentTimeMillis() >> time
        timerService.getTimers() >> []

        when: "Timer Service mocks are set"
        createTimerServiceMocks()
        and: "PIB parameter is changed"
        scheduledRecovery.listenForIntermediateScheduledRecoveryTime(intermediateRecoveryTime)

        then: "Old Timer should cancel and create new Timer"
        2 * timer.cancel()
        timerPeriod[timerPeriod.size()-1] == TimeUnit.HOURS.toMillis(expectedIntermediateRecoveryTime)

        where:
        recoveryHours << [24,24,24,24,24,24,24,1,1,1,1,1,5,5,5,5,5]
        intermediateRecoveryTime << [0,4,5,9,19,24,25,1,5,9,19,24,4,5,9,19,24]
        expectedIntermediateRecoveryTime << [1,4,4,8,24,24,24,1,1,1,1,1,5,5,5,5,5]
    }

    @Unroll
    def 'Should update the file recovery hours'() {
        expect: 'that the correct default value is set'
            scheduledRecovery.fileRecoveryHoursInfo == 2

        when: 'the change listener is activated'
            scheduledRecovery.listenForFileRecoveryHoursInfoChanges(value)

        then: 'the value is updated'
            scheduledRecovery.fileRecoveryHoursInfo == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           PROP_FILE_RECOVERY_HOURS,
                                           "${PROP_FILE_RECOVERY_HOURS} parameter value changed, old value = '2' new value = '${value}'")

        where:
            value << [1, 14, 1000, 1_000_000_000]
    }

    @Unroll
    def 'Should update the scheduled recovery period'() {
        given:
            scheduledRecovery.scheduledRecoveryTimer = timer
        expect: 'that the correct default value is set'
            scheduledRecovery.scheduledRecoveryTime == '00:00:00'

        when: 'the change listener is activated'
            scheduledRecovery.listenForScheduledFileRecoveryTimeChanges(value)

        then: 'the value is updated'
            scheduledRecovery.scheduledRecoveryTime == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                    PROP_SCHEDULED_RECOVERY_TIME,
                    "${PROP_SCHEDULED_RECOVERY_TIME} parameter value changed, old value = '00:00:00' new value = '${value}'")

        and: 'the timer is reset'
            1 * timer.cancel()

        where:
            value << ['13:00', '14:00', 'cats']
    }

    def "On Timeout should retrieve file collection tasks from cache and send if for recovery"() {
        given: "Node exists in DPS and there is file collection tasks in the cache"
        node.builder(NODE_NAME1).pmEnabled(true).build()
        node.builder(NODE_NAME2).pmEnabled(true).build()
        membershipListener.isMaster() >> true
        scheduledRecovery.fileRecoveryHoursInfo = 24
        scheduledRecovery.intermediateRecoveryTimePeriodInHour = intermediateTimeInHour
        scheduledRecovery.scheduledRecoveryTime = fileRecoveryTimeSDF.format(new Date(System.currentTimeMillis()+(long)recoveryTime))

        for(Integer ropPeriod : ropPeriods) {
            populateTaskCache(ProcessType.STATS, ropPeriod)
        }

        when: "Recovery is started"
        scheduledRecovery.startRecovery()

        then: "Should sent recovery tasks"
        events * eventSender.send(_ as MediationTaskRequest)

        where:
        ropPeriods      | intermediateTimeInHour  | recoveryTime                                                             | events
        [900]           | 1                     | TimeUnit.SECONDS.toMillis(10)                                              | 2
        [300,900]       | 1                     | TimeUnit.SECONDS.toMillis(10)                                              | 4
        [300,900]       | 1                     | TimeUnit.HOURS.toMillis(2*1)                                               | 4
        [300,900]       | 1                     | 0                                                                          | 4
        [300,900,86400] | 1                     | 0                                                                          | 6
        [300,900,86400] | 1                     | TimeConstants.ONE_HOUR_IN_MILLISECONDS*(1/2)+TimeUnit.SECONDS.toMillis(10) | 4
        [300,900,86400] | 1                     | TimeUnit.HOURS.toMillis(1)                                                 | 4
        [300,900,86400] | 1                     | TimeUnit.HOURS.toMillis(2*1)                                               | 4
        [300,900,86400] | 1                     | TimeUnit.HOURS.toMillis(3*1)                                               | 4
        [300,900,86400] | 1                     | TimeUnit.HOURS.toMillis(3*1)+TimeUnit.SECONDS.toMillis(10)                 | 4
        [300]           | 1                     | TimeUnit.HOURS.toMillis(2*1)                                               | 2
        [300]           | 1                     | TimeUnit.HOURS.toMillis(3*1)+TimeUnit.SECONDS.toMillis(10)                 | 2
        [900]           | 1                     | TimeUnit.HOURS.toMillis(2*1)                                               | 2     
        [60,900]        | 1                     | TimeUnit.HOURS.toMillis(2*1)                                               | 2
        [60]            | 1                     | TimeUnit.HOURS.toMillis(2*1)                                               | 0
        [900]           | 6                     | TimeUnit.SECONDS.toMillis(10)                                              | 2
        [300,900]       | 6                     | TimeUnit.SECONDS.toMillis(10)                                              | 4
        [300,900]       | 6                     | TimeUnit.HOURS.toMillis(2*6)                                               | 4
        [300,900]       | 6                     | 0                                                                          | 4
        [300,900,86400] | 6                     | 0                                                                          | 6
        [300,900,86400] | 6                     | TimeConstants.ONE_HOUR_IN_MILLISECONDS*(6/2)+TimeUnit.SECONDS.toMillis(10) | 4
        [300,900,86400] | 6                     | TimeUnit.HOURS.toMillis(6)                                                 | 4
        [300,900,86400] | 6                     | TimeUnit.HOURS.toMillis(2*6)                                               | 4
        [300,900,86400] | 6                     | TimeUnit.HOURS.toMillis(3*6)                                               | 4
        [300,900,86400] | 6                     | TimeUnit.HOURS.toMillis(3*6)+TimeUnit.SECONDS.toMillis(10)                 | 4
        [300]           | 6                     | TimeUnit.HOURS.toMillis(2*6)                                               | 2
        [300]           | 6                     | TimeUnit.HOURS.toMillis(3*6)+TimeUnit.SECONDS.toMillis(10)                 | 2
        [900]           | 6                     | TimeUnit.HOURS.toMillis(2*6)                                               | 2
        [60,900]        | 6                     | TimeUnit.HOURS.toMillis(2*6)                                               | 2
        [60]            | 6                     | TimeUnit.HOURS.toMillis(2*6)                                               | 0
        [300,900,86400] | 24                    | 0                                                                          | 6
        [300,900,86400] | 24                    | TimeUnit.HOURS.toMillis(24)                                                | 6
        [300,900,86400] | 24                    | TimeUnit.HOURS.toMillis(3*24)+TimeUnit.SECONDS.toMillis(10)                | 6
    }

    def "If Process Type is not stats should not send recovery tasks"() {
        given: "Node exists in DPS and there is file collection tasks in the cache"
        node.builder(NODE_NAME1).pmEnabled(true).build()
        node.builder(NODE_NAME2).pmEnabled(true).build()
        populateTaskCache(ProcessType.NORMAL_PRIORITY_CELLTRACE)
        membershipListener.isMaster() >> true

        when: "Recovery is started"
        scheduledRecovery.startRecovery()

        then: "Should sent recovery tasks"
        0 * eventSender.send(_ as MediationTaskRequest)
    }

    def "If isMaster is false should not send any recovery tasks"() {
        given: "Node exists in DPS and there is file collection tasks in the cache"
        node.builder(NODE_NAME1).pmEnabled(true).build()
        node.builder(NODE_NAME2).pmEnabled(true).build()
        populateTaskCache()
        scheduledRecovery.scheduledRecoveryTime = "00:00:00"

        and: "isMaster is set to false"
        membershipListener.isMaster() >> false

        when: "Recovery is started"
        scheduledRecovery.startRecovery()

        then: ""
        0 * eventSender.send(_ as MediationTaskRequest)
    }

    def "If pmFunction is off on the node should not send any recovery tasks"() {
        given: "Node exists in DPS and there is file collection tasks in the cache"
        node.builder(NODE_NAME1).pmEnabled(false).build()
        node.builder(NODE_NAME2).pmEnabled(false).build()
        populateTaskCache()
        membershipListener.isMaster() >> true
        scheduledRecovery.scheduledRecoveryTime = "00:00:00"

        when: "Recovery is started"
        scheduledRecovery.startRecovery()

        then: ""
        0 * eventSender.send(_ as MediationTaskRequest)
    }

    def "If Process Type is stats, scheduled recovery tasks should be sent"() {
        given: "Node exists in DPS and there is file collection tasks in the cache"
        node.builder(NODE_NAME1).pmEnabled(true).build()
        node.builder(NODE_NAME2).pmEnabled(true).build()
        populateTaskCache(ProcessType.STATS,300)
        membershipListener.isMaster() >> true

        when: "Recovery is started"
        scheduledRecovery.startRecovery()

        then: "Should sent recovery tasks"
        2 * eventSender.send(_ as MediationTaskRequest)
    }

    def populateTaskCache(ProcessType processType = ProcessType.STATS, int ropPeriod = 900) {
        ProcessRequestVO requestVO1 = new ProcessRequestVO.ProcessRequestVOBuilder("NetworkElement=$NODE_NAME1", ropPeriod,
                processType.toString()).endTime(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30)).build()
        ProcessRequestVO requestVO2 = new ProcessRequestVO.ProcessRequestVOBuilder("NetworkElement=$NODE_NAME2", ropPeriod,
                processType.toString()).endTime(System.currentTimeMillis() + 1000).build()
        fileCollectionActiveTasksCache.addProcessRequest(requestVO1)
        fileCollectionScheduledRecoveryCache.addProcessRequest(requestVO2)
    }

    def createTimerServiceMocks() {
        timersCreated.each {
            timer.getInfo() >> it.info
            timers.add(timer)
        }
    }
}
