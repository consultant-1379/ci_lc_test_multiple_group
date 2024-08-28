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

package com.ericsson.oss.services.pm.collection.notification.handlers;

import java.util.Collections;
import java.util.List;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.services.pm.collection.notification.handlers.initiationresponsecache.handlers.InitiationResponseCacheHelper;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.scheduling.impl.DelayedTaskStatusValidator;

/**
 * This class can handle only behavior related to scanner deleted event
 *
 * @author eushmar
 */
@Stateless
public class ScannerDeleteOperationHandler {

    @Inject
    private Logger logger;
    @Inject
    private ScannerService scannerService;
    @Inject
    private MembershipListener membershipListener;
    @Inject
    private DelayedTaskStatusValidator delayedTaskStatusValidator;
    @Inject
    private FileCollectionOperationHelper fileCollectionOperationHelper;
    @Inject
    private InitiationResponseCacheHelper initiationResponseCacheHelper ;

    /**
     * Performs any actions that have to be performed when a scanner is deleted
     *
     * @param scannerVO
     *         the scanner vo
     */
    public void execute(final ScannerOperationVO scannerVO) {
        if (membershipListener.isMaster()) {
            stopFileCollectionForScanner(scannerVO);
            if (Scanner.isValidSubscriptionId(scannerVO.getSubscriptionId())) {
                final String nodeFdn = Scanner.getNodeFdnFromScannerFdn(scannerVO.getScannerFdn());
                initiationResponseCacheHelper.processInitiationResponseCacheForDeactivation(scannerVO.getSubscriptionId(), nodeFdn);
                logger.debug("Performing task status validation for subscription {} after timeout", scannerVO.getSubscriptionId());
                delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(Long.valueOf(scannerVO.getSubscriptionId()), nodeFdn);
            } else {
                logger.debug("Subscription ID is not valid for scanner {}", scannerVO.getScannerFdn());
            }
        }
    }

    /**
     * The file collection will be stopped only in case it is really started
     *
     * @param scannerVO
     *         - Scanner Operation
     */
    protected void stopFileCollectionForScanner(final ScannerOperationVO scannerVO) {
        try {
            final String nodeFdn = Scanner.getNodeFdnFromScannerFdn(scannerVO.getScannerFdn());
            final ProcessType processType = ProcessType.fromString(scannerVO.getProcessType());
            if (!hasAtLeastOneActiveScannerWithFileCollectionEnabled(nodeFdn, scannerVO.getRopTimeInSeconds(), processType)) {
                logger.info("No active scanners found for node {} with type {} and ROP {}. Removing from file collection node list", nodeFdn,
                        processType, scannerVO.getRopTimeInSeconds());
                fileCollectionOperationHelper.stopFileCollection(scannerVO.getRopTimeInSeconds(), nodeFdn, scannerVO.getProcessType());
            }
            logger.debug("Deleted Scanner {} for subscription ID {} got status of {}", scannerVO.getScannerFdn(), scannerVO.getSubscriptionId(),
                    scannerVO.getScannerStatus());
        } catch (final DataAccessException exception) {
            logger.error("Could not stop file collection for scanner {}. Exception message: {}", scannerVO.getScannerFdn(), exception.getMessage());
            logger.info("Could not stop file collection for scanner {}.", scannerVO.getScannerFdn(), exception);
        }
    }

    private boolean hasAtLeastOneActiveScannerWithFileCollectionEnabled(final String nodeFdn, final int ropPeriodInSeconds,
                                                                        final ProcessType processType)
            throws DataAccessException {
        final ProcessType[] processTypes = {processType};
        final ScannerStatus[] scannerStatuses = {ScannerStatus.ACTIVE};
        final List<Scanner> scanners = scannerService.findAllByNodeFdnAndProcessTypeAndRopDurationAndScannerStatusAndFileCollection(
                Collections.singleton(nodeFdn), processTypes, RopPeriod.fromSeconds(ropPeriodInSeconds), scannerStatuses, true);
        return scanners != null && !scanners.isEmpty();
    }

}
