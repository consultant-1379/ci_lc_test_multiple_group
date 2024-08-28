package com.ericsson.oss.services.pm.initiation.validators
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

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.services.pm.generic.NodeService
import com.ericsson.oss.services.pm.services.exception.ValidationException
import spock.lang.Unroll

class SubscriptionCommonValidatorSpec extends CdiSpecification {

    @MockedImplementation
    private NodeService nodeService;

    @ObjectUnderTest
    SubscriptionCommonValidator objectUnderTest

    static def FDN_OF_NODE_WITH_PM_ENABLED = 'node with pm enabled'
    static def FDN_OF_NODE_WITH_PM_DISABLED = 'node with pm disabled'
    static def nodeWithPmEnabled = new Node()
    static def nodeWithPmDisabled = new Node()
    def technologyDomainsSupportedBySubscription = ['required technology domain']

    def setup() {
        nodeWithPmEnabled.setFdn(FDN_OF_NODE_WITH_PM_ENABLED)
        nodeService.isPmFunctionEnabled(FDN_OF_NODE_WITH_PM_ENABLED) >> true
        nodeWithPmDisabled.setFdn(FDN_OF_NODE_WITH_PM_DISABLED)
        nodeService.isPmFunctionEnabled(FDN_OF_NODE_WITH_PM_DISABLED) >> false
    }

    def 'Validate PM function with node with PM enabled'() {
        given: 'a node with pm enabled'
            def nodes = [nodeWithPmEnabled]

        when: 'nodes PM function is validated'
            objectUnderTest.validatePmFunction(nodes)

        then: 'no exception is thrown'
            noExceptionThrown()
    }

    def 'Validate PM function with node with PM disabled'() {
        given: 'at least one node with pm disabled'
            def nodes = [nodeWithPmEnabled, nodeWithPmDisabled]

        when: 'nodes PM function is validated'
            objectUnderTest.validatePmFunction(nodes)

        then: 'a validation exception is thrown'
            thrown ValidationException
    }

    @Unroll
    def 'Validate technology domain and PM function fails for #nodeFdn and technology domains #nodeTechnologyDomains'() {
        given: 'a #nodeFdn and technology domains #nodeTechnologyDomains'
            def node = new Node()
            node.setFdn(nodeFdn)
            node.setTechnologyDomain(nodeTechnologyDomains)
            node.setName(nodeName)

        when: 'nodes technology domain and PM function are validated'
            objectUnderTest.validateApplicableNodesForSubscription([node], technologyDomainsSupportedBySubscription)

        then: 'a validation exception is thrown'
        ValidationException exception = thrown()
        if(exception != null) exception.message == "Unable to import subscription :Nodes with unsupported technology domain:["+nodeName+"]"

        where: 'node has the following configuration'
            nodeName | nodeFdn                      | nodeTechnologyDomains
            "node1"  |FDN_OF_NODE_WITH_PM_ENABLED  | ['other technology domain']
            "node2"  |FDN_OF_NODE_WITH_PM_ENABLED  | []
            "node3"  |FDN_OF_NODE_WITH_PM_DISABLED | ['required technology domain']
    }

    @Unroll
    def 'Validate technology domain and PM function passes for  #nodeFdn and technology domains #nodeTechnologyDomains'() {
        given: 'a #nodeFdn and technology domains #nodeTechnologyDomains'
            def node = new Node()
            node.setFdn(nodeFdn)
            node.setTechnologyDomain(nodeTechnologyDomains)

        when: 'nodes technology domain and PM function are validated'
            objectUnderTest.validateApplicableNodesForSubscription([node], technologyDomainsSupportedBySubscription)

        then: 'no exception is thrown'
            noExceptionThrown()

        where: 'node has the following configuration'
            nodeFdn                     | nodeTechnologyDomains
            FDN_OF_NODE_WITH_PM_ENABLED | ['required technology domain']
            FDN_OF_NODE_WITH_PM_ENABLED | ['other technology domain', 'required technology domain']
    }

}
