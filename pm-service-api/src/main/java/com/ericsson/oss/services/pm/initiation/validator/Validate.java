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
package com.ericsson.oss.services.pm.initiation.validator;

import com.ericsson.oss.services.pm.exception.RetryServiceException;

/**
 * The Interface Validate. This interface is implemented by SubscriptionValidator
 *
 * @param <T>
 *         the generic type
 */
public interface Validate<T> {

    /**
     * validates attributes of a subscription.
     *
     * @param subscription
     *         the subscription
     *
     * @throws RetryServiceException
     *         the PMIC invalid input exception
     */
    void validate(final T subscription) throws RetryServiceException;
}
