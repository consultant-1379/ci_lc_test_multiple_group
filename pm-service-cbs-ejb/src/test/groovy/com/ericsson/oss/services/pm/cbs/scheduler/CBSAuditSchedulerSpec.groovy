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

import javax.ejb.TimerService
import javax.inject.Inject
import java.text.SimpleDateFormat

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.subscription.cdts.CriteriaSpecification
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.cbs.config.listener.CBSConfigurationChangeListener
import com.ericsson.oss.services.pm.cbs.events.CBSExecutionPlanEvent200
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener

class CBSAuditSchedulerSpec extends SkeletonSpec {

    def SimpleDateFormat SDF = new SimpleDateFormat('dd/MM/yyyy HH:mm:ss')
    def long INITIAL_TIME = SDF.parse('22/09/2000 12:01:00').getTime()

    @ObjectUnderTest
    CBSAuditScheduler auditScheduler
    @Inject
    CBSConfigurationChangeListener cbsConfigurationChangeListener
    @ImplementationInstance
    MembershipListener membershipListener = Mockito.mock(MembershipListener)
    @Inject
    @Modeled
    EventSender<CBSExecutionPlanEvent200> cbsExecutionPlanEventSender
    @ImplementationInstance
    TimerService timerService = [
        getTimers          : { [] },
        createIntervalTimer: { a, b, c -> null }
    ] as TimerService
    @ImplementationInstance
    TimeGenerator timer = Mockito.mock(TimeGenerator)
    @Inject
    SupportedRopTimes supportedRopTimes;

    def setup() {
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
    }

    def "On Timeout, should audit subscriptions"() {
        given: "node is master node"
        Mockito.when(membershipListener.isMaster()).thenReturn(true)
        def node = dps.node().name("1").build()
        dps.subscription().
                type(SubscriptionType.STATISTICAL).
                cbs(true).
                criteriaSpecification(new CriteriaSpecification("Name", "NetworkElement where neType=ERBS")).
                nodes(node).
                build()
        when: "CBS Audit Schedule Timer timeout"
        auditScheduler.onTimeout()

        then: "CBS audit event should be created and sent"
        1 * cbsExecutionPlanEventSender.send(_ as CBSExecutionPlanEvent200, "//global/ClusteredCBSExecutionPlanEventQueue")
    }

    def "On Timeout when Membership listener is not master, should not create and send audit event"() {

        given: "node is slave node"
        Mockito.when(membershipListener.isMaster()).thenReturn(false)
        def node = dps.node().name("1").build()
        dps.subscription().
                type(SubscriptionType.STATISTICAL).
                cbs(true).
                criteriaSpecification(new CriteriaSpecification("Name", "NetworkElement where neType=ERBS")).
                nodes(node).
                build()
        when: "CBS Audit Schedule Timer timeout"
        auditScheduler.onTimeout()

        then: "CBS audit event should not be created and sent"
        0 * cbsExecutionPlanEventSender.send(_ as CBSExecutionPlanEvent200, "//global/ClusteredCBSExecutionPlanEventQueue")

    }

    def "On Timeout when periodicCBSAudit is false, should not create and send audit event"() {
        given: "Audit is not periodic"
        cbsConfigurationChangeListener.listenForcbsEnableFlag(false)
        def node = dps.node().name("1").build()
        dps.subscription().
                type(SubscriptionType.STATISTICAL).
                cbs(true).
                criteriaSpecification(new CriteriaSpecification("Name", "NetworkElement where neType=ERBS")).
                nodes(node).
                build()
        when: "CBS Audit Schedule Timer timeout"
        auditScheduler.onTimeout()

        then: "CBS audit event should not be created and sent"
        0 * cbsExecutionPlanEventSender.send(_ as CBSExecutionPlanEvent200, "//global/ClusteredCBSExecutionPlanEventQueue")

    }

}
