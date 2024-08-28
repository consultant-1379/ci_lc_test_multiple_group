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

package com.ericsson.oss.services.pm.common.scheduling;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;

import com.ericsson.oss.services.pm.time.TimeGenerator;

public class SchedulingServiceTest {

    private SchedulingService implementationOne;

    private SchedulingService implementationTwo;

    @Mock
    private TimerService timerService;

    @Mock
    private TimeGenerator timeGenerator;

    @Mock
    private Logger log;

    private List<Timer> timers;

    @Before
    public void setUp() {
        initSchedulingServiceImplementations();
        MockitoAnnotations.initMocks(this);
        timers = new ArrayList<>();
        Whitebox.setInternalState(implementationOne, "timerService", timerService);
        Whitebox.setInternalState(implementationTwo, "timerService", timerService);
        Whitebox.setInternalState(implementationOne, "timeGenerator", timeGenerator);
        Whitebox.setInternalState(implementationTwo, "timeGenerator", timeGenerator);
        Whitebox.setInternalState(implementationOne, "log", log);
        Whitebox.setInternalState(implementationTwo, "log", log);
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(System.currentTimeMillis());
        Mockito.when(timerService.getTimers()).thenReturn(timers);
        Mockito.when(timerService.createIntervalTimer(Matchers.any(Date.class), Matchers.anyLong(), Matchers.any(TimerConfig.class))).thenAnswer(
                new Answer<Timer>() {

                    @Override
                    public Timer answer(final InvocationOnMock invocation) {
                        final Object[] args = invocation.getArguments();
                        final TimerConfig config = (TimerConfig) args[2];
                        final Timer timer = Mockito.mock(Timer.class);
                        Mockito.stub(timer.getInfo()).toReturn(config.getInfo());
                        timers.add(timer);
                        return timer;
                    }
                });
    }

    @After
    public void clean() {
        timers.clear();
    }

    @Test
    public void createIntervalTimerShouldCreateATimerInTimerService() throws CreateSchedulingServiceTimerException {
        final Serializable info = implementationOne.createIntervalTimer(1);
        Mockito.verify(timerService).createIntervalTimer(Matchers.any(Date.class), Matchers.anyLong(), Matchers.any(TimerConfig.class));
        Assert.assertTrue(info instanceof IntervalTimerInfo);
        Assert.assertEquals(1, ((IntervalTimerInfo) info).getTimerIntervalMilliSec());
        Assert.assertEquals(((IntervalTimerInfo) info).getTimerName(), "One");
    }

    @Test
    public void shouldBeAbleToCreateTwoIntervalTimersWithDifferentImplementationAndSameInterval() throws CreateSchedulingServiceTimerException {
        final Serializable info1 = implementationOne.createIntervalTimer(1);
        final Serializable info2 = implementationTwo.createIntervalTimer(1);
        Mockito.verify(timerService, Mockito.times(2)).createIntervalTimer(Matchers.any(Date.class), Matchers.anyLong(),
                Matchers.any(TimerConfig.class));
        Assert.assertTrue(info1 instanceof IntervalTimerInfo);
        Assert.assertEquals(1, ((IntervalTimerInfo) info1).getTimerIntervalMilliSec());
        Assert.assertEquals(((IntervalTimerInfo) info1).getTimerName(), "One");
        Assert.assertTrue(info2 instanceof IntervalTimerInfo);
        Assert.assertEquals(1, ((IntervalTimerInfo) info2).getTimerIntervalMilliSec());
        Assert.assertEquals(((IntervalTimerInfo) info2).getTimerName(), "Two");
    }

    @Test
    public void cancelTimerShouldCallCancelOnTimerObjectWithThatIntervalAndImplementation() throws CreateSchedulingServiceTimerException {
        implementationOne.createIntervalTimer(1);
        implementationOne.createIntervalTimer(2);
        implementationOne.createIntervalTimer(3);
        implementationOne.cancelIntervalTimer(3);
        Mockito.verify(timers.get(2)).cancel();
    }

    @Test
    public void getIntervalTimerShouldReturnTheTimerWithTheSpecificImplementation() throws CreateSchedulingServiceTimerException {
        implementationOne.createIntervalTimer(1);
        implementationOne.createIntervalTimer(2);
        implementationOne.createIntervalTimer(3);
        final Timer timer = implementationOne.getIntervalTimer(3);
        Assert.assertEquals(((IntervalTimerInfo) timer.getInfo()).getTimerName(), "One");
        Assert.assertEquals(((IntervalTimerInfo) timer.getInfo()).getTimerIntervalMilliSec(), 3);
    }

    @Test
    public void resetTimerShouldCancelTheOldTimerAndCreateANewOne() throws CreateSchedulingServiceTimerException {
        implementationOne.createIntervalTimer(1);
        implementationOne.createIntervalTimer(2);
        implementationOne.createIntervalTimer(3);
        implementationOne.resetIntervalTimer(3, 4);
        Mockito.verify(timers.get(2)).cancel();
        final Timer result = timers.get(3);
        Assert.assertEquals(((IntervalTimerInfo) result.getInfo()).getTimerName(), "One");
        Assert.assertEquals(((IntervalTimerInfo) result.getInfo()).getTimerIntervalMilliSec(), 4);
    }

    private void initSchedulingServiceImplementations() {
        implementationOne = new SchedulingService() {
            @Override
            public void onTimeout() {
                // Test, no implementation needed
            }

            @Override
            public String getTimerName() {
                return "One";
            }
        };
        implementationTwo = new SchedulingService() {
            @Override
            public void onTimeout() {
                // Test, no implementation needed
            }

            @Override
            public String getTimerName() {
                return "Two";
            }
        };
    }

}
