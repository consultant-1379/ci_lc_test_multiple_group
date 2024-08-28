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

package com.ericsson.oss.services.pm.initiation.task.factories.auditor;

import java.util.Map;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.Subscription;

/**
 * Allows subscription auditors to provide additional criteria to narrow down which subscriptions should be audited
 */
public interface SubscriptionAuditorCriteria {

    /**
     * Use attributes of the subscription to decide whether to audit it or not, e.g. to skip system defined subscriptions
     *
     * @param subscription
     *         subscription
     *
     * @return true if the subscription should be audited
     */
    boolean shouldAuditSubscription(final Subscription subscription);

    /**
     * If more than one scanner is found for the subscription, send a deactivation request for all of them. The subscription state will be resolved on
     * the next audit
     *
     * @return true if deactivation requests should be sent where duplicate scanners are found
     */
    boolean deactivateDuplicateScanners();

    /**
     * Get Expected count of scanner per Node
     *
     * @param subscription
     *         - The subscription object to audit
     * @param node
     *         - The node object to audit
     * @param additionalAttributes
     *         - additional attributes required to find the expected number of scanners per node.
     *
     * @return subscriptionType
     */
    int getExpectedNumberOfScannersPerNode(final Subscription subscription, final Node node, final Map<String, Object> additionalAttributes);

    /**
     * Subscription to be Audited shares Scanners.
     *
     * @param subscription
     *         - The subscription object to audit
     *
     * @return true if subscription uses shared Scanners
     */
    boolean isSharedScannerSubscription(final Subscription subscription);

}
