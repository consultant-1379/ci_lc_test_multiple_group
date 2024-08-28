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

import java.util.Map;

/**
 * This interface is provided to initiate file collection for node after counter activation.
 *
 * @author ekamkal, epirgui
 */
public interface FileCollectionTaskManagerLocal extends RopSchedulerServiceLocal {

    /**
     * Requests file collection to be started. Actual file collection takes a initial delay and Files will be collected for <br>
     * the next available full rop.
     *
     * @param request
     *         see {@link ProcessRequestVO}
     */
    void startFileCollection(ProcessRequestVO request);

    /**
     * Requests file collection to be stopped. No more file collection Jobs will be created for this Node/Rop. <br>
     * The jobs that are already in the queue to be collected will still be sent for collection
     *
     * @param request
     *         see {@link ProcessRequestVO}
     */
    void stopFileCollection(ProcessRequestVO request);

    /**
     * Starts File Collection immediately for a set of nodes and its processTypes.
     *
     * @param nodesWithProcessTypes
     *         map of NetworkElement fdn as key and its active processes types.
     */
    void startFileCollectionForNodes(final Map<String, ProcessTypesAndRopInfo> nodesWithProcessTypes);

    /**
     * Updates file collection for new ROP periods.
     * This will update the existing file collection request with the end time to be stopped at the end of the current ROP
     * also, a new request is added to the cache with the new ROP period to be started at the end of the current ROP.
     *
     * @param request
     *         see {@link ProcessRequestVO}
     * @param currentRopEndTimeInMills
     *         next ROP boundary time in milli seconds at which the previous ROP file collection <br>
     *         should stop and new ROP period file collection should start.
     * @param oldRopPeriodInSeconds
     *         value of the Old Rop Period to query in the cache.
     */
    void updateFileCollectionForNewRopPeriod(final ProcessRequestVO request, final long currentRopEndTimeInMills, final int oldRopPeriodInSeconds);
}
