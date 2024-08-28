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

/**
 * File collection interval object to store file collection start and end times.
 */
public class FileCollectionInterval {

    private final long startTime;
    private final long endTime;

    /**
     * @param startTime
     *         - start time of FileCollectionInterval
     * @param endTime
     *         - end time of FileCollectionInterval
     */
    public FileCollectionInterval(final long startTime, final long endTime) {
        super();
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * @param time
     *         - time to check if it is between interval
     *
     * @return - returns true if time is between interval start time and end time
     */
    public boolean isBetweenInterval(final long time) {
        return time >= startTime && time < endTime;
    }

    /**
     * @return the startTime
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * @return the endTime
     */
    public long getEndTime() {
        return endTime;
    }

}
