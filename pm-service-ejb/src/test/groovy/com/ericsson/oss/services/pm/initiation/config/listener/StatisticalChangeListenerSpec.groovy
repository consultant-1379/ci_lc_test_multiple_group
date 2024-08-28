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
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.FIFTEEN_MINUTE_AND_ABOVE_TIMER
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.ONE_MINUTE_TIMER
import static com.ericsson.oss.services.pm.initiation.utils.TestConstants.FIVE_MIN_TIMER

import com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.Statistical
import com.ericsson.oss.services.pm.deletion.schedulers.StatisticalFileDeletionSchedulerBean

import javax.ejb.Timer
import javax.ejb.TimerService

import spock.lang.Shared
import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest

class StatisticalChangeListenerSpec extends ConfigChangeListenerParent {

    @Shared def timersDuration

    @ObjectUnderTest
    StatisticalFileDeletionSchedulerBean statisticalFileDeletionSchedulerBean

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
        findTimerForStatistical()
    }

    @Unroll
    def 'Should update the statistical file deletion period for fifteen minutes and above rop'() {
        expect: 'that the correct default value is set'
            statisticalFileDeletionSchedulerBean.pmicStatisticalFileDeletionIntervalInMinutes == 360
            assert timersDuration[FIFTEEN_MINUTE_AND_ABOVE_TIMER] == 360 * ONE_MINUTE_IN_MILLISECONDS

        when: 'the change listener is activated'
            statisticalFileDeletionSchedulerBean.listenForPmicStatisticalFileDeletionIntervalInMinutesChanges(value)

        then: 'the value is updated'
            statisticalFileDeletionSchedulerBean.pmicStatisticalFileDeletionIntervalInMinutes == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                    Statistical.PROP_FILE_DELETION_INTERVAL_IN_MINUTES,
                    "${Statistical.PROP_FILE_DELETION_INTERVAL_IN_MINUTES} parameter value changed, old value = '360' new value = '${value}'")

        and: 'the timer is reset'
            findTimerForStatistical()
            assert timersDuration[FIFTEEN_MINUTE_AND_ABOVE_TIMER] == value * ONE_MINUTE_IN_MILLISECONDS

        where:
            value << [1, 100, -1, 1_000_000]
    }

    @Unroll
    def 'Should update the statistical file deletion period for one minute rop'() {
        expect: 'that the correct default value is set'
            statisticalFileDeletionSchedulerBean.pmicStatisticalFileDeletionIntervalInMinutesFor1MinRop == 15
            assert timersDuration[ONE_MINUTE_TIMER] == 15 * ONE_MINUTE_IN_MILLISECONDS

        when: 'the change listener is activated'
            statisticalFileDeletionSchedulerBean.listenForPmicStatisticalFileDeletionIntervalInMinutesFor1MinRopChanges(value)

        then: 'the value is updated'
            statisticalFileDeletionSchedulerBean.pmicStatisticalFileDeletionIntervalInMinutesFor1MinRop == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                    Statistical.PROP_FILE_DELETION_INTERVAL_IN_MINUTES_FOR_1_MIN_ROP,
                    "${Statistical.PROP_FILE_DELETION_INTERVAL_IN_MINUTES_FOR_1_MIN_ROP} parameter value changed, old value = '15' new value = '${value}'")

        and: 'the timer is reset'
            findTimerForStatistical()
            assert timersDuration[ONE_MINUTE_TIMER] == value * ONE_MINUTE_IN_MILLISECONDS

        where:
            value << [1, 100, -1, 1_000_000]
    }

    @Unroll
    def 'Should update the statistical file deletion period for five minutes rop'() {
        expect: 'that the correct default value is set'
            statisticalFileDeletionSchedulerBean.pmicStatisticalFileDeletionIntervalInMinutesFor5MinRop == 360
            assert timersDuration[FIVE_MIN_TIMER] == 360 * ONE_MINUTE_IN_MILLISECONDS

        when: 'the change listener is activated'
            statisticalFileDeletionSchedulerBean.listenForPmicStatisticalFileDeletionIntervalInMinutesFor5MinRopChanges(value)

        then: 'the value is updated'
            statisticalFileDeletionSchedulerBean.pmicStatisticalFileDeletionIntervalInMinutesFor5MinRop == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                    Statistical.PROP_FILE_DELETION_INTERVAL_IN_MINUTES_FOR_5_MIN_ROP,
                    "${Statistical.PROP_FILE_DELETION_INTERVAL_IN_MINUTES_FOR_5_MIN_ROP} parameter value changed, old value = '360' new value = '${value}'")

        and: 'the timer is reset'
            findTimerForStatistical()
            assert timersDuration[FIVE_MIN_TIMER] == value * ONE_MINUTE_IN_MILLISECONDS

        where:
            value << [1, 100, -1, 1_000_000]
    }

    @Unroll
    def 'Should update the statistical file retention period for fifteen minutes and above rop'() {
        expect: 'that the correct default value is set'
            statisticalFileDeletionSchedulerBean.pmicStatisticalFileRetentionPeriodInMinutes == 4320

        when: 'the change listener is activated'
            statisticalFileDeletionSchedulerBean.listenForPmicStatisticalFileRetentionPeriodInMinutesChanges(value)

        then: 'the value is updated'
            statisticalFileDeletionSchedulerBean.pmicStatisticalFileRetentionPeriodInMinutes == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           Statistical.PROP_FILE_RETENTION_PERIOD_IN_MINUTES,
                                           "${Statistical.PROP_FILE_RETENTION_PERIOD_IN_MINUTES} parameter value changed, old value = '4320' new value = '${value}'")

        where:
            value << [1, 100, -1, 1_000_000]
    }

    @Unroll
    def 'Should update the statistical file retention period for one minute rop'() {
        expect: 'that the correct default value is set'
            statisticalFileDeletionSchedulerBean.pmicStatisticalFileRetentionPeriodInMinutesFor1MinRop == 180

        when: 'the change listener is activated'
            statisticalFileDeletionSchedulerBean.listenForPmicStatisticalFileRetentionPeriodInMinutesFor1MinRopChanges(value)

        then: 'the value is updated'
            statisticalFileDeletionSchedulerBean.pmicStatisticalFileRetentionPeriodInMinutesFor1MinRop == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           Statistical.PROP_FILE_RETENTION_PERIOD_IN_MINUTES_FOR_1_MIN_ROP,
                                           "${Statistical.PROP_FILE_RETENTION_PERIOD_IN_MINUTES_FOR_1_MIN_ROP} parameter value changed, old value = '180' new value = '${value}'")

        where:
            value << [1, 100, -1, 1_000_000]
    }

    @Unroll
    def 'Should update the statistical file retention period for five minute rop'() {
        expect: 'that the correct default value is set'
            statisticalFileDeletionSchedulerBean.pmicStatisticalFileRetentionPeriodInMinutesFor5MinRop == 4320

        when: 'the change listener is activated'
            statisticalFileDeletionSchedulerBean.listenForPmicStatisticalFileRetentionPeriodInMinutesFor5MinRopChanges(value)

        then: 'the value is updated'
            statisticalFileDeletionSchedulerBean.pmicStatisticalFileRetentionPeriodInMinutesFor5MinRop == value
            1 * systemRecorder.eventCoarse(CONFIGURATION_CHANGE_NOTIFICATION,
                                           Statistical.PROP_FILE_RETENTION_PERIOD_IN_MINUTES_FOR_5_MIN_ROP,
                                           "${Statistical.PROP_FILE_RETENTION_PERIOD_IN_MINUTES_FOR_5_MIN_ROP} parameter value changed, old value = '4320' new value = '${value}'")

        where:
            value << [1, 100, -1, 1_000_000]
    }

    def findTimerForStatistical() {
        def index = 0
        timersCreated.each {
            def timerInfo = it.info
            if (FIVE_MIN_TIMER.equals(timerInfo) || ONE_MINUTE_TIMER.equals(timerInfo) || FIFTEEN_MINUTE_AND_ABOVE_TIMER.equals(timerInfo)) {
                timersDuration[timerInfo] = timersDurationList[index]
            }
            index++
        }
    }
}
