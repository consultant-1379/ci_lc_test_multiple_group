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
package com.ericsson.oss.services.pm.initiation.common.accesscontrol;

/**
 * interface for access control resources.
 */
public final class AccessControlResources {

    public static final String SUBSCRIPTION = "subscription";
    public static final String STATISTICAL = "statistical";
    public static final String CELLTRACE_EBS_L = "celltrace_ebs-l";
    public static final String CTR = "ctr";
    public static final String EBM_EBS_M = "ebm_ebs-m";
    public static final String UETRACE = "uetrace";
    public static final String UETR = "uetr";
    public static final String CTUM = "ctum";
    public static final String GPEH = "gpeh";
    public static final String RES = "res";
    public static final String BSCRECORDINGS = "bscrecordings";
    public static final String MTR = "mtr";
    public static final String BSCPERFORMANCEEVENTS = "bscperformanceevents";
    public static final String RTT = "rtt";

    public static final String PMIC_CONFIG_PARAM = "pmcapability";

    private AccessControlResources() {
        //empty constructor.
    }
}
