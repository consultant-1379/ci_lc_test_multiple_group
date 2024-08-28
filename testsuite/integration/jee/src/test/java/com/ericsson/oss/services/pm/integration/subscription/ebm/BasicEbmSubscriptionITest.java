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

package com.ericsson.oss.services.pm.integration.subscription.ebm;

import static io.restassured.http.ContentType.JSON;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.oss.itpf.sdk.resources.Resources;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.EbmSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.EbsOutputStrategy;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;
import com.ericsson.oss.services.pm.initiation.node.data.NodeData;
import com.ericsson.oss.services.pm.integration.RestBaseArquillian;
import com.ericsson.oss.services.pm.integration.test.helpers.JSonUtils;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;

@RunWith(Arquillian.class)
public class BasicEbmSubscriptionITest extends RestBaseArquillian {

    private static final String EBM_INPUT_NODE_JSON_FILE = "EBMSubscription/EbmSubscriptionNodes.json";
    private static final String INPUT_SUBSCRPTION_JSON_FILE = "EBMSubscription/BasicEbmSubscription.json";
    private static final String SIMPLIFIED_INPUT_SUBSCRPTION_JSON_FILE = "EBMSubscription/SimplifiedBasicEbmSubscription.json";
    private static final String SIMPLIFIED_INPUT_SUBSCRPTION_JSON_FILE_WITH_VALID_NODES =
            "EBMSubscription/SimplifiedBasicEbmSubscriptionWithNodes.json";
    private static final String SIMPLIFIED_INPUT_SUBSCRPTION_JSON_FILE_WITH_VALID_NODES_COUNTERS_EVENTS =
            "EBMSubscription/SimplifiedBasicEbmSubscriptionWithNodesAndCounters.json";

    private static EbmSubscription ebmSubscription;

    private static List<NodeData> nodeDataList;

    @Before
    public void setUp() throws Exception {
        RestAssured.requestSpecification = new RequestSpecBuilder().build().baseUri(CONTAINER_BASE_URL).accept(JSON).contentType(JSON).log().all();
        nodeDataList = Arrays.asList(JSonUtils.getJsonMapperObjectFromFile(EBM_INPUT_NODE_JSON_FILE, NodeData[].class));
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

    @Test
    @InSequence(3)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testBasicEbmSubscriptionWithNameAndDescription() throws Exception {
        final EbmSubscription ebmSubscription = JSonUtils.getJsonMapperObjectFromFile(INPUT_SUBSCRPTION_JSON_FILE, EbmSubscription.class);
        // create subscription
        final String pollingUrl = assertThatPostSubscriptionIsSuccessful(ebmSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(ebmSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final com.ericsson.oss.pmic.dto.subscription.EbmSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(subscriptionId,
                com.ericsson.oss.pmic.dto.subscription.EbmSubscription.class);
        Assert.assertEquals(ebmSubscription.getName(), actualSubscription.getName());

        // get the light subscription object

        final String exportSubscription = assertThatGetSubscriptionByIdForExportIsSuccessful(subscriptionId, EbmSubscription.class);

        Assert.assertTrue(exportSubscription.contains("@class"));
        Assert.assertTrue(exportSubscription.contains("name"));
        Assert.assertTrue(exportSubscription.contains("description"));
        Assert.assertTrue(exportSubscription.contains("type"));
        Assert.assertTrue(exportSubscription.contains("scheduleInfo"));
        Assert.assertTrue(exportSubscription.contains("rop"));
        Assert.assertTrue(exportSubscription.contains("nodes"));
        Assert.assertTrue(!exportSubscription.contains("compressionEnabled"));
        Assert.assertTrue(exportSubscription.contains("outputMode"));
        Assert.assertTrue(exportSubscription.contains("streamInfoList"));
        Assert.assertTrue(exportSubscription.contains("ebsCounters"));
        Assert.assertTrue(exportSubscription.contains("ebsOutputStrategy"));
        Assert.assertTrue(!exportSubscription.contains("ebsOutputInterval"));
        Assert.assertTrue(!exportSubscription.contains("ebsEnabled"));
        Assert.assertTrue(exportSubscription.contains("events"));
        Assert.assertTrue(!exportSubscription.contains("activationTime"));
        Assert.assertTrue(!exportSubscription.contains("deactivationTime"));
        Assert.assertTrue(!exportSubscription.contains("cbs"));
        Assert.assertTrue(!exportSubscription.contains("criteriaSpecification"));
        Assert.assertTrue(!exportSubscription.contains("nodeFilter"));
        Assert.assertTrue(!exportSubscription.contains("nodeListIdentity"));
        Assert.assertTrue(!exportSubscription.contains("selectedNeTypes"));
        Assert.assertTrue(!exportSubscription.contains("userActivationDateTime"));
        Assert.assertTrue(!exportSubscription.contains("userDeActivationDateTime"));

        // delete subscription
        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportSimplifiedBasicEbmSubscription() throws Exception {

        final EbmSubscription ebmSubscription = JSonUtils.getJsonObjectFromSimplifiedFile(SIMPLIFIED_INPUT_SUBSCRPTION_JSON_FILE,
                SubscriptionType.EBM, EbmSubscription.class);

        final String pollingUrl = assertThatPostSimplifiedImportedSubscriptionIsSuccessful(
                Resources.getClasspathResource(SIMPLIFIED_INPUT_SUBSCRPTION_JSON_FILE).getAsText());
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(ebmSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final EbmSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(subscriptionId, EbmSubscription.class);

        Assert.assertEquals(ebmSubscription.getName(), actualSubscription.getName());
        Assert.assertNull(ebmSubscription.getIdAsString());
        Assert.assertNotNull(actualSubscription.getIdAsString());
        Assert.assertNull(ebmSubscription.getOwner());
        Assert.assertNotNull(actualSubscription.getOwner());
        Assert.assertNull(ebmSubscription.getUserType());
        Assert.assertEquals(UserType.USER_DEF, actualSubscription.getUserType());
        Assert.assertEquals(AdministrationState.INACTIVE, actualSubscription.getAdministrationState());
        Assert.assertEquals(ebmSubscription.getEbsCounters(), actualSubscription.getEbsCounters());
        Assert.assertEquals(ebmSubscription.getNodes(), actualSubscription.getNodes());

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Test import of basic EBM subscription with valid nodes.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportBasicEbmSubscriptionWithValidNodes() throws Exception {

        final EbmSubscription ebmSubscription = JSonUtils.getJsonObjectFromSimplifiedFile(SIMPLIFIED_INPUT_SUBSCRPTION_JSON_FILE_WITH_VALID_NODES,
                SubscriptionType.EBM, EbmSubscription.class);

        final String pollingUrl = assertThatPostSimplifiedImportedSubscriptionIsSuccessful(
                Resources.getClasspathResource(SIMPLIFIED_INPUT_SUBSCRPTION_JSON_FILE_WITH_VALID_NODES).getAsText());
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(ebmSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final EbmSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(subscriptionId, EbmSubscription.class);
        Assert.assertEquals(ebmSubscription.getName(), actualSubscription.getName());
        Assert.assertEquals(1, actualSubscription.getNodes().size());
        Assert.assertTrue(actualSubscription.getNodes().get(0).getNeType().equalsIgnoreCase("SGSN-MME"));
        Assert.assertTrue(actualSubscription.getNodes().get(0).getOssModelIdentity().equalsIgnoreCase("16A-CP02"));

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    @Test
    @InSequence(6)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportBasicEbmSubscriptionWithValidRopPeriodAndNodes() throws Exception {

        final EbmSubscription ebmSubscription = JSonUtils.getJsonObjectFromSimplifiedFile(SIMPLIFIED_INPUT_SUBSCRPTION_JSON_FILE_WITH_VALID_NODES,
                SubscriptionType.EBM, EbmSubscription.class);
        ebmSubscription.setRop(RopPeriod.ONE_MIN);
        Assert.assertEquals(RopPeriod.ONE_MIN, ebmSubscription.getRop());

        final String pollingUrl = assertThatPostImportedSubscriptionIsSuccessful(ebmSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(ebmSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final EbmSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(subscriptionId, EbmSubscription.class);
        Assert.assertEquals(ebmSubscription.getName(), actualSubscription.getName());
        Assert.assertEquals(RopPeriod.ONE_MIN, actualSubscription.getRop());
        Assert.assertEquals(RopPeriod.FIFTEEN_MIN, actualSubscription.getEbsOutputInterval());
        Assert.assertTrue(actualSubscription.getNodes().size() == 1);
        Assert.assertTrue(actualSubscription.getNodes().get(0).getNeType().toString().equalsIgnoreCase("SGSN-MME"));
        Assert.assertTrue(actualSubscription.getNodes().get(0).getOssModelIdentity().equalsIgnoreCase("16A-CP02"));

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    @Test
    @InSequence(7)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportBasicEbmSubscriptionWithValidRopPeriodAndNoNodes() throws Exception {

        final EbmSubscription ebmSubscription = JSonUtils.getJsonObjectFromSimplifiedFile(SIMPLIFIED_INPUT_SUBSCRPTION_JSON_FILE,
                SubscriptionType.EBM, EbmSubscription.class);
        ebmSubscription.setRop(RopPeriod.TEN_SECONDS);

        final String pollingUrl = assertThatPostImportedSubscriptionIsSuccessful(ebmSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(ebmSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final EbmSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(subscriptionId, EbmSubscription.class);
        Assert.assertEquals(ebmSubscription.getName(), actualSubscription.getName());
        Assert.assertEquals(RopPeriod.TEN_SECONDS, actualSubscription.getRop());
        Assert.assertEquals(RopPeriod.FIFTEEN_MIN, actualSubscription.getEbsOutputInterval());

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    @Test
    @InSequence(8)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportBasicEbmSubscriptionWithInvalidOutputMode() throws Exception {

        final EbmSubscription ebmSubscription = JSonUtils.getJsonObjectFromSimplifiedFile(SIMPLIFIED_INPUT_SUBSCRPTION_JSON_FILE,
                SubscriptionType.EBM, EbmSubscription.class);

        ebmSubscription.setOutputMode(OutputModeType.FILE_AND_STREAMING);
        final List<StreamInfo> streamInfoList = new ArrayList<>();
        final StreamInfo streamInfo = new StreamInfo("127.0.0.1", 80);
        streamInfoList.add(streamInfo);
        ebmSubscription.setStreamInfoList(streamInfoList);

        final String pollingUrl = assertThatPostImportedSubscriptionIsRejected(ebmSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(ebmSubscription.getName());
        Assert.assertTrue(subscriptionId.equals("0"));
    }

    @Test
    @InSequence(9)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportBasicEbmSubscriptionAndCheckCompressionEnable() throws Exception {

        final EbmSubscription ebmSubscription = JSonUtils.getJsonObjectFromSimplifiedFile(SIMPLIFIED_INPUT_SUBSCRPTION_JSON_FILE,
                SubscriptionType.EBM, EbmSubscription.class);

        ebmSubscription.setEbsOutputStrategy(EbsOutputStrategy.TGPP_GZ);

        final String pollingUrl = assertThatPostImportedSubscriptionIsSuccessful(ebmSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(ebmSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final EbmSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(subscriptionId, EbmSubscription.class);
        Assert.assertEquals(ebmSubscription.getName(), actualSubscription.getName());
        Assert.assertEquals(EbsOutputStrategy.TGPP_GZ, actualSubscription.getEbsOutputStrategy());
        Assert.assertTrue(actualSubscription.isCompressionEnabled());

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    @Test
    @InSequence(10)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportBasicEbmSubscriptionWithInvalidNodes() throws Exception {

        final EbmSubscription ebmSubscription = JSonUtils.getJsonObjectFromSimplifiedFile(SIMPLIFIED_INPUT_SUBSCRPTION_JSON_FILE_WITH_VALID_NODES,
                SubscriptionType.EBM, EbmSubscription.class);

        final List<Node> nodes = new ArrayList<>();
        final Node node1 = new Node();
        final Node node2 = new Node();
        node1.setFdn("InvalidMo=SGSN-16A-CP02-V301");
        nodes.add(node1);
        node2.setFdn("NetworkElement=Not Existent");
        nodes.add(node2);
        ebmSubscription.setNodes(nodes);

        final String pollingUrl = assertThatPostImportedSubscriptionIsRejected(ebmSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(ebmSubscription.getName());
        Assert.assertTrue(subscriptionId.equals("0"));
    }

    @Test
    @InSequence(11)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportBasicEbmSubscriptionWithInvalidRopPeriod() throws Exception {

        final EbmSubscription ebmSubscription = JSonUtils.getJsonObjectFromSimplifiedFile(SIMPLIFIED_INPUT_SUBSCRPTION_JSON_FILE_WITH_VALID_NODES,
                SubscriptionType.EBM, EbmSubscription.class);
        ebmSubscription.setRop(RopPeriod.TEN_SECONDS);
        ebmSubscription.setEbsOutputInterval(RopPeriod.TEN_SECONDS);

        final String pollingUrl = assertThatPostImportedSubscriptionIsRejected(ebmSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(ebmSubscription.getName());
        Assert.assertTrue(subscriptionId.equals("0"));
    }

    @Test
    @InSequence(12)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportBasicEbmSubscriptionWithCounters() throws Exception {

        final EbmSubscription ebmSubscription = JSonUtils.getJsonObjectFromSimplifiedFile(
                SIMPLIFIED_INPUT_SUBSCRPTION_JSON_FILE_WITH_VALID_NODES_COUNTERS_EVENTS, SubscriptionType.EBM, EbmSubscription.class);

        final String pollingUrl = assertThatPostImportedSubscriptionIsSuccessful(ebmSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(ebmSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final EbmSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(subscriptionId, EbmSubscription.class);
        Assert.assertEquals(ebmSubscription.getName(), actualSubscription.getName());
        Assert.assertTrue(actualSubscription.getEbsCounters().size() == 2);
        Assert.assertTrue(actualSubscription.getEvents().size() == 1);
        Assert.assertTrue(actualSubscription.getNodes().size() == 1);
        Assert.assertTrue(actualSubscription.getNodes().get(0).getNeType().toString().equalsIgnoreCase("SGSN-MME"));
        Assert.assertTrue(actualSubscription.getNodes().get(0).getOssModelIdentity().equalsIgnoreCase("16A-CP02"));

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Cleanup Test Environment by Deleting added Nodes
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(13)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void cleanupEnv() throws Exception {
        for (final NodeData node : nodeDataList) {
            nodeCreationHelperBeanEJB.deleteNode(node);
        }
    }
}
