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

import javax.enterprise.inject.Specializes;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.time.PastTime;
import com.ericsson.oss.services.pm.time.TimeGeneratorImpl2;

@PastTime
@Specializes
public class TimeGeneratorForTests extends TimeGeneratorImpl2 {

    private static final long MILLISECONDS_TO_SUBTRACT_FROM_CURRENT_TIME = 600_000L;

    @Inject
    private Logger logger;

    @Override
    public long currentTimeMillis() {
        final long pastTime = System.currentTimeMillis() - MILLISECONDS_TO_SUBTRACT_FROM_CURRENT_TIME;
        logger.info("Returning time {}, which is {} ms in the past so that subscriptions are expired immediately for testing purposes", pastTime,
                MILLISECONDS_TO_SUBTRACT_FROM_CURRENT_TIME);
        return pastTime;
    }
}
