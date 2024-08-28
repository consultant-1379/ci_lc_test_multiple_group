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

package com.ericsson.oss.services.pm.initiation.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectCreatedEvent;
import com.ericsson.oss.pmic.api.cache.PmFunctionData;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;

public class DpsPmFunctionCreatedNotificationListenerTest {

    @InjectMocks
    DpsPmFunctionCreatedNotificationListener listener = new DpsPmFunctionCreatedNotificationListener();

    @Mock
    private NodeService nodeService;

    @Mock
    private PmFunctionEnabledWrapper pmFunctionCache;

    @Mock
    private Logger logger;

    @BeforeTest
    public void setup() {
        MockitoAnnotations.initMocks(this);
        try {
            final Node node = mock(Node.class);
            when(node.getNeType()).thenReturn("ERBS");
            when(nodeService.findOneByFdn("NetworkElement=LTE01ERBS00001")).thenReturn(node);
        } catch (final DataAccessException ex) {
            logger.error("Unable to mock objects,Exception {}", ex);
        }
    }

    @Test
    public void shouldAddEntryToPmFunctionCacheIfPmFunctionCreateIsReceivedWithPmEnabledTrue() {
        when(pmFunctionCache.containsFdn("NetworkElement=LTE01ERBS00001")).thenReturn(true);
        final Map<String, Object> attributes = new HashMap<>();
        attributes.put("pmEnabled", true);
        attributes.put("fileCollectionState", "ENABLED");
        attributes.put("scannerMasterState", "ENABLED");
        attributes.put("neConfigurationManagerState", "ENABLED");
        listener.onEvent(new DpsObjectCreatedEvent("namespace", "type", "version", 123L, "NetworkElement=LTE01ERBS00001,PmFunction=1",
                "theBucketName", true, attributes));
        verify(pmFunctionCache, times(1)).addEntry(Matchers.anyString(), (PmFunctionData) Matchers.any());
    }

    @Test
    public void shouldNotAddToPmFunctionCacheIfEntryAlreadyExists() {
        when(pmFunctionCache.containsFdn("NetworkElement=LTE01ERBS00001")).thenReturn(true);
        final Map<String, Object> attributes = new HashMap<>();
        attributes.put("pmEnabled", false);
        attributes.put("fileCollectionState", "ENABLED");
        attributes.put("scannerMasterState", "ENABLED");
        attributes.put("neConfigurationManagerState", "ENABLED");
        listener.onEvent(new DpsObjectCreatedEvent("namespace", "type", "version", 123L, "NetworkElement=LTE01ERBS00001,PmFunction=1",
                "theBucketName", true, attributes));
        final PmFunctionData pmFunctionData = new PmFunctionData(true, null, null, null);
        verify(pmFunctionCache, times(0)).addEntry("NetworkElement=LTE01ERBS00001", pmFunctionData);
    }

    @Test
    public void shouldAddEntryToPmFunctionCacheIfPmFunctionCreateIsReceivedWithPmEnabledFalse() {
        when(pmFunctionCache.containsFdn("NetworkElement=LTE01ERBS00001")).thenReturn(false);
        final Map<String, Object> attributes = new HashMap<>();
        attributes.put("pmEnabled", true);
        attributes.put("fileCollectionState", "ENABLED");
        attributes.put("scannerMasterState", "ENABLED");
        attributes.put("neConfigurationManagerState", "ENABLED");
        listener.onEvent(new DpsObjectCreatedEvent("namespace", "type", "version", 123L, "NetworkElement=LTE01ERBS00001,PmFunction=1",
                "theBucketName", true, attributes));
        verify(pmFunctionCache, times(1)).addEntry(Matchers.anyString(), (PmFunctionData) Matchers.any());
    }
}
