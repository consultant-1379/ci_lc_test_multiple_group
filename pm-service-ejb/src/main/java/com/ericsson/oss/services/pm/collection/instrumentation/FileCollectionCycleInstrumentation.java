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

package com.ericsson.oss.services.pm.collection.instrumentation;

import static com.ericsson.oss.pmic.api.handler.PmMediationHandlerConstants.RecorderMessageFormat.PMIC_INPUT_EVENT_RECEIVED;

import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.recording.EventLevel;
import com.ericsson.oss.itpf.sdk.recording.SystemRecorder;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.initiation.constants.PmicLogCommands;
import com.ericsson.oss.services.pm.initiation.util.RopTime;
import com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants;
import com.ericsson.oss.services.pm.time.TimeGenerator;

/**
 * VO class to hold the instrumentation details for different rop intervals. Each rop instrumentation data will be written to the system recorder. The
 * instrumentation data will be updated for each file collected. When the first notification comes for the current rop the old rop data will be
 * written to the system recorder and will be swapped
 */
public class FileCollectionCycleInstrumentation {

    private final ROPCollectionStats lastROPCollectionStats;
    private final ROPCollectionStats currentROPCollectionStats;

    @Inject
    private TimeGenerator timeGenerator;

    @Inject
    private SupportedRopTimes supportedRopTimes;

    private long ropPeriodInSeconds;
    private boolean isCurrentRopCollectionStartTimeUpdated;
    /* id that helps DDP to distinguish data between different file collection cycles instrumentation */
    private String cycleId;

    @Inject
    private SystemRecorder systemRecorder;

    /**
     * Default FileCollectionCycleInstrumentation constructor, ropPeriodInSeconds defaults to 900.
     */
    public FileCollectionCycleInstrumentation() {
        this(900);
    }

    /**
     * FileCollectionCycleInstrumentation constructor, takes ropPeriodInSeconds
     *
     * @param ropPeriodInSeconds
     *            - The ROP period in seconds
     */
    public FileCollectionCycleInstrumentation(final long ropPeriodInSeconds) {
        this.ropPeriodInSeconds = ropPeriodInSeconds;
        lastROPCollectionStats = new ROPCollectionStats();
        currentROPCollectionStats = new ROPCollectionStats();
    }

    /**
     * FileCollectionCycleInstrumentation constructor, takes ropPeriodInSeconds
     *
     * @param cycleId
     *            - The identifier
     * @param ropPeriodInSeconds
     *            - The ROP period in seconds
     */
    public FileCollectionCycleInstrumentation(final String cycleId, final long ropPeriodInSeconds) {
        this.cycleId = cycleId;
        this.ropPeriodInSeconds = ropPeriodInSeconds;
        lastROPCollectionStats = new ROPCollectionStats();
        currentROPCollectionStats = new ROPCollectionStats();
    }

    /**
     * Setting Instrumentation data for successful and failed file collection. Updating start and end times for current ROP
     *
     * @param numberOfSuccessfulHits
     *            - number of successful files collected
     * @param numberOfFailedHits
     *            - number of files that failed to be collected
     * @param aggregatedBytesStored
     *            - number of bytes stored
     * @param aggregatedBytesTransferred
     *            - number of bytes transferred
     */
    public synchronized void updateDataForSuccessAndFailure(final long numberOfSuccessfulHits, final long numberOfFailedHits,
                                                            final long aggregatedBytesStored, final long aggregatedBytesTransferred) {
        updateDataForSuccess(numberOfSuccessfulHits, aggregatedBytesStored, aggregatedBytesTransferred);
        updateDataForFailure(numberOfFailedHits);
        updateStartEndTimeCurrentROP(numberOfSuccessfulHits, numberOfFailedHits);
    }

    private synchronized void updateDataForSuccess(final long numberOfSuccessfulHits, final long aggregatedBytesStored,
                                                   final long aggregatedBytesTransferred) {
        if (numberOfSuccessfulHits > 0) {
            currentROPCollectionStats.incrementNumberOfFilesCollectedBy(numberOfSuccessfulHits);
            currentROPCollectionStats.incrementNumberOfBytesStored(aggregatedBytesStored);
            currentROPCollectionStats.incrementNumberOfBytesTransferred(aggregatedBytesTransferred);
        }
    }

    private synchronized void updateDataForFailure(final long numberOfFailedHits) {
        if (numberOfFailedHits > 0) {
            currentROPCollectionStats.incrementNumberOfFilesFailedBy(numberOfFailedHits);
        }
    }

    private synchronized void updateStartEndTimeCurrentROP(final long numberOfSuccessfulHits, final long numberOfFailedHits) {
        if (!isCurrentRopCollectionStartTimeUpdated) {
            currentROPCollectionStats.setCollectionStartTime(timeGenerator.currentTimeMillis());
            isCurrentRopCollectionStartTimeUpdated = true;
        }
        if (numberOfSuccessfulHits > 0 || numberOfFailedHits > 0) {
            currentROPCollectionStats.setCollectionEndTime(timeGenerator.currentTimeMillis());
        }
    }

    /**
     * Set the Counters for DDP to parse
     */
    public synchronized void resetCurrentROP() {
        // log previous rop stats data using system recorder in order to make it available for DDP
        logStatistics(currentROPCollectionStats, false);
        // Swap the data before resetting
        lastROPCollectionStats.setNumberOfFilesCollected(currentROPCollectionStats.getNumberOfFilesCollected());
        lastROPCollectionStats.setNumberOfFilesFailed(currentROPCollectionStats.getNumberOfFilesFailed());
        lastROPCollectionStats.setNumberOfBytesStored(currentROPCollectionStats.getNumberOfBytesStored());
        lastROPCollectionStats.setNumberOfBytesTransferred(currentROPCollectionStats.getNumberOfBytesTransferred());
        lastROPCollectionStats.setCollectionStartTime(currentROPCollectionStats.getCollectionStartTime());
        lastROPCollectionStats.setCollectionEndTime(currentROPCollectionStats.getCollectionEndTime());
        // Reset current counters
        currentROPCollectionStats.resetCounters();
        isCurrentRopCollectionStartTimeUpdated = false;
    }

    /**
     * Setting the Counters recorded for the ROP
     */
    public void writeOutCurrentBufferedDate() {
        systemRecorder.recordEvent(PmicLogCommands.PMIC_PRE_DESTROY.getDescription(), EventLevel.COARSE, this.getClass().getSimpleName(),
            "PMIC INSTRUMENTATION", "Writing down all buffered intrumentation because teardown was detected");
        logStatistics(currentROPCollectionStats, true);
    }

    private void logStatistics(final ROPCollectionStats ropStats, final boolean isWriteout) {
        final StringBuilder stats = new StringBuilder("id=").append(cycleId);
        if (isWriteout) {
            stats.append(",ropStartTimeIdentifier=").append(getRopStartTimeIdentifierForWriteout());
        } else {
            stats.append(",ropStartTimeIdentifier=").append(getRopStartTimeIdentifier());
        }
        stats.append(",ropPeriodInMinutes=").append(ropPeriodInSeconds / 60);
        stats.append(",numberOfFilesCollected=").append(ropStats.getNumberOfFilesCollected());
        stats.append(",numberOfFilesFailed=").append(ropStats.getNumberOfFilesFailed());
        stats.append(",numberOfBytesStored=").append(ropStats.getNumberOfBytesStored());
        stats.append(",numberOfBytesTransferred=").append(ropStats.getNumberOfBytesTransferred());
        stats.append(",ropStartTime=").append(ropStats.getCollectionStartTime());
        stats.append(",ropEndTime=").append(ropStats.getCollectionEndTime());

        systemRecorder.recordEvent(PmicLogCommands.PMIC_FILE_COLLECTION_STATISTICS.getDescription(), EventLevel.DETAILED, PMIC_INPUT_EVENT_RECEIVED,
            "COMPONENT EVENT", stats.toString());

    }

    /**
     * for 5 mins rop and above the start time identifier is 2 rops in the past: e.g. for 15 mins if we send tasks at 12:20 (1200-1215), we collect files until 12:35 at which time we write out the start time of the rop for
     * which we collected files for (1200 or 2 rops ago), for 24 hrs rop: if we send tasks at 04/12/2017 00:05 (03/12/2017 00:00- 04/12/2017 00:00), we collect files until 05/12/2017 00:05 at which
     * time we write out the start time of the rop for which we collected files for (03/12/2017 00:00 or 2 rops ago)
     * for 1 min rop the start time identifier is 3 rops in the past: if we send tasks at 12:20 (1218-1219), we collect files until 12:21 at which time we write out the start time of the rop for
     * which we collected files for (1218 or 3 rops ago)

     *
     * @return rop start time
     */
    private long getRopStartTimeIdentifier() {
        final RopTime currentRopTime = new RopTime(timeGenerator.currentTimeMillis(), ropPeriodInSeconds);
        if (is1Min()) {
            return currentRopTime.getLastROP(3).getCurrentRopStartTimeInMilliSecs();
        } else {
            return currentRopTime.getLastROP(2).getCurrentRopStartTimeInMilliSecs();
        }
    }

    /**
     * for 5 min rop and above the start time identifier for writeout is 1 or 2 rops in the past depending on the current time: e.g. if we send tasks at 12:20 (1200-1215) and we start going offline at 12:23, we write out the start time of the rop for which
     * we are collecting files (1200 or 1 rops ago), if we send tasks at 12:20 (1200-1215) and we start going offline at 12:34, we write out the start time of the rop for which
     * we are collecting files (1200 or 2 rops ago)
     * for 1 min rop the start time identifier for writeout is 2 rops in the past: if we send tasks at 12:20:00 (1218-1219) and we start going offline at 12:20:16, we write out the start time of the rop for
     * which we are collecting files (1218 or 2 rops ago)
     *
     * @return rop start time
     */
    private long getRopStartTimeIdentifierForWriteout() {
        final RopTime currentRopTime = new RopTime(timeGenerator.currentTimeMillis(), ropPeriodInSeconds);
        if (is1Min()) {
            return currentRopTime.getLastROP(2).getCurrentRopStartTimeInMilliSecs();
        } else {
            if (isBeforeCollectionDelayOfCurrentRop(currentRopTime, RopPeriod.fromSeconds((int) ropPeriodInSeconds))) {
                return currentRopTime.getLastROP(2).getCurrentRopStartTimeInMilliSecs();
            } else {
                return currentRopTime.getLastROP(1).getCurrentRopStartTimeInMilliSecs();
            }
        }
    }

    private boolean isBeforeCollectionDelayOfCurrentRop(final RopTime currentRopTime, final RopPeriod ropPeriod) {
        return timeGenerator.currentTimeMillis() < getCollectionDelayTimeForCurrentRop(currentRopTime, ropPeriod);
    }

    private long getCollectionDelayTimeForCurrentRop(final RopTime currentRopTime, final RopPeriod ropPeriod) {
        return currentRopTime.getCurrentRopStartTimeInMilliSecs() +
            supportedRopTimes.getRopTime(ropPeriod.getDurationInSeconds()).getCollectionDelayInMilliSecond();
    }

    private boolean is1Min() {
        return ropPeriodInSeconds == RopPeriod.ONE_MIN.getDurationInSeconds();
    }

    /**
     * @return the Record Output Period collection time
     */
    public synchronized long getRopCollectionTime() {
        return (lastROPCollectionStats.getCollectionEndTime() - lastROPCollectionStats.getCollectionStartTime())
            / TimeConstants.ONE_SECOND_IN_MILLISECONDS;
    }

    /**
     * @return - the number of files collected last Record Output Period
     */
    public synchronized long getNumberOfFilesCollectedLastROP() {
        return lastROPCollectionStats.getNumberOfFilesCollected();
    }

    /**
     * @return - the number of files failed last Record Output Period
     */
    public synchronized long getNumberOfFilesFailedLastROP() {
        return lastROPCollectionStats.getNumberOfFilesFailed();
    }

    /**
     * @return - the number of bytes stored last Record Output Period
     */
    public synchronized long getNumberOfBytesStoredLastROP() {
        return lastROPCollectionStats.getNumberOfBytesStored();
    }

    /**
     * @return - the number of bytes transfered last Record Output Period
     */
    public synchronized long getNumberOfBytesTransferedLastROP() {
        return lastROPCollectionStats.getNumberOfBytesTransferred();
    }

    /**
     * @return the start time for the last Record Output Period
     */

    public synchronized long getRopStartTimeLastROP() {
        return lastROPCollectionStats.getCollectionStartTime();
    }

    /**
     * @return the end time for the last Record Output Period
     */

    public synchronized long getRopEndTimeLastROP() {
        return lastROPCollectionStats.getCollectionEndTime();
    }

    /**
     * @return the number of bytes stored for current Record Output Period
     */
    public synchronized long getNumberOfBytesStoredCurrentROP() {
        return currentROPCollectionStats.getNumberOfBytesStored();
    }

    /**
     * @return the number of bytes transfered for current Record Output Period
     */
    public synchronized long getNumberOfBytesTransferredCurrentROP() {
        return currentROPCollectionStats.getNumberOfBytesTransferred();
    }

    /**
     * @return the start time for the current Record Output Period
     */
    public synchronized long getRopStartTimeCurrentROP() {
        return currentROPCollectionStats.getCollectionStartTime();
    }

    /**
     * @return the end time for the current Record Output Period
     */
    public synchronized long getRopEndTimeCurrentROP() {
        return currentROPCollectionStats.getCollectionEndTime();
    }

    /**
     * sets the rop period
     *
     * @param ropPeriodInSeconds
     *            rop period in seconds
     */
    public void setRopPeriodInSeconds(final long ropPeriodInSeconds) {
        this.ropPeriodInSeconds = ropPeriodInSeconds;
    }

    /**
     * sets the cycle id
     *
     * @param cycleId
     *            cycled id using which ddp/ddc will distinguish the rop periods
     */
    public void setCycleId(final String cycleId) {
        this.cycleId = cycleId;
    }

    /**
     * @return the number of files collected for current Record Output Period
     */
    public synchronized long getNumberOfFilesCollectedCurrentROP() {
        return currentROPCollectionStats.getNumberOfFilesCollected();
    }

    /**
     * @return the number Of files failed for current Record Output Period
     */
    public synchronized long getNumberOfFilesFailedCurrentROP() {
        return currentROPCollectionStats.getNumberOfFilesFailed();
    }

}
