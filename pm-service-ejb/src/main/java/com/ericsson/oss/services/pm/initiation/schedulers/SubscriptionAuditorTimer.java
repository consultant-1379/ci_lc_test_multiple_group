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
package com.ericsson.oss.services.pm.initiation.schedulers;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;

import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.common.scheduling.CreateSchedulingServiceTimerException;
import com.ericsson.oss.services.pm.common.scheduling.SchedulingService;
import com.ericsson.oss.services.pm.initiation.common.RopUtil;
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.config.listener.SubscriptionAuditIntervalConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.task.SubscriptionAuditor;
import com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * Handles the execution interval of @SubscriptionAuditor classes
 */
@Stateless
public class SubscriptionAuditorTimer extends SchedulingService {

    private static final String TIMER_NAME = "Subscription_Auditor_Timer";

    @Inject
    private Logger logger;
    @Inject
    private RopUtil ropUtil;
    @Inject
    private SupportedRopTimes supportedRopTimes;
    @Inject
    private MembershipListener membershipChangeListener;
    @Inject
    @Any
    private Instance<SubscriptionAuditor> subscriptionAuditors;
    @Inject
    private ConfigurationChangeListener configurationChangeListener;
    @Inject
    private SubscriptionAuditIntervalConfigurationChangeListener intervalConfigurationChangeListener;

    /**
     * timer will be created on startup
     *
     * @return - returns true if timer is created
     */
    @Asynchronous
    public Future<Boolean> createTimerOnStartup() {
        try {
            scheduleSubscriptionAudit();
            return new AsyncResult<>(true);
        } catch (final Exception exception) {
            logger.error("Exception while creating timer", exception);
            return new AsyncResult<>(false);
        }
    }

    /**
     * Start a timer for the given interval
     * <p>
     * NOTE: To avoid overall with other CBS audit and Scanner polling overlap, By Default Subscription audit will start 3th min before 15min ROP.
     *  Subscription Audit time : 00:03, 00:18, 00:33, 00:48
     * </p>
     *
     * @throws CreateSchedulingServiceTimerException
     *         - Thrown when cannot create a new timer as a timer already exists for this interval
     */
    public void scheduleSubscriptionAudit() throws CreateSchedulingServiceTimerException {
        final Date initialExpiration = ropUtil.getInitialExpirationTime(3);
        final long interval = intervalConfigurationChangeListener.getSubscriptionAuditScheduleInterval() * TimeConstants.ONE_MINUTE_IN_MILLISECONDS;
        createIntervalTimer(initialExpiration, interval, false);
    }

    /**
     * On time out, audit subscriptions.
     */
    @Override
    @Timeout
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @LogProfiler(ignoreExecutionTimeLowerThan = 1L)
    public void onTimeout() {
        if (configurationChangeListener.getPmMigrationEnabled()) {
            logger.info("Subscription audit skipped due to PM Migration is turned on.");
            return;
        }
        logger.info("Beginning subscription audit");
        if (membershipChangeListener.isMaster()) {
            logger.debug("Subscription audit timer expired...");
            for (final SubscriptionAuditor subscriptionAuditor : subscriptionAuditors) {
                try {
                    final List<Long> subscriptionIds = subscriptionAuditor.getActiveSubscriptionIds();
                    subscriptionAuditor.audit(subscriptionIds);
                } catch (final Exception exception) {
                    logger.error("Exception occurred while auditing subscriptions:{}", exception.getMessage());
                    logger.info("Exception occurred while auditing subscriptions", exception);
                }
            }
        }
    }

    @Override
    public String getTimerName() {
        return TIMER_NAME;
    }
}
