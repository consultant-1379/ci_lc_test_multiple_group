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
package com.ericsson.oss.services.pm.common.utils;

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.NOTVALID_SUBSCRIPTION_TYPE;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorAnnotationLiteral;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * Generic SubscriptionTypeInstanceSelector class.
 * Selects Instance based on a subscription type.
 *
 * @param <T>
 *         Type of Instance the class will use.
 */
public abstract class SubscriptionTypeInstanceSelector<T> {

    @Inject
    private Logger logger;

    /**
     * Gets the single instance of SystemDefinedSubscriptionAuditRule.
     *
     * @param subscriptionType
     *         the subscription type
     * @param instances
     *         the subscription type instances to select from
     *
     * @return single instance of SystemDefinedSubscriptionAuditRule
     * @throws ValidationException
     *         if validator for subscriptionType if not found
     */
    protected Instance<T> getInstance(final SubscriptionType subscriptionType, final Instance instances) throws ValidationException {
        final SubscriptionValidatorAnnotationLiteral selector = new SubscriptionValidatorAnnotationLiteral(subscriptionType);
        final Instance<T> instance = instances.select(selector);
        if (instance.isUnsatisfied()) {
            logger.error(getMessage(), subscriptionType);
            throw new ValidationException(NOTVALID_SUBSCRIPTION_TYPE);
        }
        return instance;
    }

    /**
     * The error message to return for concrete implementations fo the selector.
     *
     * @return the error message.
     */
    protected abstract String getMessage();
}