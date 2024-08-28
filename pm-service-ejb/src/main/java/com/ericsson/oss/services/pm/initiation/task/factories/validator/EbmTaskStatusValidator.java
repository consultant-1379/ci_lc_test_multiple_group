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

import com.ericsson.oss.pmic.dto.subscription.EbmSubscription;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;

/**
 * The type Ebm task status validator.
 */
@SubscriptionTaskStatusValidation(subscriptionType = EbmSubscription.class)
@ApplicationScoped
public class EbmTaskStatusValidator extends ResourceTaskStatusValidator {
}
