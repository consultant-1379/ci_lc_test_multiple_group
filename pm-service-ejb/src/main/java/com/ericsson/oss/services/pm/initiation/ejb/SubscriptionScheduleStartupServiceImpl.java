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

package com.ericsson.oss.services.pm.initiation.ejb;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Singleton;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionStatus;
import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventController;
import com.ericsson.oss.services.pm.initiation.notification.model.InitiationScheduleModel;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * Subscription schedule startup service.
 */
@Singleton
public class SubscriptionScheduleStartupServiceImpl {

    private static final String SUBSCRIPTION_SCHEDULER_INIT_TIMER_NAME = "PM_SERVICE_SUBSCRIPTION_SCHEDULER_TIMER";

    private boolean isSchedulerStarted;

    @Inject
    private Logger log;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    @Inject
    private MembershipListener membershipListener;

    @Resource
    private TimerService timerService;

    @Inject
    private InitiationEventController initiationController;

    /**
     * Create timer for membership check.
     *
     * @return the future boolean value for successful timer creation
     */
    @Asynchronous
    public Future<Boolean> createTimerForMembershipCheck() {
        log.info("Creating timer to keep checking membership status and create scheduler if membership changes");
        final TimerConfig timerConfig = new TimerConfig();
        timerConfig.setInfo(SUBSCRIPTION_SCHEDULER_INIT_TIMER_NAME);
        timerConfig.setPersistent(false);
        log.info("Setting a programmatic timeout for each 10 seconds from now.");
        timerService.createIntervalTimer(0, TimeUnit.SECONDS.toMillis(10), timerConfig);
        return new AsyncResult<>(true);
    }

    /**
     * method to check membership and timers.
     */
    @Timeout
    @LogProfiler(name = "StartupService creating activation/deactivation ejb timers for scheduled subscriptions", ignoreExecutionTimeLowerThan = 1L)
    public void checkMembership() {
        log.debug("Subscription Scheduler timer running, checking if membership has changed.");
        if (membershipListener.isMaster() && !isSchedulerStarted()) {
            log.info("Switched to Master node, creating timers for Subscriptions.");
            try {
                createTimers();
                isSchedulerStarted = true;
            } catch (final DataAccessException | RuntimeDataAccessException e) {
                isSchedulerStarted = false;
                log.error("Unable to create timers for ACTIVE or SCHEDULED subscriptions, failed to find subscriptions in DPS.", e);
            }
        } else if (!membershipListener.isMaster() && isSchedulerStarted()) {
            log.info("Switched to Slave node, cancelling timers for Subscriptions.");
            cancelTimers();
            isSchedulerStarted = false;
        }
    }

    private void createTimers() throws DataAccessException, RuntimeDataAccessException {
        if (!dpsAvailabilityStatus.isAvailable()) {
            log.warn("Failed to start {} timer , Dps not available", SUBSCRIPTION_SCHEDULER_INIT_TIMER_NAME);
            return;
        }
        int createdTimers = 0;
        final AdministrationState[] administrationStates = {AdministrationState.ACTIVE, AdministrationState.SCHEDULED};
        final List<Subscription> subscriptions = subscriptionReadOperationService
                .findAllBySubscriptionTypeAndAdministrationState(null, administrationStates,
                        false);
        log.info("Creating timers for {} active Subscriptions.", subscriptions.size());
        for (final Subscription subscription : subscriptions) {
            if (subscription.getSubscriptionStatus().isOneOf(SubscriptionStatus.Scheduled, SubscriptionStatus.RunningWithSchedule)) {
                initiationController.processEvent(subscription);
                createdTimers++;
            }
        }
        log.info("Finish Creating timers for {} active Subscriptions.", createdTimers);
    }

    private void cancelTimers() {
        log.info("Cancelling Subscription timers.");
        int cancelledTimers = 0;
        final Collection<Timer> timers = timerService.getTimers();
        for (final Timer timer : timers) {
            if (timer.getInfo() instanceof InitiationScheduleModel) {
                log.info("Cancelling timer for {}", timer.getInfo());
                timer.cancel();
                cancelledTimers++;
            }
        }
        log.info("Cancelled {} Subscription timers.", cancelledTimers);
    }

    /**
     * Is scheduler started.
     *
     * @return returns true if the scheduler is started
     */
    public boolean isSchedulerStarted() {
        return isSchedulerStarted;
    }
}
