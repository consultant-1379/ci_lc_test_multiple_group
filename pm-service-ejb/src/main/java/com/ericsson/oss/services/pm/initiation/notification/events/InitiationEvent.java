/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.notification.events;

import java.util.List;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.Subscription;

/**
 * Initiation event.
 */
public interface InitiationEvent {

    /**
     * Execute.
     *
     * @param subscriptionId
     *         the subscription id
     */
    void execute(long subscriptionId);

    /**
     * Execute.
     *
     * @param nodes
     *         list of nodes
     * @param subscription
     *         the subscription object
     */
    void execute(final List<Node> nodes, final Subscription subscription);

}
