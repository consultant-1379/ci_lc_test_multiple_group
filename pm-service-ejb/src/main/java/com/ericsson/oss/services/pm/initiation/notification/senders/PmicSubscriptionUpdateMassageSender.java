/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.notification.senders;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.SUBSCRIPTION_OPERATION;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent;
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender;
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.ebs.utils.EbsSubscriptionHelper;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.initiation.events.PmicSubscriptionChangedAttribute;
import com.ericsson.oss.services.pm.initiation.events.PmicSubscriptionUpdate;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * This class listens for DPS notifications of type Subscription for DpsAttributeChangedEvent for Attribute AdministrationState.
 */
public class PmicSubscriptionUpdateMassageSender {
    @Inject
    @Modeled
    private EventSender<PmicSubscriptionUpdate> pmicSubscriptionUpdateEventSender;

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    @Inject
    private Logger logger;

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    @Inject
    private EbsSubscriptionHelper ebsSubscriptionHelper;

    /**
     * send Notification message to External consumer .
     *
     * @param pmicSubscriptionChangedAttributeList
     *         list of changed attributes with old and new value
     */
    public void sendNotificationToExternalConsumer(final List<PmicSubscriptionChangedAttribute> pmicSubscriptionChangedAttributeList) {
        if (!pmicSubscriptionChangedAttributeList.isEmpty()) {
            final PmicSubscriptionUpdate pmicSubscriptionUpdate = new PmicSubscriptionUpdate(pmicSubscriptionChangedAttributeList);
            pmicSubscriptionUpdateEventSender.send(pmicSubscriptionUpdate);
            systemRecorder.eventCoarse(SUBSCRIPTION_OPERATION, pmicSubscriptionUpdate.toString(), "Sent subscriptionUpdate event");
        } else {
            logger.debug("No message to send for external consumer");
        }
    }

    /**
     * send Notification message to External consumer .
     *
     * @param attributeName
     *         attribute of mo
     * @param oldValue
     *         old value of attribute
     * @param newValue
     *         new value of attribute
     */
    public void sendNotficationToExternalConsumer(final String attributeName, final String oldValue, final String newValue) {
        final PmicSubscriptionUpdate pmicSubscriptionUpdate = buildPmicSubscriptionUpdateEvent(attributeName, oldValue, newValue);
        pmicSubscriptionUpdateEventSender.send(pmicSubscriptionUpdate);
    }

    /**
     * Prepare Notification message for External consumer .
     *
     * @param event
     *         Dps mo attribute change event
     * @param data
     *         the subscription attributes information which are changed
     * @param administrationState
     *         administration state of the subscription
     *
     * @return pmicSubscriptionChangedAttributeList List of changed attributes
     */
    public List<PmicSubscriptionChangedAttribute> prepareNotificationMessageForExternalConsumer(final DpsAttributeChangedEvent event,
                                                                                                final AttributeChangeData data,
                                                                                                final AdministrationState administrationState) {
        final List<PmicSubscriptionChangedAttribute> pmicSubscriptionChangedAttributeList = new ArrayList<>();

        final Subscription subscription = getSubscription(administrationState, event.getPoId());
        if (subscription == null) {
            logger.warn("Subscription {} not found while receiving initiation event {}.", event.getPoId(), administrationState);
            return pmicSubscriptionChangedAttributeList;
        }
        filterSubscriptionForNotificationMessage(data, pmicSubscriptionChangedAttributeList, subscription);
        return pmicSubscriptionChangedAttributeList;
    }

    /**
     * Prepare and send Notification message for External consumer when JMS or pmserv switch over .
     *
     * @param attributeName
     *         Dps mo attribute change event
     * @param attributeValue
     *         the subscription attributes information which are changed
     */
    public void sendNotificationOnSwitchOverToExternalConsumer(final String attributeName, final String attributeValue) {
        final List<PmicSubscriptionChangedAttribute> pmicSubscriptionChangedAttributeList = new ArrayList<>();
        PmicSubscriptionChangedAttribute pmicSubscriptionChangedAttribute = new PmicSubscriptionChangedAttribute("SwitchOver", "", "");
        pmicSubscriptionChangedAttributeList.add(pmicSubscriptionChangedAttribute);
        pmicSubscriptionChangedAttribute = new PmicSubscriptionChangedAttribute(attributeName, attributeValue, attributeValue);
        pmicSubscriptionChangedAttributeList.add(pmicSubscriptionChangedAttribute);
        sendNotificationToExternalConsumer(pmicSubscriptionChangedAttributeList);
    }

    private PmicSubscriptionUpdate buildPmicSubscriptionUpdateEvent(final String attributeName, final String oldValue, final String newValue) {
        final PmicSubscriptionChangedAttribute pmicSubscriptionChangedAttribute = new PmicSubscriptionChangedAttribute(attributeName, oldValue,
                newValue);
        final List<PmicSubscriptionChangedAttribute> pmicSubscriptionChangedAttributeList = new ArrayList<>();
        pmicSubscriptionChangedAttributeList.add(pmicSubscriptionChangedAttribute);
        return new PmicSubscriptionUpdate(pmicSubscriptionChangedAttributeList);
    }

    private void filterSubscriptionForNotificationMessage(final AttributeChangeData data,
                                                          final List<PmicSubscriptionChangedAttribute> pmicSubscriptionChangedAttributeList,
                                                          final Subscription subscription) {
        if (subscription.getType().equals(SubscriptionType.EBM)) {
            logger.trace("Preparing Notification Message For External Consumer for subscriptionId : {} Administrative State : {}",
                    subscription.getId(), subscription.getAdministrationState());
            final PmicSubscriptionChangedAttribute pmicSubscriptionChangedAttribute = new PmicSubscriptionChangedAttribute(data.getName(),
                    data.getOldValue().toString(), data.getNewValue().toString(), subscription.getType().name());
            pmicSubscriptionChangedAttributeList.add(pmicSubscriptionChangedAttribute);
        } else if (subscription.getType().equals(SubscriptionType.CELLTRACE) &&
                   ebsSubscriptionHelper.isCellTraceEbs((CellTraceSubscription)subscription)) {
            logger.trace("Preparing Notification Message For External Consumer for subscriptionId : {} Administrative State : {}",
                    subscription.getId(), subscription.getAdministrationState());
            final PmicSubscriptionChangedAttribute pmicSubscriptionChangedAttribute = new PmicSubscriptionChangedAttribute(data.getName(),
                    data.getOldValue().toString(), data.getNewValue().toString(), subscription.getType().name(),
                    ((CellTraceSubscription) subscription).getCellTraceCategory().name());
            pmicSubscriptionChangedAttributeList.add(pmicSubscriptionChangedAttribute);
        }
    }

    private Subscription getSubscription(final AdministrationState administrationState, final long poId) {
        Subscription subscription = null;
        try {
            subscription = subscriptionReadOperationService.findOneById(poId);
        } catch (final DataAccessException | RuntimeDataAccessException e) {
            logger.error(
                    "Was unable to process Subscription Event {}, as was unable to find subscription with ID {} through DPS. Exception message: {}",
                    administrationState, poId, e.getMessage());
            logger.info("Was unable to process Subscription Event {}, as was unable to find subscription with ID {} through DPS", administrationState,
                    poId, e);
        }
        return subscription;
    }
}
