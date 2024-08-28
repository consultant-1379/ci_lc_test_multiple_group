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

package com.ericsson.oss.services.pm.initiation.task.factories.validator;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.subscription.MtrSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * The Mtr task status validator.
 */
@SubscriptionTaskStatusValidation(subscriptionType = MtrSubscription.class)
@ApplicationScoped
public class MtrTaskStatusValidator extends ResourceTaskStatusValidator {

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    @Override
    public int getSubscriptionResourceCount(final int nodes, final Subscription subscription) throws DataAccessException {
        final MtrSubscription mtrSubscription = (MtrSubscription) subscriptionReadOperationService.findOneById(subscription.getId(), true);
        logger.debug("Attached Nodes for MtrSubscription -- {}", mtrSubscription.getAttachedNodes());
        return nodes + mtrSubscription.getAttachedNodes().size();
    }
}