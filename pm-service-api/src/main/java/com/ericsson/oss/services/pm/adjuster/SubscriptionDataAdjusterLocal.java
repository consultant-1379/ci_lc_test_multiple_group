/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.adjuster;

import java.util.List;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventType;

/**
 * SubscriptionDataAdjuster interface.
 *
 * @param <T
 *         extends Subscription>
 *         - Subscription Object
 */
public interface SubscriptionDataAdjusterLocal<T extends Subscription> {

    /**
     * Update Counters or Events of a subscription depending on the available counters for the nodes of the given subscription.
     * <b>NOTE:</b> This does not update the subscription in DPS, only the subscription object passed as argument.
     *
     * @param subscription
     *         - subscription
     */
    void correctSubscriptionData(final T subscription);

    /**
     * Update Counters or Events of a subscription depending on the available counters for the nodes of the given subscription.
     * <b>NOTE:</b> This does not update the subscription in DPS, only the subscription object passed as argument.
     *
     * @param subscription
     *         - subscription
     */
    void adjustPfmSubscriptionData(final T subscription);

    /**
     * Applies and persists Specific correction on Subscription Data required on initiation event.
     *
     * @param nodes
     *         - nodes to be activated/deactivated
     * @param subscription
     *         - subscription
     * @param initiationEventType
     *         - the initiation event
     *
     * @return - true if the subscription need to be updated on dps
     * @throws DataAccessException
     *         - if an exception is thrown while updating subscription attributes.
     */
    boolean shouldUpdateSubscriptionDataOnInitiationEvent(final List<Node> nodes, final T subscription, final InitiationEventType initiationEventType)
            throws DataAccessException;

    /**
     * Update subscription with default predefined values. <b>NOTE:</b> This does not update the subscription in DPS, only the subscription object
     * passed as argument.
     *
     * @param subscription
     *         - imported subscription object.
     *
     * @throws DataAccessException
     *         - if an exception is thrown while updating subscription attributes.
     */
    void updateImportedSubscriptionWithCorrectValues(final T subscription) throws DataAccessException;
}
