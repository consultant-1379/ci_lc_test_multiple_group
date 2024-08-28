/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.model;

import static com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants.NE_DEFINED_PATTERN;
import static com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants.OSS_DEFINED_PATTERN;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;

import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo;
import com.ericsson.oss.services.pm.initiation.model.metadata.PmMetaDataLookup;
import com.ericsson.oss.services.pm.initiation.model.metadata.counters.PmCountersLookUp;
import com.ericsson.oss.services.pm.initiation.model.metadata.events.PmEventsLookUp;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import com.ericsson.services.pm.initiation.restful.api.CounterTableRow;
import com.ericsson.services.pm.initiation.restful.api.PmMimVersionQuery;

public class PmMetaDataLookupTest {

    private static final List<String> MODEL_DEFINERS_OSS = singletonList(OSS_DEFINED_PATTERN);
    private static final List<String> MODEL_DEFINERS_NE = singletonList(NE_DEFINED_PATTERN);

    PmMetaDataLookup pmMetaDataLookUp;

    @Mock
    private PmCountersLookUp pmCountersLookUp;

    @Mock
    private PmEventsLookUp pmEventsLookUp;

    @Before
    public void setUp() {
        pmMetaDataLookUp = spy(new PmMetaDataLookup());
        MockitoAnnotations.initMocks(this);
        Whitebox.setInternalState(pmMetaDataLookUp, "pmCountersLookUp",
                pmCountersLookUp);
        Whitebox.setInternalState(pmMetaDataLookUp, "pmEventsLookUp", pmEventsLookUp);
    }

    @Test
    public void getMetaDataShouldReturnCounterDataForCounters()
            throws PfmDataException, ValidationException {
        final PmMimVersionQuery pmvq = new PmMimVersionQuery();
        when(pmCountersLookUp.getCountersForAllVersions(pmvq.getMimVersions(),
                MODEL_DEFINERS_NE, false)).thenReturn(new TreeSet<>());
        final Collection<CounterTableRow> eventsData = pmMetaDataLookUp
                .getCounters(pmvq, MODEL_DEFINERS_NE, false, false, new LinkedList<>());
        Assert.assertNotNull(eventsData);
    }

    @Test
    public void getApplicableCountersShouldCallPmCounterLookup() {
        final List<CounterInfo> counterEventList = new ArrayList<>();
        pmMetaDataLookUp.getApplicableCounters(counterEventList, "type",
                "version");
        verify(pmCountersLookUp, times(1)).getApplicableCounters(counterEventList,
                "type", "version");
    }

    @Test
    public void getCorrectMetaDataListForTheSpecifiedMimsShouldReturnCorrectCounterListWhenCounters() {
        final List<CounterInfo> counterEventList = new ArrayList<>();
        final Set<NodeTypeAndVersion> nodesSet = new HashSet<>();
        when(pmCountersLookUp.getCorrectCounterListForTheSpecifiedMims(
                eq(counterEventList), eq(nodesSet), eq(MODEL_DEFINERS_OSS), eq(emptyList()), eq(false)))
                .thenReturn(counterEventList);
        pmMetaDataLookUp.getCorrectCounterListForTheSpecifiedMims(
                counterEventList, nodesSet, MODEL_DEFINERS_OSS, emptyList(), false);
        verify(pmCountersLookUp, times(1))
                .getCorrectCounterListForTheSpecifiedMims(eq(counterEventList),
                        eq(nodesSet), eq(MODEL_DEFINERS_OSS), eq(emptyList()), eq(false));
    }

    @Test
    public void getCorrectMetaDataListForTheSpecifiedMimsShouldReturnCorrectEventListWhenEvents() {
        final List<EventInfo> counterEventList = new ArrayList<>();
        final Set<NodeTypeAndVersion> nodesSet = new HashSet<>();
        when(pmEventsLookUp.getCorrectEventListForTheSpecifiedMims(
                counterEventList, nodesSet)).thenReturn(counterEventList);
        pmMetaDataLookUp.getCorrectEventListForTheSpecifiedMims(
                counterEventList, nodesSet);
        verify(pmEventsLookUp, times(1)).getCorrectEventListForTheSpecifiedMims(
                counterEventList, nodesSet);
    }
}
