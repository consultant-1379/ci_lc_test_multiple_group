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

package com.ericsson.oss.services.pm.initiation.validators;

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_SUBSCRITPION_TO_ACTIVATE;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.UNABLE_TO_ACTIVATE_RES;
import static com.ericsson.oss.services.pm.initiation.model.metadata.moinstances.PmMoinstancesLookUp.UTRAN_CELL_GROUP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.ResSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.ResInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.ResServiceCategory;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.initiation.model.metadata.moinstances.PmMoinstancesLookUp;
import com.ericsson.oss.services.pm.initiation.model.metadata.res.PmResLookUp;
import com.ericsson.oss.services.pm.initiation.restful.ResSubscriptionAttributes;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.services.pm.initiation.restful.api.PmMetaDataLookupLocal;

/**
 * This class validates Statistical Subscription
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.RES)
public class ResSubscriptionValidator extends ResourceSubscriptionValidator<ResSubscription> {

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    @Inject
    private PmResLookUp pmResLookUp;

    @Inject
    private PmMetaDataLookupLocal metaDataLookup;

    @Inject
    private PmMoinstancesLookUp pmMoinstancesLookUp;

    @Override
    public void validate(final ResSubscription subscription) throws ValidationException {
        super.validate(subscription);
        if (subscription.getAdministrationState() == AdministrationState.ACTIVE) {
            checkNodesPresenceOnActiveSubscription(subscription);
        }
    }

    @Override
    public void validateImport(final ResSubscription subscription) throws ValidationException, PfmDataException {
        super.validateImport(subscription);

        final Set<NodeTypeAndVersion> nodeTypeVersionList = subscription.getNodesTypeVersion();
        if (nodeTypeVersionList.isEmpty()) {
            throw new ValidationException("Nodes list in imported subscription is empty");
        }
        final ResSubscriptionAttributes supportedAttributes = pmResLookUp.getResAttributes(nodeTypeVersionList);
        final Map<String, List<String>> supportedCounters = metaDataLookup.getCounterSubGroups(subscription.getType().name());
        List<CellInfo> supportedCells = null;
        try {
            supportedCells = pmMoinstancesLookUp.getCellInfos(nodeTypeVersionList, SubscriptionType.RES, getNodeNamesToTypeMap(subscription));
        } catch (final DataAccessException e) {
            throw new ValidationException(e.getMessage(), e);
        }
        validateSamplingRates(subscription, supportedAttributes);
        validateResMeasDef(supportedAttributes, supportedCounters, subscription);
        validateCounters(supportedCounters, subscription);
        validateCountersConsistency(subscription.getCounters(), subscription.getResMeasDef());
        validateResUeFraction(supportedAttributes, subscription);
        validateCells(supportedCells, subscription);

    }

    /**
     * @param subscription
     */
    private Map<String, String> getNodeNamesToTypeMap(final ResSubscription subscription) {
        final Map<String, String> nodeNamesToTypeMap = new HashMap<>();
        for (final Node node : subscription.getNodes()) {
            nodeNamesToTypeMap.put(node.getName(), node.getNeType());
        }
        return nodeNamesToTypeMap;
    }

    @Override
    public void validateActivation(final ResSubscription subscription) throws ValidationException {
        super.validateActivation(subscription);
        if (subscription.getCounters().isEmpty()) {
            throw new ValidationException(String.format(INVALID_SUBSCRITPION_TO_ACTIVATE, subscription.getName()));
        }
        checkNodesPresenceOnActiveSubscription(subscription);
    }

    private void validateCounters(final Map<String, List<String>> supportedCounters, final ResSubscription subscription) throws ValidationException {
        final List<CounterInfo> countersFromSubscription = subscription.getCounters();
        for (final CounterInfo counter : countersFromSubscription) {
            if (!UTRAN_CELL_GROUP.equals(counter.getMoClassType())) {
                final String message = String.format("Counter Group provided from input subscription is invalid: %s", counter.getMoClassType());
                throw new ValidationException(message);
            }
            if (!supportedCounters.get(UTRAN_CELL_GROUP).contains(counter.getName())) {
                final String message = String.format("Counter provided from input subscription is invalid: %s", counter.getName());
                throw new ValidationException(message);
            }
        }
    }

    private void validateCountersConsistency(final List<CounterInfo> counters, final Map<String, ResInfo> resMeasDef) throws ValidationException {
        final Set<String> namesFromCounters = new HashSet<>(counters.size());
        for (final CounterInfo counter : counters) {
            namesFromCounters.add(counter.getName());
        }
        if (namesFromCounters.size() != counters.size() || namesFromCounters.size() != resMeasDef.keySet().size()
                || namesFromCounters.retainAll(resMeasDef.keySet())) {
            throw new ValidationException("counters and resMeasDef attributes from input Subscription are not consistent");
        }
    }

    private void validateResMeasDef(final ResSubscriptionAttributes supportedAttributes, final Map<String, List<String>> supportedCounters,
                                    final ResSubscription subscription)
            throws ValidationException {
        final Iterator<Map.Entry<String, ResInfo>> resMeasDefFromSubscriptionIt = subscription.getResMeasDef().entrySet().iterator();
        final Set<ResInfo> processedResInfo = new HashSet<>();
        while (resMeasDefFromSubscriptionIt.hasNext()) {
            final Map.Entry<String, ResInfo> resMeasDef = resMeasDefFromSubscriptionIt.next();
            if (!processedResInfo.add(resMeasDef.getValue())) {
                final String message = String.format("Rmq-Service pair provided from input subscription is already used: %s",
                        resMeasDef.getValue().toString());
                throw new ValidationException(message);
            }
            if (!supportedCounters.get(UTRAN_CELL_GROUP).contains(resMeasDef.getKey())) {
                final String message = String.format("Counters provided from input subscription are invalid: %s", resMeasDef.getKey());
                throw new ValidationException(message);
            }
            if (!supportedAttributes.getSupportedResRmq().contains(resMeasDef.getValue().getRmq())) {
                final String message = String.format("Rmq provided from input subscription is invalid: %s", resMeasDef.getValue().getRmq());
                throw new ValidationException(message);
            }
            if (!supportedAttributes.getSupportedResServices().contains(resMeasDef.getValue().getService())) {
                final String message = String.format("Service provided from input subscription is invalid: %s", resMeasDef.getValue().getService());
                throw new ValidationException(message);
            }
        }
    }

    private void validateSamplingRates(final ResSubscription subscription, final ResSubscriptionAttributes supportedAttributes)
            throws ValidationException {
        final Map<ResServiceCategory, Integer> resMeasPeriodsFromSub = subscription.getResMeasPeriods();
        for (final Map.Entry<ResServiceCategory, Integer> subResServiceCategory : resMeasPeriodsFromSub.entrySet()) {
            if (!supportedAttributes.getSupportedSamplingRates().get(subResServiceCategory.getKey().name())
                    .contains(subResServiceCategory.getValue())) {
                final String message = String.format("Sampling Rate provided from input subscription are invalid: %s",
                        subResServiceCategory.getValue());
                throw new ValidationException(message);
            }
        }
    }

    private void validateResUeFraction(final ResSubscriptionAttributes supportedAttributes, final ResSubscription subscription)
            throws ValidationException {
        final Integer resUeFractionFromSub = subscription.getResUeFraction();
        if (!supportedAttributes.getSupportedResUeFraction().contains(resUeFractionFromSub)) {
            final String message = String.format("UE Fraction provided from input subscription is invalid: %s", resUeFractionFromSub);
            throw new ValidationException(message);
        }
    }

    private void checkNodesPresenceOnActiveSubscription(final ResSubscription subscription) throws ValidationException {
        final Set<String> availableNodes = new HashSet<>(subscription.getNodesFdns().size());
        availableNodes.addAll(subscription.getNodesFdns());
        try {
            subscriptionReadOperationService.getAvailableNodes(subscription.getId(), SubscriptionType.RES, availableNodes);
        } catch (final RetryServiceException | DataAccessException e) {
            throw new ValidationException(e.getMessage(), e);
        }
        if (availableNodes.size() != subscription.getNodesFdns().size()) {
            throw new ValidationException(String.format(UNABLE_TO_ACTIVATE_RES, subscription.getName()));
        }
    }

    private void validateCells(final List<CellInfo> supportedCells, final ResSubscription subscription) throws ValidationException {
        final List<CellInfo> cellsList = subscription.getCells();
        final List<CellInfo> invalidCells = new ArrayList<>();
        for (final CellInfo cell : cellsList) {
            if (cell != null && !supportedCells.contains(cell)) {
                invalidCells.add(cell);
            }
        }
        if (!invalidCells.isEmpty()) {
            final String message = String.format("Cell provided from input subscription are invalid: %s", invalidCells);
            throw new ValidationException(message);
        }
    }
}
