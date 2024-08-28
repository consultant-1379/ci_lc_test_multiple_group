/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.config.listener;

import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants.FILE_COLLECTION_LAST_ROP_DATA;
import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants.PMIC_SUBSCRIPTION_DATA_CACHE_V2;
import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants.STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_COUNTERS_CACHE_V2;
import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants.STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_NODES_CACHE;
import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants.SUBSCRIPTION_OPERATION_EXECUTION_TRACKING_CACHE_V2;

import java.util.Map;
import java.util.Set;
import javax.cache.Cache;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache;
import com.ericsson.oss.itpf.sdk.cache.data.ValueHolder;
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache;
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionResultStatistics;
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;

/**
 * Util class to force syncing of pm caches
 */
public class CacheHolder {

    @Inject
    @NamedCache(PMIC_SUBSCRIPTION_DATA_CACHE_V2)
    private Cache<String, ValueHolder> subscriptionDataCache;

    @Inject
    private PMICInitiationTrackerCache initiationResponseCache;

    @Inject
    @NamedCache("PMICFileCollectionScheduledRecoveryCache")
    private Cache<String, Map<String, Object>> fileCollectionScheduleRecoveryCache;

    @Inject
    @NamedCache("PMICFileCollectionActiveTaskListCache")
    private Cache<String, Map<String, Object>> fileCollectionActiveTasksCache;

    @Inject
    @NamedCache("PMICFileCollectionResultCache")
    private Cache<String, FileCollectionResultStatistics> fileCollectionResultStatisticsCache;

    @Inject
    @NamedCache("PMICFileCollectionTaskCache")
    private Cache<String, FileCollectionTaskWrapper> fileCollectionTaskCache;

    @Inject
    @NamedCache(STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_NODES_CACHE)
    private Cache<String, Set<String>> subscriptionNodesCache;

    @Inject
    @NamedCache(STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_COUNTERS_CACHE_V2)
    private Cache<String, Set<String>> subscriptionCountersCache;

    @Inject
    @NamedCache(SUBSCRIPTION_OPERATION_EXECUTION_TRACKING_CACHE_V2)
    private Cache<Long, Map<String, Object>> subscriptionOperationExecutionTrackingCache;

    @Inject
    @NamedCache(FILE_COLLECTION_LAST_ROP_DATA)
    private Cache<String, Object> fileCollectionLastRopData;

    @Inject
    @NamedCache("EBSLSubscriptionInitiationQueue")
    private Cache<String, Map<String, Object>> ebslSubscriptionInitiationQueue;

    @Inject
    private Logger log;

    /**
     * https://jira-nam.lmera.ericsson.se/browse/TORF-148028 Force cache synchronisation on startup
     */
    public void forceSynchronizationOfReplicatedCaches() {
        log.info("FileCollectionLastRopData has at least one element: {}", fileCollectionLastRopData.iterator().hasNext());
        log.info("subscriptionDataCache has at least one element: {}", subscriptionDataCache.iterator().hasNext());
        log.info("initiationResponseCache has at least one element: {}", !initiationResponseCache.getAllTrackers().isEmpty());
        log.info("{} has at least one element: {}", STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_NODES_CACHE, subscriptionNodesCache.iterator().hasNext());
        log.info("{} has at least one element: {}", STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_COUNTERS_CACHE_V2,
                subscriptionCountersCache.iterator().hasNext());
        log.info("fileCollectionScheduleRecoveryCache has at least one element: {}", fileCollectionScheduleRecoveryCache.iterator().hasNext());
        log.info("fileCollectionActiveTasksCache has at least one element: {}", fileCollectionActiveTasksCache.iterator().hasNext());
        log.info("fileCollectionResultStatisticsCache has at least one element: {}", fileCollectionResultStatisticsCache.iterator().hasNext());
        log.info("fileCollectionTaskCache has at least one element: {}", fileCollectionTaskCache.iterator().hasNext());
        log.info("subscriptionOperationExecutionTrackingCache has at least one element: {}",
                subscriptionOperationExecutionTrackingCache.iterator().hasNext());
        log.info("EBSLSubscriptionInitiationQueue has at least one element: {}", ebslSubscriptionInitiationQueue.iterator().hasNext());
    }
}
