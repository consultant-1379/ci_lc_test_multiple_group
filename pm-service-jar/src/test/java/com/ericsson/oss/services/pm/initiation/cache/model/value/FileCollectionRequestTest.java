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
package com.ericsson.oss.services.pm.initiation.cache.model.value;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;


public class FileCollectionRequestTest {

    private static final String KEY = "STATS";
    private FileCollectionRequest fileCollectionRequest;
    private int ropPeriod = 900;
    private long requestStopTime = 400;
    private long requestStartTime = 100;

    @Before
    public void setup() {
        final String nodeAddress = "NetworkElement=Test0001";
        fileCollectionRequest = new FileCollectionRequest(nodeAddress, ropPeriod);
        fileCollectionRequest.addProcessType(KEY, requestStartTime, requestStopTime);
    }

    @Test
    public void shouldAddProcessTypeToFileCollectionRequest() {
        setup();
        final Set<String> result = fileCollectionRequest.getProcessTypes();
        assertNotNull(result);
        assertEquals(result.size(), 1);
    }

    @Test
    public void shouldAddProcessTypeToFileCollectionRequestAndRemoveProcessType() {
        setup();
        fileCollectionRequest.removeProcessType(KEY);
        final Set<String> fcrProcessTypes = fileCollectionRequest.getProcessTypes();
        assertEquals(fcrProcessTypes.size(), 0);
    }

    @Test
    public void shouldAddProcessTypeToFileCollectionRequestAndRemoveExpiredProcessType() {
        setup();
        final long expiredTime = 500;
        final boolean result = fileCollectionRequest.removeExpiredProcessTypes(expiredTime);
        final Set<String> fcrProcessTypes = fileCollectionRequest.getProcessTypes();
        assertTrue(result);
        assertEquals(fcrProcessTypes.size(), 0);
    }

    @Test
    public void shouldAddProcessTypeToFileCollectionRequestAndNotRemoveExpiredProcessTypeIfEexpiredTimeIsNotGreaterThanZero() {
        setup();
        final long expiredTime = 0;
        final boolean result = fileCollectionRequest.removeExpiredProcessTypes(expiredTime);
        final Set<String> fcrProcessTypes = fileCollectionRequest.getProcessTypes();
        assertFalse(result);
        assertEquals(fcrProcessTypes.size(), 1);
    }

    @Test
    public void
    shouldAddProcessTypeToFileCollectionRequestAndNotRemoveExpiredProcessTypeIfExpiredTimeIsNotLessThanRequestStopTime() {
        setup();
        final long expiredTime = 400;
        final boolean result = fileCollectionRequest.removeExpiredProcessTypes(expiredTime);
        final Set<String> fcrProcessTypes = fileCollectionRequest.getProcessTypes();
        assertFalse(result);
        assertEquals(fcrProcessTypes.size(), 1);
    }
}
