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

import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.*
import static com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant.LAST_FILE_COLLECTION_TASKS_CREATED_FOR_ROP

import spock.lang.Unroll

import javax.ejb.Timer
import javax.ejb.TimerConfig
import javax.ejb.TimerService
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.stub.RuntimeConfigurableDps
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO
import com.ericsson.oss.services.pm.collection.api.ProcessTypesAndRopInfo
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper
import com.ericsson.oss.services.pm.collection.cache.FileCollectionLastRopData
import com.ericsson.oss.services.pm.collection.cache.FileCollectionScheduledRecoveryCacheWrapper
import com.ericsson.oss.services.pm.collection.cache.FileCollectionTaskCacheWrapper
import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes
import com.ericsson.oss.services.pm.collection.schedulers.FileCollectionTaskManagerBean
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest
import com.ericsson.oss.services.pm.initiation.util.RopTime
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener
import com.ericsson.oss.services.pm.time.TimeGenerator

class FileCollectionTaskManagerBeanSpec extends SkeletonSpec {

    RuntimeConfigurableDps configurableDps = cdiInjectorRule.getService(RuntimeConfigurableDps.class)

    @ObjectUnderTest
    FileCollectionTaskManagerBean fileCollectionTaskManagerBean

    @Inject
    FileCollectionTaskCacheWrapper fileCollectionTaskCache;

    @Inject
    FileCollectionActiveTaskCacheWrapper fileCollectionActiveTasksCache;

    @Inject
    FileCollectionScheduledRecoveryCacheWrapper fileCollectionScheduledRecoveryCache

    @Inject
    EventSender<MediationTaskRequest> eventSender

    @Inject
    SupportedRopTimes supportedRopTimes;

    @Inject
    FileCollectionLastRopData fileCollectionLastRopData

    @ImplementationInstance
    TimerService timerService = Mock(TimerService)

    @ImplementationInstance
    TimeGenerator timeGenerator = Mock(TimeGenerator)

    @ImplementationInstance
    MembershipListener stubbedListener = Stub(MembershipListener)

    final String nodeFdn = "MeContext=1,ManagedElement=1";

    static final int FIFTEEN_MINUTES_IN_MILLISECONDS = 15 * 60 * 1000

    static final String NODE_FDN1 = "NetworkElement=LTE01ERBS0001"
    static final String NODE_ADDRESS1 = "LTE01ERBS0001"
    static final String NODE_FDN2 = "NetworkElement=LTE01ERBS0002"
    static final String NODE_ADDRESS2 = "LTE01ERBS0002"
    static final String NODE_FDN3 = "NetworkElement=LTE01ERBS0003"
    static final String NODE_ADDRESS3 = "LTE01ERBS0003"
    static final String NODE_FDN4 = "NetworkElement=LTE01ERBS0004"
    static final String NODE_ADDRESS4 = "LTE01ERBS0004"
    static final String CISCO_NODE = "CISCO-ASR9000-01"
    static final String CISCO_NODE_FDN = "NetworkElement=CISCO-ASR9000-01"
    static final String WCDMA_NODE = "RNC01"
    static final String WCDMA_NODE_FDN = "NetworkElement=RNC01"

    static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    static long currentTime = simpleDateFormat.parse("22/09/2000 12:07:30").getTime()
    static long recordedTime = simpleDateFormat.parse("22/09/2000 11:45:00").getTime()
    RopTime fifteenMinutesRop
    RopTime oneMinuteRop
    RopTime twentyFourRop
    RopTimeInfo fifteenMinutesRopTimeInfo;
    RopTimeInfo oneMinutesRopTimeInfo;
    RopTimeInfo twentyFourRopTimeInfo;

    Timer oneMinuteTimer = Mock(Timer)
    Timer fifteenMinutetimer = Mock(Timer)
    Timer twentyFourtimer = Mock(Timer)

    def setup() {
        fifteenMinutetimer.getInfo() >> 900
        oneMinuteTimer.getInfo() >> 60
        twentyFourtimer.getInfo() >> 86400
        stubbedListener.isMaster() >> true
        nodeUtil.builder(NODE_ADDRESS1).pmEnabled(true).neType(NetworkElementType.ERBS).build()
        nodeUtil.builder(NODE_ADDRESS2).pmEnabled(true).neType(NetworkElementType.ERBS).build()
        nodeUtil.builder(NODE_ADDRESS3).pmEnabled(true).neType(NetworkElementType.ERBS).build()
        nodeUtil.builder(NODE_ADDRESS4).pmEnabled(true).neType(NetworkElementType.ERBS).build()
        nodeUtil.builder(CISCO_NODE).pmEnabled(true).neType(NetworkElementType.CISCOASR9000).build()
        nodeUtil.builder(WCDMA_NODE).pmEnabled(true).neType(NetworkElementType.RNC).build()
    }

    def createMocks(long currentTime, long recordedTime) {
        timeGenerator.currentTimeMillis() >> currentTime
        fifteenMinutesRop = new RopTime(currentTime, 900)
        oneMinuteRop = new RopTime(currentTime, 60)
        twentyFourRop = new RopTime(currentTime, 86400)
        fifteenMinutesRopTimeInfo = supportedRopTimes.getRopTime(900)
        oneMinutesRopTimeInfo = supportedRopTimes.getRopTime(60)
        twentyFourRopTimeInfo = supportedRopTimes.getRopTime(86400)
        fileCollectionLastRopData.recordRopStartTimeForTaskCreation(new RopTime(recordedTime, 900))
        fileCollectionLastRopData.recordRopStartTimeForTaskCreation(new RopTime(recordedTime, 60))
        fileCollectionLastRopData.recordRopStartTimeForTaskCreation(new RopTime(recordedTime, 86400))
    }

    @Unroll
    def "update the existing file collection entry when the rop period is updated from #oldRopPeriod to #newRopPeriod"() {
        given: "Active file collection task exists in the cache"
        createMocks(currentTime, oldRopPeriod)
        ProcessRequestVO processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(WCDMA_NODE_FDN, oldRopPeriod, ProcessType.REGULAR_GPEH.name()).startTime(currentTime).build()
        fileCollectionActiveTasksCache.addProcessRequest(processRequestVO)
        timerService.getTimers() >> [fifteenMinutetimer, oneMinuteTimer]
        when: "rop period is updated on the scanner for the node"
        ProcessRequestVO updatedProcessRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(WCDMA_NODE_FDN, newRopPeriod, ProcessType.REGULAR_GPEH.name()).startTime(currentTime).build()
        fileCollectionTaskManagerBean.updateFileCollectionForNewRopPeriod(updatedProcessRequestVO, ropBoundary, oldRopPeriod)

        then: "fileCollectionActiveTasksCache should have an additional record"
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) + 1

        and: "existing cache entry should be updated with and end time"
        ProcessRequestVO oldEntry = fileCollectionActiveTasksCache.getProcessRequest(fileCollectionActiveTasksCache.getKeyFor(processRequestVO))
        oldEntry.getEndTime() > 0

        and: "new cache entry should have a start time 1000 ms less than the existing entry end time"
        ProcessRequestVO newEntry = fileCollectionActiveTasksCache.getProcessRequest(fileCollectionActiveTasksCache.getKeyFor(updatedProcessRequestVO))
        newEntry.getEndTime() == 0
        newEntry.getStartTime() == oldEntry.getEndTime() - TimeUnit.SECONDS.toMillis(1)

        where:
        oldRopPeriod | newRopPeriod | ropBoundary
        900          | 60           | getTimeInMillis("20/06/2017 10:15:00")
        60           | 900          | getTimeInMillis("20/06/2017 10:15:00")
    }


    @Unroll
    def "when FC is started for CISCO node and no existing timers then FC Task should not be added to cache and timer shouldn't be created"() {
        given: "File Collection Request is created"
        createMocks(currentTime, recordedTime)
        ProcessRequestVO processRequestVO = createProcessRequest(CISCO_NODE_FDN, STATS,
                fifteenMinutesRop.getRopPeriodInSeconds(), fifteenMinutesRop.getTime())
        timerService.getTimers() >> []

        when: "File Collection request is sent to file Collection Task Manager Bean"
        fileCollectionTaskManagerBean.startFileCollection(processRequestVO)

        then: "Task is not added to File Collection Task cache and timer is not created"
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size())
        0 * timerService.createIntervalTimer(_, _, { timerConfig -> timerConfig.info == processRequestVO.ropPeriod } as TimerConfig)
        and: "the active task cache is empty"
        fileCollectionActiveTasksCache.getProcessRequests().size() == 0
    }

    @Unroll
    def "when FC is started for #processType and no existing timers then FC Task should be added to cache and timer should be created"(
            ProcessType processType
    ) {

        given: "File Collection Request is created"
        createMocks(currentTime, recordedTime)
        ProcessRequestVO processRequestVO = createProcessRequest(NODE_FDN1, processType,
                fifteenMinutesRop.getRopPeriodInSeconds(), fifteenMinutesRop.getTime())
        timerService.getTimers() >> []

        when: "File Collection request is sent to file Collection Task Manager Bean"
        fileCollectionTaskManagerBean.startFileCollection(processRequestVO)


        then: "Task is added to File Collection Task cache and timer is created"
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) + 1
        1 * timerService.createIntervalTimer(_, _, { timerConfig -> timerConfig.info == processRequestVO.ropPeriod } as TimerConfig)
        and: "task in the cache has the correct node address"
        fileCollectionActiveTasksCache.getProcessRequests().each {
            it.nodeAddress == NODE_FDN1
            it.processType == processType.name()
            it.getRopPeriod() == fifteenMinutesRop.ropPeriodInSeconds
        }

        where: "For process types"
        processType << ProcessType.values().findAll { it != OTHER }
    }

    @Unroll
    def "when FC is started for #processType and no existing timers then FC Task should be added to cache and timer should be created for 24ROP"(
            ProcessType processType
    ) {

        given: "File Collection Request is created"
        createMocks(getTimeInMillis("22/09/2000 12:00:00"), getTimeInMillis("21/09/2000 00:00:00"))
        ProcessRequestVO processRequestVO = createProcessRequest(NODE_FDN1, processType,
                twentyFourRop.getRopPeriodInSeconds(), twentyFourRop.getTime())
        timerService.getTimers() >> []

        when: "File Collection request is sent to file Collection Task Manager Bean"
        fileCollectionTaskManagerBean.startFileCollection(processRequestVO)


        then: "Task is added to File Collection Task cache and timer is created"
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) + 1
        1 * timerService.createIntervalTimer(_, _, { timerConfig -> timerConfig.info == processRequestVO.ropPeriod } as TimerConfig)
        and: "task in the cache has the correct node address"
        fileCollectionActiveTasksCache.getProcessRequests().each {
            it.nodeAddress == NODE_FDN1
            it.processType == processType.name()
            it.getRopPeriod() == twentyFourRop.ropPeriodInSeconds
        }

        where: "For process types"
        processType << ProcessType.values().findAll { it != OTHER }
    }

    @Unroll
    def "when file collection is started with existing timers then file Collection Task should be added to cache and no timer should be created"() {

        given: "File Collection Request is created"
        createMocks(currentTime, recordedTime)
        ProcessRequestVO processRequestVO = createProcessRequest(NODE_FDN1, processType,
                fifteenMinutesRop.getRopPeriodInSeconds(), fifteenMinutesRop.getTime())
        timerService.getTimers() >> [fifteenMinutetimer]

        when: "File Collection request is sent to file Collection Task Manager Bean"
        fileCollectionTaskManagerBean.startFileCollection(processRequestVO)

        then: "task is added to File Collection Task cache and no timer is created "
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) + 1
        0 * timerService.createIntervalTimer(_, _, _)
        and: "task in the cache has the correct node address"
        fileCollectionActiveTasksCache.getProcessRequests().each {
            it.nodeAddress == NODE_FDN1
            it.processType == processType.name()
            it.getRopPeriod() == fifteenMinutesRop.ropPeriodInSeconds
        }

        where: "For process types"
        processType << ProcessType.values().findAll { it != OTHER }
    }

    @Unroll
    def "when FC is started for #processType with #existingTimer existing timers then file Collection Task should be added to cache and #existingTimer timer should be created"(
            ProcessType processType
    ) {

        given: "File Collection Request is created"
        createMocks(currentTime, recordedTime)
        ProcessRequestVO processRequestVO = createProcessRequest(NODE_FDN1, processType,
                oneMinuteRop.getRopPeriodInSeconds(), oneMinuteRop.getTime())
        timerService.getTimers() >> [fifteenMinutetimer]

        when: "File Collection request is sent to file Collection Task Manager Bean"
        fileCollectionTaskManagerBean.startFileCollection(processRequestVO)

        then: "task is added to File Collection Task cache and timer is created "
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) + 1
        1 * timerService.createIntervalTimer(_, _, { timerConfig -> timerConfig.info == processRequestVO.ropPeriod } as TimerConfig)
        and: "task in the cache has the correct node address"
        fileCollectionActiveTasksCache.getProcessRequests().each {
            it.nodeAddress == NODE_FDN1
            it.processType == processType.name()
            it.getRopPeriod() == oneMinuteRop.ropPeriodInSeconds
        }

        where: "For process types"
        processType << ProcessType.values().findAll { it != OTHER }
    }

    @Unroll
    def "when FC is stop request is received for #processType Process Request Task should be removed from cache and timer should be cancelled"(
            ProcessType processType
    ) {

        given: "File Collection Request is created"
        createMocks(currentTime, recordedTime)
        ProcessRequestVO processRequestVOfifteen = createProcessRequest(NODE_FDN1, processType,
                fifteenMinutesRop.getRopPeriodInSeconds(), fifteenMinutesRop.getTime())
        timerService.getTimers() >> [fifteenMinutetimer]

        and: "File Collection request is sent to file Collection Task Manager Bean"
        fileCollectionTaskManagerBean.startFileCollection(processRequestVOfifteen)

        when: "File Collection request is sent to file Collection Task Manager Bean"
        fileCollectionTaskManagerBean.stopFileCollection(processRequestVOfifteen)

        then: "task is taken out of File Collection Task cache and added to recovey cache"
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) - 1
        fileCollectionScheduledRecoveryCache.size() == old(fileCollectionScheduledRecoveryCache.size()) + 1
        and: "timer is cancelled"
        1 * fifteenMinutetimer.cancel()
        and: "task in the cache has the correct node address"
        fileCollectionScheduledRecoveryCache.getProcessRequests().each {
            it.nodeAddress == NODE_FDN1
            it.processType == processType.name()
            it.getRopPeriod() == fifteenMinutesRop.ropPeriodInSeconds

        }

        where: "For process types"
        processType << ProcessType.values().findAll { it != OTHER }
    }

    @Unroll
    def "when FC is stop request is received for #processType Process Request Task should be removed from cache and  timer should be cancelled"(
            ProcessType processType
    ) {

        given: "File Collection Request is created"
        createMocks(currentTime, recordedTime)
        ProcessRequestVO processRequestVOfifteen = createProcessRequest(NODE_FDN1, processType,
                fifteenMinutesRop.getRopPeriodInSeconds(), fifteenMinutesRop.getTime())
        ProcessRequestVO processRequestVOone = createProcessRequest(NODE_FDN2, processType,
                oneMinuteRop.getRopPeriodInSeconds(), oneMinuteRop.getTime())

        timerService.getTimers() >> [
                fifteenMinutetimer,
                oneMinuteTimer
        ]

        and: "File Collection request is sent to file Collection Task Manager Bean"
        fileCollectionTaskManagerBean.startFileCollection(processRequestVOfifteen)
        fileCollectionTaskManagerBean.startFileCollection(processRequestVOone)

        when: "File Collection request is sent to file Collection Task Manager Bean"
        fileCollectionTaskManagerBean.stopFileCollection(processRequestVOfifteen)

        then: "task is removed from File Collection Task cache and added to recovery cache "
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) - 1
        fileCollectionScheduledRecoveryCache.size() == old(fileCollectionScheduledRecoveryCache.size()) + 1
        and: "one minute file collection task remains in the cache"
        fileCollectionActiveTasksCache.getProcessRequest(
                fileCollectionActiveTasksCache.getKeyFor(processRequestVOone)).ropPeriod == oneMinuteRop.getRopPeriodInSeconds()
        1 * fifteenMinutetimer.cancel()
        and: "the tasks in both caches are correct"
        fileCollectionScheduledRecoveryCache.getProcessRequests().each {
            it.nodeAddress == NODE_FDN1
            it.processType == processType.name()
            it.getRopPeriod() == fifteenMinutesRop.ropPeriodInSeconds
        }
        fileCollectionActiveTasksCache.getProcessRequests().each {
            it.nodeAddress == NODE_FDN2
            it.processType == processType.name()
            it.getRopPeriod() == oneMinuteRop.ropPeriodInSeconds
        }


        where: "For process types"
        processType << ProcessType.values().findAll { it != OTHER }
    }

    @Unroll
    def "when FC stop request is received for #processType and 2 15min tasks are in cache PR Task should be removed from cache and timer should not be cancelled"(
            //PR - Process Request
            //FC - File Collection
            ProcessType processType
    ) {

        given: "File Collection Request is created"
        createMocks(currentTime, recordedTime)
        ProcessRequestVO processRequestVOfifteen = createProcessRequest(NODE_FDN1, processType,
                fifteenMinutesRop.getRopPeriodInSeconds(), fifteenMinutesRop.getTime())
        ProcessRequestVO processRequestVOfifteen2 = createProcessRequest(NODE_FDN2, processType,
                fifteenMinutesRop.getRopPeriodInSeconds(), fifteenMinutesRop.getTime())

        timerService.getTimers() >> [fifteenMinutetimer]

        and: "File Collection request is sent to file Collection Task Manager Bean"
        fileCollectionTaskManagerBean.startFileCollection(processRequestVOfifteen)
        fileCollectionTaskManagerBean.startFileCollection(processRequestVOfifteen2)

        when: "File Collection request is sent to file Collection Task Manager Bean"
        fileCollectionTaskManagerBean.stopFileCollection(processRequestVOfifteen)

        then: "task is removed from File Collection Task cache and added recovery cache "
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) - 1
        fileCollectionScheduledRecoveryCache.size() == old(fileCollectionScheduledRecoveryCache.size()) + 1
        and: "timer is not cancelled"
        0 * fifteenMinutetimer.cancel()
        and: "the tasks in both caches are correct"
        fileCollectionActiveTasksCache.getProcessRequests().each {
            it.nodeAddress == NODE_FDN1
            it.processType == processType.name()
            it.getRopPeriod() == fifteenMinutesRop.ropPeriodInSeconds
        }
        fileCollectionScheduledRecoveryCache.getProcessRequests().each {
            it.nodeAddress == NODE_FDN1
            it.processType == processType.name()
            it.getRopPeriod() == fifteenMinutesRop.ropPeriodInSeconds
        }

        where: "For process types"
        processType << ProcessType.values().findAll { it != OTHER }
    }

    @Unroll
    def "when create file collection task is called for #processType , should create task and add to cache"() {

        given: "Node exists in dps where current time is 12:07:30 and last tasks were created at 11:52:30"
        createMocks(currentTime, recordedTime)
        fileCollectionLastRopData.recordRopStartTimeForTaskCreation(new RopTime(simpleDateFormat.parse("22/09/2000 11:52:30").getTime(), 900))

        and: "Process request is created"
        ProcessRequestVO processRequestVOfifteen = createProcessRequest("NetworkElement=$NODE_ADDRESS1", processType,
                fifteenMinutesRop.getRopPeriodInSeconds(), fifteenMinutesRop.getTime())
        and: "Request is added to cache"
        fileCollectionActiveTasksCache.addProcessRequest(processRequestVOfifteen)

        when: "timer is passed to create file collection task"
        fileCollectionTaskManagerBean.createFileCollectionTasksForRop(fifteenMinutetimer)

        then: "task should be created and added to the cache"
        fileCollectionTaskCache.size() == old(fileCollectionTaskCache.size()) + 1

        and: "the task in the cache has the correct values"
        fileCollectionTaskCache.getAllTasks().each {
            assert it.priority == priority
            with(it.fileCollectionTaskRequest) {
                assert it.subscriptionType == subscriptionType.name()
                assert it.ropPeriod == FIFTEEN_MINUTES_IN_MILLISECONDS
                assert it.nodeAddress == NODE_FDN1
            }
        }

        where: "For process types"
        processType               | subscriptionType             | priority
        STATS                     | SubscriptionType.STATISTICAL | 6
        NORMAL_PRIORITY_CELLTRACE | SubscriptionType.CELLTRACE   | 7
        HIGH_PRIORITY_CELLTRACE   | SubscriptionType.CELLTRACE   | 7
        EVENTJOB                  | SubscriptionType.EBM         | 7
        CTUM                      | SubscriptionType.CTUM        | 7
        UETR                      | SubscriptionType.UETR        | 7
        REGULAR_GPEH              | SubscriptionType.GPEH        | 7
        CTR                       | SubscriptionType.CELLTRAFFIC | 7
    }

    def "when create file collection task is called with no process request in cache, should not create task and add to cache"() {

        when: " timer is passed to create file collection task for and no process request is in the cache"
        createMocks(currentTime, recordedTime)
        fileCollectionTaskManagerBean.createFileCollectionTasksForRop(fifteenMinutetimer)

        then: "no task is created and added to the cache"
        fileCollectionTaskCache.size() == old(fileCollectionTaskCache.size())
    }

    @Unroll
    def "when file collection is started for nodes for #processType, should add tasks to cache"(ProcessType processType) {

        given: "Node info map is created"
        createMocks(currentTime, recordedTime)
        final Map<String, ProcessTypesAndRopInfo> nodesWithProcessTypes = new HashMap<>()
        nodesWithProcessTypes.put(NODE_FDN1, createProcessTypeRopInfo(900, processType.name()))
        nodesWithProcessTypes.put(NODE_FDN2,
                createProcessTypeRopInfo(900, HIGH_PRIORITY_CELLTRACE.name()))
        nodesWithProcessTypes.put(CISCO_NODE_FDN, createProcessTypeRopInfo(900, processType.name()))
        nodesWithProcessTypes.put(NODE_FDN3,
                createProcessTypeRopInfo(900, HIGH_PRIORITY_CELLTRACE.name()))
        nodesWithProcessTypes.put(NODE_FDN4,
                createProcessTypeRopInfo(900, NORMAL_PRIORITY_CELLTRACE.name()))


        when: "file collection task is created for nodes (excluded CISCO)"
        fileCollectionTaskManagerBean.startFileCollectionForNodes(nodesWithProcessTypes)

        then: "a task for every node (excluded CISCO) is added to the cache"
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) + 4

        where: "For process types"
        processType << ProcessType.values().findAll { it != OTHER }
    }

    @Unroll
    def "Will create #tasksToCreate, send #tasksToSend for rop #ropPeriod where active scanner exists, current time is #currentTime and recorded time is #recordedRopTime"() {
        given: "Node exists in dps and active scanner exists"
        createMocks(currentTime, recordedRopTime)
        fileCollectionLastRopData.recordRopStartTimeForTaskCreation(new RopTime(recordedRopTime, ropPeriod))
        addActiveScannersToCache(processType, ropPeriod, currentTime)

        when: "timer is passed to create file collection task"
        ropPeriod == 900 ? fileCollectionTaskManagerBean.createFileCollectionTasksForRop(fifteenMinutetimer) : fileCollectionTaskManagerBean.createFileCollectionTasksForRop(oneMinuteTimer)

        then: "task should be created and added to the cache"
        fileCollectionTaskCache.size() == tasksToCreate

        and: "File collection task for all tasks are sent"
        tasksToSend * eventSender.send({ listOfMtrs -> listOfMtrs.size() == 1 } as List<FileCollectionTaskRequest>, _)
        and: "Recorded time is updated"
        updatedRecord == fileCollectionLastRopData.getFromCache(LAST_FILE_COLLECTION_TASKS_CREATED_FOR_ROP + ropPeriod)
        where:
        processType | recordedRopTime                        | currentTime                            | ropPeriod | updatedRecord                          | tasksToSend | tasksToCreate
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:07:30") | 900       | getTimeInMillis("15/05/2016 12:00:00") | 1           | 2 //will create and send recovery. Will create task for current ROP
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:30") | 60        | getTimeInMillis("15/05/2016 12:00:00") | 1           | 2 //will create and send recovery. Will create task for current ROP
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:14:59") | 900       | getTimeInMillis("15/05/2016 12:00:00") | 1           | 2 //will create and send recovery. Will create task for current ROP
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:59") | 60        | getTimeInMillis("15/05/2016 12:00:00") | 1           | 2 //will create and send recovery. Will create task for current ROP
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:00") | 900       | getTimeInMillis("15/05/2016 11:45:00") | 1           | 1 //will create and send recovery. Will not create for current ROP as current time is not past the middle of current ROP
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:00") | 60        | getTimeInMillis("15/05/2016 11:59:00") | 1           | 1 //will create and send recovery. Will not create for current ROP as current time is not past the middle of current ROP
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:07:29") | 900       | getTimeInMillis("15/05/2016 11:45:00") | 1           | 1 //will create and send recovery. Will not create for current ROP as current time is not past the middle of current ROP
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:29") | 60        | getTimeInMillis("15/05/2016 11:59:00") | 1           | 1 //will create and send recovery. Will not create for current ROP as current time is not past the middle of current ROP
        STATS       | getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:22:30") | 900       | getTimeInMillis("15/05/2016 12:15:00") | 0           | 1 //will not create and send recovery. Will create task for current ROP
        STATS       | getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:01:30") | 60        | getTimeInMillis("15/05/2016 12:01:00") | 0           | 1 //will not create and send recovery. Will create task for current ROP
        STATS       | getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:29:59") | 900       | getTimeInMillis("15/05/2016 12:15:00") | 0           | 1 //will not create and send recovery. Will create task for current ROP
        STATS       | getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:01:59") | 60        | getTimeInMillis("15/05/2016 12:01:00") | 0           | 1 //will not create and send recovery. Will create task for current ROP
        STATS       | getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:00:00") | 900       | getTimeInMillis("15/05/2016 12:00:00") | 0           | 0 //will not create and send recovery. Will not create task for current ROP as current time is not past the middle of current ROP
        STATS       | getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:00:00") | 60        | getTimeInMillis("15/05/2016 12:00:00") | 0           | 0 //will not create and send recovery. Will not create task for current ROP as current time is not past the middle of current ROP
        STATS       | getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:07:29") | 900       | getTimeInMillis("15/05/2016 12:00:00") | 0           | 0 //will not create and send recovery. Will not create task for current ROP as current time is not past the middle of current ROP
        STATS       | getTimeInMillis("15/05/2016 12:00:00") | getTimeInMillis("15/05/2016 12:00:29") | 60        | getTimeInMillis("15/05/2016 12:00:00") | 0           | 0 //will not create and send recovery. Will not create task for current ROP as current time is not past the middle of current ROP
    }

    @Unroll
    def "Testing recovery scenario for previous rops baseed on #scannerActivationTime"() {
        given: "Node exists in dps and active scanner exists"
        createMocks(currentTime as long, recordedRopTime as long)
        fileCollectionLastRopData.recordRopStartTimeForTaskCreation(new RopTime(recordedRopTime, ropPeriod))
        ProcessRequestVO requestVO = new ProcessRequestVO.ProcessRequestVOBuilder("NetworkElement=$NODE_ADDRESS1", ropPeriod as int, processType as String).startTime(scannerActivationTime as long).build()
        fileCollectionActiveTasksCache.addProcessRequest(requestVO)

        when: "timer is passed to create file collection task"
        ropPeriod == 900 ? fileCollectionTaskManagerBean.createFileCollectionTasksForRop(fifteenMinutetimer) : fileCollectionTaskManagerBean.createFileCollectionTasksForRop(oneMinuteTimer)

        then: "task should be created and added to the cache"
        fileCollectionTaskCache.size() == tasksToCreate

        and: "File collection task for all tasks are sent"
        tasksToSend * eventSender.send({ listOfMtrs -> listOfMtrs.size() == 1 } as List<FileCollectionTaskRequest>, _)

        where:
        processType | recordedRopTime                        | currentTime                            | ropPeriod | scannerActivationTime                  | tasksToSend | tasksToCreate
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:07:30") | 900       | getTimeInMillis("15/05/2016 12:02:00") | 0           | 0
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:07:30") | 900       | getTimeInMillis("15/05/2016 11:59:00") | 0           | 1
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:07:30") | 900       | getTimeInMillis("15/05/2016 11:45:00") | 0           | 1
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:07:30") | 900       | getTimeInMillis("15/05/2016 11:44:59") | 1           | 2
        STATS       | getTimeInMillis("15/05/2016 11:30:00") | getTimeInMillis("15/05/2016 12:07:30") | 900       | getTimeInMillis("15/05/2016 12:02:00") | 0           | 0
        STATS       | getTimeInMillis("15/05/2016 11:30:00") | getTimeInMillis("15/05/2016 12:07:30") | 900       | getTimeInMillis("15/05/2016 11:59:00") | 0           | 1
        STATS       | getTimeInMillis("15/05/2016 11:30:00") | getTimeInMillis("15/05/2016 12:07:30") | 900       | getTimeInMillis("15/05/2016 11:45:00") | 0           | 1
        STATS       | getTimeInMillis("15/05/2016 11:30:00") | getTimeInMillis("15/05/2016 12:07:30") | 900       | getTimeInMillis("15/05/2016 11:44:59") | 1           | 2
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:30") | 60        | getTimeInMillis("15/05/2016 12:00:10") | 0           | 0
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:30") | 60        | getTimeInMillis("15/05/2016 11:59:50") | 0           | 1
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:30") | 60        | getTimeInMillis("15/05/2016 11:59:00") | 0           | 1
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:30") | 60        | getTimeInMillis("15/05/2016 11:58:59") | 1           | 2
        STATS       | getTimeInMillis("15/05/2016 11:58:00") | getTimeInMillis("15/05/2016 12:00:30") | 60        | getTimeInMillis("15/05/2016 12:00:10") | 0           | 0
        STATS       | getTimeInMillis("15/05/2016 11:58:00") | getTimeInMillis("15/05/2016 12:00:30") | 60        | getTimeInMillis("15/05/2016 11:59:50") | 0           | 1
        STATS       | getTimeInMillis("15/05/2016 11:58:00") | getTimeInMillis("15/05/2016 12:00:30") | 60        | getTimeInMillis("15/05/2016 11:59:00") | 0           | 1
        STATS       | getTimeInMillis("15/05/2016 11:58:00") | getTimeInMillis("15/05/2016 12:00:30") | 60        | getTimeInMillis("15/05/2016 11:58:59") | 1           | 2
    }

    def "even if there are no tasks to create, if preconditions are correct, the record will be updated"() {
        given: "Node exists in dps but there are no active scanners"
        createMocks(currentTime, recordedRopTime)
        fileCollectionLastRopData.recordRopStartTimeForTaskCreation(new RopTime(recordedRopTime, ropPeriod))
        when: "timer is passed to create file collection task"
        ropPeriod == 900 ? fileCollectionTaskManagerBean.createFileCollectionTasksForRop(fifteenMinutetimer) : fileCollectionTaskManagerBean.
                createFileCollectionTasksForRop(oneMinuteTimer)

        then: "task should be created and added to the cache"
        fileCollectionTaskCache.size() == tasksToCreate

        and: "File collection task for all tasks are sent"
        tasksToSend * eventSender.send(_ as List<FileCollectionTaskRequest>, _)
        and: "Recorded time is updated"
        updatedRecord == fileCollectionLastRopData.getFromCache(LAST_FILE_COLLECTION_TASKS_CREATED_FOR_ROP + ropPeriod)
        where:
        processType | recordedRopTime                        | currentTime                            | ropPeriod | updatedRecord                          | tasksToSend | tasksToCreate
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:07:30") | 900       | getTimeInMillis("15/05/2016 12:00:00") | 0           | 0
        STATS       | getTimeInMillis("22/09/1900 23:59:59") | getTimeInMillis("15/05/2016 12:00:30") | 60        | getTimeInMillis("15/05/2016 12:00:00") | 0           | 0
    }

    private void addActiveScannersToCache(ProcessType processType, int ropPeriod, long currentTime) {
        RopTime somePastRop = new RopTime(currentTime, ropPeriod).getLastROP(50);
        ProcessRequestVO requestVO = createProcessRequest("NetworkElement=$NODE_ADDRESS1", processType, ropPeriod, somePastRop.getCurrentRopStartTimeInMilliSecs())
        fileCollectionActiveTasksCache.addProcessRequest(requestVO)
    }

    private static long getTimeInMillis(String time) {
        return simpleDateFormat.parse(time).getTime()
    }

    private static ProcessRequestVO createProcessRequest(final String nodeFdn, final ProcessType processType, final int ropPeriodInSeconds,
                                                         final long time) {
        final RopTime ropTime = new RopTime(time, ropPeriodInSeconds);
        final ProcessRequestVO processRequest = new ProcessRequestVO.ProcessRequestVOBuilder(nodeFdn, ropPeriodInSeconds, processType.name())
                .startTime(ropTime.getPreviousROPPeriodEndTime().getTime() - 1000).build();
        return processRequest;
    }

    private static ProcessTypesAndRopInfo createProcessTypeRopInfo(final int ropPeriod, final String processType) {
        final ProcessTypesAndRopInfo processTypesAndRopInfo = new ProcessTypesAndRopInfo();
        processTypesAndRopInfo.addRopInfoAndProcessType(ropPeriod, processType);
        return processTypesAndRopInfo;
    }
}
