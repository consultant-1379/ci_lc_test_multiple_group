/*******************************************************************************
 * COPYRIGHT Ericsson 2017
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.services.generic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ejb.EJBTransactionRolledbackException;
import javax.ejb.Stateless;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dao.SubscriptionDao;
import com.ericsson.oss.pmic.dto.ModelInfo;
import com.ericsson.oss.pmic.dto.PaginatedList;
import com.ericsson.oss.pmic.dto.PersistenceTrackingStatus;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription.CellTraceSubscription210Attribute;
import com.ericsson.oss.pmic.dto.subscription.CtumSubscription;
import com.ericsson.oss.pmic.dto.subscription.EbmSubscription.EbmSubscription120Attribute;
import com.ericsson.oss.pmic.dto.subscription.EventSubscription;
import com.ericsson.oss.pmic.dto.subscription.MtrSubscription.MtrSubscription100Attribute;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription.Subscription220Attribute;
import com.ericsson.oss.pmic.dto.subscription.UETraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.UETraceSubscription.UeTraceSubscription220Attribute;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.util.CollectionUtil;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterLocal;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterQualifier;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.exception.ServiceException;
import com.ericsson.oss.services.pm.exception.SubscriptionNotFoundDataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.validator.ValidateSubscription;
import com.ericsson.oss.services.pm.initiation.validators.SubscriptionValidatorSelector;
import com.ericsson.oss.services.pm.retry.interceptor.RetryOnNewTransaction;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import com.ericsson.oss.services.pm.time.TimeGenerator;

/**
 * Intermediary helper class between DAO and service layer. This class should used only for dps read operation on Subscription object.
 */
@SuppressWarnings("DuplicateThrows")
@Stateless
public class SubscriptionReadOperationServiceImpl implements SubscriptionReadOperationService {

    @Inject
    private Logger logger;
    @Inject
    private BeanManager beanManager;
    @Inject
    private NodeService nodeService;
    @Inject
    private TimeGenerator timeGenerator;
    @Inject
    private SubscriptionValidatorSelector subscriptionValidatorSelector;
    @Inject
    @SubscriptionDataAdjusterQualifier
    private SubscriptionDataAdjusterLocal<Subscription> subscriptionDataAdjuster;
    @Inject
    private SubscriptionServiceWriteOperationsWithTrackingSupport writeOperationsWithTrackingSupport;

    @Override
    public boolean existsByFdn(final String fdn) throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.existsByFdn(fdn);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public Subscription findOneById(final Long subscriptionId) throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.findOneById(subscriptionId);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public Subscription findOneById(final Long subscriptionId, final boolean loadAssociations)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.findOneById(subscriptionId, loadAssociations);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public void validate(final Subscription subscription) throws ValidationException {
        subscriptionValidatorSelector.getInstance(subscription.getType()).validate(subscription);
    }

    @Override
    public void adjustPfmSubscriptionData(final Subscription subscription) {
        subscriptionDataAdjuster.adjustPfmSubscriptionData(subscription);
    }

    @Override
    public void adjustSubscriptionData(final Subscription subscription) throws DataAccessException {
        subscriptionDataAdjuster.updateImportedSubscriptionWithCorrectValues(subscription);
        subscriptionDataAdjuster.correctSubscriptionData(subscription);
    }

    @Override
    public void validateAndAdjustSubscriptionData(final Subscription subscription)
            throws ValidationException, DataAccessException, PfmDataException {
        final ValidateSubscription<Subscription> subscriptionValidator = subscriptionValidatorSelector.getInstance(subscription.getType());
        if (subscription.getIsImported()) {
            subscriptionDataAdjuster.updateImportedSubscriptionWithCorrectValues(subscription);
            subscriptionValidator.validateImport(subscription);
        }
        if (subscription.getIsExported()) {
            subscriptionValidator.validateExport(subscription);
        }
        subscriptionValidator.validate(subscription);
        subscriptionDataAdjuster.correctSubscriptionData(subscription);
    }

    @Override
    public String generateTrackingId(final PersistenceTrackingStatus initialPersistenceTrackingStatus) {
        return writeOperationsWithTrackingSupport.generateTrackingId(initialPersistenceTrackingStatus);
    }

    @Override
    public PersistenceTrackingStatus getTrackingStatus(final String trackingId) throws ServiceException {
        return writeOperationsWithTrackingSupport.getTrackingStatus(trackingId);
    }

    @Override
    @RetryOnNewTransaction(retryOn = {RuntimeDataAccessException.class, EJBTransactionRolledbackException.class})
    public PaginatedList getNodesFromSubscription(final long subscriptionId, final int pageNumber, final int pageSize)
            throws DataAccessException, RetryServiceException {
        final Subscription subscription = findOneById(subscriptionId, true);

        if (subscription instanceof ResourceSubscription) {
            final ResourceSubscription resourceSubscription = (ResourceSubscription) subscription;
            final List<Node> nodes = resourceSubscription.getNodes();
            if (!nodes.isEmpty()) {
                final PaginatedList<Node> nodeList = new PaginatedList<>(new ArrayList<Node>(), pageNumber, pageSize, nodes.size());
                return getPaginatedNodeList(nodeList, nodes);
            }
        } else {
            throw new SubscriptionNotFoundDataAccessException(String.format(
                    "Was unable to find nodes for subscription with id %s. Subscription does not exist or is not a ResourceSubscription.",
                    subscriptionId));
        }
        return new PaginatedList<>(new ArrayList<Node>(0), pageNumber, pageSize, 0);
    }

    private PaginatedList getPaginatedNodeList(final PaginatedList<Node> nodeList, final List<Node> nodes) {
        if (nodes.size() >= nodeList.getStartIndex() && nodes.size() >= nodeList.getEndIndex()) {
            nodeList.setItems(nodes.subList(nodeList.getStartIndex() - 1, nodeList.getEndIndex()));
            return nodeList;
        }
        return null;
    }

    @Override
    public List<Subscription> findAllFilteredBy(final List<String> names, final List<String> types, final List<String> adminStatuses)
            throws ServiceException {

        // get all subscriptions if inputs are null or empty
        if (CollectionUtil.isNullOrEmpty(names) && CollectionUtil.isNullOrEmpty(types) && CollectionUtil.isNullOrEmpty(adminStatuses)) {

            final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
            final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
            try {
                final Map<SubscriptionType, Iterable<Enum>> extraAttributesPerSubscriptionType = buildExtraSubscriptionTypeAttribute();

                final List<Enum> baseAttributes = new ArrayList<>();
                baseAttributes.addAll(Arrays.asList(Subscription220Attribute.values()));
                // remove CDT as projection query doesn't work on complex data types
                baseAttributes.remove(Subscription220Attribute.scheduleInfo);
                final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
                return subscriptionDao.findAll(baseAttributes, extraAttributesPerSubscriptionType);
            } catch (final DataAccessException | RuntimeDataAccessException e) {
                creationalContext.release();
                throw new ServiceException(e.getMessage(), e);
            }
        }

        return findAllSubscriptions(names, types, adminStatuses);
    }

    private List<Subscription> findAllSubscriptions(final List<String> names, final List<String> types, final List<String> adminStatuses)
            throws ServiceException {
        try {
            // convert inputs to enums and validate. Enums must not be null
            final String[] subscriptionNames = null == names ? null : names.toArray(new String[names.size()]);
            SubscriptionType[] subscriptionTypes = null;
            AdministrationState[] administrationStates = null;
            if (types != null) {
                subscriptionTypes = SubscriptionType.fromStringsAsArray(types);
            }
            if (adminStatuses != null) {
                administrationStates = AdministrationState.fromStringsAsArray(adminStatuses);
            }
            if (CollectionUtil.containsNullElements(subscriptionTypes) || CollectionUtil.containsNullElements(administrationStates)
                    || CollectionUtil.containsNullOrEmptyString(subscriptionNames)) {
                throw new IllegalArgumentException("Inputs contain null elements!");
            }
            return findAllByNameAndSubscriptionTypeAndAdministrationState(subscriptionNames, subscriptionTypes, administrationStates, false);
        } catch (final DataAccessException | RuntimeDataAccessException e) {
            throw new ServiceException(e.getMessage(), e);
        }
    }

    private Map<SubscriptionType, Iterable<Enum>> buildExtraSubscriptionTypeAttribute() {
        final Map<SubscriptionType, Iterable<Enum>> extraAttributesPerSubscriptionType = new EnumMap<>(SubscriptionType.class);
        extraAttributesPerSubscriptionType.put(SubscriptionType.RESOURCE,
                Collections.<Enum>singletonList(ResourceSubscription.ResourceSubscription120Attribute.numberOfNodes));
        extraAttributesPerSubscriptionType.put(SubscriptionType.CTUM,
                Collections.<Enum>singletonList(CtumSubscription.CtumSubscription100Attribute.numberOfNodes));
        //TODO can this be removed???
        extraAttributesPerSubscriptionType.put(SubscriptionType.CELLTRACE,
                Arrays.asList(CellTraceSubscription210Attribute.ebsEnabled, CellTraceSubscription210Attribute.cellTraceCategory));
        extraAttributesPerSubscriptionType.put(SubscriptionType.EBM, Collections.<Enum>singletonList(EbmSubscription120Attribute.ebsEnabled));
        extraAttributesPerSubscriptionType.put(SubscriptionType.UETRACE,
                Collections.<Enum>singletonList(UeTraceSubscription220Attribute.traceReference));
        extraAttributesPerSubscriptionType.put(SubscriptionType.MTR,
                Collections.<Enum>singletonList(MtrSubscription100Attribute.traceReference));
        extraAttributesPerSubscriptionType.put(SubscriptionType.STATISTICAL,
                Collections.<Enum>singletonList(ResourceSubscription.ResourceSubscription120Attribute.selectedNeTypes));
        return extraAttributesPerSubscriptionType;
    }

    @Override
    public boolean doesSubscriptionSupportFileCollection(final Subscription subscription) {
        if (subscription instanceof EventSubscription) {
            return OutputModeType.STREAMING != ((EventSubscription) subscription).getOutputMode();
        } else if (subscription instanceof CtumSubscription) {
            return OutputModeType.STREAMING != ((CtumSubscription) subscription).getOutputMode();
        } else if (subscription instanceof UETraceSubscription) {
            return OutputModeType.STREAMING != ((UETraceSubscription) subscription).getOutputMode();
        }
        return true;
    }

    @Override
    public boolean doesSubscriptionSupportFileCollection(final Long subscriptionId) throws DataAccessException {
        // File collection should happen for PRE-DEF or USER-DEF scanners that is not associated to subscription
        if (!Scanner.isValidSubscriptionId(subscriptionId)) {
            return true;
        }
        try {
            final Subscription subscription = findOneById(subscriptionId, false);
            if (subscription == null) {
                logger.error("Unable to update status of subscription because subscription with id {} does not exist", subscriptionId);
                return false;
            }
            return doesSubscriptionSupportFileCollection(subscription);
        } catch (final SubscriptionNotFoundDataAccessException e) {
            logger.error("Subscription not found with Id {} ", subscriptionId);
        }
        return false;
    }

    @Override
    public boolean areThereAnyActiveSubscriptionsWithSubscriptionTypeForFileCollection(final SubscriptionType subscriptionType)
            throws DataAccessException {
        final SubscriptionType[] subscriptionTypes = {subscriptionType};
        final AdministrationState[] administrationStates = {AdministrationState.ACTIVE};
        List<Subscription> activeSubscriptions = null;
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            activeSubscriptions = subscriptionDao.findAllBySubscriptionTypeAndAdministrationState(subscriptionTypes, administrationStates, false);
        } finally {
            creationalContext.release();
        }
        for (final Subscription subscription : activeSubscriptions) {
            if (doesSubscriptionSupportFileCollection(subscription)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean existsBySubscriptionName(final String subscriptionName)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.existsBySubscriptionName(subscriptionName);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public int countCriteriaBasedSubscriptions() throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.countCriteriaBasedSubscriptions();
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public Long findIdByExactName(final String subscriptionName)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.findIdByExactName(subscriptionName);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public List<Long> findIdsByExactName(final String subscriptionName) throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.findIdsByExactName(subscriptionName);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public List<Long> findAllIdsByName(final String subscriptionNamePattern)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.findAllIdsByName(subscriptionNamePattern);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public List<Long> findAllIdsBySubscriptionType(final SubscriptionType... subscriptionTypes)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.findAllIdsBySubscriptionType(subscriptionTypes);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public Subscription findOneByExactName(final String subscriptionName, final boolean loadAssociations)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.findOneByExactName(subscriptionName, loadAssociations);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public List<Subscription> findAllBySubscriptionTypeAndAdministrationState(final SubscriptionType[] subscriptionTypes,
                                                                              final AdministrationState[] administrationStates,
                                                                              final boolean loadAssociations)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.findAllBySubscriptionTypeAndAdministrationState(subscriptionTypes, administrationStates, loadAssociations);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public List<Subscription> findAllWithSubscriptionTypeAndMatchingAttributes(final SubscriptionType subscriptionType,
                                                                               final Map<String, List<Object>> attributesWithValuesToMatch,
                                                                               final boolean loadAssociations)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.findAllWithSubscriptionTypeAndMatchingAttributes(subscriptionType, attributesWithValuesToMatch, loadAssociations);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public List<Subscription> findAllByNameAndSubscriptionTypeAndAdministrationState(final String[] subscriptionNames,
                                                                                     final SubscriptionType[] subscriptionTypes,
                                                                                     final AdministrationState[] administrationStates,
                                                                                     final boolean loadAssociations)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.findAllByNameAndSubscriptionTypeAndAdministrationState(subscriptionNames, subscriptionTypes, administrationStates,
                    loadAssociations);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public List<ResourceSubscription> findAllCriteriaBasedResourceSubscriptions(final boolean loadAssociations)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.findAllCriteriaBasedResourceSubscriptions(loadAssociations);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public String generateUniqueTraceReference(final OutputModeType outputMode)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.generateUniqueTraceReference(outputMode);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public String generateUniqueTraceReference(final String traceReferenceName, final String modelNamespace, final String modelType)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.generateUniqueTraceReference(traceReferenceName, modelNamespace, modelType);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public int countNodesById(final Long subscriptionId)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.countNodesById(subscriptionId);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public List<Long> findAllIdsBySubscriptionTypeAndAdminState(final SubscriptionType[] subscriptionTypes,
                                                                final AdministrationState[] administrationStates)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.findAllIdsBySubscriptionTypeAndAdminState(subscriptionTypes, administrationStates);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public List<Subscription> findAllBySubscriptionModelInfo(final ModelInfo subscriptionModel, final boolean loadAssociations)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.findAllBySubscriptionModelInfo(subscriptionModel, loadAssociations);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    @RetryOnNewTransaction(retryOn = {
            RuntimeDataAccessException.class,
            EJBTransactionRolledbackException.class
    })
    public Subscription findByIdWithRetry(final long subscriptionId, final boolean withNodes) throws DataAccessException, RetryServiceException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.findOneById(subscriptionId, withNodes);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    @RetryOnNewTransaction(retryOn = {EJBTransactionRolledbackException.class, RuntimeDataAccessException.class})
    public int countNodesWithRetry(final Subscription subscription) throws RetryServiceException, DataAccessException {
        if (subscription.hasAssociations()) {
            return ((ResourceSubscription) subscription).getNodes().size();
        }
        if (subscription instanceof ResourceSubscription) {
            return nodeService.countBySubscriptionId(subscription.getId());
        }
        return 0;
    }

    @Override
    @RetryOnNewTransaction(retryOn = {EJBTransactionRolledbackException.class, RuntimeDataAccessException.class})
    public List<Subscription> findAllByNameWithRetry(final String name, final boolean withNodes) throws RetryServiceException, DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.findAllByName(name, withNodes);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    @RetryOnNewTransaction(retryOn = {EJBTransactionRolledbackException.class, RuntimeDataAccessException.class})
    public void getAvailableNodes(final long subId, final SubscriptionType subscriptionType, final Set<String> nodesFdn)
            throws RetryServiceException, DataAccessException {
        final SubscriptionType[] subscriptionTypes = {subscriptionType};
        final AdministrationState[] administrationStates = {AdministrationState.ACTIVE, AdministrationState.ACTIVATING,
                AdministrationState.DEACTIVATING};
        final List<Subscription> fetchedSubscriptions = findAllBySubscriptionTypeAndAdministrationState(subscriptionTypes, administrationStates,
                true);
        final Set<String> fetchedNodes = new HashSet<>();
        for (final Subscription sub : fetchedSubscriptions) {
            if (sub.getId() != subId) {
                fetchedNodes.addAll(((ResourceSubscription) sub).getNodesFdns());
            }
        }
        nodesFdn.removeAll(fetchedNodes);
    }

    /**
     * Checks whether a subscription with given name and model info exists or not.
     *
     * @param subscriptionName  exact subscription name, non-null, non-empty String
     * @param subscriptionModel model info related to the subscription
     * @return - true or false
     * @throws DataAccessException - if data access connection is unobtainable or any exception is thrown from Data Access layer
     */
    @Override
    public boolean existsBySubscriptionNameAndModelInfo(final String subscriptionName, final ModelInfo subscriptionModel)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.existsBySubscriptionNameAndModelInfo(subscriptionName, subscriptionModel);
        } finally {
            creationalContext.release();
        }
    }

    @Override
    public List<Subscription> findAllBySubscriptionNameAndModelInfo(final String subscriptionName,
                                                                    final ModelInfo subscriptionModel,
                                                                    final boolean loadNodes)
            throws DataAccessException {
        final Bean<?> bean = beanManager.getBeans(SubscriptionDao.class).iterator().next();
        final CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        try {
            final SubscriptionDao subscriptionDao = (SubscriptionDao) beanManager.getReference(bean, SubscriptionDao.class, creationalContext);
            return subscriptionDao.findAllBySubscriptionNameAndModelInfo(subscriptionName, subscriptionModel, loadNodes);
        } finally {
            creationalContext.release();
        }
    }
}
