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
package com.ericsson.oss.services.pm.instrumentation;

import static org.testng.Assert.assertEquals;

import static com.ericsson.oss.services.pm.instrumentation.ScannerPollingInstrumentation.SCANNER_POLLING_DURATION_PER_NODE;
import static com.ericsson.oss.services.pm.instrumentation.ScannerPollingInstrumentation.SCANNER_POLLING_DURATION_PER_ROP;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

public class ScannerPollingInstrumentationTest {

    ScannerPollingInstrumentation scannerPollingInstrumentation;

    MetricRegistry metricRegistry;
    Map<String, Deque<Timer.Context>> tasks;

    @Before
    public void beforeMethod() {
        scannerPollingInstrumentation = new ScannerPollingInstrumentation();
        metricRegistry = new MetricRegistry();
        tasks = new HashMap<>();
        Whitebox.setInternalState(scannerPollingInstrumentation, "metricRegistry", metricRegistry);
        Whitebox.setInternalState(scannerPollingInstrumentation, "tasks", tasks);
    }

    @Test
    public void testScannerPollingDurationForOneRop() {
        final Set<String> fdns = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            fdns.add("LTE01ERBS00" + i);
        }
        scannerPollingInstrumentation.scannerPollingTaskStarted(fdns);
        for (int i = 0; i < 10; i++) {
            final String nodeFdn = "LTE01ERBS00" + i;
            scannerPollingInstrumentation.scannerPollingTaskEnded(nodeFdn);
            assertEquals(tasks.get(SCANNER_POLLING_DURATION_PER_NODE + "|" + "nodeFdn = " + nodeFdn).size(), 0);
        }
        assertEquals(metricRegistry.timer(SCANNER_POLLING_DURATION_PER_NODE).getCount(), 10);
        assertEquals(metricRegistry.timer(SCANNER_POLLING_DURATION_PER_ROP).getCount(), 1);
    }

    @Test
    public void testScannerPollingDurationForTwoRops() {
        // create a template fdns hash set
        final Collection<String> fdns = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            fdns.add("LTE01ERBS00" + i);
        }

        Set<String> newFdns = new HashSet<>(fdns);
        scannerPollingInstrumentation.scannerPollingTaskStarted(newFdns);
        // process only 99 nodes from 100
        for (int i = 0; i < 99; i++) {
            final String nodeFdn = "LTE01ERBS00" + i;
            scannerPollingInstrumentation.scannerPollingTaskEnded(nodeFdn);
            assertEquals(tasks.get(SCANNER_POLLING_DURATION_PER_NODE + "|" + "nodeFdn = " + nodeFdn).size(), 0);
        }
        assertEquals(metricRegistry.timer(SCANNER_POLLING_DURATION_PER_ROP).getCount(), 0);
        assertEquals(metricRegistry.timer(SCANNER_POLLING_DURATION_PER_NODE).getCount(), 99);

        // second poll
        newFdns = new HashSet<>(fdns);
        scannerPollingInstrumentation.scannerPollingTaskStarted(newFdns);
        scannerPollingInstrumentation.scannerPollingTaskEnded("LTE01ERBS00" + 99); // end last node poll from first batch
        assertEquals(metricRegistry.timer(SCANNER_POLLING_DURATION_PER_ROP).getCount(), 1);
        // process next 100 nodes
        for (int i = 0; i < 100; i++) {
            final String nodeFdn = "LTE01ERBS00" + i;
            scannerPollingInstrumentation.scannerPollingTaskEnded(nodeFdn);
            assertEquals(tasks.get(SCANNER_POLLING_DURATION_PER_NODE + "|" + "nodeFdn = " + nodeFdn).size(), 0);
        }
        assertEquals(metricRegistry.timer(SCANNER_POLLING_DURATION_PER_ROP).getCount(), 2);
        assertEquals(metricRegistry.timer(SCANNER_POLLING_DURATION_PER_NODE).getCount(), 200);
    }

}
