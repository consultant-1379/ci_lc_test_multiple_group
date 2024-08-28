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

import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_FILE_RECOVERY_HOURS;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_INTERMEDIATE_SCHEDULED_RECOVERY_TIME;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_SCHEDULED_RECOVERY_TIME;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.SCHEDULED_FILE_RECOVERY;
import static com.ericsson.oss.services.pm.model.PMCapability.SupportedRecoveryTypes.SCHEDULED_RECOVERY;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Singleton;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.sdk.config.annotation.ConfigurationChangeNotification;
import com.ericsson.oss.itpf.sdk.config.annotation.Configured;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;
import com.ericsson.oss.services.pm.collection.recovery.util.DstUtil;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.common.constants.TimeConstants;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.config.listener.AbstractConfigurationChangeListener;
import com.ericsson.oss.services.pm.instrumentation.ExtendedFileCollectionInstrumentation;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * Scheduled recovery bean.
 */
@Singleton
public class ScheduledRecovery extends AbstractConfigurationChangeListener {
    private static final Logger logger = LoggerFactory.getLogger(ScheduledRecovery.class);
    public static final String SCHEDULED_RECOVERY_TIMER = "Scheduled recovery timer";
    private static final String TIME_VALIDATION_REGEX = "([01]?\\d|2[0-3])(:([0-5]?\\d))?(:([0-5]?\\d))?";
    private static final int MAX_TIMER_PERIOD_IN_HOUR = 24;

    @Inject
    private DstUtil dstUtil;
    @Inject
    private NodeService nodeService;
    @Inject
    private TimerService timerService;
    @Inject
    private RecoveryHandler recoveryHandler;
    @Inject
    private SupportedRopTimes supportedRopTimes;
    @Inject
    private MembershipListener membershipListener;
    @Inject
    private ExtendedFileCollectionInstrumentation extendedFileCollectionInstrumentation;

    @Inject
    @Configured(propertyName = PROP_INTERMEDIATE_SCHEDULED_RECOVERY_TIME)
    private int intermediateScheduledRecoveryTime;
    private int intermediateRecoveryTimePeriodInHour = MAX_TIMER_PERIOD_IN_HOUR;

    @Inject
    @Configured(propertyName = PROP_FILE_RECOVERY_HOURS)
    private Integer fileRecoveryHoursInfo;


    @Inject
    @Configured(propertyName = PROP_SCHEDULED_RECOVERY_TIME)
    private String scheduledRecoveryTime;

    private Timer scheduledRecoveryTimer;

    @PostConstruct
    public void initialize() {
        updateTimerPeriodInHour();
    }

    /**
     * Create recovery scheduler
     *
     * @return the future boolean value
     */
    @Asynchronous
    public Future<Boolean> createRecoveryScheduler() {
        final TimerConfig timerConfig = new TimerConfig(SCHEDULED_RECOVERY_TIMER, false);
        cancelTimers();
        final Calendar calendar = getCalendarWithInitialExpirationForRecovery();
        scheduledRecoveryTimer =
                timerService.createIntervalTimer(calendar.getTime(), TimeUnit.HOURS.toMillis(intermediateRecoveryTimePeriodInHour), timerConfig);
        logger.info("Created timer for scheduled recovery to first run at {} ", calendar.getTime());
        return new AsyncResult<>(true);
    }

    /**
     * Start recovery.
     */
    @Timeout
    public void startRecovery() {
        if (membershipListener.isMaster()) {
            systemRecorder.eventCoarse(SCHEDULED_FILE_RECOVERY, "All Active Subscriptions", "Initiating scheduled file recovery");
            final boolean isIntermediateScheduledRecoveryTimeout = isIntermediateScheduledRecoveryTimeout();
            final Set<Integer> intermediateScheduledRecoveryRop = supportedRopTimes.getIntermediateRecoverySupportedRopList();
            final Set<ProcessRequestVO> intermediateProcessRequests = getProcessRequestSetForRecovery(intermediateRecoveryTimePeriodInHour,
                    intermediateScheduledRecoveryRop.toArray(new Integer[intermediateScheduledRecoveryRop.size()]));
            performRecovery(intermediateProcessRequests, intermediateRecoveryTimePeriodInHour);
            if (!isIntermediateScheduledRecoveryTimeout) {
                final Set<Integer> scheduledRecoveryRop = supportedRopTimes.getRecoverySupportedRopList();
                scheduledRecoveryRop.removeAll(intermediateScheduledRecoveryRop);
                final Set<ProcessRequestVO> processRequests =
                        getProcessRequestSetForRecovery(fileRecoveryHoursInfo,
                                scheduledRecoveryRop.toArray(new Integer[scheduledRecoveryRop.size()]));
                performRecovery(processRequests, fileRecoveryHoursInfo);
            }
        }
        if (dstUtil.observesDSTChange(LocalDate.now())) {
            systemRecorder.eventCoarse(SCHEDULED_FILE_RECOVERY, "Scheduled Recovery Reset",
                    "Daylight saving change noticed, so resetting schedule recovery timer");
            resetRecoveryTimer();
        }
    }

    private void performRecovery(final Set<ProcessRequestVO> processRequests, final int recoveryPeriod) {
        logger.debug("Gathering information to recover files missed for processes: {}", processRequests);
        removeProcessRequestForNodesWithScheduledRecoveryNotSupported(processRequests);
        final Set<ProcessRequestVO> processRequestsToRecover = new HashSet<>();
        processRequests.forEach(processRequest -> {
            final boolean isRopToRecover = ProcessType.STATS.name().equals(processRequest.getProcessType());
            final Long ropsToRecover = ropsToRecover(processRequest.getRopPeriod(), recoveryPeriod);
            logger.debug("ropPeriod: {} - ropsToRecover: {} isRopToRecover: {}", processRequest.getRopPeriod(), ropsToRecover, isRopToRecover);
            if (ropsToRecover > 0 && isRopToRecover) {
                processRequestsToRecover.add(processRequest);
            }
            if (isRopToRecover) {
                recoveryHandler.recoverFilesForNode(processRequest, ropsToRecover, true);
            }
        });
        extendedFileCollectionInstrumentation.scheduledFileRecoveryTaskGroupStarted(processRequestsToRecover);
    }

    private Set<ProcessRequestVO> getProcessRequestSetForRecovery(final int recoveryPeriod, final Integer... rops) {
        logger.debug("Building process requests for all currently active processes recoveryPeriod :{}, rops: {}", recoveryPeriod, rops);
        final Set<ProcessRequestVO> requests =
                recoveryHandler.buildProcessRequestForAllActiveProcess(rops);
        logger.debug("Found {} currently active process requests for scheduled recovery", requests.size());
        recoveryHandler.appendProcessRequestForAllRemovedProcesses(requests, recoveryPeriod, rops);
        logger.debug("Found {} total process request (active and deactive nodes) for scheduled recovery for rops: {}", requests.size(), rops);
        logger.debug("Attempting to recover files missed in the last {} hours", recoveryPeriod);
        return requests;
    }

    private void removeProcessRequestForNodesWithScheduledRecoveryNotSupported(final Set<ProcessRequestVO> processRequests) {
        for (final Iterator<ProcessRequestVO> iterator = processRequests.iterator(); iterator.hasNext();) {
            final String nodeFdn = iterator.next().getNodeAddress();
            final boolean isScheduledRecoverySupported = nodeService.isRecoveryTypeSupported(nodeFdn, SCHEDULED_RECOVERY.name());
            logger.debug("for nodeFdn : {}, isScheduledRecoverySupported : {} ", nodeFdn, isScheduledRecoverySupported);
            if (!isScheduledRecoverySupported) {
                logger.debug("removing processRequests of nodeFdn : {} as scheduled recovery is not supported this Node", nodeFdn);
                iterator.remove();
            }
        }
    }

    private long ropsToRecover(final int ropTimeInSeconds, final int recoveryPeriod) {
        return TimeUnit.HOURS.toMinutes(recoveryPeriod) / TimeUnit.SECONDS.toMinutes(ropTimeInSeconds);
    }

    private Calendar getCalendarWithInitialExpirationForRecovery() {
        final Calendar calendar = getScheduledRecoveryCalendar();
        if (calendar.getTime().getTime() < System.currentTimeMillis()) {
            calendar.add(Calendar.DATE, 1);
        }
        final Calendar now = Calendar.getInstance();
        while (calendar.compareTo(now) > 0) {
            calendar.add(Calendar.HOUR, -intermediateRecoveryTimePeriodInHour);
        }
        calendar.add(Calendar.HOUR, intermediateRecoveryTimePeriodInHour);
        return calendar;
    }

    private boolean isIntermediateScheduledRecoveryTimeout() {
        final Calendar calendar = getScheduledRecoveryCalendar();
        final long now = System.currentTimeMillis();
        //Please do not use TimeUnit to convert hours in millis since (intermediateRecoveryTimePeriodInHour / 2) could be less than 1 hour
        if (calendar.getTime().getTime() < now) {
            return now - calendar.getTime().getTime() > TimeConstants.ONE_HOUR_IN_MILLISECONDS * intermediateRecoveryTimePeriodInHour / 2;
        } else {
            return calendar.getTime().getTime() - now > TimeConstants.ONE_HOUR_IN_MILLISECONDS * intermediateRecoveryTimePeriodInHour / 2;
        }
    }

    /**
     * @return the fileRecoveryHoursInfo
     */
    public Integer getFileRecoveryHoursInfo() {
        return fileRecoveryHoursInfo;
    }

    /**
     * @return execution time for scheduled recovery
     */
    public Calendar getScheduledRecoveryCalendar() {
        final int hourIndex = 0;
        final int minuteIndex = 1;
        final int secondIndex = 2;
        int hour = 0;
        int minute = 0;
        int second = 0;
        logger.debug("Configured scheduled recovery time is {} ", scheduledRecoveryTime);
        if (scheduledRecoveryTime.matches(TIME_VALIDATION_REGEX)) {
            final String[] hourMinuteSecond = scheduledRecoveryTime.split(":");
            switch (hourMinuteSecond.length) {
                case 1:
                    hour = Integer.valueOf(hourMinuteSecond[hourIndex]);
                    break;
                case 2:
                    hour = Integer.valueOf(hourMinuteSecond[hourIndex]);
                    minute = Integer.valueOf(hourMinuteSecond[minuteIndex]);
                    break;
                case 3:
                    hour = Integer.valueOf(hourMinuteSecond[hourIndex]);
                    minute = Integer.valueOf(hourMinuteSecond[minuteIndex]);
                    second = Integer.valueOf(hourMinuteSecond[secondIndex]);
                    break;
                default:
                    logger.debug("Recovery time is not configured in right format , please check if value is provided in HH:mm:SS or atleast HH " +
                            "value is provided , using default value as {}:{}:{} PM", hour, minute, second);
            }
        }
        final Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, second);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    public void listenForIntermediateScheduledRecoveryTime(
            @Observes @ConfigurationChangeNotification(
                    propertyName = PROP_INTERMEDIATE_SCHEDULED_RECOVERY_TIME) final Integer intermediateSchedRecoveryTime) {
        logChange(PROP_INTERMEDIATE_SCHEDULED_RECOVERY_TIME, intermediateScheduledRecoveryTime, intermediateSchedRecoveryTime);
        intermediateScheduledRecoveryTime = intermediateSchedRecoveryTime;
        updateTimerPeriodInHour();
        resetRecoveryTimer();
    }

    /**
     * Listens for PROP_FILE_RECOVERY_HOURS changes.
     *
     * @param ropInfo
     *            - Record Output Period info to update fileRecoveryHoursInfo object
     */
    public void listenForFileRecoveryHoursInfoChanges(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_FILE_RECOVERY_HOURS) final Integer ropInfo) {
        logChange(PROP_FILE_RECOVERY_HOURS, fileRecoveryHoursInfo, ropInfo);
        fileRecoveryHoursInfo = ropInfo;
        updateTimerPeriodInHour();
        resetRecoveryTimer();
    }

    /**
     * Listens for PROP_SCHEDULED_RECOVERY_TIME changes.
     *
     * @param scheduledFileRecoveryTime
     *            - scheduled time for file recovery
     */
    public void listenForScheduledFileRecoveryTimeChanges(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_SCHEDULED_RECOVERY_TIME) final String scheduledFileRecoveryTime) {
        logChange(PROP_SCHEDULED_RECOVERY_TIME, scheduledRecoveryTime, scheduledFileRecoveryTime);
        scheduledRecoveryTime = scheduledFileRecoveryTime;
        resetRecoveryTimer();
    }

    private void updateTimerPeriodInHour() {
        logger.debug("intermediateScheduledRecoveryTime value: {}", intermediateScheduledRecoveryTime);
        intermediateRecoveryTimePeriodInHour = intermediateScheduledRecoveryTime;
        if (intermediateScheduledRecoveryTime == 0 || fileRecoveryHoursInfo % intermediateScheduledRecoveryTime != 0) {
            int maxConvertedValue = intermediateScheduledRecoveryTime;
            int minConvertedValue = intermediateScheduledRecoveryTime;
            while (maxConvertedValue == 0 || fileRecoveryHoursInfo % maxConvertedValue != 0) {
                maxConvertedValue = Math.min(++maxConvertedValue, fileRecoveryHoursInfo);
            }
            while (minConvertedValue == 0 || fileRecoveryHoursInfo % minConvertedValue != 0) {
                minConvertedValue = Math.max(--minConvertedValue, 1);
            }
            intermediateRecoveryTimePeriodInHour = maxConvertedValue - intermediateScheduledRecoveryTime < intermediateScheduledRecoveryTime
                    - minConvertedValue ? maxConvertedValue : minConvertedValue;
            logger.warn("PIB parameter intermediateScheduledRecoveryTime value [{}] is not a submultiples for {}. Value used will be {}",
                    intermediateScheduledRecoveryTime, fileRecoveryHoursInfo, intermediateRecoveryTimePeriodInHour);
        } else {
            logger.debug("intermediateRecoveryTimePeriodInHour value: {}", intermediateRecoveryTimePeriodInHour);
        }
    }

    /**
     * This method is called when recovery time is reset by operator.
     */
    public void resetRecoveryTimer() {
        if (scheduledRecoveryTimer != null) {
            scheduledRecoveryTimer.cancel();
        }
        createRecoveryScheduler();
    }

    private Timer getTimerFromTimerService() {
        final Collection<Timer> timers = timerService.getTimers();
        for (final Timer timer : timers) {
            if (timer.getInfo().equals(SCHEDULED_RECOVERY_TIMER)) {
                return timer;
            }
        }
        return null;
    }

    private void cancelTimers() {
        try {
            final Timer timer = getTimerFromTimerService();
            if (timer != null) {
                logger.debug("Stopping {}s timer for File collection Recovery", timer.getInfo());
                timer.cancel();
                logger.debug("Stopped {}s ROP  timer for File collection Recovery", timer.getInfo());
            }
        } catch (final Exception e) {
            logger.error("Failed to stop timer for {} Exception {}", SCHEDULED_RECOVERY_TIMER, e);
        }
    }
}
