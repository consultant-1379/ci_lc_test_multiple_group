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

import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.AroundTimeout;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.initiation.custom.annotation.EnsureOnMasterNode;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * The Master node interceptor.
 */
@EnsureOnMasterNode
@Interceptor
public class MasterNodeInterceptor {

    @Inject
    private Logger log;

    @Inject
    private MembershipListener membershipListener;

    /**
     * Ensure is master node object.
     *
     * @param ctx
     *         - the invocation context
     *
     * @return returns the value of ctx.proceed() or null if node is not master
     * @throws Exception
     *         - passes on the generic exception thrown by ctx.proceed()
     */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    @AroundInvoke
    @AroundTimeout
    public Object ensureIsMasterNode(final InvocationContext ctx) throws Exception {
        if (membershipListener.isMaster()) {
            return ctx.proceed();
        } else {
            log.info("Not master node, ignoring execution for method {}", ctx.getMethod());
            return null;
        }
    }

}
