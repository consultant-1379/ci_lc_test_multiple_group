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

package com.ericsson.oss.services.pm.initiation.config.listener;

import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.PROP_SUBSCRIPTION_AUDIT_INTERVAL;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.CONFIGURATION_CHANGE_NOTIFICATION;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.config.annotation.ConfigurationChangeNotification;
import com.ericsson.oss.itpf.sdk.config.annotation.Configured;
import com.ericsson.oss.services.pm.common.scheduling.CreateSchedulingServiceTimerException;
import com.ericsson.oss.services.pm.initiation.schedulers.SubscriptionAuditorTimer;
import com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants;

/**
 * SubscriptionAuditInterval Listener for Configuration parameter (PIB) changes
 */
@ApplicationScoped
public class SubscriptionAuditIntervalConfigurationChangeListener extends AbstractConfigurationChangeListener {

    @Inject
    @Configured(propertyName = PROP_SUBSCRIPTION_AUDIT_INTERVAL)
    private long subscriptionAuditScheduleInterval;

    @Inject
    private SubscriptionAuditorTimer subscriptionAuditorTimer;

    /**
     * This method listens for subscriptionAuditScheduleInterval
     *
     * @param subscriptionAuditScheduleInterval
     *         - Delay interval of the subscription audit
     *
     * @throws CreateSchedulingServiceTimerException
     *         - throw exception if the timer cannot be reset when the config value is modified.
     */
    void listenForActivationDelayInterval(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_SUBSCRIPTION_AUDIT_INTERVAL) final long
                    subscriptionAuditScheduleInterval) throws CreateSchedulingServiceTimerException {
        log(PROP_SUBSCRIPTION_AUDIT_INTERVAL, this.subscriptionAuditScheduleInterval, subscriptionAuditScheduleInterval);
        final long newSubscriptionAuditScheduleInterval = subscriptionAuditScheduleInterval;
        final long oldSubscriptionAuditScheduleInterval = this.subscriptionAuditScheduleInterval;
        subscriptionAuditorTimer.resetIntervalTimer(oldSubscriptionAuditScheduleInterval * TimeConstants.ONE_MINUTE_IN_MILLISECONDS,
                newSubscriptionAuditScheduleInterval * TimeConstants.ONE_MINUTE_IN_MILLISECONDS);
        this.subscriptionAuditScheduleInterval = subscriptionAuditScheduleInterval;
    }

    /**
     * This method returns the activationDelayInterval
     *
     * @return returns subscriptionAuditScheduleInterval {@link long}
     */
    public long getSubscriptionAuditScheduleInterval() {
        return subscriptionAuditScheduleInterval;
    }

    /**
     * This method is used to record the notification
     *
     * @param parameterName
     * @param oldValue
     * @param newValue
     */
    private void log(final String parameterName, final Object oldValue, final Object newValue) {
        systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION, parameterName,
                parameterName + " parameter value changed, old value = '" + oldValue + "' new value = '" + newValue + "'");
    }
}
