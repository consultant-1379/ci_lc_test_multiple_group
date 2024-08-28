/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.ejb;

import static org.testng.AssertJUnit.assertEquals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.ericsson.oss.services.pm.initiation.cache.api.ActiveNode;
import com.ericsson.oss.services.pm.initiation.cache.api.Counter;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ActiveNodeTest {

    private ActiveNode objectUnderTest;

    @Test
    public void getAssociatedCountersForSubscriptionExpectCountersForGivenSubcriptionId() {
        final Map<String, Set<String>> moClassToCounter = getMoClassToCountersMapforGroup1();
        final Map<Counter, Set<String>> counterToSubscriptionSet = getCounterToSubscriptionId(moClassToCounter, "123");
        objectUnderTest = new ActiveNode("node1", counterToSubscriptionSet);

        final Set<Counter> expextedCounters = getCounterSet(moClassToCounter);
        assertEquals(expextedCounters, objectUnderTest.getAssociatedCountersForSubscription("123"));
    }

    @Test
    public void getAssociatedSubscriptionsForCounterExpectCorrectSubscriptionIdsForGivenCounter() {

        final Map<String, Set<String>> moClassToCounter = getMoClassToCountersMapforGroup1();
        final Map<Counter, Set<String>> counterToSubscriptionSet = getCounterToSubscriptionId(moClassToCounter, "123");
        objectUnderTest = new ActiveNode("node1", counterToSubscriptionSet);
        final Counter counter = getCounter("GeranCellRelation", "pmHoPrepSucc");

        Set<String> expextedSubscription = getSubscriptionSet("123");
        Set<String> actualSubscriptionIds = objectUnderTest.getAssociatedSubscriptionsForCounter(counter);

        assertEquals(expextedSubscription, actualSubscriptionIds);

        final Set<String> subscriptionIds = counterToSubscriptionSet.get(counter);
        subscriptionIds.add("124");

        expextedSubscription = getSubscriptionSet("123", "124");
        actualSubscriptionIds = objectUnderTest.getAssociatedSubscriptionsForCounter(counter);
        assertEquals(expextedSubscription, actualSubscriptionIds);

        final Set<Counter> expectedCounterSet = new HashSet<>();
        expectedCounterSet.add(counter);
        assertEquals(expectedCounterSet, objectUnderTest.getAssociatedCountersForSubscription("124"));
    }

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(ActiveNode.class).verify();
    }

    private Map<Counter, Set<String>> getCounterToSubscriptionId(final Map<String, Set<String>> moClassToCounterMap,
                                                                 final String... subscriptionIds) {
        final Map<Counter, Set<String>> counterToSubscriptionSet = new HashMap<>();

        final Set<Counter> counters = getCounterSet(moClassToCounterMap);
        for (final Counter counter : counters) {
            final Set<String> subscriptionSet = getSubscriptionSet(subscriptionIds);
            counterToSubscriptionSet.put(counter, subscriptionSet);
        }
        return counterToSubscriptionSet;
    }

    private Set<String> getSubscriptionSet(final String... subscriptionIds) {
        final Set<String> subscriptionSet = new HashSet<>();

        for (final String subscriptionId : subscriptionIds) {
            subscriptionSet.add(subscriptionId);
        }
        return subscriptionSet;
    }

    private Set<Counter> getCounterSet(final Map<String, Set<String>> moClassToCounter) {
        final Set<Counter> counterSet = new HashSet<>();
        for (final Map.Entry<String, Set<String>> entry : moClassToCounter.entrySet()) {
            final String moClass = entry.getKey();
            final Set<String> counters = entry.getValue();
            for (final String counterName : counters) {
                counterSet.add(getCounter(moClass, counterName));
            }
        }
        return counterSet;
    }

    private Counter getCounter(final String moClass, final String counterName) {
        return new Counter(moClass, counterName);
    }

    private Map<String, Set<String>> getMoClassToCountersMapforGroup1() {
        final Map<String, Set<String>> moClassToCounter = new HashMap<>();
        final String moClass = "GeranCellRelation";
        final Set<String> counters = new HashSet<>();
        counters.add("pmHoPrepSucc");
        counters.add("pmHoPrepAtt");
        counters.add("pmHoExeSucc");
        counters.add("pmHoExeAtt");
        moClassToCounter.put(moClass, counters);
        return moClassToCounter;
    }
}
