package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource

import spock.lang.Shared
import spock.lang.Unroll

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.PmBaseSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.MoinstanceSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.StatisticalSubscriptionBuilder
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.pmic.impl.counters.PmCountersLifeCycleResolverImpl
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilityReaderImpl
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.initiation.ejb.CounterConflictServiceImpl
import com.ericsson.oss.services.pm.initiation.rest.response.ConflictingNodeCounterInfo

class SubscriptionResourceCounterConflictsSpec extends PmBaseSpec {

    @ObjectUnderTest
    SubscriptionResource subscriptionResource

    @Inject
    CounterConflictServiceImpl counterConflictService

    @Shared
    def counterErbs1 = 'pmBbmDlBbCapacityUtilization'
    @Shared
    def counterErbs2 = 'pmBbmDlPrbUtilization'
    @Shared
    def counterErbs3 = 'pmBbmDlSeUtilization'
    @Shared
    def counterErbs4 = 'pmAdmNrRrcUnknownArpRatio'

    @Shared
    def groupErbs1 = 'BbProcessingResource'
    @Shared
    def groupErbs2 = 'AdmissionControl'

    @Shared
    def counterSgsn1 = 'bgpPeerInTotalMessages'
    @Shared
    def counterSgsn2 = 'bgpPeerInUpdateElapsedTime'

    @Shared
    String groupSgsn1 = 'SGSN-MME_BgpPeer'

    @Shared
    List<CounterInfo> countersErbs = [
            new CounterInfo(counterErbs1, groupErbs1),
            new CounterInfo(counterErbs2, groupErbs1),
            new CounterInfo(counterErbs3, groupErbs1),
            new CounterInfo(counterErbs4, groupErbs2),
    ]

    @Shared
    ManagedObject nodeErbs1
    @Shared
    ManagedObject nodeErbs2
    @Shared
    ManagedObject nodeSgsn

    def neAdditionalAttributes =['technologyDomain': ['EPS'] as List]

    @ImplementationInstance
    def mockedPmFunctionEnabledWrapper = Mock(PmFunctionEnabledWrapper)

    @ImplementationClasses
    def classes = [PmCapabilityReaderImpl.class, PmCountersLifeCycleResolverImpl.class]

    def setup() {

        nodeErbs1 = nodeUtil.builder('ERBS_1').neType(NetworkElementType.ERBS).ossModelIdentity('18.Q2-J.1.280').attributes(neAdditionalAttributes).build()
        nodeErbs2 = nodeUtil.builder('ERBS_2').neType(NetworkElementType.ERBS).ossModelIdentity('18.Q2-J.1.280').attributes(neAdditionalAttributes).build()
        nodeSgsn = nodeUtil.builder('SGSN_1').neType(NetworkElementType.SGSNMME).ossModelIdentity('16A-CP02').attributes(neAdditionalAttributes).build()
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
    }

    def 'when getConflictingSubscriptionsForCounters is called for a not Statistical subscription will return an empty List'() {
        given: 'persist a CellTraceSubscription'
            def sub = cellTrafficSubscriptionBuilder.name('Test1').nodes(nodeErbs1, nodeErbs2)
                    .events(new EventInfo('Test', 'TestGroup'))
                    .administrativeState(AdministrationState.ACTIVE).build()
        
        when:
            def result = subscriptionResource.getConflictingSubscriptionsForCounters(sub.poId as String).entity as Map<String, Object>

        then:
            result.isEmpty()
    }

    def 'when getConflictingSubscriptionsForCounters is called for a celltrace subscription will return an empty List'() {
        given: 'persist a CellTraceSubscription'
            def sub = cellTraceSubscriptionBuilder.ebsEvents(new EventInfo('EbsEvent', 'EbsEventGroup'))
                    .name('Test1').nodes(nodeErbs1, nodeErbs2)
                    .administrativeState(AdministrationState.ACTIVE).build()
        
        when:
            def result = subscriptionResource.getConflictingSubscriptionsForCounters(sub.poId as String).entity as Map<String, Object>

        then:
            result.isEmpty()
    }

    def 'getCounterConflictsReport is called for a StatisticalSubscription subscription with conflicts will return a formatted report String'() {
        given: 'persist subscription already active in the system'
            def subName = 'SubscriptionAlreadyActive'
            statisticalSubscriptionBuilder.administrativeState(AdministrationState.ACTIVE)
                    .taskStatus(TaskStatus.OK).nodes(nodeErbs1, nodeErbs2).counters(countersErbs).name(subName).build()

        and: 'populate counter conflict caches'
            counterConflictService.addNodesAndCounters([nodeErbs1.fdn, nodeErbs2.fdn] as Set, countersErbs, subName)

        and: 'persist subscription being activated with one common node and one common counter'
            def subBeingActivated = statisticalSubscriptionBuilder.administrativeState(AdministrationState.ACTIVE)
                    .taskStatus(TaskStatus.OK).nodes(nodeErbs1, nodeErbs2)
                    .counters([new CounterInfo(counterErbs1, groupErbs1), new CounterInfo(counterErbs2, groupErbs1)])
                    .name('SubscriptionBeingActivated').build()

        when:
            String result = subscriptionResource.getCounterConflictsReport(subBeingActivated.poId as String).entity as String

        then:
            def resultArray = result.split(',')
            resultArray[0] == 'Report generated for inactive subscription:\nSubscriptionBeingActivated\n\n\nConflicting Subscriptions:\nSubscriptionAlreadyActive\n\n\n\nConflicting Subscription'
            resultArray[1] == 'Conflicting nodes (node1;node2...)'
            resultArray[2] == 'Conflicting group'
            resultArray[3] == 'Conflicting counters (counter1;counter2...)\nSubscriptionAlreadyActive'
            (resultArray[4] == 'ERBS_1;ERBS_2') || (resultArray[4] == 'ERBS_2;ERBS_1')
            resultArray[5] == 'BbProcessingResource'
            (resultArray[6] == 'pmBbmDlPrbUtilization;pmBbmDlBbCapacityUtilization\n') || (resultArray[6] == 'pmBbmDlBbCapacityUtilization;pmBbmDlPrbUtilization\n')
    }

    @Unroll
    def 'getConflictingSubscriptionsForCounters is called for a #subType.getSimpleName() subscription with #hasCommonNodes common node and #hasCommonCounters common counters, #hasConflict conflicts present'() {
        given: 'persist subscription already active in the system'
            def subName = 'SubscriptionAlreadyActive'
            statisticalSubscriptionBuilder.administrativeState(AdministrationState.ACTIVE)
                    .taskStatus(TaskStatus.OK).nodes(nodesActiveSub).counters(countersActiveSub).name(subName).build()

        and: 'populate counter conflict caches'
            def fdnsActiveSub = nodesActiveSub.collect { it.fdn }
            counterConflictService.addNodesAndCounters(fdnsActiveSub as Set, countersActiveSub, subName)


        and: 'persist subscription being activated with one common node and one common counter'
            def subBeingActivated = statisticalSubscriptionBuilder.administrativeState(AdministrationState.ACTIVE)
                    .taskStatus(TaskStatus.OK).nodes(nodesActivatingSub)
                    .counters(countersActivatingSub)
                    .name('SubscriptionBeingActivated').build()

        when:
            def result = subscriptionResource.getConflictingSubscriptionsForCounters(subBeingActivated.poId as String).entity as Map<String, Object>

        then:
            result.keySet().size() == Resultsize
            result.containsKey(subName) == (Resultsize == 1)


        where:
            hasCommonNodes | hasCommonCounters | hasConflict | countersActiveSub                                                                      | nodesActiveSub         | countersActivatingSub                                                                  | nodesActivatingSub     | Resultsize
            ''             | ''                | ''          | countersErbs                                                                           | [nodeErbs1, nodeErbs2] | [new CounterInfo(counterErbs1, groupErbs1), new CounterInfo(counterErbs2, groupErbs1)] | [nodeErbs1, nodeErbs2] | 1
            ''             | 'no'              | 'no'        | [new CounterInfo(counterErbs1, groupErbs1)]                                            | [nodeErbs1, nodeErbs2] | [new CounterInfo(counterErbs2, groupErbs1)]                                            | [nodeErbs1]           | 0
            'no'           | ''                | 'no'        | countersErbs                                                                           | [nodeErbs1]            | countersErbs                                                                           | [nodeErbs2]           | 0
            ''             | ''                | 'no'        | [new CounterInfo(counterErbs1, groupErbs1), new CounterInfo(counterSgsn1, groupSgsn1)] | [nodeErbs1, nodeSgsn]  | [new CounterInfo(counterErbs1, groupErbs1), new CounterInfo(counterSgsn2, groupSgsn1)] | [nodeErbs2, nodeSgsn] | 0
    }

    @Unroll
    def 'when get conflicts is called for #subType.getSimpleName() subscription, it will return empty result if only our subscription\'s entries exist'() {
        given: 'persist subscription and update the counter conflict service with subscription\'s values'
            def subName = 'Test1'
            def subscription = builder.newInstance(dpsUtils).name(subName)
                    .counters([new CounterInfo('pmLicDlCapActual', 'BbProcessingResource')]).nodes(nodeErbs1)
                    .taskStatus(TaskStatus.OK).administrativeState(AdministrationState.ACTIVE).userType(UserType.USER_DEF).build()
            counterConflictService.addNodesAndCounters([nodeErbs1.fdn, nodeErbs2.fdn] as Set, countersErbs, subName)

        when:
            def result = subscriptionResource.getCounterConflicts(subscription.poId as String).entity as ConflictingNodeCounterInfo

        then:
            result.nodes.empty
            result.counterEventInfo.empty

        where:
            builder << [StatisticalSubscriptionBuilder, MoinstanceSubscriptionBuilder]
    }

    def getExpectedReport() {
        return 'Report generated for inactive subscription:\nSubscriptionBeingActivated\n\n\nConflicting Subscriptions:\nSubscriptionAlreadyActive\n\n\n\nConflicting Subscription,Conflicting nodes (node1;node2...),Conflicting group,Conflicting counters (counter1;counter2...)\nSubscriptionAlreadyActive,ERBS_2;ERBS_1;,BbProcessingResource,'
    }
}
