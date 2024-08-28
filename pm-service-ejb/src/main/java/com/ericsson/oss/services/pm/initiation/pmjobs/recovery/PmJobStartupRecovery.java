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

package com.ericsson.oss.services.pm.initiation.pmjobs.recovery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.pmjob.PmJob;
import com.ericsson.oss.services.pm.collection.api.FileCollectionTaskManagerLocal;
import com.ericsson.oss.services.pm.collection.api.ProcessTypesAndRopInfo;

/**
 * Recovery bean for pm jobs.
 */
@Stateless
public class PmJobStartupRecovery {

    @Resource
    private SessionContext sessionContext;

    @Inject
    private Logger logger;

    @Inject
    private FileCollectionTaskManagerLocal fileCollectionTaskManager;

    /**
     * Performs startup recovery for PM Job based file collections.
     *
     * @param activePmJobs
     *         the active pm jobs
     */
    public void recoverActivePmJobs(final List<PmJob> activePmJobs) {
        final Map<String, ProcessTypesAndRopInfo> nodesWithActivePmJobs = getNodesWithActivePmJobs(activePmJobs);
        resumeNormalFileCollection(nodesWithActivePmJobs);
    }

    /**
     * This method extract nodes that have active pm jobs. Collection must be resumed for these nodes after start up.
     */
    private Map<String, ProcessTypesAndRopInfo> getNodesWithActivePmJobs(final List<PmJob> activePmJobs) {
        final Map<String, ProcessTypesAndRopInfo> nodesWithActivePmJobs = new HashMap<>();

        for (final PmJob pmJob : activePmJobs) {
            final ProcessTypesAndRopInfo processTypeAndRop = new ProcessTypesAndRopInfo();
            processTypeAndRop.addRopInfoAndProcessType(pmJob.getRopPeriod(), pmJob.getProcessType().name());
            nodesWithActivePmJobs.put(pmJob.getNodeFdn(), processTypeAndRop);
        }
        return nodesWithActivePmJobs;
    }

    /**
     * Resume normal file collection in separate tx.
     *
     * @param nodesWithActiveScanner
     *         the nodes with active scanner map
     */
    public void resumeNormalFileCollection(final Map<String, ProcessTypesAndRopInfo> nodesWithActiveScanner) {
        fileCollectionTaskManager.startFileCollectionForNodes(nodesWithActiveScanner);
        logger.debug("Resumed file collection for nodes with active scanner: {}", nodesWithActiveScanner);
        logger.info("Total number of file collection tasks added to cache for the active Pmjobs: {}", nodesWithActiveScanner.size());
    }

}
