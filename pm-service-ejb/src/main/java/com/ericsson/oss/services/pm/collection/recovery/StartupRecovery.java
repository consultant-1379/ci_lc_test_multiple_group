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

package com.ericsson.oss.services.pm.collection.recovery;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.STARTUP_FILE_RECOVERY;
import static com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants.ONE_HOUR_IN_MILLISECONDS;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import javax.ejb.AsyncResult;
import javax.ejb.Singleton;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.pmjob.PmJob;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.services.pm.collection.api.ProcessTypesAndRopInfo;
import com.ericsson.oss.services.pm.collection.cache.PmFunctionOffErrorNodeCache;
import com.ericsson.oss.services.pm.collection.cache.StartupRecoveryMonitorLocal;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.pmjobs.recovery.PmJobStartupRecovery;
import com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler.ErrorNodeCacheAttributes;
import com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler.ErrorNodeCacheProcessType;
import com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler.ScannerErrorHandler;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.time.FrequencyGenerator;

/**
 * Startup recovery bean.
 */
@Singleton
@SuppressWarnings("PMD.TooManyFields")
public class StartupRecovery {

    private static final String STARTUP_RECOVERY_TIMER = "PM_SERVICE_STARTUP_RECOVERY_SCHEDULER_TIMER";

    @Inject
    private TimerService timerService;
    @Inject
    private MembershipListener membershipListener;
    @Inject
    private FrequencyGenerator frequencyGenerator;
    @Inject
    private ScannerErrorHandler scannerErrorHandler;
    @Inject
    private StartupRecoveryHelper startupRecoveryHelper;
    @Inject
    private PmJobStartupRecovery pmJobStartupRecovery;
    @Inject
    private StartupRecoveryMonitorLocal startupRecoveryMonitor;
    @Inject
    private PmFunctionOffErrorNodeCache pmFunctionOffErrorNodeCache;
    @Inject
    private ConfigurationChangeListener modeledPropertyChangeListener;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private Logger logger;

    private int attempts;

    private boolean isRecoveryDone;

    private Long timeWhenLastCollectionStarted;

    private boolean isScannerHandlingRequired;

    /**
     * Performs startup recovery
     */
    @Timeout
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @LogProfiler(name = "Startup recovery")
    public void checkMembership() {
        logger.debug("StartUpRecovery timer running, checking if membership has changed.");
        startRecovery();
        attempts++;
        if (isRecoveryDone || attempts >= 135) {
            // After 45 minutes stop the membership check for startup recovery, Startup recovery should of happen by now.
            // If you are in here you are a slave node, which means the master would have done startup Recovery.
            // The reason for having this timer is for upgrade, where the new version of pmserv is a slave until other(Old) pmserv is taken down and
            // upgrade.
            // From which the membership will change for the new pmserv (master) and startup recovery can commence and the timer will be cancelled.
            // The reason for having a check for the number of attempts is for HA scenarios, since the slave will also have the timer and we are
            // currently unable to determine
            // if an upgrade or HA scenario is taking place. We are making a assumption that after 45 minutes the upgrade should be completed for
            // pmserv's.
            cancelTimerForMembershipCheck();
        }
    }

    private void startRecovery() {
        try {
            if (membershipListener.isMaster() && !isRecoveryDone) {
                logger.info("Master node, running startup recovery.");

                isScannerHandlingRequired |= startupRecoveryHelper.isCacheEmpty();

                final List<Scanner> activeScanners = startupRecoveryHelper.getActiveScannerWithFileCollectionEnabled();
                logger.info("startup recovery : number of Active scanner : {}", activeScanners.size());
                final List<Scanner> scanners = getActiveScannerWithMediationAutonomyDisabled(activeScanners);
                logger.info("startup recovery : number of non mediationAutonomyEnabled Active scanner : {}", scanners.size());
                final List<Scanner> scannersWithStartUpRecoverySupport = getActiveScannerWitStartUpRecoverySupported(scanners);
                logger.info("startup recovery : number of Start-Up recovery supported Active scanner : {}",
                        scannersWithStartUpRecoverySupport.size());
                final Map<String, ProcessTypesAndRopInfo> nodesWithActiveScanner = getNodesWithActiveProcessInfo(scannersWithStartUpRecoverySupport);

                if (isScannerHandlingRequired) {
                    final List<PmJob> activePmJobs = startupRecoveryHelper.getActivePmJobs();
                    setLastFileCollectionTime();
                    logSystemStartupRecoveryDuration();
                    startupRecoveryHelper.handleActiveScanners(nodesWithActiveScanner, timeWhenLastCollectionStarted);
                    processStoredErrorHandlerRequests();
                    pmJobStartupRecovery.recoverActivePmJobs(activePmJobs);
                }

                startupRecoveryHelper.cleanupCacheForScannerWithFileCollectionNotEnable(nodesWithActiveScanner, scanners);
                isScannerHandlingRequired = false;
                isRecoveryDone = true;
            }
            startupRecoveryMonitor.startupRecoveryDone();
        } catch (final Exception e) {
            isRecoveryDone = false;
            logSystemErrorOnStartupRecovery(e);
        }
    }

    private List<Scanner> getActiveScannerWitStartUpRecoverySupported(final List<Scanner> scanners) {
        final Map<String, Boolean> startUpRecoverySupportedNeTypesTemporaryCache = new HashMap<>();

        for (final Iterator<Scanner> iterator = scanners.iterator(); iterator.hasNext(); ) {
            final String nodeFdn = iterator.next().getNodeFdn();
            final boolean isRecoverySupported =
                    startUpRecoverySupportedNeTypesTemporaryCache.computeIfAbsent(nodeFdn, k -> startupRecoveryHelper.isStartUpRecoverySupported(nodeFdn));
            if (!isRecoverySupported) {
                logger.debug("Removing nodeFdn : {} as start up recovery is not supported", nodeFdn);
                iterator.remove();
            }
        }
        return scanners;
    }

    private List<Scanner> getActiveScannerWithMediationAutonomyDisabled(final List<Scanner> scanners) {
        final Map<String, Boolean> mediationAutonomyTemporaryCache = new HashMap<>();

        for (final Iterator<Scanner> iterator = scanners.iterator(); iterator.hasNext(); ) {
            final String nodeFdn = iterator.next().getNodeFdn();
            final boolean isMediationAutonomyEnabled =
                    mediationAutonomyTemporaryCache.computeIfAbsent(nodeFdn, k -> startupRecoveryHelper.isMediationAutonomyEnabled(nodeFdn));
            if (isMediationAutonomyEnabled) {
                iterator.remove();
            }
        }
        return scanners;
    }

    /**
     * Log system startup recovery duration.
     */
    private void logSystemStartupRecoveryDuration() {
        final Date lastCollectionStartedTime = new Date(timeWhenLastCollectionStarted);
        final Date recoveryStartForFullRecoveryPeriod = new Date(getRecoveryStartTimeForFullRecoveryPeriod());
        final String message = String.format(
                "File Collection startup recovery with lastCollectionStarted : %s time and recovery start time for full recovery period : %s . ",
                lastCollectionStartedTime, recoveryStartForFullRecoveryPeriod);
        systemRecorder.eventCoarse(STARTUP_FILE_RECOVERY, message, "Initiating startup file recovery");
    }

    /**
     * Log system error on startup recovery.
     *
     * @param exception
     *         the exception
     */
    private void logSystemErrorOnStartupRecovery(final Exception exception) {
        if (isScannerHandlingRequired) {
            systemRecorder.eventCoarse(STARTUP_FILE_RECOVERY,
                    "Startup recovery couldn't complete , this may result in partial or no file collection.", "Startup recovery failed");
            logger.error("Startup recovery couldn't complete , this may result in partial or no file collection. {}", exception.getMessage());
            logger.info("Startup recovery couldn't complete , this may result in partial or no file collection.", exception);
        } else {
            systemRecorder.eventCoarse(STARTUP_FILE_RECOVERY, "Startup recovery couldn't clean the unwanted processRequests from cache",
                    "Startup extra processrequest cleaning failed");
            logger.info("Startup recovery couldn't clean the unwanted processRequests from cache,This may result in extra logging and exceptions",
                    exception);
        }
    }

    private void processStoredErrorHandlerRequests() {
        startupRecoveryMonitor.startupRecoveryDone();
        if (membershipListener.isMaster()) {
            final Set<Map<String, Object>> storedRequests = pmFunctionOffErrorNodeCache.removeStoredRequests();
            logger.info("Removed stored request from ErrorNodeCache");
            for (final Map<String, Object> storedRequest : storedRequests) {
                try {
                    scannerErrorHandler.process((ErrorNodeCacheProcessType) storedRequest.get(ErrorNodeCacheAttributes.ERROR_HANDLER_PROCESS_TYPE),
                            storedRequest);
                } catch (final Exception exception) {
                    logger.error("An error occurred while trying to process {}. Continuing", storedRequest);
                }
            }
        }
    }

    /**
     * Create timer for membership check.
     *
     * @return returns the future boolean value
     */
    public Future<Boolean> createTimerForMembershipCheck() {
        logger.info("Creating timer to keep checking membership status and run StartupRecovery if membership changes");
        final TimerConfig timerConfig = new TimerConfig();
        timerConfig.setInfo(STARTUP_RECOVERY_TIMER);
        timerConfig.setPersistent(false);
        final long frequency = frequencyGenerator.getFrequency();
        logger.info("Setting a programmatic timeout for every {} milliseconds from now.", frequency);
        timerService.createIntervalTimer(0, frequency, timerConfig);
        return new AsyncResult<>(true);
    }

    /**
     * This method extract nodes that have active scanners. Collection must be resumed for these nodes after start up.
     */
    private Map<String, ProcessTypesAndRopInfo> getNodesWithActiveProcessInfo(final List<Scanner> activeScanners) {
        final Map<String, ProcessTypesAndRopInfo> nodesWithActiveProcessInfo = new HashMap<>();

        for (final Scanner scanner : activeScanners) {
            final String nodeFdn = scanner.getNodeFdn();
            if (nodesWithActiveProcessInfo.containsKey(nodeFdn)) {
                nodesWithActiveProcessInfo.get(nodeFdn).addRopInfoAndProcessType(scanner.getRopPeriod(), scanner.getProcessType().name());
            } else {
                final ProcessTypesAndRopInfo processTypeAndRop = new ProcessTypesAndRopInfo();
                processTypeAndRop.addRopInfoAndProcessType(scanner.getRopPeriod(), scanner.getProcessType().name());
                nodesWithActiveProcessInfo.put(nodeFdn, processTypeAndRop);
            }
        }
        return nodesWithActiveProcessInfo;
    }

    private void setLastFileCollectionTime() {

        timeWhenLastCollectionStarted = getRecoveryStartTimeForFullRecoveryPeriod();
    }

    /**
     * This method would return start time for recovery in case full recovery period to be recovered. E.g. recovery period is configured for 24 hours
     * so this method will return (current time - 24 hours)
     *
     * @return
     */
    private long getRecoveryStartTimeForFullRecoveryPeriod() {
        return System.currentTimeMillis() - modeledPropertyChangeListener.getStartupFileRecoveryHoursInfo() * ONE_HOUR_IN_MILLISECONDS;
    }

    private void cancelTimerForMembershipCheck() {
        logger.info("Stopping StartUpRecovery timer.");
        final Collection<Timer> timers = timerService.getTimers();
        for (final Timer timer : timers) {
            logger.info("Timer is {}", timer.getInfo());
            if (timer.getInfo().equals(STARTUP_RECOVERY_TIMER)) {

                timer.cancel();

                logger.info("StartUpRecovery Cancelled timer ");
            }
        }
    }
}
