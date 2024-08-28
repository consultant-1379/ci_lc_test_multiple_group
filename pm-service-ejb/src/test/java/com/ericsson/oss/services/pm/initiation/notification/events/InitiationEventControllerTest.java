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

package com.ericsson.oss.services.pm.initiation.notification.events;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Calendar;
import java.util.Date;
import javax.ejb.ScheduleExpression;
import javax.ejb.Timer;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.ScheduleInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.services.pm.initiation.notification.model.InitiationScheduleModel;
import com.ericsson.oss.services.pm.scheduling.api.SchedulerServiceInitiationLocal;

public class InitiationEventControllerTest {

    @Mock
    public Logger logger;
    InitiationEventController controller;
    @Mock
    private SchedulerServiceInitiationLocal initiationSchedulerServiceBean;
    @Mock
    @Deactivate
    private InitiationEvent deactivationEvent;
    private Subscription subscription;

    @Before
    public void setMocks()  {
        subscription = mock(Subscription.class);
        MockitoAnnotations.initMocks(this);
        controller = spy(new InitiationEventController());

        Whitebox.setInternalState(controller, "logger", logger);
        Whitebox.setInternalState(controller, "deactivationEvent", deactivationEvent);
        Whitebox.setInternalState(controller, "initiationSchedulerServiceBean", initiationSchedulerServiceBean);
    }

    @Test
    public void processEventShouldCallCreateTimerWhenScheduleInfoStartTimeIsSpecifiedAndEndTimeIsNull()  {
        final Date startTime = new Date();
        final ScheduleInfo scheduleInfo = new ScheduleInfo();
        final Calendar cal = Calendar.getInstance();
        cal.setTime(startTime);
        cal.add(Calendar.HOUR, 1);
        scheduleInfo.setStartDateTime(cal.getTime());
        when(subscription.getScheduleInfo()).thenReturn(scheduleInfo);
        when(subscription.getAdministrationState()).thenReturn(AdministrationState.ACTIVATING);
        controller.processEvent(123l, subscription);
        verify(initiationSchedulerServiceBean, times(1)).createTimer(any(InitiationScheduleModel.class), any(ScheduleExpression.class),
                any(Boolean.class));
    }

    @Test
    public void processEventShouldCallCreateTimerWithCurrentDateWhenScheduleInfoStartTimeIsNotSpecified() {
        final ScheduleInfo scheduleInfo = new ScheduleInfo();
        when(subscription.getScheduleInfo()).thenReturn(scheduleInfo);
        when(subscription.getAdministrationState()).thenReturn(AdministrationState.ACTIVATING);
        controller.processEvent(123l, subscription);
        verify(initiationSchedulerServiceBean, times(1)).createTimer(any(InitiationScheduleModel.class), any(ScheduleExpression.class),
                any(Boolean.class));
    }

    @Test
    public void processEventShouldCallCreateTimerWithCurrentDateWhenScheduleInfoIsNull() {
        when(subscription.getScheduleInfo()).thenReturn(null);
        when(subscription.getAdministrationState()).thenReturn(AdministrationState.ACTIVATING);
        controller.processEvent(123l, subscription);
        verify(initiationSchedulerServiceBean, times(1)).createTimer(any(InitiationScheduleModel.class), any(ScheduleExpression.class),
                any(Boolean.class));
    }

    @Test
    public void processEventShouldNotCallCreateTimerWhenAdminStateIsActive() {
        when(subscription.getScheduleInfo()).thenReturn(null);
        when(subscription.getAdministrationState()).thenReturn(AdministrationState.ACTIVE);
        controller.processEvent(123l, subscription);
        verify(initiationSchedulerServiceBean, times(0)).createTimer(any(InitiationScheduleModel.class), any(ScheduleExpression.class),
                any(Boolean.class));
    }

    @Test
    public void processEventShouldCancelTimerWhenAdminStateIsInactive() {
        final Timer activationTimer = mock(Timer.class);
        final Timer deactivationTimer = mock(Timer.class);
        when(initiationSchedulerServiceBean.getTimer(123l, AdministrationState.ACTIVATING)).thenReturn(activationTimer);
        when(initiationSchedulerServiceBean.getTimer(123l, AdministrationState.DEACTIVATING)).thenReturn(deactivationTimer);
        when(subscription.getAdministrationState()).thenReturn(AdministrationState.INACTIVE);
        controller.processEvent(123l, subscription);
        verify(activationTimer, times(1)).cancel();
        verify(deactivationTimer, times(1)).cancel();
        verify(deactivationEvent, times(0)).execute(123l);
    }

    @Test
    public void processEventShouldNotCancelTimerWhenTimerNull() {
        final Timer timer = mock(Timer.class);
        when(initiationSchedulerServiceBean.getTimer(123l, AdministrationState.ACTIVATING)).thenReturn(null);
        when(subscription.getAdministrationState()).thenReturn(AdministrationState.INACTIVE);
        controller.processEvent(123l, subscription);
        verify(timer, times(0)).cancel();
        verify(deactivationEvent, times(0)).execute(123l);
    }

    @Test
    public void processEventShouldCallCreateTimerWhenEndTimeIsSpecified() {
        final Date startTime = new Date();
        final ScheduleInfo scheduleInfo = new ScheduleInfo();
        final Calendar cal = Calendar.getInstance();
        cal.setTime(startTime);
        cal.add(Calendar.HOUR, 1);
        scheduleInfo.setStartDateTime(cal.getTime());
        cal.add(Calendar.HOUR, 1);
        scheduleInfo.setEndDateTime(cal.getTime());
        when(subscription.getScheduleInfo()).thenReturn(scheduleInfo);

        when(subscription.getAdministrationState()).thenReturn(AdministrationState.ACTIVATING);
        controller.processEvent(123l, subscription);
        verify(initiationSchedulerServiceBean, times(2)).createTimer(any(InitiationScheduleModel.class), any(ScheduleExpression.class),
                any(Boolean.class));
    }

    @Test
    public void processEvent2ShouldCreateTimerForBothStartAndEndWhenSubscriptionStatusIsScheduled() {
        final Subscription sub = new StatisticalSubscription();
        sub.setAdministrationState(AdministrationState.SCHEDULED);
        final ScheduleInfo scheduleInfo = new ScheduleInfo();
        sub.setScheduleInfo(scheduleInfo);
        sub.setId(123L);

        final Calendar startCal = Calendar.getInstance();
        startCal.setTime(new Date());
        startCal.set(Calendar.HOUR, startCal.get(Calendar.HOUR) + 1);

        final Calendar endCal = Calendar.getInstance();
        endCal.setTime(new Date());
        endCal.set(Calendar.HOUR, endCal.get(Calendar.HOUR) + 2);

        sub.getScheduleInfo().setEndDateTime(endCal.getTime());
        sub.getScheduleInfo().setStartDateTime(startCal.getTime());

        controller.processEvent(sub);
        verify(initiationSchedulerServiceBean, times(2)).createTimer(any(InitiationScheduleModel.class), any(ScheduleExpression.class),
                any(Boolean.class));
    }

    @Test
    public void processEvent2ShouldCreateTimerOnlyForStartAndEndTimeIsNullWhenSubscriptionStatusIsScheduled() {
        final Subscription sub = new StatisticalSubscription();
        sub.setAdministrationState(AdministrationState.ACTIVATING);
        final ScheduleInfo scheduleInfo = new ScheduleInfo();
        sub.setScheduleInfo(scheduleInfo);
        sub.setId(123L);

        final Calendar startCal = Calendar.getInstance();
        startCal.setTime(new Date());
        startCal.set(Calendar.HOUR, startCal.get(Calendar.HOUR) + 1);

        sub.getScheduleInfo().setEndDateTime(null);
        sub.getScheduleInfo().setStartDateTime(startCal.getTime());

        controller.processEvent(sub);
        verify(initiationSchedulerServiceBean, times(1)).createTimer(any(InitiationScheduleModel.class), any(ScheduleExpression.class),
                any(Boolean.class));
    }

    @Test
    public void processEvent2ShouldCreateTimerOnlyForEndWhenSubscriptionStatusIsRunningWithSchedule() {
        final Subscription sub = new StatisticalSubscription();
        sub.setAdministrationState(AdministrationState.ACTIVATING);
        final ScheduleInfo scheduleInfo = new ScheduleInfo();
        sub.setScheduleInfo(scheduleInfo);
        sub.setId(123L);

        final Calendar startCal = Calendar.getInstance();
        startCal.setTime(new Date());
        startCal.set(Calendar.HOUR, startCal.get(Calendar.HOUR) - 2);

        final Calendar endCal = Calendar.getInstance();
        endCal.setTime(new Date());
        endCal.set(Calendar.HOUR, endCal.get(Calendar.HOUR) - 1);

        sub.getScheduleInfo().setEndDateTime(endCal.getTime());
        sub.getScheduleInfo().setStartDateTime(null);

        sub.setActivationTime(startCal.getTime());
        startCal.set(Calendar.HOUR, startCal.get(Calendar.HOUR) - 3);
        sub.setDeactivationTime(startCal.getTime());

        controller.processEvent(sub);
        verify(initiationSchedulerServiceBean, times(1)).createTimer(any(InitiationScheduleModel.class), any(ScheduleExpression.class),
                any(Boolean.class));
    }
}
