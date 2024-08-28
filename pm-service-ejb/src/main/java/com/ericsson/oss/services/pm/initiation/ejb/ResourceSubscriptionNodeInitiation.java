/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.ejb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.ejb.Singleton;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dao.SubscriptionDao;
import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.EventSubscription;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionStatus;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.notification.events.Activate;
import com.ericsson.oss.services.pm.initiation.notification.events.Deactivate;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEvent;
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionManager;
import com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionException;

/**
 * This class is used to decide whether the nodes of a resource subscription should be activated or deactivated. Typically this class should be called
 * after an active resource subscription was updated.
 */
@Singleton
public class ResourceSubscriptionNodeInitiation {

    @Inject
    private Logger logger;
    @Inject
    private SubscriptionDao subscriptionDao;
    @Inject
    @Activate
    private InitiationEvent activationEvent;
    @Inject
    @Deactivate
    private InitiationEvent deactivationEvent;
    @Inject
    private SubscriptionManager subscriptionManager;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;
    @Inject
    private ConfigurationChangeListener configurationChangeListener;

    /**
     * Activate/deactivate nodes for this active Resource Subscription.
     *
     * @param subscription
     *         - Active resource subscription
     * @param nodesToActivate
     *         - list of nodes to activate
     * @param nodesToDeactivate
     *         - list of nodes to deactivate
     */
    public void activateOrDeactivateNodesOnActiveSubscription(final ResourceSubscription subscription, final List<Node> nodesToActivate,
                                                              final List<Node> nodesToDeactivate) {
        if (!AdministrationState.ACTIVE.equals(subscription.getAdministrationState())) {
            return;
        }
        if (!subscription.getSubscriptionStatus().isOneOf(SubscriptionStatus.RunningContinuous, SubscriptionStatus.RunningWithSchedule)) {
            logger.info(
                    "Subscription {} with id {} was updated, but its status is {} therefore it will not activate or deactivate nodes "
                            + "that were added({}) or removed({}).",
                    subscription.getName(), subscription.getId(), subscription.getSubscriptionStatus(), nodesToActivate.size(),
                    nodesToDeactivate.size());
            return;
        }

        final List<String> countersOrEvents = getCountersOrEventsFromSubscription(subscription);

        if (nodesToActivate != null && !nodesToActivate.isEmpty()) {
            logger.info("Adding {} nodes and {} counters to the Active Subscription [poid={},name={}]", nodesToActivate.size(),
                    countersOrEvents.size(), subscription.getId(), subscription.getName());
            updateSubscriptionStateToUpdatingAndUpdateSubscriptionManagerCache(subscription);
            activationEvent.execute(nodesToActivate, subscription);
        }
        if (nodesToDeactivate != null && !nodesToDeactivate.isEmpty()) {
            final List<Node> filteredNodes = filterPmFunctionOffNodesWhenMigrationOn(nodesToDeactivate);
            logger.info(
                    "Removing {} nodes and {} counters from the Active Subscription [poid={},name={}]."
                            + " Sending {} deactivation events, {} PmFunction OFF nodes will be skipped",
                    nodesToDeactivate.size(), countersOrEvents.size(), subscription.getId(), subscription.getName(), filteredNodes.size(),
                    nodesToDeactivate.size() - filteredNodes.size());
            updateSubscriptionStateToUpdatingAndUpdateSubscriptionManagerCache(subscription);
            deactivationEvent.execute(filteredNodes, subscription);
        }
    }

    private List<Node> filterPmFunctionOffNodesWhenMigrationOn(final List<Node> nodesToDeactivate) {
        if (isMigrationGoingOn()) {
            final List<Node> filteredNodes = new ArrayList<>();
            for (final Node node : nodesToDeactivate) {
                if (node.getPmFunction() != null && node.getPmFunction()) {
                    filteredNodes.add(node);
                }
            }
            return filteredNodes;
        } else {
            return nodesToDeactivate;
        }
    }

    private boolean isMigrationGoingOn() {
        return configurationChangeListener.getPmMigrationEnabled();
    }

    private List<String> getCountersOrEventsFromSubscription(final ResourceSubscription subscription) {
        if (subscription instanceof StatisticalSubscription) {
            return ((StatisticalSubscription) subscription).getCountersAsList();
        } else if (subscription instanceof EventSubscription) {
            return ((EventSubscription) subscription).getEventNames();
        }
        return Collections.emptyList();
    }

    private void updateSubscriptionStateToUpdatingAndUpdateSubscriptionManagerCache(final Subscription subscription) {
        if (!dpsAvailabilityStatus.isAvailable()) {
            logger.warn("Failed to update Subscription {}, Dps not available", subscription.getName());
            return;
        }
        try {
            if (subscription.getAdministrationState() != AdministrationState.UPDATING) {
                subscription.setAdministrationState(AdministrationState.UPDATING);
                final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
                map.put(Subscription.Subscription220Attribute.administrationState.name(), AdministrationState.UPDATING.name());
                subscriptionDao.updateSubscriptionAttributes(subscription.getId(), map);
                subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));
            }
            subscriptionManager.removeSubscriptionFromCache(subscription);
            subscriptionManager.getSubscriptionWrapper(subscription.getName(), subscription.getType());
        } catch (final InvalidSubscriptionException | DataAccessException e) {
            logger.warn("Couldn't update subscription's admin state to Updating. Continuing execution... Exception: {}", e.getMessage());
            logger.info("Exception stacktrace:", e);
        }
    }

}
