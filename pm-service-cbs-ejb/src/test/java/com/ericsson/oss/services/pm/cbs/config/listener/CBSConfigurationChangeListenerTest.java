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

package com.ericsson.oss.services.pm.cbs.config.listener;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ericsson.oss.services.pm.cbs.scheduler.CBSAuditScheduler;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.common.scheduling.CreateSchedulingServiceTimerException;

public class CBSConfigurationChangeListenerTest {

    CBSConfigurationChangeListener initiator;

    @Mock
    private CBSAuditScheduler cbsAuditScheduler;

    @Mock
    private SystemRecorderWrapperLocal systemRecorder;

    @BeforeMethod
    public void setUp() {
        initiator = new CBSConfigurationChangeListener();
        MockitoAnnotations.initMocks(this);
        Whitebox.setInternalState(initiator, "systemRecorder", systemRecorder);
        Whitebox.setInternalState(initiator, "cbsAuditScheduler", cbsAuditScheduler);
    }

    @Test
    public void testListenForCbsAuditScheduleInterval() throws CreateSchedulingServiceTimerException {
        initiator.listenForCbsAuditScheduleInterval(50);
        final long oldvalue = initiator.getCbsScheduleInterval();
        initiator.listenForCbsAuditScheduleInterval(100);
        Assert.assertEquals(initiator.getCbsScheduleInterval(), 100);
        Assert.assertNotEquals(oldvalue, initiator.getCbsScheduleInterval());
        verify(cbsAuditScheduler, times(2)).resetIntervalTimer(any(Long.class), any(Long.class), any(Long.class));
    }

    @Test
    public void testlistenForcbsEnableFlag() {
        initiator.listenForcbsEnableFlag(false);
        Assert.assertEquals(initiator.isPeriodicCbsAudit(), false);
        final boolean oldvalue = initiator.isPeriodicCbsAudit();
        initiator.listenForcbsEnableFlag(true);
        Assert.assertEquals(initiator.isPeriodicCbsAudit(), true);
        Assert.assertNotEquals(oldvalue, initiator.isPeriodicCbsAudit());
    }

    @Test
    public void testlistenForCbsAuditDelayInterval() throws CreateSchedulingServiceTimerException {
        initiator.listenForCbsAuditDelayInterval(120);
        Assert.assertEquals(initiator.getCbsAuditDelayInterval(), 120);
        final long oldValue = initiator.getCbsAuditDelayInterval();
        initiator.listenForCbsAuditDelayInterval(200);
        Assert.assertEquals(initiator.getCbsAuditDelayInterval(), 200);
        Assert.assertNotEquals(oldValue, initiator.getCbsAuditDelayInterval());
        verify(cbsAuditScheduler, times(2)).resetIntervalTimer(any(Long.class), any(Long.class), any(Long.class));
    }

}
