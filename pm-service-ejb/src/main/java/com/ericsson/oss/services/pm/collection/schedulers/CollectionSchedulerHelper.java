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

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.services.pm.collection.roptime.RopConstants;
import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.initiation.util.RopTime;
import com.ericsson.oss.services.pm.time.TimeGenerator;

/**
 * Helper class to calculate initial delay for FileCollectionTask creation and sender timer. Creation and sender Timers are created in
 * FileCollectionTaskManagerBean and FileCollectionTaskSenderBean respectively.
 *
 * @author ekamkal
 */
@ApplicationScoped
public class CollectionSchedulerHelper {

    @Inject
    private TimeGenerator timeGenerator;

    @Inject
    private SupportedRopTimes supportedRopTimes;

    /**
     * Calculate FileCollectionTaskManager Timer initial delay to start timer. FileCollectionTaskManagerBean is responsible to create
     * FileCollectionTasks for nodes.
     *
     * @param ropTime
     *            - Record Output Period Time Object used to supported Record Output Period times
     * @return - returns TaskCreationTime Delay
     */
    public long getTaskCreationTimerInitialDealay(final long ropTime) {

        final long currentTime = timeGenerator.currentTimeMillis();
        final RopTimeInfo supportedRopTime = supportedRopTimes.getRopTime(ropTime);
        final RopTime actualRopTime = new RopTime(currentTime, supportedRopTime.getRopTimeInSeconds());

        if (currentTime > actualRopTime.getCurrentROPPeriodEndTime().getTime() - supportedRopTime.getRopTimeInMilliSecond()
                / RopConstants.COLLECTION_POINT) {
            return getTaskCreationTimerInitialDelayForNextRop(supportedRopTime, actualRopTime, currentTime);
        } else {
            return getTaskCreationTimerInitialDelayForCurrentROP(supportedRopTime, actualRopTime, currentTime);
        }

    }

    /**
     * While switching over timer must be started at the right time. If switch over occurs just before the collection task creation was about to start
     * , FileCollectionTaskManger timer must start at
     * the right time. E.g. For ROP 10:00-10:15 file collection task creation time is 10:07:30(hhMMss) and switch over occurs at 10:05 in this case
     * next master instance must start a
     * FileCollectionTaskManager timer at 10:07:30. And FileCollectionSender timer at 10:20.
     *
     * @param supportedRopTime
     *            - supported Record Output Period time used to create task creation delay for current Record Output Period
     * @param actualRopTime
     *            - actual Record Output Period time used to create task creation delay for current Record Output Period
     * @param currentTime
     *            - current time used to create task creation delay for current Record Output Period
     * @return
     */
    private long getTaskCreationTimerInitialDelayForCurrentROP(final RopTimeInfo supportedRopTime, final RopTime actualRopTime,
            final long currentTime) {
        return actualRopTime.getCurrentROPPeriodEndTime().getTime() - supportedRopTime.getRopTimeInMilliSecond() / RopConstants.COLLECTION_POINT
                - currentTime;
    }

    /**
     * If switch over occurs after the file collection task creation timer then FileCollectionTaskManager timer must start in next ROP. E.g. for ROP
     * 10:00-10:15 file collection task creation time is
     * 10:07:30(hhMMss) and switch over occurs at 10:10 in this case next master instance must start a FileCollectionTaskManager timer at 10:22:30.
     * And FileCollectionSender timer at 10:35.
     *
     * @param supportedRopTime
     *            - supported Record Output Period time used to create task creation delay for next Record Output Period
     * @param actualRopTime
     *            - actual Record Output Period time used to create task creation delay for next Record Output Period
     * @return
     */
    private long getTaskCreationTimerInitialDelayForNextRop(final RopTimeInfo supportedRopTime, final RopTime actualRopTime,
            final long currentTime) {
        return actualRopTime.getCurrentROPPeriodEndTime().getTime() - currentTime + supportedRopTime.getRopTimeInMilliSecond()
                / RopConstants.COLLECTION_POINT;
    }

    /**
     * To calculate FileCollectionTaskSenderBean timer to start sending FileCollectionTask FileCollectionTaskSenderBean is responsible to send
     * FileCollectionTasks to mediation.
     *
     * @param ropTime
     *            - Record Output Period Time used to create task sender delay
     * @return - returns TaskSender Delay
     */
    public long getTaskSenderInitialDelay(final long ropTime) {
        final long currentTime = timeGenerator.currentTimeMillis();
        final RopTimeInfo supportedRopTime = supportedRopTimes.getRopTime(ropTime);
        final RopTime actualRopTime = new RopTime(currentTime, supportedRopTime.getRopTimeInSeconds());

        if (currentTime > actualRopTime.getPreviousROPPeriodEndTime().getTime() + supportedRopTime.getCollectionDelayInMilliSecond()) {
            return getTaskSenderInitialDelayForCurrentRop(supportedRopTime, actualRopTime, currentTime);
        } else {
            return getTaskSenderInitialDelayForPreviousROP(supportedRopTime, actualRopTime, currentTime);
        }

    }

    /**
     * @param supportedRopTime
     *            - supported Record Output Period time used to create task sender delay for previous Record Output Period
     * @param actualRopTime
     *            - actual Record Output Period time used to create task sender delay for previous Record Output Period
     * @param currentTime
     *            - current time used to create task sender delay for previous Record Output Period
     * @return - returns TaskSender Delay for previous Record Output Period
     */
    private long getTaskSenderInitialDelayForPreviousROP(final RopTimeInfo supportedRopTime, final RopTime actualRopTime,
            final long currentTime) {
        return actualRopTime.getPreviousROPPeriodEndTime().getTime() + supportedRopTime.getCollectionDelayInMilliSecond() - currentTime;
    }

    /**
     * @param supportedRopTime
     *            - supported Record Output Period time used to create task sender delay for current Record Output Period
     * @param actualRopTime
     *            - actual Record Output Period time used to create task sender delay for current Record Output Period
     * @param currentTime
     *            - current time used to create task sender delay for current Record Output Period
     * @return - returns TaskSender Delay for current rop
     */
    private long getTaskSenderInitialDelayForCurrentRop(final RopTimeInfo supportedRopTime, final RopTime actualRopTime, final long currentTime) {
        return actualRopTime.getCurrentROPPeriodEndTime().getTime() - currentTime + supportedRopTime.getCollectionDelayInMilliSecond();
    }

}
