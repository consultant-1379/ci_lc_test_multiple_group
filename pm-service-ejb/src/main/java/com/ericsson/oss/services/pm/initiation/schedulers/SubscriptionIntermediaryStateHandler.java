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

package com.ericsson.oss.services.pm.initiation.schedulers;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.pmic.util.TimeGenerator;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.initiation.cache.EbsSubscriptionInitiationQueue;
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache;
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper;
import com.ericsson.oss.services.pm.initiation.task.TaskStatusValidator;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;

/**
 * This class handles the process of subscription intermediary timeout. It changes admin state to ACTIVE/INACTIVE for subscriptions not
 * present in PMICInitiationResponseCache whose admin state is transient (ACTIVATING/DEACTIVATING/UPDATING)
 */
@Stateless
public class SubscriptionIntermediaryStateHandler {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionIntermediaryStateHandler.class);

    private static final long LAST_AUDIT_INTERVAL_TIME_IN_MILLISECONDS = TimeUnit.MINUTES.toMillis(5);

    @Inject
    private TimeGenerator timeGenerator;
    @Inject
    private PMICInitiationTrackerCache pmicInitiationTrackerCache;
    @Inject
    private TaskStatusValidator<Subscription> taskStatusValidator;
    @Inject
    private EbsSubscriptionInitiationQueue ebsSubscriptionsInitiationQueue;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;
    @Inject
    private SubscriptionOperationExecutionTrackingCacheWrapper subscriptionOperationExecutionTrackingCacheWrapper;

    /**
     * Get list of subscriptions not present in PMICInitiationResponseCache whose admin state is transient (ACTIVATING/DEACTIVATING/UPDATING)
     *
     * @return - list of subscriptions or empty list.
     */
    @LogProfiler(name = "Changing admin state to Active/Inactive for subscriptions in *ING state without a tracker",
        ignoreExecutionTimeLowerThan = 1L)
    public List<Subscription> getSubscriptionStuckInUpdatingActivatingAndDeactivatingState() {
        final AdministrationState[] adminStates = {AdministrationState.ACTIVATING, AdministrationState.DEACTIVATING, AdministrationState.UPDATING};

        final List<Subscription> subscriptionStuckInUpdatingActivatingAndDeactivatingState = new ArrayList<>();
        try {
            final List<Subscription> subscriptionsIntermediaryState = subscriptionReadOperationService
                .findAllBySubscriptionTypeAndAdministrationState(null, adminStates, true);

            for (final Subscription subscription : subscriptionsIntermediaryState) {
                if (isSubscriptionOperationStuckInIntermediaryState(subscription)) {
                    subscriptionStuckInUpdatingActivatingAndDeactivatingState.add(subscription);
                }
            }
        } catch (final RuntimeDataAccessException | IllegalArgumentException | DataAccessException exception) {
            logger.error("Subscriptions with intermediary admin state are not found due to"
                + " an underlying issue when trying to access data with the DPS. Exception message: {}", exception.getMessage());
            logger.info(
                "Subscriptions with intermediary admin state are not found due to" + " an underlying issue when trying to access data with the DPS.",
                exception);
        }
        return subscriptionStuckInUpdatingActivatingAndDeactivatingState;
    }

    /**
     * Change admin state to ACTIVE/INACTIVE for subscriptions not present in PMICInitiationResponseCache whose admin state is transient
     * (ACTIVATING/DEACTIVATING/UPDATING)
     *
     * @param subscription
     *     - subscriptions to update admin state
     */
    public void updateSubscriptionStatus(final Subscription subscription) {
        final AdministrationState adminState = subscription.getAdministrationState();
        final AdministrationState newAdminState = getNewAdminState(adminState);
        if (newAdminState != null) {
            try {
                subscriptionWriteOperationService.updateSubscriptionStateActivationTimeAndTaskStatus(subscription, newAdminState,
                    taskStatusValidator.getTaskStatus(subscription));
                logger.info("Subscription with id {} hung in {} state is set to  {}", subscription.getId(), adminState, newAdminState);
            } catch (final DataAccessException exception) {
                logger.error("Subscription {} with intermediary admin state is not processed due to an underlying issue when trying to access data"
                    + " with the DPS. Exception message: {}", subscription.getId(), exception.getMessage());
                logger.info("Subscription {} with intermediary admin state is not processed due to an underlying issue when trying to access data"
                    + "with the DPS.", subscription.getId(), exception);
            }
        }
    }

    private AdministrationState getNewAdminState(final AdministrationState adminState) {
        if (AdministrationState.ACTIVATING.equals(adminState) || AdministrationState.UPDATING.equals(adminState)) {
            return AdministrationState.ACTIVE;
        } else if (AdministrationState.DEACTIVATING.equals(adminState)) {
            return AdministrationState.INACTIVE;
        }
        return null;
    }

    /**
     * Check if a subscription operation is stuck in intermediary state.
     *
     * @param subscription
     *     - current subscription
     *
     * @return boolean
     * -true if subscription user activation or deactivation time is 5 mins older than current time stamp
     */
    private boolean isSubscriptionOperationStuckInIntermediaryState(final Subscription subscription) {
        if (pmicInitiationTrackerCache.getTracker(subscription.getIdAsString()) != null
            || subscriptionOperationExecutionTrackingCacheWrapper.containsEntry(subscription.getId())
            || ebsSubscriptionsInitiationQueue.isFirstInQueue(subscription.getIdAsString())) {
            return false;
        }
        final Date expiredDate = new Date(timeGenerator.currentTimeMillis() - LAST_AUDIT_INTERVAL_TIME_IN_MILLISECONDS);
        final Date subscriptionOperationInitiatedTime = getSubscriptionOperationInitiatedTime(subscription);
        if (subscriptionOperationInitiatedTime != null && subscriptionOperationInitiatedTime.before(expiredDate)) {
            logger.debug("Found subscription {} stuck waiting in {} state for more than {} minutes"
                , subscription.getId(), subscription.getAdministrationState(), LAST_AUDIT_INTERVAL_TIME_IN_MILLISECONDS);
            return true;
        } else if (AdministrationState.UPDATING.equals(subscription.getAdministrationState())) {
            logger.info("Found subscription {} stuck waiting in UPDATING state, no entry found in trackerCache" +
                "subscriptionOperationCache or ebsInitiationQueue", subscription.getId());
            return true;
        }
        return false;
    }

    /**
     * Get for element's expired time. If element is > 5 mins old then remove it
     *
     * @param subscription
     *     - current subscription
     *
     * @return Date
     * -UserActivationDateTime if subscription is ACTIVATING, userDeactivationTime if subscription is DEACTIVATING , current time otherwise
     */
    private Date getSubscriptionOperationInitiatedTime(final Subscription subscription) {
        if (AdministrationState.ACTIVATING.equals(subscription.getAdministrationState())) {
            return subscription.getUserActivationDateTime();
        } else if (AdministrationState.DEACTIVATING.equals(subscription.getAdministrationState())) {
            return subscription.getUserDeActivationDateTime();
        }
        return new Date(timeGenerator.currentTimeMillis());
    }
}
