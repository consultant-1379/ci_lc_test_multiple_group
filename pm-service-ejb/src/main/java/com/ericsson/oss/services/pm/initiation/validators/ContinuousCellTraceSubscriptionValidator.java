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

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_SUBSCRITPION_TO_ACTIVATE;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;

import com.ericsson.oss.pmic.dto.subscription.ContinuousCellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This class validates ContinousCellTrace Subscription
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.CONTINUOUSCELLTRACE)
public class ContinuousCellTraceSubscriptionValidator extends EventSubscriptionValidator<ContinuousCellTraceSubscription> {
    @Override
    public void validateActivation(final ContinuousCellTraceSubscription subscription) throws ValidationException {
        super.validateActivation(subscription);
        if (subscription.getEvents().isEmpty()) {
            throw new ValidationException(String.format(INVALID_SUBSCRITPION_TO_ACTIVATE, subscription.getName()));
        }
    }

    @Override
    public void validateImport(final ContinuousCellTraceSubscription subscription) throws ValidationException, PfmDataException {
        subscriptionCommonValidator.validatePmFunction(subscription.getNodes());

        final List<EventInfo> events = subscriptionPfmData.getCorrectEvents(subscription.getName(), subscription.getEvents(),
                subscription.getNodesTypeVersion());
        if (subscription.getIsImported() && subscription.getEvents().size() != events.size()) {
            final Set<EventInfo> incompatible = new HashSet<>(subscription.getEvents());
            incompatible.removeAll(events);

            throw new PfmDataException("Events provided from imported subscription are invalid: " + incompatible.toString());
        }
    }
}
