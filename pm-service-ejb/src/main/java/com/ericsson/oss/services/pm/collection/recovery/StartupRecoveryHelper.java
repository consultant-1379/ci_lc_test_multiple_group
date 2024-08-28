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

package com.ericsson.oss.services.pm.collection.recovery;

import static com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants.ONE_MINUTE_IN_SECONDS;
import static com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants.ONE_SECOND_IN_MILLISECONDS;
import static com.ericsson.oss.services.pm.model.PMCapability.SupportedRecoveryTypes.RECOVERY_ON_STARTUP;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.pmjob.PmJob;
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.services.pm.collection.api.FileCollectionTaskManagerLocal;
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;
import com.ericsson.oss.services.pm.collection.api.ProcessTypesAndRopInfo;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper;
import com.ericsson.oss.services.pm.common.logging.PMICLog;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.generic.PmJobService;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.pmjobs.helper.PmJobHelper;
import com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation;

/**
 * Helper class for startup Recovery.
 */
@Stateless
public class StartupRecoveryHelper {

    @Inject
    private Logger logger;
    @Inject
    private RecoveryHandler recoveryHandler;
    @Inject
    private ScannerService scannerService;
    @Inject
    private PmJobService pmJobService;
    @Inject
    private NodeService nodeService;
    @Inject
    private PmJobHelper pmJobHelper;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private FileCollectionActiveTaskCacheWrapper fileCollectionActiveTaskCache;
    @Inject
    private FileCollectionTaskManagerLocal fileCollectionTaskManager;
    @Inject
    private ExtendedFileCollectionInstrumentation extendedFileCollectionInstrumentation;

    /**
     * Find all Scanners with given fileCollectionEnabled attribute.
     *
     * @return Returns a list of the scanners or an empty list.
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    public List<Scanner> getActiveScannerWithFileCollectionEnabled() throws DataAccessException {
        final ScannerStatus[] scannerStatuses = {ScannerStatus.ACTIVE};
        return scannerService.findAllByNodeFdnAndProcessTypeAndRopDurationAndScannerStatusAndFileCollection(null, null, null, scannerStatuses, true);
    }

    /**
     * Get boolean value of mediation autonomy support for node
     *
     * @param nodeFdn
     *         - FDN of the node
     *
     * @return value of mediation autonomy capability : TRUE/FALSE
     */
    public boolean isMediationAutonomyEnabled(final String nodeFdn) {
        return nodeService.isMediationAutonomyEnabled(nodeFdn);
    }

    /**
     * Get boolean value of startup recovery support for node
     *
     * @param nodeFdn
     *         - FDN of the node
     *
     * @return true if start up recovery is supported for the Node
     */
    public boolean isStartUpRecoverySupported(final String nodeFdn) {
        return nodeService.isRecoveryTypeSupported(nodeFdn, RECOVERY_ON_STARTUP.name());
    }

    /**
     * Find all PmJobs for given process types and PmJobStatuses.
     *
     * @return - List of PmJobs or empty list.
     * @throws DataAccessException
     *         - if any exception from Data Access layer is thrown
     */
    public List<PmJob> getActivePmJobs() throws DataAccessException {
        return pmJobService.findAllByProcessTypeAndPmJobStatus(Collections.singleton(ProcessType.CTUM), PmJobStatus.ACTIVE);
    }

    /**
     * Remove ProcessRequestVO from cache which are not in DPS and which are not PmJob based
     *
     * @param nodesWithActiveScanner
     *         process request stored in DPS for nodes with active scanners
     * @param scanners
     *         Active node scanners
     */
    public void cleanupCacheForScannerWithFileCollectionNotEnable(final Map<String, ProcessTypesAndRopInfo> nodesWithActiveScanner,
                                                                  final List<Scanner> scanners) {
        final Set<ProcessRequestVO> processRequestsCache = fileCollectionActiveTaskCache.getProcessRequests();
        final Set<ProcessRequestVO> processRequestsDps = buildProcessRequestVOs(nodesWithActiveScanner);
        int noOfRemovedProcessRequests = 0;
        for (final ProcessRequestVO processRequestInCache : processRequestsCache) {
            if (!processRequestsDps.contains(processRequestInCache) && !pmJobHelper.isPmJobSupported(processRequestInCache.getProcessType())) {
                fileCollectionActiveTaskCache.removeProcessRequest(processRequestInCache);
                systemRecorder.eventCoarse(PMICLog.Event.STARTUP_FILE_RECOVERY, processRequestInCache.getNodeAddress(),
                        "Removing from the cache as entry not found in DPS. Request {}" + processRequestInCache.toString());
                noOfRemovedProcessRequests++;
            }
        }
        systemRecorder.eventCoarse(PMICLog.Event.STARTUP_FILE_RECOVERY, StartupRecoveryHelper.class.getSimpleName(),
                "Removed " + noOfRemovedProcessRequests + " extra process requests from cache, Number of Active scanner: " + scanners.size());
        logger.info("Removed {} extra process requests from cache", noOfRemovedProcessRequests);
    }

    /**
     * Return fileCollectionActiveTaskCache is empty or not
     * <p>
     *
     * @return true if fileCollectionActiveTaskCache size is 0 else return false
     */
    public boolean isCacheEmpty() {
        return fileCollectionActiveTaskCache.size() == 0;
    }

    private Set<ProcessRequestVO> buildProcessRequestVOs(final Map<String, ProcessTypesAndRopInfo> nodesWithProcessTypes) {
        final Set<ProcessRequestVO> processRequests = new HashSet<>();
        for (final Map.Entry<String, ProcessTypesAndRopInfo> entry : nodesWithProcessTypes.entrySet()) {
            final String nodeAddress = entry.getKey();
            final ProcessTypesAndRopInfo processAndRopInfo = entry.getValue();
            final Map<Integer, Set<String>> ropAndProcessTypeInfo = processAndRopInfo.getRopAndProcessTypes();

            for (final Map.Entry<Integer, Set<String>> entryProcessAndRopInfo : ropAndProcessTypeInfo.entrySet()) {
                final Integer ropPeriod = entryProcessAndRopInfo.getKey();
                final Set<String> processTypes = entryProcessAndRopInfo.getValue();
                logger.debug("Adding Node {} for file collection of {} seconds ROP interval ", nodeAddress, ropPeriod);
                for (final String processType : processTypes) {
                    final ProcessRequestVO requestVO = new ProcessRequestVO.ProcessRequestVOBuilder(nodeAddress, ropPeriod, processType).build();
                    processRequests.add(requestVO);
                }
            }
        }
        return processRequests;
    }

    /**
     * Handle startup recovery for active node scanners.
     *
     * @param nodesWithActiveScanner
     *         - active scanner the scanners processRequest
     * @param timeWhenLastCollectionStarted
     *         - time when last collection started
     */
    public void handleActiveScanners(final Map<String, ProcessTypesAndRopInfo> nodesWithActiveScanner, final Long timeWhenLastCollectionStarted) {
        final Set<ProcessRequestVO> processRequestsToRecover = recoverAllActiveTasks(nodesWithActiveScanner);
        logger.info("Found {} process request for startup recovery", processRequestsToRecover.size());
        for (final ProcessRequestVO processRequest : processRequestsToRecover) {
            try {
                recoveryHandler.recoverFilesForNode(processRequest, ropsToRecover(processRequest.getRopPeriod(), timeWhenLastCollectionStarted),
                        false);
            } catch (final Exception e) {
                if (processRequest.getNodeAddress() != null) {
                    logger.error("Startup recovery encountered error for node {}: {} ", processRequest.getNodeAddress(), e.getMessage());
                    logger.info("Startup recovery encountered error for node {} ", processRequest.getNodeAddress(), e);
                } else {
                    logger.error("Startup recovery encountered an error, ProcessRequest is Null: {}", e.getMessage());
                    logger.info("Startup recovery encountered an error, ProcessRequest is Null", e);
                }
            }
        }
        extendedFileCollectionInstrumentation.startupFileRecoveryTaskGroupStarted(processRequestsToRecover);
    }

    /**
     * @return number of rops in recovery period
     */
    private long ropsToRecover(final int ropTimeInSeconds, final Long timeWhenLastCollectionStarted) {
        final long totalDownTime = System.currentTimeMillis() - timeWhenLastCollectionStarted;
        return totalDownTime / (ropTimeInSeconds / ONE_MINUTE_IN_SECONDS * ONE_SECOND_IN_MILLISECONDS * ONE_MINUTE_IN_SECONDS);
    }

    private Set<ProcessRequestVO> recoverAllActiveTasks(final Map<String, ProcessTypesAndRopInfo> nodesWithActiveScanner) {
        resumeNormalFileCollection(nodesWithActiveScanner);
        // get all request for all the supported rop times and then recover
        final Set<ProcessRequestVO> processRequestsToRecover = new HashSet<>();
        final Set<ProcessRequestVO> processRequests = recoveryHandler.buildProcessRequestForAllActiveProcess();
        logger.debug("Active file collection jobs in cache {} ",processRequests.size() );
        for(ProcessRequestVO processRequest : processRequests){
           if(ProcessType.STATS.name().equals(processRequest.getProcessType())){
                logger.debug("startup recovery supported for {}", processRequest);
                processRequestsToRecover.add(processRequest);
            }
        }
        return processRequestsToRecover;
    }

    /**
     * If the cache is not recovered, this method would make sure to resume normal file collection for all active scanners/tasks
     */
    private void resumeNormalFileCollection(final Map<String, ProcessTypesAndRopInfo> nodesWithActiveScanner) {
        fileCollectionTaskManager.startFileCollectionForNodes(nodesWithActiveScanner);
        logger.debug("Resumed file collection for nodes with active scanner: {}", nodesWithActiveScanner);
    }

}
