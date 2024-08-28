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

package com.ericsson.oss.services.pm.initiation.config.listener

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.CONFIGURATION_CHANGE_NOTIFICATION
import static com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants.*;
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.SYMLINK_ONE_MINUTE_TIMER
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER

import com.ericsson.oss.itpf.sdk.config.annotation.Configured
import com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants
import com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.Statistical
import com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.StatsSymlinks
import com.ericsson.oss.services.pm.deletion.schedulers.StatisticalSymlinkDeletionSchedulerBean

import javax.ejb.Timer
import javax.ejb.TimerService
import javax.inject.Inject

import spock.lang.Shared
import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest

class StatisticalSymLinkChangeListenerSpec extends ConfigChangeListenerParent {

    @Shared def timersDuration

    @ObjectUnderTest
    StatisticalSymlinkDeletionSchedulerBean statisticalSymlinkDeletionSchedulerBean

    @ImplementationInstance
    Timer timer = Mock()

    def timersCreated = []
    def timersDurationList = []

    @ImplementationInstance
    TimerService timerService = [
            getTimers          : { [] },
            createIntervalTimer: { a, b, c ->
                timersCreated += c;
                timersDurationList += b;
                return timer},
            createTimer        : { a, b -> timersCreated += b; null }
    ] as TimerService

    def setup() {
        timersDuration = [:]
        findTimerForStatsSymlinks()
    }

    @Unroll
    def 'Should update the statistical symlink deletion period for five minutes and above rop'() {
        expect: 'that the correct default value is set'
            statisticalSymlinkDeletionSchedulerBean.pmicSymbolicLinkDeletionIntervalInMinutes == 15
            assert timersDuration[SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER] == 15 * ONE_MINUTE_IN_MILLISECONDS

        when: 'the change listener is activated'
            statisticalSymlinkDeletionSchedulerBean.listenForPmicSymbolicLinkDeletionIntervalInMinutesChanges(value)

        then: 'the value is updated'
            statisticalSymlinkDeletionSchedulerBean.pmicSymbolicLinkDeletionIntervalInMinutes== value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                    StatsSymlinks.PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES,
                    "${StatsSymlinks.PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES} parameter value changed, old value = '15' new value = '${value}'")

        and: 'the timer is reset'
            findTimerForStatsSymlinks()
            assert timersDuration[SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER] == value * ONE_MINUTE_IN_MILLISECONDS

        where:
            value << [1, 100, -1, 1_000_000]
    }

    @Unroll
    def 'Should update the statistical symlink deletion period for one minute rop'() {
        expect: 'that the correct default value is set'
            statisticalSymlinkDeletionSchedulerBean.pmicSymbolicLinkDeletionIntervalInMinutesFor1MinRop == 15
            assert timersDuration[SYMLINK_ONE_MINUTE_TIMER] == 15 * ONE_MINUTE_IN_MILLISECONDS

        when: 'the change listener is activated'
            statisticalSymlinkDeletionSchedulerBean.listenForPmicSymbolicLinkDeletionIntervalInMinutesFor1MinRopChanges(value)

        then: 'the value is updated'
            statisticalSymlinkDeletionSchedulerBean.pmicSymbolicLinkDeletionIntervalInMinutesFor1MinRop == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                    StatsSymlinks.PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES_FOR_1_MIN_ROP,
                    "${StatsSymlinks.PROP_PMIC_SYMLINK_DELETION_INTERVAL_IN_MINUTES_FOR_1_MIN_ROP} parameter value changed, old value = '15' new value = '${value}'")

        and: 'the timer is reset'
            findTimerForStatsSymlinks()
            assert timersDuration[SYMLINK_ONE_MINUTE_TIMER] == value * ONE_MINUTE_IN_MILLISECONDS

        where:
            value << [1, 100, -1, 1_000_000]
    }

    @Unroll
    def 'Should update the statistical symlink retention period for five minutes and above rop'() {
        expect: 'that the correct default value is set'
            statisticalSymlinkDeletionSchedulerBean.pmicSymbolicLinkRetentionPeriodInMinutes == 1440

        when: 'the change listener is activated'
            statisticalSymlinkDeletionSchedulerBean.listenForPmicSymbolicLinkRetentionPeriodInMinutesChanges(value)

        then: 'the value is updated'
            statisticalSymlinkDeletionSchedulerBean.pmicSymbolicLinkRetentionPeriodInMinutes == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                    StatsSymlinks.PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES,
                    "${StatsSymlinks.PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES} parameter value changed, old value = '1440' new value = '${value}'")

        where:
            value << [1, 100, -1, 1_000_000]
    }

    @Unroll
    def 'Should update the statistical symlink retention period for one minute rop'() {
        expect: 'that the correct default value is set'
            statisticalSymlinkDeletionSchedulerBean.pmicSymbolicLinkRetentionPeriodInMinutesFor1MinRop == 180

        when: 'the change listener is activated'
            statisticalSymlinkDeletionSchedulerBean.listenForPmicSymbolicLinkRetentionPeriodInMinutesFor1MinRopChanges(value)

        then: 'the value is updated'
            statisticalSymlinkDeletionSchedulerBean.pmicSymbolicLinkRetentionPeriodInMinutesFor1MinRop == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                    StatsSymlinks.PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES_FOR_1_MIN_ROP,
                    "${StatsSymlinks.PROP_PMIC_SYMLINK_PMIC_RETENTION_PERIOD_IN_MINUTES_FOR_1_MIN_ROP} parameter value changed, old value = '180' new value = '${value}'")

        where:
            value << [1, 100, -1, 1_000_000]
    }
    @Unroll
    def 'Should update the events symbolic link volume'() {
        expect: 'the initial symbolic link volume to have default value'
            statisticalSymlinkDeletionSchedulerBean.symbolicLinkVolume == 'target/symlinks/'

        when: 'the listener is activated'
            statisticalSymlinkDeletionSchedulerBean.listenForSymbolicLinkVolumeChanges(value)

        then: 'the value is updated'
            statisticalSymlinkDeletionSchedulerBean.symbolicLinkVolume == value

        and: 'correct log is recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           StatsSymlinks.PROP_SYMLINK_VOLUME,
                                           "${StatsSymlinks.PROP_SYMLINK_VOLUME} "
                                           + "parameter value changed, old value = 'target/symlinks/' new value = '${value}'")

        when: 'the listener is activated without a value change'
            statisticalSymlinkDeletionSchedulerBean.listenForSymbolicLinkVolumeChanges(value)

        then: 'the correct log is recorded'
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           StatsSymlinks.PROP_SYMLINK_VOLUME,
                                           "${StatsSymlinks.PROP_SYMLINK_VOLUME} parameter was not changed. "
                                           + "Either the new value is the same or the new value was malformed and the change was ignored. "
                                           + "Value is still ${value}")

        where:
            value << ["mnt${File.separator}a", "mnt${File.separator}b", "mnt${File.separator}fff"]
    }

    def findTimerForStatsSymlinks() {
        def index = 0
        timersCreated.each {
            def timerInfo = it.info
            if (SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER.equals(timerInfo) || SYMLINK_ONE_MINUTE_TIMER.equals(timerInfo)) {
                timersDuration[timerInfo] = timersDurationList[index]
            }
            index++
        }
    }
}
