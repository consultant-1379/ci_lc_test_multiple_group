/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.license;

import java.util.List;
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

import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * Represents a {@link License} resource that exposes information about ENM licenses.
 */
@Path("/")
public class LicenseResource {

    @Inject
    private Logger logger;

    @Inject
    private LicenseChecker licenseChecker;

    /**
     * Verify the license name.
     *
     * @param licenseName
     *         The license name (i.e., 'FAT1023459').
     *
     * @return The response containing information about the license, where the most relevant data is the property 'allowed', that
     * indicates if the license name is allowed or not.
     * {@link com.ericsson.oss.services.pm.initiation.common.exceptionmapper.GlobalExceptionMapper}.
     * @throws ValidationException
     *         - ValidationException
     */
    @GET
    @Path("/license/{licenseName}")
    public Response verifyLicenseName(@PathParam("licenseName") final String licenseName) throws ValidationException {
        try {
            final License license = licenseChecker.verify(licenseName);
            logger.debug("License name '{}' is '{}'.", licenseName, license);
            return Response.status(Status.OK).entity(license.toMap()).build();

        } catch (final IllegalArgumentException exception) {
            logger.info("License name '{}' is invalid.", licenseName, exception);
            throw new ValidationException("The license name '" + licenseName + "' is invalid.");
        }
    }

    /**
     * Verify the license names.
     *
     * @param licenseNames
     *         The license name list (i.e., ['FAT1023459','FAT1023459'].
     *
     * @return The response is Map containing information about the license with license Name is key and License Information as Value,
     * where the most relevant data in value is the property 'allowed', that indicates if the license name is allowed or not.
     */
    @GET
    @Path("/licenses")
    @Produces({MediaType.APPLICATION_JSON})
    public Response verifyLicenseNames(@QueryParam("name") final List<String> licenseNames) {
        final Map<String, Map<String, Object>> licenses = licenseChecker.verify(licenseNames);
        logger.debug("License names '{}' is '{}'.", licenseNames, licenses);
        return Response.status(Status.OK).entity(licenses).build();
    }
}
