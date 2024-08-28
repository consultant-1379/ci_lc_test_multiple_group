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

package com.ericsson.oss.services.pm.initiation.processor.activation;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.ACTIVATE_SUBSCRIPTION;

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
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;
import com.ericsson.oss.services.pm.collection.cache.PmFunctionOffErrorNodeCache;
import com.ericsson.oss.services.pm.common.logging.PMICLog;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.common.systemdefined.SystemDefinedSubscriptionManager;
import com.ericsson.oss.services.pm.ebs.utils.EbsSubscriptionHelper;
import com.ericsson.oss.services.pm.eventSender.PmEventSender;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.ejb.CounterConflictServiceImpl;
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventType;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventUtils;
import com.ericsson.oss.services.pm.initiation.processor.initiation.AbstractActivationProcessor;
import com.ericsson.oss.services.pm.initiation.task.TaskStatusValidator;
import com.ericsson.oss.services.pm.initiation.task.factories.MediationTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.activation.qualifier.ActivationTaskRequest;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;

/**
 * The Class ActivationProcessor.
 */
@ApplicationScoped
public class ActivationProcessor extends AbstractActivationProcessor {

    @Inject
    private Logger logger;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private NodeService nodeService;
    @Inject
    private InitiationEventUtils initiationEventUtil;
    @Inject
    private PmFunctionOffErrorNodeCache pmFunctionOffErrorNodeCache;
    @Inject
    private SubscriptionOperationExecutionTrackingCacheWrapper subscriptionOperationExecutionTrackingCacheWrapper;
    @Inject
    @ActivationTaskRequest
    private MediationTaskRequestFactory<Subscription> subscriptionActivationTaskRequestFactory;
    @Inject
    private PmEventSender sender;
    @Inject
    private SystemDefinedSubscriptionManager systemDefinedSubscriptionManager;
    @Inject
    private CounterConflictServiceImpl counterConflictCacheService;
    @Inject
    private EbsSubscriptionHelper ebsSubscriptionHelper;
    @Inject
    @SubscriptionTaskStatusValidation
    private TaskStatusValidator<Subscription> taskStatusValidator;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;

    /**
     * Activates nodes of a Subscription.
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
     */
    @Override
    public void activate(final List<Node> nodes, final Subscription subscription, final InitiationEventType initiationEventType)
        throws DataAccessException {
        subscriptionWriteOperationService.updateSubscriptionDataOnInitationEvent(nodes, subscription, initiationEventType);
        if (InitiationEventType.ADD_NODES_TO_SUBSCRIPTION == initiationEventType
                || InitiationEventType.PM_FUNCTION_ADD_NODES_TO_SUBSCRIPTION == initiationEventType) {
            activateNodes(nodes, subscription, initiationEventType, PMICLog.Command.ADD_NODES_TO_SUBSCRIPTION);
            if (subscription instanceof StatisticalSubscription) {
                counterConflictCacheService.addNodesToExistingSubscriptionEntry(subscription.getName(), initiationEventUtil.getFdnsFromNodes(nodes));
            }
        } else if (InitiationEventType.SUBSCRIPTION_ACTIVATION == initiationEventType) {
            updateActivationTimeAndAdminState(subscription);
            if (nodes.isEmpty()) {
                systemRecorder.commandFinishedError(ACTIVATE_SUBSCRIPTION, subscription.getId().toString(),
                        "Subscription {} with id {} has no nodes. Activation : %s", subscription.getName(), subscription.getId());
                updateSubscriptionStatusAndAndMarkActivationComplete(subscription, AdministrationState.ACTIVE, TaskStatus.ERROR);
                return;
            }
            activateNodes(nodes, subscription, initiationEventType, ACTIVATE_SUBSCRIPTION);
            if (subscription instanceof StatisticalSubscription) {
                counterConflictCacheService.addNodesAndCounters(initiationEventUtil.getFdnsFromNodes(nodes),
                        ((StatisticalSubscription) subscription).getCounters(), subscription.getName());
            }
        } else if (InitiationEventType.SCANNER_MASTER_ACTIVATE_NODE == initiationEventType) {
            activateNodeWithoutPreCheck(nodes, subscription);
        } else {
            logger.error("UNKNOWN Initiation Event Type for Subscription {}", subscription.getName());
        }
    }

    private void activateNodes(final List<Node> nodes, final Subscription subscription, final InitiationEventType initiationEventType,
                               final PMICLog.Command command)
        throws DataAccessException {
        if (!isInitiationContinue(subscription, nodes, initiationEventType)) {
            return;
        }
        final List<Node> pmFunctionONnodes = getNodesWithPmFunctionOnAndUpdateErrorNodeCache(subscription.getId(), subscription, nodes);
        if (noTasksToInitiate(pmFunctionONnodes, subscription.getIdAsString(), initiationEventType, subscription)) {
            logger.warn(
                    "All of the nodes to be activated for subscription {} with id {} have PmFunction pmEnabled FALSE. "
                            + "Updating administrationState to {} and taskStatus to {}",
                    subscription.getName(), subscription.getId(), AdministrationState.ACTIVE, TaskStatus.ERROR);
            updateSubscriptionStatusAndAndMarkActivationComplete(subscription, AdministrationState.ACTIVE, TaskStatus.ERROR);
            return;
        }

        final List<Node> nodesToActivate = getFilteredNodesToActivate(subscription, pmFunctionONnodes, initiationEventType);
        if (noTasksToInitiate(nodesToActivate, subscription.getIdAsString(), initiationEventType, subscription)) {
            final TaskStatus taskStatus = taskStatusValidator.getTaskStatus(subscription);
            updateSubscriptionStatusAndAndMarkActivationComplete(subscription, AdministrationState.ACTIVE, taskStatus);
            logger.info("Activation MediationTaskRequest creation not needed for subscription {} with subscriptionId:{}, administrativeState:{}, "
                    + "taskstatus:{}", subscription.getName(), subscription.getId(), AdministrationState.ACTIVE, taskStatus);
            return;
        }
        final List<MediationTaskRequest> tasks = createActivationTaskRequests(nodesToActivate, subscription);
        logger.info("pmFunction ON nodes ({}) for subscription {} with id {}, prepared tasks : {}. ", pmFunctionONnodes.size(),
            subscription.getName(), subscription.getId(), tasks.size());
        if (noTasksToInitiate(tasks, subscription.getIdAsString(), initiationEventType, subscription)) {
            logger.warn(
                "Couldn't create activation mediation tasks for all of the pmFunction ON nodes ({}) for subscription {} "
                    + "with id {}. Possible reason: subscription doesn't have any counters/events selected "
                    + "or there aren't any unused predefined scanners available in DPS or predef scanner is already active in DPS",
                pmFunctionONnodes.size(), subscription.getName(), subscription.getId());
            TaskStatus status = TaskStatus.ERROR;
            // On a System Defined Statistical Subscription predef scanners could be already active, no MTR is required and status is OK
            // If the subscription task status is already in error, leave it (scenario when node is not reachable and never answered to scanner
            // polling request)
            if ((SubscriptionType.CONTINUOUSCELLTRACE.isOneOf(subscription.getType()) || isSystemDefinedStatisticalSubscriptionWithPredefScanners(subscription)) && TaskStatus.OK == subscription.getTaskStatus()) {
                logger.info("updating subscription {} status to Ok", subscription.getName());
                status = TaskStatus.OK;
            }
            updateSubscriptionStatusAndAndMarkActivationComplete(subscription, AdministrationState.ACTIVE, status);
            return;
        }
        sendTasks(tasks, subscription, command);
    }

    private void activateNodeWithoutPreCheck(final List<Node> nodes, final Subscription subscription) {
        final List<MediationTaskRequest> tasks = subscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, subscription, false);
        if (tasks == null || tasks.isEmpty()) {
            logger.error("Cannot activate scanner because created task is null/not created");
            return;
        }
        sender.sendTasksForNodesWithPmFunctionOn(tasks);
    }

    private List<MediationTaskRequest> createActivationTaskRequests(final List<Node> nodeList, final Subscription activateSub) {
        logger.debug("Creating Performance Monitoring Activation Mediation Tasks for {} nodes in subscription {}", nodeList.size(),
                activateSub.getName());
        final List<MediationTaskRequest> mediationTaskRequests = subscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodeList,
                activateSub, true);
        return (mediationTaskRequests == null) ? Collections.<MediationTaskRequest>emptyList() : mediationTaskRequests;
    }

    private Set<String> sendTasks(final List<MediationTaskRequest> tasks, final Subscription subscription, final PMICLog.Command command) {
        logger.debug("Sending Performance Monitoring Activation Tasks to Mediation client for subscription {}", subscription.getName());
        final List<MediationTaskRequest> skippedTasks = sender.sendTasksForNodesWithPmFunctionOn(tasks);
        systemRecorder.commandFinishedSuccess(command, subscription.getId().toString(),
                "%s Performance Monitoring Activation skipped for %s of %s tasks for subscription: %s", subscription.getType().name(),
                skippedTasks.size(), tasks.size(), subscription.getId());
        subscriptionOperationExecutionTrackingCacheWrapper.removeEntry(subscription.getId());
        return initiationEventUtil.getFdnsFromMediationTaskRequests(skippedTasks);
    }

    private List<Node> getNodesWithPmFunctionOnAndUpdateErrorNodeCache(final long subscriptionId, final Subscription subscription,
                                                                       final List<Node> nodes) {
        final List<Node> nodesWithPmFunctionOn = new ArrayList<>();
        for (final Node node : nodes) {
            logger.info("PmEnabled cache value {} on node {} ", nodeService.isPmFunctionEnabled(node.getFdn()), node.getFdn());
            if (nodeService.isPmFunctionEnabled(node.getFdn())) {
                nodesWithPmFunctionOn.add(node);
            } else {
                logger.info("Cannot activate subscription {} with id {} on node {} because pm function is off. Adding entry to error node cache",
                    subscription.getName(), subscription.getId(), node.getFdn());
                pmFunctionOffErrorNodeCache.addNodeWithPmFunctionOff(node.getFdn(), subscriptionId);
            }
        }
        return nodesWithPmFunctionOn;
    }

    private void updateActivationTimeAndAdminState(final Subscription subscription) throws DataAccessException {
        final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
        if (AdministrationState.SCHEDULED.equals(subscription.getAdministrationState())) {
            subscription.setAdministrationState(AdministrationState.ACTIVATING);
            map.put(Subscription.Subscription220Attribute.administrationState.name(), AdministrationState.ACTIVATING.name());
        }
        final Date date = new Date();
        subscription.setActivationTime(date);
        map.put(Subscription.Subscription220Attribute.activationTime.name(), date);
        subscriptionWriteOperationService.updateAttributes(subscription.getId(), map);
        subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));
    }

    private void updateSubscriptionStatusAndAndMarkActivationComplete(final Subscription subscription, final AdministrationState adminState,
                                                                      final TaskStatus status) throws DataAccessException {
        subscription.setAdministrationState(adminState);
        subscription.setTaskStatus(status);
        final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
        map.put(Subscription.Subscription220Attribute.administrationState.name(), adminState.name());
        map.put(Subscription.Subscription220Attribute.taskStatus.name(), status.name());
        subscriptionWriteOperationService.updateAttributes(subscription.getId(), map);
        subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));
        subscriptionOperationExecutionTrackingCacheWrapper.removeEntry(subscription.getId());
    }

    private boolean isSystemDefinedStatisticalSubscriptionWithPredefScanners(final Subscription sysDefSub) {
        // It returns true in case of system defined statistical subscription SYSTEM_DEF with predef scanners supported
        // False on all other statistical subscriptions and on system defined with no predef scanner support (eg. EPG, MGW, BSC).
        boolean hasPredefScanner = false;
        if (SubscriptionType.STATISTICAL == sysDefSub.getType() && UserType.SYSTEM_DEF == sysDefSub.getUserType()) {
            hasPredefScanner = systemDefinedSubscriptionManager.hasSubscriptionPredefinedScanner(sysDefSub.getName());
        }
        return SubscriptionType.STATISTICAL == sysDefSub.getType() && UserType.SYSTEM_DEF == sysDefSub.getUserType() && hasPredefScanner;
    }

    private List<Node> getFilteredNodesToActivate(final Subscription subscription, final List<Node> nodes,
                                                  final InitiationEventType initiationEventType)
            throws DataAccessException {
        final List<Node> nodestoActivate = new ArrayList<>();
        if (subscription instanceof CellTraceSubscription && ebsSubscriptionHelper.isEbsStreamOnlyCategory((CellTraceSubscription) subscription)) {
            final Map<String, Set<EventInfo>> nodeToActivatedEbsEventMapping = ebsSubscriptionHelper.getActiveEventNodeMap(subscription.getId(),
                    initiationEventType);
            for (final Node node : nodes) {
                final List<EventInfo> ebsEvents = ((CellTraceSubscription) subscription).getEbsEvents();
                final Set<EventInfo> activatedEbsEvents = nodeToActivatedEbsEventMapping.get(node.getName());
                if (activatedEbsEvents != null && activatedEbsEvents.containsAll(ebsEvents)) {
                    ebsSubscriptionHelper.createPmicSubScannerInfoForNode(subscription, node);
                } else {
                    nodestoActivate.add(node);
                }
            }
            return nodestoActivate;
        } else {
            return nodes;
        }
    }
}
