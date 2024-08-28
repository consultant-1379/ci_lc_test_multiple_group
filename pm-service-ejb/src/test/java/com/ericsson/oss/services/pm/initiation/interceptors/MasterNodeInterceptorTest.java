/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2017
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */
package com.ericsson.oss.services.pm.initiation.interceptors;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import javax.interceptor.InvocationContext;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

public class MasterNodeInterceptorTest {
    @InjectMocks
    private MasterNodeInterceptor underTest;

    @Mock
    private Logger logger;

    @Mock
    private MembershipListener membershipListener;

    @Mock
    private InvocationContext invocationContext;

    @Before
    public void setMocks() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void masterNode() throws Exception {
        when(membershipListener.isMaster()).thenReturn(true);

        underTest.ensureIsMasterNode(invocationContext);

        verify(invocationContext).proceed();
    }

    @Test
    public void slaveNode() throws Exception {
        when(membershipListener.isMaster()).thenReturn(false);

        underTest.ensureIsMasterNode(invocationContext);

        verify(invocationContext, never()).proceed();
    }

}
