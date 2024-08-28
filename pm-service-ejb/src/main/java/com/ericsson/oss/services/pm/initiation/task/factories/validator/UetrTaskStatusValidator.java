/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.task.factories.validator;

import javax.enterprise.context.ApplicationScoped;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.UetrSubscription;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;

/**
 * This class validates the Task status for the UetrSubscription
 */
@SubscriptionTaskStatusValidation(subscriptionType = UetrSubscription.class)
@ApplicationScoped
public class UetrTaskStatusValidator extends ResourceTaskStatusValidator {

    @Override
    protected int getExpectedNumberOfScannersPerNode(final Subscription subscription) {
        final UetrSubscription uetrSubscription = (UetrSubscription) subscription;
        final int multiplier = uetrSubscription.getOutputMode().equals(OutputModeType.FILE_AND_STREAMING) ? 2 : 1;
        return multiplier * uetrSubscription.getUeInfoList().size();
    }
}
