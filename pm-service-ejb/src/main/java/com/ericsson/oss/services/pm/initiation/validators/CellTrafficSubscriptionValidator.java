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

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_SUBSCRITPION_NO_TRIGGEREVENT_TO_ACTIVATE;

import javax.enterprise.context.ApplicationScoped;

import com.ericsson.oss.pmic.dto.subscription.CellTrafficSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.TriggerEventInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This class validates CellTraffic Subscription
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.CELLTRAFFIC)
public class CellTrafficSubscriptionValidator extends EventSubscriptionValidator<CellTrafficSubscription> {

    @Override
    public void validateActivation(final CellTrafficSubscription subscription)
            throws ValidationException {
        super.validateActivation(subscription);

        final TriggerEventInfo triggerEvent = subscription.getTriggerEventInfo();
        if (triggerEvent == null || triggerEvent.getGroupName() == null || triggerEvent.getName() == null) {
            throw new ValidationException(String.format(INVALID_SUBSCRITPION_NO_TRIGGEREVENT_TO_ACTIVATE, subscription.getName()));
        }
    }
}
