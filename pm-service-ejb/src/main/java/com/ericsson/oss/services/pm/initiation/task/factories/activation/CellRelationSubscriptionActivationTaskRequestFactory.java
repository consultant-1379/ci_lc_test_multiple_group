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

package com.ericsson.oss.services.pm.initiation.task.factories.activation;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.ACTIVATION_ERROR;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.NO_CELL_ON_SELECTED_NODE_FOR_ACTIVATION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.NO_MOTYPEINSTANCE_ON_SELECTED_NODE_FOR_ACTIVATION;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.CellRelationSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.MoTypeInstanceInfo;
import com.ericsson.oss.services.pm.common.logging.PMICLog;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractSubscriptionTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.MediationTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.activation.qualifier.ActivationTaskRequest;

/**
 * The CellRelation subscription activation task request factory.
 */
@ActivationTaskRequest(subscriptionType = CellRelationSubscription.class)
@ApplicationScoped
public class CellRelationSubscriptionActivationTaskRequestFactory extends AbstractSubscriptionTaskRequestFactory
        implements MediationTaskRequestFactory<CellRelationSubscription> {


    @Inject
    private Logger logger;

    @Override
    public List<MediationTaskRequest> createMediationTaskRequests(final List<Node> nodes, final CellRelationSubscription subscription,
                                                                  final boolean trackResponse) {
        logger.info("Resolve cells, instances and create activation task for  CellRelation Subscription {} nodes", nodes.size());
        final List<MediationTaskRequest> tasks = new ArrayList<>(nodes.size());
        final Map<String, String> nodesFdnsToActivate = new HashMap<>(nodes.size());
        final List<String> nodeFdnsWithNoCells = new ArrayList<>();
        final List<String> nodeFdnsWithNoMoInstances = new ArrayList<>();
        for (final Node node : nodes) {
            try {
                if (!isCellsAvailableForNodeFdns(node, subscription.getCells())) {
                    nodeFdnsWithNoCells.add(node.getFdn());
                } else if (!isMoTypeinstanceAvailableForNodeFdns(node, subscription.getMoTypeInstances())) {
                    nodeFdnsWithNoMoInstances.add(node.getFdn());
                } else {
                    final MediationTaskRequest task = createActivationTask(node.getFdn(), subscription);
                    tasks.add(task);
                    nodesFdnsToActivate.put(node.getFdn(), node.getNeType());
                    logger.debug("Activating Subscription [{}] type [{}] for node [{}], task id [{}]", subscription.getName(), subscription.getType(),
                            node.getFdn(), task.getJobId());
                }
            } catch (final Exception e) {
                systemRecorder.error(ACTIVATION_ERROR, subscription.getName(), "Failed to activate on node " + node.getFdn(),
                        PMICLog.Operation.ACTIVATION);
                updateSubscriptionTaskStatusToError(subscription);
                logger.error("Error in building activation task for CellRelation subscription: {} with id {} for node {}. ExceptionMessage: {}",
                        subscription.getName(), subscription.getId(), node.getFdn(), e);
            }
        }
        checkInstancesInNodes(nodeFdnsWithNoCells, nodeFdnsWithNoMoInstances, subscription);
        if (trackResponse) {
            addNodeFdnsToActivateToInitiationCache(subscription, nodesFdnsToActivate);
        }

        return tasks;
    }

    /**
     * To check the Cells and Instances for the Nodes
     *
     * @param nodeFdnsWithNoCells
     *         - List of Nodes without cells
     * @param nodeFdnsWithNoMoInstances
     *         - List of Nodes without Instances
     * @param subscription
     *         - Subscription Object
     */
    protected void checkInstancesInNodes(final List<String> nodeFdnsWithNoCells, final List<String> nodeFdnsWithNoMoInstances,
                                         final CellRelationSubscription subscription) {

        if (!nodeFdnsWithNoCells.isEmpty()) {
            systemRecorder.error(NO_CELL_ON_SELECTED_NODE_FOR_ACTIVATION, subscription.getName(),
                    "Failed to activate on nodes due to Cells are not selected " + nodeFdnsWithNoCells, PMICLog.Operation.ACTIVATION);
        }

        if (!nodeFdnsWithNoMoInstances.isEmpty()) {
            systemRecorder.error(NO_MOTYPEINSTANCE_ON_SELECTED_NODE_FOR_ACTIVATION, subscription.getName(),
                    "Failed to activate on nodes due to no CounterGroup Instances are not available on the node selected "
                            + nodeFdnsWithNoMoInstances, PMICLog.Operation.ACTIVATION);
        }

        if (!nodeFdnsWithNoCells.isEmpty() || !nodeFdnsWithNoMoInstances.isEmpty()) {
            logger.error("Error in building activation task for CellRelation subscription: {} with id {} ",
                    subscription.getName(), subscription.getId());
            updateSubscriptionTaskStatusToError(subscription);
        }
    }

    /**
     * To check the Cells for the Node
     *
     * @param node
     *         - Node Data
     * @param availableNodeWithCell
     *         - List of CellInfo from the Subscription
     *
     * @return boolean : availabilty of cells for the Node
     */
    protected boolean isCellsAvailableForNodeFdns(final Node node, final List<CellInfo> availableNodeWithCell) {
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
     * To check  the MoTypeInstances for the Node
     *
     * @param node
     *         - Node Data
     * @param availableNodeWithMoTypeInstance
     *         - List of MoTypeInstanceInfo from the Subscription
     *
     * @return boolean : availabilty of moTypeInstances for the Node
     */
    protected boolean isMoTypeinstanceAvailableForNodeFdns(final Node node, final List<MoTypeInstanceInfo> availableNodeWithMoTypeInstance) {
        for (final MoTypeInstanceInfo instance : availableNodeWithMoTypeInstance) {
            if (instance.getNodeName().equals(node.getName())) {
                logger.debug("MoTypeInstance for node is selected {} .", instance.getNodeName());
                return true;
            }
        }
        logger.debug("MoTypeInstance for node is  not selected {} .", node.getName());
        return false;
    }
}