package com.ericsson.oss.services.pm.initiation.notification.events

import static com.ericsson.oss.pmic.cdi.test.util.Constants.*
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ASSOCIATION_SUBSCRIPTION_NODES

import org.mockito.Mockito
import spock.lang.Unroll

import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.UeTraceSubscriptionBuilder
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.*
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.enums.*
import com.ericsson.oss.pmic.impl.counters.PmCountersLifeCycleResolverImpl
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilityReaderImpl
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState
import com.ericsson.oss.services.pm.PmServiceEjbFullSpec
import com.ericsson.oss.services.pm.collection.cache.PmFunctionOffErrorNodeCache
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.ejb.CounterConflictServiceImpl
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper
import com.ericsson.oss.services.pm.initiation.rest.response.ConflictingCounterGroup

class DeactivationEventSpec extends PmServiceEjbFullSpec {
    @ObjectUnderTest
    DeactivationEvent deactivationEvent
    @Inject
    @Modeled
    EventSender<MediationTaskRequest> eventSender
    @Inject
    PMICInitiationTrackerCache initiationTrackerCache
    @Inject
    PmFunctionOffErrorNodeCache errorNodeCache
    @Inject
    CounterConflictServiceImpl counterConflictCacheService
    @Inject
    SubscriptionOperationExecutionTrackingCacheWrapper subscriptionInitiationCacheWrapper
    @Inject
    SubscriptionDao subscriptionDao
    @Inject
    NodeDao nodeDao
    @ImplementationClasses
    def implementationClasses = [PmCapabilityReaderImpl.class, PmCountersLifeCycleResolverImpl.class]
    @MockedImplementation
    TimerService timerService
    @ImplementationInstance
    TimeGenerator timer = Mockito.mock(TimeGenerator)

    def subscriptionMo, nodeMo1, nodeMO2, nodeMO3

    def setup() {
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
    }
    def defaultScannerAttributes = [status: 'ACTIVE', ropPeriod: 900, fileCollectionEnabled: true, errorCode: -1 as short] as Map<String, String>

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

    def 'will not do anything if subscription id does not exist'() {
        when:
            deactivationEvent.execute(123L)
        
        then:
            0 * eventSender.send(_ as MediationTaskRequest)
    }

    @Unroll
    def 'will not call event sender and update subscription state to INACTIVE if there are no nodes in the dps for #subscriptionType.'() {
        given:
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes(fdn, nameSpace, version, subscriptionType, attributes)
        
        when:
            deactivationEvent.execute(subscriptionMo.poId)
        
        then:
            0 * eventSender.send(_ as MediationTaskRequest)
        
        and: 'administration state is inactive'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.INACTIVE.name()
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == TaskStatus.OK.name()
        
        where:
            fdn                                    | nameSpace                           | version | subscriptionType                  | attributes
            'StatisticalSubscription=Test'         | 'pmic_stat_subscription'            | '2.1.0' | 'StatisticalSubscription'         | [type: SubscriptionType.STATISTICAL.name(), userType: UserType.USER_DEF.name(), name: 'Test']
            'CellTraceSubscription=Test'           | 'pmic_cell_subscription'            | '2.1.0' | 'CellTraceSubscription'           | [type: SubscriptionType.CELLTRACE.name(), name: 'Test']
            'UetrSubscription=Test'                | 'pmic_uetr_subscription'            | '1.0.0' | 'UetrSubscription'                | [type: SubscriptionType.UETR.name(), outputMode: OutputModeType.FILE.name(), name: 'Test']
            'UetrSubscription=Test'                | 'pmic_uetr_subscription'            | '1.0.0' | 'UetrSubscription'                | [type: SubscriptionType.UETR.name(), outputMode: OutputModeType.STREAMING.name(), name: 'Test']
            'UetrSubscription=Test'                | 'pmic_uetr_subscription'            | '1.0.0' | 'UetrSubscription'                | [type: SubscriptionType.UETR.name(), outputMode: OutputModeType.FILE_AND_STREAMING.name(), name: 'Test']
            'ContinuousCellTraceSubscription=Test' | 'pmic_continuous_cell_subscription' | '1.1.0' | 'ContinuousCellTraceSubscription' | [type: SubscriptionType.CONTINUOUSCELLTRACE.name(), name: 'Test']
            'EbmSubscription=Test'                 | 'pmic_ebm_subscription'             | '1.2.0' | 'EbmSubscription'                 | [type: SubscriptionType.EBM.name(), name: 'Test']
            'UETraceSubscription=Test'             | 'pmic_ue_subscription'              | '2.2.0' | 'UETraceSubscription'             | [type: SubscriptionType.UETRACE.name(), name: 'Test']
            'CtumSubscription=Test'                | 'pmic_ctum_subscription'            | '1.0.0' | 'CtumSubscription'                | [type: SubscriptionType.CTUM.name(), name: 'Test']
            'ResSubscription=Test'                 | 'pmic_res_subscription'             | '1.0.0' | 'ResSubscription'                 | [type: SubscriptionType.RES.name(), name: 'Test']
    }

    def 'will not call event sender and update subscription state to INACTIVE if there are no nodes in the dps for CellTrace Subscription'() {
        given:
            def attributes = [type: SubscriptionType.CELLTRACE.name(), administrationState: AdministrationState.ACTIVE.name(), taskStatus: TaskStatus.OK.name()]
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes('CellTraceSubscription=EbsTest', 'pmic_cell_subscription', '2.1.0', 'CellTraceSubscription', attributes)
        
        when:
            deactivationEvent.execute(subscriptionMo.poId)
        
        then:
            0 * eventSender.send(_ as MediationTaskRequest)
        
        and: 'administration state is inactive'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.INACTIVE.name()
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == TaskStatus.OK.name()
    }

    @Unroll
    def 'will not call event sender if there are no scanners in dps'() {
        given:
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes(fdn, nameSpace, version, subscriptionType, attributes)
            def erbsNode = nodeUtil.builder('LTE01ERBS0001').pmEnabled(true).build()
            dpsUtils.addAssociation(subscriptionMo, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNode)
        
        when:
            deactivationEvent.execute(subscriptionMo.poId)
        
        then:
            0 * eventSender.send(_ as MediationTaskRequest)
        
        and: 'administration state is inactive'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.INACTIVE.name()
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == TaskStatus.OK.name()
        
        where:
            fdn                                    | nameSpace                           | version | subscriptionType                  | attributes
            'StatisticalSubscription=Test'         | 'pmic_stat_subscription'            | '2.1.0' | 'StatisticalSubscription'         | [type: SubscriptionType.STATISTICAL.name(), userType: UserType.USER_DEF.name(), name: 'testsub']
            'CellTraceSubscription=Test'           | 'pmic_cell_subscription'            | '2.1.0' | 'CellTraceSubscription'           | [type: SubscriptionType.CELLTRACE.name(), name: 'testsub']
            'ContinuousCellTraceSubscription=Test' | 'pmic_continuous_cell_subscription' | '1.1.0' | 'ContinuousCellTraceSubscription' | [type: SubscriptionType.CONTINUOUSCELLTRACE.name(), name: 'testsub']
            'EbmSubscription=Test'                 | 'pmic_ebm_subscription'             | '1.2.0' | 'EbmSubscription'                 | [type: SubscriptionType.EBM.name(), name: 'testsub']
            'CtumSubscription=Test'                | 'pmic_ctum_subscription'            | '1.0.0' | 'CtumSubscription'                | [type: SubscriptionType.CTUM.name(), name: 'testsub']
            'ResSubscription=Test'                 | 'pmic_res_subscription'             | '1.0.0' | 'ResSubscription'                 | [type: SubscriptionType.RES.name(), userType: UserType.USER_DEF.name(), name: 'testsub']
    }

    def 'will not call event sender if there are no scanners in dps for Cell Trace subscription'() {
        given:
            def attributes = [type: SubscriptionType.CELLTRACE.name(), administrationState: AdministrationState.ACTIVE.name(), taskStatus: TaskStatus.OK.name()]
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes('CellTraceSubscription=Test', 'pmic_cell_subscription', '2.1.0', 'CellTraceSubscription', attributes)
            def erbsNode = nodeUtil.builder('LTE01ERBS0001').pmEnabled(true).build()
            dpsUtils.addAssociation(subscriptionMo, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNode)

        when:
            deactivationEvent.execute(subscriptionMo.poId)
        
        then:
            0 * eventSender.send(_ as MediationTaskRequest)
        
        and: 'administration state is inactive'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.INACTIVE.name()
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == TaskStatus.OK.name()
    }

    @Unroll
    def 'will not call event sender if NeConfigurationManager disabled for nodes'() {
        given:
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes(fdn, nameSpace, version, subscriptionType, attributes)
            def erbsNode = nodeUtil.builder('LTE01ERBS0001').neConfigurationManagerState(NeConfigurationManagerState.DISABLED).build()
            dpsUtils.addAssociation(subscriptionMo, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNode)
            scannerUtil.builder(scannerName, 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(scannerType).subscriptionId(subscriptionMo.poId).errorCode(-1 as Short).build()
        
        when:
            deactivationEvent.execute(subscriptionMo.poId)
        
        then:
            0 * eventSender.send(_ as MediationTaskRequest)
        
        
        and: 'administration state is inactive'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.INACTIVE.name()
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == TaskStatus.OK.name()
        
        where:
            fdn                                    | nameSpace                           | version | subscriptionType                  | attributes                                                                                       | scannerName                    | scannerType
            'StatisticalSubscription=Test'         | 'pmic_stat_subscription'            | '2.1.0' | 'StatisticalSubscription'         | [type: SubscriptionType.STATISTICAL.name(), userType: UserType.USER_DEF.name(), name: 'testsub'] | 'USERDEF-testsub.Cont.Y.STATS' | ProcessType.STATS
            'CellTraceSubscription=Test'           | 'pmic_cell_subscription'            | '2.1.0' | 'CellTraceSubscription'           | [type: SubscriptionType.CELLTRACE.name(), name: 'testsub']                                       | 'PREDEF.10002.CELLTRACE'       | ProcessType.NORMAL_PRIORITY_CELLTRACE
            'ContinuousCellTraceSubscription=Test' | 'pmic_continuous_cell_subscription' | '1.1.0' | 'ContinuousCellTraceSubscription' | [type: SubscriptionType.CONTINUOUSCELLTRACE.name(), name: 'testsub']                             | 'PREDEF.10005.CELLTRACE'       | ProcessType.HIGH_PRIORITY_CELLTRACE
            'EbmSubscription=Test'                 | 'pmic_ebm_subscription'             | '1.2.0' | 'EbmSubscription'                 | [type: SubscriptionType.EBM.name(), name: 'testsub']                                             | 'USERDEF-testsub.Cont.Y.STATS' | ProcessType.EVENTJOB
            'CtumSubscription=Test'                | 'pmic_ctum_subscription'            | '1.0.0' | 'CtumSubscription'                | [type: SubscriptionType.CTUM.name(), name: 'testsub']                                            | 'USERDEF-testsub.Cont.Y.STATS' | ProcessType.CTUM
            'ResSubscription=Test'                 | 'pmic_res_subscription'             | '1.0.0' | 'ResSubscription'                 | [type: SubscriptionType.RES.name(), userType: UserType.USER_DEF.name(), name: 'testsub']         | 'USERDEF-testsub.Cont.Y.STATS' | ProcessType.STATS
    }

    @Unroll
    def 'will call event sender and deactivate #subscriptionType subscription'() {
        given:
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes(fdn, nameSpace, version, subscriptionType, attributes)
            def erbsNode = nodeUtil.builder('LTE01ERBS0001').pmEnabled(true).build()
            dpsUtils.addAssociation(subscriptionMo, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNode)
            scannerUtil.builder(scannerName, 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(scannerType).subscriptionId(subscriptionMo.poId).errorCode(-1 as Short).build()

        when:
            deactivationEvent.execute(subscriptionMo.poId)

        then:
            1 * eventSender.send(_ as MediationTaskRequest)

        and: 'administration state is inactive'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.DEACTIVATING.name()

        and: 'initiation tracker is created for the MTR'
            initiationTrackerCache.getTracker(subscriptionMo.poId as String).subscriptionId == subscriptionMo.poId as String

        where:
            fdn                                    | nameSpace                           | version | subscriptionType                  | attributes                                                                                    | scannerName                 | scannerType
            'StatisticalSubscription=Test'         | 'pmic_stat_subscription'            | '2.1.0' | 'StatisticalSubscription'         | [type: SubscriptionType.STATISTICAL.name(), name: 'Test', userType: UserType.USER_DEF.name()] | 'USERDEF-Test.Cont.Y.STATS' | ProcessType.STATS
            'CellTraceSubscription=Test'           | 'pmic_cell_subscription'            | '2.1.0' | 'CellTraceSubscription'           | [type: SubscriptionType.CELLTRACE.name(), name: 'Test']                                       | 'PREDEF.10002.CELLTRACE'    | ProcessType.NORMAL_PRIORITY_CELLTRACE
            'ContinuousCellTraceSubscription=Test' | 'pmic_continuous_cell_subscription' | '1.1.0' | 'ContinuousCellTraceSubscription' | [type: SubscriptionType.CONTINUOUSCELLTRACE.name(), name: 'Test']                             | 'PREDEF.10005.CELLTRACE'    | ProcessType.HIGH_PRIORITY_CELLTRACE
            'ResSubscription=Test'                 | 'pmic_res_subscription'             | '1.0.0' | 'ResSubscription'                 | [type: SubscriptionType.RES.name(), name: 'Test', userType: UserType.USER_DEF.name()]         | 'USERDEF-Test.Cont.Y.STATS' | ProcessType.STATS
    }

    def 'will call event sender and deactivate for Cell Trace subscription'() {
        given:
            def attributes = [type: SubscriptionType.CELLTRACE.name(), name: 'EbsTest', administrationState: AdministrationState.ACTIVE.name(), taskStatus: TaskStatus.OK.name()]
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes('CellTraceSubscription=Test', 'pmic_cell_subscription', '2.1.0', 'CellTraceSubscription', attributes)
            def erbsNode = nodeUtil.builder('LTE01ERBS0001').pmEnabled(true).build()
            dpsUtils.addAssociation(subscriptionMo, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNode)
            scannerUtil.builder(PREDEF_10000_CELLTRACE_SCANNER, 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).subscriptionId(subscriptionMo.poId).errorCode(-1 as Short).build()
            scannerUtil.builder('PREDEF.10004.CELLTRACE', 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(ProcessType.HIGH_PRIORITY_CELLTRACE).subscriptionId(subscriptionMo.poId).errorCode(-1 as Short).build()
        
        when:
            deactivationEvent.execute(subscriptionMo.poId)
        
        then:
            1 * eventSender.send(_ as MediationTaskRequest)
        
        and: 'administration state is inactive'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.DEACTIVATING.name()
        
        and: 'initiation tracker is created for the MTR'
            initiationTrackerCache.getTracker(subscriptionMo.poId as String).subscriptionId == subscriptionMo.poId as String
    }

    @Unroll
    def 'will call event sender and deactivate #subscriptionType subscription and remove error node entries for all subscription nodes'() {
        given:
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes(fdn, nameSpace, version, subscriptionType, attributes)
            def erbsNode = nodeUtil.builder('LTE01ERBS0001').pmEnabled(true).build()
            def erbsNodeWithPmFunctionOff = nodeUtil.builder('LTE01ERBS0002').pmEnabled(false).build()
            dpsUtils.addAssociation(subscriptionMo, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNode, erbsNodeWithPmFunctionOff)
            scannerUtil.builder(scannerName, 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(scannerType).subscriptionId(subscriptionMo.poId).build()
            errorNodeCache.addNodeWithPmFunctionOff(erbsNodeWithPmFunctionOff.fdn, subscriptionMo.poId)

        when:
            deactivationEvent.execute(subscriptionMo.poId)

        then: 'send deactivation MTR only for the first nodes since only this node has an active scanner'
            1 * eventSender.send(_ as MediationTaskRequest)

        and: 'administration state is deactivating'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.DEACTIVATING.name()

        and: 'initiation tracker is created for the MTR'
            initiationTrackerCache.getTracker(subscriptionMo.poId as String).subscriptionId == subscriptionMo.poId as String

        and: 'error node cache is modified. All entries for this subscription will be removed'
            (errorNodeCache.getErrorEntry(erbsNodeWithPmFunctionOff.fdn) as Map).isEmpty()

        where:
            fdn                                    | nameSpace                           | version | subscriptionType                  | attributes                                                                                    | scannerName                 | scannerType
            'StatisticalSubscription=Test'         | 'pmic_stat_subscription'            | '2.1.0' | 'StatisticalSubscription'         | [type: SubscriptionType.STATISTICAL.name(), name: 'Test', userType: UserType.USER_DEF.name()] | 'USERDEF-Test.Cont.Y.STATS' | ProcessType.STATS
            'CellTraceSubscription=Test'           | 'pmic_cell_subscription'            | '2.1.0' | 'CellTraceSubscription'           | [type: SubscriptionType.CELLTRACE.name(), name: 'Test']                                       | 'PREDEF.10002.CELLTRACE'    | ProcessType.NORMAL_PRIORITY_CELLTRACE
            'ContinuousCellTraceSubscription=Test' | 'pmic_continuous_cell_subscription' | '1.1.0' | 'ContinuousCellTraceSubscription' | [type: SubscriptionType.CONTINUOUSCELLTRACE.name(), name: 'Test']                             | 'PREDEF.10005.CELLTRACE'    | ProcessType.HIGH_PRIORITY_CELLTRACE
            'ResSubscription=Test'                 | 'pmic_res_subscription'             | '1.0.0' | 'ResSubscription'                 | [type: SubscriptionType.RES.name(), name: 'Test', userType: UserType.USER_DEF.name()]         | 'USERDEF-Test.Cont.Y.STATS' | ProcessType.STATS
    }

    def 'will call event sender and deactivate Cell Trace subscription and remove error node entries'() {
        given:
            def attributes = [type: SubscriptionType.CELLTRACE.name(), name: 'EbsTest', administrationState: AdministrationState.ACTIVE.name(), taskStatus: TaskStatus.OK.name()]
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes('CellTraceSubscription=Test', 'pmic_cell_subscription', '2.1.0', 'CellTraceSubscription', attributes)
            def erbsNode = nodeUtil.builder('LTE01ERBS0001').pmEnabled(true).build()
            def erbsNodeWithPmFunctionOff = nodeUtil.builder('LTE01ERBS0002').pmEnabled(false).build()
            dpsUtils.addAssociation(subscriptionMo, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNode, erbsNodeWithPmFunctionOff)
            scannerUtil.builder(PREDEF_10000_CELLTRACE_SCANNER, 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).subscriptionId(subscriptionMo.poId).errorCode(-1 as Short).build()
            scannerUtil.builder('PREDEF.10004.CELLTRACE', 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(ProcessType.HIGH_PRIORITY_CELLTRACE).subscriptionId(subscriptionMo.poId).errorCode(-1 as Short).build()
            errorNodeCache.addNodeWithPmFunctionOff(erbsNodeWithPmFunctionOff.fdn, subscriptionMo.poId)
        
        when:
            deactivationEvent.execute(subscriptionMo.poId)
        
        then: 'send deactivation MTR only for the first nodes since only this node has an active scanner'
            1 * eventSender.send(_ as MediationTaskRequest)
        
        and: 'administration state is deactivating'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.DEACTIVATING.name()
        
        and: 'initiation tracker is created for the MTR'
            initiationTrackerCache.getTracker(subscriptionMo.poId as String).subscriptionId == subscriptionMo.poId as String
       
        and: 'error node cache is modified. All entries for this subscription will be removed'
            (errorNodeCache.getErrorEntry(erbsNodeWithPmFunctionOff.fdn) as Map).isEmpty()

    }

    @Unroll
    def 'will call event sender and deactivate uetrace subscription'() {
        given:
            def subscriptionMo = new UeTraceSubscriptionBuilder(dpsUtils).administrativeState(AdministrationState.ACTIVE).name('Test').build()
            def erbsNodeWithPmFunctionOff = nodeUtil.builder('LTE01SGSN0002').neType('SGSN-MME').pmEnabled(false).build()
            nodeUtil.builder('LTE01SGSN0003').neType('SGSN-MME').pmEnabled(false).build()
            errorNodeCache.addNodeWithPmFunctionOff(erbsNodeWithPmFunctionOff.fdn, subscriptionMo.poId)

        when:
            deactivationEvent.execute(subscriptionMo.poId)

        then:
            2 * eventSender.send(_ as MediationTaskRequest)

        and: 'administration state is deactivating'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.DEACTIVATING.name()

        and: 'initiation tracker is created for the MTR'
            initiationTrackerCache.getTracker(subscriptionMo.poId as String).subscriptionId == subscriptionMo.poId as String

        and: 'error node cache is modified. All entries for this subscription will be removed. Not really sure if this is a real use case for urtrace'
            (errorNodeCache.getErrorEntry(erbsNodeWithPmFunctionOff.fdn) as Map).isEmpty()
    }

    @Unroll
    def 'will call event sender and deactivate #subscriptionType subscription when nodes are removed from subscription'() {
        given: 'subscription with 2 nodes where one node has pm function OFF and no scanner on this node'
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes(fdn, nameSpace, version, subscriptionType, attributes)
            def erbsNode = nodeUtil.builder('LTE01ERBS0001').pmEnabled(true).build()
            def erbsNodeWithPmFunctionOff = nodeUtil.builder('LTE01ERBS0002').pmEnabled(false).build()
            dpsUtils.addAssociation(subscriptionMo, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNode, erbsNodeWithPmFunctionOff)
            scannerUtil.builder(scannerName, 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(scannerType).subscriptionId(subscriptionMo.poId).build()
            errorNodeCache.addNodeWithPmFunctionOff(erbsNodeWithPmFunctionOff.fdn, subscriptionMo.poId)
            def node1 = nodeDao.findOneById(erbsNode.poId)
            def node2 = nodeDao.findOneById(erbsNodeWithPmFunctionOff.poId)

            def subscription = subscriptionDao.findOneById(subscriptionMo.poId, true)

        when:
            deactivationEvent.execute([node1, node2], subscription)

        then: 'send deactivation MTR only for the first nodes since only this node has an active scanner'
            1 * eventSender.send(_ as MediationTaskRequest)

        and: 'initiation tracker is created for the MTR'
            initiationTrackerCache.getTracker(subscriptionMo.poId as String).subscriptionId == subscriptionMo.poId as String

        and: 'error node cache is modified. All entries for this subscription will be removed'
            (errorNodeCache.getErrorEntry(erbsNodeWithPmFunctionOff.fdn) as Map).isEmpty()

        where:
            fdn                                    | nameSpace                           | version | subscriptionType                  | attributes                                                                                                                                                        | scannerName                 | scannerType                           | subClass
            'StatisticalSubscription=Test'         | 'pmic_stat_subscription'            | '2.1.0' | 'StatisticalSubscription'         | [type: SubscriptionType.STATISTICAL.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.USER_DEF.name()]           | 'USERDEF-Test.Cont.Y.STATS' | ProcessType.STATS                     | StatisticalSubscription
            'CellTraceSubscription=Test'           | 'pmic_cell_subscription'            | '2.1.0' | 'CellTraceSubscription'           | [type: SubscriptionType.CELLTRACE.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.SYSTEM_DEF.name()]           | 'PREDEF.10002.CELLTRACE'    | ProcessType.NORMAL_PRIORITY_CELLTRACE | CellTraceSubscription
            'ContinuousCellTraceSubscription=Test' | 'pmic_continuous_cell_subscription' | '1.1.0' | 'ContinuousCellTraceSubscription' | [type: SubscriptionType.CONTINUOUSCELLTRACE.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.SYSTEM_DEF.name()] | 'PREDEF.10005.CELLTRACE'    | ProcessType.HIGH_PRIORITY_CELLTRACE   | ContinuousCellTraceSubscription
            'ResSubscription=Test'                 | 'pmic_res_subscription'             | '1.0.0' | 'ResSubscription'                 | [type: SubscriptionType.RES.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.USER_DEF.name()]                   | 'USERDEF-Test.Cont.Y.STATS' | ProcessType.STATS                     | StatisticalSubscription
    }

    def 'will call event sender and deactivate Celltrace subscription when nodes are removed from subscription'() {
        given: 'subscription with 2 nodes where one node has pm function OFF and no scanner on this node'
            def attributes = [type: SubscriptionType.CELLTRACE.name(), name: 'EbsTest', administrationState: AdministrationState.UPDATING.name(), userType: UserType.SYSTEM_DEF.name()]
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes('CellTraceSubscription=Test', 'pmic_cell_subscription', '2.1.0', 'CellTraceSubscription', attributes)
            def erbsNode = nodeUtil.builder('LTE01ERBS0001').pmEnabled(true).build()
            def erbsNodeWithPmFunctionOff = nodeUtil.builder('LTE01ERBS0002').pmEnabled(false).build()
            dpsUtils.addAssociation(subscriptionMo, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNode, erbsNodeWithPmFunctionOff)
            scannerUtil.builder(PREDEF_10001_CELLTRACE_SCANNER, 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).subscriptionId(subscriptionMo.poId).errorCode(-1 as Short).build()
            scannerUtil.builder('PREDEF.10004.CELLTRACE', 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(ProcessType.HIGH_PRIORITY_CELLTRACE).subscriptionId(subscriptionMo.poId).errorCode(-1 as Short).build()
            errorNodeCache.addNodeWithPmFunctionOff(erbsNodeWithPmFunctionOff.fdn, subscriptionMo.poId)
            def node1 = nodeDao.findOneById(erbsNode.poId)
            def node2 = nodeDao.findOneById(erbsNodeWithPmFunctionOff.poId)
            def subscription = subscriptionDao.findOneById(subscriptionMo.poId, true)

        when:
            deactivationEvent.execute([node1, node2], subscription)

        then: 'send deactivation MTR only for the first nodes since only this node has an active scanner'
            1 * eventSender.send(_ as MediationTaskRequest)

        and: 'initiation tracker is created for the MTR'
            initiationTrackerCache.getTracker(subscriptionMo.poId as String).subscriptionId == subscriptionMo.poId as String

        and: 'error node cache is modified. All entries for this subscription will be removed'
            (errorNodeCache.getErrorEntry(erbsNodeWithPmFunctionOff.fdn) as Map).isEmpty()
    }

    @Unroll
    def 'will not call event sender and not set INACTIVE #subscriptionType subscription when subscrition is in UPDATING/ACTIVE status'() {
        given: 'subscription with 2 nodes where one node has pm function OFF and no scanner on this node'
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes(fdn, nameSpace, version, subscriptionType, attributes)
            def erbsNode = nodeUtil.builder('LTE01ERBS0001').pmEnabled(true).build()
            def erbsNodeWithPmFunctionOff = nodeUtil.builder('LTE01ERBS0002').pmEnabled(false).build()
            dpsUtils.addAssociation(subscriptionMo, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNode, erbsNodeWithPmFunctionOff)
            scannerUtil.builder(scannerName, 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(scannerType).subscriptionId(subscriptionMo.poId).build()

            errorNodeCache.addNodeWithPmFunctionOff(erbsNodeWithPmFunctionOff.fdn, subscriptionMo.poId)
            def node2 = nodeDao.findOneById(erbsNodeWithPmFunctionOff.poId)

            def subscription = subscriptionDao.findOneById(subscriptionMo.poId, true)

        when:
            deactivationEvent.execute([node2], subscription)

        then: 'No deactivation MTR is sent'
            0 * eventSender.send(_ as MediationTaskRequest)
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.ACTIVE.name()

        and: 'error node cache is modified. All entries for this subscription will be removed'
            (errorNodeCache.getErrorEntry(erbsNodeWithPmFunctionOff.fdn) as Map).isEmpty()

        where:
            fdn                                    | nameSpace                           | version | subscriptionType                  | attributes                                                                                                                                                      | scannerName                 | scannerType                           | subClass
            'StatisticalSubscription=Test'         | 'pmic_stat_subscription'            | '2.1.0' | 'StatisticalSubscription'         | [type: SubscriptionType.STATISTICAL.name(), name: 'Test', administrationState: AdministrationState.ACTIVE.name(), userType: UserType.USER_DEF.name()]           | 'USERDEF-Test.Cont.Y.STATS' | ProcessType.STATS                     | StatisticalSubscription
            'StatisticalSubscription=Test'         | 'pmic_stat_subscription'            | '2.1.0' | 'StatisticalSubscription'         | [type: SubscriptionType.STATISTICAL.name(), name: 'Test', administrationState: AdministrationState.ACTIVE.name(), userType: UserType.SYSTEM_DEF.name()]         | 'PREDEF.STATS'              | ProcessType.STATS                     | StatisticalSubscription
            'CellTraceSubscription=Test'           | 'pmic_cell_subscription'            | '2.1.0' | 'CellTraceSubscription'           | [type: SubscriptionType.CELLTRACE.name(), name: 'Test', administrationState: AdministrationState.ACTIVE.name(), userType: UserType.SYSTEM_DEF.name()]           | 'PREDEF.10002.CELLTRACE'    | ProcessType.NORMAL_PRIORITY_CELLTRACE | CellTraceSubscription
            'ContinuousCellTraceSubscription=Test' | 'pmic_continuous_cell_subscription' | '1.1.0' | 'ContinuousCellTraceSubscription' | [type: SubscriptionType.CONTINUOUSCELLTRACE.name(), name: 'Test', administrationState: AdministrationState.ACTIVE.name(), userType: UserType.SYSTEM_DEF.name()] | 'PREDEF.10005.CELLTRACE'    | ProcessType.HIGH_PRIORITY_CELLTRACE   | ContinuousCellTraceSubscription
            'ResSubscription=Test'                 | 'pmic_res_subscription'             | '1.0.0' | 'ResSubscription'                 | [type: SubscriptionType.RES.name(), name: 'Test', administrationState: AdministrationState.ACTIVE.name(), userType: UserType.USER_DEF.name()]                   | 'USERDEF-Test.Cont.Y.STATS' | ProcessType.STATS                     | ResSubscription
    }

    @Unroll
    def 'will not call event sender, updated #subscriptionType subscription state to INACTIVE when last node are removed from Active subscription'() {
        given: 'subscription with node of pmFunction OFF'
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes(fdn, nameSpace, version, subscriptionType, attributes)
            def subscription = subscriptionDao.findOneById(subscriptionMo.poId, true)
            def erbsNodeWithPmFunctionOff = nodeUtil.builder('LTE01ERBS0001').pmEnabled(false).build()
            dpsUtils.addAssociation(subscriptionMo, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNodeWithPmFunctionOff)
            errorNodeCache.addNodeWithPmFunctionOff(erbsNodeWithPmFunctionOff.fdn, subscriptionMo.poId)
            def node1 = nodeDao.findOneById(erbsNodeWithPmFunctionOff.poId)

            subscription.setType(SubscriptionType.valueOf(attributes.get('type') as String))
            subscription.setUserType(UserType.valueOf(attributes.get('userType') as String))

        when: 'Remove node from subscription Object'
            subscriptionMo.removeAssociation(PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNodeWithPmFunctionOff)

        and: 'Call Deactivate event execute'
            deactivationEvent.execute([node1], subscription)

        then: 'will not send deactivation MTR as there is no scanner'
            0 * eventSender.send(_ as MediationTaskRequest)

        and: 'administration state is inactive'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.INACTIVE.name()
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == TaskStatus.OK.name()

        where:
            fdn                                    | nameSpace                           | version | subscriptionType                  | attributes
            'StatisticalSubscription=Test'         | 'pmic_stat_subscription'            | '2.1.0' | 'StatisticalSubscription'         | [type: SubscriptionType.STATISTICAL.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.USER_DEF.name()]
            'CellTraceSubscription=Test'           | 'pmic_cell_subscription'            | '2.1.0' | 'CellTraceSubscription'           | [type: SubscriptionType.CELLTRACE.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.SYSTEM_DEF.name()]
            'UetrSubscription=Test'                | 'pmic_uetr_subscription'            | '1.0.0' | 'UetrSubscription'                | [type: SubscriptionType.UETR.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.SYSTEM_DEF.name(), outputMode: OutputModeType.FILE.name()]
            'UetrSubscription=Test'                | 'pmic_uetr_subscription'            | '1.0.0' | 'UetrSubscription'                | [type: SubscriptionType.UETR.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.SYSTEM_DEF.name(), outputMode: OutputModeType.STREAMING.name()]
            'ContinuousCellTraceSubscription=Test' | 'pmic_continuous_cell_subscription' | '1.1.0' | 'ContinuousCellTraceSubscription' | [type: SubscriptionType.CONTINUOUSCELLTRACE.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.SYSTEM_DEF.name()]
            'ResSubscription=Test'                 | 'pmic_res_subscription'             | '1.0.0' | 'ResSubscription'                 | [type: SubscriptionType.RES.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.USER_DEF.name()]
    }

    @Unroll
    def 'will call event sender and deactivate #subscriptionType subscription when nodes are removed from subscription. Sends deactivation event for all nodes regardless of pm function'() {
        given: 'subscription with 2 nodes, active scanners and pm function OFF'
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes(fdn, nameSpace, version, subscriptionType, attributes)
            def erbsNodeWithPmFunctionOff = nodeUtil.builder('LTE01ERBS0001').pmEnabled(false).build()
            def erbsNodeWithPmFunctionOff1 = nodeUtil.builder('LTE01ERBS0002').pmEnabled(false).build()
            dpsUtils.addAssociation(subscriptionMo, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNodeWithPmFunctionOff, erbsNodeWithPmFunctionOff1)
            scannerUtil.builder(scannerName, 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(scannerType).subscriptionId(subscriptionMo.poId).build()
            scannerUtil.builder(scannerName, 'LTE01ERBS0002').attributes(defaultScannerAttributes).processType(scannerType).subscriptionId(subscriptionMo.poId).build()
            errorNodeCache.addNodeWithPmFunctionOff(erbsNodeWithPmFunctionOff.fdn, subscriptionMo.poId)
            errorNodeCache.addNodeWithPmFunctionOff(erbsNodeWithPmFunctionOff1.fdn, subscriptionMo.poId)
            def node1 = nodeDao.findOneById(erbsNodeWithPmFunctionOff.poId)
            def node2 = nodeDao.findOneById(erbsNodeWithPmFunctionOff1.poId)

            def subscription = subscriptionDao.findOneById(subscriptionMo.poId, true)

        when:
            deactivationEvent.execute([node1, node2], subscription)

        then: 'send deactivation MTRs for both nodes even if PM function is OFF because they have active scanners in DPS'
            2 * eventSender.send(_ as MediationTaskRequest)

        and: 'initiation tracker is created for the MTR'
            initiationTrackerCache.getTracker(subscriptionMo.poId as String).subscriptionId == subscriptionMo.poId as String

        and: 'error node cache is modified. All entries for this subscription will be removed'
            (errorNodeCache.getErrorEntry(erbsNodeWithPmFunctionOff1.fdn) as Map).isEmpty()

        and: 'subscription status is set to Updating'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.UPDATING.name()

        where:
            fdn                                    | nameSpace                           | version | subscriptionType                  | attributes                                                                                                                                                        | scannerName                 | scannerType                           | subClass
            'StatisticalSubscription=Test'         | 'pmic_stat_subscription'            | '2.1.0' | 'StatisticalSubscription'         | [type: SubscriptionType.STATISTICAL.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.USER_DEF.name()]           | 'USERDEF-Test.Cont.Y.STATS' | ProcessType.STATS                     | StatisticalSubscription
            'CellTraceSubscription=Test'           | 'pmic_cell_subscription'            | '2.1.0' | 'CellTraceSubscription'           | [type: SubscriptionType.CELLTRACE.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.SYSTEM_DEF.name()]           | 'PREDEF.10002.CELLTRACE'    | ProcessType.NORMAL_PRIORITY_CELLTRACE | CellTraceSubscription
            'ContinuousCellTraceSubscription=Test' | 'pmic_continuous_cell_subscription' | '1.1.0' | 'ContinuousCellTraceSubscription' | [type: SubscriptionType.CONTINUOUSCELLTRACE.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.SYSTEM_DEF.name()] | 'PREDEF.10005.CELLTRACE'    | ProcessType.HIGH_PRIORITY_CELLTRACE   | ContinuousCellTraceSubscription
            'ResSubscription=Test'                 | 'pmic_res_subscription'             | '1.0.0' | 'ResSubscription'                 | [type: SubscriptionType.RES.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.USER_DEF.name()]                   | 'USERDEF-Test.Cont.Y.STATS' | ProcessType.STATS                     | ResSubscription
    }

    def 'will call event sender and deactivate Celltrace subscription when nodes are removed from subscription. Sends deactivation event for all nodes regardless of pm function'() {
        given: 'subscription with 2 nodes, active scanners and pm function OFF'
            def attributes = [type: SubscriptionType.CELLTRACE.name(), name: 'EbsTest', administrationState: AdministrationState.UPDATING.name(), userType: UserType.SYSTEM_DEF.name()]
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes('CellTraceSubscription=Test', 'pmic_cell_subscription', '2.1.0', 'CellTraceSubscription', attributes)
            def erbsNodeWithPmFunctionOff = nodeUtil.builder('LTE01ERBS0001').pmEnabled(false).build()
            def erbsNodeWithPmFunctionOff1 = nodeUtil.builder('LTE01ERBS0002').pmEnabled(false).build()
            dpsUtils.addAssociation(subscriptionMo, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNodeWithPmFunctionOff, erbsNodeWithPmFunctionOff1)
            scannerUtil.builder(PREDEF_10000_CELLTRACE_SCANNER, 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).subscriptionId(subscriptionMo.poId).errorCode(-1 as Short).build()
            scannerUtil.builder('PREDEF.10004.CELLTRACE', 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(ProcessType.HIGH_PRIORITY_CELLTRACE).subscriptionId(subscriptionMo.poId).errorCode(-1 as Short).build()
            scannerUtil.builder(PREDEF_10001_CELLTRACE_SCANNER, 'LTE01ERBS0002').attributes(defaultScannerAttributes).processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).subscriptionId(subscriptionMo.poId).errorCode(-1 as Short).build()
            scannerUtil.builder('PREDEF.10004.CELLTRACE', 'LTE01ERBS0002').attributes(defaultScannerAttributes).processType(ProcessType.HIGH_PRIORITY_CELLTRACE).subscriptionId(subscriptionMo.poId).errorCode(-1 as Short).build()
            errorNodeCache.addNodeWithPmFunctionOff(erbsNodeWithPmFunctionOff.fdn, subscriptionMo.poId)
            errorNodeCache.addNodeWithPmFunctionOff(erbsNodeWithPmFunctionOff1.fdn, subscriptionMo.poId)
            def node1 = nodeDao.findOneById(erbsNodeWithPmFunctionOff.poId)
            def node2 = nodeDao.findOneById(erbsNodeWithPmFunctionOff1.poId)

            def subscription = subscriptionDao.findOneById(subscriptionMo.poId, true)

        when:
            deactivationEvent.execute([node1, node2], subscription)

        then: 'send deactivation MTRs for both nodes even if PM function is OFF because they have active scanners in DPS'
            2 * eventSender.send(_ as MediationTaskRequest)

        and: 'initiation tracker is created for the MTR'
            initiationTrackerCache.getTracker(subscriptionMo.poId as String).subscriptionId == subscriptionMo.poId as String

        and: 'error node cache is modified. All entries for this subscription will be removed'
            (errorNodeCache.getErrorEntry(erbsNodeWithPmFunctionOff1.fdn) as Map).isEmpty()

        and: 'subscription status is set to Updating'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.UPDATING.name()
    }

    @Unroll
    def 'will call event sender and deactivate #subscriptionType subscription and remove error node entries for all nodes regardless of Pm Function'() {
        given:
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes(fdn, nameSpace, version, subscriptionType, attributes)
            def erbsNodeWithPmFunctionOff = nodeUtil.builder('LTE01ERBS0002').pmEnabled(false).build()
            def erbsNodeWithPmFunctionOff1 = nodeUtil.builder('LTE01ERBS0001').pmEnabled(true).build()
            dpsUtils.addAssociation(subscriptionMo, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNodeWithPmFunctionOff1, erbsNodeWithPmFunctionOff)
            scannerUtil.builder(scannerName, 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(scannerType).subscriptionId(subscriptionMo.poId).build()
            scannerUtil.builder(scannerName, 'LTE01ERBS0002').attributes(defaultScannerAttributes).processType(scannerType).subscriptionId(subscriptionMo.poId).build()
            errorNodeCache.addNodeWithPmFunctionOff(erbsNodeWithPmFunctionOff.fdn, subscriptionMo.poId)
            errorNodeCache.addNodeWithPmFunctionOff(erbsNodeWithPmFunctionOff1.fdn, subscriptionMo.poId)

        when:
            deactivationEvent.execute(subscriptionMo.poId)

        then: 'send deactivation MTRs for both nodes even if PM function is OFF because they have active scanners in DPS'
            2 * eventSender.send(_ as MediationTaskRequest)

        and: 'administration state is deactivating'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.DEACTIVATING.name()

        and: 'initiation tracker is created for the MTR'
            initiationTrackerCache.getTracker(subscriptionMo.poId as String).subscriptionId == subscriptionMo.poId as String

        and: 'error node cache is modified. All entries for this subscription will be removed'
            (errorNodeCache.getErrorEntry(erbsNodeWithPmFunctionOff.fdn) as Map).isEmpty()

        where:
            fdn                                    | nameSpace                           | version | subscriptionType                  | attributes                                                                                    | scannerName                 | scannerType
            'StatisticalSubscription=Test'         | 'pmic_stat_subscription'            | '2.1.0' | 'StatisticalSubscription'         | [type: SubscriptionType.STATISTICAL.name(), name: 'Test', userType: UserType.USER_DEF.name()] | 'USERDEF-Test.Cont.Y.STATS' | ProcessType.STATS
            'CellTraceSubscription=Test'           | 'pmic_cell_subscription'            | '2.1.0' | 'CellTraceSubscription'           | [type: SubscriptionType.CELLTRACE.name(), name: 'Test']                                       | 'PREDEF.10002.CELLTRACE'    | ProcessType.NORMAL_PRIORITY_CELLTRACE
            'ContinuousCellTraceSubscription=Test' | 'pmic_continuous_cell_subscription' | '1.1.0' | 'ContinuousCellTraceSubscription' | [type: SubscriptionType.CONTINUOUSCELLTRACE.name(), name: 'Test']                             | 'PREDEF.10005.CELLTRACE'    | ProcessType.HIGH_PRIORITY_CELLTRACE
            'ResSubscription=Test'                 | 'pmic_res_subscription'             | '1.0.0' | 'ResSubscription'                 | [type: SubscriptionType.RES.name(), name: 'Test', userType: UserType.USER_DEF.name()]         | 'USERDEF-Test.Cont.Y.STATS' | ProcessType.STATS
    }

    def 'will call event sender and deactivate Celltrace subscription and remove error node entries for all nodes regardless of Pm Function'() {
        given:
            def attributes = [type: SubscriptionType.CELLTRACE.name(), name: 'EbsTest', administrationState: AdministrationState.ACTIVE.name(), taskStatus: TaskStatus.OK.name()]
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes('CellTraceSubscription=Test', 'pmic_cell_subscription', '2.1.0', 'CellTraceSubscription', attributes)
            def erbsNodeWithPmFunctionOff = nodeUtil.builder('LTE01ERBS0002').pmEnabled(false).build()
            def erbsNodeWithPmFunctionOff1 = nodeUtil.builder('LTE01ERBS0001').pmEnabled(true).build()
            dpsUtils.addAssociation(subscriptionMo, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNodeWithPmFunctionOff1, erbsNodeWithPmFunctionOff)
            scannerUtil.builder(PREDEF_10001_CELLTRACE_SCANNER, 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).subscriptionId(subscriptionMo.poId).errorCode(-1 as Short).build()
            scannerUtil.builder('PREDEF.10004.CELLTRACE', 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(ProcessType.HIGH_PRIORITY_CELLTRACE).subscriptionId(subscriptionMo.poId).errorCode(-1 as Short).build()
            scannerUtil.builder(PREDEF_10001_CELLTRACE_SCANNER, 'LTE01ERBS0002').attributes(defaultScannerAttributes).processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).subscriptionId(subscriptionMo.poId).errorCode(-1 as Short).build()
            scannerUtil.builder('PREDEF.10004.CELLTRACE', 'LTE01ERBS0002').attributes(defaultScannerAttributes).processType(ProcessType.HIGH_PRIORITY_CELLTRACE).subscriptionId(subscriptionMo.poId).errorCode(-1 as Short).build()
            errorNodeCache.addNodeWithPmFunctionOff(erbsNodeWithPmFunctionOff.fdn, subscriptionMo.poId)
            errorNodeCache.addNodeWithPmFunctionOff(erbsNodeWithPmFunctionOff1.fdn, subscriptionMo.poId)

        when:
            deactivationEvent.execute(subscriptionMo.poId)

        then: 'send deactivation MTRs for both nodes even if PM function is OFF because they have active scanners in DPS'
            2 * eventSender.send(_ as MediationTaskRequest)

        and: 'administration state is deactivating'
            subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == AdministrationState.DEACTIVATING.name()

        and: 'initiation tracker is created for the MTR'
            initiationTrackerCache.getTracker(subscriptionMo.poId as String).subscriptionId == subscriptionMo.poId as String

        and: 'error node cache is modified. All entries for this subscription will be removed'
            (errorNodeCache.getErrorEntry(erbsNodeWithPmFunctionOff.fdn) as Map).isEmpty()
    }

    def 'when deactivating a RES subscription deactivation tasks will be sent for attachedNodes too'() {
        given: 'a RES subscription with 1 node and two attached Nodes plus scanners on dps'
            def rncNodeMo = dps.node().name('RNC01').attributes(['pmFunction': true]).build()
            def rbsNode1Mo = dps.node().name('RBS01').attributes(['pmFunction': true]).build()
            def rbsNode2Mo = dps.node().name('RBS02').attributes(['pmFunction': true]).build()
            def subscriptionAttribute = ['resSpreadingFactor': ['SF_32']]
            def resSubscriptionMo = dps.subscription().type(SubscriptionType.RES).name('Test').nodes(rncNodeMo).attachedNodes([rbsNode1Mo, rbsNode2Mo]).attributes(subscriptionAttribute).administrationState(AdministrationState.ACTIVE).build()
            scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', 'RNC01').attributes(defaultScannerAttributes).processType(ProcessType.STATS).subscriptionId(resSubscriptionMo.poId).build()
            scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', 'RBS01').attributes(defaultScannerAttributes).processType(ProcessType.STATS).subscriptionId(resSubscriptionMo.poId).build()
            scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', 'RBS02').attributes(defaultScannerAttributes).processType(ProcessType.STATS).subscriptionId(resSubscriptionMo.poId).build()

        when: 'the subscription is deactivated'
            deactivationEvent.execute(resSubscriptionMo.poId)

        then:
            3 * eventSender.send(_ as MediationTaskRequest)
    }

    def 'will remove counter conflicts from cache if node is removed from StatisticalSubscription, regardless if pm function on those nodes is on or off'() {
        given: 'deactivating statistical subscription with 4 nodes and 2 counters, only first 3 nodes have scanners for subscription'
            def nodeMo1 = nodeUtil.builder(NODE_NAME_1).neType(NetworkElementType.ERBS).ossModelIdentity('18.Q2-J.1.280').build()
            def nodeMo2 = nodeUtil.builder(NODE_NAME_2).neType(NetworkElementType.ERBS).ossModelIdentity('18.Q2-J.1.280').pmEnabled(false).build()
            def nodeMo3 = nodeUtil.builder(NODE_NAME_3).neType(NetworkElementType.ERBS).ossModelIdentity('18.Q2-J.1.280').build()
            def nodeMo4 = nodeUtil.builder('LTE01ERBS00004').neType(NetworkElementType.ERBS).ossModelIdentity('18.Q2-J.1.280').pmEnabled(false).build()

            def subMo = statisticalSubscriptionBuilder.name('Test')
                    .counters(counters as CounterInfo[])
                    .administrativeState(AdministrationState.DEACTIVATING)
                    .nodes(nodeMo1, nodeMo2)
                    .userType(UserType.USER_DEF)
                    .build()
            def subscription = subscriptionDao.findOneById(subMo.poId, true) as StatisticalSubscription
            scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', NODE_NAME_1).status(ScannerStatus.ACTIVE).processType(ProcessType.STATS)
                    .node(nodeMo1).subscriptionId(subscription.id).build()
            scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', NODE_NAME_2).status(ScannerStatus.ACTIVE).processType(ProcessType.STATS)
                    .node(nodeMo2).subscriptionId(subscription.id).build()
            scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', NODE_NAME_3).status(ScannerStatus.ACTIVE).processType(ProcessType.STATS)
                    .node(nodeMo3).subscriptionId(subscription.id).build()
            //fourth node has no scanner in DPS

        and: 'counter conflict caches have correct values' // node3 and node4 will be removed from subscription in the when: clause
            counterConflictCacheService.addNodesAndCounters([nodeMo1.fdn, nodeMo2.fdn, nodeMo3.fdn, nodeMo4.fdn] as Set,
                    counters, 'Test')

        and: 'for testing purpose a clone subscription will be added in the conflict cache'
            counterConflictCacheService.addNodesAndCounters([nodeMo1.fdn, nodeMo2.fdn, nodeMo3.fdn, nodeMo4.fdn] as Set,
                    counters, 'ActiveSub')

        when: 'two nodes are removed from subscription, one of which has pm function OFF'
            deactivationEvent.execute(nodeDao.findAllByFdn([nodeMo3.fdn, nodeMo4.fdn]), subscription)

        then: 'subscriptionNodesCache will remove both nodes even in one of the nodes didnt generate a MTR'
            def result = counterConflictCacheService.getConflictingCountersInSubscription(subscription)
            result.nodes == [nodeMo1.fdn, nodeMo2.fdn] as Set
            result.counterEventInfo.containsAll(expectedCounterInfos)
    }

    def 'will remove counter conflicts from cache if node is removed from MoInstance subscription, regardless if pm function on those nodes is on or off'() {
        given: 'deactivating statistical subscription with 4 nodes and 2 counters, only first 3 nodes have scanners for subscription'
            def nodeMo1 = nodeUtil.builder(NODE_NAME_1).neType(NetworkElementType.ERBS).ossModelIdentity('18.Q2-J.1.280').build()
            def nodeMo2 = nodeUtil.builder(NODE_NAME_2).neType(NetworkElementType.ERBS).ossModelIdentity('18.Q2-J.1.280').pmEnabled(false).build()
            def nodeMo3 = nodeUtil.builder(NODE_NAME_3).neType(NetworkElementType.ERBS).ossModelIdentity('18.Q2-J.1.280').build()
            def nodeMo4 = nodeUtil.builder('LTE01ERBS00004').neType(NetworkElementType.ERBS).ossModelIdentity('18.Q2-J.1.280').pmEnabled(false).build()

            def subMo = moinstanceSubscriptionBuilder.name('Test').counters(counters as CounterInfo[]).administrativeState(AdministrationState.DEACTIVATING).nodes(nodeMo1, nodeMo2).userType(UserType.USER_DEF).build()
            def subscription = subscriptionDao.findOneById(subMo.poId, true) as MoinstanceSubscription

            scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', NODE_NAME_1).status(ScannerStatus.ACTIVE).processType(ProcessType.STATS)
                    .node(nodeMo1).subscriptionId(subscription.id).build()
            scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', NODE_NAME_2).status(ScannerStatus.ACTIVE).processType(ProcessType.STATS)
                    .node(nodeMo2).subscriptionId(subscription.id).build()
            scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', NODE_NAME_3).status(ScannerStatus.ACTIVE).processType(ProcessType.STATS)
                    .node(nodeMo3).subscriptionId(subscription.id).build()
            //fourth node has no scanner in DPS

        and: 'counter conflict caches have correct values' // node3 and node4 will be removed from subscription in the when: clause
            counterConflictCacheService.addNodesAndCounters([nodeMo1.fdn, nodeMo2.fdn, nodeMo3.fdn, nodeMo4.fdn] as Set,
                    counters, 'Test')

        and: 'for testing purpose a clone subscription will be added in the conflict cache'
            counterConflictCacheService.addNodesAndCounters([nodeMo1.fdn, nodeMo2.fdn, nodeMo3.fdn, nodeMo4.fdn] as Set,
                    counters, 'ActiveSub')

        when: 'two nodes are removed from subscription, one of which has pm function OFF'
            deactivationEvent.execute(nodeDao.findAllByFdn([nodeMo3.fdn, nodeMo4.fdn]), subscription)

        then: 'subscriptionNodesCache will remove both nodes even in one of the nodes didnt generate a MTR'
            def result = counterConflictCacheService.getConflictingCountersInSubscription(subscription)
            result.nodes == [nodeMo1.fdn, nodeMo2.fdn] as Set
            result.counterEventInfo.containsAll(expectedCounterInfos)
    }

    def 'will remove counter conflicts from cache if #StatisticalSubscription subscription is deactivated, regardless if pm function on those nodes is on or off'() {
        given: 'deactivating statistical subscription with 4 nodes and 2 counters, only first 3 nodes have scanners for subscription'
            def nodeMo1 = nodeUtil.builder(NODE_NAME_1).build()
            def nodeMo2 = nodeUtil.builder(NODE_NAME_2).pmEnabled(false).build()
            def nodeMo3 = nodeUtil.builder(NODE_NAME_3).build()
            def nodeMo4 = nodeUtil.builder('LTE01ERBS00004').pmEnabled(false).build()

            def subMo = statisticalSubscriptionBuilder.name('Test').counters(counters as CounterInfo[]).administrativeState(AdministrationState.DEACTIVATING)
                    .nodes(nodeMo1, nodeMo2, nodeMo3, nodeMo4).userType(UserType.USER_DEF).build()
            def subscription = subscriptionDao.findOneById(subMo.poId, true) as StatisticalSubscription

            scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', NODE_NAME_1).status(ScannerStatus.ACTIVE).processType(ProcessType.STATS)
                    .node(nodeMo1).subscriptionId(subscription.id).build()
            scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', NODE_NAME_2).status(ScannerStatus.ACTIVE).processType(ProcessType.STATS)
                    .node(nodeMo2).subscriptionId(subscription.id).build()
            scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', NODE_NAME_3).status(ScannerStatus.ACTIVE).processType(ProcessType.STATS)
                    .node(nodeMo3).subscriptionId(subscription.id).build()
            //fourth node has no scanner in DPS

        and: 'counter conflict caches have correct values'
            counterConflictCacheService.addNodesAndCounters([nodeMo1.fdn, nodeMo2.fdn, nodeMo3.fdn, nodeMo4.fdn] as Set,
                    counters, 'Test')

        when: 'subscription is then deactivated, both conflict caches will be cleaned for that subscription'
            deactivationEvent.execute(subscription.id)

        then: 'subscriptionNodesCache and subscriptionCountersCache will be empty'
            !counterConflictCacheService.areEntriesInCounterCache()
            !counterConflictCacheService.areEntriesInNodesCache()
    }

    def 'will remove counter conflicts from cache if MoInstance subscription is deactivated, regardless if pm function on those nodes is on or off'() {
        given: 'deactivating statistical subscription with 4 nodes and 2 counters, only first 3 nodes have scanners for subscription'
            def nodeMo1 = nodeUtil.builder(NODE_NAME_1).build()
            def nodeMo2 = nodeUtil.builder(NODE_NAME_2).pmEnabled(false).build()
            def nodeMo3 = nodeUtil.builder(NODE_NAME_3).build()
            def nodeMo4 = nodeUtil.builder('LTE01ERBS00004').pmEnabled(false).build()

            def subMo = moinstanceSubscriptionBuilder.name('Test').counters(counters as CounterInfo[]).administrativeState(AdministrationState.DEACTIVATING)
                    .nodes(nodeMo1, nodeMo2, nodeMo3, nodeMo4).userType(UserType.USER_DEF).build()
            def subscription = subscriptionDao.findOneById(subMo.poId, true) as MoinstanceSubscription

            scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', NODE_NAME_1).status(ScannerStatus.ACTIVE).processType(ProcessType.STATS)
                    .node(nodeMo1).subscriptionId(subscription.id).build()
            scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', NODE_NAME_2).status(ScannerStatus.ACTIVE).processType(ProcessType.STATS)
                    .node(nodeMo2).subscriptionId(subscription.id).build()
            scannerUtil.builder('USERDEF-Test.Cont.Y.STATS', NODE_NAME_3).status(ScannerStatus.ACTIVE).processType(ProcessType.STATS)
                    .node(nodeMo3).subscriptionId(subscription.id).build()
            //fourth node has no scanner in DPS
            and: 'counter conflict caches have correct values'
            counterConflictCacheService.addNodesAndCounters([nodeMo1.fdn, nodeMo2.fdn, nodeMo3.fdn, nodeMo4.fdn] as Set,
                    counters, 'Test')

        when: 'subscription is then deactivated, both conflict caches will be cleaned for that subscription'
            deactivationEvent.execute(subscription.id)
        
        then: 'subscriptionNodesCache and subscriptionCountersCache will be empty'
            !counterConflictCacheService.areEntriesInCounterCache()
            !counterConflictCacheService.areEntriesInNodesCache()
    }

    def 'verify that after successful deactivation, the initiation cache will be empty'() {
        given: 'Statistical Subscription exists in DPS with valid nodes'
            nodeMo1 = nodeUtil.builder('LTE01ERBS00001').build()
            subscriptionMo = statisticalSubscriptionBuilder.name('Test').addCounter(new CounterInfo('Name', 'MoClassType')).addNode(nodeMo1)
                    .administrativeState(AdministrationState.DEACTIVATING).taskStatus(TaskStatus.OK).build()
        
        when: 'subscription is deactivated successfully'
            deactivationEvent.execute(subscriptionMo.poId)
        
        then: 'no entries will remain in the initiation cache'
            subscriptionInitiationCacheWrapper.allEntries.empty
    }

    def 'verify that after successful deactivation of nodes added to subscription, the initiation cache will be empty'() {
        given: 'Statistical Subscription exists in DPS with valid nodes'
            nodeMo1 = nodeUtil.builder('LTE01ERBS00001').build()
            nodeMO2 = nodeUtil.builder('LTE01ERBS00002').build()
            nodeMO3 = nodeUtil.builder('LTE01ERBS00003').build()
    
            def subMo = statisticalSubscriptionBuilder.name('Test').counters(counters as CounterInfo[]).administrativeState(AdministrationState.DEACTIVATING)
                    .nodes(nodeMo1).userType(UserType.USER_DEF).build()
            def subscription = subscriptionDao.findOneById(subMo.poId, true) as StatisticalSubscription
        when: '2 nodes are removed to subscription'
            deactivationEvent.execute(nodeDao.findAllByFdn([nodeMO2.fdn, nodeMO3.fdn]), subscription)
        then: 'no entries will remain in the initiation cache'
            subscriptionInitiationCacheWrapper.allEntries.empty
    }

    @Unroll
    def 'subscription entry should be removed from SubscriptionOperationExecutionTrackingCacheWrapper when deactivation is successful'() {
        given:
            def subscriptionMo = dpsUtils.createMoInDPSWithAttributes(fdn, nameSpace, version, subscriptionType, attributes)
            def erbsNode = nodeUtil.builder('LTE01ERBS0001').pmEnabled(true).build()
            def erbsNodeWithPmFunctionOff = nodeUtil.builder('LTE01ERBS0002').pmEnabled(false).build()
            dpsUtils.addAssociation(subscriptionMo, PMIC_ASSOCIATION_SUBSCRIPTION_NODES, erbsNode, erbsNodeWithPmFunctionOff)
            scannerUtil.builder(scannerName, 'LTE01ERBS0001').attributes(defaultScannerAttributes).processType(scannerType).subscriptionId(subscriptionMo.poId).errorCode(-1 as short).build()
            errorNodeCache.addNodeWithPmFunctionOff(erbsNodeWithPmFunctionOff.fdn, subscriptionMo.poId)
            def node1 = nodeDao.findOneById(erbsNode.poId)
    
            def subscription = subscriptionDao.findOneById(subscriptionMo.poId, true)
        
        when:
            deactivationEvent.execute([node1], subscription)
        
        then: 'subscription entry should be removed from SubscriptionOperationExecutionTrackingCacheWrapper'
            subscriptionInitiationCacheWrapper.allEntries.empty
        
        where:
            fdn                                    | nameSpace                           | version | subscriptionType                  | attributes                                                                                                                                                        | scannerName                 | scannerType                           | subClass
            'StatisticalSubscription=Test'         | 'pmic_stat_subscription'            | '2.1.0' | 'StatisticalSubscription'         | [type: SubscriptionType.STATISTICAL.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.USER_DEF.name()]           | 'USERDEF-Test.Cont.Y.STATS' | ProcessType.STATS                     | StatisticalSubscription
            'CellTraceSubscription=Test'           | 'pmic_cell_subscription'            | '2.1.0' | 'CellTraceSubscription'           | [type: SubscriptionType.CELLTRACE.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.USER_DEF.name()]             | 'PREDEF.10002.CELLTRACE'    | ProcessType.NORMAL_PRIORITY_CELLTRACE | CellTraceSubscription
            'ContinuousCellTraceSubscription=Test' | 'pmic_continuous_cell_subscription' | '1.1.0' | 'ContinuousCellTraceSubscription' | [type: SubscriptionType.CONTINUOUSCELLTRACE.name(), name: 'Test', administrationState: AdministrationState.UPDATING.name(), userType: UserType.SYSTEM_DEF.name()] | 'PREDEF.10005.CELLTRACE'    | ProcessType.HIGH_PRIORITY_CELLTRACE   | ContinuousCellTraceSubscription
    }
}
