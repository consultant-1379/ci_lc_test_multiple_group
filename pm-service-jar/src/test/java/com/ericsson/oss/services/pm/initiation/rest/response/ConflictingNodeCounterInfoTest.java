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
package com.ericsson.oss.services.pm.initiation.rest.response;

import static java.util.Collections.unmodifiableList;

import static org.testng.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;


public class ConflictingNodeCounterInfoTest {

    private static final String SUBSCRIPTION_ID = "any-subscription-id";
    private static final String ANOTHER_SUBSCRIPTION_ID = "another-subscription-id";
    private static final List<String> NODES = unmodifiableList(new ArrayList<String>());
    private static final List<String> ANOTHER_NODES = unmodifiableList(new ArrayList<String>());
    private static final List<ConflictingCounterGroup> COUNTER_EVENT_INFO = unmodifiableList(new ArrayList<ConflictingCounterGroup>());
    private static final List<ConflictingCounterGroup> ANOTHER_COUNTER_EVENT_INFO = unmodifiableList(new ArrayList<ConflictingCounterGroup>());

    private ConflictingNodeCounterInfo conflictingNodeCounterInfo;

    @Before
    public void setUp() {
        conflictingNodeCounterInfo = new ConflictingNodeCounterInfo(SUBSCRIPTION_ID, NODES, COUNTER_EVENT_INFO);
    }

    @Test
    public void conflictingNodeCounterInfoGetSubscriptionId() {
        assertEquals(conflictingNodeCounterInfo.getSubscriptionId(), SUBSCRIPTION_ID);
    }

    @Test
    public void conflictingNodeCounterInfoSetSubscriptionId() {
        conflictingNodeCounterInfo.setSubscriptionId(ANOTHER_SUBSCRIPTION_ID);
        assertEquals(conflictingNodeCounterInfo.getSubscriptionId(), ANOTHER_SUBSCRIPTION_ID);
    }

    @Test
    public void conflictingNodeCounterInfoGetCounterEventInfo() {
        assertEquals(conflictingNodeCounterInfo.getCounterEventInfo(), COUNTER_EVENT_INFO);
    }

    @Test
    public void conflictingNodeCounterInfoSetCounterEventInfo() {
        conflictingNodeCounterInfo.setCounterEventInfo(ANOTHER_COUNTER_EVENT_INFO);
        assertEquals(conflictingNodeCounterInfo.getCounterEventInfo(), ANOTHER_COUNTER_EVENT_INFO);
    }

    @Test
    public void conflictingNodeCounterInfoGetNodes() {
        assertEquals(conflictingNodeCounterInfo.getNodes(), NODES);
    }

    @Test
    public void conflictingNodeCounterInfoSetNodes() {
        conflictingNodeCounterInfo.setNodes(ANOTHER_NODES);
        assertEquals(conflictingNodeCounterInfo.getNodes(), ANOTHER_NODES);
    }
}
