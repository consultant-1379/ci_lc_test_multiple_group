/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.scheduling.impl;

import static com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants.ONE_MINUTE_IN_MILLISECONDS;

import java.util.Date;
import java.util.concurrent.Future;

import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.common.scheduling.CreateSchedulingServiceTimerException;
import com.ericsson.oss.services.pm.common.scheduling.SchedulingService;
import com.ericsson.oss.services.pm.initiation.common.RopUtil;
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.pmjobs.sync.PmJobSyncInitiator;
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionDataCacheWrapper;
import com.ericsson.oss.services.pm.initiation.scanner.polling.ScannerPollingInitiator;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * Master Polling scheduler for Scanner Polling as well as PmJob Sync
 */
@Stateless
public class ScannerPollSyncScheduler extends SchedulingService {

    private static final String TIMER_NAME = "common_polling_syncing";

    @Inject
    private MembershipListener membershipListener;
    @Inject
    private Logger logger;
    @Inject
    private RopUtil ropUtil;
    @Inject
    private SupportedRopTimes supportedRopTimes;
    @Inject
    private PmJobSyncInitiator pmJobSyncInitiator;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;
    @Inject
    private ScannerPollingInitiator scannerPollingInitiator;
    @Inject
    private ConfigurationChangeListener configurationChangeListener;
    @Inject
    private SubscriptionDataCacheWrapper subscriptionDataCacheWrapper;
    /**
     * timer will be created on startup
     *
     * @return - returns true if timer is created
     */
    @Asynchronous
    public Future<Boolean> createTimerOnStartup() {
        try {
            createTimer();
            return new AsyncResult<>(true);
        } catch (final Exception exception) {
            logger.error("Exception while creating timer", exception);
            return new AsyncResult<>(false);
        }
    }

    /**
     * Start a timer for the given interval
     * <p>
     * NOTE: To avoid overall with CBS audit, Subscription audit, System Defined subscription audit, File collection overlap, By default
     * scanner polling start at end of rop.
     * Scanner polling default time - 00:00, 00:15, 00:30, 00:45
     * </p>
     *
     * @throws CreateSchedulingServiceTimerException
     *     - Thrown when cannot create a new timer as a timer already exists for this interval
     */
    public void createTimer() throws CreateSchedulingServiceTimerException {
        final Date initialExpiration = ropUtil.getInitialExpirationTime(0);
        final long scannerPollingInterval = configurationChangeListener.getScannerPollingIntervalMinutes() * ONE_MINUTE_IN_MILLISECONDS;
        createIntervalTimer(initialExpiration, scannerPollingInterval, false);
        logger.info("Scanner Polling timer will triggered at {}", initialExpiration);
    }

    @Override
    @Timeout
    public void onTimeout() {
        if (!dpsAvailabilityStatus.isAvailable()) {
            logger.warn("Failed PMIC scanner Master Polling and PmJob Syncing , Dps not available");
        }
        if (membershipListener.isMaster()) {
            //Remove all subscription from cache before scanner polling
            subscriptionDataCacheWrapper.getCache().removeAll();
            logger.info("PMIC Master scanner Polling and PmJob Syncing started!");
            scannerPollingInitiator.startScannerPolling();
            pmJobSyncInitiator.startPmJobSyncing();
        }
    }

    @Override
    public String getTimerName() {
        return TIMER_NAME;
    }
}
