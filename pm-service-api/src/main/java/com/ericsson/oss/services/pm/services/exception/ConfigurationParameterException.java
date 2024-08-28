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
 * Exception thrown when accessing configuration parameters (PIB) and an exception is thrown (unknown parameter and so on)
 */
public class ConfigurationParameterException extends ServiceException {

    /**
     * Constructor.
     *
     * @param message
     *         - exception message
     */
    public ConfigurationParameterException(final String message) {
        super(message);
    }

    /**
     * Constructor.
     *
     * @param message
     *         - exception message
     * @param cause
     *         - throwable
     */
    public ConfigurationParameterException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
