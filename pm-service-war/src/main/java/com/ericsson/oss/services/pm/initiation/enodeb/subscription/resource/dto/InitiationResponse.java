/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2014
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource.dto;

import java.util.Date;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;

/**
 * The Initiation response.
 */
public class InitiationResponse {

    private Long subscriptionId;
    private String name;
    private Date persistenceTime;
    private AdministrationState administrationState;
    private Date userActivationDateTime;
    private Date userDeactivationDateTime;

    /**
     * Build initiation response from subscription.
     *
     * @param subscription
     *         the subscription
     *
     * @return the initiation response
     */
    public InitiationResponse buildFromSubscription(final Subscription subscription) {
        subscriptionId = subscription.getId();
        name = subscription.getName();
        persistenceTime = subscription.getPersistenceTime();
        administrationState = subscription.getAdministrationState();
        userActivationDateTime = subscription.getUserActivationDateTime();
        userDeactivationDateTime = subscription.getUserDeActivationDateTime();
        return this;
    }

    /**
     * Gets id.
     *
     * @return the id
     */
    public Long getId() {
        return subscriptionId;
    }

    /**
     * Gets name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets persistence time.
     *
     * @return the persistence time
     */
    public Date getPersistenceTime() {
        return persistenceTime;
    }

    /**
     * Gets administration state.
     *
     * @return the administration state
     */
    public AdministrationState getAdministrationState() {
        return administrationState;
    }

    /**
     * Gets user activation date time.
     *
     * @return the user activation date time
     */
    public Date getUserActivationDateTime() {
        return userActivationDateTime;
    }

    /**
     * Gets user deactivation date time.
     *
     * @return the user deactivation date time
     */
    public Date getUserDeactivationDateTime() {
        return userDeactivationDateTime;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("InitiationResponse [subscriptionId=");
        builder.append(subscriptionId);
        builder.append(", name=");
        builder.append(name);
        builder.append(", persistenceTime=");
        builder.append(persistenceTime);
        builder.append(", administrationState=");
        builder.append(administrationState);
        builder.append(", userActivationDateTime=");
        builder.append(userActivationDateTime);
        builder.append(", userDeactivationDateTime=");
        builder.append(userDeactivationDateTime);
        builder.append("]");
        return builder.toString();
    }
}
