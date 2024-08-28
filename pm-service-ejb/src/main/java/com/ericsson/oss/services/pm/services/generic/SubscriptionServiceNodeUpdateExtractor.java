/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.services.generic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

import com.ericsson.oss.pmic.dao.SubscriptionDao;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.exception.SubscriptionNotFoundDataAccessException;

/**
 * Utility class shared between {@link SubscriptionReadOperationService} and {@link SubscriptionServiceWriteOperationsWithTrackingSupportImpl}
 * to extract nodes added and removed from active subscription on update before the subscription is actually updated.
 */
public class SubscriptionServiceNodeUpdateExtractor {

    @Inject
    private SubscriptionDao subscriptionDao;

    /**
     * Get nodes added and nodes removed from given subscription in comparison to the subscription that is in DPS with the same ID.
     *
     * @param subscription
     *         - subscription with nodes to verify against.
     *
     * @return - NodeDiff containing nodes added and nodes removed.
     * @throws DataAccessException
     *         - if an exception is thrown from database.
     * @throws RuntimeDataAccessException
     *         - if a retrievable exception is thrown from database.
     */
    public NodeDiff getNodeDifference(final Subscription subscription) throws DataAccessException, RuntimeDataAccessException {
        final ResourceSubscription oldSubscription = (ResourceSubscription) subscriptionDao.findOneById(subscription.getId(), true);
        if (oldSubscription == null) {
            throw new SubscriptionNotFoundDataAccessException("Subscription with id [" + subscription.getId() + "] does not exist.");
        }
        final Set<String> oldSubscriptionNodeFdns = oldSubscription.getNodesFdns();
        final Set<String> newSubscriptionNodeFdns = ((ResourceSubscription) subscription).getNodesFdns();

        oldSubscriptionNodeFdns.removeAll(newSubscriptionNodeFdns);
        newSubscriptionNodeFdns.removeAll(oldSubscription.getNodesFdns());

        final List<Node> nodesRemoved = new ArrayList<>(oldSubscriptionNodeFdns.size());
        for (final Node node : oldSubscription.getNodes()) {
            if (oldSubscriptionNodeFdns.contains(node.getFdn())) {
                nodesRemoved.add(node);
            }
        }

        final List<Node> nodesAdded = new ArrayList<>(newSubscriptionNodeFdns.size());
        for (final Node node : ((ResourceSubscription) subscription).getNodes()) {
            if (newSubscriptionNodeFdns.contains(node.getFdn())) {
                nodesAdded.add(node);
            }
        }
        return new NodeDiff(nodesAdded, nodesRemoved);
    }

    /**
     * Inner Class to hold node difference.
     */
    class NodeDiff {
        private final List<Node> nodesAdded;
        private final List<Node> nodesRemoved;

        /**
         * Constructor.
         *
         * @param nodesAdded
         *         - nodesAdded
         * @param nodesRemoved
         *         - nodesRemoved
         */
        NodeDiff(final List<Node> nodesAdded, final List<Node> nodesRemoved) {
            this.nodesAdded = nodesAdded;
            this.nodesRemoved = nodesRemoved;
        }

        List<Node> getNodesRemoved() {
            return nodesRemoved;
        }

        List<Node> getNodesAdded() {
            return nodesAdded;
        }
    }
}
