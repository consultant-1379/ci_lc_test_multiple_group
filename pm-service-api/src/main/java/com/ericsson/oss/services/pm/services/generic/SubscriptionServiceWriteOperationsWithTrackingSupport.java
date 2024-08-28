/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.services.generic;

import com.ericsson.oss.pmic.dto.PersistenceTrackingStatus;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.ServiceException;

/**
 * SubscriptionServiceWriteOperationsWithTrackingSupport interface for saving/updating subscriptions with retry mechanism. Not to be used outside of
 * {@link SubscriptionWriteOperationService}!
 */
public interface SubscriptionServiceWriteOperationsWithTrackingSupport {

    /**
     * Save subscription to database. Subscription must not contain a subscription ID.<br>
     * <b>NOTE:</b> The purpose of this method is to be used from {@link SubscriptionWriteOperationService}. DO NOT use this method directly.
     *
     * @param subscription
     *         - subscription to be saved. Must not have subscription ID.
     * @param trackingId
     *         - tracking id
     */
    void saveOrUpdateAsync(Subscription subscription, String trackingId);

    /**
     * Save subscription with tracker with retry interceptor. NOT TO BE USED! Use
     * {@link SubscriptionWriteOperationService#saveOrUpdate(Subscription, String)}.
     * <b>NOTE:</b> The purpose of this method is to be used from {@link SubscriptionWriteOperationService}. DO NOT use this method directly.
     *
     * @param subscription
     *         - subscription
     * @param trackingId
     *         - trackingId
     * @param hasAssignedId
     *         - hasAssignedId
     *
     * @throws RetryServiceException
     *         - if exception throws is not retriable or retry attempts exceeded the limit.
     */
    void saveOrUpdate(Subscription subscription, String trackingId, boolean hasAssignedId) throws RetryServiceException;

    /**
     * Save or update subscriptions in new transaction. NOT TO BE USED outside of this class. <b>NOTE:</b> The purpose of this method is to be used
     * from {@link SubscriptionWriteOperationService}. DO NOT use this method directly.
     *
     * @param subscription
     *         - subscription
     *
     * @throws DataAccessException
     *         - DataAccessException
     */
    void saveOrUpdateInNewTransaction(Subscription subscription) throws DataAccessException;

    /**
     * Creates tracking id. <b>NOTE:</b> The purpose of this method is to be used from {@link SubscriptionReadOperationService}.
     * DO NOT use this method directly.
     *
     * @param initialPersistenceTrackingStatus
     *         - initialPersistenceTrackingStatus
     *
     * @return Returns tracking id.
     */
    String generateTrackingId(PersistenceTrackingStatus initialPersistenceTrackingStatus);

    /**
     * Gets current status by tracking id. <b>NOTE:</b> The purpose of this method is to be used from {@link SubscriptionReadOperationService}.
     * DO NOT use this method directly.
     *
     * @param trackingId
     *         - the non-null, non-empty tracker id.
     *
     * @return - PersistenceTracking object if tracker Id exists. Will return null if tracker id does not exist.
     * @throws IllegalArgumentException
     *         - if tracker id is null or empty string
     * @throws ServiceException
     *         - if any Exception is thrown when reading tracker from cache.
     */
    PersistenceTrackingStatus getTrackingStatus(String trackingId) throws ServiceException;
}
