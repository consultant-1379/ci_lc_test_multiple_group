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

package com.ericsson.oss.services.pm.common.startup;

import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_LAG_PERIOD_IN_SECONDS_FOR_15_MIN_AND_ABOVE_ROP;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_LAG_PERIOD_IN_SECONDS_FOR_1_MIN_ROP;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_LAG_PERIOD_IN_SECONDS_FOR_5_MIN_ROP;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_PMIC_SUPPORTED_ROP_PERIODS;
import static com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants.ONE_SECOND_IN_MILLISECONDS;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService;
import com.ericsson.oss.itpf.sdk.core.annotation.EServiceRef;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.services.pm.collection.api.FileCollectionTaskManagerLocal;
import com.ericsson.oss.services.pm.collection.api.FileCollectionTaskSenderLocal;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionLastRopData;
import com.ericsson.oss.services.pm.collection.recovery.ScheduledRecovery;
import com.ericsson.oss.services.pm.collection.recovery.StartupRecovery;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.collection.schedulers.UeTraceFileCollectionTaskManager;
import com.ericsson.oss.services.pm.common.logging.PMICLog;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.config.event.ConfigurationParameterUpdateEvent;
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionScheduleStartupServiceImpl;
import com.ericsson.oss.services.pm.initiation.ha.TransitionHandlerImpl;
import com.ericsson.oss.services.pm.initiation.schedulers.EbsSubscriptionInitiationQueueWatcher;
import com.ericsson.oss.services.pm.initiation.schedulers.InitiationDelayTimer;
import com.ericsson.oss.services.pm.initiation.schedulers.SubscriptionAuditorTimer;
import com.ericsson.oss.services.pm.initiation.schedulers.SubscriptionInitiationManager;
import com.ericsson.oss.services.pm.initiation.schedulers.SubscriptionIntermediaryStateTimer;
import com.ericsson.oss.services.pm.initiation.startup.CounterConflictsBootstrap;
import com.ericsson.oss.services.pm.instrumentation.NodeTypeDataRetriver;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.scheduling.impl.ScannerPollSyncScheduler;
import com.ericsson.oss.services.pm.scheduling.impl.SystemDefinedSubscriptionAuditScheduler;
import com.ericsson.oss.services.pm.upgrade.PostUpgradeHandler;

/**
 * The Startup service.
 */
@Singleton
@Startup
@SuppressWarnings("PMD.TooManyFields")
public class StartupService {

    private static final long EXECUTION_DELAY = 5;

    @Inject
    private ScheduledRecovery scheduledRecovery;

    @Inject
    private StartupRecovery startupRecovery;

    @Inject
    private SubscriptionScheduleStartupServiceImpl subScheduleStartup;

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    @Inject
    private TransitionHandlerImpl transitionHandler;

    @Inject
    private InitiationDelayTimer initiationDelayTimer;

    @Inject
    private CounterConflictsBootstrap counterConflictsBootstrap;

    @Inject
    private Logger log;

    @Resource
    private TimerService timerService;

    @Inject
    private SystemDefinedSubscriptionAuditScheduler sysDefSubscriptionAuditScheduler;

    @Inject
    private FileCollectionTaskManagerLocal fileCollectionTaskManager;

    @Inject
    private UeTraceFileCollectionTaskManager ueTraceTaskManager;

    @Inject
    private FileCollectionTaskSenderLocal taskSender;

    @Inject
    private MembershipListener membershipListener;

    @Inject
    private PostUpgradeHandler postUpgradeHandler;

    @Inject
    private InstrumentationTimer instrumentationTimer;

    @Inject
    private SubscriptionInitiationManager subscriptionInitiationManager;

    @Inject
    private FileCollectionLastRopData fileCollectionLastRopData;

    @EServiceRef
    private DataPersistenceService dataPersistenceService;

    @Inject
    private ScannerPollSyncScheduler scannerPollSyncScheduler;

    @Inject
    private SubscriptionAuditorTimer subscriptionAuditorTimer;

    @Inject
    private NodeService nodeService;

    @Inject
    private SubscriptionIntermediaryStateTimer subscriptionIntermediaryStateTimer;

    @Inject
    private EbsSubscriptionInitiationQueueWatcher ebsSubscriptionInitiationQueueWatcher;

    @Inject
    private NodeTypeDataRetriver nodeTypeDataRetriver;

    @Inject
    private SupportedRopTimes supportedRopTimesBean;

    /**
     * Start all tasks.
     *
     * @throws DataAccessException
     *             - DataAccessException
     */
    @Timeout
    @LogProfiler(name = "Startup service", ignoreExecutionTimeLowerThan = 1L)
    public void startAllTasks() throws DataAccessException {
        log.info("Initiating tasks for startup");
        if (!isDataPersistenceServiceAvailable()) {
            log.warn("DPS is not available yet");
            triggerStartupService();
            return;
        }
        updatePmFunctionCache();
        log.info("Pre phase complete now firing Bootstrap tasks");
        createStartUpTimers();
        performMasterTasks();

        sysDefSubscriptionAuditScheduler.scheduleAudit();
        scheduleRecoveryOnMembershipChange();
        postUpgradeHandler.updateAttributeForUpgrade();
    }

    private void updatePmFunctionCache() {
        final long start = System.currentTimeMillis();
        try {
            nodeService.populatePmFunctionCache();
        } catch (final Exception exception) {
            log.error("Could not retrieve all PmFunction Managed Objects.", exception);
        }
        log.debug("StartupService update pmFunctionCache in {} seconds", TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start));
    }

    private boolean isDataPersistenceServiceAvailable() {
        try {
            dataPersistenceService.getLiveBucket();
            return true;
        } catch (final Exception e) {
            log.debug("Exception thrown while retrieving dataBucket: {}", e.getMessage());
            return false;
        }
    }

    private void createStartUpTimers() {
        final Set<Long> supportedRopTimes = supportedRopTimesBean.getSupportedRopTimeList();
        createInstrumentationTimers(supportedRopTimes);
        subscriptionInitiationManager.createTimerOnStartup();
        createFileCollectionTimers(supportedRopTimes);
        subScheduleStartup.createTimerForMembershipCheck();
        transitionHandler.createTimerForMembershipCheck();
        initiationDelayTimer.createTimerOnStartup();
        scannerPollSyncScheduler.createTimerOnStartup();
        subscriptionAuditorTimer.createTimerOnStartup();
        subscriptionIntermediaryStateTimer.createTimerOnStartup();
        ebsSubscriptionInitiationQueueWatcher.createTimerOnStartup();
    }

    private void performMasterTasks() {
        if (membershipListener.isMaster()) {
            counterConflictsBootstrap.bootstrapActiveCountersCache();
            fileCollectionLastRopData.resyncLocalRecords();
            nodeTypeDataRetriver.nodeTypeFileCreation();
        }
    }

    private void scheduleRecoveryOnMembershipChange() {
        boolean activeJobListRecovered = false;
        final Future<Boolean> activeJobListRecoveredPromise = startupRecovery.createTimerForMembershipCheck();
        try {
            activeJobListRecovered = activeJobListRecoveredPromise.get();
        } catch (final Exception e) {
            log.error("Failed to execute recovery of active job list cache", e);
        }
        log.info("Active job lists recovered? {}", activeJobListRecovered);
        if (activeJobListRecovered) {
            log.info("Creating file recovery scheduler");
            scheduledRecovery.createRecoveryScheduler();
        }
    }

    /**
     * Trigger startup service.
     */
    public void triggerStartupService() {
        log.debug("Creating single action timer for all start up tasks");
        final TimerConfig timerConfig = new TimerConfig("Single action timer for startup recovery", false);
        timerService.createSingleActionTimer(EXECUTION_DELAY * ONE_SECOND_IN_MILLISECONDS, timerConfig);
        log.debug("Created single action timer for all start up tasks {}s execution delay ", EXECUTION_DELAY);
    }

    private void createFileCollectionTimers(final Set<Long> ropTimes) {
        systemRecorder.eventCoarse(PMICLog.Event.CREATE_FILE_COLLECTION_SENDER_TIMER, getClass().getSimpleName(),
                "Creating file collection timers to handle file collection fail-over for rops : {}.", ropTimes);
        for (final Long ropTimeInSeconds : ropTimes) {
            // This is to create timers on non-master instance so that when master instance is down
            // non-master timers can continue file collection.
            log.debug("Just creating file collection timers to handler failover for rop: {}", ropTimeInSeconds);
            fileCollectionTaskManager.createTimer((int) ropTimeInSeconds.longValue());
            taskSender.createTimer((int) ropTimeInSeconds.longValue());
        }
        if (ropTimes.contains(Long.valueOf(RopPeriod.FIFTEEN_MIN.getDurationInSeconds()))) {
            ueTraceTaskManager.startUeTraceFileCollectionTaskManagement(RopPeriod.FIFTEEN_MIN);
        }
    }

    private void stopFileCollectionTimers(final Set<Long> ropTimes) {
        for (final Long ropTimeInSeconds : ropTimes) {
            log.debug("Just stopping file collection for rops {}", ropTimes);
            fileCollectionTaskManager.stopTimer((int) ropTimeInSeconds.longValue(), true);
            taskSender.stopTimer((int) ropTimeInSeconds.longValue());
        }
        if (ropTimes.contains(Long.valueOf(RopPeriod.FIFTEEN_MIN.getDurationInSeconds()))) {
            ueTraceTaskManager.stopUeTraceFileCollectionTaskManagement(RopPeriod.FIFTEEN_MIN);
        }
    }

    private void stopInstrumentationTimers(final Set<Long> ropTimes) {
        for (final Long ropTimeInSeconds : ropTimes) {
            instrumentationTimer.stopTimer(ropTimeInSeconds.intValue());
        }
    }

    private void createInstrumentationTimers(final Set<Long> ropTimes) {
        for (final Long ropTimeInSeconds : ropTimes) {
            instrumentationTimer.createTimer(ropTimeInSeconds.intValue());
        }
    }

    @SuppressWarnings("unchecked")
    public void configParamUpdateEventObserver(@Observes final ConfigurationParameterUpdateEvent configParamUpdateEvent) {
        log.info("Config param update received: {}", configParamUpdateEvent);
        if (PROP_PMIC_SUPPORTED_ROP_PERIODS.equals(configParamUpdateEvent.getConfigParamName())) {
            final Set<Long> newSet = (Set<Long>) configParamUpdateEvent.getNewValue();
            final Set<Long> oldSet = (Set<Long>) configParamUpdateEvent.getOldValue();
            // Remove from newSet the oldSet to find out the rop to be started
            final Set<Long> ropsToBeStarted = new HashSet<>(newSet);
            ropsToBeStarted.removeAll(oldSet);
            // Remove from oldSet the newSet to find out the rop to be stopped
            final Set<Long> ropsToBeStopped = new HashSet<>(oldSet);
            ropsToBeStopped.removeAll(newSet);
            createFileCollectionTimers(ropsToBeStarted);
            createInstrumentationTimers(ropsToBeStarted);
            stopFileCollectionTimers(ropsToBeStopped);
            stopInstrumentationTimers(ropsToBeStopped);
        }
        if (PROP_LAG_PERIOD_IN_SECONDS_FOR_5_MIN_ROP.equals(configParamUpdateEvent.getConfigParamName())) {
            final Set<Long> ropsToBeRestarted = (Set<Long>) configParamUpdateEvent.getNewValue();
            stopInstrumentationTimers(ropsToBeRestarted);
            createInstrumentationTimers(ropsToBeRestarted);
        }
        if (PROP_LAG_PERIOD_IN_SECONDS_FOR_1_MIN_ROP.equals(configParamUpdateEvent.getConfigParamName())) {
            final Set<Long> ropsToBeRestarted = (Set<Long>) configParamUpdateEvent.getNewValue();
            stopInstrumentationTimers(ropsToBeRestarted);
            createInstrumentationTimers(ropsToBeRestarted);
        }
        if (PROP_LAG_PERIOD_IN_SECONDS_FOR_15_MIN_AND_ABOVE_ROP.equals(configParamUpdateEvent.getConfigParamName())) {
            final Set<Long> ropsToBeRestarted = (Set<Long>) configParamUpdateEvent.getNewValue();
            stopInstrumentationTimers(ropsToBeRestarted);
            createInstrumentationTimers(ropsToBeRestarted);
        }
    }
}
