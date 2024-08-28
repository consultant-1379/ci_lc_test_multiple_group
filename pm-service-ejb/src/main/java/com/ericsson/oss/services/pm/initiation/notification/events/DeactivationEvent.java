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

import static com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_DEACTIVATE_NODES;
import static com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_DEACTIVATE_SUBSCRIPTION;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper;
import com.ericsson.oss.services.pm.initiation.processor.deactivation.DeactivationProcessor;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * Deactivate a subscription. StatisticsPerformanceMonitoringDeactivationTask is created for each node and sent to EventBasedMediationClient via
 * EventSender
 */
@Deactivate
@ApplicationScoped
public class DeactivationEvent implements InitiationEvent {

    @Inject
    private Logger logger;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private InitiationEventUtils initiationEventUtil;
    @Inject
    private DeactivationProcessor deactivationProcessor;
    @Inject
    private SubscriptionOperationExecutionTrackingCacheWrapper subscriptionOperationExecutionTrackingCacheWrapper;

    @Override
    @LogProfiler(name = "Deactivating subscription")
    public void execute(final long subscriptionId) {
        logger.debug("Executing DEACTIVATE for subscription {}", subscriptionId);
        Subscription subscription = null;
        subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionId, OPERATION_DEACTIVATE_SUBSCRIPTION);
        try {
            subscription = subscriptionReadOperationService.findByIdWithRetry(subscriptionId, true);
            if (subscription == null) {
                subscriptionOperationExecutionTrackingCacheWrapper.removeEntry(subscriptionId);
                logger.warn("Cannot deactivate subscription. Subscription with id {} not found in DPS.", subscriptionId);
                return;
            }
            final List<Node> nodes = initiationEventUtil.getNodesForSubscription(subscription);
            deactivationProcessor.deactivate(nodes, subscription, InitiationEventType.SUBSCRIPTION_DEACTIVATION);
        } catch (final DataAccessException | RuntimeDataAccessException | RetryServiceException e) {
            final String subscriptionName = subscription == null ? "" : subscription.getName();
            logger.error("Error while executing deactivation of Subscription {}} Id: {}. Error: {}", subscriptionName, subscriptionId,
                    e.getMessage());
            logger.info("Error stack trace:", e);
        }
    }

    @Override
    @LogProfiler(name = "Removing nodes from subscription")
    public void execute(final List<Node> nodes, final Subscription subscription) {
        if (!nodes.isEmpty()) {
            subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscription.getId(), OPERATION_DEACTIVATE_NODES, nodes);
            try {
                deactivationProcessor.deactivate(nodes, subscription, InitiationEventType.REMOVE_NODES_FROM_SUBSCRIPTION);
            } catch (final RuntimeDataAccessException | DataAccessException | RetryServiceException e) {
                logger.error("Error while removing nodes from Subscription {} Id: {}. Error: {}", subscription.getName(), subscription.getId(),
                        e.getMessage());
                logger.info("Error stack trace:", e);
            }
        }
    }
}
