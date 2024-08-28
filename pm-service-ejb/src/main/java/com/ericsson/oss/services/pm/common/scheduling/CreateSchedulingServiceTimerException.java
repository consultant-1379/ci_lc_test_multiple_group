/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.common.scheduling;

/**
 * Class for creating a scheduling service timer exception.
 */
public class CreateSchedulingServiceTimerException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a default SchedulingServiceTimerException
     */
    public CreateSchedulingServiceTimerException() {
        super();
    }

    /**
     * Creates a SchedulingServiceTimerException with a given message
     *
     * @param message
     *         - nessage to create exception with
     */
    public CreateSchedulingServiceTimerException(final String message) {
        super(message);
    }

}
