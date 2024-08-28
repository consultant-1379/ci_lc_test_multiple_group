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
package com.ericsson.oss.services.pm.collection.cache;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.enterprise.context.ApplicationScoped;

/**
 * {@inheritDoc}
 */
@ApplicationScoped
public class StartupRecoveryMonitor implements StartupRecoveryMonitorLocal {

    private final AtomicBoolean startupRecoveryDone = new AtomicBoolean(false);

    /**
     * {@inheritDoc}
     */
    public boolean isStartupRecoveryDone() {
        return startupRecoveryDone.get();
    }

    /**
     * {@inheritDoc}
     */
    public void startupRecoveryDone() {
        startupRecoveryDone.set(true);
    }
}
