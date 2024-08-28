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

package com.ericsson.oss.services.pm.scheduling.roptime;

import static com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants.ONE_MINUTE_IN_SECONDS;
import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.junit.Test;

import com.ericsson.oss.services.pm.initiation.util.RopTime;

public class RopTimeTest {

    long fifteenMinutesInSeconds = 15 * ONE_MINUTE_IN_SECONDS;

    long oneMinutesInSeconds = ONE_MINUTE_IN_SECONDS;

    @Test
    public void testgetCurrentROPPeriodEndTime() {
        final RopTime ropTime = new RopTime(System.currentTimeMillis(), fifteenMinutesInSeconds);
        final Date date = ropTime.getCurrentROPPeriodEndTime();
        confirmRopTime(date);
    }

    @Test
    public void testgetPreviousROPPeriodEndTime() {
        final RopTime ropTime = new RopTime(System.currentTimeMillis(), fifteenMinutesInSeconds);
        final Date date = ropTime.getPreviousROPPeriodEndTime();
        confirmRopTime(date);
    }

    @Test
    public void testTimeStampsWhenLocalTimeIsOnCurrentDate() {
        // Use time in February, note this is one hour ahead
        testTimeStampForDifferentWinterOrSummerTime(1, "A20090213.", "2115-2130", "2215+0100-2230+0100");
    }

    @Test
    public void testTimeStampsWhenWorldTimeZoneIDIsInDST() {
        // Use time in July, note this is two hours ahead as it is during summer (daylight savings) time
        testTimeStampForDifferentWinterOrSummerTime(6, "A20090713.", "2015-2030", "2215+0200-2230+0200");
    }

    @Test
    public void timeStampsWhenLocalTimeIsOnCurrentDateFor1MinuteROP() {
        final String timeZoneName = "Europe/Amsterdam"; // "Etc/GMT+1"
        final Calendar localCalendar = getLocalCalendar(timeZoneName);
        // Set time to 13-Feb 22:28h "local time", note this is one hour ahead
        localCalendar.set(2009, 1, 13, 22, 28, 0);
        final String datePrefix = "A20090213.";
        // note one hour ahead, so in UTC time ROP ends at 2130
        final String expectedUtcTimeStamps = datePrefix + "2127-2128";
        final String expectedOssTimeStamps = datePrefix + "2227+0100-2228+0100";
        final RopTime ropEndTime = new RopTime(localCalendar.getTimeInMillis(), oneMinutesInSeconds);
        assertEquals(expectedUtcTimeStamps, ropEndTime.getUtcTimeStamp());
        assertEquals(expectedOssTimeStamps, ropEndTime.getOssTimeStamp(timeZoneName));
    }

    @Test
    public void testTimeStampsWhenWorldTimeZoneIDIsInDSTFor1MinuteRop() {
        final String timeZoneName = "Europe/Amsterdam"; // "Etc/GMT+1"
        final Calendar localCalendar = getLocalCalendar(timeZoneName);
        localCalendar.set(2011, 2, 29, 7, 49, 0);
        final String datePrefix = "A20110329.";
        // note one hour ahead, so in UTC time ROP ends at 2130
        final String expectedUtcTimeStamps = datePrefix + "0548-0549";
        final String expectedOssTimeStamps = datePrefix + "0748+0200-0749+0200";
        final RopTime ropEndTime = new RopTime(localCalendar.getTimeInMillis(), oneMinutesInSeconds);
        assertEquals(expectedUtcTimeStamps, ropEndTime.getUtcTimeStamp());
        assertEquals(expectedOssTimeStamps, ropEndTime.getOssTimeStamp(timeZoneName));
    }

    private void confirmRopTime(final Date time) {
        final Calendar proposedTime = Calendar.getInstance();
        proposedTime.setTime(time);
        // confirm it is an exact minute
        assertEquals(proposedTime.get(Calendar.SECOND), 0);
        assertEquals(proposedTime.get(Calendar.MILLISECOND), 0);
        // confirm is is a multiple of 15 minutes past the hour
        assertEquals(proposedTime.get(Calendar.MINUTE) % 15, 0);
    }

    private Calendar getLocalCalendar(String timeZoneName ) {
        final Calendar localCalendar = Calendar.getInstance();
        final TimeZone timeZone = TimeZone.getTimeZone(timeZoneName);
        localCalendar.setTimeZone(timeZone);
        return localCalendar;
    }

    private void testTimeStampForDifferentWinterOrSummerTime(final int month, final String expectedDatePrefix, final String expectedUtcTimeStamp,
                                                             final String expectedOssTimeStamp) {
        final String timeZoneName = "Europe/Amsterdam"; // "Etc/GMT+1"
        final Calendar localCalendar = getLocalCalendar(timeZoneName);
        localCalendar.set(2009, month, 13, 22, 30, 0);
        final RopTime ropEndTime = new RopTime(localCalendar.getTimeInMillis(), fifteenMinutesInSeconds);
        assertEquals(expectedDatePrefix + expectedUtcTimeStamp, ropEndTime.getUtcTimeStamp());
        assertEquals(expectedDatePrefix + expectedOssTimeStamp, ropEndTime.getOssTimeStamp(timeZoneName));
    }


}
