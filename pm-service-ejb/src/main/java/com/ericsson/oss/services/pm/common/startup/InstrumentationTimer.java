/*
 * ------------------------------------------------------------------------------
 * ********************************************************************************
 * * COPYRIGHT Ericsson 2016
 * *
 * * The copyright to the computer program(s) herein is the property of
 * * Ericsson Inc. The programs may be used and/or copied only with written
 * * permission from Ericsson Inc. or in accordance with the terms and
 * * conditions stipulated in the agreement/contract under which the
 * * program(s) have been supplied.
 * *******************************************************************************
 * *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.common.startup;

import java.util.Collection;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.collection.instrumentation.CombinedRopFileCollectionCycleInstrumentation;
import com.ericsson.oss.services.pm.collection.schedulers.CollectionSchedulerHelper;

/**
 * This class creates Instrumentation Timer for File Collection.
 */
@Stateless
public class InstrumentationTimer {

    @Inject
    private CombinedRopFileCollectionCycleInstrumentation collectionCycleInstrumentation;

    @Inject
    private CollectionSchedulerHelper collectionSchedulerHelper;

    @Inject
    private Logger logger;

    @Resource
    private TimerService timerService;

    /**
     * @param ropPeriodInSeconds
     *            - the Rop duration in seconds
     */
    public void createTimer(final int ropPeriodInSeconds) {
        final Timer timer = getTimerFromTimerService(ropPeriodInSeconds);
        if (timer == null) {
            final long initialDelay = getInitialDelay(ropPeriodInSeconds);
            final TimerConfig timerConfig = new TimerConfig(ropPeriodInSeconds, false);
            timerService.createIntervalTimer(initialDelay, ropPeriodInSeconds * 1000L, timerConfig);
        }
    }

    /**
     * @param timer
     *            - the Timer Service for Instrumentation
     *            When Timer completes, reset the Instrumentation data.
     */
    @Timeout
    public void timeout(final Timer timer) {
        final int ropTimeInSeconds = (int) timer.getInfo();
        collectionCycleInstrumentation.resetCurrentROP(ropTimeInSeconds);
    }

    /**
     * Delay required to start Instrumentation timer for File Collection
     */
    private long getInitialDelay(final long ropPeriodInSeconds) {
        return collectionSchedulerHelper.getTaskSenderInitialDelay(ropPeriodInSeconds);
    }

    public void stopTimer(final int ropPeriodInSeconds) {
        getAndCancelTimer(ropPeriodInSeconds);
    }

    private boolean getAndCancelTimer(final int ropTime) {
        boolean isTimerStopped = false;
        try {
            final Timer timer = getTimerFromTimerService(ropTime);
            if (timer != null) {
                logger.debug("Stopping {}s ROP  timer for File collection", timer.getInfo());
                timer.cancel();
                logger.debug("Stopped {}s ROP  timer for File collection", timer.getInfo());
                isTimerStopped = true;
            }
        } catch (final Exception e) {
            logger.error("Failed to stop timer for roptime {} Exception {}", ropTime, e);
        }

        return isTimerStopped;
    }

    private Timer getTimerFromTimerService(final int ropTime) {
        final Collection<Timer> timers = timerService.getTimers();
        for (final Timer timer : timers) {
            if (timer.getInfo().equals(ropTime)) {
                return timer;
            }
        }
        return null;
    }
}
