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
import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.subscription.CtumSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.common.constants.PmFeature;
import com.ericsson.oss.services.pm.initiation.ctum.CtumSubscriptionServiceLocal;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;

/**
 * Validator for the Ctum Subscription
 */
@SubscriptionTaskStatusValidation(subscriptionType = CtumSubscription.class)
@ApplicationScoped
public class CtumTaskStatusValidator extends TraceTaskStatusValidator {

    @Inject
    private CtumSubscriptionServiceLocal ctumSubscriptionService;

    @Override
    public void validateTaskStatusAndAdminState(final Subscription subscription) {
        logger.debug("Performing CTUM audit of subscription {}", subscription.getName());
        ctumSubscriptionService.ctumAudit();
    }

    @Override
    PmFeature getPmFeatureForSupportedNodes() {
        return PmFeature.CTUM_FILE_COLLECTION;
    }
}
