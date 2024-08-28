/*
 * COPYRIGHT Ericsson 2017
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.services.generic;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.SubscriptionNotFoundDataAccessException;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventType;
import com.ericsson.oss.services.pm.services.exception.ConcurrentSubscriptionUpdateException;
import com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionOperationException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This interface should used only for dps write operation on Subscription object.
 */
public interface SubscriptionWriteOperationService {

    /**
     * Update the required subscription. It is the responsibility of the caller to make sure that the attributes and values are in the
     * format that the model expects, strings for enums and so on. Subscription persistenceTime must also be updated.
     *
     * @param subscriptionPoId
     *     - subscription id
     * @param attributes
     *     - Any attributes that are required to update the subscription
     * @param associationsToAdd
     *     - Any associations that are required to be added to the subscription
     * @param associationsToRemove
     *     - Any associations that are required to be removed from the subscription
     *
     * @throws SubscriptionNotFoundDataAccessException
     *     - in case when subscription with such id do not exist
     */
    void update(final long subscriptionPoId, final Map<String, Object> attributes, final Map<String, Set<String>> associationsToAdd,
                final Map<String, Set<String>> associationsToRemove) throws SubscriptionNotFoundDataAccessException;

    /**
     * Save subscription to database. It invokes saveOrUpdate method in case subscription has not an assigned id (it has not been already created
     * in dps) otherwise it invokes updateSubscription method.
     *
     * @param subscription
     *     - subscription to be saved. Must not have subscription ID.
     *
     * @throws DataAccessException
     *     - For any exception thrown from PM service layer.
     * @throws RetryServiceException
     *     - If the retry mechanism exhausts the retry attempt or the exception thrown is not one of the expected exception to retry on.
     */
    void manageSaveOrUpdate(final Subscription subscription) throws DataAccessException, RetryServiceException;

    /**
     * Delete subscription if exists in DPS. Employs Retry mechanism for transaction rollback.
     *
     * @param subscription
     *     - Subscription DTO to delete. Cannot be null and must have a valid subscription ID.
     *
     * @throws DataAccessException
     *     - if any exception from Database is thrown.
     * @throws RetryServiceException
     *     - If the retry mechanism exhausts the retry attempt or the exception thrown is not one of the expected exception to retry on.
     * @throws InvalidSubscriptionOperationException
     *     - if the subscription's admin state does not allow deactivation.
     */
    void deleteWithRetry(final Subscription subscription) throws DataAccessException, RetryServiceException, InvalidSubscriptionOperationException;

    /**
     * Update the required subscription attributes. It is the responsibility of the caller to make sure that the attributes and values are in the
     * format that the model expects, strings for enums and so on. Subscription persistenceTime must also be updated.
     *
     * @param subscriptionPoId
     *     - subscription id
     * @param attributes
     *     - Any attributes that are required to update the subscription
     *
     * @throws SubscriptionNotFoundDataAccessException
     *     - in case when subscription with such id do not exist
     */
    void updateAttributes(long subscriptionPoId, Map<String, Object> attributes) throws SubscriptionNotFoundDataAccessException;

    /**
     * Save subscription to database. Subscription must not contain a subscription ID.
     *
     * @param subscription
     *     - subscription to be saved. Must not have subscription ID.
     *
     * @throws DataAccessException
     *     - For any exception thrown from PM service layer.
     */
    void saveOrUpdate(Subscription subscription) throws DataAccessException;

    /**
     * Save subscription to database. Subscription must not contain a subscription ID.
     * Employs Retry mechanism for transaction rollback.
     *
     * @param subscription
     *     - subscription to be saved. Must not have subscription ID.
     *
     * @throws DataAccessException
     *     - For any exception thrown from PM service layer.
     */
    void saveOrUpdateWithRetry(Subscription subscription) throws DataAccessException;

    /**
     * For SAVE action:<br>
     * Subscription must not contain a subscription ID.<br>
     * <br>
     * <br>
     * For UPDATE action: <br>
     * Subscription must exist in database. If subscription is active and it is a ResourceSubscription, for all the nodes that have been added or
     * removed, activation/deactivation event will also be sent.<br>
     * <b>NOTE:</b> The purpose of this method is to be used from REST endpoint. Use
     * {@link SubscriptionWriteOperationService#saveOrUpdate(Subscription)} (Subscription)} otherwise.
     *
     * @param subscription
     *     - subscription to be saved. Must not have subscription ID.
     * @param trackingId
     *     - tracking id
     *
     * @throws DataAccessException
     *     - For any exception thrown from PM service layer.
     * @throws RetryServiceException
     *     - If the retry mechanism exhausts the retry attempt or the exception thrown is not one of the expected exception to retry on.
     * @throws InvalidSubscriptionOperationException
     *     - If the update is not possible for this subscription. Example: AdminState is UPDATING
     * @throws ConcurrentSubscriptionUpdateException
     *     - if the subscription's persistence time is not the same as the persistence time of the subscription DTO to save. This indicates
     *     that in the process of retrieving the subscription on the UI, making changes to it and updating it, someone else already updated
     *     the same subscription. In order to prevent this update to override the previous changes, the execution is halted with this
     *     exception.
     */
    void saveOrUpdate(Subscription subscription, String trackingId)
        throws DataAccessException, RetryServiceException, InvalidSubscriptionOperationException, ConcurrentSubscriptionUpdateException;

    /**
     * Delete subscription with retry mechanism if administration state is INACTIVE.
     *
     * @param subscription
     *     - subscription object to delete. Must have a valid poid.
     *
     * @throws InvalidSubscriptionOperationException
     *     - if administration state is not INACTIVE
     * @throws DataAccessException
     *     - if any exception is thrown from Database
     * @throws RetryServiceException
     *     - if database access thrown a Runtime exception which prompted the retry mechanism to retry the execution but retry limit was
     *     reached.
     */
    void delete(Subscription subscription) throws InvalidSubscriptionOperationException, DataAccessException, RetryServiceException;

    /**
     * Deletes a given object by p. The Long is used to extract corresponding object from Data Access layer. If no such object is found for a valid p,
     * no action will be taken.
     *
     * @param subscriptionId
     *     - a non-null, positive number Long of the object.
     *
     * @throws DataAccessException
     *     - if an exception from Data Access layer is thrown
     */
    void deleteById(Long subscriptionId) throws DataAccessException;

    /**
     * Applies and persists specific correction on Subscription Data required on initiation events.
     *
     * @param nodes
     *     - nodes to activate/deactivate.
     * @param subscription
     *     - subscription object
     * @param initiationEventType
     *     - the initiation event
     *
     * @throws DataAccessException
     *     - if an exception is thrown from database or a DPS data related fault is found.
     */
    void updateSubscriptionDataOnInitationEvent(final List<Node> nodes, final Subscription subscription,
                                                final InitiationEventType initiationEventType)
        throws DataAccessException;

    /**
     * Activates subscription. This is not a synchronous operation, if validation passes, the admin state of the subscription will simply be updated
     * to ACTIVATING/SCHEDULED.
     *
     * @param subscription
     *     - subscription to activate. Will fail validation if the subscription DTO is not loaded with nodes (if it has nodes).
     * @param persistenceTime
     *     - the persistence time of the subscription at which point the activation action was decided to be executed.
     *
     * @return - the subscription object with updated persistence time and administration state.
     * @throws DataAccessException
     *     - if during the validation process or during update, the access to database throws any exception
     * @throws ConcurrentSubscriptionUpdateException
     *     - if the persistence time of the subscription is not matching the persistence time provided.
     * @throws ValidationException
     *     - if validations fails.
     */
    Subscription activate(Subscription subscription, Date persistenceTime)
        throws DataAccessException, ConcurrentSubscriptionUpdateException, ValidationException;

    /**
     * Deactivate subscription. This is not a synchronous operation, if validation passes, the admin state of the subscription will simply be updated
     * to DEACTIVATING/INACTIVE.
     *
     * @param subscription
     *     - subscription to activate. Does not require nodes to be loaded.
     * @param persistenceTime
     *     - the persistence time of the subscription at which point the deactivation action was decided to be executed.
     *
     * @return - the subscription object with updated persistence time and administration state.
     * @throws DataAccessException
     *     - if during the validation process or during update, the access to database throws any exception
     * @throws ConcurrentSubscriptionUpdateException
     *     - if the persistence time of the subscription is not matching the persistence time provided.
     * @throws InvalidSubscriptionOperationException
     *     - if the subscription's admin state does not allow deactivation.
     */
    Subscription deactivate(Subscription subscription, Date persistenceTime)
        throws ConcurrentSubscriptionUpdateException, InvalidSubscriptionOperationException, DataAccessException;

    /**
     * Update subscriptions object with admin state,task status and activation time details.
     *
     * @param subscription
     *     - subscription to activate. Does not require nodes to be loaded.
     * @param administrationState
     *     administration state of the subscription
     * @param taskStatus
     *     - task status of subscription
     *
     * @throws SubscriptionNotFoundDataAccessException
     *     - in case when subscription with such id do not exist
     */
    void updateSubscriptionStateActivationTimeAndTaskStatus(final Subscription subscription, final AdministrationState administrationState,
                                                            final TaskStatus taskStatus) throws SubscriptionNotFoundDataAccessException;
}
