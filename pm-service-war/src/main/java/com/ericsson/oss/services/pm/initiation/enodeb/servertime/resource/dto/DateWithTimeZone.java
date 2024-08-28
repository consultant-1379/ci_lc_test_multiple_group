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

package com.ericsson.oss.services.pm.initiation.enodeb.servertime.resource.dto;

import java.util.Date;

/**
 * Class for created a Date with time zone offset.
 */
public class DateWithTimeZone {
    private Date date;
    private int offset;
    private String serverLocation;

    /**
     */
    public DateWithTimeZone() {
        super();
    }

    /**
     * @param date
     *         - current date
     * @param offset
     *         - timezone offset
     * @param serverLocation
     *         - String value for the server location
     */
    public DateWithTimeZone(final Date date, final int offset, final String serverLocation) {
        super();
        this.date = date;
        this.offset = offset;
        this.serverLocation = serverLocation;
    }

    /**
     * @return the date
     */
    public Date getDate() {
        return date;
    }

    /**
     * @param date
     *         the date to set
     */
    public void setDate(final Date date) {
        this.date = date;
    }

    /**
     * @return the offset
     */
    public int getOffset() {
        return offset;
    }

    /**
     * @param offset
     *         the offset to set
     */
    public void setOffset(final int offset) {
        this.offset = offset;
    }

    /**
     * @return the serverLocation
     */
    public String getServerLocation() {
        return serverLocation;
    }

    /**
     * @param serverLocation
     *         the serverLocation to set
     */
    public void setServerLocation(final String serverLocation) {
        this.serverLocation = serverLocation;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        final DateWithTimeZone that = (DateWithTimeZone) other;

        if (offset != that.offset) {
            return false;
        }
        if (date != null ? !date.equals(that.date) : that.date != null) {
            return false;
        }
        return !(serverLocation != null ? !serverLocation.equals(that.serverLocation) : that.serverLocation != null);

    }

    @Override
    public int hashCode() {
        int result = date != null ? date.hashCode() : 0;
        result = 31 * result + offset;
        result = 31 * result + (serverLocation != null ? serverLocation.hashCode() : 0);
        return result;
    }
}
