/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.collection.schedulers;

import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.ejb.*;
import javax.ejb.Timer;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.services.pm.collection.api.FileCollectionTaskManagerLocal;
import com.ericsson.oss.services.pm.collection.api.FileCollectionTaskSenderLocal;
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;
import com.ericsson.oss.services.pm.collection.api.ProcessTypesAndRopInfo;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionLastRopData;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionScheduledRecoveryCacheWrapper;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionTaskCacheWrapper;
import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.common.logging.PMICLog;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;
import com.ericsson.oss.services.pm.initiation.util.RopTime;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.time.TimeGenerator;

/**
 * This Class manages file collection tasks. Responsible to create file collection tasks for ROP.
 *
 * @author ekamkal, epirgui
 */
@Stateless
public class FileCollectionTaskManagerBean implements FileCollectionTaskManagerLocal {

    protected static final String MSG_ERROR_COULD_NOT_ADD_FILE_COLLECTION_TASK = "Could not add file collection task to cache for network element:{}";

    private static final long TIMER_INTERVAL_IN_MILLIS = 15000L; // 15 sec

    @Inject
    private Logger logger;

    @Inject
    private FileCollectionTaskCacheWrapper fileCollectionTaskCache;

    @Inject
    private FileCollectionScheduledRecoveryCacheWrapper fileCollectionScheduledRecoveryCache;

    @Inject
    private FileCollectionActiveTaskCacheWrapper fileCollectionActiveTasksCache;

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    @Inject
    private TimerService timerService;

    @Inject
    private FileCollectionTaskWrapperFactory fileCollectionTaskFactory;

    @Inject
    private MembershipListener membershipChangeListener;

    @Inject
    private TimeGenerator timeGenerator;

    @Inject
    private CollectionSchedulerHelper collectionSchedulerHelper;

    @Inject
    private FileCollectionLastRopData fileCollectionLastRopData;

    @Inject
    private FileCollectionTaskSenderLocal fileCollectionTaskSender;

    @Inject
    private NodeService nodeService;

    @Inject
    private SupportedRopTimes supportedRopTimes;

    /**
     * This is FileCollection task creation timer , on expiration it creates file collection tasks and add them to cache. Timer in this class is
     * responsible for creating FileCollectionTask for nodes. Timer times out every 15 seconds and tries to create tasks. In a normal scenario, the
     * timer will create tasks in the middle of the ROP or any 15 seconds after the last execution. Only tasks that were no created in the previous
     * ROP may be created in the first half of the current ROP. This is to handle the upgrade/availability scenarios where files creation was missed,
     * but we only recover one ROP from behind.
     *
     * @param timer
     *            - timer to provide Record Output Period info for file collection task to be created
     */
    @Timeout
    @LogProfiler(name = "Creating file collection tasks", ignoreExecutionTimeLowerThan = 10L)
    public void createFileCollectionTasksForRop(final Timer timer) {
        final Integer ropTimePeriodInSeconds = (Integer) timer.getInfo();
        final long currentTime = timeGenerator.currentTimeMillis();
        logger.debug("Total tasks in fileCollectionTaskCache : {}", fileCollectionTaskCache.size());
        logger.debug("Total processRequests in fileCollectionActiveTasksCache : {}", fileCollectionActiveTasksCache.size());
        if (membershipChangeListener.isMaster() && supportedRopTimes.getSupportedRopTimeList().contains(ropTimePeriodInSeconds.longValue())) {
            boolean createTasksForCurrentRop = false;
            boolean createTasksForPreviousRop = false;
            if (fileCollectionLastRopData.shouldCreateTasksForPreviousRop(new RopTime(currentTime, ropTimePeriodInSeconds))) {
                // in case we missed tasks in the previous rop due to availability/upgrade
                createTasksForPreviousRop = true;
            }
            if (fileCollectionLastRopData.shouldCreateTasksForCurrentRop(new RopTime(currentTime, ropTimePeriodInSeconds))) {
                createTasksForCurrentRop = true;
            }
            createTasks(ropTimePeriodInSeconds, currentTime, createTasksForCurrentRop, createTasksForPreviousRop);
        }
    }

    private void createTasks(final int ropTimePeriodInSeconds, final long currentTime, final boolean createTasksForCurrentRop,
            final boolean createTasksForPreviousRop) {
        if (createTasksForPreviousRop || createTasksForCurrentRop) {
            final Set<ProcessRequestVO> processRequests = fileCollectionActiveTasksCache.getProcessRequestForRop(ropTimePeriodInSeconds);
            systemRecorder.eventCoarse(PMICLog.Event.BUILD_FILE_COLLECTION_TASK_LIST, getClass().getSimpleName(),
                    "Creating file collection tasks for rop {} in master node. Currently there are {} tasks in fileCollectionTaskCache "
                            + "and {} processRequests in fileCollectionActiveTasksCache for this rop. Tasks Creation for Current Rop: {} "
                            + "and for Previous Rop: {}", ropTimePeriodInSeconds, fileCollectionTaskCache.size(),
                    processRequests.size(), createTasksForCurrentRop, createTasksForPreviousRop);
            RopTime rop = null;
            if (createTasksForPreviousRop) {
                rop = new RopTime(currentTime, ropTimePeriodInSeconds).getLastROP(1);
                final List<FileCollectionTaskWrapper> createdTasks = updateFileCollectionTaskCache(processRequests, rop, true);
                if (!createdTasks.isEmpty()) {
                    fileCollectionTaskSender.sendFileCollectionTasks(createdTasks, rop);
                }
            }
            if (createTasksForCurrentRop) {
                rop = new RopTime(currentTime, ropTimePeriodInSeconds);
                updateFileCollectionTaskCache(processRequests, rop, false);
            }
            fileCollectionLastRopData.recordRopStartTimeForTaskCreation(rop);
        } else {
            logger.trace("No tasks to create for rop {} at this time.", ropTimePeriodInSeconds);
        }
    }

    private List<FileCollectionTaskWrapper> updateFileCollectionTaskCache(final Set<ProcessRequestVO> processRequests,
            final RopTime ropForWhichTasksShouldBeUpdatedFor,
            final boolean createUpgradeRecoveryTasks) {
        final List<FileCollectionTaskWrapper> createdTasks = new ArrayList<>();
        if (processRequests.isEmpty()) {
            logger.info("No process Requests found for rop interval {} seconds ", ropForWhichTasksShouldBeUpdatedFor.getRopPeriodInSeconds());
            return createdTasks;
        }
        int taskCount = 0;
        final RopTimeInfo ropTimeInfo = supportedRopTimes.getRopTime(ropForWhichTasksShouldBeUpdatedFor.getRopPeriodInSeconds());
        for (final ProcessRequestVO processRequest : processRequests) {
            if (processRequest.getEndTime() > 0 && processRequest.getEndTime() < timeGenerator.currentTimeMillis()) {
                // file collection request has expired. remove from the cache
                logger.info("File collection request expired. Removing from the cache. Request {}", processRequest);
                fileCollectionActiveTasksCache.removeProcessRequest(processRequest);
            } else {
                try {
                    if (processRequest.getStartTime() < ropForWhichTasksShouldBeUpdatedFor.getPreviousROPPeriodEndTime().getTime()) {
                        taskCount = createTask(ropForWhichTasksShouldBeUpdatedFor, createdTasks, taskCount, ropTimeInfo, processRequest);
                    } else {
                        logger.debug("Task not created for {} as criteria not met {}", processRequest,
                                ropForWhichTasksShouldBeUpdatedFor.getPreviousROPPeriodEndTime().getTime());
                    }
                } catch (final Exception e) {
                    logger.error(MSG_ERROR_COULD_NOT_ADD_FILE_COLLECTION_TASK, processRequest.getNodeAddress(), e);
                    systemRecorder.error(PMICLog.Error.FILE_COLLECTION_TASK_ADD_TO_CACHE_FAILURE, processRequest.getNodeAddress(),
                            processRequest.getNodeAddress(), PMICLog.Operation.FILE_COLLECTION);
                }
            }
        }
        recordCreatedTasks(processRequests, ropForWhichTasksShouldBeUpdatedFor, createUpgradeRecoveryTasks, taskCount, ropTimeInfo);
        return createdTasks;
    }

    private void recordCreatedTasks(final Set<ProcessRequestVO> processRequests, final RopTime ropForWhichTasksShouldBeUpdatedFor,
            final boolean createUpgradeRecoveryTasks, final int taskCount, final RopTimeInfo ropTimeInfo) {
        if (createUpgradeRecoveryTasks) {
            systemRecorder.eventCoarse(PMICLog.Event.BUILD_FILE_COLLECTION_TASK_LIST, getClass().getSimpleName(),
                    "Created {} FileCollectionTask requests for ROP period {} for ROP timestamp {}, to be collected now as part of last "
                            + "rop recovery. Total active scanners for this ROP period :{}.",
                    taskCount,
                    ropForWhichTasksShouldBeUpdatedFor.getRopPeriodInSeconds(),
                    ropForWhichTasksShouldBeUpdatedFor.getCurrentROPPeriodEndTime().getOssTimeStamp(TimeZone.getDefault().getID()),
                    processRequests.size());
        } else {
            systemRecorder.eventCoarse(PMICLog.Event.BUILD_FILE_COLLECTION_TASK_LIST, getClass().getSimpleName(),
                    "Created {} FileCollectionTask requests for ROP period {} for ROP timestamp {}, to be collected at {}. "
                            + "Total active scanners for this ROP period :{}",
                    taskCount,
                    ropForWhichTasksShouldBeUpdatedFor.getRopPeriodInSeconds(),
                    ropForWhichTasksShouldBeUpdatedFor.getCurrentROPPeriodEndTime().getOssTimeStamp(TimeZone.getDefault().getID()),
                    new Date(ropForWhichTasksShouldBeUpdatedFor.getCurrentROPPeriodEndTime().getCurrentRopStartTimeInMilliSecs()
                            + ropTimeInfo.getCollectionDelayInMilliSecond()),
                    processRequests.size());
        }
    }

    private int createTask(final RopTime ropForWhichTasksShouldBeUpdatedFor, final List<FileCollectionTaskWrapper> createdTasks,
            final int countOfTask,
            final RopTimeInfo ropTimeInfo, final ProcessRequestVO processRequest) {
        int countOfTaskLocal = countOfTask;
        final FileCollectionTaskWrapper fileCollectionTaskWrapper = fileCollectionTaskFactory
                .createFileCollectionTaskRequestWrapper(processRequest, ropForWhichTasksShouldBeUpdatedFor, ropTimeInfo);
        if (fileCollectionTaskWrapper != null) {
            fileCollectionTaskCache.addTask(fileCollectionTaskWrapper);
            createdTasks.add(fileCollectionTaskWrapper);
            countOfTaskLocal++;
            logger.debug("FileCollectionTask {} added to FileCollectionTaskCache for Node {}. Is recovery: {}.",
                    fileCollectionTaskWrapper, fileCollectionTaskWrapper.getFileCollectionTaskRequest().getNodeAddress(),
                    fileCollectionTaskWrapper.getFileCollectionTaskRequest().isRecoverInNextRop());
        } else {
            logger.debug("FileCollectionTask not created for node [{}] , process type [{}]  ", processRequest.getNodeAddress(),
                    processRequest.getProcessType());
        }
        return countOfTaskLocal;
    }

    /**
     * Add FileCollectionRequest to FileCollectionActiveTasksCache and also add file collection task for current rop
     */
    @Override
    @LogProfiler(name = "Starting file collection for all active processes on startup")
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void startFileCollectionForNodes(final Map<String, ProcessTypesAndRopInfo> nodesWithProcessTypes) {
        for (final Map.Entry<String, ProcessTypesAndRopInfo> nodeWithProcessTypeEntry : nodesWithProcessTypes.entrySet()) {
            if (nodeService.isMediationAutonomyEnabled(nodeWithProcessTypeEntry.getKey())) {
                continue;
            }
            final ProcessTypesAndRopInfo processAndRopInfo = nodeWithProcessTypeEntry.getValue();
            final Map<Integer, Set<String>> ropAndProcessTypeInfo = processAndRopInfo.getRopAndProcessTypes();
            ropAndProcessTypeInfo.remove(RopPeriod.NOT_APPLICABLE.getDurationInSeconds());

            for (final Map.Entry<Integer, Set<String>> ropAndProcessTypeInfoEntry : ropAndProcessTypeInfo.entrySet()) {
                logger.debug("Adding Node {} for file collection of {} seconds ROP interval ",
                        nodeWithProcessTypeEntry.getKey(), ropAndProcessTypeInfoEntry.getKey());
                final RopTime ropTime = new RopTime(timeGenerator.currentTimeMillis(), ropAndProcessTypeInfoEntry.getKey()).getLastROP(1);
                final Set<String> processTypes = ropAndProcessTypeInfoEntry.getValue();
                for (final String processType : processTypes) {
                    final ProcessRequestVO requestVO = new ProcessRequestVO.ProcessRequestVOBuilder(nodeWithProcessTypeEntry.getKey(),
                            ropAndProcessTypeInfoEntry.getKey(), processType).startTime(ropTime.getCurrentRopStartTimeInMilliSecs() - 1L).build();
                    fileCollectionActiveTasksCache.addProcessRequest(requestVO);
                }
            }
        }
    }

    @Override
    public void startFileCollection(final ProcessRequestVO requestVO) {
        if (nodeService.isMediationAutonomyEnabled(requestVO.getNodeAddress())) {
            return;
        }
        logger.info("Starting file collection request: {}.", requestVO);
        final ProcessRequestVO updatedRequest = new ProcessRequestVO.ProcessRequestVOBuilder(requestVO.getNodeAddress(), requestVO.getRopPeriod(),
                requestVO.getProcessType()).startTime(timeGenerator.currentTimeMillis()).build();
        // on addProcessRequest the request is not updated, if the cache does contain an entry for the key ?
        fileCollectionActiveTasksCache.addProcessRequest(updatedRequest);
        createTimer(requestVO.getRopPeriod());
    }

    @Override
    public void updateFileCollectionForNewRopPeriod(final ProcessRequestVO requestVO, final long currentRopEndTimeInMills,
            final int oldRopPeriodInSeconds) {
        logger.debug("Updating File collection request: {} for new ROP period: {} to start at: {}.", requestVO, requestVO.getRopPeriod(),
                currentRopEndTimeInMills);
        final String key = fileCollectionActiveTasksCache.getKeyForRop(requestVO, oldRopPeriodInSeconds);
        final ProcessRequestVO requestVOCache = fileCollectionActiveTasksCache.getProcessRequest(key);
        if (requestVOCache != null) {
            final ProcessRequestVO updatedRequest = new ProcessRequestVO.ProcessRequestVOBuilder(requestVOCache.getNodeAddress(),
                    requestVOCache.getRopPeriod(), requestVOCache.getProcessType()).startTime(requestVOCache.getStartTime())
                            .endTime(currentRopEndTimeInMills).build();
            fileCollectionActiveTasksCache.removeProcessRequest(key);
            fileCollectionActiveTasksCache.addProcessRequest(updatedRequest);
            logger.debug("{} updated in file collection active node list cache. Number of process requests on list: {}.", updatedRequest,
                    fileCollectionActiveTasksCache.size());
        }
        final ProcessRequestVO newFileCollectionRequest = new ProcessRequestVO.ProcessRequestVOBuilder(requestVO.getNodeAddress(),
                requestVO.getRopPeriod(), requestVO.getProcessType()).startTime(currentRopEndTimeInMills - TimeUnit.SECONDS.toMillis(1)).build();
        fileCollectionActiveTasksCache.addProcessRequest(newFileCollectionRequest);
        systemRecorder.eventCoarse(PMICLog.Event.GPEH_ONE_MINUTE_LICENSE_UPDATE_EVENT, getClass().getSimpleName(),
                "{} added to file collection active node list cache and file collection scheduled. Number of process requests on list: {}.",
                newFileCollectionRequest, fileCollectionActiveTasksCache.size());
        createTimer(requestVO.getRopPeriod());
    }

    @Override
    public void stopFileCollection(final ProcessRequestVO requestVO) {
        logger.info("Stopping file collection request: {}", requestVO);
        final String key = fileCollectionActiveTasksCache.getKeyFor(requestVO);
        final ProcessRequestVO requestVOCache = fileCollectionActiveTasksCache.getProcessRequest(key);
        if (requestVOCache != null) {
            final ProcessRequestVO updatedRequest = new ProcessRequestVO.ProcessRequestVOBuilder(requestVOCache.getNodeAddress(),
                    requestVOCache.getRopPeriod(), requestVOCache.getProcessType()).startTime(requestVOCache.getStartTime())
                            .endTime(timeGenerator.currentTimeMillis()).build();
            fileCollectionActiveTasksCache.removeProcessRequest(key);
            logger.debug("{} removed from file collection active node list cache. Number of process requests on list: {}.", requestVO,
                    fileCollectionActiveTasksCache.size());
            fileCollectionScheduledRecoveryCache.addProcessRequest(updatedRequest);
            logger.debug("{} added to file collection Scheduled recovery cache. Number of process requests on list: {}.", updatedRequest,
                    fileCollectionScheduledRecoveryCache.size());
        } else {
            logger.info("Scanner not found in cache for ProcessRequestVO: {}", requestVO);
        }
        stopTimer(requestVO.getRopPeriod());
    }

    @Override
    public boolean createTimer(final int ropPeriodInSeconds) {
        final Timer timer = getTimerFromTimerService(ropPeriodInSeconds);
        boolean isTimerCreated = false;
        if (timer == null) {
            final RopTimeInfo supportedRopTime = supportedRopTimes.getRopTime(ropPeriodInSeconds);
            if (supportedRopTime == null) {
                logger.warn("Rop time {} seconds not supported currently", ropPeriodInSeconds);
            } else {
                final long initialDelay = collectionSchedulerHelper.getTaskCreationTimerInitialDealay(ropPeriodInSeconds);
                logger.info("Creating file collection task manager timer for rop period {}", ropPeriodInSeconds);
                final TimerConfig timerConfig = new TimerConfig(ropPeriodInSeconds, false);
                timerService.createIntervalTimer(initialDelay, TIMER_INTERVAL_IN_MILLIS, timerConfig);
                isTimerCreated = true;
                systemRecorder.eventCoarse(PMICLog.Event.CREATE_FILE_COLLECTION_TASK_MANAGER_TIMER, getClass().getSimpleName(),
                        "Created file collection task manager timer with interval {}(seconds) for ROP period {} to start "
                                + "creating file collection tasks at {}",
                        TIMER_INTERVAL_IN_MILLIS / 1000, ropPeriodInSeconds, new Date(timeGenerator.currentTimeMillis() + initialDelay));
            }
        }
        return isTimerCreated;
    }

    @Override
    public boolean stopTimer(final int ropTime) {
        return stopTimer(ropTime, false);
    }

    @Override
    public boolean stopTimer(final int ropTime, final boolean isForced) {
        boolean isTimerStopped = false;
        if (isForced || fileCollectionActiveTasksCache.getProcessRequestForRop(ropTime).isEmpty()) {
            logger.info("No Active scanners exists for this rop {}. Stopping timer to stop creating future file collection tasks", ropTime);
            isTimerStopped = getAndCancelTimer(ropTime);
        }
        return isTimerStopped;
    }

    private boolean getAndCancelTimer(final int ropTime) {
        boolean isTimerStopped = false;
        try {
            final Timer timer = getTimerFromTimerService(ropTime);
            if (timer != null) {
                logger.debug("Stopping {}s ROP  timer for File collection", timer.getInfo());
                timer.cancel();
                logger.debug("Stopped {}s ROP  timer for File collection", timer.getInfo());
                isTimerStopped = true;
            }
        } catch (final Exception e) {
            logger.error("Failed to stop timer for roptime {} Exception {}", ropTime, e);
        }

        return isTimerStopped;
    }

    private Timer getTimerFromTimerService(final int ropTime) {
        final Collection<Timer> timers = timerService.getTimers();
        for (final Timer timer : timers) {
            if (timer.getInfo().equals(ropTime)) {
                return timer;
            }
        }
        return null;
    }
}
