/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.validators;

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.RTT_SUBSCRIPTIONATTRIBUTES;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.EBS_STREAMINFO_REQUIRED_FORMATTER;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_RTT_SUBSCRIPTION_NO_IMSI_OR_IMEI_TO_ACTIVATE;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.RttSubscription;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This class validates Rtt Subscription
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.RTT)
public class RttSubscriptionValidator extends EventSubscriptionValidator<RttSubscription> {

    @Inject
    private Logger logger;

    @Override
    public void validateActivation(final RttSubscription subscription) throws ValidationException {
        super.validateActivation(subscription);
        validateUeInfo(subscription);
        validateStreamClusterDataNeededForActivation(subscription);
        isMultipleSubscriptionsAllowed(subscription);
    }

    @Override
    public void validate(final RttSubscription subscription) throws ValidationException {
        super.validate(subscription);
        if (subscription.getAdministrationState() == AdministrationState.ACTIVE) {
            isMultipleSubscriptionsAllowed(subscription);
        }
    }

    private void validateUeInfo(final RttSubscription subscription) throws ValidationException {
        if (subscription.getUeInfoList() == null || subscription.getUeInfoList().isEmpty()) {
            throw new ValidationException(String.format(INVALID_RTT_SUBSCRIPTION_NO_IMSI_OR_IMEI_TO_ACTIVATE, subscription.getName()));
        }
    }

    private void validateStreamClusterDataNeededForActivation(final RttSubscription subscription) throws ValidationException {
        if (subscription.getStreamInfoList().isEmpty()) {
            throw new ValidationException(String.format(EBS_STREAMINFO_REQUIRED_FORMATTER, subscription.getName()));
        }
    }

    /**
     * Find all subscriptions with given name pattern.
     *
     * @param subscription
     *         - subscription names array.
     *
     * @throws ValidationException
     *         - if any exception is thrown from Database
     */
    private void isMultipleSubscriptionsAllowed(final RttSubscription subscription) throws ValidationException{
        subscriptionCommonValidator.validateMultipleSubscriptionAllowed(NetworkElementType.BSC.name(), RTT_SUBSCRIPTIONATTRIBUTES, subscription);
    }

}
