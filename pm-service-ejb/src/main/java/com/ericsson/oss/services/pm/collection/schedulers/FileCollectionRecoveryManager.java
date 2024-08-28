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

package com.ericsson.oss.services.pm.collection.schedulers;

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.ALLOWED_OPERATOR;
import static com.ericsson.oss.services.pm.initiation.log.events.PmicLogEvents.FILE_COLLECTION_NOT_RECOVERABLE;
import static com.ericsson.oss.services.pm.model.PMCapability.SupportedRecoveryTypes.SINGLE_ROP_RECOVERY;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.recording.SystemRecorder;
import com.ericsson.oss.mediation.ftp.jca.api.error.FtpErrors;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionTaskCacheWrapper;
import com.ericsson.oss.services.pm.collection.events.FileCollectionFailure;
import com.ericsson.oss.services.pm.collection.events.FileCollectionResult;
import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.collection.task.factories.FileCollectionSingleRopRecoveryTaskRequestFactory;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.cache.model.PMICFileCollectionTaskCache;
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;
import com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages;
import com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.Operations;
import com.ericsson.oss.services.pm.initiation.util.RopTime;
import com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants;

/***
 * Class responsible to manage the recovery of FileCollection failures.
 *
 * @author epirgui, eushmar - updated for Node Reconnection
 */
@Stateless
public class FileCollectionRecoveryManager {

    private static final String FILE_COLLECTION = "FILE_COLLECTION";

    @Inject
    private SystemRecorder recorder;
    @Inject
    private Logger logger;
    @Inject
    private FileCollectionTaskCacheWrapper fileCollectionTaskCache;
    @Inject
    private NodeService nodeService;
    @Inject
    private SupportedRopTimes supportedRopTimes;
    @Inject
    private FileCollectionSingleRopRecoveryTaskRequestFactory fileCollectionSingleRopRecoveryTaskRequestFactory;

    /**
     * Recover single failure.
     *
     * @param fileCollectionResult
     *            the file collection result
     */
    public void recoverSingleFailure(final FileCollectionResult fileCollectionResult) {
        final List<FileCollectionFailure> fileCollectionFailure = fileCollectionResult.getFileCollectionFailure();
        final Map<String, String> filesDueForRecovery = new HashMap<>();
        for (final FileCollectionFailure failure : fileCollectionFailure) {
            final String message = String.format(
                    "File Collection failure received for network element: %s. File Name: %s. Error code: %d. Error message: %s",
                    fileCollectionResult.getNodeAddress(), failure.getDestinationFileName(), failure.getErrorCode(), failure.getErrorMessage());
            logger.debug(message);
            final String destFileName = failure.getDestinationFileName();
            // check if failure is due to recovery
            if (isRecoverable(failure) && fileCollectionResult.isRecoverInNextRop()
                    && isSingleRopRecoverySupported(fileCollectionResult.getNodeAddress())) {
                filesDueForRecovery.put(destFileName, fileCollectionResult.getDestAndSourceFileNames().get(destFileName));
            } else {
                final String additionalInformation = getErrorMessage(fileCollectionResult, failure);
                recordEvent(fileCollectionResult, additionalInformation);
            }
        }
        // invoke taskManager to recovery
        // build the single rop recovery task and send it.
        if (!filesDueForRecovery.isEmpty()) {
            addRecoveryTaskToQueue(fileCollectionResult);
        }
    }

    private boolean isSingleRopRecoverySupported(final String nodeFdn) {
        final boolean isSingleRopRecoverySupported = nodeService.isRecoveryTypeSupported(nodeFdn, SINGLE_ROP_RECOVERY.name());
        logger.debug("for nodeFdn : {}, isSingleRopRecoverySupported : {} ", nodeFdn, isSingleRopRecoverySupported);
        return isSingleRopRecoverySupported;
    }

    private String getErrorMessage(final FileCollectionResult fileCollectionResult, final FileCollectionFailure failure) {

        // collection was for a recovery, shouldn't recover again
        if (fileCollectionResult.isRecoverInNextRop() || failureIsOfType(failure, FtpErrors.FILE_ALREADY_COLLECTED)) {
            return "PMIC, "
                    + ALLOWED_OPERATOR
                    + ", "
                    + ApplicationMessages.Operations.FILE_RECOVERY.getOperationCode()
                    + ", "
                    + this.getClass().getSimpleName()
                    + ", "
                    + String.format(
                            "PMIC, %s, %s, %s, It isn't necessary to recover this failure. taskId: %s. Failure Error code: %d."
                                    + " Failure Error message: %s",
                            ALLOWED_OPERATOR, Operations.FILE_RECOVERY.getOperationCode(), this.getClass().getSimpleName(),
                            fileCollectionResult.getJobId(), failure.getErrorCode(), failure.getErrorMessage());
        }
        return String.format(
                "PMIC, %s, %s, %s, File Collection couldn't recover file: %s. taskId: %s. Failure Error code: %d."
                        + " Failure Error message: %s",
                ALLOWED_OPERATOR, Operations.FILE_RECOVERY.getOperationCode(), this.getClass().getSimpleName(),
                failure.getDestinationFileName(), fileCollectionResult.getJobId(), failure.getErrorCode(),
                failure.getErrorMessage());
    }

    private void recordEvent(final FileCollectionResult fileCollectionResult, final String additionalInformation) {
        recorder.recordEvent(
                FILE_COLLECTION_NOT_RECOVERABLE.getEventKey(),
                FILE_COLLECTION_NOT_RECOVERABLE.getEventLevel(),
                FILE_COLLECTION,
                fileCollectionResult.getNodeAddress(),
                additionalInformation);
    }

    /**
     * Translates an instance of {@link FileCollectionResult} into a {@link FileCollectionTaskRequest} that corresponds to a single ROP recovery and
     * queues it into the modeled cache {@link PMICFileCollectionTaskCache} so that the file collection request is re-sent upon the next immediate
     * file collection schedule.
     *
     * @param fileCollectionResult
     *            - file collection result to provide params to factory to create file collection recovery task
     */
    public void addRecoveryTaskToQueue(final FileCollectionResult fileCollectionResult) {
        final FileCollectionTaskRequest recoveryTask = fileCollectionSingleRopRecoveryTaskRequestFactory
                .createFileCollectionSingleRopRecoveryTaskRequest(fileCollectionResult.getJobId(), fileCollectionResult.getNodeAddress(),
                        fileCollectionResult.getSubscriptionType(), fileCollectionResult.getRopStartTime(),
                        fileCollectionResult.getRopPeriod() * TimeConstants.ONE_SECOND_IN_MILLISECONDS,
                        fileCollectionResult.getDestAndSourceFileNames());
        logger.debug("Adding recovery task for ne {}, ropPeriod {} to sender queue. Task details: {}", recoveryTask.getNodeAddress(),
                recoveryTask.getRopPeriod(), recoveryTask);
        addTaskToSendQueue(recoveryTask);
        logger.debug("File Collection recovery taskId: {} created for failed taskId: {}.",
                recoveryTask.getJobId(), fileCollectionResult.getJobId());
    }

    private void addTaskToSendQueue(final FileCollectionTaskRequest task) {
        logger.debug("Adding File Collection task {} to sender queue for ropPeriod {}.", task, task.getRopPeriod());
        final RopTimeInfo ropTimeInfo = supportedRopTimes.getRopTime(task.getRopPeriod() / TimeConstants.ONE_SECOND_IN_MILLISECONDS);
        final RopTime ropEndTime = SupportedRopTimes.calculateSingleRopRecoveryRopEndTime(task.getRopPeriod());
        final FileCollectionTaskWrapper fileCollectionTaskWrapper;
        fileCollectionTaskWrapper = new FileCollectionTaskWrapper(task, ropEndTime, ropTimeInfo);
        fileCollectionTaskCache.addTask(fileCollectionTaskWrapper);
        logger.debug("Added File Collection task {} to sender queue for ropPeriod {}. Queue size: {}", task, task.getRopPeriod(),
                fileCollectionTaskCache.size());
    }

    /**
     * @param failure
     *            the file collection failure
     * @return returns true if file is recoverable
     */
    protected boolean isRecoverable(final FileCollectionFailure failure) {
        logger.debug("Checking if file {} to be recovered", failure.getDestinationFileName());
        boolean isRecoveryNeeded = true;
        if (failureIsOfType(failure, FtpErrors.FILE_ALREADY_EXISTS, FtpErrors.FILE_SIZE_LIMIT_EXCEEDED,
                FtpErrors.SOURCE_LOCATION_FILE_NOT_AVAILABLE)) {
            isRecoveryNeeded = false;
            logger.debug("File {} won't be recovered because of error code {} ", failure.getDestinationFileName(), failure.getErrorCode());
        } else {
            logger.debug("File {} will be recovered  ", failure.getDestinationFileName());
        }
        return isRecoveryNeeded;
    }

    private boolean failureIsOfType(final FileCollectionFailure failure, final FtpErrors... errors) {
        final int errorCode = failure.getErrorCode();
        for (final FtpErrors error : errors) {
            if (error.getErrorCode() == errorCode) {
                return true;
            }
        }
        return false;
    }
}
