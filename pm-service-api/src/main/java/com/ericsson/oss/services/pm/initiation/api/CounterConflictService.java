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

package com.ericsson.oss.services.pm.initiation.api;

import java.util.Collection;
import java.util.Map;

import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.services.pm.initiation.rest.response.ConflictingNodeCounterInfo;
import com.ericsson.oss.services.pm.services.exception.CannotGetConflictingCountersException;

/**
 * Interface for finding Statistical subscription counter conflicts.
 */
public interface CounterConflictService {

    /**
     * Checks whether at least one counter is already active on the same node.
     *
     * @param subscription
     *         - the {@link StatisticalSubscription}
     *
     * @return - True or false.
     * @throws CannotGetConflictingCountersException
     *         - If there is an exception that prevents extraction of conflicting counters.
     */
    boolean hasAnyCounterConflict(final StatisticalSubscription subscription)
            throws CannotGetConflictingCountersException;

    /**
     * Provides Detailed Report for counters conflicts. The returned value will be a csv formatted String containing full node and counter conflicts
     * details. The String is then passed as byte-stream to the UI via rest I/F. The UI will then allow the user to download it as a csv file
     *
     * @param subscription
     *         - the {@link StatisticalSubscription} being activated
     *
     * @return - Conflicting Counters Report csv formatted String
     * @throws CannotGetConflictingCountersException
     *         - If there is an exception that prevents extraction of conflicting counters.
     */
    String getCounterConflictsReport(final StatisticalSubscription subscription)
            throws CannotGetConflictingCountersException;

    /**
     * Provide list of conflicting Subscriptions for every counter selected on the subscription being activated
     *
     * @param subscription
     *         - the {@link StatisticalSubscription} being activated
     *
     * @return - Conflicting Subscriptions for Counters Map: Keys contain conflicting subscription names, Values contain conflicting counters in the
     * form group:counter1,counter2....
     * @throws CannotGetConflictingCountersException
     *         - If there is an exception that prevents extraction of conflicting counters.
     */
    Map<String, Collection<String>> getConflictingSubscriptionsForCounters(
            final StatisticalSubscription subscription)
            throws CannotGetConflictingCountersException;

    /**
     * Get counter conflicts for subscription's counters.
     *
     * @param subscription
     *         - Subscription ID
     *
     * @return - {@link ConflictingNodeCounterInfo}
     * @throws CannotGetConflictingCountersException
     *         - If there is an exception that prevents extraction of conflicting counters.
     */
    ConflictingNodeCounterInfo getConflictingCountersInSubscription(
            final StatisticalSubscription subscription)
            throws CannotGetConflictingCountersException;
}
