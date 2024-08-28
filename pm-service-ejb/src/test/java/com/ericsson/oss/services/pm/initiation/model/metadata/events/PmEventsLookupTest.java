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

package com.ericsson.oss.services.pm.initiation.model.metadata.events;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import static com.ericsson.oss.services.pm.initiation.common.Constants.MANUAL_PROTOCOL_GROUPING;
import static com.ericsson.oss.services.pm.initiation.common.Constants.MODEL_VERSION_1_0_0;
import static com.ericsson.oss.services.pm.initiation.common.Constants.PMIC_CCTR_ERBS_NODE_NAME;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.slf4j.Logger;

import com.ericsson.oss.itpf.modeling.common.info.ModelInfo;
import com.ericsson.oss.itpf.modeling.modelservice.direct.DirectModelAccess;
import com.ericsson.oss.itpf.modeling.modelservice.exception.UnknownModelException;
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo;
import com.ericsson.oss.services.pm.initiation.model.metadata.PmMetaDataHelper;
import com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_event.EventGroupType;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_event.EventType;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_event.PerformanceEventDefinition;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.services.pm.initiation.restful.api.EventTableRow;

public class PmEventsLookupTest {

    private static final String OSS_MODEL_IDENTITY_1 = "1094-174-285";
    private static final String OSS_MODEL_IDENTITY_2 = "1094-174-295";
    private static final String OSS_MODEL_IDENTITY_3 = "1094-175-285";
    private static final String OSS_MODEL_IDENTITY_4 = "1094-175-275";

    private final Collection<String> modelUrns = new HashSet<>();
    private final Collection<String> modelUrns2 = new HashSet<>();

    @Spy
    private final EventsPerMimVersionLocalCache eventsMimCache = new EventsPerMimVersionLocalCache();

    @Mock
    private Logger logger;
    @Mock
    private DirectModelAccess directModelAccess;
    @Mock
    private PmMetaDataHelper metaDataHelper;

    @InjectMocks
    PmEventsLookUp pmEventsLookUp;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        modelUrns.add("/pfm_event/ERBS/NE-defined/387901871689.140813204546.374877253817");
        modelUrns2.add("/pfm_event/ERBS/NE-defined/497901871689.250813204546.484877253817");

        when(metaDataHelper.getMetaDataUrnsFromModelService("ERBS", OSS_MODEL_IDENTITY_1, PmMetaDataConstants.PFM_EVENT_PATTERN)).thenReturn(modelUrns);
        when(metaDataHelper.getMetaDataUrnsFromModelService("ERBS", OSS_MODEL_IDENTITY_2, PmMetaDataConstants.PFM_EVENT_PATTERN)).thenReturn(modelUrns);
        when(metaDataHelper.getMetaDataUrnsFromModelService("ERBS", OSS_MODEL_IDENTITY_3, PmMetaDataConstants.PFM_EVENT_PATTERN)).thenReturn(
                new HashSet<>());
        when(metaDataHelper.getMetaDataUrnsFromModelService("ERBS", OSS_MODEL_IDENTITY_4, PmMetaDataConstants.PFM_EVENT_PATTERN)).thenReturn(modelUrns2);
    }

    @Test
    public void getEventsTestForSingleMim() throws PfmDataException {
        final List<String> technologyDomainList = Arrays.asList("EPS");
        final Set<NodeTypeAndVersion> mimVersions = new HashSet<>();
        final NodeTypeAndVersion typeVersion = new NodeTypeAndVersion("ERBS", OSS_MODEL_IDENTITY_1, technologyDomainList);
        mimVersions.add(typeVersion);

        setUpModelServiceEvents();

        final Collection<EventTableRow> expectedResult = setUpExpectedResultForSingleERBSMim();

        final Collection<EventTableRow> actualresult = pmEventsLookUp.getEventsForAllVersions(mimVersions);
        assertEquals(actualresult, expectedResult);
    }

    @Test
    public void verifyGetListOfSupportedEvents() {
        final Map<String, Set<String>> mapOfNeTypeModelIdentitySet = new HashMap<>();
        final Set<String> setOfModelIdentity = new HashSet<>();
        setOfModelIdentity.add(OSS_MODEL_IDENTITY_1);
        setOfModelIdentity.add(OSS_MODEL_IDENTITY_2);
        mapOfNeTypeModelIdentitySet.put("ERBS", setOfModelIdentity);

        setUpModelServiceEventsForCCTR(1);

        final List<EventInfo> expectedResult = setUpExpectedResultEventInfo(1);

        final List<EventInfo> actualresult = pmEventsLookUp.getListOfSupportedEvents(mapOfNeTypeModelIdentitySet);
        assertEquals(actualresult, expectedResult);
    }

    @Test
    public void getEventsTestForMultipleMim() throws PfmDataException {
        final List<String> technologyDomainList = Arrays.asList("EPS");
        final Set<NodeTypeAndVersion> mimVersions = new HashSet<>();
        final NodeTypeAndVersion correctTypeVersion = new NodeTypeAndVersion("ERBS", OSS_MODEL_IDENTITY_1, technologyDomainList);
        final NodeTypeAndVersion incorrectTypeVersion = new NodeTypeAndVersion("ERBS", OSS_MODEL_IDENTITY_4, technologyDomainList);
        mimVersions.add(correctTypeVersion);
        mimVersions.add(incorrectTypeVersion);

        setUpModelServiceEventsForMultipleMim();

        final Collection<EventTableRow> expectedResult = setUpExpectedResultForSingleERBSMim();

        final Collection<EventTableRow> actualResult = pmEventsLookUp.getEventsForAllVersions(mimVersions);
        assertEquals(actualResult, expectedResult);
    }

    @Test
    public void getEventsTestWithOneInvalidMim() throws PfmDataException {
        final List<String> technologyDomainList = Arrays.asList("EPS");
        final Set<NodeTypeAndVersion> mimVersions = new HashSet<>();
        final NodeTypeAndVersion correctTypeVersion = new NodeTypeAndVersion("ERBS", OSS_MODEL_IDENTITY_1, technologyDomainList);
        final NodeTypeAndVersion incorrectTypeVersion = new NodeTypeAndVersion("ERBS", OSS_MODEL_IDENTITY_3, technologyDomainList);
        mimVersions.add(correctTypeVersion);
        mimVersions.add(incorrectTypeVersion);

        setUpModelServiceEvents();

        final Collection<EventTableRow> expectedResult = setUpExpectedResultForSingleERBSMim();

        final Collection<EventTableRow> actualresult = pmEventsLookUp.getEventsForAllVersions(mimVersions);
        assertEquals(actualresult, expectedResult);
    }

    @Test(expected = PfmDataException.class)
    public void getEventsTestWithException() throws PfmDataException {
        final List<String> technologyDomainList = Arrays.asList("EPS");
        final Set<NodeTypeAndVersion> mimVersions = new HashSet<>();
        final NodeTypeAndVersion incorrectTypeVersion = new NodeTypeAndVersion("ERBS", OSS_MODEL_IDENTITY_3, technologyDomainList);
        mimVersions.add(incorrectTypeVersion);
        pmEventsLookUp.getEventsForAllVersions(mimVersions);
    }

    @Test(expected = PfmDataException.class)
    public void getPMEventsShouldThrowUnKnownModelException() throws PfmDataException {

        final List<String> technologyDomainList = Arrays.asList("EPS");
        final Set<NodeTypeAndVersion> mimVersions = new HashSet<>();
        final NodeTypeAndVersion typeVersion = new NodeTypeAndVersion("ERBS", OSS_MODEL_IDENTITY_1, technologyDomainList);
        mimVersions.add(typeVersion);
        when(directModelAccess.getAsJavaTree(any(ModelInfo.class), eq(PerformanceEventDefinition.class))).thenThrow(new UnknownModelException(""));
        pmEventsLookUp.getEventsForAllVersions(mimVersions);
    }

    @Test
    public void getApplicableEeventsOnlyWhenLessSelected() {
        when(metaDataHelper.getMetaDataUrnsFromModelService("ERBS", OSS_MODEL_IDENTITY_1, PmMetaDataConstants.PFM_EVENT_PATTERN)).thenReturn(modelUrns);
        setUpModelServiceEvents();
        final List<EventInfo> moClassList = createEventGroupInfoArgs();
        final List<String> expectedResult = createExpectedEventStringListForApplicableEvents();
        final List<String> result = pmEventsLookUp.getApplicableEvents(moClassList, "ERBS", OSS_MODEL_IDENTITY_1);

        assertEquals(result, expectedResult);
    }

    private List<EventInfo> createEventGroupInfoArgs() {
        final List<EventInfo> moClassList = new ArrayList<>();
        moClassList.add(new EventInfo("M3_M3_SETUP_FAILURE", "MBMS"));
        return moClassList;
    }

    private List<String> createExpectedEventStringListForApplicableEvents() {
        final List<String> moClassList = new ArrayList<>();

        moClassList.add("M3_M3_SETUP_FAILURE");

        return moClassList;
    }

    private void setUpModelServiceEvents() {

        final PerformanceEventDefinition perfEvent1 = mock(PerformanceEventDefinition.class);
        final PerformanceEventDefinition perfEvent2 = mock(PerformanceEventDefinition.class);
        final ModelInfo modelInfo = ModelInfo.fromUrn(PmMetaDataConstants.PFM_EVENT_PATTERN + PMIC_CCTR_ERBS_NODE_NAME + MANUAL_PROTOCOL_GROUPING
                + MODEL_VERSION_1_0_0);

        when(directModelAccess.getAsJavaTree(any(ModelInfo.class), eq(PerformanceEventDefinition.class))).thenReturn(perfEvent1);
        when(directModelAccess.getAsJavaTree(modelInfo, PerformanceEventDefinition.class)).thenReturn(perfEvent2);

        final List<EventGroupType> eventGroups = new ArrayList<>();

        final EventGroupType eventGroupType1 = mock(EventGroupType.class);
        final List<String> eventList1 = new ArrayList<>();
        eventList1.add("M3_M3_SETUP_FAILURE");
        eventList1.add("INTERNAL_PROC_M3_SETUP");

        when(eventGroupType1.getEvent()).thenReturn(eventList1);
        when(eventGroupType1.getName()).thenReturn("MBMS");

        final EventGroupType eventGroupType2 = mock(EventGroupType.class);
        final List<String> eventList2 = new ArrayList<>();
        eventList2.add("INTERNAL_EVENT_HO_WRONG_CELL_REEST");

        when(eventGroupType2.getEvent()).thenReturn(eventList2);
        when(eventGroupType2.getName()).thenReturn("HANDOVER_EVALUATION");

        final EventGroupType eventGroupType3 = mock(EventGroupType.class);
        final List<String> eventList3 = new ArrayList<>();
        eventList3.add("INTERNAL_PER_UE_MDT_M1_REPORT");

        when(eventGroupType3.getEvent()).thenReturn(eventList3);
        when(eventGroupType3.getName()).thenReturn("MDT");

        final EventGroupType eventGroupType4 = mock(EventGroupType.class);
        final List<String> eventList4 = new ArrayList<>();
        eventList4.add("UE_MEAS_GERAN1");

        when(eventGroupType4.getEvent()).thenReturn(eventList4);
        when(eventGroupType4.getName()).thenReturn("RDT");

        eventGroups.add(eventGroupType1);
        eventGroups.add(eventGroupType2);
        eventGroups.add(eventGroupType3);
        eventGroups.add(eventGroupType4);

        final List<EventType> ungroupedEvents = new ArrayList<>();

        final EventType ungroupedEvent1 = mock(EventType.class);
        when(ungroupedEvent1.getName()).thenReturn("M3_M3_SETUP_FAILURE");
        ungroupedEvents.add(ungroupedEvent1);

        final EventType ungroupedEvent2 = mock(EventType.class);
        when(ungroupedEvent2.getName()).thenReturn("INTERNAL_EVENT_HO_WRONG_CELL_REEST");
        ungroupedEvents.add(ungroupedEvent2);

        final EventType ungroupedEvent3 = mock(EventType.class);
        when(ungroupedEvent3.getName()).thenReturn("INTERNAL_PER_UE_MDT_M1_REPORT");
        ungroupedEvents.add(ungroupedEvent3);

        final EventType ungroupedEvent4 = mock(EventType.class);
        when(ungroupedEvent4.getName()).thenReturn("UE_MEAS_GERAN1");
        ungroupedEvents.add(ungroupedEvent4);

        when(perfEvent1.getEventGroup()).thenReturn(eventGroups);
        when(perfEvent1.getEvent()).thenReturn(ungroupedEvents);

        final List<EventGroupType> eventProtGroups = new ArrayList<>();

        final EventGroupType eventProtGroupType1 = mock(EventGroupType.class);
        final List<String> eventProtList1 = new ArrayList<>();
        eventProtList1.add("M3_M3_SETUP_FAILURE");

        when(eventProtGroupType1.getEvent()).thenReturn(eventProtList1);
        when(eventProtGroupType1.getName()).thenReturn("EXTERNAL");

        final EventGroupType eventProtGroupType2 = mock(EventGroupType.class);
        final List<String> eventProtList2 = new ArrayList<>();
        eventProtList2.add("INTERNAL_EVENT_HO_WRONG_CELL_REEST");

        when(eventProtGroupType2.getEvent()).thenReturn(eventProtList2);
        when(eventProtGroupType2.getName()).thenReturn("INTERNAL");

        final EventGroupType eventProtGroupType3 = mock(EventGroupType.class);
        final List<String> eventProtList3 = new ArrayList<>();
        eventProtList3.add("INTERNAL_PER_UE_MDT_M1_REPORT");

        when(eventProtGroupType3.getEvent()).thenReturn(eventProtList3);
        when(eventProtGroupType3.getName()).thenReturn("INTERNAL_PERIODIC");

        final EventGroupType eventProtGroupType4 = mock(EventGroupType.class);
        final List<String> eventProtList4 = new ArrayList<>();
        eventProtList4.add("UE_MEAS_GERAN1");

        when(eventProtGroupType4.getEvent()).thenReturn(eventProtList4);
        when(eventProtGroupType4.getName()).thenReturn("PM_INITIATED_UE_MEASUREMENTS");

        eventProtGroups.add(eventProtGroupType1);
        eventProtGroups.add(eventProtGroupType2);
        eventProtGroups.add(eventProtGroupType3);
        eventProtGroups.add(eventProtGroupType4);

        final List<EventType> ungroupedProtEvents = new ArrayList<>();

        when(perfEvent2.getEventGroup()).thenReturn(eventProtGroups);
        when(perfEvent2.getEvent()).thenReturn(ungroupedProtEvents);

    }

    private void setUpModelServiceEventsForMultipleMim() {

        final PerformanceEventDefinition perfEvent1 = mock(PerformanceEventDefinition.class);
        final PerformanceEventDefinition perfEvent2 = mock(PerformanceEventDefinition.class);
        final PerformanceEventDefinition perfEvent3 = mock(PerformanceEventDefinition.class);

        final ModelInfo modelInfo1 = ModelInfo.fromUrn("/pfm_event/ERBS/NE-defined/387901871689.140813204546.374877253817");
        final ModelInfo modelInfo2 = ModelInfo.fromUrn("/pfm_event/ERBS/NE-defined/497901871689.250813204546.484877253817");
        final ModelInfo modelInfo3 = ModelInfo.fromUrn(PmMetaDataConstants.PFM_EVENT_PATTERN + PMIC_CCTR_ERBS_NODE_NAME + MANUAL_PROTOCOL_GROUPING
                + MODEL_VERSION_1_0_0);

        when(directModelAccess.getAsJavaTree(modelInfo1, PerformanceEventDefinition.class)).thenReturn(perfEvent1);
        when(directModelAccess.getAsJavaTree(modelInfo2, PerformanceEventDefinition.class)).thenReturn(perfEvent2);
        when(directModelAccess.getAsJavaTree(modelInfo3, PerformanceEventDefinition.class)).thenReturn(perfEvent3);

        final List<EventGroupType> eventGroups1 = new ArrayList<>();
        final List<EventGroupType> eventGroups2 = new ArrayList<>();

        final EventGroupType eventGroupType1 = mock(EventGroupType.class);
        final List<String> eventList1 = new ArrayList<>();
        eventList1.add("M3_M3_SETUP_FAILURE");
        eventList1.add("INTERNAL_PROC_M3_SETUP");

        when(eventGroupType1.getEvent()).thenReturn(eventList1);
        when(eventGroupType1.getName()).thenReturn("MBMS");

        final EventGroupType eventGroupType2 = mock(EventGroupType.class);
        final List<String> eventList2 = new ArrayList<>();
        eventList2.add("INTERNAL_EVENT_HO_WRONG_CELL_REEST");

        when(eventGroupType2.getEvent()).thenReturn(eventList2);
        when(eventGroupType2.getName()).thenReturn("HANDOVER_EVALUATION");

        eventGroups1.add(eventGroupType1);
        eventGroups1.add(eventGroupType2);

        final EventGroupType eventGroupType3 = mock(EventGroupType.class);
        final List<String> eventList3 = new ArrayList<>();
        eventList3.add("INTERNAL_PER_UE_MDT_M1_REPORT");

        when(eventGroupType3.getEvent()).thenReturn(eventList3);
        when(eventGroupType3.getName()).thenReturn("MDT");

        final EventGroupType eventGroupType4 = mock(EventGroupType.class);
        final List<String> eventList4 = new ArrayList<>();
        eventList4.add("UE_MEAS_GERAN1");

        when(eventGroupType4.getEvent()).thenReturn(eventList4);
        when(eventGroupType4.getName()).thenReturn("RDT");

        eventGroups2.add(eventGroupType3);
        eventGroups2.add(eventGroupType4);

        final List<EventType> ungroupedEvents1 = new ArrayList<>();
        final List<EventType> ungroupedEvents2 = new ArrayList<>();

        final EventType ungroupedEvent1 = mock(EventType.class);
        when(ungroupedEvent1.getName()).thenReturn("M3_M3_SETUP_FAILURE");
        ungroupedEvents1.add(ungroupedEvent1);

        final EventType ungroupedEvent2 = mock(EventType.class);
        when(ungroupedEvent2.getName()).thenReturn("INTERNAL_EVENT_HO_WRONG_CELL_REEST");
        ungroupedEvents1.add(ungroupedEvent2);

        final EventType ungroupedEvent3 = mock(EventType.class);
        when(ungroupedEvent3.getName()).thenReturn("INTERNAL_PER_UE_MDT_M1_REPORT");
        ungroupedEvents2.add(ungroupedEvent3);

        final EventType ungroupedEvent4 = mock(EventType.class);
        when(ungroupedEvent4.getName()).thenReturn("UE_MEAS_GERAN1");
        ungroupedEvents2.add(ungroupedEvent4);

        when(perfEvent1.getEventGroup()).thenReturn(eventGroups1);
        when(perfEvent1.getEvent()).thenReturn(ungroupedEvents1);

        when(perfEvent2.getEventGroup()).thenReturn(eventGroups2);
        when(perfEvent2.getEvent()).thenReturn(ungroupedEvents2);

        final List<EventGroupType> eventProtGroups = new ArrayList<>();

        final EventGroupType eventProtGroupType1 = mock(EventGroupType.class);
        final List<String> eventProtList1 = new ArrayList<>();
        eventProtList1.add("M3_M3_SETUP_FAILURE");

        when(eventProtGroupType1.getEvent()).thenReturn(eventProtList1);
        when(eventProtGroupType1.getName()).thenReturn("EXTERNAL");

        final EventGroupType eventProtGroupType2 = mock(EventGroupType.class);
        final List<String> eventProtList2 = new ArrayList<>();
        eventProtList2.add("INTERNAL_EVENT_HO_WRONG_CELL_REEST");

        when(eventProtGroupType2.getEvent()).thenReturn(eventProtList2);
        when(eventProtGroupType2.getName()).thenReturn("INTERNAL");

        final EventGroupType eventProtGroupType3 = mock(EventGroupType.class);
        final List<String> eventProtList3 = new ArrayList<>();
        eventProtList3.add("INTERNAL_PER_UE_MDT_M1_REPORT");

        when(eventProtGroupType3.getEvent()).thenReturn(eventProtList3);
        when(eventProtGroupType3.getName()).thenReturn("INTERNAL_PERIODIC");

        final EventGroupType eventProtGroupType4 = mock(EventGroupType.class);
        final List<String> eventProtList4 = new ArrayList<>();
        eventProtList4.add("UE_MEAS_GERAN1");

        when(eventProtGroupType4.getEvent()).thenReturn(eventProtList4);
        when(eventProtGroupType4.getName()).thenReturn("PM_INITIATED_UE_MEASUREMENTS");

        eventProtGroups.add(eventProtGroupType1);
        eventProtGroups.add(eventProtGroupType2);
        eventProtGroups.add(eventProtGroupType3);
        eventProtGroups.add(eventProtGroupType4);

        final List<EventType> ungroupedProtEvents = new ArrayList<>();

        when(perfEvent3.getEventGroup()).thenReturn(eventProtGroups);
        when(perfEvent3.getEvent()).thenReturn(ungroupedProtEvents);

    }

    private void setUpModelServiceEventsForCCTR(final int noOfEvents) {

        final PerformanceEventDefinition perfEvent1 = mock(PerformanceEventDefinition.class);
        when(directModelAccess.getAsJavaTree(any(ModelInfo.class), eq(PerformanceEventDefinition.class))).thenReturn(perfEvent1);
        if (noOfEvents > 0) {
            final List<EventGroupType> eventGroups = new ArrayList<>();
            final EventGroupType eventGroupType = new EventGroupType();
            eventGroupType.setName("CCTR");
            eventGroupType.setType("ERBS");
            eventGroupType.getEvent().add("pmEvent1");
            eventGroups.add(eventGroupType);
            when(perfEvent1.getEventGroup()).thenReturn(eventGroups);
        }
    }

    private Collection<EventTableRow> setUpExpectedResultForSingleERBSMim() {
        final Collection<EventTableRow> expectedResult = new TreeSet<>();

        expectedResult.add(new EventTableRow("M3_M3_SETUP_FAILURE", "EXTERNAL"));
        expectedResult.add(new EventTableRow("INTERNAL_PROC_M3_SETUP", "MBMS"));
        expectedResult.add(new EventTableRow("INTERNAL_EVENT_HO_WRONG_CELL_REEST", "HANDOVER_EVALUATION"));
        expectedResult.add(new EventTableRow("INTERNAL_EVENT_HO_WRONG_CELL_REEST", "INTERNAL"));
        expectedResult.add(new EventTableRow("INTERNAL_PER_UE_MDT_M1_REPORT", "MDT"));
        expectedResult.add(new EventTableRow("INTERNAL_PER_UE_MDT_M1_REPORT", "INTERNAL_PERIODIC"));
        expectedResult.add(new EventTableRow("UE_MEAS_GERAN1", "RDT"));
        expectedResult.add(new EventTableRow("UE_MEAS_GERAN1", "PM_INITIATED_UE_MEASUREMENTS"));
        expectedResult.add(new EventTableRow("M3_M3_SETUP_FAILURE", "MBMS"));
        return expectedResult;
    }

    private List<EventInfo> setUpExpectedResultEventInfo(final int noOfEvents) {
        final List<EventInfo> expectedResult = new ArrayList<>();
        for (int n = 1; n <= noOfEvents; n++) {
            expectedResult.add(new EventInfo("pmEvent" + n, "CCTR"));
        }
        return expectedResult;
    }
}
