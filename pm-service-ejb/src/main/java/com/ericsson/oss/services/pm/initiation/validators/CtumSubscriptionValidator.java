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

import java.util.Collections;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;

import com.ericsson.oss.pmic.dto.subscription.CtumSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This class validates Ctum Subscription
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.CTUM)
public class CtumSubscriptionValidator extends SubscriptionValidator<CtumSubscription> {

    @Override
    public void validate(final CtumSubscription subscription) throws ValidationException {
        super.validate(subscription);

        final OutputModeType outputModeType = subscription.getOutputMode();
        final List<StreamInfo> streamInfos = subscription.getStreamInfo() != null ? Collections.singletonList(subscription.getStreamInfo()) : null;

        if (subscriptionCommonValidator.isStreamInfoApplicable(outputModeType, streamInfos)) {
            subscriptionCommonValidator.validateIpAddresses(streamInfos);
        }
    }

    @Override
    public void validateImport(final CtumSubscription subscription) throws ValidationException, PfmDataException {
        subscriptionCommonValidator.validatePmFunction(subscription.getNodes());
    }

}
