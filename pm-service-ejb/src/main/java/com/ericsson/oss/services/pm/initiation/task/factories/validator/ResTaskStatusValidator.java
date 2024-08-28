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

package com.ericsson.oss.services.pm.initiation.task.factories.validator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResSubscription;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.notification.events.Deactivate;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEvent;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;
import com.ericsson.oss.services.pm.initiation.utils.PmFunctionUtil;
import com.ericsson.oss.services.pm.initiation.utils.PmFunctionUtil.PmFunctionPropertyValue;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * This class validates the Task status for the ResInstanceSubscription
 */
@SubscriptionTaskStatusValidation(subscriptionType = ResSubscription.class)
@ApplicationScoped
public class ResTaskStatusValidator extends ResourceTaskStatusValidator {

    @Inject
    private ScannerService scannerService;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    @Deactivate
    private InitiationEvent deactivationEvent;

    @Override
    public int getSubscriptionNodeCount(final Subscription subscription) throws DataAccessException {
        final ResSubscription resSubscription = (ResSubscription) subscription;
        if (resSubscription.getNodes().isEmpty()) {
            return nodeService.countAllBySubscriptionId(resSubscription.getId());
        } else {
            return resSubscription.getAllNodes().size();
        }
    }

    @Override
    public void validateTaskStatusAndAdminState(final Subscription subscription) {
        final ResSubscription resSubscription = (ResSubscription) subscription;
        final Set<String> allNodesFdns = resSubscription.getNodesFdns();
        allNodesFdns.addAll(resSubscription.getAttachedNodesFdn());
        validateTaskStatusAndAdminState(subscription, allNodesFdns);
    }

    @Override
    public void validateTaskStatusAndAdminState(final Subscription subscription, final Set<String> nodesToBeVerified) {
        try {
            if (AdministrationState.ACTIVE.equals(subscription.getAdministrationState())) {
                final ResSubscription resSubscription = (ResSubscription) subscriptionReadOperationService.findOneById(subscription.getId(), true);
                final PmFunctionPropertyValue pmFunctionPropertyValue = pmFunctionConfig.getPmFunctionConfig();
                final Set<String> explicitNodeFdnsToBeRemoved = new HashSet<>();
                final Set<String> attachedNodeFdnsToBeRemoved = new HashSet<>();
                getNodeFdnsToBeRemoved(nodesToBeVerified, resSubscription, explicitNodeFdnsToBeRemoved, attachedNodeFdnsToBeRemoved);
                if (PmFunctionUtil.PmFunctionPropertyValue.PM_FUNCTION_LEGACY == pmFunctionPropertyValue) {
                    handleScanners(resSubscription, explicitNodeFdnsToBeRemoved, attachedNodeFdnsToBeRemoved);
                }
                logger.debug("nodesToBeVerified: {} - explicitNodeFdnsToBeRemoved: {} - attachedNodeFdnsToBeRemoved: {}", nodesToBeVerified,
                        explicitNodeFdnsToBeRemoved, attachedNodeFdnsToBeRemoved);
                final int explicitNodesCount = getSubscriptionExplicitNodeCount(resSubscription) - explicitNodeFdnsToBeRemoved.size();
                logger.debug("explicit nodes count: {}", explicitNodesCount);
                final Set<String> nodeFdnsToBeRemoved = attachedNodeFdnsToBeRemoved;
                if (PmFunctionUtil.PmFunctionPropertyValue.PM_FUNCTION_LEGACY != pmFunctionPropertyValue) {
                    nodeFdnsToBeRemoved.addAll(explicitNodeFdnsToBeRemoved);
                }
                final int allNodesCount = resSubscription.getAllNodes().size() - nodeFdnsToBeRemoved.size();
                if (explicitNodesCount == 0) {
                    updateSubscriptionToInactiveAndRemoveNodeAssociations(allNodesCount, resSubscription, nodeFdnsToBeRemoved);
                } else {
                    updateSubscription(allNodesCount, resSubscription, nodeFdnsToBeRemoved, resSubscription.getTaskStatus());
                }
            }
        } catch (final DataAccessException | IllegalArgumentException exception) {
            logger.error("Unable to validate subscription {} with id {}. Exception message {}", subscription.getName(), subscription.getId(),
                    exception.getMessage());
            logger.info("Unable to validate subscription {} with id {}.", subscription.getName(), subscription.getId(), exception);
        }
    }

    private void getNodeFdnsToBeRemoved(final Set<String> nodeFdns, final ResSubscription resSubscription,
                                        final Set<String> explicitNodeFdnsToBeRemoved, final Set<String> attachedNodeFdnsToBeRemoved) {

        explicitNodeFdnsToBeRemoved.addAll(resSubscription.getNodesFdns());
        explicitNodeFdnsToBeRemoved.retainAll(nodeFdns);
        attachedNodeFdnsToBeRemoved.addAll(resSubscription.getAttachedNodesFdn());
        attachedNodeFdnsToBeRemoved.retainAll(nodeFdns);
        filterByPmFunction(attachedNodeFdnsToBeRemoved);
        filterByPmFunction(explicitNodeFdnsToBeRemoved);
    }

    private void handleScanners(final ResSubscription resSubscription, final Set<String> explicitNodeFdnsToBeRemoved,
                                final Set<String> attachedNodeFdnsToBeRemoved) {

        final List<Node> attachedNodesToBeDeactivated = new ArrayList<>();
        final List<String> attachedNodesFdnsToBeDeactivated = new ArrayList<>();

        for (final Node attachedNode : resSubscription.getAttachedNodes()) {
            if (explicitNodeFdnsToBeRemoved.contains(attachedNode.getControllingRnc())) {
                attachedNodesFdnsToBeDeactivated.add(attachedNode.getFdn());
                attachedNodesToBeDeactivated.add(attachedNode);
            }
        }
        logger.debug("Deleting scanners on dps for nodes: {} - sending deactivation for {} nodes", attachedNodeFdnsToBeRemoved,
                attachedNodesToBeDeactivated.size());
        deleteScannerOnDps(attachedNodeFdnsToBeRemoved, resSubscription);
        deactivateScannersForNodes(attachedNodesToBeDeactivated, resSubscription);
        attachedNodeFdnsToBeRemoved.addAll(attachedNodesFdnsToBeDeactivated);
    }

    private void filterByPmFunction(final Set<String> nodeFdns) {
        nodeFdns.removeIf(nodeFdn -> nodeService.isPmFunctionEnabled(nodeFdn));
    }

    private void deactivateScannersForNodes(final List<Node> nodes, final ResSubscription subscription) {
        deactivationEvent.execute(nodes, subscription);
    }

    @Override
    public Map<String, Set<String>> getSubscriptionNodesToBeRemoved(final Subscription subscription, final Set<String> nodeFdnsToBeRemoved) {
        Map<String, Set<String>> associationsToRemove = null;
        final ResSubscription resSubscription = (ResSubscription) subscription;
        final Set<String> nodeFdnsToBeRemovedCopy = new HashSet<>(nodeFdnsToBeRemoved);
        final Set<String> attachedNodeFdnsToBeRemoved = new HashSet<>(nodeFdnsToBeRemoved);
        nodeFdnsToBeRemovedCopy.removeAll(resSubscription.getAttachedNodesFdn());
        attachedNodeFdnsToBeRemoved.removeAll(resSubscription.getNodesFdns());
        if (!nodeFdnsToBeRemoved.isEmpty()) {
            associationsToRemove = new HashMap<>();
            associationsToRemove.put(ResourceSubscription.ResourceSubscription120Attribute.nodes.name(), nodeFdnsToBeRemovedCopy);
            associationsToRemove.put(ResSubscription.ResSubscription100Attribute.attachedNodes.name(), attachedNodeFdnsToBeRemoved);
            logger.debug("Associations to remove {} for subscription {} and Id {}", nodeFdnsToBeRemoved, resSubscription.getName(),
                    resSubscription.getId());
        }
        return associationsToRemove;
    }

    private void deleteScannerOnDps(final Set<String> attachedNodeFdnsToBeRemoved, final ResSubscription resSubscription) {
        if (!attachedNodeFdnsToBeRemoved.isEmpty()) {
            try {
                scannerService.delete(scannerService.findAllByNodeFdnAndSubscriptionId(attachedNodeFdnsToBeRemoved,
                        Collections.singleton(resSubscription.getId())));
            } catch (final DataAccessException exception) {
                logger.error("Scanner deletion for subscription {} with id {}. Exception message {}", resSubscription.getName(), resSubscription.getId(),
                        exception.getMessage());
                logger.info("Scanner deletion for subscription {} with id {}.", resSubscription.getName(), resSubscription.getId(), exception);
            }
        }
    }

    private int getSubscriptionExplicitNodeCount(final ResSubscription resSubscription) throws DataAccessException {
        if (resSubscription.getNodes().isEmpty()) {
            return nodeService.countBySubscriptionId(resSubscription.getId());
        } else {
            return resSubscription.getNodes().size();
        }
    }
}