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

import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ID;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler.ErrorNodeCacheAttributes;
import com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler.ErrorNodeCacheProcessType;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * This cache is used to keep track of nodes that are causing subcriptions to be in ERROR state.
 * It stores values with the nodeFdn as a key and a set of Longs as the value.
 * The Long values are the IDs of subscriptions that are in ERROR because of this node
 */
@ApplicationScoped
public class SubscriptionErrorNodeCache implements PmFunctionOffErrorNodeCache {

    @Inject
    private Logger logger;

    @Inject
    private FileCollectionActiveTaskCacheWrapper fileCollectionActiveTasksCache;

    @Inject
    private MembershipListener membershipListener;

    @Inject
    private StartupRecoveryMonitorLocal startupRecoveryMonitor;

    /**
     * Add an entry to the cache to indicate that the node is causing the given subscription to be in ERROR state
     *
     * @param nodeFdn
     *         - The node fdn
     * @param subscriptionId
     *         - The subscription ID
     *
     * @return
     */
    @Override
    public synchronized void addNodeWithPmFunctionOff(final String nodeFdn, final long subscriptionId) {
        masterCheck();
        final Map<String, Object> existingEntry = getErrorEntryOrEmptyMap(nodeFdn);
        final Set<Long> subIds = new HashSet<>(Arrays.asList(subscriptionId));
        @SuppressWarnings("unchecked") final Set<Long> existingSubIds = (Set<Long>) existingEntry.get(PMIC_ATT_SUBSCRIPTION_ID);
        if (existingSubIds != null) {
            subIds.addAll(existingSubIds);
        }
        existingEntry.put(PMIC_ATT_SUBSCRIPTION_ID, subIds);
        fileCollectionActiveTasksCache.put(nodeFdn, existingEntry);
    }

    /**
     * Removes a node from the list of nodes that are causing subscriptions to be in ERROR state
     *
     * @param nodeFdn
     *         - The node fdn
     *
     * @return true if the entry was removed
     */
    @Override
    public synchronized boolean removeErrorEntry(final String nodeFdn) {
        masterCheck();
        return fileCollectionActiveTasksCache.remove(nodeFdn);
    }

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
    @Override
    public synchronized boolean removeErrorEntry(final String nodeFdn, final long subscriptionId) {
        masterCheck();
        final Map<String, Object> errorEntry = getErrorEntry(nodeFdn);
        @SuppressWarnings("unchecked") final Set<Long> existingSubIds = (Set<Long>) errorEntry.get(PMIC_ATT_SUBSCRIPTION_ID);
        if (existingSubIds == null || existingSubIds.isEmpty()) {
            logger.debug("There are no associated error subscriptions for node {}. Removing cache entry", nodeFdn);
            return fileCollectionActiveTasksCache.remove(nodeFdn);
        }
        final boolean wasRemoved = existingSubIds.remove(subscriptionId);
        if (existingSubIds.isEmpty()) {
            logger.debug("Removing error cache entry for node {}", nodeFdn);
            return fileCollectionActiveTasksCache.remove(nodeFdn);
        } else if (wasRemoved) {
            fileCollectionActiveTasksCache.put(nodeFdn, errorEntry);
        }
        return wasRemoved;
    }

    /**
     * Returns a map of attributes associated with a node
     * The cache will contain an entry for this node if it is causing a subscription to be in ERROR state
     *
     * @param nodeFdn
     *         - The node fdn
     *
     * @return the associated attributes
     */
    @Override
    public Map<String, Object> getErrorEntry(final String nodeFdn) {
        if (startupRecoveryMonitor.isStartupRecoveryDone()) {
            final Map<String, Object> entry = fileCollectionActiveTasksCache.get(nodeFdn);
            if (entry == null) {
                return new HashMap<>();
            }
            return entry;
        }
        return new HashMap<>();
    }

    /**
     * Use this method to store error handler process requests until @StartupRecovery has finished populating the cache
     *
     * @param processType
     *         - The process type. Used to determine which implementation of the @ScannerErrorHandler interface to use to resolve the error
     * @param attributes
     *         - The attributes to be passed to the @ScannerErrorHandler
     *
     * @return
     */
    @Override
    public synchronized void storeRequest(final ErrorNodeCacheProcessType processType, final Map<String, Object> attributes) {
        masterCheck();
        if (attributes.get(ErrorNodeCacheAttributes.ERROR_HANDLER_PROCESS_TYPE) != null) {
            throw new IllegalArgumentException("Please do not use " + ErrorNodeCacheAttributes.ERROR_HANDLER_PROCESS_TYPE + " as a key");
        }
        attributes.put(ErrorNodeCacheAttributes.ERROR_HANDLER_PROCESS_TYPE, processType);
        final Map<String, Object> existingEntry = getErrorEntryOrEmptyMap(ErrorNodeCacheAttributes.STORED_REQUESTS_KEY);
        final Set<Map<String, Object>> existingStoredRequests = getStoredRequests(existingEntry);
        existingStoredRequests.add(attributes);
        existingEntry.put(ErrorNodeCacheAttributes.STORED_REQUESTS, existingStoredRequests);
        fileCollectionActiveTasksCache.put(ErrorNodeCacheAttributes.STORED_REQUESTS_KEY, existingEntry);
    }

    /**
     * @return The requests that had been stored up to this point
     * @StartupRecovery calls this method when it has finished its operations.
     * It then calls @ScannerErrorHandler.process() for each of the stored requests
     */
    @Override
    public synchronized Set<Map<String, Object>> removeStoredRequests() {
        masterCheck();
        final Map<String, Object> storedRequestsMap = getErrorEntryOrEmptyMap(ErrorNodeCacheAttributes.STORED_REQUESTS_KEY);
        @SuppressWarnings("unchecked") final Set<Map<String, Object>> storedRequests = (Set<Map<String, Object>>) storedRequestsMap.get(ErrorNodeCacheAttributes.STORED_REQUESTS);
        fileCollectionActiveTasksCache.remove(ErrorNodeCacheAttributes.STORED_REQUESTS_KEY);
        if (storedRequests == null || storedRequests.isEmpty()) {
            return new HashSet<>();
        }
        return storedRequests;
    }

    private Set<Map<String, Object>> getStoredRequests(final Map<String, Object> existingEntry) {
        @SuppressWarnings("unchecked") final Set<Map<String, Object>> storedRequests = (Set<Map<String, Object>>) existingEntry.get(ErrorNodeCacheAttributes.STORED_REQUESTS);
        if (storedRequests == null || storedRequests.isEmpty()) {
            return new HashSet<>();
        }
        return storedRequests;
    }

    private Map<String, Object> getErrorEntryOrEmptyMap(final String nodeFdn) {
        final Map<String, Object> entry = fileCollectionActiveTasksCache.get(nodeFdn);
        if (entry == null) {
            return new HashMap<>();
        }
        return entry;
    }

    private void masterCheck() {
        if (!membershipListener.isMaster()) {
            logger.error("This cache should only be modified on the master node");
        }
    }
}
