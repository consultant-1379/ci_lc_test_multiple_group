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

package com.ericsson.oss.services.pm.scheduling.constants;

/**
 * Cell trace file collection constants.
 */
public final class CellTraceFileCollectionConstant {

    public static final String CELL_TRACE_DUL1 = "_CellTrace_DUL1";

    public static final String BIN_GZ = ".bin.gz";

    public static final String CELL_TRACE_LOCATION_FILE_NODE_POSTFIX = "CellTraceFilesLocation";

    public static final String CELL_TRACE_LOCATION_FILE_ENM_POSTFIX = "_CellTraceFilesLocation";

    public static final String INTERNAL_LTE_RC_VALUE = "_1";

    public static final String EXTERNAL_LTE_RC_VALUE = "_3";

    public static final String CELL_TRACE_HIGH_PRIORITY_NODE_POSTFIX = CELL_TRACE_DUL1 + EXTERNAL_LTE_RC_VALUE + BIN_GZ;

    public static final String CELL_TRACE_HIGH_PRIORITY_ENM_POSTFIX = "_celltracefile" + EXTERNAL_LTE_RC_VALUE + BIN_GZ;

    public static final String CELL_TRACE_NORMAL_NODE_POSTFIX = CELL_TRACE_DUL1 + INTERNAL_LTE_RC_VALUE + BIN_GZ;

    public static final String CELL_TRACE_NORMAL_ENM_POSTFIX = "_celltracefile" + INTERNAL_LTE_RC_VALUE + BIN_GZ;

    public static final String CELL_TRACE_NODE_POSTFIX = CELL_TRACE_DUL1;

    public static final String CELL_TRACE_ENM_POSTFIX = CELL_TRACE_DUL1;

    private CellTraceFileCollectionConstant() {
        //utility class should not be instantiated.
    }
}
