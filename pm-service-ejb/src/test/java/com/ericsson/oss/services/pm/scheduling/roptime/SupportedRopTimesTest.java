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

package com.ericsson.oss.services.pm.scheduling.roptime;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.enterprise.event.Event;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;

import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.initiation.config.event.ConfigurationParameterUpdateEvent;

public class SupportedRopTimesTest {
    static final int LAG_ONE_MIN = 60;
    static final int LAG_FIVE_MIN = 60;
    static final int LAG_FIFTEEN_MIN = 300;
    private SupportedRopTimes objectUnderTest;
    private long roptTime15MinutesInLongAsSecond = 900;
    private long collectionDelayInSeconds = 300;
    final String[] supportedRopPeriods = { "ONE_MIN", "FIFTEEN_MIN", "ONE_DAY" };

    @Mock
    private Event<ConfigurationParameterUpdateEvent> event;

    @Mock
    private SystemRecorderWrapperLocal systemRecorder;

    @Before
    public void setup() {
        objectUnderTest = new SupportedRopTimes();
        MockitoAnnotations.initMocks(this);
        Whitebox.setInternalState(objectUnderTest, "pmicSupportedRopPeriods", supportedRopPeriods);
        Whitebox.setInternalState(objectUnderTest, "pmicLagPeriodInSecondsFor1MinROP", LAG_ONE_MIN);
        Whitebox.setInternalState(objectUnderTest, "pmicLagPeriodInSecondsFor5MinROP", LAG_FIVE_MIN);
        Whitebox.setInternalState(objectUnderTest, "pmicLagPeriodInSecondsFor15MinAndAboveROP", LAG_FIFTEEN_MIN);
        Whitebox.setInternalState(objectUnderTest, "event", event);
        Whitebox.setInternalState(objectUnderTest, "systemRecorder", systemRecorder);
        objectUnderTest.initialize();
    }

    @Test
    public void testgetRopTime15MinutesPassingStringAsARopPeriod() {
        final String roptTime15MinutesInStringAsSecond = "900";
        final RopTimeInfo ropTimeInfo = objectUnderTest.getRopTime(roptTime15MinutesInStringAsSecond);
        assertRopTimeInfo(ropTimeInfo);
    }

    @Test
    public void testgetRopTime15MinutesPassingLongAsARopPeriod() {
        final RopTimeInfo ropTimeInfo = objectUnderTest.getRopTime(roptTime15MinutesInLongAsSecond);
        assertRopTimeInfo(ropTimeInfo);
    }

    @Test
    public void testgetRopTime1MinutesPassingLongAsARopPeriod() {
        roptTime15MinutesInLongAsSecond = 60;
        collectionDelayInSeconds = 60;
        final RopTimeInfo ropTimeInfo = objectUnderTest.getRopTime(roptTime15MinutesInLongAsSecond);
        assertRopTimeInfo(ropTimeInfo);
    }

    @Test
    public void testgetRopTime1MinutesPassingStringAsARopPeriod() {
        final String roptTime15MinutesInStringAsSecond = "60";
        roptTime15MinutesInLongAsSecond = 60;
        collectionDelayInSeconds = 60;
        final RopTimeInfo ropTimeInfo = objectUnderTest.getRopTime(roptTime15MinutesInStringAsSecond);
        assertRopTimeInfo(ropTimeInfo);
    }

    @Test
    public void testSupportedRopPIBUpdate() {
        final String[] newSupportedRopPeriods = { "ONE_MIN", "FIVE_MIN", "ONE_DAY" };
        final String[] defaultRopPeriods = { "ONE_MIN", "FIFTEEN_MIN", "ONE_DAY" };
        final Set<Long> expectedSupportedRopPeriodsDuration = new HashSet<>(Arrays.asList(60L, 300L, 86400L));
        final Set<Long> defaultSupportedRopPeriodsDuration = new HashSet<>(Arrays.asList(60L, 900L, 86400L));
        objectUnderTest.listenForSupportedRopPeriods(newSupportedRopPeriods);
        Set<Long> supportedRops = objectUnderTest.getSupportedRopTimeList();
        Assert.assertEquals(expectedSupportedRopPeriodsDuration, supportedRops);
        objectUnderTest.listenForSupportedRopPeriods(defaultRopPeriods);
        supportedRops = objectUnderTest.getSupportedRopTimeList();
        Assert.assertEquals(defaultSupportedRopPeriodsDuration, supportedRops);
    }

    private void assertRopTimeInfo(final RopTimeInfo ropTimeInfo) {
        final long collectionDelayInMilliseconds = TimeUnit.MILLISECONDS.convert(collectionDelayInSeconds, TimeUnit.SECONDS);
        final long fifteenSecondsInMiliSeconds = TimeUnit.MILLISECONDS.convert(roptTime15MinutesInLongAsSecond, TimeUnit.SECONDS);
        Assert.assertEquals(roptTime15MinutesInLongAsSecond, ropTimeInfo.getRopTimeInSeconds());
        Assert.assertEquals(fifteenSecondsInMiliSeconds, ropTimeInfo.getRopTimeInMilliSecond());
        Assert.assertEquals(roptTime15MinutesInLongAsSecond, ropTimeInfo.getRopTimeInSeconds());
        Assert.assertEquals(collectionDelayInSeconds, ropTimeInfo.getcollectionDelayInSeconds());
        Assert.assertEquals(collectionDelayInMilliseconds, ropTimeInfo.getCollectionDelayInMilliSecond());
    }
}
