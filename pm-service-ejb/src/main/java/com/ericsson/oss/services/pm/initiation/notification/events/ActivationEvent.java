/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.notification.events;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.ACTIVATION_EVENT;
import static com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_ACTIVATE_NODES;
import static com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_ACTIVATE_SUBSCRIPTION;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper;
import com.ericsson.oss.services.pm.initiation.processor.activation.ActivationProcessor;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * Activate a subscription. A subscription must have at least one node and one counter for activation. PerformanceMonitoringActivationTask is created
 * for each node and sent to EventBasedMediationClient via EventSender
 */
@Activate
@ApplicationScoped
public class ActivationEvent implements InitiationEvent {

    @Inject
    private Logger logger;
    @Inject
    private InitiationEventUtils initiationEventUtil;
    @Inject
    private ActivationProcessor activationProcessor;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private SubscriptionOperationExecutionTrackingCacheWrapper subscriptionOperationExecutionTrackingCacheWrapper;

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;


    @Override
    @LogProfiler(name = "Activating subscription")
    public void execute(final long subscriptionId) {
        logger.debug("Executing ACTIVATE for subscription {}", subscriptionId);
        Subscription subscription = null;
        subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionId, OPERATION_ACTIVATE_SUBSCRIPTION);
        try {
            subscription = subscriptionReadOperationService.findByIdWithRetry(subscriptionId, true);
            if (subscription == null) {
                subscriptionOperationExecutionTrackingCacheWrapper.removeEntry(subscriptionId);
                logger.error("Cannot activate subscription. Subscription with id {} not found in DPS.", subscriptionId);
                return;
            }
            systemRecorder.eventCoarse(ACTIVATION_EVENT, subscription.getName(), "Subscription %s with id %s retrieved from dps with admin state %s",
                       subscription.getName(), subscription.getId(), subscription.getAdministrationState());
            final List<Node> nodes = initiationEventUtil.getNodesForSubscription(subscription);
            activationProcessor.activate(nodes, subscription, InitiationEventType.SUBSCRIPTION_ACTIVATION);
        } catch (final DataAccessException | RuntimeDataAccessException | RetryServiceException e) {
            final String subscriptionName = subscription == null ? "" : subscription.getName();
            logger.error("Error while executing activation of Subscription {} Id: {}. Error: {}", subscriptionName, subscriptionId, e.getMessage());
            logger.info("Error stack trace:", e);
        }
    }

    @Override
    @LogProfiler(name = "Adding nodes to subscription")
    public void execute(final List<Node> nodes, final Subscription subscription) {
        if (!nodes.isEmpty()) {
            subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscription.getId(), OPERATION_ACTIVATE_NODES, nodes);
            try {
                activationProcessor.activate(nodes, subscription, InitiationEventType.ADD_NODES_TO_SUBSCRIPTION);
            } catch (final DataAccessException e) {
                logger.error("Error while adding nodes to Subscription {} Id: {}. Error: {}", subscription.getName(), subscription.getId(),
                        e.getMessage());
                logger.info("Error stack trace:", e);
            }
        }
    }
}
