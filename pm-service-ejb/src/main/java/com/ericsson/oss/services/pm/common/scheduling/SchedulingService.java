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

package com.ericsson.oss.services.pm.common.scheduling;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.Future;

import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.api.constants.TimeConstants;
import com.ericsson.oss.services.pm.time.TimeGenerator;

/**
 * The Scheduling service.
 */
@Stateless
public abstract class SchedulingService {

    @Inject
    private TimerService timerService;

    @Inject
    private TimeGenerator timeGenerator;

    @Inject
    private Logger log;

    /**
     * Creates a new Timer which is set to start from the specified Date
     *
     * @param initialExpiration
     *         : java.util.Date - the date in time for the intial expiration of the timer (if Date is in past timer will kick off immediately)
     * @param intervalDuration
     *         : long - the length of time in milliseconds betwwen timer intervals
     * @param persistentTimer
     *         boolean - The persistent property determines whether the corresponding timer has a lifetime that spans the JVM in which it was created.
     *         Default is false
     *
     * @return - returns info for IntervalTimer
     * @throws CreateSchedulingServiceTimerException
     *         - thrown if timer cannot be created
     */
    public IntervalTimerInfo createIntervalTimer(final Date initialExpiration, final long intervalDuration, final boolean persistentTimer)
            throws CreateSchedulingServiceTimerException {
        log.debug("Creating a new interval timer: {} for class {}, starting at {}, with interval duration {}", getTimerName(),
                this.getClass().getSimpleName(), initialExpiration, intervalDuration);
        final IntervalTimerInfo info = new IntervalTimerInfo(getTimerName(), intervalDuration);
        final TimerConfig config = new TimerConfig(info, persistentTimer);
        final Timer existingTimer = getIntervalTimer(info);
        if (existingTimer != null) {
            throw new CreateSchedulingServiceTimerException(
                    "Timer already exists for this interval, cannot create a new timer for " + this.getClass().getSimpleName());
        }
        timerService.createIntervalTimer(initialExpiration, intervalDuration, config);
        log.debug("Timer created with info: {}", info);
        return info;
    }

    /**
     * Creates a new Timer which is set to start from from the specified Date. Not persisted
     *
     * @param initialExpiration
     *         : java.util.Date - the date in time for the intial expiration of the timer (if Date is in past timer will kick off immediately)
     * @param intervalDuration
     *         : long - the length of time in milliseconds betwwen timer intervals
     *
     * @return - returns created IntervalTimer
     * @throws CreateSchedulingServiceTimerException
     *         - thrown if timer cannot be created
     */
    public IntervalTimerInfo createIntervalTimer(final Date initialExpiration, final long intervalDuration)
            throws CreateSchedulingServiceTimerException {
        return createIntervalTimer(initialExpiration, intervalDuration, false);
    }

    /**
     * Creates a new Timer which is set to start from next interval to the hour (e.g. if interval is 10 min and time is 9:07, timer will initially
     * expire at 9:10)
     *
     * @param intervalDuration
     *         : long - the length of time in milliseconds betwwen timer intervals
     * @param persistentTimer
     *         : boolean - The persistent property determines whether the corresponding timer has a lifetime that spans the JVM in which it was
     *         created. Default is false
     *
     * @return - returns created IntervalTimer
     * @throws CreateSchedulingServiceTimerException
     *         - thrown if timer cannot be created
     */
    public IntervalTimerInfo createIntervalTimer(final long intervalDuration, final boolean persistentTimer)
            throws CreateSchedulingServiceTimerException {
        return createIntervalTimer(getInitialExpiration(intervalDuration), intervalDuration, persistentTimer);
    }

    /**
     * Creates a new Timer which is set to start from next interval to the hour (e.g. if interval is 10 min and time is 9:07, timer will initially
     * expire at 9:10). Not persisted
     *
     * @param intervalDuration
     *         : long - the length of time in milliseconds between timer intervals
     *
     * @return - returns created IntervalTimer
     * @throws CreateSchedulingServiceTimerException
     *         - thrown if timer cannot be created
     */
    public IntervalTimerInfo createIntervalTimer(final long intervalDuration) throws CreateSchedulingServiceTimerException {
        return createIntervalTimer(intervalDuration, false);
    }

    /**
     * Creates a new Timer which is set to start from next interval to the hour (e.g. if interval is 10 min and time is 9:07, timer will initially
     * expire at 9:10). Not persisted
     *
     * @param intervalDuration
     *         : long - the length of time in milliseconds between timer intervals
     *
     * @return - returns true if timer is successfully created
     */
    @Asynchronous
    public Future<Boolean> createIntervalTimerAsync(final long intervalDuration) {
        try {
            createIntervalTimer(intervalDuration, false);
            return new AsyncResult<>(true);
        } catch (final CreateSchedulingServiceTimerException e) {
            log.error("Failed to create timer for class {} with interval {} ", this.getClass().getSimpleName(), intervalDuration);
            log.info("Failed to create timer.", e);
            return new AsyncResult<>(false);
        }
    }

    /**
     * On timeout.
     */
    public abstract void onTimeout();

    /**
     * Gets the interval timer for this instance based on its name and interval
     *
     * @param timerIntervalMilliSec
     *         - interval duration for timer in milliseconds
     *
     * @return Timer - null if no timer is found
     */
    public Timer getIntervalTimer(final long timerIntervalMilliSec) {
        log.debug("Looking for timer for class {} with interval {}", this.getClass().getSimpleName(), timerIntervalMilliSec);
        final IntervalTimerInfo info = new IntervalTimerInfo(getTimerName(), timerIntervalMilliSec);
        return getIntervalTimer(info);
    }

    /**
     * Gets the interval timer for this instance based on its TimerInfo
     *
     * @param info
     *         : TimerInfo - contains information about the timers name and interval
     *
     * @return Timer - null if no timer found
     */
    public Timer getIntervalTimer(final IntervalTimerInfo info) {
        log.debug("Looking for timer for class {} with info {}", this.getClass().getSimpleName(), info);
        final Collection<Timer> timers = timerService.getTimers();
        if (null != timers) {
            Timer result = null;
            for (final Timer timer : timers) {
                log.trace("Timer with info {} found", timer.getInfo());
                if (timer.getInfo().equals(info)) {
                    result = timer;
                    log.debug("Interval timer found {}", timer.getInfo());
                    break;
                }
            }
            log.debug("Returning Interval timer {}", result);
            return result;
        }
        return null;
    }

    /**
     * Get all active interval timers associated with this bean.
     *
     * @return - returns all active timers associated with this bean.
     */
    public Collection<Timer> getIntervalTimers() {
        log.debug("Finding all interval timers in system");
        final Collection<Timer> timers = timerService.getTimers();
        final Collection<Timer> results = new ArrayList<>();
        for (final Timer timer : timers) {
            if (timer.getInfo() instanceof IntervalTimerInfo) {
                results.add(timer);
                log.debug("Interval timer found {}", timer.getInfo());
            }
        }
        return results;
    }

    /**
     * Resets the timer for this bean with the specified timer name and the specified old timer interval, this timer will now be identified by the
     * timer name and the newTimerInterval
     *
     * @param oldTimerInterval
     *         - timer interval to cancel
     * @param newTimerInterval
     *         - timer interval to create
     * @param offset
     *         - a long used to represent the offset duration in milliseconds.
     *
     * @throws CreateSchedulingServiceTimerException
     *         - thrown if timer cannot be created
     */
    public void resetIntervalTimer(final long oldTimerInterval, final long newTimerInterval, final long offset)
            throws CreateSchedulingServiceTimerException {
        log.debug("Resetting interval timer for class {}. Setting interval from {} to {}", this.getClass().getSimpleName(), oldTimerInterval,
                newTimerInterval);
        cancelIntervalTimer(oldTimerInterval);
        createIntervalTimer(getExpirationTimeWithOffset(oldTimerInterval, offset * TimeConstants.ONE_SECOND_IN_MILLISECONDS), newTimerInterval);
    }

    /**
     * Resets the timer for this bean with the specified timer name and the specified old timer interval, this timer will now be identified by the
     * timer name and the newTimerInterval
     *
     * @param oldTimerInterval
     *         - timer interval to cancel
     * @param newTimerInterval
     *         - timer interval to create
     *
     * @throws CreateSchedulingServiceTimerException
     *         - thrown if timer cannot be created
     */
    public void resetIntervalTimer(final long oldTimerInterval, final long newTimerInterval) throws CreateSchedulingServiceTimerException {
        log.debug("Resetting interval timer for class {}. Setting interval from {} to {}", this.getClass().getSimpleName(), oldTimerInterval,
                newTimerInterval);
        cancelIntervalTimer(oldTimerInterval);
        createIntervalTimer(getInitialExpiration(newTimerInterval), newTimerInterval);
    }

    /**
     * Cancels the timer for this bean with the specified timer name and the specified timer interval
     *
     * @param timerIntervalMilliSec
     *         - used to retrieve corresponding interval timer that is to be cancelled
     */
    public void cancelIntervalTimer(final long timerIntervalMilliSec) {
        final Timer timer = getIntervalTimer(timerIntervalMilliSec);
        if (timer == null) {
            throw new IllegalArgumentException("Cannot find interval timer with interval " + timerIntervalMilliSec);
        }
        timer.cancel();
        log.debug("Timer for class {} and interval {} canceled", this.getClass().getSimpleName(), timerIntervalMilliSec);
    }

    /**
     * Utility method to find the next expiration for an interval, starting at on the hour
     *
     * @param interval
     *         - a long used to represent the interval duration in milliseconds.
     *
     * @return - returns the next expiration for an interval
     */
    public Date getInitialExpiration(final long interval) {
        final long currentTime = timeGenerator.currentTimeMillis();
        final long initialDuration = interval - currentTime % interval;
        long futureTime = currentTime + initialDuration;
        if (futureTime < timeGenerator.currentTimeMillis()) {
            futureTime += interval;
        }
        return new Date(futureTime);
    }

    /**
     * Utility method to find the next expiration for an interval with offset, starting at on the hour
     *
     * @param interval
     *         - a long used to represent the interval duration in milliseconds.
     * @param offset
     *         - a long used to represent the offset duration in milliseconds.
     *
     * @return - returns the next expiration for an interval
     */
    public Date getExpirationTimeWithOffset(final long interval, final long offset) {
        log.debug("getMyExpiration interval {}, offset {}", interval, offset);
        final long currentTime = System.currentTimeMillis();
        final long initialDuration = interval - currentTime % interval + offset;
        long futureTime = currentTime + initialDuration;
        if (futureTime < System.currentTimeMillis()) {
            futureTime += interval;
        }
        return new Date(futureTime);
    }

    /**
     * Gets timer name.
     *
     * @return the timer name
     */
    public abstract String getTimerName();

}
