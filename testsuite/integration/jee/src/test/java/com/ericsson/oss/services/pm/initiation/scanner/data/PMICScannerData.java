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

package com.ericsson.oss.services.pm.initiation.scanner.data;

import java.util.List;
import java.util.Map;

public class PMICScannerData {
    private String scannerName;
    private List<String> nodes;
    private Map<String, Object> scannerAttributes;

    public PMICScannerData(final String scannerName, final List<String> nodes, final Map<String, Object> scannerAttributes) {
        this.scannerName = scannerName;
        this.nodes = nodes;
        this.scannerAttributes = scannerAttributes;
    }

    /**
     * @return the scannerName
     */
    public String getScannerName() {
        return scannerName;
    }

    /**
     * @param scannerName
     *         the scannerName to set
     */
    public void setScannerName(final String scannerName) {
        this.scannerName = scannerName;
    }

    /**
     * @return the nodes
     */
    public List<String> getNodes() {
        return nodes;
    }

    /**
     * @param nodes
     *         the nodes to set
     */
    public void setNodes(final List<String> nodes) {
        this.nodes = nodes;
    }

    /**
     * @return the scannerAttributes
     */
    public Map<String, Object> getScannerAttributes() {
        return scannerAttributes;
    }

    /**
     * @param scannerAttributes
     *         the scannerAttributes to set
     */
    public void setScannerAttributes(final Map<String, Object> scannerAttributes) {
        this.scannerAttributes = scannerAttributes;
    }
}
