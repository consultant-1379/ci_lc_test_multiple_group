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

import javax.enterprise.context.ApplicationScoped;

import com.ericsson.oss.pmic.dto.subscription.BscRecordingsSubscription;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This class validates BscRecordingsSubscription
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.BSCRECORDINGS)
public class BscRecordingsSubscriptionValidator extends ResourceSubscriptionValidator<BscRecordingsSubscription> {

    private static final String INVALID_ROP_PERIOD = "Invalid rop period %s for subscription type = %s";

    @Override
    public void validateImport(final BscRecordingsSubscription subscription) throws ValidationException, PfmDataException {
        super.validateImport(subscription);
        validateRopInfo(subscription.getRop() != null ? subscription.getRop() : RopPeriod.NOT_APPLICABLE, subscription.getType());
    }

    /*
     * To Validate the RopInfo Without Nodes
     * @param BscRecordingsSubscription
     */
    private void validateRopInfo(final RopPeriod ropPeriod, final SubscriptionType type) throws ValidationException {
        if (!ropPeriod.equals(RopPeriod.FIFTEEN_MIN)) {
            throw new ValidationException(String.format(INVALID_ROP_PERIOD, ropPeriod.name(), type));
        }
    }

}
