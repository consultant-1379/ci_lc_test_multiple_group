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

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.instrument.annotation.InstrumentedBean;
import com.ericsson.oss.itpf.sdk.instrument.annotation.MonitoredAttribute;
import com.ericsson.oss.itpf.sdk.instrument.annotation.MonitoredAttribute.Category;
import com.ericsson.oss.itpf.sdk.instrument.annotation.MonitoredAttribute.CollectionType;
import com.ericsson.oss.itpf.sdk.instrument.annotation.MonitoredAttribute.Interval;
import com.ericsson.oss.itpf.sdk.instrument.annotation.MonitoredAttribute.Units;
import com.ericsson.oss.itpf.sdk.instrument.annotation.MonitoredAttribute.Visibility;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;

/**
 * FileCollectionInstrumentation class contains information of ROP collection statistics.
 * E.g. while pm-service is running this class would hold the collection statistics for a complete ROP.
 * For ROP 10:00-10:15 collection processing time is from 10:20-10:35 so good to show collection statistics for period of 10:20-10:35. This helps to
 * understand the system performance.
 * Hyperic external system monitoring tool will use this data to show collection statistics on the interval of 15 minutes,.
 *
 * @author ekamkal
 */
@InstrumentedBean(displayName = "PM File Collection Statistics", description = "PM file collection statistics")
@ApplicationScoped
// FifteenMinutesRopFileCollectionCycleInstrumentation, the class name hasn't been changed in order to have backward compatibility with DDC/DDP
public class FileCollectionInstrumentation {

    @Inject
    private CombinedRopFileCollectionCycleInstrumentation combinedRopFileCollectionCycleInstrumentation;

    /**
     * @return - the number of files collected last Record Output Period
     */
    @MonitoredAttribute(displayName = "Number of files Collected (current ROP)", visibility = Visibility.ALL, units = Units.NONE,
            category = Category.PERFORMANCE,
            interval = Interval.ONE_MIN, collectionType = CollectionType.DYNAMIC)
    public synchronized long getNumberOfFilesCollectedCurrentROP() {
        return combinedRopFileCollectionCycleInstrumentation.getFileCollectionCycleInstrumentation(RopPeriod.FIFTEEN_MIN.getDurationInSeconds())
                .getNumberOfFilesCollectedCurrentROP();
    }

    /**
     * @return - the number of files collected last Record Output Period
     */
    @MonitoredAttribute(displayName = "Number of files Collected (last ROP)", visibility = Visibility.ALL, units = Units.NONE,
            category = Category.PERFORMANCE,
            interval = Interval.ONE_MIN, collectionType = CollectionType.DYNAMIC)
    public synchronized long getNumberOfFilesCollectedLastROP() {
        return combinedRopFileCollectionCycleInstrumentation.getFileCollectionCycleInstrumentation(RopPeriod.FIFTEEN_MIN.getDurationInSeconds())
                .getNumberOfFilesCollectedLastROP();
    }

    /**
     * @return - the number of files failed last Record Output Period
     */
    @MonitoredAttribute(displayName = "Number of files failed (current ROP)", visibility = Visibility.ALL, units = Units.NONE,
            category = Category.PERFORMANCE,
            interval = Interval.ONE_MIN, collectionType = CollectionType.DYNAMIC)
    public synchronized long getNumberOfFilesFailedCurrentROP() {
        return combinedRopFileCollectionCycleInstrumentation.getFileCollectionCycleInstrumentation(RopPeriod.FIFTEEN_MIN.getDurationInSeconds())
                .getNumberOfFilesFailedCurrentROP();
    }

    /**
     * @return - the number of files failed last Record Output Period
     */
    @MonitoredAttribute(displayName = "Number of files failed (last ROP)", visibility = Visibility.ALL, units = Units.NONE,
            category = Category.PERFORMANCE,
            interval = Interval.ONE_MIN, collectionType = CollectionType.DYNAMIC)
    public synchronized long getNumberOfFilesFailedLastROP() {
        return combinedRopFileCollectionCycleInstrumentation.getFileCollectionCycleInstrumentation(RopPeriod.FIFTEEN_MIN.getDurationInSeconds())
                .getNumberOfFilesFailedLastROP();
    }

    /**
     * @return the number of bytes stored for current Record Output Period
     */
    @MonitoredAttribute(displayName = "Number of stored bytes (current ROP)", visibility = Visibility.ALL, units = Units.BYTES,
            category = Category.PERFORMANCE,
            interval = Interval.ONE_MIN, collectionType = CollectionType.DYNAMIC)
    public synchronized long getNumberOfBytesStoredCurrentROP() {
        return combinedRopFileCollectionCycleInstrumentation.getFileCollectionCycleInstrumentation(RopPeriod.FIFTEEN_MIN.getDurationInSeconds())
                .getNumberOfBytesStoredCurrentROP();
    }

    /**
     * @return - the number of bytes stored last Record Output Period
     */
    @MonitoredAttribute(displayName = "Number of stored bytes (last ROP)", visibility = Visibility.ALL, units = Units.BYTES,
            category = Category.PERFORMANCE,
            interval = Interval.ONE_MIN, collectionType = CollectionType.DYNAMIC)
    public synchronized long getNumberOfBytesStoredLastROP() {
        return combinedRopFileCollectionCycleInstrumentation.getFileCollectionCycleInstrumentation(RopPeriod.FIFTEEN_MIN.getDurationInSeconds())
                .getNumberOfBytesStoredLastROP();
    }

    /**
     * @return - the number of bytes transfered last Record Output Period
     */
    @MonitoredAttribute(displayName = "Number of transfered bytes (current ROP)", visibility = Visibility.ALL, units = Units.BYTES,
            category = Category.PERFORMANCE, interval = Interval.ONE_MIN, collectionType = CollectionType.DYNAMIC)
    public synchronized long getNumberOfBytesTransferedCurrentROP() {
        return combinedRopFileCollectionCycleInstrumentation.getFileCollectionCycleInstrumentation(RopPeriod.FIFTEEN_MIN.getDurationInSeconds())
                .getNumberOfBytesTransferredCurrentROP();
    }

    /**
     * @return - the number of bytes transfered last Record Output Period
     */
    @MonitoredAttribute(displayName = "Number of transfered bytes", visibility = Visibility.ALL, units = Units.BYTES,
            category = Category.PERFORMANCE, interval = Interval.ONE_MIN, collectionType = CollectionType.DYNAMIC)
    public synchronized long getNumberOfBytesTransferedLastROP() {
        return combinedRopFileCollectionCycleInstrumentation.getFileCollectionCycleInstrumentation(RopPeriod.FIFTEEN_MIN.getDurationInSeconds())
                .getNumberOfBytesTransferedLastROP();
    }

    /**
     * @return the Record Output Period collection time
     */
    @MonitoredAttribute(displayName = "ROP Collection time", visibility = Visibility.ALL, units = Units.NONE, category = Category.PERFORMANCE,
            interval = Interval.ONE_MIN, collectionType = CollectionType.DYNAMIC)
    public synchronized long getRopCollectionTime() {
        return combinedRopFileCollectionCycleInstrumentation.getFileCollectionCycleInstrumentation(RopPeriod.FIFTEEN_MIN.getDurationInSeconds())
                .getRopCollectionTime();
    }

    /**
     * @return the start time for the current Record Output Period
     */
    @MonitoredAttribute(displayName = "Current ROP start time", visibility = Visibility.ALL, units = Units.NONE, category = Category.PERFORMANCE,
            interval = Interval.ONE_MIN, collectionType = CollectionType.DYNAMIC)
    public synchronized long getRopStartTimeCurrentROP() {
        return combinedRopFileCollectionCycleInstrumentation.getFileCollectionCycleInstrumentation(RopPeriod.FIFTEEN_MIN.getDurationInSeconds())
                .getRopStartTimeCurrentROP();
    }

    /**
     * @return the end time for the current Record Output Period
     */
    @MonitoredAttribute(displayName = "Current ROP end time", visibility = Visibility.ALL, units = Units.NONE, category = Category.PERFORMANCE,
            interval = Interval.ONE_MIN, collectionType = CollectionType.DYNAMIC)
    public synchronized long getRopEndTimeCurrentROP() {
        return combinedRopFileCollectionCycleInstrumentation.getFileCollectionCycleInstrumentation(RopPeriod.FIFTEEN_MIN.getDurationInSeconds())
                .getRopEndTimeCurrentROP();
    }

    /**
     * @return the end time for the last Record Output Period
     */
    @MonitoredAttribute(displayName = "Last ROP end time", visibility = Visibility.ALL, units = Units.NONE, category = Category.PERFORMANCE,
            interval = Interval.ONE_MIN, collectionType = CollectionType.DYNAMIC)
    public synchronized long getRopEndTimeLastROP() {
        return combinedRopFileCollectionCycleInstrumentation.getFileCollectionCycleInstrumentation(RopPeriod.FIFTEEN_MIN.getDurationInSeconds())
                .getRopEndTimeLastROP();
    }

    /**
     * @return the start time for the last Record Output Period
     */
    @MonitoredAttribute(displayName = "Last ROP start time", visibility = Visibility.ALL, units = Units.NONE, category = Category.PERFORMANCE,
            interval = Interval.ONE_MIN, collectionType = CollectionType.DYNAMIC)
    public synchronized long getRopStartTimeLastROP() {
        return combinedRopFileCollectionCycleInstrumentation.getFileCollectionCycleInstrumentation(RopPeriod.FIFTEEN_MIN.getDurationInSeconds())
                .getRopStartTimeLastROP();
    }
}
