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

package com.ericsson.oss.services.pm.initiation.timer;

import java.io.Serializable;
import java.util.Date;
import javax.ejb.EJBException;
import javax.ejb.NoMoreTimeoutsException;
import javax.ejb.NoSuchObjectLocalException;
import javax.ejb.ScheduleExpression;
import javax.ejb.Timer;
import javax.ejb.TimerHandle;

public class DummyTimer implements Timer {

    private final int timePeriodInSeconds;

    public DummyTimer(final int timePeriodInSeconds) {
        this.timePeriodInSeconds = timePeriodInSeconds;
    }

    /*
     * (non-Javadoc)
     * @see javax.ejb.Timer#cancel()
     */
    @Override
    public void cancel() throws IllegalStateException, NoSuchObjectLocalException, EJBException {
    }

    /*
     * (non-Javadoc)
     * @see javax.ejb.Timer#getHandle()
     */
    @Override
    public TimerHandle getHandle() throws IllegalStateException, NoSuchObjectLocalException, EJBException {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see javax.ejb.Timer#getInfo()
     */
    @Override
    public Serializable getInfo() throws IllegalStateException, NoSuchObjectLocalException, EJBException {
        return timePeriodInSeconds;
    }

    /*
     * (non-Javadoc)
     * @see javax.ejb.Timer#getNextTimeout()
     */
    @Override
    public Date getNextTimeout() throws IllegalStateException, NoMoreTimeoutsException, NoSuchObjectLocalException, EJBException {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see javax.ejb.Timer#getSchedule()
     */
    @Override
    public ScheduleExpression getSchedule() throws IllegalStateException, NoSuchObjectLocalException, EJBException {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see javax.ejb.Timer#getTimeRemaining()
     */
    @Override
    public long getTimeRemaining() throws IllegalStateException, NoMoreTimeoutsException, NoSuchObjectLocalException, EJBException {
        return 0;
    }

    /*
     * (non-Javadoc)
     * @see javax.ejb.Timer#isCalendarTimer()
     */
    @Override
    public boolean isCalendarTimer() throws IllegalStateException, NoSuchObjectLocalException, EJBException {
        return false;
    }

    /*
     * (non-Javadoc)
     * @see javax.ejb.Timer#isPersistent()
     */
    @Override
    public boolean isPersistent() throws IllegalStateException, NoSuchObjectLocalException, EJBException {
        return false;
    }

}
