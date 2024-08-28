/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.pmjobs.sync;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to contain the result of the PmJobProcessor
 */
public class PmJobSyncResult {

    private List<SyncResultVO> pmJobsToActivate = new ArrayList<>();
    private List<SyncResultVO> pmJobsToDeactivate = new ArrayList<>();
    private List<String> pmJobsToDelete = new ArrayList<>();

    /**
     * Gets list of pm jobs which needs to be activated in ENM
     *
     * @return List of ProcessorResultVO can be used for activation
     */
    public List<SyncResultVO> getPmJobsToActivate() {
        return pmJobsToActivate;
    }

    /**
     * Sets the list of jobs for activation
     *
     * @param pmJobsToActivate
     *         List of ProcessorResultVO can be used for activation
     */
    public void setPmJobsToActivate(final List<SyncResultVO> pmJobsToActivate) {
        this.pmJobsToActivate = pmJobsToActivate;
    }

    /**
     * Gets the list of Pm Jobs which needs to be deactivated in ENM
     *
     * @return List of ProcessorResultVO can be used for deactivation
     */
    public List<SyncResultVO> getPmJobsToDeactivate() {
        return pmJobsToDeactivate;
    }

    /**
     * Sets the list of jobs for deactivation
     *
     * @param pmJobsToDeactivate
     *         List of ProcessorResultVO can be used for deactivation
     */
    public void setPmJobsToDeactivate(final List<SyncResultVO> pmJobsToDeactivate) {
        this.pmJobsToDeactivate = pmJobsToDeactivate;
    }

    /**
     * Gets the list of pm jobs which needs to be deleted in ENM
     *
     * @return List of ProcessorResultVO can be used for deletion
     */
    public List<String> getPmJobsToDelete() {
        return pmJobsToDelete;
    }

    /**
     * Sets the list of jobs for deletion
     *
     * @param pmJobsToDelete
     *         List of ProcessorResultVO can be used for deletion
     */
    public void setPmJobsToDelete(final List<String> pmJobsToDelete) {
        this.pmJobsToDelete = pmJobsToDelete;
    }
}
