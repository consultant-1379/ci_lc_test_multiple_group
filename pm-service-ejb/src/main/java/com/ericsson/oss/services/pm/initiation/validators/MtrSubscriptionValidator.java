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

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_MTR_SUBSCRIPTION_NO_IMSI_TO_ACTIVATE;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_MTR_SUBSCRIPTION_NO_RR_TO_ACTIVATE;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.MTR_SUBSCRIPTION_INVALID_RR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;

import com.ericsson.oss.pmic.dto.subscription.MtrSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.MtrAccessType;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import java.time.temporal.ValueRange;

/**
 * This class validates MtrSubscription
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.MTR)
public class MtrSubscriptionValidator extends ResourceSubscriptionValidator<MtrSubscription> {

    private static final String INVALID_ROP_PERIOD = "Invalid rop period %s for subscription type = %s";

    private static final String INVALID_ACCESS_TYPE = "Invalid AccessTypes for subscription type = %s, %s is mandatory";

    private static final String NULL_ACCESS_TYPE = "Access Types cannot be null";

    @Override
    public void validate(final MtrSubscription subscription) throws ValidationException {
        super.validate(subscription);
        subscriptionCommonValidator.validateUeInfos(subscription.getUeInfo() != null ?
                Collections.singletonList(subscription.getUeInfo()) : new ArrayList<UeInfo>());
        validateRRRange(subscription);

    }

    @Override
    public void validateImport(final MtrSubscription mtrSubscription) throws PfmDataException, ValidationException {
        super.validateImport(mtrSubscription);
        validateRopInfo(mtrSubscription.getRop() != null ? mtrSubscription.getRop() : RopPeriod.NOT_APPLICABLE, mtrSubscription.getType());
        validateMtrAccessTypes(mtrSubscription);
    }

    /*
     * To Validate the RopInfo Without Nodes
     * @param mtrSubscription
     */
    private void validateRopInfo(final RopPeriod ropPeriod, final SubscriptionType type) throws ValidationException {
        if (!ropPeriod.equals(RopPeriod.FIFTEEN_MIN)) {
            throw new ValidationException(String.format(INVALID_ROP_PERIOD, ropPeriod.name(), type));
        }
    }

    /*
     * To validate the MtrAccessTypes while importing the subscription
     * @param mtrSubscription
     */
    private void validateMtrAccessTypes(final MtrSubscription mtrSubscription) throws ValidationException {
        final List<MtrAccessType> mtrAccessTypeList = mtrSubscription.getMtrAccessTypes();
        if (mtrAccessTypeList == null) {
            throw new ValidationException(NULL_ACCESS_TYPE);
        } else if (mtrAccessTypeList.isEmpty() || !mtrSubscription.getMtrAccessTypes().contains(MtrAccessType.LCS)) {
            throw new ValidationException(String.format(INVALID_ACCESS_TYPE, mtrSubscription.getType(), MtrAccessType.LCS));
        }
    }

    @Override
    public void validateActivation(final MtrSubscription mtrSubscription) throws ValidationException {
        super.validateActivation(mtrSubscription);
        validateMtrAccessTypes(mtrSubscription);
        validateUeInfo(mtrSubscription);
        validateRR(mtrSubscription);
    }

    /*
     * To validate the UeInfo
     * @param mtrSubscription
     */
    private void validateUeInfo(final MtrSubscription mtrSubscription) throws ValidationException {
        if (mtrSubscription.getUeInfo() == null || mtrSubscription.getUeInfo().getValue() == null) {
            throw new ValidationException(String.format(INVALID_MTR_SUBSCRIPTION_NO_IMSI_TO_ACTIVATE, mtrSubscription.getName()));
        }
    }


    /*
     * To validate the RR
     * @param mtrSubscription
     */
    private void validateRR(final MtrSubscription mtrSubscription) throws ValidationException {
        if (mtrSubscription.getRecordingReference() == null) {
            throw new ValidationException(String.format(INVALID_MTR_SUBSCRIPTION_NO_RR_TO_ACTIVATE, mtrSubscription.getName()));
        }
        validateRRRange(mtrSubscription);
    }


    /*
     * To validate the RR Range
     * @param mtrSubscription
     */
    private void validateRRRange(final MtrSubscription mtrSubscription) throws ValidationException {
        if (mtrSubscription.getRecordingReference() != null && !ValueRange.of(0, 63).isValidIntValue(mtrSubscription.getRecordingReference())) {
                throw new ValidationException(MTR_SUBSCRIPTION_INVALID_RR);
        }
    }

}
