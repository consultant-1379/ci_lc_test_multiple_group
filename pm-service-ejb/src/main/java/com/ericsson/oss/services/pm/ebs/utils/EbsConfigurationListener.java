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

package com.ericsson.oss.services.pm.ebs.utils;

import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Asr.PROP_ASR_STREAM_CLUSTER_DEPLOYED;
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Ebsl.PROP_EBS_STREAM_CLUSTER_DEPLOYED;
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Ebsl.PROP_PMIC_EBSL_ROP_IN_MINUTES;
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Ebsm.PROP_EBSM_ENABLED;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.config.annotation.ConfigurationChangeNotification;
import com.ericsson.oss.itpf.sdk.config.annotation.Configured;
import com.ericsson.oss.services.pm.initiation.notification.senders.PmicSubscriptionUpdateMassageSender;

/**
 * Listener for Configuration parameter (PIB) changes
 */
@ApplicationScoped
public class EbsConfigurationListener implements EbsConfiguration {

    @Inject
    private Logger logger;

    @Inject
    @Configured(propertyName = PROP_EBSM_ENABLED)
    private boolean ebsmEnabled;

    @Inject
    @Configured(propertyName = PROP_EBS_STREAM_CLUSTER_DEPLOYED)
    private boolean isEbsStreamClusterDeployed;

    @Inject
    @Configured(propertyName = PROP_PMIC_EBSL_ROP_IN_MINUTES)
    private String pmicEbslRopInMinutes;

    @Inject
    private PmicSubscriptionUpdateMassageSender pmicSubscriptionUpdateMassageSender;

    @Inject
    @Configured(propertyName = PROP_ASR_STREAM_CLUSTER_DEPLOYED)
    private boolean isAsrStreamClusterDeployed;

    /**
     * On ebsm enabled changed.
     *
     * @param ebsmEnabled
     *         the ebsm enabled boolean value
     */
    public void onEbsmEnabledChanged(@Observes @ConfigurationChangeNotification(propertyName = PROP_EBSM_ENABLED) final boolean ebsmEnabled) {
        logChange(PROP_EBSM_ENABLED, this.ebsmEnabled, ebsmEnabled);
        this.ebsmEnabled = ebsmEnabled;
    }

    /**
     * On isEbsStreamClusterDeployed changed.
     *
     * @param isEbsStreamClusterDeployed
     *         isEbsStreamClusterDeployed boolean value
     */
    public void onEbsStreamClusterDeployedChanged(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_EBS_STREAM_CLUSTER_DEPLOYED) final boolean isEbsStreamClusterDeployed) {
        logChange(PROP_EBSM_ENABLED, this.isEbsStreamClusterDeployed, isEbsStreamClusterDeployed);
        this.isEbsStreamClusterDeployed = isEbsStreamClusterDeployed;
    }

    /**
     * On ebsStreamNewFileInterval changed.
     *
     * @param pmicEbslRopInMinutes
     *         pmicEbslRopInMinutes String value
     */
    public void onPmicEbslRopInMinutesChanged(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_PMIC_EBSL_ROP_IN_MINUTES) final String pmicEbslRopInMinutes) {
        logChange(PROP_PMIC_EBSL_ROP_IN_MINUTES, this.pmicEbslRopInMinutes, pmicEbslRopInMinutes);
        final String oldRopValue = this.pmicEbslRopInMinutes;
        this.pmicEbslRopInMinutes = pmicEbslRopInMinutes;
        pmicSubscriptionUpdateMassageSender.sendNotficationToExternalConsumer(PROP_PMIC_EBSL_ROP_IN_MINUTES, oldRopValue, pmicEbslRopInMinutes);
    }

    /**
     * On isAsrStreamClusterDeployed changed.
     *
     * @param isAsrStreamClusterDeployed
     *         isAsrStreamClusterDeployed boolean value
     */
    public void onAsrStreamClusterDeployedChanged(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_ASR_STREAM_CLUSTER_DEPLOYED) final boolean isAsrStreamClusterDeployed) {
        logChange(PROP_ASR_STREAM_CLUSTER_DEPLOYED, this.isAsrStreamClusterDeployed, isAsrStreamClusterDeployed);
        this.isAsrStreamClusterDeployed = isAsrStreamClusterDeployed;
    }

    private void logChange(final String configurationParameter, final boolean oldConfigurationValue, final boolean newConfigurationValue) {
        logger.debug("Configuration parameter {} has changed from {} to {}", configurationParameter, oldConfigurationValue, newConfigurationValue);
    }

    private void logChange(final String configurationParameter, final String oldConfigurationValue, final String newConfigurationValue) {
        logger.debug("Configuration parameter {} has changed from {} to {}", configurationParameter, oldConfigurationValue, newConfigurationValue);
    }

    @Override
    public boolean isEbsmEnabled() {
        return ebsmEnabled;
    }

    public boolean isEbsStreamClusterDeployed() {
        return isEbsStreamClusterDeployed;
    }

    @Override
    public boolean isEbsOrAsrStreamClusterDeployed() {
        return isEbsStreamClusterDeployed || isAsrStreamClusterDeployed;
    }

    public String getPmicEbslRopInMinutes() {
        return pmicEbslRopInMinutes;
    }
}
