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

package com.ericsson.oss.services.pm.initiation.task;

import java.util.List;

/**
 * Audits the subscriptions to ensure that all expected scanners are present in DPS
 */
public interface SubscriptionAuditor {

    /**
     * Audits the subscriptions to ensure that all expected scanners are present in DPS
     * @param subscriptionIds
     *         - list of subscriptionObjects
     */
    void audit(final List<Long> subscriptionIds);

    /**
     * Gets Active Subscription Ids
     *
     * @return list of subscriptionIds
     */
    List<Long> getActiveSubscriptionIds();
}
