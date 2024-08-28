/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.restful.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for ResSubscriptionHelper.
 */
public class ResSubscriptionHelper {

    static final String AMR_4750 = "AMR4750";
    static final String AMR_5900 = "AMR5900";
    static final String AMR_7950 = "AMR7950";
    static final String AMRNBMM = "AMRNBMM";
    static final String AMRNBMM_ALL = "AMRNBMM_ALL";
    static final String AMRWB = "AMRWB";
    static final String AMRWB_ALL = "AMRWB_ALL";
    static final String CSSTREAMING = "CSSTREAMING";
    static final String PSCONVSPEECHEULHS = "PSCONVSPEECHEULHS";
    static final String PSCONVSPEECHEULHS_ALL = "PSCONVSPEECHEULHS_ALL";
    static final String PSCONVUNKNOWNEULHS = "PSCONVUNKNOWNEULHS";
    static final String PSINTDCHDCH = "PSINTDCHDCH";
    static final String PSINTDCHHS = "PSINTDCHHS";
    static final String PSINTEULHS = "PSINTEULHS";
    static final String PSSTRDCHDCH = "PSSTRDCHDCH";
    static final String PSSTRDCHHS = "PSSTRDCHHS";
    static final String PSSTRDCHHS_ALL = "PSSTRDCHHS_ALL";
    static final String SPEECH_12200 = "SPEECH12200";
    static final String SPEECH_12200_PSDCHHS = "SPEECH12200_PSDCHHS";
    static final String SPEECH_12200_ALL = "SPEECH12200_ALL";
    static final String VIDEO = "VIDEO";

    static final String RES_MEAS_PERIOD_INTERACTIVE = "resMeasPeriodInteractive";
    static final String RES_MEAS_PERIOD_SPEECH = "resMeasPeriodSpeech";
    static final String RES_MEAS_PERIOD_STREAMING = "resMeasPeriodStreaming";
    static final String RES_MEAS_PERIOD_VIDEO = "resMeasPeriodVideo";

    private ResSubscriptionHelper() {}

    /**
     * populate the value of Res Service attribute and CategoryMapping.
     *
     * @return map of ResServiceCategoryMapping
     */
    public static Map<String, String> getResServiceCategoryMapping() {
        final Map<String, String> resServiceCategoryMapping = new HashMap<>();
        resServiceCategoryMapping.put(SPEECH_12200, RES_MEAS_PERIOD_SPEECH);
        resServiceCategoryMapping.put(VIDEO, RES_MEAS_PERIOD_VIDEO);
        resServiceCategoryMapping.put(PSINTDCHDCH, RES_MEAS_PERIOD_INTERACTIVE);
        resServiceCategoryMapping.put(PSINTDCHHS, RES_MEAS_PERIOD_INTERACTIVE);
        resServiceCategoryMapping.put(AMR_7950, RES_MEAS_PERIOD_SPEECH);
        resServiceCategoryMapping.put(AMR_5900, RES_MEAS_PERIOD_SPEECH);
        resServiceCategoryMapping.put(AMR_4750, RES_MEAS_PERIOD_SPEECH);
        resServiceCategoryMapping.put(AMRWB, RES_MEAS_PERIOD_SPEECH);
        resServiceCategoryMapping.put(PSINTEULHS, RES_MEAS_PERIOD_INTERACTIVE);
        resServiceCategoryMapping.put(PSSTRDCHHS, RES_MEAS_PERIOD_STREAMING);
        resServiceCategoryMapping.put(PSSTRDCHDCH, RES_MEAS_PERIOD_STREAMING);
        resServiceCategoryMapping.put(SPEECH_12200_ALL, RES_MEAS_PERIOD_SPEECH);
        resServiceCategoryMapping.put(AMRWB_ALL, RES_MEAS_PERIOD_SPEECH);
        resServiceCategoryMapping.put(CSSTREAMING, RES_MEAS_PERIOD_STREAMING);
        resServiceCategoryMapping.put(PSSTRDCHHS_ALL, RES_MEAS_PERIOD_STREAMING);
        resServiceCategoryMapping.put(AMRNBMM, RES_MEAS_PERIOD_SPEECH);
        resServiceCategoryMapping.put(AMRNBMM_ALL, RES_MEAS_PERIOD_SPEECH);
        resServiceCategoryMapping.put(SPEECH_12200_PSDCHHS, RES_MEAS_PERIOD_SPEECH);
        resServiceCategoryMapping.put(PSCONVSPEECHEULHS, RES_MEAS_PERIOD_SPEECH);
        resServiceCategoryMapping.put(PSCONVSPEECHEULHS_ALL, RES_MEAS_PERIOD_SPEECH);
        resServiceCategoryMapping.put(PSCONVUNKNOWNEULHS, RES_MEAS_PERIOD_VIDEO);
        return resServiceCategoryMapping;
    }
}
