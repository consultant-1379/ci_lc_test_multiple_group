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
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionOperationMessageSender;
import com.ericsson.oss.services.pm.initiation.node.data.NodeData;
import com.ericsson.oss.services.pm.integration.RestBaseArquillian;
import com.ericsson.oss.services.pm.integration.test.helpers.JSonUtils;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;

@RunWith(Arquillian.class)
public class AsrCellTraceSubscriptionITest extends RestBaseArquillian {

    private static final String INPUT_NODE_JSON_FILE = "CellTraceSubscription/AsrCellTraceSubscriptionNodes.json";
    private static final String ASR_JSON_WITH_EVENTS_FILE = "CellTraceSubscription/AsrCellTraceSubscription.json";

    private static List<NodeData> nodeDataList;

    @Inject
    private SubscriptionOperationMessageSender sender;

    @Before
    public void setUp() throws Exception {
        RestAssured.requestSpecification = new RequestSpecBuilder().build().baseUri(CONTAINER_BASE_URL).accept(JSON).contentType(JSON).log().all();
        nodeDataList = Arrays.asList(JSonUtils.getJsonMapperObjectFromFile(INPUT_NODE_JSON_FILE, NodeData[].class));
    }

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
    public void testAsrSubscriptionCreation() throws Exception {
        final CellTraceSubscription expectedSubscription =
                JSonUtils.getJsonMapperObjectFromFile(ASR_JSON_WITH_EVENTS_FILE, CellTraceSubscription.class);

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

        assertDeleteSubscriptionIsSuccessful(subscriptionId);
    }
}
