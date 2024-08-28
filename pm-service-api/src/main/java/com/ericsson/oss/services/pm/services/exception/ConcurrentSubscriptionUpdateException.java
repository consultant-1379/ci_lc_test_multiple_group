/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.services.exception;

import com.ericsson.oss.services.pm.exception.ServiceException;

/**
 * Throw this exception if subscription is about to be updated but the existing persistence time is not the same as the subscription-to-update's
 * persistence time.
 */
public class ConcurrentSubscriptionUpdateException extends ServiceException {

    /**
     * Constructor.
     *
     * @param message
     *         - message
     */
    public ConcurrentSubscriptionUpdateException(final String message) {
        super(message);
    }

    /**
     * Constructor.
     *
     * @param message
     *         - message
     * @param cause
     *         - cause
     */
    public ConcurrentSubscriptionUpdateException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
