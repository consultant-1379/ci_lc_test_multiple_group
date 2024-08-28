/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2016
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.cbs.scheduler

import org.mockito.Mockito

import javax.ejb.TimerConfig
import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.cbs.config.listener.CBSConfigurationChangeListener
import com.ericsson.oss.services.pm.common.scheduling.IntervalTimerInfo
import com.ericsson.oss.services.pm.initiation.common.RopUtil

class CBSAuditSchedulerMockSpec extends SkeletonSpec {
    @ObjectUnderTest
    CBSAuditScheduler auditScheduler
    @Inject
    private RopUtil util;
    @ImplementationInstance
    CBSConfigurationChangeListener configurationChangeListener = Mock(CBSConfigurationChangeListener)
    @ImplementationInstance
    TimerService timerService = Mock(TimerService)
    @ImplementationInstance
    TimeGenerator timer = Mockito.mock(TimeGenerator)

    def setup() {
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
    }

    def 'create timer starts 10th min of 15min rop'() {
        configurationChangeListener.getCbsScheduleInterval() >> 15
        long intervalMillis = 15 * 60000L
        given: 'set current time'
            timer.currentTimeMillis() >> "CBS_Audit_Scheduler"
            final Date initialExpiration = util.getInitialExpirationTime(10);
        when: 'The timer is created'
            auditScheduler.scheduleJobs();

        then: 'Validate polling timer start at end of 15min rop'
            1 * timerService.createIntervalTimer(initialExpiration, intervalMillis,
                { new TimerConfig(new IntervalTimerInfo("CBS_Audit_Scheduler", intervalMillis), false) } as TimerConfig)
    }
}
