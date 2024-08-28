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
package com.ericsson.oss.services.pm.testsuite.steps.rest;

import static com.jayway.awaitility.Awaitility.await;

import static io.restassured.RestAssured.given;

import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.math.NumberUtils;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.cucumber.arquillian.api.CucumberGlues;
import com.ericsson.oss.services.test.rest.step.AbstractSharedRestSteps;
import com.jayway.awaitility.Duration;

import blast.tools.http.client.rest.api.RestRequestInfo;
import cucumber.api.java.en.And;
import io.restassured.http.Header;
import io.restassured.response.Response;

@CucumberGlues
public class SubscriptionRestStep extends AbstractSharedRestSteps {
    private static final String SUBSCRIPTION_URI = "/pm-service/rest/subscription/";

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRestStep.class);

    private String subscriptionId = null;

    @And("^Create a subscription(?:s?)$")
    public void createSubscription(final List<RestRequestInfo.Builder> reqs) throws InterruptedException {
        invokeRestCall(reqs);
    }

    @And("^Retrieve a subscription(?:s?)$")
    public void retrieveSubscription(final List<RestRequestInfo.Builder> reqs) throws InterruptedException {
        invokeRestCall(reqs);
        if (NumberUtils.isCreatable(response.asString())) {
            subscriptionId = response.asString();
        }
        logger.debug("subscriptionId : {}", subscriptionId);
    }

    @And("^Export a subscription(?:s?)$")
    public void exportSubscription(final List<RestRequestInfo.Builder> reqs) throws InterruptedException {
        invokeRestCall(reqs);
    }

    @And("^Delete a subscription(?:s?)$")
    public void deleteSubscription(final List<RestRequestInfo.Builder> reqs) throws InterruptedException {
        invokeRestCall(reqs);
        subscriptionId = null;
    }

    @And("^Wait for Subscription creation$")
    public void performPollRestCall() {
        String pollingUrl = response.getBody().path("url");
        if (logger.isInfoEnabled()) {
            logger.info("Body: {}", response.getBody().print());
        }
        logger.debug(pollingUrl);
        pollResponse(pollingUrl);
    }

    @And("^Verify Exported Subscription contains(?:s?)$")
    public void verifyExportRespnose(List<String> attributes) {
        if (logger.isInfoEnabled()) {
            logger.info(response.asString());
        }
        for (String attribute : attributes) {
            Assert.assertTrue(response.asString().contains(attribute));
        }
    }

    @And("^Verify Exported Subscription does not contains(?:s?)$")
    public void verifyNegativeExportResponse(List<String> attributes) {
        for (String attribute : attributes) {
            Assert.assertTrue(!response.asString().contains(attribute));
        }
    }

    protected void invokeRestCall(final List<RestRequestInfo.Builder> reqs) throws InterruptedException {
        for (final RestRequestInfo.Builder req : reqs) {
            RestRequestInfo restRequestInfo = req.build();
            performRequest(restRequestInfo);
        }
    }

    protected Logger getLogger() {
        return logger;
    }

    protected void pollResponse(final String pollingUrl) {
        if (pollingUrl == null) {
            logger.debug("polling url not valid {} ", pollingUrl);
            return;
        }
        await().pollDelay(Duration.ONE_SECOND).pollInterval(Duration.ONE_SECOND).atMost(Duration.ONE_MINUTE).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws InterruptedException {
                final Response pollResponse = given().header(new Header("X-Tor-UserId", "pmOperator")).get(pollingUrl);
                return (pollResponse.getStatusCode() == 200 || pollResponse.getStatusCode() == 201);
            }
        });
    }

    @Override
    protected void prepareRequest(RestRequestInfo req) {
        super.prepareRequest(req);
        String uri = req.getUri() != null ? req.getUri() : "";
        if (subscriptionId != null && !uri.contains(subscriptionId)) {
            uri += subscriptionId;
        }
        if (!uri.contains(SUBSCRIPTION_URI)) {
            String newUri = SUBSCRIPTION_URI + uri;
            req.setUri(newUri);
        }
    }
}
