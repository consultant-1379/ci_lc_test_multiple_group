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

package com.ericsson.oss.services.pm.initiation.scanner.polling;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.SENT_SCANNER_POLLING_TASK_LIST;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.eventSender.PmEventSender;
import com.ericsson.oss.services.pm.initiation.tasks.ScannerPollingTaskRequest;

/**
 * Class for sending scanner polling tasks.
 */
public class ScannerPollingTaskSender {

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    @Inject
    private PmEventSender eventSender;

    @Inject
    private ScannerPollingTaskFactory scannerPollingTaskFactory;

    @Inject
    private Logger logger;

    /**
     * Creates and sends tasks for list of nodes
     *
     * @param nodeFdns
     *         - Set of node Fully Distinguished Names to create Scanner Polling Tasks for
     */
    public void sendScannerPollingTaskForNodes(final Set<String> nodeFdns) {
        final List<MediationTaskRequest> tasks = new ArrayList<>();
        for (final String nodeFdn : nodeFdns) {
            final ScannerPollingTaskRequest scannerPollingTaskRequest = scannerPollingTaskFactory.createScannerPollingTask(nodeFdn);
            logger.debug("Scanner polling task [{}] created.", scannerPollingTaskRequest);
            tasks.add(scannerPollingTaskRequest);
        }
        sendScannerPollingTasks(tasks);
    }

    /**
     * Create and send task for single node
     *
     * @param nodeFdn
     *         - The Fully Distinguished Name of the node to create Scanner Polling Task for
     */
    public void sendScannerPollingTaskForNode(final String nodeFdn) {
        final ScannerPollingTaskRequest scannerPollingTaskRequest = scannerPollingTaskFactory.createScannerPollingTask(nodeFdn);
        sendScannerPollingTask(scannerPollingTaskRequest);
    }

    /**
     * Sends single scanner polling task.
     *
     * @param task
     *         - Scanner Polling Task to me sent
     */
    private void sendScannerPollingTask(final ScannerPollingTaskRequest task) {
        if (eventSender.sendPmEvent(task)) {
            logger.info("ScannerPollingTask sent for node fdn: {}", task.getNodeAddress());
        }
    }

    /**
     * Sends list of scanner polling task
     *
     * @param tasks
     *         - list of Scanner Polling Tasks to be sent
     */
    private void sendScannerPollingTasks(final List<MediationTaskRequest> tasks) {
        eventSender.sendTasksForNodesWithPmFunctionOn(tasks);
        systemRecorder.eventCoarse(SENT_SCANNER_POLLING_TASK_LIST, "EventBasedMediationClient", "ScannerPollingTask list sent for %d nodes",
                tasks.size());
    }

}
