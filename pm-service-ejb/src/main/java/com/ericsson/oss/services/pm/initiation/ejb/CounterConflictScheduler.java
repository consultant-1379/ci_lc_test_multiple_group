/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.ejb;

import static com.ericsson.oss.services.pm.common.constants.TimeConstants.ONE_HOUR_IN_MILLISECONDS;

import java.util.List;
import javax.annotation.PostConstruct;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.common.scheduling.CreateSchedulingServiceTimerException;
import com.ericsson.oss.services.pm.common.scheduling.SchedulingService;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * CounterConflictScheduler is a class that extends Scheduling service and follows Singleton pattern. This Audit periodically (every hour) update counter conflict cache.
 */
@Singleton
@Startup
public class CounterConflictScheduler extends SchedulingService {

    private static final String TIMER_NAME = "Counter_Conflict_Scheduler";
    @Inject
    private Logger logger;
    @Inject
    private MembershipListener membershipListener;
    @Inject
    private CounterConflictServiceImpl counterConflictCacheService;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    @Override
    @Lock(LockType.READ)
    public String getTimerName() {
        return TIMER_NAME;
    }

    /**
     * This method constructs the Scheduler post construction of CounterConflictScheduler.
     *
     * @throws CreateSchedulingServiceTimerException
     *     - thrown if timer cannot be created
     */
    @PostConstruct
    public void scheduleJobs() throws CreateSchedulingServiceTimerException {
        createIntervalTimer(getInitialExpiration(ONE_HOUR_IN_MILLISECONDS), ONE_HOUR_IN_MILLISECONDS, false);
        logger.info("Periodic Counter conflict Audit Scheduled successfully.");
    }

    @Override
    @Timeout
    public void onTimeout() {
        if (membershipListener.isMaster()) {
            try {
                logger.info("Triggered Counter conflict Audit");
                final List<Subscription> activeSubscriptions = subscriptionReadOperationService.findAllBySubscriptionTypeAndAdministrationState(
                    new SubscriptionType[]{SubscriptionType.STATISTICAL}, new AdministrationState[]{AdministrationState.ACTIVE}, true);
                for (final Subscription subscription : activeSubscriptions) {
                    counterConflictCacheService.addNodesAndCounters(((StatisticalSubscription) subscription).getNodesFdns(),
                        ((StatisticalSubscription) subscription).getCounters(), subscription.getName());
                }
            } catch (final DataAccessException exception) {
                logger.error("DataAccessException:{}", exception.getMessage());
                logger.info("DataAccessException", exception);
            }
        }
    }
}
