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

import static com.ericsson.oss.pmic.api.time.RopInterval.ONE_MINUTE_IN_MILLISECONDS;

import java.util.concurrent.Future;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.common.scheduling.CreateSchedulingServiceTimerException;
import com.ericsson.oss.services.pm.common.scheduling.SchedulingService;
import com.ericsson.oss.services.pm.initiation.notification.handlers.InitiationTrackerCacheEntryRemovedHandler;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * Timer, every two minutes send the first InitiationData from InitiationQueue for processing.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class EbsSubscriptionInitiationQueueWatcher extends SchedulingService {

    private static final String TIMER_NAME = "EBS_SUBCRIPTION_INITIATION_QUEUE_WATCHER";

    @Inject
    private Logger logger;

    @Inject
    private MembershipListener membershipListener;

    @Inject
    private InitiationTrackerCacheEntryRemovedHandler cacheRemoveHandler;

    /**
     * Start timer to watcher InitiationQueue.
     *
     * @return Boolean
     * If timer was created.
     * @throws CreateSchedulingServiceTimerException
     *         - thrown if timer cannot be created
     */
    @Asynchronous
    public Future<Boolean> createTimerOnStartup() {
        try {
            final long intervalDurationInMilliseconds = 2L * ONE_MINUTE_IN_MILLISECONDS;
            createIntervalTimer(intervalDurationInMilliseconds, false);
            logger.debug("Checking InitiationQueue for blockage every {} minutes", 2);
            return new AsyncResult<>(true);
        } catch (final Exception exception) {
            logger.error("Exception while creating timer", exception);
            return new AsyncResult<>(false);
        }
    }

    /**
     * Method to send initiation request after timer expires.
     */
    @Timeout
    @Override
    public void onTimeout() {
        if (membershipListener.isMaster()) {
            cacheRemoveHandler.checkQueueForBlockage();
        }
    }

    @Override
    public String getTimerName() {
        return TIMER_NAME;
    }
}
