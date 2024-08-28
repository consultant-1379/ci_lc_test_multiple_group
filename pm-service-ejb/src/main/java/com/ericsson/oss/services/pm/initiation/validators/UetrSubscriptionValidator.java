/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.validators;

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_SUBSCRIPTION_NO_IMSI_TO_ACTIVATE;

import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;

import com.ericsson.oss.pmic.dto.subscription.UetrSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This class validates Uetr Subscription
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.UETR)
public class UetrSubscriptionValidator extends EventSubscriptionValidator<UetrSubscription> {

    @Override
    public void validate(final UetrSubscription subscription) throws ValidationException {
        super.validate(subscription);
        final List<UeInfo> ueInfos = subscription.getUeInfoList() != null ? subscription.getUeInfoList() : new ArrayList<>();
        subscriptionCommonValidator.validateUeInfos(ueInfos);

        final OutputModeType outputModeType = subscription.getOutputMode();
        final List<StreamInfo> streamInfos = subscription.getStreamInfoList();
        if (subscriptionCommonValidator.isStreamInfoCheckRequired(outputModeType, streamInfos)) {
            subscriptionCommonValidator.validateIpAddresses(streamInfos);
        }
    }

    @Override
    public void validateActivation(final UetrSubscription subscription) throws ValidationException {
        super.validateActivation(subscription);

        if (subscription.getUeInfoList() == null || subscription.getUeInfoList().isEmpty()) {
            throw new ValidationException(String.format(INVALID_SUBSCRIPTION_NO_IMSI_TO_ACTIVATE, subscription.getName()));
        }
    }
}
