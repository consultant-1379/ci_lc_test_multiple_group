/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.notification.model;

import java.io.Serializable;

import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;

/**
 * The Initiation schedule model.
 */
public class InitiationScheduleModel implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long subscriptionId;

    private final AdministrationState eventType;

    /**
     * Instantiates a new Initiation schedule model.
     *
     * @param subscriptionId
     *         the subscription id
     * @param eventType
     *         the event type
     */
    public InitiationScheduleModel(final long subscriptionId, final AdministrationState eventType) {
        this.subscriptionId = subscriptionId;
        this.eventType = eventType;
    }

    /**
     * Gets subscription id.
     *
     * @return the subscriptionId
     */
    public long getSubscriptionId() {
        return subscriptionId;
    }

    /**
     * Gets event type.
     *
     * @return the eventType
     */
    public AdministrationState getEventType() {
        return eventType;
    }

    @Override
    public String toString() {
        final StringBuilder str = new StringBuilder(this.getClass().getSimpleName());
        str.append("[subscriptionId=").append(subscriptionId).append(",eventType=").append(eventType).append("]");
        return str.toString();
    }

}
