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

package com.ericsson.oss.services.pm.integration.subscription.moinstance;

import static io.restassured.http.ContentType.JSON;

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
import com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription;
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
public class MoinstanceSubscriptionITTest extends RestBaseArquillian {

    private static final String INPUT_NODE_JSON_FILE = "MoinstanceSubscription/MoinstanceSubscriptionNodes.json";
    private static final String BASIC_MOINSTANCE_JSON_FILE = "MoinstanceSubscription/MoinstanceSubscription1.json";
    private static final String MOINSTACNE_SUB_JSON_WITH_COUNTERS_MOINSTANCE_FILE = "MoinstanceSubscription/MoinstanceSubscription2.json";
    private static final String MOINSTANCE_SUB_JSON_REDUCED = "MoinstanceSubscription/MoInstanceReduced.json";

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
     * Test basic moinstances subscription with name and description.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(3)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testBasicMoinstanceSubscriptionWithNameAndDescription() throws Exception {
        final MoinstanceSubscription expectedSubscription = JSonUtils.getJsonMapperObjectFromFile(BASIC_MOINSTANCE_JSON_FILE,
                MoinstanceSubscription.class);

        final String pollingUrl = assertThatPostSubscriptionIsSuccessful(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(
                subscriptionId, com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription.class);
        Assert.assertEquals(expectedSubscription.getName(), actualSubscription.getName());

        // get the light subscription object

        final String exportSubscription = assertThatGetSubscriptionByIdForExportIsSuccessful(subscriptionId, MoinstanceSubscription.class);

        Assert.assertTrue(exportSubscription.contains("@class"));
        Assert.assertTrue(exportSubscription.contains("name"));
        Assert.assertTrue(exportSubscription.contains("description"));
        Assert.assertTrue(exportSubscription.contains("type"));
        Assert.assertTrue(exportSubscription.contains("scheduleInfo"));
        Assert.assertTrue(exportSubscription.contains("rop"));
        Assert.assertTrue(exportSubscription.contains("nodes"));
        Assert.assertTrue(exportSubscription.contains("counters"));
        Assert.assertTrue(exportSubscription.contains("moInstances"));
        Assert.assertTrue(!exportSubscription.contains("activationTime"));
        Assert.assertTrue(!exportSubscription.contains("cbs"));
        Assert.assertTrue(!exportSubscription.contains("criteriaSpecification"));
        Assert.assertTrue(!exportSubscription.contains("nodeListIdentity"));
        Assert.assertTrue(!exportSubscription.contains("selectedNeTypes"));
        Assert.assertTrue(!exportSubscription.contains("userDeActivationDateTime"));

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Test moinstances subscription with coounters.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testMoinstanceSubscriptionWithCounters() throws Exception {
        final MoinstanceSubscription expectedSubscription = JSonUtils.getJsonMapperObjectFromFile(MOINSTACNE_SUB_JSON_WITH_COUNTERS_MOINSTANCE_FILE,
                MoinstanceSubscription.class);

        enrichNodePoidForSubscrition(expectedSubscription);

        final String pollingUrl = assertThatPostSubscriptionIsSuccessful(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the subscription object with Counters
        final com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(
                subscriptionId, com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription.class);
        Assert.assertEquals(expectedSubscription.getName(), actualSubscription.getName());
        Assert.assertEquals(expectedSubscription.getCounters(), actualSubscription.getCounters());

        // delete subscription
        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Test moinstances subscription with counters and Moinstances.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testMoinstanceSubscriptionWithCountersAndMoinstances() throws Exception {
        final MoinstanceSubscription expectedSubscription = JSonUtils.getJsonMapperObjectFromFile(MOINSTACNE_SUB_JSON_WITH_COUNTERS_MOINSTANCE_FILE,
                MoinstanceSubscription.class);

        enrichNodePoidForSubscrition(expectedSubscription);

        final String pollingUrl = assertThatPostSubscriptionIsSuccessful(expectedSubscription);
        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the subscription object with Counters and MoInstances
        final com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(
                subscriptionId, com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription.class);
        Assert.assertEquals(expectedSubscription.getName(), actualSubscription.getName());
        Assert.assertEquals(expectedSubscription.getCounters(), actualSubscription.getCounters());
        Assert.assertEquals(expectedSubscription.getMoInstances(), actualSubscription.getMoInstances());

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Test import of moinstance subscription in reduced format.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(6)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testImportMoinstanceSubscriptionReduced() throws Exception {

        final MoinstanceSubscription expectedSubscription =
                JSonUtils.getJsonObjectFromSimplifiedFile(MOINSTANCE_SUB_JSON_REDUCED, SubscriptionType.MOINSTANCE, MoinstanceSubscription.class);

        final String pollingUrl =
                assertThatPostSimplifiedImportedSubscriptionIsSuccessful(Resources.getClasspathResource(MOINSTANCE_SUB_JSON_REDUCED).getAsText());

        pollResponse(pollingUrl);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);

        // get the heavy subscription object
        final com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription actualSubscription =
                assertThatGetSubscriptionByIdIsSuccessful(subscriptionId, com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription.class);

        Assert.assertNotEquals(expectedSubscription.getNodes(), actualSubscription.getNodes());

        // Check default and validation settings
        Assert.assertNull(expectedSubscription.getNodes().get(0).getOssModelIdentity());
        Assert.assertNull(expectedSubscription.getOwner());
        Assert.assertNull(expectedSubscription.getUserType());

        Assert.assertNotNull(actualSubscription.getNodes().get(0).getOssModelIdentity());
        Assert.assertNotNull(actualSubscription.getIdAsString());
        Assert.assertNotNull(actualSubscription.getOwner());
        Assert.assertEquals(UserType.USER_DEF, actualSubscription.getUserType());
        Assert.assertEquals(AdministrationState.INACTIVE, actualSubscription.getAdministrationState());
        Assert.assertEquals(RopPeriod.FIFTEEN_MIN, actualSubscription.getRop());

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }

    /**
     * Cleanup Test Environment by Deleting added Nodes
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(7)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void cleanupEnv() throws Exception {
        for (final NodeData node : nodeDataList) {
            nodeCreationHelperBeanEJB.deleteNode(node);
        }
    }
}
