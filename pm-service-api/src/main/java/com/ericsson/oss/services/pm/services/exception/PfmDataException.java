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
 * This exception should be thrown whenever the pfm_measurements(commonly called counters) or pfm_events(commonly called events) are not valid,
 * incorrect or if there is a need to throw exception when dealing with pfm_counters/pfm_events.
 */
public class PfmDataException extends ServiceException {

    /**
     * PfmDataException.
     *
     * @param message
     *         message
     */
    public PfmDataException(final String message) {
        super(message);
    }

    /**
     * PfmDataException.
     *
     * @param message
     *         message
     * @param cause
     *         cause
     */
    public PfmDataException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
