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

package com.ericsson.oss.services.pm.initiation.enodeb.servertime.resource;

import java.util.Calendar;
import java.util.TimeZone;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.security.accesscontrol.annotation.Authorize;
import com.ericsson.oss.services.pm.initiation.common.ResponseData;
import com.ericsson.oss.services.pm.initiation.common.accesscontrol.AccessControlActions;
import com.ericsson.oss.services.pm.initiation.common.accesscontrol.AccessControlResources;
import com.ericsson.oss.services.pm.initiation.enodeb.servertime.resource.dto.DateWithTimeZone;

/**
 * The Server time resource.
 */
@Path("/serverTime")
public class ServerTimeResource {

    @Inject
    Logger log;

    /**
     * Gets time offset.
     *
     * @return the time offset
     */
    @GET
    @Path("/getTimeOffset")
    @Produces({MediaType.APPLICATION_JSON})
    @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    public Response getTimeOffset() {
        final ResponseData response = new ResponseData();

        try {
            final String serverLocationTimeZone = getServerLocationTimezone();
            final TimeZone timeZone = TimeZone.getTimeZone(serverLocationTimeZone);
            final Calendar cal = getCurrentDateAndTime();

            final DateWithTimeZone dateWithTimeZone =
                    new DateWithTimeZone(cal.getTime(), timeZone.getOffset(cal.getTimeInMillis()), serverLocationTimeZone);

            log.debug("DateWithTimeZone Server Response {} ", dateWithTimeZone);

            return Response.status(Response.Status.OK).entity(dateWithTimeZone).build();
        } catch (final Exception e) {
            log.error(e.getMessage(), e);
            response.setCode(Response.Status.INTERNAL_SERVER_ERROR);
            response.setError(e.getMessage());
        }
        return Response.status(response.getCode()).entity(response).build();
    }

    /**
     * Gets server location timezone.
     *
     * @return the server location timezone
     */
    protected String getServerLocationTimezone() {
        return TimeZone.getDefault().getID();
    }

    /**
     * Gets current date and time.
     *
     * @return the current date and time
     */
    protected Calendar getCurrentDateAndTime() {
        return Calendar.getInstance();
    }

}
