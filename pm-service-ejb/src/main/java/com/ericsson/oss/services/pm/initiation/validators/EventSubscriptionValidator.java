/*
 * ------------------------------------------------------------------------------
 * ********************************************************************************
 * * COPYRIGHT Ericsson 2017
 * *
 * * The copyright to the computer program(s) herein is the property of
 * * Ericsson Inc. The programs may be used and/or copied only with written
 * * permission from Ericsson Inc. or in accordance with the terms and
 * * conditions stipulated in the agreement/contract under which the
 * * program(s) have been supplied.
 * *******************************************************************************
 * *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.initiation.validators;

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_SUBSCRITPION_TO_ACTIVATE;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.EventSubscription;
import com.ericsson.oss.pmic.dto.subscription.RpmoSubscription;
import com.ericsson.oss.pmic.dto.subscription.RttSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;
import com.ericsson.oss.services.pm.adjuster.impl.SubscriptionMetaDataService;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import org.slf4j.Logger;

/**
 * This class validates Event Subscription, Event Subscription's could extend this Interface for validation
 *
 * @param <T>
 *         the type of subscription to be validated
 */
public class EventSubscriptionValidator<T extends EventSubscription> extends ResourceSubscriptionValidator<T> {

    @Inject
    protected SubscriptionMetaDataService subscriptionPfmData;

    @Inject
    private Logger logger;

    @Override
    public void validate(final T subscription) throws ValidationException {
        super.validate(subscription);

        if (!subscription.getType().isOneOf(SubscriptionType.UETR, SubscriptionType.RPMO, SubscriptionType.RTT)) {
            logger.info("Subscription type is {}", subscription.getType());
            final OutputModeType outputModeType = subscription.getOutputMode();
            final List<StreamInfo> streamInfos = subscription.getStreamInfoList();
            logger.debug("Outputmode type is {} and Stream Infos is {}", subscription.getOutputMode(),
                    subscription.getStreamInfoList());

            if (subscriptionCommonValidator.isStreamInfoApplicable(outputModeType, streamInfos)) {
                subscriptionCommonValidator.validateIpAddresses(streamInfos);
            }
        }
    }

    @Override
    public void validateActivation(final T subscription)
            throws ValidationException {
        super.validateActivation(subscription);
        if (noValidationRequired(subscription)) {
            return;
        }
        if (!(subscription instanceof CellTraceSubscription) && subscription.getEvents().isEmpty()) {
            throw new ValidationException(String.format(INVALID_SUBSCRITPION_TO_ACTIVATE, subscription.getName()));
        }
    }

    private boolean noValidationRequired(final T subscription) {
        return UserType.SYSTEM_DEF == subscription.getUserType() ?
                !systemDefinedPmCapabilityReader.isEventCounterVerificationNeeded(subscription.getName())
                : !subscriptionCommonValidator.isEventCounterVerificationNeeded(subscription);
    }

    @Override
    public void validateImport(final T subscription) throws ValidationException, PfmDataException {
        super.validateImport(subscription);

        if (!(subscription instanceof CellTraceSubscription || subscription instanceof RpmoSubscription || subscription instanceof RttSubscription)) {
            final List<EventInfo> events = subscriptionPfmData.getCorrectEvents(subscription.getName(), subscription.getEvents(),
                    subscription.getNodesTypeVersion());
            if (subscription.getIsImported() && subscription.getEvents().size() != events.size()) {
                final Set<EventInfo> incompatible = new HashSet<>(subscription.getEvents());
                incompatible.removeAll(events);

                throw new PfmDataException("Events provided from imported subscription are invalid: " + incompatible.toString());
            }
        }
    }
}
