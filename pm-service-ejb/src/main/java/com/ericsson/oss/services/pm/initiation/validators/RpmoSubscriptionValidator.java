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

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.RPMO_SUBSCRIPTIONATTRIBUTES;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.EBS_STREAMINFO_REQUIRED_FORMATTER;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_SUBSCRIPTION_CELLS_TO_ACTIVATE;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.RpmoSubscription;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This class validates Rpmo Subscription.
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.RPMO)
public class RpmoSubscriptionValidator extends EventSubscriptionValidator<RpmoSubscription> {

    @Inject
    private Logger logger;

    @Override
    public void validate(final RpmoSubscription subscription) throws ValidationException {
        super.validate(subscription);
        if (subscription.getAdministrationState() == AdministrationState.ACTIVE) {
            isMultipleSubscriptionsAllowed(subscription);
        }
    }

    @Override
    public void validateActivation(final RpmoSubscription subscription) throws ValidationException {
        super.validateActivation(subscription);
        validateCellsData(subscription);
        validateStreamClusterDataNeededForActivation(subscription);
        isMultipleSubscriptionsAllowed(subscription);
    }

    private void validateCellsData(final RpmoSubscription subscription) throws ValidationException {
        if (subscription.getCells().isEmpty() && !subscription.isApplyOnAllCells()) {
            logger.error("The cells of the subscription {} is empty.", subscription.getName());
            throw new ValidationException(String.format(INVALID_SUBSCRIPTION_CELLS_TO_ACTIVATE, subscription.getName()));
        }
    }

    private void validateStreamClusterDataNeededForActivation(final RpmoSubscription subscription) throws ValidationException {
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
    private void isMultipleSubscriptionsAllowed(final RpmoSubscription subscription) throws ValidationException{
        subscriptionCommonValidator.validateMultipleSubscriptionAllowed(NetworkElementType.BSC.name(), RPMO_SUBSCRIPTIONATTRIBUTES, subscription);
    }

}
