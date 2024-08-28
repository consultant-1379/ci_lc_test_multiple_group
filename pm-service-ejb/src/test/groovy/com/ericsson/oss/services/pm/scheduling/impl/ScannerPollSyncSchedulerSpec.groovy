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

package com.ericsson.oss.services.pm.scheduling.impl

import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.CTUM
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.UETRACE

import org.mockito.Mockito
import spock.lang.Unroll

import javax.ejb.TimerConfig
import javax.ejb.TimerService
import javax.inject.Inject
import java.text.SimpleDateFormat

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.CtumSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.UeTraceSubscriptionBuilder
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes
import com.ericsson.oss.services.pm.common.scheduling.IntervalTimerInfo
import com.ericsson.oss.services.pm.initiation.common.RopUtil
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener

class ScannerPollSyncSchedulerSpec extends SkeletonSpec {
    def String TIMER_NAME = 'common_polling_syncing'
    def SimpleDateFormat SDF = new SimpleDateFormat('dd/MM/yyyy HH:mm:ss')
    def long INITIAL_TIME = SDF.parse('22/09/2000 12:01:00').getTime()

    @ObjectUnderTest
    ScannerPollSyncScheduler scheduler
    @ImplementationInstance
    ConfigurationChangeListener configurationChangeListener = Mock(ConfigurationChangeListener)
    @Inject
    @Modeled
    private EventSender<MediationTaskRequest> eventSender
    @Inject
    SupportedRopTimes supportedRopTimes;
    @Inject
    RopUtil ropUtil
    @ImplementationInstance
    MembershipListener membershipListener = Mock(MembershipListener)
    @ImplementationInstance
    TimerService timerService = Mock(TimerService)
    @ImplementationInstance
    TimeGenerator timer = Mockito.mock(TimeGenerator)

    def setup() {
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
    }

    def 'create timer starts according to the end of 15 min rop'() {
        configurationChangeListener.getScannerPollingIntervalMinutes() >> 15
        long intervalMillis = 15 * 60000L
        long time = INITIAL_TIME
        given: 'set current time'
            timer.currentTimeMillis() >> time
            timerService.getTimers() >> []
            final Date initialExpiration = ropUtil.getInitialExpirationTime(0);
        when: 'The timer is created'
            scheduler.createTimer()

        then: 'Validate polling timer start at end of 15min rop'
            1 * timerService.createIntervalTimer(initialExpiration, intervalMillis,
                { new TimerConfig(new IntervalTimerInfo(TIMER_NAME, intervalMillis), false) } as TimerConfig)
    }

    @Unroll
    def "On Timeout should create and send scanner polling tasks for all nodes, create #noPmJobs #pmJobName pmJobs for #subscriptionType subscription with #subscriptionState state with pmJobStates #pmJobStates "() {
        given: "subscription in DPS"
            def subscriptionMO = builder.newInstance(dpsUtils).
                name("Subscription").
                administrativeState(subscriptionState).
                operationalState(OperationalState.RUNNING).
                taskStatus(TaskStatus.OK).
                build()
        and: "add nodes to DPS"
            def sgsnMO1 = nodeUtil.builder('SGSN-16A-V1-CP0201').neType(NetworkElementType.SGSNMME).build()
            def sgsnMO2 = nodeUtil.builder('SGSN-16A-V1-CP0202').neType(NetworkElementType.SGSNMME).build()
            def sgsnMO3 = nodeUtil.builder('SGSN-16A-V1-CP0203').neType(NetworkElementType.SGSNMME).build()
            def sgsnMO4 = nodeUtil.builder('SGSN-16A-V1-CP0204').neType(NetworkElementType.SGSNMME).build()
            nodeUtil.builder('LTE01ERBS0001').neType(NetworkElementType.ERBS).build()
            nodeUtil.builder('LTE01DG200001').neType(NetworkElementType.RADIONODE).build()
            nodeUtil.builder('CISCO-ASR9000-01').neType(NetworkElementType.CISCOASR9000).build()
        and: "add pmJobs for SGSN nodes"
            pmJobBuilder.nodeName(sgsnMO1).processType(processType).subscriptionId(subscriptionMO).status(pmJobStates.get(0)).build()
            pmJobBuilder.nodeName(sgsnMO2).processType(processType).subscriptionId(subscriptionMO).status(pmJobStates.get(1)).build()
            pmJobBuilder.nodeName(sgsnMO3).processType(processType).subscriptionId(subscriptionMO).status(pmJobStates.get(2)).build()
            pmJobBuilder.nodeName(sgsnMO4).processType(processType).subscriptionId(subscriptionMO).status(pmJobStates.get(3)).build()
        and: "isMaster is set"
            membershipListener.isMaster() >> isMaster
        when: "Timeout occurs"
            scheduler.onTimeout()
        then: "If isMaster is true, should send ScannerPollingTask for each node in dps except for cisco nodes"
            if (isMaster) {
                6 * eventSender.send({ request -> request.toString().contains('ScannerPollingTaskRequest') } as MediationTaskRequest)
            } else {
                0 * eventSender.send({ request -> request.toString().contains('ScannerPollingTaskRequest') } as MediationTaskRequest)
            }
        and: "If isMaster is true should sync PmJobs status with subscription status"
            if (subscriptionState == AdministrationState.ACTIVE) {
                noPmJobs * eventSender.send({ request -> request.toString().contains("SubscriptionActivationTaskRequest") } as MediationTaskRequest)
            } else {
                noPmJobs * eventSender.send({ request -> request.toString().contains("SubscriptionDeactivationTaskRequest") } as MediationTaskRequest)
            }

        where:
            subscriptionState            | processType | pmJobStates                                      | noPmJobs | isMaster
            AdministrationState.ACTIVE   | UETRACE     | ['ACTIVE', 'ACTIVE', 'INACTIVE', 'INACTIVE']     | 2        | true
            AdministrationState.INACTIVE | UETRACE     | ['ACTIVE', 'ACTIVE', 'INACTIVE', 'INACTIVE']     | 2        | true
            AdministrationState.ACTIVE   | UETRACE     | ['ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE']         | 0        | true
            AdministrationState.INACTIVE | UETRACE     | ['ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE']         | 4        | true
            AdministrationState.ACTIVE   | UETRACE     | ['INACTIVE', 'INACTIVE', 'INACTIVE', 'INACTIVE'] | 4        | true
            AdministrationState.INACTIVE | UETRACE     | ['INACTIVE', 'INACTIVE', 'INACTIVE', 'INACTIVE'] | 0        | true
            AdministrationState.ACTIVE   | UETRACE     | ['ACTIVE', 'INACTIVE', 'INACTIVE', 'INACTIVE']   | 3        | true
            AdministrationState.INACTIVE | UETRACE     | ['ACTIVE', 'INACTIVE', 'INACTIVE', 'INACTIVE']   | 1        | true
            AdministrationState.ACTIVE   | UETRACE     | ['ACTIVE', 'ACTIVE', 'ACTIVE', 'INACTIVE']       | 1        | true
            AdministrationState.INACTIVE | UETRACE     | ['ACTIVE', 'ACTIVE', 'ACTIVE', 'INACTIVE']       | 3        | true
            AdministrationState.ACTIVE   | CTUM        | ['ACTIVE', 'ACTIVE', 'INACTIVE', 'INACTIVE']     | 2        | true
            AdministrationState.INACTIVE | CTUM        | ['ACTIVE', 'ACTIVE', 'INACTIVE', 'INACTIVE']     | 2        | true
            AdministrationState.ACTIVE   | CTUM        | ['ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE']         | 0        | true
            AdministrationState.INACTIVE | CTUM        | ['ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE']         | 4        | true
            AdministrationState.ACTIVE   | CTUM        | ['INACTIVE', 'INACTIVE', 'INACTIVE', 'INACTIVE'] | 4        | true
            AdministrationState.INACTIVE | CTUM        | ['INACTIVE', 'INACTIVE', 'INACTIVE', 'INACTIVE'] | 0        | true
            AdministrationState.ACTIVE   | CTUM        | ['ACTIVE', 'INACTIVE', 'INACTIVE', 'INACTIVE']   | 3        | true
            AdministrationState.INACTIVE | CTUM        | ['ACTIVE', 'INACTIVE', 'INACTIVE', 'INACTIVE']   | 1        | true
            AdministrationState.ACTIVE   | CTUM        | ['ACTIVE', 'ACTIVE', 'ACTIVE', 'INACTIVE']       | 1        | true
            AdministrationState.INACTIVE | CTUM        | ['ACTIVE', 'ACTIVE', 'ACTIVE', 'INACTIVE']       | 3        | true
            AdministrationState.ACTIVE   | UETRACE     | ['ACTIVE', 'ACTIVE', 'INACTIVE', 'INACTIVE']     | 0        | false
            AdministrationState.ACTIVE   | CTUM        | ['ACTIVE', 'ACTIVE', 'INACTIVE', 'INACTIVE']     | 0        | false

            builder = (processType == UETRACE) ? UeTraceSubscriptionBuilder.class : CtumSubscriptionBuilder.class
            subscriptionType = (processType == UETRACE) ? 'UeTrace' : 'Ctum'
    }

    @Unroll
    def "On Timeout should delete #processType PmJob when there is no matching subscription in DPS "() {
        given: "PmJob exist in dps with no subscription in dps"
            long subId = 123456L
            ManagedObject pmJob = pmJobBuilder.nodeName("SGSN-16A-V1-CP0201").
                processType(processType.name()).
                subscriptionId(subId).
                status(PmJobStatus.ACTIVE).
                build()
            membershipListener.isMaster() >> true
        when: "Timeout occurs"
            scheduler.onTimeout()

        then: "PmJob should no longer exist in dps"
            configurableDps.build().getLiveBucket().findMoByFdn(pmJob.fdn) == null

        where:
            processType << [UETRACE, CTUM]
    }
}

