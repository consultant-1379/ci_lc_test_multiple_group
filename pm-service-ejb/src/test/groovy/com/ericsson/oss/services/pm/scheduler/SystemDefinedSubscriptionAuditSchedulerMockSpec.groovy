/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.scheduler

import javax.ejb.TimerConfig
import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.services.pm.common.scheduling.IntervalTimerInfo
import com.ericsson.oss.services.pm.initiation.common.RopUtil
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener
import com.ericsson.oss.services.pm.scheduling.impl.SystemDefinedSubscriptionAuditScheduler
/***
 * This class will test System Defined Subscription creation and deletion.
 */
class SystemDefinedSubscriptionAuditSchedulerMockSpec extends SkeletonSpec {

    @ObjectUnderTest
    SystemDefinedSubscriptionAuditScheduler systemDefinedSubscriptionAuditScheduler
    @ImplementationInstance
    ConfigurationChangeListener configurationChangeListener = Mock(ConfigurationChangeListener)
    @Inject
    RopUtil ropUtil
    @ImplementationInstance
    TimerService timerService = Mock(TimerService)

    def setup() {
        timerService.timers >> []
    }

    def 'create timer starts 8th min of 15 min rop'() {
        configurationChangeListener.getSysDefSubscriptionAuditInterval() >> 15
        long intervalMillis = 15 * 60000L
        given: 'set current time'
            timerService.getTimers() >> []
            Date initialExpiration = ropUtil.getInitialExpirationTime(8)
        when: 'The timer is created'
            systemDefinedSubscriptionAuditScheduler.scheduleAudit()

        then: 'Validate polling timer start at end of 15min rop'
            1 * timerService.createIntervalTimer(initialExpiration, intervalMillis,
                { new TimerConfig(new IntervalTimerInfo("System_Defined_Subscription_Audit_Scheduler", intervalMillis), false) } as TimerConfig)
    }
}
