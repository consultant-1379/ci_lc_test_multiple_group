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

package com.ericsson.oss.services.pm.initiation.notification.handlers;

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.RES_SUBSCRIPTION_ATTRIBUTES;

import java.util.Collections;
import java.util.List;
import javax.cache.Cache;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache;
import com.ericsson.oss.pmic.api.modelservice.PmCapabilitiesLookupLocal;
import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventType;
import com.ericsson.oss.services.pm.initiation.processor.activation.ActivationProcessor;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * This class can handle behavior related to ResPmFunctionEnabled event
 */
public class ResPmFunctionHelper {

    static final String ATTACHED_NODE_TYPES = "multipleNeTypesList";

    @Inject
    private NodeService nodeService;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;
    @Inject
    private Logger logger;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private ActivationProcessor activationProcessor;
    @Inject
    private PmCapabilitiesLookupLocal pmCapabilitiesLookupLocal;

    @Inject
    @NamedCache(CacheNamingConstants.RES_ATTACHED_NODES_BY_SUBSCRIPTION_ID)
    private Cache<Long, List<Node>> attachedNodesBySubscriptionId;

    /**
     * Create notification when the neType is an attachedNode
     *
     * @param node
     *         - The node
     */
    public void handlePmFunctionEnabled(final Node node) {
        if (!dpsAvailabilityStatus.isAvailable()) {
            logger.warn("Failed to handle RES subscription on pmFunctionEnabled for {}, Dps not available", node.getFdn());
            return;
        }
        final List<String> resSubscriptionAttachedNodeTypes = (List<String>) pmCapabilitiesLookupLocal
                .getDefaultCapabilityValue(RES_SUBSCRIPTION_ATTRIBUTES, ATTACHED_NODE_TYPES);
        if (!validResNeTypeAndRncPmFunctionEnabled(node, resSubscriptionAttachedNodeTypes)) {
            return;
        }
        try {
            final String rncFdn = node.getControllingRnc();
            final List<Subscription> activeResSubscriptions =
                    subscriptionReadOperationService.findAllBySubscriptionTypeAndAdministrationState(new SubscriptionType[]{SubscriptionType.RES},
                                new AdministrationState[]{AdministrationState.ACTIVE}, true);
            for (final Subscription subscription : activeResSubscriptions) {
                final ResSubscription resSubscription = (ResSubscription) subscription;
                if (shouldIncludeNodeInSubscription(resSubscription, node, rncFdn, resSubscriptionAttachedNodeTypes)) {
                    resSubscription.getAttachedNodes().add(node);
                    activationProcessor.activate(Collections.singletonList(node), resSubscription, InitiationEventType.ADD_NODES_TO_SUBSCRIPTION);
                }
            }
        } catch (final DataAccessException ex) {
            logger.warn("Could not process PmFunctionEnabled event on node {} due to data layer exception", node.getFdn(), ex);
        }
    }

    private boolean validResNeTypeAndRncPmFunctionEnabled(final Node node, final List<String> resSubscriptionAttachedNodeTypes) {
        return resSubscriptionAttachedNodeTypes.contains(node.getNeType()) && node.getControllingRnc() != null && nodeService.isPmFunctionEnabled(node.getControllingRnc());
    }

    private boolean shouldIncludeNodeInSubscription(final ResSubscription subscription, final Node node,
                                                    final String rncFdn, final List<String> resSubscriptionAttachedNodeTypes) throws DataAccessException {
        return !subscription.getResSpreadingFactor().isEmpty()
                && subscription.getNodesFdns().contains(rncFdn)
                && (subscription.isApplyOnAllCells() || getAttachedNodes(subscription, resSubscriptionAttachedNodeTypes).contains(node));
    }

    private synchronized List<Node> getAttachedNodes(final ResSubscription resSubscription, final List<String> resSubscriptionAttachedNodeTypes)
            throws DataAccessException {
        final Long subId = resSubscription.getId();
        List<Node> attachedNodes = attachedNodesBySubscriptionId.get(subId);
        if (attachedNodes == null) {
            logger.debug("fetching attached nodes for subscription {} from dps", resSubscription.getName());
            attachedNodes = nodeService.fetchWranAttached(resSubscriptionAttachedNodeTypes, resSubscription.getNodesFdns(),
                    resSubscription.getCells(), false);
        }
        attachedNodesBySubscriptionId.put(subId, attachedNodes);
        return attachedNodes;
    }
}