/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.task.factories.activation;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.ACTIVATION_ERROR;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.NO_CELL_ON_SELECTED_NODE_FOR_ACTIVATION;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.RpmoSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo;
import com.ericsson.oss.services.pm.common.logging.PMICLog;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractSubscriptionTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.MediationTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.activation.qualifier.ActivationTaskRequest;

/**
 * The RPMO subscription activation task request factory.
 */
@ActivationTaskRequest(subscriptionType = RpmoSubscription.class)
@ApplicationScoped
public class RpmoSubscriptionActivationTaskRequestFactory extends AbstractSubscriptionTaskRequestFactory
        implements MediationTaskRequestFactory<RpmoSubscription> {
    @Inject
    private Logger logger;

    @Override
    public List<MediationTaskRequest> createMediationTaskRequests(final List<Node> nodes, final RpmoSubscription subscription,
                                                                  final boolean trackResponse) {
        logger.info("Create activation task for RPMO Subscription {} nodes", nodes.size());
        final List<MediationTaskRequest> tasks = new ArrayList<>(nodes.size());
        final Map<String, String> nodesFdnsToActivate = new HashMap<>(nodes.size());
        final Map<String, Integer> nodeFdnExpectedNotificationMap = new HashMap<>(nodes.size());
        final List<String> nodeFdnsWithNoCells = new ArrayList<>();

        for (final Node node : nodes) {
            try {
                if (!subscription.isApplyOnAllCells() && !isCellsAvailableForNodeFdns(node, subscription.getCells())) {
                    nodeFdnsWithNoCells.add(node.getFdn());
                } else {
                    final MediationTaskRequest task = createActivationTask(node.getFdn(), subscription);
                    tasks.add(task);
                    nodesFdnsToActivate.put(node.getFdn(), node.getNeType());
                    nodeFdnExpectedNotificationMap.put(node.getFdn(), 1);
                    logger.debug("Activating Subscription [{}] type [{}] for node [{}], task id [{}]", subscription.getName(), subscription.getType(),
                            node.getFdn(), task.getJobId());
                }
            } catch (final Exception e) {
                systemRecorder.error(ACTIVATION_ERROR, subscription.getName(), "Failed to activate on node " + node.getFdn(),
                        PMICLog.Operation.ACTIVATION);
                updateSubscriptionTaskStatusToError(subscription);
                logger.error("Error in building activation task for RPMO subscription: {} with id {} for node {}. ExceptionMessage: {}",
                        subscription.getName(), subscription.getId(), node.getFdn(), e);
            }
        }

        checkCellsForNodes(nodeFdnsWithNoCells, subscription);
        if (trackResponse && !nodesFdnsToActivate.isEmpty()) {
            addNodeFdnsToActivateToInitiationCache(subscription, nodesFdnsToActivate, nodeFdnExpectedNotificationMap);
        }

        return tasks;
    }

    /**
     * To check whether Cells are available for the Node.
     *
     * @param node
     *         - Node Data
     * @param availableNodeWithCell
     *         - List of CellInfo from the Subscription
     *
     * @return boolean : availabilty of cells for the Node
     */
    private boolean isCellsAvailableForNodeFdns(final Node node, final List<CellInfo> availableNodeWithCell) {
        for (final CellInfo cell : availableNodeWithCell) {
            if (cell.getNodeName().equals(node.getName())) {
                logger.debug("Cell for node is selected {} .", cell.getNodeName());
                return true;
            }
        }
        logger.debug("Cell for node is  not selected {} .", node.getName());
        return false;
    }

    /**
     * To check the Cells for the Nodes.
     *
     * @param nodeFdnsWithNoCells
     *         - List of Nodes without cells
     * @param subscription
     *         - Subscription Object
     */
    private void checkCellsForNodes(final List<String> nodeFdnsWithNoCells, final RpmoSubscription subscription) {
        if (!nodeFdnsWithNoCells.isEmpty()) {
            systemRecorder.error(NO_CELL_ON_SELECTED_NODE_FOR_ACTIVATION, subscription.getName(),
                    "Failed to activate on nodes due to Cells not selected " + nodeFdnsWithNoCells, PMICLog.Operation.ACTIVATION);
            logger.error("Error in building activation task for RPMO subscription: {} with id {} ",
                    subscription.getName(), subscription.getId());
            updateSubscriptionTaskStatusToError(subscription);
        }
    }
}
