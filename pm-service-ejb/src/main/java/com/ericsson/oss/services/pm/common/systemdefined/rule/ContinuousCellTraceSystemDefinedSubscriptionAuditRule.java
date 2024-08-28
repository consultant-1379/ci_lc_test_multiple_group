/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.common.systemdefined.rule;

import static com.ericsson.oss.pmic.api.constants.ModelConstants.SubscriptionConstants.CONTINUOUS_CELLTRACE_SUBSCRIPTION_NRAN_NAME;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.CONTINUOUS_CELLTRACE_LRAN_SUBSCRIPTION_ATTRIBUTES;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.CONTINUOUS_CELLTRACE_NRAN_SUBSCRIPTION_ATTRIBUTES;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUPPORTED_TECHNOLOGY_DOMAINS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ContinuousCellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.common.constants.PmFeature;
import com.ericsson.oss.services.pm.common.systemdefined.SystemDefinedPmCapabilities;
import com.ericsson.oss.services.pm.initiation.model.metadata.events.PmEventsLookUp;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.oss.services.pm.modelservice.PmGlobalCapabilities;

/**
 * This class applies audit rules CCTR Subscription
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.CONTINUOUSCELLTRACE)
public class ContinuousCellTraceSystemDefinedSubscriptionAuditRule
    extends ResourceSubscriptionSystemDefinedAuditRule<ContinuousCellTraceSubscription> {

    @Inject
    private PmCapabilityModelService capabilityAccessor;
    @Inject
    private PmEventsLookUp pmEventsLookUp;

    @Override
    public void applyRuleOnUpdate(final List<Node> nodes, final ContinuousCellTraceSubscription subscription) {
        super.applyRuleOnUpdate(nodes, subscription);
        addEventsToCCTRSubscription(nodes, subscription);
    }

    @Override
    public void applyRuleOnCreate(final List<Node> nodes, final ContinuousCellTraceSubscription subscription) {
        super.applyRuleOnCreate(nodes, subscription);
        addEventsToCCTRSubscription(nodes, subscription);
    }

    private void addEventsToCCTRSubscription(final List<Node> nodes, final ContinuousCellTraceSubscription subscription) {
        logger.debug("ContinuousCellTraceSubscription subscription name {}, adding events", subscription);
        final Map<String, Set<String>> technologyDomainsPerNodeType = getTechnologyDomainsPerNodeType(nodes);
        removeUnsupportedTechnologyDomainsForSubscriptionName(technologyDomainsPerNodeType, subscription.getName());
        subscription.setEvents(getListOfSupportedEvents(nodes, technologyDomainsPerNodeType));
    }

    @Override
    public void removeUnsupportedNodes(final List<Node> nodes, final SystemDefinedPmCapabilities systemDefinedPmCapabilities) {
        final List<String> mixedModeSupportedNodeTypes = capabilityAccessor.
            getSupportedNodeTypesForPmFeatureCapability(PmFeature.SUPPORTED_MIXED_MODE_TECHNOLOGY);
        if (mixedModeSupportedNodeTypes.isEmpty()) {
            logger.debug("Create Continuous CellTrace: There are no supported nodes with capability: {}",
                PmFeature.SUPPORTED_MIXED_MODE_TECHNOLOGY);
        }
        removeInvalidAndUnsupportedMixedModeNodes(nodes, mixedModeSupportedNodeTypes, systemDefinedPmCapabilities);
    }

    private void removeInvalidAndUnsupportedMixedModeNodes(final List<Node> nodes, final List<String> mixedModeNodeTypes,
                                                           final SystemDefinedPmCapabilities systemDefinedPmCapabilities) {
        nodes.removeIf(node -> !Node.isValidOssModelIdentity(node.getOssModelIdentity())
            || (mixedModeNodeTypes.contains(node.getNeType())
            && !isNodeTechnologyDomainSupportedBySubscription(node, systemDefinedPmCapabilities)));
    }

    private boolean isNodeTechnologyDomainSupportedBySubscription(final Node node, final SystemDefinedPmCapabilities systemDefinedPmCapabilities) {
        final List<String> subscriptionTechnologyDomains = getSystemDefinedSubscriptionTechnologyDomains(
            systemDefinedPmCapabilities.getSubscriptionName());
        final List<String> nodeTechnologyDomains = node.getTechnologyDomain();
        return nodeTechnologyDomains.stream().anyMatch(subscriptionTechnologyDomains::contains);
    }

    private List<String> getSystemDefinedSubscriptionTechnologyDomains(final String subscriptionName) {
        final String subscriptionCapabilityName = isCellTraceNran(subscriptionName) ?
            CONTINUOUS_CELLTRACE_NRAN_SUBSCRIPTION_ATTRIBUTES :
            CONTINUOUS_CELLTRACE_LRAN_SUBSCRIPTION_ATTRIBUTES;
        final PmGlobalCapabilities pmGlobalCapabilities = capabilityAccessor.getGlobalCapabilitiesByFunction(subscriptionCapabilityName,
            SUPPORTED_TECHNOLOGY_DOMAINS);
        return (List<String>) pmGlobalCapabilities.getGlobalCapabilities().get(SUPPORTED_TECHNOLOGY_DOMAINS);
    }

    private boolean isCellTraceNran(final String subscriptionName) {
        return CONTINUOUS_CELLTRACE_SUBSCRIPTION_NRAN_NAME.equals(subscriptionName);
    }

    private Map<String, Set<String>> getTechnologyDomainsPerNodeType(final List<Node> nodes) {
        final Map<String, Set<String>> technologyDomainsPerNodeType = new HashMap<>();
        nodes.forEach(node -> {
            if (!technologyDomainsPerNodeType.containsKey(node.getNeType())) {
                technologyDomainsPerNodeType.put(node.getNeType(), new HashSet<>());
            }
            technologyDomainsPerNodeType.get(node.getNeType()).addAll(node.getTechnologyDomain());
        });
        return technologyDomainsPerNodeType;
    }

    private void removeUnsupportedTechnologyDomainsForSubscriptionName(final Map<String, Set<String>> technologyDomainsPerNodeType,
                                                                       final String subscriptionName) {
        final List<String> subscriptionTechnologyDomains = getSystemDefinedSubscriptionTechnologyDomains(subscriptionName);
        for (final Map.Entry<String, Set<String>> technologyDomainsForNeType : technologyDomainsPerNodeType.entrySet()) {
            technologyDomainsForNeType.getValue().retainAll(subscriptionTechnologyDomains);
        }
    }

    private List<EventInfo> getListOfSupportedEvents(final List<Node> listOfEnbNodes,
                                                     final Map<String, Set<String>> technologyDomainsPerNodeType) {
        logger.debug("Get Supported events for {}", listOfEnbNodes);
        return pmEventsLookUp.getListOfSupportedEventsForTechnologyDomain(getMapOfModelIdentityFromNodeList(listOfEnbNodes),
            technologyDomainsPerNodeType);
    }

    private Map<String, Set<String>> getMapOfModelIdentityFromNodeList(final List<Node> nodeList) {
        logger.debug("Get Model Identity for {}", nodeList);
        final Map<String, Set<String>> mapOfModelIdentity = new HashMap<>();
        nodeList.forEach(node -> {
            if (mapOfModelIdentity.containsKey(node.getNeType())) {
                mapOfModelIdentity.get(node.getNeType()).add(node.getOssModelIdentity());
            } else {
                final Set<String> setOfModelIdentity = new HashSet<>();
                setOfModelIdentity.add(node.getOssModelIdentity());
                mapOfModelIdentity.put(node.getNeType(), setOfModelIdentity);
            }
        });
        logger.debug("Model Identity {} for node {}", mapOfModelIdentity, nodeList);
        return mapOfModelIdentity;
    }

}
