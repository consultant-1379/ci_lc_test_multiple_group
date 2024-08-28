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

import com.ericsson.oss.pmic.dto.subscription.EventSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.DataAccessException;

/**
 * EventSubscriptionDataAdjuster.
 *
 * @param <T>
 */
public class EventSubscriptionDataAdjuster<T extends EventSubscription> extends ResourceSubscriptionDataAdjuster<T> {

    @Override
    public void correctSubscriptionData(final T subscription) {
        super.correctSubscriptionData(subscription);
        if (SubscriptionType.RPMO != subscription.getType() && SubscriptionType.RTT != subscription.getType()) {
            final List<EventInfo> events = subscriptionPfmData.getCorrectEvents(subscription.getName(),
                    subscription.getEvents(),
                    subscription.getNodesTypeVersion());
            subscription.setEvents(events);
        }
    }

    /**
     * for celltrace, events already corrected.
     * Just call super class.
     *
     * @param subscription
     *         the subscription
     */
    public void correctCelltraceSubscriptionData(final T subscription) {
        super.correctSubscriptionData(subscription);
    }

    @Override
    public void updateImportedSubscriptionWithCorrectValues(final T subscription) throws DataAccessException {
        if (subscription.getIsImported()) {
            super.updateImportedSubscriptionWithCorrectValues(subscription);
        }
    }
}
