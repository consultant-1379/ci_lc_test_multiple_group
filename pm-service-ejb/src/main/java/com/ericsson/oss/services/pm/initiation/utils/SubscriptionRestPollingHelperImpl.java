/*******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.initiation.utils;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.PersistenceTrackingState;
import com.ericsson.oss.pmic.dto.SubscriptionPersistenceTrackingStatus;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.ServiceException;
import com.ericsson.oss.services.pm.initiation.api.SubscriptionRestPollingHelperLocal;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * Implementation EJB for SubscriptionRestPollingHelperLocal. Should be used by the REST endpoints for tracking asynchronous subscription actions
 */
@Stateless
public class SubscriptionRestPollingHelperImpl implements SubscriptionRestPollingHelperLocal {

    private static final String POLLING_URL = "/pm-service/rest/subscription/status/";

    private static final Response ERROR_RESPONSE = Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity("Subscription created successfully but an internal error occurred when extracting the subscription").build();

    @Inject
    private Logger logger;

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    @Override
    public Response getSubscriptionCreationResponse(final String trackerId) {
        return getTrackerResponse(trackerId, Response.Status.CREATED);
    }

    @Override
    public Response getSubscriptionUpdateResponse(final String trackerId) {
        return getTrackerResponse(trackerId, Response.Status.OK);
    }

    private Response getTrackerResponse(final String trackerId, final Response.Status status) {
        final SubscriptionPersistenceTrackingStatus tracker;
        try {
            tracker = (SubscriptionPersistenceTrackingStatus) subscriptionReadOperationService.getTrackingStatus(trackerId);
            if (tracker == null) {
                return getResponseWhenTrackerIsNull();
            }
            if (tracker.getState().equals(PersistenceTrackingState.DONE)) {
                return getResponseWithSubscription(trackerId, tracker, status);
            } else {
                return getResponseIfTrackingIsInProgress(trackerId, tracker);
            }
        } catch (final ServiceException e) {
            logger.error("Error occurred when extracting the tracker subscription: {}", e.getMessage());
            logger.info("Error occurred when extracting the tracker subscription:", e);
            return getResponseWhenTrackerIsNull();
        }
    }

    private Response getResponseWhenTrackerIsNull() {
        //return with error message.
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("System has no knowledge of this tracked request. Please retry").build();
    }

    private Response getResponseWithSubscription(final String trackerId, final SubscriptionPersistenceTrackingStatus tracker,
                                                 final Response.Status status) {
        try {
            final Subscription subscription = subscriptionReadOperationService.findOneById(tracker.getPersistedObjectId(), false);
            if (subscription != null) {
                return Response.status(status).entity(subscription).build();
            } else {
                //Subscription created successfully, but error fetching so keep retrying .
                return getResponseIfTrackingIsInProgress(trackerId, tracker);
            }
        } catch (final DataAccessException e) {
            logger.error("Error thrown when checking if subscription with id {} exists in database. Message: {}", tracker.getPersistedObjectId(),
                    e.getMessage());
            logger.info("Error thrown when checking if subscription exists in database", e);
            return ERROR_RESPONSE;
        }
    }

    private Response getResponseIfTrackingIsInProgress(final String trackerId, final SubscriptionPersistenceTrackingStatus tracker) {
        if (tracker.getState().equals(PersistenceTrackingState.ERROR)) {
            //return with error message.
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(tracker.getErrorMessage()).build();
        } else {
            //back off
            return Response.status(Response.Status.ACCEPTED).entity("{\"url\":\"" + POLLING_URL + trackerId + "\"}").build();
        }
    }
}
