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
package com.ericsson.oss.services.pm.common.systemdefined;

import static com.ericsson.oss.pmic.dto.subscription.ResourceSubscription.getHashCodeFromNodeNamesAndTechnologyDomains;
import static com.ericsson.oss.pmic.subscription.capability.SubscriptionCapabilityReaderImpl.getSystemDefinedAttributesPath;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUPPORTS_MULTIPLE_PREDEFINED_OPERATIONS;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUPPORTS_PREDEFINED_SCANNER;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.DELETE_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.POST_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.UPDATE_SUBSCRIPTION;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dao.versant.mapper.DtoMapper;
import com.ericsson.oss.pmic.dao.versant.mapper.qualifier.SubscriptionMapperQualifier;
import com.ericsson.oss.pmic.dto.Entity;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.ScheduleInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.impl.handler.InvokeInTransaction;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import com.ericsson.oss.pmic.subscription.capability.SubscriptionCapabilityReader;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.common.systemdefined.rule.SystemDefinedAuditRuleSelector;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.exception.ServiceException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.task.TaskStatusValidator;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;
import com.ericsson.oss.services.pm.services.exception.ConcurrentSubscriptionUpdateException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;

/**
 * Will create and update the system defined subscriptions based on the capabilities. Responsible for all CRUD operations on the System Defined
 * Subscription
 */
@Stateless
public class SystemDefinedSubscriptionManager {
    @Inject
    private Logger logger;
    @Inject
    private NodeService nodeService;
    @Inject
    protected ScannerService scannerService;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    @SubscriptionMapperQualifier
    private DtoMapper<Subscription> subscriptionMapper;
    @Inject
    private SubscriptionCapabilityReader systemDefinedCapabilityReader;
    @Inject
    private SystemDefinedAuditRuleSelector systemDefinedAuditRuleSelector;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;
    @EJB
    private SystemDefinedSubscriptionManager self;
    @Inject
    @SubscriptionTaskStatusValidation
    private TaskStatusValidator<Subscription> taskStatusValidator;

    /**
     * Updates System Defined Subscription with now nodes information
     *
     * @param nodes
     *         - list of Nodes
     * @param systemDefinedSubscription
     *         - Resource Subscription object
     * @param rule
     *         - audit rule to apply to the subscription
     */
    public void updateSystemDefinedSubscriptionWithNewNodes(final List<Node> nodes, final ResourceSubscription systemDefinedSubscription,
                                                            final SubscriptionSystemDefinedAuditRule rule) {
        logger.trace("Updating System Defined Subscription with the list of nodes {} ", nodes);
        final int networkElementIdentity = getHashCodeFromNodeNamesAndTechnologyDomains(nodes);
        logger.debug("Node list new networkElementIdentity: {} and old networkElementIdentity: {}", networkElementIdentity,
                systemDefinedSubscription.getNodeListIdentity());
        if (getHashCodeFromNodeNamesAndTechnologyDomains(systemDefinedSubscription.getNodes()) != networkElementIdentity) {
            try {
                rule.applyRuleOnUpdate(nodes, systemDefinedSubscription);
                logger.debug("System Defined Subscription Update: System Defined Subscription updating started for {}", systemDefinedSubscription);
                subscriptionWriteOperationService.manageSaveOrUpdate(systemDefinedSubscription);
                systemRecorder.commandFinishedSuccess(UPDATE_SUBSCRIPTION, systemDefinedSubscription.getIdAsString(),
                        "Successfully updated System Defined Subscription %s", systemDefinedSubscription.getIdAsString());
            } catch (final Exception exception) {
                logger.error("System Defined Subscription: Error updating subscription {}", exception.getMessage());
                logger.info("Error while updating subscription [{}] ", systemDefinedSubscription.getName(), exception);
            }
        }
        self.taskStatusValidate(systemDefinedSubscription);
    }

    /**
     * validates task status and admin state
     *
     * @param subscription
     *         - Subscription to be updated
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void taskStatusValidate(final ResourceSubscription subscription) {
        taskStatusValidator.validateTaskStatusAndAdminState(subscription);
    }

    /**
     * Creates the System Defined Subscription with now nodes information and returns the name of the subscription to be activated
     *
     * @param listOfNodes
     *         - list of nodes to create system defined subscription for
     * @param subscriptionCapabilities
     *         - Subscription attributes extracted from capability model.
     * @param rule
     *         - audit rule to apply to the subscription
     *
     * @return - Subscription or null if subscription with said name already exists or cannot be created in database.
     */
    public Subscription createSystemDefinedSubscriptionAndGetName(final List<Node> listOfNodes,
                                                                  final SystemDefinedPmCapabilities subscriptionCapabilities,
                                                                  final SubscriptionSystemDefinedAuditRule rule) {

        Subscription subscription = null;
        logger.debug("System Defined Subscription Create: Continue creating System Defined Subscription with the list of nodes {} ", listOfNodes);
        try {
            subscription = createSystemDefinedSubscription(listOfNodes, subscriptionCapabilities, rule);

            if (subscription == null) {
                throw new IllegalArgumentException("Cannot create subscription!");
            }

            if (subscriptionReadOperationService.existsByFdn(subscription.getFdn())) {
                logger.error("Subscription with name: {}, already exists.", subscription.getName());
                return null;
            }
            if (subscription instanceof ResourceSubscription && !((ResourceSubscription) subscription).getNodes().isEmpty()) {
                subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription);
                subscriptionWriteOperationService.manageSaveOrUpdate(subscription);
                logger.debug("System Defined Subscription Create: System Defined Subscription created successfully for {}", subscription);
            } else {
                logger.debug("Failed to create System Defined Subscription {}, Found supported node empty.", subscription);
            }
        } catch (final DataAccessException | RuntimeDataAccessException | ServiceException e) {
            logger.error("Cannot create system defined subscription [{}]. {}", subscriptionCapabilities, e.getMessage());
            logger.info("Error while create system defined subscription [{}] ", subscriptionCapabilities.getSubscriptionName(), e);
        }
        return subscription;
    }

    private Subscription createSystemDefinedSubscription(final List<Node> listOfNodes, final SystemDefinedPmCapabilities subscriptionCapabilities,
                                                         final SubscriptionSystemDefinedAuditRule rule) {
        try {
            Subscription systemDefinedSubscription = subscriptionCapabilities.getSubscriptionType().getIdentifier().newInstance();
            systemDefinedSubscription = populateSubscription(subscriptionCapabilities, systemDefinedSubscription);

            if (systemDefinedSubscription instanceof ResourceSubscription || listOfNodes != null) {
                rule.applyRuleOnCreate(listOfNodes, (ResourceSubscription) systemDefinedSubscription);
            }
            systemDefinedSubscription.setScheduleInfo(new ScheduleInfo());
            return systemDefinedSubscription;
        } catch (final IllegalAccessException | InstantiationException | IllegalArgumentException exception) {
            systemRecorder.commandFinishedError(POST_SUBSCRIPTION, subscriptionCapabilities.getSubscriptionName(), exception.getMessage());
            logger.info("Exception stacktrace: ", exception);
        }
        return null;
    }

    private Subscription populateSubscription(final SystemDefinedPmCapabilities subscriptionCapabilities,
                                              final Subscription systemDefinedSubscription) {
        logger.debug("Populating subscription attributes retrieved from the capability");
        final Entity entity = new Entity(systemDefinedSubscription.getModelType(), systemDefinedSubscription.getId(),
                systemDefinedSubscription.getFdn(), subscriptionCapabilities.getPmCapabilities());
        return subscriptionMapper.toDto(entity);
    }

    /**
     * Delete System Defined Subscription if no nodes are present in the subscription
     *
     * @param subscription
     *         - Subscription to be deleted
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void deleteSystemDefinedSubscriptionWithEmptyNodes(final Subscription subscription) {
        logger.info("Deleting subscription with Name: {} and Id: {} because there are no nodes with pm function ON and valid OssModelIdentity "
                + "in the system for this subscription.", subscription.getName(), subscription.getId());
        try {

            subscriptionWriteOperationService.deleteById(subscription.getId());
            systemRecorder.commandFinishedSuccess(DELETE_SUBSCRIPTION, subscription.getIdAsString(),
                    "Successfully deleted System Defined Subscription %s", subscription.getId());
        } catch (final DataAccessException | IllegalArgumentException | RuntimeDataAccessException e) {
            systemRecorder.commandFinishedError(DELETE_SUBSCRIPTION, subscription.getIdAsString(), e.getMessage());
            logger.info("Error while deleting system defined subscription [{}] ", subscription.getName(), e);
        }
    }

    /**
     * Reads the support for predefined scanner from the capabilities of the passed system defined stat subscription.
     *
     * @param subscriptionName
     *         the subscription name
     *
     * @return true or false
     */
    public boolean hasSubscriptionPredefinedScanner(final String subscriptionName) {
        logger.debug("hasSubscriptionPredefinedScanner check for subscription  name '{}'", subscriptionName);
        return getBooleanCapabilityForSubscription(subscriptionName, SUPPORTS_PREDEFINED_SCANNER);
    }

    /**
     * Reads the support for multiple predefined statistical operations from the capabilities of the passed system defined stat subscription.
     *
     * @param subscriptionName
     *         the subscription name
     *
     * @return true or false
     */
    public boolean hasSubscriptionMultiplePredefinedOperations(final String subscriptionName) {
        logger.debug("hasSubscriptionMultiplePredefinedOperations check for subscription  name '{}'", subscriptionName);
        return getBooleanCapabilityForSubscription(subscriptionName, SUPPORTS_MULTIPLE_PREDEFINED_OPERATIONS);
    }

    private boolean getBooleanCapabilityForSubscription(final String subscriptionName, final String capabilityName) {
        try {
            final Map<String, List<SystemDefinedPmCapabilities>> capabilities = systemDefinedCapabilityReader
                    .getSupportedSystemDefinedPmCapabilities();
            for (final List<SystemDefinedPmCapabilities> capabilitiesList : capabilities.values()) {
                for (final SystemDefinedPmCapabilities systemDefinedPmCapabilities : capabilitiesList) {
                    if (systemDefinedPmCapabilities.getSubscriptionName().equals(subscriptionName)) {
                        final Map<String, Object> capabilitiesForSubscription = systemDefinedCapabilityReader.getSystemDefinedSubscriptionAttributes(
                                systemDefinedPmCapabilities.getFirstTargetType(), getSystemDefinedAttributesPath(systemDefinedPmCapabilities));
                        return (Boolean) capabilitiesForSubscription.get(capabilityName);
                    }
                }
            }
        } catch (final Exception e) {
            logger.warn("No capability found for subscription name {}", subscriptionName);
            logger.debug("Exception details {} ", e);
        }
        return false; // default
    }

    /**
     * System Defined Subscription audits such as creating, updating and deleting System defined subscriptions.
     *
     * @param subscriptionCapabilities
     *         - attributes for the system defined subscription
     * @param nodes
     *         - list of nodes found for system defined subscription
     * @param rule
     *         - audit rule to apply to the subscription
     */
    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void createUpdateOrDeleteSystemDefinedSubscription(final SystemDefinedPmCapabilities subscriptionCapabilities, final List<Node> nodes,
                                                              final SubscriptionSystemDefinedAuditRule rule) {
        final String subscriptionName = subscriptionCapabilities.getSubscriptionName();
        logger.debug("Total nodes available in dps for subscription {} are {}", subscriptionName, nodes.size());
        logger.trace("List of nodes available in dps for subscription {} are {}", subscriptionName, nodes);
        try {
            if (subscriptionName == null) {
                logger.error("SystemDefinedPmCapabilityReader cannot find subscription name from {}", subscriptionName);
                return;
            }
            final ResourceSubscription subscription = (ResourceSubscription) subscriptionReadOperationService.findOneByExactName(subscriptionName,
                    true);
            if (subscription == null && !nodes.isEmpty()) {
                final Map<String, Object> subscriptionAttributes = systemDefinedCapabilityReader.getSystemDefinedSubscriptionAttributes(
                        subscriptionCapabilities.getFirstTargetType(), getSystemDefinedAttributesPath(subscriptionCapabilities));
                subscriptionCapabilities.updateWithCommonAttributes(subscriptionAttributes);
                createSubscription(nodes, subscriptionCapabilities, rule);
                return;
            }

            if (subscription != null) {
                if (nodes.isEmpty()
                        && (subscription.getNodes().isEmpty() || subscription.getAdministrationState().isOneOf(AdministrationState.INACTIVE))) {
                    self.deleteSystemDefinedSubscriptionWithEmptyNodes(subscription);
                } else {
                    updateSystemDefinedSubscriptionWithNewNodes(nodes, subscription, rule);
                }
            }
        } catch (final DataAccessException | RuntimeDataAccessException e) {
            logger.error(
                    "System Defined Subscription Create: Find Subscription throws DataAccessException, "
                            + "so continue creating System Defined Subscription with the list of nodes {}. Exception Message: {}",
                    nodes, e.getMessage());
            logger.info("System Defined Subscription Create. Exception stacktrace:", e);
        }
    }

    private void createSubscription(final List<Node> nodes, final SystemDefinedPmCapabilities subscriptionCapabilities,
                                    final SubscriptionSystemDefinedAuditRule rule) {
        logger.debug("Attributes received to create system defined subscription : {} from Capability is {}",
                subscriptionCapabilities.getSubscriptionName(), subscriptionCapabilities);
        final Subscription subscription = createSystemDefinedSubscriptionAndGetName(nodes, subscriptionCapabilities, rule);
        try {
            if (subscriptionCapabilities.isActiveAfterCreation() && subscription != null) {
                logger.info("Activating Subscription {}.", subscription.getName());
                self.activateInNewTx(subscription);
            }
        } catch (final DataAccessException | RuntimeDataAccessException | ServiceException e) {
            logger.error("Error {} occurred while activating System defined subscription {} after create", e.getMessage(), subscription.getName());
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void activateInNewTx(final Subscription subscription) throws DataAccessException, ConcurrentSubscriptionUpdateException, ValidationException {
        subscriptionWriteOperationService.activate(subscription, subscription.getPersistenceTime());
    }
    /**
     * Get Nodes for System Defined Subscriptions.
     *
     * @param systemDefinedCapabilities
     *         - the system defined subscription name
     *
     * @return - list of supported node for System Defined Subscriptions.
     */
    public List<Node> getNodes(final SystemDefinedPmCapabilities systemDefinedCapabilities,
                               final SubscriptionSystemDefinedAuditRule rule) {
        final List<Node> nodes = new ArrayList<>();
        final List<String> targetTypes = systemDefinedCapabilities.getTargetTypes();
        try {
            nodes.addAll(nodeService.findAllByNeTypeAndPmFunction(targetTypes, true));
            rule.removeUnsupportedNodes(nodes, systemDefinedCapabilities);
        } catch (final DataAccessException | RuntimeDataAccessException e) {
            logger.error("DataAccessException thrown while finding nodes by neType [{}].", targetTypes);
            throw new RuntimeDataAccessException("Exception thrown while finding nodes by neType.", e);
        }
        return systemDefinedCapabilities.isCountersEventsValidationApplicable()
                ? filteredNodeWithEmptyOssModelIdentity(nodes, systemDefinedCapabilities.getSubscriptionName())
                : nodes;
    }

    private List<Node> filteredNodeWithEmptyOssModelIdentity(final List<Node> nodes, final String subscriptionName) {
        int filteredNodesCount = 0;
        for (final Iterator<Node> iterator = nodes.iterator(); iterator.hasNext(); ) {
            final Node node = iterator.next();
            if (!Node.isValidOssModelIdentity(node.getOssModelIdentity())) {
                iterator.remove();
                filteredNodesCount++;
            }
        }
        if (filteredNodesCount > 0) {
            logger.info("{} node(s) filtered and not added to subscription [{}] because PmFunction is enabled but with no valid ossModelIdentity",
                    filteredNodesCount, subscriptionName);
        }
        return nodes;
    }
}
