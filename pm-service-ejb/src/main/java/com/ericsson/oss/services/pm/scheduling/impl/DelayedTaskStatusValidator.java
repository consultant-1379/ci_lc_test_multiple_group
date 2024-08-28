/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.scheduling.impl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Singleton;
import javax.ejb.Timeout;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.util.TimeGenerator;
import com.ericsson.oss.services.pm.initiation.task.TaskStatusValidator;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * This class performs task status and admin state validation for the given subscription after a timeout. The reason for the timeout is to prevent the
 * validation taking place many times if many notifications are received simultaneously
 */
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class DelayedTaskStatusValidator {
    private static final Logger logger = LoggerFactory.getLogger(DelayedTaskStatusValidator.class);

    private final Map<Long, ScheduleData> subscriptionIdAndInitialDelay = new ConcurrentHashMap<>();

    @Inject
    private TimerService timerService;

    @Inject
    @SubscriptionTaskStatusValidation
    private TaskStatusValidator<Subscription> taskStatusValidator;

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    @Inject
    private TimeGenerator timeGenerator;

    /**
     * Postconstruct to start a Timer
     */
    @PostConstruct
    public void postConstruct() {
        final TimerConfig timerConfig = new TimerConfig("DelayedTaskStatusValidator", false);
        timerService.createIntervalTimer(TimeUnit.SECONDS.toMillis(30), TimeUnit.SECONDS.toMillis(30), timerConfig);
    }

    /**
     * Start a timer to validate the given subscription after a timeout
     *
     * @param subscriptionId
     *         - The ID of the subscription to validate
     * @param nodesToBeValidated
     *         - The list on node associated to the subscription to validate
     */
    private synchronized void scheduleDelayedTaskStatusValidation(final Long subscriptionId, final Set<String> nodesToBeValidated) {
        final ScheduleData scheduleData = subscriptionIdAndInitialDelay.get(subscriptionId) != null ?
                subscriptionIdAndInitialDelay.get(subscriptionId).mergeNodesToBeValidated(nodesToBeValidated).reschedule() :
                new ScheduleData(nodesToBeValidated);
        subscriptionIdAndInitialDelay.put(subscriptionId, scheduleData);
    }

    /**
     * Start a timer to validate the given subscription after a timeout
     *
     * @param subscriptionId
     *         - The ID of the subscription to validate
     * @param nodeToBeValidated
     *         - The node associated to the subscription to validate
     */
    public synchronized void scheduleDelayedTaskStatusValidation(final Long subscriptionId, final String nodeToBeValidated) {
        scheduleDelayedTaskStatusValidation(subscriptionId,
                nodeToBeValidated == null ? new HashSet<String>() : new HashSet<String>(Arrays.asList(nodeToBeValidated)));
    }

    /**
     * Start a timer to validate the given subscription after a timeout
     *
     * @param subscriptionId
     *         - The ID of the subscription to validate
     */
    public synchronized void scheduleDelayedTaskStatusValidation(final Long subscriptionId) {
        scheduleDelayedTaskStatusValidation(subscriptionId, (String) null);
    }

    /**
     * Perform the validation
     */
    @Timeout
    public void validateTaskStatusAdminState() {
        if (subscriptionIdAndInitialDelay.isEmpty()) {
            return;
        }
        final Set<Long> subscriptionIds = subscriptionIdAndInitialDelay.keySet();
        for (final Long subscriptionId : subscriptionIds) {
            if (subscriptionIdAndInitialDelay.get(subscriptionId).delay < timeGenerator.currentTimeMillis()) {
                final ScheduleData scheduleData = subscriptionIdAndInitialDelay.remove(subscriptionId);
                logger.debug("subscriptionId: {} - scheduleData: {}", subscriptionId, scheduleData);
                if (validateSubscription(subscriptionId, scheduleData.getNodesToBeVerified())) {
                    scheduleDelayedTaskStatusValidation(subscriptionId, scheduleData.getNodesToBeVerified());
                }
            }
        }
    }

    private boolean validateSubscription(final Long subscriptionPoId, final Set<String> nodeToBeValidated) {
        try {
            final Subscription subscription = subscriptionReadOperationService.findOneById(subscriptionPoId, false);
            if (subscription == null) {
                logger.debug("Nothing to validate. Subscription with ID {} does not exist!", subscriptionPoId);
                return false;
            }
            taskStatusValidator.validateTaskStatusAndAdminState(subscription, nodeToBeValidated);
            return false;
        } catch (final Exception e) {
            logger.error("Can not validate subscription with id {}. Exception:{}", subscriptionPoId, e.getMessage());
            logger.info("Can not validate subscription with id {}.", subscriptionPoId, e);
        }
        return true;
    }

    private class ScheduleData {
        private final Set<String> nodesToBeValidated;
        private long delay;

        private ScheduleData(final Set<String> nodesToBeValidated) {
            delay = timeGenerator.currentTimeMillis() + TimeUnit.SECONDS.toMillis(8);
            this.nodesToBeValidated = nodesToBeValidated == null ? new HashSet<>() : nodesToBeValidated;
        }

        private ScheduleData() {
            this(null);
        }

        private ScheduleData mergeNodesToBeValidated(final Set<String> nodesToBeValidated) {
            if (nodesToBeValidated != null) {
                this.nodesToBeValidated.addAll(nodesToBeValidated);
            }
            return this;
        }

        private ScheduleData reschedule() {
            delay = timeGenerator.currentTimeMillis() + TimeUnit.SECONDS.toMillis(8);
            return this;
        }

        private Set<String> getNodesToBeVerified() {
            return nodesToBeValidated;
        }

        @Override
        public String toString() {
            return "[delay: " + delay + " - nodesToBeVerified: " + nodesToBeValidated + "]";
        }
    }
}
