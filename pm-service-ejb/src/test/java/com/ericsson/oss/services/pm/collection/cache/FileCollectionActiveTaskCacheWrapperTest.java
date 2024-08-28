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

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.cache.Cache;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;

import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.cache.model.value.ProcessType;
import com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler.ErrorNodeCacheAttributes;

public class FileCollectionActiveTaskCacheWrapperTest {

    private static final String DELIMITER = "_";
    private static final String NODE_ADDRESS_ATTRIBUTE = "nodeAddress";
    FileCollectionActiveTaskCacheWrapper cacheWrapper;
    @Mock
    AtomicInteger cacheSize;
    @Mock
    NodeService nodeService;
    Cache<String, Map<String, Object>> cache;

    @Before
    public void beforeTests() {
        cacheWrapper = spy(new FileCollectionActiveTaskCacheWrapper());
        cache = spy(new MockedCache<String, Map<String, Object>>());
        MockitoAnnotations.initMocks(this);
        Whitebox.setInternalState(cacheWrapper, "cache", cache);
        Whitebox.setInternalState(cacheWrapper, "cacheSize", cacheSize);
        Whitebox.setInternalState(cacheWrapper, "nodeService", nodeService);
        when(nodeService.isFileCollectionEnabled(Matchers.anyString())).thenReturn(true);
        when(nodeService.isPmFunctionEnabled(Matchers.anyString())).thenReturn(true);
    }

    @Test
    public void addNewProcessRequestCacheContainEntry() {
        final String node1Fdn = "NetworkElement=LTEERBS0001";
        final int ropPeriodInSeconds = 15 * 60;

        final String key = ropPeriodInSeconds + DELIMITER + node1Fdn + DELIMITER + ProcessType.STATS.getSubscriptionType().name();

        final ProcessRequestVO processRequestVO =
                new ProcessRequestVO.ProcessRequestVOBuilder(node1Fdn, ropPeriodInSeconds, ProcessType.STATS.name())
                        .build();
        cacheWrapper.addProcessRequest(processRequestVO);
        assertTrue(cache.containsKey(key));
        assertEquals(cacheWrapper.size(), 1);
    }

    @Test
    public void ignoreNewProcessRequestIfSubscriptionTypeIsNull() {
        final String node1Fdn = "NetworkElement=LTEERBS0001";
        final int ropPeriodInSeconds = 15 * 60;
        final String key = ropPeriodInSeconds + DELIMITER + node1Fdn + DELIMITER + ProcessType.OTHER.name();
        assertTrue(cacheWrapper.getProcessRequest(key) == null);
    }

    @Test
    public void addTheSameProcessRequestTwiceCacheContainOneEntry() {
        final String node1Fdn = "NetworkElement=LTEERBS0001";
        final int ropPeriodInSeconds = 15 * 60;
        final String key = ropPeriodInSeconds + DELIMITER + node1Fdn + DELIMITER + ProcessType.STATS.getSubscriptionType().name();

        final ProcessRequestVO processRequestVO =
                new ProcessRequestVO.ProcessRequestVOBuilder(node1Fdn, ropPeriodInSeconds, ProcessType.STATS.name())
                        .build();
        cacheWrapper.addProcessRequest(processRequestVO);
        cacheWrapper.addProcessRequest(processRequestVO);
        assertTrue(cache.containsKey(key));
        assertEquals(cacheWrapper.size(), 1);
    }

    @Test
    public void updateExistingProcessRequest() {
        final String node1Fdn = "NetworkElement=LTEERBS0001";
        final int ropPeriodInSeconds = 15 * 60;
        final String processTypeAsString = ProcessType.STATS.name();
        final long startTime = 1434469180162L;
        final long newStartTime = 1434469180162L + 1;
        final String key = ropPeriodInSeconds + DELIMITER + node1Fdn + DELIMITER + ProcessType.STATS.getSubscriptionType().name();

        ProcessRequestVO processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(node1Fdn, ropPeriodInSeconds, processTypeAsString)
                .startTime(startTime).build();
        cacheWrapper.addProcessRequest(processRequestVO);
        processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(node1Fdn, ropPeriodInSeconds, processTypeAsString).startTime(newStartTime)
                .build();
        cacheWrapper.updateProcessRequest(processRequestVO);
        final ProcessRequestVO cacheProcessRequestVO = cacheWrapper.getProcessRequest(key);
        assertTrue(cache.containsKey(key));
        assertTrue(cacheProcessRequestVO.getStartTime() == newStartTime);
        assertEquals(cacheWrapper.size(), 1);
    }

    @Test
    public void getProcessRequests() {
        final String node1Fdn = "NetworkElement=LTEERBS0001";
        final String node2Fdn = "NetworkElement=LTEERBS0002";
        final int ropPeriodInSeconds = 15 * 60;
        final String processTypeAsString = ProcessType.NORMAL_PRIORITY_CELLTRACE.name();
        final long startTime = 1434469180162L;
        final String key1 = ropPeriodInSeconds + DELIMITER + node1Fdn + DELIMITER
                + ProcessType.NORMAL_PRIORITY_CELLTRACE.getSubscriptionType().name();
        final String key2 = ropPeriodInSeconds + DELIMITER + node2Fdn + DELIMITER
                + ProcessType.NORMAL_PRIORITY_CELLTRACE.getSubscriptionType().name();

        final ProcessRequestVO processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(node1Fdn, ropPeriodInSeconds, processTypeAsString)
                .startTime(startTime).build();
        final ProcessRequestVO processRequestVO1 = new ProcessRequestVO.ProcessRequestVOBuilder(node2Fdn, ropPeriodInSeconds, processTypeAsString)
                .startTime(startTime).build();
        cacheWrapper.addProcessRequest(processRequestVO);
        cacheWrapper.addProcessRequest(processRequestVO1);

        assertEquals(cacheWrapper.getProcessRequests().size(), 2);
        assertTrue(cache.containsKey(key1));
        assertTrue(cache.containsKey(key2));
    }

    @Test
    public void removeProcessRequest() {
        final String node1Fdn = "NetworkElement=LTEERBS0001";
        final int ropPeriodInSeconds = 15 * 60;
        final String key = ropPeriodInSeconds + DELIMITER + node1Fdn + DELIMITER + ProcessType.STATS.getSubscriptionType().name();
        final ProcessRequestVO processRequestVO =
                new ProcessRequestVO.ProcessRequestVOBuilder(node1Fdn, ropPeriodInSeconds, ProcessType.STATS.name())
                        .build();
        cacheWrapper.addProcessRequest(processRequestVO);
        cacheWrapper.removeProcessRequest(processRequestVO);
        assertFalse(cache.containsKey(key));
        assertEquals(cacheWrapper.size(), 0);
    }

    @Test
    public void removeProcessRequestWithKey() {
        final String node1Fdn = "NetworkElement=LTEERBS0001";
        final int ropPeriodInSeconds = 15 * 60;
        final String key = ropPeriodInSeconds + DELIMITER + node1Fdn + DELIMITER + ProcessType.STATS.getSubscriptionType().name();
        final ProcessRequestVO processRequestVO =
                new ProcessRequestVO.ProcessRequestVOBuilder(node1Fdn, ropPeriodInSeconds, ProcessType.STATS.name())
                        .build();
        cacheWrapper.addProcessRequest(processRequestVO);
        cacheWrapper.removeProcessRequest(key);
        assertFalse(cache.containsKey(key));
        assertEquals(cacheWrapper.size(), 0);
    }

    @Test
    public void addMultipleProcessRequestCacheContainEntries() {
        final String node1Fdn = "NetworkElement=LTEERBS0001";
        final String node2Fdn = "NetworkElement=LTEERBS0002";
        final String node3Fdn = "NetworkElement=LTEERBS0003";
        final int ropPeriodInSeconds = 15 * 60;
        final String subscriptionType = ProcessType.STATS.getSubscriptionType().name();
        final String key1 = ropPeriodInSeconds + DELIMITER + node1Fdn + DELIMITER + subscriptionType;
        final String key2 = ropPeriodInSeconds + DELIMITER + node2Fdn + DELIMITER + subscriptionType;
        final String key3 = ropPeriodInSeconds + DELIMITER + node3Fdn + DELIMITER + subscriptionType;
        final ProcessRequestVO processRequestVO1 = new ProcessRequestVO.ProcessRequestVOBuilder(node1Fdn, ropPeriodInSeconds,
                ProcessType.STATS.name()).build();
        final ProcessRequestVO processRequestVO2 = new ProcessRequestVO.ProcessRequestVOBuilder(node2Fdn, ropPeriodInSeconds,
                ProcessType.STATS.name()).build();
        final ProcessRequestVO processRequestVO3 = new ProcessRequestVO.ProcessRequestVOBuilder(node3Fdn, ropPeriodInSeconds,
                ProcessType.STATS.name()).build();
        final ProcessRequestVO processRequestVO4 = new ProcessRequestVO.ProcessRequestVOBuilder(node3Fdn, ropPeriodInSeconds,
                ProcessType.PREDEF_STATS.name()).build();

        cacheWrapper.addProcessRequest(processRequestVO1);
        cacheWrapper.addProcessRequest(processRequestVO2);
        cacheWrapper.addProcessRequest(processRequestVO3);
        cacheWrapper.addProcessRequest(processRequestVO4);
        assertTrue(cache.containsKey(key1));
        assertTrue(cache.containsKey(key2));
        assertTrue(cache.containsKey(key3));
        assertEquals(cacheWrapper.size(), 3);
    }

    @Test
    public void addStartupFilecollectionRequest() {
        final Map<String, Object> attributes = new HashMap<>();
        final Map<String, Object> entry = new HashMap<>();
        final Set<Map<String, Object>> requestSet = new HashSet<>();
        requestSet.add(attributes);
        entry.put(ErrorNodeCacheAttributes.STORED_REQUESTS, requestSet);
        cacheWrapper.put(ErrorNodeCacheAttributes.STORED_REQUESTS_KEY, entry);
        assertEquals(cacheWrapper.getProcessRequestForRop(0).size(), 0);
    }

    @Test
    public void removeProcessRequestWhenNodeAddressNotExist() {
        final String nodeFdn = "NetworkElement=RBS";
        final Set<ProcessRequestVO> processRequest = cacheWrapper.getProcessRequestForRop(nodeFdn);
        assertEquals(processRequest.size(), 0);
        assertFalse(cache.containsKey(NODE_ADDRESS_ATTRIBUTE));
    }

}
