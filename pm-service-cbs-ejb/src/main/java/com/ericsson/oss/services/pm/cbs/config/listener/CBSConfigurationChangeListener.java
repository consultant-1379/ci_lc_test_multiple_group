/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.cbs.config.listener;

import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Cbs.PROP_PERIODIC_AUDIT;
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Cbs.PROP_PERIODIC_AUDIT_DELAY_INTERVAL;
import static com.ericsson.oss.pmic.api.constants.ModelledConfigurationConstants.Cbs.PROP_SCHEDULE_INTERVAL;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.CONFIGURATION_CHANGE_NOTIFICATION;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.config.annotation.ConfigurationChangeNotification;
import com.ericsson.oss.itpf.sdk.config.annotation.Configured;
import com.ericsson.oss.services.pm.cbs.scheduler.CBSAuditScheduler;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.common.scheduling.CreateSchedulingServiceTimerException;
import com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants;

/**
 * This class handles the changes done for CBS configuration parameters
 */
@ApplicationScoped
public class CBSConfigurationChangeListener {

    @Inject
    @Configured(propertyName = PROP_SCHEDULE_INTERVAL)
    private long cbsScheduleInterval;

    @Inject
    @Configured(propertyName = PROP_PERIODIC_AUDIT)
    private boolean periodicCbsAudit;

    @Inject
    @Configured(propertyName = PROP_PERIODIC_AUDIT_DELAY_INTERVAL)
    private long cbsAuditDelayInterval;

    @Inject
    private CBSAuditScheduler cbsAuditScheduler;

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    /**
     * This method is used to listen the cbsAuditInterval
     *
     * @param cbsAuditInterval
     *         - Audit Interval for Criteria Based Subscription
     *
     * @throws CreateSchedulingServiceTimerException
     *         - thrown if timer cannot be created
     */
    void listenForCbsAuditScheduleInterval(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_SCHEDULE_INTERVAL) final long cbsAuditInterval)
            throws CreateSchedulingServiceTimerException {
        log(PROP_SCHEDULE_INTERVAL, cbsScheduleInterval, cbsAuditInterval);
        final long newCbsScheduleTimerInterval = cbsAuditInterval * TimeConstants.ONE_MINUTE_IN_MILLISECONDS;
        final long oldCbsScheduleTimerInterval = cbsScheduleInterval * TimeConstants.ONE_MINUTE_IN_MILLISECONDS;
        cbsAuditScheduler.resetIntervalTimer(oldCbsScheduleTimerInterval, newCbsScheduleTimerInterval, this.cbsAuditDelayInterval);
        cbsScheduleInterval = cbsAuditInterval;
    }

    /**
     * This method is used to set the periodicCbsAudit
     *
     * @param periodicCbsAudit
     *         - Boolean Variable used to set the Criteria Based Subscription Audit
     */

    public void listenForcbsEnableFlag(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_PERIODIC_AUDIT) final boolean periodicCbsAudit) {
        log(PROP_PERIODIC_AUDIT, this.periodicCbsAudit, periodicCbsAudit);
        this.periodicCbsAudit = periodicCbsAudit;
    }

    /**
     * This method listens for cbsAuditDelayInterval
     *
     * @param cbsAuditDelayIntervalTime
     *         - Delay interval of the Criteria Based Subscription Audit
     *
     * @throws CreateSchedulingServiceTimerException
     *         - thrown if timer cannot be created
     */
    void listenForCbsAuditDelayInterval(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_PERIODIC_AUDIT_DELAY_INTERVAL) final long cbsAuditDelayIntervalTime)
            throws CreateSchedulingServiceTimerException {
        log(PROP_PERIODIC_AUDIT_DELAY_INTERVAL, this.cbsAuditDelayInterval, cbsAuditDelayIntervalTime);
        final long cbsScheduleTimerInterval = cbsScheduleInterval * TimeConstants.ONE_MINUTE_IN_MILLISECONDS;
        cbsAuditScheduler.resetIntervalTimer(cbsScheduleTimerInterval, cbsScheduleTimerInterval, cbsAuditDelayIntervalTime);
        this.cbsAuditDelayInterval = cbsAuditDelayIntervalTime;
    }

    /**
     * This method returns the CbsAuditDelayInterval
     *
     * @return returns cbsAuditDelayInterval {@link long}
     */
    public long getCbsAuditDelayInterval() {
        return cbsAuditDelayInterval;
    }

    /**
     * This method returns the cbsScheduleInterval in minutes
     *
     * @return returns cbsScheduleInterval {@link long}
     */

    public long getCbsScheduleInterval() {
        return cbsScheduleInterval;
    }

    /**
     * returns boolean value for periodic cbs audit
     *
     * @return {@link boolean} true to perform periodic audit else false
     */
    public boolean isPeriodicCbsAudit() {
        return periodicCbsAudit;
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
