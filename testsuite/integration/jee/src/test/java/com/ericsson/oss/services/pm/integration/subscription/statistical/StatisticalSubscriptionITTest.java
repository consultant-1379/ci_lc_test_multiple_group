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

package com.ericsson.oss.services.pm.integration.subscription.statistical;

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
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;
import com.ericsson.oss.services.pm.initiation.node.data.NodeData;
import com.ericsson.oss.services.pm.integration.RestBaseArquillian;
import com.ericsson.oss.services.pm.integration.test.helpers.JSonUtils;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;

@RunWith(Arquillian.class)
public class StatisticalSubscriptionITTest extends RestBaseArquillian {

    private static final String STATISTICAL_SUBSCRIPTION_WITH_INCOMPATIBLE_NODES_JSON =
            "StatisticalSubscription/StatisticalSubscriptionWithIncompatibleNodes.json";
    private static final String BSC_TEST_SOURCE_DIR = "/bsc/test/";
    private static final String DATA_TRANSFER_DESTINATIONS_CDHDEFAULT_READY = "/data_transfer/destinations/CDHDEFAULT/Ready/";
    private static final String INPUT_NODE_JSON_FILE = "StatisticalSubscription/StatisticalSubscriptionNodes.json";
    private static final String BASIC_STATISTICAL_JSON_FILE = "StatisticalSubscription/StatisticalSubscription1.json";
    private static final String IMPORT_SIMPLIFIED_BASIC_STATISTICAL_SUBSCRIPTION =
            "StatisticalSubscription/SimplifiedBasicEmptyStatisticalSubscription.json";
    private static final String IMPORT_STATISTICAL_JSON_FILE_WITH_VALID_NODES =
            "StatisticalSubscription/StatisticalSubscriptionImportWithValidNodes.json";
    private static final String IMPORT_SIMPLIFIED_STATISTICAL_SUBSCRIPTION_WITH_VALID_NODES =
            "StatisticalSubscription/SimplifiedStatisticalSubscriptionImportWithValidNodes.json";
    private static final String SIMPLIFIED_STATISTICAL_SUBSCRIPTION_IMPORT_WITH_NODE_NOT_SUPPORTING_COUNTERS_JSON =
            "StatisticalSubscription/SimplifiedStatisticalSubscriptionImportWithNodeNotSupportingCounters.json";
    private static final String STATISTICAL_SUBSCRIPTION_WITH_INCOMPATIBLE_NODE_TYPES =
            "StatisticalSubscription/StatisticalSubscriptionWithInCompatibleNodeTypes.json";
    private static final String STATISTICAL_SUBSCRIPTION_WITH_COMPATIBLE_NODE_TYPES =
            "StatisticalSubscription/StatisticalSubscriptionWithCompatibleNodeTypes.json";

    private static List<NodeData> nodeDataList;

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
     * Test import of basic Statistical subscription with name.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(3)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportBasicStatisticalSubscriptionWithName() throws Exception {
        final StatisticalSubscription expectedSubscription = JSonUtils.getJsonMapperObjectFromFile(BASIC_STATISTICAL_JSON_FILE,
                StatisticalSubscription.class);

        final String pollingUrl = assertThatPostImportedSubscriptionIsSuccessful(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final StatisticalSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(subscriptionId, StatisticalSubscription.class);
        Assert.assertEquals(expectedSubscription.getName(), actualSubscription.getName());

        // get the light subscription object

        final String exportSubscription = assertThatGetSubscriptionByIdForExportIsSuccessful(subscriptionId, StatisticalSubscription.class);

        Assert.assertTrue(exportSubscription.contains("@class"));
        Assert.assertTrue(exportSubscription.contains("name"));
        Assert.assertTrue(exportSubscription.contains("description"));
        Assert.assertTrue(exportSubscription.contains("type"));
        Assert.assertTrue(exportSubscription.contains("scheduleInfo"));
        Assert.assertTrue(exportSubscription.contains("rop"));
        Assert.assertTrue(exportSubscription.contains("nodes"));
        Assert.assertTrue(exportSubscription.contains("counters"));
        Assert.assertTrue(!exportSubscription.contains("activationTime"));
        Assert.assertTrue(!exportSubscription.contains("cbs"));
        Assert.assertTrue(!exportSubscription.contains("criteriaSpecification"));
        Assert.assertTrue(!exportSubscription.contains("nodeListIdentity"));
        Assert.assertTrue(!exportSubscription.contains("selectedNeTypes"));
        Assert.assertTrue(!exportSubscription.contains("userDeActivationDateTime"));

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Test import of basic Statistical subscription with name already present is rejected.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportBasicStatisticalSubscriptionWithNameRejected() throws Exception {
        final StatisticalSubscription expectedSubscription = JSonUtils.getJsonMapperObjectFromFile(BASIC_STATISTICAL_JSON_FILE,
                StatisticalSubscription.class);

        String pollingUrl = assertThatPostImportedSubscriptionIsSuccessful(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final StatisticalSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(subscriptionId, StatisticalSubscription.class);
        Assert.assertEquals(expectedSubscription.getName(), actualSubscription.getName());

        // checks that imported subscription with duplicated subscription name is rejected
        pollingUrl = assertThatPostImportedSubscriptionIsRejected(expectedSubscription);
        Assert.assertNull(pollingUrl);

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Test import of basic Statistical subscription with valid nodes.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportBasicStatisticalSubscriptionWithValidNodes() throws Exception {
        final StatisticalSubscription expectedSubscription = JSonUtils.getJsonMapperObjectFromFile(IMPORT_STATISTICAL_JSON_FILE_WITH_VALID_NODES,
                StatisticalSubscription.class);

        final String pollingUrl = assertThatPostImportedSubscriptionIsSuccessful(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final StatisticalSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(subscriptionId, StatisticalSubscription.class);
        Assert.assertEquals(expectedSubscription.getName(), actualSubscription.getName());
        Assert.assertTrue(actualSubscription.getNodes().size() == 1);
        Assert.assertTrue(actualSubscription.getNodes().get(0).getNeType().toString().equalsIgnoreCase("RNC"));
        Assert.assertTrue(actualSubscription.getNodes().get(0).getOssModelIdentity().equalsIgnoreCase("16B-V.7.1659"));

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Test import of simplified basic Statistical subscription
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(6)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportSimplifiedBasicStatisticalSubscription() throws Exception {

        final StatisticalSubscription expectedSubscription = JSonUtils.getJsonObjectFromSimplifiedFile(
                IMPORT_SIMPLIFIED_BASIC_STATISTICAL_SUBSCRIPTION, SubscriptionType.STATISTICAL, StatisticalSubscription.class);

        final String pollingUrl = assertThatPostSimplifiedImportedSubscriptionIsSuccessful(
                Resources.getClasspathResource(IMPORT_SIMPLIFIED_BASIC_STATISTICAL_SUBSCRIPTION).getAsText());
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final StatisticalSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(subscriptionId, StatisticalSubscription.class);

        Assert.assertEquals(expectedSubscription.getName(), actualSubscription.getName());
        Assert.assertNull(expectedSubscription.getIdAsString());
        Assert.assertNotNull(actualSubscription.getIdAsString());
        Assert.assertNull(expectedSubscription.getOwner());
        Assert.assertNotNull(actualSubscription.getOwner());
        Assert.assertNull(expectedSubscription.getUserType());
        Assert.assertEquals(UserType.USER_DEF, actualSubscription.getUserType());
        Assert.assertEquals(AdministrationState.INACTIVE, actualSubscription.getAdministrationState());
        Assert.assertEquals(expectedSubscription.getCounters(), actualSubscription.getCounters());
        Assert.assertEquals(expectedSubscription.getNodes(), actualSubscription.getNodes());

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Test import of simplified Statistical subscription with nodes
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(7)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportSimplifiedStatisticalSubscriptionWithValidNodes() throws Exception {

        final StatisticalSubscription expectedSubscription = JSonUtils
                .getJsonMapperObjectFromFile(IMPORT_SIMPLIFIED_STATISTICAL_SUBSCRIPTION_WITH_VALID_NODES, StatisticalSubscription.class);

        final String pollingUrl = assertThatPostImportedSubscriptionIsSuccessful(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final StatisticalSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(subscriptionId, StatisticalSubscription.class);

        Assert.assertEquals(expectedSubscription.getName(), actualSubscription.getName());
        Assert.assertTrue(actualSubscription.getNodes().size() == 1);
        Assert.assertTrue(actualSubscription.getNodes().get(0).getNeType().toString().equalsIgnoreCase("RNC"));
        Assert.assertTrue(actualSubscription.getNodes().get(0).getOssModelIdentity().equalsIgnoreCase("16B-V.7.1659"));
        Assert.assertTrue(actualSubscription.getNodes().get(0).getName().equalsIgnoreCase("RNC03"));
        Assert.assertNotNull(actualSubscription.getNodes().get(0).getIdAsString());

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Test import of simplified Statistical subscription with invalid nodes
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(8)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportSimplifiedStatisticalSubscriptionWithInvalidNodesFdn() throws Exception {

        final StatisticalSubscription expectedSubscription = JSonUtils
                .getJsonMapperObjectFromFile(IMPORT_SIMPLIFIED_STATISTICAL_SUBSCRIPTION_WITH_VALID_NODES, StatisticalSubscription.class);

        replaceNodeListWithInvalidFdn(expectedSubscription);
        expectedSubscription.setName("Subscription with Invalid Node");

        final String pollingUrl = assertThatPostImportedSubscriptionIsRejected(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertTrue(subscriptionId.equals("0"));
    }

    /**
     * Test import of simplified Statistical subscription with rop period and nodes type incompatible
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(9)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportSimplifiedStatisticalSubscriptionWithIncompatibleRopPeriod() throws Exception {

        final StatisticalSubscription expectedSubscription = JSonUtils
                .getJsonMapperObjectFromFile(IMPORT_SIMPLIFIED_STATISTICAL_SUBSCRIPTION_WITH_VALID_NODES, StatisticalSubscription.class);

        expectedSubscription.setRop(RopPeriod.FIVE_MIN);
        expectedSubscription.setName("Subscription with Incompatible ROP");

        final String pollingUrl = assertThatPostImportedSubscriptionIsRejected(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertTrue(subscriptionId.equals("0"));
    }

    /**
     * Test import of simplified Statistical subscription with invalid nodes and valid counters
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(10)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportSimplifiedStatisticalSubscriptionWithCountersAndInvalidNodes() throws Exception {

        final StatisticalSubscription expectedSubscription = JSonUtils
                .getJsonMapperObjectFromFile(IMPORT_SIMPLIFIED_STATISTICAL_SUBSCRIPTION_WITH_VALID_NODES, StatisticalSubscription.class);

        expectedSubscription.setName("Subscription with counters and invalid nodes");
        replaceNodeListWithInvalidFdn(expectedSubscription);
        final List<CounterInfo> counters = new ArrayList<>();
        counters.add(new CounterInfo("pmBbmDlPrbUtilization", "BbProcessingResource"));
        counters.add(new CounterInfo("pmBbmUlSeUtilization", "BbProcessingResource"));
        expectedSubscription.setCounters(counters);
        final String pollingUrl = assertThatPostImportedSubscriptionIsRejected(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertTrue(subscriptionId.equals("0"));
    }

    /**
     * Test import of simplified Statistical subscription for nodes not supporting counters. If capability value for counterEventValidationRequired is
     * false, the validation process reject subscription creation specifying counters
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(11)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testStatisticalSubscriptionForNodesWithNoCountersSupport() throws Exception {

        final StatisticalSubscription expectedSubscription = JSonUtils.getJsonMapperObjectFromFile(
                SIMPLIFIED_STATISTICAL_SUBSCRIPTION_IMPORT_WITH_NODE_NOT_SUPPORTING_COUNTERS_JSON, StatisticalSubscription.class);

        expectedSubscription.setName("Subscription with nodes not supporting counters");
        final List<CounterInfo> counters = new ArrayList<>();
        counters.add(new CounterInfo("pmBbmDlPrbUtilization", "BbProcessingResource"));
        counters.add(new CounterInfo("pmBbmUlSeUtilization", "BbProcessingResource"));
        expectedSubscription.setCounters(counters);
        final String pollingUrl = assertThatPostImportedSubscriptionIsRejected(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertTrue(subscriptionId.equals("0"));
    }

    /**
     * Test PmFunction extension node sourceDir specification for nodes supporting it (BSC)
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(12)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testPmFunctionExtensionDefaultSourceDir() throws Exception {
        final NodeData bscNode = nodeDataList.get(1);
        final String sourceDir = nodeCreationHelperBeanEJB.getPmFunctionExtsourceDir("NetworkElement=" + bscNode.getNodeName());
        Assert.assertTrue(sourceDir.equals(DATA_TRANSFER_DESTINATIONS_CDHDEFAULT_READY));
    }

    /**
     * Test PmFunction extension node sourceDir change for nodes supporting it (BSC)
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(13)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testPmFunctionExtensionSetSourceDir() throws Exception {
        final NodeData bscNode = nodeDataList.get(1);
        String sourceDir = nodeCreationHelperBeanEJB.getPmFunctionExtsourceDir("NetworkElement=" + bscNode.getNodeName());
        Assert.assertTrue(sourceDir.equals(DATA_TRANSFER_DESTINATIONS_CDHDEFAULT_READY));
        nodeCreationHelperBeanEJB.changePmFunctionExtsourceDir("NetworkElement=" + bscNode.getNodeName(), BSC_TEST_SOURCE_DIR);
        sourceDir = nodeCreationHelperBeanEJB.getPmFunctionExtsourceDir("NetworkElement=" + bscNode.getNodeName());
        Assert.assertTrue(sourceDir.equals(BSC_TEST_SOURCE_DIR));
    }

    @Test
    @InSequence(14)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportStatisticalSubscriptionWithInvalidCompatiblenodes() throws Exception {

        final StatisticalSubscription expectedSubscription = JSonUtils
                .getJsonMapperObjectFromFile(STATISTICAL_SUBSCRIPTION_WITH_INCOMPATIBLE_NODE_TYPES, StatisticalSubscription.class);

        final String pollingUrl = assertThatPostImportedSubscriptionIsRejected(expectedSubscription);
        Assert.assertNull(pollingUrl);
    }

    /**
     * Test import of Statistical subscription with compatible nodes
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(15)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportStatisticalSubscriptionWithCompatibleNodes() throws Exception {

        final StatisticalSubscription expectedSubscription = JSonUtils
                .getJsonMapperObjectFromFile(STATISTICAL_SUBSCRIPTION_WITH_COMPATIBLE_NODE_TYPES, StatisticalSubscription.class);

        final String pollingUrl = assertThatPostImportedSubscriptionIsSuccessful(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final StatisticalSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(subscriptionId, StatisticalSubscription.class);

        Assert.assertEquals(expectedSubscription.getName(), actualSubscription.getName());
        Assert.assertTrue(actualSubscription.getNodes().size() == 1);
        Assert.assertTrue(actualSubscription.getNodes().get(0).getName().equalsIgnoreCase("ESA01"));

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Cleanup Test Environment by Deleting added Nodes
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(16)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void cleanupEnv() throws Exception {
        for (final NodeData node : nodeDataList) {
            nodeCreationHelperBeanEJB.deleteNode(node);
        }
    }

    /**
     * @param expectedSubscription
     */
    private void replaceNodeListWithInvalidFdn(final StatisticalSubscription expectedSubscription) {
        final Node node = expectedSubscription.getNodes().get(0);
        final String invalidNodeFdn = node.getFdn().replace("NetworkElement", "InvalidMO");
        node.setFdn(invalidNodeFdn);
        final List<Node> nodes = new ArrayList<Node>();
        nodes.add(node);
        expectedSubscription.setNodes(nodes);
    }
}
