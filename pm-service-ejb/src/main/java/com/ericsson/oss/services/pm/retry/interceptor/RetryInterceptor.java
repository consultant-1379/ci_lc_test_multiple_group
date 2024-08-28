/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.retry.interceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import com.ericsson.oss.itpf.sdk.core.retry.RetriableCommand;
import com.ericsson.oss.itpf.sdk.core.retry.RetriableCommandException;
import com.ericsson.oss.itpf.sdk.core.retry.RetryContext;
import com.ericsson.oss.itpf.sdk.core.retry.RetryManager;
import com.ericsson.oss.itpf.sdk.core.retry.RetryPolicy;
import com.ericsson.oss.itpf.sdk.core.retry.classic.RetryManagerNonCDIImpl;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import com.ericsson.oss.services.pm.exception.DpsUnavailableException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.ServiceException;

/**
 * Adds a retry mechanism to methods annotated with {@link RetryOnNewTransaction}.
 *
 * @see RetryOnNewTransaction
 */
@Interceptor
@RetryOnNewTransaction
public class RetryInterceptor {

    private final RetryManager retryManager = new RetryManagerNonCDIImpl();
    @Inject
    private InvokableInNewTransaction invokableInNewTransaction;

    /**
     * Interception.
     *
     * @param methodContext
     *         - methodContext
     *
     * @return Object
     * @throws ServiceException
     *         ServiceException
     * @throws DpsUnavailableException
     *         DPS is unavailable
     */
    @AroundInvoke
    public Object execute(final InvocationContext methodContext) throws ServiceException {
        final RetryPolicy retryPolicy = getRetryPolicy(methodContext);
        try {
            final Boolean writeAccess = methodContext.getMethod().getAnnotation(ReadOnly.class) == null;
            return retryManager.executeCommand(retryPolicy, new RetriableCommand<Object>() {
                @Override
                public Object execute(final RetryContext retryContext) throws Exception {
                    return writeAccess ? invokableInNewTransaction.invoke(methodContext) :
                                         invokableInNewTransaction.setWriteAccessAndInvoke(methodContext, writeAccess);
                }
            });
        } catch (final RetriableCommandException exception) {
            final ServiceException pmicException = getBaseCauseServiceException(exception);
            if (pmicException == null) {
                final DpsUnavailableException dpsUnavailableException = getBaseCauseDpsUnavailableException(exception);
                if (dpsUnavailableException != null) {
                    throw new RetryServiceException(dpsUnavailableException.getMessage(), dpsUnavailableException);
                } else {
                    throw new RetryServiceException("Unexpected exception", exception);
                }
            }
            throw pmicException;
        }
    }

    private DpsUnavailableException getBaseCauseDpsUnavailableException(final RetriableCommandException exception) {
        Exception cause = new Exception(exception);
        while (cause.getCause() != null) {
            cause = (Exception) cause.getCause();
        }
        if (cause instanceof DpsUnavailableException) {
            return (DpsUnavailableException) cause;
        }
        return null;
    }

    private ServiceException getBaseCauseServiceException(final RetriableCommandException exception) {
        Exception cause = new Exception(exception);
        while (cause.getCause() != null) {
            cause = (Exception) cause.getCause();
        }
        if (cause instanceof ServiceException) {
            return (ServiceException) cause;
        }
        return null;
    }

    private RetryPolicy getRetryPolicy(final InvocationContext methodContext) {
        final RetryOnNewTransaction annotation = getAnnotation(RetryOnNewTransaction.class, methodContext.getMethod());
        return annotation != null ? RetryPolicy.builder().attempts(annotation.attempts()).waitInterval(annotation.waitIntervalInMs(), TimeUnit.MILLISECONDS)
                .exponentialBackoff(annotation.exponentialBackoff()).retryOn(annotation.retryOn()).build() : null;
    }

    private RetryOnNewTransaction getAnnotation(final Class<RetryOnNewTransaction> annotationClass, final Method method) {
        final RetryOnNewTransaction annotation = findAnnotation(annotationClass, method.getAnnotations());
        return annotation != null ? annotation : findAnnotation(annotationClass, method.getDeclaringClass().getAnnotations());
    }

    private RetryOnNewTransaction findAnnotation(final Class<RetryOnNewTransaction> annotationClass, final Annotation[] annotations) {
        for (final Annotation annotation : annotations) {
            if (annotation.annotationType().isAssignableFrom(annotationClass)) {
                return (RetryOnNewTransaction) annotation;
            }
        }
        return null;
    }
}
