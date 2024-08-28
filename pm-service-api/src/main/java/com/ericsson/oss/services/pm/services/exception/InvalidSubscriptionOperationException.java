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
 * This exception should be thrown from the service layer when an operation is performed on a subscription that is not allowed. For example: User
 * tries to edit a subscription that is in ACTIVATING state. Please note that {@link InvalidSubscriptionException} should be used if the subscription
 * is already in an invalid state (for example, it has counters but no nodes.), This exception should be thrown strictly when the action to be
 * performed would invalidate the subscription if it would be saved with such changes.
 */
public class InvalidSubscriptionOperationException extends ServiceException {
    /**
     * Constructor.
     *
     * @param message
     *         - message
     */
    public InvalidSubscriptionOperationException(final String message) {
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
    public InvalidSubscriptionOperationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
