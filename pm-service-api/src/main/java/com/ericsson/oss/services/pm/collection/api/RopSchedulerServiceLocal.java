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

package com.ericsson.oss.services.pm.collection.api;

/**
 * Rop scheduler service local.
 */
public interface RopSchedulerServiceLocal {
    /**
     * Creates a timer.
     *
     * @param ropTime
     *            the record output period time
     * @return returns true if timer is created
     */
    boolean createTimer(int ropTime);

    /**
     * Stop timer boolean.
     *
     * @param ropTime
     *            the record output period time
     * @return returns true if timer is stopped
     */
    boolean stopTimer(int ropTime);

    /**
     * Stop timer boolean.
     *
     * @param ropTime
     *            the record output period time
     * @return returns true if timer is stopped
     */
    boolean stopTimer(int ropTime, boolean isForced);

}
