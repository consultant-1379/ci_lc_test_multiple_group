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
package com.ericsson.oss.services.pm.common.systemdefined.rule;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.common.systemdefined.SubscriptionSystemDefinedAuditRule;
import com.ericsson.oss.services.pm.common.utils.SubscriptionTypeInstanceSelector;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This Class SystemDefinedAuditRuleSelector, selects an instance of SystemDefinedSubscriptionAuditRule
 */
@ApplicationScoped
public class SystemDefinedAuditRuleSelector
        extends SubscriptionTypeInstanceSelector<SubscriptionSystemDefinedAuditRule<ResourceSubscription>> {

    private static final String MESSAGE = "No SystemDefined audit rule found for type {}";

    @Any
    @Inject
    private Instance<SubscriptionSystemDefinedAuditRule<ResourceSubscription>> systemDefinedSubscriptionAuditRules;

    /**
     * Gets the single instance of SystemDefinedSubscriptionAuditRule.
     *
     * @param subscriptionType
     *         the subscription type
     *
     * @return single instance of SystemDefinedSubscriptionAuditRule
     * @throws ValidationException
     *         if validator for subscriptionType if not found
     */
    public SubscriptionSystemDefinedAuditRule<ResourceSubscription> getInstance(final SubscriptionType subscriptionType) throws ValidationException {
        return super.getInstance(subscriptionType, systemDefinedSubscriptionAuditRules).get();
    }

    protected String getMessage() {
        return MESSAGE;
    }
}
