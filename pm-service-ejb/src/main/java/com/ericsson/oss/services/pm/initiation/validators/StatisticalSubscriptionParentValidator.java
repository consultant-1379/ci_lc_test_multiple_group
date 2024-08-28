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

package com.ericsson.oss.services.pm.initiation.validators;

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.STATISTICAL_SUBSCRIPTIONATTRIBUTES;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_SUBSCRITPION_TO_ACTIVATE;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.SOME_NODES_ALREADY_ACTIVE;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.UNABLE_TO_ACTIVATE_STATISTICAL_SUBSCRIPTION_FLEX_ROP_MAX_NODES_EXCEEDED;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.UNABLE_TO_ACTIVATE_SUBSCRIPTION_UNKNOWN_REASON;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import com.ericsson.oss.pmic.api.modelservice.PmCapabilityReader;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription.Subscription220Attribute;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;
import com.ericsson.oss.services.pm.adjuster.impl.SubscriptionMetaDataService;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.modelservice.PmCapabilities;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * This class validates Statistical Subscription, Statistical Type Subscription's could extend this Interface for validation
 *
 * @param <R> - Statistical Subscription type
 */
public class StatisticalSubscriptionParentValidator<R extends StatisticalSubscription> extends ResourceSubscriptionValidator<R> {

    private static final String MULTIPLE_SUBSCRIPTIONS_SUPPORTED = "multipleSubscriptionsSupported";
    private static final String SUBSCRIPTION_TYPE_TO_CAPABILITYMODEL = "STATISTICAL_SubscriptionAttributes";
    private static final List<Object> FLEX_ROP_LIST = new ArrayList<>(SupportedRopTimes.getFlexRopList());
    private static final List<Object> ALL_ACTIVE_ADMINISTRATION_STATE = new ArrayList<>();

    @Inject
    private SubscriptionMetaDataService subscriptionPfmData;

    @Inject
    private SubscriptionReadOperationService subscriptionreadOperationService;

    @Inject
    private PmCapabilityModelService capabilityModelService;

    @Inject
    private PmCapabilityReader pmCapabilityReader;

    @Inject
    private SupportedRopTimes supportedRopTimes;

    static {
        ALL_ACTIVE_ADMINISTRATION_STATE.add(AdministrationState.ACTIVE.name());
        ALL_ACTIVE_ADMINISTRATION_STATE.add(AdministrationState.ACTIVATING.name());
    }

    @Override
    public void validate(final R subscription) throws ValidationException {
        super.validate(subscription);
        if (subscription.getIsImported()) {
            final boolean supportExternalCounterNames =
                    pmCapabilityReader.shouldSupportExternalCounterName(STATISTICAL_SUBSCRIPTIONATTRIBUTES, subscription.getNodesTypeVersion());
            final List<CounterInfo> correctCounters = subscriptionPfmData.getCorrectCounters(subscription.getName(), subscription.getCounters(),
                    subscription.getNodesTypeVersion(), pmCapabilityReader.getSupportedModelDefinersForCounters(STATISTICAL_SUBSCRIPTIONATTRIBUTES), Collections.emptyList(), supportExternalCounterNames);
            if (correctCounters.size() != subscription.getCounters().size()) {
                SubscriptionCommonValidator.detectsIncompatibleCounters(subscription.getCounters(), correctCounters, subscription.getNodes());
            }
        }
        if (AdministrationState.INACTIVE != subscription.getAdministrationState()) {
            validateActivationForSelectedRop(subscription);
        }
    }

    @Override
    public void validateActivation(final R subscription)
            throws ValidationException {
        super.validateActivation(subscription);
        isMultipleSubscriptionsAllowed(subscription);
        if (validationRequired(subscription) && subscription.getCounters().isEmpty()) {
            throw new ValidationException(String.format(INVALID_SUBSCRITPION_TO_ACTIVATE, subscription.getName()));
        }
        validateActivationForSelectedRop(subscription);
    }

    private boolean validationRequired(final R subscription) {
        return UserType.SYSTEM_DEF == subscription.getUserType()
                ? systemDefinedPmCapabilityReader.isEventCounterVerificationNeeded(subscription.getName())
                : subscriptionCommonValidator.isEventCounterVerificationNeeded(subscription);
    }

    /**
     * Find all subscriptions with given name pattern.
     *
     * @param subscription - subscription names array.
     * @throws ValidationException - if any exception is thrown from Database
     */
    private void isMultipleSubscriptionsAllowed(final ResourceSubscription subscription) throws ValidationException {
        final AdministrationState[] administrationStates = {AdministrationState.ACTIVATING, AdministrationState.ACTIVE,
                AdministrationState.UPDATING};
        final List<Subscription> subscriptions = getAllStatisticalSubascriptions(administrationStates);
        final List<Node> allNodes = new ArrayList<>();
        for (final Subscription subscriptionOther : subscriptions) {
            allNodes.addAll(((ResourceSubscription) subscriptionOther).getNodes());
        }
        final List<Node> nodesOfSubscription = subscription.getNodes();
        nodesOfSubscription.retainAll(allNodes);
        for (final Node nodeObj : nodesOfSubscription) {
            final PmCapabilities pmCapabilities = capabilityModelService.getCapabilityForTargetTypeByFunction(SUBSCRIPTION_TYPE_TO_CAPABILITYMODEL,
                    nodeObj.getNeType(), MULTIPLE_SUBSCRIPTIONS_SUPPORTED);
            if (pmCapabilities == null || pmCapabilities.getTargetTypes().isEmpty()
                    || pmCapabilities.getTargetTypes().get(0).getCapabilities().isEmpty()) {
                continue;
            }
            final boolean supportedMultipleSubscription = (boolean) pmCapabilities.getTargetTypes().get(0).getCapabilities()
                    .get(MULTIPLE_SUBSCRIPTIONS_SUPPORTED);
            if (!supportedMultipleSubscription) {
                final Set<String> activeSubscriptionHasNodeFdn = validateActiveSubscriptionHasNodeFdn(subscriptions, nodeObj.getFdn(),
                        nodeObj.getNeType());
                if (!activeSubscriptionHasNodeFdn.isEmpty()) {
                    throw new ValidationException(String.format(SOME_NODES_ALREADY_ACTIVE, activeSubscriptionHasNodeFdn));
                }
            }
        }
    }

    /**
     * Check if is possible to update/activate a subscription according to the number of nodes involved in STATISTICAL subscription.
     *
     * @param subscription - subscription involved in update/activation.
     * @throws ValidationException - if any exception is thrown from Database
     */
    private void validateActivationForSelectedRop(final Subscription subscription) throws ValidationException {
        if (SupportedRopTimes.isFlexRop(subscription.getRop())) {
            int counter = ((StatisticalSubscription) subscription).getNumberOfNodes();
            try {
                final SubscriptionType subscriptionType = subscription.getType();
                final Map<String, List<Object>> attributes = new HashMap<>();
                attributes.put(Subscription220Attribute.rop.name(), FLEX_ROP_LIST);
                attributes.put(Subscription220Attribute.administrationState.name(), ALL_ACTIVE_ADMINISTRATION_STATE);
                final List<Subscription> allSubscriptions =
                        subscriptionreadOperationService.findAllWithSubscriptionTypeAndMatchingAttributes(subscriptionType, attributes, true);
                for (final Subscription dpsSubscription : allSubscriptions) {
                    counter += subscription.getId().equals(dpsSubscription.getId()) ? 0 : ((StatisticalSubscription) dpsSubscription)
                            .getNumberOfNodes();
                }
            } catch (final DataAccessException e) {
                throw new ValidationException(String.format(UNABLE_TO_ACTIVATE_SUBSCRIPTION_UNKNOWN_REASON, subscription.getName()));
            }
            final int maxNumberOfNodesForFlexRop = supportedRopTimes.getMaxNumberOfNodesForFlexRop();
            if (counter > maxNumberOfNodesForFlexRop) {
                throw new ValidationException(
                        String.format(UNABLE_TO_ACTIVATE_STATISTICAL_SUBSCRIPTION_FLEX_ROP_MAX_NODES_EXCEEDED, maxNumberOfNodesForFlexRop, counter));
            }
        }
    }

    private List<Subscription> getAllStatisticalSubascriptions(final AdministrationState[] administrationStates) throws ValidationException {
        final SubscriptionType[] subscriptionTypes = {SubscriptionType.STATISTICAL};
        final List<Subscription> subscriptions = new ArrayList<>();
        try {
            subscriptions.addAll(
                    subscriptionreadOperationService.findAllBySubscriptionTypeAndAdministrationState(subscriptionTypes, administrationStates, true));
        } catch (final DataAccessException e) {
            throw new ValidationException("Can not list active subscriptions");
        }
        return subscriptions;
    }

    private static Set<String> validateActiveSubscriptionHasNodeFdn(final List<Subscription> subscriptions, final String nodeFdn, final String neType) {
        final Set<String> activeSubscriptionHasNodeFdn = new HashSet<>();
        for (final Subscription subscription : subscriptions) {
            final List<String> nodeTypes = ((ResourceSubscription) subscription).getSelectedNeTypes();
            if (nodeTypes.contains(neType)) {
                final Set<String> nodesFdns = ((ResourceSubscription) subscription).getNodesFdns();
                if (nodesFdns.contains(nodeFdn)) {
                    activeSubscriptionHasNodeFdn.add(subscription.getName());
                }
            }
        }
        return activeSubscriptionHasNodeFdn;
    }
}
