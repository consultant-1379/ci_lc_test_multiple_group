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
 * This exception should be thrown if validation of any general PM object fails.
 */
public class ValidationException extends ServiceException {
    /**
     * Constructor.
     *
     * @param message
     *         - message
     */
    public ValidationException(final String message) {
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
    public ValidationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
