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

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.config.annotation.ConfigurationChangeNotification;
import com.ericsson.oss.services.pm.common.startup.StartupService;

/**
 * The type Deployment ready configuration listener.
 */
@ApplicationScoped
public class DeploymentReadyConfigurationListener extends AbstractConfigurationChangeListener {

    public static final String PM_DEPLOYMENT_PHASE_COMPLETE_PARAMETER = "pm_deployment_phase_complete_boolean";

    private static final String PM_DEPLOYMENT_PHASE_COMPLETED_FLAG = "true";

    @Inject
    private Logger log;

    @Inject
    private StartupService startupService;

    @Inject
    private CacheHolder cacheHolder;

    private boolean hasDeploymentReadyEventBeenTriggered;

    /**
     * On start up. sets hasDeploymentReadyEventBeenTriggered to false
     */
    @PostConstruct
    public void onStartUp() {
        log.debug("Creating single action timer for all start up tasks");
    }

    /**
     * Listen for cbs enabled changes.
     *
     * @param pm_deployment_phase_completed
     *         the pm deployment phase completed
     */
    public void listenForCbsEnabledChanges(@Observes @ConfigurationChangeNotification(propertyName = PM_DEPLOYMENT_PHASE_COMPLETE_PARAMETER) final String pm_deployment_phase_completed) {
        logChange(PM_DEPLOYMENT_PHASE_COMPLETE_PARAMETER, "NOT_INTERESTED", pm_deployment_phase_completed);

        if (!hasDeploymentReadyEventBeenTriggered && PM_DEPLOYMENT_PHASE_COMPLETED_FLAG.equals(pm_deployment_phase_completed)) {
            cacheHolder.forceSynchronizationOfReplicatedCaches();
            startupService.triggerStartupService();
            hasDeploymentReadyEventBeenTriggered = true;
        }
    }

    /**
     * Getter.
     *
     * @return value.
     */
    public boolean hasDeploymentReadyEventBeenTriggered() {
        return hasDeploymentReadyEventBeenTriggered;
    }
}
