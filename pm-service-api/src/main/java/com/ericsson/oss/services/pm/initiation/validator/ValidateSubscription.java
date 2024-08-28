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
package com.ericsson.oss.services.pm.initiation.validator;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * The Interface Validate. This interface is implemented by SubscriptionValidator
 *
 * @param <T>
 *         the generic type
 */
public interface ValidateSubscription<T extends Subscription> {

    /**
     * validates attributes of a subscription.
     *
     * @param subscription
     *         the subscription
     *
     * @throws ValidationException
     *         if validation fails
     */
    void validate(final T subscription) throws ValidationException;

    /**
     * Validate subscription activation and throw exception if validation fails.
     *
     * @param subscription
     *         - subscription object to validate. If it is a resource subscription, it must have nodes loaded.
     *
     * @throws ValidationException
     *         - if validation fails
     */
    void validateActivation(final T subscription)
            throws ValidationException;

    /**
     * @param subscription
     *         - subscription object to validate. If it is a resource subscription, it must have nodes loaded.
     *
     * @throws ValidationException
     *         - if validation fails
     * @throws PfmDataException
     *         - exception when dealing with pfm_counters/pfm_events
     */
    void validateImport(final T subscription) throws ValidationException, PfmDataException;

    /**
     * Validates export of subscription
     *
     * @param subscription
     *         - subscription object to validate. If it is a resource subscription, it must have nodes loaded.
     *
     * @throws ValidationException
     *         - if validation fails
     */
    void validateExport(final T subscription) throws ValidationException;
}
