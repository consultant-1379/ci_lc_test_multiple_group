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

package com.ericsson.oss.services.pm.initiation.schedulers;

import java.util.concurrent.Future;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * This class is meant for creating timer for processing timeout during subscription activation/ deactivation, add/remove nodes from ACTIVE
 * subscription. The initial delay is 1min and this will be triggered every 20sec
 *
 * @author eushmar
 */
@Stateless
public class InitiationDelayTimer {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitiationDelayTimer.class);

    private static final long INTERVAL_TIME_IN_MILLISECONDS = 20000;

    private static final long INITIAL_EXECUTION_DELAY = 20000; // 20 secs

    @Inject
    private TimerService timerService;

    @Inject
    private MembershipListener membershipChangeListener;

    @Inject
    private InitiationDelayTimerHandler timerHandler;

    /**
     * timer will be created on startup
     *
     * @return - returns true if timer is created
     */
    @Asynchronous
    public Future<Boolean> createTimerOnStartup() {
        try {
            createTimer(INTERVAL_TIME_IN_MILLISECONDS);
            return new AsyncResult<>(true);
        } catch (final Exception e) {
            LOGGER.debug("Exception while creating timer for InitiationDelay", e);
            return new AsyncResult<>(false);
        }
    }

    /**
     * On time out, apply activation/deactivation time out logic
     *
     * @param timer
     *         - timer to process initiation timeout
     */
    @Timeout
    @LogProfiler(name = "Processing initiation timeout for subscriptions", ignoreExecutionTimeLowerThan = 10L)
    public void execute(final Timer timer) {
        if (membershipChangeListener.isMaster()) {
            LOGGER.debug("Initiation delay timer expired...");
            timerHandler.processInitiationTimeout();
        }
    }

    /**
     * Start the timer
     *
     * @param intervalTime
     *         - interval time to create new interval timer
     */
    public void createTimer(final long intervalTime) {
        final TimerConfig timerConfig = new TimerConfig(intervalTime, false);
        timerService.createIntervalTimer(INITIAL_EXECUTION_DELAY, intervalTime, timerConfig);
        LOGGER.info("Created subscription initiation timer [{} milliseconds] for time delay", intervalTime);
    }
}
