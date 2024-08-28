package com.ericsson.oss.services.pm.initiation.processor

import org.mockito.Mockito

import javax.ejb.TimerService

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.subscription.ResSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventType
import com.ericsson.oss.services.pm.initiation.processor.deactivation.DeactivationProcessor

class DeactivationProcessorSpec extends BaseProcessorSpec {
    @ObjectUnderTest
    DeactivationProcessor deactivationProcessor
    @ImplementationInstance
    TimerService timerService = Mock(TimerService)
    @ImplementationInstance
    TimeGenerator timer = Mockito.mock(TimeGenerator)

    def setup() {
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
    }

    def "When removing RNC nodes from a RES subscription deactivation process adds also attached nodes"() {
        given: "subscription and nodes in dps"
            buildNodesForRes()
            long subscrId = buildActiveResSubscription()
            String[] nodeNames = [rnc1NodeAttributes.name, rnc2NodeAttributes.name, rnc3NodeAttributes.name, rbs1NodeAttributes.name, rbs2NodeAttributes.name, rbs3NodeAttributes.name]
            buildScannersForRes(nodeNames, subscrId)
            ResSubscription resSubscription = subscriptionReadOperationService.findOneById(subscrId, true)
            mockedPmResLookUp.fetchAttachedNodes(_, _, _, _) >> attachedNodesList()

        when: "Deactivation processor called"
            List<Node> nodesDel = new ArrayList<>()
            nodesDel.addAll(Arrays.asList(RncNode2(), RncNode3()))
            deactivationProcessor.deactivate(nodesDel, resSubscription, InitiationEventType.REMOVE_NODES_FROM_SUBSCRIPTION)
        then: "Check nodes to be deactivated"
            4 * eventSender.send(_ as MediationTaskRequest)
    }

    long buildActiveResSubscription() {
        def subAttributes = ["resSpreadingFactor": ["SF_32"]]
        ManagedObject resSubscriptionMo = dps.subscription().type(SubscriptionType.RES).name(RES_SUBSCRIPTION_NAME).nodes(RNC01, RNC02, RNC03).attributes(subAttributes).attachedNodes(attachedNode1Mo, attachedNode2Mo, attachedNode3Mo).administrationState(AdministrationState.ACTIVE).build()
        return resSubscriptionMo.getPoId()
    }
}