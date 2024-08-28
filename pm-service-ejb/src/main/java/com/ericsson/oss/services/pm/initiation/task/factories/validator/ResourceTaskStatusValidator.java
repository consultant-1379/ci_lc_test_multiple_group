/*
 * COPYRIGHT Ericsson 2017
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.task.factories.validator;

import static com.ericsson.oss.services.pm.initiation.utils.PmFunctionUtil.PmFunctionPropertyValue.PM_FUNCTION_LEGACY;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription.ResourceSubscription120Attribute;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;
import com.ericsson.oss.services.pm.ebs.utils.EbsSubscriptionHelper;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.generic.PmSubScannerService;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.task.TaskStatusValidator;
import com.ericsson.oss.services.pm.initiation.utils.PmFunctionConfig;
import com.ericsson.oss.services.pm.initiation.utils.PmFunctionUtil.PmFunctionPropertyValue;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;

/**
 * Task status validator for Resource subscriptions
 */
@ApplicationScoped
public class ResourceTaskStatusValidator extends AbstractTaskStatusValidator implements TaskStatusValidator<Subscription> {

    private static final int DEFAULT_NUMBER_OF_SCANNERS_PER_NODE = 1;

    @Inject
    protected NodeService nodeService;

    @Inject
    private ScannerService scannerService;

    @Inject
    private PmSubScannerService subScannerService;

    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;

    @Inject
    PmFunctionConfig pmFunctionConfig;

    @Inject
    protected Logger logger;

    @Override
    protected boolean isTaskStatusError(final Subscription subscription) throws DataAccessException {
        if (subscription == null) {
            return false;
        }
        return isSystemDefinedStatisticalSubscription(subscription)
                ? validateSystemDefinedSubscriptionResourceCount(subscription)
                : validateSubscriptionResourceCount(getSubscriptionNodeCount(subscription), subscription);
    }

    /**
     * Checks if task status is Error in case number of active scanners does not equal number of nodes in subscription or number of active scanners
     * does not equal number of imsi associated with node in UETR subscription. Specific behavior needed on Baseband RadioNode where multiple scanners
     * can be associated to a System Defined Statistical Subscription.
     *
     * @param nodes
     *         number of nodes associated to subscription after update
     * @param subscription
     *         Subscription
     *
     * @return true is Error else false
     * @throws DataAccessException
     *         will be thrown when an exception from database is thrown
     * @throws IllegalArgumentException
     *         will be thrown when invalid value is provided to update
     * @throws RuntimeDataAccessException
     *         - if a retriable exception is thrown from database.
     */
    private boolean isTaskStatusError(final int nodes, final Subscription subscription) throws DataAccessException {
        if (subscription == null) {
            return false;
        }
        return isSystemDefinedStatisticalSubscription(subscription)
                ? validateSystemDefinedSubscriptionResourceCount(subscription)
                : validateSubscriptionResourceCount(nodes, subscription);
    }

    /**
     * Validates the subscription's task status and admin state based on the number of nodes and scanners the subscription has Deactivates the
     * subscription if it has no nodes and sets the task status if necessary
     *
     * @param subscription
     *         - The subscription
     */
    @Override
    public void validateTaskStatusAndAdminState(final Subscription subscription) {
        validateTaskStatusAndAdminState(subscription, ((ResourceSubscription) subscription).getNodesFdns());
    }

    /**
     * Validates the subscription's task status and admin state based on the number of nodes and scanners the subscription has Deactivates the
     * subscription if it has no nodes and sets the task status if necessary
     *
     * @param subscription
     *         - The subscription
     */
    @Override
    public void validateTaskStatusAndAdminState(final Subscription subscription, final String nodeFdn) {
        validateTaskStatusAndAdminState(subscription, Collections.singleton(nodeFdn));
    }

    /**
     * Validates the subscription's task status and admin state based on the number of nodes and scanners the subscription has Deactivates the
     * subscription if it has no nodes and sets the task status if necessary
     *
     * @param subscription
     *         - The subscription
     * @param nodesToBeVerified
     *         - The list of node that need to be verified for subscription
     */
    @Override
    public void validateTaskStatusAndAdminState(final Subscription subscription, final Set<String> nodesToBeVerified) {
        try {
            if (AdministrationState.ACTIVE.equals(subscription.getAdministrationState())) {
                final PmFunctionPropertyValue pmFunctionPropertyValue = pmFunctionConfig.getPmFunctionConfig();
                Set<String> nodeFdnsToBeRemoved = new HashSet<>();
                if (PM_FUNCTION_LEGACY != pmFunctionPropertyValue) {
                    if (shouldRemoveNodeWithPmFunctionOff(subscription)) {
                        nodeFdnsToBeRemoved = getNodeFdnsToBeRemoved(nodesToBeVerified);
                    }
                    logger.debug("nodesToBeVerified: {} - nodeFdnsToBeRemoved: {}", nodesToBeVerified, nodeFdnsToBeRemoved);
                }
                final int nodesCount = getSubscriptionNodeCount(subscription) - nodeFdnsToBeRemoved.size();
                logger.info("Subscription {} nodesCount: {}", subscription.getName(), nodesCount);
                if (nodesCount == 0) {
                    updateSubscriptionToInactiveAndRemoveNodeAssociations(nodesCount, subscription, nodeFdnsToBeRemoved);
                } else {
                    updateSubscription(nodesCount, subscription, nodeFdnsToBeRemoved, subscription.getTaskStatus());
                }
            }
        } catch (final DataAccessException | IllegalArgumentException exception) {
            logger.error("Unable to validate subscription {} with id {}. Exception message {}", subscription.getName(), subscription.getId(),
                    exception.getMessage());
            logger.info("Unable to validate subscription {} with id {}.", subscription.getName(), subscription.getId(), exception);
        }
    }

    /**
     * gets nodes count for Subscription
     *
     * @param subscription
     *         - The subscription
     *
     * @return count of nodes in Subscription
     * @throws DataAccessException
     *         - will be thrown when an exception from database is thrown
     */
    protected int getSubscriptionNodeCount(final Subscription subscription) throws DataAccessException {
        final ResourceSubscription resourceSubscription = (ResourceSubscription) subscription;
        if (resourceSubscription.getNodes().isEmpty()) {
            return nodeService.countBySubscriptionId(resourceSubscription.getId());
        }
        return resourceSubscription.getNodes().size();
    }

    /**
     * update subscription admin state
     *
     * @param nodes
     *         - The nodes
     * @param subscription
     *         - The subscription
     * @param nodeFdnsToBeRemoved
     *         - The nodeFdnsToBeRemoved
     *
     * @throws DataAccessException
     *         - will be thrown when an exception from database is thrown
     */
    void updateSubscriptionToInactiveAndRemoveNodeAssociations(final int nodes, final Subscription subscription, final Set<String> nodeFdnsToBeRemoved)
            throws DataAccessException {
        logger.info("Changing admin state to INACTIVE for subscription {} and Id {}", subscription.getName(), subscription.getId());
        subscription.setAdministrationState(AdministrationState.INACTIVE);
        final Map<String, Object> attributes = Subscription.getMapWithPersistenceTime();
        attributes.put(Subscription.Subscription220Attribute.administrationState.name(), AdministrationState.INACTIVE.name());
        attributes.put(ResourceSubscription.ResourceSubscription120Attribute.numberOfNodes.name(), nodes);
        final Map<String, Set<String>> associationsToRemove = getSubscriptionNodesToBeRemoved(subscription, nodeFdnsToBeRemoved);
        subscription.setPersistenceTime((Date) attributes.get(Subscription.Subscription220Attribute.persistenceTime.name()));
        subscriptionWriteOperationService.update(subscription.getId(), attributes, null, associationsToRemove);
    }

    /**
     * update subscription
     *
     * @param nodes
     *         - The nodes
     * @param subscription
     *         - The subscription
     * @param nodeFdnsToBeRemoved
     *         - The nodeFdnsToBeRemoved
     * @param currentTaskStatus
     *         - The currentTaskStatus
     * @throws DataAccessException
     *         - will be thrown when an exception from database is thrown
     */
    void updateSubscription(final int nodes, final Subscription subscription, final Set<String> nodeFdnsToBeRemoved,
                                      final TaskStatus currentTaskStatus) throws DataAccessException {
        Map<String, Object> attributes = updateSubscriptionTaskStatusAndGetAttributesToBeUpdated(nodes, subscription, currentTaskStatus);
        final Map<String, Set<String>> associationsToRemove = getSubscriptionNodesToBeRemoved(subscription, nodeFdnsToBeRemoved);
        if (attributes != null || associationsToRemove != null) {
            if (associationsToRemove != null) {
                attributes = attributes == null ? new HashMap<>() : attributes;
                attributes.put(ResourceSubscription.ResourceSubscription120Attribute.numberOfNodes.name(), nodes);
            }
            subscriptionWriteOperationService.update(subscription.getId(), attributes, null, associationsToRemove);
        }
    }

    private Map<String, Object> updateSubscriptionTaskStatusAndGetAttributesToBeUpdated(final int nodes, final Subscription subscription,
                                                                                        final TaskStatus currentTaskStatus) {
        if (subscription == null) {
            return null;
        }
        Map<String, Object> attributes = null;
        try {
            final TaskStatus taskStatus = isTaskStatusError(nodes, subscription) ? TaskStatus.ERROR : TaskStatus.OK;
            if (currentTaskStatus != taskStatus) {
                logger.info("Setting task status for subscription {} with id {} to {}", subscription.getName(), subscription.getId(), taskStatus);
                subscription.setTaskStatus(taskStatus);
                attributes = Subscription.getMapWithPersistenceTime();
                attributes.put(Subscription.Subscription220Attribute.taskStatus.name(), taskStatus.name());
                subscription.setPersistenceTime((Date) attributes.get(Subscription.Subscription220Attribute.persistenceTime.name()));
            } else {
                logger.debug("Task status of subscription {} is already {}", subscription.getName(), taskStatus);
            }
        } catch (final DataAccessException exception) {
            logger.error("Unable to get taskStatus for subscription {} with id {}. Exception message {}",
                    subscription.getName(), subscription.getId(), exception.getMessage());
            logger.info("Unable to get taskStatus for subscription {} with id {}.", subscription.getName(), subscription.getId(), exception);
        }
        return attributes;
    }

    /**
     * gets nodeFdns to be removed from Subscription
     *
     * @param subscription
     *         - The subscription
     * @param nodeFdnsToBeRemoved
     *         - The list of nodeFdns to be removed
     *
     * @return associations nodeFdns to be removed
     */
    protected Map<String, Set<String>> getSubscriptionNodesToBeRemoved(final Subscription subscription, final Set<String> nodeFdnsToBeRemoved) {
        Map<String, Set<String>> associationsToRemove = null;
        if (!nodeFdnsToBeRemoved.isEmpty()) {
            associationsToRemove = new HashMap<>();
            associationsToRemove.put(ResourceSubscription120Attribute.nodes.name(), nodeFdnsToBeRemoved);
            logger.debug("Associations to remove {} for subscription {} and Id {}", nodeFdnsToBeRemoved, subscription.getName(),
                    subscription.getId());
        }
        return associationsToRemove;
    }

    private boolean isSystemDefinedStatisticalSubscription(final Subscription subscription) {
        return subscription.getType().equals(SubscriptionType.STATISTICAL)
                && subscription.getUserType().equals(UserType.SYSTEM_DEF);
    }

    private boolean validateSubscriptionResourceCount(final int nodes, final Subscription subscription) throws DataAccessException {
        final int resourceCount = getSubscriptionResourceCount(nodes, subscription);
        if (isSharedScannerSubscription(subscription)) {
            final int scannersCount = scannerService.countBySubscriptionIdAndScannerStatus(Collections.singleton(subscription.getId()),
                    ScannerStatus.ACTIVE);
            final int subScannerCount = subScannerService.countBySubscriptionIdAndParentScannerStatus(Collections.singleton(subscription.getId()),
                    ScannerStatus.ACTIVE);
            logger.debug("resourceCount count is {}, scanner count is {} and subscanner count is {} ", resourceCount, scannersCount, subScannerCount);
            return resourceCount != scannersCount + subScannerCount;
        } else {
            final int scannersCount = scannerService.countBySubscriptionIdAndScannerStatus(Collections.singleton(subscription.getId()),
                    ScannerStatus.ACTIVE);
            logger.debug("resourceCount count is {} and scanner count is {}", resourceCount, scannersCount);
            return resourceCount != scannersCount;
        }
    }

    private boolean validateSystemDefinedSubscriptionResourceCount(final Subscription subscription) throws DataAccessException {
        int scannersCount = scannerService.countBySubscriptionIdAndScannerStatus(Collections.singleton(subscription.getId()), ScannerStatus.ACTIVE);
        logger.info("{} Number of active scanners counts : {} ", subscription.getName(), scannersCount);
        if (scannersCount == 0) {
            return true;
        }

        // if the number of not active scanners (means status= ERROR or INACTIVE or UNKNOWN) is not zero, then the subscription is in error
        scannersCount = scannerService.countBySubscriptionIdAndScannerStatus(Collections.singleton(subscription.getId()),
                ScannerStatus.UNKNOWN, ScannerStatus.INACTIVE, ScannerStatus.ERROR);
        logger.info("{} Number of non active scanners counts : {} ", subscription.getName(), scannersCount);
        return scannersCount > 0;
    }

    private Set<String> getNodeFdnsToBeRemoved(final Set<String> nodeFdns) {
        final Set<String> nodeFdnsToRemove = new HashSet<>();
        for (final String nodeFdn : nodeFdns) {
            if (!nodeService.isPmFunctionEnabled(nodeFdn)) {
                nodeFdnsToRemove.add(nodeFdn);
            }
        }
        return nodeFdnsToRemove;
    }

    /**
     * Find all subscription Resource count (node, imsi).
     *
     * @param nodes
     *         number of nodes associated to subscription after update
     * @param subscription
     *         - The subscription we want to find the task status of.
     *
     * @return - count of resources
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    protected int getSubscriptionResourceCount(final int nodes, final Subscription subscription) throws DataAccessException {
        return nodes * getExpectedNumberOfScannersPerNode(subscription);
    }

    /**
     * Get Expected count of scanner per Node
     *
     * @param subscription
     *         - The subscription object to audit
     *
     * @return numberOfScannersPerNode
     */
    protected int getExpectedNumberOfScannersPerNode(final Subscription subscription) {
        return DEFAULT_NUMBER_OF_SCANNERS_PER_NODE;
    }

    /**
     * Shared Scanner Subscription.
     *
     * @param subscription
     *         - The subscription to be Validated
     *
     * @return numberOfScannersPerNode
     */
    protected boolean isSharedScannerSubscription(final Subscription subscription) {
        return false;
    }

    /**
     * Behaviour (subscription based) in case of node with PmFunctionOff.
     *
     * @param subscription
     *         - The subscription to be Validated
     *
     * @return true if node should be removed by subscription
     */
    private boolean shouldRemoveNodeWithPmFunctionOff(final Subscription subscription) {
        return UserType.USER_DEF.equals(subscription.getUserType()) && !EbsSubscriptionHelper.isASR(subscription);
    }
}
