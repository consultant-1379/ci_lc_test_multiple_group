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
package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource

import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.ACTIVE
import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.INACTIVE
import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.UPDATING
import static com.ericsson.oss.pmic.dto.subscription.enums.UserType.USER_DEF

import spock.lang.Unroll

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.security.accesscontrol.EAccessControl
import com.ericsson.oss.pmic.cdi.test.util.PmBaseSpec
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.impl.counters.PmCountersLifeCycleResolverImpl
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilityReaderImpl
import com.ericsson.oss.services.pm.adjuster.impl.SubscriptionMetaDataService
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.ebs.utils.EbsStreamInfoResolver
import com.ericsson.oss.services.pm.initiation.api.ReadPMICConfigurationLocal
import com.ericsson.oss.services.pm.initiation.ejb.PMICConfigParameter
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService
import com.ericsson.oss.services.pm.services.exception.ValidationException
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService

class SubscriptionResourceUpdateSingleSubscriptionSpec extends PmBaseSpec {

    EventInfo VALID_EVENT = new EventInfo('INTERNAL_EVENT_UE_CAPABILITY', 'CCTR')
    CounterInfo VALID_COUNTER = new CounterInfo('pmAdmNrRrcUnknownArpRatio', 'AdmissionControl')

    @ObjectUnderTest
    SubscriptionResource subscriptionResource

    Date persistenceTime = new Date()

    @MockedImplementation
    EAccessControl eAccessControl

    @MockedImplementation
    SubscriptionMetaDataService subscriptionMetaDataService

    @Inject
    SubscriptionReadOperationService subscriptionReadOperationService

    @MockedImplementation
    ReadPMICConfigurationLocal configurationLocal

    @MockedImplementation
    EbsStreamInfoResolver ebsStreamInfoResolver

    @MockedImplementation
    PmFunctionEnabledWrapper mockedPmFunctionEnabledWrapper

    @MockedImplementation
    PmCapabilityModelService mockedPmCapabilityModelService

    @ImplementationClasses
    def classes = [PmCapabilityReaderImpl.class, PmCountersLifeCycleResolverImpl.class]

    def setup() {
        eAccessControl.isAuthorized(_, _) >> true
        configurationLocal.getConfigParamValue(PMICConfigParameter.MAXNOOFMOINSTANCEALLOWED.name()) >> '999'
        configurationLocal.getConfigParamValue(PMICConfigParameter.MAXNOOFCBSALLOWED.name()) >> '2'
        ebsStreamInfoResolver.getStreamingDestination(_) >> [new StreamInfo('3.3.3.3', 10101)]
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
        mockedPmCapabilityModelService.getSupportedCounterLifeCycles(_) >> ['CURRENT', 'DEPRECATED']
        subscriptionMetaDataService.getCorrectEvents(_, _, _) >> [VALID_EVENT]
        subscriptionMetaDataService.getEsnApplicableEvents(_) >> [VALID_EVENT]
        subscriptionMetaDataService.getCorrectCounters(_, _, _, _, _, _) >> [VALID_COUNTER]
    }

    @Unroll
    def 'when activating first statistical subscription with rop=#ropPeriod, adminStatus=#adminStatus and 2 nodes, expectedException=#exception'() {
        given: 'Some nodes involved with the subscription to activate'
            def numOfNodes = 2
            def nodes = new ManagedObject[numOfNodes]
            for (int i = 0; i < numOfNodes; ++i) {
                nodes[i] = createNode('TestNode00' + i, NetworkElementType.RADIONODE.getNeTypeString())
            }

        and: 'An inactive subscription in DPS'
            def subMo = createSubscriptionMoBuilderWithNameAndState(SubscriptionType.STATISTICAL, adminStatus).nodes(nodes)
                .counters(VALID_COUNTER as CounterInfo[])
                .rop(ropPeriod)
                .description('not blank')
                .build()

        and: 'Subscription value to update'
            def originalSubscription = subscriptionReadOperationService.findOneById(subMo.poId, true)
            def subscription = cloneSubscriptionAddingNode(originalSubscription, NetworkElementType.RADIONODE)

        when: 'The subscription is activated'
            try {
                subscriptionResource.updateSingleSubscription(Long.toString(subMo.poId), subscription)
            } catch (final ValidationException e) {
                assert exception: "Unexpected ValidationException"
            }
            def updatedSubscription = subscriptionReadOperationService.findOneById(subMo.poId, true)

        then: 'subscription is activated'
            expectedAdminStatus == updatedSubscription.administrationState
            expectedNumNodes == updatedSubscription.nodes.size()

        where:
            ropPeriod             | adminStatus || exception | expectedAdminStatus | expectedNumNodes
            RopPeriod.ONE_MIN     | INACTIVE    || false     | INACTIVE            | 3
            RopPeriod.FIVE_MIN    | INACTIVE    || false     | INACTIVE            | 3
            RopPeriod.FIFTEEN_MIN | INACTIVE    || false     | INACTIVE            | 3
            RopPeriod.THIRTY_MIN  | INACTIVE    || false     | INACTIVE            | 3
            RopPeriod.ONE_HOUR    | INACTIVE    || false     | INACTIVE            | 3
            RopPeriod.TWELVE_HOUR | INACTIVE    || false     | INACTIVE            | 3
            RopPeriod.ONE_DAY     | INACTIVE    || false     | INACTIVE            | 3
            RopPeriod.ONE_MIN     | ACTIVE      || true      | ACTIVE              | 2
            RopPeriod.FIVE_MIN    | ACTIVE      || true      | ACTIVE              | 2
            RopPeriod.FIFTEEN_MIN | ACTIVE      || false     | UPDATING            | 3
            RopPeriod.THIRTY_MIN  | ACTIVE      || true      | ACTIVE              | 2
            RopPeriod.ONE_HOUR    | ACTIVE      || true      | ACTIVE              | 2
            RopPeriod.TWELVE_HOUR | ACTIVE      || true      | ACTIVE              | 2
            RopPeriod.ONE_DAY     | ACTIVE      || false     | UPDATING            | 3
    }

    @Unroll
    def 'when activating another statistical subscription with rop=#ropPeriod and #numOfNodes nodes, expectedException=#exception'() {
        given: 'Some nodes and subscriptions already configured'
            def neType = NetworkElementType.RADIONODE
            alreadyConfiguredSubscription.eachWithIndex { rop, adminState, index ->
                def node = createNode('TestNode00' + index, neType.getNeTypeString())
                createSubscriptionMoBuilderWithNameAndState(SubscriptionType.STATISTICAL, adminState, 'sub00' + index).nodes(node)
                    .counters(VALID_COUNTER as CounterInfo[])
                    .rop(rop)
                    .description('not blank')
                    .build()
            }

        and: 'Some nodes involved with the subscription to activate'
            def node = createNode('NewNode00', neType.getNeTypeString())

        and: 'A #adminStatus subscription in DPS'
            def subMo = createSubscriptionMoBuilderWithNameAndState(SubscriptionType.STATISTICAL, adminStatus).nodes(node)
                .counters(VALID_COUNTER as CounterInfo[])
                .rop(ropPeriod)
                .description('not blank')
                .build()

        and: 'Subscription value to update'
            def originalSubscription = subscriptionReadOperationService.findOneById(subMo.poId, true)
            def subscription = cloneSubscriptionAddingNode(originalSubscription, neType)

        when: 'The subscription is activated'
            try {
                subscriptionResource.updateSingleSubscription(Long.toString(subMo.poId), subscription)
            } catch (final ValidationException e) {
                assert exception: "Unexpected ValidationException: " + e.getMessage()
            }
            def updatedSubscription = subscriptionReadOperationService.findOneById(subMo.poId, true)

        then: 'subscription is activated'
            expectedAdminStatus == updatedSubscription.administrationState
            expectedNumNodes == updatedSubscription.nodes.size()

        where:
            alreadyConfiguredSubscription                                       | ropPeriod             | adminStatus || exception | expectedAdminStatus | expectedNumNodes
            [(RopPeriod.ONE_MIN): (ACTIVE), (RopPeriod.FIVE_MIN): (INACTIVE)]   | RopPeriod.ONE_MIN     | INACTIVE    || false     | INACTIVE            | 2
            [(RopPeriod.ONE_MIN): (ACTIVE), (RopPeriod.FIVE_MIN): (ACTIVE)]     | RopPeriod.ONE_MIN     | INACTIVE    || false     | INACTIVE            | 2
            [(RopPeriod.FIFTEEN_MIN): (ACTIVE), (RopPeriod.FIVE_MIN): (ACTIVE)] | RopPeriod.ONE_MIN     | INACTIVE    || false     | INACTIVE            | 2
            [(RopPeriod.ONE_MIN): (ACTIVE), (RopPeriod.FIVE_MIN): (ACTIVE)]     | RopPeriod.FIFTEEN_MIN | INACTIVE    || false     | INACTIVE            | 2
            [(RopPeriod.ONE_MIN): (ACTIVE), (RopPeriod.FIVE_MIN): (ACTIVE)]     | RopPeriod.ONE_DAY     | INACTIVE    || false     | INACTIVE            | 2
            [(RopPeriod.ONE_MIN): (ACTIVE), (RopPeriod.FIVE_MIN): (INACTIVE)]   | RopPeriod.ONE_MIN     | ACTIVE      || true      | ACTIVE              | 1
            [(RopPeriod.ONE_MIN): (ACTIVE), (RopPeriod.FIVE_MIN): (ACTIVE)]     | RopPeriod.ONE_MIN     | ACTIVE      || true      | ACTIVE              | 1
            [(RopPeriod.FIFTEEN_MIN): (ACTIVE), (RopPeriod.FIVE_MIN): (ACTIVE)] | RopPeriod.ONE_MIN     | ACTIVE      || true      | ACTIVE              | 1
            [(RopPeriod.ONE_MIN): (ACTIVE), (RopPeriod.FIVE_MIN): (ACTIVE)]     | RopPeriod.FIFTEEN_MIN | ACTIVE      || false     | UPDATING            | 2
            [(RopPeriod.ONE_MIN): (ACTIVE), (RopPeriod.FIVE_MIN): (ACTIVE)]     | RopPeriod.ONE_DAY     | ACTIVE      || false     | UPDATING            | 2
    }

    def createSubscriptionMoBuilderWithNameAndState(final SubscriptionType type,
                                                    final AdministrationState state = INACTIVE,
                                                    final String name = 'sub1') {
        dps.subscription()
            .type(type)
            .name(name)
            .administrationState(state)
            .persistenceTime(persistenceTime)
            .userType(USER_DEF)
    }

    def createNode(final String name, final String neType, final String ossModelIdentity = '16A-R22AC') {
        def node = dps.node()
            .name(name)
            .neType(neType)
            .ossModelIdentity(ossModelIdentity)
            .pmEnabled(true)
            .build()

        return node
    }

    def cloneSubscriptionAddingNode(final Subscription originalSubscription, final NetworkElementType neType) {
        def subscription = new StatisticalSubscription()
        subscription.id = originalSubscription.id
        subscription.fdn = originalSubscription.fdn
        subscription.name = originalSubscription.name
        subscription.administrationState = originalSubscription.administrationState
        subscription.rop = originalSubscription.rop
        subscription.counters = originalSubscription.counters
        subscription.userType = originalSubscription.userType
        subscription.type = originalSubscription.type
        subscription.persistenceTime = originalSubscription.persistenceTime

        def nodeInDps = createNode('AddedNode00', neType.getNeTypeString())
        List<Node> nodes = new ArrayList<Node>(originalSubscription.nodes)
        Node node = new Node()
        node.id = nodeInDps.poId
        node.name = nodeInDps.name
        node.fdn = nodeInDps.fdn
        node.neType = neType.getNeTypeString()
        node.ossModelIdentity = '16A-R22AC'
        nodes.add(node)
        subscription.nodes = nodes

        return subscription
    }
}
