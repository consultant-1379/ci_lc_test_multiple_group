/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.config.listener;

import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Cbs.PROP_CBS_ENABLED;
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Cbs.PROP_MAX_NUMBER_OF_CBS_ALLOWED;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.config.annotation.ConfigurationChangeNotification;
import com.ericsson.oss.itpf.sdk.config.annotation.Configured;

/**
 * Listener for Configuration parameter (PIB) changes
 */
@ApplicationScoped
public class CBSConfigurationChangeListener extends AbstractConfigurationChangeListener {

    @Inject
    @Configured(propertyName = PROP_CBS_ENABLED)
    private Boolean cbsEnabled;

    @Inject
    @Configured(propertyName = PROP_MAX_NUMBER_OF_CBS_ALLOWED)
    private Integer maxNoOfCBSAllowed;

    /**
     * @param cbsEnabled
     *         - boolean value for whether criteria based subscriptions is enabled
     */
    void listenForCbsEnabledChanges(@Observes @ConfigurationChangeNotification(propertyName = PROP_CBS_ENABLED) final Boolean cbsEnabled) {
        logChange(PROP_CBS_ENABLED, this.cbsEnabled, cbsEnabled);
        this.cbsEnabled = cbsEnabled;
    }

    /**
     * @param maxNoOfCBSAllowed
     *         - maximum number of criteria based subcriptions allowed
     */
    void listenForMaxNoOfCBSAllowedChanges(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_MAX_NUMBER_OF_CBS_ALLOWED) final Integer maxNoOfCBSAllowed) {
        logChange(PROP_MAX_NUMBER_OF_CBS_ALLOWED, this.maxNoOfCBSAllowed, maxNoOfCBSAllowed);
        this.maxNoOfCBSAllowed = maxNoOfCBSAllowed;
    }

    /**
     * @return the cbsEnabled
     */
    public Boolean getCbsEnabled() {
        return cbsEnabled;
    }

    /**
     * @return the maxNoOfCBSAllowed
     */
    public Integer getMaxNoOfCBSAllowed() {
        return maxNoOfCBSAllowed;
    }
}
