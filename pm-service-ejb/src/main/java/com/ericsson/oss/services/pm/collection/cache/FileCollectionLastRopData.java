/*******************************************************************************
 * COPYRIGHT Ericsson 2017
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.collection.cache;

import static com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant.LAST_FILE_COLLECTION_TASKS_CREATED_FOR_ROP;
import static com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant.LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP;
import static com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant.LAST_UETRACE_FILE_COLLECTION_TASKS_CREATED_FOR_ROP;
import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants.FILE_COLLECTION_LAST_ROP_DATA;

import java.util.HashMap;
import java.util.Map;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.initiation.util.RopTime;

/**
 * Contains helper methods to check whether execution should proceed
 */
@Singleton
@Lock(LockType.READ)
public class FileCollectionLastRopData {

    private static final String CACHE_ENTRY_NOT_UPDATED = "Cannot update cache entry.";
    private static final String CACHE_ENTRY_NOT_UPDATED_FOR_PARENTEHESIS = "Cannot update cache entry for {} with value {}. {}";

    private final Map<String, Long> counterRopRecords = new HashMap<>();

    @Inject
    @NamedCache(FILE_COLLECTION_LAST_ROP_DATA)
    private Cache<String, Object> cache;

    @Inject
    private Logger logger;

    @Inject
    private SupportedRopTimes supportedRopTimes;

    @EJB
    private FileCollectionLastRopData self;

    /**
     * Returns true if current time is greater that the the middle of current rop AND if the last recorded ROP is not currentROP
     *
     * @param currentRop
     *            - The ROP object created with the current time to compared against.
     * @return true or false
     */
    public boolean shouldCreateTasksForCurrentRop(final RopTime currentRop) {
        final long lastRecordedRopStartTime = getRopStartTimeForRecord(
                LAST_FILE_COLLECTION_TASKS_CREATED_FOR_ROP + currentRop.getRopPeriodInSeconds());
        final RopTime recordedRop = new RopTime(lastRecordedRopStartTime, currentRop.getRopPeriodInSeconds());
        return recordedRopIsNotCurrentRop(recordedRop, currentRop) && currentTimeIsPastMiddleOfCurrentRop(currentRop.getTime(), currentRop);
    }

    /**
     * Returns true if current time is greater that the the middle of current rop AND if the last recorded ROP is not currentROP
     *
     * @param currentRop
     *            - The ROP object created with the current time to compared against.
     * @return true or false
     */
    public boolean shouldCreateUetraceTasksForCurrentRop(final RopTime currentRop) {
        final long lastRecordedRopStartTime = getRopStartTimeForRecord(
                LAST_UETRACE_FILE_COLLECTION_TASKS_CREATED_FOR_ROP + currentRop.getRopPeriodInSeconds());
        final RopTime recordedRop = new RopTime(lastRecordedRopStartTime, currentRop.getRopPeriodInSeconds());
        return recordedRopIsNotCurrentRop(recordedRop, currentRop) && currentTimeIsPastMiddleOfCurrentRop(currentRop.getTime(), currentRop);
    }

    private long getRopStartTimeForRecord(final String recordName) {
        final Long ropStartTime = counterRopRecords.get(recordName);
        if (ropStartTime != null && ropStartTime > 0L) {
            return ropStartTime;
        }

        try {
            final Long ropStartTimeFromCache = (Long) getFromCache(recordName);
            if (ropStartTimeFromCache != null) {
                counterRopRecords.put(recordName, ropStartTimeFromCache);
                return ropStartTimeFromCache;
            }
        } catch (final IllegalStateException | CacheException e) {
            logger.error("Cannot get {} from PMICGeneralCache! {}", recordName, e.getMessage());
            logger.info("Cannot get entry from PMICGeneralCache. ", e);
        }

        return 0L;
    }

    /**
     * Checks whether the ROP for which last time we created tasks is current ROP. This prevents sending/creating duplicate tasks for current rop.
     *
     * @param recordedRop
     *            - the recorded ROP, which is representing the ROP for which tasks have been recorded for
     * @param currentRop
     *            - the current ROP
     * @return true or false based on the condition described above
     */
    private boolean recordedRopIsNotCurrentRop(final RopTime recordedRop, final RopTime currentRop) {
        return currentRop.getCurrentRopStartTimeInMilliSecs() != recordedRop.getCurrentRopStartTimeInMilliSecs();
    }

    /**
     * Verifies that the current time is in the middle of the ROP or after the middle of the current ROP
     *
     * @param currentTime
     *            - the time file creator started executing
     * @param currentRop
     *            - the current rop based on "current" time
     * @return - true or false
     */
    private boolean currentTimeIsPastMiddleOfCurrentRop(final long currentTime, final RopTime currentRop) {
        return currentTime >= currentRop.getCurrentRopStartTimeInMilliSecs() + currentRop.getRopPeriod() / 2;
    }

    /**
     * Returns true if last time the recorded ROP's start time is less than the previous ROP's start time
     *
     * @param currentRop
     *            - The ROP object created with the current time to compared against.
     * @return - true or false
     */
    public boolean shouldCreateTasksForPreviousRop(final RopTime currentRop) {
        final long lastRecordedRopStartTime = getRopStartTimeForRecord(
                LAST_FILE_COLLECTION_TASKS_CREATED_FOR_ROP + currentRop.getRopPeriodInSeconds());
        final RopTime recordedRop = new RopTime(lastRecordedRopStartTime, currentRop.getRopPeriodInSeconds());
        return recordedRop.getCurrentRopStartTimeInMilliSecs() < currentRop.getLastROP(1).getCurrentRopStartTimeInMilliSecs();
    }

    /**
     * Returns true if last time the recorded ROP's start time is less than the previous ROP's start time
     *
     * @param currentRop
     *            - The ROP object created with the current time to compared against.
     * @return - true or false
     */
    public boolean shouldCreateUetraceTasksForPreviousRop(final RopTime currentRop) {
        final long lastRecordedRopStartTime = getRopStartTimeForRecord(
                LAST_UETRACE_FILE_COLLECTION_TASKS_CREATED_FOR_ROP + currentRop.getRopPeriodInSeconds());
        final RopTime recordedRop = new RopTime(lastRecordedRopStartTime, currentRop.getRopPeriodInSeconds());
        return recordedRop.getCurrentRopStartTimeInMilliSecs() < currentRop.getLastROP(1).getCurrentRopStartTimeInMilliSecs();
    }

    /**
     * Records the currentRopTime's start time in milliseconds for the record.
     *
     * @param currentRop
     *            - The ROP object created with the current time to compared against.
     */
    public void recordRopStartTimeForTaskCreation(final RopTime currentRop) {
        final String record = LAST_FILE_COLLECTION_TASKS_CREATED_FOR_ROP + currentRop.getRopPeriodInSeconds();
        final Long ropStartTime = currentRop.getCurrentRopStartTimeInMilliSecs();
        counterRopRecords.put(record, ropStartTime);
        try {
            self.putToCacheAsync(record, ropStartTime);
        } catch (final IllegalStateException | CacheException e) {
            logger.error(CACHE_ENTRY_NOT_UPDATED_FOR_PARENTEHESIS, record, ropStartTime, e.getMessage());
            logger.info(CACHE_ENTRY_NOT_UPDATED, e);
        }
    }

    /**
     * Records the currentRopTime's start time in milliseconds for the record.
     *
     * @param currentRop
     *            - The ROP object created with the current time to compared against.
     */
    public void recordRopStartTimeForUetraceTaskCreation(final RopTime currentRop) {
        final String record = LAST_UETRACE_FILE_COLLECTION_TASKS_CREATED_FOR_ROP + currentRop.getRopPeriodInSeconds();
        final Long ropStartTime = currentRop.getCurrentRopStartTimeInMilliSecs();
        counterRopRecords.put(record, ropStartTime);
        try {
            self.putToCacheAsync(record, ropStartTime);
        } catch (final IllegalStateException | CacheException e) {
            logger.error(CACHE_ENTRY_NOT_UPDATED_FOR_PARENTEHESIS, record, ropStartTime, e.getMessage());
            logger.info(CACHE_ENTRY_NOT_UPDATED, e);
        }
    }

    /**
     * Records the current ROP start time in millis for this RopTime.
     *
     * @param rop
     *            - RopTime for which file collection task sending is being recorded for.
     */
    public void recordRopStartTimeForTaskSending(final RopTime rop) {
        final String record = LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP + rop.getRopPeriodInSeconds();
        final Long ropStartTime = rop.getCurrentRopStartTimeInMilliSecs();
        counterRopRecords.put(record, ropStartTime);
        try {
            self.putToCacheAsync(record, ropStartTime);
        } catch (final IllegalStateException | CacheException e) {
            logger.error(CACHE_ENTRY_NOT_UPDATED_FOR_PARENTEHESIS, record, ropStartTime, e.getMessage());
            logger.info(CACHE_ENTRY_NOT_UPDATED, e);
        }
    }

    /**
     * For 15 min ROP: current time >= current ROP time + ROP time delay interval (5 minutes)) AND (LAST_ROP_TASKS_WERE_SENT_FOR + 1 ROP != current
     * ROP For 1 min ROP: LAST_ROP_TASKS_WERE_SENT_FOR + 1 ROP != current ROP
     *
     * @param currentRop
     *            - currentRop
     * @return true or false
     */
    public boolean shouldSendTasks(final RopTime currentRop) {
        final long lastRecordedRopStartTime = getRopStartTimeForRecord(LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP + currentRop.getRopPeriodInSeconds());
        final RopTime recordedRop = new RopTime(lastRecordedRopStartTime, currentRop.getRopPeriodInSeconds());
        return recordedRopIsNotPreviousRop(recordedRop, currentRop)
                && isToExecuteNow(currentRop.getRopPeriodInSeconds(), currentRop, currentRop.getTime());
    }

    /**
     * Since we are recording the ROP for which tasks are sent for, we need to check whether the recorded ROP + 1 ROP is not equal current ROP. Or
     * simpler, if previous ROP is not the recorded ROP.
     *
     * @param recordedRop
     *            - the recorded ROP, which is representing the ROP for which tasks have been sent for (basically previous ROP since in current rop we
     *            are sending tasks that have been prepared for us in the previous ROP)
     * @param currentRop
     *            - the current ROP
     * @return true or false based on the condition described above
     */
    private boolean recordedRopIsNotPreviousRop(final RopTime recordedRop, final RopTime currentRop) {
        final long previousRopStartTime = currentRop.getLastROP(1).getCurrentRopStartTimeInMilliSecs();
        return previousRopStartTime != recordedRop.getCurrentRopStartTimeInMilliSecs();
    }

    /**
     * Verifies that the current time is at or after the current ROP start time + collection delay. Since collection delay for 1 min ROP is 1 min, it
     * always results in true.
     *
     * @param ropTimePeriodInSeconds
     *            - rop time in seconds
     * @param currentRop
     *            - the current ROP
     * @param currentTime
     *            - the "current" time when sender started executing
     * @return true if conditions are matched
     */
    private boolean isToExecuteNow(final int ropTimePeriodInSeconds, final RopTime currentRop, final long currentTime) {
        if (RopPeriod.ONE_MIN.getDurationInSeconds() == ropTimePeriodInSeconds) {
            return true;
        }
        return currentTime >= currentRop.getCurrentRopStartTimeInMilliSecs()
                + supportedRopTimes.getRopTime(ropTimePeriodInSeconds).getCollectionDelayInMilliSecond();
    }

    /**
     * Re-sync local records from cache. When mastership changes back to master, it is possible that the existing local records are existent and not 0
     * but still invalid. The records should therefore be synched from cache or reset to their defaults if cache does not contain such entries or any
     * exception is thrown in the sync process.
     */
    public void resyncLocalRecords() {
        for (final Map.Entry<String, Long> entry : counterRopRecords.entrySet()) {
            Long value = null;
            try {
                value = (Long) getFromCache(entry.getKey());
            } catch (final IllegalStateException | CacheException e) {
                logger.error("Exception thrown while synchronizing local file collection record [{}] with cache entries. {}", entry.getKey(),
                        e.getMessage());
                logger.info("Cannot synchronize records.", e);
            }
            if (value != null) {
                entry.setValue(value);
            } else {
                entry.setValue(0L);
            }
        }
    }

    private Object getFromCache(final String key) {
        final Object object = cache.get(key);

        logger.trace("Extracted {} with value {} from PMICFileCollectionRopRecordingCache", key, object);
        return cache.get(key);
    }

    /**
     * Add entry to the cache by overwriting the existing value if it exists. This is done asynchronously.
     *
     * @param key
     *            - non null String
     * @param value
     *            - non null Object
     */
    @Asynchronous
    @Lock(LockType.WRITE)
    public void putToCacheAsync(final String key, final Long value) {
        cache.put(key, value);
        logger.trace("Added {} with value {} to PMICFileCollectionRopRecordingCache", key, value);
    }
}
