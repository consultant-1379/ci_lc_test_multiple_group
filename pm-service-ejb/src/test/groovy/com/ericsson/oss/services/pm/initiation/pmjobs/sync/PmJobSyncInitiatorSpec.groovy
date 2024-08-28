package com.ericsson.oss.services.pm.initiation.pmjobs.sync

import org.mockito.Mockito

import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.node.TestNetworkElementDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.CtumSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.SubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.UeTraceSubscriptionBuilder
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionActivationTaskRequest
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionDeactivationTaskRequest

class PmJobSyncInitiatorSpec extends SkeletonSpec {

    @ObjectUnderTest
    PmJobSyncInitiator objectUnderTest

    @Override
    def autoAllocateFrom() {
        def packages = super.autoAllocateFrom()
        packages.addAll(['com.ericsson.oss.pmic.dao', 'com.ericsson.oss.pmic.dto'])
        return packages
    }

    @Inject
    EventSender<MediationTaskRequest> eventSender
    @Inject
    PMICInitiationTrackerCache initiationTrackerCache
    @ImplementationInstance
    TimerService timerService = Mock(TimerService)
    @ImplementationInstance
    TimeGenerator timer = Mockito.mock(TimeGenerator)

    TestNetworkElementDpsUtils nodeBuilder = new TestNetworkElementDpsUtils(configurableDps)
    List<ManagedObject> nodes = []

    def setup() {
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
    }

    def "startPmJobSyncing where Sub is #builder with adminState #adminState, 2 nodes with pmjobstatus #jobStatus where firstNode has pmFunction #pmFunctionNode1 and second node has #pmFunctionNode2"() {
        given:
            nodes.add(nodeBuilder.builder("SGSN-16A-V1-CP0001").neType("SGSN-MME").pmEnabled(pmFunctionNode1).build())
            nodes.add(nodeBuilder.builder("SGSN-16A-V1-CP0002").neType("SGSN-MME").pmEnabled(pmFunctionNode2).build())
            ManagedObject subscriptionMO = addSubscription(builder, subscriptionType, adminState)
            pmJobBuilder.nodeName(nodes.get(0)).processType(subscriptionMO).subscriptionId(subscriptionMO).status(jobStatus).build()
            pmJobBuilder.nodeName(nodes.get(1)).processType(subscriptionMO).subscriptionId(subscriptionMO).status(jobStatus).build()
        when:
            objectUnderTest.startPmJobSyncing()
        then: "#countOfMTRToSend are created and sent"
            if (MTRtype == "activate") {
                countOfMTRToSend * eventSender.send(_ as SubscriptionActivationTaskRequest)
            } else if (MTRtype == "deactivate") {
                countOfMTRToSend * eventSender.send(_ as SubscriptionDeactivationTaskRequest)
            } else {
                countOfMTRToSend * eventSender.send(_ as MediationTaskRequest)
            }
        and: "subscription state is updated to UPDATING or not if there is no action to be done"
        subscriptionMO.getAttribute("administrationState") == expectedAdminState.name()
        and: "initiation tracker is tracking only the MTRs that have been sent to mediation"
        if (countOfInitiationTrackers > 0) {
            initiationTrackerCache.getTracker(subscriptionMO.getPoId() as String).getTotalAmountOfExpectedNotifications() == countOfInitiationTrackers as long
        } else {
            initiationTrackerCache.getTracker(subscriptionMO.getPoId() as String) == null
        }
        where:
        builder                          | adminState                   | jobStatus            | pmFunctionNode1 | pmFunctionNode2 | countOfMTRToSend | expectedAdminState           | countOfInitiationTrackers | MTRtype
        //If subscription is Active but jpmJobStatus on node is Inactive,Error or Unknown, we send activation task request for those nodes
        UeTraceSubscriptionBuilder.class | AdministrationState.ACTIVE   | PmJobStatus.INACTIVE | true            | true            | 2                | AdministrationState.UPDATING | 2                         | "activate"
        CtumSubscriptionBuilder.class    | AdministrationState.ACTIVE   | PmJobStatus.INACTIVE | true            | true            | 2                | AdministrationState.UPDATING | 2                         | "activate"
        UeTraceSubscriptionBuilder.class | AdministrationState.ACTIVE   | PmJobStatus.UNKNOWN  | true            | true            | 2                | AdministrationState.UPDATING | 2                         | "activate"
        CtumSubscriptionBuilder.class    | AdministrationState.ACTIVE   | PmJobStatus.UNKNOWN  | true            | true            | 2                | AdministrationState.UPDATING | 2                         | "activate"
        UeTraceSubscriptionBuilder.class | AdministrationState.ACTIVE   | PmJobStatus.ERROR    | true            | true            | 2                | AdministrationState.UPDATING | 2                         | "activate"
        CtumSubscriptionBuilder.class    | AdministrationState.ACTIVE   | PmJobStatus.ERROR    | true            | true            | 2                | AdministrationState.UPDATING | 2                         | "activate"

        //If subscription is Inactive but jpmJobStatus on node is Active,Error or Unknown, we send deactivation task request for those nodes
        UeTraceSubscriptionBuilder.class | AdministrationState.INACTIVE | PmJobStatus.ACTIVE   | true            | true            | 2                | AdministrationState.UPDATING | 2                         | "deactivate"
        CtumSubscriptionBuilder.class    | AdministrationState.INACTIVE | PmJobStatus.ACTIVE   | true            | true            | 2                | AdministrationState.UPDATING | 2                         | "deactivate"
        UeTraceSubscriptionBuilder.class | AdministrationState.INACTIVE | PmJobStatus.UNKNOWN  | true            | true            | 2                | AdministrationState.UPDATING | 2                         | "deactivate"
        CtumSubscriptionBuilder.class    | AdministrationState.INACTIVE | PmJobStatus.UNKNOWN  | true            | true            | 2                | AdministrationState.UPDATING | 2                         | "deactivate"
        UeTraceSubscriptionBuilder.class | AdministrationState.INACTIVE | PmJobStatus.ERROR    | true            | true            | 2                | AdministrationState.UPDATING | 2                         | "deactivate"
        CtumSubscriptionBuilder.class    | AdministrationState.INACTIVE | PmJobStatus.ERROR    | true            | true            | 2                | AdministrationState.UPDATING | 2                         | "deactivate"

        //If nodes have pm function off, then there is no action
        UeTraceSubscriptionBuilder.class | AdministrationState.ACTIVE   | PmJobStatus.INACTIVE | false           | false           | 0                | AdministrationState.ACTIVE   | 0                         | "none"
        CtumSubscriptionBuilder.class    | AdministrationState.ACTIVE   | PmJobStatus.INACTIVE | false           | false           | 0                | AdministrationState.ACTIVE   | 0                         | "none"
        UeTraceSubscriptionBuilder.class | AdministrationState.INACTIVE | PmJobStatus.ACTIVE   | false           | false           | 0                | AdministrationState.INACTIVE | 0                         | "none"
        CtumSubscriptionBuilder.class    | AdministrationState.INACTIVE | PmJobStatus.ACTIVE   | false           | false           | 0                | AdministrationState.INACTIVE | 0                         | "none"

        //If only one of the nodes have pm function off, then there is only one activation or deactivation tasks to be performed
        UeTraceSubscriptionBuilder.class | AdministrationState.ACTIVE   | PmJobStatus.INACTIVE | true            | false           | 1                | AdministrationState.UPDATING | 1                         | "activate"
        CtumSubscriptionBuilder.class    | AdministrationState.ACTIVE   | PmJobStatus.INACTIVE | true            | false           | 1                | AdministrationState.UPDATING | 1                         | "activate"
        UeTraceSubscriptionBuilder.class | AdministrationState.INACTIVE | PmJobStatus.ACTIVE   | true            | false           | 1                | AdministrationState.UPDATING | 1                         | "deactivate"
        CtumSubscriptionBuilder.class    | AdministrationState.INACTIVE | PmJobStatus.ACTIVE   | true            | false           | 1                | AdministrationState.UPDATING | 1                         | "deactivate"

        subscriptionType = builder.getSimpleName().replaceAll('Builder', '')
    }

    def "startPmJobSyncing testing that if 2 nodes exist with one incorrect pmJobState, only one activation/deactivation event will be processed"() {
        given:
        nodes.add(nodeBuilder.builder("SGSN-16A-V1-CP0001").neType("SGSN-MME").pmEnabled(true).build())
        nodes.add(nodeBuilder.builder("SGSN-16A-V1-CP0002").neType("SGSN-MME").pmEnabled(true).build())

        ManagedObject ueTraceSubscriptionMO = new UeTraceSubscriptionBuilder(dpsUtils).name("UeTraceSubscription").administrativeState(AdministrationState.ACTIVE).taskStatus(TaskStatus.OK).build()
        ManagedObject ctumSubscriptionMO = new CtumSubscriptionBuilder(dpsUtils).name("CtumSubscription").administrativeState(AdministrationState.INACTIVE).taskStatus(TaskStatus.OK).build()

        pmJobBuilder.nodeName(nodes[0]).processType(ueTraceSubscriptionMO).subscriptionId(ueTraceSubscriptionMO).status(PmJobStatus.INACTIVE).build()
        pmJobBuilder.nodeName(nodes[1]).processType(ueTraceSubscriptionMO).subscriptionId(ueTraceSubscriptionMO).status(PmJobStatus.ACTIVE).build()
        pmJobBuilder.nodeName(nodes[0]).processType(ctumSubscriptionMO).subscriptionId(ctumSubscriptionMO).status(PmJobStatus.INACTIVE).build()
        pmJobBuilder.nodeName(nodes[1]).processType(ctumSubscriptionMO).subscriptionId(ctumSubscriptionMO).status(PmJobStatus.ACTIVE).build()
        when:
        objectUnderTest.startPmJobSyncing()
        then:
        2 * eventSender.send(_ as MediationTaskRequest)
        and:
        ueTraceSubscriptionMO.getAttribute("administrationState") == "UPDATING"
        ctumSubscriptionMO.getAttribute("administrationState") == "UPDATING"
        and:
        initiationTrackerCache.getTracker(ueTraceSubscriptionMO.getPoId() as String).getTotalAmountOfExpectedNotifications() == 1 as long
        initiationTrackerCache.getTracker(ctumSubscriptionMO.getPoId() as String).getTotalAmountOfExpectedNotifications() == 1 as long
    }

    def addSubscription(Class<? extends SubscriptionBuilder> clazz, String name, AdministrationState administrationState) {
        SubscriptionBuilder builder = clazz.newInstance(dpsUtils)
        return builder.name(name).administrativeState(administrationState).taskStatus(TaskStatus.OK).build()
    }

}
