/*******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.initiation.api;

import javax.ws.rs.core.Response;

/**
 * Utility class for getting http response for asynchronous subscription requests
 */
public interface SubscriptionRestPollingHelperLocal {

    /**
     * Returns the response for the REST request. Depends on whether the asynchronous subscription creation has finished or not.
     *
     * @param trackerId
     *         - the unique id to identify the subscription that is being created
     *
     * @return - Can return Response with code 202 and a url for future polling if subscription creation is in progress, or Response with status 201
     * and light subscription object in the payload. Response with status 504 is returned if any error occurs in the subscription creation
     * process
     */
    Response getSubscriptionCreationResponse(final String trackerId);

    /**
     * Returns the response for the REST request. Depends on whether the asynchronous subscription update has finished or not.
     *
     * @param trackerId
     *         - the unique id to identify the subscription that is being updated
     *
     * @return - Can return Response with code 202 and a url for future polling if subscription update is in progress, or Response with status OK and
     * light subscription object in the payload. Response with status 504 is returned if any error occurs in the subscription update process
     */
    Response getSubscriptionUpdateResponse(final String trackerId);
}
