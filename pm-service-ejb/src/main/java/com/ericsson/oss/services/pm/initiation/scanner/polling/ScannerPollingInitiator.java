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

package com.ericsson.oss.services.pm.initiation.scanner.polling;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.SCANNER_POLLING_STARTED;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import com.ericsson.oss.pmic.impl.handler.InvokeInTransaction;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import org.slf4j.Logger;

import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.instrumentation.ScannerPollingInstrumentation;

/**
 * Entry point for Scanner polling. This class starts scanner polling on every 15 minutes interval
 */
@Stateless
public class ScannerPollingInitiator {

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    @Inject
    private ScannerPollingTaskSender scannerPollingTaskSender;

    @Inject
    private ScannerPollingInstrumentation scannerPollingInstrumentation;

    @Inject
    private NodeService nodeService;

    @Inject
    private Logger logger;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;

    /**
     * Starts scanner polling and send scanner polling tasks
     */
    @LogProfiler(name = "Sending scanner polling requests for all nodes")
    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void startScannerPolling() {
        if (!dpsAvailabilityStatus.isAvailable()) {
            logger.warn("Failed to send scanner polling requests, Dps not available");
            return;
        }
        systemRecorder.eventCoarse(SCANNER_POLLING_STARTED, "All Nodes",
                "Scanner Polling has started, will send a scanner polling task for all nodes");
        try {
            final List<Node> nodes = nodeService.findAll();
            sendScannerPollingTasks(nodes);
        } catch (final DataAccessException | RuntimeDataAccessException e) {
            logger.error("Unable to find all Network elements due to some underlying issue with th DPS. Exception Message: {}", e.getMessage());
            logger.info("Unable to find all Network elements due to some underlying issue with th DPS", e);
        }
    }

    private void sendScannerPollingTasks(final List<Node> nodes) {
        final Set<String> nodesFoundForScannerPolling = new HashSet<>();
        for (final Node node : nodes) {
            if (nodeService.isPmFunctionEnabled(node.getFdn()) && nodeService.isScannerMasterEnabled(node)
                    && !nodeService.isMediationAutonomyEnabled(node)) {
                nodesFoundForScannerPolling.add(node.getFdn());
                logger.debug("Scanner polling task created for node [{}] ", node.getFdn());
            }
        }
        scannerPollingInstrumentation.scannerPollingTaskStarted(nodesFoundForScannerPolling);
        if (!nodesFoundForScannerPolling.isEmpty()) {
            scannerPollingTaskSender.sendScannerPollingTaskForNodes(nodesFoundForScannerPolling);
        }
    }
}
