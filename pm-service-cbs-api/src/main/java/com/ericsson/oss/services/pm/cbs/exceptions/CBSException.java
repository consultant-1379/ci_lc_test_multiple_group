/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.cbs.exceptions;

/**
 * Exception class to support CBS runtime exceptions
 */
public class CBSException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Instantiates a new Cbs exception.
     *
     * @param message
     *         the exception message
     */
    public CBSException(final String message) {
        super(message);
    }

    /**
     * Instantiates a new Cbs exception.
     *
     * @param message
     *         the exception message
     * @param throwable
     *         the throwable
     */
    public CBSException(final String message, final Throwable throwable) {
        super(message, throwable);
    }
}
