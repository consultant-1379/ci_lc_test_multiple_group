/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
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


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.cache.Cache;

import com.ericsson.oss.services.pm.initiation.api.FileCollectionInterval;
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionChartData;
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionResultStatistics;


public abstract class AbstractFileCollectionStatsProcessor {

    /**
     * Only 1 Hr data needs to be retained for processing.
     * Cleaning up the entries older than 1.30 hr(5400s) from Map
     *
     */
     public void performCleanup(){
        final Date time = new Date(System.currentTimeMillis() - 5400 * 1000);
        final long retentionStartTime = time.getTime() - time.getTime() % ONE_MINUTE_IN_MILLISECONDS;
        final long retentionEndTime = System.currentTimeMillis() - System.currentTimeMillis() % ONE_MINUTE_IN_MILLISECONDS;
        final FileCollectionInterval interval = new FileCollectionInterval(retentionStartTime, retentionEndTime);
        for (final long collectionTime : getMap().keySet()) {
            if(!interval.isBetweenInterval(collectionTime)){
                getMap().remove(collectionTime);
            }
        }
    }

     public void updateStats(long collectionTime,long data) {
        if(getMap().containsKey(collectionTime)){
            long storedData = getMap().get(collectionTime);
            storedData +=data;
            getMap().put(collectionTime,storedData);
        }else{
            getMap().put(collectionTime,data);
        }
    }

    /**
     * Returns the data collection chart after processing.
     * @param intervals
     * @param chartDataList
     * @return
     */
     public List<FileCollectionChartData> getDataForIntervals(List<FileCollectionInterval> intervals,
                                                              List<FileCollectionChartData> chartDataList) {
        for (final Map.Entry<Long, Long> collectionTime : getMap().entrySet()) {
            for (int i = 0; i < intervals.size(); i++) {
                final FileCollectionInterval interval = intervals.get(i);
                final FileCollectionChartData chartData = chartDataList.get(i);
                if (interval.isBetweenInterval(collectionTime.getKey())) {
                    chartData.addToYaxisValue(collectionTime.getValue());
                    break;
                }
            }
        }
        return chartDataList;
    }

    /**
     * Retruns the processed data for the provided interval.
     * Data will be processed from cache only if the interval is not present in local map.
     * @param intervals
     * @param chartDataList
     * @return
     */
    public List<FileCollectionChartData> getProcessedData(List<FileCollectionInterval> intervals, List<FileCollectionChartData> chartDataList) {
        final List<Long> intervalListToProcessed = processIntervalList(intervals);
        if(!intervalListToProcessed.isEmpty()){
            performCleanup();
            updateFromCache(intervalListToProcessed);
        }
        return getDataForIntervals(intervals,chartDataList);
    }

    abstract Cache<String, FileCollectionResultStatistics> getCache();

    abstract ConcurrentHashMap<Long, Long> getMap();

    abstract List<Long> processIntervalList(List<FileCollectionInterval> interval);

    abstract void updateFromCache(List<Long> intervals);

}
