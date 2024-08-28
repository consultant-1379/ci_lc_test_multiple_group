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

package com.ericsson.oss.services.pm.initiation.notification;

import static java.lang.String.format;

import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.ACTIVE;
import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.DEACTIVATING;
import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.INACTIVE;
import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.SCHEDULED;
import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.UPDATING;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.ACTIVATE_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.INVALID_SCHEDULE_INFO;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.common.logging.PMICLog;
import com.ericsson.oss.services.pm.common.logging.PMICLog.Operation;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.common.notification.EventHandler;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache;
import com.ericsson.oss.services.pm.initiation.common.SubscriptionValidationResult;
import com.ericsson.oss.services.pm.initiation.events.PmicSubscriptionChangedAttribute;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventController;
import com.ericsson.oss.services.pm.initiation.notification.senders.PmicSubscriptionUpdateMassageSender;
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionManager;
import com.ericsson.oss.services.pm.initiation.validators.SubscriptionCommonValidator;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * This class listens for DPS notifications of type Subscription for DpsAttributeChangedEvent for Attribute AdministrationState.
 */
@Startup
@Singleton
@Lock(LockType.READ)
public class DpsNotificationListenerForInitiation implements EventHandler<DpsAttributeChangedEvent> {

    @Inject
    private Logger logger;

    @Inject
    private MembershipListener membershipListener;

    @Inject
    private InitiationEventController initiationEventController;

    @Inject
    private SubscriptionCommonValidator subscriptionCommonValidator;

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    @Inject
    private PMICInitiationTrackerCache initiationResponseCacheWrapper;

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private SubscriptionManager subscriptionManager;

    @Inject
    private PmicSubscriptionUpdateMassageSender pmicSubscriptionUpdateMassageSender;

    @Override
    public void onEvent(final DpsAttributeChangedEvent event) {
        final int initiationTrackerCacheSize = initiationResponseCacheWrapper.getAmountOfTrackers();
        logger.debug("Total Subscriptions in PmicInitiationTrackerCache : {}", initiationTrackerCacheSize);
        logger.debug("Received dps notification for PM Initiation with subscription {}", event);
        final Set<AttributeChangeData> changedAttributes = event.getChangedAttributes();
        final List<PmicSubscriptionChangedAttribute> pmicSubscriptionChangedAttributeList = new ArrayList<>();

        for (final AttributeChangeData changedData : changedAttributes) {
            logger.trace("Name of the changed attribute : {}", changedData.getName());
            logger.trace("Old Value of the changed attribute : {}", changedData.getOldValue());
            logger.trace("Updated Value of the changed attribute : {}", changedData.getNewValue());

            processAttributeChangeEvents(event, pmicSubscriptionChangedAttributeList, changedData);
            final String message = getMessage(event.getPoId(), changedData);
            systemRecorder.eventCoarse(PMICLog.Event.SUBSCRIPTION_OPERATION, event.getPoId().toString(), message);
        }

        pmicSubscriptionUpdateMassageSender.sendNotificationToExternalConsumer(pmicSubscriptionChangedAttributeList);
    }

    private String getMessage(final Object subscriptionId, final AttributeChangeData changedData) {
        return format("Received event for subscription attribute change, name of attribute: %s, old value: %s , new Value : %s for Subscription %s",
                changedData.getName(), changedData.getOldValue(), changedData.getNewValue(), subscriptionId);
    }

    private void processAttributeChangeEvents(final DpsAttributeChangedEvent event,
                                              final List<PmicSubscriptionChangedAttribute> pmicSubscriptionChangedAttributeList,
                                              final AttributeChangeData changedData) {

        final AdministrationState administrationState = AdministrationState.fromString((String) changedData.getNewValue());
        if (administrationState == null) {
            logger.warn("Not supported Subscription, administrationState is null for {}", event.getPoId());
            return;
        }
        final Long poId = event.getPoId();
        logger.info("processAttributeChangeEvents : {} for subscriptionId : {}", administrationState, poId);
        final Subscription subscription = getSubscription(administrationState, poId);

        if (subscription == null) {
            logger.warn("Subscription {} not found while receiving initiation event {}s.", poId, administrationState);
            return;
        } else {
            subscriptionManager.removeSubscriptionFromCache(subscription);
        }

        if (isScheduledOrImmediateActivatingOrImmediateDeactivating(changedData)) {
            logger.debug("Received event for new Administration state {} for Subscription {}", changedData.getNewValue(), event);
            processSubscriptionEvent(subscription, administrationState);
        } else if (isDeactivatedAsScheduledOrDeactivatedAsUpdating(changedData)) {
            logger.debug("Received DeActivation for {} Subscription {}", changedData.getOldValue(), event);
            processSubscriptionEvent(subscription, INACTIVE);
        } else if (isDeactivatedDuringUpdating(changedData)) {
            logger.debug("Received DeActivation during updating for Subscription {}", event);
            initiationResponseCacheWrapper.stopTracking(event.getPoId().toString());
            processSubscriptionEvent(subscription, DEACTIVATING);
        }
        if (changedData.getName().equals(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
                && AdministrationState.fromString((String) changedData.getNewValue()).isOneOf(ACTIVE, INACTIVE)) {
            logger.debug("Received {} to {} state for Subscription {}", changedData.getOldValue(), changedData.getNewValue(), event);
            pmicSubscriptionChangedAttributeList.addAll(pmicSubscriptionUpdateMassageSender.prepareNotificationMessageForExternalConsumer(event,
                    changedData, AdministrationState.fromString((String) changedData.getNewValue())));
        }
    }

    private boolean isScheduledOrImmediateActivatingOrImmediateDeactivating(final AttributeChangeData changedData) {
        return isScheduled(changedData) || isImmediateActivating(changedData) || isImmediateDeactivating(changedData);
    }

    private boolean isScheduled(final AttributeChangeData changedData) {
        return changedData.getName().equals(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) && changedData.getOldValue().equals(INACTIVE.name())
                && changedData.getNewValue().equals(SCHEDULED.name());
    }

    private boolean isImmediateActivating(final AttributeChangeData changedData) {
        return changedData.getName().equals(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) && !changedData.getOldValue().equals(SCHEDULED.name())
                && changedData.getNewValue().equals(AdministrationState.ACTIVATING.name());
    }

    private boolean isImmediateDeactivating(final AttributeChangeData changedData) {
        return PMIC_ATT_SUBSCRIPTION_ADMINSTATE.equals(changedData.getName()) && isTransition(changedData, ACTIVE, DEACTIVATING);
    }

    private boolean isDeactivatedAsScheduledOrDeactivatedAsUpdating(final AttributeChangeData changedData) {
        return isDeactivateAScheduled(changedData) || isDeactivatedAsUpdating(changedData);
    }

    private boolean isDeactivateAScheduled(final AttributeChangeData changedData) {
        return PMIC_ATT_SUBSCRIPTION_ADMINSTATE.equals(changedData.getName()) && isTransition(changedData, SCHEDULED, INACTIVE);
    }

    private boolean isDeactivatedAsUpdating(final AttributeChangeData changedData) {
        return PMIC_ATT_SUBSCRIPTION_ADMINSTATE.equals(changedData.getName()) && isTransition(changedData, UPDATING, INACTIVE);
    }

    private boolean isDeactivatedDuringUpdating(final AttributeChangeData changedData) {
        return PMIC_ATT_SUBSCRIPTION_ADMINSTATE.equals(changedData.getName()) && isTransition(changedData, UPDATING, DEACTIVATING);
    }

    private boolean isTransition(final AttributeChangeData data, final AdministrationState oldState, final AdministrationState newState) {
        return oldState.name().equals(data.getOldValue()) && newState.name().equals(data.getNewValue());
    }

    private void processSubscriptionEvent(final Subscription subscription, final AdministrationState administrationState) {
        if (administrationState == subscription.getAdministrationState() && administrationState.isOneOf(INACTIVE, DEACTIVATING)
                || isValidSchedule(subscription)) {
            initiationEventController.processEvent(subscription.getId(), subscription);
        }
    }

    public Subscription getSubscription(final AdministrationState administrationState, final long poId) {
        Subscription subscription = null;
        try {
            subscription = subscriptionReadOperationService.findOneById(poId, false);
        } catch (final DataAccessException | RuntimeDataAccessException e) {
            logger.error(
                    "Was unable to process Subscription Event {}, as was unable to find subscription with ID {} through DPS. Exception message: {}",
                    administrationState, poId, e.getMessage());
            logger.info("Was unable to process Subscription Event {}, as was unable to find subscription with ID {} through DPS", administrationState,
                    poId, e);
        }
        return subscription;
    }

    private boolean isValidSchedule(final Subscription subscription) {
        try {
            final SubscriptionValidationResult result = subscriptionCommonValidator.validateScheduleInfo(subscription);
            if (result.hasErrors()) {
                logger.error("isValidSchedule HAS ERRORS {}", result.getError());
                systemRecorder.error(INVALID_SCHEDULE_INFO, ACTIVATE_SUBSCRIPTION.getSource(), subscription.getIdAsString(), Operation.ACTIVATION);
                return false;
            }
        } catch (final IllegalArgumentException | ValidationException e) {
            logger.info("Invalid Schedule info for subscription {} having error: {}", subscription.getName(), e);
            logger.warn("Invalid Schedule info for subscription {} having error: {}", subscription.getName(), e.getMessage());
            return false;
        }
        return true;
    }

    @Override
    public boolean isInterested(final DpsAttributeChangedEvent event) {
        return isSubscriptionTypeEvent(event) && membershipListener.isMaster();
    }

    private boolean isSubscriptionTypeEvent(final DpsAttributeChangedEvent event) {
        return SubscriptionType.fromModelType(event.getType()) != null;
    }

    @Override
    public Class<DpsAttributeChangedEvent> getEventClass() {
        return DpsAttributeChangedEvent.class;
    }

}
