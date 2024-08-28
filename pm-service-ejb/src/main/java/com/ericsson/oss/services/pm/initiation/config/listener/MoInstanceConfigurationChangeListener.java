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

package com.ericsson.oss.services.pm.initiation.config.listener;

import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Moinstance.PROP_MAX_NUMBER_OF_MOINSTANCE_ALLOWED;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.config.annotation.ConfigurationChangeNotification;
import com.ericsson.oss.itpf.sdk.config.annotation.Configured;

/**
 * MoInstance ChangeListener for Configuration parameter (PIB) changes
 */
@ApplicationScoped
public class MoInstanceConfigurationChangeListener extends AbstractConfigurationChangeListener {

    @Inject
    @Configured(propertyName = PROP_MAX_NUMBER_OF_MOINSTANCE_ALLOWED)
    private int maxNoOfMOInstanceAllowed;

    /**
     * Listens for  defaults value of maximum number MO instance supported for MO Based subscription
     *
     * @param maxNoOfMOInstanceAllowed
     *         maximum number of mo instance
     */
    void listenForMaxNoOfMOInstanceAllowedChanges(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_MAX_NUMBER_OF_MOINSTANCE_ALLOWED) final int maxNoOfMOInstanceAllowed) {
        logChange(PROP_MAX_NUMBER_OF_MOINSTANCE_ALLOWED, this.maxNoOfMOInstanceAllowed, maxNoOfMOInstanceAllowed);
        this.maxNoOfMOInstanceAllowed = maxNoOfMOInstanceAllowed;
    }

    /**
     * @return the maxNoOfMOInstanceAllowed
     */
    public int getMaxNoOfMOInstanceAllowed() {
        return maxNoOfMOInstanceAllowed;
    }
}