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
package com.ericsson.oss.services.pm.initiation.config.listener

import spock.lang.Unroll

import javax.ejb.Timer
import javax.ejb.TimerConfig
import javax.ejb.TimerService
import javax.inject.Inject
import java.text.SimpleDateFormat

import com.ericsson.cds.cdi.support.providers.custom.sfwk.PropertiesForTest
import com.ericsson.cds.cdi.support.providers.custom.sfwk.SuppliedProperty
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.services.pm.common.scheduling.IntervalTimerInfo
import com.ericsson.oss.services.pm.initiation.schedulers.SubscriptionAuditorTimer
import com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants
import com.ericsson.oss.services.pm.time.TimeGenerator

class SubscriptionAuditIntervalConfigurationChangeListenerSpec extends CdiSpecification {

    private static final String TIMER_NAME = 'Subscription_Auditor_Timer'

    private static final SimpleDateFormat SDF = new SimpleDateFormat('dd/MM/yyyy HH:mm:ss')
    private static final long INITIAL_TIME = SDF.parse('10/08/2017 12:00:00').time

    private static final String subscriptionAuditScheduleInterval = 'subscriptionAuditScheduleInterval'

    @ImplementationInstance
    TimerService timerService = Mock(TimerService)

    @ImplementationInstance
    Timer timer = Mock(Timer)

    @ImplementationInstance
    TimeGenerator timeGenerator = Mock()

    @Inject
    private SubscriptionAuditorTimer subscriptionAuditorTimer

    @ObjectUnderTest
    private SubscriptionAuditIntervalConfigurationChangeListener subscriptionAuditIntervalConfigurationChangeListener

    def setup() {
        timerService.getTimers() >>> [[], [timer]]
        subscriptionAuditorTimer.createTimerOnStartup()
    }

    @PropertiesForTest(properties = [@SuppliedProperty(name = 'subscriptionAuditScheduleInterval', value = '15')])
    @Unroll
    def 'when valid #pibParam pibParameter is passed to config resource should return correct audit Schedule Interval value2'() {
            long time = INITIAL_TIME
            long oldIntervalMillis = oldValue * TimeConstants.ONE_MINUTE_IN_MILLISECONDS
            long newIntervalMillis = newValue * TimeConstants.ONE_MINUTE_IN_MILLISECONDS
            long expectedDelay = newIntervalMillis - (time % newIntervalMillis)
        given:
            timeGenerator.currentTimeMillis() >> time
            timer.getInfo() >> new IntervalTimerInfo(TIMER_NAME, oldIntervalMillis)
        when: 'pibConfigParam request with interval value #newValue is sent'
            subscriptionAuditIntervalConfigurationChangeListener.listenForActivationDelayInterval(newValue)
            Long newAuditScheduleInterval = subscriptionAuditIntervalConfigurationChangeListener.getSubscriptionAuditScheduleInterval()
        then: 'returned value should be as set in configured.properties and timer for interval #oldValue cancelled timer for #newValue created'
            newAuditScheduleInterval == newValue
            1 * timer.cancel()
            1 * timerService.createIntervalTimer(new Date(expectedDelay + time), newIntervalMillis,
                    { new TimerConfig(new IntervalTimerInfo(TIMER_NAME, newIntervalMillis), false) } as TimerConfig)
        where:
            pibParam                          | oldValue || newValue
            subscriptionAuditScheduleInterval | 15       || 30
    }
}
