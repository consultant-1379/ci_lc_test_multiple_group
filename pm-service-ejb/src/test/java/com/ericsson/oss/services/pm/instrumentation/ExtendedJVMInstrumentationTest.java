/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.instrumentation;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.Whitebox;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jvm.FileDescriptorRatioGauge;

public class ExtendedJVMInstrumentationTest {

    ExtendedJVMInstrumentation extendedJVMInstrumentation;

    @Mock
    MetricRegistry metricRegistry;

    @Before
    public void setup() {
        extendedJVMInstrumentation = new ExtendedJVMInstrumentation();
        initMocks(this);
    }

    @Test
    public void registerTestThatFileDescriptionRatioGaugeIsAddedToMetricRegistry() {
        Whitebox.setInternalState(extendedJVMInstrumentation, "metricRegistry", metricRegistry);
        extendedJVMInstrumentation.register();
        verify(metricRegistry).register(eq("extended_jvm_instrumentation.file_descriptor_ratio"), any(FileDescriptorRatioGauge.class));
    }

    @Test
    public void registerTestThatGaugeIsNotRegisteredWhenMetricsIsDisabled() {
        extendedJVMInstrumentation.register();
        verify(metricRegistry, times(0)).register(eq("extended_jvm_instrumentation.file_descriptor_ratio"), any(FileDescriptorRatioGauge.class));
    }

}
