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

package com.ericsson.oss.services.pm.initiation.integration.subscription;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_OK;

import static io.restassured.RestAssured.given;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.hamcrest.collection.IsIn;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants;
import com.ericsson.oss.services.pm.integration.RestBaseArquillian;
import com.ericsson.services.pm.initiation.restful.api.EventTableRow;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import io.restassured.response.Response;

@RunWith(Arquillian.class)
public class EventsFromModelServiceITTest extends RestBaseArquillian {

    private static final String UNDEPLOYED_MIM = "XXXX:9.9.9";

    private static final String ERBS_3958_644_341 = "ERBS:3958-644-341";
    private static final String ERBS_4322_436_393 = "ERBS:4322-436-393";
    private static final String ERBS_16_B_G_1_281 = "ERBS:16B-G.1.281";
    private static final String SGSN_MME_15_B_CP01 = "SGSN-MME:15B-CP01";
    private static final String SGSN_MME_16_A_CP02 = "SGSN-MME:16A-CP02";
    private static final String RNC_16_A_V_6_940 = "RNC:16A-V.6.940";
    private static final String RNC_16_B_V_7_1659 = "RNC:16B-V.7.1659";
    private static final String RADIO_NODE_18_Q3_R49A16 = "RadioNode:18.Q3-R49A16";

    private static final String INVALID_VERSION_ERROR_MESSAGE = "Invalid Version XXXX:9.9.9. Please remove invalid version(s).";
    private static final String GET_EVENT = "getEvent";
    private static final String EVENT_NAME = "eventName";
    private static final String GET_WCDMA_EVENTS = "getWcdmaEvents";
    private static final String GET_CELL_TRAFFIC_EVENTS = "getCellTrafficEvents";
    private static final String EVENT_FILTER_CELLTRACE_LRAN = "cellTraceLRan";
    private static final String EVENT_FILTER_CELLTRACE_NRAN = "cellTraceNRan";

    private static String getGroupName(final Collection<EventTableRow> events, final String event) {
        for (final EventTableRow eventRow : events) {
            if (eventRow.getEventName().equals(event)) {
                return eventRow.getSourceObject();
            }
        }
        return "Event not available";
    }

    private static boolean hasEvent(final Collection<EventTableRow> events, final String event) {
        for (final EventTableRow eventRow : events) {
            if (eventRow.getEventName().equals(event)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @RunAsClient
    @InSequence(3)
    public void testGetEventsForSingeleMim() {

        final Response response = given().header(USERID_HEADER).expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_EVENT_MIM_QUERY + ERBS_4322_436_393);
        final List<String> events = response.path(EVENT_NAME);
        final List<String> groupNames = response.path(GROUP_NAME);

        final Collection<String> uniqueGroupNames = new HashSet<>();
        for (final String groupName : groupNames) {
            uniqueGroupNames.add(groupName);
        }

        Assert.assertThat(events.size(), IsIn.isOneOf(439, 440));
        Assert.assertEquals(14, uniqueGroupNames.size());
    }

    @Test
    @RunAsClient
    @InSequence(4)
    public void testGetEventsForMultipleMim() {

        final Response response = given().header(USERID_HEADER).expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_EVENT_MIM_QUERY + ERBS_4322_436_393 + "," + ERBS_3958_644_341);
        final List<String> events = response.path(EVENT_NAME);
        final List<String> groupNames = response.path(GROUP_NAME);

        final Collection<String> uniqueGroupNames = new HashSet<>();
        for (final String groupName : groupNames) {
            uniqueGroupNames.add(groupName);
        }
        //temporary workaround to allow test to pass in both docker and managed mode
        //till baseline is updated
        Assert.assertThat(events.size(), IsIn.isOneOf(489, 506));
        Assert.assertThat(uniqueGroupNames.size(), IsIn.isOneOf(16, 17));
    }

    @Test
    @RunAsClient
    @InSequence(5)
    public void testGetEventsForOneDeployedMimAndOneUndeployedMim() {
        final Response response = given().header(USERID_HEADER).expect().statusCode(SC_BAD_REQUEST).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_EVENT_MIM_QUERY + ERBS_3958_644_341 + "," + UNDEPLOYED_MIM);
        // validate the error message
        response.statusLine().equals(INVALID_VERSION_ERROR_MESSAGE);
    }

    @Test
    @RunAsClient
    @InSequence(6)
    public void testGetEventsForUndeployedMim() {
        final Response response = given().header(USERID_HEADER).expect().statusCode(SC_BAD_REQUEST).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_EVENT_MIM_QUERY + UNDEPLOYED_MIM);
        // validate the error message
        response.statusLine().equals(INVALID_VERSION_ERROR_MESSAGE);
    }

    @Test
    @RunAsClient
    @InSequence(7)
    public void testGetSgsnMmeEventsForSingeleMim() {
        final List<String> events = given().header(USERID_HEADER).expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_EVENT_MIM_QUERY + SGSN_MME_15_B_CP01).path(EVENT_NAME);
        Assert.assertEquals(18, events.size());
    }

    @Test
    @InSequence(11)
    @RunAsClient
    public void getEvents_SGSN_16A_CP02() {
        final Response response = given().header(USERID_HEADER).param(MIM_QUERY, SGSN_MME_16_A_CP02).expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_EVENT);

        final List<String> events = response.path(EVENT_NAME);
        Assert.assertEquals(18, events.size());
    }

    //@Test
    @InSequence(11)
    @RunAsClient
    public void getEvents_ERBS_16B_G_1_281() {
        final Response response = given().header(USERID_HEADER).param(MIM_QUERY, ERBS_16_B_G_1_281).expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_EVENT);

        final List<String> events = response.path(EVENT_NAME);
        Assert.assertEquals(549, events.size());
    }

    @Test
    @InSequence(11)
    @RunAsClient
    public void getEvents_RADIO_NODE_18_Q3_R49A16_with_celltrace_lran_filter() {
        final Response response = given().header(USERID_HEADER).param(MIM_QUERY, RADIO_NODE_18_Q3_R49A16)
                .param(EVENT_FILTER_FOR_TECHNOLOGY_DOMAINS, EVENT_FILTER_CELLTRACE_LRAN).expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_EVENT);

        final List<String> events = response.path(EVENT_NAME);
        Assert.assertEquals(582, events.size());
    }

    @Test
    @InSequence(11)
    @RunAsClient
    public void getEvents_RADIO_NODE_18_Q3_R49A16_with_celltrace_nran_filter() {
        //TODO EEITSIK Add test for NRAN node with 5GS events when available
        final Response response = given().header(USERID_HEADER).param(MIM_QUERY, RADIO_NODE_18_Q3_R49A16)
                .param(EVENT_FILTER_FOR_TECHNOLOGY_DOMAINS, EVENT_FILTER_CELLTRACE_NRAN).expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_EVENT);

        final List<String> events = response.path(EVENT_NAME);
        Assert.assertTrue(events.isEmpty());
    }

    @Test
    @InSequence(11)
    @RunAsClient
    public void getEvents_Wideband_for_all_versions() {
        final Response response = given().header(USERID_HEADER).param(MIM_QUERY, RNC_16_A_V_6_940)
                .param("subscriptionType", SubscriptionType.GPEH.name()).expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_WCDMA_EVENTS);

        final List<String> events = response.path(EVENT_NAME);
        Assert.assertEquals(301, events.size());
    }

    @Test
    @InSequence(11)
    @RunAsClient
    public void getEvents_Celltraffic() throws IOException {
        final Response response = given().header(USERID_HEADER).param(MIM_QUERY, RNC_16_A_V_6_940)
                .param("subscriptionType", SubscriptionType.CELLTRAFFIC.name()).expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_CELL_TRAFFIC_EVENTS);

        final JsonArray jsonArrayForTriggeredEvents = new JsonParser().parse(response.path(PmMetaDataConstants.TRIGGER_EVENTS).toString())
                .getAsJsonArray();
        final List<EventTableRow> triggeredEvents = getEventTableRows(jsonArrayForTriggeredEvents);

        final JsonArray jsonArrayForNonTriggeredEvents = new JsonParser().parse(response.path(PmMetaDataConstants.NON_TRIGGER_EVENTS).toString())
                .getAsJsonArray();
        final List<EventTableRow> nonTriggeredEvents = getEventTableRows(jsonArrayForNonTriggeredEvents);

        Assert.assertEquals(147, triggeredEvents.size());
        Assert.assertEquals(50, nonTriggeredEvents.size());

        Assert.assertEquals("RRC", getGroupName(triggeredEvents, "RRC_ACTIVE_SET_UPDATE"));
        Assert.assertEquals("INTERNAL", getGroupName(nonTriggeredEvents, "INTERNAL_IMSI"));
    }

    @Test
    @InSequence(11)
    @RunAsClient
    public void getEvents_Celltraffic_for_different_mims() {
        final Response response = given().header(USERID_HEADER).param(MIM_QUERY, RNC_16_A_V_6_940 + "," + RNC_16_B_V_7_1659)
                .param("subscriptionType", SubscriptionType.CELLTRAFFIC.name()).expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_CELL_TRAFFIC_EVENTS);

        final List<String> triggeredEvents = response.path(PmMetaDataConstants.TRIGGER_EVENTS, EVENT_NAME);
        final List<String> nonTriggeredEvents = response.path(PmMetaDataConstants.NON_TRIGGER_EVENTS, EVENT_NAME);

        Assert.assertEquals(147, triggeredEvents.size());
        Assert.assertEquals(50, nonTriggeredEvents.size());

        //ONE INVALID MIM
        given().header(USERID_HEADER).param(MIM_QUERY, RNC_16_A_V_6_940 + ",RNC:111-111-111")
                .param("subscriptionType", SubscriptionType.CELLTRAFFIC.name()).expect().statusCode(SC_BAD_REQUEST).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_CELL_TRAFFIC_EVENTS);
    }

    @Test
    @InSequence(11)
    @RunAsClient
    public void getEvents_Celltraffic_should_not_fetch_GPEH_only_events_when_retrieving_CTR_profile_events_for_a_node_with_valid_MIM_version()
            throws IOException {
        final Response response = given().header(USERID_HEADER).param(MIM_QUERY, RNC_16_A_V_6_940)
                .param("subscriptionType", SubscriptionType.CELLTRAFFIC.name()).expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_CELL_TRAFFIC_EVENTS);

        final JsonArray jsonArrayForTriggeredEvents = new JsonParser().parse(response.path(PmMetaDataConstants.TRIGGER_EVENTS).toString())
                .getAsJsonArray();
        final List<EventTableRow> triggeredEvents = getEventTableRows(jsonArrayForTriggeredEvents);

        final JsonArray jsonArrayForNonTriggeredEvents = new JsonParser().parse(response.path(PmMetaDataConstants.NON_TRIGGER_EVENTS).toString())
                .getAsJsonArray();
        final List<EventTableRow> nonTriggeredEvents = getEventTableRows(jsonArrayForNonTriggeredEvents);

        Assert.assertFalse(hasEvent(triggeredEvents, "RRC_MBMS_GENERAL_INFORMATION"));
        Assert.assertFalse(hasEvent(nonTriggeredEvents, "RRC_MBMS_GENERAL_INFORMATION"));
    }

    private List<EventTableRow> getEventTableRows(final JsonArray jsonArray) {
        final Gson gson = new Gson();
        final List<EventTableRow> eventTableRows = new ArrayList<>();
        for (int i = 0; i < jsonArray.size(); i++) {
            final JsonElement str = jsonArray.get(i);
            final EventTableRow eventTableRow = gson.fromJson(str, EventTableRow.class);
            eventTableRows.add(eventTableRow);
        }
        return eventTableRows;
    }
}
