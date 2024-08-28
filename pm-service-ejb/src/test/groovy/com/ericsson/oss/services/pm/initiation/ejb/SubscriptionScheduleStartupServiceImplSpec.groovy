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

package com.ericsson.oss.services.pm.initiation.ejb

import org.mockito.Mockito

import javax.ejb.AsyncResult
import javax.ejb.Timer
import javax.ejb.TimerConfig
import javax.ejb.TimerService

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.*
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener
import com.ericsson.oss.services.pm.initiation.notification.model.InitiationScheduleModel
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener

class SubscriptionScheduleStartupServiceImplSpec extends SkeletonSpec {

    @ObjectUnderTest
    SubscriptionScheduleStartupServiceImpl startupService

    @ImplementationInstance
    ConfigurationChangeListener configurationChangeListener = Mock()

    @ImplementationInstance
    MembershipListener membershipListener = Mockito.mock(MembershipListener)

    def timersCreated = []
    def timers = []

    def subscriptions = []
    def subscriptionIds = []

    def builders = [StatisticalSubscriptionBuilder.class, EbmSubscriptionBuilder.class, UeTraceSubscriptionBuilder.class, CtumSubscriptionBuilder
            .class, CellTraceSubscriptionBuilder.class, CctrSubscriptionBuilder.class]

    final String subsriptionSchedulerInitTimerName = "PM_SERVICE_SUBSCRIPTION_SCHEDULER_TIMER";

    @ImplementationInstance
    TimerService timerService = [
            getTimers              : { timers },
            createIntervalTimer    : { a, b, c -> timersCreated += c; null },
            createTimer            : { a, b -> timersCreated += b; null },
            createSingleActionTimer: { a, b -> timersCreated += b; null }
    ] as TimerService

    @ImplementationInstance
    Timer timer = Mock()

    def setup() {
        Mockito.when(membershipListener.isMaster()).thenReturn(true)
    }

    def "When createTimer is called should create timer in timer service for Membership Check"() {
        when: "When createTimer is called"
        AsyncResult<Boolean> result = startupService.createTimerForMembershipCheck()
        TimerConfig timerConfig = timersCreated.get(timersCreated.size() - 1) as TimerConfig
        then: "Timer created should have correct info and result should be true"
        result.get()
        timerConfig.info == subsriptionSchedulerInitTimerName
        !timerConfig.persistent
    }

    def "On Timeout when membership check is called should create deactivation timers for all Active running with schedule subscriptions"() {
        given: "Scheduled subscriptions exist in DPS"
        addSubscriptions()

        when: "Membership check is called"
        startupService.checkMembership()

        then: "Timers created should be equal to number of subscriptions"
        timersCreated.size() == old(timersCreated.size() + subscriptions.size())
        timersCreated.remove(0)
        timersCreated.each { timerConfig ->
            timerConfig.getInfo().each { initiationScheduleModel ->
                initiationScheduleModel.subscriptionId in subscriptionIds;
                initiationScheduleModel.eventType == AdministrationState.DEACTIVATING
            } as InitiationScheduleModel
        }
    }

    def "On Timeout when membership check is called should create activation and deactivation timers for all scheduled subscriptions"() {
        boolean counter = true

        given: "Scheduled subscriptions exist in DPS"
        addSubscriptions(AdministrationState.SCHEDULED)

        when: "Membership check is called"
        startupService.checkMembership()

        then: "Timers created should be twice to number of subscriptions as activation and deactivation are created for each subscription"
        timersCreated.size() == old(timersCreated.size() + (subscriptions.size() * 2))
        timersCreated.remove(0)
        timersCreated.each { timerConfig ->
            timerConfig.getInfo().each { initiationScheduleModel ->
                initiationScheduleModel.subscriptionId in subscriptionIds;
                (counter) ? initiationScheduleModel.eventType == AdministrationState.ACTIVATING : initiationScheduleModel.eventType == AdministrationState.DEACTIVATING
                counter = !counter
            } as InitiationScheduleModel
        }
    }


    def "On Timeout when membership check is called twice and isMaster is false should create then cancel timers"() {
        given: "Scheduled subscriptions exist in DPS"
        addSubscriptions()

        when: "Membership check is called"
        startupService.checkMembership()

        then: "Timers created should be twice to number of subscriptions as activation and deactivation are created for each subscription"
        timersCreated.size() == old(timersCreated.size() + subscriptions.size())
        timersCreated.remove(0)

        when: "isMaster is set to false"
        Mockito.when(membershipListener.isMaster()).thenReturn(false)
        and: "Timer Service Mock is set"
        createTimerServiceMocks()
        and: "Membership check is called"
        startupService.checkMembership()

        then: "Timers created should be twice to number of subscriptions as activation and deactivation are created for each subscription"
        (timersCreated.size() as int) * timer.cancel()

    }

    def "On Timeout when membership check is called  and isMaster is false should not create timers for all subscriptions"() {
        given: "Scheduled subscriptions exist in DPS"
        addSubscriptions(AdministrationState.SCHEDULED)
        and: "isMaster is set to false"
        Mockito.when(membershipListener.isMaster()).thenReturn(false)

        when: "Membership check is called"
        startupService.checkMembership()

        then: "Timers created should be twice to number of subscriptions as activation and deactivation are created for each subscription"
        timersCreated.size() == old(timersCreated.size())

    }

    def addSubscriptions(AdministrationState administrationState = AdministrationState.ACTIVE) {
        def (Date startTime, Date endTime) = (administrationState == AdministrationState.ACTIVE) ? getStartAndEndTime() : getStartAndEndTime(true)
        subscriptions = builders.collect {
            SubscriptionBuilder builder = it.newInstance(dpsUtils)
            builder.name("subscription").
                    administrativeState(administrationState).
                    operationalState(OperationalState.RUNNING).
                    taskStatus(TaskStatus.OK).
                    scheduleInfo(startTime, endTime).
                    build()
        }
        subscriptionIds = subscriptions.collect {
            it.poId
        }
    }

    def createTimerServiceMocks() {
        timersCreated.each {
            timer.getInfo() >> it.info
            timers.add(timer)
        }
    }

    private static List getStartAndEndTime(Boolean scheduled = false) {
        Calendar calendar = Calendar.getInstance()
        (scheduled) ? calendar.add(Calendar.MINUTE, 60) : null
        Date startTime = calendar.getTime()
        calendar.add(Calendar.MINUTE, 30)
        Date endTime = calendar.getTime()
        [startTime, endTime]
    }


}
