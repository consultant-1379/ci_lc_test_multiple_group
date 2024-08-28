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

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.EBM_SUBSCRIPTIONATTRIBUTES;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.UNABLE_TO_ACTIVATE_EBM;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.pmic.api.modelservice.PmCapabilityReader;
import com.ericsson.oss.pmic.dto.subscription.EbmSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * This class validates EBM Subscription
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.EBM)
public class EbmSubscriptionValidator extends EventSubscriptionValidator<EbmSubscription> {

    @Inject
    private PmCapabilityReader pmCapabilityReader;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private PmCapabilityModelService capabilityAccessor;

    @Override
    public void validate(final EbmSubscription subscription) throws ValidationException {
        super.validate(subscription);
        if (subscription.isEbsEnabled() && subscription.getIsImported()) {
            final List<CounterInfo> correctCounters = subscriptionPfmData.getCorrectCounters(subscription.getName(), subscription.getEbsCounters(),
                    subscription.getNodesTypeVersion(), pmCapabilityReader.getSupportedModelDefinersForCounters(EBM_SUBSCRIPTIONATTRIBUTES), Collections.emptyList(), true);
            if (correctCounters.size() != subscription.getEbsCounters().size()) {
                SubscriptionCommonValidator.detectsIncompatibleCounters(subscription.getEbsCounters(), correctCounters, subscription.getNodes());
            }
        }
    }

    @Override
    public void validateActivation(final EbmSubscription subscription) throws ValidationException {
        super.validateActivation(subscription);
        try {
            final String subscriptionName = getAlreadyActiveEbmSubscriptionName(SubscriptionType.EBM,
                    subscription.getOutputMode());
            if (subscriptionName != null) {
                throw new ValidationException(String.format(UNABLE_TO_ACTIVATE_EBM, subscriptionName));
            }
        } catch (final DataAccessException e) {
            throw new ValidationException(e.getMessage());
        }
    }

    @Override
    public void validateImport(final EbmSubscription subscription) throws ValidationException, PfmDataException {
        super.validateImport(subscription);
        final List<CounterInfo> counterInfoList = subscription.getEbsCounters();
        if (!OutputModeType.FILE.equals(subscription.getOutputMode())) {
            throw new ValidationException("Invalid OutputMode " + subscription.getOutputMode().name() + " for subscription type "
                    + subscription.getType().name());
        }
        if (counterInfoList != null && !counterInfoList.isEmpty()) {
            if (!subscription.isEbsEnabled()) {
                throw new ValidationException("Ebs Counters are not allowed when ebsEnabled flag is false");
            }
            if (subscription.getNodes() == null || subscription.getNodes().isEmpty()) {
                throw new ValidationException("Nodes list in imported subscription is empty");
            }
            final Set<EventInfo> events = subscriptionPfmData.validateEventBasedCounters(subscription.getNodesTypeVersion(),
                    pmCapabilityReader.getSupportedModelDefinersForCounters(EBM_SUBSCRIPTIONATTRIBUTES), counterInfoList, EBM_SUBSCRIPTIONATTRIBUTES);
            events.addAll(subscription.getEvents());
            subscription.setEvents(new ArrayList<>(events));
        }
    }

    private String getAlreadyActiveEbmSubscriptionName(final SubscriptionType subscriptionType, final OutputModeType outputMode)
            throws DataAccessException {
        final SubscriptionType[] subscriptionTypes = {subscriptionType};
        final AdministrationState[] administrationStates = {AdministrationState.ACTIVATING,
                AdministrationState.ACTIVE,
                AdministrationState.UPDATING};
        final List<Subscription> subscriptions = subscriptionReadOperationService
                .findAllBySubscriptionTypeAndAdministrationState(subscriptionTypes,
                        administrationStates, false);
        for (final Subscription subscription : subscriptions) {
            final EbmSubscription ebmSubscription = (EbmSubscription) subscription;
            if (outputMode == ebmSubscription.getOutputMode() || outputMode.name().contains(ebmSubscription.getOutputMode().name())
                    || ebmSubscription.getOutputMode().name().contains(outputMode.name())) {
                return ebmSubscription.getName();
            }
        }
        return null;
    }
}
