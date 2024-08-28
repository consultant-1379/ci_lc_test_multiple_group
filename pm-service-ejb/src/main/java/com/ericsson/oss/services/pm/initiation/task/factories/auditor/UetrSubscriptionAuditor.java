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
import java.util.Map;

import javax.ejb.Asynchronous;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.UetrSubscription;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.task.SubscriptionAuditor;

/**
 * Audits the UETR subscriptions to ensure that all expected scanners are present in DPS
 */
@Singleton
@Lock(LockType.READ)
public class UetrSubscriptionAuditor implements SubscriptionAuditor {

    @Inject
    private ResourceSubscriptionHelper resourceSubscriptionHelper;

    private final ResourceSubscriptionAuditorCriteria resourceSubscriptionAuditorCriteria = new ResourceSubscriptionAuditorCriteria() {
        @Override
        public int getExpectedNumberOfScannersPerNode(final Subscription subscription, final Node node,
                                                      final Map<String, Object> additionalAttributes) {
            final UetrSubscription uetrSubscription = (UetrSubscription) subscription;
            final int multiplier = uetrSubscription.getOutputMode().equals(OutputModeType.FILE_AND_STREAMING) ? 2 : 1;
            return multiplier * uetrSubscription.getUeInfoList().size();
        }
    };

    @Override
    @Asynchronous
    public void audit(final List<Long> subscriptionIds) {
        subscriptionIds.forEach(subscriptionId->resourceSubscriptionHelper.audit(resourceSubscriptionAuditorCriteria, subscriptionId));
    }

    @Override
    public List<Long> getActiveSubscriptionIds() {
        return resourceSubscriptionHelper.getActiveSubscriptionIds(SubscriptionType.UETR);
    }
}
