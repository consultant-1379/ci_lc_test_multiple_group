/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.adjuster.impl;

import java.util.List;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.context.ContextService;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterLocal;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventType;

/**
 * SubscriptionDataAdjuster.
 *
 * @param <T>
 *         - Type of subscription.
 */
public class SubscriptionDataAdjuster<T extends Subscription> implements SubscriptionDataAdjusterLocal<T> {

    private static final String LOGGED_USER = "X-Tor-UserID";
    private static final String DEFAULT_OPERATOR = "pmOperator";
    private static final String LOGGED_USER_NOT_FOUND = "logged user not found";

    @Inject
    protected SubscriptionMetaDataService subscriptionPfmData;

    @Inject
    private ContextService contextService;

    @Inject
    private Logger logger;

    @Override
    public void correctSubscriptionData(final T subscription) {
        //no default behaviour defined.
    }

    @Override
    public boolean shouldUpdateSubscriptionDataOnInitiationEvent(final List<Node> nodes, final T subscription,
                                                                 final InitiationEventType initiationEventType)
            throws DataAccessException {
        return false;
    }

    public void adjustPfmSubscriptionData(final T subscription) {
        //no default behaviour defined.
    }

    @Override
    public void updateImportedSubscriptionWithCorrectValues(final T subscription) throws DataAccessException {
        if (subscription.getIsImported()) {
            subscription.setAdministrationState(AdministrationState.INACTIVE);
            subscription.setOperationalState(OperationalState.NA);
            subscription.setUserType(UserType.USER_DEF);
            subscription.setTaskStatus(TaskStatus.OK);
            subscription.setActivationTime(null);
            subscription.setDeactivationTime(null);
            subscription.setUserActivationDateTime(null);
            subscription.setUserDeActivationDateTime(null);
            subscription.setPersistenceTime(null);
            subscription.setOwner(getLoggedUser());
        }
    }

    private String getLoggedUser() {
        String operator = null;
        if (contextService != null && contextService.getContextValue(LOGGED_USER) != null) {
            operator = contextService.getContextValue(LOGGED_USER).toString();
        } else {
            operator = DEFAULT_OPERATOR;
            logger.warn(LOGGED_USER_NOT_FOUND);
        }
        return operator;
    }
}
