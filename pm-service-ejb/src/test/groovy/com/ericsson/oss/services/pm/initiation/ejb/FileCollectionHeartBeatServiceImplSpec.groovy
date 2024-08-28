/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2016
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.initiation.ejb

import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheKeyConstants.FAILURE
import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheKeyConstants.SUCCESS
import static com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants.ONE_MINUTE_IN_MILLISECONDS

import spock.lang.Unroll

import javax.cache.Cache
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionChartData
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionFailureStatistics
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionResultStatistics
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionSuccessStatistics

class FileCollectionHeartBeatServiceImplSpec extends SkeletonSpec {

    @ObjectUnderTest
    FileCollectionHeartBeatServiceImpl fileCollectionHeartBeatService

    @Inject
    @NamedCache("PMICFileCollectionResultCache")
    private Cache<String, FileCollectionResultStatistics> cache;

    def setup() {
        final long currentTimeMs = System.currentTimeMillis();
        final long jobEndTime = currentTimeMs - ONE_MINUTE_IN_MILLISECONDS;
        final long jobStartTime = jobEndTime - 10;
        final FileCollectionResultStatistics successStats = new FileCollectionSuccessStatistics(jobStartTime, jobEndTime, 1024, 1024);
        final FileCollectionResultStatistics failureStats = new FileCollectionFailureStatistics(jobEndTime, 8);
        cache.put(SUCCESS, successStats)
        cache.put(FAILURE, failureStats)
    }

    def "when request for chart date is received, should return list of chart data"() {

        given: "interval duration and number of intervals are set"
        final int intervalDuraton = 15
        final int noOfIntervals = 2

        when: "fileCollectionHeartBeatService is queried for success statistics"
        List<FileCollectionChartData> chartDataList = fileCollectionHeartBeatService.getBytesStoredChartData(intervalDuraton, noOfIntervals)

        then: "chart data list should have statistics with correct values"
        chartDataList.size() == noOfIntervals
        chartDataList.iterator().next().getyAxisValue() == 1024

        when: "fileCollectionHeartBeatService is queried for failure statistics"
        chartDataList = fileCollectionHeartBeatService.getFilesMissedChartData(intervalDuraton, noOfIntervals)

        then: "chart data list should have statistics with correct values"
        chartDataList.size() == noOfIntervals
        chartDataList.iterator().next().getyAxisValue() == 8
    }

    @Unroll
    def "when #message , should throw illegal argument exception "() {
        when: "fileCollectionHeartBeatService is queried for success statistics with invalid parmaters"
        fileCollectionHeartBeatService.getBytesStoredChartData(intervalDuraton, noOfIntervals)

        then: "An Illegal Argument Exception is thrown"
        thrown(IllegalArgumentException)

        where:
        intervalDuraton | noOfIntervals | message
        0               | 2             | 'interval duration is 0'
        -5              | 2             | 'interval duration is less than 0'
        15              | 0             | 'number of intervals is 0'
        15              | -2            | 'number of intervals is less than 0'
    }
}
