
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

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * The Instrumentation beans loader.
 */
@Singleton
@Startup
@ApplicationScoped
public class InstrumentationBeansLoader {
    @Inject
    private ExtendedFileCollectionInstrumentation extendedFileCollectionInstrumentation;
    @Inject
    private ExtendedJVMInstrumentation extendedJVMInstrumentation;
    @Inject
    private ScannerPollingInstrumentation scannerPollingInstrumentation;
    @Inject
    private SubscriptionInstrumentation subscriptionInstrumentation;

    /**
     * Initialises instrumentation
     */
    @PostConstruct
    public void init() {
        if (InstrumentationUtil.isTimerMetricEnabled()) {
            // pushing the Service Framework to initialise and expose these beans in JMX just by calling a method
            extendedJVMInstrumentation.getFileDescriptorRatio();
            extendedFileCollectionInstrumentation.getMinStatisticalFileCollectionDurationPerNode();
            scannerPollingInstrumentation.getCountScannerPollingDurationPerNode();
            subscriptionInstrumentation.getCountCelltraceSubscriptionActivationDurationForStatusToGoToActive();
        }
    }

}
