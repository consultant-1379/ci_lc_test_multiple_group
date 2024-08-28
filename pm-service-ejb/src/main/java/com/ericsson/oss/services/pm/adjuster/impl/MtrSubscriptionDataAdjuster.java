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
import java.util.List;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.MtrSubscription;
import com.ericsson.oss.pmic.dto.subscription.MtrSubscription.MtrSubscription100Attribute;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterQualifier;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.model.metadata.mtr.PmMtrLookUp;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventType;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * MtrSubscriptionDataAdjuster.
 */
@SubscriptionDataAdjusterQualifier(subscriptionClass = MtrSubscription.class)
@ApplicationScoped
public class MtrSubscriptionDataAdjuster extends ResourceSubscriptionDataAdjuster<MtrSubscription> {

    @Inject
    PmMtrLookUp pmMtrLookup;

    @Inject
    Logger logger;

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    private String bscNEType = "BSC";

    /**
     * This method returns Set of node fdns for a list of nodes
     *
     * @param nodes
     *         - list of nodes
     *
     * @return nodeFdns
     */
    public static Set<String> getNodeFdns(final List<Node> nodes) {
        final Set<String> nodeFdns = new HashSet<>();
        for (final Node node : nodes) {
            nodeFdns.add(node.getFdn());
        }
        return nodeFdns;
    }

    @Override
    public boolean shouldUpdateSubscriptionDataOnInitiationEvent(final List<Node> nodes, final MtrSubscription subscription,
                                                                 final InitiationEventType initiationEventType)
            throws DataAccessException {
        final List<Node> attachedNodes;
        if (InitiationEventType.SUBSCRIPTION_ACTIVATION == initiationEventType) {
            attachedNodes = pmMtrLookup.fetchAttachedNodes(subscription.getNodesFdns(), true);
            if (subscription.getAttachedNodes() != null) {
                subscription.getAttachedNodes().clear();
            }
            subscription.setAttachedNodes(attachedNodes);
            subscription.setTraceReference(subscriptionReadOperationService.generateUniqueTraceReference(
                    MtrSubscription100Attribute.traceReference.name(), MtrSubscription.MTR_SUBSCRIPTION_MODEL_NAMESPACE,
                    MtrSubscription.MTR_SUBSCRIPTION_MODEL_TYPE));
        } else if (InitiationEventType.ADD_NODES_TO_SUBSCRIPTION == initiationEventType) {
            if (!checkNodeContainsBSC(nodes)) {
                subscription.getAttachedNodes().clear();
                attachedNodes = pmMtrLookup.fetchAttachedNodes(subscription.getNodesFdns(), true);
                subscription.getAttachedNodes().addAll(attachedNodes);
                subscription.setNodes(((MtrSubscription) subscriptionReadOperationService.findOneById(subscription.getId(), true)).getNodes());
            }
        } else if (InitiationEventType.REMOVE_NODES_FROM_SUBSCRIPTION == initiationEventType) {
            subscription.getAttachedNodes().clear();
            attachedNodes = pmMtrLookup.fetchAttachedNodes(subscription.getNodesFdns(), false);
            subscription.getAttachedNodes().addAll(attachedNodes);
        } else if (InitiationEventType.SUBSCRIPTION_DEACTIVATION == initiationEventType) {
            if (subscription.getAttachedNodes() != null) {
                subscription.getAttachedNodes().clear();
            }
        } else {
            attachedNodes = subscription.getAttachedNodes();
            if (attachedNodes.isEmpty()) {
                return false;
            }
            subscription.setAttachedNodes(new ArrayList<>(0));
        }
        logger.debug("Attached nodes in subscription: {} and TraceReference {}", subscription.getAttachedNodes(), subscription.getTraceReference());

        return true;
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
    public void updateImportedSubscriptionWithCorrectValues(final MtrSubscription subscription) throws DataAccessException {
        super.updateImportedSubscriptionWithCorrectValues(subscription);
        subscription.setAttachedNodes(getMatchingNodesFromDps(subscription.getAttachedNodes()));
    }

    /**
     * @param nodes
     */
    private boolean checkNodeContainsBSC(final List<Node> nodes) {
        final Set<String> nodeSet = new HashSet<>();
        for (final Node node : nodes) {
            if (bscNEType.equals(node.getNeType())) {
                nodeSet.add(node.getNeType());
            }
        }
        return nodeSet.contains(bscNEType);

    }

}
