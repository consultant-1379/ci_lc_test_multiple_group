/*******************************************************************************
 * COPYRIGHT Ericsson 2017
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.services.generic;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ericsson.oss.pmic.dto.ModelInfo;
import com.ericsson.oss.pmic.dto.PaginatedList;
import com.ericsson.oss.pmic.dto.PersistenceTrackingStatus;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.IncorrectResultSizeDataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.exception.ServiceException;
import com.ericsson.oss.services.pm.exception.SubscriptionNotFoundDataAccessException;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This interface should used only for dps read operation on Subscription object.
 */
@SuppressWarnings({"EjbInterfaceMethodInspection", "DuplicateThrows"})
public interface SubscriptionReadOperationService {

    /**
     * Verify if the object with Fdn exists in the Data Access layer.
     *
     * @param fdn
     *         - non-null, non-empty Fully Distinguished Name of the object
     *
     * @return true - found, false - not found
     * @throws DataAccessException
     *         - if any exception from Data Access layer is thrown
     */
    boolean existsByFdn(String fdn) throws DataAccessException;

    /**
     * Retrieves an object without associations.
     *
     * @param subscriptionId
     *         - object identifier
     *
     * @return Returns an object without associations if it was found or null otherwise.
     * @throws DataAccessException
     *         - if any exception from Data Access layer is thrown
     */
    Subscription findOneById(Long subscriptionId) throws DataAccessException;

    /**
     * Generic validation that depends on subscription's type and current status.
     *
     * @param subscription
     *         - Subscription object
     *
     * @throws ValidationException
     *         - if validation fails.
     */
    void validate(Subscription subscription) throws ValidationException;

    /**
     * Using subscription's node types and version, extract available counters/events from model service, compare them with counters/events that are
     * assigned to the subscription and remove any that do not exist in the models. For update attributes with predefined
     * values.
     *
     * @param subscription
     *         - subscription.
     */
    void adjustPfmSubscriptionData(Subscription subscription);

    /**
     * Using subscription's node types and version, extract available counters/events from model service, compare them with counters/events that are
     * assigned to the subscription and remove any that do not exist in the models. For imported subscriptions, update attributes with predefined
     * values.
     *
     * @param subscription
     *         - subscription.
     *
     * @throws DataAccessException
     *         - if an exception is thrown from database or a DPS data related fault is found.
     */
    void adjustSubscriptionData(Subscription subscription) throws DataAccessException;

    /**
     * Validates subscription attributes and updates counters/events according to available countes/events in model service. For imported
     * subscriptions, predefined attributes are set and nodes are updated from DPS.
     *
     * @param subscription
     *         - subscription objects to validate.
     *
     * @throws ValidationException
     *         - if validation fails.
     * @throws DataAccessException
     *         - if an exception is thrown from database or a DPS data related fault is found.
     * @throws PfmDataException
     *         - exception when dealing with pfm_counters/pfm_events
     */
    void validateAndAdjustSubscriptionData(Subscription subscription) throws ValidationException, DataAccessException, PfmDataException;

    /**
     * Creates tracking id.
     *
     * @param initialPersistenceTrackingStatus
     *         - initialPersistenceTrackingStatus
     *
     * @return Returns tracking id.
     */
    String generateTrackingId(PersistenceTrackingStatus initialPersistenceTrackingStatus);

    /**
     * Gets current status by tracking id.
     *
     * @param trackingId
     *         - the non-null, non-empty tracker id.
     *
     * @return - PersistenceTracking object if tracker Id exists. Will return null if tracker id does not exist.
     * @throws ServiceException
     *         - if any Exception is thrown when reading tracker from cache.
     */
    PersistenceTrackingStatus getTrackingStatus(String trackingId) throws ServiceException;

    /**
     * Get nodes from subscription for the corresponding page and page size..
     *
     * @param subscriptionId
     *         - subscription ID
     * @param page
     *         - page number of paginated node list to retrieve. Cannot be negative number.
     * @param pageSize
     *         - size of pages. Cannot be negative.
     *
     * @return - Paginated list containing corresponding nodes for the specified page. Will return an empty paginated list of no nodes are found for
     * the given offset (page).
     * @throws DataAccessException
     *         - If any exception from Database is thrown.
     * @throws RetryServiceException
     *         - If the retry mechanism exhausts the retry attempt or the exception thrown is not one of the expected exception to retry on.
     */

    PaginatedList getNodesFromSubscription(long subscriptionId, int page, int pageSize) throws DataAccessException, RetryServiceException;

    /**
     * Find subscriptions with no nodes with given restrictions. If inputs are null, all subscriptions will be returned with the minimum amount of
     * attributes. Please see implementation for details.<br>
     * <b>NOTE:</b>This is a REST endpoint method. Use alternative ways to get the same information.
     *
     * @param names
     *         - The subscription names. Can be null, cannot contain null elements.
     * @param types
     *         - The subscription types. Can be null, cannot contain null elements.
     * @param adminStatuses
     *         - The subscription administrationStates. Can be null, cannot contain null elements.
     *
     * @return - list of Subscriptions or empty list.
     * @throws ServiceException
     *         - If an exception is thrown from Database.
     */
    List<Subscription> findAllFilteredBy(List<String> names, List<String> types, List<String> adminStatuses) throws ServiceException;

    /**
     * Check whether there is at least one active subscription for the given type where files are supposed to be collected. EventSubscriptions, Ctum
     * and Uetrace subscriptions may have output mode pointing to STREAMING only, in such cases even if subscription is active, we do not file
     * collect.
     *
     * @param subscriptionType
     *         - subscription type
     *
     * @return - true or false
     * @throws DataAccessException
     *         - if an exception is thrown from database while extracting subscriptions for given type.
     */
    boolean areThereAnyActiveSubscriptionsWithSubscriptionTypeForFileCollection(SubscriptionType subscriptionType) throws DataAccessException;

    /**
     * Retrieves an object with or without associations.
     *
     * @param subscriptionId
     *         - Long
     * @param loadAssociations
     *         If true - loads associated objects from the database, false - does nothing with associations.
     *
     * @return Returns an object without associations if it was found or null otherwise.
     * @throws DataAccessException
     *         - if any exception from Data Access layer is thrown
     */
    Subscription findOneById(Long subscriptionId, boolean loadAssociations) throws DataAccessException;

    /**
     * Checks whether the subscription with given name exists.
     *
     * @param subscriptionName
     *         exact subscription name, non-null, non-empty String
     *
     * @return - true or false
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    boolean existsBySubscriptionName(String subscriptionName) throws DataAccessException;

    /**
     * Get count of the Criteria Based Subscriptions (CBS).
     *
     * @return count of the Criteria Based Subscriptions (CBS), only Resource Subscriptions could be CBS
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    int countCriteriaBasedSubscriptions() throws DataAccessException;

    /**
     * Get the subscription ID by subscription's name.
     *
     * @param subscriptionName
     *         - exact subscription name - cannot be null or empty string.
     *
     * @return - subscriptionId or null if not found
     * @throws IncorrectResultSizeDataAccessException
     *         in case if there more then one subscription with such name
     * @throws DataAccessException
     *         - if an exception is thrown from the Data Access layer.
     */
    Long findIdByExactName(String subscriptionName) throws DataAccessException;

    /**
     * Extract all subscription ids that have this name. Please note that subscription names must be unique for subscriptions of same model type, but
     * different types may have the same name.
     *
     * @param subscriptionName
     *         - the exact subscription name. Cannot be null or empty.
     *
     * @return - List of subscription IDs or empty list.
     * @throws DataAccessException
     *         - if an exception is thrown from the Data Access layer.
     */
    List<Long> findIdsByExactName(String subscriptionName) throws DataAccessException;

    /**
     * Retrieves all ids for given subscriptionNamePattern.
     *
     * @param subscriptionNamePattern
     *         full or part of the subscription name. Cannot be null or empty string.
     *
     * @return List of ids that match given name pattern or empty List.
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    List<Long> findAllIdsByName(String subscriptionNamePattern) throws DataAccessException;

    /**
     * Retrieves all ids for given subscriptionTypes.
     *
     * @param subscriptionTypes
     *         subscription types. Cannot be null. Cannot contain null values.
     *
     * @return List of all id that match given subscription type. Returns empty list if no Subscriptions were found.
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    List<Long> findAllIdsBySubscriptionType(SubscriptionType... subscriptionTypes) throws DataAccessException;

    /**
     * Retrieve a subscription for given subscription name.
     *
     * @param subscriptionName
     *         exact subscription name
     * @param loadAssociations
     *         If true - loads associated objects from the database, false - does nothing with associations.
     *
     * @return Subscription that matches given name or null if no such subscription exists.
     * @throws IncorrectResultSizeDataAccessException
     *         - if result size doesn't match expected result size
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    Subscription findOneByExactName(String subscriptionName, boolean loadAssociations) throws DataAccessException;

    /**
     * Retrieve a subscription for given subscriptionTypes and administrationStates. Either subscriptionTypes or administrationStates can be null or
     * empty but not both at the same time.
     *
     *
     *
     * @param subscriptionTypes
     *         subscription types or null
     * @param administrationStates
     *         administration states or null
     * @param loadAssociations
     *         If true - loads associated objects from the database, false - does nothing with associations.
     *
     * @return List of subscriptions or empty list
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    List<Subscription> findAllBySubscriptionTypeAndAdministrationState(SubscriptionType[] subscriptionTypes,
                                                                       AdministrationState[] administrationStates, boolean loadAssociations) throws DataAccessException;

    /**
     * Retrieve all subscriptions for given subscriptionType and administrationState and with given attributes.
     *
     * @param subscriptionType
     *         - the type of the subscription for which to query on. Cannot be null.
     * @param attributesWithValuesToMatch
     *         - Map of attributes to filter on, where key is the subscription attribute name and value is the list of expected values to match.
     *         Map must not be null, empty, contain null keys, null values. Also values must not be empty or contain null elements. <br>
     *         <b>NOTE:</b> All attributes to filter on must exist on the model of the subscription type provided (including all the model's
     *         children)
     * @param loadAssociations
     *         If true - loads associated objects from the database, false - does nothing with associations.
     *
     * @return List of subscriptions or empty list
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer.
     */
    List<Subscription> findAllWithSubscriptionTypeAndMatchingAttributes(SubscriptionType subscriptionType,
                                                                        Map<String, List<Object>> attributesWithValuesToMatch,
                                                                        boolean loadAssociations)
            throws DataAccessException;

    /**
     * Retrieve a subscription for given subscriptionNamePatterns, subscriptionTypes and administrationStates. Any input can be null but not all at
     * the same time.
     *
     * @param subscriptionNames
     *         Subscription name or null
     * @param subscriptionTypes
     *         subscription types or null
     * @param administrationStates
     *         administration states or null
     * @param loadAssociations
     *         If true - loads associated objects from the database, false - does nothing with associations.
     *
     * @return List of subscriptions or empty list
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    List<Subscription> findAllByNameAndSubscriptionTypeAndAdministrationState(String[] subscriptionNames, SubscriptionType[] subscriptionTypes,
                                                                              AdministrationState[] administrationStates, boolean loadAssociations) throws DataAccessException;

    /**
     * Retrieve the Criteria Based subscriptions (CBS).
     *
     * @param loadAssociations
     *         If true - loads associated objects from the database, false - does nothing with associations.
     *
     * @return a list of the resource subscriptions or an empty list in case if wasn't found any
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    List<ResourceSubscription> findAllCriteriaBasedResourceSubscriptions(boolean loadAssociations) throws DataAccessException;

    /**
     * Generates an unique trace reference for an UE Trace profile. For UE Trace subscription only.
     *
     * @param outputMode
     *         output mode of subscription
     *
     * @return trace reference
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    String generateUniqueTraceReference(OutputModeType outputMode) throws DataAccessException;

    /**
     * Count nodes associated with given subscriptionId.
     *
     * @param subscriptionId
     *         subscription id
     *
     * @return Number of nodes or 0
     * @throws SubscriptionNotFoundDataAccessException
     *         - in case when subscription with such id doesn't exist
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    int countNodesById(Long subscriptionId) throws DataAccessException;

    /**
     * Get subscription ids for the given subscription types and admin states.
     *
     * @param subscriptionTypes
     *         - Subscription types. Can be null or empty. Cannot contain null enums.
     * @param administrationStates
     *         - Admin states. Can be null or empty. Cannot contain null enums.
     *
     * @return - list of subscription ids or empty list
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    List<Long> findAllIdsBySubscriptionTypeAndAdminState(SubscriptionType[] subscriptionTypes, AdministrationState[] administrationStates)
            throws DataAccessException;

    /**
     * Retrieve a list of Subscription for the given modelInfo. this method is useful to retrieve subscriptions based on an abstract type like
     * ResourceSubscription or EventSubscription. Associations are loaded or not as for boolean parameter
     *
     * @param subscriptionModel
     *         - The ModelInfo associated to the Subscription type (can be retrieved using SubscriptionType enum getModelInfo method())
     * @param loadAssociations
     *         - If true - loads associated objects from the database, false - does nothing with associations.
     *
     * @return - list of subscriptions or empty list
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    List<Subscription> findAllBySubscriptionModelInfo(final ModelInfo subscriptionModel, final boolean loadAssociations) throws DataAccessException;

    /**
     * Find subscription by Id.
     *
     * @param subscriptionId
     *         - subscription ID
     * @param loadNodes
     *         - true if the DTO should be constructed with nodes.
     *
     * @return - Subscription DTO object or null if no such subscription found in database.
     * @throws DataAccessException
     *         - If any aexception from Database is thrown.
     * @throws RetryServiceException
     *         - If the retry mechanism exhausts the retry attempt or the exception thrown is not one of the expected exception to retry on.
     */
    Subscription findByIdWithRetry(long subscriptionId, boolean loadNodes) throws DataAccessException, RetryServiceException;

    /**
     * Find nodes from subscription. Please <b>NOTE</b> that this method can return incorrect node count if the subscription was not extracted from
     * DPS using a DAO interface. In most cases, when subscription is constructed by Jaxb from REST endpoint, it comes with nodes as well.
     *
     * @param subscription
     *         - subscription object. Cannot be null.
     *
     * @return - count of nodes
     * @throws DataAccessException
     *         - if any exception is thrown from Database
     * @throws RetryServiceException
     *         - if database access thrown a Runtime exception which prompted the retry mechanism to retry the execution but retry limit was
     *         reached.
     */
    int countNodesWithRetry(Subscription subscription) throws DataAccessException, RetryServiceException;

    /**
     * Find all subscriptions with given name pattern.
     *
     * @param name
     *         - subscription name pattern
     * @param withNodes
     *         - true if nodes should be loaded, false otherwise
     *
     * @return - list of subscriptions or empty list.
     * @throws DataAccessException
     *         - if any exception is thrown from Database
     * @throws RetryServiceException
     *         - if database access thrown a Runtime exception which prompted the retry mechanism to retry the execution but retry limit was
     *         reached.
     */
    List<Subscription> findAllByNameWithRetry(String name, boolean withNodes) throws DataAccessException, RetryServiceException;

    /**
     * Gets the list of available nodes. Available nodes are calculated filtering out from the input Subscription nodes list all nodes already
     * included in other subscription of the same type.
     *
     * @param subscriptionId
     *         - the Id of the subscriotion or "0" if not available
     * @param subscriptionType
     *         - subscriptionType object. Cannot be null.
     * @param nodesFdn
     *         - the list of nodes fdn
     *
     * @throws DataAccessException
     *         - if any exception is thrown from Database
     * @throws RetryServiceException
     *         - if database access thrown a Runtime exception which prompted the retry mechanism to retry the execution but retry limit was
     *         reached.
     */
    void getAvailableNodes(final long subscriptionId, final SubscriptionType subscriptionType, final Set<String> nodesFdn)
            throws RetryServiceException, DataAccessException;

    /**
     * @param subscriptionId
     *         - the Id of the subscription or "0" if not available
     *
     * @return - true if subscription support File Collection
     * @throws DataAccessException
     *         - if any exception is thrown from Database
     * @throws RuntimeDataAccessException
     *         - if any runtime exception is thrown
     */
    boolean doesSubscriptionSupportFileCollection(Long subscriptionId) throws DataAccessException;

    /**
     * Check whether subscription support file collection or not.
     *
     * @param subscription
     *         - subscription object without associations
     *
     * @return - true or false if the subscription is with STREAMING outputMode.
     */
    boolean doesSubscriptionSupportFileCollection(Subscription subscription);

    /**
     * Check whether there are any subscriptions with given name and model info
     *
     * @param subscriptionName
     *         name of a subscription. Cannot contain null values.
     * @param subscriptionModel
     *         model information related to the subscription.
     *
     * @return - true or false
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    boolean existsBySubscriptionNameAndModelInfo(String subscriptionName, ModelInfo subscriptionModel) throws DataAccessException;

    /**
     * Find all subscriptions with given name pattern.
     *
     * @param subscriptionName
     *         - subscription names array
     * @param subscriptionModel
     *         - model information related to the subscription.
     * @param loadNodes
     *         - true if the DTO should be constructed with nodes.
     *
     * @return - list of subscriptions or empty list.
     * @throws DataAccessException
     *         - if any exception is thrown from Database
     * @throws RetryServiceException
     *         - if database access thrown a Runtime exception which prompted the retry mechanism to retry the execution but retry limit was
     *         reached.
     */
    List<Subscription> findAllBySubscriptionNameAndModelInfo(String subscriptionName, ModelInfo subscriptionModel, boolean loadNodes)
            throws DataAccessException;

    /**
     * Generates an unique trace reference for an MTR subscription only.
     *
     * @param traceRefName
     *         traceRefName of subscription
     * @param modelNamespace
     *         modelNamespace of subscription
     * @param modelType
     *         modelType of subscription
     *
     * @return trace reference
     * @throws DataAccessException
     *         - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    String generateUniqueTraceReference(final String traceRefName, final String modelNamespace, final String modelType) throws DataAccessException;
}
