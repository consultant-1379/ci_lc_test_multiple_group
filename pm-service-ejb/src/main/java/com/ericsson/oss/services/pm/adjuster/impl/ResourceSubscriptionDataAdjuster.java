/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.adjuster.impl;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.NodeNotFoundDataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;

/**
 * ResourceSubscriptionDataAdjuster
 *
 * @param <T>
 *         - subscription type.
 */
public class ResourceSubscriptionDataAdjuster<T extends ResourceSubscription> extends SubscriptionDataAdjuster<T> {

    @Inject
    private NodeService nodeService;

    @Inject
    private PmFunctionEnabledWrapper pmFunctionCache;

    @Override
    public void updateImportedSubscriptionWithCorrectValues(final T subscription) throws DataAccessException {
        super.updateImportedSubscriptionWithCorrectValues(subscription);
        subscription.setNodes(getMatchingNodesFromDps(subscription.getNodes()));
    }

    @Override
    public void correctSubscriptionData(final T subscription) {
        final List<Node> nodes = subscription.getNodes();
        getNodesPmFunction(nodes, subscription);
    }

    /**
     * This method set PmFunction
     *
     * @param nodes
     *         - list of nodes
     * @param subscription
     *         - subscription model
     */
    protected void getNodesPmFunction(final List<Node> nodes, final T subscription) {
        final List<String> selectedNeTypes = new ArrayList();
        for (final Node node : nodes) {
            if (!selectedNeTypes.contains(node.getNeType())) {
                selectedNeTypes.add(node.getNeType());
            }
            if (node.getPmFunction() == null) {
                node.setPmFunction(pmFunctionCache.isPmFunctionEnabled(node.getFdn()));
            }
        }
        subscription.setSelectedNeTypes(selectedNeTypes);
    }

    /**
     * This method retrieves nodes from DPS starting from node fdns.
     *
     * @param nodes
     *         - list of nodes
     *
     * @return {@code List<Node>} - list of nodes found in DPS
     * @throws DataAccessException
     *         - in case of problems with DPS
     */
    protected List<Node> getMatchingNodesFromDps(final List<Node> nodes) throws DataAccessException {
        final List<String> invalidNodes = new ArrayList<>();
        final List<Node> nodesFromDps = new ArrayList<>(nodes.size());
        for (final Node node : nodes) {
            try {
                final Node dpsNode = nodeService.findOneByFdn(node.getFdn());
                if (dpsNode == null) {
                    /* Not existent or malformed fdn */
                    invalidNodes.add(node.getFdn());
                } else {
                    nodesFromDps.add(dpsNode);
                }
            } catch (final IllegalArgumentException exception) {
                /* null or empty fdn */
                invalidNodes.add(node.getFdn());
            } catch (final DataAccessException e) {
                throw e;
            }
        }
        if (!invalidNodes.isEmpty()) {
            throw new NodeNotFoundDataAccessException("Invalid node fdns: " + invalidNodes);
        }
        return nodesFromDps;
    }
}
