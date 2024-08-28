/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.processor.deactivation;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.DEACTIVATE_SUBSCRIPTION;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.services.pm.collection.cache.PmFunctionOffErrorNodeCache;
import com.ericsson.oss.services.pm.common.logging.PMICLog;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.ebs.utils.EbsSubscriptionHelper;
import com.ericsson.oss.services.pm.eventSender.PmEventSender;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.initiation.ejb.CounterConflictServiceImpl;
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventType;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventUtils;
import com.ericsson.oss.services.pm.initiation.processor.initiation.AbstractDeactivationProcessor;
import com.ericsson.oss.services.pm.initiation.task.TaskStatusValidator;
import com.ericsson.oss.services.pm.initiation.task.factories.MediationTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.deactivation.qualifier.DeactivationTaskRequest;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;

/**
 * The Class DeactivationProcessor.
 */
@ApplicationScoped
public class DeactivationProcessor extends AbstractDeactivationProcessor {

    @Inject
    private Logger logger;
    @Inject
    private PmEventSender sender;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private InitiationEventUtils initiationEventUtil;
    @Inject
    private EbsSubscriptionHelper ebsSubscriptionHelper;
    @Inject
    @SubscriptionTaskStatusValidation
    private TaskStatusValidator<Subscription> taskStatusValidator;
    @Inject
    private PmFunctionOffErrorNodeCache pmFunctionOffErrorNodeCache;
    @Inject
    private CounterConflictServiceImpl counterConflictCacheService;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;
    @Inject
    @DeactivationTaskRequest
    private MediationTaskRequestFactory<Subscription> subscriptionDeactivationTaskRequestFactory;
    @Inject
    private SubscriptionOperationExecutionTrackingCacheWrapper subscriptionOperationExecutionTrackingCacheWrapper;

    /**
     * Deactivate nodes of a Subscription.
     *
     * @param nodes
     *         nodes to activate
     * @param subscription
     *         Subscription for which nodes need to be activated
     * @param initiationEventType
     *         type of activation
     *
     * @throws DataAccessException
     *         DataAccessException
     * @throws RetryServiceException
     *         RetryServiceException
     * @throws RuntimeDataAccessException
     *         RuntimeDataAccessException
     */
    @Override
    public void deactivate(final List<Node> nodes, final Subscription subscription, final InitiationEventType initiationEventType)
            throws DataAccessException, RuntimeDataAccessException, RetryServiceException {
        subscriptionWriteOperationService.updateSubscriptionDataOnInitationEvent(nodes, subscription, initiationEventType);
        if (initiationEventType == InitiationEventType.SUBSCRIPTION_DEACTIVATION) {

            if (!AdministrationState.DEACTIVATING.equals(subscription.getAdministrationState())) {
                changeSubscriptionAdminStateToDeactivating(subscription);
            }

            if (!isInitiationContinue(subscription, nodes, initiationEventType)) {
                return;
            }

            updateSubscriptionDeactivationTime(subscription);
            final List<MediationTaskRequest> tasks = createDeactivationTasks(subscription, initiationEventType);
            validateAndSendTasks(tasks, subscription, PMICLog.Command.DEACTIVATE_SUBSCRIPTION, initiationEventType);
            if (subscription instanceof StatisticalSubscription) {
                counterConflictCacheService.removeSubscriptionFromCache(subscription.getName());
            }
        } else if (initiationEventType == InitiationEventType.REMOVE_NODES_FROM_SUBSCRIPTION) {

            if (!isInitiationContinue(subscription, nodes, initiationEventType)) {
                return;
            }
            removeEntriesFromErrorCacheForTheseNodes(nodes, subscription);
            final List<MediationTaskRequest> tasks = createDeactivationTasks(nodes, subscription, initiationEventType);
            validateAndSendTasks(tasks, subscription, PMICLog.Command.REMOVE_NODES_FROM_SUBSCRIPTION, initiationEventType);
            counterConflictCacheService.removeNodesFromExistingSubscriptionEntry(initiationEventUtil.getFdnsFromNodes(nodes), subscription.getName());
        } else {
            logger.error("UNKNOWN Initiation Event Type for Subscription {}", subscription.getName());
        }
    }

    private List<MediationTaskRequest> createDeactivationTasks(final List<Node> nodes, final Subscription subscription,
                                                               final InitiationEventType initiationEventType)
            throws DataAccessException {
        final List<Node> nodesToDeactivate = getFilteredNodesToDeactivate(subscription, nodes, initiationEventType);
        if (noTasksToInitiate(nodesToDeactivate, subscription.getIdAsString(), initiationEventType, subscription)) {
            logger.info("Deactivation MediationTaskRequest creation not needed for subscription {} with subscriptionId {}", subscription.getName(),
                    subscription.getId());
            return Collections.emptyList();
        } else {
            logger.debug("Creating Performance Monitoring Deactivation Mediation Tasks for {} nodes in subscription {}", nodesToDeactivate.size(),
                    subscription.getName());

            return subscriptionDeactivationTaskRequestFactory.createMediationTaskRequests(nodesToDeactivate, subscription, true);
        }
    }

    private List<MediationTaskRequest> createDeactivationTasks(final Subscription subscription, final InitiationEventType initiationEventType)
            throws DataAccessException, RuntimeDataAccessException {
        final List<Node> nodes = initiationEventUtil.getNodesForSubscription(subscription);
        if (nodes == null || nodes.isEmpty()) {
            systemRecorder.commandFinishedError(DEACTIVATE_SUBSCRIPTION, subscription.getIdAsString(), "No nodes found for subscription: %s",
                    subscription.getName());
            return Collections.emptyList();
        }
        removeEntriesFromErrorCacheForTheseNodes(nodes, subscription);
        return createDeactivationTasks(nodes, subscription, initiationEventType);
    }

    private void validateAndSendTasks(final List<MediationTaskRequest> tasks, final Subscription subscription, final PMICLog.Command command,
                                      final InitiationEventType initiationEventType)
            throws DataAccessException, RuntimeDataAccessException, RetryServiceException {
        if (noTasksToInitiate(tasks, subscription.getIdAsString(), initiationEventType, subscription)) {
            final TaskStatus taskStatus = taskStatusValidator.getTaskStatus(subscription);
            logAndUpdateTaskStatus(subscription, taskStatus, command);
            return;
        }

        sender.sendTasksRegardlessOfPmFunctionState(tasks);

        subscriptionOperationExecutionTrackingCacheWrapper.removeEntry(subscription.getId());
        final String message = "%s Performance Monitoring Deactivation Tasks have been sent for subscription %s with id: %s";
        systemRecorder.commandFinishedSuccess(command, subscription.getIdAsString(), message, tasks.size(), subscription.getName(),
                subscription.getId());
    }

    private void removeEntriesFromErrorCacheForTheseNodes(final List<Node> nodes, final Subscription subscription) {
        logger.info(
                "Subscription Deactivation event processing is removing entries from pm function error node cache for {} nodes for subscription "
                        + "{} with id {}. Enable DEBUG logging to see which nodes are beuing removed",
                nodes.size(), subscription.getName(), subscription.getId());
        for (final Node node : nodes) {
            logger.info("Removing error node entry corresponding to node fdn {} and subscription id {} for subscription {}", node.getFdn(),
                    subscription.getId(), subscription.getName());
            pmFunctionOffErrorNodeCache.removeErrorEntry(node.getFdn(), subscription.getId());
        }
    }

    private void logAndUpdateTaskStatus(final Subscription subscription, final TaskStatus taskStatus, final PMICLog.Command command)
            throws DataAccessException, RuntimeDataAccessException, RetryServiceException {
        AdministrationState adminStatus = AdministrationState.INACTIVE;
        TaskStatus taskStatusLocal = taskStatus;
        if (subscriptionReadOperationService.countNodesWithRetry(subscription) == 0) {
            taskStatusLocal = TaskStatus.OK;
        } else if (AdministrationState.UPDATING.equals(subscription.getAdministrationState())
                || AdministrationState.ACTIVE.equals(subscription.getAdministrationState())) {
            adminStatus = AdministrationState.ACTIVE;
        } else {
            taskStatusLocal = TaskStatus.OK;
        }
        subscription.setAdministrationState(adminStatus);
        subscription.setTaskStatus(taskStatusLocal);
        final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
        map.put(Subscription.Subscription220Attribute.administrationState.name(), adminStatus.name());
        map.put(Subscription.Subscription220Attribute.taskStatus.name(), taskStatusLocal.name());
        subscriptionWriteOperationService.updateAttributes(subscription.getId(), map);
        subscriptionOperationExecutionTrackingCacheWrapper.removeEntry(subscription.getId());
        subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));

        systemRecorder.commandFinishedError(command, subscription.getIdAsString(),
                "No Performance Monitoring Tasks to send to mediation, no nodes/scanners exists for the subscription %s "
                        + "Changed Administration State to %s and Task status to %s",
                subscription.getId(), adminStatus, taskStatusLocal);
    }

    private void updateSubscriptionDeactivationTime(final Subscription subscription)
            throws DataAccessException, RuntimeDataAccessException, RetryServiceException {
        final Date date = new Date();
        subscription.setDeactivationTime(date);
        final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
        map.put(Subscription.Subscription220Attribute.deactivationTime.name(), date);
        subscriptionWriteOperationService.updateAttributes(subscription.getId(), map);
        subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));
    }

    private void changeSubscriptionAdminStateToDeactivating(final Subscription subscription)
            throws DataAccessException, RuntimeDataAccessException, RetryServiceException {
        subscription.setAdministrationState(AdministrationState.DEACTIVATING);
        final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
        map.put(Subscription.Subscription220Attribute.administrationState.name(), AdministrationState.DEACTIVATING.name());
        subscriptionWriteOperationService.updateAttributes(subscription.getId(), map);
        subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));
    }

    private List<Node> getFilteredNodesToDeactivate(final Subscription subscription, final List<Node> nodes,
                                                    final InitiationEventType initiationEventType)
            throws DataAccessException {
        final List<Node> nodesToDeactivate = new ArrayList<>();
        if (subscription instanceof CellTraceSubscription && ebsSubscriptionHelper.isEbsStreamOnlyCategory((CellTraceSubscription) subscription)) {
            final Map<String, Set<EventInfo>> nodeToActivatedEbsEventMapping = ebsSubscriptionHelper.getActiveEventNodeMap(subscription.getId(),
                    initiationEventType);
            for (final Node node : nodes) {
                final List<EventInfo> ebsEvents = ((CellTraceSubscription) subscription).getEbsEvents();
                final Set<EventInfo> activatedEbsEvents = nodeToActivatedEbsEventMapping.get(node.getName());
                if (activatedEbsEvents != null && activatedEbsEvents.containsAll(ebsEvents)) {
                    ebsSubscriptionHelper.deletePmicSubScannerInfoForNode(subscription, node);
                } else {
                    nodesToDeactivate.add(node);
                }
            }
            return nodesToDeactivate;
        } else {
            return nodes;
        }
    }
}
