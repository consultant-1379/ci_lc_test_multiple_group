package com.ericsson.oss.services.pm.initiation.processor

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.subscription.ResSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventType
import com.ericsson.oss.services.pm.initiation.processor.activation.ActivationProcessor

class ActivationProcessorSpec extends BaseProcessorSpec {
    @ObjectUnderTest
    ActivationProcessor activationProcessor

    def "When adding RNC nodes from a RES subscription activation process adds also attached nodes"() {
        given: "subscription and nodes in dps"
            buildNodesForRes()
            long activeResSubscriptionId = buildActiveResSubscription()
            String[] nodeNames = [rnc1NodeAttributes.name, rbs1NodeAttributes.name]
            buildScannersForRes(nodeNames, activeResSubscriptionId)
            ResSubscription resSubscription = subscriptionReadOperationService.findOneById(activeResSubscriptionId, true)
            mockedPmResLookUp.fetchAttachedNodes(_, _, _, _) >> attachedNodesList()

        when: "Activation processor called"
            List<Node> nodesAdd = new ArrayList<>()
            nodesAdd.add(RncNode2())
            nodesAdd.add(RncNode3())
            activationProcessor.activate(nodesAdd, resSubscription, InitiationEventType.ADD_NODES_TO_SUBSCRIPTION)

        then: "Check activation tasks are sent"
            4 * eventSender.send(_ as MediationTaskRequest)
    }

    long buildActiveResSubscription() {
        def subAttributes = ["resSpreadingFactor": ["SF_32"]]
        ManagedObject resSubscriptionMo = dps.subscription().type(SubscriptionType.RES).name(RES_SUBSCRIPTION_NAME).nodes(RNC01).attributes(subAttributes).attachedNodes(attachedNode1Mo).administrationState(AdministrationState.ACTIVE).build()
        return resSubscriptionMo.getPoId()
    }
}