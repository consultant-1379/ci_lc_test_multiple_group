/*
 * COPYRIGHT Ericsson 2019
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.scanner.operation;

import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.services.pm.eventSender.PmEventSender;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.scanner.lifecycle.task.factories.deletion.ScannerDeletionTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.scanner.lifecycle.task.factories.resumption.ScannerResumptionTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.scanner.lifecycle.task.factories.suspension.ScannerSuspensionTaskRequestFactory;

/**
 * Implementation of Scanner mediation task.
 */
@Stateless
public class ScannerOperation {

    @Inject
    private Logger logger;
    @Inject
    private PmEventSender sender;
    @Inject
    private ScannerService scannerService;
    @Inject
    private ScannerDeletionTaskRequestFactory scannerDeletionTaskRequestFactory;
    @Inject
    private ScannerSuspensionTaskRequestFactory scannerSuspensionTaskRequestFactory;
    @Inject
    private ScannerResumptionTaskRequestFactory scannerResumptionTaskRequestFactory;

    public void deleteScannerFromTheNode(final String scannerFdn, final String scannerId) {
        deleteScannerFromTheNode(scannerFdn, scannerId, false);
    }

    private void deleteScannerFromTheNode(final String scannerFdn, final String scannerId, final boolean ignorePmFunctionValue) {
        logger.info("Deleting Scanner {} on node", scannerFdn);
        final String nodeFdn = Scanner.getNodeFdnFromScannerFdn(scannerFdn);
        if (nodeFdn == null) {
            logger.error("Cannot delete scanner on the node with fdn {} because extracted node fdn is null", scannerFdn);
            return;
        }
        final MediationTaskRequest task = scannerDeletionTaskRequestFactory.createScannerDeletionTask(nodeFdn, scannerId);
        if (task == null) {
            logger.warn("Cannot delete scanner on the node with fdn {} because created task is null/not created."
                    + " Check if NeConfigurationManagerState is ENABLED", scannerFdn);
            return;
        }
        sender.sendPmEvent(task, ignorePmFunctionValue);
    }

    public void deleteScannerFromTheNodeRegardlessOfPmFunction(final String scannerFdn, final String scannerId) {
        deleteScannerFromTheNode(scannerFdn, scannerId, true);
    }

    public void resumeScannerOnTheNode(final String nodeFdn, final Long scannerPoId) {
        logger.info("Resuming Scanner {} on Node {} ", scannerPoId, nodeFdn);
        final MediationTaskRequest task = scannerResumptionTaskRequestFactory.createScannerResumeTask(nodeFdn, String.valueOf(scannerPoId));
        if (task == null) {
            logger.warn("Cannot resume scannerId {} because created task is null/not created. Check if NeConfigurationManagerState is ENABLED",
                    scannerPoId);
            return;
        }
        sender.sendPmEvent(task);
    }

    public void suspendScannerOnTheNode(final String scannerFdn) {
        logger.info("Suspending Scanner {} on Node", scannerFdn);
        try {
            final Scanner scanner = scannerService.findOneByFdn(scannerFdn);
            if (scanner == null) {
                logger.error("Cannot suspend scanner on the node with fdn {} because there is no such scanner in DPS", scannerFdn);
                return;
            }
            suspendScannerOnTheNode(scannerFdn, scanner.getPoId());
        } catch (final DataAccessException | RuntimeDataAccessException e) {
            logger.error("Cannot suspend scanner on the node with fdn {} because an exception was thrown from DPS while finding this scanner. "
                    + "Exception message: {}", scannerFdn, e.getMessage());
            logger.info("Cannot suspend scanner on the node with fdn {}.", scannerFdn, e);
        }
    }

    public void suspendScannerOnTheNode(final String scannerFdn, final Long dpsScannerPoId) {
        suspendScannerOnTheNode(scannerFdn, dpsScannerPoId, false);
    }

    public void suspendScannerOnTheNodeRegardlessOfPmFunction(final String scannerFdn, final Long scannerPoId) {
        suspendScannerOnTheNode(scannerFdn, scannerPoId, true);
    }

    private void suspendScannerOnTheNode(final String scannerFdn, final Long dpsScannerPoId, final boolean ignorePmFunctionValue) {
        logger.info("Suspending Scanner {} on Node", scannerFdn);
        final String nodeFdn = Scanner.getNodeFdnFromScannerFdn(scannerFdn);
        if (nodeFdn == null) {
            logger.error("Cannot suspend scanner with fdn {} because extracted node fdn is null", scannerFdn);
            return;
        }
        final MediationTaskRequest task = scannerSuspensionTaskRequestFactory.createScannerSuspensionTask(nodeFdn, String.valueOf(dpsScannerPoId));
        if (task == null) {
            logger.warn("Cannot suspend scanner {} because created task is null/not created. Check if NeConfigurationManagerState is ENABLED",
                    scannerFdn);
            return;
        }
        sender.sendPmEvent(task, ignorePmFunctionValue);
    }
}
