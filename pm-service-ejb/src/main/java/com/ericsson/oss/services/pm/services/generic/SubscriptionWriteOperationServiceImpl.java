/*
 * COPYRIGHT Ericsson 2017
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.services.generic;

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUBSCRIPTION_DESCRIPTION_REQUIRED;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.DELETE_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.DPS_PERSIST_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.UPDATE_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.ACTIVE_SUBSCRIPTION_DELETE;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.INVALID_INPUT;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.SUBSCRIPTION_NOT_FOUND;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.ACTIVE_SUB_CANT_DELETE_MESSAGE;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.SUBSCRIPTION_DOES_NOT_EXIST;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.ejb.EJBException;
import javax.ejb.EJBTransactionRolledbackException;
import javax.ejb.Stateless;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.persistence.OptimisticLockException;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dao.SubscriptionDao;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionStatus;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterLocal;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterQualifier;
import com.ericsson.oss.services.pm.common.logging.PMICLog;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.exception.SubscriptionNotFoundDataAccessException;
import com.ericsson.oss.services.pm.initiation.ejb.ResourceSubscriptionNodeInitiation;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventType;
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionManager;
import com.ericsson.oss.services.pm.initiation.validators.SubscriptionCommonValidator;
import com.ericsson.oss.services.pm.initiation.validators.SubscriptionValidatorSelector;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.oss.services.pm.retry.interceptor.RetryOnNewTransaction;
import com.ericsson.oss.services.pm.services.exception.ConcurrentSubscriptionUpdateException;
import com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionOperationException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import com.ericsson.oss.services.pm.time.TimeGenerator;

/**
 * Class is annotated as a Stateless session bean to make it fully transactional for the subscription Create, Update and delete DPS ops that need to
 * run with Retry transaction.
 */
@Stateless
public class SubscriptionWriteOperationServiceImpl implements SubscriptionWriteOperationService {

    @Inject
    private Logger logger;

    @Inject
    private TimeGenerator timeGenerator;

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    @Inject
    private BeanManager beanManager;

    @Inject
    private SubscriptionServiceWriteOperationsWithTrackingSupport writeOperationsWithTrackingSupport;

    @Inject
    private SubscriptionServiceNodeUpdateExtractor nodeUpdateExtractor;

    @Inject
    private ResourceSubscriptionNodeInitiation subscriptionNodeInitiation;

    @Inject
    @SubscriptionDataAdjusterQualifier
    private SubscriptionDataAdjusterLocal<Subscription> subscriptionDataAdjuster;

    @Inject
    private SubscriptionValidatorSelector subscriptionValidatorSelector;

    @Inject
    private PmCapabilityModelService pmCapabilityModelService;

    @Inject
    protected SubscriptionCommonValidator subscriptionCommonValidator;

    @Inject
    private SubscriptionManager subscriptionManager;


    @Override
    public void update(final long subscriptionPoId, final Map<String, Object> attributes, final Map<String, Set<String>> associationsToAdd,
                       final Map<String, Set<String>> associationsToRemove) throws SubscriptionNotFoundDataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            subscriptionDao.updateSubscription(subscriptionPoId, attributes, associationsToAdd, associationsToRemove);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public void updateAttributes(final long subscriptionPoId, final Map<String, Object> attributes)
            throws SubscriptionNotFoundDataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            subscriptionDao.updateSubscriptionAttributes(subscriptionPoId, attributes);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    @RetryOnNewTransaction(retryOn = {RuntimeDataAccessException.class, OptimisticLockException.class, EJBTransactionRolledbackException.class,
            EJBException.class}, attempts = 5, waitIntervalInMs = 200, exponentialBackoff = 2)
    public void manageSaveOrUpdate(final Subscription subscription) throws DataAccessException, RetryServiceException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            if (subscription.hasAssignedId()) {
                updateSubscription(subscription, null, subscriptionDao);
            } else {
                subscriptionDao.saveOrUpdate(subscription);
            }
        } finally {
            creationalContext.release();
        }
        systemRecorder.commandFinishedSuccess(DPS_PERSIST_SUBSCRIPTION, subscription.getName(),
                "Subscription %s with id %s was persisted successfully", subscription.getName(), subscription.getIdAsString());
    }

    private void updateSubscription(final Subscription subscription, final String tracker, final SubscriptionDao subscriptionDao)
            throws DataAccessException {
        logger.info("Started updating subscription in DPS. Name: {}, ID: {}", subscription.getName(), subscription.getIdAsString());
        final boolean isActiveResourceSubscription = subscription instanceof ResourceSubscription
                && AdministrationState.ACTIVE == subscription.getAdministrationState();
        if (tracker == null) {
            if (isActiveResourceSubscription) {
                final SubscriptionServiceNodeUpdateExtractor.NodeDiff nodeDiff = nodeUpdateExtractor.getNodeDifference(subscription);
                subscriptionDao.saveOrUpdate(subscription);
                subscriptionNodeInitiation.activateOrDeactivateNodesOnActiveSubscription((ResourceSubscription) subscription,
                        nodeDiff.getNodesAdded(), nodeDiff.getNodesRemoved());
            } else {
                subscriptionDao.saveOrUpdate(subscription);
            }
            systemRecorder.commandFinishedSuccess(UPDATE_SUBSCRIPTION, subscription.getName(), "Successfully updated subscription %s with id %s ",
                    subscription.getName(), subscription.getIdAsString());
            return;
        }

        writeOperationsWithTrackingSupport.saveOrUpdateAsync(subscription, tracker);
    }

    @Override
    @RetryOnNewTransaction(retryOn = {RuntimeDataAccessException.class,
            EJBTransactionRolledbackException.class}, attempts = 5, waitIntervalInMs = 200, exponentialBackoff = 2)
    public void deleteWithRetry(final Subscription subscription)
            throws DataAccessException, RetryServiceException, InvalidSubscriptionOperationException {
        /// TODO (subscription.getAdministrationState() != AdministrationState.INACTIVE) check required here as well, legacy issue, maybe add later
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            subscriptionDao.delete(subscription);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    @RetryOnNewTransaction(retryOn = {RuntimeDataAccessException.class,
            EJBTransactionRolledbackException.class}, attempts = 5, waitIntervalInMs = 200, exponentialBackoff = 2)
    public void saveOrUpdateWithRetry(final Subscription subscription) throws DataAccessException {
        saveOrUpdate(subscription);
    }

    @Override
    public void saveOrUpdate(final Subscription subscription) throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            // TODO subscription.hasAssignedId() check required here as well, legacy issue, maybe add later
            subscriptionDao.saveOrUpdate(subscription);
        } finally {
            creationalContext.release();
        }
        systemRecorder.commandFinishedSuccess(DPS_PERSIST_SUBSCRIPTION, subscription.getName(),
                "Subscription %s with id %s was persisted successfully with admin state %s", subscription.getName(), subscription.getIdAsString(),
                subscription.getAdministrationState());
    }

    @Override
    public void saveOrUpdate(final Subscription subscription, final String trackingId)
            throws DataAccessException, RetryServiceException, InvalidSubscriptionOperationException, ConcurrentSubscriptionUpdateException {
        if (subscription == null || trackingId == null) {
            throw new IllegalArgumentException("Cannot save or update subscription with tracker. Either Subscription or tracker is null");
        }
        if (subscription.hasAssignedId()) {
            systemRecorder.commandStarted(UPDATE_SUBSCRIPTION, subscription.getIdAsString(), "Update Subscription started for Subscription: %s",
                    subscription.getIdAsString());

            Subscription subscriptionFromDb = null;
            final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
            final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
            try {
                final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
                subscriptionFromDb = subscriptionDao.findOneById(subscription.getId(), false);

                if (subscriptionFromDb == null) {
                    systemRecorder.commandFinishedError(UPDATE_SUBSCRIPTION, subscription.getIdAsString(),
                            SUBSCRIPTION_DOES_NOT_EXIST + subscription.getIdAsString());
                    systemRecorder.error(SUBSCRIPTION_NOT_FOUND, UPDATE_SUBSCRIPTION.getSource(), subscription.getIdAsString(),
                            PMICLog.Operation.EDIT);
                    throw new SubscriptionNotFoundDataAccessException(
                            "Subscription [" + subscription.getName() + "] with ID [" + subscription.getIdAsString() + "] does not exist.");
                }

                if (!subscriptionFromDb.getAdministrationState().isOneOf(AdministrationState.ACTIVE, AdministrationState.INACTIVE)) {
                    systemRecorder.error(INVALID_INPUT, UPDATE_SUBSCRIPTION.getSource(), subscription.getIdAsString(), PMICLog.Operation.EDIT);
                    throw new InvalidSubscriptionOperationException(String.format("Update not possible while Subscription is %s ,%s",
                            subscriptionFromDb.getAdministrationState(), subscriptionFromDb.getName()));
                }

                if (!Objects.equals(subscriptionFromDb.getPersistenceTime(), subscription.getPersistenceTime())) {
                    systemRecorder.error(INVALID_INPUT, UPDATE_SUBSCRIPTION.getSource(), subscription.getIdAsString(), PMICLog.Operation.EDIT);
                    throw new ConcurrentSubscriptionUpdateException(String.format("Subscription '%s' with id '%s' has been modified by another user.",
                            subscription.getName(), subscription.getIdAsString()));
                }

                // Admin state, operational state and task status remain unchanged.
                subscription.setAdministrationState(subscriptionFromDb.getAdministrationState());
                subscription.setTaskStatus(subscriptionFromDb.getTaskStatus());
                subscription.setOperationalState(subscriptionFromDb.getOperationalState());

                updateSubscription(subscription, trackingId, subscriptionDao);
            } finally {
                creationalContext.release();
            }
            return;
        }
        writeOperationsWithTrackingSupport.saveOrUpdateAsync(subscription, trackingId);
    }

    @Override
    public void delete(final Subscription subscription) throws InvalidSubscriptionOperationException, DataAccessException, RetryServiceException {
        if (subscription.getAdministrationState() != AdministrationState.INACTIVE) {
            final String message = String.format(ACTIVE_SUB_CANT_DELETE_MESSAGE, subscription.getAdministrationState());
            systemRecorder.commandOngoing(DELETE_SUBSCRIPTION, subscription.getIdAsString(), message + subscription.getIdAsString());
            systemRecorder.error(ACTIVE_SUBSCRIPTION_DELETE, DELETE_SUBSCRIPTION.getSource(), subscription.getIdAsString(), PMICLog.Operation.DELETE);
            throw new InvalidSubscriptionOperationException(message + subscription.getIdAsString());
        }

        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            subscriptionDao.delete(subscription);
        } finally {
            creationalContext.release();
        }
        systemRecorder.commandOngoing(DELETE_SUBSCRIPTION, subscription.getIdAsString(), "Successfully deleted subscription %s",
                subscription.getIdAsString());
    }

    @Override
    public void deleteById(final Long subscriptionId) throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            subscriptionDao.deleteById(subscriptionId);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public void updateSubscriptionDataOnInitationEvent(final List<Node> nodes, final Subscription subscription,
                                                       final InitiationEventType initiationEventType)
            throws DataAccessException {
        if (subscriptionDataAdjuster.shouldUpdateSubscriptionDataOnInitiationEvent(nodes, subscription, initiationEventType)) {
            saveOrUpdate(subscription);
        }
    }

    @Override
    public Subscription activate(final Subscription subscription, final Date persistenceTime)
            throws DataAccessException, ConcurrentSubscriptionUpdateException, ValidationException {
        throwExceptionIfPersistenceTimesAreNotEqual(subscription, persistenceTime);
        subscriptionValidatorSelector.getInstance(subscription.getType()).validateActivation(subscription);
        validateDescription(subscription);

        AdministrationState administrationState = AdministrationState.ACTIVATING;
        subscription.setAdministrationState(administrationState);
        if (SubscriptionStatus.Scheduled == subscription.getSubscriptionStatus()) {
            administrationState = AdministrationState.SCHEDULED;
        }
        subscription.setAdministrationState(administrationState);
        subscription.setTaskStatus(TaskStatus.OK);
        subscription.setUserActivationDateTime(new Date(timeGenerator.currentTimeMillis()));
        subscription.setUserDeActivationDateTime(null);

        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            subscriptionDao.saveOrUpdate(subscription);
        } finally {
            creationalContext.release();
        }
        return subscription;
    }

    @Override
    public Subscription deactivate(final Subscription subscription, final Date persistenceTime)
            throws ConcurrentSubscriptionUpdateException, InvalidSubscriptionOperationException, DataAccessException {
        throwExceptionIfPersistenceTimesAreNotEqual(subscription, persistenceTime);
        if (!subscription.getAdministrationState().isOneOf(AdministrationState.SCHEDULED, AdministrationState.ACTIVE)) {
            throw new InvalidSubscriptionOperationException(
                    String.format("Subscription %s with id %s cannot be deactivated because administration state is %s", subscription.getName(),
                            subscription.getIdAsString(), subscription.getAdministrationState()));
        }

        if (AdministrationState.SCHEDULED == subscription.getAdministrationState()) {
            subscription.setAdministrationState(AdministrationState.INACTIVE);
        } else {
            subscription.setAdministrationState(AdministrationState.DEACTIVATING);
            subscriptionManager.removeSubscriptionFromCache(subscription);
        }
        subscription.setTaskStatus(TaskStatus.OK);
        subscription.setUserDeActivationDateTime(new Date(timeGenerator.currentTimeMillis()));

        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            subscriptionDao.saveOrUpdate(subscription);
        } finally {
            creationalContext.release();
        }
        return subscription;
    }

    private static void throwExceptionIfPersistenceTimesAreNotEqual(final Subscription subscription, final Date persistenceTime)
            throws ConcurrentSubscriptionUpdateException {
        if (subscription.getPersistenceTime().getTime() != persistenceTime.getTime()) {
            throw new ConcurrentSubscriptionUpdateException(
                    String.format("Subscription %s  with id %s has changed by another user. Subscription PersistenceTime is %s "
                                    + "InitiationRequest persistenceTime is %s", subscription.getName(), subscription.getIdAsString(),
                            subscription.getPersistenceTime().getTime(), persistenceTime.getTime()));
        }
    }

    private void validateDescription(final Subscription subscription) throws ValidationException {
        if (UserType.USER_DEF.equals(subscription.getUserType())) {
            final Map<String, Object> globalCapabilities = pmCapabilityModelService.getSubscriptionAttributesGlobalCapabilities(subscription, SUBSCRIPTION_DESCRIPTION_REQUIRED);
            final Object capabilityValue = globalCapabilities.get(SUBSCRIPTION_DESCRIPTION_REQUIRED);
            if (capabilityValue instanceof Boolean && (Boolean) capabilityValue) {
                SubscriptionCommonValidator.validateDescription(subscription.getDescription());
            }
        }
    }

    @Override
    public void updateSubscriptionStateActivationTimeAndTaskStatus(final Subscription subscription, final AdministrationState administrationState,
                                                                   final TaskStatus taskStatus) throws SubscriptionNotFoundDataAccessException {
        subscription.setAdministrationState(administrationState);
        subscription.setTaskStatus(taskStatus);
        final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
        map.put(Subscription.Subscription220Attribute.administrationState.name(), administrationState.name());
        map.put(Subscription.Subscription220Attribute.taskStatus.name(), taskStatus.name());
        if (AdministrationState.ACTIVE == administrationState) {
            final Date date = new Date();
            subscription.setActivationTime(date);
            map.put(Subscription.Subscription220Attribute.activationTime.name(), date);
        }
        updateAttributes(subscription.getId(), map);
        subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));
    }
}
