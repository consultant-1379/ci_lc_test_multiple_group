/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2016
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */
package com.ericsson.oss.services.pm.initiation.notification.events

import static com.ericsson.oss.pmic.cdi.test.util.Constants.ACTIVATING
import static com.ericsson.oss.pmic.cdi.test.util.Constants.ACTIVE
import static com.ericsson.oss.pmic.cdi.test.util.Constants.ERROR
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NA
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NETWORK_ELEMENT_1
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NETWORK_ELEMENT_2
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_1
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_2
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_3
import static com.ericsson.oss.pmic.cdi.test.util.Constants.OK
import static com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription.MoinstanceSubscription100Attribute.moInstances
import static com.ericsson.oss.pmic.dto.subscription.cdts.MoinstanceInfo.MoinstanceInfo100Attribute.moInstanceName
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ASSOCIATION_SUBSCRIPTION_NODES
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ID
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE

import org.mockito.Mockito
import spock.lang.Unroll

import javax.cache.Cache
import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.PmBaseSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.ResSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.SubscriptionTypeClassMapper
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.ScannerDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription
import com.ericsson.oss.pmic.dto.subscription.ResSubscription
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.MoinstanceInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.dto.subscription.enums.UeType
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.pmic.impl.counters.PmCountersLifeCycleResolverImpl
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilityReaderImpl
import com.ericsson.oss.pmic.util.CollectionUtil
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.ejb.CounterConflictServiceImpl
import com.ericsson.oss.services.pm.initiation.ejb.GroovyTestUtils
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper
import com.ericsson.oss.services.pm.initiation.model.metadata.res.PmResLookUp
import com.ericsson.oss.services.pm.initiation.rest.response.ConflictingCounterGroup
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionActivationTaskRequest

class ActivationEventSpec extends PmBaseSpec {

    @ObjectUnderTest
    ActivationEvent objectUnderTest
    @Inject
    @NamedCache('PMICFileCollectionActiveTaskListCache')
    Cache<String, Map<String, Object>> activeTaskCache
    @Inject
    FileCollectionActiveTaskCacheWrapper fileCollectionActiveTasksCache
    @Inject
    EventSender<MediationTaskRequest> eventSender
    @Inject
    GroovyTestUtils testUtils
    @Inject
    PMICInitiationTrackerCache initiationTrackerCache
    @Inject
    CounterConflictServiceImpl counterConflictService
    @Inject
    SubscriptionOperationExecutionTrackingCacheWrapper subscriptionInitiationCacheWrapper
    @Inject
    SubscriptionDao subscriptionDao
    @Inject
    NodeDao nodeDao
    @Inject
    ScannerDao scannerDao
    @MockedImplementation
    PmResLookUp mockedPmResLookUp
    @ImplementationClasses
    def classes = [PmCapabilityReaderImpl.class, PmCountersLifeCycleResolverImpl.class]

    @MockedImplementation
    TimerService timerService
    @ImplementationInstance
    TimeGenerator timer = Mockito.mock(TimeGenerator)

    def nodes = [] as List<ManagedObject>
    def subscriptionMo
    def ne1ErrorEntryMap = [:]
    def ne2ErrorEntryMap = [:]
    def nodeMo1, nodeMo2, nodeMo3

    def counters = [
        new CounterInfo('pmLicDlCapActual', 'BbProcessingResource'),
        new CounterInfo('pmLicDlCapDistr', 'BbProcessingResource'),
        new CounterInfo('pmLicDlPrbCapActual', 'BbProcessingResource'),
        new CounterInfo('pmAdmNrRrcUnknownArpRatio', 'AdmissionControl')
    ]
    def expectedCounterInfos = [
        new ConflictingCounterGroup('BbProcessingResource', ['pmLicDlCapActual', 'pmLicDlCapDistr', 'pmLicDlPrbCapActual'] as Set),
        new ConflictingCounterGroup('AdmissionControl', ['pmAdmNrRrcUnknownArpRatio'] as Set)
    ]

    def attachedNode1Mo
    def attachedNode2Mo
    def node1Mo
    def node2Mo
    private static final RES_SUBSCRIPTION_NAME = 'Test_RES'

    def rncNodeAttributes = [
        name             : 'RNC01',
        networkElementId : 'RNC01',
        fdn              : 'NetworkElement=RNC01',
        platformType     : 'CPP',
        neType           : 'RNC',
        nodeModelIdentity: '16B-V.7.1659',
        ossModelIdentity : '16B-V.7.1659',
        ossPrefix        : 'MeContext=RNC01',
        pmFunction       : true]

    def rbsNodeAttributes = [
        platformType     : 'CPP',
        neType           : 'RBS',
        nodeModelIdentity: '16A-U.4.210',
        ossModelIdentity : '16A-U.4.210',
        controllingRnc   : 'NetworkElement=RNC01',
        pmFunction       : true]

    def RncNode() {
        def node = new Node()
        node.setNeType('RNC')
        node.setNetworkElementId('RNC02')
        node.setFdn('NetworkElement=RNC02')
        node.setOssModelIdentity('16B-V.7.1659')
        node.setName('RNC02')
        node.setId(node2Mo.poId)
        node.setTechnologyDomain(['EPS'])
        node.setPmFunction(true)
        return node
    }

    def setup() {
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
    }

    def 'When activated, subscription will go ACTIVE/ERROR if nodes have pmFunction off'() {
        given: 'two nodes with pmFunction off attached to one subscription'
            subscriptionMo =
                statisticalSubscriptionBuilder.name('Test').administrativeState(AdministrationState.ACTIVATING).taskStatus(TaskStatus.OK).build()
            fillErrorMaps(subscriptionMo.poId)
            nodes = [createNode(NODE_NAME_1, 'ERBS', [:], null, false), createNode(NODE_NAME_2, 'ERBS', [:], null, false)]
            dpsUtils.addAssociation(subscriptionMo, 'nodes', nodes.get(0), nodes.get(1))
            scannerUtil.builder('USERDEF.Test.Cont.Y.Stats', NODE_NAME_1)
                .subscriptionId(subscriptionMo.poId)
                .status(ScannerStatus.ACTIVE)
                .processType(ProcessType.STATS)
                .build()
            scannerUtil.builder('USERDEF.Test.Cont.Y.Stats', NODE_NAME_2)
                .subscriptionId(subscriptionMo.poId)
                .status(ScannerStatus.ACTIVE)
                .processType(ProcessType.STATS)
                .build()

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the status will be ACTIVE/ERROR'
            ACTIVE == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'the node associations will be 2'
            2 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)

        and: 'nothing will be added to the tracker cache'
            0 == testUtils.getTotalNodesToBeActivated() as Integer
    }

    @Unroll
    def 'When activated, #subType subscription will go ACTIVE if nodes have pmFunction with pmEnabled true and neConfigurationManagerState disabled'() {
        given: 'two nodes with pmEnabled true and neConfigurationManagerState disabled attached to one statistical subscription'
            subscriptionMo =
                subscriptionBuilderFactory(subType, eventList, ueInfoList)
                    .name('Test')
                    .administrativeState(AdministrationState.ACTIVATING)
                    .taskStatus(TaskStatus.OK)
                    .build()
            fillErrorMaps(subscriptionMo.poId)
            nodes = [createNode(NODE_NAME_1, 'ERBS', nodeAttrib, null, true, NeConfigurationManagerState.DISABLED),
                     createNode(NODE_NAME_2, 'ERBS', nodeAttrib, null, true, NeConfigurationManagerState.DISABLED)]
            dpsUtils.addAssociation(subscriptionMo, 'nodes', nodes[0], nodes[1])
            if (createPmJob) {
                pmJobBuilder.nodeName(NODE_NAME_1).processType(processType).subscriptionId(subscriptionMo).status(PmJobStatus.INACTIVE).build()
                pmJobBuilder.nodeName(NODE_NAME_2).processType(processType).subscriptionId(subscriptionMo).status(PmJobStatus.INACTIVE).build()
            }

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the status will be ACTIVE/OK'
            ACTIVE == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            OK == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'the node associations will be 2'
            2 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)

        and: 'no MTR is sent'
            0 * eventSender.send(_ as MediationTaskRequest)

        and: 'nothing will be added to the tracker cache'
            0 == testUtils.getTotalNodesToBeActivated() as Integer

        and: 'the scanners with unknown status are created'
            if (checkScanners) {
                def scanners = scannerDao.findAllBySubscriptionId(subscriptionMo.poId)
                scanners.each {
                    assert it.getStatus().name() == 'UNKNOWN'
                }
            }

        and: 'no tracker is created'
            initiationTrackerCache.getTracker(subscriptionMo.poId as String) == null

        where:
            subType                                         | processType                         | nodeAttrib | eventList | ueInfoList | createPmJob || checkScanners
            SubscriptionTypeClassMapper.CONTINUOUSCELLTRACE | ProcessType.HIGH_PRIORITY_CELLTRACE | [:]        | null      | null       | false       || false
    }

    @Unroll
    def 'When activated, #subType subscription will go ACTIVE/ERROR if nodes have pmFunction with pmEnabled true and neConfigurationManagerState disabled'() {
        given: 'two nodes with pmEnabled true and neConfigurationManagerState disabled attached to one statistical subscription'
            subscriptionMo =
                subscriptionBuilderFactory(subType, eventList, ueInfoList)
                    .name('Test')
                    .administrativeState(AdministrationState.ACTIVATING)
                    .taskStatus(TaskStatus.OK)
                    .build()
            fillErrorMaps(subscriptionMo.poId)
            nodes = [createNode(NODE_NAME_1, 'ERBS', nodeAttrib, null, true, NeConfigurationManagerState.DISABLED),
                     createNode(NODE_NAME_2, 'ERBS', nodeAttrib, null, true, NeConfigurationManagerState.DISABLED)]
            dpsUtils.addAssociation(subscriptionMo, 'nodes', nodes[0], nodes[1])
            if (createPmJob) {
                pmJobBuilder.nodeName(NODE_NAME_1).processType(processType).subscriptionId(subscriptionMo).status(PmJobStatus.INACTIVE).build()
                pmJobBuilder.nodeName(NODE_NAME_2).processType(processType).subscriptionId(subscriptionMo).status(PmJobStatus.INACTIVE).build()
            }

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the status will be ACTIVE/ERROR'
            ACTIVE == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'the node associations will be 2'
            2 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)

        and: 'no MTR is sent'
            0 * eventSender.send(_ as MediationTaskRequest)

        and: 'nothing will be added to the tracker cache'
            0 == testUtils.getTotalNodesToBeActivated() as Integer

        and: 'the scanners with unknown status are created'
            if (checkScanners) {
                def scanners = scannerDao.findAllBySubscriptionId(subscriptionMo.poId)
                scanners.each {
                    assert it.getStatus().name() == 'UNKNOWN'
                }
            }

        and: 'no tracker is created'
            initiationTrackerCache.getTracker(subscriptionMo.poId as String) == null

        where:
            subType                                 | processType                           | nodeAttrib             | eventList                        | ueInfoList                             | createPmJob || checkScanners
            SubscriptionTypeClassMapper.STATISTICAL | ProcessType.STATS                     | [:]                    | null                             | null                                   | false       || true
            SubscriptionTypeClassMapper.CELLTRACE   | ProcessType.NORMAL_PRIORITY_CELLTRACE | [:]                    | null                             | null                                   | false       || false
            SubscriptionTypeClassMapper.UETRACE     | ProcessType.UETRACE                   | ['neType': 'SGSN-MME'] | null                             | null                                   | true        || false
            SubscriptionTypeClassMapper.EBM         | ProcessType.EVENTJOB                  | [:]                    | [new EventInfo('group', 'name')] | null                                   | false       || false
            SubscriptionTypeClassMapper.UETR        | ProcessType.UETR                      | [:]                    | [new EventInfo('group', 'name')] | [new UeInfo(UeType.IMEI, '214256543')] | false       || false
            SubscriptionTypeClassMapper.CTUM        | ProcessType.CTUM                      | ['neType': 'SGSN-MME'] | null                             | null                                   | true        || false
            SubscriptionTypeClassMapper.MOINSTANCE  | ProcessType.STATS                     | [:]                    | null                             | null                                   | false       || false
            SubscriptionTypeClassMapper.CELLTRAFFIC | ProcessType.CTR                       | [:]                    | [new EventInfo('group', 'name')] | null                                   | false       || false
            SubscriptionTypeClassMapper.GPEH        | ProcessType.REGULAR_GPEH              | [:]                    | [new EventInfo('group', 'name')] | null                                   | false       || false
            SubscriptionTypeClassMapper.GPEH        | ProcessType.OPTIMIZER_GPEH            | [:]                    | [new EventInfo('group', 'name')] | null                                   | false       || false
            SubscriptionTypeClassMapper.RES         | ProcessType.STATS                     | [:]                    | null                             | null                                   | false       || false
    }

    def 'When activated, subscription will stay in current state if nodes have pmFunction on'() {
        given: 'two nodes with pmFunction on attached to one subscription'
            subscriptionMo =
                statisticalSubscriptionBuilder.name('Test').administrativeState(AdministrationState.ACTIVATING).taskStatus(TaskStatus.NA).build()
            fillErrorMaps(subscriptionMo.poId)
            nodes = [createNode(NODE_NAME_1), createNode(NODE_NAME_2)]
            dpsUtils.addAssociation(subscriptionMo, 'nodes', nodes[0], nodes[1])
            scannerUtil.builder('USERDEF.Test.Cont.Y.Stats', NODE_NAME_1)
                .subscriptionId(subscriptionMo.poId)
                .status(ScannerStatus.ACTIVE)
                .processType(ProcessType.STATS)
                .build()
            scannerUtil.builder('USERDEF.Test.Cont.Y.Stats', NODE_NAME_2)
                .subscriptionId(subscriptionMo.poId)
                .status(ScannerStatus.ACTIVE)
                .processType(ProcessType.STATS)
                .build()

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the status will not be changed'
            ACTIVATING == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            NA == (String) subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'the node associations will be 2'
            2 == subscriptionMo.getAssociatedObjectCount(PMIC_ASSOCIATION_SUBSCRIPTION_NODES)

        and: 'both nodes will be added to the tracker cache'
            2 == testUtils.totalNodesToBeActivated as Integer
    }

    def 'When activated, if a node has pmFunction off it will be added as an error entry in the active task cache'() {
        given: 'two nodes with pmFunction off attached to one subscription'
            subscriptionMo =
                statisticalSubscriptionBuilder.name('Test').administrativeState(AdministrationState.ACTIVATING).taskStatus(TaskStatus.OK).build()
            fillErrorMaps(subscriptionMo.poId)
            nodes = [createNode(NODE_NAME_1, 'ERBS', [:], null, false), createNode(NODE_NAME_2, 'ERBS', [:], null, false)]
            dpsUtils.addAssociation(subscriptionMo, 'nodes', nodes.get(0), nodes.get(1))
            scannerUtil.builder('USERDEF.Test.Cont.Y.Stats', NODE_NAME_1)
                .subscriptionId(subscriptionMo.poId)
                .status(ScannerStatus.ACTIVE)
                .processType(ProcessType.STATS)
                .build()
            scannerUtil.builder('USERDEF.Test.Cont.Y.Stats', NODE_NAME_2)
                .subscriptionId(subscriptionMo.poId)
                .status(ScannerStatus.ACTIVE)
                .processType(ProcessType.STATS)
                .build()

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the active task cache will contain 2 entries'
            2 == activeTaskCache.size()

        and: 'the entry attributes contain the subscription ID'
            ne1ErrorEntryMap == activeTaskCache.get(NETWORK_ELEMENT_1)
            ne2ErrorEntryMap == activeTaskCache.get(NETWORK_ELEMENT_2)
    }

    def 'When activated, if one of two nodes has pmFunction off, only the OFF node will be added as an error entry in the active task cache'() {
        given: 'two nodes, one with pmFunction ON and one OFF, attached to one subscription'
            subscriptionMo =
                statisticalSubscriptionBuilder.name('Test').administrativeState(AdministrationState.ACTIVATING).taskStatus(TaskStatus.OK).build()
            fillErrorMaps(subscriptionMo.poId)
            nodes = [createNode(NODE_NAME_1), createNode(NODE_NAME_2, 'ERBS', [:], null, false)]
            dpsUtils.addAssociation(subscriptionMo, 'nodes', nodes[0], nodes[1])
            scannerUtil.builder('USERDEF.Test.Cont.Y.Stats', NODE_NAME_1)
                .subscriptionId(subscriptionMo.poId)
                .status(ScannerStatus.ACTIVE)
                .processType(ProcessType.STATS)
                .build()
            scannerUtil.builder('USERDEF.Test.Cont.Y.Stats', NODE_NAME_2)
                .subscriptionId(subscriptionMo.poId)
                .status(ScannerStatus.ACTIVE)
                .processType(ProcessType.STATS)
                .build()

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the active task cache will contain 1 entry'
            1 == activeTaskCache.size()

        and: 'the entry attributes contain the subscription ID'
            ne2ErrorEntryMap == activeTaskCache.get(NETWORK_ELEMENT_2)
    }

    def 'When the same node is in two ACTIVE/ERROR subscriptions, both subscriptions are added to the set in the cache'() {
        given: 'yhe same nodes with pmFunction off attached to two subscriptions'
            subscriptionMo =
                statisticalSubscriptionBuilder.name('Test').administrativeState(AdministrationState.ACTIVATING).taskStatus(TaskStatus.OK).build()
            fillErrorMaps(subscriptionMo.poId)
            nodes = [createNode(NODE_NAME_1, 'ERBS', [:], null, false)]
            def subscriptionMo2 = statisticalSubscriptionBuilder.name('Test2').
                administrativeState(AdministrationState.ACTIVATING).
                taskStatus(TaskStatus.OK).
                build()
            dpsUtils.addAssociation(subscriptionMo, 'nodes', nodes[0])
            dpsUtils.addAssociation(subscriptionMo2, 'nodes', nodes[0])
            scannerUtil.builder('USERDEF.Test.Cont.Y.Stats', NODE_NAME_1)
                .subscriptionId(subscriptionMo.poId)
                .status(ScannerStatus.ACTIVE)
                .processType(ProcessType.STATS)
                .build()
            scannerUtil.builder('USERDEF.Test2.Cont.Y.Stats', NODE_NAME_1)
                .subscriptionId(subscriptionMo.poId)
                .status(ScannerStatus.ACTIVE)
                .processType(ProcessType.STATS)
                .build()
            def subIds = [subscriptionMo.poId, subscriptionMo2.poId] as HashSet
            def errorEntryMap = [(PMIC_ATT_SUBSCRIPTION_ID): subIds]

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)
            objectUnderTest.execute(subscriptionMo2.poId)

        then: 'the active task cache will contain 2 entries'
            1 == activeTaskCache.size()

        and: 'both subscription IDs will be in the error cache'
            errorEntryMap == activeTaskCache.get(NETWORK_ELEMENT_1)
    }

    def 'Subscription will not be tracked if adding pmFunctionOFF nodes to active subscription'() {
        given: 'a stats subscription with 2 nodes, one of which has pmFunctionOFF'
            subscriptionMo =
                statisticalSubscriptionBuilder.name('Test').administrativeState(AdministrationState.UPDATING).taskStatus(TaskStatus.OK).build()
            def nodeMo1 = createNode(NODE_NAME_1) //this node is added to subscription to emulate realistic scenario ... active subscription must have at least one node
            def nodeMo2 = createNode(NODE_NAME_2, 'ERBS', [:], null, false)

            dpsUtils.addAssociation(subscriptionMo, 'nodes', nodeMo1, nodeMo2)
            def subscription = subscriptionDao.findOneById(subscriptionMo.poId, true)
            def nodeInfo = nodeDao.findOneById(nodeMo2.poId)

        when: 'a node with pm function off is added to subscription'
            objectUnderTest.execute([nodeInfo], subscription)

        then: 'no MTR is sent, no tracker is created'
            0 * eventSender.send(_ as SubscriptionActivationTaskRequest)
            initiationTrackerCache.getTracker(subscriptionMo.poId as String) == null

        and: 'the error node cache is updated'
            !((Set<Long>) activeTaskCache.get(nodeMo2.fdn).get(PMIC_ATT_SUBSCRIPTION_ID)).empty

        and: 'the subscription AdministrationState is ACTIVE and taskStatus ERROR'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.ACTIVE.name()
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == TaskStatus.ERROR.name()
    }

    def 'When adding nodes to active subscription, only pmFunctionON nodes will be tracked'() {
        given: 'a stats subscription with 3 nodes, one of which has pmFunctionOFF'
            subscriptionMo =
                statisticalSubscriptionBuilder.name('Test').administrativeState(AdministrationState.UPDATING).taskStatus(TaskStatus.OK).build()
            def nodeMo1 = createNode(NODE_NAME_1)
            def nodeMo2 = createNode(NODE_NAME_2, 'ERBS', [:], null, false)
            def nodeMo3 = createNode(NODE_NAME_3) //this node is added to subscription to emulate realistic scenario ... active subscription must have at least one node

            dpsUtils.addAssociation(subscriptionMo, 'nodes', nodeMo1, nodeMo2, nodeMo3)
            def subscription = subscriptionDao.findOneById(subscriptionMo.poId, true)
            def nodeInfo1 = nodeDao.findOneById(nodeMo1.poId)
            def nodeInfo2 = nodeDao.findOneById(nodeMo2.poId)

        when: 'nodes  1 and 2 are added to subscription, node 2 has pmFunctionOFF'
            objectUnderTest.execute([nodeInfo1, nodeInfo2], subscription)

        then: 'one MTR is sent, One tracker is created'
            1 * eventSender.send(_ as SubscriptionActivationTaskRequest)
            initiationTrackerCache.getTracker(subscriptionMo.poId as String).totalAmountOfExpectedNotifications == 1 as Integer

        and: 'the error node cache is updated'
            !((Set<Long>) activeTaskCache.get(nodeMo2.fdn).get(PMIC_ATT_SUBSCRIPTION_ID)).empty
            activeTaskCache.get(nodeMo1.fdn) == null
            activeTaskCache.get(nodeMo3.fdn) == null

        and: 'the subscription AdministrationState remains UPDATING and original taskStatus'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.UPDATING.name()
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == TaskStatus.OK.name()
    }

    def 'When activating a subscription with no nodes, the subscription goes to ACTIVE ERROR state - INVALID scenario, but allowed through REST endpoint'() {
        given:
            subscriptionMo = statisticalSubscriptionBuilder.build()

        when:
            objectUnderTest.execute(subscriptionMo.poId)

        then:
            (subscriptionMo.getAttribute('taskStatus') as String) == 'ERROR'
            (subscriptionMo.getAttribute('administrationState') as String) == 'ACTIVE'
            0 * eventSender.send(_ as MediationTaskRequest)
    }

    def 'When no tasks can be created, the subscription goes to ACTIVE ERROR state'() {
        given:
            subscriptionMo = cellTraceSubscriptionBuilder.build() //no events, no scanners
            build3erbsNodes()
            dpsUtils.addAssociation(subscriptionMo, 'nodes', nodeMo1, nodeMo2, nodeMo3)

        when:
            objectUnderTest.execute(subscriptionMo.poId)

        then:
            (subscriptionMo.getAttribute('taskStatus') as String) == ('ERROR')
            (subscriptionMo.getAttribute('administrationState') as String) == 'ACTIVE'
            0 * eventSender.send(_ as MediationTaskRequest)
    }

    def 'when Statistical subscription is activated, the counter conflict cache will be updated with nodes and counters'() {
        given: '3 nodes in DPS and Activating statistical subscription with 2 counters'
            build3erbsNodes()
            def subMo = statisticalSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2).
                userType(UserType.USER_DEF).
                name('testSub').
                administrativeState(AdministrationState.ACTIVE).
                build()
            def subMoVerify = statisticalSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2, nodeMo3).
                userType(UserType.USER_DEF).
                administrativeState(AdministrationState.ACTIVE).
                name('VerifySub').
                build()

            def subscription = subscriptionDao.findOneById(subMo.poId, true) as StatisticalSubscription
            def verificationSubscription = subscriptionDao.findOneById(subMoVerify.poId, true) as StatisticalSubscription

        when: 'the subscription is activated'
            objectUnderTest.execute(subscription.id)

        then: 'counter conflict cache has correct values for nodes and counters'
            def result = counterConflictService.getConflictingCountersInSubscription(verificationSubscription)
            result.nodes == [nodeMo1.fdn, nodeMo2.fdn] as Set
            result.counterEventInfo.containsAll(expectedCounterInfos)
    }

    def 'when MoInstance subscription is activated, the counter conflict cache will be updated with nodes and counters'() {
        given: '3 nodes in DPS and Activating statistical subscription with 2 counters'
            build3erbsNodes()
            def subMo = moinstanceSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2).
                userType(UserType.USER_DEF).
                name('testSub').
                administrativeState(AdministrationState.ACTIVE).
                build()
            def subMoVerify = moinstanceSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2, nodeMo3).
                userType(UserType.USER_DEF).
                administrativeState(AdministrationState.ACTIVE).
                name('VerifySub').
                build()

            def subscription = subscriptionDao.findOneById(subMo.poId, true) as MoinstanceSubscription
            def verificationSubscription = subscriptionDao.findOneById(subMoVerify.poId, true) as MoinstanceSubscription

        when: 'subscription is activated'
            objectUnderTest.execute(subscription.id)

        then: 'counter conflict cache has correct values for nodes and counters'
            def result = counterConflictService.getConflictingCountersInSubscription(verificationSubscription)
            result.nodes == [nodeMo1.fdn, nodeMo2.fdn] as Set
            result.counterEventInfo.containsAll(expectedCounterInfos)
    }

    def 'when a node is added to an active StatisticalSubscription, the counter conflicts will update with this new node'() {
        given: '3 nodes in DPS and Activating statistical subscription with 2 counters'
            build3erbsNodes()
            def subMo = statisticalSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2).
                userType(UserType.USER_DEF).
                name('testSub').
                administrativeState(AdministrationState.ACTIVE).
                build()
            def subMoVerify = statisticalSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2, nodeMo3).
                userType(UserType.USER_DEF).
                administrativeState(AdministrationState.ACTIVE).
                name('VerifySub').
                build()

            def subscription = subscriptionDao.findOneById(subMo.poId, true) as StatisticalSubscription
            def verificationSubscription = subscriptionDao.findOneById(subMoVerify.poId, true) as StatisticalSubscription

        and: 'the subscription already has entries in the counter conflict cache'
            counterConflictService.addNodesAndCounters([nodeMo1.fdn, nodeMo2.fdn] as Set, counters, subscription.name)

        when: 'a node is added to existing subscription'
            objectUnderTest.execute([nodeDao.findOneById(nodeMo3.poId)], subscription)

        then: 'the counter conflict cache adds the node'
            def result = counterConflictService.getConflictingCountersInSubscription(verificationSubscription)
            result.nodes == [nodeMo1.fdn, nodeMo2.fdn, nodeMo3.fdn] as Set

        and: 'subscriptionCountersCache is not updated'
            result.counterEventInfo.containsAll(expectedCounterInfos)
    }

    def 'when a node is added to an active MoinstanceSubscription, the counter conflicts will update with this new node'() {
        given: '3 nodes in DPS and Activating statistical subscription with 2 counters'
            build3erbsNodes()
            def subMo = moinstanceSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2).
                userType(UserType.USER_DEF).
                name('testSub').
                administrativeState(AdministrationState.ACTIVE).
                build()
            def subMoVerify = moinstanceSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2, nodeMo3).
                userType(UserType.USER_DEF).
                administrativeState(AdministrationState.ACTIVE).
                name('VerifySub').
                build()

            def subscription = subscriptionDao.findOneById(subMo.poId, true) as MoinstanceSubscription
            def verificationSubscription = subscriptionDao.findOneById(subMoVerify.poId, true) as MoinstanceSubscription

        and: 'the subscription already has entries in the counter conflict cache'
            counterConflictService.addNodesAndCounters([nodeMo1.fdn, nodeMo2.fdn] as Set, counters, subscription.name)

        when: 'a node is added to existing subscription'
            objectUnderTest.execute([nodeDao.findOneById(nodeMo3.poId)], subscription)

        then: 'the counter conflict cache adds the node'
            def result = counterConflictService.getConflictingCountersInSubscription(verificationSubscription)
            result.nodes == [nodeMo1.fdn, nodeMo2.fdn, nodeMo3.fdn] as Set

        and: 'subscriptionCountersCache is not updated'
            result.counterEventInfo.containsAll(expectedCounterInfos)
    }

    def 'when non statistical subscription is activated, the counter conflict cache will not be updated with nodes and counters'() {
        given: 'a celltrace subscription in activating state with 2 nodes'
            build3erbsNodes()
            subscriptionMo = cellTraceSubscriptionBuilder.administrativeState(AdministrationState.ACTIVATING).
                name('testSub').
                nodes(nodeMo1, nodeMo2).
                addEvent('x', 'y').
                build()
            def subscription = subscriptionDao.findOneById(subscriptionMo.poId) as CellTraceSubscription

        when: 'the subscription is activated'
            objectUnderTest.execute(subscription.id)

        then: 'the counter conflict cache has is not updated'
            !counterConflictService.areEntriesInCounterCache()
            !counterConflictService.areEntriesInNodesCache()
    }

    def 'when celltrace EBSL_STREAM subscription is activated, the counter conflict cache will not be updated with nodes and counter'() {
        given: 'a celltrace subscription in activating state with 1 nodes'
            build3erbsNodes()
            subscriptionMo = cellTraceSubscriptionBuilder.cellTraceCategory(CellTraceCategory.EBSL_STREAM).addEbsEvent('ebsGroupEvent', 'ebsEvent').name('Test')
                .name('testSub').taskStatus(TaskStatus.OK).administrativeState(AdministrationState.ACTIVATING)
                .addNode(nodeMo1).build()

        when: 'the subscription is activated'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'the counter conflict cache has is not updated'
            !counterConflictService.areEntriesInCounterCache()
            !counterConflictService.areEntriesInNodesCache()
    }

    def 'when a node is added to a non statistical subscription, the counter conflict cache will not be updated with nodes and counters'() {
        given: 'a celltrace subscription in activating state with 2 nodes'
            build3erbsNodes()
            subscriptionMo = cellTraceSubscriptionBuilder.administrativeState(AdministrationState.ACTIVATING).
                name('testSub').
                nodes(nodeMo1, nodeMo2).
                addEvent('x', 'y').
                build()
            def subscription = subscriptionDao.findOneById(subscriptionMo.poId) as CellTraceSubscription

        when: 'a node is added to existing subscription'
            objectUnderTest.execute([nodeDao.findOneById(nodeMo3.poId)], subscription)

        then: 'the counter conflict cache has is not updated'
            !counterConflictService.areEntriesInCounterCache()
            !counterConflictService.areEntriesInNodesCache()
    }

    def 'when a node is added to a celltrace EBSL_STREAM subscription, the counter conflict cache will not be updated with nodes and counters'() {
        given: 'a celltrace subscription in activating state with 1 node'
            build3erbsNodes()
            subscriptionMo = cellTraceSubscriptionBuilder.cellTraceCategory(CellTraceCategory.EBSL_STREAM).addEbsEvent('ebsGroupEvent', 'ebsEvent').name('Test')
                .taskStatus(TaskStatus.OK).administrativeState(AdministrationState.ACTIVATING)
                .addNode(nodeMo1).build()
            def subscription = subscriptionDao.findOneById(subscriptionMo.poId) as CellTraceSubscription

        when: 'a node is added to existing subscription'
            objectUnderTest.execute([nodeDao.findOneById(nodeMo3.poId)], subscription)

        then: 'the counter conflict cache has is not updated'
            !counterConflictService.areEntriesInCounterCache()
            !counterConflictService.areEntriesInNodesCache()
    }

    def 'testing counter conflict cache updates for StatisticalSubscription subscription on activation with pm function off nodes'() {
        given: '4 nodes in DPS, 2 of which have pm function off, and Activating statistical subscription with 2 counters'
            def nodeMo1 = createNode(NODE_NAME_1, 'ERBS', [:], '18.Q2-J.1.280')
            def nodeMo2 = createNode(NODE_NAME_2, 'ERBS', [:], '18.Q2-J.1.280', false)
            def nodeMo3 = createNode(NODE_NAME_3, 'ERBS', [:], '18.Q2-J.1.280', false)
            def nodeMo4 = createNode('LTE01ERBS0004', 'ERBS', [:], '18.Q2-J.1.280')

            def subMo = statisticalSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2).
                userType(UserType.USER_DEF).
                name('testSub').
                administrativeState(AdministrationState.ACTIVE).
                build()
            def subMoVerify = statisticalSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2, nodeMo3, nodeMo4).
                userType(UserType.USER_DEF).
                administrativeState(AdministrationState.ACTIVE).
                name('VerifySub').
                build()

            def verificationSubscription = subscriptionDao.findOneById(subMoVerify.poId, true) as StatisticalSubscription

        when: 'the subscription is activated with 2 nodes, one of which has pm function off'
            objectUnderTest.execute(subMo.poId)

        then: 'the counter conflict cache has correct values for nodes and counters, both nodes are added'
            def result = counterConflictService.getConflictingCountersInSubscription(verificationSubscription)
            result.nodes == [nodeMo1.fdn, nodeMo2.fdn] as Set
            result.counterEventInfo.containsAll(expectedCounterInfos)
    }

    def 'testing counter conflict cache updates for MoinstanceSubscription subscription on activation with pm function off nodes'() {
        given: '4 nodes in DPS, 2 of which have pm function off, and Activating statistical subscription with 2 counters'
            def nodeMo1 = createNode(NODE_NAME_1, 'ERBS', [:], '18.Q2-J.1.280')
            def nodeMo2 = createNode(NODE_NAME_2, 'ERBS', [:], '18.Q2-J.1.280', false)
            def nodeMo3 = createNode(NODE_NAME_3, 'ERBS', [:], '18.Q2-J.1.280', false)
            def nodeMo4 = createNode('LTE01ERBS0004', 'ERBS', [:], '18.Q2-J.1.280')

            def subMo = moinstanceSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2).
                name('testSub').
                userType(UserType.USER_DEF).
                administrativeState(AdministrationState.ACTIVE).
                build()
            def subMoVerify = moinstanceSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2, nodeMo3, nodeMo4).
                userType(UserType.USER_DEF).
                administrativeState(AdministrationState.ACTIVE).
                name('VerifySub').
                build()

            def verificationSubscription = subscriptionDao.findOneById(subMoVerify.poId, true) as MoinstanceSubscription

        when: 'the subscription is activated with 2 nodes, one of which has pm function off'
            objectUnderTest.execute(subMo.poId)

        then: 'the counter conflict cache has correct values for nodes and counters, both nodes are added'
            def result = counterConflictService.getConflictingCountersInSubscription(verificationSubscription)
            result.nodes == [nodeMo1.fdn, nodeMo2.fdn] as Set
            result.counterEventInfo.containsAll(expectedCounterInfos)
    }

    def 'testing counter conflict cache updates for StatisticalSubscription subscription on adding node with pm function off'() {
        given: '4 nodes in DPS, 2 of which have pm function off, and Activating statistical subscription with 2 counters'
            def nodeMo1 = createNode(NODE_NAME_1, 'ERBS', [:], '18.Q2-J.1.280')
            def nodeMo2 = createNode(NODE_NAME_2, 'ERBS', [:], '18.Q2-J.1.280', false)
            def nodeMo3 = createNode(NODE_NAME_3, 'ERBS', [:], '18.Q2-J.1.280', false)
            def nodeMo4 = createNode('LTE01ERBS0004', 'ERBS', [:], '18.Q2-J.1.280')

            def subMo = statisticalSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2).
                name('testSub').
                userType(UserType.USER_DEF).
                administrativeState(AdministrationState.ACTIVE).
                build()
            def subMoVerify = statisticalSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2, nodeMo3, nodeMo4).
                userType(UserType.USER_DEF).
                administrativeState(AdministrationState.ACTIVE).
                name('VerifySub').
                build()

            def subscription = subscriptionDao.findOneById(subMo.poId, true) as StatisticalSubscription
            def verificationSubscription = subscriptionDao.findOneById(subMoVerify.poId, true) as StatisticalSubscription

        and: 'the subscription already has entries in the counter conflict cache'
            counterConflictService.addNodesAndCounters([nodeMo1.fdn, nodeMo2.fdn] as Set, counters, subscription.name)

        when: 'two more nodes are added to existing subscription, one of which has pm function off'
            objectUnderTest.execute([nodeDao.findOneById(nodeMo3.poId), nodeDao.findOneById(nodeMo4.poId)], subscription)

        then: 'the counter conflict cache adds both nodes'
            def result = counterConflictService.getConflictingCountersInSubscription(verificationSubscription)
            result.nodes == [nodeMo1.fdn, nodeMo2.fdn, nodeMo3.fdn, nodeMo4.fdn] as Set

        and: 'subscriptionCounterCache is not updated'
            result.counterEventInfo.containsAll(expectedCounterInfos)
    }

    def 'testing counter conflict cache updates for MoinstanceSubscription subscription on adding node with pm function off'() {
        given: '4 nodes in DPS, 2 of which have pm function off, and Activating statistical subscription with 2 counters'
            def nodeMo1 = createNode(NODE_NAME_1, 'ERBS', [:], '18.Q2-J.1.280')
            def nodeMo2 = createNode(NODE_NAME_2, 'ERBS', [:], '18.Q2-J.1.280', false)
            def nodeMo3 = createNode(NODE_NAME_3, 'ERBS', [:], '18.Q2-J.1.280', false)
            def nodeMo4 = createNode('LTE01ERBS0004', 'ERBS', [:], '18.Q2-J.1.280')

            def subMo = moinstanceSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2).
                name('testSub').
                userType(UserType.USER_DEF).
                administrativeState(AdministrationState.ACTIVE).
                build()
            def subMoVerify = moinstanceSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2, nodeMo3, nodeMo4).
                userType(UserType.USER_DEF).
                administrativeState(AdministrationState.ACTIVE).
                name('VerifySub').
                build()

            def subscription = subscriptionDao.findOneById(subMo.poId, true) as MoinstanceSubscription
            def verificationSubscription = subscriptionDao.findOneById(subMoVerify.poId, true) as MoinstanceSubscription

        and: 'the subscription already has entries in the counter conflict cache'
            counterConflictService.addNodesAndCounters([nodeMo1.fdn, nodeMo2.fdn] as Set, counters, subscription.name)

        when: 'two more nodes are added to existing subscription, one of which has pm function off'
            objectUnderTest.execute([nodeDao.findOneById(nodeMo3.poId), nodeDao.findOneById(nodeMo4.poId)], subscription)

        then: 'the counter conflict cache adds both nodes'
            def result = counterConflictService.getConflictingCountersInSubscription(verificationSubscription)
            result.nodes == [nodeMo1.fdn, nodeMo2.fdn, nodeMo3.fdn, nodeMo4.fdn] as Set

        and: 'the subscriptionCounterCache is not updated'
            result.counterEventInfo.containsAll(expectedCounterInfos)
    }

    def 'verify that after successful activation, the initiation cache will be empty'() {
        given: 'a Statistical Subscription exists in DPS with valid nodes'
            build3erbsNodes()
            subscriptionMo = statisticalSubscriptionBuilder.name('Test').addCounter(new CounterInfo('Name', 'MoClassType')).addNode(nodeMo1)
                .administrativeState(AdministrationState.ACTIVATING).taskStatus(TaskStatus.OK).build()

        when: 'the subscription is activated successfully'
            objectUnderTest.execute(subscriptionMo.poId)

        then: 'no entries will remain in the initiation cache'
            subscriptionInitiationCacheWrapper.allEntries.empty
    }

    def 'verify that after successful activation of nodes added to subscription, the initiation cache will be empty'() {
        given: 'Statistical Subscription exists in DPS with valid nodes'
            build3erbsNodes()
            def subMo = statisticalSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1, nodeMo2, nodeMo3).
                name('testSub').
                userType(UserType.USER_DEF).
                administrativeState(AdministrationState.ACTIVE).
                build()
            def subscription = subscriptionDao.findOneById(subMo.poId, true) as StatisticalSubscription

        when: '2 nodes are added to subscription'
            objectUnderTest.execute([nodeDao.findOneById(nodeMo2.poId), nodeDao.findOneById(nodeMo3.poId)], subscription)

        then: 'no entries will remain in the initiation cache'
            subscriptionInitiationCacheWrapper.allEntries.empty
    }

    def 'verify that after successful activation of WMG nodes added to systemdefined subscription, the initiation cache will be empty'() {
        given: 'a System defined Statistical Subscription exists in DPS with valid nodes'
            nodeMo1 = createNode(NODE_NAME_1, 'WMG', ['technologyDomain': ['EPS']], '17B')
            def subMo = statisticalSubscriptionBuilder.counters(counters as CounterInfo[]).
                nodes(nodeMo1).
                userType(UserType.SYSTEM_DEF).
                administrativeState(AdministrationState.ACTIVE).
                name('WMGvWMGStatisticalSubscription').
                build()

            def subscription = subscriptionDao.findOneById(subMo.poId, true) as StatisticalSubscription

        when: '1 node is added to subscription'
            objectUnderTest.execute([nodeDao.findOneById(nodeMo1.poId)], subscription)

        then: 'no entries will remain in the initiation cache'
            subscriptionInitiationCacheWrapper.allEntries.empty
    }

    def 'when an active MoInstance Subscription is being Edit and new nodes without moInstance are added to it, Activation task skipped for newly added node'() {
        given: '2 nodes in DPS and Activating moInstance subscription with MoInstance attached with 1 node'
            build3erbsNodes()
            def subMo = moinstanceSubscriptionBuilder.nodes(nodeMo1, nodeMo2).
                setAdditionalAttributes([(moInstances.name()): [[(MoinstanceInfo.MoinstanceInfo100Attribute.nodeName.name()): 'LTE01ERBS0001', (moInstanceName.name()): 'Aal2PathVccTp=b1a3']]]).
                userType(UserType.USER_DEF).
                name('testSub').
                administrativeState(AdministrationState.ACTIVE).
                build()
            def subscription = subscriptionDao.findOneById(subMo.poId, true) as MoinstanceSubscription

        when: 'the subscription is activated'
            objectUnderTest.execute(subscription.id)

        then:
            1 * eventSender.send(_ as MediationTaskRequest)
    }

    def 'when executing the activation for a RES subscription the attachedNode list will be written on DPS and activation tasks sent'() {
        given: 'a RES subscription with 1 node'
            long subId = buildInactiveResSubscription()
            mockedPmResLookUp.fetchAttachedNodes(_, _, _, _) >> attachedNodesList()

        when: 'the subscription is activated'
            objectUnderTest.execute(subId)

        then:
            3 * eventSender.send(_ as SubscriptionActivationTaskRequest)
            def subscription = (ResSubscription) subscriptionDao.findOneById(subId, true)
            subscription.nodes.size() == 1
            subscription.attachedNodes.size() == 2
            subscription.nodes[0].name == rncNodeAttributes.name
    }

    def fillErrorMaps(subId) {
        fillErrorEntryMap(ne1ErrorEntryMap, subId)
        fillErrorEntryMap(ne2ErrorEntryMap, subId)
    }

    def fillErrorEntryMap(inputMap, subId) {
        inputMap.put(PMIC_ATT_SUBSCRIPTION_ID, [subId] as HashSet)
    }

    def buildNodesForRes() {
        attachedNode1Mo = createNode('RBS1', 'RBS', rbsNodeAttributes)
        attachedNode2Mo = createNode('RBS2', 'RBS', rbsNodeAttributes)
        node1Mo = createNode(rncNodeAttributes.name, 'RNC', rncNodeAttributes)
        node2Mo = createNode('RNC02', 'RNC', rncNodeAttributes)
    }

    long buildInactiveResSubscription() {
        buildNodesForRes()
        def subAttributes = ['resSpreadingFactor': ['SF_32']]
        def resSubscriptionMo = dps.subscription().type(SubscriptionType.RES).name(RES_SUBSCRIPTION_NAME).nodes(node1Mo).attributes(subAttributes).administrationState(AdministrationState.ACTIVATING).build()
        return resSubscriptionMo.poId
    }

    long buildActiveResSubscription() {
        buildNodesForRes()
        def subAttributes = ['resSpreadingFactor': ['SF_32']]
        def resSubscriptionMo = dps.subscription().type(SubscriptionType.RES).name(RES_SUBSCRIPTION_NAME).nodes(node1Mo, node2Mo).attributes(subAttributes).attachedNodes(attachedNode1Mo).administrationState(AdministrationState.ACTIVE).build()
        return resSubscriptionMo.poId
    }

    def attachedNodesList() {
        def nodeIds = [
            attachedNode1Mo.poId,
            attachedNode2Mo.poId
        ]
        return nodeDao.findAllById(CollectionUtil.toList(nodeIds))
    }

    def attachedNodesNewList() {
        def nodeIds = [
            attachedNode2Mo.poId
        ]
        return nodeDao.findAllById(CollectionUtil.toList(nodeIds))
    }

    def build3erbsNodes() {
        def neAdditionalAttributes = ['technologyDomain': ['EPS']]
        nodeMo1 = createNode(NODE_NAME_1, NetworkElementType.ERBS, neAdditionalAttributes, '18.Q2-J.1.280')
        nodeMo2 = createNode(NODE_NAME_2, NetworkElementType.ERBS, neAdditionalAttributes, '18.Q2-J.1.280')
        nodeMo3 = createNode(NODE_NAME_3, NetworkElementType.ERBS, neAdditionalAttributes, '18.Q2-J.1.280')
    }

    def createNode(name, type = 'ERBS', attributes = [:], modelId = null, pmEnabled = true, neConfigState = NeConfigurationManagerState.ENABLED) {
        def node = nodeUtil.builder(name)
            .neType(type)
            .attributes(attributes)
            .pmEnabled(pmEnabled)
            .neConfigurationManagerState(neConfigState)
        if (modelId != null) {
            node.ossModelIdentity(modelId)
        }
        node.build()
    }

    def subscriptionBuilderFactory(type, eventList = null, ueInfoList = null) {
        switch (type) {
            case SubscriptionTypeClassMapper.STATISTICAL:
                return statisticalSubscriptionBuilder
            case SubscriptionTypeClassMapper.CELLTRACE:
                if (eventList != null) {
                    cellTraceSubscriptionBuilder.events(eventList as EventInfo[])
                }
                return cellTraceSubscriptionBuilder
            case SubscriptionTypeClassMapper.CONTINUOUSCELLTRACE:
                if (eventList != null) {
                    cctrSubscriptionBuilder.events(eventList as EventInfo[])
                }
                return cctrSubscriptionBuilder
            case SubscriptionTypeClassMapper.UETRACE:
                return ueTraceSubscriptionBuilder
            case SubscriptionTypeClassMapper.EBM:
                if (eventList != null) {
                    ebmSubscriptionBuilder.events(eventList as EventInfo[])
                }
                return ebmSubscriptionBuilder
            case SubscriptionTypeClassMapper.UETR:
                if (eventList != null) {
                    uetrSubscriptionBuilder.events(eventList as EventInfo[])
                }
                if (ueInfoList != null) {
                    uetrSubscriptionBuilder.ueInfoList(ueInfoList)
                }
                return uetrSubscriptionBuilder
            case SubscriptionTypeClassMapper.CTUM:
                return ctumSubscriptionBuilder
            case SubscriptionTypeClassMapper.MOINSTANCE:
                return moinstanceSubscriptionBuilder
            case SubscriptionTypeClassMapper.CELLTRAFFIC:
                if (eventList != null) {
                    cellTrafficSubscriptionBuilder.events(eventList as EventInfo[])
                }
                return cellTrafficSubscriptionBuilder
            case SubscriptionTypeClassMapper.GPEH:
                if (eventList != null) {
                    for (def event : eventList) {
                        gpehSubscriptionBuilder.addEvent(event.groupName, event.name)
                    }
                }
                return gpehSubscriptionBuilder
            case SubscriptionTypeClassMapper.RES:
                def resSubscriptionBuilder = new ResSubscriptionBuilder(dpsUtils)
                return resSubscriptionBuilder
            default:
                return null // not already supported
        // EVENTS, EBS, CELLRELATION, OTHER;
        }
    }
}
