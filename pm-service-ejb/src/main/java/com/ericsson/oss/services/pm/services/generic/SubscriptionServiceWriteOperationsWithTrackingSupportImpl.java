
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

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.POST_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.UPDATE_SUBSCRIPTION;

import java.util.Map;
import java.util.UUID;
import javax.cache.Cache;
import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.EJBTransactionRolledbackException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.persistence.OptimisticLockException;

import org.infinispan.remoting.transport.jgroups.SuspectException;
import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache;
import com.ericsson.oss.pmic.dao.SubscriptionDao;
import com.ericsson.oss.pmic.dto.PersistenceTrackingState;
import com.ericsson.oss.pmic.dto.PersistenceTrackingStatus;
import com.ericsson.oss.pmic.dto.SubscriptionPersistenceTrackingStatus;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.exception.ServiceException;
import com.ericsson.oss.services.pm.initiation.cache.constants.CacheNamingConstants;
import com.ericsson.oss.services.pm.initiation.ejb.ResourceSubscriptionNodeInitiation;
import com.ericsson.oss.services.pm.retry.interceptor.RetryOnNewTransaction;

/**
 * SubscriptionServiceWriteOperationsWithTrackingSupportImpl implementation.
 */
@Stateless
public class SubscriptionServiceWriteOperationsWithTrackingSupportImpl implements SubscriptionServiceWriteOperationsWithTrackingSupport {

    private static final String SUBSCRIPTION_COULD_NOT_BE_CREATED = "Subscription %s could not be created. Please try again.";

    @Inject
    private Logger logger;
    @Inject
    private SubscriptionDao subscriptionDao;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @EJB
    private SubscriptionServiceWriteOperationsWithTrackingSupport self;
    @Inject
    private SubscriptionServiceNodeUpdateExtractor nodeUpdateExtractor;
    @Inject
    private ResourceSubscriptionNodeInitiation subscriptionNodeInitiation;
    @Inject
    @NamedCache(CacheNamingConstants.PMIC_REST_SUBSCRIPTION_CACHE_V2)
    private Cache<String, Map<String, Object>> subscriptionPersistenceTrackingStatusCache;

    @Override
    @Asynchronous
    public void saveOrUpdateAsync(final Subscription subscription, final String trackingId) {
        try {
            self.saveOrUpdate(subscription, trackingId, subscription.hasAssignedId());
            systemRecorder.commandFinishedSuccess(UPDATE_SUBSCRIPTION, subscription.getName(), "Successfully updated subscription %s with id %s ",
                    subscription.getName(), subscription.getIdAsString());
        } catch (final RetryServiceException e) {
            subscriptionPersistenceTrackingStatusCache.put(trackingId, new SubscriptionPersistenceTrackingStatus(trackingId,
                    PersistenceTrackingState.ERROR, "Subscription " + subscription.getName() + " could not be created. Please try again.").asMap());
            systemRecorder.commandFinishedSuccess(POST_SUBSCRIPTION, subscription.getName(),
                    "Subscription %s with id %s could not be saved to database because: %s", subscription.getName(), subscription.getIdAsString(),
                    e.getMessage());
            logger.info("Subscription {} with id {} (ok to be null if saving, should have id if updating) could not be persisted. Exception:",
                    subscription.getName(), subscription.getId(), e);
        }
    }

    @Override
    @RetryOnNewTransaction(retryOn = {RuntimeDataAccessException.class, OptimisticLockException.class, EJBTransactionRolledbackException.class,
            EJBException.class, SuspectException.class}, attempts = 5, waitIntervalInMs = 200, exponentialBackoff = 2)
    public void saveOrUpdate(final Subscription subscription, final String trackingId, final boolean hasAssignedId) throws RetryServiceException {
        if (subscription == null || trackingId == null) {
            throw new IllegalArgumentException("Cannot save subscription with tracker because either subscription or tracker are null");
        }
        if (!hasAssignedId) {
            // in case of retry, subscription persisting transaction was rolled back but the assigning of subscription ID to this subscription
            // Object was not un-done as it is not a transactional change.
            subscription.setId(null);
        }
        try {
            self.saveOrUpdateInNewTransaction(subscription);
            subscriptionPersistenceTrackingStatusCache.put(trackingId,
                    new SubscriptionPersistenceTrackingStatus(trackingId, subscription.getId(), PersistenceTrackingState.DONE).asMap());
            systemRecorder.commandFinishedSuccess(POST_SUBSCRIPTION, subscription.getName(), "Subscription %s with id %s was persisted successfully",
                    subscription.getName(), subscription.getIdAsString());
        } catch (final DataAccessException e) {
            subscriptionPersistenceTrackingStatusCache.put(trackingId, new SubscriptionPersistenceTrackingStatus(trackingId,
                    PersistenceTrackingState.ERROR, String.format(SUBSCRIPTION_COULD_NOT_BE_CREATED, subscription.getName())).asMap());
            systemRecorder.commandFinishedSuccess(POST_SUBSCRIPTION, subscription.getName(),
                    "Subscription %s with id %s could not be saved to database because: %s", subscription.getName(), subscription.getIdAsString(),
                    e.getMessage());
            logger.info("Subscription {} with id {} (ok to be null if saving, should have id if updating) could not be persisted. Exception:",
                    subscription.getName(), subscription.getId(), e);
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void saveOrUpdateInNewTransaction(final Subscription subscription) throws DataAccessException, RuntimeDataAccessException {
        final boolean isActiveResourceSubscription = subscription instanceof ResourceSubscription
                && AdministrationState.ACTIVE == subscription.getAdministrationState();
        if (isActiveResourceSubscription && subscription.hasAssignedId()) {
            final SubscriptionServiceNodeUpdateExtractor.NodeDiff nodeDiff = nodeUpdateExtractor
                    .getNodeDifference(subscription);
            subscriptionDao.saveOrUpdate(subscription);
            subscriptionNodeInitiation.activateOrDeactivateNodesOnActiveSubscription((ResourceSubscription) subscription, nodeDiff.getNodesAdded(),
                    nodeDiff.getNodesRemoved());
        } else {
            subscriptionDao.saveOrUpdate(subscription);
        }
    }

    @Override
    public String generateTrackingId(final PersistenceTrackingStatus initialPersistenceTrackingStatus) {
        if (initialPersistenceTrackingStatus == null) {
            throw new IllegalArgumentException("initialPersistenceTrackingState should be not null");
        }
        if (initialPersistenceTrackingStatus.getState() == null) {
            throw new IllegalArgumentException("initialPersistenceTrackingState.status should be initially set");
        }
        if (initialPersistenceTrackingStatus.getState().isOneOf(PersistenceTrackingState.UPDATING, PersistenceTrackingState.DELETING)
                && initialPersistenceTrackingStatus.getPersistedObjectId() == null) {
            throw new IllegalArgumentException(
                    "initialPersistenceTrackingState.persistedObjectId should be initially set when status is UPDATING and DELETING");
        }
        if (initialPersistenceTrackingStatus.getState() == PersistenceTrackingState.ERROR
                && initialPersistenceTrackingStatus.getErrorMessage() == null) {
            throw new IllegalArgumentException(
                    "initialPersistenceTrackingStatus.errorMessage cannot be null if initialPersistenceTrackingState.status is ERROR");
        }
        if (initialPersistenceTrackingStatus.getErrorMessage() != null && initialPersistenceTrackingStatus.getErrorMessage().isEmpty()) {
            throw new IllegalArgumentException("initialPersistenceTrackingState.errorMessage suppose to be empty in initial state");
        }
        final String trackingId = UUID.randomUUID().toString();
        initialPersistenceTrackingStatus.setPersistenceTrackingId(trackingId);
        subscriptionPersistenceTrackingStatusCache.put(trackingId, initialPersistenceTrackingStatus.asMap());
        return trackingId;
    }

    @Override
    public PersistenceTrackingStatus getTrackingStatus(final String trackingId) throws IllegalArgumentException, ServiceException {
        if (trackingId == null || trackingId.isEmpty()) {
            throw new IllegalArgumentException("Tracking id cannot be nul lor empty!");
        }
        final Map<String, Object> trackingStatusAttributes;
        try {
            trackingStatusAttributes = subscriptionPersistenceTrackingStatusCache.get(trackingId);
        } catch (final Exception exception) {
            throw new ServiceException(exception.getMessage(), exception);
        }
        if (trackingStatusAttributes == null) {
            logger.debug("Cannot get tracking information for {} because tracker does not exist", trackingId);
            return null;
        }
        final SubscriptionPersistenceTrackingStatus result = SubscriptionPersistenceTrackingStatus.toPersistenceTracking(trackingStatusAttributes);
        result.setPersistenceTrackingId(trackingId);
        return result;
    }

}
