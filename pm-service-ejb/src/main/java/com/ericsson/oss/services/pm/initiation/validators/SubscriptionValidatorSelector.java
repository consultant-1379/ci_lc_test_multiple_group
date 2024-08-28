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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.common.utils.SubscriptionTypeInstanceSelector;
import com.ericsson.oss.services.pm.initiation.validator.ValidateSubscription;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This Class SubscriptionValidatorSelectorDto, selects an instance of SubscriptionValidator
 */
@ApplicationScoped
public class SubscriptionValidatorSelector extends SubscriptionTypeInstanceSelector<ValidateSubscription<Subscription>> {

    private static final String MESSAGE = "No validator defined for subscription type {}. Subscription will not be saved. ";

    @Any
    @Inject
    private Instance<ValidateSubscription<Subscription>> subscriptionValidators;

    /**
     * Gets the single instance of SubscriptionValidator.
     *
     * @param subscriptionType
     *         the subscription type
     *
     * @return single instance of SubscriptionValidatorSelector
     * @throws ValidationException
     *         if validator for subscriptionType if not found
     */
    public ValidateSubscription<Subscription> getInstance(final SubscriptionType subscriptionType) throws ValidationException {
        return super.getInstance(subscriptionType, subscriptionValidators).get();
    }

    protected String getMessage() {
        return MESSAGE;
    }
}