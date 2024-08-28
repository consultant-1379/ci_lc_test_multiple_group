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

import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.INITIATION_SCHEDULE_CURRENT_TIME_OFFSET;

import java.util.Date;
import javax.ejb.ScheduleExpression;
import javax.ejb.Timer;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.ScheduleInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionStatus;
import com.ericsson.oss.services.pm.initiation.notification.model.InitiationScheduleModel;
import com.ericsson.oss.services.pm.scheduling.api.SchedulerServiceInitiationLocal;

/**
 * This controller will create timer for activation or deactivation event based
 * on the Schedule
 *
 * @author eushmar
 */
public class InitiationEventController {

    @Inject
    private SchedulerServiceInitiationLocal initiationSchedulerServiceBean;

    @Inject
    private Logger logger;

    @Inject
    @Deactivate
    private InitiationEvent deactivationEvent;

    /**
     * Process event.
     *
     * @param poId
     *         the persistence object id
     * @param subscription
     *         the subscription object to process
     */
    public void processEvent(final long poId, final Subscription subscription) {
        switch (subscription.getAdministrationState()) {
            // This case will be changed to SCHEDULED and a new case will be added for ACTIVATING where we have to trigger activationTasks
            case ACTIVATING:
            case SCHEDULED:
                createTimerForActivate(poId, subscription.getScheduleInfo());
                createTimerForDeactivate(poId, subscription.getScheduleInfo());
                break;
            case DEACTIVATING:
                final Timer deactivatingTimer = initiationSchedulerServiceBean.getTimer(poId, AdministrationState.DEACTIVATING);
                cancelTimer(deactivatingTimer, subscription.getName(), AdministrationState.DEACTIVATING);
                deactivationEvent.execute(poId);
                break;
            case INACTIVE:
                final Timer activationTimer = initiationSchedulerServiceBean.getTimer(poId, AdministrationState.ACTIVATING);
                cancelTimer(activationTimer, subscription.getName(), AdministrationState.ACTIVATING);
                final Timer deactivationTimer = initiationSchedulerServiceBean.getTimer(poId, AdministrationState.DEACTIVATING);
                cancelTimer(deactivationTimer, subscription.getName(), AdministrationState.DEACTIVATING);
                break;
            default:
                logger.warn("Unexpected Admin state {}", subscription.getAdministrationState());
                break;
        }
    }

    /**
     * Process event.
     *
     * @param subscription
     *         the subscription to process
     */
    public void processEvent(final Subscription subscription) {

        if (subscription.getSubscriptionStatus().equals(SubscriptionStatus.Scheduled)) {
            createTimerForActivate(subscription.getId(), subscription.getScheduleInfo());
            createTimerForDeactivate(subscription.getId(), subscription.getScheduleInfo());
        } else if (subscription.getSubscriptionStatus().equals(SubscriptionStatus.RunningWithSchedule)) {
            createTimerForDeactivate(subscription.getId(), subscription.getScheduleInfo());
        }
    }

    private void cancelTimer(final Timer initiationTimer, final String subscriptionName, final AdministrationState adminState) {
        if (initiationTimer == null) {
            logger.info("Timer is not available for PM Initiation event : {} for subscription {} ", adminState, subscriptionName);
        } else {
            initiationTimer.cancel();
            logger.debug("Canceled timer for PM Initiation event : {} for Subscription : {} ", adminState, subscriptionName);
        }
    }

    private void createTimer(Date date, final long poId, final AdministrationState adminState) {
        final long currentTime = System.currentTimeMillis();
        Date localDate = date;
        if (localDate.getTime() <= currentTime) {
            localDate = new Date(currentTime + INITIATION_SCHEDULE_CURRENT_TIME_OFFSET);
        }
        final ScheduleExpression expression = new ScheduleExpression();
        expression.start(localDate);
        final InitiationScheduleModel model = new InitiationScheduleModel(poId, adminState);
        initiationSchedulerServiceBean.createTimer(model, expression, false);
        logger.debug("Created timer for PM Initiation event : {} for SubscriptionId : {} to start at : {}", adminState, poId, localDate);
    }

    private void createTimerForActivate(final long poId, final ScheduleInfo schedule) {
        Date startDateTime;
        if (schedule != null && schedule.getStartDateTime() != null) {
            startDateTime = schedule.getStartDateTime();
        } else {
            startDateTime = new Date(System.currentTimeMillis() + INITIATION_SCHEDULE_CURRENT_TIME_OFFSET);
        }
        createTimer(startDateTime, poId, AdministrationState.ACTIVATING);
    }

    private void createTimerForDeactivate(final long poId, final ScheduleInfo schedule) {
        if (schedule != null && schedule.getEndDateTime() != null) {
            createTimer(schedule.getEndDateTime(), poId, AdministrationState.DEACTIVATING);
        }
    }
}
