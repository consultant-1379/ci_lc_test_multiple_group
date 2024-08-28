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

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.EBM_SUBSCRIPTIONATTRIBUTES;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.pmic.api.modelservice.PmCapabilityReader;
import com.ericsson.oss.pmic.dto.subscription.EbmSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.EbsOutputStrategy;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterQualifier;
import com.ericsson.oss.services.pm.exception.DataAccessException;

/**
 * EbmSubscriptionPfmDataAdjuster.
 */
@SubscriptionDataAdjusterQualifier(subscriptionClass = EbmSubscription.class)
@ApplicationScoped
public class EbmSubscriptionDataAdjuster extends EventSubscriptionDataAdjuster<EbmSubscription> {

    @Inject
    private PmCapabilityReader pmCapabilityReader;

    @Override
    public void correctSubscriptionData(final EbmSubscription subscription) {
        super.correctSubscriptionData(subscription);
        if (subscription.isEbsEnabled()) {
            final List<CounterInfo> ebsCounters = subscriptionPfmData.getCorrectCounters(subscription.getName(), subscription.getEbsCounters(),
                    subscription.getNodesTypeVersion(), pmCapabilityReader.getSupportedModelDefinersForCounters(EBM_SUBSCRIPTIONATTRIBUTES), Collections.emptyList(), true);
            subscription.setEbsCounters(ebsCounters);
        } else {
            subscription.setEbsCounters(new ArrayList<CounterInfo>(0));
        }
    }

    @Override
    public void updateImportedSubscriptionWithCorrectValues(final EbmSubscription subscription) throws DataAccessException {
        if (subscription.getIsImported()) {
            super.updateImportedSubscriptionWithCorrectValues(subscription);
            subscription.setEbsOutputInterval(RopPeriod.FIFTEEN_MIN);
            if (subscription.getEbsOutputStrategy().isOneOf(EbsOutputStrategy.ENIQ_GZ, EbsOutputStrategy.TGPP, EbsOutputStrategy.TGPP_ENIQ_GZ)) {
                subscription.setCompressionEnabled(false);
            }
            if (subscription.getEbsOutputStrategy().isOneOf(EbsOutputStrategy.TGPP_GZ, EbsOutputStrategy.TGPP_GZ_ENIQ_GZ)) {
                subscription.setCompressionEnabled(true);
            }
            if (!subscription.getEbsCounters().isEmpty()) {
                subscription.setEbsEnabled(true);
            }
        }
    }
}
