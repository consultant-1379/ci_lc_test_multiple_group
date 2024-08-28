/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.adjuster.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterQualifier;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.model.metadata.res.PmResLookUp;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventType;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * This class validates Res Statistical Subscription
 */
@SubscriptionDataAdjusterQualifier(subscriptionClass = ResSubscription.class)
@ApplicationScoped
public class ResSubscriptionDataAdjuster extends ResourceSubscriptionDataAdjuster<ResSubscription> {

    @Inject
    NodeService nodeService;

    @Inject
    PmResLookUp pmResLookup;

    @Inject
    SubscriptionReadOperationService subscriptionReadOperationService;

    @Inject
    Logger logger;

    /**
     * Performs a filtering of target List removing all nodes coming from source List based on Node Fdn only. Returns removed nodes. That's safer than
     * using removeAll because Node.equals() checks also fields which could change in time (e.g. utcOffset)
     *
     * @param target
     *         - List to be filtered
     * @param source
     *         - source list
     *
     * @return removed nodes
     */
    public static List<Node> filterByFdn(final List<Node> target, final List<Node> source) {
        final Set<String> sourceFdns = getNodeFdns(source);
        final List<Node> removedNodes = new ArrayList<>(source.size());
        final Iterator<Node> targetIterator = target.iterator();
        while (targetIterator.hasNext()) {
            final Node node = targetIterator.next();
            if (sourceFdns.contains(node.getFdn())) {
                removedNodes.add(node);
                targetIterator.remove();
            }
        }
        return removedNodes;
    }

    private static Set<String> getNodeFdns(final List<Node> nodes) {
        final Set<String> nodeFdns = new HashSet<>();
        for (final Node node : nodes) {
            nodeFdns.add(node.getFdn());
        }
        return nodeFdns;
    }

    @Override
    public boolean shouldUpdateSubscriptionDataOnInitiationEvent(final List<Node> nodes, final ResSubscription subscription,
                                                                 final InitiationEventType initiationEventType)
            throws DataAccessException {
        List<Node> attachedNodes;
        loadAssociations(subscription);
        if (!subscription.getResSpreadingFactor().isEmpty()) {
            if (InitiationEventType.SUBSCRIPTION_ACTIVATION == initiationEventType) {
                attachedNodes = pmResLookup.fetchAttachedNodes(subscription.getCells(), subscription.isApplyOnAllCells(), subscription.getNodesFdns(),
                        true);
                subscription.setAttachedNodes(attachedNodes);
                nodes.clear();
                nodes.addAll(subscription.getAllNodes());
            } else if (InitiationEventType.ADD_NODES_TO_SUBSCRIPTION == initiationEventType) {
                final List<CellInfo> filteredCells = (!subscription.isApplyOnAllCells()) ? getFilteredCells(subscription.getCells(), nodes) : null;
                attachedNodes = pmResLookup.fetchAttachedNodes(filteredCells, subscription.isApplyOnAllCells(), getNodeFdns(nodes), true);
                filterByFdn(attachedNodes, subscription.getAttachedNodes());
                subscription.getAttachedNodes().addAll(attachedNodes);
                nodes.addAll(attachedNodes);
            } else if (initiationEventType == InitiationEventType.REMOVE_NODES_FROM_SUBSCRIPTION) {
                attachedNodes = filterByFdn(subscription.getAttachedNodes(),
                        pmResLookup.fetchAttachedNodes(null, subscription.isApplyOnAllCells(), getNodeFdns(nodes), false));
                nodes.addAll(attachedNodes);
            }
            logger.debug("Attached nodes will be updated in subscription: {}", subscription.getName());
        } else {
            attachedNodes = subscription.getAttachedNodes();
            if (attachedNodes.isEmpty()) {
                return false;
            }
            subscription.setAttachedNodes(new ArrayList<Node>(0));
            nodes.removeAll(attachedNodes);
            logger.debug("Attached nodes will be removed in subscription: {}", subscription.getName());
        }
        return true;
    }

    @Override
    public void correctSubscriptionData(final ResSubscription subscription) {
        final List<Node> nodes = subscription.getAllNodes();
        getNodesPmFunction(nodes, subscription);
    }

    private List<CellInfo> getFilteredCells(final List<CellInfo> cells, final List<Node> nodes) {
        final List<CellInfo> filteredCells = new ArrayList<>();
        final Iterator<Node> nodeItr = nodes.iterator();
        final Iterator<CellInfo> cellItr = cells.iterator();

        while (nodeItr.hasNext()) {
            final String nodeName = nodeItr.next().getName();
            while (cellItr.hasNext()) {
                final CellInfo cellInfo = cellItr.next();
                if (cellInfo.getNodeName().equals(nodeName)) {
                    filteredCells.add(cellInfo);
                }
            }
        }
        return filteredCells;
    }

    private void loadAssociations(final ResSubscription subscription) throws DataAccessException {
        if (subscription.getNodes().isEmpty()) {
            final ResSubscription subscriptionReloaded = (ResSubscription) subscriptionReadOperationService.findOneById(subscription.getId(), true);
            subscription.setNodes(subscriptionReloaded.getNodes());
            subscription.setAttachedNodes(subscriptionReloaded.getAttachedNodes());
            subscription.setAssociationsLoaded(true);
            logger.debug("Retrieved associations from dps for subscrition {}", subscription.getName());
        }
    }

    /**
     * This method updates the imported subscription with the attached nodes found in DPS
     *
     * @param subscription
     *         - imported subscription
     *
     * @throws DataAccessException
     *         - in case of problems with dps
     */
    @Override
    public void updateImportedSubscriptionWithCorrectValues(final ResSubscription subscription) throws DataAccessException {
        super.updateImportedSubscriptionWithCorrectValues(subscription);
        subscription.setAttachedNodes(getMatchingNodesFromDps(subscription.getAttachedNodes()));
    }
}
