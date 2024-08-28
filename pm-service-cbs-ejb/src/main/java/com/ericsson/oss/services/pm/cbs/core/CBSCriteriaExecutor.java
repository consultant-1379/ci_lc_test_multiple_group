/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.cbs.core;

import static com.ericsson.oss.pmic.dto.subscription.ResourceSubscription.getHashCodeFromNodeNamesAndTechnologyDomains;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.COUNTER_EVENTS_VALIDATION_APPLICABLE;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUBSCRIPTION_ATTRIBUTES_SUFFIX;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.NETWORK_EXPLORER_RESPONSE_FAILED;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.SUBSCRIPTION_NOT_FOUND;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.CBS_AUDIT_EXECUTION;
import static com.ericsson.oss.services.pm.initiation.common.Constants.CBS_AUDIT_FIND_SUB_MO_FAILURE;
import static com.ericsson.oss.services.pm.initiation.common.Constants.CBS_AUDIT_SUCCESS;
import static com.ericsson.oss.services.pm.initiation.common.Constants.NETWORK_EXPLORER_NOT_AVAILABLE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.api.modelservice.PmCapabilitiesLookupLocal;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.impl.handler.InvokeInTransaction;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import com.ericsson.oss.pmic.util.CollectionUtil;
import com.ericsson.oss.services.cm.cmshared.dto.CmObject;
import com.ericsson.oss.services.pm.cbs.events.CBSExecutionPlanEvent200;
import com.ericsson.oss.services.pm.cbs.events.CBSResourceSubscription;
import com.ericsson.oss.services.pm.cbs.exceptions.CBSException;
import com.ericsson.oss.services.pm.common.logging.PMICLog.Operation;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.dps.utility.UpdateResourceSubscription;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.oss.services.topologySearchService.service.api.dto.NetworkExplorerResponse;

/**
 * CBSCriteriaExecutor executes CBS criteria query and triggers for NodeList update in ResourceSubscription.
 */
@Stateless
public class CBSCriteriaExecutor {

    private static final String CBS_AUDIT_COMPLETED_AND_UPDATED_SUBSCRIPTION = "CBS Audit completed and updated subscription ";

    @Inject
    private TopologySearchExecutor topologySearchExecutor;

    @Inject
    private UpdateResourceSubscription updateResourceSubscription;

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    @Inject
    private Logger logger;

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    @Inject
    private NodeService nodeService;

    @Inject
    private PmCapabilitiesLookupLocal pmCapabilitiesLookupLocal;

    @EJB
    private CBSCriteriaExecutor self;

    /**
     * Executes the criteria from cbsExecutionPlanEvent on topology search service and get the node list. Initiates a check to update the
     * ResourceSubscriptionList by passing generated networkElementListIdentity from {@link ResourceSubscription#getHashCodeFromNodeNames(List)}
     *
     * @param cbsExecutionPlanEvent200
     *            event with CBS criteria
     */
    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void executeCriteriaAndUpdateNodeList(final CBSExecutionPlanEvent200 cbsExecutionPlanEvent200) {
        final String searchQuery = cbsExecutionPlanEvent200.getCbsQuery();
        logger.debug("search query is {} :", searchQuery);
        try {
            final NetworkExplorerResponse queryResult = topologySearchExecutor.executeQuery(searchQuery, null);
            if (null != queryResult) {
                if (queryResult.getCmObjects() == null) {
                    systemRecorder.eventCoarse(CBS_AUDIT_EXECUTION, searchQuery,
                        "Cannot find all Nodes by cm objects for search query " + searchQuery + " because cmObjects is null, empty or contains null " +
                            "elements!");
                    return;
                }
                cbsExecutionPlanEvent200.getResourceSubscriptionList().forEach(cbsResourceSubscription -> {
                    try {
                        executeNodeUpdate(queryResult.getCmObjects(), cbsResourceSubscription);
                    } catch (final DataAccessException | RuntimeDataAccessException | RetryServiceException | InvalidSubscriptionException exception) {
                        systemRecorder.error(SUBSCRIPTION_NOT_FOUND, searchQuery, CBS_AUDIT_FIND_SUB_MO_FAILURE, Operation.EDIT);
                        logger.info("Exception caught while executing CBS audit. ", exception);
                    }
                });
                systemRecorder.eventCoarse(CBS_AUDIT_EXECUTION, searchQuery, CBS_AUDIT_SUCCESS + searchQuery);
            }
        } catch (final CBSException cbsException) {
            systemRecorder.error(NETWORK_EXPLORER_RESPONSE_FAILED, searchQuery, NETWORK_EXPLORER_NOT_AVAILABLE, Operation.CBS_AUDIT);
            logger.info("Exception caught while executing CBS audit.", cbsException);
        }
    }

    private void executeNodeUpdate(final Collection<CmObject> cmObjectsFromSearchCriteria, final CBSResourceSubscription cbsResourceSubscription)
            throws DataAccessException, RetryServiceException, InvalidSubscriptionException {
        // When subscriptionPoId is valid
        final ResourceSubscription resourceSubscription = (ResourceSubscription) subscriptionReadOperationService
                .findOneById(Long.valueOf(cbsResourceSubscription.getPoid()), true);
        if (resourceSubscription == null) {
            logger.warn("CBS Audit: Failed to retrieve subscription object {} from  SubscriptionWrapper cache", cbsResourceSubscription.getPoid());
            return;
        }
        final List<Node> nodesFromSearchCriteria = CollectionUtil.isEmpty(cmObjectsFromSearchCriteria) ? Collections.<Node> emptyList()
            : getPmEnabledNodesFromSearchCriteria(cmObjectsFromSearchCriteria, resourceSubscription);
        if (searchCriteriaResultDiffersFromNodesInSubscription(nodesFromSearchCriteria, resourceSubscription.getNodes())) {
            logger.info("CBS Audit for subscription {}: NodeListIdentity of Search result is different, new generated networkElementIdentity is {}",
                    resourceSubscription.getName(), cbsResourceSubscription.getNodeListIdentity());
            self.updateCbsSubscriptionInNewTx(resourceSubscription, nodesFromSearchCriteria);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateCbsSubscriptionInNewTx(final ResourceSubscription resourceSubscription, final List<Node> nodesFromSearchCriteria) throws DataAccessException, RetryServiceException, InvalidSubscriptionException {
        updateResourceSubscription.updateResourceSubscription(resourceSubscription, nodesFromSearchCriteria);
    }

    private List<Node> getPmEnabledNodesFromSearchCriteria(final Collection<CmObject> cmObjectsFromSearchCriteria,
                                                           final ResourceSubscription resourceSubscription) {

        final Set<String> subscriptionNodeFdns = resourceSubscription.getNodesFdns();
        logger.debug("Current subscription nodes : {}, subscriptionName : {}", subscriptionNodeFdns, resourceSubscription.getName());
        final Set<Node> pmEnabledNodesFromSearchCriteria = new HashSet<>();
        final Set<String> pmEnabledFdnsFromSearchCriteria = new HashSet<>();
        final Set<String> invalidFdnsFromSearchCriteria = new HashSet<>();
        final Map<String, Boolean> nodeTypeToValidationCapability = new HashMap<>();

        final List<Node> nodesFromSearchCriteria = nodeService.findAllByCmObject(cmObjectsFromSearchCriteria);
        nodesFromSearchCriteria.stream().forEach(node -> {
            if (!nodeTypeToValidationCapability.containsKey(node.getNeType())) {
                final Boolean countersEventsValidationApplicable = (Boolean) pmCapabilitiesLookupLocal.getCapabilityValue(node.getNeType(),
                    resourceSubscription.getType().name() + SUBSCRIPTION_ATTRIBUTES_SUFFIX, COUNTER_EVENTS_VALIDATION_APPLICABLE);
                nodeTypeToValidationCapability.put(node.getNeType(), countersEventsValidationApplicable);
            }
            if (nodeService.isPmFunctionEnabled(node.getFdn())
                    && checkValidOssModelIdentity(nodeTypeToValidationCapability.get(node.getNeType()), node.getOssModelIdentity())) {
                pmEnabledNodesFromSearchCriteria.add(node);
                pmEnabledFdnsFromSearchCriteria.add(node.getFdn());
            } else {
                invalidFdnsFromSearchCriteria.add(node.getFdn());
            }
        });
        if (!invalidFdnsFromSearchCriteria.isEmpty()) {
            systemRecorder.eventCoarse(CBS_AUDIT_EXECUTION, resourceSubscription.getName(), "CBS Audit found nodes " + invalidFdnsFromSearchCriteria
                    + " in subscription " + resourceSubscription.getName() + " but not included in subscription due to pmFunction not enabled or "
                    + "invalid oss model identity.");
        }
        logger.debug("pmEnabledFdnsFromSearchCriteria : {}", pmEnabledFdnsFromSearchCriteria);
        final Set<String> removedFdns = subscriptionNodeFdns.stream().filter(fdn -> !pmEnabledFdnsFromSearchCriteria.contains(fdn))
                .collect(Collectors.toSet());
        final Set<String> addedFdns = pmEnabledFdnsFromSearchCriteria.stream().filter(fdn -> !subscriptionNodeFdns.contains(fdn))
                .collect(Collectors.toSet());
        logCBSAuditResult(removedFdns, resourceSubscription.getName(), addedFdns);
        return new ArrayList<>(pmEnabledNodesFromSearchCriteria);
    }

    private boolean checkValidOssModelIdentity(final boolean countersEventsValidationApplicable, final String ossModelIdentity) {
        return !countersEventsValidationApplicable || Node.isValidOssModelIdentity(ossModelIdentity);
    }

    private void logCBSAuditResult(final Set<String> removedFdns, final String subscriptionName, final Set<String> addedFdns) {
        if (!addedFdns.isEmpty() && !removedFdns.isEmpty()) {
            systemRecorder.eventCoarse(CBS_AUDIT_EXECUTION, subscriptionName, CBS_AUDIT_COMPLETED_AND_UPDATED_SUBSCRIPTION
                    + subscriptionName + " successfully with new Network Elements :" + addedFdns + " and removed Network Elements :" + removedFdns);
        } else if (!addedFdns.isEmpty()) {
            systemRecorder.eventCoarse(CBS_AUDIT_EXECUTION, subscriptionName,
                    CBS_AUDIT_COMPLETED_AND_UPDATED_SUBSCRIPTION + subscriptionName + " successfully with new Network Elements :" + addedFdns);
        } else if (!removedFdns.isEmpty()) {
            systemRecorder.eventCoarse(CBS_AUDIT_EXECUTION, subscriptionName,
                    CBS_AUDIT_COMPLETED_AND_UPDATED_SUBSCRIPTION + subscriptionName + " successfully with removed Network Elements :" + removedFdns);
        } else {
            systemRecorder.eventCoarse(CBS_AUDIT_EXECUTION, subscriptionName,
                    CBS_AUDIT_COMPLETED_AND_UPDATED_SUBSCRIPTION + subscriptionName + " successfully with no change in nodes");
        }
    }

    private boolean searchCriteriaResultDiffersFromNodesInSubscription(final List<Node> nodesFromSearchCriteria,
                                                                       final List<Node> nodesCurrentlyInSubscription) {
        return getHashCodeFromNodeNamesAndTechnologyDomains(nodesFromSearchCriteria) !=
                getHashCodeFromNodeNamesAndTechnologyDomains(nodesCurrentlyInSubscription);
    }
}
