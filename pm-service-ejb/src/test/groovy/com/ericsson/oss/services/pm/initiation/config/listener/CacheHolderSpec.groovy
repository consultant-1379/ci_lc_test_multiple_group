/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.config.listener

import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionFailureStatistics
import com.ericsson.oss.services.pm.initiation.util.RopTime
import spock.lang.Unroll

import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants.FILE_COLLECTION_LAST_ROP_DATA
import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants.PMIC_SUBSCRIPTION_DATA_CACHE_V2
import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants.STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_COUNTERS_CACHE_V2
import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants.STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_NODES_CACHE
import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants.SUBSCRIPTION_OPERATION_EXECUTION_TRACKING_CACHE_V2

import javax.cache.Cache
import javax.inject.Inject

import org.slf4j.Logger

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionResultStatistics
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache
import com.ericsson.oss.itpf.sdk.cache.data.ValueHolder
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache

class CacheHolderSpec extends CdiSpecification {

    @ObjectUnderTest
    CacheHolder cacheHolder

    @Inject
    @NamedCache(PMIC_SUBSCRIPTION_DATA_CACHE_V2)
    Cache<String, ValueHolder> subscriptionDataCache
    @Inject
    PMICInitiationTrackerCache initiationResponseCache
    @Inject
    @NamedCache("PMICFileCollectionScheduledRecoveryCache")
    Cache<String, Map<String, Object>> fileCollectionScheduleRecoveryCache
    @Inject
    @NamedCache("PMICFileCollectionActiveTaskListCache")
    Cache<String, Map<String, Object>> fileCollectionActiveTasksCache
    @Inject
    @NamedCache("PMICFileCollectionResultCache")
    Cache<String, FileCollectionResultStatistics> fileCollectionResultStatisticsCache
    @Inject
    @NamedCache("PMICFileCollectionTaskCache")
    Cache<String, FileCollectionTaskWrapper> fileCollectionTaskCache
    @Inject
    @NamedCache(STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_NODES_CACHE)
    Cache<String, Set<String>> subscriptionNodesCache
    @Inject
    @NamedCache(STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_COUNTERS_CACHE_V2)
    Cache<String, Set<String>> subscriptionCountersCache;
    @Inject
    @NamedCache(SUBSCRIPTION_OPERATION_EXECUTION_TRACKING_CACHE_V2)
    Cache<Long, Map<String, Object>> subscriptionOperationExecutionTrackingCache
    @Inject
    @NamedCache(FILE_COLLECTION_LAST_ROP_DATA)
    Cache<String, Object> fileCollectionLastRopData
    @Inject
    @NamedCache("EBSLSubscriptionInitiationQueue")
    Cache<String, Map<String, Object>> ebslSubscriptionInitiationQueue

    @MockedImplementation
    Logger logger

    @Unroll
    def 'Cache Holder should perform correct logging'() {
        given: 'named caches with data which are not empty = #arePopulated'
            populateCachesIfRequired(arePopulated)

        when: 'cache holder synchronization is force'
            cacheHolder.forceSynchronizationOfReplicatedCaches()

        then: 'Correct logging is performed'
            1 * logger.info('FileCollectionLastRopData has at least one element: {}', arePopulated)
            1 * logger.info('subscriptionDataCache has at least one element: {}', arePopulated)
            1 * logger.info('initiationResponseCache has at least one element: {}', arePopulated)
            1 * logger.info('{} has at least one element: {}', STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_NODES_CACHE, arePopulated)
            1 * logger.info('{} has at least one element: {}', STATISTICAL_ACTIVE_SUBSCRIPTIONS_AND_COUNTERS_CACHE_V2, arePopulated)
            1 * logger.info('fileCollectionScheduleRecoveryCache has at least one element: {}', arePopulated)
            1 * logger.info('fileCollectionActiveTasksCache has at least one element: {}', arePopulated)
            1 * logger.info('fileCollectionResultStatisticsCache has at least one element: {}', arePopulated)
            1 * logger.info('fileCollectionTaskCache has at least one element: {}', arePopulated)
            1 * logger.info('subscriptionOperationExecutionTrackingCache has at least one element: {}', arePopulated)
            1 * logger.info('EBSLSubscriptionInitiationQueue has at least one element: {}', arePopulated)
        where:
            arePopulated << [true, false]

    }

    void populateCachesIfRequired(arePopulated) {
        if (arePopulated) {
            subscriptionDataCache.put('aKey', new ValueHolder())
            initiationResponseCache.startTrackingActivation('1', 'ACTIVATING', [:])
            fileCollectionScheduleRecoveryCache.put('aKey', [:])
            fileCollectionActiveTasksCache.put('aKey', [:])
            fileCollectionResultStatisticsCache.put('aKey', new FileCollectionFailureStatistics())
            fileCollectionTaskCache.put('aKey', new FileCollectionTaskWrapper(new FileCollectionTaskRequest(), new RopTime(1, 1), new RopTimeInfo(1, 1)))
            subscriptionNodesCache.put('aKey', [])
            subscriptionCountersCache.put('aKey', [])
            subscriptionOperationExecutionTrackingCache.put('aKey', [:])
            fileCollectionLastRopData.put('aKey', 'aValue')
            ebslSubscriptionInitiationQueue.put('aKey', [:])
        }
    }

}
