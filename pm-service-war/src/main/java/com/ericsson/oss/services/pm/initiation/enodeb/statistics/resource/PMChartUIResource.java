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

package com.ericsson.oss.services.pm.initiation.enodeb.statistics.resource;

import java.util.List;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.initiation.api.FileCollectionHeartBeatServiceLocal;
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionChartData;

/**
 * Pm chart ui resource.
 */
@Path("/pmchartdata")
public class PMChartUIResource {


    @Inject
    Logger logger;

    @Inject
    FileCollectionHeartBeatServiceLocal fileCollectionHeartBeatService;

    /**
     * Gets bytes stored.
     *
     * @param timeIntervalMinutes
     *         the interval in minutes
     * @param numberOfIntervals
     *         the number of intervals
     *
     * @return the bytes stored
     */
    @GET
    @Path("/bytesStored/{timeIntervalMinutes}/{numberOfIntervals}")
    @Produces("application/json")
    public Response getBytesStored(@PathParam("timeIntervalMinutes") final int timeIntervalMinutes,
                                   @PathParam("numberOfIntervals") final int numberOfIntervals) {
        logger.debug("Recieved REST request for bytes transferred data for {}, {} minute intervals", numberOfIntervals, timeIntervalMinutes);
        final List<FileCollectionChartData> fileCollectionSuccessIntervals = fileCollectionHeartBeatService.getBytesStoredChartData(
                timeIntervalMinutes, numberOfIntervals);
        return Response.status(Response.Status.OK).entity(fileCollectionSuccessIntervals).build();
    }

    /**
     * Gets files missed.
     *
     * @param timeIntervalMinutes
     *         the interval in minutes
     * @param numberOfIntervals
     *         the number of intervals
     *
     * @return the files missed
     */
    @GET
    @Path("/filesMissed/{timeIntervalMinutes}/{numberOfIntervals}")
    @Produces("application/json")
    public Response getFilesMissed(@PathParam("timeIntervalMinutes") final int timeIntervalMinutes,
                                   @PathParam("numberOfIntervals") final int numberOfIntervals) {
        logger.debug("Recieved REST request for files missed data for {}, {} minute intervals", numberOfIntervals, timeIntervalMinutes);
        final List<FileCollectionChartData> result = fileCollectionHeartBeatService.getFilesMissedChartData(timeIntervalMinutes,
                numberOfIntervals);
        return Response.status(Response.Status.OK).entity(result).build();
    }
}
