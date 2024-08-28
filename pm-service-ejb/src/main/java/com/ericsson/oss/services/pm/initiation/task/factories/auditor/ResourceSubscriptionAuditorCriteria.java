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
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;

/**
 * ResourceSubscriptionAuditorCriteria for Resource Subscriptions Audit.
 */
public abstract class ResourceSubscriptionAuditorCriteria implements SubscriptionAuditorCriteria {

    private static final int DEFAULT_EXPECTED_SCANNERS_PER_NODE = 1;

    @Override
    public int getExpectedNumberOfScannersPerNode(final Subscription subscription, final Node node, final Map<String, Object> additionalAttributes) {
        return DEFAULT_EXPECTED_SCANNERS_PER_NODE;
    }

    @Override
    public boolean shouldAuditSubscription(final Subscription subscription) {
        return UserType.USER_DEF.equals(subscription.getUserType());
    }

    @Override
    public boolean deactivateDuplicateScanners() {
        return true;
    }


    @Override
    public boolean isSharedScannerSubscription(final Subscription subscription) {
        return false;
    }
}
