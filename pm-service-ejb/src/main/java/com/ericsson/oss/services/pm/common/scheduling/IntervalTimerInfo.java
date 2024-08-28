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

import static com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants.ONE_MINUTE_IN_MILLISECONDS;
import static com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants.ONE_SECOND_IN_MILLISECONDS;

import java.io.Serializable;

/**
 * This class represents the info for a new Interval timer as created by the {@link SchedulingService} class. Is created by Scheduling service under
 * the hood when creating an interval timer, it is used to uniquely identify an interval timer. An interval usual has one specific name per
 * implementation of Scheduling service and each implementation of scheduling service can only create one timer for a specific interval, to prevent
 * duplication of timers.
 *
 * @author enichyl
 * @see SchedulingService
 */
public class IntervalTimerInfo implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     * The unique name for this timer, usually corresponds to the implementation of {@link SchedulingService}
     */
    private String timerName;

    /**
     * The specific interval duration for this timer in milliseconds.
     */
    private long timerIntervalMilliSec;

    /**
     * Default no arg constructor, timerName and timerIntervalMilliSec set as null by default.
     */
    public IntervalTimerInfo() {
        //currently no implementation
    }

    /**
     * Create a new instance of intervalTimerInfo with the specified timerName and timerIntervalMilliSec
     *
     * @param timerName
     *         - unique name of this timer
     * @param timerIntervalMilliSec
     *         - specific interval duration for this timer in milliseconds
     */
    public IntervalTimerInfo(final String timerName, final long timerIntervalMilliSec) {
        this.timerName = timerName;
        this.timerIntervalMilliSec = timerIntervalMilliSec;
    }

    /**
     * @return the timerName
     */
    public String getTimerName() {
        return timerName;
    }

    /**
     * @param timerName
     *         the timerName to set
     */
    public void setTimerName(final String timerName) {
        this.timerName = timerName;
    }

    /**
     * @return the timerIntervalMilliSec
     */
    public long getTimerIntervalMilliSec() {
        return timerIntervalMilliSec;
    }

    /**
     * @param timerIntervalMilliSec
     *         the timerIntervalMilliSec to set
     */
    public void setTimerIntervalMilliSec(final long timerIntervalMilliSec) {
        this.timerIntervalMilliSec = timerIntervalMilliSec;
    }

    /**
     * Gets the interval of the timer in seconds as an int. Result is the floor of the decimal value.
     *
     * @return Result is the floor of the decimal value from dividing by one second in milliseconds
     */
    public int getTimerIntervalSeconds() {
        return (int) (timerIntervalMilliSec / ONE_SECOND_IN_MILLISECONDS);
    }

    /**
     * Gets the interval of the timer in minutes as an int. Result is the floor of the decimal value.
     *
     * @return Result is the floor of the decimal value from dividing by one minute in milliseconds
     */
    public int getTimerIntervalMinutes() {
        return (int) (timerIntervalMilliSec / ONE_MINUTE_IN_MILLISECONDS);
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (timerIntervalMilliSec ^ timerIntervalMilliSec >>> 32);
        result = prime * result + (timerName == null ? 0 : timerName.hashCode());
        return result;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof IntervalTimerInfo)) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final IntervalTimerInfo other = (IntervalTimerInfo) obj;
        if (timerIntervalMilliSec != other.timerIntervalMilliSec) {
            return false;
        }
        if (timerName == null) {
            if (other.timerName != null) {
                return false;
            }
        } else if (!timerName.equals(other.timerName)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "[name=" + timerName + ",interval=" + timerIntervalMilliSec + "]";
    }

}
