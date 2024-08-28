/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.common.accesscontrol;

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.NO_PERMISSION_ACCESS;

import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.security.accesscontrol.EAccessControl;
import com.ericsson.oss.itpf.sdk.security.accesscontrol.ESecurityAction;
import com.ericsson.oss.itpf.sdk.security.accesscontrol.ESecurityResource;
import com.ericsson.oss.itpf.sdk.security.accesscontrol.SecurityViolationException;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;

/**
 * Utility Class for access control resources.
 */
public class AccessControlUtil {

    @Inject
    private EAccessControl accessControl;

    /**
     * Checks if the subscription is authorized to perform a specific action.
     *
     * @param subscriptionType
     *         the subscription type that needs to be authorized
     * @param action
     *         the action that needs to be performed on the resource
     *
     * @throws IllegalArgumentException
     *         if access control action is {@code null} or subscription don't match
     * @throws SecurityViolationException
     *         if user does not have permission to perform action
     */
    public void checkSubscriptionTypeRoleBasedAccess(final SubscriptionType subscriptionType, final String action) {
        if (action == null) {
            throw new IllegalArgumentException("Access control action can't be null");
        }
        if (accessControl.isAuthorized(new ESecurityResource(AccessControlResources.SUBSCRIPTION), new ESecurityAction(action))) {
            return;
        }
        final String resourceId = SubscriptionTypeToResourceIdMapper.getResourceId(subscriptionType);
        if (!accessControl.isAuthorized(new ESecurityResource(resourceId), new ESecurityAction(action))) {
            throw new SecurityViolationException(NO_PERMISSION_ACCESS);
        }
    }
}
