/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.services.exception;

import com.ericsson.oss.services.pm.exception.ServiceException;

/**
 * InvalidSubscriptionException.
 */
public class InvalidSubscriptionException extends ServiceException {

    /**
     * InvalidSubscriptionException.
     *
     * @param message
     *         - message
     */
    public InvalidSubscriptionException(final String message) {
        super(message);
    }

    /**
     * InvalidSubscriptionException.
     *
     * @param message
     *         - message
     * @param cause
     *         - underlying cause
     */
    public InvalidSubscriptionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
