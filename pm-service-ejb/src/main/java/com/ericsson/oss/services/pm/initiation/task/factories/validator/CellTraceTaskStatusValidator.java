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

package com.ericsson.oss.services.pm.initiation.task.factories.validator;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.pmic.api.selector.annotation.Selector;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.ebs.utils.EbsSubscriptionHelper;
import com.ericsson.oss.services.pm.initiation.task.factories.auditor.CellTraceSubscriptionHelper;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;

/**
 * The Cell trace task status validator.
 */
@SubscriptionTaskStatusValidation(subscriptionType = CellTraceSubscription.class)
@ApplicationScoped
public class CellTraceTaskStatusValidator extends ResourceTaskStatusValidator {

    @Inject
    private EbsSubscriptionHelper ebsSubscriptionHelper;

    @Inject
    @Selector(filter = "CellTraceSubscriptionHelper")
    private CellTraceSubscriptionHelper cellTraceSubscriptionHelper;

    @Override
    protected int getExpectedNumberOfScannersPerNode(final Subscription subscription) {
        final CellTraceSubscription cellTraceSubscription = (CellTraceSubscription) subscription;
        if (cellTraceSubscriptionHelper.isCellTraceNran(cellTraceSubscription.getCellTraceCategory())) {
            return cellTraceSubscription.getEventProducerIdsFromEvents().size() + cellTraceSubscription.getEbsEventProducerIdsFromEvents().size();
        }
        return ebsSubscriptionHelper.isBothEbsStreamAndFileCategory(cellTraceSubscription) ? 2 : 1;
    }

    @Override
    protected boolean isSharedScannerSubscription(final Subscription subscription) {
        return ebsSubscriptionHelper.isEbsStream((CellTraceSubscription) subscription);
    }

}
