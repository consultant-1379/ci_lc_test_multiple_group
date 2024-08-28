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
 * Simple class to allow integration tests to override the frequency of timers
 */
public class FrequencyGeneratorImpl implements FrequencyGenerator {

    /**
     * {@inheritDoc}
     */
    @Override
    public long getFrequency() {
        return 20000L;
    }

}
