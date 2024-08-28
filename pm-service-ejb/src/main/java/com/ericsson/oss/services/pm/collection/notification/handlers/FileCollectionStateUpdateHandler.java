/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.collection.notification.handlers;

import java.util.Collections;
import java.util.List;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import com.ericsson.oss.pmic.impl.handler.InvokeInTransaction;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.dto.pmjob.PmJob;
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.model.ned.pm.function.FileCollectionState;
import com.ericsson.oss.services.pm.config.task.factories.PmConfigTaskRequestFactory;
import com.ericsson.oss.services.pm.eventSender.PmEventSender;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.generic.PmJobService;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionManager;
import com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * This class can handle only behavior related to FileCollectionState update event.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class FileCollectionStateUpdateHandler {
    private static final Logger logger = LoggerFactory.getLogger(FileCollectionStateUpdateHandler.class);

    @Inject
    private NodeService nodeService;
    @Inject
    private PmJobService pmJobService;
    @Inject
    private PmEventSender pmEventSender;
    @Inject
    private ScannerService scannerService;
    @Inject
    private SubscriptionManager subscriptionManager;
    @Inject
    private PmConfigTaskRequestFactory pmConfigTaskRequestFactory;
    @Inject
    private FileCollectionOperationHelper fileCollectionOperationHelper;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    /**
     * Handle FileCollectionState attribute updates
     *
     * @param nodeFdn
     *         - the fdn of the NetworkElement MO
     * @param fileCollectionState
     *         - the value for attribute fileCollectionState
     */
    @ReadOnly
    @InvokeInTransaction
    public void handleFileCollectionStateAttribute(final String nodeFdn, final FileCollectionState fileCollectionState) {
        try {
            final List<Scanner> scanners = scannerService.findAllByNodeFdnAndSubscriptionIdAndScannerStatusInReadTx(Collections.singleton(nodeFdn), null,
                    ScannerStatus.ACTIVE);
            logger.debug("Scanners active found: {}", scanners);

            final List<PmJob> pmJobs = pmJobService.findAllbyNodeFdnAndPmJobStatus(Collections.singleton(nodeFdn), PmJobStatus.ACTIVE);
            logger.debug("PmJobs active found: {}", pmJobs);

            if (nodeService.isMediationAutonomyEnabled(nodeFdn)) {
                if (!scanners.isEmpty() || !pmJobs.isEmpty()) {
                    updateFileCollectionScheduleForNodeWithMediationAutonomy(nodeFdn, fileCollectionState);
                }
                return;
            }
            updateFileCollectionStateForActiveScanners(nodeFdn, fileCollectionState, scanners);
            updateFileCollectionStateForActivePmJobs(nodeFdn, fileCollectionState, pmJobs);
        } catch (final RetryServiceException | DataAccessException | InvalidSubscriptionException | RuntimeDataAccessException exception) {
            logger.error("Could not process cache error entry for {}", nodeFdn);
            logger.info("Could not process cache error entry for {}", nodeFdn, exception);
        }
    }

    /**
     * Update File Collection Schedule for nodes with Mediation Autonomy Enabled
     *
     * @param nodeFdn
     *         - the fdn of the NetworkElement MO
     * @param fileCollectionState
     *         - the value for attribute fileCollectionState
     */
    public void updateFileCollectionScheduleForNodeWithMediationAutonomy(final String nodeFdn, final FileCollectionState fileCollectionState) {
        final boolean enableFileCollectionSchedule = FileCollectionState.ENABLED == fileCollectionState;
        logger.debug("{} file collection on node {} with Mediation Autonomy", enableFileCollectionSchedule ? "Starting" : "Stopping", nodeFdn);
        final MediationTaskRequest mediationTaskRequest = pmConfigTaskRequestFactory.createFileCollectionConfigTask(nodeFdn,
                enableFileCollectionSchedule);
        final boolean ignorePmFunctionValue = true;
        pmEventSender.sendPmEvent(mediationTaskRequest, ignorePmFunctionValue);
    }

    /**
     * Find all active scanners for the node and start or stop file collection for the rop
     *
     * @param nodeFdn
     *         - the fdn of the node for file collection
     * @param fileCollectionState
     *         - the value for attribute fileCollectionState
     *
     * @throws RetryServiceException
     *         - if an invalid input exception is generated
     * @throws InvalidSubscriptionException
     *         - if an exception is thrown while trying to resolve the exception.
     * @throws DataAccessException
     *         - if an exception is thrown while updating subscription attributes.
     */
    private void updateFileCollectionStateForActiveScanners(final String nodeFdn, final FileCollectionState fileCollectionState,
                                                            final List<Scanner> scanners)
        throws RetryServiceException, DataAccessException, InvalidSubscriptionException {
        for (final Scanner scanner : scanners) {
            final int ropPeriodInSeconds = scanner.getRopPeriod();
            final ProcessType processType = scanner.getProcessType();
            final Long subscriptionId = scanner.getSubscriptionId();
            updateFileCollectionBehaviour(nodeFdn, ropPeriodInSeconds, processType, subscriptionId, fileCollectionState);
        }
    }

    private void updateFileCollectionStateForActivePmJobs(final String nodeFdn, final FileCollectionState fileCollectionState,
                                                          final List<PmJob> pmJobs)
        throws RetryServiceException, DataAccessException, InvalidSubscriptionException {
        for (final PmJob pmJob : pmJobs) {
            final int ropPeriodInSeconds = pmJob.getRopPeriod();
            final ProcessType processType = pmJob.getProcessType();
            final Long subscriptionId = pmJob.getSubscriptionId();
            updateFileCollectionBehaviour(nodeFdn, ropPeriodInSeconds, processType, subscriptionId, fileCollectionState);
        }
    }

    private void updateFileCollectionBehaviour(final String nodeFdn, final int ropPeriodInSeconds, final ProcessType processType,
                                               final Long subscriptionId, final FileCollectionState fileCollectionState)
        throws DataAccessException, InvalidSubscriptionException, RetryServiceException {
        logger.debug("Update File Collection to {} for subscriptionId: {} processType: {}", fileCollectionState, subscriptionId, processType);
        if (Subscription.isDefaultFileCollectionSupported(processType)) {
            final Subscription subscription = subscriptionManager.getSubscriptionWrapperById(subscriptionId).getSubscription();
            if (fileCollectionState == FileCollectionState.ENABLED
                && subscriptionReadOperationService.doesSubscriptionSupportFileCollection(subscription)) {
                logger.debug("FDN:{} processType:{} subscription ID:{} and fileCollectionState:{} so starting file collection for ROP period : {}",
                        nodeFdn, processType, subscriptionId, fileCollectionState, ropPeriodInSeconds);
                fileCollectionOperationHelper.startFileCollection(ropPeriodInSeconds, nodeFdn, processType.name());
            } else if (fileCollectionState == FileCollectionState.DISABLED) {
                logger.debug("FDN:{} processType:{} subscription ID:{} and fileCollectionState:{} so stopping file collection for ROP period : {}",
                        nodeFdn, processType, subscriptionId, fileCollectionState, ropPeriodInSeconds);
                fileCollectionOperationHelper.stopFileCollection(ropPeriodInSeconds, nodeFdn, processType.name());
            }
        } else {
            logger.debug("FDN:{} belongs to process type:{} which is not using PMIC Common file collection mechanism!", nodeFdn, processType);
        }
    }
}