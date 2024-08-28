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
package com.ericsson.oss.service.pm.initiation.cache.data;

import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.ericsson.oss.services.pm.initiation.cache.data.InitiationTracker;

/**
 * Created by ezdecsa on 17/11/2015.
 */
public class InitiationTrackerTest {

    InitiationTracker tracker;

    @Before
    public void setUp() {
        final String subId = "123";
        final String adminState = "ACTIVATING";
        final long timestamp = System.currentTimeMillis();
        final Map<String, String> nodesToBeActivated = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            nodesToBeActivated.put("NetworkElement=" + i, "ERBS");
        }
        tracker = new InitiationTracker(subId, adminState, timestamp, nodesToBeActivated, null, null);
    }

    @Test
    public void haveAllNotificationsBeenReceived() {
        tracker.incrementReceivedNotifications("NetworkElement=1");
        tracker.incrementReceivedNotifications("NetworkElement=2");
        assertFalse(tracker.haveAllNotificationsBeenReceived());
        tracker.incrementReceivedNotifications("NetworkElement=0");
        assertTrue(tracker.haveAllNotificationsBeenReceived());
    }
}
