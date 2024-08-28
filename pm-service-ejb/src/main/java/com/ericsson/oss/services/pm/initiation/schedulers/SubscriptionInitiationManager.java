/*******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.initiation.schedulers;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheEntry;
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper;
import com.ericsson.oss.services.pm.initiation.notification.events.Activate;
import com.ericsson.oss.services.pm.initiation.notification.events.Deactivate;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEvent;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.oss.services.pm.time.TimeGenerator;

/**
 * Timer logic for verifying whether subscriptions are stuck in activating/deactivating/updating state. This logic works only for the initiations for
 * such event, for example, when the subscription is activated, the services code has to create and send activation MTRs for all the nodes in that
 * subscription. This is executed only one, but if the DPS in not available, in most cases the subscription is already in the ACTIVATING state. There
 * is existing logic for subscription timeout if the activation/deactivation takes longer than x amount fo time. This existing recovery mechanism
 * however only works if the Activation/Deactivation MTRS were created/sent. There are 2 points this class tries to address: 1) Automatic retry of
 * failed activating/deactivating subscriptions. 2) Recovery of subscriptions stuck in initiation state.
 */
@Stateless
public class SubscriptionInitiationManager {

    private static final long INTERVAL_IN_MILLISECONDS = TimeUnit.MINUTES.toMillis(3);

    @Inject
    private Logger logger;
    @Inject
    private TimerService timerService;
    @Inject
    private TimeGenerator timeGenerator;
    @Inject
    @Activate
    private InitiationEvent activationEvent;
    @Inject
    @Deactivate
    private InitiationEvent deactivationEvent;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private MembershipListener membershipChangeListener;
    @Inject
    private SubscriptionOperationExecutionTrackingCacheWrapper cacheWrapper;

    /**
     * timer will be created on startup
     *
     * @return - returns true if timer is created
     */
    @Asynchronous
    public Future<Boolean> createTimerOnStartup() {
        try {
            final TimerConfig timerConfig = new TimerConfig(INTERVAL_IN_MILLISECONDS, false);
            timerService.createIntervalTimer(INTERVAL_IN_MILLISECONDS, INTERVAL_IN_MILLISECONDS, timerConfig);
            logger.info("Created subscription initiation manager timer [{} milliseconds] for time delay", INTERVAL_IN_MILLISECONDS);
            return new AsyncResult<>(true);
        } catch (final Exception e) {
            logger.debug("Exception while creating timer for Subscription Initiation Manager timer");
            return new AsyncResult<>(false);
        }
    }

    /**
     * Timeout method.
     */
    @Timeout
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @LogProfiler(name = "Processing subscriptions stuck in *ING admin state", ignoreExecutionTimeLowerThan = 1L)
    public void execute() {
        if (membershipChangeListener.isMaster()) {
            logger.debug("Subscription Initiation Manager timer expired...");
            processSubscriptionStuckInBetweenStates();
        }
    }

    private void execute(final SubscriptionOperationExecutionTrackingCacheEntry cacheEntry, final InitiationEvent initiationEvent) {
        try {
            if (cacheEntry.getSubscriptionId() == null || cacheEntry.getSubscriptionId() < 1L) {
                logWarningMessageAndRemoveEntryFromCache(cacheEntry);
            }
            final Subscription subscription = subscriptionReadOperationService.findByIdWithRetry(cacheEntry.getSubscriptionId(), true);
            if (subscription == null) {
                logWarningMessageAndRemoveEntryFromCache(cacheEntry);
            }
            initiationEvent.execute(cacheEntry.getNodes(), subscription);
        } catch (final RetryServiceException | DataAccessException e) {
            logger.warn("Exception was thrown while extracting subscription with ID {} from DPS. {} cannot recover subscription's initiation "
                    + "for action {}", cacheEntry.getSubscriptionId(), this.getClass().getSimpleName(), cacheEntry.getOperation());
            logger.info("Exception stackTrace:", e);
        }
    }

    private void processSubscriptionStuckInBetweenStates() {
        final List<SubscriptionOperationExecutionTrackingCacheEntry> cacheEntries = cacheWrapper.getAllEntries();
        final Date expiredDate = new Date(timeGenerator.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
        for (final SubscriptionOperationExecutionTrackingCacheEntry entry : cacheEntries) {
            if (entry.getCreatedDate().before(expiredDate)) {
                logger.info("Found subscription {} in the initiation cache which is waiting for processing more than 5 minutes. "
                        + "Re-executing initiation for this subscription. Action is: {}", entry.getSubscriptionId(), entry.getOperation());
                switch (entry.getOperation()) {
                    case SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_ACTIVATE_SUBSCRIPTION:
                        activateSubscription(entry);
                        break;
                    case SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_ACTIVATE_NODES:
                        activateNodesForSubscription(entry);
                        break;
                    case SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_DEACTIVATE_SUBSCRIPTION:
                        deactivateSubscription(entry);
                        break;
                    case SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_DEACTIVATE_NODES:
                        deactivateNodesForSubscription(entry);
                        break;
                    default:
                        logger.warn("Cannot resolve subscription {} stuck in the initiation phase because the action to perform [{}] is not handled.",
                                entry.getSubscriptionId(), entry.getOperation());
                        break;
                }
            }
        }
    }

    private void activateSubscription(final SubscriptionOperationExecutionTrackingCacheEntry cacheEntry) {
        activationEvent.execute(cacheEntry.getSubscriptionId());
    }

    private void activateNodesForSubscription(final SubscriptionOperationExecutionTrackingCacheEntry cacheEntry) {
        execute(cacheEntry, activationEvent);
    }

    private void deactivateSubscription(final SubscriptionOperationExecutionTrackingCacheEntry cacheEntry) {
        deactivationEvent.execute(cacheEntry.getSubscriptionId());
    }

    private void deactivateNodesForSubscription(final SubscriptionOperationExecutionTrackingCacheEntry cacheEntry) {
        execute(cacheEntry, deactivationEvent);
    }

    private void logWarningMessageAndRemoveEntryFromCache(final SubscriptionOperationExecutionTrackingCacheEntry cacheEntry) {
        logger.warn(
                "Subscription with id {} does not exist in DPS. {} cannot recover subscription's initiation for action {}. "
                        + "Removing this subscription from cache.",
                cacheEntry.getSubscriptionId(), this.getClass().getSimpleName(), cacheEntry.getOperation());
        cacheWrapper.removeEntry(cacheEntry.getSubscriptionId());
    }
}
