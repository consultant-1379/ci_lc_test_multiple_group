
/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.instrumentation;

import com.ericsson.oss.itpf.sdk.core.util.ServiceFrameworkConfigurationProperties;

/**
 * The Instrumentation util.
 */
public class InstrumentationUtil {

    private InstrumentationUtil() {
    }

    /**
     * Is timer metric enabled boolean.
     *
     * @return returns true if timer metric is enabled
     */
    public static boolean isTimerMetricEnabled() {
        /**
         * @see com.ericsson.oss.itpf.sdk.instrument.MetricsUtil
         */
        final String value = System.getProperty(ServiceFrameworkConfigurationProperties.ENABLE_TIMER_METRIC_FOR_PROFILING_JVM_PROPERTY_NAME, "false");
        return "true".equalsIgnoreCase(value);
    }
}
