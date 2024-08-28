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

package com.ericsson.oss.services.pm.collection.notification;

import static com.ericsson.oss.mediation.ftp.jca.api.error.FtpErrors.COULD_NOT_CONNECT_TO_NODE;
import static com.ericsson.oss.mediation.ftp.jca.api.error.FtpErrors.COULD_NOT_LIST_FILES;
import static com.ericsson.oss.mediation.ftp.jca.api.error.FtpErrors.FILE_ALREADY_COLLECTED;
import static com.ericsson.oss.mediation.ftp.jca.api.error.FtpErrors.FILE_ALREADY_EXISTS;
import static com.ericsson.oss.mediation.ftp.jca.api.error.FtpErrors.SOURCE_LOCATION_FILE_NOT_AVAILABLE;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.FILE_COLLECTION_FAILURE;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.FILE_COLLECTION_RESULT_NOTIFICATION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.FILE_COLLECTION_RECOVERY_RESULT;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.FILE_COLLECTION_RESULT;
import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheKeyConstants.FAILURE;
import static com.ericsson.oss.services.pm.initiation.cache.constants.CacheKeyConstants.SUCCESS;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.cache.Cache;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache;
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender;
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionTaskCacheWrapper;
import com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant;
import com.ericsson.oss.services.pm.collection.events.FileCollectionEvent;
import com.ericsson.oss.services.pm.collection.events.FileCollectionEventBuilder;
import com.ericsson.oss.services.pm.collection.events.FileCollectionFailure;
import com.ericsson.oss.services.pm.collection.events.FileCollectionResult;
import com.ericsson.oss.services.pm.collection.events.FileCollectionSuccess;
import com.ericsson.oss.services.pm.collection.instrumentation.CombinedRopFileCollectionCycleInstrumentation;
import com.ericsson.oss.services.pm.collection.instrumentation.FileCollectionStatistics;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.collection.schedulers.FileCollectionRecoveryManager;
import com.ericsson.oss.services.pm.common.logging.PMICLog.Operation;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionFailureStatistics;
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionResultStatistics;
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionSuccessStatistics;
import com.ericsson.oss.services.pm.initiation.util.RopTime;
import com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation;

/**
 * Listener to listen for file collection result notifications
 */
@ApplicationScoped
public class FileCollectionResultNotificationListener {

    private static final int UNKNOWN_SYSTEM_ERROR = 101;

    @Inject
    @Modeled
    private EventSender<FileCollectionEvent> fileCollectionEventSender;

    @Inject
    @NamedCache("PMICFileCollectionResultCache")
    private Cache<String, FileCollectionResultStatistics> cache;

    @Inject
    private FileCollectionRecoveryManager recoverer;

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    @Inject
    private Logger logger;

    @Inject
    private CombinedRopFileCollectionCycleInstrumentation fileCollectionInst;

    @Inject
    private FileCollectionStatistics fileCollectionStatistics;

    @Inject
    private ExtendedFileCollectionInstrumentation extendedFileCollectionInstrumentation;

    @Inject
    private FileCollectionTaskCacheWrapper fileCollectionTaskCache;

    @Inject
    private NodeService nodeService;

    @Inject
    private SupportedRopTimes supportedRopTimes;

    /**
     * @param fileCollectionResult
     *            - {@link FileCollectionResult} to be processed.
     */
    public void receiveFileCollectionResultEvent(@Observes @Modeled final FileCollectionResult fileCollectionResult) {
        logger.debug("Notification received for : {} ", fileCollectionResult);
        extendedFileCollectionInstrumentation.fileCollectionTaskEnded(fileCollectionResult);

        validateFileCollectionJobIdAndRemoveFromCache(fileCollectionResult);

        final List<FileCollectionFailure> fileCollectionFailure = fileCollectionResult.getFileCollectionFailure();
        if (fileCollectionFailure != null && fileCollectionFailure.size() == 1
                && fileCollectionFailure.get(0).getErrorCode() == UNKNOWN_SYSTEM_ERROR) {
            systemRecorder.eventCoarse(FILE_COLLECTION_RECOVERY_RESULT, fileCollectionResult.getNodeAddress(),
                    "UNKNOWN_SYSTEM_ERROR during file collection");
            logger.debug("UnRecoverable Scenario : Event/Headers were empty in Handler");
            return;
        }

        logFileCollectionResultInfo(fileCollectionResult);
        processFileCollectionSuccessAndFailure(fileCollectionResult);

        try {
            sendMessageToExternalConsumerAndSubscribers(fileCollectionResult);
        } catch (final Exception e) {
            systemRecorder.error(FILE_COLLECTION_RESULT_NOTIFICATION, FILE_COLLECTION_RESULT.toString(), fileCollectionResult.getNodeAddress(),
                    Operation.FILE_COLLECTION, e.getMessage());
        }
    }

    private void logFileCollectionResultInfo(final FileCollectionResult fileCollectionResult) {
        if (!fileCollectionResult.isRecoverInNextRop() && SupportedRopTimes.isRecoverySupported(Long.valueOf(fileCollectionResult.getRopPeriod()))) {
            logger.debug("File Collection recovery result for TaskId {} and File(s) {}", fileCollectionResult.getJobId(),
                    fileCollectionResult.getDestAndSourceFileNames().keySet());
        } else {
            logger.debug("Normal File Collection result for TaskId {} and File(s) {}.", fileCollectionResult.getJobId(),
                    fileCollectionResult.getDestAndSourceFileNames().keySet());
        }
    }

    private void validateFileCollectionJobIdAndRemoveFromCache(final FileCollectionResult fileCollectionResult) {
        try {
            if (fileCollectionResult.getJobId() != null) {
                fileCollectionTaskCache.removeTask(fileCollectionResult.getJobId());
            } else {
                logger.error("Failed to remove task [{}] from the cache", fileCollectionResult);
            }
        } catch (final Exception e) {
            logger.error("Failed to remove task {} from the cache {}",
                    fileCollectionResult.getJobId() == null ? "Null JobId" : fileCollectionResult.getJobId(), e.getMessage());
        }
    }

    /**
     * @param fileCollectionResult
     *            - file collection result to process file collection success
     * @purpose process file collection success
     */
    private void processFileCollectionSuccess(final FileCollectionResult fileCollectionResult) {
        final List<FileCollectionSuccess> fileCollectionSuccess = fileCollectionResult.getFileCollectionSuccess();
        if (fileCollectionSuccess == null || fileCollectionSuccess.isEmpty()) {
            logger.debug("No success file collection files available to for network element {}.", fileCollectionResult.getNodeAddress());
            return;
        }
        logger.debug("Processing file collection success for : {} files for network element {}.", fileCollectionSuccess.size(),
                fileCollectionResult.getNodeAddress());
        final List<String> successFiles = new ArrayList<>();
        for (final FileCollectionSuccess success : fileCollectionSuccess) {
            final String destinationFileName = success.getDestinationFileName();
            successFiles.add(destinationFileName);
            logger.debug("Processing file collection success for file : {} and recovery in next rop : {}", destinationFileName,
                    fileCollectionResult.isRecoverInNextRop());
        }
        if (fileCollectionResult.getAggregatedBytesStored() > 0 || fileCollectionResult.getAggregatedBytesTransferred() > 0) {
            final FileCollectionResultStatistics result = new FileCollectionSuccessStatistics(fileCollectionResult.getTaskStartTime(),
                    fileCollectionResult.getTaskEndTime(), fileCollectionResult.getAggregatedBytesTransferred(),
                    fileCollectionResult.getAggregatedBytesStored());
            final String key =
                    SUCCESS + fileCollectionSuccess.hashCode() + System.currentTimeMillis() + System.identityHashCode(fileCollectionSuccess);
            cache.put(key, result);
            logger.debug("Added FileCollectionSuccess statistics to cache : {} ", fileCollectionSuccess);
        }
        if (!fileCollectionResult.isRecoverInNextRop() && SupportedRopTimes.isRecoverySupported(Long.valueOf(fileCollectionResult.getRopPeriod()))) {
            logger.info("File Collection successfully recovered for taskId: {}. Files: {}.", fileCollectionResult.getJobId(), successFiles);
        }
    }

    private void processFileCollectionSuccessAndFailure(final FileCollectionResult fileCollectionResult) {
        final int numberOfSuccessfulHits = getNumberOfElementsInList(fileCollectionResult.getFileCollectionSuccess());
        final int numberOfFailedHits = getNumberOfFailedElementsInList(fileCollectionResult.getFileCollectionFailure(),
                FILE_ALREADY_EXISTS.getErrorCode());
        processFileCollectionSuccess(fileCollectionResult);
        processFileCollectionFailure(fileCollectionResult);
        processFileCollectionTaskFailure(fileCollectionResult);
        fileCollectionStatistics.updateData(fileCollectionResult, numberOfSuccessfulHits, numberOfFailedHits);
        final long ropPeriod = fileCollectionResult.getRopPeriod();
        final long ropStartTime = fileCollectionResult.getRopStartTime();
        if (!isSingleRopRecoveryTask(fileCollectionResult) || isSingleRopRecoveryInstrumentationSupported(ropStartTime, ropPeriod)) {
            fileCollectionInst.updateDataForSuccessAndFailure(ropPeriod, numberOfSuccessfulHits, numberOfFailedHits,
                    fileCollectionResult.getAggregatedBytesStored(),
                    fileCollectionResult.getAggregatedBytesTransferred());
        }
    }

    private boolean isSingleRopRecoveryInstrumentationSupported(final long ropStartTime, final long ropPeriod) {
        final RopTime ropEndTime = new RopTime(ropStartTime, ropPeriod).getCurrentROPPeriodEndTime();
        final RopTime singleRopRecoveryEndTime =
                SupportedRopTimes.calculateSingleRopRecoveryRopEndTime(ropStartTime, TimeUnit.SECONDS.toMillis(ropPeriod));
        return ropEndTime.getTime() == singleRopRecoveryEndTime.getTime();
    }

    private int getNumberOfElementsInList(final List<?> list) {
        int elementCount = 0;
        if (list != null) {
            elementCount = list.size();
        }
        return elementCount;
    }

    private int getNumberOfFailedElementsInList(final List<FileCollectionFailure> failuresList, final int failureType) {
        int elementCount = 0;
        for (final FileCollectionFailure fail : failuresList) {
            if (fail.getErrorCode() != failureType) {
                elementCount++;
            }
        }
        return elementCount;
    }

    private void processFileCollectionFailure(final FileCollectionResult fileCollectionResult) {
        final List<FileCollectionFailure> fileCollectionFailure = fileCollectionResult.getFileCollectionFailure();
        if (fileCollectionFailure == null || fileCollectionFailure.isEmpty()) {
            logger.debug("No failure file collection files available to process for network element {}.", fileCollectionResult.getNodeAddress());
            return;
        }
        logger.debug("Processing file collection failures for : {} files for network element {}.", fileCollectionFailure.size(),
                fileCollectionResult.getNodeAddress());
        final List<String> failedFiles = new ArrayList<>();
        int numberOfFailures = 0;
        for (final FileCollectionFailure failure : fileCollectionFailure) {
            final String destinationFileName = failure.getDestinationFileName();
            failedFiles.add(destinationFileName);
            logger.debug("Processing file collection failure for file : {} ", failure.getDestinationFileName());
            // do not record failure in UI or ElasticSearch if error code is FILE_ALREADY_EXISTS or SOURCE_LOCATION_FILE_NOT_AVAILABLE
            if (failure.getErrorCode() != FILE_ALREADY_EXISTS.getErrorCode() &&
                    failure.getErrorCode() != SOURCE_LOCATION_FILE_NOT_AVAILABLE.getErrorCode()
                    && failure.getErrorCode() != FILE_ALREADY_COLLECTED.getErrorCode()) {
                numberOfFailures++;
                // log failure
                final String message = String.format(
                        "File Collection failure received. File: %s. NodeType: %s. taskId: %s. ropPeriod: %d. Error code: %d. Error message: %s.",
                        failure.getDestinationFileName(), fileCollectionResult.getNeType(), fileCollectionResult.getJobId(), fileCollectionResult.getRopPeriod(),
                        failure.getErrorCode(), failure.getErrorMessage());
                systemRecorder.error(FILE_COLLECTION_FAILURE, FILE_COLLECTION_RESULT.toString(), fileCollectionResult.getNodeAddress(),
                        Operation.FILE_COLLECTION, message);
            }
        }
        if (numberOfFailures > 0) {
            final FileCollectionResultStatistics result = new FileCollectionFailureStatistics(fileCollectionResult.getTaskEndTime(),
                    numberOfFailures);
            final String key = FAILURE + fileCollectionFailure.hashCode() + System.currentTimeMillis()
                    + System.identityHashCode(fileCollectionFailure);
            cache.put(key, result);
            logger.debug("Added FileCollectionFailure statistics to cache : {} ", fileCollectionFailure);
            recoverer.recoverSingleFailure(fileCollectionResult);
        }
    }

    private void processFileCollectionTaskFailure(final FileCollectionResult fileCollectionResult) {
        if (isFileCollectionResultEmpty(fileCollectionResult) && isCandidateForNextRopRecovery(fileCollectionResult)) {
            final RopTime ropTime = new RopTime(fileCollectionResult.getRopStartTime(), fileCollectionResult.getRopPeriod());
            final String message = String
                    .format("File Collection failure for Subscription Type : %s. TaskId: %s. Rop Start Time: %s Rop Period: %d. Error code: %d. "
                            + "Error message: %s.", fileCollectionResult.getSubscriptionType(), fileCollectionResult.getJobId(),
                            ropTime.getUtcStartTime(), fileCollectionResult.getRopPeriod(), fileCollectionResult.getTaskStatusCode(),
                            fileCollectionResult.getTaskStatusMessage());
            systemRecorder.error(FILE_COLLECTION_FAILURE, FILE_COLLECTION_RESULT.toString(), fileCollectionResult.getNodeAddress(),
                    Operation.FILE_COLLECTION, message);
            // Mediation Autonomy Check
            if (!nodeService.isMediationAutonomyEnabled(fileCollectionResult.getNeType(), false)) {
                recoverer.addRecoveryTaskToQueue(fileCollectionResult);
            }
        }
    }

    private boolean isFileCollectionResultEmpty(final FileCollectionResult fileCollectionResult) {
        final List<FileCollectionFailure> fileCollectionFailure = fileCollectionResult.getFileCollectionFailure();
        final List<FileCollectionSuccess> fileCollectionSuccess = fileCollectionResult.getFileCollectionSuccess();
        return fileCollectionFailure.isEmpty() && fileCollectionSuccess.isEmpty();
    }

    private boolean isCandidateForNextRopRecovery(final FileCollectionResult fileCollectionResult) {
        return (fileCollectionResult.getTaskStatusCode() == COULD_NOT_LIST_FILES.getErrorCode()
                || fileCollectionResult.getTaskStatusCode() == COULD_NOT_CONNECT_TO_NODE.getErrorCode()) && fileCollectionResult
                        .isRecoverInNextRop();
    }

    private void sendMessageToExternalConsumerAndSubscribers(final FileCollectionResult fileCollectionResult) {
        if (!isFileCollectionResultEmpty(fileCollectionResult)) {
            final FileCollectionEvent fileCollectionEvent = buildFileCollectionEvent(fileCollectionResult);
            fileCollectionEventSender.send(fileCollectionEvent);
            logger.debug("Sent file collection result to FileCollectionResultNotificationTopic {}",
                    fileCollectionEvent);
        }
    }

    private FileCollectionEvent buildFileCollectionEvent(final FileCollectionResult fileCollectionResult) {
        return new FileCollectionEventBuilder().subscriptionType(fileCollectionResult.getSubscriptionType())
                .destinationDirectory(fileCollectionResult.getDestinationDirectory()).ropStartTime(fileCollectionResult.getRopStartTime())
                .ropPeriod(fileCollectionResult.getRopPeriod()).nodeAddress(fileCollectionResult.getNodeAddress())
                .taskEndTime(fileCollectionResult.getTaskEndTime()).neType(fileCollectionResult.getNeType())
                .ossModelIdentity(fileCollectionResult.getOssModelIdentity())
                .successDestinationFileNames(fileCollectionResult.getFileCollectionSuccess())
                .failedDestinationFileNames(fileCollectionResult.getFileCollectionFailure()).build();
    }

    private boolean isSingleRopRecoveryTask(final FileCollectionResult fileCollectionResult) {
        final String fileCollectionTaskId = fileCollectionResult.getJobId();
        return fileCollectionTaskId != null
                && fileCollectionTaskId.contains(FileCollectionConstant.FILE_COLLECTION_SINGLE_ROP_RECOVERY_TASK_ID_PREFIX);
    }
}
