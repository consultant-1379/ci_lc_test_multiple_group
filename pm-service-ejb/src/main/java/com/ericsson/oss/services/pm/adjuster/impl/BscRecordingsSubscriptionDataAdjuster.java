/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
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

import com.ericsson.oss.pmic.dto.subscription.BscRecordingsSubscription;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterQualifier;

/**
 * BscRecordingsSubscriptionDataAdjuster.
 */
@SubscriptionDataAdjusterQualifier(subscriptionClass = BscRecordingsSubscription.class)
@ApplicationScoped
public class BscRecordingsSubscriptionDataAdjuster extends ResourceSubscriptionDataAdjuster<BscRecordingsSubscription> {

}