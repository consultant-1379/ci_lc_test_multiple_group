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

package com.ericsson.oss.services.pm.initiation.ejb;

import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants.STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_COUNTERS_CACHE_V2;
import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants.STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_NODES_CACHE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.cache.Cache;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache;
import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.initiation.api.CounterConflictService;
import com.ericsson.oss.services.pm.initiation.cache.api.StatisticalActiveSubscriptionsAndCountersCacheV2;
import com.ericsson.oss.services.pm.initiation.cache.api.StatisticalActiveSubscriptionsAndNodesCache;
import com.ericsson.oss.services.pm.initiation.model.metadata.counters.PmCountersLookUp;
import com.ericsson.oss.services.pm.initiation.rest.response.ConflictingCounterGroup;
import com.ericsson.oss.services.pm.initiation.rest.response.ConflictingNodeCounterInfo;
import com.ericsson.oss.services.pm.initiation.rest.response.ConflictsReportCsvUtils;
import com.ericsson.oss.services.pm.services.exception.CannotGetConflictingCountersException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * Provides an implementation class towards CounterConflict Cache.
 */
@ApplicationScoped
public class CounterConflictServiceImpl implements CounterConflictService {

    @Inject
    @NamedCache(STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_COUNTERS_CACHE_V2)
    private Cache<String, Set<CounterInfo>> subscriptionCountersCache;

    @Inject
    @NamedCache(STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_NODES_CACHE)
    private Cache<String, Set<String>> subscriptionNodesCache;
    @Inject
    private PmCountersLookUp countersLookup;
    @Inject
    private Logger logger;
    @Inject
    private ConflictsReportCsvUtils csvUtils;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    /**
     * Records active counters on subscription nodes in the cache. Will not add counters if already present, will update nodes if already present.
     *
     * @param nodeFdns
     *         - The list of node fdns of the subscription
     * @param counters
     *         - The {@link CounterInfo} objects from subscription.
     * @param subscriptionName
     *         - The subscription name.
     */
    public synchronized void addNodesAndCounters(final Set<String> nodeFdns, final List<CounterInfo> counters, final String subscriptionName) {
        logger.debug("Adding {} nodes and counters to {} and {} for subscription id {}", nodeFdns.size(),
                STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_COUNTERS_CACHE_V2, STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_NODES_CACHE, subscriptionName);
        addNodeToSubscriptionNodesCache(nodeFdns, subscriptionName);
        addCountersToSubscriptionCountersCache(counters, subscriptionName);
    }

    /**
     * Add nodes to the existing subscription in the cache. If the entry does not exist, nothing will be added.
     *
     * @param subscriptionName
     *         - the subscription name
     * @param nodeFdns
     *         - the node fdns
     *
     * @return - true if nodes was added to the cache, false if there was no such subscription ID in the cache.
     */
    public synchronized boolean addNodesToExistingSubscriptionEntry(final String subscriptionName, final Set<String> nodeFdns) {
        logger.debug("Adding {} nodes to {} for subscription ID {}", nodeFdns.size(), STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_NODES_CACHE,
                subscriptionName);
        if (subscriptionNodesCache.containsKey(subscriptionName)) {
            final Set<String> fdnsToUpdate = subscriptionNodesCache.get(subscriptionName);
            fdnsToUpdate.addAll(nodeFdns);
            subscriptionNodesCache.put(subscriptionName, fdnsToUpdate);
            return true;
        }
        return false;
    }

    /**
     * Remove node from all entries in the {@link StatisticalActiveSubscriptionsAndNodesCache}.
     *
     * @param nodeFdn
     *         - the node Fdn that has been deleted from ENM
     */
    public synchronized void removeNodeFromAllEntries(final String nodeFdn) {
        final Set<String> subscriptionIdsForEmptyEntries = new HashSet<>();
        for (final Cache.Entry<String, Set<String>> nodeEntryFromCache : subscriptionNodesCache) {
            final Set<String> nodeFdns = subscriptionNodesCache.get(nodeEntryFromCache.getKey());
            if (nodeFdns.remove(nodeFdn)) {
                if (nodeFdns.isEmpty()) {
                    logger.info("Last node {} has been removed from {} for subscription {}. Will remove all counters for this subscription as well",
                            nodeFdn, STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_NODES_CACHE, nodeEntryFromCache.getKey());
                    subscriptionIdsForEmptyEntries.add(nodeEntryFromCache.getKey());
                } else {
                    logger.trace("Removed node {} from {} for subscription {}", nodeFdn, STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_NODES_CACHE,
                            nodeEntryFromCache.getKey());
                    subscriptionNodesCache.put(nodeEntryFromCache.getKey(), nodeFdns);
                }
            }
        }
        for (final String subscriptionName : subscriptionIdsForEmptyEntries) {
            subscriptionNodesCache.remove(subscriptionName);
            subscriptionCountersCache.remove(subscriptionName);
        }
    }

    /**
     * Remove nodes from the cache. If all subscription's nodes are removed, the active counters are also removed.
     *
     * @param nodeFdns
     *         - The fdns of the nodes to remove.
     * @param subscriptionName
     *         - The subscriptionName
     */
    public synchronized void removeNodesFromExistingSubscriptionEntry(final Set<String> nodeFdns, final String subscriptionName) {
        logger.debug("Removing {} nodes from {} for subscription {}", nodeFdns.size(), STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_NODES_CACHE,
                subscriptionName);
        if (subscriptionNodesCache.containsKey(subscriptionName)) {
            final Set<String> nodeFdnsFromCache = subscriptionNodesCache.get(subscriptionName);
            nodeFdnsFromCache.removeAll(nodeFdns);
            if (nodeFdnsFromCache.isEmpty()) {
                logger.info("Last node has been removed from {} for subscription {}. Will remove all counters for this subscription as well",
                        STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_NODES_CACHE, subscriptionName);
                subscriptionNodesCache.remove(subscriptionName);
                subscriptionCountersCache.remove(subscriptionName);
            } else {
                subscriptionNodesCache.put(subscriptionName, nodeFdnsFromCache);
            }
        } else {
            logger.debug("Removing subscription {} from {} ", subscriptionName, STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_COUNTERS_CACHE_V2);
            subscriptionCountersCache.remove(subscriptionName);
        }
    }

    /**
     * Remove conflicts for subscription.
     *
     * @param subscriptionName
     *         - subscriptionName. Must be a valid subscription.
     */
    public synchronized void removeSubscriptionFromCache(final String subscriptionName) {
        logger.debug("Completely removing active counters and nodes for subscription {} from {} and {}", subscriptionName,
                STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_COUNTERS_CACHE_V2, STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_NODES_CACHE);
        subscriptionNodesCache.remove(subscriptionName);
        subscriptionCountersCache.remove(subscriptionName);
    }

    @Override
    public boolean hasAnyCounterConflict(final StatisticalSubscription subscription)
            throws CannotGetConflictingCountersException {
        final Set<String> nodeFdnsFromSubscription = getNodeFdnsFromSubscription(subscription);
        for (final Cache.Entry<String, Set<String>> nodeCacheEntry : subscriptionNodesCache) {
            if (!Objects.equals(nodeCacheEntry.getKey(), subscription.getName())) {
                final Set<String> commonNodes = getCommonNodes(nodeFdnsFromSubscription, nodeCacheEntry.getValue());
                if (!commonNodes.isEmpty()) {
                    final String conflictingSubscriptionName = nodeCacheEntry.getKey();
                    final Set<CounterInfo> commonCounters = getCommonCounters(subscription.getCounters(),
                            subscriptionCountersCache.get(conflictingSubscriptionName));
                    if (!commonCounters.isEmpty()) {
                        final Set<NodeTypeAndVersion> commonNodesVersionSet = getNodesTypeVersion(subscription, commonNodes);
                        final List<CounterInfo> filteredCommonCounters = countersLookup.getApplicableCounters(commonCounters, commonNodesVersionSet);
                        if (!filteredCommonCounters.isEmpty()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String getCounterConflictsReport(final StatisticalSubscription subscription)
            throws CannotGetConflictingCountersException {

        final long startTime = System.currentTimeMillis();

        final Set<String> nodeFdnsFromSubscription = subscription.getNodesFdns();

        final Set<String> subscriptionList = new HashSet<>();
        StringBuilder conflictsTable = new StringBuilder();

        for (final Cache.Entry<String, Set<String>> nodeCacheEntry : subscriptionNodesCache) {
            conflictsTable = processSubscriptionNodeCacheEntry(subscription, nodeCacheEntry, nodeFdnsFromSubscription, subscriptionList);
        }
        logger.debug("getCounterConflictsReport Processing Time = {}", System.currentTimeMillis() - startTime);
        return csvUtils.getReport(subscription.getName(), subscriptionList, conflictsTable);
    }

    private StringBuilder processSubscriptionNodeCacheEntry(final StatisticalSubscription subscription, final Cache.Entry<String, Set<String>> nodeCacheEntry,
                                                   final Set<String> nodeFdnsFromSubscription, final Set<String> subscriptionList) {
        if (Objects.equals(nodeCacheEntry.getKey(), subscription.getName())) {
            return new StringBuilder();
        }
        final Set<String> commonNodesFdns = getCommonNodes(nodeFdnsFromSubscription, nodeCacheEntry.getValue());
        if (commonNodesFdns.isEmpty()) {
            return new StringBuilder();
        }
        final String conflictingSubscriptionName = nodeCacheEntry.getKey();
        final Set<CounterInfo> commonCounters = getCommonCounters(subscription.getCounters(), subscriptionCountersCache.get(conflictingSubscriptionName));
        if (commonCounters.isEmpty()) {
            return new StringBuilder();
        }
        return updateCsvUtilWithEntries(subscription, commonNodesFdns, commonCounters, subscriptionList, conflictingSubscriptionName);
    }

    private StringBuilder updateCsvUtilWithEntries(final StatisticalSubscription subscription, final Set<String> commonNodesFdns,
                                                   final Set<CounterInfo> commonCounters, final Set<String> subscriptionList,
                                                   final String conflictingSubscriptionName) {
        final StringBuilder conflictsTable = new StringBuilder();
        final Map<NodeTypeAndVersion, List<String>> commonNodesVersionMap = getNodeTypeVersionMap(subscription, commonNodesFdns);
        for (final Map.Entry<NodeTypeAndVersion, List<String>> entry : commonNodesVersionMap.entrySet()) {
            final List<String> filteredCommonCounters = countersLookup.getApplicableCountersAsString(commonCounters, Collections.singleton(entry.getKey()));
            if (!filteredCommonCounters.isEmpty()) {
                subscriptionList.add(conflictingSubscriptionName);
                csvUtils.addTableEntry(conflictsTable, conflictingSubscriptionName, entry.getValue(), filteredCommonCounters);
            }
        }
        return conflictsTable;
    }
    @Override
    public Map<String, Collection<String>> getConflictingSubscriptionsForCounters(final StatisticalSubscription subscription)
            throws CannotGetConflictingCountersException {

        final long startTime = System.currentTimeMillis();

        final Map<String, Collection<String>> conflictingCounters = new HashMap<>();

        final Set<String> nodeFdnsFromSubscription = subscription.getNodesFdns();

        for (final Cache.Entry<String, Set<String>> nodeCacheEntry : subscriptionNodesCache) {
            if (!Objects.equals(nodeCacheEntry.getKey(), subscription.getName())) {
                final Set<String> commonNodesFdns = getCommonNodes(nodeFdnsFromSubscription, nodeCacheEntry.getValue());
                if (!commonNodesFdns.isEmpty()) {
                    final String conflictingSubscriptionName = nodeCacheEntry.getKey();
                    final Set<CounterInfo> commonCounters = getCommonCounters(subscription.getCounters(),
                            subscriptionCountersCache.get(conflictingSubscriptionName));
                    if (!commonCounters.isEmpty()) {
                        final Set<NodeTypeAndVersion> commonNodesVersionSet = getNodesTypeVersion(subscription, commonNodesFdns);
                        final List<String> filteredCommonCounters = countersLookup.getApplicableCountersAsString(commonCounters,
                                commonNodesVersionSet);
                        if (!filteredCommonCounters.isEmpty()) {
                            conflictingCounters.put(conflictingSubscriptionName, filteredCommonCounters);
                        }
                    }
                }
            }
        }

        logger.debug("getConflictingSubscriptionsForCounters Processing Time = {}", System.currentTimeMillis() - startTime);
        return conflictingCounters;

    }

    private Set<NodeTypeAndVersion> getNodesTypeVersion(final StatisticalSubscription subscription, final Set<String> nodesFdns) {
        final Set<NodeTypeAndVersion> nodeAndVersionSet = new HashSet<>();
        for (final Node node : subscription.getNodes()) {
            if (nodesFdns.contains(node.getFdn())) {
                nodeAndVersionSet.add(new NodeTypeAndVersion(node.getNeType(), node.getOssModelIdentity(), node.getTechnologyDomain()));
            }
        }
        return nodeAndVersionSet;
    }

    private Map<NodeTypeAndVersion, List<String>> getNodeTypeVersionMap(final StatisticalSubscription subscription, final Set<String> nodesFdns) {
        final Map<NodeTypeAndVersion, List<String>> nodeAndVersionMap = new HashMap<>();
        for (final Node node : subscription.getNodes()) {
            final String nodeFdn = node.getFdn();
            if (nodesFdns.contains(nodeFdn)) {
                final NodeTypeAndVersion nodeTypeAndVersion = new NodeTypeAndVersion(node.getNeType(), node.getOssModelIdentity(),
                        node.getTechnologyDomain());
                if (!nodeAndVersionMap.containsKey(nodeTypeAndVersion)) {
                    nodeAndVersionMap.put(nodeTypeAndVersion, new ArrayList<String>());

                }
                nodeAndVersionMap.get(nodeTypeAndVersion).add(nodeFdn);
            }
        }
        return nodeAndVersionMap;
    }

    @Override
    public ConflictingNodeCounterInfo getConflictingCountersInSubscription(final StatisticalSubscription subscription)
            throws CannotGetConflictingCountersException {
        final Set<String> conflictingNodes = new HashSet<>();
        final Set<CounterInfo> conflictingCounters = new HashSet<>();

        final Set<String> nodeFdnsFromSubscription = getNodeFdnsFromSubscription(subscription);

        for (final Cache.Entry<String, Set<String>> nodeCacheEntry : subscriptionNodesCache) {
            if (!Objects.equals(nodeCacheEntry.getKey(), subscription.getName())) {
                final Set<String> commonNodes = getCommonNodes(nodeFdnsFromSubscription, nodeCacheEntry.getValue());
                if (!commonNodes.isEmpty()) {
                    final String conflictingSubscriptionName = nodeCacheEntry.getKey();
                    final Set<CounterInfo> commonCounters = getCommonCounters(subscription.getCounters(),
                            subscriptionCountersCache.get(conflictingSubscriptionName));
                    if (!commonCounters.isEmpty()) {
                        final Set<NodeTypeAndVersion> commonNodesVersionSet = getNodesTypeVersion(subscription, commonNodes);
                        final List<CounterInfo> filteredCommonCounters = countersLookup.getApplicableCounters(commonCounters, commonNodesVersionSet);
                        conflictingNodes.addAll(commonNodes);
                        conflictingCounters.addAll(filteredCommonCounters);
                    }
                }
            }
        }

        return generateConflictingCounterReport(subscription.getId().toString(), conflictingNodes, conflictingCounters);
    }

    /**
     * Check whether there are any entries in the {@link StatisticalActiveSubscriptionsAndCountersCacheV2}
     *
     * @return - true if at least one entry exist
     */
    public boolean areEntriesInCounterCache() {
        return subscriptionCountersCache.iterator().hasNext();
    }

    /**
     * Check whether there are any entries in the {@link StatisticalActiveSubscriptionsAndNodesCache}
     *
     * @return - true if at least one entry exist
     */
    public boolean areEntriesInNodesCache() {
        return subscriptionNodesCache.iterator().hasNext();
    }

    private Set<String> getNodeFdnsFromSubscription(final StatisticalSubscription subscription) throws CannotGetConflictingCountersException {
        try {
            if (subscription.hasAssociations()) {
                return subscription.getNodesFdns();
            }
            if (!dpsAvailabilityStatus.isAvailable()) {
                logger.warn("Failed to find subscription for {}, Dps not available", subscription.getId());
                return Collections.emptySet();
            }
            final Subscription subscriptionFromDps = subscriptionReadOperationService.findByIdWithRetry(subscription.getId(), true);
            if (subscriptionFromDps == null) {
                logger.error("Cannot get conflicting counters for subscription with id {} as no such subscription exists in DPS",
                        subscription.getId());
                throw new CannotGetConflictingCountersException("Cannot get conflicting counters");
            }
            return ((ResourceSubscription) subscriptionFromDps).getNodesFdns();
        } catch (final RetryServiceException | DataAccessException e) {
            logger.error("Cannot get conflicting counters for subscription {} with id {}. Error Message: {}", subscription.getName(),
                    subscription.getId(), e.getMessage());
            throw new CannotGetConflictingCountersException("Cannot get conflicting counters", e);
        }
    }

    private void addNodeToSubscriptionNodesCache(final Set<String> nodeFdns, final String subscriptionName) {
        if (subscriptionNodesCache.containsKey(subscriptionName)) {
            final Set<String> activeNodesToUpdate = subscriptionNodesCache.get(subscriptionName);
            activeNodesToUpdate.addAll(nodeFdns);
            subscriptionNodesCache.put(subscriptionName, activeNodesToUpdate);
        } else {
            subscriptionNodesCache.put(subscriptionName, new HashSet<>(nodeFdns));
        }
    }

    private void addCountersToSubscriptionCountersCache(final List<CounterInfo> counters, final String subscriptionName) {
        if (subscriptionCountersCache.containsKey(subscriptionName)) {
            //counters can't change on an already active subscription.
            logger.trace("Will not add entry in the subscriptionCountersCache because counters for this subscription [{}] are already present.",
                    subscriptionName);
        } else {
            subscriptionCountersCache.put(subscriptionName, new HashSet<>(counters));
        }
    }

    private Set<CounterInfo> getCommonCounters(final List<CounterInfo> subscriptionCounters, final Set<CounterInfo> cacheCounters) {
        final Set<CounterInfo> commonCounters = new HashSet<>();
        if (subscriptionCounters != null) {
            commonCounters.addAll(subscriptionCounters);
        }
        if (cacheCounters != null) {
            commonCounters.retainAll(cacheCounters);
        }
        return commonCounters;
    }

    private Set<String> getCommonNodes(final Collection<String> nodeFdnsFromSubscription, final Set<String> nodeFdnsFromCache) {
        final Set<String> commonNodes = new HashSet<>();
        if (nodeFdnsFromCache != null) {
            commonNodes.addAll(nodeFdnsFromCache);
        }
        if (nodeFdnsFromSubscription != null) {
            commonNodes.retainAll(nodeFdnsFromSubscription);
        }
        return commonNodes;
    }

    private ConflictingNodeCounterInfo generateConflictingCounterReport(final String subscriptionId, final Set<String> conflictingNodes,
                                                                        final Set<CounterInfo> conflictingCounters) {

        final List<ConflictingCounterGroup> conflictingCounterGroups = new ArrayList<>();

        final Map<String, Set<String>> moClassToCounterMap = new HashMap<>();
        for (final CounterInfo counter : conflictingCounters) {
            final String moClassType = counter.getMoClassType();
            final String counterName = counter.getName();
            moClassToCounterMap.computeIfAbsent(moClassType, k -> new HashSet<>());
            moClassToCounterMap.get(moClassType).add(counterName);
        }

        for (final Map.Entry<String, Set<String>> entry : moClassToCounterMap.entrySet()) {
            final ConflictingCounterGroup group = new ConflictingCounterGroup(entry.getKey(), entry.getValue());
            conflictingCounterGroups.add(group);
        }
        logger.trace("Counter Conflict Report created for Subscription {}. Conflicting nodes: {} Conflicting Counters: {}", subscriptionId,
                conflictingNodes, conflictingCounterGroups);
        return new ConflictingNodeCounterInfo(subscriptionId, conflictingNodes, conflictingCounterGroups);
    }
}
