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

import java.util.List;

import javax.ejb.Asynchronous;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.inject.Inject;

import com.ericsson.oss.pmic.api.selector.annotation.Selector;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;
import com.ericsson.oss.services.pm.initiation.task.SubscriptionAuditor;

/**
 * Audits the Celltrace subscriptions to ensure that all expected scannerNames are present in DPS.
 */
@Singleton
@Lock(LockType.READ)
public class CellTraceSubscriptionAuditor implements SubscriptionAuditor {

    @Inject
    @Selector(filter = "CellTraceSubscriptionHelper")
    private CellTraceSubscriptionHelper cellTraceSubscriptionHelper;

    private final ResourceSubscriptionAuditorCriteria resourceSubscriptionAuditorCriteria = new ResourceSubscriptionAuditorCriteria() {
        @Override
        public boolean isSharedScannerSubscription(final Subscription subscription) {
            return true;
        }

        @Override
        public boolean shouldAuditSubscription(final Subscription subscription) {
            return UserType.USER_DEF.equals(subscription.getUserType()) ||
                ((CellTraceSubscription) subscription).getCellTraceCategory() == CellTraceCategory.ASR;
        }
    };

    @Override
    @Asynchronous
    public void audit(final List<Long> subscriptionIds) {
        subscriptionIds.forEach(subscriptionId -> cellTraceSubscriptionHelper.audit(resourceSubscriptionAuditorCriteria, subscriptionId));
    }

    @Override
    public List<Long> getActiveSubscriptionIds() {
        return cellTraceSubscriptionHelper.getActiveSubscriptionIds(SubscriptionType.CELLTRACE);
    }
}
