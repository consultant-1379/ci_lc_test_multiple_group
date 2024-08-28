/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2019
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.collection.cache;

import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.cache.Cache;
import javax.cache.Cache.Entry;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;

import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;

public class FileCollectionScheduledRecoveryCacheWrapperTest {

    private FileCollectionScheduledRecoveryCacheWrapper objectUnderTest;

    @Mock
    private Cache<String, Map<String, Object>> mockCache;

    @Mock
    private Iterator<Cache.Entry<String, Map<String, Object>>> mockCacheIterator;

    @Mock
    private Cache.Entry<String, Map<String, Object>> cacheEntry;

    @Before
    public void setUp() {
        objectUnderTest = new FileCollectionScheduledRecoveryCacheWrapper();
        MockitoAnnotations.initMocks(this);
        setUpMockCacheEntry();
        setUpMockCacheIterator();
        setUpMockCache();
        Whitebox.setInternalState(objectUnderTest, "cache", mockCache);
    }

    @Test
    public void initExpectSuccessfullCacheInitiation() {
        objectUnderTest.init();
        assertEquals(1, objectUnderTest.size());
    }

    @Test
    public void addProcessRequestExpectProcessRequestTobeAdded() {
        objectUnderTest.init();
        final ProcessRequestVO prcoessRequest = new ProcessRequestVO.ProcessRequestVOBuilder("1.1.1.1", 2, "STATS").build();
        when(mockCache.containsKey(anyString())).thenReturn(false);
        doNothing().when(mockCache).put(anyString(), anyMapOf(String.class, Object.class));
        objectUnderTest.addProcessRequest(prcoessRequest);
        assertEquals(2, objectUnderTest.size());
    }

    @Test
    public void getProcessRequestForRopExpectRightPreocessRequestSet() {
        final Set<ProcessRequestVO> processRequestSet = objectUnderTest.getProcessRequestForRop(900);
        assertEquals(1, processRequestSet.size());
    }

    @Test
    public void removeProcessRequestExpectSucessFullRemoval() {
        objectUnderTest.init();

        when(mockCache.remove(anyString())).thenReturn(true);
        objectUnderTest.removeProcessRequest("dummyKey");
        verify(mockCache, timeout(1)).remove(anyString());

        assertEquals(0, objectUnderTest.size());
    }

    private void setUpMockCacheEntry() {
        when(cacheEntry.getValue()).thenReturn(getProcessRequestVOMap());
    }

    private Map<String, Object> getProcessRequestVOMap() {
        final Map<String, Object> processRequestMap = new HashMap<>();
        processRequestMap.put("nodeAddress", "192.168.0.0");
        processRequestMap.put("ropPeriod", 900);
        processRequestMap.put("processType", "STATS");
        processRequestMap.put("startTime", 12345678765L);
        processRequestMap.put("endTime", 234567887654L);
        return processRequestMap;
    }

    private void setUpMockCache() {
        when(mockCache.iterator()).thenReturn(getMockCacheIterator());
    }

    private Iterator<Entry<String, Map<String, Object>>> getMockCacheIterator() {
        return mockCacheIterator;
    }

    private void setUpMockCacheIterator() {
        when(mockCacheIterator.hasNext()).thenReturn(true, false);
        when(mockCacheIterator.next()).thenReturn(cacheEntry);
    }
}
