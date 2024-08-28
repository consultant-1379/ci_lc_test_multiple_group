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

import static org.testng.Assert.assertEquals;

import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;

public class ConflictingCounterGroupTest {

    private static final String GROUP_NAME = "any-group-name";
    private static final String ANOTHER_GROUP_NAME = "another-group-name";
    private static final HashSet<String> COUNTER_NAMES = new HashSet<>();
    private static final HashSet<String> ANOTHER_COUNTER_NAMES = new HashSet<>();
    private ConflictingCounterGroup conflictingCounterGroup;

    @Before
    public void setUp() {
        conflictingCounterGroup = new ConflictingCounterGroup(GROUP_NAME, COUNTER_NAMES);
    }

    @Test
    public void conflictingCounterGroupGetGroupName() {
        assertEquals(conflictingCounterGroup.getGroupName(), GROUP_NAME);
    }

    @Test
    public void conflictingCounterGroupSetGroupName() {
        conflictingCounterGroup.setGroupName(ANOTHER_GROUP_NAME);
        assertEquals(conflictingCounterGroup.getGroupName(), ANOTHER_GROUP_NAME);
    }

    @Test
    public void conflictingCounterGroupGetEventCounterNames() {
        assertEquals(conflictingCounterGroup.getEventCounterNames(), COUNTER_NAMES);
    }

    @Test
    public void conflictingCounterGroupSetEventCounterNames() {
        conflictingCounterGroup.setEventCounterNames(ANOTHER_COUNTER_NAMES);
        assertEquals(conflictingCounterGroup.getEventCounterNames(), ANOTHER_COUNTER_NAMES);
    }
}
