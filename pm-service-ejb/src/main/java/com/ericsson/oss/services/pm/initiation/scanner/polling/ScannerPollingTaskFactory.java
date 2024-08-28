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

import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.initiation.tasks.ScannerPollingTaskRequest;

/**
 * Creates ScannerPollingTask object
 *
 * @author ekamkal
 */
public class ScannerPollingTaskFactory {

    @Inject
    private Logger logger;

    /**
     * Create scanner polling task scanner polling task request.
     *
     * @param nodeFdn
     *         the node fully distinguished name
     *
     * @return the scanner polling task request
     */
    public ScannerPollingTaskRequest createScannerPollingTask(final String nodeFdn) {
        logger.debug("ScannerPollingTask being created for nodeAddress {}", nodeFdn);
        return new ScannerPollingTaskRequest(nodeFdn, "scanner_polling_" + nodeFdn);
    }

}
