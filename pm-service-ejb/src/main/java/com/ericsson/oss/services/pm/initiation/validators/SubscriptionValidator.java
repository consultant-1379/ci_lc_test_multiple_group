/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2017
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */
package com.ericsson.oss.services.pm.initiation.validators;

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.UNABLE_TO_ACTIVATE;

import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.subscription.capability.SubscriptionCapabilityReader;
import com.ericsson.oss.services.pm.initiation.common.SubscriptionValidationResult;
import com.ericsson.oss.services.pm.initiation.validator.ValidateSubscription;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This class Validates Subscriptions
 *
 * @param <S> the type of subscription to validate
 */
public class SubscriptionValidator<S extends Subscription> implements ValidateSubscription<S> {

    private static final String SUBS_GENERAL_NAME_PATTERN = "^([\\p{Punct}&&[^\",=\\[\\]{}~`]]|[a-zA-Z0-9 ])+$";

    @Inject
    protected SubscriptionCommonValidator subscriptionCommonValidator;

    @Inject
    protected SubscriptionCapabilityReader systemDefinedPmCapabilityReader;

    @Override
    public void validate(final S subscription) throws ValidationException {
        subscriptionCommonValidator.validateSubscriptionName(subscription.getName(), SUBS_GENERAL_NAME_PATTERN);
        subscriptionCommonValidator.validateSubscriptionType(subscription.getType());
        subscriptionCommonValidator.validateUserType(subscription.getUserType());
        if (subscription.getScheduleInfo() != null) {
            final SubscriptionValidationResult result = subscriptionCommonValidator.validateScheduleInfo(subscription);
            if (result.hasErrors()) {
                throw new ValidationException(String.format(result.getError(), result.getParameters()));
            }
        }
        if (subscription.getIsImported()) {
            SubscriptionCommonValidator.validateSubscriptionType(subscription);
        }
    }

    @Override
    public void validateActivation(final S subscription)
            throws ValidationException {
        if (AdministrationState.INACTIVE != subscription.getAdministrationState()) {
            throw new ValidationException(
                    String.format(UNABLE_TO_ACTIVATE, subscription.getName(), subscription.getAdministrationState()));
        }
        if (subscription.getScheduleInfo() != null) {
            final SubscriptionValidationResult result = subscriptionCommonValidator.validateScheduleInfo(subscription);
            if (result.hasErrors()) {
                throw new ValidationException(result.getError());
            }
        }
    }

    @Override
    public void validateImport(final S subscription) throws ValidationException, PfmDataException {
        // Do nothing because this is generic handling, any subscription type that do not support export can throw ValidationException
    }

    @Override
    public void validateExport(final S subscription) throws ValidationException {
        // Do nothing because this is generic handling, any subscription type that do not support export can throw ValidationException
    }
}
