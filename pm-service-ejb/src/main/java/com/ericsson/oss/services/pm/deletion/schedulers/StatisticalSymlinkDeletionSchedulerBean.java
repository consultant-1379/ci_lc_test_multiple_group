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

package com.ericsson.oss.services.pm.deletion.schedulers;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.CONFIGURATION_CHANGE_NOTIFICATION;
import static com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants.ONE_MINUTE_IN_MILLISECONDS;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.config.annotation.ConfigurationChangeNotification;
import com.ericsson.oss.itpf.sdk.config.annotation.Configured;
import com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants;
import com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.StatsSymlinks;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;

/**
 * Singleton bean responsible for removing files older than the file retention period from NFS share (default is found in PMICModeledConfiguration).
 * If current node is master node, it will execute the performCleanup method a minute after startup, and every interval duration after that (default
 * is found in PMICModeledConfiguration). If the interval duration attribute is changed, then performCleanup will be executed at the end of the
 * current interval, then the timer will be reset with the new interval value.
 */
@Singleton
@Startup
@Lock(LockType.READ)
public class StatisticalSymlinkDeletionSchedulerBean extends AbstractFileDeletionScheduler {

    private static final String SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER = "SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER";
    private static final String SYMLINK_ONE_MINUTE_TIMER = "SYMLINK_ONE_MINUTE_TIMER";

    // symlink directories
    private static final String SYMLINK_DIR_FOR_15MIN_ROP = "";
    private static final String SYMLINK_DIR_FOR_1MIN_ROP = DIR_1MIN;
    private static final String SYMLINK_DIR_FOR_5MIN_ROP = DIR_5MIN;
    private static final String SYMLINK_DIR_FOR_30MIN_ROP = DIR_30MIN;
    private static final String SYMLINK_DIR_FOR_1HOUR_ROP = DIR_1HOUR;
    private static final String SYMLINK_DIR_FOR_12HOUR_ROP = DIR_12HOUR;
    private static final String SYMLINK_DIR_FOR_24HOUR_ROP = DIR_24HOUR;

    private static final RopPeriod[] ROP_LIST = { RopPeriod.ONE_MIN,
        RopPeriod.FIVE_MIN, RopPeriod.FIFTEEN_MIN,
        RopPeriod.THIRTY_MIN, RopPeriod.ONE_HOUR,
        RopPeriod.TWELVE_HOUR, RopPeriod.ONE_DAY };

    // mapping between ROP and timerId
    private static final Map<RopPeriod, String> ROP_TO_TIMERENAME_MAP = new EnumMap<>(RopPeriod.class);

    // list of symlink directories
    private static final String[] SYMLINK_DEST_DIR_FOR_5MIN_AND_ABOVE_ROP = {
        SYMLINK_DIR_FOR_5MIN_ROP, SYMLINK_DIR_FOR_15MIN_ROP, SYMLINK_DIR_FOR_30MIN_ROP,
        SYMLINK_DIR_FOR_1HOUR_ROP, SYMLINK_DIR_FOR_12HOUR_ROP, SYMLINK_DIR_FOR_24HOUR_ROP };
    private static final String[] SYMLINK_DEST_DIR_FOR_1MIN_ROP = { SYMLINK_DIR_FOR_1MIN_ROP };
    private static final Map<String, String[]> SYMLINK_DEST_DIR_MAP = new HashMap<>();

    static {
        ROP_TO_TIMERENAME_MAP.put(RopPeriod.ONE_MIN, SYMLINK_ONE_MINUTE_TIMER);
        ROP_TO_TIMERENAME_MAP.put(RopPeriod.FIVE_MIN, SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER);
        ROP_TO_TIMERENAME_MAP.put(RopPeriod.FIFTEEN_MIN, SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER);
        ROP_TO_TIMERENAME_MAP.put(RopPeriod.THIRTY_MIN, SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER);
        ROP_TO_TIMERENAME_MAP.put(RopPeriod.ONE_HOUR, SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER);
        ROP_TO_TIMERENAME_MAP.put(RopPeriod.TWELVE_HOUR, SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER);
        ROP_TO_TIMERENAME_MAP.put(RopPeriod.ONE_DAY, SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER);

        SYMLINK_DEST_DIR_MAP.put(SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER, SYMLINK_DEST_DIR_FOR_5MIN_AND_ABOVE_ROP);
        SYMLINK_DEST_DIR_MAP.put(SYMLINK_ONE_MINUTE_TIMER, SYMLINK_DEST_DIR_FOR_1MIN_ROP);
    }

    // key is the TimerName
    private final Map<String, Timer> timerMap = new HashMap<>();

    @Inject
    private Logger logger;

    @Inject
    @Configured(propertyName = FileCollectionModelledConfigConstants.StatsSymlinks.PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES)
    private Integer pmicSymbolicLinkRetentionPeriodInMinutes;

    @Inject
    @Configured(propertyName = FileCollectionModelledConfigConstants.StatsSymlinks.PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES_FOR_1_MIN_ROP)
    private Integer pmicSymbolicLinkRetentionPeriodInMinutesFor1MinRop;

    @Inject
    @Configured(propertyName = FileCollectionModelledConfigConstants.StatsSymlinks.PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES)
    private Integer pmicSymbolicLinkDeletionIntervalInMinutes;

    @Inject
    @Configured(propertyName = FileCollectionModelledConfigConstants.StatsSymlinks.PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES_FOR_1_MIN_ROP)
    private Integer pmicSymbolicLinkDeletionIntervalInMinutesFor1MinRop;

    @Inject
    @Configured(propertyName = FileCollectionModelledConfigConstants.StatsSymlinks.PROP_SYMLINK_VOLUME)
    private String symbolicLinkVolume;

    /*
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#createTimerOnStartup()
     */
    @Override
    @PostConstruct
    public void createTimerOnStartup() {
        for (final RopPeriod rop : ROP_LIST) {
            final String timerName = ROP_TO_TIMERENAME_MAP.get(rop);
            logger.info("creating File deletion interval timer with timerName {} for ROP {}", timerName, rop);
            if (!timerMap.containsKey(timerName)) {
                final Integer intervalDurationInMinutes = getDeletionInterval(timerName);
                logger.info("File deletion interval duration for {} is {} minutes", timerName, intervalDurationInMinutes);
                final long intervalDurationInMilliseconds = intervalDurationInMinutes * ONE_MINUTE_IN_MILLISECONDS;
                final TimerConfig timerConfig = new TimerConfig(timerName, false);
                final Timer timer = timerService.createIntervalTimer(ONE_MINUTE_IN_MILLISECONDS, intervalDurationInMilliseconds, timerConfig);
                timerMap.put(timerName, timer);
                logger.info("File deletion interval timer {} for ROP {} CREATED", timerName, rop);
            } else {
                logger.info("File deletion interval timer for ROP {} already exists and it is named {}", rop, timerName);
            }
        }
    }

    /*
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#performCleanup()
     */
    @Override
    @Timeout
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void performCleanup(final Timer timer) {
        super.performCleanup(timer);
    }

    /**
     * Listen for PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES changes.
     *
     * @param pmicSymbolicLinkRetentionPeriodInMinutes
     *         the pmic symbolic link retention period in minutes
     */
    public void listenForPmicSymbolicLinkRetentionPeriodInMinutesChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = StatsSymlinks.PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES)
                        final Integer pmicSymbolicLinkRetentionPeriodInMinutes) {
        logChange(StatsSymlinks.PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES, this.pmicSymbolicLinkRetentionPeriodInMinutes,
                pmicSymbolicLinkRetentionPeriodInMinutes);
        this.pmicSymbolicLinkRetentionPeriodInMinutes = pmicSymbolicLinkRetentionPeriodInMinutes;
    }

    /**
     * Listen for PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES_FOR_1MIN_ROP changes.
     *
     * @param pmicSymbolicLinkRetentionPeriodInMinutesFor1MinRop
     *         the pmic symbolic link retention period in minutes for 1 min rop
     */
    public void listenForPmicSymbolicLinkRetentionPeriodInMinutesFor1MinRopChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = StatsSymlinks.PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES_FOR_1_MIN_ROP)
                        final Integer pmicSymbolicLinkRetentionPeriodInMinutesFor1MinRop) {
        logChange(StatsSymlinks.PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES_FOR_1_MIN_ROP,
                this.pmicSymbolicLinkRetentionPeriodInMinutesFor1MinRop, pmicSymbolicLinkRetentionPeriodInMinutesFor1MinRop);
        this.pmicSymbolicLinkRetentionPeriodInMinutesFor1MinRop = pmicSymbolicLinkRetentionPeriodInMinutesFor1MinRop;
    }

    /**
     * Listen for PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES changes.
     *
     * @param pmicSymbolicLinkDeletionIntervalInMinutes
     *         the pmic symbolic link deletion interval in minutes
     */
    public void listenForPmicSymbolicLinkDeletionIntervalInMinutesChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = StatsSymlinks.PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES)
                        final Integer pmicSymbolicLinkDeletionIntervalInMinutes) {
        logChange(StatsSymlinks.PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES, this.pmicSymbolicLinkDeletionIntervalInMinutes,
                pmicSymbolicLinkDeletionIntervalInMinutes);
        this.pmicSymbolicLinkDeletionIntervalInMinutes = pmicSymbolicLinkDeletionIntervalInMinutes;
        resetTimer(SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER, pmicSymbolicLinkDeletionIntervalInMinutes);
    }

    /**
     * Listen for PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES_FOR_1_MIN_ROP changes.
     *
     * @param pmicSymbolicLinkDeletionIntervalInMinutesFor1MinRop
     *         the pmic symbolic link deletion interval in minutes for 1 min rop
     */
    public void listenForPmicSymbolicLinkDeletionIntervalInMinutesFor1MinRopChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = StatsSymlinks.PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES_FOR_1_MIN_ROP)
                        final Integer pmicSymbolicLinkDeletionIntervalInMinutesFor1MinRop) {
        logChange(StatsSymlinks.PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES_FOR_1_MIN_ROP,
                this.pmicSymbolicLinkDeletionIntervalInMinutesFor1MinRop, pmicSymbolicLinkDeletionIntervalInMinutesFor1MinRop);
        this.pmicSymbolicLinkDeletionIntervalInMinutesFor1MinRop = pmicSymbolicLinkDeletionIntervalInMinutesFor1MinRop;
        resetTimer(SYMLINK_ONE_MINUTE_TIMER, pmicSymbolicLinkDeletionIntervalInMinutesFor1MinRop);
    }

    /**
     * Listen for symbolic link volume changes.
     *
     * @param symbolicLinkVolume
     *            the symbolic link volume
     */
    public void listenForSymbolicLinkVolumeChanges(
            @Observes @ConfigurationChangeNotification(propertyName = FileCollectionModelledConfigConstants.StatsSymlinks.PROP_SYMLINK_VOLUME)
                    final String symbolicLinkVolume) {
        final String valueBefore = this.symbolicLinkVolume;
        this.symbolicLinkVolume = pathValidator.formAndValidatePath(this.symbolicLinkVolume, symbolicLinkVolume);
        if (valueBefore.equals(this.symbolicLinkVolume)) {
            logNoChange(FileCollectionModelledConfigConstants.StatsSymlinks.PROP_SYMLINK_VOLUME, valueBefore);
        } else {
            logChange(FileCollectionModelledConfigConstants.StatsSymlinks.PROP_SYMLINK_VOLUME, valueBefore, this.symbolicLinkVolume);
        }
    }

    /*
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#getSubscriptionType()
     */
    @Override
    public SubscriptionType getSubscriptionType() {
        return SubscriptionType.STATISTICAL;
    }

    /*
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#isDeletionFromFLSRequired()
     */
    @Override
    public boolean isDeletionFromFLSRequired() {
        return false;
    }

    /*
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#isSubscriptionHasMultiptleDataTypes()
     */
    @Override
    public boolean isSubscriptionHasMultiptleDataTypes() {
        return true;
    }

    /*
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#getDeleteionInterval()
     */
    @Override
    public Integer getDeletionInterval() {
        throw new IllegalAccessError("Unsupported method!!!");
    }

    private Integer getDeletionInterval(final String timerName) {
        return SYMLINK_ONE_MINUTE_TIMER.equals(timerName)
                ? pmicSymbolicLinkDeletionIntervalInMinutesFor1MinRop : pmicSymbolicLinkDeletionIntervalInMinutes;
    }

    /*
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#getRetentionPeriod()
     */
    @Override
    public Integer getRetentionPeriod() {
        throw new IllegalAccessError("Unsupported method!!!");
    }

    /*
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#getRetentionPeriod()
     */
    @Override
    public Integer getRetentionPeriod(final Timer timer) {
        return getRetentionPeriod((String) timer.getInfo());
    }

    private Integer getRetentionPeriod(final String timerName) {
        return SYMLINK_ONE_MINUTE_TIMER.equals(timerName)
                ? pmicSymbolicLinkRetentionPeriodInMinutesFor1MinRop : pmicSymbolicLinkRetentionPeriodInMinutes;
    }

    /*
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#getDestinationDirectory()
     */
    @Override
    public List<String> getDestinationDirectories() {
        throw new IllegalAccessError("Unsupported method!!!");
    }

    /*
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#getDestinationDirectory(javax.ejb.Timer)
     */
    @Override
    public List<String> getDestinationDirectories(final Timer timer) {
        final List<String> destDirList = getDestinationDirectoriesForSymLink(timer);
        logger.debug("Symlink destinationDirectories List = {}", destDirList);
        return destDirList;
    }

    /*
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#isDeletionOfEmptyDirectoryRequired()
     */
    @Override
    public boolean isDeletionOfEmptyDirectoryRequired() {
        return false;
    }

    @Override
    public boolean isDeletionOfEmptyDirectoryRequired(final String directory) {
        return !directory.startsWith(symbolicLinkVolume);
    }

    /*
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#systemLogFileDeletion(java.lang.Integer, java.lang.String)
     */
    @Override
    public void logFileDeletion(final Integer retentionPeriodInMinutes, final String destinationDirectory) {
        logger.info("DESTINATION DIRECTORY: {}. Removing Statistical files from NFS Share older than {} minutes", destinationDirectory,
                retentionPeriodInMinutes);
    }

    private synchronized Timer resetTimer(final String timerName, final int newIntervalInMinutes) {
        logger.debug("Resetting deletion interval to {} minutes for {}", newIntervalInMinutes, timerName);
        Timer timer = timerMap.get(timerName);
        timer.cancel();
        timerMap.remove(timerName);
        final long currentIntervalInMilliseconds = newIntervalInMinutes * ONE_MINUTE_IN_MILLISECONDS;

        final TimerConfig timerConfig = new TimerConfig(timerName, false);
        logger.debug("Next deletion of {} will occur in {} minutes", timerName, newIntervalInMinutes);
        timer = timerService.createIntervalTimer(ONE_MINUTE_IN_MILLISECONDS, currentIntervalInMilliseconds, timerConfig);
        timerMap.put(timerName, timer);
        return timer;
    }

    /**
     * Log change.
     *
     * @param parameterName
     *            - name of parameter to be validated
     * @param oldValue
     *            - old value of parameter
     * @param newValue
     *            - new value of parameter
     */
    private void logChange(final String parameterName, final Object oldValue, final Object newValue) {
        systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION, parameterName, parameterName + " parameter value changed, old value = '"
                + oldValue + "' new value = '" + newValue + "'");
    }

    /**
     * Log no change.
     *
     * @param parameterName
     *         - name of parameter to be validated
     * @param oldValue
     *         - old value of parameter
     */
    protected void logNoChange(final String parameterName, final Object oldValue) {
        systemRecorder
                .eventCoarse(
                        CONFIGURATION_CHANGE_NOTIFICATION,
                        parameterName,
                        parameterName
                                + " parameter was not changed. Either the new value is the same or the new value was malformed"
                                + " and the change was ignored. Value is still " + oldValue);
    }

    private List<String> formSymbolicLinkPath(final Timer timer) {
        final String timerName = (String) timer.getInfo();
        final List<String> symbolicLinkPathList = new ArrayList<>();
        for (final String destDir : SYMLINK_DEST_DIR_MAP.get(timerName)) {
            final String symbolicLinkPath = pathValidator.formSymbolicLinkPath(this.symbolicLinkVolume, destDir);
            symbolicLinkPathList.add(symbolicLinkPath);
        }
        logger.debug("symbolicLinkPath List for timer {} is {}", timerName, symbolicLinkPathList);
        return symbolicLinkPathList;
    }

    private List<String> getDestinationDirectoriesForSymLink(final Timer timer) {
        final List<String> symlinkDestinationDirectories = new ArrayList<>();
        final List<String> symlinkDirectories = formSymbolicLinkPath(timer);
        for (final String symlinkDirectory : symlinkDirectories) {
            symlinkDestinationDirectories.addAll(prepareDestinationDirectoriesForSymlink(symlinkDirectory));
        }
        return symlinkDestinationDirectories;
    }
}
