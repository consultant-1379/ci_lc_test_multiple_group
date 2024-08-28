/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.validators;

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_SUBSCRIPTION_MOINSTANCE_TO_ACTIVATE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.MoinstanceInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.initiation.model.metadata.moinstances.PmMoinstancesLookUp;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This class validates Moinstance Subscription
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.MOINSTANCE)
public class MoinstanceSubscriptionValidator extends StatisticalSubscriptionParentValidator<MoinstanceSubscription> {
    @Inject
    private Logger logger;

    @Inject
    private PmMoinstancesLookUp pmMoinstancesLookUp;

    @Inject
    private MoLimitValidator moLimitValidator;

    @Override
    public void validateImport(final MoinstanceSubscription subscription) throws ValidationException, PfmDataException {
        super.validateImport(subscription);
        final List<MoinstanceInfo> subscriptionMoInstances = subscription.getMoInstances();

        // Nothing to validate if moInstanceIfoList is null or empty
        if (subscriptionMoInstances == null || subscriptionMoInstances.isEmpty()) {
            return;
        }
        // MoInstances cannot exist without both nodes and counters
        if (subscription.getNodes().isEmpty() || subscription.getCounters().isEmpty()) {
            logger.error("No nodes or counters found. MoInstances cannot exist without them");
            throw new ValidationException("Mo Instances needs nodes and counters");
        }

        validateMoinstance(subscription, subscriptionMoInstances);
    }

    private void validateMoinstance(final MoinstanceSubscription subscription, final List<MoinstanceInfo> subscriptionMoInstances)
            throws ValidationException {
        final Map<String, String> nodeNamesToTypeMap = new HashMap<>();
        for (final Node node : subscription.getNodes()) {
            nodeNamesToTypeMap.put(node.getName(), node.getNeType());
        }
        final List<String> moClasses = new ArrayList<>();
        for (final CounterInfo counter : subscription.getCounters()) {
            moClasses.add(counter.getMoClassType());
        }
        final List<MoinstanceInfo> validMoInstances = getValidMoInstances(subscription.getNodesTypeVersion(), nodeNamesToTypeMap, moClasses,
                subscription.getType());

        final List<MoinstanceInfo> invalidMoInstances = new ArrayList<>();
        for (final MoinstanceInfo moInstance : subscriptionMoInstances) {
            if (moInstance != null && !validMoInstances.contains(moInstance)) {
                invalidMoInstances.add(moInstance);
            }
        }
        if (!invalidMoInstances.isEmpty()) {
            logger.error("Invalid MoInstances found ");
            throw new ValidationException("Following Mo Instances are invalid: " + invalidMoInstances.toString());
        }
    }

    private List<MoinstanceInfo> getValidMoInstances(final Set<NodeTypeAndVersion> nodeTypeVersionSet, final Map<String, String> nodeNamesToTypeMap,
                                                     final List<String> moClasses, final SubscriptionType subType)
            throws ValidationException {
        try {
            final List<MoinstanceInfo> validMoinstanceInfos = pmMoinstancesLookUp.getMoinstanceInfos(nodeTypeVersionSet, subType, nodeNamesToTypeMap,
                    moClasses);
            if (validMoinstanceInfos.isEmpty()) {
                logger.error("No valid Mo Instances found for Ne type/version and Counters provided");
                throw new ValidationException("No valid Mo Instances found for Ne type/version and Counters provided");
            }
            return validMoinstanceInfos;
        } catch (final DataAccessException | RuntimeDataAccessException e) {
            throw new ValidationException(e.getMessage(), e);
        }
    }

    @Override
    public void validateActivation(final MoinstanceSubscription subscription) throws ValidationException {
        super.validateActivation(subscription);
        ensureSubscriptionHasCorrectMoinstance(subscription);
    }

    private void ensureSubscriptionHasCorrectMoinstance(final MoinstanceSubscription subscription) throws ValidationException {
        if (subscription.getMoInstances().isEmpty()) {
            logger.error("The moinstance of the subscription {} is empty.", subscription.getName());
            throw new ValidationException(String.format(INVALID_SUBSCRIPTION_MOINSTANCE_TO_ACTIVATE, subscription.getName()));
        }
        final Map<String, List<String>> moinstanceMap = new HashMap<>();
        for (final MoinstanceInfo moinstance : subscription.getMoInstances()) {
            List<String> moinstances = new ArrayList<>();
            if (moinstanceMap.containsKey(moinstance.getNodeName())) {
                moinstances = moinstanceMap.get(moinstance.getNodeName());
            }
            moinstances.add(moinstance.getMoInstanceName());
            moinstanceMap.put(moinstance.getNodeName(), moinstances);
        }
        logger.debug("Number of Moinstance with Node Address :{} and Number of nodes {} for subscription {}.", moinstanceMap.size(),
                subscription.getNodesFdns().size(), subscription.getName());
        if (moinstanceMap.size() != subscription.getNodesFdns().size()) {
            logger.error("Number of Moinstance with Node Address :{} and Number of nodes {} for subscription {} are not matching.",
                    moinstanceMap.size(), subscription.getNodesFdns().size(), subscription.getName());
            throw new ValidationException(String.format(INVALID_SUBSCRIPTION_MOINSTANCE_TO_ACTIVATE, subscription.getName()));
        }

        moLimitValidator.validateLimit(subscription, moinstanceMap);
    }

}
