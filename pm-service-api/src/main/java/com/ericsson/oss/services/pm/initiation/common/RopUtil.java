/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2014
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.common;

import java.util.Calendar;
import java.util.Date;

import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.services.pm.initiation.util.RopTime;
import com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants;
import com.ericsson.oss.services.pm.time.TimeGenerator;

/**
 * Util class for ROP time calculations in a centralized way. Doesnt have state, and doesnt require a specific rop size. Assumption is that ROP size
 * is seconds, but has not partial minutes.
 *
 * @author epirgui
 */
public class RopUtil {

    @Inject
    private TimeGenerator timeGenerator;
    /**
     * Returns the time when next immediate ROP starts based on the time given and the ROP size.
     * Assumptions:
     * - although ROP size is in seconds, in reality must be a multiple of 60, ie in a minute scale.
     * - value in minutes should be divisible by 60 (1 hour) for simplicity, since rops required now are 1,15,60
     * Ex.:
     * - ropSize 60 (seconds) is equivalent to 1 (minute)
     * - ropSize 600 (seconds) is equivalent to 10 (minutes)
     * - ropSize 900 (seconds) is equivalent to 15 (minutes)
     * - ropSize 1800 (seconds) is equivalent to 30 (minutes)
     * - ropSize 3600 (seconds) is equivalent to 60 (minutes)
     *
     * @param ropSize
     *         interval size in seconds
     * @param time
     *         to calculate the next immediate ROP time.
     *
     * @return time when the next immediate ROP starts
     * @throws IllegalArgumentException
     *         when ropSize is not convertible into exact minutes.
     */
    public Date nextRop(final int ropSize, final Date time) {
        final Date currentRop = currentRop(ropSize, time);
        final int ropSizeMinutes = ropSize / 60;
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(currentRop);
        // next
        calendar.add(Calendar.MINUTE, ropSizeMinutes);
        return calendar.getTime();
    }

    /**
     * Returns the current ROP time based on the time given and the ROP size.
     * Assumptions:
     * - although ROP size is in seconds, in reality must be a multiple of 60, ie in a minute scale.
     * - value in minutes should be divisible by 60 (1 hour) for simplicity, since rops required now are 1,15,60,24
     * Ex.:
     * - ropSize 60 (seconds) is equivalent to 1 (minute)
     * - ropSize 600 (seconds) is equivalent to 10 (minutes)
     * - ropSize 900 (seconds) is equivalent to 15 (minutes)
     * - ropSize 1800 (seconds) is equivalent to 30 (minutes)
     * - ropSize 3600 (seconds) is equivalent to 60 (minutes)
     * - ropSize 86400 (seconds) is equivalent to 1440 (minutes)
     *
     * @param ropSize
     *         interval size in seconds
     * @param time
     *         to calculate the current ROP time.
     *
     * @return time when current ROP started
     * @throws IllegalArgumentException
     *         when ropSize is not convertible into exact minutes.
     */
    public Date currentRop(final int ropSize, final Date time) {
        if (ropSize < 60 || time == null) {
            throw new IllegalArgumentException("Rop size must be greater or equal than 60s and time must not be null.");
        }
        int rest = ropSize % 60;
        if (rest != 0) {
            throw new IllegalArgumentException("rop size must be multiple of 60, ie convertible to mins.");
        }

        final int ropSizeMinutes = ropSize / 60;
        rest = 1440 % ropSizeMinutes;
        if (rest != 0) {
            throw new IllegalArgumentException("Up to 24 hour ROP is supported " + ropSizeMinutes + "minutes.");
        }
        rest = 60 % ropSizeMinutes;
        final Calendar calendar = Calendar.getInstance();
        // clean seconds and milliseconds of the rop, since its always on 0
        calendar.setTime(time);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.SECOND, 0);
        final int elapsedMinutes = calendar.get(Calendar.MINUTE);
        int currentRop;
        if (rest == 60) {
            //Greater that 60min ROP supported.
            calendar.set(Calendar.MINUTE, 0);
            final int elapsedHour = calendar.get(Calendar.HOUR_OF_DAY);

            final int ropSizeinHour = ropSizeMinutes / 60;

            final int elapsedRops = elapsedHour / ropSizeinHour;
            currentRop = elapsedRops * ropSizeinHour;
            calendar.set(Calendar.HOUR_OF_DAY, currentRop);
        } else {
            //Less than 60min ROP supported.
            final int elapsedRops = elapsedMinutes / ropSizeMinutes;
            currentRop = elapsedRops * ropSizeMinutes;
            calendar.set(Calendar.MINUTE, currentRop);
        }

        return calendar.getTime();
    }

    /**
     * Util method look 15 min roptime and check whether current rop start time and offset within rop if yes, return the date in time for the intial
     * expiration of the timer (if Date is in past timer, date will calculate based on CurrentROPPeriodEndTime and offset).
     *
     * @param offset
     *     int value for delayed timer.
     *
     * @return date
     * - the date in time for the intial expiration of the timer.
     */
    public Date getInitialExpirationTime(final int offset) {
        final RopTime currentRop = new RopTime(System.currentTimeMillis(), RopPeriod.FIFTEEN_MIN.getDurationInSeconds());
        long initialExpirationTime = currentRop.getCurrentRopStartTimeInMilliSecs() + (offset * TimeConstants.ONE_MINUTE_IN_MILLISECONDS);
        if (initialExpirationTime < timeGenerator.currentTimeMillis()) {
            initialExpirationTime = currentRop.getCurrentROPPeriodEndTime().getTime() + (offset * TimeConstants.ONE_MINUTE_IN_MILLISECONDS);
        }
        return new Date(initialExpirationTime);
    }

}
