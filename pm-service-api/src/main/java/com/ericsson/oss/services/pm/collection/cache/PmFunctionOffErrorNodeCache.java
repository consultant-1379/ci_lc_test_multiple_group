/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.collection.cache;

import java.util.Map;
import java.util.Set;

import com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler.ErrorNodeCacheProcessType;

/**
 * Interface for tracking nodes with erroneous subscriptions caused by failed activation because pm function was off at the time.
 */
public interface PmFunctionOffErrorNodeCache {

    /**
     * Add an entry to the cache to indicate that the node is causing the given subscription to be in ERROR state.
     * You should only add such error entry if the cause of the error is a failed activation caused by node having PmFunction pmEnabled == false
     *
     * @param nodeFdn
     *         - The node fdn
     * @param subscriptionId
     *         - The subscription ID
     */
    void addNodeWithPmFunctionOff(final String nodeFdn, final long subscriptionId);

    /**
     * Removes a node from the list of nodes that are causing subscriptions to be in ERROR state
     *
     * @param nodeFdn
     *         - The node fdn
     *
     * @return true if the entry was removed
     */
    boolean removeErrorEntry(final String nodeFdn);

    /**
     * Removes a subscription ID from the list of subscriptions that this node is causing to be in ERROR state
     *
     * @param nodeFdn
     *         - The node fdn
     * @param subscriptionId
     *         - The subscription ID
     *
     * @return true if the entry was removed
     */
    boolean removeErrorEntry(final String nodeFdn, final long subscriptionId);

    /**
     * Returns a map of attributes associated with a node The cache will contain an entry for this node if it is causing a subscription to be in ERROR
     * state
     *
     * @param nodeFdn
     *         - The node fdn
     *
     * @return the associated attributes
     */
    Map<String, Object> getErrorEntry(final String nodeFdn);

    /**
     * Use this method to store error handler process requests until @StartupRecovery has finished populating the cache
     *
     * @param processType
     *         - The process type. Used to determine which implementation of the @ScannerErrorHandler interface to use to resolve the error
     * @param attributes
     *         - The attributes to be passed to the @ScannerErrorHandler
     */
    void storeRequest(final ErrorNodeCacheProcessType processType, final Map<String, Object> attributes);

    /**
     * @return The requests that had been stored up to this point
     * @StartupRecovery calls this method when it has finished its operations.
     * It then calls @ScannerErrorHandler.process() for each of the stored requests
     */
    Set<Map<String, Object>> removeStoredRequests();
}
