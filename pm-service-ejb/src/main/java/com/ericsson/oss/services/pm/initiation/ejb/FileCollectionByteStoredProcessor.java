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
import javax.ejb.Singleton;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache;
import com.ericsson.oss.services.pm.initiation.api.FileCollectionInterval;
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionChartData;
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionResultStatistics;
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionSuccessStatistics;

@Singleton
public class FileCollectionByteStoredProcessor extends AbstractFileCollectionStatsProcessor{

    @Inject
    @NamedCache("PMICFileCollectionResultCache")
    private Cache<String, FileCollectionResultStatistics> cache;

    ConcurrentHashMap<Long,Long> bytesStoredStats = new ConcurrentHashMap<>();

    private static final long INITIAL_BYTES_STORED = 0L;

    @Override
    public Cache<String, FileCollectionResultStatistics> getCache() {
        return cache;
    }

    @Override
    public ConcurrentHashMap<Long, Long> getMap() {
        return bytesStoredStats;
    }

    public List<FileCollectionChartData> populateBytesStoredChartDataList(final List<FileCollectionInterval> intervals,
                                                                          final List<FileCollectionChartData> chartDataList){
        return getProcessedData(intervals, chartDataList);
    }

    @Override
    public synchronized List<Long> processIntervalList(final List<FileCollectionInterval> intervals) {
        List<Long> intervalListToProcess = new ArrayList<>();
        for (final FileCollectionInterval interval: intervals) {
            final long endTime = interval.getEndTime();
            long startTime = interval.getStartTime();
            do{
                if (!bytesStoredStats.containsKey(startTime)) {
                    bytesStoredStats.put(startTime, INITIAL_BYTES_STORED);
                    intervalListToProcess.add(startTime);
                }
            startTime = startTime + ONE_MINUTE_IN_MILLISECONDS;
            }while(startTime<endTime);
        }
        return intervalListToProcess;
    }

    @Override
    public void updateFromCache(final List<Long> intervals){
        final Iterator<Cache.Entry<String, FileCollectionResultStatistics>> iterator = getCache().iterator();
        while (iterator.hasNext()) {
            final FileCollectionResultStatistics fileCollectionResultStatistics = iterator.next().getValue();
            if (fileCollectionResultStatistics instanceof FileCollectionSuccessStatistics) {
                final FileCollectionSuccessStatistics successStats = (FileCollectionSuccessStatistics) fileCollectionResultStatistics;
                final long endTime = successStats.getJobEndTime();
                final long normalizedEndTime = endTime - endTime % ONE_MINUTE_IN_MILLISECONDS;
                final long bytesStored = successStats.getBytesStored();
                if(intervals.contains(normalizedEndTime)) {
                    updateStats(normalizedEndTime, bytesStored);
                }
            }
        }

    }
}
