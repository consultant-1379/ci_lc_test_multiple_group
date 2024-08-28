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

package com.ericsson.oss.services.pm.initiation.model.utils;

/**
 * Pm meta data constants.
 */
public final class PmMetaDataConstants {
    public static final String PFM_MEASUREMENT_PATTERN = "/pfm_measurement/";
    public static final String PFM_EVENT_PATTERN = "/pfm_event/";
    public static final String CFM_MIMINFO_PATTERN = "/cfm_miminfo/";
    public static final String NE_DEFINED_PATTERN = "/NE-defined/";
    public static final String NE_DEFINED_PREFIX = "/NE-defined";
    public static final String OSS_DEFINED_PATTERN = "/NE-defined-EBS/";
    public static final String CELL_TRAFFIC_PROFILE_PATTERN = "/CTR-profile/";
    public static final String TRIGGER_EVENTS = "triggerEvents";
    public static final String NON_TRIGGER_EVENTS = "nonTriggerEvents";
    public static final String GPEH_PROFILE_PATTERN = "/GPEH-profile/";
    public static final String UETR_PROFILE_PATTERN = "/UETR-profile/";
    public static final String STAR_CHAR_APPENDER = "*";
    public static final String OSS_DEFINED_REG_EXP = "/pfm_measurement/(.*)/NE-defined\\W?\\w*-EBS/";
    public static final String SUPPORTED_SUBSCRIPTIONTYPE = "supportedSubscriptionTypes";

    private PmMetaDataConstants() {
        //empty constructor
    }
}
