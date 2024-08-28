/*------------------------------------------------------------------------------
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.enodeb.pmprocess.resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerType;
import com.ericsson.oss.services.pm.initiation.errors.NodeScannerErrorExceptionEnum;
import com.ericsson.oss.services.pm.initiation.rest.response.NodeScannerError;

/**
 * The Node Report Table Info class used to send flowing information to the node process table
 * - CorbaErrors
 * - Process Types for FilterHeaderCell
 * - Process Statuses for FilterHeaderCell
 */
public class NodeReportTableHeaderInfo {

    private List<NodeScannerError> nodeScannerErrors;

    private Set<String> processTypeList;

    private Set<String> processStatusList;

    /**
     *
     */
    public NodeReportTableHeaderInfo() {
        nodeScannerErrors = getCorbaError();
        processTypeList = getProcessTypes();
        processStatusList = getProcessStatuses();
    }

    /**
     * @return the nodeScannerErrors
     */
    public List<NodeScannerError> getNodeScannerErrors() {
        return nodeScannerErrors;
    }

    /**
     * @param nodeScannerErrors
     *         the nodeScannerErrors to set
     */
    public void setNodeScannerErrors(final List<NodeScannerError> nodeScannerErrors) {
        this.nodeScannerErrors = nodeScannerErrors;
    }

    /**
     * @return the processTypeList
     */
    public Set<String> getProcessTypeList() {
        return processTypeList;
    }

    /**
     * @param processTypeList
     *         the processTypeList to set
     */
    public void setProcessTypeList(final Set<String> processTypeList) {
        this.processTypeList = processTypeList;
    }

    /**
     * @return the processStatusList
     */
    public Set<String> getProcessStatusList() {
        return processStatusList;
    }

    /**
     * @param processStatusList
     *         the processStatusList to set
     */
    public void setProcessStatusList(final Set<String> processStatusList) {
        this.processStatusList = processStatusList;
    }

    private List<NodeScannerError> getCorbaError() {
        nodeScannerErrors = new ArrayList<>();
        final NodeScannerErrorExceptionEnum[] errors = NodeScannerErrorExceptionEnum.values();
        for (final NodeScannerErrorExceptionEnum error : errors) {
            nodeScannerErrors.add(new NodeScannerError(error.name(), error.getCode(), error.getDescription()));
        }
        return nodeScannerErrors;
    }

    private Set<String> getProcessTypes() {
        final Set<String> processTypes = new HashSet<>();
        final ScannerType[] processTypeMappers = ScannerType.values();
        for (final ScannerType processTypeMapper : processTypeMappers) {
            if (processTypeMapper.getNamePostfix() != null && !processTypeMapper.getNamePostfix().contains(".")) {
                processTypes.add(processTypeMapper.getNamePostfix());
            }
        }
        return processTypes;
    }

    private Set<String> getProcessStatuses() {
        final Set<String> processStatuses = new HashSet<>();

        final ScannerStatus[] scannerStatuses = ScannerStatus.values();
        for (final ScannerStatus scannerStatus : scannerStatuses) {
            processStatuses.add(scannerStatus.name());
        }
        return processStatuses;
    }

}
