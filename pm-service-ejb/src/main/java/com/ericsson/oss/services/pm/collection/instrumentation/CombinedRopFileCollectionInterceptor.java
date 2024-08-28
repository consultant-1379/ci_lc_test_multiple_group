/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.collection.instrumentation;

import javax.ejb.EJBException;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.InvocationContext;

import com.ericsson.oss.itpf.sdk.recording.EventLevel;
import com.ericsson.oss.itpf.sdk.recording.SystemRecorder;
import com.ericsson.oss.services.pm.initiation.constants.PmicLogCommands;

/**
 * Interceptor for filecollection instrumentation
 */
public class CombinedRopFileCollectionInterceptor {
    @Inject
    private SystemRecorder systemRecorder;

    /**
     * This cache the exception if any thrown from method and print with method
     * name and throw the EJB Exception
     *
     * @param invctx
     *         - invocationContext
     *
     * @return object
     */
    @AroundInvoke
    public Object handleException(final InvocationContext invctx) {
        try {
            return invctx.proceed();
        } catch (final Exception ex) {
            final String methodAndException = invctx.getMethod().getName() + " : " + ex.getClass().getSimpleName();
            systemRecorder.recordEvent(PmicLogCommands.PMIC_INSTRUMENTATION_INTERCEPTOR.getDescription(), EventLevel.COARSE, this.getClass()
                            .getSimpleName(),
                    "INSTRUMENTATION EXCEPTION", methodAndException);
            throw new EJBException("Failed to execute" + invctx.getMethod().getName(), ex);
        }
    }
}
