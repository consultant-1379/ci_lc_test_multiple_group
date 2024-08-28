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

package com.ericsson.oss.services.pm.initiation.notification

import static com.ericsson.oss.pmic.api.constants.ModelConstants.SubscriptionConstants.SUBSCRIPTION_ADMINISTRATIVE_STATE
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_1
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.PROCESS_TYPE_ATTRIBUTE
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_FILE_COLLECTION_ENABLED_ATTRIBUTE
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_MODEL_NAME
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_MODEL_NAME_SPACE
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_MODEL_VERSION
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_ROP_PERIOD_ATTRIBUTE
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_STATUS_ATTRIBUTE
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE

import javax.ejb.TimerConfig
import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.rule.SpyImplementation
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectCreatedEvent
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.CctrSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.CellTraceSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.CellTrafficSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.EbmSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.GpehSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.MoinstanceSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.StatisticalSubscriptionBuilder
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.dto.subscription.enums.UeType
import com.ericsson.oss.services.model.ned.pm.function.FileCollectionState
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper
import com.ericsson.oss.services.pm.collection.notification.DpsScannerCreateNotificationListener
import com.ericsson.oss.services.pm.collection.notification.handlers.FileCollectionStateUpdateHandler
import com.ericsson.oss.services.pm.generic.NodeServiceImpl
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.scheduling.cluster.ClusterMembershipListener

import spock.lang.Unroll

class DpsScannerCreateNotificationListenerSpec extends SkeletonSpec {

    @ObjectUnderTest
    DpsScannerCreateNotificationListener dpsScannerCreateNotificationListener

    @ImplementationInstance
    private TimerService timerService = Mock(TimerService, {
        getTimers() >> []
    })

    @Inject
    private PMICInitiationTrackerCache pmicInitiationTrackerCache

    @Inject
    private final FileCollectionActiveTaskCacheWrapper fileCollectionActiveTasksCache

    @SpyImplementation
    private NodeServiceImpl nodeService

    @MockedImplementation
    private FileCollectionStateUpdateHandler fileCollectionStateUpdateHandler

    @ImplementationInstance
    ClusterMembershipListener membershipListener = Mock(ClusterMembershipListener, {
        isMaster() >> true;
    })

    ManagedObject subscriptionMO, nodeMO, scannerMO, pmJobMO

    def "create statistical subscription in dps with one node and active scanner"() {
        nodeMO = nodeUtil.builder(NODE_NAME_1).build()
        subscriptionMO = statisticalSubscriptionBuilder.addCounter("CounterName", "counterGroup").name("Stats").addNode(nodeMO).taskStatus(TaskStatus.OK).administrativeState(AdministrationState.ACTIVE).build()
        scannerMO = scannerUtil.builder("USERDEF.Stats.Cont.Y.STATS", NODE_NAME_1).processType(ProcessType.STATS).node(nodeMO).subscriptionId(subscriptionMO).status(ScannerStatus.ACTIVE).build()
    }

    def DpsObjectCreatedEvent getObjectCreatedEvent(ManagedObject scanner) {
        def attributes = [(SCANNER_ROP_PERIOD_ATTRIBUTE)             : scanner.getAttribute(SCANNER_ROP_PERIOD_ATTRIBUTE),
                          (SCANNER_STATUS_ATTRIBUTE)                 : scanner.getAttribute(SCANNER_STATUS_ATTRIBUTE),
                          (SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE)     : scanner.getAttribute(SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE),
                          (SCANNER_FILE_COLLECTION_ENABLED_ATTRIBUTE): scanner.getAttribute(SCANNER_FILE_COLLECTION_ENABLED_ATTRIBUTE),
                          (PROCESS_TYPE_ATTRIBUTE)                   : scanner.getAttribute(PROCESS_TYPE_ATTRIBUTE)]
        return new DpsObjectCreatedEvent(SCANNER_MODEL_NAME_SPACE, SCANNER_MODEL_NAME, SCANNER_MODEL_VERSION, scanner.getPoId(), scanner.getFdn(), null, false, attributes);
    }

    @Unroll
    def "do not do anything if continuous celltrace scanner with status #scannerState and file collection #fileCollection is created"() {
        given: "valid active cctr subscription and scanner on one node"
        nodeMO = nodeUtil.builder(NODE_NAME_1).build()
        subscriptionMO = cctrSubscriptionBuilder.administrativeState(AdministrationState.ACTIVE).taskStatus(TaskStatus.OK).addNode(nodeMO).build()
        scannerMO = scannerUtil.builder("PREDEF.10005.CELLTRACE", NODE_NAME_1).status(scannerState).fileCollectionEnabled(fileCollection).build()

        when:
        dpsScannerCreateNotificationListener.onEvent(getObjectCreatedEvent(scannerMO))
        then: "no file collection, task status, admin state changes"
        fileCollectionActiveTasksCache.size() == 0
        subscriptionMO.getAttribute(SUBSCRIPTION_ADMINISTRATIVE_STATE) == AdministrationState.ACTIVE.name()
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == TaskStatus.OK.name()
        where:
        scannerState           | fileCollection
        ScannerStatus.ACTIVE   | true
        ScannerStatus.ERROR    | true
        ScannerStatus.UNKNOWN  | true
        ScannerStatus.INACTIVE | true
        ScannerStatus.ACTIVE   | false
        ScannerStatus.ERROR    | false
        ScannerStatus.UNKNOWN  | false
        ScannerStatus.INACTIVE | false
    }

    @Unroll
    def "when #processType #scannerState scanner is created with file collection enabled: #fileCollection, file collection should start only if scanner is active and has collection enabled"() {
        given: "scanner exist on valid node. No subscription is needed"
        nodeMO = nodeUtil.builder("ADBC").build()
        scannerMO = scannerUtil.builder(scannerProcessToNameMap.get(processType), "ADBC").node(nodeMO).processType(processType as ProcessType).status(scannerState as ScannerStatus).fileCollectionEnabled(fileCollection as boolean).build()
        int times = scannerState == ScannerStatus.ACTIVE && fileCollection ? 1 : 0
        when:
        dpsScannerCreateNotificationListener.onEvent(getObjectCreatedEvent(scannerMO))
        then:
        if (scannerState == ScannerStatus.ACTIVE) {
            fileCollectionActiveTasksCache.getProcessRequests().collect {
                it.nodeAddress
            } as Collection == [nodeMO.getFdn()]
        }
        fileCollectionActiveTasksCache.size() == times
        times * timerService.createIntervalTimer(_ as Long, _ as Long, _ as TimerConfig)

        where:
        [processType, scannerState, fileCollection] << [
                [ProcessType.STATS, ProcessType.NORMAL_PRIORITY_CELLTRACE, ProcessType.HIGH_PRIORITY_CELLTRACE, ProcessType.EVENTJOB, ProcessType.CTR],
                [ScannerStatus.ACTIVE, ScannerStatus.ERROR, ScannerStatus.UNKNOWN, ScannerStatus.INACTIVE],
                [true, false]].combinations()
    }

    def 'testing scanner create notification where scanner state is ACTIVE and subscription ID is 0'() {
        given: "erbs node with active scanner and active stats subscription"
        'create statistical subscription in dps with one node and active scanner'()
        ManagedObject scannerMoWithInvalidSubscription = scannerUtil.builder("USERDEF.unknown.Cont.Y.STATS", NODE_NAME_1).processType(ProcessType.STATS).
                node(nodeMO).subscriptionId("0").status(ScannerStatus.ACTIVE).build()
        final DpsObjectCreatedEvent dpsObjectCreatedEvent = getObjectCreatedEvent(scannerMoWithInvalidSubscription)

        when: 'A DpsObjectCreatedEvent is received by DPS Scanner Create Notification Listener, where subscription is not valid'
        dpsScannerCreateNotificationListener.onEvent(dpsObjectCreatedEvent)

        then: 'it should start file collection for this scanner'
        (fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) + 1)
        and: 'it should start timer for file collection'
        1 * timerService.createIntervalTimer(_ as Long, _ as Long, _ as TimerConfig)
        and: 'check that correct node is used in file collection cache'
        fileCollectionActiveTasksCache.getProcessRequests().collect {
            it.nodeAddress
        } as Collection == [nodeMO.getFdn()]
    }

    @Unroll
    def "when scanner create notification is received where scanner status is #scannerStatus with #subscriptionName, it should update task Status to #taskStatus"() {
        given:
        nodeMO = nodeUtil.builder("LTE01ERBS0001").build()
        subscriptionMO = subscriptionBuilderClass.newInstance(dpsUtils).name(subscriptionName).administrativeState(AdministrationState.ACTIVATING)
                .taskStatus(TaskStatus.NA).addNode(nodeMO).build()
        scannerMO = scannerUtil.builder("scanner name not important", nodeMO.getName()).processType(subscriptionMO).node(nodeMO)
                .subscriptionId(subscriptionMO).status(scannerStatus).build()
        and:
        pmicInitiationTrackerCache.startTrackingActivation(subscriptionMO.poId as String, subscriptionMO.getAttribute("administrationState") as String, [(nodeMO.getFdn()): nodeMO.getAttribute("neType") as String])
        when: 'A DpsObjectCreatedEvent is received by DPS Scanner Create Notification Listener'
        dpsScannerCreateNotificationListener.onEvent(getObjectCreatedEvent(scannerMO))
        then:
        subscriptionMO.getAttribute(SUBSCRIPTION_ADMINISTRATIVE_STATE) == AdministrationState.ACTIVE.name()
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == taskStatus
        where:
        scannerStatus | subscriptionName                  | subscriptionBuilderClass             || taskStatus
        'ACTIVE'      | 'StatsSubscription'               | StatisticalSubscriptionBuilder.class || 'OK'
        'ERROR'       | 'StatsSubscription'               | StatisticalSubscriptionBuilder.class || 'ERROR'
        'ACTIVE'      | 'CelltraceSubscription'           | CellTraceSubscriptionBuilder.class   || 'OK'
        'ERROR'       | 'CelltraceSubscription'           | CellTraceSubscriptionBuilder.class   || 'ERROR'
        'ACTIVE'      | 'EbmSubscription'                 | EbmSubscriptionBuilder.class         || 'OK'
        'ERROR'       | 'EbmSubscription'                 | EbmSubscriptionBuilder.class         || 'ERROR'
        'ACTIVE'      | 'ContinuousCellTraceSubscription' | CctrSubscriptionBuilder.class        || 'OK'
        'ERROR'       | 'ContinuousCellTraceSubscription' | CctrSubscriptionBuilder.class        || 'ERROR'
        'ACTIVE'      | 'MoinstanceSubscription'          | MoinstanceSubscriptionBuilder.class  || 'OK'
        'ERROR'       | 'MoinstanceSubscription'          | MoinstanceSubscriptionBuilder.class  || 'ERROR'
        'ACTIVE'      | 'CellTrafficSubscription'         | CellTrafficSubscriptionBuilder.class || 'OK'
        'ERROR'       | 'CellTrafficSubscription'         | CellTrafficSubscriptionBuilder.class || 'ERROR'
        'ACTIVE'      | 'GpehSubscription'                | GpehSubscriptionBuilder.class        || 'OK'
        'ERROR'       | 'GpehSubscription'                | GpehSubscriptionBuilder.class        || 'ERROR'
    }

    @Unroll
    def "when scanner create notification is received where scanner status is #helper.scannerStatus with a valid UETR subscription, it should update task Status to #helper.taskStatus"() {
        given:
        nodeMO = nodeUtil.builder("LTE01ERBS0001").build()
        subscriptionMO = uetrSubscriptionBuilder.ueInfoList([new UeInfo(UeType.IMSI, "01234567")]).name("test").administrativeState(AdministrationState.ACTIVATING)
                .taskStatus(TaskStatus.NA).addNode(nodeMO).build()
        scannerMO = scannerUtil.builder("scanner name not important", nodeMO.getName()).processType(subscriptionMO).node(nodeMO)
                .subscriptionId(subscriptionMO).status(helper.scannerStatus as ScannerStatus).build()
        and:
        pmicInitiationTrackerCache.startTrackingActivation(subscriptionMO.poId as String, subscriptionMO.getAttribute("administrationState") as String, [(nodeMO.getFdn()): nodeMO.getAttribute("neType") as String])
        when: 'A DpsObjectCreatedEvent is received by DPS Scanner Create Notification Listener'
        dpsScannerCreateNotificationListener.onEvent(getObjectCreatedEvent(scannerMO))
        then:
        subscriptionMO.getAttribute(SUBSCRIPTION_ADMINISTRATIVE_STATE) == AdministrationState.ACTIVE.name()
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == (helper.taskStatus as TaskStatus).name()
        where:
        [helper] << [
                [new Helper(ScannerStatus.ACTIVE, TaskStatus.OK), new Helper(ScannerStatus.ERROR, TaskStatus.ERROR)]
        ]
    }

    @Unroll
    def "when scanner create notification is received where scanner status is #helper.scannerStatus with a valid Uetr subscription, it should update task Status to #helper.taskStatus"() {
        given:
        nodeMO = dps.node().name("LTE01ERBS0001").build()
        subscriptionMO = dps.subscription().
                type(SubscriptionType.UETR).
                name("test").
                administrationState(AdministrationState.ACTIVATING).
                taskStatus(TaskStatus.NA).
                nodes(nodeMO).
                ueInfoList(new UeInfo(UeType.IMEI, "2142565432")).
                build()
        scannerMO = dps.scanner().
                nodeName(nodeMO).
                name("scanner name not important").
                processType(subscriptionMO).
                subscriptionId(subscriptionMO).
                status(helper.scannerStatus as ScannerStatus).
                build()
        and:
        pmicInitiationTrackerCache.startTrackingActivation(subscriptionMO.poId as String,
                subscriptionMO.getAttribute("administrationState") as String,
                [(nodeMO.getFdn()): nodeMO.getAttribute("neType") as String])
        when: 'A DpsObjectCreatedEvent is received by DPS Scanner Create Notification Listener'
        dpsScannerCreateNotificationListener.onEvent(getObjectCreatedEvent(scannerMO))
        then:
        subscriptionMO.getAttribute(SUBSCRIPTION_ADMINISTRATIVE_STATE) == AdministrationState.ACTIVE.name()
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == (helper.taskStatus as TaskStatus).name()
        where:
        helper << [new Helper(ScannerStatus.ACTIVE, TaskStatus.OK), new Helper(ScannerStatus.ERROR, TaskStatus.ERROR)]
    }

    @Unroll
    def 'File collection scheduling upon scanner creation for mediation autonomy node given #description'() {
        given: 'a valid mediation autonomy node in ENM'
            nodeMO = dps.node().name('dummyNodeName').build()
            nodeService.isMediationAutonomyEnabled(nodeMO.getFdn()) >> true
        and: 'a scanner for the node'
            scannerMO = dps.scanner().
                    nodeName(nodeMO).
                    name(isPredefStatsScanner ? 'PREDEF.Dummy.STATS' : 'Dummy_NotPredefStatsScanner').
                    processType(ProcessType.STATS).
                    status(scannerStatus).build()
        and: 'PM function enabled is #pmEnabled'
            nodeService.isPmFunctionEnabled(nodeMO.getFdn()) >> pmEnabled

        when: 'a scanner creation event is received'
            dpsScannerCreateNotificationListener.onEvent(getObjectCreatedEvent(scannerMO))

        then: 'the file collection is scheduled: #expectFileCollectionScheduling'
            if (expectFileCollectionScheduling) {
                1 * fileCollectionStateUpdateHandler.updateFileCollectionScheduleForNodeWithMediationAutonomy(nodeMO.getFdn(), FileCollectionState.ENABLED)
            } else {
                0 * fileCollectionStateUpdateHandler.updateFileCollectionScheduleForNodeWithMediationAutonomy(nodeMO.getFdn(), FileCollectionState.ENABLED)
            }

        where:
            description                                                         | scannerStatus          | isPredefStatsScanner | pmEnabled || expectFileCollectionScheduling
            'active Predefined Stats scanner should schedule file collection'   | ScannerStatus.ACTIVE   | true                 | true      || true
            'any other scanner ; no file collection scheduling'                 | ScannerStatus.ACTIVE   | false                | true      || false
            'inactive predefined stats scanner ; no file collection scheduling' | ScannerStatus.INACTIVE | true                 | true      || false
            'pm function disabled ; no file collection scheduling'              | ScannerStatus.ACTIVE   | true                 | false     || false
    }

    def 'File collection scheduling event is not sent upon scanner creation for non mediation autonomy node'() {
        given: 'a valid non mediation autonomy node in ENM'
            nodeMO = dps.node().name('dummyNodeName').build()
            nodeService.isMediationAutonomyEnabled(nodeMO.getFdn()) >> false
        and: 'an ACTIVE predef stats scanner for the node'
            scannerMO = dps.scanner().
                    nodeName(nodeMO).
                    name('PREDEF.Dummy.STATS').
                    processType(ProcessType.STATS).
                    status(ScannerStatus.ACTIVE).build()
        and: 'PM function is enabled'
            nodeService.isPmFunctionEnabled(nodeMO.getFdn()) >> true

        when: 'a scanner creation event is received'
            dpsScannerCreateNotificationListener.onEvent(getObjectCreatedEvent(scannerMO))

        then: 'the file collection is not scheduled'
            0 * fileCollectionStateUpdateHandler.updateFileCollectionScheduleForNodeWithMediationAutonomy(nodeMO.getFdn(), FileCollectionState.ENABLED)
    }

    private class Helper {
        public ScannerStatus scannerStatus
        public TaskStatus taskStatus

        Helper(final ScannerStatus scannerStatus, final TaskStatus taskStatus) {
            this.scannerStatus = scannerStatus
            this.taskStatus = taskStatus
        }
    }


}
