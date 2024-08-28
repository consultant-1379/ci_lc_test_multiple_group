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

/**
 * This class is used to determine if @StartupRecovery has finished
 *
 * @return true if startup recovery is done
 */
public interface StartupRecoveryMonitorLocal {

    /**
     * This method is used to determine if @StartupRecovery has finished
     *
     * @return true if startup recovery is done
     */
    boolean isStartupRecoveryDone();

    /**
     * This method is called by @StartupRecovery when it finishes
     */
    void startupRecoveryDone();
}
