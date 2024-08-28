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

package com.ericsson.oss.services.pm.initiation.pmjobs.sync;

import java.util.List;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.Subscription;

/**
 * Value Object for the Processor result for the subscriptions and the related nodes to be operated
 */
public class SyncResultVO {

    private final Subscription subscription;
    private final List<Node> nodesToBeUpdated;

    /**
     * Constructor
     *
     * @param subscription
     *         The Subscription
     * @param nodesToBeUpdated
     *         List of nodes
     */
    public SyncResultVO(final Subscription subscription, final List<Node> nodesToBeUpdated) {
        this.subscription = subscription;
        this.nodesToBeUpdated = nodesToBeUpdated;
    }

    /**
     * Gets the list of Nodes
     *
     * @return List of Node
     */

    public List<Node> getNodesToBeUpdated() {
        return nodesToBeUpdated;
    }

    /**
     * Gets subscription
     *
     * @return The Subscription
     */
    public Subscription getSubscription() {
        return subscription;
    }

}
