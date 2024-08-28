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
 * Exception to be thrown when asting for counter conflicts and there is an error extracting conflicts (Most likely due to resources not being found
 * in database)
 */
public class CannotGetConflictingCountersException extends ServiceException {

    /**
     * Message constructor.
     *
     * @param message
     *         - the message of the exeption to be presented to UI.
     */
    public CannotGetConflictingCountersException(final String message) {
        super(message);
    }

    /**
     * Message constructor with Throwable.
     *
     * @param message
     *         - the message of the exception to be presented to the UI.
     * @param cause
     *         - the cause of the exception.
     */
    public CannotGetConflictingCountersException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
