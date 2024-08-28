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

package com.ericsson.oss.services.pm.initiation.enodeb.pmprocess.resource;

import java.util.List;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.interceptor.Interceptors;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.security.accesscontrol.annotation.Authorize;
import com.ericsson.oss.pmic.dao.PmProcessReportDao;
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus;
import com.ericsson.oss.pmic.dto.scanner.PmProcessReportEntry;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.impl.handler.InvokeInTransactionInterceptor;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.NodeNotFoundDataAccessException;
import com.ericsson.oss.services.pm.initiation.common.ResponseData;
import com.ericsson.oss.services.pm.initiation.common.accesscontrol.AccessControlActions;
import com.ericsson.oss.services.pm.initiation.common.accesscontrol.AccessControlResources;

/**
 * Resource class providing Scanners and PmJobs for GUI
 */
@Path("/nodereports")
@Stateless
public class PmProcessReportResource {

    @Inject
    private Logger log;

    @Inject
    private PmProcessReportDao pmProcessReportDao;

    /**
     * Fetch the PmProcess for a list of nodes. As per UISDK convention is to send ids as a comma seperated list in the URI List can be one or more
     * nodes
     *
     * @param type
     *         - The type of response
     * @param poid
     *         - id for Persistence Object from DPS
     *
     * @return - Paginated list of scanners/PmJobs for the given nodes
     * @throws DataAccessException
     *         - if a non-retriable exception is thrown from DPS
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @ReadOnly
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @Interceptors(InvokeInTransactionInterceptor.class)
    public Response getPmProcessInfoForNodes(@QueryParam("type") final String type, @QueryParam("id") final String poid)
            throws DataAccessException {
        if (type != null && "list".equals(type)) {
            return getPmProcessInfoForList();
        }

        // id is the poid from DPS. It is assumed this poid value is always numerical
        if (poid == null || !poid.matches("^[0-9]*$")) {
            final ResponseData response = new ResponseData();
            response.setCode(Response.Status.BAD_REQUEST);
            response.setError("The URL does not include a valid 'id'");
            return Response.status(response.getCode()).entity(response).build();
        }

        if (type == null) {
            final ResponseData response = new ResponseData();
            response.setCode(Response.Status.BAD_REQUEST);
            response.setError("You have not specified the request type");
            return Response.status(response.getCode()).entity(response).build();
        }

        if ("ne".equals(type)) {
            return getPmProcessInfoForNode(Long.valueOf(poid));
        } else if ("sub".equals(type)) {
            return getPmProcessInfoForSubscription(Long.valueOf(poid));
        } else {
            final ResponseData response = new ResponseData();
            response.setCode(Response.Status.BAD_REQUEST);
            response.setError("The URL does not include a valid 'type'");
            return Response.status(response.getCode()).entity(response).build();
        }

    }

    private Response getPmProcessInfoForList() throws DataAccessException {
        final ScannerStatus[] scannerStatuses = {ScannerStatus.ERROR};
        final PmJobStatus[] pmJobStatuses = {PmJobStatus.ERROR};
        return prepareResponse(pmProcessReportDao.createPmProcessReportByScannerStatusAndPmJobStatus(scannerStatuses, pmJobStatuses));
    }

    private Response getPmProcessInfoForNode(final Long nodePoId) throws DataAccessException {
        try {
            log.debug("preparing PmProcessInfo for poid {}", nodePoId);
            return prepareResponse(pmProcessReportDao.createPmProcessReportByNodeId(nodePoId));
        } catch (final NodeNotFoundDataAccessException e) {
            log.error("Node with poid : {} not found.", nodePoId);
            log.info("Failed to find Node.", e);
            return buildEmptyOrErrorResponse(e.getMessage(), Response.Status.NOT_FOUND);
        }
    }

    private Response getPmProcessInfoForSubscription(final Long subscriptionId) throws DataAccessException {
        log.debug("preparing PmProcessInfo for subscriptionId {}", subscriptionId);
        return prepareResponse(pmProcessReportDao.createPmProcessReportBySubscriptionId(subscriptionId));
    }

    /**
     * @return Response containing the status
     */
    @GET
    @Path("/nodeprocess/headers")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getNodeProcessHeaders() {
        return Response.status(Response.Status.OK).entity(new NodeReportTableHeaderInfo()).build();
    }

    private Response buildEmptyOrErrorResponse(final String errorMsg, final Response.Status status) {
        final ResponseData response = new ResponseData();
        response.setCode(status);
        response.setError(errorMsg);
        return Response.status(response.getCode()).entity(response).build();
    }

    private Response prepareResponse(final List<PmProcessReportEntry> combinedReports) {
        if (combinedReports.isEmpty()) {
            return buildEmptyOrErrorResponse("There were no scanners/pmjos found", Response.Status.NO_CONTENT);
        }
        log.debug("Scanner/PmJobs report generated, converting to output format");
        return Response.status(Response.Status.OK).entity(combinedReports).build();
    }

}
