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
import org.slf4j.LoggerFactory;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * Timer logic for verifying whether subscriptions are stuck in activating/deactivating/updating state. Other timer logic exist in the service
 * to handle subscriptions hung in transient state, but they cover different scenarios:
 * 1) InitiationDelayTimer works if the Activation/Deactivation MTRS were created/sent and it is based on PMICInitiationResponseCache.
 * 2) SubscriptionInitiationManager works if the Activation/Deactivation MTRS failed to be created/sent and it is based on
 * SubscriptionOperationExecutionTrackingCache
 * This timer covers the issue happened in TORF-199831 when the transaction to deactivate the subscription failed to commit and the entry was removed
 * from SubscriptionOperationExecutionTrackingCache, so recovery implemented by two above timers is not applicable: Activation/Deactivation MTRS not
 * sent and entry not available in the cache.
 */
@Stateless
public class SubscriptionIntermediaryStateTimer {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionIntermediaryStateTimer.class);

    private static final long INTERVAL_TIME_IN_MILLISECONDS = TimeUnit.MINUTES.toMillis(5);

    @Inject
    private TimerService timerService;
    @Inject
    private MembershipListener membershipChangeListener;
    @Inject
    private ConfigurationChangeListener configurationChangeListener;
    @Inject
    private SubscriptionIntermediaryStateHandler subscriptionIntermediaryStateHandler;

    /**
     * timer will be created on startup
     *
     * @return - returns true if timer is created
     */
    @Asynchronous
    public Future<Boolean> createTimerOnStartup() {
        try {
            createTimer(INTERVAL_TIME_IN_MILLISECONDS);
            return new AsyncResult<>(true);
        } catch (final Exception e) {
            logger.debug("Exception while creating Subscription Intermediary State timer", e);
            return new AsyncResult<>(false);
        }
    }

    /**
     * Start the timer
     *
     * @param intervalTime
     *         - interval time to create new interval timer
     */
    public void createTimer(final long intervalTime) {
        final TimerConfig timerConfig = new TimerConfig(intervalTime, false);
        timerService.createIntervalTimer(intervalTime, intervalTime, timerConfig);
        logger.info("Created subscription intermediary state timer [{} milliseconds]", intervalTime);
    }

    /**
     * On time out, apply subscriptionIntermediaryStateHandler logic
     */
    @Timeout
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void execute() {
        if (configurationChangeListener.getPmMigrationEnabled()) {
            logger.info("Subscription audit skipped due to PM Migration is turned on.");
            return;
        }
        if (membershipChangeListener.isMaster()) {
            logger.debug("Subscription Intermediary State timer expired...");
            processSubscriptionStuckInUpdatingActivatingAndDeactivatingState();
        }
    }

    private void processSubscriptionStuckInUpdatingActivatingAndDeactivatingState() {
        final List<Subscription> subscriptionStuckInUpdatingActivatingAndDeactivatingState = subscriptionIntermediaryStateHandler
                .getSubscriptionStuckInUpdatingActivatingAndDeactivatingState();
        if (subscriptionStuckInUpdatingActivatingAndDeactivatingState.isEmpty()) {
            return;
        }
        for (final Subscription subscription : subscriptionStuckInUpdatingActivatingAndDeactivatingState) {
            subscriptionIntermediaryStateHandler.updateSubscriptionStatus(subscription);
        }
    }
}
