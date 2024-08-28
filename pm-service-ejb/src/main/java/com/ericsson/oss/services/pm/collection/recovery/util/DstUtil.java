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

package com.ericsson.oss.services.pm.collection.recovery.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Utility to check for change in DST.
 */
public class DstUtil {

    enum DayType {
        STANDARD_TIME, DAYLIGHT_SAVING_TIME
    }

    /**
     * Checks if the type of the given date. Possible return values are standard time, the date when to switch to daylight saving time (in Europe
     * the last Sunday in March), daylight saving time or the date when to switch back to standard time (in Europe the last Sunday in October).
     *
     * @param cal
     *         Date to check, cannot be null
     *
     * @return True if there is dayLight Saving change, else false.
     */
    public boolean observesDSTChange(final LocalDate cal) {
        final LocalDateTime today = cal.atStartOfDay();
        return getDayType(today) != getDayType(today.plusDays(1));
    }

    DayType getDayType(final LocalDateTime theDay) {
        final ZonedDateTime zonedDateTime = ZonedDateTime.of(theDay, ZoneId.systemDefault());
        return zonedDateTime.getZone().getRules().isDaylightSavings(theDay.toInstant(zonedDateTime.getOffset())) ? DayType.DAYLIGHT_SAVING_TIME
                : DayType.STANDARD_TIME;
    }


}
