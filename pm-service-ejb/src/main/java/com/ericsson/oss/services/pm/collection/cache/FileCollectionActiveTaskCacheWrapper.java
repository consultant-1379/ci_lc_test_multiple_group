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
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ID;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache;
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.cache.model.value.ProcessType;
import com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler.ErrorNodeCacheAttributes;

/**
 * This class acts as a wrapper for PMICFileCollectionActiveTaskListCache
 *
 * @author eeimho
 */

@Singleton
@Lock(LockType.WRITE)
public class FileCollectionActiveTaskCacheWrapper {
    private static final Logger logger = LoggerFactory.getLogger(FileCollectionActiveTaskCacheWrapper.class);

    private static final String DELIMITER = "_";
    private static final String NODE_ADDRESS_ATTRIBUTE = "nodeAddress";
    private static final String ROP_PERIOD_ATTRIBUTE = "ropPeriod";
    private static final String PROCESS_TYPE_ATTRIBUTE = "processType";
    private static final String START_TIME_ATTRIBUTE = "startTime";
    private static final String END_TIME_ATTRIBUTE = "endTime";
    private final AtomicInteger cacheSize = new AtomicInteger();
    @Inject
    @NamedCache("PMICFileCollectionActiveTaskListCache")
    private Cache<String, Map<String, Object>> cache;
    @Inject
    private NodeService nodeService;

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
     *            - ProcessRequest
     */
    public void addProcessRequest(final ProcessRequestVO requestVO) {
        final String key = getKeyFor(requestVO);
        if (!isFileCollectionEnabled(requestVO)) {
            logger.info("{} for key {} not added to file collection active node list cache. File Collection DISABLED", requestVO, key);
            return;
        }
        if (!cache.containsKey(key)) {
            final Map<String, Object> map = extractAttributesToMap(requestVO);
            cache.put(key, map);
            cacheSize.incrementAndGet();
            logger.debug("Added new file collection task request to cache for key {} with values {} ",key, map);
        } else {
            logger.debug("{} not added to file collection active node list cache. Cache contains {}", requestVO, key);
        }
    }

    private void addProcessRequest(final Set<ProcessRequestVO> processRequests, final Map<String, Object> map) {
        final ProcessRequestVO processRequestVO = generateProcessRequest(map);
        if (processRequestVO != null) {
            processRequests.add(processRequestVO);
        }
    }

    /**
     * Updates an existing ProcessRequest.
     *
     * @param requestVO
     *            - ProcessRequest
     */
    public void updateProcessRequest(final ProcessRequestVO requestVO) {
        final String key = getKeyFor(requestVO);
        final Map<String, Object> map = extractAttributesToMap(requestVO);
        cache.replace(key, map);
    }

    /**
     * Retrieves a ProcessRequest based on a key.
     *
     * @param key
     *            - Record Output Period InSeconds + Fully Distinguished Name Of Node + ProcessType
     * @return ProcessRequest
     */
    @Lock(LockType.READ)
    public ProcessRequestVO getProcessRequest(final String key) {
        final Map<String, Object> map = cache.get(key);
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
    @Lock(LockType.READ)
    public Set<ProcessRequestVO> getProcessRequests() {
        final Set<ProcessRequestVO> processRequests = new HashSet<>();
        final Iterator<Cache.Entry<String, Map<String, Object>>> cacheIterator = cache.iterator();
        while (cacheIterator.hasNext()) {
            final Cache.Entry<String, Map<String, Object>> cacheEntry = cacheIterator.next();
            final Map<String, Object> map = cacheEntry.getValue();
            if (!isAnErrorEntry(map) && isValidRequestToProcess(map)) {
                addProcessRequest(processRequests, map);
            }
        }
        return processRequests;
    }

    private boolean isAnErrorEntry(final Map<String, Object> map) {
        return map.containsKey(PMIC_ATT_SUBSCRIPTION_ID) || map.containsKey(ErrorNodeCacheAttributes.STORED_REQUESTS);
    }

    /**
     * Removes a ProcessRequest based on a key.
     *
     * @param key
     *            - Record Output Period InSeconds + Fully Distinguished Name Of Node + ProcessType
     */
    public void removeProcessRequest(final String key) {
        if (cache.remove(key)) {
            cacheSize.decrementAndGet();
        } else {
            logger.debug("{} could not remove from cache", key);
        }
    }

    /**
     * Removes ProcessRequest.
     *
     * @param requestVO
     *            - ProcessRequest
     */
    public void removeProcessRequest(final ProcessRequestVO requestVO) {
        final String key = getKeyFor(requestVO);
        removeProcessRequest(key);
    }

    /**
     * Returns the size of the cache.
     *
     * @return The size of the cache.
     */
    @Lock(LockType.READ)
    public int size() {
        return cacheSize.get();
    }

    /**
     * Retrieves all ProcessRequest for a particular ROP period.
     *
     * @param rop
     *            -int value for Record Output Period
     * @return {@code Set<ProcessRequestVO>} A list of ProcessRequest for a Record Output Period
     */
    @Lock(LockType.READ)
    public Set<ProcessRequestVO> getProcessRequestForRop(final Integer... rops) {
        if (rops.length == 0) {
            return getProcessRequests();
        }
        final Set<ProcessRequestVO> processRequests = new HashSet<>();
        final List<Integer> validRops = Arrays.asList(rops);
        final Iterator<Cache.Entry<String, Map<String, Object>>> cacheIterator = cache.iterator();
        while (cacheIterator.hasNext()) {
            final Cache.Entry<String, Map<String, Object>> cacheEntry = cacheIterator.next();
            final Map<String, Object> map = cacheEntry.getValue();
            if (isValidRequestToProcess(map)) {
                final int ropPeriod = getIntValue(ROP_PERIOD_ATTRIBUTE, map);
                if (validRops.contains(ropPeriod)) {
                    addProcessRequest(processRequests, map);
                }
            }
        }
        return processRequests;
    }

    /**
     * Retrieves all ProcessRequest for a particular node address.
     *
     * @param nodeAddress
     *            -node address
     * @return {@code Set<ProcessRequestVO>} A list of ProcessRequest for a Record Output Period
     */
    @Lock(LockType.READ)
    public Set<ProcessRequestVO> getProcessRequestForRop(final String nodeAddress) {
        final Set<ProcessRequestVO> processRequests = new HashSet<>();
        final Iterator<Cache.Entry<String, Map<String, Object>>> cacheIterator = cache.iterator();

        while (cacheIterator.hasNext()) {
            final Cache.Entry<String, Map<String, Object>> cacheEntry = cacheIterator.next();
            final Map<String, Object> map = cacheEntry.getValue();
            if (isValidRequestToProcess(map)) {
                final String nodeFdn = getStringValue(NODE_ADDRESS_ATTRIBUTE, map);
                if (nodeFdn.equalsIgnoreCase(nodeAddress)) {
                    addProcessRequest(processRequests, map);
                }
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
        logger.debug("Found cached request for {}-{}-{}", nodeAddress, ropPeriod, processType);
        final ProcessRequestVO processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(nodeAddress, ropPeriod, processType)
                .startTime(startTime).endTime(endTime).build();
        return processRequestVO.getSubscriptionType() != null ? processRequestVO : null;
    }

    /**
     * Generates the key used in the cache from the ProcessRequest object.
     *
     * @param requestVO
     *            - ProcessRequest
     * @return String
     */
    @Lock(LockType.READ)
    public String getKeyFor(final ProcessRequestVO requestVO) {
        return getKeyForRop(requestVO, requestVO.getRopPeriod());
    }

    /**
     * Generates the key used in the cache from the attributes in the ProcessRequest and the ROP period supplied. <br>
     * Return format is : ROP_FDN_PROCESSTYPE.
     *
     * @param requestVO
     *            - ProcessRequest
     * @param ropPeriod
     *            - ROP period in seconds
     * @return String
     */
    @Lock(LockType.READ)
    public String getKeyForRop(final ProcessRequestVO requestVO, final int ropPeriod) {
        final StringBuilder key = new StringBuilder();
        key.append(ropPeriod);
        key.append(DELIMITER);
        key.append(requestVO.getNodeAddress());
        key.append(DELIMITER);
        final ProcessType processType = ProcessType.valueOf(requestVO.getProcessType());
        key.append(processType.getSubscriptionType());
        return key.toString();
    }

    /**
     * Generates a map from the ProcessRequest object.
     *
     * @param requestVo
     *            - ProcessRequest
     * @return {@code Map<String, Object>}
     */
    private Map<String, Object> extractAttributesToMap(final ProcessRequestVO requestVo) {
        final Map<String, Object> map = new HashMap<>();
        map.put(NODE_ADDRESS_ATTRIBUTE, requestVo.getNodeAddress());
        map.put(PROCESS_TYPE_ATTRIBUTE, requestVo.getProcessType());
        map.put(ROP_PERIOD_ATTRIBUTE, requestVo.getRopPeriod());
        map.put(START_TIME_ATTRIBUTE, requestVo.getStartTime());
        map.put(END_TIME_ATTRIBUTE, requestVo.getEndTime());
        return map;
    }

    /**
     * Returns true if the cache contains an entry for the given nodeFdn
     *
     * @param nodeFdn
     *            - node FDN
     * @return - true if the cache has an entry for this node
     */
    public boolean containsKey(final String nodeFdn) {
        return cache.containsKey(nodeFdn);
    }

    /**
     * Adds an entry to the error node cache for the given nodeFdn
     *
     * @param nodeFdn
     *            - node FDN
     * @param entry
     *            - The map of attributes associated with this node
     */
    public void put(final String nodeFdn, final Map<String, Object> entry) {
        if (!isCacheAccessAllowed(nodeFdn)) {
            logger.warn("Entries for key {} not added to file collection active node list cache. Access not allowed", nodeFdn);
            return;
        }
        cache.put(nodeFdn, entry);
    }

    /**
     * Removes an entry from the error node cache for the given nodeFn
     *
     * @param nodeFdn
     *            - node FDN
     * @return returns true if entry was successfully removed from the cache
     */
    public boolean remove(final String nodeFdn) {
        return cache.remove(nodeFdn);
    }

    /**
     * Gets an entry from the error node cache for the given nodeFdn
     *
     * @param nodeFdn
     *            - node FDN
     * @return - The map of attributes associated with this node
     */
    public Map<String, Object> get(final String nodeFdn) {
        return cache.get(nodeFdn);
    }

    private boolean isFileCollectionEnabled(final ProcessRequestVO requestVO) {
        final String key = requestVO.getNodeAddress();
        return nodeService.isPmFunctionEnabled(key) && nodeService.isFileCollectionEnabled(key);
    }

    private boolean isCacheAccessAllowed(final String key) {
        return ErrorNodeCacheAttributes.STORED_REQUESTS_KEY.equals(key) || !nodeService.isPmFunctionEnabled(key)
                || nodeService.isFileCollectionEnabled(key);
    }

    private boolean isValidRequestToProcess(final Map<String, Object> map) {
        return !(map.containsKey(ErrorNodeCacheAttributes.STORED_REQUESTS)
                || null == getStringValue(PROCESS_TYPE_ATTRIBUTE, map)
                || null == getStringValue(NODE_ADDRESS_ATTRIBUTE, map));
    }
}
