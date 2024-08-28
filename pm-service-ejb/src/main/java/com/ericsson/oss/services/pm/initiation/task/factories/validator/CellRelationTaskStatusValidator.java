/*
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.task.factories.validator;

import javax.enterprise.context.ApplicationScoped;

import com.ericsson.oss.pmic.dto.subscription.CellRelationSubscription;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;

/**
 * This class validates the Task status for the CellRelation
 */
@SubscriptionTaskStatusValidation(subscriptionType = CellRelationSubscription.class)
@ApplicationScoped
public class CellRelationTaskStatusValidator extends StatisticalTaskStatusValidator {

}
