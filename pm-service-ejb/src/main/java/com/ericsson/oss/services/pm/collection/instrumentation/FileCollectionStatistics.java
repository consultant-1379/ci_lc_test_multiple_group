/*
 * COPYRIGHT Ericsson 2017
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.collection.instrumentation;

import javax.enterprise.context.ApplicationScoped;

import com.codahale.metrics.MetricRegistry;
import com.ericsson.oss.itpf.sdk.instrument.annotation.InstrumentedBean;
import com.ericsson.oss.itpf.sdk.instrument.annotation.MonitoredAttribute;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.services.pm.collection.events.FileCollectionResult;

/**
 * The FileCollectionStatistics class
 */
@ApplicationScoped
@InstrumentedBean(displayName = "File Collection Statistics")
public class FileCollectionStatistics {
    public static final String NUMBER_OF_FILES_COLLECTED = "Number of files collected";
    public static final String NUMBER_OF_FILES_FAILED = "Number of files failed";
    public static final String NUMBER_OF_STORED_BYTES = "Number of stored bytes";
    public static final String NUMBER_OF_TRANSFERRED_BYTES = "Number of transferred bytes";

    private final MetricRegistry metricRegistry = new MetricRegistry();

    /**
     * Method takes a FileCollectionResult and updates counters.
     *
     * @param fileCollectionResult
     *            - The file collection result
     */
    public void updateData(final FileCollectionResult fileCollectionResult, final long numberOfSuccessfulHits, final long numberOfFailedHits) {
        final int ropInMinutes = (int) (fileCollectionResult.getRopPeriod() / 60);
        increaseRopBasedCounters(NUMBER_OF_FILES_COLLECTED, ropInMinutes, numberOfSuccessfulHits);
        increaseRopBasedCounters(NUMBER_OF_FILES_FAILED, ropInMinutes, numberOfFailedHits);
        increaseRopBasedCounters(NUMBER_OF_STORED_BYTES, ropInMinutes, fileCollectionResult.getAggregatedBytesStored());
        increaseRopBasedCounters(NUMBER_OF_TRANSFERRED_BYTES, ropInMinutes, fileCollectionResult.getAggregatedBytesTransferred());
    }

    /**
     * Method increases ROP based counters
     *
     * @param name
     *            - the counter name
     * @param ropInMinutes
     *            - the ROP period in minutes
     * @param amount
     *            - the amount to increase the counter by
     */
    protected void increaseRopBasedCounters(final String name, final int ropInMinutes, final long amount) {
        increaseCounter(ropBasedCounterName(name, ropInMinutes), amount);
        increaseCounter(name, amount);
    }

    /**
     * Method generates a ROP based counter name
     *
     * @param counterName
     *            - the counter name
     * @param ropInMinutes
     *            - the ROP period in minutes
     * @return - the ROP based counter name
     */
    protected String ropBasedCounterName(final String counterName, final int ropInMinutes) {
        return ropInMinutes + " minutes ROP - " + counterName;
    }

    /**
     * @param name
     *            - the counter name
     * @param amount
     *            - the value to increase the counter by
     */
    protected void increaseCounter(final String name, final long amount) {
        metricRegistry.counter(name).inc(amount);
    }

    /**
     * @param name
     *            - the counter name
     * @return - the counter valueCombinedRopFileCollectionCycleInstrumentation
     */
    public long getCounterValue(final String name) {
        return metricRegistry.counter(name).getCount();
    }

    /**
     * Gets the combined file collection counter value.
     *
     * @return - the combined file collection counter value
     */
    @MonitoredAttribute(
            displayName = "Combined ROPs: number of files collected",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getCombinedNumberOfFilesCollected() {
        return getCounterValue(NUMBER_OF_FILES_COLLECTED);
    }

    /**
     * Gets the combined failed file collection counter value.
     *
     * @return - the combined failed file collection counter value
     */
    @MonitoredAttribute(
            displayName = "Combined ROPs: number of files failed",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getCombinedNumberOfFilesFailed() {
        return getCounterValue(NUMBER_OF_FILES_FAILED);
    }

    /**
     * Gets the combined stored bytes counter value.
     *
     * @return - the combined stored bytes counter value
     */
    @MonitoredAttribute(
            displayName = "Combined ROPs: number of stored bytes",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getCombinedNumberOfStoredBytes() {
        return getCounterValue(NUMBER_OF_STORED_BYTES);
    }

    /**
     * Gets the combined transferred bytes counter value.
     *
     * @return - the combined transferred bytes counter value
     */
    @MonitoredAttribute(
            displayName = "Combined ROPs: number of transferred bytes",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getCombinedNumberOfTransferredBytes() {
        return getCounterValue(NUMBER_OF_TRANSFERRED_BYTES);
    }

    /**
     * Gets the file collection counter value for 1-minute ROP.
     *
     * @return - the file collection counter value for 1-minute ROP
     */
    @MonitoredAttribute(
            displayName = "1 minute ROP: number of files collected",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getOneMinuteRopNumberOfFilesCollected() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_FILES_COLLECTED, RopPeriod.ONE_MIN.getDurationInMinutes()));
    }

    /**
     * Gets the failed file collection counter value for 1-minute ROP.
     *
     * @return - the failed file collection counter value for 1-minute ROP
     */
    @MonitoredAttribute(
            displayName = "1 minute ROP: number of files failed",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getOneMinuteRopNumberOfFilesFailed() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_FILES_FAILED, RopPeriod.ONE_MIN.getDurationInMinutes()));
    }

    /**
     * Gets the stored bytes counter value for 1-minute ROP.
     *
     * @return - the stored bytes counter value for 1-minute ROP
     */
    @MonitoredAttribute(
            displayName = "1 minute ROP: number of stored bytes",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getOneMinuteRopNumberOfStoredBytes() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_STORED_BYTES, RopPeriod.ONE_MIN.getDurationInMinutes()));
    }

    /**
     * Gets the transferred bytes counter value for 1-minute ROP.
     *
     * @return - the transferred bytes counter value for 1-minute ROP
     */
    @MonitoredAttribute(
            displayName = "1 minute ROP: number of transferred bytes",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getOneMinuteRopNumberOfTransferredBytes() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_TRANSFERRED_BYTES, RopPeriod.ONE_MIN.getDurationInMinutes()));
    }

    /**
     * Gets the file collection counter value for 5-minutes ROP.
     *
     * @return - the file collection counter value for 5-minutes ROP
     */
    @MonitoredAttribute(
            displayName = "5 minutes ROP: number of files collected",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getFiveMinutesRopNumberOfFilesCollected() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_FILES_COLLECTED, RopPeriod.FIVE_MIN.getDurationInMinutes()));
    }

    /**
     * Gets the failed file collection counter value for 5-minutes ROP.
     *
     * @return - the failed file collection counter value for 5-minutes ROP
     */
    @MonitoredAttribute(
            displayName = "5 minutes ROP: number of files failed",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getFiveMinutesRopNumberOfFilesFailed() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_FILES_FAILED, RopPeriod.FIVE_MIN.getDurationInMinutes()));
    }

    /**
     * Gets the stored bytes counter value for 5-minutes ROP.
     *
     * @return - the stored bytes counter value for 5-minutes ROP
     */
    @MonitoredAttribute(
            displayName = "5 minutes ROP: number of stored bytes",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getFiveMinutesRopNumberOfStoredBytes() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_STORED_BYTES, RopPeriod.FIVE_MIN.getDurationInMinutes()));
    }

    /**
     * Gets the transferred bytes counter value for 5-minutes ROP.
     *
     * @return - the transferred bytes counter value for 5-minutes ROP
     */
    @MonitoredAttribute(
            displayName = "5 minutes ROP: number of transferred bytes",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getFiveMinutesRopNumberOfTransferredBytes() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_TRANSFERRED_BYTES, RopPeriod.FIVE_MIN.getDurationInMinutes()));
    }

    /**
     * Gets the file collection counter value for 15-minute ROP.
     *
     * @return - the file collection counter value for 15-minute ROP
     */
    @MonitoredAttribute(
            displayName = "15 minutes ROP: number of files collected",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getFifteenMinutesRopNumberOfFilesCollected() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_FILES_COLLECTED, RopPeriod.FIFTEEN_MIN.getDurationInMinutes()));
    }

    /**
     * Gets the failed file collection counter value for 15-minute ROP.
     *
     * @return - the failed file collection counter value for 15-minute ROP
     */
    @MonitoredAttribute(
            displayName = "15 minutes ROP: number of files failed",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getFifteenMinutesRopNumberOfFilesFailed() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_FILES_FAILED, RopPeriod.FIFTEEN_MIN.getDurationInMinutes()));
    }

    /**
     * Gets the stored bytes counter value for 15-minute ROP.
     *
     * @return - the stored bytes counter value for 15-minute ROP
     */
    @MonitoredAttribute(
            displayName = "15 minutes ROP: number of stored bytes",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getFifteenMinutesRopNumberOfStoredBytes() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_STORED_BYTES, RopPeriod.FIFTEEN_MIN.getDurationInMinutes()));
    }

    /**
     * Gets the transferred bytes counter value for 15-minute ROP.
     *
     * @return - the transferred bytes counter value for 15-minute ROP
     */
    @MonitoredAttribute(
            displayName = "15 minutes ROP: number of transferred bytes",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getFifteenMinutesRopNumberOfTransferredBytes() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_TRANSFERRED_BYTES, RopPeriod.FIFTEEN_MIN.getDurationInMinutes()));
    }

    /**
     * Gets the file collection counter value for 30-minute ROP.
     *
     * @return - the file collection counter value for 30-minute ROP
     */
    @MonitoredAttribute(
            displayName = "30 minutes ROP: number of files collected",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getThirtyMinutesRopNumberOfFilesCollected() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_FILES_COLLECTED, RopPeriod.THIRTY_MIN.getDurationInMinutes()));
    }

    /**
     * Gets the failed file collection counter value for 30-minute ROP.
     *
     * @return - the failed file collection counter value for 30-minute ROP
     */
    @MonitoredAttribute(
            displayName = "30 minutes ROP: number of files failed",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getThirtyMinutesRopNumberOfFilesFailed() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_FILES_FAILED, RopPeriod.THIRTY_MIN.getDurationInMinutes()));
    }

    /**
     * Gets the stored bytes counter value for 30-minute ROP.
     *
     * @return - the stored bytes counter value for 30-minute ROP
     */
    @MonitoredAttribute(
            displayName = "30 minutes ROP: number of stored bytes",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getThirtyMinutesRopNumberOfStoredBytes() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_STORED_BYTES, RopPeriod.THIRTY_MIN.getDurationInMinutes()));
    }

    /**
     * Gets the transferred bytes counter value for 30-minute ROP.
     *
     * @return - the transferred bytes counter value for 30-minute ROP
     */
    @MonitoredAttribute(
            displayName = "30 minutes ROP: number of transferred bytes",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getThirtyMinutesRopNumberOfTransferredBytes() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_TRANSFERRED_BYTES, RopPeriod.THIRTY_MIN.getDurationInMinutes()));
    }

    /**
     * Gets the file collection counter value for 1-hour ROP.
     *
     * @return - the file collection counter value for 1-hour ROP
     */
    @MonitoredAttribute(
            displayName = "1 hour ROP: number of files collected",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getOneHourRopNumberOfFilesCollected() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_FILES_COLLECTED, RopPeriod.ONE_HOUR.getDurationInMinutes()));
    }

    /**
     * Gets the failed file collection counter value for 1 hour ROP.
     *
     * @return - the failed file collection counter value for 1 hour ROP
     */
    @MonitoredAttribute(
            displayName = "1 hour ROP: number of files failed",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getOneHourRopNumberOfFilesFailed() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_FILES_FAILED, RopPeriod.ONE_HOUR.getDurationInMinutes()));
    }

    /**
     * Gets the stored bytes counter value for 1 hour ROP.
     *
     * @return - the stored bytes counter value for 1 hour ROP
     */
    @MonitoredAttribute(
            displayName = "1 hour ROP: number of stored bytes",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getOneHourRopNumberOfStoredBytes() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_STORED_BYTES, RopPeriod.ONE_HOUR.getDurationInMinutes()));
    }

    /**
     * Gets the transferred bytes counter value for 1 hour ROP.
     *
     * @return - the transferred bytes counter value for 1 hour ROP
     */
    @MonitoredAttribute(
            displayName = "1 hour ROP: number of transferred bytes",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getOneHourRopNumberOfTransferredBytes() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_TRANSFERRED_BYTES, RopPeriod.ONE_HOUR.getDurationInMinutes()));
    }

    /**
     * Gets the file collection counter value for 12-hours ROP.
     *
     * @return - the file collection counter value for 12-hours ROP
     */
    @MonitoredAttribute(
            displayName = "12 hours ROP: number of files collected",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getTwelveHourRopNumberOfFilesCollected() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_FILES_COLLECTED, RopPeriod.TWELVE_HOUR.getDurationInMinutes()));
    }

    /**
     * Gets the failed file collection counter value for 12 hour ROP.
     *
     * @return - the failed file collection counter value for 12 hour ROP
     */
    @MonitoredAttribute(
            displayName = "12 hours ROP: number of files failed",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getTwelveHourRopNumberOfFilesFailed() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_FILES_FAILED, RopPeriod.TWELVE_HOUR.getDurationInMinutes()));
    }

    /**
     * Gets the stored bytes counter value for 12 hours ROP.
     *
     * @return - the stored bytes counter value for 12 hour ROP
     */
    @MonitoredAttribute(
            displayName = "12 hours ROP: number of stored bytes",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getTwelveHourRopNumberOfStoredBytes() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_STORED_BYTES, RopPeriod.TWELVE_HOUR.getDurationInMinutes()));
    }

    /**
     * Gets the transferred bytes counter value for 12 hour ROP.
     *
     * @return - the transferred bytes counter value for 12 hour ROP
     */
    @MonitoredAttribute(
            displayName = "12 hours ROP: number of transferred bytes",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getTwelveHourRopNumberOfTransferredBytes() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_TRANSFERRED_BYTES, RopPeriod.TWELVE_HOUR.getDurationInMinutes()));
    }

    /**
     * Gets the file collection counter value for 24-hours ROP.
     *
     * @return - the file collection counter value for 24-hours ROP
     */
    @MonitoredAttribute(
            displayName = "24 hours ROP: number of files collected",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getTwentyFourRopNumberOfFilesCollected() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_FILES_COLLECTED, RopPeriod.ONE_DAY.getDurationInMinutes()));
    }

    /**
     * Gets the failed file collection counter value for 24 hour ROP.
     *
     * @return - the failed file collection counter value for 24 hour ROP
     */
    @MonitoredAttribute(
            displayName = "24 hours ROP: number of files failed",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getTwentyFourRopNumberOfFilesFailed() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_FILES_FAILED, RopPeriod.ONE_DAY.getDurationInMinutes()));
    }

    /**
     * Gets the stored bytes counter value for 24 hours ROP.
     *
     * @return - the stored bytes counter value for 24 hour ROP
     */
    @MonitoredAttribute(
            displayName = "24 hours ROP: number of stored bytes",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getTwentyFourRopNumberOfStoredBytes() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_STORED_BYTES, RopPeriod.ONE_DAY.getDurationInMinutes()));
    }

    /**
     * Gets the transferred bytes counter value for 24 hour ROP.
     *
     * @return - the transferred bytes counter value for 24 hour ROP
     */
    @MonitoredAttribute(
            displayName = "24 hours ROP: number of transferred bytes",
            visibility = MonitoredAttribute.Visibility.EXTERNAL,
            units = MonitoredAttribute.Units.NONE,
            category = MonitoredAttribute.Category.PERFORMANCE,
            interval = MonitoredAttribute.Interval.ONE_MIN,
            collectionType = MonitoredAttribute.CollectionType.TRENDSUP)
    public long getTwentyFourRopNumberOfTransferredBytes() {
        return getCounterValue(ropBasedCounterName(NUMBER_OF_TRANSFERRED_BYTES, RopPeriod.ONE_DAY.getDurationInMinutes()));
    }
}
