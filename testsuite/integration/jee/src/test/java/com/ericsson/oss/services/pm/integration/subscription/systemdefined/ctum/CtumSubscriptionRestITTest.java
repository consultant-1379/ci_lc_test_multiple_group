/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.integration.subscription.systemdefined.ctum;

import static org.awaitility.Awaitility.await;

import static io.restassured.http.ContentType.JSON;

import java.util.concurrent.Callable;

import javax.inject.Inject;

import org.awaitility.Duration;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionTestUtils;
import com.ericsson.oss.services.pm.integration.RestBaseArquillian;
import com.ericsson.oss.services.pm.integration.test.helpers.SystemRecorderLogHelper;
import com.ericsson.oss.services.pm.integration.test.steps.NetworkElementSteps;
import com.ericsson.oss.services.pm.integration.test.steps.SubscriptionEjbSteps;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;

@RunWith(Arquillian.class)
public class CtumSubscriptionRestITTest extends RestBaseArquillian {

    @Inject
    private NetworkElementSteps networkElementSteps;

    @Inject
    private SubscriptionEjbSteps subscriptionEjbSteps;

    @Inject
    private SubscriptionTestUtils testUtils;

    @Inject
    SystemRecorderLogHelper systemRecorderLogHelper;

    @Before
    public void setUp() throws Exception {
        RestAssured.requestSpecification = new RequestSpecBuilder().build().baseUri(CONTAINER_BASE_URL).accept(JSON).contentType(JSON).log().all();
    }

    @Test
    @InSequence(2)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void cleanupAnyExistingNodesAndSubscription() {
        networkElementSteps.deleteAllNodes();
        subscriptionEjbSteps.deleteAllSubscription();
    }

    @Test
    @InSequence(3)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void on_audit_ctum_subscription_should_be_created() throws Exception {
        networkElementSteps.createNodes("SGSN-MME/SystemDefinedSubscriptionNodes.json");
        systemRecorderLogHelper.cleanup();

        subscriptionEjbSteps.invokeSysDefSubscriptionAudit();

        final String sytemDefinedsubsciptionName = "CTUM";

        // wait and assert subscription creation
        await().pollDelay(Duration.ONE_SECOND).pollInterval(Duration.ONE_SECOND).atMost(Duration.ONE_MINUTE).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws DataAccessException {
                return testUtils.existsBySubscriptionName(sytemDefinedsubsciptionName);
            }
        });
        // Assert.assertTrue(systemRecorderLogHelper.PRIVACY_ERROR_MESSAGE, systemRecorderLogHelper.isCompliantWithPrivacyLogging());

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(sytemDefinedsubsciptionName);
        Assert.assertNotNull(subscriptionId);
        // Assert.assertTrue(systemRecorderLogHelper.PRIVACY_ERROR_MESSAGE, systemRecorderLogHelper.isCompliantWithPrivacyLogging());
    }
}
