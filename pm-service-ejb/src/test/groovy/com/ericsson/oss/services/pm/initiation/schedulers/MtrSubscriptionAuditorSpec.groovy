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

package com.ericsson.oss.services.pm.initiation.schedulers

import com.ericsson.oss.pmic.dto.subscription.MtrSubscription
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.services.pm.exception.DataAccessException
import com.ericsson.oss.services.pm.initiation.ejb.ResourceSubscriptionNodeInitiation
import org.slf4j.Logger

import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.STATS
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.MTR

import javax.inject.Inject

import spock.lang.Unroll

import com.ericsson.oss.pmic.api.modelservice.PmCapabilitiesLookupLocal
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.pm.initiation.notification.events.Activate
import com.ericsson.oss.services.pm.initiation.notification.events.Deactivate
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEvent
import com.ericsson.oss.services.pm.initiation.task.factories.auditor.MtrSubscriptionAuditor
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.oss.pmic.dto.node.Node

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.MTR_SUBSCRIPTION_ATTRIBUTES

class MtrSubscriptionAuditorSpec  extends AuditorParentSpec {

    @MockedImplementation
    @Activate
    InitiationEvent activationEvent
    @MockedImplementation
    @Deactivate
    InitiationEvent deactivationEvent

    @MockedImplementation
    PmCapabilitiesLookupLocal pmCapabilitiesLookupLocal

    @MockedImplementation
    ResourceSubscriptionNodeInitiation subscriptionNodeInitiation

    @MockedImplementation
    Logger logger

    @Inject
    MtrSubscriptionAuditor mtrAuditor

    def setup() {
        pmCapabilitiesLookupLocal.getDefaultCapabilityValue(MTR_SUBSCRIPTION_ATTRIBUTES,
                                              'multipleNeTypesList') >> ['BSC']
    }

    @Unroll
    def 'Should send activation request only for explicit nodes when attached node connected Msc has no active scanner(s)'() {
        given: '1 explicit and 1 attached node'
            def rncNodeMo = buildNode('rncNodeMo', 'RNC')
            def bscNode = buildNodeBuilder('bscNode', 'BSC').attributes(['connectedMsc': connectedMscFdn]).build()

        and: 'an active MTR subscription with rncNodeMo as explicit node, bscNode as attached node and associated scanners'
            def subscriptionMo = createSubscription(rncNodeMo, bscNode)
            buildScanner(rncNodeMo.name, 'USERDEF-mtr_sub.Cont.Y.STATS', subscriptionMo, false, STATS)
            buildScanner(bscNode.name, 'USERDEF-mtr_sub.Cont.Y.STATS', subscriptionMo, false, STATS)

        when: 'the audit runs'
            mtrAuditor.audit(Arrays.asList(subscriptionMo.getPoId()))

        then: 'one activation and one deactivation sent'
            1 * activationEvent.execute({it -> it.size() == 1 && it[0].fdn == rncNodeMo.fdn} as List<Node>, {it -> it.id == subscriptionMo.poId } as Subscription)

        where:
            connectedMscFdn             || expectedMtrs
            'NetworkElement=rncNodeMo'  || 1
            null                        || 1
            'Some other fdn'            || 1
    }

    def 'Should send activation request for attached node when connected Msc has active scanner(s)'() {
        given: '1 explicit and 1 attached node'
            def rncNodeMo = buildNode('rncNodeMo', 'RNC')
            def bscNode = buildNodeBuilder('bscNode', 'BSC').attributes(['connectedMsc': 'NetworkElement=rncNodeMo']).build()

        and: 'an active MTR subscription with rncNodeMo as explicit node, bscNode as attached node and associated scanners'
            def subscriptionMo = createSubscription(rncNodeMo, bscNode)
            buildScanner(rncNodeMo.name, 'USERDEF-mtr_sub.Cont.Y.STATS', subscriptionMo, true, STATS)
            buildScanner(bscNode.name, 'USERDEF-mtr_sub.Cont.Y.STATS', subscriptionMo, false, STATS)

        when: 'the audit runs'
            mtrAuditor.audit(Arrays.asList(subscriptionMo.getPoId()))

        then: 'one activation and one deactivation sent'
            1 * activationEvent.execute({it -> it.size() == 1 && it[0].fdn == bscNode.fdn} as List<Node>, {it -> it.id == subscriptionMo.poId } as Subscription)
    }

    def 'Should not throw exception from auditor if SubscriptionNodeInitiation attempt throws an exception'() {
        given: '1 explicit and 1 attached node'
            def rncNodeMo = buildNode('rncNodeMo', 'RNC')
            def bscNode = buildNodeBuilder('bscNode', 'BSC').attributes(['connectedMsc': 'NetworkElement=rncNodeMo']).build()
            subscriptionNodeInitiation.activateOrDeactivateNodesOnActiveSubscription(_ as ResourceSubscription, [] as List<Node>, [] as List<Node>) >> {throw new DataAccessException('a message')}

        and: 'an active MTR subscription with rncNodeMo as explicit node, bscNode as attached node and associated scanners'
            def subscriptionMo = createSubscription(rncNodeMo, bscNode)
            buildScanner(rncNodeMo.name, 'USERDEF-mtr_sub.Cont.Y.STATS', subscriptionMo, true, STATS)
            buildScanner(bscNode.name, 'USERDEF-mtr_sub.Cont.Y.STATS', subscriptionMo, false, STATS)

        when: 'the audit runs'
            mtrAuditor.audit(Arrays.asList(subscriptionMo.getPoId()))

        then: 'one activation and one deactivation sent'
            1 * activationEvent.execute({it -> it.size() == 1 && it[0].fdn == bscNode.fdn} as List<Node>, {it -> it.id == subscriptionMo.poId } as Subscription)

        and: 'no exception is thrown'
            noExceptionThrown()
            1 * logger.warn("Audit for MtrSubscription {} failed! Could not fetch attached Nodes from dps :: {}", subscriptionMo.name, 'a message')
            1 * logger.debug("fetchAttachedNodes failed during auditing of MtrSubscription {} :: {}", subscriptionMo.name, _ as StackTraceElement[])
    }

    def 'Should try to activate missing attached node for subscription'() {
        given: '1 explicit and 1 attached node'
            def mscNodeMo = buildNodeBuilder('mscNodeMo', 'MSC').attributes(['poolRefs': ['Pool=Pool1']]).build()
            def attachedBscNode = buildNodeBuilder('attachedBscNode', 'BSC').attributes(['connectedMsc': 'NetworkElement=mscNodeMo']).build()
            def missingBscNode = buildNodeBuilder('missingBscNode', 'BSC').attributes(['connectedMsc': 'NetworkElement=mscNodeMo']).build()
            def missingBscNode2 = buildNodeBuilder('missingBscNode2', 'BSC').attributes(['connectedMscs': ['NetworkElement=mscNodeMo']]).build()
            def missingBscNode3 = buildNodeBuilder('missingBscNode3', 'BSC').attributes(['mscPoolRefs': ['Pool=Pool1']]).build()

        and: 'an active MTR subscription with mscNodeMo as explicit node, attachedBscNodes as attached node and associated scanners'
            def subscriptionMo = createSubscription(mscNodeMo, attachedBscNode)
            buildScanner(mscNodeMo.name, 'USERDEF-mtr_sub.Cont.Y.MTR', subscriptionMo, true, MTR)
            buildScanner(attachedBscNode.name, 'USERDEF-mtr_sub.Cont.Y.MTR', subscriptionMo, true, MTR)

        when: 'the audit runs'
            mtrAuditor.audit(Arrays.asList(subscriptionMo.getPoId()))

        then: 'node initiation called with missing attached node to be added to subscription'
            1 * subscriptionNodeInitiation.activateOrDeactivateNodesOnActiveSubscription({it -> it.id == subscriptionMo.poId} as MtrSubscription, { it -> it.size() == 3 && it[0].fdn == missingBscNode.fdn && it[1].fdn == missingBscNode2.fdn && it[2].fdn == missingBscNode3.fdn} as List<Node>, [] as List<Node>)

    }

    def 'Should try to deactivate removed attached node for subscription'() {
        given: '1 explicit and 4 attached node'
        def mscNodeMo = buildNodeBuilder('mscNodeMo', 'MSC').attributes(['poolRefs': ['Pool=Pool1']]).build()
        def attachedBscNode1 = buildNodeBuilder('attachedBscNode1', 'BSC').attributes(['connectedMsc': 'NetworkElement=mscNodeMo']).build()
        def attachedBscNode2 = buildNodeBuilder('attachedBscNode2', 'BSC').build()
        def attachedBscNode3 = buildNodeBuilder('attachedBscNode3', 'BSC').build()
        def attachedBscNode4 = buildNodeBuilder('attachedBscNode4', 'BSC').build()
        def expectedNodeList = [attachedBscNode2.fdn,attachedBscNode3.fdn,attachedBscNode4.fdn]

        and: 'an active MTR subscription with mscNodeMo as explicit node, attachedBscNodes as attached node and associated scanners'
        def subscriptionMo = createSubscription(mscNodeMo, [attachedBscNode1, attachedBscNode2, attachedBscNode3, attachedBscNode4])
        buildScanner(mscNodeMo.name, 'USERDEF-mtr_sub.Cont.Y.MTR', subscriptionMo, true, MTR)
        buildScanner(attachedBscNode1.name, 'USERDEF-mtr_sub.Cont.Y.MTR', subscriptionMo, true, MTR)
        buildScanner(attachedBscNode2.name, 'USERDEF-mtr_sub.Cont.Y.MTR', subscriptionMo, true, MTR)
        buildScanner(attachedBscNode3.name, 'USERDEF-mtr_sub.Cont.Y.MTR', subscriptionMo, true, MTR)
        buildScanner(attachedBscNode4.name, 'USERDEF-mtr_sub.Cont.Y.MTR', subscriptionMo, true, MTR)

        when: 'the audit runs'
        mtrAuditor.audit(Arrays.asList(subscriptionMo.getPoId()))

        then: 'node deactivation called for attached node to be removed from subscription'
        1 * subscriptionNodeInitiation.activateOrDeactivateNodesOnActiveSubscription({it -> it.id == subscriptionMo.poId} as MtrSubscription, [] as List<Node>, {it -> it.size() == 3 && expectedNodeList.contains(it[0].fdn) && expectedNodeList.contains(it[1].fdn) && expectedNodeList.contains(it[2].fdn)} as List<Node>)

    }

    def 'Should try to activate attached node for subscription if the scanner is missing for it'() {
        given: '1 explicit and 4 attached node'
        def mscNodeMo = buildNodeBuilder('mscNodeMo', 'MSC').attributes(['poolRefs': ['Pool=Pool1']]).build()
        def attachedBscNode1 = buildNodeBuilder('attachedBscNode1', 'BSC').attributes(['connectedMsc': 'NetworkElement=mscNodeMo']).build()
        def attachedBscNode2 = buildNodeBuilder('attachedBscNode2', 'BSC').attributes(['connectedMsc': 'NetworkElement=mscNodeMo']).build()
        def attachedBscNode3 = buildNodeBuilder('attachedBscNode3', 'BSC').attributes(['connectedMscs': ['NetworkElement=mscNodeMo']]).build()
        def attachedBscNode4 = buildNodeBuilder('attachedBscNode4', 'BSC').attributes(['mscPoolRefs': ['Pool=Pool1']]).build()
        def expectedNodeList = [attachedBscNode2.fdn,attachedBscNode3.fdn,attachedBscNode4.fdn]

        and: 'an active MTR subscription with mscNodeMo as explicit node, attachedBscNodes as attached node and associated scanners'
        def subscriptionMo = createSubscription(mscNodeMo, [attachedBscNode1,attachedBscNode2,attachedBscNode3,attachedBscNode4])
        buildScanner(mscNodeMo.name, 'USERDEF-mtr_sub.Cont.Y.MTR', subscriptionMo, true, MTR)
        buildScanner(attachedBscNode1.name, 'USERDEF-mtr_sub.Cont.Y.MTR', subscriptionMo, true, MTR)

        when: 'the audit runs'
        mtrAuditor.audit(Arrays.asList(subscriptionMo.getPoId()))

        then: 'one activation '
        1 * activationEvent.execute({it -> it.size() == 3 && expectedNodeList.contains(it[0].fdn) && expectedNodeList.contains(it[1].fdn) && expectedNodeList.contains(it[2].fdn)} as List<Node>, {it -> it.id == subscriptionMo.poId } as Subscription)

        and: 'node initiation called with attached nodes with missing scanners for subscription'
        1 * subscriptionNodeInitiation.activateOrDeactivateNodesOnActiveSubscription({it -> it.id == subscriptionMo.poId} as MtrSubscription, [] as List<Node>, [] as List<Node>)

    }

    def 'Should try to deactivate attached node for subscription if the scanner is duplicate for it'() {
        given: '1 explicit and 3 attached node'
        def mscNodeMo = buildNodeBuilder('mscNodeMo', 'MSC').attributes(['poolRefs': ['Pool=Pool1']]).build()
        def attachedBscNode1 = buildNodeBuilder('attachedBscNode1', 'BSC').attributes(['connectedMsc': 'NetworkElement=mscNodeMo']).build()
        def attachedBscNode2 = buildNodeBuilder('attachedBscNode2', 'BSC').attributes(['connectedMscs': ['NetworkElement=mscNodeMo']]).build()
        def attachedBscNode3 = buildNodeBuilder('attachedBscNode3', 'BSC').attributes(['mscPoolRefs': ['Pool=Pool1']]).build()
        def expectedNodeList = [attachedBscNode1.fdn,attachedBscNode2.fdn,attachedBscNode3.fdn]

        and: 'an active MTR subscription with mscNodeMo as explicit node, attachedBscNodes as attached node and associated scanners'
        def subscriptionMo = createSubscription(mscNodeMo, [attachedBscNode1,attachedBscNode2,attachedBscNode3])
        buildScanner(mscNodeMo.name, 'USERDEF-mtr_sub.Cont.Y.MTR', subscriptionMo, true, MTR)
        buildScanner(attachedBscNode1.name, 'USERDEF-mtr_sub.Cont.Y.MTR', subscriptionMo, true, MTR)
        buildScanner(attachedBscNode1.name, 'USERDEF-mtr_sub1.Cont.Y.MTR', subscriptionMo, true, MTR)
        buildScanner(attachedBscNode2.name, 'USERDEF-mtr_sub.Cont.Y.MTR', subscriptionMo, true, MTR)
        buildScanner(attachedBscNode2.name, 'USERDEF-mtr_sub1.Cont.Y.MTR', subscriptionMo, true, MTR)
        buildScanner(attachedBscNode3.name, 'USERDEF-mtr_sub.Cont.Y.MTR', subscriptionMo, true, MTR)
        buildScanner(attachedBscNode3.name, 'USERDEF-mtr_sub1.Cont.Y.MTR', subscriptionMo, true, MTR)

        when: 'the audit runs'
        mtrAuditor.audit(Arrays.asList(subscriptionMo.getPoId()))

        then: 'one deactivation event sent'
        1 * deactivationEvent.execute({it -> it.size() == 3 && expectedNodeList.contains(it[0].fdn) && expectedNodeList.contains(it[1].fdn) && expectedNodeList.contains(it[2].fdn)} as List<Node>, {it -> it.id == subscriptionMo.poId } as Subscription)

        and: 'node deactivation called for attached nodes if the scanner is duplicate'
        1 * subscriptionNodeInitiation.activateOrDeactivateNodesOnActiveSubscription({it -> it.id == subscriptionMo.poId} as MtrSubscription, [] as List<Node>, [] as List<Node>)

    }

    def 'Should not try to activate missing attached node if the controlling msc has no active scanner'() {
        given: '2 explicit nodes, 2 attached nodes and 1 missing attached node'
            def rncNodeMo1 = buildNode('rncNodeMo1', 'RNC')
            def rncNodeMo2 = buildNode('rncNodeMo2', 'RNC')
            def attachedBscNode1 = buildNodeBuilder('attachedBscNode1', 'BSC').attributes(['connectedMsc': 'NetworkElement=rncNodeMo1']).build()
            def attachedBscNode2 = buildNodeBuilder('attachedBscNode2', 'BSC').attributes(['connectedMsc': 'NetworkElement=rncNodeMo2']).build()
            buildNodeBuilder('missingBscNode', 'BSC').attributes(['connectedMsc': 'NetworkElement=rncNodeMo1']).build()

        and: 'an active MTR subscription with rncNodeMo as explicit node, attachedBscNode as attached node and associated scanners'
            def subscriptionMo = createSubscription([rncNodeMo1, rncNodeMo2], [attachedBscNode1, attachedBscNode2])
            buildScanner(rncNodeMo1.name, 'USERDEF-mtr_sub.Cont.Y.STATS', subscriptionMo, false, STATS)
            buildScanner(attachedBscNode1.name, 'USERDEF-mtr_sub.Cont.Y.STATS', subscriptionMo, false, STATS)
            buildScanner(attachedBscNode2.name, 'USERDEF-mtr_sub.Cont.Y.STATS', subscriptionMo, true, STATS)
            buildScanner(rncNodeMo2.name, 'USERDEF-mtr_sub.Cont.Y.STATS', subscriptionMo, true, STATS)

        and: 'an active scanner for some other nodes'

        when: 'the audit runs'
            mtrAuditor.audit(Arrays.asList(subscriptionMo.getPoId()))

        then: 'one activation event sent'
            1 * activationEvent.execute({it -> it[0].fdn == rncNodeMo1.fdn} as List<Node>, {it -> it.id == subscriptionMo.poId } as Subscription)

        and: 'node initiation called with missing attached node to be added to subscription'
            1 * subscriptionNodeInitiation.activateOrDeactivateNodesOnActiveSubscription({it -> it.id == subscriptionMo.poId} as MtrSubscription, [] as List<Node>, [] as List<Node>)

    }

    def createSubscription(nodes, attachedNodes) {
        dps.subscription()
           .type(SubscriptionType.MTR)
           .administrationState(AdministrationState.ACTIVE)
           .taskStatus(TaskStatus.OK)
           .nodes(nodes)
           .attachedNodes(attachedNodes)
           .build()
    }

}
