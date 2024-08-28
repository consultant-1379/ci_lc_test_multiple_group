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

package com.ericsson.oss.services.pm.collection.roptime;

import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_LAG_PERIOD_IN_SECONDS_FOR_15_MIN_AND_ABOVE_ROP;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_LAG_PERIOD_IN_SECONDS_FOR_1_MIN_ROP;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_LAG_PERIOD_IN_SECONDS_FOR_5_MIN_ROP;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.PROP_PMIC_SUPPORTED_ROP_PERIODS;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.Statistical.PROP_MAX_NUMBER_OF_NODES_FOR_FLEX_ROP;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.Statistical.PROP_MAX_NUM_OF_NODES_FOR_1_MIN_ROP;
import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.Statistical.PROP_MAX_NUM_OF_NODES_FOR_5_MIN_ROP;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.sdk.config.annotation.ConfigurationChangeNotification;
import com.ericsson.oss.itpf.sdk.config.annotation.Configured;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.services.pm.initiation.config.event.ConfigurationParameterUpdateEvent;
import com.ericsson.oss.services.pm.initiation.config.listener.AbstractConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.util.RopTime;

/**
 * This class will have a list of all supported ROP times. We will try adding models for supported ROPs and this class will read those models to
 * create a list of supported ROPs.
 * This would help adding/supporting ROPs dynamically.
 * For any ROP to be supported we need below info :
 * 1) ROP period in seconds 2) Delay to collect files
 * E.g. 15 minutes ROP has delay of 5 minutes delay. For as ROP 10:15-10:30 file collection will start at 10:35(5 minutes is the delay time here)
 * Delay is used so that OSS doesn't collect incomplete file or corrupted files. Let the node write file completely with in the delay and then
 * collect.
 *
 * @author erosrob
 */
@ApplicationScoped
public class SupportedRopTimes extends AbstractConfigurationChangeListener {
    private static final Logger logger = LoggerFactory.getLogger(SupportedRopTimes.class);
    private final Map<Long, RopTimeInfo> ropTimeSupported = new HashMap<>();

    @Inject
    @Configured(propertyName = PROP_LAG_PERIOD_IN_SECONDS_FOR_1_MIN_ROP)
    private int pmicLagPeriodInSecondsFor1MinROP;

    @Inject
    @Configured(propertyName = PROP_LAG_PERIOD_IN_SECONDS_FOR_5_MIN_ROP)
    private int pmicLagPeriodInSecondsFor5MinROP;

    @Inject
    @Configured(propertyName = PROP_LAG_PERIOD_IN_SECONDS_FOR_15_MIN_AND_ABOVE_ROP)
    private int pmicLagPeriodInSecondsFor15MinAndAboveROP;

    @Inject
    @Configured(propertyName = PROP_PMIC_SUPPORTED_ROP_PERIODS)
    private String[] pmicSupportedRopPeriods;

    @Inject
    @Configured(propertyName = PROP_MAX_NUMBER_OF_NODES_FOR_FLEX_ROP)
    private int maxNumberOfNodesForFlexRop;

    @Inject
    @Configured(propertyName = PROP_MAX_NUM_OF_NODES_FOR_1_MIN_ROP)
    private int maxNumberOfNodesForOneMinRop;

    @Inject
    @Configured(propertyName = PROP_MAX_NUM_OF_NODES_FOR_5_MIN_ROP)
    private int maxNumberOfNodesForFiveMinRop;

    private final Set<Long> supportedRopPeriodsSet = new HashSet<>();

    @Inject
    private Event<ConfigurationParameterUpdateEvent> event;

    @PostConstruct
    public void initialize() {
        populateSupportedROPTimes();
    }

    /**
     * Gets rop time.
     *
     * @param ropPeriodInSeconds
     *            the record output period in seconds
     * @return the record output period time
     */
    public RopTimeInfo getRopTime(final long ropPeriodInSeconds) {
        return ropTimeSupported.get(ropPeriodInSeconds);
    }

    /**
     * Gets rop time.
     *
     * @param ropPeriodInSeconds
     *            the record output period in seconds
     * @return the record output period time
     */
    public RopTimeInfo getRopTime(final String ropPeriodInSeconds) {
        return getRopTime(Long.valueOf(ropPeriodInSeconds));
    }

    /**
     * Gets supported record output period time list.
     *
     * @return the supported record output period time list
     */
    public Set<Long> getSupportedRopTimeList() {
        return ropTimeSupported.keySet();
    }

    public Set<Integer> getIntermediateRecoverySupportedRopList() {
        final Set<Integer> intermediateRecoverySupportedRop = new HashSet<>();
        for (final Long rop : ropTimeSupported.keySet()) {
            if (isIntermediateRecoverySupported(rop)) {
                intermediateRecoverySupportedRop.add(rop.intValue());
            }
        }
        return intermediateRecoverySupportedRop;
    }

    public Set<Integer> getRecoverySupportedRopList() {
        final Set<Integer> recoverySupported = new HashSet<>();
        for (final Long rop : ropTimeSupported.keySet()) {
            if (isRecoverySupported(rop)) {
                recoverySupported.add(rop.intValue());
            }
        }
        return recoverySupported;
    }

    public static boolean isIntermediateRecoverySupported(final Long ropPeriodInSeconds) {
        final RopPeriod ropPeriod = RopPeriod.fromSeconds(ropPeriodInSeconds.intValue());
        switch (ropPeriod) {
            case TEN_SECONDS:
            case THIRTY_SECONDS:
            case ONE_MIN:
                return false;
            case FIVE_MIN:
            case FIFTEEN_MIN:
                return true;
            case ONE_HOUR:
            case THIRTY_MIN:
            case TWELVE_HOUR:
            case ONE_DAY:
                return false;
            default:
                break;
        }
        return false;
    }

    public static boolean isRecoverySupported(final Long ropPeriodInSeconds) {
        final RopPeriod ropPeriod = RopPeriod.fromSeconds(ropPeriodInSeconds.intValue());
        switch (ropPeriod) {
            case TEN_SECONDS:
            case THIRTY_SECONDS:
            case ONE_MIN:
                return false;
            case FIVE_MIN:
            case FIFTEEN_MIN:
            case ONE_HOUR:
            case THIRTY_MIN:
            case TWELVE_HOUR:
            case ONE_DAY:
                return true;
            default:
                break;
        }
        return false;
    }

    /**
     * Get RopTime for scheduling Single Rop recovery. For 12h ROP recovery is triggerd after 15m. For 24h ROP after 30 min.
     *
     * @param ropPeriodInMilliseconds
     *            rop period in milliseconds
     * @return the requested rop time
     */
    public static RopTime calculateSingleRopRecoveryRopEndTime(final Long ropPeriodInMilliseconds) {
        return calculateSingleRopRecoveryRopEndTime(System.currentTimeMillis(), ropPeriodInMilliseconds);
    }

    public static RopTime calculateSingleRopRecoveryRopEndTime(final long startTime, final Long ropPeriodInMilliseconds) {
        final RopPeriod ropPeriod = RopPeriod.fromMilliseconds(ropPeriodInMilliseconds.intValue());
        switch (ropPeriod) {
            case TEN_SECONDS:
            case THIRTY_SECONDS:
            case ONE_MIN:
                return new RopTime(startTime, ropPeriod.getDurationInSeconds()).getPreviousROPPeriodEndTime();
            case FIVE_MIN:
            case FIFTEEN_MIN:
            case THIRTY_MIN:
            case ONE_HOUR:
                return new RopTime(startTime, ropPeriod.getDurationInSeconds()).getCurrentROPPeriodEndTime();
            case TWELVE_HOUR:
                return new RopTime(startTime, RopPeriod.FIFTEEN_MIN.getDurationInSeconds()).getCurrentROPPeriodEndTime();
            case ONE_DAY:
                return new RopTime(startTime, RopPeriod.THIRTY_MIN.getDurationInSeconds()).getCurrentROPPeriodEndTime();
            default:
                break;
        }
        return null;
    }

    /**
     * Get a list of supported flexible Rop
     *
     * @return the list of flex rops
     */
    public static Set<String> getFlexRopList() {
        final Set<String> flexRopList = new HashSet<>();
        for (final RopPeriod ropPeriod : RopPeriod.values()) {
            if (isFlexRop(ropPeriod)) {
                flexRopList.add(ropPeriod.name());
            }
        }
        return flexRopList;
    }

    /**
     * return if the ropPeriod is flexible Rop
     *
     * @param ropPeriod
     *            rop period as enum
     * @return if the ropPeriod is flexible Rop
     */
    public static boolean isFlexRop(final RopPeriod ropPeriod) {
        switch (ropPeriod) {
            case TEN_SECONDS:
            case THIRTY_SECONDS:
            case ONE_MIN:
            case FIVE_MIN:
            case THIRTY_MIN:
            case ONE_HOUR:
            case TWELVE_HOUR:
                return true;
            default:
                return false;
        }
    }

    /**
     * Check if a check for sending single rop recovery must be done.
     *
     * @param ropPeriodSec
     *            rop period in seconds
     * @param previousEvaluationTime
     *            previous evaluation time in milliseconds
     * @param currentEvaluationTime
     *            previous evaluation time in milliseconds
     * @return the task requests periodic check for provided rop in seconds
     */
    public static boolean isSingleRopRecoveryCheckRequired(final int ropPeriodSec, final long previousEvaluationTime,
            final long currentEvaluationTime) {
        final RopPeriod ropPeriod = RopPeriod.fromSeconds(ropPeriodSec);
        switch (ropPeriod) {
            case TEN_SECONDS:
            case THIRTY_SECONDS:
            case ONE_MIN:
            case FIVE_MIN:
            case FIFTEEN_MIN:
            case ONE_HOUR:
            case THIRTY_MIN:
                return false;
            case TWELVE_HOUR:
                return isSingleRopRecoveryCheckRequired(previousEvaluationTime, currentEvaluationTime,
                        RopPeriod.FIFTEEN_MIN.getDurationInMilliseconds());
            case ONE_DAY:
                return isSingleRopRecoveryCheckRequired(previousEvaluationTime, currentEvaluationTime,
                        RopPeriod.THIRTY_MIN.getDurationInMilliseconds());
            default:
                return false;
        }
    }

    /**
     * Method to validate if given ropPeriod is supported.
     *
     * @param ropPeriodInSeconds
     * @return True when given rop period is supported, else false
     */
    public boolean isRopSupported(final Long ropPeriodInSeconds) {
        return supportedRopPeriodsSet.contains(ropPeriodInSeconds);
    }

    /**
     * returns max number of nodes for Flexible ROP
     *
     * @return {@link int} max number of nodes for Flexible ROP
     */
    public int getMaxNumberOfNodesForFlexRop() {
        return maxNumberOfNodesForFlexRop;
    }

    /**
     * returns max number of nodes for 1 minute Flexible ROP
     *
     * @return {@link int} max number of nodes for 1 minute Flexible ROP
     */
    public int getMaxNumberOfNodesForOneMinRop() {
        return maxNumberOfNodesForOneMinRop;
    }

    /**
     * returns max number of nodes for 5 minutes Flexible ROP
     *
     * @return {@link int} max number of nodes for 5 minutes Flexible ROP
     */
    public int getMaxNumberOfNodesForFiveMinRop() {
        return maxNumberOfNodesForFiveMinRop;
    }

    /**
     * Use this method to read ROP models and populate the list of supported ROPs. Currently hard coding 1 minute and 15 minutes ROPs
     */
    private void populateSupportedROPTimes() {
        // TODO read supported models through models
        logger.debug("Adding supported ROP times");
        handleSupportedRopPeriodSet();
        for (final RopPeriod ropPeriod : RopPeriod.values()) {
            if (isRopSupported(Long.valueOf(ropPeriod.getDurationInSeconds()))) {
                ropTimeSupported.put((long) ropPeriod.getDurationInSeconds(), new RopTimeInfo(ropPeriod.getDurationInSeconds(),
                        getLagForRop(ropPeriod)));
            } else {
                ropTimeSupported.remove((long) ropPeriod.getDurationInSeconds());
            }
        }
        logger.debug("Added ROPs {} to supported list", ropTimeSupported.values());
    }

    private static boolean isSingleRopRecoveryCheckRequired(final long previousEvaluationTime, final long currentEvaluationTime,
            final int singleRopRecoveryDelay) {
        final int previousEvaluation = (int) (previousEvaluationTime / singleRopRecoveryDelay);
        final int currentEvaluation = (int) (currentEvaluationTime / singleRopRecoveryDelay);
        logger.trace("singleRopRecoveryDelay:{} PreviousTime:{} Module:{} -- CurrentTime:{} Module:{}", singleRopRecoveryDelay,
                previousEvaluationTime, previousEvaluation, currentEvaluationTime, currentEvaluation);
        return previousEvaluation != currentEvaluation;
    }

    private void handleSupportedRopPeriodSet() {
        supportedRopPeriodsSet.clear();
        for (final String rop : pmicSupportedRopPeriods) {
            final RopPeriod ropPeriod = RopPeriod.fromString(rop);
            if (ropPeriod != null) {
                supportedRopPeriodsSet.add((long) ropPeriod.getDurationInSeconds());
            }
        }
    }

    private int getLagForRop(final RopPeriod ropPeriod) {
        switch (ropPeriod) {
            case TEN_SECONDS:
            case THIRTY_SECONDS:
            case ONE_MIN:
                return pmicLagPeriodInSecondsFor1MinROP;
            case FIVE_MIN:
                return pmicLagPeriodInSecondsFor5MinROP;
            case FIFTEEN_MIN:
            case ONE_DAY:
            case ONE_HOUR:
            case THIRTY_MIN:
            case TWELVE_HOUR:
                return pmicLagPeriodInSecondsFor15MinAndAboveROP;
            default:
                break;

        }
        return 0;
    }

    private void updateSupportedRopTimes(final RopTimeInfo ropPeriod) {
        if (ropTimeSupported.containsKey(ropPeriod.getRopTimeInSeconds())) {
            ropTimeSupported.put(ropPeriod.getRopTimeInSeconds(), ropPeriod);
            logger.debug("Adding ROP {} lag to {}", ropPeriod.getRopTimeInSeconds(), ropPeriod.getcollectionDelayInSeconds());
        }
    }

    void listenForFileCollectionLagForOneMinRop(
            @Observes @ConfigurationChangeNotification(
                    propertyName = PROP_LAG_PERIOD_IN_SECONDS_FOR_1_MIN_ROP) final Integer fileCollectionLagForOneMinRop) {
        logChange(PROP_LAG_PERIOD_IN_SECONDS_FOR_1_MIN_ROP, pmicLagPeriodInSecondsFor1MinROP, fileCollectionLagForOneMinRop);
        pmicLagPeriodInSecondsFor1MinROP = fileCollectionLagForOneMinRop;
        updateSupportedRopTimes(new RopTimeInfo(RopPeriod.ONE_MIN.getDurationInSeconds(), fileCollectionLagForOneMinRop));
        final Set<Long> changedRop = new HashSet<>(Arrays.asList(Long.valueOf(RopPeriod.ONE_MIN.getDurationInSeconds())));
        event.fire(new ConfigurationParameterUpdateEvent(PROP_LAG_PERIOD_IN_SECONDS_FOR_1_MIN_ROP, null, changedRop));
    }

    void listenForFileCollectionLagForFiveMinRop(
            @Observes @ConfigurationChangeNotification(
                    propertyName = PROP_LAG_PERIOD_IN_SECONDS_FOR_5_MIN_ROP) final Integer fileCollectionLagForFiveMinRop) {
        logChange(PROP_LAG_PERIOD_IN_SECONDS_FOR_5_MIN_ROP, pmicLagPeriodInSecondsFor5MinROP, fileCollectionLagForFiveMinRop);
        pmicLagPeriodInSecondsFor5MinROP = fileCollectionLagForFiveMinRop;
        updateSupportedRopTimes(new RopTimeInfo(RopPeriod.FIVE_MIN.getDurationInSeconds(), fileCollectionLagForFiveMinRop));
        final Set<Long> changedRop = new HashSet<>(Arrays.asList(Long.valueOf(RopPeriod.FIVE_MIN.getDurationInSeconds())));
        event.fire(new ConfigurationParameterUpdateEvent(PROP_LAG_PERIOD_IN_SECONDS_FOR_5_MIN_ROP, null, changedRop));
    }

    void listenForFileCollectionLagForFifteenMinAndAboveRop(
            @Observes @ConfigurationChangeNotification(
                    propertyName = PROP_LAG_PERIOD_IN_SECONDS_FOR_15_MIN_AND_ABOVE_ROP) final Integer fileCollectionLagForFifteenMinAndAboveRop) {
        logChange(PROP_LAG_PERIOD_IN_SECONDS_FOR_15_MIN_AND_ABOVE_ROP, pmicLagPeriodInSecondsFor15MinAndAboveROP,
                fileCollectionLagForFifteenMinAndAboveRop);
        pmicLagPeriodInSecondsFor15MinAndAboveROP = fileCollectionLagForFifteenMinAndAboveRop;
        updateSupportedRopTimes(
                new RopTimeInfo(RopPeriod.FIFTEEN_MIN.getDurationInSeconds(), fileCollectionLagForFifteenMinAndAboveRop));
        updateSupportedRopTimes(new RopTimeInfo(RopPeriod.THIRTY_MIN.getDurationInSeconds(), fileCollectionLagForFifteenMinAndAboveRop));
        updateSupportedRopTimes(new RopTimeInfo(RopPeriod.ONE_HOUR.getDurationInSeconds(), fileCollectionLagForFifteenMinAndAboveRop));
        updateSupportedRopTimes(
                new RopTimeInfo(RopPeriod.TWELVE_HOUR.getDurationInSeconds(), fileCollectionLagForFifteenMinAndAboveRop));
        updateSupportedRopTimes(new RopTimeInfo(RopPeriod.ONE_DAY.getDurationInSeconds(), fileCollectionLagForFifteenMinAndAboveRop));
        final Set<Long> changedRop = new HashSet<>(Arrays.asList(Long.valueOf(RopPeriod.FIFTEEN_MIN.getDurationInSeconds()),
                Long.valueOf(RopPeriod.THIRTY_MIN.getDurationInSeconds()),
                Long.valueOf(RopPeriod.ONE_HOUR.getDurationInSeconds()),
                Long.valueOf(RopPeriod.TWELVE_HOUR.getDurationInSeconds()),
                Long.valueOf(RopPeriod.ONE_DAY.getDurationInSeconds())));
        event.fire(new ConfigurationParameterUpdateEvent(PROP_LAG_PERIOD_IN_SECONDS_FOR_15_MIN_AND_ABOVE_ROP, null, changedRop));
    }

    public void listenForSupportedRopPeriods(
            @Observes @ConfigurationChangeNotification(
                    propertyName = PROP_PMIC_SUPPORTED_ROP_PERIODS) final String[] supportedRopPeriods) {
        logChange(PROP_PMIC_SUPPORTED_ROP_PERIODS, pmicSupportedRopPeriods, supportedRopPeriods);
        pmicSupportedRopPeriods = supportedRopPeriods;
        final Set<Long> oldSupported = new HashSet<>(supportedRopPeriodsSet);
        populateSupportedROPTimes();
        final Set<Long> newSupported = new HashSet<>(supportedRopPeriodsSet);
        event.fire(new ConfigurationParameterUpdateEvent(PROP_PMIC_SUPPORTED_ROP_PERIODS, oldSupported, newSupported));
    }

    public void listenForMaxNumberOfNodesForFlexRop(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_MAX_NUMBER_OF_NODES_FOR_FLEX_ROP) final int maxNumberOfNodesForFlexRop) {
        logChange(PROP_MAX_NUMBER_OF_NODES_FOR_FLEX_ROP, this.maxNumberOfNodesForFlexRop, maxNumberOfNodesForFlexRop);
        this.maxNumberOfNodesForFlexRop = maxNumberOfNodesForFlexRop;
    }

    public void listenForMaxNumberOfNodesForOneMinRop(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_MAX_NUM_OF_NODES_FOR_1_MIN_ROP) final int maxNumberOfNodesForOneMinRop) {
        logChange(PROP_MAX_NUM_OF_NODES_FOR_1_MIN_ROP, this.maxNumberOfNodesForOneMinRop, maxNumberOfNodesForOneMinRop);
        this.maxNumberOfNodesForOneMinRop = maxNumberOfNodesForOneMinRop;
    }

    public void listenForMaxNumberOfNodesForFiveMinRop(
            @Observes @ConfigurationChangeNotification(propertyName = PROP_MAX_NUM_OF_NODES_FOR_5_MIN_ROP) final int maxNumberOfNodesForFiveMinRop) {
        logChange(PROP_MAX_NUM_OF_NODES_FOR_5_MIN_ROP, this.maxNumberOfNodesForFiveMinRop, maxNumberOfNodesForFiveMinRop);
        this.maxNumberOfNodesForFiveMinRop = maxNumberOfNodesForFiveMinRop;
    }
}
