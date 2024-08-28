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
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService

class ResourceSubscriptionValidatorSpec extends CdiSpecification {

    @MockedImplementation
    private PmCapabilityModelService pmCapabilityModelService

    @MockedImplementation
    protected SubscriptionCommonValidator subscriptionCommonValidator

    @ObjectUnderTest
    ResourceSubscriptionValidator objectUnderTest

    @MockedImplementation
    ResourceSubscription resourceSubscription

    def supportedTechnologyDomains = ['Some Technology Domain']

    def 'Import validation for subscription with nodes and supported technology domain capability'() {
        given: 'subscription has nodes (even an empty list)'
            def nodes = []
            resourceSubscription.getNodes() >> nodes
        and: 'supported technology domain is defined'
            pmCapabilityModelService.getSubscriptionAttributesGlobalCapabilities(resourceSubscription, _) >> [supportedTechnologyDomains: supportedTechnologyDomains]

        when: 'the import is validated'
            objectUnderTest.validateImport(resourceSubscription)

        then:
            1 * subscriptionCommonValidator.validateApplicableNodesForSubscription(nodes, supportedTechnologyDomains)
    }

    def 'Import validation for subscription with no supported technology domains capability'() {
        given: 'subscription has nodes (even an empty list)'
            resourceSubscription.getNodes() >> []
        and: 'no supported technology domain capability is defined'
            pmCapabilityModelService.getSubscriptionAttributesGlobalCapabilities(resourceSubscription, _) >> [:]

        when: 'the import is validated'
            objectUnderTest.validateImport(resourceSubscription)

        then: '(only) the pm function is validated'
            1 * subscriptionCommonValidator.validatePmFunction(_)
            0 * subscriptionCommonValidator.validateApplicableNodesForSubscription(_, _)
    }

    def 'Import validation for subscription with no nodes defined'() {
        given: 'subscription has no nodes defined'
            resourceSubscription.getNodes() >> null
        and: 'supported technology domain is defined'
            pmCapabilityModelService.getSubscriptionAttributesGlobalCapabilities(resourceSubscription, _) >> [supportedTechnologyDomains: supportedTechnologyDomains]

        when: 'the import is validated'
            objectUnderTest.validateImport(resourceSubscription)

        then: '(only) the pm function is validated'
            1 * subscriptionCommonValidator.validatePmFunction(_)
            0 * subscriptionCommonValidator.validateApplicableNodesForSubscription(_, _)
    }
}
