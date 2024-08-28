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
import java.util.Arrays;
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
import com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.Statistical;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.fls.constants.DataType;

/**
 * Singleton bean responsible for removing files older than the file retention period from NFS share (default is found in PMICModeledConfiguration).
 * If current node is master node, it will execute the performCleanup method a minute after startup, and every interval duration after that (default
 * is found in PMICModeledConfiguration). If the interval duration attribute is changed, then performCleanup will be executed at the end of the
 * current interval, then the timer will be reset with the new interval value.
 */
@Singleton
@Startup
@Lock(LockType.READ)
public class StatisticalFileDeletionSchedulerBean extends AbstractFileDeletionScheduler {

    private static final String FIFTEEN_MINUTE_AND_ABOVE_TIMER = "FIFTEEN_MINUTE_AND_ABOVE_TIMER";
    private static final String ONE_MINUTE_TIMER = "ONE_MINUTE_TIMER";
    private static final String FIVE_MIN_TIMER = "FIVE_MIN_TIMER";

    // file collection directories
    private static final String XML_DIR = "XML";
    private static final String FILE_DIR_FOR_15MIN_ROP = XML_DIR;
    private static final String FILE_DIR_FOR_ASN_1 = "ASN.1";
    private static final String FILE_DIR_FOR_ASN = "asn1";
    private static final String FILE_DIR_FOR_1MIN_ROP = XML_DIR + "/" + DIR_1MIN;
    private static final String FILE_DIR_FOR_5MIN_ROP = XML_DIR + "/" + DIR_5MIN;
    private static final String FILE_DIR_FOR_30MIN_ROP = XML_DIR + "/" + DIR_30MIN;
    private static final String FILE_DIR_FOR_1HOUR_ROP = XML_DIR + "/" + DIR_1HOUR;
    private static final String FILE_DIR_FOR_12HOUR_ROP = XML_DIR + "/" + DIR_12HOUR;
    private static final String FILE_DIR_FOR_24HOUR_ROP = XML_DIR + "/" + DIR_24HOUR;

    private static final RopPeriod[] ROP_LIST = { RopPeriod.ONE_MIN,
        RopPeriod.FIVE_MIN, RopPeriod.FIFTEEN_MIN,
        RopPeriod.THIRTY_MIN, RopPeriod.ONE_HOUR,
        RopPeriod.TWELVE_HOUR, RopPeriod.ONE_DAY };

    // mapping between ROP and timerId
    private static final Map<RopPeriod, String> ROP_TO_TIMERENAME_MAP = new EnumMap<>(RopPeriod.class);

    // list of file collection directories
    private static final String[] DEST_DIR_FOR_15MIN_AND_ABOVE_ROP = {
        FILE_DIR_FOR_ASN_1, FILE_DIR_FOR_ASN, FILE_DIR_FOR_15MIN_ROP,
        FILE_DIR_FOR_30MIN_ROP, FILE_DIR_FOR_1HOUR_ROP, FILE_DIR_FOR_12HOUR_ROP, FILE_DIR_FOR_24HOUR_ROP };
    private static final String[] DEST_DIR_FOR_1MIN_ROP = { FILE_DIR_FOR_1MIN_ROP };
    private static final String[] DEST_DIR_FOR_5MIN_ROP = { FILE_DIR_FOR_5MIN_ROP };
    // mapping between timerId and file collection directories
    private static final Map<String, String[]> DEST_DIR_MAP = new HashMap<>();

    // list of dataTypes
    private static final String[] DATATYPE_FOR_15MIN_AND_ABOVE_ROP = { DataType.STATISTICAL.toString(),
        DataType.STATISTICAL_30MIN.toString(), DataType.STATISTICAL_1HOUR.toString(),
        DataType.STATISTICAL_12HOUR.toString(), DataType.STATISTICAL_24HOUR.toString() };
    private static final String[] DATATYPE_FOR_1MIN_ROP = {
        DataType.STATISTICAL_1MIN.toString() };
    private static final String[] DATATYPE_FOR_5MIN_ROP = {
        DataType.STATISTICAL_5MIN.toString() };
    // mapping between timerId and dataType
    private static final Map<String, String[]> DATATYPE_MAP = new HashMap<>();

    static {
        ROP_TO_TIMERENAME_MAP.put(RopPeriod.ONE_MIN, ONE_MINUTE_TIMER);
        ROP_TO_TIMERENAME_MAP.put(RopPeriod.FIVE_MIN, FIVE_MIN_TIMER);
        ROP_TO_TIMERENAME_MAP.put(RopPeriod.FIFTEEN_MIN, FIFTEEN_MINUTE_AND_ABOVE_TIMER);
        ROP_TO_TIMERENAME_MAP.put(RopPeriod.THIRTY_MIN, FIFTEEN_MINUTE_AND_ABOVE_TIMER);
        ROP_TO_TIMERENAME_MAP.put(RopPeriod.ONE_HOUR, FIFTEEN_MINUTE_AND_ABOVE_TIMER);
        ROP_TO_TIMERENAME_MAP.put(RopPeriod.TWELVE_HOUR, FIFTEEN_MINUTE_AND_ABOVE_TIMER);
        ROP_TO_TIMERENAME_MAP.put(RopPeriod.ONE_DAY, FIFTEEN_MINUTE_AND_ABOVE_TIMER);

        DEST_DIR_MAP.put(FIFTEEN_MINUTE_AND_ABOVE_TIMER, DEST_DIR_FOR_15MIN_AND_ABOVE_ROP);
        DEST_DIR_MAP.put(ONE_MINUTE_TIMER, DEST_DIR_FOR_1MIN_ROP);
        DEST_DIR_MAP.put(FIVE_MIN_TIMER, DEST_DIR_FOR_5MIN_ROP);

        DATATYPE_MAP.put(FIFTEEN_MINUTE_AND_ABOVE_TIMER, DATATYPE_FOR_15MIN_AND_ABOVE_ROP);
        DATATYPE_MAP.put(ONE_MINUTE_TIMER, DATATYPE_FOR_1MIN_ROP);
        DATATYPE_MAP.put(FIVE_MIN_TIMER, DATATYPE_FOR_5MIN_ROP);
    }

    // key is the TimerName
    private final Map<String, Timer> timerMap = new HashMap<>();

    @Inject
    private Logger logger;

    @Inject
    @Configured(propertyName = FileCollectionModelledConfigConstants.Statistical.PROP_FILE_RETENTION_PERIOD_IN_MINUTES)
    private Integer pmicStatisticalFileRetentionPeriodInMinutes;

    @Inject
    @Configured(propertyName = FileCollectionModelledConfigConstants.Statistical.PROP_FILE_RETENTION_PERIOD_IN_MINUTES_FOR_1_MIN_ROP)
    private Integer pmicStatisticalFileRetentionPeriodInMinutesFor1MinRop;

    @Inject
    @Configured(propertyName = FileCollectionModelledConfigConstants.Statistical.PROP_FILE_RETENTION_PERIOD_IN_MINUTES_FOR_5_MIN_ROP)
    private Integer pmicStatisticalFileRetentionPeriodInMinutesFor5MinRop;

    @Inject
    @Configured(propertyName = FileCollectionModelledConfigConstants.Statistical.PROP_FILE_DELETION_INTERVAL_IN_MINUTES)
    private Integer pmicStatisticalFileDeletionIntervalInMinutes;

    @Inject
    @Configured(propertyName = FileCollectionModelledConfigConstants.Statistical.PROP_FILE_DELETION_INTERVAL_IN_MINUTES_FOR_1_MIN_ROP)
    private Integer pmicStatisticalFileDeletionIntervalInMinutesFor1MinRop;

    @Inject
    @Configured(propertyName = FileCollectionModelledConfigConstants.Statistical.PROP_FILE_DELETION_INTERVAL_IN_MINUTES_FOR_5_MIN_ROP)
    private Integer pmicStatisticalFileDeletionIntervalInMinutesFor5MinRop;

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
     * Listens for PROP_FILE_DELETION_INTERVAL_IN_MINUTES changes.
     *
     * @param pmicStatisticalFileDeletionIntervalInMinutes
     *            - file deletion interval in minutes for stats files
     */
    public void listenForPmicStatisticalFileDeletionIntervalInMinutesChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = Statistical.PROP_FILE_DELETION_INTERVAL_IN_MINUTES) final Integer pmicStatisticalFileDeletionIntervalInMinutes) {
        logChange(Statistical.PROP_FILE_DELETION_INTERVAL_IN_MINUTES,
                this.pmicStatisticalFileDeletionIntervalInMinutes, pmicStatisticalFileDeletionIntervalInMinutes);
        this.pmicStatisticalFileDeletionIntervalInMinutes = pmicStatisticalFileDeletionIntervalInMinutes;
        resetTimer(FIFTEEN_MINUTE_AND_ABOVE_TIMER, pmicStatisticalFileDeletionIntervalInMinutes);
    }

    /**
     * Listens for PROP_FILE_DELETION_INTERVAL_IN_MINUTES_FOR_1_MIN_ROP changes.
     *
     * @param pmicStatisticalFileDeletionIntervalInMinutesFor1MinRop
     *            - file deletion interval in minutes for stats files
     */
    public void listenForPmicStatisticalFileDeletionIntervalInMinutesFor1MinRopChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = Statistical.PROP_FILE_DELETION_INTERVAL_IN_MINUTES_FOR_1_MIN_ROP)
                            final Integer pmicStatisticalFileDeletionIntervalInMinutesFor1MinRop) {
        logChange(Statistical.PROP_FILE_DELETION_INTERVAL_IN_MINUTES_FOR_1_MIN_ROP,
                this.pmicStatisticalFileDeletionIntervalInMinutesFor1MinRop, pmicStatisticalFileDeletionIntervalInMinutesFor1MinRop);
        this.pmicStatisticalFileDeletionIntervalInMinutesFor1MinRop = pmicStatisticalFileDeletionIntervalInMinutesFor1MinRop;
        resetTimer(ONE_MINUTE_TIMER, pmicStatisticalFileDeletionIntervalInMinutesFor1MinRop);
    }

    /**
     * Listens for PROP_FILE_DELETION_INTERVAL_IN_MINUTES_FOR_5_MIN_ROP changes.
     *
     * @param pmicStatisticalFileDeletionIntervalInMinutesFor5MinRop
     *            - file deletion interval in minutes for stats files
     */
    public void listenForPmicStatisticalFileDeletionIntervalInMinutesFor5MinRopChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = Statistical.PROP_FILE_DELETION_INTERVAL_IN_MINUTES_FOR_5_MIN_ROP)
                            final Integer pmicStatisticalFileDeletionIntervalInMinutesFor5MinRop) {
        logChange(Statistical.PROP_FILE_DELETION_INTERVAL_IN_MINUTES_FOR_5_MIN_ROP,
                this.pmicStatisticalFileDeletionIntervalInMinutesFor5MinRop, pmicStatisticalFileDeletionIntervalInMinutesFor5MinRop);
        this.pmicStatisticalFileDeletionIntervalInMinutesFor5MinRop = pmicStatisticalFileDeletionIntervalInMinutesFor5MinRop;
        resetTimer(FIVE_MIN_TIMER, pmicStatisticalFileDeletionIntervalInMinutesFor5MinRop);
    }

    /**
     * Listens for PROP_FILE_RETENTION_PERIOD_IN_MINUTES changes.
     *
     * @param pmicStatisticalFileRetentionPeriodInMinutes
     *            - file retention period in minutes for stats files
     */
    public void listenForPmicStatisticalFileRetentionPeriodInMinutesChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = Statistical.PROP_FILE_RETENTION_PERIOD_IN_MINUTES) final Integer pmicStatisticalFileRetentionPeriodInMinutes) {
        logChange(Statistical.PROP_FILE_RETENTION_PERIOD_IN_MINUTES, this.pmicStatisticalFileRetentionPeriodInMinutes,
                pmicStatisticalFileRetentionPeriodInMinutes);
        this.pmicStatisticalFileRetentionPeriodInMinutes = pmicStatisticalFileRetentionPeriodInMinutes;
    }

    /**
     * Listens for PROP_FILE_RETENTION_PERIOD_IN_MINUTES_FOR_1_MIN_ROP changes.
     *
     * @param pmicStatisticalFileRetentionPeriodInMinutesFor1MinRop
     *            - file retention period in minutes for stats files
     */
    public void listenForPmicStatisticalFileRetentionPeriodInMinutesFor1MinRopChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = Statistical.PROP_FILE_RETENTION_PERIOD_IN_MINUTES_FOR_1_MIN_ROP)
                            final Integer pmicStatisticalFileRetentionPeriodInMinutesFor1MinRop) {
        logChange(Statistical.PROP_FILE_RETENTION_PERIOD_IN_MINUTES_FOR_1_MIN_ROP, this.pmicStatisticalFileRetentionPeriodInMinutesFor1MinRop,
                pmicStatisticalFileRetentionPeriodInMinutesFor1MinRop);
        this.pmicStatisticalFileRetentionPeriodInMinutesFor1MinRop = pmicStatisticalFileRetentionPeriodInMinutesFor1MinRop;
    }

    /**
     * Listens for PROP_FILE_RETENTION_PERIOD_IN_MINUTES_FOR_5_MIN_ROP changes.
     *
     * @param pmicStatisticalFileRetentionPeriodInMinutesFor5MinRop
     *            - file retention period in minutes for stats files
     */
    public void listenForPmicStatisticalFileRetentionPeriodInMinutesFor5MinRopChanges(
            @Observes @ConfigurationChangeNotification(
                    propertyName = Statistical.PROP_FILE_RETENTION_PERIOD_IN_MINUTES_FOR_5_MIN_ROP)
                            final Integer pmicStatisticalFileRetentionPeriodInMinutesFor5MinRop) {
        logChange(Statistical.PROP_FILE_RETENTION_PERIOD_IN_MINUTES_FOR_5_MIN_ROP, this.pmicStatisticalFileRetentionPeriodInMinutesFor5MinRop,
                pmicStatisticalFileRetentionPeriodInMinutesFor5MinRop);
        this.pmicStatisticalFileRetentionPeriodInMinutesFor5MinRop = pmicStatisticalFileRetentionPeriodInMinutesFor5MinRop;
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
        return true;
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
        switch (timerName) {
            case ONE_MINUTE_TIMER:
                return pmicStatisticalFileDeletionIntervalInMinutesFor1MinRop;
            case FIVE_MIN_TIMER:
                return pmicStatisticalFileDeletionIntervalInMinutesFor5MinRop;
            default:
                return pmicStatisticalFileDeletionIntervalInMinutes;
        }
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
        switch (timerName) {
            case ONE_MINUTE_TIMER:
                return pmicStatisticalFileRetentionPeriodInMinutesFor1MinRop;
            case FIVE_MIN_TIMER:
                return pmicStatisticalFileRetentionPeriodInMinutesFor5MinRop;
            default:
                return pmicStatisticalFileRetentionPeriodInMinutes;
        }
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
        final List<String> destDirList = getDestinationDirectories(DEST_DIR_MAP.get(timer.getInfo()));
        logger.debug("DestinationDirectories List = {}", destDirList);
        return destDirList;
    }

    private List<String> getDestinationDirectories(final String[] destinationDirList) {
        final List<String> destinationDirectories = new ArrayList<>();
        for (final String destinationDir : destinationDirList) {
            destinationDirectories.addAll(prepareDestinationDirectoriesForFiles(destinationDir));
        }
        return destinationDirectories;
    }

    /*
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#isDeletionOfEmptyDirectoryRequired()
     */
    @Override
    public boolean isDeletionOfEmptyDirectoryRequired() {
        return true;
    }

    /*
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#systemLogFileDeletion(java.lang.Integer, java.lang.String)
     */
    @Override
    public void logFileDeletion(final Integer retentionPeriodInMinutes, final String destinationDirectory) {
        logger.info("DESTINATION DIRECTORY: {}. Removing Statistical files from NFS Share older than {} minutes", destinationDirectory,
                retentionPeriodInMinutes);
    }

    /*
     * @see com.ericsson.oss.services.pm.deletion.schedulers.AbstractFileDeletionScheduler#getDataTypesForSubscription(javax.ejb.Timer)
     */
    @Override
    public List<String> getDataTypesForSubscription(final Timer timer) {
        return Arrays.asList(DATATYPE_MAP.get(timer.getInfo()));
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
}
