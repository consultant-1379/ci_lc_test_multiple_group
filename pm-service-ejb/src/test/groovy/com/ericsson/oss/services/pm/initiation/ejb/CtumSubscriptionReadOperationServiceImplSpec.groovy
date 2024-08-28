/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.ejb

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.subscription.CtumSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService

/***
 * This class will test Ctum Subscription Add and Delete Operation
 */
class CtumSubscriptionReadOperationServiceImplSpec extends SkeletonSpec {

    @ObjectUnderTest
    CtumSubscriptionServiceImpl ctumSubscriptionService

    @Inject
    SubscriptionReadOperationService subscriptionReadOperationService

    def 'On CTUM audit, verify subscription is created and deleted'() {
        given: 'Add SGSN-MME nodes in DPS'
        def nodes = [
                nodeUtil.builder("SGSN-16A-V1-CP02200").neType("SGSN-MME").ossModelIdentity('6607-651-025').pmEnabled(true).build(),
                nodeUtil.builder("SGSN-16A-V1-CP02201").neType("SGSN-MME").ossModelIdentity('6607-651-025').pmEnabled(true).build(),
                nodeUtil.builder("SGSN-16A-V1-CP02202").neType("SGSN-MME").ossModelIdentity('6607-651-025').pmEnabled(true).build(),
                nodeUtil.builder("SGSN-16A-V1-CP02203").neType("SGSN-MME").ossModelIdentity('6607-651-025').pmEnabled(true).build(),
                nodeUtil.builder("SGSN-16A-V1-CP02204").neType("SGSN-MME").ossModelIdentity('6607-651-025').pmEnabled(true).build()
        ]

        when: 'When CTUM audit get called, added nodes should aligned to CTUM subscription'
        ctumSubscriptionService.ctumAudit()
        and: 'Subscription can be retrieved from DPS'
        def sub = liveBucket().findMoByFdn("CtumSubscription=CTUM")

        then: 'Subscription Object should exist in DPS and is of type CTUM'
        sub.getType() == CtumSubscription.CTUM_SUBSCRIPTION_MODEL_TYPE

        when: 'Delete All SGSN-MME nodes from DPS'
        nodes.each { liveBucket().deletePo(it) }

        and: 'ON CTUM AUDIT, DPS should delete subscription Object'
        ctumSubscriptionService.ctumAudit()

        then: 'CTUM Subscription no longer exist in DPS'
        liveBucket().findMoByFdn("CtumSubscription=CTUM") == null
    }

    def "on CTUM audit, subscription will be created if nodes exist and subscription does not"() {
        given:
        nodeUtil.builder("SGSN-16A-V1-CP02200").neType("SGSN-MME").ossModelIdentity('6607-651-025').pmEnabled(true).build()
        nodeUtil.builder("SGSN-16A-V1-CP02201").neType("SGSN-MME").ossModelIdentity('6607-651-025').pmEnabled(false).build()
        when:
        ctumSubscriptionService.ctumAudit()
        then:
        liveBucket().findMoByFdn("CtumSubscription=CTUM") != null
    }

    def "on CTUM audit, subscription will not be created if nodes exist but have pm function off and subscription does not"() {
        given:
        nodeUtil.builder("SGSN-16A-V1-CP02200").neType("SGSN-MME").ossModelIdentity('6607-651-025').pmEnabled(false).build()
        when:
        ctumSubscriptionService.ctumAudit()
        then:
        liveBucket().findMoByFdn("CtumSubscription=CTUM") == null
    }

    def "on CTUM audit, subscription will not be created if nodes exist and subscription exists"() {
        when: "CTUM audit get called"
        ctumSubscriptionService.ctumAudit()
        then: "no CTUM subscription will be created"
        liveBucket().findMoByFdn("CtumSubscription=CTUM") == null
        when: "Adding SGSN-MME node in DPS and calling CTUM audit"
        nodeUtil.builder("SGSN-16A-V1-CP02200").neType("SGSN-MME").ossModelIdentity('6607-651-025')
                .pmEnabled(true).build()
        ctumSubscriptionService.ctumAudit()
        then: "CTUM subscription will be created"
        liveBucket().findMoByFdn("CtumSubscription=CTUM") != null
        when: "Adding another SGSN-MME node in DPS and calling CTUM audit again"
        nodeUtil.builder("SGSN-16A-V1-CP02201").neType("SGSN-MME").ossModelIdentity('6607-651-025')
                .pmEnabled(true).build()
        ctumSubscriptionService.ctumAudit()
        then: "there will be still a single CTUM subscription"
        subscriptionReadOperationService.findAllIdsBySubscriptionType(SubscriptionType.CTUM).size() == 1
    }

    def "on CTUM audit, subscription will be deleted if nodes exist but have pm function off"() {
        given:
        ctumSubscriptionBuilder.name("CTUM").build()
        nodeUtil.builder("SGSN-16A-V1-CP02200").neType("SGSN-MME").ossModelIdentity('6607-651-025').pmEnabled(false).build()
        when:
        ctumSubscriptionService.ctumAudit()
        then:
        liveBucket().findMoByFdn("CtumSubscription=CTUM") == null
    }

    def "on CTUM audit, subscription will be deleted if nodes no longer exist "() {
        given:
        ctumSubscriptionBuilder.name("CTUM").build()
        when:
        ctumSubscriptionService.ctumAudit()
        then:
        liveBucket().findMoByFdn("CtumSubscription=CTUM") == null
    }

    def "on CTUM audit, subscription numberOfNodes attribute will be correctly updated "() {
        given: 'Add SGSN-MME node in DPS'
        nodeUtil.builder("SGSN-16A-V1-CP02200").neType("SGSN-MME").ossModelIdentity('6607-651-025').pmEnabled(true).build()
        when: 'When CTUM audit get called, added nodes should aligned to CTUM subscription'
        ctumSubscriptionService.ctumAudit()
        then: 'Ctum Subscription can be retrieved in dps and numberOfNodes attribute is correctly set'
        ((CtumSubscription) subscriptionReadOperationService.findOneByExactName('CTUM', false)).getNumberOfNodes() == 1
        when: 'Add further SGSN-MME node on dps'
        nodeUtil.builder("SGSN-16A-V1-CP02201").neType("SGSN-MME").ossModelIdentity('6607-651-025').pmEnabled(true).build()
        and: 'CTUM audit get called'
        ctumSubscriptionService.ctumAudit()
        then: 'Subscription numberOfNodes attribute is correctly updated'
        ((CtumSubscription) subscriptionReadOperationService.findOneByExactName('CTUM', false)).getNumberOfNodes() == 2
    }


    def "on CTUM audit, will not set nodes with invalid OMI (containing string 'valid', empty or null)"() {
        given: "Nodes exist with different OMIs"
        nodeUtil.builder("SGSN-16A-V1-CP02200").neType("SGSN-MME").ossModelIdentity('6607-651-025').pmEnabled(true).build()
        nodeUtil.builder("SGSN-16A-V1-CP02201").neType("SGSN-MME").ossModelIdentity('Must be set to a valid value').pmEnabled(true).build()
        nodeUtil.builder("SGSN-16A-V1-CP02202").neType("SGSN-MME").ossModelIdentity('').pmEnabled(true).build()
        nodeUtil.builder("SGSN-16A-V1-CP02203").neType("SGSN-MME").ossModelIdentity(null).pmEnabled(true).build()
        when: "Audit is triggered"
        ctumSubscriptionService.ctumAudit();
        then: "Subscription numberOfNodes attribute is correctly updated for vaild nodes"
        ((CtumSubscription) subscriptionReadOperationService.findOneByExactName('CTUM', false)).getNumberOfNodes() == 1
    }
}
