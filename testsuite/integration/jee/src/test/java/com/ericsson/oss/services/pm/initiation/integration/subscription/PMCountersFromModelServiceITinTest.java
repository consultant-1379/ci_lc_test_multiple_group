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

package com.ericsson.oss.services.pm.initiation.integration.subscription;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_OK;

import static io.restassured.RestAssured.given;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.oss.services.pm.initiation.model.utils.ModelDefiner;
import com.ericsson.oss.services.pm.integration.RestBaseArquillian;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_measurement.ScannerType;
import com.ericsson.services.pm.initiation.restful.api.CounterTableRow;
import com.ericsson.services.pm.initiation.restful.api.EventBasedCounterTableRow;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import io.restassured.response.Response;

@RunWith(Arquillian.class)
public class PMCountersFromModelServiceITinTest extends RestBaseArquillian {

    private static final String ERBS_4322_940_032 = "ERBS:4322-940-032";
    private static final String ERBS_4322_436_393 = "ERBS:4322-436-393";
    private static final String ERBS_3958_644_341 = "ERBS:3958-644-341";
    private static final String ERBS_18_Q4_J_2_300 = "ERBS:18.Q4-J.2.300";
    private static final String SGSN_MME_16_A_CP02 = "SGSN-MME:16A-CP02";
    private static final String SGSN_MME_15_B_CP01 = "SGSN-MME:15B-CP01";
    private static final String RNC_16B_V_7_1659_M9 = "RNC:16B-V.7.1659-M9";
    private static final String RADIO_NODE_18_Q2_R43A23 = "RadioNode:18.Q2-R43A23";
    private static final String RADIO_NODE_18_Q3_R49A16 = "RadioNode:18.Q3-R49A16";
    private static final String RADIO_NODE_19_Q3_R41A26 = "RadioNode:19.Q3-R41A26";
    private static final String CISCO_ASR9000_17A = "CISCO-ASR9000:17A";
    private static final String CISCO_ASR900_17A = "CISCO-ASR900:17A";

    private static final String UNDEPLOYED_MIM = "XXXX:9.9.9";

    private static final String INVALID_VERSION_ERROR_MESSAGE = "Invalid Version XXXX:9.9.9. Please remove invalid version(s).";
    private static final String COUNTERSUBGROUPS = "countersubgroups/";

    @Test
    @RunAsClient
    @InSequence(3)
    public void getPMCounters_For_a_given_mim_first_time() throws Exception {
        getPMCounters_For_a_given_mim_ERBS_3958_644_341();
    }

    @Test
    @InSequence(4)
    @RunAsClient
    public void getPMCounters_For_a_given_mim_second_time() throws Exception {
        getPMCounters_For_a_given_mim_ERBS_3958_644_341();
    }

    private void getPMCounters_For_a_given_mim_ERBS_3958_644_341() {
        final String mimQuery = ERBS_3958_644_341;
        final int expectedNumberOfGroups = 52;
        final int expectedNumberOfCounters = 2386;
        executeGetCountersRequestAndAssertOnNumberOfGroupsAndNumberOfCountersOnSuccessfulResponse(mimQuery, expectedNumberOfGroups, expectedNumberOfCounters);
    }

    @Test
    @InSequence(5)
    @RunAsClient
    public void getPMCounters_should_return_all_moClass_and_its_counters_for_multiple_mim_without_technology_domain() throws Exception {
        final String mimQuery = ERBS_4322_436_393 + "," + ERBS_4322_940_032 + "," + ERBS_3958_644_341;
        final int expectedNumberOfGroups = 61;
        final int expectedNumberOfCounters = 2627;
        executeGetCountersRequestAndAssertOnNumberOfGroupsAndNumberOfCountersOnSuccessfulResponse(mimQuery, expectedNumberOfGroups, expectedNumberOfCounters);
    }

    @Test
    @InSequence(6)
    @RunAsClient
    public void getPMCounters_should_return_all_moClass_and_its_counters_for_multiple_mim_with_technology_domain() throws Exception {
        final String mimQuery = ERBS_4322_436_393 + ":EPS," + ERBS_4322_940_032 + ":EPS," + ERBS_3958_644_341 + ":EPS";
        final int expectedNumberOfGroups = 61;
        final int expectedNumberOfCounters = 2627;
        executeGetCountersRequestAndAssertOnNumberOfGroupsAndNumberOfCountersOnSuccessfulResponse(mimQuery, expectedNumberOfGroups, expectedNumberOfCounters);
    }

    @Test
    @InSequence(7)
    @RunAsClient
    public void getPMCounters_should_return_all_moClass_and_its_counters_for_single_mim_without_technology_domain() throws Exception {
        final String mimQuery = ERBS_3958_644_341;
        final int expectedNumberOfGroups = 52;
        final int expectedNumberOfCounters = 2386;
        executeGetCountersRequestAndAssertOnNumberOfGroupsAndNumberOfCountersOnSuccessfulResponse(mimQuery, expectedNumberOfGroups, expectedNumberOfCounters);
    }

    @Test
    @InSequence(8)
    @RunAsClient
    public void getPMCounters_should_return_all_moClass_and_its_counters_for_single_mim_with_technology_domain() throws Exception {
        final String mimQuery = ERBS_3958_644_341 + ":EPS";
        final int expectedNumberOfGroups = 52;
        final int expectedNumberOfCounters = 2386;
        executeGetCountersRequestAndAssertOnNumberOfGroupsAndNumberOfCountersOnSuccessfulResponse(mimQuery, expectedNumberOfGroups, expectedNumberOfCounters);
    }

    @Test
    @InSequence(9)
    @RunAsClient
    public void getPMCounters_should_return_all_counters_for_correct_mim_when_we_have_one_correct_mim_and_one_incorrect_mim() throws Exception {
        final String mimQuery = ERBS_3958_644_341 + "," + UNDEPLOYED_MIM;

        final Response response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.NE).param(MIM_QUERY, mimQuery).expect()
                .statusCode(SC_BAD_REQUEST).when().get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        // validate the error message
        response.statusLine().equals(INVALID_VERSION_ERROR_MESSAGE);
    }

    @Test
    @InSequence(10)
    @RunAsClient
    public void getPMCounters_should_return_no_counters_for_one_incorrect_mim() throws Exception {
        final Response response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.NE).param(MIM_QUERY, UNDEPLOYED_MIM).expect()
                .statusCode(SC_BAD_REQUEST).when().get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        // validate the error message
        response.statusLine().equals(INVALID_VERSION_ERROR_MESSAGE);
    }

    @Test
    @InSequence(11)
    @RunAsClient
    public void getPMCounters_should_return_correct_number_of_node_counter() throws Exception {
        final String mimQuery = SGSN_MME_15_B_CP01;
        final int expectedNumberOfGroups = 127;
        final int expectedNumberOfCounters = 1280;
        executeGetCountersRequestAndAssertOnNumberOfGroupsAndNumberOfCountersOnSuccessfulResponse(mimQuery, expectedNumberOfGroups, expectedNumberOfCounters);
    }

    @Test
    @InSequence(12)
    @RunAsClient
    public void getCounters_SGSN_16A_CP02() {
        final String mimQuery = SGSN_MME_16_A_CP02 + ":EPS";
        Response response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.OSS).param(MIM_QUERY, mimQuery).expect().statusCode(SC_OK)
                .when().get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        List<String> counters = response.path(COUNTER_NAME);
        Assert.assertEquals(283, counters.size());

        final EventBasedCounterTableRow eventBasedcounter = findEventBasedCounter(response, "MME", "MME PDN Connectivity Abort");
        Assert.assertNotNull(eventBasedcounter);
        Assert.assertEquals(1, eventBasedcounter.getBasedOnEvent().size());

        response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.NE).param(MIM_QUERY, mimQuery).expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);
        counters = response.path(COUNTER_NAME);
        Assert.assertEquals(624, counters.size());
    }

    @Test
    @InSequence(13)
    @RunAsClient
    public void getCounters_ERBS_18_Q4_J_2_300() {
        final String mimQuery = ERBS_18_Q4_J_2_300 + ":EPS";
        Response response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.NE).param(MIM_QUERY, mimQuery).expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        List<String> counters = response.path(COUNTER_NAME);
        Assert.assertEquals(4298, counters.size());

        response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.OSS).param(MIM_QUERY, mimQuery).expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);
        counters = response.path(COUNTER_NAME);

        Assert.assertEquals(712, counters.size());


        final EventBasedCounterTableRow eventBasedcounter = findEventBasedCounter(response, "EUtranCellFDD", "pmDrxMeasSuccCgi");
        Assert.assertNotNull(eventBasedcounter);
        Assert.assertEquals(4, eventBasedcounter.getBasedOnEvent().size());

        //A known obolete counter will not be in the list of counters returned
        for (final String counter : counters) {
            if (counter.contains("IpTerminationHost")) {
                Assert.fail("Obsolete counter IpTerminationHost found");
            }
        }
    }

    @Test
    @InSequence(14)
    @RunAsClient
    public void getCounters_RNC_16B_V_7_1659_M9() {
        final String mimQuery = RNC_16B_V_7_1659_M9 + ":UMTS";
        final Response response = given().header(USERID_HEADER)
                .param(DEFINER, ModelDefiner.NE)
                .param(MIM_QUERY, mimQuery)
                .expect()
                .statusCode(SC_OK)
                .when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        final List<String> counters = response.path(COUNTER_NAME);
        Assert.assertEquals(2261, counters.size());
    }

    @Test
    @InSequence(15)
    @RunAsClient
    public void getCounters_RADIO_NODE_18_Q3_R49A16() {
        Response response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.NE).param(MIM_QUERY, RADIO_NODE_18_Q3_R49A16 + ":UMTS#EPS")
                .expect().statusCode(SC_OK).when().get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        List<String> counters = response.path(COUNTER_NAME);
        Assert.assertEquals(4693, counters.size());

        response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.NE).param(MIM_QUERY, RADIO_NODE_18_Q3_R49A16 + ":EPS").expect()
                .statusCode(SC_OK).when().get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        counters = response.path(COUNTER_NAME);
        Assert.assertEquals(4122, counters.size());

        response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.OSS).param(MIM_QUERY, RADIO_NODE_18_Q3_R49A16 + ":EPS").expect()
                .statusCode(SC_OK).when().get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        counters = response.path(COUNTER_NAME);


        Assert.assertEquals(712, counters.size());


        final EventBasedCounterTableRow counter = findEventBasedCounter(response, "EUtranCellFDD", "pmAgMeasSuccCgi");
        Assert.assertNotNull(counter);
        Assert.assertEquals(4, counter.getBasedOnEvent().size());

        response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.NE).param(MIM_QUERY, RADIO_NODE_18_Q3_R49A16 + ":UMTS").expect()
                .statusCode(SC_OK).when().get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        counters = response.path(COUNTER_NAME);
        Assert.assertEquals(939, counters.size());
    }

    @Test
    @InSequence(16)
    @RunAsClient
    public void getCounters_RadioNode_PRIMARY() {
        final Response response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.NE)
                .param(MIM_QUERY, RADIO_NODE_18_Q3_R49A16 + ":EPS," + RADIO_NODE_18_Q2_R43A23 + ":EPS").expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        JsonArray jsonArray = new JsonParser().parse(response.asString()).getAsJsonArray();
        List<CounterTableRow> counters = getCounterTableRows(jsonArray);

        final CounterTableRow counter = findCounter(counters, "QueueTailDrop", "queueHCDroppedOctets");
        Assert.assertNotNull(counter);
        Assert.assertEquals(ScannerType.PRIMARY, counter.getScannerType());
    }

    @Test
    @InSequence(17)
    @RunAsClient
    public void getCounters_RadioNode_PRIMARY_USER_DEFINED() {
        final Response response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.NE).param(MIM_QUERY, RADIO_NODE_18_Q3_R49A16 + ":EPS").expect()
                .statusCode(SC_OK).when().get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);


        JsonArray jsonArray = new JsonParser().parse(response.asString()).getAsJsonArray();
        List<CounterTableRow> counters = getCounterTableRows(jsonArray);

        final CounterTableRow counter = findCounter(counters, "QueueTailDrop", "queueHCDroppedPkts");
        Assert.assertNotNull(counter);
        Assert.assertEquals(ScannerType.USER_DEFINED, counter.getScannerType());
    }

    @Test
    @InSequence(18)
    @RunAsClient
    public void getCounters_RadioNode_PRIMARY1() {

        final Response response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.NE).param(MIM_QUERY, RADIO_NODE_18_Q2_R43A23 + ":EPS").expect()
                .statusCode(SC_OK).when().get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        JsonArray jsonArray = new JsonParser().parse(response.asString()).getAsJsonArray();
        List<CounterTableRow> counters = getCounterTableRows(jsonArray);

        final CounterTableRow counter = findCounter(counters, "QueueTailDrop", "queueHCDroppedOctets");
        Assert.assertNotNull(counter);
        Assert.assertEquals(ScannerType.PRIMARY, counter.getScannerType());
    }

    @Test
    @InSequence(19)
    @RunAsClient
    public void getCounter_get_subgroups_should_return_CPS_and_F4_F5_counter_subgroups_and_counters() {
        final Response response = given().header(USERID_HEADER).expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + COUNTERSUBGROUPS + "MOINSTANCE");

        List<String> cps = response.path("CPS");
        String[] sortedCps = cps.toArray(new String[cps.size()]);
        Arrays.sort(sortedCps);
        Assert.assertArrayEquals(sortedCps, Arrays.asList("pmDiscardedEgressCpsPackets", "pmEgressCpsPackets", "pmIngressCpsPackets").toArray());
    }

    @Test
    @InSequence(20)
    @RunAsClient
    public void getPMCounters_For_CISCO_ASR900_should_return_currect_number_of_node_counter() throws Exception {
        final String mimQuery = CISCO_ASR900_17A;
        final int expectedNumberOfGroups = 153;
        final int expectedNumberOfCounters = 1363;
        executeGetCountersRequestAndAssertOnNumberOfGroupsAndNumberOfCountersOnSuccessfulResponse(mimQuery, expectedNumberOfGroups, expectedNumberOfCounters);
    }

    @Test
    @InSequence(21)
    @RunAsClient
    public void getPMCounters_For_CISCO_ASR9000_should_return_currect_number_of_node_counter() throws Exception {
        final String mimQuery = CISCO_ASR9000_17A;
        final int expectedNumberOfGroups = 151;
        final int expectedNumberOfCounters = 1080;
        executeGetCountersRequestAndAssertOnNumberOfGroupsAndNumberOfCountersOnSuccessfulResponse(mimQuery, expectedNumberOfGroups, expectedNumberOfCounters);
    }

    @Test
    @InSequence(22)
    @RunAsClient
    public void getCounters_RADIO_NODE_19_Q3_R41A26() {
        Response response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.NE).param(MIM_QUERY, RADIO_NODE_19_Q3_R41A26 + ":UMTS#EPS")
                .expect().statusCode(SC_OK).when().get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        List<String> counters = response.path(COUNTER_NAME);
        Assert.assertEquals(5176, counters.size());

        response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.NE).param(MIM_QUERY, RADIO_NODE_19_Q3_R41A26 + ":EPS").expect()
                .statusCode(SC_OK).when().get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        counters = response.path(COUNTER_NAME);
        Assert.assertEquals(4603, counters.size());

        response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.NE).param(MIM_QUERY, RADIO_NODE_19_Q3_R41A26 + ":5GS").expect()
                .statusCode(SC_OK).when().get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        counters = response.path(COUNTER_NAME);
        Assert.assertEquals(830, counters.size());

        response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.OSS).param(MIM_QUERY, RADIO_NODE_19_Q3_R41A26 + ":EPS").expect()
                .statusCode(SC_OK).when().get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        counters = response.path(COUNTER_NAME);
        Assert.assertEquals(774, counters.size());


        final EventBasedCounterTableRow counter = findEventBasedCounter(response, "EUtranCellFDD", "pmAgMeasSuccCgi");
        Assert.assertNotNull(counter);
        Assert.assertEquals(4, counter.getBasedOnEvent().size());

        response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.NE).param(MIM_QUERY, RADIO_NODE_19_Q3_R41A26 + ":UMTS").expect()
                .statusCode(SC_OK).when().get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        counters = response.path(COUNTER_NAME);
        Assert.assertEquals(966, counters.size());

        response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.OSS).param(MIM_QUERY, RADIO_NODE_19_Q3_R41A26 + ":5GS").expect()
                .statusCode(SC_OK).when().get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        counters = response.path(COUNTER_NAME);


        Assert.assertEquals(6, counters.size());
    }

    private void executeGetCountersRequestAndAssertOnNumberOfGroupsAndNumberOfCountersOnSuccessfulResponse(final String mimQuery,
                                                                                                           final int expectedNumberOfGroups, final int expectedNumberOfCounters) {
        final Response response = given().header(USERID_HEADER).param(DEFINER, ModelDefiner.NE)
                .param(MIM_QUERY, mimQuery).expect().statusCode(SC_OK).when()
                .get(SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH + GET_COUNTERS);

        final List<String> counters = response.path(COUNTER_NAME);
        final List<String> groups = response.path(GROUP_NAME);

        final Collection<String> uniqueGroupNames = new HashSet<>(groups);

        Assert.assertEquals(expectedNumberOfGroups, uniqueGroupNames.size());
        Assert.assertEquals(expectedNumberOfCounters, counters.size());
    }

    private List<CounterTableRow> getCounterTableRows(final JsonArray jsonArray) {
        Gson gson = new Gson();
        final List<CounterTableRow> tableRows = new ArrayList<>();
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonElement str = jsonArray.get(i);
            final CounterTableRow counterTableRow = gson.fromJson(str, CounterTableRow.class);
            tableRows.add(counterTableRow);
        }
        return tableRows;
    }

    private List<EventBasedCounterTableRow> getEventBasedCounterTableRows(final JsonArray jsonArray) {
        final Gson gson = new Gson();
        final List<EventBasedCounterTableRow> tableRows = new ArrayList<>();
        for (int i = 0; i < jsonArray.size(); i++) {
            final JsonElement str = jsonArray.get(i);
            final EventBasedCounterTableRow counterTableRow = gson.fromJson(str, EventBasedCounterTableRow.class);
            tableRows.add(counterTableRow);
        }
        return tableRows;
    }

    private CounterTableRow findCounter(final Collection<CounterTableRow> counters, final String counterGroup, final String counterName) {
        for (final CounterTableRow counter : counters) {
            if (counter.getSourceObject().equals(counterGroup) && counter.getCounterName().equals(counterName)) {
                return counter;
            }
        }
        return null;
    }

    private EventBasedCounterTableRow findEventBasedCounter(final Response response,
                                                            final String counterGroup, final String counterName) {

        final JsonArray jsonArray = new JsonParser().parse(response.asString()).getAsJsonArray();
        final List<EventBasedCounterTableRow> eventBasedcounters = getEventBasedCounterTableRows(jsonArray);

        return findEventBasedCounter(eventBasedcounters, counterGroup, counterName);
    }

    private EventBasedCounterTableRow findEventBasedCounter(final Collection<EventBasedCounterTableRow> counters, final String counterGroup,
                                                            final String counterName) {
        for (final EventBasedCounterTableRow counter : counters) {
            if (counter.getSourceObject().equals(counterGroup) && counter.getCounterName().equals(counterName)) {
                return counter;
            }
        }
        return null;
    }
}
