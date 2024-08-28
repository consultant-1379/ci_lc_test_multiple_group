/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.adjuster.impl;

import javax.enterprise.context.ApplicationScoped;

import com.ericsson.oss.pmic.dto.subscription.CtumSubscription;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterQualifier;

/**
 * CtumSubscriptionDataAdjuster - Not applicable for Pfm measurements or pfm events.
 */
@ApplicationScoped
@SubscriptionDataAdjusterQualifier(subscriptionClass = CtumSubscription.class)
public class CtumSubscriptionDataAdjuster extends SubscriptionDataAdjuster<CtumSubscription> {
}
