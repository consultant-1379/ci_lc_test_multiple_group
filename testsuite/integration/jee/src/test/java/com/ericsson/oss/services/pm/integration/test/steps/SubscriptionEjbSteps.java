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

package com.ericsson.oss.services.pm.integration.test.steps;

import javax.inject.Inject;

import com.ericsson.oss.services.pm.integration.test.SubscriptionHelper;
import com.ericsson.oss.services.pm.scheduling.impl.SystemDefinedSubscriptionAuditScheduler;

public class SubscriptionEjbSteps {

    @Inject
    private SystemDefinedSubscriptionAuditScheduler subscriptionAuditor;

    @Inject
    private SubscriptionHelper subscriptionHelper;

    public void invokeSysDefSubscriptionAudit() {
        subscriptionAuditor.onTimeout();
    }

    public void deleteAllSubscription() {
        subscriptionHelper.deleteAllSubscription();
    }
}
