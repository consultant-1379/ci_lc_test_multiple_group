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

import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerType;
import com.ericsson.oss.services.model.ned.pm.function.FileCollectionState;
import com.ericsson.oss.services.pm.collection.notification.handlers.initiationresponsecache.handlers.InitiationResponseCacheHelper;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.scheduling.impl.DelayedTaskStatusValidator;
import com.ericsson.oss.services.topologySearchService.exception.InvalidFdnException;

/**
 * This class can handle only behavior related to scanner created event
 */
@Stateless
public class ScannerCreateOperationHandler {

    @Inject
    private Logger logger;
    @Inject

    private MembershipListener membershipListener;
    @Inject
    private FileCollectionOperationHelper fileCollectionOperationHelper;
    @Inject
    private InitiationResponseCacheHelper initiationResponseCacheHelper;
    @Inject
    private DelayedTaskStatusValidator delayedTaskStatusValidator;
    @Inject
    private NodeService nodeService;
    @Inject
    private FileCollectionStateUpdateHandler fileCollectionStateUpdateHandler;

    /**
     * On Scanner create notification this method will update initiation cache and starts file collection
     *
     * @param scannerOperationVO
     *         Scanner Operation VO
     */
    public void execute(final ScannerOperationVO scannerOperationVO) {
        if (membershipListener.isMaster()) {
            try {
                if (!scannerOperationVO.getScannerFdn().contains(ScannerType.HIGH_PRIORITY_CELLTRACE_10005.getExactName())) {
                    final String nodeFdn = Scanner.getNodeFdnFromScannerFdn(scannerOperationVO.getScannerFdn());
                    final String subscriptionId = scannerOperationVO.getSubscriptionId();
                    initiationResponseCacheHelper.processInitiationResponseCacheForActivation(subscriptionId, nodeFdn);
                    startFileCollectionForScanner(scannerOperationVO, nodeFdn);
                    if (Scanner.isValidSubscriptionId(subscriptionId)) {
                        delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(Long.parseLong(subscriptionId));
                    }
                }
            } catch (final InvalidFdnException invalidFdnException) {
                logger
                        .error("Scanner without parent, Not starting File Collection for Scanner : {} ", scannerOperationVO.getScannerFdn(), invalidFdnException);
            } catch (RuntimeDataAccessException e) {
                logger.error("There was an error extracting subscription with id {} from database when trying to calculate "
                        + "subscription's task status. Scanner fdn: {}", scannerOperationVO.getSubscriptionId(), scannerOperationVO.getScannerFdn());
            }
        }
    }

    private void startFileCollectionForScanner(final ScannerOperationVO scannerOperationVO, final String nodeFdn) {
        if (scannerOperationVO.isScannerActive() && scannerOperationVO.isFileCollectionEnabled()) {
            // File collection will be triggered only if the scanner status is ACTIVE.
            logger.debug(
                    "Scanner status is : {} for scannerFDN : {} associated to subscription ID : {}, so starting file collection for ROP period : {}",
                    scannerOperationVO.getScannerStatus(), scannerOperationVO.getScannerFdn(), scannerOperationVO.getSubscriptionId(), scannerOperationVO.getRopTimeInSeconds());
            final String nodeAddress = Scanner.getNodeFdnFromScannerFdn(scannerOperationVO.getScannerFdn());
            fileCollectionOperationHelper.startFileCollection(scannerOperationVO.getRopTimeInSeconds(), nodeAddress, scannerOperationVO.getProcessType());

            if (shouldCreateFileCollectionScheduler(scannerOperationVO, nodeFdn)) {
                fileCollectionStateUpdateHandler.updateFileCollectionScheduleForNodeWithMediationAutonomy(nodeFdn, FileCollectionState.ENABLED);
            }
        }
    }

    private boolean shouldCreateFileCollectionScheduler(final ScannerOperationVO scannerOperationVO, final String nodeFdn) {
        return isPredefinedStatsScanner(scannerOperationVO) && nodeService.isMediationAutonomyEnabled(nodeFdn) && nodeService.isPmFunctionEnabled(nodeFdn);
    }

    private boolean isPredefinedStatsScanner(final ScannerOperationVO scannerOperationVO) {
        final String scannerName = Scanner.getScannerNameFromScannerFdn(scannerOperationVO.getScannerFdn());
        return ScannerType.PREDEF_STATS == ScannerType.getScannerTypeFromScannerName(scannerName);
    }
}
