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

package com.ericsson.oss.services.pm.scheduling.cluster;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.ericsson.oss.itpf.sdk.cluster.MembershipChangeEvent;
import com.ericsson.oss.itpf.sdk.recording.SystemRecorder;
import com.ericsson.oss.services.pm.common.startup.ClusterMembershipChangeAction;

public class MembershipListenerTest {

    @InjectMocks
    MembershipListener listener = new ClusterMembershipListener();

    @Mock
    MembershipChangeEvent event;

    @Mock
    SystemRecorder systemRecorder;

    @Mock
    ClusterMembershipChangeAction clusterMembershipChangeAction;

    @BeforeTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldSetThisInstanceToMaster() {
        when(event.isMaster()).thenReturn(true);
        listener.listenForMembershipChange(event);
        assertEquals(true, listener.isMaster());
    }

    @Test
    public void shouldSetThisInstanceToSlave() {
        when(event.isMaster()).thenReturn(false);
        listener.listenForMembershipChange(event);
        assertEquals(false, listener.isMaster());
    }
}
