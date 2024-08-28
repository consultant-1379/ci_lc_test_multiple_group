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

package com.ericsson.oss.services.pm.initiation.ha.operations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.PmJobService;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventController;
import com.ericsson.oss.services.pm.initiation.task.TaskStatusValidator;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;

/**
 * The type Initiation ha manager.
 */
public class InitiationHaManager {

    @Inject
    private Logger logger;
    @Inject
    private InitiationEventController controller;
    @Inject
    private PMICInitiationTrackerCache initiationResponseCacheWrapper;
    @Inject
    @SubscriptionTaskStatusValidation
    private TaskStatusValidator<Subscription> taskStatusValidator;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private PmJobService pmJobService;
    @Inject
    private ScannerService scannerService;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;

    /**
     * Handle unfinished tasks.
     *
     * @return true if the subscriptions were handled, false otherwise. For Example, if an exception is thrown, false will be returned to let the
     * caller retry after some time.
     */
    @LogProfiler(name = "StartupService/MembershipChange restarting initiation trackers for *ING subscriptions without tracker")
    public boolean handleUnfinishedTasks() {
        if (!dpsAvailabilityStatus.isAvailable()) {
            logger.warn("Failed to update *ING subscriptions, Dps not available");
            return false;
        }
        try {
            final AdministrationState[] initiatingStates = {AdministrationState.ACTIVATING, AdministrationState.DEACTIVATING,
                AdministrationState.UPDATING};
            final List<Subscription> subscriptionsList = subscriptionReadOperationService
                .findAllBySubscriptionTypeAndAdministrationState(null, initiatingStates,
                    false);
            if (!subscriptionsList.isEmpty()) {
                final List<Subscription> subsNotInInitiationCacheList = retrieveSubscriptionsNotInInitiationTrackerCache(subscriptionsList);
                for (final Subscription subscription : subsNotInInitiationCacheList) {
                    if (AdministrationState.UPDATING == subscription.getAdministrationState()) {
                        logger.info("Found subscription {}, {} with {} admin state without a tracker. Will update admin state to ACTIVE/INACTIVE",
                            subscription.getName(), subscription.getId(), subscription.getAdministrationState());
                        updateAdminStateAndTaskStatusOfSubscriptionWithNoTrackerAndAdminStateUpdating(subscription);
                    } else {
                        logger.info(
                            "Found subscription {}, {} with {} admin state without a tracker. "
                                + "Restarting Activation/Deactivation for this subscription",
                            subscription.getName(), subscription.getId(), subscription.getAdministrationState());
                        controller.processEvent(subscription.getId(), subscription);
                    }
                }
            }
            return true;
        } catch (final Exception e) {
            logger.error("Error occurred trying retrieve subscriptions from DPS");
            logger.info("An Error occurred trying to handle unfinished Tasks {}", e);
            return false;
        }

    }

    private void updateAdminStateAndTaskStatusOfSubscriptionWithNoTrackerAndAdminStateUpdating(final Subscription subscription) {
        try {
            if (subscriptionHasOnlyInactiveProcesses(subscription)) {
                setSubscriptionStateAndTaskStatus(subscription, AdministrationState.INACTIVE, TaskStatus.OK);
            } else {
                final TaskStatus status = taskStatusValidator.getTaskStatus(subscription);
                setSubscriptionStateAndTaskStatus(subscription, AdministrationState.ACTIVE, status);
            }
        } catch (final DataAccessException | RuntimeDataAccessException exception) {
            logger.error("Cannot resolve UPDATING subscription {} with id {} because of database connection error. User action is required. "
                + "Suggested action: Update subscription's admin state to either Inactive or Active depending on subscription's active " + "processes"
                + "Exception thrown: {}", subscription.getName(), subscription.getId(), exception.getMessage());
            logger.info("Exception thrown: ", exception);
        }
    }

    private void setSubscriptionStateAndTaskStatus(final Subscription subscription, final AdministrationState administrationState,
                                                   final TaskStatus taskStatus) {
        logger.info("Subscription {} with id {} is updating admin state to {} and task status to {}", subscription.getName(), subscription.getId(),
            administrationState, taskStatus);
        try {
            subscriptionWriteOperationService.updateSubscriptionStateActivationTimeAndTaskStatus(subscription, administrationState, taskStatus);
        } catch (final DataAccessException e) {
            logger.error("Unable to update the status to {} for subscription {} with ID {}, due to an issue with DPS. Exception: {}",
                taskStatus.name(), subscription.getName(), subscription.getId(), e.getMessage());
            logger.info("Exception stacktrace: ", e);
        }
    }

    private boolean subscriptionHasOnlyInactiveProcesses(final Subscription subscription) throws DataAccessException {
        if (Subscription.isPmJobSupported(subscription.getType())) {
            return pmJobService.countAllBySubscriptionIdAndPmJobStatus(subscription.getId(), PmJobStatus.ACTIVE, PmJobStatus.ERROR,
                PmJobStatus.UNKNOWN) == 0;
        }
        return scannerService.countBySubscriptionIdAndScannerStatus(Collections.singleton(subscription.getId()), ScannerStatus.ACTIVE,
            ScannerStatus.ERROR, ScannerStatus.UNKNOWN) == 0;
    }

    private List<Subscription> retrieveSubscriptionsNotInInitiationTrackerCache(final List<Subscription> subscriptions) {
        final List<Subscription> subscriptionListWithoutTask = new ArrayList<>();
        for (final Subscription sub : subscriptions) {
            if (!initiationResponseCacheWrapper.isTracking(sub.getIdAsString())) {
                subscriptionListWithoutTask.add(sub);
            }
        }
        return subscriptionListWithoutTask;
    }

}
