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

package com.ericsson.oss.services.pm.integration.subscription;

import static org.awaitility.Awaitility.await;

import static io.restassured.http.ContentType.JSON;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.awaitility.Duration;
import org.junit.Assert;

import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.services.pm.initiation.node.data.NodeData;
import com.ericsson.oss.services.pm.integration.RestBaseArquillian;
import com.ericsson.oss.services.pm.integration.test.helpers.JSonUtils;
import com.ericsson.oss.services.pm.integration.test.helpers.SystemRecorderLogHelper;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;

public class BaseSubscriptionRest extends RestBaseArquillian {
    private long persistenceTimeAfterActivation;

    @Inject
    SystemRecorderLogHelper systemRecorderLogHelper;

    public void setPersistenceTimeAfterActivation(final long persistenceTimeAfterActivation) {
        this.persistenceTimeAfterActivation = persistenceTimeAfterActivation;
    }

    public List<NodeData> setUp(final String nodeJsonFile) throws Exception {
        RestAssured.requestSpecification = new RequestSpecBuilder().build().baseUri(CONTAINER_BASE_URL).accept(JSON).contentType(JSON).log().all();
        return Arrays.asList(JSonUtils.getJsonMapperObjectFromFile(nodeJsonFile, NodeData[].class));
    }

    public <T extends Subscription, S extends com.ericsson.oss.pmic.dto.subscription.Subscription> void testSubscription(final String filePath,
            final Class<T> type, final Class<S> dtoType, final boolean isTraceSubscription)
            throws Exception {
        tasksListener.setUseStubbedMediation(true);
        systemRecorderLogHelper.cleanup();
        final T expectedSubscription = JSonUtils.getJsonMapperObjectFromFile(filePath, type);

        if (ResourceSubscription.class.isAssignableFrom(type)) {
            enrichNodePoidForSubscrition((ResourceSubscription) expectedSubscription);
        }

        // create subscription
        final String pollingUrl = assertThatPostSubscriptionIsSuccessful(expectedSubscription);
        pollResponse(pollingUrl);
        // assertOnPrivacyLogging(isTraceSubscription);

        // retrieve subscription Id
        final String subscriptionId = assertThatGetSubscriptionIdByNameIsSuccessful(expectedSubscription.getName());
        Assert.assertNotNull(subscriptionId);
        // assertOnPrivacyLogging(isTraceSubscription);

        // get the heavy subscription object
        final S actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(subscriptionId, dtoType);
        // assertOnPrivacyLogging(isTraceSubscription);

        // activate subscription
        activateSubscriptionRest(Long.parseLong(subscriptionId), actualSubscription.getPersistenceTime().getTime());
        await().pollDelay(Duration.ONE_SECOND).pollInterval(Duration.ONE_SECOND).atMost(new Duration(2, TimeUnit.MINUTES))
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws InterruptedException {
                        final S actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(subscriptionId, dtoType);
                        if (actualSubscription.getAdministrationState() == AdministrationState.ACTIVE) {
                            setPersistenceTimeAfterActivation(actualSubscription.getPersistenceTime().getTime());
                            return true;
                        }
                        return false;
                    }
                });
        // assertOnPrivacyLogging(isTraceSubscription);

        // deactivate subscription
        deactivateSubscriptionRest(Long.parseLong(subscriptionId), persistenceTimeAfterActivation);
        await().pollDelay(Duration.ONE_SECOND).pollInterval(Duration.ONE_SECOND).atMost(new Duration(2, TimeUnit.MINUTES))
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws InterruptedException {
                        final S actualSubscription = assertThatGetSubscriptionByIdIsSuccessful(subscriptionId, dtoType);
                        if (actualSubscription.getAdministrationState() == AdministrationState.INACTIVE) {
                            return true;
                        }
                        return false;
                    }
                });
        // assertOnPrivacyLogging(isTraceSubscription);

        // delete subscription
        assertDeleteSubscriptionIsSuccessful(subscriptionId);
        // assertOnPrivacyLogging(isTraceSubscription);
    }

    private void assertOnPrivacyLogging(final boolean isTraceSubscription) {
        if (isTraceSubscription) {
            Assert.assertTrue(SystemRecorderLogHelper.PRIVACY_ERROR_MESSAGE, systemRecorderLogHelper.isCompliantWithPrivacyLogging());
        }
    }
}
