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

package com.ericsson.oss.services.pm.initiation.api;

import java.util.List;

import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionChartData;

/**
 * The File collection heart beat service.
 */
public interface FileCollectionHeartBeatServiceLocal {

    /**
     * Gets bytes stored chart data.
     *
     * @param timeIntervalMinutes
     *         the interval time in minutes
     * @param numberOfIntervals
     *         the number of intervals
     *
     * @return the bytes stored chart data
     */
    List<FileCollectionChartData> getBytesStoredChartData(final int timeIntervalMinutes, final int numberOfIntervals);

    /**
     * Gets files missed chart data.
     *
     * @param timeIntervalMinutes
     *         the interval time in minutes
     * @param numberOfIntervals
     *         the number of intervals
     *
     * @return the files missed chart data
     */
    List<FileCollectionChartData> getFilesMissedChartData(final int timeIntervalMinutes, final int numberOfIntervals);

}
