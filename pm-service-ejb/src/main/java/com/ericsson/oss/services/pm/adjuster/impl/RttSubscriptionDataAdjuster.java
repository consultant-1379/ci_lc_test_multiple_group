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

import com.ericsson.oss.pmic.dto.subscription.RttSubscription;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterQualifier;
import com.ericsson.oss.services.pm.ebs.utils.EbsStreamInfoResolver;

/**
 * RttSubscriptionDataAdjuster.
 */
@SubscriptionDataAdjusterQualifier(subscriptionClass = RttSubscription.class)
@ApplicationScoped
public class RttSubscriptionDataAdjuster extends EventSubscriptionDataAdjuster<RttSubscription> {

    @Inject
    private EbsStreamInfoResolver ebsStreamInfoResolver;

    @Override
    public void correctSubscriptionData(final RttSubscription subscription) {
        super.correctSubscriptionData(subscription);
        subscription.setStreamInfoList(ebsStreamInfoResolver.getStreamingDestination(true));
    }

}
