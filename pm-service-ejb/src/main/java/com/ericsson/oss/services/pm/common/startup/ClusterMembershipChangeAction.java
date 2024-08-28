/*******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.common.startup;

import javax.inject.Inject;

import com.ericsson.oss.services.pm.collection.cache.FileCollectionLastRopData;
import com.ericsson.oss.services.pm.initiation.config.listener.DeploymentReadyConfigurationListener;

/**
 * Class that holds one-time actions performed when master status is gained or lost.
 */
public class ClusterMembershipChangeAction {

    @Inject
    private FileCollectionLastRopData fileCollectionLastRopData;

    @Inject
    private DeploymentReadyConfigurationListener deploymentReadyConfigurationListener;

    /**
     * Executed when master status is gained or if server comes up as master.
     */
    public void executeAsMaster() {
        if (deploymentReadyConfigurationListener.hasDeploymentReadyEventBeenTriggered()) {
            fileCollectionLastRopData.resyncLocalRecords();
        }
    }

    /**
     * Executed when master status is lost or if the server comes up as slave.
     */
    public void executeAsSlave() {
        //nothing to do for now.
    }
}
