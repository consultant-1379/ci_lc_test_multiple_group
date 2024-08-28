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

package com.ericsson.oss.services.pm.initiation.errors;

/**
 * The Corba/Netconf error enum.
 */
public enum NodeScannerErrorExceptionEnum {

    OPTIONAL_OPERATION_NOT_SUPPORTED_EXCEPTION(
            (short) 1, "The OPTIONAL_OPERATION_NOT_SUPPORTED_EXCEPTION is raised if an operation is not supported."),
    PROCESSING_ERROR_EXCEPTION((short) 2,
            "The PROCESSING_ERROR_EXCEPTION is raised if the operation cannot be performed due to some unknown reason (run-time errors etc.)."),
    NUMBER_OF_COUNTERS_EXCEEDED_EXCEPTION((short) 3,
            "The NUMBER_OF_COUNTERS_EXCEEDED_EXCEPTION is raised if the limit for the number of allowed active counters would be exceeded."),
    NUMBER_OF_MONITORS_EXCEEDED_EXCEPTION((short) 4,
            "The NUMBER_OF_MONITORS_EXCEEDED_EXCEPTION is raised if the limit for the number of monitors would be exceeded."),
    INVALID_PARAMETER_EXCEPTION((short) 5,
            "The INVALID_PARAMETER_EXCEPTION may be raised by a node application if the combination of parameters"
                    + " (granularity period not included) is not accepted for some reason."),
    NO_SUCH_ID_EXCEPTION((short) 6,
            "The NO_SUCH_ID_EXCEPTION is raised if none of the specified ids can be mapped to a performance monitoring in the node."),
    OVERLOAD_EXCEPTION((short) 7, "The OVERLOAD_EXCEPTION is raised if there is an overload problem on the node."),
    GRANULARITY_AND_ATTRIBUTE_ACTIVATION_EXCEPTION((short) 8, "The GRANULARITY_AND_ATTRIBUTE_ACTIVATION_EXCEPTION is raised "
            + "if one of the specified counter attributes is already active for a granularity period other than the one specified."),
    INVALID_OBSERVATION_CLASS_EXCEPTION((short) 9, "The INVALID_OBSERVATION_CLASS_EXCEPTION is raised if the model information is violated."),
    OPTIONAL_PARAMETER_NOT_SUPPORTED_EXCEPTION((short) 10, "The OPTIONAL_PARAMETER_NOT_SUPPORTED_EXCEPTION is raised a parameter is not supported."),
    INVALID_OBSERVATION_OBJECT_LIST_EXCEPTION(
            (short) 11, "The INVALID_OBSERVATION_OBJECT_LIST_EXCEPTION is raised if the model information is violated."),
    INVALID_SCHEDULE_EXCEPTION((short) 12, "The INVALID_SCHEDULE_EXCEPTION is raised if a schedule parameter is not accepted."),
    INVALID_REPORTING_PERIOD_EXCEPTION((short) 13, "The INVALID_REPORTING_PERIOD_EXCEPTION is raised if a reporting period is not accepted."),
    DESTINATION_NOT_SUPPORTED_EXCEPTION(
            (short) 14, "The DESTINATION_NOT_SUPPORTED_EXCEPTION is raised if the destination parameter is not supported."),
    GRANULARITY_NOT_SUPPORTED_EXCEPTION((short) 15, "Although PM supports the specified granularity period, one of the"
            + " specified counters may not. In this case the GRANULARITY_NOT_SUPPORTED_EXCEPTION is raised."),
    INVALID_GRANULARITY_PERIOD_EXCEPTION((short) 16,
            "The INVALID_GRANULARITY_PERIOD_EXCEPTION is raised if the specified granularity period is not supported by PM."),
    NON_CORBA_EXCEPTION((short) 17, "The NON_CORBA_EXCEPTION is raised if a non corba exception is received."),
    PERFORMANCE_MONITORING_EXCEPTION((short) 18,
            "The PERFORMANCE_MONITORING_EXCEPTION is raised if a generic performance monitoring exception is received."),
    CONNECTION_ERROR((short) 19, "The CONNECTION_ERROR is raised if Protocol Manager is not connected."),
    OPERATIONS_ERROR((short) 20, "The OPERATIONS_ERROR is raised if Protocol Manager operations failed."),
    SCANNER_NOT_AVAILABLE_ERROR((short) 21, "The SCANNER_NOT_AVAILABLE_ERROR is raised if a celltrace subscription"
            + " tries to activate a scanner on the node where no normal-priority scanners are available."),
    CELL_NOT_SELECTED((short) 22, "The CELL_NOT_SELECTED is raised if subscription has nodes without cells"),
    NO_VALID_COUNTERS_EXCEPTION((short) 30, "The NO_VALID_COUNTERS_EXCEPTION is raised if subscription has nodes with no valid counters selected"),
    NO_VALID_EVENTS_EXCEPTION((short) 31, "The NO_VALID_EVENTS_EXCEPTION is raised if subscription has nodes with no valid events selected"),
    BSC_RECORDING_ALREADY_INITIATED((short) 35, "BSC recording has already been initiated for this mobile subscriber"),
    RECORDING_REFERENCE_USED((short) 36, "The specified recording reference has already been used to initiate BSC recording "
            + "for another mobile subscriber or greylisted equipment tracing"),
    BSC_RECORDING_NOT_INITIATED((short) 37, "No BSC recording has been initiated for the mobile subscriber or recording"),
    MTR_INITIATION_FAILURE((short) 38, "MTR Initiation Failure"),
    NO_IDLE_RECORDING_REFERENCE_AVAILABLE((short) 49, "The maximum number of BSC recording references has already been initiated");

    private short code;

    private String description;

    /**
     * @param code
     *         - the error code
     * @param description
     *         - the error description
     */
    NodeScannerErrorExceptionEnum(final short code, final String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * @return the code
     */
    public short getCode() {
        return code;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }
}
