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

package com.ericsson.oss.services.pm.bdd.collection.schedulers

import static com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant.LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP

import spock.lang.Unroll

import javax.ejb.Timer
import javax.ejb.TimerConfig
import javax.ejb.TimerService
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.node.TestNetworkElementDpsUtils
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper
import com.ericsson.oss.services.pm.collection.cache.FileCollectionLastRopData
import com.ericsson.oss.services.pm.collection.cache.FileCollectionScheduledRecoveryCacheWrapper
import com.ericsson.oss.services.pm.collection.cache.FileCollectionTaskCacheWrapper
import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes
import com.ericsson.oss.services.pm.collection.schedulers.FileCollectionTaskSenderBean
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper
import com.ericsson.oss.services.pm.initiation.util.RopTime
import com.ericsson.oss.services.pm.time.TimeGenerator

class FileCollectionTaskSenderBeanSpec extends SkeletonSpec {

    @ObjectUnderTest
    FileCollectionTaskSenderBean fileCollectionTaskSenderBean

    @ImplementationInstance
    TimerService timerService = Mock(TimerService)

    @ImplementationInstance
    TimeGenerator timeGenerator = Mock(TimeGenerator)

    @Inject
    @Modeled
    private EventSender<MediationTaskRequest> eventSender;

    @Inject
    FileCollectionTaskCacheWrapper fileCollectionTaskCache;

    @Inject
    FileCollectionActiveTaskCacheWrapper fileCollectionActiveTasksCache;

    @Inject
    FileCollectionScheduledRecoveryCacheWrapper fileCollectionScheduledRecoveryCache;

    @Inject
    SupportedRopTimes supportedRopTimes;

    @Inject
    private FileCollectionLastRopData fileCollectionLastRopData

    final String nodeFdn = "MeContext=1,ManagedElement=1";
    RopTime fifteenMinutesRop
    RopTime oneMinuteRop
    RopTime twentyFourRop
    Timer fifteenMinutetimer = Mock(Timer)
    Timer oneMinuteTimer = Mock(Timer)
    Timer twentyFourtimer = Mock(Timer)
    RopTimeInfo fifteenMinutesRopTimeInfo
    RopTimeInfo oneMinuteRopTimeInfo
    RopTimeInfo twentyFourRopTimeInfo
    TestNetworkElementDpsUtils node = new TestNetworkElementDpsUtils(configurableDps)
    static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    static currentTimeDefault = simpleDateFormat.parse("22/09/2000 12:05:00").getTime()
    static recordedTimeDefault = simpleDateFormat.parse("22/09/1900 00:00:00").getTime()

    def setup() {
        fifteenMinutesRopTimeInfo = supportedRopTimes.getRopTime(900)
        oneMinuteRopTimeInfo = supportedRopTimes.getRopTime(60)
        twentyFourRopTimeInfo = supportedRopTimes.getRopTime(86400)
        fifteenMinutetimer.getInfo() >> new FileCollectionTaskSenderBean.TaskSenderTimerConfig(900, 0)
        oneMinuteTimer.getInfo() >> new FileCollectionTaskSenderBean.TaskSenderTimerConfig(60, 0)
        twentyFourtimer.getInfo() >> new FileCollectionTaskSenderBean.TaskSenderTimerConfig(86400, 0)
    }

    def createMocks(long currentTime, long recordedRopTimeInMilis) {
        timeGenerator.currentTimeMillis() >> currentTime
        fifteenMinutesRop = new RopTime(currentTime, 900)
        oneMinuteRop = new RopTime(currentTime, 60)
        twentyFourRop = new RopTime(currentTime, 86400)

        fileCollectionLastRopData.recordRopStartTimeForTaskSending(new RopTime(recordedRopTimeInMilis, 900))
        fileCollectionLastRopData.recordRopStartTimeForTaskSending(new RopTime(recordedRopTimeInMilis, 60))
        fileCollectionLastRopData.recordRopStartTimeForTaskSending(new RopTime(recordedRopTimeInMilis, 86400))
    }

    @Unroll
    def "when timer is passed to sender bean, should collect and send executable #subscriptionType tasks from cache "() {
        given: "File Collection Task is in the cache"
        createMocks(currentTimeDefault, recordedTimeDefault)
        long oneRop = TimeUnit.MINUTES.toMillis(15)
        RopTime endTime = new RopTime(timeGenerator.currentTimeMillis() - oneRop, 900)
        FileCollectionTaskWrapper collectionTask = new FileCollectionTaskWrapper(new FileCollectionTaskRequest('NetworkElement=1', '1',
                subscriptionType.toString(), timeGenerator.currentTimeMillis(), 900, false),
                endTime, fifteenMinutesRopTimeInfo)
        fileCollectionTaskCache.addTask(collectionTask)

        and: "Node exists in dps"
        node.builder('1').pmEnabled(true).build()

        when: "Request to send file collection tasks is received"
        fileCollectionTaskSenderBean.sendFileCollectionTasks(fifteenMinutetimer)

        then: "file collection task is sent"
        1 * eventSender.send({ result -> result.size() == 1; result.get(0) == collectionTask.fileCollectionTaskRequest } as List<FileCollectionTaskRequest>, _)
        and: "task is removed from the cache"
        fileCollectionTaskCache.size() == old(fileCollectionTaskCache.size())

        where:
        subscriptionType << SubscriptionType.values()

    }

    @Unroll
    def "when timer is passed to sender bean, should collect and send executable #subscriptionType tasks from cache for 24ROP "() {
        given: "File Collection Task is in the cache"
        createMocks(getTimeInMillis("22/09/2000 12:00:00"), getTimeInMillis("21/09/2000 00:00:00"))
        long oneRop = TimeUnit.MINUTES.toMillis(1440)
        RopTime endTime = new RopTime(timeGenerator.currentTimeMillis() - oneRop, 86400)
        FileCollectionTaskWrapper collectionTask = new FileCollectionTaskWrapper(new FileCollectionTaskRequest('NetworkElement=1', '1',
                subscriptionType.toString(), timeGenerator.currentTimeMillis(), 86400, false),
                endTime, twentyFourRopTimeInfo)
        fileCollectionTaskCache.addTask(collectionTask)

        and: "Node exists in dps"
        node.builder('1').pmEnabled(true).build()

        when: "Request to send file collection tasks is received"
        fileCollectionTaskSenderBean.sendFileCollectionTasks(twentyFourtimer)

        then: "file collection task is sent"
        1 * eventSender.send({ result -> result.size() == 1; result.get(0) == collectionTask.fileCollectionTaskRequest } as List<FileCollectionTaskRequest>, _)
        and: "task is removed from the cache"
        fileCollectionTaskCache.size() == old(fileCollectionTaskCache.size())

        where:
        subscriptionType << SubscriptionType.values()

    }

    @Unroll
    def "when timer is passed to sender bean, should remove old tasks and collect and send executable #subscriptionType tasks from cache "() {
        given: "File Collection Task is in the cache"
        createMocks(currentTimeDefault, recordedTimeDefault)
        long oneRop = TimeUnit.MINUTES.toMillis(15)
        long twoRops = TimeUnit.MINUTES.toMillis(30)
        long threeRops = TimeUnit.MINUTES.toMillis(45)

        RopTime endTime = new RopTime(timeGenerator.currentTimeMillis(), 900)
        RopTime endTime2 = new RopTime(timeGenerator.currentTimeMillis() - oneRop, 900)
        RopTime endTime3 = new RopTime(timeGenerator.currentTimeMillis() - twoRops, 900)
        RopTime endTime4 = new RopTime(timeGenerator.currentTimeMillis() - threeRops, 900)

        FileCollectionTaskWrapper currentRop = new FileCollectionTaskWrapper(new FileCollectionTaskRequest('NetworkElement=1', '1',
                subscriptionType.toString(), timeGenerator.currentTimeMillis(), 900, true),
                endTime, fifteenMinutesRopTimeInfo)
        FileCollectionTaskWrapper oneRopAgo = new FileCollectionTaskWrapper(new FileCollectionTaskRequest('NetworkElement=1', '2',
                subscriptionType.toString(), timeGenerator.currentTimeMillis(), 900, true),
                endTime2, fifteenMinutesRopTimeInfo)
        FileCollectionTaskWrapper twoRopsAgo = new FileCollectionTaskWrapper(new FileCollectionTaskRequest('NetworkElement=1', '3',
                subscriptionType.toString(), timeGenerator.currentTimeMillis(), 900, true),
                endTime3, fifteenMinutesRopTimeInfo)
        FileCollectionTaskWrapper threeRopsAgo = new FileCollectionTaskWrapper(new FileCollectionTaskRequest('NetworkElement=1', '4',
                subscriptionType.toString(), timeGenerator.currentTimeMillis(), 900, true),
                endTime4, fifteenMinutesRopTimeInfo)

        fileCollectionTaskCache.addTask(currentRop)
        fileCollectionTaskCache.addTask(oneRopAgo)
        fileCollectionTaskCache.addTask(twoRopsAgo)
        fileCollectionTaskCache.addTask(threeRopsAgo)

        and: "Node exists in dps with pmEnabled"
        node.builder('1').pmEnabled(true).build()

        when: "Request to send file collection tasks is received"
        fileCollectionTaskSenderBean.sendFileCollectionTasks(fifteenMinutetimer)

        then: "file collection task is sent for executable tasks"
        1 * eventSender.send({ result -> result.size() == 1 && result.get(0) == oneRopAgo.fileCollectionTaskRequest } as List<FileCollectionTaskRequest>, _)
        and: "task is removed from the cache"
        fileCollectionTaskCache.size() == old(fileCollectionTaskCache.size()) - 1

        where:
        subscriptionType << SubscriptionType.values().findAll {
            !(it in [SubscriptionType.RESOURCE])
        }

    }

    def "when no tasks are in cache and timer is passed to sender , no tasks should be sent"() {
        given: "node exits with pmEnabled"
        createMocks(currentTimeDefault, recordedTimeDefault)
        node.builder('1').pmEnabled(true).build()

        when: "Request to send file collection tasks is received"
        fileCollectionTaskSenderBean.sendFileCollectionTasks(fifteenMinutetimer)

        then: "no file collection task executed as cache is empty"
        fileCollectionTaskCache.size() == 0
        0 * eventSender.send(_, _)
        0 * eventSender.send(_)

    }

    @Unroll
    def "when multiple file collection tasks for #subscriptionType subscription exist for one rop should execute all tasks "() {
        given: "File Collection Task is in the cache"
        createMocks(currentTimeDefault, recordedTimeDefault)
        (1..5).each {
            long oneRop = TimeUnit.MINUTES.toMillis(15)
            RopTime endTime = new RopTime(timeGenerator.currentTimeMillis() - oneRop, 900)
            FileCollectionTaskWrapper collectionTask = new FileCollectionTaskWrapper(new FileCollectionTaskRequest('NetworkElement=1', "$it"
                    .toString(),
                    subscriptionType.toString(), timeGenerator.currentTimeMillis(), 900, false),
                    endTime, fifteenMinutesRopTimeInfo)
            fileCollectionTaskCache.addTask(collectionTask)
        }
        and: "Node exists in dps"
        node.builder('1').pmEnabled(true).build()

        when: "Request to send file collection tasks is received"
        fileCollectionTaskSenderBean.sendFileCollectionTasks(fifteenMinutetimer)

        then: "file collection task is sent"
        1 * eventSender.send({ result -> result.size() == 5 } as List<FileCollectionTaskRequest>, _)
        and: "task is removed from the cache"
        fileCollectionTaskCache.size() == old(fileCollectionTaskCache.size())

        where:
        subscriptionType << SubscriptionType.values().findAll {
            !(it in [SubscriptionType.RESOURCE])
        }
    }

    @Unroll
    def "when tasks are in cache but pmFunction is disabled and timer is passed to sender , no tasks should be sent"() {
        given:
        createMocks(currentTimeDefault, recordedTimeDefault)
        node.builder('1').pmEnabled(false).build()
        long threeRops = TimeUnit.MINUTES.toMillis(45)
        RopTime endTime = new RopTime(timeGenerator.currentTimeMillis() - threeRops, 900)
        FileCollectionTaskWrapper collectionTask = new FileCollectionTaskWrapper(new FileCollectionTaskRequest('NetworkElement=1', '1',
                subscriptionType.toString(), timeGenerator.currentTimeMillis(), 900, false),
                endTime, fifteenMinutesRopTimeInfo)
        fileCollectionTaskCache.addTask(collectionTask)

        when: "Request to send file collection tasks is received"
        fileCollectionTaskSenderBean.sendFileCollectionTasks(fifteenMinutetimer)

        then: "task is removed from the cache and no task is executed"
        fileCollectionTaskCache.size() == old(fileCollectionTaskCache.size()) - 1
        0 * eventSender.send(_, _)
        0 * eventSender.send(_)

        where:
        subscriptionType << SubscriptionType.values()
    }

    @Unroll
    def "when tasks are in cache but is not executable now and timer is passed to sender , no tasks should be sent"() {
        given:
        createMocks(currentTimeDefault, recordedTimeDefault)
        node.builder('1').pmEnabled(true).build()
        RopTime endTime = new RopTime(timeGenerator.currentTimeMillis(), 900)
        FileCollectionTaskWrapper collectionTask = new FileCollectionTaskWrapper(new FileCollectionTaskRequest('NetworkElement=1', '1',
                subscriptionType.toString(), timeGenerator.currentTimeMillis(), 900, false),
                endTime, fifteenMinutesRopTimeInfo)
        fileCollectionTaskCache.addTask(collectionTask)
        when: "Request to send file collection tasks is received"
        fileCollectionTaskSenderBean.sendFileCollectionTasks(fifteenMinutetimer)

        then: "task remains in cache but is not executed"
        fileCollectionTaskCache.size() == 1
        0 * eventSender.send(_, _)
        0 * eventSender.send(_)

        where:
        subscriptionType << [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.CTUM, SubscriptionType.EBM]
    }

    def "when create timer is called with no existing timer, should create timer"() {
        given: "no timers in timer service"
        createMocks(currentTimeDefault, recordedTimeDefault)
        timerService.getTimers() >> []

        when: "call for timer to be created"
        fileCollectionTaskSenderBean.createTimer(900)

        then: "one timer should be created"
        1 * timerService.createIntervalTimer(_, _, { timerConfig -> ((FileCollectionTaskSenderBean.TaskSenderTimerConfig)timerConfig.info).ropPeriodInSeconds == 900 } as TimerConfig)

    }

    def "when create timer is called with existing timer with same interval, should not create timer"() {
        given: "a timer exists in timer service"
        createMocks(currentTimeDefault, recordedTimeDefault)
        timerService.getTimers() >> [fifteenMinutetimer]

        when: "call for timer to be created for same rop interval"
        fileCollectionTaskSenderBean.createTimer(900)

        then: "no timer should be created"
        0 * timerService.createIntervalTimer(_, _, _)
    }

    def "when create timer is called with  existing timer for different intervals, should create timer"() {
        given: "a timer exists in timer service"
        createMocks(currentTimeDefault, recordedTimeDefault)
        timerService.getTimers() >> [oneMinuteTimer]

        when: "call for timer to be created for different rop interval"
        fileCollectionTaskSenderBean.createTimer(900)

        then: "one timer should be created"
        1 * timerService.createIntervalTimer(_, _, { timerConfig -> ((FileCollectionTaskSenderBean.TaskSenderTimerConfig)timerConfig.info).ropPeriodInSeconds == 900 } as TimerConfig)
    }

    def "when stop timer is called, should stop the timer"() {
        given: "a timer exists in timer service"
        createMocks(currentTimeDefault, recordedTimeDefault)
        timerService.getTimers() >> [fifteenMinutetimer]

        when: "request to stop timer is received"
        fileCollectionTaskSenderBean.stopTimer(900)

        then: "timer is cancelled"
        1 * fifteenMinutetimer.cancel()
    }

    def "when stop timer is called with multiple timers, should stop the correct timer"() {
        given: "two timers exist in timer service"
        createMocks(currentTimeDefault, recordedTimeDefault)
        timerService.getTimers() >> [fifteenMinutetimer, oneMinuteTimer]

        when: "request to stop timer is received"
        fileCollectionTaskSenderBean.stopTimer(60)

        then: "only one timer is cancelled"
        0 * fifteenMinutetimer.cancel()
        1 * oneMinuteTimer.cancel()
    }

    def "scenario testing to verify behaviour of preconditions which allows the execution of task sending"() {
        given: "3 tasks exist in the cache for current, previous and 2 rop ago ROPS. Only one will be sent if preconditions are successful"
        createMocks(currentTime, recordedRopTime)
        fileCollectionLastRopData.recordRopStartTimeForTaskSending(new RopTime(recordedRopTime, ropPeriod))
        RopTimeInfo ropTimeInfo = supportedRopTimes.getRopTime(ropPeriod);
        for (int i = 0; i < 10; i++) {
            RopTime rop = new RopTime(timeGenerator.currentTimeMillis(), ropPeriod).getLastROP(i);
            FileCollectionTaskRequest request = new FileCollectionTaskRequest('NetworkElement=1', String.valueOf(i), SubscriptionType.STATISTICAL.
                    name(), rop.getCurrentRopStartTimeInMilliSecs(), ropPeriod, false);
            FileCollectionTaskWrapper wrapper = new FileCollectionTaskWrapper(request, rop.getCurrentROPPeriodEndTime(), ropTimeInfo)
            fileCollectionTaskCache.addTask(wrapper)
        }
        and: "Node exists in dps"
        node.builder('1').pmEnabled(true).build()

        when: "Request to send file collection tasks is received"
        ropPeriod == 900 ? fileCollectionTaskSenderBean.sendFileCollectionTasks(fifteenMinutetimer) : fileCollectionTaskSenderBean.
                sendFileCollectionTasks(oneMinuteTimer)

        then: "File collection task for previous ROP is sent if preconditions are true"
        tasksToSend * eventSender.send({ result -> result.size() == tasksToSend } as List<FileCollectionTaskRequest>, _)
        
        where:
        recordedRopTime                        | currentTime                            | ropPeriod | tasksToSend
        //IF recorded time is the beginning of time, then the task will be sent only if the current time is >= the file collection delay period of current rop
        getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:00") | 900       | 0
        getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:15") | 900       | 0
        getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:04:45") | 900       | 0
        getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:05:00") | 900       | 1
        getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:05:15") | 900       | 1
        getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:14:45") | 900       | 1
        // for 1 min ROP time, we always send file collection tasks if recorded time is the beginning of time
        getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:00") | 60        | 1
        getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:15") | 60        | 1
        getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:30") | 60        | 1
        getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:45") | 60        | 1
        // we only send tasks if recorded time is greater or equal to 2 rops ago.
        // Remember, then successful sending of files sends files generated in previous ROP,
        // therefore the recorded time is updated as previous ROP...which explains the condition above.
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:15:00") | 900       | 0
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:15:15") | 900       | 0
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:19:45") | 900       | 0
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:20:00") | 900       | 0
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:20:15") | 900       | 0
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:34:45") | 900       | 0
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:35:00") | 900       | 1
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:35:15") | 900       | 1
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:44:45") | 900       | 1
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:01:00") | 60        | 0
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:01:15") | 60        | 0
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:01:30") | 60        | 0
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:01:45") | 60        | 0
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:02:00") | 60        | 1
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:02:15") | 60        | 1
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:02:30") | 60        | 1
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:02:45") | 60        | 1
    }

    def "verify that when sending 1000 tasks or various subscription types, they will be sent in groups"() {
        given:
        createMocks(currentTime, recordedRopTime)
        fileCollectionLastRopData.recordRopStartTimeForTaskSending(new RopTime(recordedRopTime, ropPeriod))
        long oneRop = TimeUnit.SECONDS.toMillis(ropPeriod)
        final Random random = new Random(System.nanoTime())
        RopTime lastRop = new RopTime(timeGenerator.currentTimeMillis() - oneRop, ropPeriod)
        RopTimeInfo ropTimeInfo = supportedRopTimes.getRopTime(ropPeriod);
        List<SubscriptionType> subscriptionTypeList = []
        for (int i = 0; i < 1000; i++) {
            subscriptionTypeList.add(subscriptionTypes[random.nextInt(subscriptionTypes.size())])
        }
        for (int i = 0; i < 1000; i++) {
            FileCollectionTaskRequest fileCollectionTaskRequest = new FileCollectionTaskRequest('NetworkElement=1', String.valueOf(i), subscriptionTypeList.
                    get(i).
                    name(), lastRop.getCurrentRopStartTimeInMilliSecs(), ropPeriod, false);
            FileCollectionTaskWrapper collectionTask = new FileCollectionTaskWrapper(fileCollectionTaskRequest, lastRop.getCurrentROPPeriodEndTime(), ropTimeInfo)
            fileCollectionTaskCache.addTask(collectionTask)
        }
        and: "Node exists in dps"
        node.builder('1').pmEnabled(true).build()

        when: "Request to send file collection tasks is received"
        fileCollectionTaskSenderBean.sendFileCollectionTasks(fifteenMinutetimer)

        then: "File collection task for previous ROP is sent if preconditions are true"
        (subscriptionTypes.size()) * eventSender.send(_ as List<FileCollectionTaskRequest>, _)

        where:
        recordedRopTime                        | currentTime                            | ropPeriod | subscriptionTypes
        //IF recorded time is the beginning of time, then the task will be sent only if the current time is >= the file collection delay period of current rop
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:44:45") | 900       | SubscriptionType.values().findAll {
            !(it in [SubscriptionType.RESOURCE])
        }
    }

    def "even if there are no tasks to send, if preconditions are correct, the record will be updated"() {
        given:
        timeGenerator.currentTimeMillis() >> currentTime
        RopTime previousRop = new RopTime(timeGenerator.currentTimeMillis() - ropPeriod * 1000, ropPeriod)
        fileCollectionLastRopData.recordRopStartTimeForTaskSending(new RopTime(recordedRopTime, ropPeriod))
        when: "Request to send file collection tasks is received"
        ropPeriod == 900 ? fileCollectionTaskSenderBean.sendFileCollectionTasks(fifteenMinutetimer) : fileCollectionTaskSenderBean.
                sendFileCollectionTasks(oneMinuteTimer)

        then: "File collection task for previous ROP is sent if preconditions are true"
        tasksToSend * eventSender.send(_ as List<FileCollectionTaskRequest>, _)
        and: "When a Task is sent, record is updated to the previous ROP's start time"
        fileCollectionLastRopData.getFromCache(LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP + ropPeriod) == previousRop.
                getCurrentRopStartTimeInMilliSecs()

        where:
        recordedRopTime                        | currentTime                            | ropPeriod | tasksToSend
        getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:05:00") | 900       | 0
        getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:00") | 60        | 0
    }

    def "Test scenarios where specific set of file collection tasks are sent"() {
        given: "3 tasks that were created 2 rops ago and one task created 5 rops ago exist in the cache"
        createMocks(currentTime, recordedRopTime)
        fileCollectionLastRopData.recordRopStartTimeForTaskSending(new RopTime(recordedRopTime, ropPeriod))
        long oneRop = TimeUnit.SECONDS.toMillis(ropPeriod)
        RopTime twoRopsAgo = new RopTime(timeGenerator.currentTimeMillis() - oneRop - oneRop, ropPeriod)
        RopTime fiveRopsAgo = new RopTime(timeGenerator.currentTimeMillis() - 5 * oneRop, ropPeriod)
        RopTimeInfo ropTimeInfo = supportedRopTimes.getRopTime(ropPeriod);
        FileCollectionTaskRequest fileCollectionTaskRequest1 = new FileCollectionTaskRequest('NetworkElement=1', "1", SubscriptionType.STATISTICAL.
                name(), twoRopsAgo.getCurrentRopStartTimeInMilliSecs(), ropPeriod, false);
        FileCollectionTaskRequest fileCollectionTaskRequest2 = new FileCollectionTaskRequest('NetworkElement=2', "2", SubscriptionType.STATISTICAL.
                name(), twoRopsAgo.getCurrentRopStartTimeInMilliSecs(), ropPeriod, false);
        FileCollectionTaskRequest fileCollectionTaskRequest3 = new FileCollectionTaskRequest('NetworkElement=3', "3", SubscriptionType.STATISTICAL.
                name(), twoRopsAgo.getCurrentRopStartTimeInMilliSecs(), ropPeriod, false);
        FileCollectionTaskRequest fileCollectionTaskRequest4 = new FileCollectionTaskRequest('NetworkElement=3', "4", SubscriptionType.STATISTICAL.
                name(), fiveRopsAgo.getCurrentRopStartTimeInMilliSecs(), ropPeriod, false);
        FileCollectionTaskWrapper collectionTask1 = new FileCollectionTaskWrapper(fileCollectionTaskRequest1, twoRopsAgo.
                getCurrentROPPeriodEndTime(), ropTimeInfo)
        FileCollectionTaskWrapper collectionTask2 = new FileCollectionTaskWrapper(fileCollectionTaskRequest2, twoRopsAgo.
                getCurrentROPPeriodEndTime(), ropTimeInfo)
        FileCollectionTaskWrapper collectionTask3 = new FileCollectionTaskWrapper(fileCollectionTaskRequest3, twoRopsAgo.
                getCurrentROPPeriodEndTime(), ropTimeInfo)
        FileCollectionTaskWrapper collectionTask4 = new FileCollectionTaskWrapper(fileCollectionTaskRequest4, fiveRopsAgo.
                getCurrentROPPeriodEndTime(), ropTimeInfo)
        fileCollectionTaskCache.addTask(collectionTask1)
        fileCollectionTaskCache.addTask(collectionTask2)
        fileCollectionTaskCache.addTask(collectionTask3)
        fileCollectionTaskCache.addTask(collectionTask4)
        and: "Nodes exists in dps"
        node.builder('1').pmEnabled(true).build()
        node.builder('2').pmEnabled(true).build()
        node.builder('3').pmEnabled(true).build()

        when: "Request to send file collection tasks is received"
        fileCollectionTaskSenderBean.sendFileCollectionTasks(fileCollectionTaskCache.getAllTasksSorted(), twoRopsAgo)

        then: "File collection task for all tasks are sent"
        1 * eventSender.send({ result -> result.size() == 4 } as List<FileCollectionTaskRequest>, _)
        and: "When a Task is sent, record is updated to the ROP start time of the previous ROP"
        fileCollectionLastRopData.getFromCache(LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP + ropPeriod) == twoRopsAgo.
                getCurrentRopStartTimeInMilliSecs()
        and: "Tasks are not removed from cache"
        fileCollectionTaskCache.size() == old(fileCollectionTaskCache.size())
        where: "The preconditions do not matter as it is a direct call to send files bypassing the removal of old tasks"
        recordedRopTime                        | currentTime                            | ropPeriod
        getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:00") | 900
        getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:00") | 900
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:00:00") | 900
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:00:00") | 900
        getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:00") | 60
        getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:00") | 60
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:00:00") | 60
        getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:00:00") | 60
    }

    private static long getTimeInMillis(String time) {
        return simpleDateFormat.parse(time).getTime()
    }
}
