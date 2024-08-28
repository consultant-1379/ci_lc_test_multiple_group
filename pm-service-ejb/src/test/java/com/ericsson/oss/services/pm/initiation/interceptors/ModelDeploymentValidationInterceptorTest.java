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
package com.ericsson.oss.services.pm.initiation.interceptors;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.interceptor.InvocationContext;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.initiation.custom.annotation.ModelDeploymentMetaData;
import com.ericsson.oss.services.pm.initiation.custom.annotation.ModelDeploymentValidation;
import com.ericsson.oss.services.pm.initiation.model.metadata.PMICModelDeploymentValidator;
import com.ericsson.services.pm.initiation.restful.api.MetaDataType;
import com.ericsson.services.pm.initiation.restful.api.PmMimVersionQuery;

public class ModelDeploymentValidationInterceptorTest {

    @InjectMocks
    private ModelDeploymentValidationInterceptor underTest;

    @Mock
    private PMICModelDeploymentValidator pmicModelDeploymentValidator;
    @Mock
    private InvocationContext invocationContext;
    @Mock
    private PmMimVersionQuery pmMimVersionQuery;

    @BeforeMethod
    public void setMocks() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void valid() throws Exception {
        when(invocationContext.getMethod()).thenReturn(
                ModelDeploymentValidationInterceptorTest.class.getMethod("validMethod", PmMimVersionQuery.class));
        when(invocationContext.getParameters()).thenReturn(new Object[]{pmMimVersionQuery});

        underTest.validateMimQuery(invocationContext);

        verify(pmicModelDeploymentValidator).modelDeploymentStatusValidator(pmMimVersionQuery, MetaDataType.COUNTERS);
        verify(invocationContext).proceed();
    }

    @Test(expectedExceptions = RetryServiceException.class)
    public void invalidMimVersionQuery() throws Exception {
        when(invocationContext.getMethod()).thenReturn(
                ModelDeploymentValidationInterceptorTest.class.getMethod("validMethod", PmMimVersionQuery.class));
        when(invocationContext.getParameters()).thenReturn(new Object[]{pmMimVersionQuery});
        doThrow(RetryServiceException.class).when(pmicModelDeploymentValidator).modelDeploymentStatusValidator(pmMimVersionQuery,
                MetaDataType.COUNTERS);

        underTest.validateMimQuery(invocationContext);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void requiredArgumentDoesNotExist() throws Exception {
        when(invocationContext.getMethod()).thenReturn(ModelDeploymentValidationInterceptorTest.class.getMethod("invalidMethod"));
        when(invocationContext.getParameters()).thenReturn(new Object[]{});

        underTest.validateMimQuery(invocationContext);
    }

    @ModelDeploymentValidation
    @ModelDeploymentMetaData(value = MetaDataType.COUNTERS)
    public void validMethod(final PmMimVersionQuery pmMimVersionQuery) {
        //no implementation required.
    }

    @ModelDeploymentValidation
    @ModelDeploymentMetaData(value = MetaDataType.COUNTERS)
    public void invalidMethod() {
        //no implementation required.
    }

}
