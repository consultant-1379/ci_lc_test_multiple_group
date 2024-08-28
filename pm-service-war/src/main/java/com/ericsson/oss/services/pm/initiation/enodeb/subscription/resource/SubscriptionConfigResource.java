/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource;

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.NO_PERMISSION_ACCESS;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.PMIC_PARAMETER_ERROR;

import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.security.accesscontrol.SecurityViolationException;
import com.ericsson.oss.itpf.sdk.security.accesscontrol.annotation.Authorize;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.initiation.api.ReadPMICConfigurationLocal;
import com.ericsson.oss.services.pm.initiation.common.ResponseData;
import com.ericsson.oss.services.pm.initiation.common.accesscontrol.AccessControlActions;
import com.ericsson.oss.services.pm.initiation.common.accesscontrol.AccessControlResources;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.oss.services.pm.services.exception.ConfigurationParameterException;

/**
 * The Subscription config resource.
 */
@Path("/pmcapability")
public class SubscriptionConfigResource {

    private static final String SUPPORTED_SUBSCRIPTIONTYPE = "supportedSubscriptionTypes";

    @Inject
    Logger logger;

    @Inject
    private ReadPMICConfigurationLocal configuration;

    @Inject
    private PmCapabilityModelService pmCapabilitiesLookup;

    /**
     * Gets pib config param info.
     *
     * @param pibParam
     *         the pib param
     *
     * @return the pib config param info
     * @throws RetryServiceException
     *         the pmic invalid input exception
     */
    @GET
    @Path("/{pibParam}")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getPIBConfigParamInfo(@PathParam("pibParam") final String pibParam) throws RetryServiceException {
        final ResponseData response = new ResponseData();
        try {
            logger.debug("Getting pibParam {}.", pibParam);
            final String pibConfigParamValue = configuration.getConfigParamValue(pibParam);
            logger.debug("PMIC Configuration parameter value {}.", pibConfigParamValue);
            return Response.status(Response.Status.OK).entity(pibConfigParamValue).build();
        } catch (final ConfigurationParameterException e) {
            handleException(e, String.format(PMIC_PARAMETER_ERROR, pibParam), Response.Status.BAD_REQUEST, response);
        } catch (final SecurityViolationException securityViolationException) {
            handleException(securityViolationException, NO_PERMISSION_ACCESS, Response.Status.FORBIDDEN, response);
        } catch (final Exception exception) {
            handleException(exception, String.format(PMIC_PARAMETER_ERROR, pibParam), Response.Status.INTERNAL_SERVER_ERROR, response);
        }
        return Response.status(response.getCode()).entity(response).build();
    }

    private void handleException(final Exception exception, final String message, final Status status, final ResponseData response) {
        logger.error(exception.getMessage(), exception);
        response.setCode(status);
        response.setError(message);
    }

    /**
     * Get method for fetching Map of node type and subscription type ex. nodeType = RNC, supportedStatsSubscriptions = [STANDARD, CELL_CELL_RELATION,
     * F4/F5/CPS]
     *
     * @return return Map of Node type and supported SubscriptionType
     */
    @GET
    @Path("/supportedSubscriptions")
    @Produces({MediaType.APPLICATION_JSON})
    @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    public Response getSupportedSubscriptions() {
        final Object supportedMOClasses = pmCapabilitiesLookup.getSupportedCapabilitiesWithTargetType(SUPPORTED_SUBSCRIPTIONTYPE);
        logger.info("supportedSubscriptionType PMCapability : {}", supportedMOClasses);
        return Response.status(Response.Status.OK).entity(supportedMOClasses).build();
    }

    /**
     * Gets a Map of {@link Map}<{@link String, List<@link String>}> which represent the Map of NodeType and List of other allowed NodeTypes list
     * for provided SubscriptionType
     *
     * @param subscriptionType
     *         - type of the subscription.
     *
     * @return return Map of Node type and other supported NodeType allowed for given SubscriptionType
     */
    @GET
    @Path("/supportedOtherNodeTypes")
    @Produces({MediaType.APPLICATION_JSON})
    @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    public Response getNodeTypeAndSubscriptionSupportedOtherNodeTypes(@QueryParam("subscriptionType") final String subscriptionType) {
        final Object nodeTypeAndSubscriptionSupportedOtherNodeTypes = pmCapabilitiesLookup.getNodeTypeAndSubscriptionSupportedOtherNodeTypes(
                SubscriptionType.valueOf(subscriptionType));
        logger.debug("Response of the NodeType and supported OtherNodeTypes {} for subscriptionType {}",
                nodeTypeAndSubscriptionSupportedOtherNodeTypes, subscriptionType);
        return Response.status(Response.Status.OK).entity(nodeTypeAndSubscriptionSupportedOtherNodeTypes).build();
    }
}
