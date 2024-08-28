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

package com.ericsson.oss.services.pm.initiation.config.listener;

import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Cbs.PROP_PERIODIC_AUDIT_DELAY_INTERVAL;
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.PROP_ACTIVATION_DELAY_INTERVAL;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.CONFIGURATION_CHANGE_NOTIFICATION;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.config.annotation.ConfigurationChangeNotification;
import com.ericsson.oss.itpf.sdk.config.annotation.Configured;

/**
 * Listener for Configuration parameter (PIB) changes
 */
@ApplicationScoped
public class ActivationDelayIntervalConfigurationChangeListener extends AbstractConfigurationChangeListener {

    @Inject
    @Configured(propertyName = PROP_ACTIVATION_DELAY_INTERVAL)
    private long activationDelayInterval;

    /**
     * This method listens for activationDelayInterval
     *
     * @param activationDelayInterval
     *         - Delay interval of the Subscription Activation
     */
    void listenForActivationDelayInterval(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_ACTIVATION_DELAY_INTERVAL) final long activationDelayInterval) {
        logChange(PROP_PERIODIC_AUDIT_DELAY_INTERVAL, this.activationDelayInterval, activationDelayInterval);
        this.activationDelayInterval = activationDelayInterval;
    }

    /**
     * This method returns the activationDelayInterval
     *
     * @return returns activationDelayInterval {@link long}
     */
    public long getActivationDelayInterval() {
        return activationDelayInterval;
    }

}
