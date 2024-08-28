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
package com.ericsson.oss.services.pm.initiation.common;

/**
 * The Class SubscriptionValidationResult. holder for subscription validation result
 */
public class SubscriptionValidationResult {

    private final String error;
    private final Object[] parameters;

    /**
     * Instantiates a new subscription validation result.
     *
     * @param error
     *         the error
     * @param parameters
     *         the parameters
     */
    public SubscriptionValidationResult(final String error, final Object... parameters) {
        this.error = error;
        this.parameters = parameters;
    }

    /**
     * Instantiates a new subscription validation result.
     */
    public SubscriptionValidationResult() {
        error = null;
        parameters = null;
    }

    /**
     * Checks for errors.
     *
     * @return true, if successful
     */
    public boolean hasErrors() {
        return error != null;
    }

    /**
     * Gets the error.
     *
     * @return the error
     */
    public String getError() {
        return error;
    }

    /**
     * Gets the parameters.
     *
     * @return the parameters
     */
    public Object[] getParameters() {
        return parameters;
    }
}