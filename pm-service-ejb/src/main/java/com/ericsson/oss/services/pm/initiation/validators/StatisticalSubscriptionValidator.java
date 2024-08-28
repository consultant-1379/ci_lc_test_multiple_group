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

import javax.enterprise.context.ApplicationScoped;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This class validates Statistical Subscription
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.STATISTICAL)
public class StatisticalSubscriptionValidator extends StatisticalSubscriptionParentValidator<StatisticalSubscription> {

    @Override
    public void validateImport(final StatisticalSubscription subscription) throws ValidationException, PfmDataException {
        super.validateImport(subscription);
        for (final Node node : subscription.getNodes()) {
            subscriptionCommonValidator.validateCountersForNotSupportedRops(node.getNeType(), subscription.getType(), subscription.getCounters(),
                    subscription.getRop());
        }
        subscriptionCommonValidator.checkForInvalidNodeTypesInSubscription(subscription);
    }
}
