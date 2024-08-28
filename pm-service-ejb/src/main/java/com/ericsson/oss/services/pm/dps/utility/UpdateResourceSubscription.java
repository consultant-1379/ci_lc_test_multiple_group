/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.dps.utility;

import static com.ericsson.oss.pmic.dto.subscription.ResourceSubscription.getHashCodeFromNodeNamesAndTechnologyDomains;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUPPORTED_SUBSCRIPTION_TYPES;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUPPORTED_TECHNOLOGY_DOMAINS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.api.selector.annotation.Selector;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionManager;
import com.ericsson.oss.services.pm.initiation.task.factories.auditor.CellTraceSubscriptionHelper;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;

/**
 * Updates ResourceSubscription in DPS with the nodeList.
 */
public class UpdateResourceSubscription {

    @Inject
    private Logger logger;
    @Inject
    private PmCapabilityModelService capabilityAccessor;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private SubscriptionManager subscriptionManager;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;
    @Inject
    @Selector(filter = "CellTraceSubscriptionHelper")
    private CellTraceSubscriptionHelper cellTraceSubscriptionHelper;

    /**
     * Update ResourceSubscription with nodeList in Dps.
     *
     * @param resourceSubscription    - ResourceSubscription to be updated
     * @param nodesFromSearchCriteria - list of nodes to be updated in ResourceSubscription
     * @throws DataAccessException          - thrown if subscription not found in database
     * @throws RetryServiceException        - if an invalid input exception is generated
     * @throws InvalidSubscriptionException - if an exception is thrown while trying to resolve the exception.
     */
    public void updateResourceSubscription(final ResourceSubscription resourceSubscription, final List<Node> nodesFromSearchCriteria)
            throws DataAccessException, RetryServiceException, InvalidSubscriptionException {
        logger.debug("Update resourceSubscription [{}] nodes [{}] ", resourceSubscription.getName(), nodesFromSearchCriteria);
        if (SubscriptionType.CELLTRACE == resourceSubscription.getType()) {
            final Map<String, Object> globalCapabilities = capabilityAccessor.getSubscriptionAttributesGlobalCapabilities(resourceSubscription, SUPPORTED_TECHNOLOGY_DOMAINS);

            final List<Node> applicableNodesForSubscription = new ArrayList<>();
            for (final Node node : nodesFromSearchCriteria) {
                addApplicableNodesForSubscription(resourceSubscription, applicableNodesForSubscription, node,
                        (List<String>) globalCapabilities.get(SUPPORTED_TECHNOLOGY_DOMAINS));
            }
            logger.info("'Updating Criteria based Celltrace subscription {} had {} nodes and now contains {} nodes'",
                    resourceSubscription.getName(), resourceSubscription.getNodes().size(), applicableNodesForSubscription.size());
            saveSubscription(resourceSubscription, applicableNodesForSubscription);
        } else {
            logger.info("Updating criteria based subscription {} with all {} nodes from search criteria", resourceSubscription.getName(),
                    nodesFromSearchCriteria.size());
            saveSubscription(resourceSubscription, nodesFromSearchCriteria);
        }
    }

    private void saveSubscription(final ResourceSubscription resourceSubscription, final List<Node> nodesApplicableForSubscription)
            throws DataAccessException, RetryServiceException, InvalidSubscriptionException {
        if (applicableNodesDiffersFromNodesInSubscription(nodesApplicableForSubscription, resourceSubscription.getNodes())) {
            resourceSubscription.setNodes(nodesApplicableForSubscription);
            subscriptionManager.removeSubscriptionFromCache(resourceSubscription.getName(), resourceSubscription.getType());
            logger.debug("updateResourceSubscription nodeListIdentity after updating [{}] ", resourceSubscription.getNodeListIdentity());
            subscriptionWriteOperationService.manageSaveOrUpdate(resourceSubscription);
            subscriptionManager.getSubscriptionWrapper(resourceSubscription.getName(), resourceSubscription.getType());
        } else {
            logger.debug("Applicable Nodes {} already in the criteria based subscription {}", nodesApplicableForSubscription,
                    resourceSubscription.getName());
        }
    }

    private static boolean applicableNodesDiffersFromNodesInSubscription(final List<Node> applicableNodes, final List<Node> nodesCurrentlyInSubscription) {
        return getHashCodeFromNodeNamesAndTechnologyDomains(applicableNodes) !=
                getHashCodeFromNodeNamesAndTechnologyDomains(nodesCurrentlyInSubscription);
    }

    private void addApplicableNodesForSubscription(final ResourceSubscription resourceSubscription, final List<Node> applicableNodesForSubscription,
                                                   final Node node, final List<String> subscriptionSupportedTechnologyDomains) {
        if (isCelltraceSupportedByNode(node) && technologyDomainIsSupported(node, subscriptionSupportedTechnologyDomains)) {
            applicableNodesForSubscription.add(node);

            if (resourceSubscription.getNodes().contains(node)) {
                logger.debug("Node {} already in the criteria based subscription {}", node, resourceSubscription.getName());
            } else {
                logger.debug("Adding node {} to the criteria based subscription {}", node, resourceSubscription.getName());
            }
        } else {
            logger.info("Found node matching saved criteria but, Not adding node {} to the criteria based subscription {} as is not compatible with "
                    + "celltrace or has the wrong technology domain", node, resourceSubscription.getName());
        }
    }

    private boolean isCelltraceSupportedByNode(final Node node) {
        final List<String> nodeSupportedSubscriptionTypes = capabilityAccessor
                .getSupportedCapabilityValues(node.getNeType(), SUPPORTED_SUBSCRIPTION_TYPES);
        return nodeSupportedSubscriptionTypes.contains(SubscriptionType.CELLTRACE.name());
    }

    private static boolean technologyDomainIsSupported(final Node node, final List<String> technologyDomainSupportedBySubscription) {
        for (final String subscriptionTechnologyDomain : technologyDomainSupportedBySubscription) {
            if (node.getTechnologyDomain().contains(subscriptionTechnologyDomain)) {
                return true;
            }
        }
        return false;
    }
}
