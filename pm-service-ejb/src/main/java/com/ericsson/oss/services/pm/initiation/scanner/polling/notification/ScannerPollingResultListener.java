/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2014
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.scanner.polling.notification;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.SCANNER_POLLING_FAILURE;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Operation.SCANNER_POLLING;

import java.util.ArrayList;
import java.util.List;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled;
import com.ericsson.oss.itpf.sdk.instrument.annotation.Profiled;
import com.ericsson.oss.pmic.api.scanner.NodeScannerInfo;
import com.ericsson.oss.pmic.api.scanner.master.ScannerPollingResultProcessor;
import com.ericsson.oss.pmic.api.scanner.master.duplicated.exceptions.InvalidSubscriptionException;
import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.events.ScannerPollingResult;
import com.ericsson.oss.services.pm.instrumentation.ScannerPollingInstrumentation;

/**
 * The Scanner polling result listener.
 */
public class ScannerPollingResultListener {

    @Inject
    private Logger logger;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;
    @Inject
    private ScannerPollingInstrumentation scannerPollingInstrumentation;
    @Inject
    private ScannerPollingResultProcessor resultProcessor;

    /**
     * Observe scanner polling result.
     *
     * @param result
     *         the scanner polling result
     */
    public void observeScannerPollingResult(@Observes @Modeled final ScannerPollingResult result) {
        logger.debug("Scanner Polling Result received for node {} .", result.getFdn());
        if (!dpsAvailabilityStatus.isAvailable()) {
            logger.warn("Failed processing scanner polling result for {}, Dps not available", result.getFdn());
            return;
        }
        try {
            processScannerPollingResult(result);
        } catch (final Exception e) {
            logger.error("Error while processing scanner polling result for node [{}]. Exception : {}", result.getFdn(), e.getMessage());
            logger.info("Error while processing scanner polling result for node [{}] ", result.getFdn(), e);
        }
    }

    /**
     * Method for processing ScannerPollingResult passed on from ScannerPollingResult Listener
     *
     * @param pollingResult
     *         - the scanner polling result to be processed
     *
     * @throws DataAccessException
     *         - thrown if the an exception from database is thrown
     * @throws InvalidSubscriptionException
     *         - if an exception is thrown while tryint to resolve the subscription.
     */
    @Profiled
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void processScannerPollingResult(final ScannerPollingResult pollingResult)
            throws DataAccessException, InvalidSubscriptionException {
        if (pollingResult == null) {
            return;
        }
        scannerPollingInstrumentation.scannerPollingTaskEnded(pollingResult.getFdn());
        if (pollingResult.getFailed()) {
            logFailedScannerPollingJob(pollingResult);
        } else {
            processSuccessfulScannerPollingJob(pollingResult);
        }
    }

    private void logFailedScannerPollingJob(final ScannerPollingResult pollingResult) {
        scannerPollingInstrumentation.scannerPollingTaskFailed(pollingResult.getFdn());
        systemRecorder.error(SCANNER_POLLING_FAILURE, pollingResult.getFdn(), "Scanner Master", SCANNER_POLLING);
    }

    private void processSuccessfulScannerPollingJob(final ScannerPollingResult pollingResult)
            throws DataAccessException, InvalidSubscriptionException {
        final List<NodeScannerInfo> scannerList = new ArrayList<>();

        for (final com.ericsson.oss.services.pm.initiation.events.NodeScannerInfo nodeScannerInfo : pollingResult.getScannerList()) {
            final NodeScannerInfo scannerInfo =  new NodeScannerInfo(nodeScannerInfo.getScannerId(),
                    nodeScannerInfo.getScannerName(), nodeScannerInfo.getScannerStatus(),
                    nodeScannerInfo.getProcessType());
            scannerInfo.setScannerRequestedStatus(nodeScannerInfo.getScannerRequestedStatus());
            scannerList.add(scannerInfo);
        }
        resultProcessor.processScanners(scannerList, pollingResult.getFdn());
    }
}
