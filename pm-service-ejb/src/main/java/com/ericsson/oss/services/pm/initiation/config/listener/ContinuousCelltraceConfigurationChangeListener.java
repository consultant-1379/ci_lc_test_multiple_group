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

import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.ContinuousCelltrace.PROP_ROP_PERIOD;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.config.annotation.ConfigurationChangeNotification;
import com.ericsson.oss.itpf.sdk.config.annotation.Configured;

/**
 * Listener for Configuration parameter (PIB) changes
 */
@ApplicationScoped
public class ContinuousCelltraceConfigurationChangeListener extends AbstractConfigurationChangeListener {

    @Inject
    @Configured(propertyName = PROP_ROP_PERIOD)
    private int continuousCelltraceRopPeriod;

    /**
     * Listens for of ContinuousCelltrace subscription rop period parameter changes
     *
     * @param cctrRopPeriod
     *         ContinuousCelltrace Rop Period
     */
    void listenForContinuousCelltraceRopPeriodChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = PROP_ROP_PERIOD) final int cctrRopPeriod) {
        logChange(PROP_ROP_PERIOD, this.continuousCelltraceRopPeriod, cctrRopPeriod);
        this.continuousCelltraceRopPeriod = cctrRopPeriod;
    }

    /**
     * @return the cctrRopPeriod
     */
    public int getContinuousCelltraceRopPeriod() {
        return continuousCelltraceRopPeriod;
    }
}
