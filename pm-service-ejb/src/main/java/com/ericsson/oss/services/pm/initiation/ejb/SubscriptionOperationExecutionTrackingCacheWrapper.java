/*******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.initiation.ejb;

import static com.ericsson.oss.pmic.api.constants.ModelConstants.SubscriptionConstants.SUBSCRIPTION_ADMINISTRATIVE_STATE;
import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants.SUBSCRIPTION_OPERATION_EXECUTION_TRACKING_CACHE_V2;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.cache.Cache;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.services.pm.time.TimeGenerator;

/**
 * The wrapper class for easy interaction with SUBSCRIPTION_OPERATION_EXECUTION_TRACKING_CACHE.
 */
@ApplicationScoped
public class SubscriptionOperationExecutionTrackingCacheWrapper {

    public static final String OPERATION_ACTIVATE_SUBSCRIPTION = "activateSubscription";
    public static final String OPERATION_ACTIVATE_NODES = "activateNodes";
    public static final String OPERATION_DEACTIVATE_SUBSCRIPTION = "deactivateSubscription";
    public static final String OPERATION_DEACTIVATE_NODES = "deactivateNodes";
    private static final String STARTED = "started";
    private static final String OPERATION = "operation";
    private static final String NODES = "nodes";
    private static final String TIMES_EXECUTED = "#executed";

    @Inject
    private Logger logger;

    @Inject
    private TimeGenerator timeGenerator;

    @Inject
    @NamedCache(SUBSCRIPTION_OPERATION_EXECUTION_TRACKING_CACHE_V2)
    private Cache<Long, Map<String, Object>> cache;

    /**
     * Add subscription to the cache. Will remove the subscription from cache if this is the 4th time the subscription is being added to the cache.
     *
     * @param subscriptionId
     *         - the subscription ID
     * @param action
     *         - the action to be taken if/when this subscription will time out
     * @param nodes
     *         - the nodes to be activated/deactivated if/when this subscription will time out
     */
    public void addEntry(final Long subscriptionId, final String action, final Iterable<Node> nodes) {
        final Map<String, Object> map = new HashMap<>();
        map.put(OPERATION, action);
        map.put(STARTED, new Date(timeGenerator.currentTimeMillis()));
        map.put(NODES, nodes);
        if (cache.containsKey(subscriptionId)) {
            final Map<String, Object> existingMap = cache.get(subscriptionId);
            Short timesExecuted = (Short) existingMap.get(TIMES_EXECUTED);
            if (timesExecuted != null) {
                timesExecuted++;
                if (timesExecuted > 2) {
                    logger.warn("Will not add subscription {} to {} because this is the 4th time it would have been added for action {}. "
                                    + "This log is printed if the subscription initiation manager finds the subscription in a "
                                    + "ACTIVATING/DEACTIVATING/UPDATING state after it has retried to execute the action {} for the 4th time. "
                                    + "Proceeding with subscription removal from the cache",
                            subscriptionId, SUBSCRIPTION_OPERATION_EXECUTION_TRACKING_CACHE_V2, action, action);
                    cache.remove(subscriptionId);
                    return;
                } else {
                    map.put(TIMES_EXECUTED, timesExecuted);
                }
            }
            logger.info("Subscription {} already exists in the cache. Action {}, Created {}. Proceeding to overwrite with for action {}",
                    subscriptionId, existingMap.get(OPERATION), existingMap.get(STARTED), action);
        } else {
            map.put(TIMES_EXECUTED, (short) 0);
        }
        cache.put(subscriptionId, map);
    }

    /**
     * Add subscription to the cache.
     *
     * @param subscriptionId
     *         - the subscription ID
     * @param action
     *         - the action to be taken if/when this subscription will time out
     */
    public void addEntry(final Long subscriptionId, final String action) {
        addEntry(subscriptionId, action, new ArrayList<Node>(0));
    }

    /**
     * Remove subscription ID from the cache.
     *
     * @param subscriptionId
     *         - the subscription ID
     */
    public void removeEntry(final Long subscriptionId) {
        if (cache.containsKey(subscriptionId)) {
            final Map<String, Object> existingMap = cache.get(subscriptionId);
            logger.debug("Requested to remove subscription {} from {}. This entry was created {} with administrationState {}.", subscriptionId,
                    SUBSCRIPTION_OPERATION_EXECUTION_TRACKING_CACHE_V2, existingMap.get(STARTED), existingMap.get(SUBSCRIPTION_ADMINISTRATIVE_STATE));
            cache.remove(subscriptionId);
        }
    }

    /**
     * Check subscription ID is presen in the cache.
     *
     * @param subscriptionId
     *         - the subscription ID
     *
     * @return boolean
     * - true if cache contains subscriptionId
     */
    public boolean containsEntry(final Long subscriptionId) {
        return cache.containsKey(subscriptionId);
    }

    /**
     * Get all cache entries
     *
     * @return - list of {@link SubscriptionOperationExecutionTrackingCacheEntry}
     */
    public List<SubscriptionOperationExecutionTrackingCacheEntry> getAllEntries() {
        final List<SubscriptionOperationExecutionTrackingCacheEntry> resultList = new ArrayList<>();
        for (final Iterator<Cache.Entry<Long, Map<String, Object>>> iterator = cache.iterator(); iterator.hasNext(); ) {
            final Cache.Entry<Long, Map<String, Object>> entry = iterator.next();
            final Map<String, Object> attributes = entry.getValue();
            final String action = (String) attributes.get(OPERATION);
            final Date created = (Date) attributes.get(STARTED);
            List<Node> nodes;
            if (attributes.containsKey(NODES)) {
                //noinspection unchecked
                nodes = (List<Node>) attributes.get(NODES);
            } else {
                nodes = new ArrayList<>(0);
            }
            resultList.add(new SubscriptionOperationExecutionTrackingCacheEntry(entry.getKey(), action, created, nodes));
        }
        return resultList;
    }
}
