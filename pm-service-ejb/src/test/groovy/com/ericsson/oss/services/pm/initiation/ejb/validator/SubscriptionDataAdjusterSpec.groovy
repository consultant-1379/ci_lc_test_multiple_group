/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.ejb.validator

import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_AND_EBSL_STREAM
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.EBSL_STREAM
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.NRAN_EBSN_STREAM
import static com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType.FILE
import static com.ericsson.oss.pmic.dto.subscription.enums.UserType.SYSTEM_DEF
import static com.ericsson.oss.pmic.dto.subscription.enums.UserType.USER_DEF

import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.subscription.*
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory
import com.ericsson.oss.pmic.dto.subscription.enums.ResSpreadingFactor
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.impl.counters.PmCountersLifeCycleResolverImpl
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilityReaderImpl
import com.ericsson.oss.services.pm.PmServiceEjbFullSpec
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterImpl
import com.ericsson.oss.services.pm.generic.SystemPropertiesService
import com.ericsson.oss.services.pm.initiation.model.metadata.mtr.PmMtrLookUp
import com.ericsson.oss.services.pm.initiation.model.metadata.res.PmResLookUp
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventType
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService

class SubscriptionDataAdjusterSpec extends PmServiceEjbFullSpec {

    @ObjectUnderTest
    SubscriptionDataAdjusterImpl objectUnderTest

    def static INVALID_EVENT = new EventInfo('INVALID_EVENT', 'INVALID_EVENT_GROUP')
    def static VALID_EVENT = new EventInfo('INTERNAL_EVENT_ANR_CONFIG_MISSING', 'INTERNAL')
    def static INVALID_COUNTER = new CounterInfo('Invalid Counter', 'Invalid Counter Group')
    def static VALID_EBS_COUNTER = new CounterInfo('MME Periodic TAU REJECT CC95', 'TAI')
    def static MSC_NODE_06 = MscNode('MSC-BC-IS', 'NetworkElement=MSC06', 'MSC06')

    @MockedImplementation
    PmResLookUp mockedPmResLookUp

    @MockedImplementation
    PmMtrLookUp mockedPmMtrLookUp

    @MockedImplementation
    private SystemPropertiesService systemPropertiesService

    @ImplementationInstance
    SubscriptionReadOperationService subscriptionReadOperationService  = Stub(SubscriptionReadOperationService)

    @ImplementationClasses
    def implementationClasses = [PmCapabilityReaderImpl.class, PmCountersLifeCycleResolverImpl.class]


    def setup() {
        mockedPmResLookUp.fetchAttachedNodes(_, _, _, _) >> [RadioNode()]
        systemPropertiesService.findAllMoInstancesOnManagedElementWithNamespaceAndType(_, _, _, _) >> MoInstanceLiist()
    }

    def 'When validating a statistical subscription with One Valid NodeCounter and One Invalid NodeCounter then Only Valid NodeCounter is Retained'() {
        given: 'a statistical subscription with 1 valid and 1 invalid counter counter'
            def validCounter = new CounterInfo('ipv6InReceives', 'SGSN-MME_IPv6_RouterInstance')
            def subscription = new StatisticalSubscription()
            subscription.counters = [INVALID_COUNTER, validCounter]
            subscription.type = SubscriptionType.STATISTICAL
            subscription.name ='SubscriptionTestName'
            subscription.userType = SYSTEM_DEF
            subscription.nodes = [SgsnNode()]

        when: 'the subscription is validated'
            objectUnderTest.correctSubscriptionData(subscription)

        then: 'no exception should be thrown'
            noExceptionThrown()
            subscription.counters.size() == 1
            subscription.counters == [validCounter]
    }

    def 'When validating an ebm subscription with One Valid OSSCounter and One Invalid OSSCounter then Only Valid OSSCounter is Retained'() {
        given: 'an ebm subscription with 1 valid and 1 invalid counter'
            def subscription = new EbmSubscription()
            subscription.setEbsCounters([INVALID_COUNTER, VALID_EBS_COUNTER])
            subscription.setName('SubscriptionTestName')
            subscription.setUserType(SYSTEM_DEF)
            subscription.setOutputMode(FILE)
            subscription.setNodes([SgsnNode()])
            subscription.setType(SubscriptionType.EBM)
            subscription.setEbsEnabled(true)

        when: 'the subscription is validated'
            objectUnderTest.correctSubscriptionData(subscription)

        then: 'no exception should be thrown'
            noExceptionThrown()
            subscription.ebsCounters.size() == 1
            subscription.ebsCounters == [VALID_EBS_COUNTER]
    }

    def 'When validating a celltrace subscription with One Valid OSSCounter and One Invalid OSSCounter then Only Valid OSSCounter is Retained'() {
        given: 'a celltrace ebs subscription with 1 valid and 1 invalid counter'
            def subscription = new CellTraceSubscription()
            subscription.setEbsCounters([INVALID_COUNTER, VALID_EBS_COUNTER])
            subscription.setName('SubscriptionTestName')
            subscription.setUserType(SYSTEM_DEF)
            subscription.setOutputMode(FILE)
            subscription.setNodes([SgsnNode()])
            subscription.setType(SubscriptionType.CELLTRACE)
            subscription.setCellTraceCategory(CellTraceCategory.CELLTRACE_AND_EBSL_FILE)

        when: 'the subscription is validated'
            objectUnderTest.correctSubscriptionData(subscription)

        then: 'no exception should be thrown'
            noExceptionThrown()
            subscription.ebsCounters.size() == 1
            subscription.ebsCounters == [VALID_EBS_COUNTER]
    }

    @Unroll
    def 'When validating a subscription of type=#subscriptionType with One Valid Event and One Invalid Event then Only Valid Event is Retained'() {
        given: 'a #subscriptionType subscription with 1 valid and 1 invalid event'
            def subscription = subscriptionType.identifier.newInstance() as EventSubscription
            subscription.setEvents([VALID_EVENT, INVALID_EVENT])
            subscription.setName('SubscriptionTestName')
            subscription.setUserType(USER_DEF)
            subscription.setOutputMode(FILE)
            subscription.setNodes([SgsnNode(), ErbsNode()])
            subscription.setType(subscriptionType)

        when: 'the subscription is validated'
            objectUnderTest.correctSubscriptionData(subscription)

        then: 'no exception should be thrown'
            noExceptionThrown()
            subscription.events == [VALID_EVENT]

        where:
            subscriptionType << [SubscriptionType.EBM, SubscriptionType.CELLTRACE, SubscriptionType.CONTINUOUSCELLTRACE, SubscriptionType.UETR]
    }

    @Unroll
    def 'When validating a CellTrace subscription, only valid events are retained'() {
        given: 'a celltrace subscription with ebs streaming =#streamClusterDeployed'
            def subscription = new CellTraceSubscription()
            subscription.setEvents(events)
            if (streamClusterDeployed) {
                subscription.setEbsEvents(ebsEvents)
                subscription.setCellTraceCategory(CellTraceCategory.CELLTRACE_AND_EBSL_STREAM)
            } else {
                subscription.setCellTraceCategory(CellTraceCategory.CELLTRACE_AND_EBSL_FILE)
            }
            subscription.setName('EBSCellTraceSubscriptionTestName')
            subscription.setUserType(USER_DEF)
            subscription.setOutputMode(FILE)
            subscription.setNodes([ErbsNode()])
            subscription.setType(SubscriptionType.CELLTRACE)

        when: 'the subscription is validated'
            objectUnderTest.correctSubscriptionData(subscription)

        then: 'no exception should be thrown'
            noExceptionThrown()
            eventsResult == subscription.events
            ebsEventsResult == subscription.ebsEvents

        where:
            streamClusterDeployed   | events                         | ebsEvents                      || eventsResult               | ebsEventsResult
            true                    | [VALID_EVENT, INVALID_EVENT]   | [VALID_EVENT, INVALID_EVENT]   || [VALID_EVENT]              | [VALID_EVENT]
            true                    | [VALID_EVENT, VALID_EVENT]     | [VALID_EVENT, VALID_EVENT]     || [VALID_EVENT, VALID_EVENT] | [VALID_EVENT, VALID_EVENT]
            true                    | [INVALID_EVENT, INVALID_EVENT] | [INVALID_EVENT, INVALID_EVENT] || []                         | []
            true                    | [VALID_EVENT, INVALID_EVENT]   | []                             || [VALID_EVENT]              | []
            true                    | []                             | [VALID_EVENT, INVALID_EVENT]   || []                         | [VALID_EVENT]
            false                   | [VALID_EVENT, INVALID_EVENT]   | [VALID_EVENT, INVALID_EVENT]   || [VALID_EVENT]              | []
            false                   | [VALID_EVENT, VALID_EVENT]     | [VALID_EVENT, VALID_EVENT]     || [VALID_EVENT, VALID_EVENT] | []
            false                   | [INVALID_EVENT, INVALID_EVENT] | [INVALID_EVENT, INVALID_EVENT] || []                         | []
            false                   | [VALID_EVENT, INVALID_EVENT]   | []                             || [VALID_EVENT]              | []
            false                   | []                             | [VALID_EVENT, INVALID_EVENT]   || []                         | []
    }

    @Unroll
    def 'When adjusting a CellTrace subscription with node of category ESN, that ebsEvents is automatically populated based on integrationpoint'() {
        given: 'CellTrace subscription with nodes and without ebsEvents'
            def subscription = new CellTraceSubscription()
            subscription.setName('EBSCellTraceSubscriptionTestName')
            subscription.setType(SubscriptionType.CELLTRACE)
            subscription.setCellTraceCategory(CellTraceCategory.ESN)
            subscription.setEbsEvents([] as List<EventInfo>)
            subscription.setUserType(USER_DEF)
            subscription.setNodes(nodes)

        when: 'the subscription is adjusted'
            objectUnderTest.correctSubscriptionData(subscription)

        then: 'a CellTrace ESN subscription should be auto populated with ebsEvents'
            noExceptionThrown()
            subscription.ebsEvents.size() == expectedEbsEventsCount

        where:
            nodes                                 || expectedEbsEventsCount
            [ErbsNode()]                          || 99
            [RadioNode()]                         || 99
            [PicoNode()]                          || 65
            [ErbsNode(), ErbsNodeInvalid()]       || 99
            [ErbsNode(), PicoNode()]              || 114
            [ErbsNode(), RadioNode()]             || 99
            [RadioNode(), PicoNode()]             || 114
            [ErbsNode(), RadioNode(), PicoNode()] || 114
            [ErbsNodeInvalid()]                   || 0
            []                                    || 0
    }

    @Unroll
    def 'When adjusting a CellTrace EBS Stream subscription, the correct port is used for ebs stream info'() {
        given: 'CellTrace subscription with nodes and without ebsEvents'
            def subscription = new CellTraceSubscription()
            subscription.setName('Test')
            subscription.setType(SubscriptionType.CELLTRACE)
            subscription.setCellTraceCategory(subscriptionCategory)
            subscription.setEbsEvents([] as List<EventInfo>)
            subscription.setUserType(USER_DEF)
            subscription.setNodes([RadioNode()])

        when: 'the subscription is adjusted'
            objectUnderTest.correctSubscriptionData(subscription)

        then: 'a CellTrace ESN subscription should be auto populated with ebsEvents'
            noExceptionThrown()
            subscription.ebsStreamInfoList.every{it.port == expectedPort}
        where:
            subscriptionCategory            || expectedPort
            CELLTRACE_AND_EBSL_STREAM       || 10101
            EBSL_STREAM                     || 10101
            NRAN_EBSN_STREAM                || 10102
            CELLTRACE_NRAN_AND_EBSN_STREAM  || 10102
    }

    @Unroll
    def 'When validating a ContinuousCellTrace subscription events should be corrected'() {
        given: 'a CCTR subscription with 1 valid and 1 invalid event'
            def subscription = new ContinuousCellTraceSubscription()
            subscription.setEvents([VALID_EVENT, INVALID_EVENT])
            subscription.setEbsEvents([])
            subscription.setName('ContinuousCellTraceSubscription')
            subscription.setUserType(SYSTEM_DEF)
            subscription.setOutputMode(FILE)
            subscription.setNodes([ErbsNode()])
            subscription.setType(SubscriptionType.CONTINUOUSCELLTRACE)

        when: 'the subscription is validated'
            objectUnderTest.correctSubscriptionData(subscription)

        then: 'no exception should be thrown'
            noExceptionThrown()
            subscription.events == [VALID_EVENT]
    }

    def 'When validating a RES subscription the correct list of attached nodes is set in attachedNodes attribute'() {
        given: 'a RES subscription'
            def subscription = new ResSubscription()
            def rncNode = RncNode()
            subscription.setCells([new CellInfo('RNC01', 'RNC01-1-1')])
            subscription.setType(SubscriptionType.RES)
            subscription.setName('Test_RES')
            subscription.setUserType(USER_DEF)
            subscription.setNodes([rncNode])
            subscription.setResSpreadingFactor([ResSpreadingFactor.SF_32])

        when: 'the subscription is validated'
            def updated = objectUnderTest.shouldUpdateSubscriptionDataOnInitiationEvent([rncNode], subscription, InitiationEventType.SUBSCRIPTION_ACTIVATION)

        then: 'no exception should be thrown'
            noExceptionThrown()
            updated
            subscription.attachedNodes.size() == 1
            subscription.attachedNodes == [RadioNode()]
    }

    def 'When validating a MTR subscription the correct list of attached nodes is set in attachedNodes attribute'() {
        given: 'an MTR subscription'
            def subscription = new MtrSubscription()
            subscription.setType(SubscriptionType.MTR)
            subscription.setName('Test_MTR')
            subscription.setUserType(USER_DEF)
            subscription.setNodes([MSC_NODE_06])
            mockedPmMtrLookUp.fetchAttachedNodes(_, _) >> [BscNode('NetworkElement=GSM01BSC01', 'GSM01BSC01')]

        when: 'the subscription is validated'
            def updated = objectUnderTest.shouldUpdateSubscriptionDataOnInitiationEvent([MSC_NODE_06], subscription, InitiationEventType.SUBSCRIPTION_ACTIVATION)

        then: 'no exception should be thrown'
            noExceptionThrown()
            updated
            subscription.attachedNodes.size() == 1
            subscription.attachedNodes == [BscNode('NetworkElement=GSM01BSC01', 'GSM01BSC01')]
    }

    def 'Add additional node to a MTR subscription the correct list of attached nodes is set in attachedNodes attribute'() {
        given: 'an MTR subscription'
            def subscription = new MtrSubscription()
            def mscNode_18 = MscNode('MSC-BC-IS', 'NetworkElement=MSC18', 'MSC18')
            subscription.setId(10)
            subscription.setType(SubscriptionType.MTR)
            subscription.setName('Test_MTR')
            subscription.setUserType(USER_DEF)
            subscription.setNodes([MSC_NODE_06, mscNode_18])

        when: 'the subscription is validated'
            subscriptionReadOperationService.findOneById(_, _) >> subscription
            mockedPmMtrLookUp.fetchAttachedNodes(_, _) >> [BscNode('NetworkElement=GSM01BSC01', 'GSM01BSC01'), BscNode('NetworkElement=GSM01BSC02', 'GSM01BSC02')]

            def updated = objectUnderTest.shouldUpdateSubscriptionDataOnInitiationEvent([mscNode_18], subscription, InitiationEventType.ADD_NODES_TO_SUBSCRIPTION)

        then: 'no exception should be thrown'
            noExceptionThrown()
            updated
            subscription.attachedNodes.size() == 2
    }

    def 'Remove additional node from a MTR subscription the correct list of attached nodes is set in attachedNodes attribute'() {
        given: 'an MTR subscription'
            def subscription = new MtrSubscription()
            def mscNode_18 = MscNode('MSC-BC-IS', 'NetworkElement=MSC18', 'MSC18')
            subscription.setType(SubscriptionType.MTR)
            subscription.setName('Test_MTR')
            subscription.setUserType(USER_DEF)
            subscription.setNodes([MSC_NODE_06, mscNode_18])

        when: 'the subscription is validated'
            mockedPmMtrLookUp.fetchAttachedNodes(_, _) >> [BscNode('NetworkElement=GSM01BSC01', 'GSM01BSC01')]
            def updated = objectUnderTest.shouldUpdateSubscriptionDataOnInitiationEvent([mscNode_18], subscription, InitiationEventType.REMOVE_NODES_FROM_SUBSCRIPTION)

        then: 'no exception should be thrown'
            noExceptionThrown()
            updated
            subscription.attachedNodes.size() == 1
    }

    def 'When deactivating a MTR subscription the correct list of attached nodes is removed in attachedNodes attribute'() {
        given: 'an MTR subscription'
            def subscription = new MtrSubscription()
            subscription.setType(SubscriptionType.MTR)
            subscription.setName('Test_MTR')
            subscription.setUserType(USER_DEF)
            subscription.setNodes([MSC_NODE_06])

        when: 'the subscription is validated'
            def updated = objectUnderTest.shouldUpdateSubscriptionDataOnInitiationEvent([MSC_NODE_06], subscription, InitiationEventType.SUBSCRIPTION_DEACTIVATION)

        then: 'no exception should be thrown'
            noExceptionThrown()
            updated
            subscription.attachedNodes.size() == 0
    }

    def 'When validating a CellRelation subscription the correct list of moTpeInstances attribute'() {
        given: 'a Cell Relation subscription'
            def subscription = new CellRelationSubscription()
            def rncNode = RncNode()
            subscription.setCells([new CellInfo('RNC01', 'UtranCell=RNC01-1-1')])
            subscription.setCounters([new CounterInfo('pmNoFailOutIratHoStandaloneGsmFailure', 'GsmRelation')])
            subscription.setType(SubscriptionType.CELLRELATION)
            subscription.setName('Test_CellRelation')
            subscription.setUserType(USER_DEF)
            subscription.setNodes([rncNode])

        when: 'the subscription is validated'
            def updated = objectUnderTest.shouldUpdateSubscriptionDataOnInitiationEvent([rncNode], subscription, InitiationEventType.SUBSCRIPTION_ACTIVATION)

        then: 'no exception should be thrown'
            noExceptionThrown()
            updated
            subscription.getMoTypeInstances().size() == 1
    }

    def MoInstanceLiist() {
        def instances = []
        def parentfdn = 'MeContext=RNC01,ManagedElement=1,RncFunction=1,UtranCell=RNC01-1-1,GsmRelation=10';
        for (int i = 0; i < 9; i++) {
            instances.add(parentfdn + i)
        }
        return instances
    }

    def ErbsNode() {
        def node = new Node()
        node.setNeType('ERBS')
        node.setFdn('NetworkElement=LTE02ERBS00001')
        node.setOssModelIdentity('18.Q2-J.1.280')
        node.setName('LTE02ERBS00001')
        node.setId(281474977595581L)
        node.setTechnologyDomain(['EPS'])
        node.setPmFunction(true)
        return node
    }

    def RncNode() {
        def node = new Node()
        node.setNeType('RNC')
        node.setFdn('NetworkElement=RNC01')
        node.setOssModelIdentity('16B-G.1.281')
        node.setOssPrefix('MeContext=RNC01')
        node.setName('RNC01')
        node.setId(281474977595581L)
        node.setTechnologyDomain(['CPP'])
        node.setPmFunction(true)
        return node
    }

    def ErbsNodeInvalid() {
        def node = new Node()
        node.setNeType('ERBS')
        node.setFdn('NetworkElement=LTE02ERBS00002')
        node.setOssModelIdentity('invalidOssModelIdentity')
        node.setName('LTE02ERBS00002')
        node.setId(281474977512345L)
        node.setTechnologyDomain(['EPS'])
        node.setPmFunction(true)
        return node
    }

    def RadioNode() {
        def node = new Node()
        node.setNeType('RadioNode')
        node.setFdn('NetworkElement=LTE02dgERBS00001')
        node.setOssModelIdentity('17A-R2YX')
        node.setName('LTE02dgERBS00001')
        node.setId(281876977512345L)
        node.setTechnologyDomain(['EPS'])
        node.setPmFunction(true)
        return node
    }

    def BscNode(String fdn, String name) {
        def node = new Node()
        node.setNeType('BSC')
        node.setFdn(fdn)
        node.setOssModelIdentity('17A-R2YX')
        node.setName(name)
        node.setId(281876977512345L)
        node.setTechnologyDomain(['EPS'])
        node.setPmFunction(true)
        return node
    }

    static MscNode(String neType, String fdn, String nodeName) {
        def node = new Node()
        node.setNeType(neType)
        node.setFdn(fdn)
        node.setOssModelIdentity('17A-R2YX')
        node.setName(nodeName)
        node.setId(281876977512345L)
        node.setTechnologyDomain(['EPS'])
        node.setPmFunction(true)
        return node
    }

    def PicoNode() {
        def node = new Node()
        node.setNeType('MSRBS_V1')
        node.setFdn('NetworkElement=LTE02MSRBS00001')
        node.setOssModelIdentity('16A-R9A')
        node.setName('LTE02MSRBS00001')
        node.setId(281342327512345L)
        node.setTechnologyDomain(['EPS'])
        node.setPmFunction(true)
        return node
    }

    def SgsnNode() {
        def node = new Node()
        node.setNeType('SGSN-MME')
        node.setFdn('NetworkElement=SGSN-16A-V1-CP0203')
        node.setOssModelIdentity('16A-CP02')
        node.setName('SGSN-16A-V1-CP0203')
        node.setId(281474977591390L)
        node.setTechnologyDomain(['EPS'])
        node.setPmFunction(true)
        return node
    }
}
