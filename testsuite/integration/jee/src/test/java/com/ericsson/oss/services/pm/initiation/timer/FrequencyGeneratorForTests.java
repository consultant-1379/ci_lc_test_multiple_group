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

package com.ericsson.oss.services.pm.initiation.timer;

import javax.enterprise.inject.Default;
import javax.enterprise.inject.Specializes;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.time.FrequencyGeneratorImpl;

@Default
@Specializes
public class FrequencyGeneratorForTests extends FrequencyGeneratorImpl {

    private static final long TEN_HOURS_IN_MILLISECONDS = 36_000_000L;

    @Inject
    private Logger logger;

    @Override
    public long getFrequency() {
        logger.info("Returning frequency {} so that the startup recovery timer does not run every 20 seconds during tests",
                TEN_HOURS_IN_MILLISECONDS);
        return TEN_HOURS_IN_MILLISECONDS;
    }
}
