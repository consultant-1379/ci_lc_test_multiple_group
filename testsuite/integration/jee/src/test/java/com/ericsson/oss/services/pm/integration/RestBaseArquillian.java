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

package com.ericsson.oss.services.pm.integration;

import static org.apache.http.HttpStatus.SC_OK;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;

import java.util.concurrent.Callable;
import javax.ejb.EJB;

import org.awaitility.Duration;
import org.hamcrest.collection.IsIn;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.Header;
import io.restassured.response.Response;

/**
 * This is base class for all common rest Test
 */
public class RestBaseArquillian extends InputBaseArquillian {

    public static final String CLIENT_BASE_URL = "http://localhost:8080";
    public static final String CONTAINER_BASE_URL = String.format("http://%s:8080", System.getProperty("container.ip", "localhost"));
    public static final String SUBSCRIPTION_RESOURCE_PATH = "/pm-service/rest/subscription";
    public static final String SUBSCRIPTION_COMPONENT_UI_RESOURCE_PATH = "/pm-service/rest/pmsubscription/";
    public static final String EXPORT_SUBSCRIPTION_PATH = "/exportsubscription";
    public static final String ID_PATH_PARAM = "/{id}";
    public static final String GET_SUBSCRIPTION_ID_BY_NAME_PATH_PARAM = "/getIdByName/{subscriptionName}";
    public static final String GET_COUNTERS = "counters";
    public static final String DEFINER = "definer";
    public static final String MIM_QUERY = "mim";
    public static final String EVENT_FILTER_FOR_TECHNOLOGY_DOMAINS = "eventFilter";
    public static final String GET_EVENT_MIM_QUERY = "getEvent?mim=";
    public static final String EVENT_NAME = "eventName";
    public static final String COUNTER_NAME = "counterName";
    public static final String GROUP_NAME = "sourceObject";
    public static final String ACTIVATE = "/activate";
    public static final String DEACTIVATE = "/deactivate";
    public static final String PERSISTENCE_TIME = "persistenceTime";
    protected final static Header USERID_HEADER = new Header("X-Tor-UserId", "pmOperator");
    protected final static Header ISIMPORTED_HEADER = new Header("X-isImported", "true");
    private static final Logger LOGGER = LoggerFactory.getLogger(InputBaseArquillian.class);
    @EJB(lookup = "java:app/PmicIntegrationTest/NodeCreationHelperBean")
    protected NodeCreationHelperBean nodeCreationHelperBeanEJB;

    @BeforeClass
    public static void init() {
        RestAssured.requestSpecification = new RequestSpecBuilder().build().baseUri(CLIENT_BASE_URL).accept(JSON).contentType(JSON);
    }

    /**
     * post subscription and assert that subscription was created
     *
     * @param subscription
     *
     * @return polling url to check if subscription has been created
     */
    protected <T extends Subscription> String assertThatPostSubscriptionIsSuccessful(final T subscription) {
        return given().
                contentType(JSON).
                header(USERID_HEADER).
                body(subscription).
                when().
                post(SUBSCRIPTION_RESOURCE_PATH).
                then().
                statusCode(IsIn.isOneOf(201, 202)).
                extract().
                path("url");
    }

    /**
     * post subscription and assert that subscription was rejected
     *
     * @param subscription
     *
     * @return polling url to check if subscription has been rejected
     */
    protected <T extends Subscription> String assertThatPostSubscriptionIsRejected(final T subscription) {
        return given().
                contentType(JSON).
                header(USERID_HEADER).
                body(subscription).
                when().
                post(SUBSCRIPTION_RESOURCE_PATH).
                then().
                statusCode(IsIn.isOneOf(400, 401, 500)).
                extract().
                path("url");
    }

    /**
     * post imported subscription and assert that subscription was created
     *
     * @param subscription
     *
     * @return polling url to check if subscription has been created
     */
    protected <T extends Subscription> String assertThatPostImportedSubscriptionIsSuccessful(final T subscription) {
        return given().
                contentType(JSON).
                header(USERID_HEADER).
                header(ISIMPORTED_HEADER).
                body(subscription).
                when().
                post(SUBSCRIPTION_RESOURCE_PATH).
                then().
                statusCode(IsIn.isOneOf(201, 202)).
                extract().
                path("url");
    }

    /**
     * post simplified imported subscription and assert that subscription was created
     *
     * @param subscription
     *         as string without @class
     *
     * @return polling url to check if subscription has been created
     */
    protected String assertThatPostSimplifiedImportedSubscriptionIsSuccessful(final String subscription) {
        return given().
                contentType(JSON).
                header(USERID_HEADER).
                header(ISIMPORTED_HEADER).
                body(subscription).
                when().
                post(SUBSCRIPTION_RESOURCE_PATH).
                then().
                statusCode(IsIn.isOneOf(201, 202)).
                extract().
                path("url");
    }

    /**
     * post imported subscription and assert that subscription was rejected
     *
     * @param subscription
     *
     * @return polling url to check if subscription has been rejected
     */
    protected <T extends Subscription> String assertThatPostImportedSubscriptionIsRejected(final T subscription) {
        return given().
                contentType(JSON).
                header(USERID_HEADER).
                header(ISIMPORTED_HEADER).
                body(subscription).
                when().
                post(SUBSCRIPTION_RESOURCE_PATH).
                then().
                statusCode(IsIn.isOneOf(400, 401, 500)).
                extract().
                path("url");
    }

    /**
     * Assert that get subscription by id for export is rejected.
     *
     * @param subscriptionId
     *         the subscription id
     */
    protected void assertThatGetSubscriptionByIdForExportIsRejected(final String subscriptionId) {
        given().
                header(USERID_HEADER).
                contentType(JSON).
                when().
                get(SUBSCRIPTION_RESOURCE_PATH + EXPORT_SUBSCRIPTION_PATH + ID_PATH_PARAM, subscriptionId).
                then().
                assertThat().
                body(containsString("Export is not supported")).
                statusCode(IsIn.isOneOf(400, 401, 500));
    }

    /**
     * Delete subscription and assert that subscription is deleted from DPS
     *
     * @param subscriptionId
     */
    protected void assertDeleteSubscriptionIsSuccessful(final String subscriptionId) {
        given().
                header(USERID_HEADER).
                expect().
                statusCode(SC_OK).
                when().
                delete(SUBSCRIPTION_RESOURCE_PATH + ID_PATH_PARAM, subscriptionId);
    }

    /**
     * get subscription and assert that subscription was retrieved in DPS
     *
     * @param <T>
     * @param subscriptionId
     *
     * @return Subscription JaxB for rest get Response
     */
    protected <T extends com.ericsson.oss.pmic.dto.subscription.Subscription> T assertThatGetSubscriptionByIdIsSuccessful(final String subscriptionId,
                                                                                                                          final Class<T> clazz) {
        return given().
                header(USERID_HEADER).
                contentType(JSON).
                expect().
                statusCode(SC_OK).
                when().
                get(SUBSCRIPTION_RESOURCE_PATH + ID_PATH_PARAM, subscriptionId).
                as(clazz);
    }

    /**
     * get subscription and assert that subscription was retrieved in DPS
     *
     * @param <T>
     * @param subscriptionId
     *
     * @return Subscription JaxB for rest get Response
     */
    protected <T extends com.ericsson.oss.pmic.dto.subscription.Subscription> String assertThatGetSubscriptionByIdForExportIsSuccessful(final String subscriptionId,
                                                                                                                                        final Class<T> clazz) {
        return given().
                header(USERID_HEADER).
                contentType(JSON).
                expect().
                statusCode(SC_OK).
                when().
                get(SUBSCRIPTION_RESOURCE_PATH + EXPORT_SUBSCRIPTION_PATH + ID_PATH_PARAM, subscriptionId).
                asString();
    }

    /**
     * get subscription and assert that subscription was retrieved from DPS
     *
     * @return subscriptionId
     */
    protected String assertThatGetSubscriptionIdByNameIsSuccessful(final String subscriptionName) {
        return given().
                header(USERID_HEADER).
                expect().
                statusCode(SC_OK).
                when().
                get(SUBSCRIPTION_RESOURCE_PATH + GET_SUBSCRIPTION_ID_BY_NAME_PATH_PARAM, subscriptionName).
                asString();
    }

    /**
     * activate subscription
     */
    protected String activateSubscriptionRest(final long subscriptionId, final long persistenceTime) {
        final JSONObject json = new JSONObject();
        json.put(PERSISTENCE_TIME, persistenceTime);
        return given().
                header(USERID_HEADER).
                body(json.toString()).
                expect().
                statusCode(SC_OK).
                when().
                post(SUBSCRIPTION_RESOURCE_PATH + ID_PATH_PARAM + ACTIVATE, subscriptionId).
                asString();
    }

    /**
     * deactivate subscription
     */
    protected String deactivateSubscriptionRest(final long subscriptionId, final long persistenceTime) {
        final JSONObject json = new JSONObject();
        json.put(PERSISTENCE_TIME, persistenceTime);
        return given().
                header(USERID_HEADER).
                body(json.toString()).
                expect().
                statusCode(SC_OK).
                when().
                post(SUBSCRIPTION_RESOURCE_PATH + ID_PATH_PARAM + DEACTIVATE, subscriptionId).
                asString();
    }

    /**
     * polls url every 1 sec for 10 sec to determine if subscription has been created
     *
     * @param pollingUrl
     *
     * @throws InterruptedException
     */
    protected void pollResponse(final String pollingUrl) throws InterruptedException {
        if (pollingUrl == null) {
            LOGGER.debug("polling url not valid {} ", pollingUrl);
            return;
        }
        await().pollDelay(Duration.ONE_SECOND).pollInterval(Duration.ONE_SECOND).atMost(Duration.ONE_MINUTE).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws InterruptedException {
                final Response pollResponse = given().header(USERID_HEADER).get(pollingUrl);
                return (pollResponse.getStatusCode() == 200 || pollResponse.getStatusCode() == 201);
            }
        });
    }

    /**
     * can be used to enrich json Subscription Node poid
     *
     * @param subscription
     */
    protected <T extends ResourceSubscription> void enrichNodePoidForSubscrition(final T subscription) {
        for (final Node nodeInfo : subscription.getNodes()) {
            final long poId = nodeCreationHelperBeanEJB.findNetworkNodeByFdn(nodeInfo.getFdn()).getPoId();
            nodeInfo.setId(poId);
        }
    }
}
