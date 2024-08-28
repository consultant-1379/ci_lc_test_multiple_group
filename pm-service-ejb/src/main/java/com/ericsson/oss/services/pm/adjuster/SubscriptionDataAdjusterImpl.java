/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.adjuster;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventType;

/**
 * Class to update counters/events of subscriptions depending on the counters/events available from the model service for the given node type and
 * version. Fot imported subscriptions, nodes are updated from DPS as well as various predefined subscription attributes.
 */
@ApplicationScoped
@SubscriptionDataAdjusterQualifier
public class SubscriptionDataAdjusterImpl implements SubscriptionDataAdjusterLocal<Subscription> {

    @Inject
    private Logger logger;

    @Any
    @Inject
    private Instance<SubscriptionDataAdjusterLocal> dataAdjusters;

    @Override
    public void correctSubscriptionData(final Subscription subscription) {
        final SubscriptionDataAdjusterLocal<Subscription> updater = getInstance(subscription);
        updater.correctSubscriptionData(subscription);
    }

    @Override
    public void adjustPfmSubscriptionData(final Subscription subscription) {
        final SubscriptionDataAdjusterLocal<Subscription> updater = getInstance(subscription);
        updater.adjustPfmSubscriptionData(subscription);
    }

    @Override
    public boolean shouldUpdateSubscriptionDataOnInitiationEvent(final List<Node> nodes, final Subscription subscription,
                                                                 final InitiationEventType initiationEventType)
            throws DataAccessException {
        final SubscriptionDataAdjusterLocal<Subscription> updater = getInstance(subscription);
        return updater.shouldUpdateSubscriptionDataOnInitiationEvent(nodes, subscription, initiationEventType);
    }

    @Override
    public void updateImportedSubscriptionWithCorrectValues(final Subscription subscription) throws DataAccessException {
        final SubscriptionDataAdjusterLocal<Subscription> updater = getInstance(subscription);
        updater.updateImportedSubscriptionWithCorrectValues(subscription);
    }

    private SubscriptionDataAdjusterLocal<Subscription> getInstance(final Subscription subscription) {
        final SubscriptionDataAdjusterAnnotationLiteral selector = new SubscriptionDataAdjusterAnnotationLiteral(subscription.getClass());
        final Instance<SubscriptionDataAdjusterLocal> selectedInstance = dataAdjusters.select(selector);

        if (selectedInstance.isUnsatisfied()) {
            logger.error("Subscription Type: {} from Subscription : {} is not currently supported ", subscription.getClass().getSimpleName(),
                    subscription.getName());
            throw new UnsupportedOperationException("Subscription Type: " + subscription.getClass().getSimpleName() + " from Subscription : "
                    + subscription.getName() + " is not currently supported");
        }
        logger.debug("Updating counters or events for {} {} with id {}", subscription.getClass().getSimpleName(), subscription.getName(),
                subscription.getId());
        return selectedInstance.get();
    }

    /**
     * SubscriptionDataAdjusterAnnotationLiteral.
     */
    @SuppressWarnings("all")
    class SubscriptionDataAdjusterAnnotationLiteral extends AnnotationLiteral<SubscriptionDataAdjusterQualifier>
            implements SubscriptionDataAdjusterQualifier {
        private static final long serialVersionUID = 5378299297468178066L;
        private final Class<? extends Subscription> subscriptionClass;

        /**
         * Instantiates a new SubscriptionDataAdjusterAnnotationLiteral annotation.
         *
         * @param subscriptionClass
         *         the subscription class
         */
        SubscriptionDataAdjusterAnnotationLiteral(final Class<? extends Subscription> subscriptionClass) {
            this.subscriptionClass = subscriptionClass;
        }

        @Override
        public Class<? extends Subscription> subscriptionClass() {
            return subscriptionClass;
        }
    }
}
