/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
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

import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo;
import com.ericsson.oss.services.pm.initiation.node.data.NodeData;
import com.ericsson.oss.services.pm.integration.RestBaseArquillian;
import com.ericsson.oss.services.pm.integration.test.helpers.JSonUtils;
import com.ericsson.oss.services.pm.integration.test.steps.PibSteps;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;

@RunWith(Arquillian.class)
public class EbsStreamCellTraceSubscriptionTest extends RestBaseArquillian {

    private static final String INPUT_NODE_JSON_FILE = "CellTraceSubscription/CellTraceSubscriptionNodes.json";
    private static final String EBS_CELLTRACE_JSON_WITH_EVENTS_COUNTERS_FILE_STREAM = "CellTraceSubscription/EbsStreamCellTraceSubscription.json";

    private static List<NodeData> nodeDataList;

    @Inject
    private PibSteps pibSteps;

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
     * Setup pmicEbsStreamClusterDeployed PIB Parameter that is required for the next test in sequence
     */
    @Test
    @InSequence(3)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void setupPmicEbsStreamClusterDeployedPibParameter() throws Exception {
        pibSteps.updateConfigParam("pmicEbsStreamClusterDeployed", "true", Boolean.class);
    }

    /**
     * Test basic EBS cell trace subscription with events, ebs events and counters.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testEbsCellTraceSubscriptionWithEventsAndCounters() throws Exception {
        final CellTraceSubscription expectedSubscription = JSonUtils.getJsonMapperObjectFromFile(EBS_CELLTRACE_JSON_WITH_EVENTS_COUNTERS_FILE_STREAM,
                CellTraceSubscription.class);

        enrichNodePoidForSubscrition(expectedSubscription);

        final String pollingUrl = assertThatPostSubscriptionIsSuccessful(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final CellTraceSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(
                subscriptionId, CellTraceSubscription.class);
        Assert.assertEquals(expectedSubscription.getName(), actualSubscription.getName());
        Assert.assertEquals(expectedSubscription.getEbsCounters(), actualSubscription.getEbsCounters());
        Assert.assertEquals(expectedSubscription.getEvents(), actualSubscription.getEvents());
        Assert.assertEquals(expectedSubscription.getEbsEvents(), actualSubscription.getEbsEvents());
        Assert.assertEquals(expectedSubscription.getCellTraceCategory(), actualSubscription.getCellTraceCategory());
        Assert.assertEquals(Arrays.asList(new StreamInfo("10.43.250.4", 10101)), actualSubscription.getEbsStreamInfoList());
        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Cleanup Test Environment by Deleting added Nodes
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void cleanupEnv() throws Exception {
        for (final NodeData node : nodeDataList) {
            nodeCreationHelperBeanEJB.deleteNode(node);
        }
        pibSteps.updateConfigParam("pmicEbsStreamClusterDeployed", "false", Boolean.class);
    }
}
