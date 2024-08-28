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

package com.ericsson.oss.services.pm.adjuster.impl;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.subscription.RpmoSubscription;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterQualifier;
import com.ericsson.oss.services.pm.ebs.utils.EbsStreamInfoResolver;

/**
 * RpmoSubscriptionDataAdjuster.
 */
@SubscriptionDataAdjusterQualifier(subscriptionClass = RpmoSubscription.class)
@ApplicationScoped
public class RpmoSubscriptionDataAdjuster extends EventSubscriptionDataAdjuster<RpmoSubscription> {

    @Inject
    private EbsStreamInfoResolver ebsStreamInfoResolver;

    @Override
    public void correctSubscriptionData(final RpmoSubscription subscription) {
        super.correctSubscriptionData(subscription);
        subscription.setStreamInfoList(ebsStreamInfoResolver.getStreamingDestination(true));
    }

}
