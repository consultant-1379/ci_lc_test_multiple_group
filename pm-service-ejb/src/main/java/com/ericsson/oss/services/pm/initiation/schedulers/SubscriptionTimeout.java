/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.schedulers;

import static com.ericsson.oss.pmic.api.constants.ModelConstants.PmCapabilityConstants.CAPABILITY_MODEL_NAME_STATS;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.api.modelservice.PmCapabilitiesLookupLocal;
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.utils.InitiationTimingInfoForNe;

/**
 * Subscription timeout.
 */
public class SubscriptionTimeout {

    private static final int WORST_CASE_INITIATION_TIMEOUT = 5_000;
    private static final int WORST_CASE_REPLY_WORST_DELAY = 180_000;
    private static final String NE_INITIATION_TIMEOUT = "neInitiationTimeout";
    private static final String NE_WORST_REPLY_DELAY = "neReplyWorstDelay";

    @Inject
    private Logger logger;

    @Inject
    private ConfigurationChangeListener configListener;

    @Inject
    private PmCapabilitiesLookupLocal pmCapabilitiesLookup;

    /**
     * Gets total timeout for all subscription nodes.
     *
     * @param neFdnsAndTypes
     *         the network element fully distinguished names and types
     *
     * @return the total timeouts for all subscription nodes
     */
    public long getTotalTimeoutForAllSubscriptionNodes(final Map<String, String> neFdnsAndTypes) {
        final Map<String, InitiationTimingInfoForNe> nodeTypeInfoMap = new HashMap<>();
        for (final String neType : neFdnsAndTypes.values()) {
            if (nodeTypeInfoMap.containsKey(neType)) {
                nodeTypeInfoMap.get(neType).incrementNumberOfNodes();
            } else {
                nodeTypeInfoMap.put(neType, getModeledTimingInfoForNeType(neType));
            }
        }
        long initiationTimeout = configListener.getInitiationTimeout();
        for (final InitiationTimingInfoForNe nodeTypeInfo : nodeTypeInfoMap.values()) {
            initiationTimeout += getTotalTimeoutPeriodForNeType(nodeTypeInfo);
        }
        logger.debug("initiationTimeout: {} ", initiationTimeout);
        return initiationTimeout;
    }

    private int getTotalTimeoutPeriodForNeType(final InitiationTimingInfoForNe nodeTypeInfo) {
        final int neTimeout = nodeTypeInfo.getNeTimeout();
        final int neReplyWorstDelay = nodeTypeInfo.getNeReplyWorstDelay();
        final int numberOfNodes = nodeTypeInfo.getNumberOfNodes();
        if (isTotalTimeoutGreaterThanNeReplyWorstDelay(neTimeout, neReplyWorstDelay, numberOfNodes)) {
            return neReplyWorstDelay;
        }
        return neTimeout * numberOfNodes;
    }

    private boolean isTotalTimeoutGreaterThanNeReplyWorstDelay(final int neTimeout, final int neReplyWorstDelay, final int numberOfNodes) {
        return neTimeout * numberOfNodes > neReplyWorstDelay;
    }

    private InitiationTimingInfoForNe getModeledTimingInfoForNeType(final String neType) {
        final Object neInitiationTimeoutValue = pmCapabilitiesLookup.getCapabilityValue(neType,
                CAPABILITY_MODEL_NAME_STATS, NE_INITIATION_TIMEOUT);
        logger.debug("initiationTimeout capability : {} for neType {}  ", neInitiationTimeoutValue, neType);
        final Object neReplyWorstDelayValue = pmCapabilitiesLookup.getCapabilityValue(neType,
                CAPABILITY_MODEL_NAME_STATS, NE_WORST_REPLY_DELAY);
        logger.debug("replyWorstDelay capability : {} for neType {}  ", neReplyWorstDelayValue, neType);
        return getInitiationTimingInfoForNe(neType, neInitiationTimeoutValue, neReplyWorstDelayValue);
    }

    private InitiationTimingInfoForNe getInitiationTimingInfoForNe(final String neType, final Object neInitiationTimeoutValue,
                                                                   final Object neReplyWorstDelayValue) {
        if (neInitiationTimeoutValue == null || neReplyWorstDelayValue == null) {
            logger.error("PM capabilities not found for neType {}. Returning default values", neType);
            return new InitiationTimingInfoForNe(WORST_CASE_INITIATION_TIMEOUT, WORST_CASE_REPLY_WORST_DELAY);
        }
        return new InitiationTimingInfoForNe((int) neInitiationTimeoutValue, (int) neReplyWorstDelayValue);
    }
}
