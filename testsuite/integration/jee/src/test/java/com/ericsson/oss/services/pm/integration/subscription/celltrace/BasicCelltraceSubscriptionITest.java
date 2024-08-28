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

package com.ericsson.oss.services.pm.integration.subscription.celltrace;

import static io.restassured.http.ContentType.JSON;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.oss.itpf.sdk.resources.Resources;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionOperationMessageSender;
import com.ericsson.oss.services.pm.initiation.node.data.NodeData;
import com.ericsson.oss.services.pm.integration.RestBaseArquillian;
import com.ericsson.oss.services.pm.integration.test.helpers.JSonUtils;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;

@RunWith(Arquillian.class)
public class BasicCelltraceSubscriptionITest extends RestBaseArquillian {

    private static final String INPUT_NODE_JSON_FILE = "CellTraceSubscription/CellTraceSubscriptionNodes.json";
    private static final String BASIC_CELLTRACE_JSON_FILE = "CellTraceSubscription/CellTraceSubscription1.json";
    private static final String CELLTRACE_JSON_WITH_EVENTS_FILE = "CellTraceSubscription/CellTraceSubscription2.json";
    private static final String CELLTRACE_JSON_WITH_EVENTS_COUNTERS_FILE = "CellTraceSubscription/CellTraceSubscription3.json";
    private static final String CELLTRACE_IMPORT_JSON_WITH_INVALID_NODES_FILE =
            "CellTraceSubscription/CellTraceSubscriptionImportWithInvalidNodes.json";
    private static final String CELLTRACE_JSON_REDUCED = "CellTraceSubscription/CellTraceSubscriptionReduced.json";

    private static List<NodeData> nodeDataList;

    @Inject
    private SubscriptionOperationMessageSender sender;

    /**
     * Sets the up. Converts Node json file to NodeData Array
     *
     * @throws Exception
     *             the exception
     */
    @Before
    public void setUp() throws Exception {
        RestAssured.requestSpecification = new RequestSpecBuilder().build().baseUri(CONTAINER_BASE_URL).accept(JSON).contentType(JSON).log().all();
        nodeDataList = Arrays.asList(JSonUtils.getJsonMapperObjectFromFile(INPUT_NODE_JSON_FILE, NodeData[].class));
    }

    /**
     * Setup Test Environment, Add Nodes for Test.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(2)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void setupEnv() throws Exception {
        for (final NodeData node : nodeDataList) {
            nodeCreationHelperBeanEJB.createNode(node);
        }
    }

    /**
     * Test basic cell trace subscription with name and description.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(3)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testBasicCellTraceSubscriptionWithNameAndDescription() throws Exception {
        final CellTraceSubscription expectedSubscription = JSonUtils.getJsonMapperObjectFromFile(BASIC_CELLTRACE_JSON_FILE,
                CellTraceSubscription.class);

        final String pollingUrl = assertThatPostSubscriptionIsSuccessful(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(
                subscriptionId, com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription.class);
        Assert.assertEquals(expectedSubscription.getName(), actualSubscription.getName());

        // get the light subscription object
        final String exportSubscription = assertThatGetSubscriptionByIdForExportIsSuccessful(subscriptionId, CellTraceSubscription.class);

        assertJsonContainsFields(exportSubscription, "@class", "name", "description", "type", "scheduleInfo", "rop", "nodes",
                "outputMode", "ueFraction", "asnEnabled", "ebsCounters", "events", "cellTraceCategory");
        //TODO should remove ebsEnabled from field list?
        assertJsonDoesNotContainsFields(exportSubscription, "compressionEnabled", "ebsStreamInfoList", "ebsEnabled", "ebsEvents",
                "activationTime", "deactivationTime", "cbs", "criteriaSpecification", "nodeFilter", "nodeListIdentity",
                "selectedNeTypes", "userActivationDateTime", "userDeActivationDateTime");

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    private void assertJsonContainsFields(final String jsonText, final String... expectedFieldNames) {
        for (final String expectedFieldName : expectedFieldNames) {
            Assert.assertTrue(jsonText.contains(expectedFieldName));
        }
    }

    private void assertJsonDoesNotContainsFields(final String jsonText, final String... transientFieldNames) {
        for (final String transientFieldName : transientFieldNames) {
            Assert.assertFalse(jsonText.contains(transientFieldName));
        }
    }

    /**
     * Test basic cell trace subscription with name.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportBasicCellTraceSubscriptionWithName() throws Exception {
        final CellTraceSubscription expectedSubscription = JSonUtils.getJsonMapperObjectFromFile(BASIC_CELLTRACE_JSON_FILE,
                CellTraceSubscription.class);

        final String pollingUrl = assertThatPostImportedSubscriptionIsSuccessful(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final CellTraceSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(
                subscriptionId, CellTraceSubscription.class);
        Assert.assertEquals(expectedSubscription.getName(), actualSubscription.getName());

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Test basic cell trace subscription with name already present is rejected.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportBasicCellTraceSubscriptionWithNameRejected() throws Exception {
        final CellTraceSubscription expectedSubscription = JSonUtils.getJsonMapperObjectFromFile(BASIC_CELLTRACE_JSON_FILE,
                CellTraceSubscription.class);

        String pollingUrl = assertThatPostImportedSubscriptionIsSuccessful(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final CellTraceSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(
                subscriptionId, CellTraceSubscription.class);
        Assert.assertEquals(expectedSubscription.getName(), actualSubscription.getName());

        // checks that imported subscription with duplicated subscription name is rejected
        pollingUrl = assertThatPostImportedSubscriptionIsRejected(expectedSubscription);
        Assert.assertNull(pollingUrl);

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Test imported basic cell trace subscription with nodes.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(6)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportBasicCellTraceSubscriptionWithValidAndInvalidNodes() throws Exception {
        final CellTraceSubscription expectedSubscription = JSonUtils.getJsonMapperObjectFromFile(CELLTRACE_IMPORT_JSON_WITH_INVALID_NODES_FILE,
                CellTraceSubscription.class);

        final String pollingUrl = assertThatPostImportedSubscriptionIsRejected(expectedSubscription);
        Assert.assertNull(pollingUrl);
    }

    /**
     * Test basic cell trace subscription with events.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(7)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testBasicCellTraceSubscriptionWithEvents() throws Exception {
        final CellTraceSubscription expectedSubscription = JSonUtils.getJsonMapperObjectFromFile(CELLTRACE_JSON_WITH_EVENTS_FILE,
                CellTraceSubscription.class);

        enrichNodePoidForSubscrition(expectedSubscription);

        final String pollingUrl = assertThatPostSubscriptionIsSuccessful(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(
                subscriptionId, com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription.class);
        Assert.assertEquals(expectedSubscription.getName(), actualSubscription.getName());
        Assert.assertEquals(expectedSubscription.getEvents(), actualSubscription.getEvents());
        Assert.assertEquals(CellTraceCategory.CELLTRACE, actualSubscription.getCellTraceCategory());

        // delete subscription
        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Test basic cell trace subscription with events and counters.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(8)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testBasicCellTraceSubscriptionWithEventsAndCounters() throws Exception {
        final CellTraceSubscription expectedSubscription = JSonUtils.getJsonMapperObjectFromFile(CELLTRACE_JSON_WITH_EVENTS_COUNTERS_FILE,
                CellTraceSubscription.class);

        enrichNodePoidForSubscrition(expectedSubscription);

        final String pollingUrl = assertThatPostSubscriptionIsSuccessful(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(
                subscriptionId, com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription.class);
        Assert.assertEquals(expectedSubscription.getName(), actualSubscription.getName());
        Assert.assertEquals(expectedSubscription.getEbsCounters(), actualSubscription.getEbsCounters());
        Assert.assertEquals(expectedSubscription.getEvents(), actualSubscription.getEvents());
        Assert.assertEquals(expectedSubscription.getCellTraceCategory(), actualSubscription.getCellTraceCategory());

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Test Import cell trace subscription using reduced json file .
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(9)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportCellTraceSubscriptionReducedWithEventsAndCounters() throws Exception {
        final CellTraceSubscription expectedSubscription =
                JSonUtils.getJsonObjectFromSimplifiedFile(CELLTRACE_JSON_REDUCED, SubscriptionType.CELLTRACE, CellTraceSubscription.class);

        enrichNodePoidForSubscrition(expectedSubscription);

        final String pollingUrl =
                assertThatPostSimplifiedImportedSubscriptionIsSuccessful(Resources.getClasspathResource(CELLTRACE_JSON_REDUCED).getAsText());
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(
                subscriptionId, com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription.class);
        Assert.assertEquals(expectedSubscription.getName(), actualSubscription.getName());
        Assert.assertEquals(expectedSubscription.getEbsCounters(), actualSubscription.getEbsCounters());
        Assert.assertEquals(expectedSubscription.getEvents(), actualSubscription.getEvents());
        Assert.assertEquals(CellTraceCategory.CELLTRACE, actualSubscription.getCellTraceCategory());

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Cleanup Test Environment by Deleting added Nodes
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(10)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void cleanupEnv() throws Exception {
        for (final NodeData node : nodeDataList) {
            nodeCreationHelperBeanEJB.deleteNode(node);
        }
    }
}
