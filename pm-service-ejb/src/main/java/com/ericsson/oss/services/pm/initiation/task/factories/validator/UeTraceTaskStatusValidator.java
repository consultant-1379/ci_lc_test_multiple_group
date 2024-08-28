/*
 * COPYRIGHT Ericsson 2017
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.task.factories.validator;

import javax.enterprise.context.ApplicationScoped;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.UETraceSubscription;
import com.ericsson.oss.services.pm.common.constants.PmFeature;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;

/**
 * Validator for the UeTrace Subscription
 */
@SubscriptionTaskStatusValidation(subscriptionType = UETraceSubscription.class)
@ApplicationScoped
public class UeTraceTaskStatusValidator extends TraceTaskStatusValidator {

    @Override
    public void validateTaskStatusAndAdminState(final Subscription subscription) {
        logger.debug("No validation action for UeTrace subscription {} with id {}", subscription.getName(), subscription.getId());
    }

    @Override
    PmFeature getPmFeatureForSupportedNodes() {
        return PmFeature.UETRACE_FILE_COLLECTION;
    }
}
