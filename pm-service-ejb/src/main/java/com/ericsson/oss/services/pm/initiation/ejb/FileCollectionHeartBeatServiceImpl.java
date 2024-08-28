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

package com.ericsson.oss.services.pm.initiation.ejb;

import static com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants.ONE_MINUTE_IN_MILLISECONDS;

import java.util.ArrayList;
import java.util.List;
import javax.cache.Cache;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache;
import com.ericsson.oss.services.pm.initiation.api.FileCollectionHeartBeatServiceLocal;
import com.ericsson.oss.services.pm.initiation.api.FileCollectionInterval;
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionChartData;
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionResultStatistics;

/**
 * The File collection heart beat service for getting chart data.
 */
public class FileCollectionHeartBeatServiceImpl implements FileCollectionHeartBeatServiceLocal {

    @Inject
    private Logger logger;

    @Inject
    @NamedCache("PMICFileCollectionResultCache")
    private Cache<String, FileCollectionResultStatistics> cache;

    @Inject
    private FileCollectionByteStoredProcessor fileCollectionByteStoredProcessor;

    @Inject
    private FileCollectionFilesMissedProcessor fileCollectionFilesMissedProcessor;

    @Override
    public List<FileCollectionChartData> getBytesStoredChartData(final int intervalDurationInMinutes, final int numberOfIntervals) {
        validate(intervalDurationInMinutes, numberOfIntervals);
        final long currentTimeMs = System.currentTimeMillis();
        final long intervalEndTimeMs = currentTimeMs - currentTimeMs % ONE_MINUTE_IN_MILLISECONDS;
        final long intervalDurationMs = intervalDurationInMinutes * ONE_MINUTE_IN_MILLISECONDS;
        final List<FileCollectionInterval> intervals = createIntervals(numberOfIntervals, intervalEndTimeMs, intervalDurationMs);
        final List<FileCollectionChartData> chartDataList = createChartDataList(numberOfIntervals, intervalEndTimeMs, intervalDurationMs);
        return populateBytesStoredChartDataList(intervals, chartDataList);
    }

    @Override
    public List<FileCollectionChartData> getFilesMissedChartData(final int intervalDurationInMinutes, final int numberOfIntervals) {
        validate(intervalDurationInMinutes, numberOfIntervals);
        final long currentTimeMs = System.currentTimeMillis();
        final long intervalEndTimeMs = currentTimeMs - currentTimeMs % ONE_MINUTE_IN_MILLISECONDS;
        final long intervalDurationMs = intervalDurationInMinutes * ONE_MINUTE_IN_MILLISECONDS;
        final List<FileCollectionInterval> fileCollectionIntervals = createIntervals(numberOfIntervals, intervalEndTimeMs, intervalDurationMs);
        final List<FileCollectionChartData> chartDataList = createChartDataList(numberOfIntervals, intervalEndTimeMs, intervalDurationMs);
        return populateFilesMissedChartDataList(fileCollectionIntervals, chartDataList);
    }

    /**
     * Create a list of interval objects Each interval in the list represents a
     * data point on the graph and has a start time and an end time. The purpose
     * of this list of intervals is for determining which interval a given time
     * falls between. e.g to determine which interval the taskFailureTime of a
     * FileCollectionFailureStatistics object corresponds to.
     */
    private List<FileCollectionInterval> createIntervals(final int numberOfIntervals, long intervalEndTimeMs, final long intervalDurationMs) {
        final List<FileCollectionInterval> fileCollectionIntervals = new ArrayList<>(numberOfIntervals);
        for (int i = 1; i <= numberOfIntervals; i++) {
            final long intervalStartTimeMs = intervalEndTimeMs - intervalDurationMs;
            final FileCollectionInterval interval = new FileCollectionInterval(intervalStartTimeMs, intervalEndTimeMs);
            fileCollectionIntervals.add(interval);
            intervalEndTimeMs = intervalEndTimeMs - intervalDurationMs;
        }
        return fileCollectionIntervals;
    }

    /**
     * Create a List of FileCollectionChartData objects This method creates the
     * objects with a time attribute (the x axis of the chart). The data
     * (y-axis) for the chart is calculated and populated later.
     */
    private List<FileCollectionChartData> createChartDataList(final int numberOfIntervals, long intervalEndTimeMs, final long intervalDurationMs) {
        final List<FileCollectionChartData> chartDataList = new ArrayList<>(numberOfIntervals);
        for (int i = 1; i <= numberOfIntervals; i++) {
            final FileCollectionChartData chartData = new FileCollectionChartData(intervalEndTimeMs);
            chartDataList.add(chartData);
            intervalEndTimeMs = intervalEndTimeMs - intervalDurationMs;
        }
        return chartDataList;
    }

    /**
     * Populate the data of the chart (y axis attribute) with Bytes Stored data.
     * This is done by getting each FileCollectionSuccessStatistcs object from
     * the cache, iterating over the List of intervals and using the
     * taskFailureTime of each statistic to determine during which interval it
     * occurred, and therefore which corresponding CharData object to update.
     */
    private List<FileCollectionChartData> populateBytesStoredChartDataList(final List<FileCollectionInterval> intervals,
                                                                           final List<FileCollectionChartData> chartDataList) {
        return fileCollectionByteStoredProcessor.populateBytesStoredChartDataList(intervals, chartDataList);
    }

    /**
     * Populate the data of the chart (y axis attribute) with Files Missed data.
     * This is done by getting each FileCollectionFauilureStatistcs from the
     * cache, iterating over the List of intervals and using the taskFailureTime
     * of each statistic to determine during which interval it occurred, and
     * therefore which corresponding CharData object to update.
     */
    private List<FileCollectionChartData> populateFilesMissedChartDataList(final List<FileCollectionInterval> intervals,
                                                                           final List<FileCollectionChartData> chartDataList) {
        return fileCollectionFilesMissedProcessor.populateMissedFilesChartDataList(intervals, chartDataList);
    }

    /**
     * Ensure the intervalDurationInMinutes and numberOfIntervals are valid.
     */
    private void validate(final int intervalDurationInMinutes, final int numberOfIntervals) {
        if (getCache() == null) {
            logger.warn("PMICFileCollectionResultCache is not present. Can't get PMIC File Collection statistics.");
            throw new IllegalArgumentException("Cache does not exist");
        }
        if (intervalDurationInMinutes <= 0) {
            throw new IllegalArgumentException("Time of Interval in minutes must be greater than 0");
        }
        if (numberOfIntervals <= 0) {
            throw new IllegalArgumentException("Number of intervals must be greater than 0");
        }
    }

    protected Cache<String, FileCollectionResultStatistics> getCache() {
        return cache;
    }
}
