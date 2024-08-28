/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.task.factories.auditor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.ejb.Stateless;
import javax.inject.Inject;

import com.ericsson.oss.pmic.api.selector.annotation.Selector;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.adjuster.impl.ResSubscriptionDataAdjuster;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.ejb.ResourceSubscriptionNodeInitiation;
import com.ericsson.oss.services.pm.initiation.model.metadata.PmDataUtilLookupBean;

/**
 * Helper Class containing audit function for RES Subscription
 */
@Selector(filter = "ResSubscriptionHelper")
@Stateless
public class ResSubscriptionHelper extends ResourceSubscriptionHelper {

    @Inject
    private PmDataUtilLookupBean pmDataUtilLookupBean;

    @Inject
    private ResourceSubscriptionNodeInitiation subscriptionNodeInitiation;

    @Override
    protected List<Node> getNodesFromSubscription(final Subscription subscription) {
        return ((ResSubscription) subscription).getAllNodes();
    }

    @Override
    protected Set<String> getNodeFdnsFromSubscription(final Subscription subscription) {
        final ResSubscription resSubscription = ((ResSubscription) subscription);
        final Set<String> allNodesFdns = resSubscription.getNodesFdns();
        allNodesFdns.addAll(resSubscription.getAttachedNodesFdn());
        return allNodesFdns;
    }

    /**
     * Recalculates attached Nodes and udates subscription data accordingly. Added/removed nodes will be included in erroneousNodes class for
     * activation/deactivation handling where subscription persistence will occur.
     */
    @Override
    protected void checkSubscriptionForExtraCriteria(final Subscription subscription) {
        final ResSubscription resSubscription = (ResSubscription) subscription;
        if (!resSubscription.getResSpreadingFactor().isEmpty()) {
            try {
                final List<Node> actualAttachedNodes = pmDataUtilLookupBean.resFetchAttachedNodesInReadTx(resSubscription.getCells(), resSubscription.isApplyOnAllCells(),
                        resSubscription.getNodesFdns(), true);
                final List<Node> attachedNodesFromSub = resSubscription.getAttachedNodes();
                final List<Node> attachedNodesToBeRemoved = new ArrayList<>(attachedNodesFromSub);
                final List<Node> attachedNodesToBeAdded = new ArrayList<>(actualAttachedNodes);
                ResSubscriptionDataAdjuster.filterByFdn(attachedNodesToBeAdded, attachedNodesFromSub);
                ResSubscriptionDataAdjuster.filterByFdn(attachedNodesToBeRemoved, actualAttachedNodes);
                logger.info("ExtraCriteria Auditing for subscription {} - Found {} attached nodes to be added and {} attached nodes to be removed",
                        subscription.getName(), attachedNodesToBeAdded.size(), attachedNodesToBeRemoved.size());
                resSubscription.setAttachedNodes(actualAttachedNodes);
                subscriptionNodeInitiation.activateOrDeactivateNodesOnActiveSubscription(resSubscription, attachedNodesToBeAdded,
                        attachedNodesToBeRemoved);

            } catch (final DataAccessException e) {
                logger.warn("Audit for ResSubscription {} failed! Could not fetch attached Nodes from dps :: {}", subscription.getName(), e.getMessage());
                logger.debug("fetchAttachedNodes failed during auditing of ResSubscription {} :: {}", subscription.getName(), e.getStackTrace());
            }
        }
    }
}
