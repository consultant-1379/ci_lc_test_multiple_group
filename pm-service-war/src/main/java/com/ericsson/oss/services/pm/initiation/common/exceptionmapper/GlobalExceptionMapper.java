
/*
 * COPYRIGHT Ericsson 2017
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.common.exceptionmapper;

import javax.ejb.EJBException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.sdk.security.accesscontrol.SecurityViolationException;
import com.ericsson.oss.services.pm.exception.ObjectNotFoundDataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.ServiceException;
import com.ericsson.oss.services.pm.initiation.common.ResponseData;
import com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages;
import com.ericsson.oss.services.pm.services.exception.CannotGetConflictingCountersException;
import com.ericsson.oss.services.pm.services.exception.ConfigurationParameterException;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.PmFunctionValidationException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * Global exception mapper to generate response from exception.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(final Throwable throwable) {
        logger.error(throwable.getMessage());
        logger.info(throwable.getMessage(), throwable);
        if (throwable instanceof EJBException) {
            return toEjbExceptionResponse((EJBException) throwable);
        }
        if (throwable instanceof PmFunctionValidationException) {
            return toPmFunctionValidationExceptionResponse(throwable);
        }
        if (throwable instanceof RetryServiceException
                || throwable instanceof CannotGetConflictingCountersException
                || throwable instanceof ConfigurationParameterException
                || throwable instanceof PfmDataException
                || throwable instanceof ValidationException) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new ResponseData(Response.Status.BAD_REQUEST, throwable.getMessage())).build();
        }
        if (throwable instanceof ServiceException) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(new ResponseData(Response.Status.SERVICE_UNAVAILABLE, throwable.getMessage())).build();
        }
        return Response.serverError().entity(new ResponseData(Response.Status.INTERNAL_SERVER_ERROR, throwable.getMessage())).build();
    }

    private Response toEjbExceptionResponse(final EJBException throwable) {
        final EJBException ejbExceptionThrowable = throwable;
        if (ejbExceptionThrowable.getCausedByException() instanceof SecurityViolationException) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ResponseData(Response.Status.FORBIDDEN, ApplicationMessages.NO_PERMISSION_ACCESS)).build();
        } else if (ejbExceptionThrowable.getCausedByException() instanceof ObjectNotFoundDataAccessException) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ResponseData(Response.Status.NOT_FOUND, ejbExceptionThrowable.getCausedByException().getMessage())).build();
        } else if (ejbExceptionThrowable.getCausedByException() instanceof ServiceException) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(new ResponseData(Response.Status.SERVICE_UNAVAILABLE, ejbExceptionThrowable.getCausedByException().getMessage()))
                    .build();
        } else if (ejbExceptionThrowable.getCausedByException() instanceof ValidationException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ResponseData(Response.Status.BAD_REQUEST, ejbExceptionThrowable.getCausedByException().getMessage())).build();
        }
        return Response.serverError()
                .entity(new ResponseData(Response.Status.INTERNAL_SERVER_ERROR, ejbExceptionThrowable.getCausedByException().getMessage()))
                .build();
    }

    private Response toPmFunctionValidationExceptionResponse(final Throwable throwable) {
        return Response.status(Response.Status.BAD_REQUEST).entity(new ResponseData(Response.Status.BAD_REQUEST, throwable.getMessage(),
                ((PmFunctionValidationException) throwable).getInvalidNodes())).build();
    }
}
