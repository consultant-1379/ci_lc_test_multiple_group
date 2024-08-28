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

package com.ericsson.oss.services.pm.initiation.interceptors;

import java.lang.reflect.Method;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import com.ericsson.oss.services.pm.initiation.custom.annotation.ModelDeploymentMetaData;
import com.ericsson.oss.services.pm.initiation.custom.annotation.ModelDeploymentValidation;
import com.ericsson.oss.services.pm.initiation.model.metadata.PMICModelDeploymentValidator;
import com.ericsson.services.pm.initiation.restful.api.MetaDataType;
import com.ericsson.services.pm.initiation.restful.api.PmMimVersionQuery;

/**
 * The Model deployment validation interceptor.
 */
@ModelDeploymentValidation
@Interceptor
public class ModelDeploymentValidationInterceptor {

    @Inject
    private PMICModelDeploymentValidator validator;

    /**
     * Validate mim query object.
     *
     * @param ctx
     *         the invocation context
     *
     * @return returns the return value from ctx.proceed()
     * @throws Exception
     *         the generic exception that is thrown by ctx.proceed()
     */
    @AroundInvoke
    public Object validateMimQuery(final InvocationContext ctx) throws Exception {
        // This is standard method declaration for intercept method and hence we are suppressing PMD
        final Method method = ctx.getMethod();
        boolean mimVersionQueryPassed = false;
        MetaDataType dataValue = null;
        if (method.isAnnotationPresent(ModelDeploymentMetaData.class)) {
            final ModelDeploymentMetaData modelAnnotation = method.getAnnotation(ModelDeploymentMetaData.class);
            dataValue = modelAnnotation.value();
        }
        for (final Object parameter : ctx.getParameters()) {
            if (parameter instanceof PmMimVersionQuery) {
                validator.validateQuery((PmMimVersionQuery) parameter);
                if (dataValue != null) {
                    validator.modelDeploymentStatusValidator((PmMimVersionQuery) parameter, dataValue);
                }
                mimVersionQueryPassed = true;
            }
        }

        if (!mimVersionQueryPassed) {
            throw new IllegalArgumentException("No mim version query passed to method");
        }
        return ctx.proceed();
    }
}
