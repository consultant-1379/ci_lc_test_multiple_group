
/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.common.exceptionmapper;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.sdk.security.accesscontrol.SecurityViolationException;
import com.ericsson.oss.services.pm.initiation.common.ResponseData;
import com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages;

/**
 * Security violation exception mapper, creates response out of exception.
 */
@Provider
public class SecurityViolationExceptionMapper implements ExceptionMapper<SecurityViolationException> {

    private static final Logger logger = LoggerFactory.getLogger(SecurityViolationExceptionMapper.class);

    @Override
    public Response toResponse(final SecurityViolationException exception) {
        logger.error(exception.getMessage(), exception);
        return Response.status(Response.Status.FORBIDDEN)
                .entity(new ResponseData(Response.Status.FORBIDDEN, ApplicationMessages.NO_PERMISSION_ACCESS)).build();
    }
}
