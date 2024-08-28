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

package com.ericsson.oss.services.pm.scheduling.impl;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.ACTIVATE_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.DEACTIVATE_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_DEACTIVATE_SUBSCRIPTION;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import javax.ejb.Local;
import javax.ejb.ScheduleExpression;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper;
import com.ericsson.oss.services.pm.initiation.notification.events.Activate;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEvent;
import com.ericsson.oss.services.pm.initiation.notification.model.InitiationScheduleModel;
import com.ericsson.oss.services.pm.scheduling.api.SchedulerServiceInitiationLocal;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;

/**
 * The Initiation scheduler service bean.
 */
@Stateless
@Local(SchedulerServiceInitiationLocal.class)
public class InitiationSchedulerServiceBean implements SchedulerServiceInitiationLocal {

    @Inject
    private Logger logger;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private TimerService timerService;
    @Inject
    @Activate
    private InitiationEvent activationEvent;
    @Inject
    private SubscriptionOperationExecutionTrackingCacheWrapper subscriptionOperationExecutionTrackingCacheWrapper;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;

    /**
     * On Timeout.
     *
     * @param timer
     *         the timer that contains the Initiation schedule model
     */
    @Timeout
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void timeout(final Timer timer) {
        final InitiationScheduleModel taskModel = (InitiationScheduleModel) timer.getInfo();
        final String subId = String.valueOf(taskModel.getSubscriptionId());
        if (AdministrationState.ACTIVATING == taskModel.getEventType()) {
            systemRecorder.commandStarted(ACTIVATE_SUBSCRIPTION, subId, "Activation command started for subscription %s", subId);
            activationEvent.execute(taskModel.getSubscriptionId());
        } else if (AdministrationState.DEACTIVATING == taskModel.getEventType()) {
            systemRecorder.commandStarted(DEACTIVATE_SUBSCRIPTION, subId, "Deactivation command started for subscription %s", subId);
            try {
                final Subscription subscription = subscriptionReadOperationService.findOneById(taskModel.getSubscriptionId());
                if (subscription == null) {
                    logger.error("Subscription with id {} does not exist!", subId);
                } else if (subscription.getAdministrationState() != AdministrationState.DEACTIVATING) {
                    subscription.setAdministrationState(AdministrationState.DEACTIVATING);
                    final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
                    map.put(Subscription.Subscription220Attribute.administrationState.name(), AdministrationState.DEACTIVATING.name());
                    subscriptionWriteOperationService.updateAttributes(subscription.getId(), map);
                    subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));
                }
            } catch (final DataAccessException exception) {
                subscriptionOperationExecutionTrackingCacheWrapper.addEntry(Long.valueOf(subId), OPERATION_DEACTIVATE_SUBSCRIPTION);
                logger.error("Unable to find Subscription, due to problems while accessing Database {}: {}", subId, exception.getMessage());
                logger.info("Unable to find Subscription, due to problems while accessing Database {}", subId, exception);
            }
        }
    }

    @Override
    public Timer createTimer(final Serializable info, final ScheduleExpression expression, final boolean persistant) {
        final TimerConfig timerConfig = new TimerConfig(info, persistant);
        logger.debug("TimerConfig created [ {} ]", timerConfig);

        return timerService.createSingleActionTimer(expression.getStart(), timerConfig);
    }

    @Override
    public Timer getTimer(final long subscriptionId, final AdministrationState eventType) {
        Timer timerInitiationEvent = null;
        for (final Timer timer : timerService.getTimers()) {
            if (timer.getInfo() instanceof InitiationScheduleModel) {
                final InitiationScheduleModel model = (InitiationScheduleModel) timer.getInfo();
                if (model.getSubscriptionId() == subscriptionId && model.getEventType().equals(eventType)) {
                    timerInitiationEvent = timer;
                }
            }
        }
        logger.debug("Found Timer {} for subscription {} and event type {}", timerInitiationEvent, subscriptionId, eventType);
        return timerInitiationEvent;
    }
}
