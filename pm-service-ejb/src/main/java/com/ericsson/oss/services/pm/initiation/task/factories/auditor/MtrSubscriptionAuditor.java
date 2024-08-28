/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.task.factories.auditor;

import java.util.List;

import javax.ejb.Asynchronous;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.inject.Inject;

import com.ericsson.oss.pmic.api.selector.annotation.Selector;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.task.SubscriptionAuditor;

/**
 * Audits the MTR subscriptions to ensure that
 * 1. BSC nodes not connected to any MSC in the active subscription will be removed from subscription(AttachedNodes)
 * 2. If any BSC nodes added in DPS, with connected MSC, included in an active MTR subscription then it will be added to the
 * MTR subscription
 * 3. All expected scanners are present in DPS
 */
@Singleton
@Lock(LockType.READ)
public class MtrSubscriptionAuditor implements SubscriptionAuditor {

    @Inject
    @Selector(filter = "MtrSubscriptionHelper")
    private MtrSubscriptionHelper mtrSubscriptionHelper;

    private final ResourceSubscriptionAuditorCriteria resourceSubscriptionAuditorCriteria = new ResourceSubscriptionAuditorCriteria() {
    };

    @Override
    @Asynchronous
    public void audit(final List<Long> subscriptionIds) {
        subscriptionIds.forEach(subscriptionId->mtrSubscriptionHelper.audit(resourceSubscriptionAuditorCriteria, subscriptionId));
    }

    @Override
    public List<Long> getActiveSubscriptionIds() {
        return mtrSubscriptionHelper.getActiveSubscriptionIds(SubscriptionType.MTR);
    }
}
