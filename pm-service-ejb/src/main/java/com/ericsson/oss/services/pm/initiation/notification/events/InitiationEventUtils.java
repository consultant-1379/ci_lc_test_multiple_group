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

package com.ericsson.oss.services.pm.initiation.notification.events;

import static com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType.RES;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResSubscription;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.pmjobs.helper.PmJobHelper;

/**
 * This is a utility class for Subscription activation, deactivation, and other operations for a subscription.
 */
public class InitiationEventUtils {

    @Inject
    private PmJobHelper pmJobHelper;

    /**
     * Return all nodes from subscription
     *
     * @param pmSubscription
     *         - pm subscription used to retrieve nodes based on subscription type
     *
     * @return List of {@link Node} or empty list
     * @throws DataAccessException
     *         if database cannot be reached
     */
    public List<Node> getNodesForSubscription(final Subscription pmSubscription) throws DataAccessException {
        if (pmJobHelper.isPmJobSupported(pmSubscription.getType().name())) {
            return pmJobHelper.getAllPmJobSupportedNodesBySubscriptionType(pmSubscription.getType());
        }
        if (pmSubscription.getType() == RES) {
            return ((ResSubscription) pmSubscription).getAllNodes();
        }
        return ((ResourceSubscription) pmSubscription).getNodes();
    }

    /**
     * Return set of fdns from mediation task requests
     *
     * @param tasks
     *         - list of MediationTaskRequest
     *
     * @return - set of node Fdns or empty set.
     */
    public Set<String> getFdnsFromMediationTaskRequests(final List<MediationTaskRequest> tasks) {
        final Set<String> taskFdns = new HashSet<>();
        if (tasks != null) {
            for (final MediationTaskRequest request : tasks) {
                taskFdns.add(request.getNodeAddress());
            }
        }
        return taskFdns;
    }

    /**
     * Return set of fdns from {@link Node} list
     *
     * @param nodes
     *         - list of {@link Node}
     *
     * @return - set of node fdns or empty set
     */
    public Set<String> getFdnsFromNodes(final List<Node> nodes) {
        final Set<String> nodeFdns = new HashSet<>();
        for (final Node node : nodes) {
            nodeFdns.add(node.getFdn());
        }
        return nodeFdns;
    }
}
