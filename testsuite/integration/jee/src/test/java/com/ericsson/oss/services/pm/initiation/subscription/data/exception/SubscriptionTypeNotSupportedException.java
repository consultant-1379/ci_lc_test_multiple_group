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

package com.ericsson.oss.services.pm.initiation.subscription.data.exception;

/**
 * This exception is used when the subscription does not belong to one of the SubscriptionType.
 *
 * @author eushmar
 */
public class SubscriptionTypeNotSupportedException extends Exception {

    private static final long serialVersionUID = 1L;

    public SubscriptionTypeNotSupportedException(final String message) {
        super(message);
    }

    public SubscriptionTypeNotSupportedException(final String message, final Throwable throwable) {
        super(message, throwable);
    }

}
