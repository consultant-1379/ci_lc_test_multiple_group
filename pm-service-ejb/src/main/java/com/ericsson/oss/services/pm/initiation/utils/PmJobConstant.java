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

package com.ericsson.oss.services.pm.initiation.utils;

/**
 * Constants related to PmJobs
 */
public final class PmJobConstant {

    public static final String PMIC_JOB_TYPE_UETRACE = "UETRACE";

    public static final String PMJOB_ID = "id";

    public static final String PMJOB_NAME_ATTRIBUTE = "name";

    public static final String PMJOB_ERROR_CODE_ATTRIBUTE = "errorCode";

    public static final String PMJOB_PROCESS_TYPE_ATTRIBUTE = "processType";

    public static final String PMJOB_SUBSCRIPTION_PO_ID_ATTRIBUTE = "subscriptionId";

    public static final String PMJOB_ROP_PERIOD_ATTRIBUTE = "ropPeriod";

    public static final String PMJOB_NODE_NAME_ATTRIBUTE = "nodeName";

    public static final String PMJOB_STATUS_ATTRIBUTE = "status";

    public static final String PMJOB_NAME_PREFIX_USERDEF = "USERDEF-";

    public static final String PMJOB_MODEL_NAME = "PMICJobInfo";

    public static final String PMIC_JOB_INFO = "," + PMJOB_MODEL_NAME;

    public static final String PMJOB_MODEL_NAME_SPACE = "pmic_subscription";

    public static final String PMJOB_MODEL_VERSION = "1.0.0";

    public static final String PMJOB_STATUS_ACTIVE = "ACTIVE";

    public static final String PMJOB_STATUS_ERROR = "ERROR";

    public static final String PMJOB_STATUS_UNKNOWN = "UNKNOWN";

    private PmJobConstant() {
        //utility class should not be instantiated.
    }


}
