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

package com.ericsson.oss.services.pm.time;

/**
 * Simple interface to return a value for a timer frequency
 */
public interface FrequencyGenerator {

    /**
     * Returns a value in milliseconds. Used so that integration tests can override timer frequencies
     *
     * @return a value in milliseconds
     */
    long getFrequency();
}
