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

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.STATISTICAL_SUBSCRIPTIONATTRIBUTES;

import com.ericsson.oss.pmic.api.modelservice.PmCapabilityReader;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;

/**
 * StatisticalSubscriptionParentDataAdjuster.
 *
 * @param <T>
 */
public class StatisticalSubscriptionParentDataAdjuster<T extends StatisticalSubscription> extends ResourceSubscriptionDataAdjuster<T> {

    @Inject
    private PmCapabilityReader pmCapabilityReader;

    @Override
    public void correctSubscriptionData(final T subscription) {
        super.correctSubscriptionData(subscription);
        final boolean supportExternalCounterNames =
                pmCapabilityReader.shouldSupportExternalCounterName(STATISTICAL_SUBSCRIPTIONATTRIBUTES, subscription.getNodesTypeVersion());
        final List<CounterInfo> nodeCounter = subscriptionPfmData.getCorrectCounters(subscription.getName(), subscription.getCounters(),
                subscription.getNodesTypeVersion(),  pmCapabilityReader.getSupportedModelDefinersForCounters(STATISTICAL_SUBSCRIPTIONATTRIBUTES), Collections.emptyList(), supportExternalCounterNames);
        subscription.setCounters(nodeCounter);
    }
}
