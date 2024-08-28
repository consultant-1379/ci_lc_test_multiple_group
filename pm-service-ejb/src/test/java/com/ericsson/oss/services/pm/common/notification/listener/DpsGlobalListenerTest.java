/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2019
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.common.notification.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsConnectionEvent;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsDataBucketEvent;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectEvent;
import com.ericsson.oss.services.pm.common.notification.router.EventRouter;

public class DpsGlobalListenerTest {

    private DpsGlobalListener dpsGlobalListener;

    private EventRouter eventRouter;

    private DpsObjectEvent dpsObjectEvent;

    private DpsConnectionEvent dpsConnectionEvent;

    private DpsDataBucketEvent dpsDataBucketEvent;

    @Before
    public void setUp() {
        eventRouter = mock(EventRouter.class);
        dpsObjectEvent = mock(DpsObjectEvent.class);
        dpsConnectionEvent = mock(DpsConnectionEvent.class);
        dpsDataBucketEvent = mock(DpsDataBucketEvent.class);
        dpsGlobalListener = new DpsGlobalListener();
        dpsGlobalListener.init(eventRouter);
    }

    @Test
    public void dpsGlobalListenerOnEventForDpsObjectEventShouldUseTheEventRouter() {
        dpsGlobalListener.processSubscriptionNotification(dpsObjectEvent);
        verify(eventRouter, times(1)).route(dpsObjectEvent);
    }

    @Test
    public void dpsGlobalListenerOnEventForDpsConnectionEventShouldUseTheEventRouter() {
        dpsGlobalListener.onEvent(dpsConnectionEvent);
        verify(eventRouter, times(1)).route(dpsConnectionEvent);
    }

    @Test
    public void dpsGlobalListenerOnEventForDpsDataBucketEventShouldUseTheEventRouter() {
        dpsGlobalListener.processSubscriptionNotification(dpsDataBucketEvent);
        verify(eventRouter, times(1)).route(dpsDataBucketEvent);
    }

    @Test
    public void dpsGlobalListenerProcessNodeReconnectNotificationForDpsDataBucketEventShouldUseTheEventRouter() {
        dpsGlobalListener.processNodeReconnectNotification(dpsDataBucketEvent);
        verify(eventRouter, times(1)).route(dpsDataBucketEvent);
    }

    @Test
    public void dpsGlobalListenerProcessPmFunctionNotificationForDpsDataBucketEventShouldUseTheEventRouter() {
        dpsGlobalListener.processPmFunctionNotification(dpsDataBucketEvent);
        verify(eventRouter, times(1)).route(dpsDataBucketEvent);
    }

    @Test
    public void dpsGlobalListenerProcessPmicJobInfoNotificationForDpsDataBucketEventShouldUseTheEventRouter() {
        dpsGlobalListener.processPmicJobInfoNotification(dpsDataBucketEvent);
        verify(eventRouter, times(1)).route(dpsDataBucketEvent);
    }

    @Test
    public void dpsGlobalListenerProcessPmScannerNotificationForDpsDataBucketEventShouldUseTheEventRouter() {
        dpsGlobalListener.processPmScannerNotification(dpsDataBucketEvent);
        verify(eventRouter, times(1)).route(dpsDataBucketEvent);
    }

    @Test
    public void dpsGlobalListenerProcessPmSubScannerNotificationForDpsDataBucketEventShouldUseTheEventRouter() {
        dpsGlobalListener.processPmSubScannerNotification(dpsDataBucketEvent);
        verify(eventRouter, times(1)).route(dpsDataBucketEvent);
    }

    @Test
    public void dpsGlobalListenerProcessPmUeScannerNotificationForDpsDataBucketEventShouldUseTheEventRouter() {
        dpsGlobalListener.processPmUeScannerNotification(dpsDataBucketEvent);
        verify(eventRouter, times(1)).route(dpsDataBucketEvent);
    }

    @Test
    public void dpsGlobalListenerProcessRncFeatureNotificationForDpsDataBucketEventShouldUseTheEventRouter() {
        dpsGlobalListener.processRncFeatureNotification(dpsDataBucketEvent);
        verify(eventRouter, times(1)).route(dpsDataBucketEvent);
    }

    @Test
    public void dpsGlobalListenerProcessSubscriptionNotificationForDpsDataBucketEventShouldUseTheEventRouter() {
        dpsGlobalListener.processSubscriptionNotification(dpsDataBucketEvent);
        verify(eventRouter, times(1)).route(dpsDataBucketEvent);
    }

}
