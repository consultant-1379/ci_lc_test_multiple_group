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

package com.ericsson.oss.services.pm.cbs.scheduler;

import java.util.Date;

import javax.annotation.PostConstruct;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.services.pm.cbs.config.listener.CBSConfigurationChangeListener;
import com.ericsson.oss.services.pm.cbs.service.api.CBSAuditCoreLocal;
import com.ericsson.oss.services.pm.common.scheduling.CreateSchedulingServiceTimerException;
import com.ericsson.oss.services.pm.common.scheduling.SchedulingService;
import com.ericsson.oss.services.pm.initiation.common.RopUtil;
import com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * CBSAuditScheduler is a class that extends Scheduling service and follows Singleton pattern. Schedules CBS Audit periodically by sending
 * CBSAuditEvent. Interval of sending the CBSAuditEvent is based upon the PIB parameter cbsAuditScheduleInterval in minutes.
 */
@Singleton
@Startup
public class CBSAuditScheduler extends SchedulingService {

    private static final String TIMER_NAME = "CBS_Audit_Scheduler";

    @Inject
    private Logger logger;
    @Inject
    private RopUtil ropUtil;
    @Inject
    private CBSConfigurationChangeListener cbsConfigurationChangeListener;
    @Inject
    private MembershipListener membershipListener;
    @Inject
    private CBSAuditCoreLocal cbsAuditCoreLocal;

    @Override
    @Timeout
    @LogProfiler(name = "Triggering periodic CBS check", ignoreExecutionTimeLowerThan = 1L)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void onTimeout() {
        logger.info("Scheduler triggering Periodic CBS check on Subscriptions, and Periodic flag is {}",
                cbsConfigurationChangeListener.isPeriodicCbsAudit());
        if (membershipListener.isMaster() && cbsConfigurationChangeListener.isPeriodicCbsAudit()) {
            logger.info("Received Criteria Based Subscription (CBS) audit event. Proceeding with audit.");
            cbsAuditCoreLocal.auditSubscriptions();
        }
    }

    @Override
    @Lock(LockType.READ)
    public String getTimerName() {
        return TIMER_NAME;
    }

    /**
     * This method constructs the Scheduler post construction of CBSAuditScheduler.
     * <p>
     * NOTE: To avoid overall with other subscriptionAudit audit, System defined Subscription audit and Scanner polling overlap.
     * CBS audit will start 5min after start of 15min rop.
     * By Default CBS audit will at 00:10:00, 00:25:00, 00:40:00, 00:55:00
     * .
     * </p>
     * @throws CreateSchedulingServiceTimerException
     *             - Thrown when cannot create a new timer as a timer already exists for this interval
     */
    @PostConstruct
    public void scheduleJobs() throws CreateSchedulingServiceTimerException {
        final long cbsAuditScheduleInterval = cbsConfigurationChangeListener.getCbsScheduleInterval() * TimeConstants.ONE_MINUTE_IN_MILLISECONDS;
        final Date initialExpiration = ropUtil.getInitialExpirationTime(10);
        createIntervalTimer(initialExpiration, cbsAuditScheduleInterval, false);
        logger.info("Periodic CBS Audit Flag value {}", cbsConfigurationChangeListener.isPeriodicCbsAudit());
    }

}
