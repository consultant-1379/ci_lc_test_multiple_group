/*
 * COPYRIGHT Ericsson 2017
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.task.factories.validator;

import java.util.Collections;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.services.pm.initiation.task.TaskStatusValidator;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;

/**
 * The Subscription task status validator.
 */
@SubscriptionTaskStatusValidation
@ApplicationScoped
public class SubscriptionTaskStatusValidator implements TaskStatusValidator<Subscription> {

    @Inject
    private Logger logger;

    @Inject
    @Any
    private Instance<TaskStatusValidator<Subscription>> taskStatusValidatorSelector;

    @Override
    public TaskStatus getTaskStatus(final Subscription subscription) {
        final TaskStatusValidator<Subscription> taskStatusValidator = getInstance(subscription);
        return taskStatusValidator.getTaskStatus(subscription);
    }

    @Override
    public void validateTaskStatusAndAdminState(final Subscription subscription) {
        final TaskStatusValidator<Subscription> taskStatusValidator = getInstance(subscription);
        taskStatusValidator.validateTaskStatusAndAdminState(subscription);
    }

    @Override
    public void validateTaskStatusAndAdminState(final Subscription subscription, final Set<String> nodesToBeVerified) {
        final TaskStatusValidator<Subscription> taskStatusValidator = getInstance(subscription);
        taskStatusValidator.validateTaskStatusAndAdminState(subscription, nodesToBeVerified);
    }

    @Override
    public void validateTaskStatusAndAdminState(final Subscription subscription, final String nodeToBeVerified) {
        final TaskStatusValidator<Subscription> taskStatusValidator = getInstance(subscription);
        taskStatusValidator.validateTaskStatusAndAdminState(subscription, Collections.singleton(nodeToBeVerified));
    }

    private TaskStatusValidator<Subscription> getInstance(final Subscription subscription) {
        final SubscriptionTaskStatusValidatorAnnotationLiteral selector = new SubscriptionTaskStatusValidatorAnnotationLiteral(
                subscription.getClass());
        final Instance<TaskStatusValidator<Subscription>> selectedInstance = taskStatusValidatorSelector.select(selector);
        if (selectedInstance.isUnsatisfied()) {
            logger.error("Subscription Type: {} from Subscription : {} is not currently supported ", subscription.getClass().getSimpleName(),
                    subscription.getName());
            throw new UnsupportedOperationException("Subscription Type: " + subscription.getClass().getSimpleName() + " from Subscription : "
                    + subscription.getName() + " is not currently supported");
        }
        logger.info("Getting task status for {} {}", subscription.getClass().getSimpleName(), subscription.getId());
        return selectedInstance.get();
    }

    /**
     * The Subscription task status validator annotation.
     */
    @SuppressWarnings("all")
    class SubscriptionTaskStatusValidatorAnnotationLiteral extends AnnotationLiteral<SubscriptionTaskStatusValidation>
            implements SubscriptionTaskStatusValidation {
        private static final long serialVersionUID = 5370297097468178066L;
        private final Class<? extends Subscription> subscriptionType;

        /**
         * Instantiates a new Subscription task status validator annotation.
         *
         * @param subscriptionType
         *         the subscription type
         */
        SubscriptionTaskStatusValidatorAnnotationLiteral(final Class<? extends Subscription> subscriptionType) {
            this.subscriptionType = subscriptionType;
        }

        @Override
        public Class<? extends Subscription> subscriptionType() {
            return subscriptionType;
        }
    }
}
