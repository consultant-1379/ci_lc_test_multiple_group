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

package com.ericsson.oss.services.pm.instrumentation;

import static org.testng.Assert.assertEquals;

import javax.inject.Inject;


import org.junit.Test;

import com.ericsson.oss.itpf.sdk.core.util.ServiceFrameworkConfigurationProperties;

public class InstrumentationUtilTest {

    @Inject
    ServiceFrameworkConfigurationProperties serviceFrameworkConfigurationProperties;

    @Test
    public void isTimerMetricEnabledTestDefaultValueReturnsFalse() {
        assertEquals(false, InstrumentationUtil.isTimerMetricEnabled());
        System.setProperty(ServiceFrameworkConfigurationProperties.ENABLE_TIMER_METRIC_FOR_PROFILING_JVM_PROPERTY_NAME, "true");
        assertEquals(true, InstrumentationUtil.isTimerMetricEnabled());
    }

}
