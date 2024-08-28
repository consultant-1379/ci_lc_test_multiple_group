/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.READ_CAPABILITY;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.initiation.common.ResponseData;
import com.ericsson.oss.services.pm.modelservice.PmCapabilities;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.oss.services.pm.modelservice.PmGlobalCapabilities;

/**
 * PmCapabilityResource used to retrieve global capabilities and target specific capabilities for all subscription types
 */
@Path("/v1/capabilities")
public class PmCapabilityResource {

    public static final String READ_CAPABILITIES = "Read capabilities for : %s";
    public static final String SUCCESSFULLY_READ_CAPABILITIES = "Successfully read capabilities : %s";

    @Inject
    private Logger logger;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private PmCapabilityModelService pmCapabilityModelService;

    /**
     * Gets all available capabilities for the provided function regardless of their target type for given capabilityName.
     * for example.,
     * https://host:port/pm-service/v1/capabilities/default-values/functions/{function}/capabilities/{capabilityName}
     * https://host:port/pm-service/v1/capabilities/default-values/functions/{function}/capabilities/{capabilityName,capabilityName}
     * <Pre>
     * This REST endpoint will allow to retrieve a specific target parameter for all target types.
     * This REST endpoint will allow to retrieve multiple target parameter for all target types.
     * This is the default value defined in the oss_capability model, a default value needs to be defined otherwise an empty response will be
     * returned.
     * </Pre>
     *
     * @param targetFunction
     *         -  the name of the oss_capability and oss_capabilitysupport models to consider.
     * @param capabilities
     *         - one or more name of the capability in the capability model.
     *
     * @return global capabilities (a default value of the capability)
     */
    @GET
    @Path("/default-values/functions/{function}")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getGlobalCapabilities(@PathParam("function") final String targetFunction,
                                          @QueryParam("capabilities") final String capabilities) {
        logger.info("targetFunction [{}] capabilities [{}]", targetFunction, capabilities);
        if (null == capabilities) {
            final ResponseData response = new ResponseData();
            response.setCode(Response.Status.NOT_FOUND);
            response.setError("capabilities parameter missing");
            return Response.status(Response.Status.NOT_FOUND).entity(response).build();
        }
        systemRecorder.commandStarted(READ_CAPABILITY, targetFunction, READ_CAPABILITIES, targetFunction);
        return getGlobalCapabilitiesResponse(targetFunction, capabilities.split(","));
    }

    /**
     * Gets all available capabilities for the provided function for their target type for given capabilityName.
     * for example.,
     * https://host:port/pm-service/rest/v1/capabilities/target-types/functions/{function}
     * https://host:port/pm-service/rest/v1/capabilities/target-types/functions/{function}?targetTypes=targetType
     * https://host:port/pm-service/rest/v1/capabilities/target-types/functions/{function}?targetTypes=targetType,targetType
     * https://host:port/pm-service/rest/v1/capabilities/target-types/functions/{function}?capabilities=capabilityName
     * https://host:port/pm-service/rest/v1/capabilities/target-types/functions/{function}?capabilities=capabilityName,capabilityName
     * https://host:port/pm-service/rest/v1/capabilities/target-types/functions/{function}?capabilities=capabilityName&targetTypes=targetType
     * https://host:port/pm-service/rest/v1/capabilities/target-types/functions/{function}?capabilities=capabilityName,capabilityName&targetTypes=targetType
     * https://host:port/pm-service/rest/v1/capabilities/target-types/functions/{function}?capabilities=capabilityName&targetTypes=targetType,targetType
     * https://host:port/pm-service/rest/v1/capabilities/target-types/functions/{function}?capabilities=capabilityName,capabilityName&targetTyps=targetType,targetType
     * <Pre>
     * This REST endpoint will allow to retrieve a specific target parameter for a specific target type.
     * This REST endpoint will allow to retrieve multiple target parameter for a specific target types.
     * </Pre>
     *
     * @param targetFunction
     *         -  the name of the oss_capability and oss_capabilitysupport models to consider.
     * @param capabilities
     *         - one or more name of the capability in the capability model.
     * @param targetTypes
     *         - the target type. For oss_capabilitysupport models for network elements, the target type is typically the NE type.
     *
     * @return supported capability with all available capabilities for the provided function of their target type
     */
    @GET
    @Path("/target-types/functions/{function}")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getCapabilities(@PathParam("function") final String targetFunction,
                                    @QueryParam("capabilities") final String capabilities,
                                    @QueryParam("targetTypes") final String targetTypes) {
        systemRecorder.commandStarted(READ_CAPABILITY, targetFunction, READ_CAPABILITIES, targetFunction);
        final PmCapabilities pmCapabilities;
        logger.info("targetFunction [{}] capabilityName [{}] targetTypes [{}]", targetFunction, capabilities, targetTypes);
        if (null == targetTypes && null == capabilities) {
            pmCapabilities = pmCapabilityModelService.getCapabilitiesForTargetTypesByFunction(targetFunction);
        } else if (null == capabilities) {
            pmCapabilities = pmCapabilityModelService.getCapabilitiesByFunctionAndTargetType(targetFunction, targetTypes.split(","));
        } else {
            final String[] capabilityNames = capabilities.split(",");
            if (null == targetTypes) {
                pmCapabilities = pmCapabilityModelService.getCapabilitiesByFunction(targetFunction, capabilityNames);
                if (pmCapabilities.getTargetTypes().isEmpty()) {
                    return getGlobalCapabilitiesResponse(targetFunction, capabilityNames);
                }
            } else {
                pmCapabilities = pmCapabilityModelService.getCapabilityForTargetTypeByFunction(targetFunction, targetTypes, capabilityNames);
            }
        }
        systemRecorder.commandFinishedSuccess(READ_CAPABILITY, targetFunction, SUCCESSFULLY_READ_CAPABILITIES, pmCapabilities);
        return Response.status(Response.Status.OK).entity(pmCapabilities).build();
    }

    private Response getGlobalCapabilitiesResponse(@PathParam("function") final String targetFunction,
                                                   @PathParam("capabilities") final String[] capabilities) {
        final PmGlobalCapabilities pmGlobalCapabilities = pmCapabilityModelService.getGlobalCapabilitiesByFunction(targetFunction, capabilities);
        systemRecorder.commandFinishedSuccess(READ_CAPABILITY, targetFunction, SUCCESSFULLY_READ_CAPABILITIES, pmGlobalCapabilities);
        return Response.status(Response.Status.OK).entity(pmGlobalCapabilities).build();
    }
}
