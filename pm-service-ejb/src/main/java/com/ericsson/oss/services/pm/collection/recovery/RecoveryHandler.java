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

package com.ericsson.oss.services.pm.collection.recovery;

import java.util.Set;

import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionScheduledRecoveryCacheWrapper;
import com.ericsson.oss.services.pm.collection.task.factories.StatisticalRecoveryTaskRequestFactory;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;
import com.ericsson.oss.services.pm.eventSender.PmEventSender;
import com.ericsson.oss.services.pm.initiation.util.RopTime;
import com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants;
import com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation;

/**
 * Handler for missed file recovery
 */
public class RecoveryHandler {

    @Inject
    private Logger logger;

    @Inject
    private PmEventSender sender;

    @Inject
    private FileCollectionActiveTaskCacheWrapper fileCollectionActiveTaskCache;

    @Inject
    private StatisticalRecoveryTaskRequestFactory statisticalRecoveryTaskRequestFactory;

    @Inject
    private ExtendedFileCollectionInstrumentation extendedFileCollectionInstrumentation;

    @Inject
    private FileCollectionScheduledRecoveryCacheWrapper fileCollectionScheduledRecoveryCache;

    /**
     * Builds a set {@link com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest} for the given ropsToRecover and sends to
     * Mediation.
     *
     * @param processRequestRecovery
     *            - the request for a given Node, processType and Record Output Period
     * @param ropsToRecover
     *            - the number of Record Output Periods to recover backwards from now.
     * @param isDeltaRecovery
     *            - if its delta (scheduled or not)
     */
    public void recoverFilesForNode(final ProcessRequestVO processRequestRecovery, final long ropsToRecover, final boolean isDeltaRecovery) {
        if (ropsToRecover <= 0) {
            logger.debug("ropsToRecover: {}. ", ropsToRecover);
            return;
        }
        final RopTime currentRopTime = new RopTime(System.currentTimeMillis(), processRequestRecovery.getRopPeriod());
        final RopTime ropStart = isDeltaRecovery ? currentRopTime.getLastROP(1) : currentRopTime;
        FileCollectionTaskRequest task;
        if (isDeltaRecovery) {
            logger.info("Building Delta FileCollectionTaskRequest for {} rops for the node {}. Request Details: [{}]", ropsToRecover,
                    processRequestRecovery.getNodeAddress(), processRequestRecovery);
            task = statisticalRecoveryTaskRequestFactory.createFileCollectionDeltaRecoveryTaskRequest(processRequestRecovery.getNodeAddress(),
                    ropStart.getTime(), processRequestRecovery.getRopPeriod() * TimeConstants.ONE_SECOND_IN_MILLISECONDS, ropsToRecover);
        } else {
            logger.info(
                    "Building Start Up FileCollectionTaskRequest for {} rops for the node {}. Recovery rop start Time {}. Request Details: [{}]",
                    ropsToRecover, processRequestRecovery.getNodeAddress(), ropStart.getCurrentRopStartTimeInMilliSecs(), processRequestRecovery);
            task = statisticalRecoveryTaskRequestFactory.createFileCollectionRecoveryOnStartupTaskRequest(processRequestRecovery.getNodeAddress(),
                    ropStart.getCurrentRopStartTimeInMilliSecs(), processRequestRecovery.getRopPeriod() * TimeConstants.ONE_SECOND_IN_MILLISECONDS,
                    ropsToRecover + 1);
        }
        extendedFileCollectionInstrumentation.scheduledFileRecoveryTaskStarted(task);
        final boolean taskSent = sender.sendPmEvent(task);
        logger.debug("FileCollectionTaskRequest task success is {} tasks for node : {}. Delta Recovery = {}", taskSent, task.getNodeAddress(),
                isDeltaRecovery);

    }

    /**
     * @return Set of requests for nodes that the file collection is currently running for..
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Set<ProcessRequestVO> buildProcessRequestForAllActiveProcess(final Integer... rops) {
        logger.debug("Getting active process requests from cache for the rops {} ",rops);
        return fileCollectionActiveTaskCache.getProcessRequestForRop(rops);
    }

    /**
     * Returns a Set of ProcessRequests that were deactivated within the recovery period.
     *
     * @param allRequests
     *            - set of Process Requests
     * @param recoveryPeriod
     *            - numerical value for the recovery period
     * @return {@code Set<ProcessRequestVO>}
     */
    public Set<ProcessRequestVO> appendProcessRequestForAllRemovedProcesses(final Set<ProcessRequestVO> allRequests, final int recoveryPeriod,
            final Integer... rops) {
        final Set<ProcessRequestVO> processRequestsCache = fileCollectionScheduledRecoveryCache.getProcessRequestForRop(rops);
        logger.debug("Found {} nodes in cache which were deactivated since the last scheduled recovery ran.", processRequestsCache.size());
        final long recoveryStartPeriod = System.currentTimeMillis() - recoveryPeriod * TimeConstants.ONE_HOUR_IN_MILLISECONDS;
        for (final ProcessRequestVO processRequest : processRequestsCache) {
            if (!hasExpired(processRequest, recoveryStartPeriod)) {
                allRequests.add(processRequest);
            }
            fileCollectionScheduledRecoveryCache.removeProcessRequest(processRequest);
        }
        return allRequests;
    }

    /**
     * Determines if the ProcessRequest falls within the recovery period.
     *
     * @param processRequest
     *            - Process Request
     * @param recoveryStartPeriod
     *            - numerical value for recovery start period
     * @return - returns true if recovery period has expired
     */
    private boolean hasExpired(final ProcessRequestVO processRequest, final long recoveryStartPeriod) {
        final Long endTime = processRequest.getEndTime();
        return endTime != null && endTime < recoveryStartPeriod;
    }
}
