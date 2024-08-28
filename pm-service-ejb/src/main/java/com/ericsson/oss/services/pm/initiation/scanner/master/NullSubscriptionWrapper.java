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

package com.ericsson.oss.services.pm.initiation.scanner.master;

/**
 * <code>NullSubscriptionWrapper</code> is to represent invalid subscription , see {@link SubscriptionWrapper} which represents valid subscription.
 */
class NullSubscriptionWrapper extends SubscriptionWrapper {

    private static final long serialVersionUID = 1L;
    private static NullSubscriptionWrapper instance;

    private NullSubscriptionWrapper() {

    }

    /**
     * Gets instance of NullSubscriptionWrapper.
     *
     * @return the instance of NullSubscriptionWrapper
     */
    public static NullSubscriptionWrapper getInstance() {
        if (instance == null) {
            instance = new NullSubscriptionWrapper();
        }
        return instance;
    }

    @Override
    public boolean isValid() {
        return false;
    }
}
