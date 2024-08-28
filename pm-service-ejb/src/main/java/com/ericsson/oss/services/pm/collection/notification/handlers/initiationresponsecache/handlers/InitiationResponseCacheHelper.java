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

package com.ericsson.oss.services.pm.collection.notification.handlers.initiationresponsecache.handlers;

import java.util.Date;
import java.util.Map;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache;
import com.ericsson.oss.services.pm.initiation.cache.data.InitiationTracker;
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionManager;
import com.ericsson.oss.services.pm.initiation.task.TaskStatusValidator;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;
import com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;

/**
 * Handles the initiation response received from the DPS
 */
@Stateless
public class InitiationResponseCacheHelper {

    private static final String EXCEPTION_STACKTRACE = "Exception stacktrace: ";

    @Inject
    private Logger logger;
    @Inject
    private SubscriptionManager subscriptionManager;
    @Inject
    @SubscriptionTaskStatusValidation
    private TaskStatusValidator<Subscription> taskStatusValidator;
    @Inject
    private PMICInitiationTrackerCache pmicInitiationTrackerCache;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;
    @EJB
    private InitiationResponseCacheHelper self;

    /**
     * Process initiation response cache boolean.
     *
     * @param subscriptionId
     *     the subscription id
     * @param nodeFdn
     *     the node fully distinguished name
     *
     * @return returns true if initiation response is successfully handled
     */
    public boolean processInitiationResponseCache(final String subscriptionId, final String nodeFdn) {
        final InitiationTracker initiationTracker = pmicInitiationTrackerCache.getTracker(subscriptionId);
        if (initiationTracker == null) {
            logger.info("Initiation Tracker is null for subscription id {}. Cannot process Initiation notification", subscriptionId);
            return false;
        }

        boolean successfullyProcessed = false;
        if (initiationTracker.isTrackingActivation(nodeFdn)) {
            successfullyProcessed = processInitiationResponseCacheForActivation(subscriptionId, nodeFdn);
        }
        if (initiationTracker.isTrackingDeactivation(nodeFdn)) {
            successfullyProcessed = processInitiationResponseCacheForDeactivation(subscriptionId, nodeFdn);
        }
        return successfullyProcessed;
    }


    /**
     * Increments the received notifications on {@link InitiationTracker} for the provided subscription id. The InitiationTracker must
     * also be in the cache {@link PMICInitiationTrackerCache}. <br>
     * <br>
     * If, number_of_nodes == number_of_notifications, update subscription admin state as ACTIVE and remove this object from cache
     *
     * @param subscriptionId
     *     - the id of the subscription
     * @param nodeFdn
     *     - the Fully Distinguished Name of the Node
     *
     * @return - True if processing was successful, False otherwise
     */
    public boolean processInitiationResponseCacheForActivation(final String subscriptionId, final String nodeFdn) {
        if (!Scanner.isValidSubscriptionId(subscriptionId)) {
            logger.debug("Couldn't process Activation Response because subscription id {} for the node {} is not valid", subscriptionId, nodeFdn);
            return false;
        }
        if (pmicInitiationTrackerCache.isTracking(subscriptionId)) {
            proceedProcessingInitiationResponseForActivation(subscriptionId, nodeFdn);
            return true;
        } else {
            logger.info("Initiation Tracker is null for subscription id {}. Cannot process Activation notification for node {}", subscriptionId,
                nodeFdn);
            return false;
        }
    }

    private void proceedProcessingInitiationResponseForActivation(final String subscriptionId, final String nodeFdn) {
        pmicInitiationTrackerCache.incrementReceivedNotifications(subscriptionId, nodeFdn);
        final InitiationTracker initiationTracker = pmicInitiationTrackerCache.getTracker(subscriptionId);
        if (initiationTracker == null) {
            return;
        }
        logger.debug("Incremented notification for Subscription {} for state: {}. Received {} of {}.", subscriptionId,
            initiationTracker.getSubscriptionAdministrationState(), initiationTracker.getTotalAmountOfReceivedNotifications(),
            initiationTracker.getTotalAmountOfExpectedNotifications());
        if (initiationTracker.haveAllNotificationsBeenReceived()) {
            try {
                final Subscription subscription = subscriptionManager.getSubscriptionWrapperById(Long.valueOf(subscriptionId)).getSubscription();
                if (subscription == null) {
                    logger.error("Subscription with ID {} does not exist. All expected notifications for this subscriptionId were received but "
                            + "cannot update admin state since subscription for such ID does not exist.",
                        subscriptionId);
                    pmicInitiationTrackerCache.stopTracking(subscriptionId);
                    return;
                }
                self.updateSubscriptionAdminStateAndTaskStatus(subscription, AdministrationState.ACTIVE);
                pmicInitiationTrackerCache.stopTracking(subscriptionId);
            } catch (final DataAccessException | RuntimeDataAccessException | InvalidSubscriptionException | RetryServiceException e) {
                logger.error("Database Connection Exception received while trying to change the state of Activating subscription to ACTIVE. " +
                    "All initiation response notifications were received but exception was thrown. Exception message: {}", e.getMessage());
                logger.info(EXCEPTION_STACKTRACE, e);
            }
        }
    }

    /**
     * Increments the received notifications on {@link InitiationTracker} for the provided subscription id. The InitiationTracker must
     * also be in the cache {@link PMICInitiationTrackerCache}.<br>
     * <br>
     * If, number_of_nodes == number_of_notifications, update subscription admin state as INACTIVE update Task status as OK. remove this object from
     * cache
     *
     * @param subscriptionId
     *     - Valid Subscription id
     * @param nodeFdn
     *     - the Fully Distinguished Name of the node
     *
     * @return - True if processing was successful, False otherwise
     */
    public boolean processInitiationResponseCacheForDeactivation(final String subscriptionId, final String nodeFdn) {
        if (!Subscription.isValidSubscriptionId(subscriptionId)) {
            logger.error("Couldn't process Deactivation Response because subscription id is not valid {}", subscriptionId);
            return false;
        }
        final InitiationTracker initiationTracker = pmicInitiationTrackerCache.getTracker(subscriptionId);
        if (initiationTracker == null) {
            logger.info("Initiation Tracker is null for subscription id {}. Cannot process Deactivation notification for node {}", subscriptionId,
                nodeFdn);
            return false;
        }
        proceedProcessingInitiationResponseForDeactivation(subscriptionId, nodeFdn);
        return true;
    }

    private void proceedProcessingInitiationResponseForDeactivation(final String subscriptionId, final String nodeFdn) {
        pmicInitiationTrackerCache.incrementReceivedNotifications(subscriptionId, nodeFdn);
        final InitiationTracker initiationTracker = pmicInitiationTrackerCache.getTracker(subscriptionId);
        if (initiationTracker == null) {
            return;
        }
        logger.debug("Incremented notification for Subscription {} for state: {}. Received {} of {}.", subscriptionId,
            initiationTracker.getSubscriptionAdministrationState(), initiationTracker.getTotalAmountOfReceivedNotifications(),
            initiationTracker.getTotalAmountOfExpectedNotifications());
        final AdministrationState administrationState = AdministrationState.fromString(initiationTracker.getSubscriptionAdministrationState());
        if (initiationTracker.haveAllNotificationsBeenReceived()
            && administrationState.isOneOf(AdministrationState.DEACTIVATING, AdministrationState.UPDATING)) {
            processStopTracking(subscriptionId, administrationState);
        }
    }

    private void processStopTracking(final String subscriptionId, final AdministrationState administrationState) {
        try {
            final Subscription subscription = subscriptionManager.getSubscriptionWrapperById(Long.valueOf(subscriptionId)).getSubscription();
            if (subscription == null) {
                logger.error("Subscription with id {} does not exist. Cannot proceed processing tracker for this subscription, "
                    + "removing tracker from tracking cache.", subscriptionId);
                pmicInitiationTrackerCache.stopTracking(subscriptionId);
                return;
            }
            if (AdministrationState.DEACTIVATING == administrationState) {
                self.updateSubscriptionAdminStateAndTaskStatus(subscription, AdministrationState.INACTIVE);
                pmicInitiationTrackerCache.stopTracking(subscription.getIdAsString());
                return;
            }
            if (AdministrationState.UPDATING == administrationState) {
                if (Subscription.isPmJobSupported(subscription.getType())) {
                    self.updateSubscriptionAdminStateAndTaskStatus(subscription, AdministrationState.INACTIVE);
                } else {
                    if (hasSubscriptionAnyNodes(subscription)) {
                        self.updateSubscriptionAdminStateAndTaskStatus(subscription, AdministrationState.ACTIVE);
                    } else {
                        self.updateSubscriptionAdminStateAndTaskStatus(subscription, AdministrationState.INACTIVE);
                    }
                }
                pmicInitiationTrackerCache.stopTracking(subscription.getIdAsString());
            }
        } catch (final DataAccessException | RuntimeDataAccessException | InvalidSubscriptionException | RetryServiceException exception) {
            logger.error("Could not process initiation response tracker as there was a problem getting data using DPS. Exception {}",
                exception.getMessage());
            logger.info(EXCEPTION_STACKTRACE, exception);
        }
    }

    private boolean hasSubscriptionAnyNodes(final Subscription subscription) throws DataAccessException, InvalidSubscriptionException,
        RetryServiceException {
        return !subscriptionManager.getSubscriptionWrapperById(subscription.getId()).getAllNodeFdns().isEmpty();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public boolean updateSubscriptionAdminStateAndTaskStatus(final Subscription subscription, final AdministrationState adminState)
        throws DataAccessException {
        final TaskStatus taskStatus;
        if (adminState == AdministrationState.INACTIVE) {
            taskStatus = TaskStatus.OK;
        } else {
            taskStatus = taskStatusValidator.getTaskStatus(subscription);
        }
        subscription.setAdministrationState(adminState);
        subscription.setTaskStatus(taskStatus);
        final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
        map.put(Subscription.Subscription220Attribute.administrationState.name(), adminState.name());
        map.put(Subscription.Subscription220Attribute.taskStatus.name(), taskStatus.name());
        subscriptionWriteOperationService.updateAttributes(subscription.getId(), map);
        subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));
        return true;
    }
}
