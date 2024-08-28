/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.task.qualifier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.inject.Qualifier;

import com.ericsson.oss.pmic.dto.subscription.Subscription;

/**
 * The Subscription task status validation interface.
 */
@Qualifier
@Retention(RUNTIME)
@Target({TYPE, FIELD, PARAMETER})
public @interface SubscriptionTaskStatusValidation {

    /**
     * Gets Subscription type class.
     *
     * @return the subscription class
     */
    Class<? extends Subscription> subscriptionType() default Subscription.class;
}
