package com.ericsson.oss.services.pm.common.systemdefined

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.COUNTER_EVENTS_VALIDATION_APPLICABLE;

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.subscription.capability.SubscriptionCapabilityReader
import com.ericsson.oss.services.pm.generic.NodeService

class SystemDefinedSubscriptionManagerSpec extends SkeletonSpec {

    @ObjectUnderTest
    SystemDefinedSubscriptionManager systemDefinedSubscriptionManager
    @ImplementationInstance
    NodeService nodeService = mock(NodeService)
    @ImplementationInstance
    SubscriptionCapabilityReader systemDefinedCapabilityReader = mock(SubscriptionCapabilityReader)
    @ImplementationInstance
    SubscriptionSystemDefinedAuditRule rule = mock(SubscriptionSystemDefinedAuditRule)

    def "on node retrieval, will not return nodes with invalid OMI (containing string 'valid', empty or null)"() {
        given:
        nodeService.findAllByNeTypeAndPmFunction(_ as List<String>, true) >> nodesWithDiffOssModelIdentity
        def systemDefinedAttributes = new SystemDefinedPmCapabilities()
        systemDefinedAttributes.addTargetType('NODE', '')
        systemDefinedAttributes.put(COUNTER_EVENTS_VALIDATION_APPLICABLE, true)
        when:
        def nodes = systemDefinedSubscriptionManager.getNodes(systemDefinedAttributes, rule)
        then:
        nodes.size() == 1
    }

    def getNodesWithDiffOssModelIdentity() {
        def node1 = new Node()
        node1.ossModelIdentity = '16A-G.1.143'
        def node2 = new Node()
        node2.ossModelIdentity = 'Must be set to a valid value'
        def node3 = new Node()
        node3.ossModelIdentity = ''
        return [node1, node2, node3, new Node()]
    }
}