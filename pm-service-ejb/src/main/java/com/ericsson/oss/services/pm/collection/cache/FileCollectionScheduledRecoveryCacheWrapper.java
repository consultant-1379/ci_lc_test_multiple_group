/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.collection.cache;

import static com.ericsson.oss.services.pm.collection.cache.utils.DataTypeUtil.getIntValue;
import static com.ericsson.oss.services.pm.collection.cache.utils.DataTypeUtil.getLongValue;
import static com.ericsson.oss.services.pm.collection.cache.utils.DataTypeUtil.getStringValue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.cache.Cache;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache;
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;

/**
 * This class acts as a wrapper for PMICFileCollectionScheduledRecoveryCache.
 */
@Singleton
@Lock(LockType.READ)
public class FileCollectionScheduledRecoveryCacheWrapper {

    private static final String DELIMITER = "_";
    private static final String NODE_ADDRESS_ATTRIBUTE = "nodeAddress";
    private static final String ROP_PERIOD_ATTRIBUTE = "ropPeriod";
    private static final String PROCESS_TYPE_ATTRIBUTE = "processType";
    private static final String START_TIME_ATTRIBUTE = "startTime";
    private static final String END_TIME_ATTRIBUTE = "endTime";
    private final AtomicInteger cacheSize = new AtomicInteger();
    @Inject
    @NamedCache("PMICFileCollectionScheduledRecoveryCache")
    private Cache<String, Map<String, Object>> cache;

    /**
     * Retrieves the initial size of the cache.
     */
    @PostConstruct
    public void init() {
        final int size = getProcessRequests().size();
        cacheSize.set(size);
    }

    /**
     * Adds a new ProcessRequest to the cache.
     *
     * @param requestVO
     *            - ProcessRequest to add
     */
    @Lock(LockType.WRITE)
    public void addProcessRequest(final ProcessRequestVO requestVO) {
        final String key = getKeyFor(requestVO);
        if (!cache.containsKey(key)) {
            final Map map = extractAttributesToMap(requestVO);
            cache.put(key, map);
            cacheSize.incrementAndGet();
        }
    }

    /**
     * Updates an existing ProcessRequest.
     *
     * @param requestVO
     *            - ProcessRequest to update
     */
    @Lock(LockType.WRITE)
    public void updateProcessRequest(final ProcessRequestVO requestVO) {
        final String key = getKeyFor(requestVO);
        final Map map = extractAttributesToMap(requestVO);
        cache.replace(key, map);
    }

    /**
     * Retrieves a ProcessRequest based on a key.
     *
     * @param key
     *            - Record Output Period in seconds + nodes address + ProcessType
     * @return ProcessRequest
     */
    public ProcessRequestVO getProcessRequest(final String key) {
        final Map map = cache.get(key);
        if (map != null) {
            return generateProcessRequest(map);
        } else {
            return null;
        }
    }

    /**
     * Returns all ProcessRequests in the cache.
     *
     * @return {@code Set<ProcessRequest>}
     */
    public Set<ProcessRequestVO> getProcessRequests() {
        final Set<ProcessRequestVO> processRequests = new HashSet<>();
        final Iterator<Cache.Entry<String, Map<String, Object>>> cacheIterator = cache.iterator();
        while (cacheIterator.hasNext()) {
            final Cache.Entry<String, Map<String, Object>> cacheEntry = cacheIterator.next();
            final Map<String, Object> map = cacheEntry.getValue();
            processRequests.add(generateProcessRequest(map));
        }
        return processRequests;
    }

    /**
     * Removes a ProcessRequest based on a key.
     *
     * @param key
     *            - Record Output Period in seconds + nodes address + ProcessType
     */
    @Lock(LockType.WRITE)
    public void removeProcessRequest(final String key) {
        if (cache.remove(key)) {
            cacheSize.decrementAndGet();
        }
    }

    /**
     * Removes ProcessRequest.
     *
     * @param requestVO
     *            - ProcessRequest to remove
     */
    public void removeProcessRequest(final ProcessRequestVO requestVO) {
        final String key = getKeyFor(requestVO);
        removeProcessRequest(key);
    }

    /**
     * @return returns the size of the cache
     */
    public int size() {
        return cacheSize.get();
    }

    /**
     * Retrieves all ProcessRequest for a particular Record Output Period
     *
     * @param rop
     *            -int value for Record Output Period
     * @return {@code Set<ProcessRequestVO>} A list of ProcessRequest for a Record Output Period period.
     */
    public Set<ProcessRequestVO> getProcessRequestForRop(final Integer... rops) {
        if (rops.length == 0) {
            return getProcessRequests();
        }
        final Set<ProcessRequestVO> processRequests = new HashSet<>();
        final Iterator<Cache.Entry<String, Map<String, Object>>> cacheIterator = cache.iterator();
        final List<Integer> validRops = Arrays.asList(rops);
        while (cacheIterator.hasNext()) {
            final Cache.Entry<String, Map<String, Object>> cacheEntry = cacheIterator.next();
            final Map<String, Object> map = cacheEntry.getValue();
            final int ropPeriod = getIntValue(ROP_PERIOD_ATTRIBUTE, map);
            if (validRops.contains(ropPeriod)) {
                processRequests.add(generateProcessRequest(map));
            }
        }
        return processRequests;
    }

    /**
     * Converts a map into a ProcessRequest POJO.
     *
     * @param map
     *            - map to be converted into a ProcessRequest
     * @return ProcessRequest
     */
    private ProcessRequestVO generateProcessRequest(final Map<String, Object> map) {
        final String nodeAddress = getStringValue(NODE_ADDRESS_ATTRIBUTE, map);
        final int ropPeriod = getIntValue(ROP_PERIOD_ATTRIBUTE, map);
        final String processType = getStringValue(PROCESS_TYPE_ATTRIBUTE, map);
        final long startTime = getLongValue(START_TIME_ATTRIBUTE, map);
        final long endTime = getLongValue(END_TIME_ATTRIBUTE, map);
        return new ProcessRequestVO.ProcessRequestVOBuilder(nodeAddress, ropPeriod, processType).startTime(startTime)
                .endTime(endTime).build();

    }

    /**
     * Generates the key used in the cache from the ProcessRequest object Format is : ROP_FDN_PROCESSTYPE
     *
     * @param requestVO
     *            - ProcessRequest to get key for
     * @return String
     */
    private String getKeyFor(final ProcessRequestVO requestVO) {
        final StringBuilder key = new StringBuilder();
        key.append(requestVO.getRopPeriod());
        key.append(DELIMITER);
        key.append(requestVO.getNodeAddress());
        key.append(DELIMITER);
        key.append(requestVO.getProcessType());
        return key.toString();
    }

    /**
     * Generates a map from the ProcessRequest object.
     *
     * @param requestVO
     *            - ProcessRequest
     * @return {@code Map<String, Object>}
     */
    private Map<String, Object> extractAttributesToMap(final ProcessRequestVO requestVO) {
        final Map<String, Object> map = new HashMap<>();
        map.put(NODE_ADDRESS_ATTRIBUTE, requestVO.getNodeAddress());
        map.put(PROCESS_TYPE_ATTRIBUTE, requestVO.getProcessType());
        map.put(ROP_PERIOD_ATTRIBUTE, requestVO.getRopPeriod());
        map.put(START_TIME_ATTRIBUTE, requestVO.getStartTime());
        map.put(END_TIME_ATTRIBUTE, requestVO.getEndTime());
        return map;
    }

}
