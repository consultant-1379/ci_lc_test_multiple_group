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

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.SEND_FILE_COLLECTION_TASK_LIST;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.service.pm.jmsreconnect.JMSLostFileCollectionTasksHandler;
import com.ericsson.oss.services.pm.collection.api.FileCollectionTaskSenderLocal;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionLastRopData;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionTaskCacheWrapper;
import com.ericsson.oss.services.pm.collection.cache.utils.FileCollectionSorter;
import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.common.logging.PMICLog;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;
import com.ericsson.oss.services.pm.initiation.util.RopTime;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.time.TimeGenerator;

/**
 * File collection task sender bean to send file collection tasks and retrieve rop info.
 */
@Stateless
public class FileCollectionTaskSenderBean implements FileCollectionTaskSenderLocal {

    private static final long TIMER_INTERVAL_IN_MILLIS = 15000L; // 15 sec

    @Inject
    private FileCollectionTaskCacheWrapper fileCollectionTaskCache;

    @Inject
    private Logger logger;

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    @Inject
    private TimerService timerService;

    @Inject
    private MembershipListener membershipChangeListener;

    @Inject
    private TimeGenerator timeGenerator;

    @Inject
    private FileCollectionTaskSenderBeanHelper helper;

    @Inject
    private CollectionSchedulerHelper collectionSchedulerHelper;

    @Inject
    private FileCollectionSorter sorter;

    @Inject
    private JMSLostFileCollectionTasksHandler lostTaskHandler;

    @Inject
    private FileCollectionLastRopData fileCollectionLastRopData;

    @Inject
    private SupportedRopTimes supportedRopTimes;

    /**
     * Send file collection tasks to mediation Will send tasks for "current" ROP, if current instance is master and if the last ROP the tasks were
     * sent for is not current ROP. In current ROP we send tasks generated in previous ROP and record the start time of previous ROP(we record the ROP
     * for which tasks were sent) More detailed information can be found in the relevant javadocs.
     *
     * @param timer
     *            - timer to proved Record Output Period info for file collection task to be sent
     */
    @Timeout
    @LogProfiler(name = "Sending file collection tasks", ignoreExecutionTimeLowerThan = 10L)
    public void sendFileCollectionTasks(final Timer timer) {
        final TaskSenderTimerConfig config = (TaskSenderTimerConfig) timer.getInfo();
        final Integer ropPeriodInSec = config.ropPeriodInSeconds;
        final long previousExpirationTime = config.previousExpirationTime;
        final String ropInfo = ropPeriodInSec.toString();
        final long currentTime = timeGenerator.currentTimeMillis();
        final boolean shouldSendTasks = fileCollectionLastRopData.shouldSendTasks(new RopTime(currentTime, ropPeriodInSec));
        final boolean checkForSingleRopRecovery = SupportedRopTimes.isSingleRopRecoveryCheckRequired(ropPeriodInSec, previousExpirationTime,
                currentTime);
        config.previousExpirationTime = currentTime;
        logger.trace("ropPeriodInSec:{} shouldSendTasks:{} checkForSingleRopRecovery:{}", ropPeriodInSec, shouldSendTasks, checkForSingleRopRecovery);
        if (membershipChangeListener.isMaster() && (shouldSendTasks || checkForSingleRopRecovery)) {
            List<FileCollectionTaskWrapper> sentTasks = new ArrayList<>();
            final RopTime previousRop = new RopTime(currentTime, ropPeriodInSec).getLastROP(1);
            final List<FileCollectionTaskWrapper> allTasksSorted = fileCollectionTaskCache.getAllTasksSorted(ropPeriodInSec);
            final int fileCollectionQueueSize = allTasksSorted.size();
            if (!allTasksSorted.isEmpty()) {
                sentTasks = helper.sendOrSkipExecutableFileCollectionTasks(ropInfo, allTasksSorted);
            }
            systemRecorder.eventCoarse(SEND_FILE_COLLECTION_TASK_LIST, ropInfo,
                    "File collection tasks queue size: {}. Sent {} File Collection tasks for Rop {}.", fileCollectionQueueSize, sentTasks.size(),
                    ropInfo);
            lostTaskHandler.setSentFileCollectionTasks(ropInfo, sentTasks);
            if (shouldSendTasks) {
                fileCollectionLastRopData.recordRopStartTimeForTaskSending(previousRop);
            }
        }
    }

    @Override
    @LogProfiler(name = "Sending file collection tasks, last recorded 'sending' was more than one rop ago", ignoreExecutionTimeLowerThan = 1L)
    public void sendFileCollectionTasks(final List<FileCollectionTaskWrapper> tasksToSend, final RopTime rop) {
        sorter.sortFileCollectionTaskRequests(tasksToSend);
        int numberOfTasksSent = 0;
        if (!tasksToSend.isEmpty()) {
            numberOfTasksSent = helper.sendFileCollectionTasks(tasksToSend);
        }
        systemRecorder.eventCoarse(SEND_FILE_COLLECTION_TASK_LIST, String.valueOf(rop.getRopPeriodInSeconds()),
                "Requested to send {} tasks because they were not sent previously. Managed to send {} File Collection tasks for Rop {}.",
                tasksToSend.size(), numberOfTasksSent, rop.getRopPeriodInSeconds());
        fileCollectionLastRopData.recordRopStartTimeForTaskSending(rop);
    }

    /*
     * (non-Javadoc) Creating timer that would send file collection tasks
     * @see com.ericsson.oss.services.pm.scheduling.api.RopSchedulerServiceLocal# createTimer(long)
     */
    @Override
    public boolean createTimer(final int ropPeriodInSeconds) {
        final Timer timer = getTimerForRop(ropPeriodInSeconds);
        boolean isTimerCreated = false;
        if (timer == null) {
            final RopTimeInfo supportedRopTime = supportedRopTimes.getRopTime(ropPeriodInSeconds);
            if (supportedRopTime == null) {
                logger.debug("Rop time {} seconds not supported currently", ropPeriodInSeconds);
            } else {
                final long initialDelay = getInitialDelay(ropPeriodInSeconds);
                logger.info("Creating file collection task sender timer for rop period {}", ropPeriodInSeconds);
                final TimerConfig timerConfig = new TimerConfig(new TaskSenderTimerConfig(ropPeriodInSeconds, 0), false);
                timerService.createIntervalTimer(initialDelay, TIMER_INTERVAL_IN_MILLIS, timerConfig);
                isTimerCreated = true;
                systemRecorder.eventCoarse(PMICLog.Event.CREATE_FILE_COLLECTION_SENDER_TIMER, getClass().getSimpleName(),
                        "Created file collection task sender timer with interval {}(seconds) for ROP period {} to start "
                                + "sending file collection tasks at {}",
                        TIMER_INTERVAL_IN_MILLIS / 1000, ropPeriodInSeconds, new Date(timeGenerator.currentTimeMillis() + initialDelay));
            }
        }
        return isTimerCreated;
    }

    private long getInitialDelay(final long ropTime) {
        return collectionSchedulerHelper.getTaskSenderInitialDelay(ropTime);
    }

    @Override
    public boolean stopTimer(final int ropTime) {
        boolean isTimerStopped = false;
        final Timer timer = getTimerForRop(ropTime);
        if (timer != null) {
            logger.info("Stopping file collection task sender timer to stop sending file collection tasks");
            timer.cancel();
            isTimerStopped = true;
            final List<FileCollectionTaskWrapper> allTasksSorted = fileCollectionTaskCache.getAllTasksSorted(ropTime);
            for (final FileCollectionTaskWrapper wrapper : allTasksSorted) {
                logger.info("Removing file collection task {} from fileCollectionTaskCache", wrapper);
                fileCollectionTaskCache.removeTask(wrapper.getFileCollectionTaskRequest().getJobId());
            }
        }
        return isTimerStopped;
    }

    /**
     * get Timer from TimerService for RopPeriod
     *
     * @param ropTime
     *            - Record Output Period to get timer for
     * @return - returns Timer for Record Output Period
     */
    public Timer getTimerForRop(final int ropTime) {
        final Collection<Timer> timers = timerService.getTimers();
        for (final Timer timer : timers) {
            final TaskSenderTimerConfig config = (TaskSenderTimerConfig) timer.getInfo();
            if (config.ropPeriodInSeconds == ropTime) {
                return timer;
            }
        }
        return null;
    }

    @Override
    public boolean stopTimer(final int ropTime, final boolean isForced) {
        return false;
    }

    /**
     * TimerConfig for TaskSender timer.
     */
    public static class TaskSenderTimerConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        int ropPeriodInSeconds;
        long previousExpirationTime;

        public TaskSenderTimerConfig(final int ropPeriodInSeconds, final long previousExpirationTime) {
            super();
            this.ropPeriodInSeconds = ropPeriodInSeconds;
            this.previousExpirationTime = previousExpirationTime;
        }
    }
}
