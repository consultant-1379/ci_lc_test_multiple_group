/*******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.initiation.util

import javax.inject.Inject
import javax.ws.rs.core.Response

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.PersistenceTrackingState
import com.ericsson.oss.pmic.dto.SubscriptionPersistenceTrackingStatus
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.services.pm.initiation.utils.SubscriptionRestPollingHelperImpl
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService

class SubscriptionRestPollingHelperSpec extends SkeletonSpec {

    static final String POLLING_URL = "/pm-service/rest/subscription/status/"
    static final String id = UUID.randomUUID().toString()

    @ObjectUnderTest
    SubscriptionRestPollingHelperImpl objectUnderTest

    @Inject
    SubscriptionReadOperationService subscriptionReadOperationService

    def "get response for subscription creation, return ACCEPTED status with url"() {
        given: "subscription tracker with PERSISTING status, no error message or subscription id"
        def tracker = subscriptionReadOperationService.generateTrackingId(new SubscriptionPersistenceTrackingStatus(null,
                PersistenceTrackingState.PERSISTING))
        when:
        Response responseActual = objectUnderTest.getSubscriptionCreationResponse(tracker)
        then: "response has ACCEPTED status and url with tracker as payload"
        Response response = Response.status(Response.Status.ACCEPTED).entity("{\"url\":\"" + POLLING_URL + tracker + "\"}").build()
        responseActual.getStatus() == response.getStatus()
        responseActual.getEntity() == response.getEntity()
    }

    def "get response for subscription creation, return CREATED status with subscription as payload"() {
        given: "subscription tracker with DONE status, no error code and valid subscription id"
        ManagedObject subMo = statisticalSubscriptionBuilder.name("Test").build()
        def tracker = subscriptionReadOperationService.generateTrackingId(new SubscriptionPersistenceTrackingStatus(null,
                subMo.getPoId(), PersistenceTrackingState.DONE))
        when:
        Response responseActual = objectUnderTest.getSubscriptionCreationResponse(tracker)
        then: "response has CREATED status and subscription object is returned in payload"
        responseActual.getStatus() == Response.Status.CREATED.getStatusCode()
        (responseActual.getEntity() as Subscription).getName() == "Test"
    }

    def "get response for subscription creation, returns INTERNAL_SERVER_ERROR if no tracker exists in cache"() {
        when:
        Response responseActual = objectUnderTest.getSubscriptionCreationResponse(id)
        then:
        responseActual.getStatus() == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()
    }

    def "get response for subscription creation, returns INTERNAL_SERVER_ERROR if tracker has error message"() {
        given: "tracker with ERROR status and error message"
        def tracker = subscriptionReadOperationService.generateTrackingId(new SubscriptionPersistenceTrackingStatus(null,
                PersistenceTrackingState.ERROR, "Subscription could not be created because such and such"))
        when:
        Response responseActual = objectUnderTest.getSubscriptionCreationResponse(tracker)
        then: "response has INTERNAL_SERVER_ERROR and error message from the tracker"
        responseActual.getStatus() == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()
        responseActual.getEntity() == "Subscription could not be created because such and such"
    }

    def "When the subscription is successfully created (but not persitted in DPS) , return tracked ID to be retried lated "() {
        given: "tracker with DONE status, no error code "
        def tracker = subscriptionReadOperationService.generateTrackingId(new SubscriptionPersistenceTrackingStatus(null,
                123L, PersistenceTrackingState.DONE))
        when:
        Response responseActual = objectUnderTest.getSubscriptionCreationResponse(tracker)
        then:
        responseActual.getStatus() == Response.Status.ACCEPTED.getStatusCode()
        responseActual.getEntity() == "{\"url\":\"" + POLLING_URL + tracker + "\"}"
    }

    def "get response for subscription update, return ACCEPTED status with url"() {
        given: "subscription tracker with UPDATING status, no error message"
        def tracker = subscriptionReadOperationService.generateTrackingId(new SubscriptionPersistenceTrackingStatus(null, 123L,
                PersistenceTrackingState.UPDATING))
        when:
        Response responseActual = objectUnderTest.getSubscriptionUpdateResponse(tracker)
        then: "response has ACCEPTED status and url with tracker as payload"
        responseActual.getStatus() == Response.Status.ACCEPTED.getStatusCode()
        responseActual.getEntity() == "{\"url\":\"" + POLLING_URL + tracker + "\"}"
    }

    def "get response for subscription update, return OK status with subscription as payload"() {
        given: "subscription tracker with DONE status, no error code and valid subscription id"
        ManagedObject subMo = statisticalSubscriptionBuilder.name("Test").build()
        def tracker = subscriptionReadOperationService.generateTrackingId(new SubscriptionPersistenceTrackingStatus(null,
                subMo.getPoId(), PersistenceTrackingState.DONE))
        when:
        Response responseActual = objectUnderTest.getSubscriptionUpdateResponse(tracker)
        then: "response has CREATED status and subscription object is returned in payload"
        responseActual.getStatus() == Response.Status.OK.getStatusCode()
        (responseActual.getEntity() as Subscription).getName() == "Test"
    }

    def "get response for subscription update, returns INTERNAL_SERVER_ERROR if no tracker exists in cache"() {
        when:
        Response responseActual = objectUnderTest.getSubscriptionUpdateResponse(id)
        then:
        responseActual.getStatus() == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()
    }

    def "get response for subscription update, returns INTERNAL_SERVER_ERROR if tracker has error message"() {
        given: "tracker with ERROR status and error message"
        def tracker = subscriptionReadOperationService.generateTrackingId(new SubscriptionPersistenceTrackingStatus(null,
                PersistenceTrackingState.ERROR, "Subscription could not be created because such and such"))
        when:
        Response responseActual = objectUnderTest.getSubscriptionUpdateResponse(tracker)
        then: "response has INTERNAL_SERVER_ERROR and error message from the tracker"
        responseActual.getStatus() == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()
        responseActual.getEntity() == "Subscription could not be created because such and such"
    }
}
