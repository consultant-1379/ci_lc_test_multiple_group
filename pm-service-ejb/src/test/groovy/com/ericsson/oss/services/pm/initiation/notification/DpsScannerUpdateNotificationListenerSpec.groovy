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

import static com.ericsson.oss.pmic.cdi.test.util.Constants.ACTIVE
import static com.ericsson.oss.pmic.cdi.test.util.Constants.EBS_CELLTRACE_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.OK
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE

import org.mockito.Mockito

import spock.lang.Shared
import spock.lang.Unroll

import javax.ejb.Timer
import javax.ejb.TimerConfig
import javax.ejb.TimerService
import javax.inject.Inject
import java.util.concurrent.TimeUnit

import com.ericsson.cds.cdi.support.configuration.InjectionProperties
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.*
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.scanner.Scanner
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo
import com.ericsson.oss.pmic.dto.subscription.enums.*
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.generic.PmSubScannerService
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper
import com.ericsson.oss.services.pm.collection.cache.FileCollectionScheduledRecoveryCacheWrapper
import com.ericsson.oss.services.pm.collection.notification.DpsScannerUpdateNotificationListener
import com.ericsson.oss.services.pm.collection.notification.handlers.FileCollectionOperationHelper
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener
import com.ericsson.oss.services.pm.scheduling.impl.DelayedTaskStatusValidator

class DpsScannerUpdateNotificationListenerSpec extends PmServiceEjbSkeletonSpec {


    @ObjectUnderTest
    DpsScannerUpdateNotificationListener dpsScannerUpdateNotificationListener

    @Override
    def addAdditionalInjectionProperties(InjectionProperties injectionProperties) {
        super.addAdditionalInjectionProperties(injectionProperties)
        injectionProperties.autoLocateFrom('com.ericsson.oss.services.pm')
    }

    @ImplementationInstance
    TimerService timerService = Mock(TimerService)
    Timer timer = Mock(Timer)
    @Inject
    private PMICInitiationTrackerCache pmicInitiationTrackerCache
    @Inject
    private FileCollectionOperationHelper fileCollectionOperationHelper
    @Inject
    private DelayedTaskStatusValidator delayedTaskStatusValidator
    @Inject
    private FileCollectionActiveTaskCacheWrapper fileCollectionActiveTasksCache
    @ImplementationInstance
    ConfigurationChangeListener configurationChangeListener = Mock()
    @ImplementationInstance
    TimeGenerator timer1 = Mockito.mock(TimeGenerator)
    @Inject
    private FileCollectionScheduledRecoveryCacheWrapper fileCollectionScheduledRecoveryCache
    @Inject
    PmSubScannerService pmSubScannerService

    def LTE01ERBS00001 = 'LTE01ERBS00001'
    def LTE01ERBS00002 = 'LTE01ERBS00002'
    def NR01gNodeBRadio00001 = 'NR01gNodeBRadio00001'
    def USERDEF_TEST_CONT_Y_STATS = 'USERDEF.TEST.Cont.Y.STATS'
    def USERDEF_TEST2_CONT_Y_STATS = 'USERDEF.TEST2.Cont.Y.STATS'
    def PREDEFSTATS = 'PREDEF.STATS'
    def PREDEF_10003_CELLTRACE = 'PREDEF.10003.CELLTRACE'
    def PREDEF_10004_CELLTRACE = 'PREDEF.10004.CELLTRACE'


    @Shared
    private ManagedObject subscriptionFileMO, subscriptionStreamMO, scannerFileMO, scannerStreamMO, erbsNodeMO, gNodeBMO, subMo, nodeMo, scannerMo

    def setup() {
        configurationChangeListener.getPmMigrationEnabled() >> false
        erbsNodeMO = nodeUtil.builder(LTE01ERBS00001).neType(NetworkElementType.ERBS).build()
        gNodeBMO = nodeUtil.builder(NR01gNodeBRadio00001).neType(NetworkElementType.RADIONODE).build()
        timerService.getTimers() >> [timer]
    }

    def 'Add Process Request to fileCollectionActiveTasksCache if File Collection is Enabled'() {
        given: 'Active statistical subscription with one node and one scanner'
        subscriptionFileMO = statisticalSubscriptionBuilder.administrativeState(AdministrationState.ACTIVATING).taskStatus(TaskStatus.OK)
                .addNode(erbsNodeMO).addCounter('CounterName', 'counterGroup').build()
        scannerFileMO = scannerUtil.builder(USERDEF_TEST_CONT_Y_STATS, LTE01ERBS00001).ropPeriod(900).status(ScannerStatus.ACTIVE)
                .processType(ProcessType.STATS).node(erbsNodeMO).subscriptionId(subscriptionFileMO).build()
        timer.info >> []

        when: 'Received the Attribute Changed Event Listener'
        dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', 'INACTIVE', 'ACTIVE', scannerFileMO))

        then: 'Start File Collection --> Process request should be added in fileCollectionActiveTasksCache'
        scannerFileMO.getAttribute('fileCollectionEnabled') == true
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) + 1

        and: 'Create Timer when File collection is started'
        1 * timerService.createIntervalTimer(_ as Long, _ as Long, _ as TimerConfig)
    }

    @Unroll
    def 'Process Request for file collection in fileCollectionActiveTasksCache for Events subscription with #outputModeType, #fileCollectionActiveTasksCacheSize, #timerCallCount'() {
        given: 'A cell trace subscription with output mode type as #outputMode'
        subscriptionFileMO = cellTraceSubscriptionBuilder.outputMode(outputModeType)
                .administrativeState(AdministrationState.ACTIVE).addEvent('group', 'name').taskStatus(TaskStatus.OK).addNode(erbsNodeMO).build()
        scannerFileMO = scannerUtil.builder(PREDEF_10003_CELLTRACE, LTE01ERBS00001).
                subscriptionId(subscriptionFileMO).
                processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
                status(ScannerStatus.ACTIVE).
                ropPeriod(900).
                build()

        and: 'Scanner status changed from INACTIVE to ACTIVE'
        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChangeEvent('status', 'INACTIVE', 'ACTIVE', scannerFileMO)

        and: 'No timer exists'
        timer.info >> []

        when: 'Received the Attribute Changed Event Listener'
        dpsScannerUpdateNotificationListener.onEvent(attributeChangedEvent)

        then: 'Process request in fileCollectionActiveTasksCache'
        fileCollectionActiveTasksCache.size() == fileCollectionActiveTasksCacheSize
        and: 'Timer status'
        timerCallCount * timerService.createIntervalTimer(_ as Long, _ as Long, _ as TimerConfig)

        where: 'output Mode Type is #outputMode and process request added to cache is #fileCollectionActiveTasksCacheSize'
        outputModeType                           | fileCollectionActiveTasksCacheSize | timerCallCount
        OutputModeType.FILE.name()               | 1                                  | 1
        OutputModeType.FILE_AND_STREAMING.name() | 1                                  | 1
        OutputModeType.STREAMING.name()          | 0                                  | 0

    }

    def 'Add Process Request to fileCollectionActiveTasksCache if File Collection is Enabled for PREDEF even if not associated with a subscription'() {
        given:
        scannerFileMO = scannerUtil.builder(PREDEFSTATS, LTE01ERBS00001).
                ropPeriod(900).
                status(ScannerStatus.ACTIVE).
                processType(ProcessType.STATS).
                node(erbsNodeMO).
                build()
        timer.info >> []

        when: 'Received the Attribute Changed Event Listener'
        dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', 'INACTIVE', 'ACTIVE', scannerFileMO))

        then: 'Start File Collection --> Process request should be added in fileCollectionActiveTasksCache'
        scannerFileMO.getAttribute('fileCollectionEnabled') == true
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) + 1

        and: 'Create Timer when File collection is started'
        1 * timerService.createIntervalTimer(_ as Long, _ as Long, _ as TimerConfig)
    }

    def 'Add Process Request to fileCollectionActiveTasksCache if File Collection is Enabled for USERDEF even if not associated with a subscription'() {
        given:
        scannerFileMO = scannerUtil.builder(USERDEF_TEST2_CONT_Y_STATS, LTE01ERBS00001).
                status(ScannerStatus.ACTIVE).
                processType(ProcessType.STATS).
                node(erbsNodeMO).
                build()
        timer.info >> []

        when: 'Received the Attribute Changed Event Listener'
        dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', 'INACTIVE', 'ACTIVE', scannerFileMO))

        then: 'Start File Collection --> Process request should be added in fileCollectionActiveTasksCache'
        scannerFileMO.getAttribute('fileCollectionEnabled') == true
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) + 1

        and: 'Create Timer when File collection is started'
        1 * timerService.createIntervalTimer(_ as Long, _ as Long, _ as TimerConfig)
    }

    def 'Don\'t add Process Request to fileCollectionActiveTasksCache if only subscription ID is updated to valid'() {
        given:
        subscriptionFileMO = cellTraceSubscriptionBuilder.administrativeState(AdministrationState.ACTIVATING).
                taskStatus(TaskStatus.OK)
                .addNode(erbsNodeMO)
                .addEvent('group', 'name').
                build()
        scannerFileMO = scannerUtil.builder(PREDEF_10003_CELLTRACE, LTE01ERBS00001).
                status(ScannerStatus.INACTIVE)
                .subscriptionId(subscriptionFileMO)
                .processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
                build()
        timer.info >> []

        when: 'Received the Attribute Changed Event Listener'
        dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('subscriptionId', '0', Long.toString(subscriptionFileMO.poId), scannerFileMO))

        then: 'Start File Collection --> Process request should not be added in fileCollectionActiveTasksCache'
        scannerFileMO.getAttribute('fileCollectionEnabled') == true
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size())

        and: 'Timer should not be created'
        0 * timerService.createIntervalTimer(_ as Long, _ as Long, _ as TimerConfig)
    }

    def 'Don\'t add Process Request to fileCollectionActiveTasksCache if only subscription ID is updated to invalid'() {
        subscriptionFileMO = cellTraceSubscriptionBuilder.administrativeState(AdministrationState.ACTIVATING).
                taskStatus(TaskStatus.OK)
                .addNode(erbsNodeMO)
                .addEvent('group', 'name').
                build()
        scannerFileMO = scannerUtil.builder(PREDEF_10003_CELLTRACE, LTE01ERBS00001).status(ScannerStatus.INACTIVE).subscriptionId(subscriptionFileMO)
                .processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).build()
        timer.info >> []

        when: 'Received the Attribute Changed Event Listener'
        dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('subscriptionId', Long.toString(subscriptionFileMO.poId), '0', scannerFileMO))

        then: 'Start File Collection --> Process request should not be added in fileCollectionActiveTasksCache'
        scannerFileMO.getAttribute('fileCollectionEnabled') == true
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size())

        and: 'Timer should not be created'
        0 * timerService.createIntervalTimer(_ as Long, _ as Long, _ as TimerConfig)

    }

    def 'Stop File Collection if there are no more Scanners Active on the Node'() {
        given: 'Deactivating stats subscription with valid node and inactive scanner'
        subscriptionFileMO = statisticalSubscriptionBuilder.administrativeState(AdministrationState.DEACTIVATING).taskStatus(TaskStatus.OK)
                .addNode(erbsNodeMO).addCounter('CounterName', 'counterGroup').build()
        scannerFileMO = scannerUtil.builder(USERDEF_TEST_CONT_Y_STATS, LTE01ERBS00001).ropPeriod(900).status(ScannerStatus.INACTIVE)
                .processType(ProcessType.STATS).node(erbsNodeMO).subscriptionId(subscriptionFileMO).build()
        timer.info >> 900

        and: 'Add Process Request to fileCollectionActiveTasksCache'
       def processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(erbsNodeMO.getFdn(), 900, ProcessType.STATS.name())
                .build()
        fileCollectionActiveTasksCache.addProcessRequest(processRequestVO)

        when: 'Received the Dps Attribute Changed Event Listener'
        dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', 'ACTIVE', 'INACTIVE', scannerFileMO))

        then: 'Stop File Collection --> File Collection task request should be removed from the fileCollectionActiveTasksCache'
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) - 1

        and: 'ProcessRequestVO added in the FileCollectionScheduledRecoveryCache'
        fileCollectionScheduledRecoveryCache.size() == old(fileCollectionScheduledRecoveryCache.size()) + 1

        and: 'Cancel Timer when File Collection is stopped'
        1 * timer.cancel()

    }

    def 'Don\'t Stop File Collection if there are any Scanners Active on the Node'() {
        given: 'Active statistical subscription with one node and one scanner'
            timer.info >> 900
            subscriptionFileMO = statisticalSubscriptionBuilder.addNode(erbsNodeMO).build()
            scannerFileMO = scannerUtil.builder(USERDEF_TEST_CONT_Y_STATS, LTE01ERBS00001).
                    status(ScannerStatus.ACTIVE).
                    processType(ProcessType.STATS).
                    node(erbsNodeMO).
                    subscriptionId(subscriptionFileMO).
                    build()

        and: 'predef stats scanner is activated on the node'
            def predefStatsScannerMO = scannerUtil.builder(PREDEFSTATS, LTE01ERBS00001).
                    status(ScannerStatus.ACTIVE).
                    processType(ProcessType.STATS).
                    node(erbsNodeMO).
                    build()
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', 'INACTIVE', 'ACTIVE', predefStatsScannerMO))

        and: 'user def stats scanner is activated'
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', 'INACTIVE', 'ACTIVE', scannerFileMO))

        and: 'Add Process Request to fileCollectionActiveTasksCache'
            def processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(erbsNodeMO.getFdn(), 900, ProcessType.STATS.name()).build()
            fileCollectionActiveTasksCache.addProcessRequest(processRequestVO)

        when: 'predef stats is then deactivated from the node'
            predefStatsScannerMO.setAttribute('status', 'INACTIVE')
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', 'ACTIVE', 'INACTIVE', predefStatsScannerMO))

        then: 'The ProcessRequestVO is never removed from fileCollectionActiveTasksCache'
            0 * fileCollectionActiveTasksCache.removeProcessRequest(fileCollectionActiveTasksCache.getKeyFor(processRequestVO))

    }

    def 'Stop File Collection if there are only Stream Scanners Active with fileCollectionEnable true on the Node'() {
        given: 'Deactivating celltrace subscription with valid node and inactive scanner'
            subscriptionFileMO = cellTraceSubscriptionBuilder.outputMode('FILE').
                    administrativeState(AdministrationState.DEACTIVATING).
                    taskStatus(TaskStatus.OK).
                    addNode(erbsNodeMO).
                    addEvent('group', 'event').
                    build()
            scannerFileMO = scannerUtil.builder(PREDEF_10003_CELLTRACE, LTE01ERBS00001).
                    status(ScannerStatus.INACTIVE).
                    processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
                    subscriptionId(subscriptionFileMO).
                    fileCollectionEnabled(true).
                    build()
            subscriptionStreamMO = cellTraceSubscriptionBuilder.outputMode('STREAMING').
                    name('streamsub').
                    administrativeState(AdministrationState.ACTIVE).
                    taskStatus(TaskStatus.OK).
                    addNode(erbsNodeMO).
                    addEvent('group', 'event').
                    build()
            scannerStreamMO = scannerUtil.builder(PREDEF_10004_CELLTRACE, LTE01ERBS00001).
                    status(ScannerStatus.ACTIVE).
                    processType(ProcessType.HIGH_PRIORITY_CELLTRACE).
                    subscriptionId(subscriptionStreamMO).
                    fileCollectionEnabled(true).
                    ropPeriod(0).
                    build()
            timer.info >> 900

        and: 'Add Process Request to fileCollectionActiveTasksCache'
            def processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(erbsNodeMO.getFdn(), 900, ProcessType.NORMAL_PRIORITY_CELLTRACE.
                    name()).build()
            fileCollectionActiveTasksCache.addProcessRequest(processRequestVO)

        when: 'Received the Dps Attribute Changed Event Listener'
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', 'ACTIVE', 'INACTIVE', scannerFileMO))

        then: 'Stop File Collection --> File Collection task request should be removed from the fileCollectionActiveTasksCache'
            fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) - 1

        and: 'ProcessRequestVO added in the FileCollectionScheduledRecoveryCache'
            fileCollectionScheduledRecoveryCache.size() == old(fileCollectionScheduledRecoveryCache.size()) + 1

        and: 'Cancel Timer when File Collection is stopped'
            1 * timer.cancel()

    }

    def 'On streaming scanner INACTIVATE, should not call stop File Collection'() {
        given: 'Deactivating celltrace subscription with valid node and inactive scanner'
            subscriptionFileMO = cellTraceSubscriptionBuilder.outputMode('STREAMING').
                    administrativeState(AdministrationState.INACTIVE).
                    taskStatus(TaskStatus.OK).
                    addNode(erbsNodeMO).
                    addEvent('group', 'event').
                    build()
            scannerFileMO = scannerUtil.builder(PREDEF_10003_CELLTRACE, LTE01ERBS00001).
                    status(ScannerStatus.INACTIVE).
                    processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
                    subscriptionId(subscriptionFileMO).
                    fileCollectionEnabled(false).
                    build()

        when: 'Received the Dps Attribute Changed Event Listener'
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', 'ACTIVE', 'INACTIVE', scannerFileMO))

        then: 'should not call Stop File Collection'
            0 * fileCollectionOperationHelper.stopFileCollection()

    }

    def 'On streaming scanner ACTIVATE, should not call start File Collection'() {
        given: 'Activating celltrace subscription with valid node and active scanner'
            subscriptionFileMO = cellTraceSubscriptionBuilder.outputMode('STREAMING').
                    administrativeState(AdministrationState.ACTIVE).
                    taskStatus(TaskStatus.OK).
                    addNode(erbsNodeMO).
                    addEvent('group', 'event').
                    build()
            scannerFileMO = scannerUtil.builder(PREDEF_10003_CELLTRACE, LTE01ERBS00001).
                    status(ScannerStatus.INACTIVE).
                    processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
                    subscriptionId(subscriptionFileMO).
                    fileCollectionEnabled(false).
                    build()

        when: 'Received the Dps Attribute Changed Event Listener'
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', 'ACTIVE', 'INACTIVE', scannerFileMO))

        then: 'should not call start File Collection'
            0 * fileCollectionOperationHelper.stopFileCollection()

    }

    def 'Stop File Collection if there are no more Scanners Active with fileCollectionEnable true on the Node'() {
        given: 'Deactivating celltrace subscription with valid node and inactive scanner'
            subscriptionFileMO = cellTraceSubscriptionBuilder.outputMode('FILE').
                    administrativeState(AdministrationState.DEACTIVATING).
                    taskStatus(TaskStatus.OK).
                    addNode(erbsNodeMO).
                    addEvent('group', 'event').
                    build()
            scannerFileMO = scannerUtil.builder(PREDEF_10003_CELLTRACE, LTE01ERBS00001).
                    status(ScannerStatus.INACTIVE).
                    processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
                    subscriptionId(subscriptionFileMO).
                    fileCollectionEnabled(true).
                    build()
            subscriptionStreamMO = cellTraceSubscriptionBuilder.outputMode('STREAMING').
                    name('streamsub').
                    administrativeState(AdministrationState.ACTIVE).
                    taskStatus(TaskStatus.OK).
                    addNode(erbsNodeMO).
                    addEvent('group', 'event').
                    build()
            scannerStreamMO = scannerUtil.builder(PREDEF_10004_CELLTRACE, LTE01ERBS00001).
                    status(ScannerStatus.ACTIVE).
                    processType(ProcessType.HIGH_PRIORITY_CELLTRACE).
                    subscriptionId(subscriptionStreamMO).
                    fileCollectionEnabled(false).
                    build()
            timer.info >> 900

        and: 'Add Process Request to fileCollectionActiveTasksCache'
            def processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(erbsNodeMO.getFdn(), 900, ProcessType.NORMAL_PRIORITY_CELLTRACE.
                    name()).build()
            fileCollectionActiveTasksCache.addProcessRequest(processRequestVO)

        when: 'Received the Dps Attribute Changed Event Listener'
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', 'ACTIVE', 'INACTIVE', scannerFileMO))

        then: 'Stop File Collection --> File Collection task request should be removed from the fileCollectionActiveTasksCache'
            fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) - 1

        and: 'ProcessRequestVO added in the FileCollectionScheduledRecoveryCache'
            fileCollectionScheduledRecoveryCache.size() == old(fileCollectionScheduledRecoveryCache.size()) + 1

        and: 'Cancel Timer when File Collection is stopped'
            1 * timer.cancel()

    }

    def 'Don\'t Stop File Collection if there are file based Scanners Active with fileCollectionEnable true on the Node'() {
        given: 'Deactivating celltrace subscription with valid node and inactive scanner'
            subscriptionFileMO = cellTraceSubscriptionBuilder.outputMode('FILE').
                    administrativeState(AdministrationState.ACTIVE).
                    taskStatus(TaskStatus.OK).
                    addNode(erbsNodeMO).
                    addEvent('group', 'event').
                    build()
            scannerFileMO = scannerUtil.builder(PREDEF_10003_CELLTRACE, LTE01ERBS00001).
                    status(ScannerStatus.ACTIVE).
                    processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
                    subscriptionId(subscriptionFileMO).
                    fileCollectionEnabled(true).
                    build()
            subscriptionStreamMO = cellTraceSubscriptionBuilder.outputMode('STREAMING').
                    name('streamsub').
                    administrativeState(AdministrationState.DEACTIVATING).
                    taskStatus(TaskStatus.OK).
                    addNode(erbsNodeMO).
                    addEvent('group', 'event').
                    build()
            scannerStreamMO = scannerUtil.builder(PREDEF_10004_CELLTRACE, LTE01ERBS00001).
                    status(ScannerStatus.INACTIVE).
                    processType(ProcessType.HIGH_PRIORITY_CELLTRACE).
                    subscriptionId(subscriptionStreamMO).
                    fileCollectionEnabled(false).
                    build()
            timer.info >> 900

        and: 'Add Process Request to fileCollectionActiveTasksCache'
            def processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(erbsNodeMO.getFdn(), 900, ProcessType.NORMAL_PRIORITY_CELLTRACE.
                    name()).build()
            fileCollectionActiveTasksCache.addProcessRequest(processRequestVO)

        when: 'Received the Dps Attribute Changed Event Listener'
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', 'ACTIVE', 'INACTIVE', scannerFileMO))

        then: 'Don\'t Stop File Collection --> File Collection task request should be removed from the fileCollectionActiveTasksCache'
            fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size())

        and: 'ProcessRequestVO is not added in the FileCollectionScheduledRecoveryCache'
            fileCollectionScheduledRecoveryCache.size() == old(fileCollectionScheduledRecoveryCache.size())

        and: 'Cancel Timer is not invoked'
            0 * timer.cancel()

    }

    @Unroll
    def 'Remove celltrace Subscription from PMICInitiationTrackerCache if Scanner status changed from #oldStatus to #newStatus and the admin state is #adminState'() {
        given: 'ERBS node, Celltrace subscription with #subscriptionAdministrationState and normal priority scanner with #newStatus'
            subscriptionFileMO = cellTraceSubscriptionBuilder.taskStatus(TaskStatus.OK).
                    administrativeState(adminState as AdministrationState).
                    addEvent('group', 'name').
                    addNode(erbsNodeMO).
                    build()
            scannerFileMO = scannerUtil.builder(PREDEF_10003_CELLTRACE, LTE01ERBS00001).
                    node(erbsNodeMO).
                    status(newStatus).
                    processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
                    subscriptionId(subscriptionFileMO).
                    build()
            timer.info >> 900

        and: 'Create InitiationTracker and Update the pmicInitiationTrackerCache with Subscription Id and ' + 'InitiationTracker'
            if (adminState.equals(AdministrationState.ACTIVATING.name())) {
                pmicInitiationTrackerCache.startTrackingActivation(subscriptionFileMO.poId as String, subscriptionFileMO.getAttribute('administrationState') as String, [(erbsNodeMO.
                        getFdn()): erbsNodeMO.getAttribute('neType') as String])
            } else {
                pmicInitiationTrackerCache.startTrackingDeactivation(subscriptionFileMO.poId as String, subscriptionFileMO.getAttribute('administrationState') as String, [(erbsNodeMO.
                        getFdn()): erbsNodeMO.getAttribute('neType') as String])
            }

        when: 'Received the Attribute Changed Event Listener'
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', oldStatus, newStatus, scannerFileMO))

        then: 'Update the administration state and task status, Remove Subscription from pmicInitiationTrackerCache'
         pmicInitiationTrackerCache.getTracker(String.valueOf(subscriptionFileMO.poId)) == null

        where: 'Scanner old scanner status is #oldStatus and new status is #newStatus and admin state is #subscriptionAdministrationState'
            oldStatus  | newStatus  | adminState
            'INACTIVE' | 'ERROR'    | AdministrationState.ACTIVATING.name()
            'INACTIVE' | 'ACTIVE'   | AdministrationState.ACTIVATING.name()
            'ACTIVE'   | 'ERROR'    | AdministrationState.DEACTIVATING.name()
            'ACTIVE'   | 'INACTIVE' | AdministrationState.DEACTIVATING.name()
            'ERROR'    | 'ERROR'    | AdministrationState.DEACTIVATING.name()
            'ERROR'    | 'INACTIVE' | AdministrationState.DEACTIVATING.name()
            'UNKNOWN'  | 'ERROR'    | AdministrationState.DEACTIVATING.name()
            'UNKNOWN'  | 'INACTIVE' | AdministrationState.DEACTIVATING.name()
            'ACTIVE'   | 'INACTIVE' | AdministrationState.UPDATING.name()
            'ACTIVE'   | 'ERROR'    | AdministrationState.UPDATING.name()
            'INACTIVE' | 'ERROR'    | AdministrationState.UPDATING.name()
            'INACTIVE' | 'ACTIVE'   | AdministrationState.UPDATING.name()
            'UNKNOWN'  | 'ERROR'    | AdministrationState.UPDATING.name()
            'UNKNOWN'  | 'INACTIVE' | AdministrationState.UPDATING.name()
            'UNKNOWN'  | 'ACTIVE'   | AdministrationState.UPDATING.name()

    }

    def 'Remove Subscription from PMICInitiationTrackerCache if Scanner status is not ERROR or ACTIVE'() {
        given: 'Dectivating celltrace subscription with one erbs node and inactive scanner'
            subscriptionFileMO = cellTraceSubscriptionBuilder.addNode(erbsNodeMO).
                    administrativeState(AdministrationState.DEACTIVATING).
                    taskStatus(TaskStatus.OK).
                    build()
            scannerFileMO = scannerUtil.builder(PREDEF_10003_CELLTRACE, LTE01ERBS00001).
                    status(ScannerStatus.INACTIVE).
                    processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
                    subscriptionId(subscriptionFileMO).
                    build()

        and: 'Create InitiationTracker and Update the pmicInitiationTrackerCache with Subscription Id and InitiationTracker'
            pmicInitiationTrackerCache.startTrackingDeactivation(subscriptionFileMO.poId as String, subscriptionFileMO.getAttribute('administrationState') as String, [(erbsNodeMO.
                    getFdn()): erbsNodeMO.getAttribute('neType') as String])

        when: 'Received the Attribute Changed Event Listener'
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', 'ACTIVE', 'INACTIVE', scannerFileMO))

        then:'If all the notifications have been received (i.e. nodes of nodes = total number of notifications), Remove Subscription from ' + 'pmicInitiationTrackerCache'
            pmicInitiationTrackerCache.getTracker(subscriptionFileMO.poId as String) == null

    }

    def 'Update PMICInitiationTrackerCache with subscription and InitiationNotificationsTracker if Scanner status is not ERROR or ACTIVE'() {
        given: 'Dectivating celltrace subscription with one erbs node and inactive scanner'
            subscriptionFileMO = cellTraceSubscriptionBuilder.addNode(erbsNodeMO).
                    administrativeState(AdministrationState.DEACTIVATING).
                    taskStatus(TaskStatus.OK).
                    build()
            scannerFileMO = scannerUtil.builder(PREDEF_10003_CELLTRACE, LTE01ERBS00001).
                    status(ScannerStatus.INACTIVE).
                    processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
                    subscriptionId(subscriptionFileMO).
                    build()

        and: 'Create InitiationTracker and Update the pmicInitiationTrackerCache with Subscription Id and InitiationTracker'
            pmicInitiationTrackerCache.startTrackingDeactivation(subscriptionFileMO.poId as String, subscriptionFileMO.getAttribute('administrationState') as String, [(erbsNodeMO.
                    getFdn()): erbsNodeMO.getAttribute('neType') as String])

        when: 'Received the Attribute Changed Event Listener'
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', 'ACTIVE', 'INACTIVE', scannerFileMO))

        then:'If number of nodes is not equal to number of notifications received, PmicInitiationTrackerCache should be updated with Subscription ' + 'and InitiationTracker'
            pmicInitiationTrackerCache.getTracker(subscriptionFileMO.poId as String) == null

    }

    @Unroll
    def 'Update subscription status when pmMigrationEnabled and subscriptionId event listen for #type'() {
        given:
            timer.info >> 900
            configurationChangeListener.getPmMigrationEnabled() >> true
            def subMo = dps.subscription().type(type).name('Test').administrationState(AdministrationState.ACTIVE).build()
            def scannerMo
            if (type == SubscriptionType.UETR) {
                scannerMo = dps.scanner().
                        scannerType(Scanner.PmicScannerType.PMICUeScannerInfo).
                        nodeName('LTE01ERBS00001').
                        name(scannerName).
                        processType(subMo).
                        status(ScannerStatus.ACTIVE).
                        build()
            } else {
                scannerMo = dps.scanner().
                        nodeName('LTE01ERBS00001').
                        name(scannerName).
                        processType(subMo).
                        status(ScannerStatus.ACTIVE).
                        build()
            }

        and:
            if (scannerName == 'PREDEF.STATS') {
                pmicInitiationTrackerCache.startTrackingActivation(subMo.poId as String, subMo.getAttribute('administrationState') as String,
                        ['NetworkElement=LTE01ERBS00001': 'ERBS'],
                        ['NetworkElement=LTE01ERBS00001': 1])
            } else {
                pmicInitiationTrackerCache.startTrackingActivation(subMo.poId as String, subMo.getAttribute('administrationState') as String,
                        ['NetworkElement=LTE01ERBS00001': 'ERBS'])
            }

        when:
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent(SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE, '0', subMo.poId as String,
                    scannerMo))

        and: 'The validation timer times out'
            Mockito.when(timer1.currentTimeMillis()).thenReturn(System.currentTimeMillis() + 30000)
            delayedTaskStatusValidator.validateTaskStatusAdminState()

        then: 'The subscription goes to ACTIVE/OK'
            verifySubscriptionState(subMo, ACTIVE, OK)

        where:
            type                                 | scannerName
            SubscriptionType.STATISTICAL         | 'PREDEF.STATS'
            SubscriptionType.CELLTRACE           | 'PREDEF.10001.CELLTRACE'
            SubscriptionType.CELLTRACE           | 'PREDEF.10004.CELLTRACE'
            SubscriptionType.CONTINUOUSCELLTRACE | 'PREDEF.10005.CELLTRACE'
            SubscriptionType.CELLTRAFFIC         | 'PREDEF.20000.CTR'
            SubscriptionType.GPEH                | 'PREDEF.30000.GPEH'
            SubscriptionType.CELLTRAFFIC         | 'PREDEF.30000.GPEH'
            SubscriptionType.EBM                 | 'PREDEF.EBMLOG.EBM'
            SubscriptionType.UETR                | 'PREDEF.10000.UETR'
            SubscriptionType.UETR                | 'PREDEF.10002.UETR'
    }

    @Unroll
    def 'Update PMICInitiationTrackerCache with subscription and InitiationNotificationsTracker if Scanner status is updated'() {
        given:
            timer.info >> 900
            def subMo = dps.subscription().type(type).name('Test').administrationState(AdministrationState.ACTIVATING).build()
            def scannerMo
            if (type == SubscriptionType.UETR) {
                scannerMo = dps.scanner().
                        nodeName('LTE01ERBS00001').
                        name(scannerName).
                        processType(subMo).
                        status(ScannerStatus.ACTIVE).
                        subscriptionId(subMo).
                        build()
            } else {
                scannerMo = dps.scanner().
                        scannerType(Scanner.PmicScannerType.PMICUeScannerInfo).
                        nodeName('LTE01ERBS00001').
                        name(scannerName).
                        processType(subMo).
                        status(ScannerStatus.ACTIVE).
                        subscriptionId(subMo).
                        build()
            }

        and:
            if (scannerName == 'PREDEF.STATS') {
                pmicInitiationTrackerCache.startTrackingActivation(subMo.poId as String, subMo.getAttribute('administrationState') as String,
                        ['NetworkElement=LTE01ERBS00001': 'ERBS'],
                        ['NetworkElement=LTE01ERBS00001': 1])
            } else {
                pmicInitiationTrackerCache.startTrackingActivation(subMo.poId as String, subMo.getAttribute('administrationState') as String,
                        ['NetworkElement=LTE01ERBS00001': 'ERBS'])
            }

        when:
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', 'INACTIVE', 'ACTIVE', scannerMo))

        then:
            pmicInitiationTrackerCache.getTracker(subMo.getPoId() as String) == null

        where:
            type                                 | scannerName
            SubscriptionType.STATISTICAL         | 'PREDEF.STATS'
            SubscriptionType.CELLTRACE           | 'PREDEF.10001.CELLTRACE'
            SubscriptionType.CONTINUOUSCELLTRACE | 'PREDEF.10005.CELLTRACE'
            SubscriptionType.CELLTRAFFIC         | 'PREDEF.20000.CTR'
            SubscriptionType.GPEH                | 'PREDEF.30000.GPEH'
            SubscriptionType.EBM                 | 'PREDEF.EBMLOG.EBM'
            SubscriptionType.UETR                | 'PREDEF.10000.UETR'

    }



    @Unroll
    def 'Update Subscription task status to #newSubscriptionStatus from #oldSubscriptionStatus when scanner inside subscription changes from #oldScannerStatus to #newScannerStatus'() {
        given: 'An ACTIVE subscription with ERBS node and valid scanner'
        subscriptionFileMO = subBuilder.newInstance(dpsUtils).
                administrativeState(AdministrationState.ACTIVE).
                taskStatus(oldSubscriptionStatus).
                addNode(erbsNodeMO).
                name('ContinuousCellTraceSubscription').
                build()
        scannerFileMO = scannerUtil.builder(scannerName, LTE01ERBS00001).
                status(newScannerStatus).
                processType(processType).
                subscriptionId(subscriptionFileMO.poId).
                node(erbsNodeMO).
                build()

        and: 'Create InitiationTracker and Update the pmicInitiationTrackerCache with Subscription Id and InitiationTracker'
        pmicInitiationTrackerCache.startTrackingActivation(subscriptionFileMO.poId as String, subscriptionFileMO.getAttribute('administrationState') as String, [(erbsNodeMO.
                getFdn()): erbsNodeMO.getAttribute('neType') as String])

        and: 'No timer exists'
        timer.info >> []

        when: 'Scanner status changed from #oldScannerStatus to #newScannerStatus'
        dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', oldScannerStatus.name(), newScannerStatus.name(), scannerFileMO))

        then: 'Subscription task status'
        subscriptionFileMO.getAttribute('taskStatus') == newSubscriptionStatus.name()

        where: 'Subscription new status is #newSubscriptionStatus and old status is #oldSubscriptionStatus changing when the status of the scanner turns #newScannerStatus from #oldScannerStatus'
        oldSubscriptionStatus | newSubscriptionStatus | oldScannerStatus     | newScannerStatus     | processType                           | scannerName                   | subBuilder
        TaskStatus.OK         | TaskStatus.ERROR      | ScannerStatus.ACTIVE | ScannerStatus.ERROR  | ProcessType.NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10003.CELLTRACE'      | CellTraceSubscriptionBuilder
        TaskStatus.ERROR      | TaskStatus.OK         | ScannerStatus.ERROR  | ScannerStatus.ACTIVE | ProcessType.NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10003.CELLTRACE'      | CellTraceSubscriptionBuilder
        TaskStatus.OK         | TaskStatus.ERROR      | ScannerStatus.ACTIVE | ScannerStatus.ERROR  | ProcessType.NORMAL_PRIORITY_CELLTRACE | 'PREDEF.DU.10003.CELLTRACE'   | CellTraceSubscriptionBuilder
        TaskStatus.ERROR      | TaskStatus.OK         | ScannerStatus.ERROR  | ScannerStatus.ACTIVE | ProcessType.NORMAL_PRIORITY_CELLTRACE | 'PREDEF.DU.10003.CELLTRACE'   | CellTraceSubscriptionBuilder
        TaskStatus.OK         | TaskStatus.ERROR      | ScannerStatus.ACTIVE | ScannerStatus.ERROR  | ProcessType.HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10005.CELLTRACE'      | CctrSubscriptionBuilder
        TaskStatus.ERROR      | TaskStatus.OK         | ScannerStatus.ERROR  | ScannerStatus.ACTIVE | ProcessType.HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10005.CELLTRACE'      | CctrSubscriptionBuilder
        TaskStatus.ERROR      | TaskStatus.OK         | ScannerStatus.ACTIVE | ScannerStatus.ACTIVE | ProcessType.HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10005.CELLTRACE'      | CctrSubscriptionBuilder
        TaskStatus.OK         | TaskStatus.ERROR      | ScannerStatus.ACTIVE | ScannerStatus.ERROR  | ProcessType.HIGH_PRIORITY_CELLTRACE   | 'PREDEF.CUCP.10005.CELLTRACE' | CctrSubscriptionBuilder
        TaskStatus.ERROR      | TaskStatus.OK         | ScannerStatus.ERROR  | ScannerStatus.ACTIVE | ProcessType.HIGH_PRIORITY_CELLTRACE   | 'PREDEF.CUCP.10005.CELLTRACE' | CctrSubscriptionBuilder
        TaskStatus.ERROR      | TaskStatus.OK         | ScannerStatus.ACTIVE | ScannerStatus.ACTIVE | ProcessType.HIGH_PRIORITY_CELLTRACE   | 'PREDEF.CUCP.10005.CELLTRACE' | CctrSubscriptionBuilder

    }

    @Unroll
    def 'Update Subscription task status to #newSubscriptionStatus from #oldSubscriptionStatus when the scanner status changes from #oldScannerStatus to #newScannerStatus for an EBS Subscription'(){
        def subscriptionList = new ArrayList()
        given: 'A valid 10004 scanner'
            scannerStreamMO = scannerUtil.builder(PREDEF_10004_CELLTRACE, LTE01ERBS00001).
                    status(newScannerStatus)
                    .processType(ProcessType.HIGH_PRIORITY_CELLTRACE)
                    .subscriptionId(0)
                    .node(erbsNodeMO)
                    .build()

        and: 'Multiple ACTIVE EBS subscription with a valid ERBS Node'
            (1..3).each{ it ->
                subscriptionStreamMO = CellTraceSubscriptionBuilder.newInstance(dpsUtils).
                        administrativeState(AdministrationState.ACTIVE).
                        taskStatus(oldSubscriptionStatus).
                        addNode(erbsNodeMO).
                        name('EBS_'+it).
                        build()
                createSubScanner(erbsNodeMO,subscriptionStreamMO)
                subscriptionList.add(subscriptionStreamMO)
            }

        when: 'Scanner status changed from #oldScannerStatus to #newScannerStatus'
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', oldScannerStatus.name(), newScannerStatus.name(), scannerStreamMO))

        and: 'validation timer times out'
            Mockito.when(timer1.currentTimeMillis()).thenReturn(8001L)
            delayedTaskStatusValidator.validateTaskStatusAdminState()

        then: 'Subscription task status gets updated to #newSubscriptionStatus'
            subscriptionList.each {subscription->
                subscription.getAttribute('taskStatus') == newSubscriptionStatus.name()
            }

        where: 'Subscription new status is #newSubscriptionStatus and old status is #oldSubscriptionStatus changing when the status of the scanner turns #newScannerStatus from #oldScannerStatus'
            oldSubscriptionStatus   |   oldScannerStatus        | newScannerStatus          |   scannerName                 |   radioNode   ||  newSubscriptionStatus
            TaskStatus.OK           |   ScannerStatus.ACTIVE    | ScannerStatus.ERROR       |   'PREDEF.10004.CELLTRACE'    |   erbsNodeMO  ||  TaskStatus.ERROR
            TaskStatus.OK           |   ScannerStatus.ACTIVE    | ScannerStatus.INACTIVE    |   'PREDEF.10004.CELLTRACE'    |   erbsNodeMO  ||  TaskStatus.ERROR
            TaskStatus.ERROR        |   ScannerStatus.ERROR     | ScannerStatus.ACTIVE      |   'PREDEF.10004.CELLTRACE'    |   erbsNodeMO  ||  TaskStatus.OK
            TaskStatus.ERROR        |   ScannerStatus.INACTIVE  | ScannerStatus.ACTIVE      |   'PREDEF.10004.CELLTRACE'    |   erbsNodeMO  ||  TaskStatus.OK
            TaskStatus.OK           |   ScannerStatus.ACTIVE    | ScannerStatus.ERROR       |   'PREDEF.DU.10004.CELLTRACE' |   gNodeBMO    ||  TaskStatus.ERROR
            TaskStatus.OK           |   ScannerStatus.ACTIVE    | ScannerStatus.INACTIVE    |   'PREDEF.DU.10004.CELLTRACE' |   gNodeBMO    ||  TaskStatus.ERROR
            TaskStatus.ERROR        |   ScannerStatus.ERROR     | ScannerStatus.ACTIVE      |   'PREDEF.DU.10004.CELLTRACE' |   gNodeBMO    ||  TaskStatus.OK
            TaskStatus.ERROR        |   ScannerStatus.INACTIVE  | ScannerStatus.ACTIVE      |   'PREDEF.DU.10004.CELLTRACE' |   gNodeBMO    ||  TaskStatus.OK

    }

    def 'Task status of an EBS Subscription is not changed when 10004 scanner status is updated but no sub scanners associated with it'(){
        given: 'A valid subscription'
            subscriptionStreamMO = CellTraceSubscriptionBuilder.newInstance(dpsUtils).
                    administrativeState(AdministrationState.ACTIVE).
                    taskStatus(TaskStatus.OK).
                    addNode(erbsNodeMO).
                    name('EBS_L').
                    build()

        and: 'A valid 10004 scanner but no sub scanner'
            scannerStreamMO = scannerUtil.builder(PREDEF_10004_CELLTRACE, LTE01ERBS00001).
                    status(ScannerStatus.INACTIVE)
                    .processType(ProcessType.HIGH_PRIORITY_CELLTRACE)
                    .subscriptionId(0)
                    .node(erbsNodeMO)
                    .build()

        when: 'Scanner status changed from #oldScannerStatus to #newScannerStatus'
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', ScannerStatus.ACTIVE.name(), ScannerStatus.INACTIVE.name(), scannerStreamMO))

        then: 'An empty sub scanner list is returned and the subscription task status remains the same'
            subscriptionStreamMO.getAttribute('taskStatus') == TaskStatus.OK.name()

    }

    @Unroll
    def 'update #subBuilder.getSimpleName() subscription\'s taskStatus to #helper.taskStatus if scanner update notification is received where the new scanner status is #helper.scannerStatus'() {
        given:
            nodeMo = nodeUtil.builder(LTE01ERBS00002).build()
            subMo = subscriptionBuilderClass.newInstance(dpsUtils).
                    administrativeState(AdministrationState.UPDATING).
                    taskStatus(TaskStatus.NA).
                    addNode(nodeMo).
                    name(subscriptionName).
                    build()
            scannerMo = scannerUtil.builder('scanner name not important', nodeMo.getName()).processType(subMo).node(nodeMo).subscriptionId(subMo)
                    .status(scannerStatus).build()

        and: 'Create InitiationTracker and Update the pmicInitiationTrackerCache with Subscription Id and InitiationTracker'
            pmicInitiationTrackerCache.startTrackingActivation(subMo.poId as String, subMo.getAttribute('administrationState') as String, [(nodeMo.
                    getFdn()): nodeMo.getAttribute('neType') as String])
            timer.info >> 900

        when:
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', ScannerStatus.INACTIVE.name(),
                    scannerStatus, scannerMo))

        then:
            subMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == taskStatus

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
    def 'update UETR subscription\'s taskStatus to #helper.taskStatus if scanner update notification is received where the new scanner status is #helper.scannerStatus'() {
        given:
            nodeMo = nodeUtil.builder(LTE01ERBS00002).build()
            subMo = uetrSubscriptionBuilder.ueInfoList([new UeInfo(UeType.IMSI, '01234567')]).
                    name('test').
                    addNode(nodeMo).
                    administrativeState(AdministrationState.UPDATING).
                    taskStatus(TaskStatus.NA).
                    build()
            scannerMo = scannerUtil.builder('scanner name not important', nodeMo.getName()).processType(subMo).node(nodeMo).subscriptionId(subMo)
                    .status(helper.scannerStatus as ScannerStatus).build()

        and: 'Create InitiationTracker and Update the pmicInitiationTrackerCache with Subscription Id and InitiationTracker'
            pmicInitiationTrackerCache.startTrackingActivation(subMo.poId as String, subMo.getAttribute('administrationState') as String, [(nodeMo.
                    getFdn()): nodeMo.getAttribute('neType') as String])

        and: 'No timer exists'
            timer.info >> []

        when:
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', ScannerStatus.INACTIVE.name(),
                    (helper.scannerStatus as ScannerStatus).name(), scannerMo))

        then:
            subMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == (helper.taskStatus as TaskStatus).name()

        where:
            [helper] << [
                    [new Helper(ScannerStatus.ACTIVE, TaskStatus.OK), new Helper(ScannerStatus.ERROR, TaskStatus.ERROR)]
            ]

    }

    @Unroll
    def 'Update File Collection if there is a change in the ROP period for the scanner with current ROP period #currentRopPeriod'() {
        given: 'Active GPEH subscription with valid node and scanner'
            def nodeMo = dps.node().name('RNC01').neType('RNC').build()
            def subMo = dps.subscription().
                    type(SubscriptionType.GPEH).
                    administrationState(AdministrationState.ACTIVE).
                    taskStatus(TaskStatus.OK).
                    nodes(nodeMo).
                    build()
            def scannerMo = dps.scanner().
                    nodeName(nodeMo).
                    name('PREDEF.30000.GPEH').
                    processType(ProcessType.REGULAR_GPEH).
                    status(ScannerStatus.ACTIVE).
                    subscriptionId(subMo).
                    build()
            timer.info >> 900

        and: 'Add Process Request to fileCollectionActiveTasksCache'
            def processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(nodeMo.getFdn(), currentRopPeriod,
                    ProcessType.REGULAR_GPEH.name()).build()
            fileCollectionActiveTasksCache.addProcessRequest(processRequestVO)

        when: 'Received the Dps Attribute Changed Event Listener'
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('ropPeriod', currentRopPeriod, newRopPeriod, scannerMo))

        then: 'Old file collection to be stopped at the end of ROP boundary and new file collection to be started'
            fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) + 1
            def updatedRequest = fileCollectionActiveTasksCache.getProcessRequest(
                    fileCollectionActiveTasksCache.getKeyFor(processRequestVO))
            def newRequest = fileCollectionActiveTasksCache.getProcessRequest(
                    fileCollectionActiveTasksCache.getKeyForRop(processRequestVO, newRopPeriod))
            updatedRequest.getEndTime() > 0
            newRequest.getEndTime() == 0
            newRequest.getStartTime() == updatedRequest.getEndTime() - TimeUnit.SECONDS.toMillis(1)

        and: 'ProcessRequestVO not added to the FileCollectionScheduledRecoveryCache'
            fileCollectionScheduledRecoveryCache.size() == old(fileCollectionScheduledRecoveryCache.size())

        where: 'Update notification received for ROP period. New ROP is #newRopPeriod and old ROP is #oldRopPeriod'
            currentRopPeriod | newRopPeriod
            900              | 60
            60               | 900

    }

    @Unroll
    def 'Scanner update EBS scanner2 : Does not stop file collection on another EBS scanner1 if its status is ACTIVE and fileCollectionEnabled is true1'() {
        given: 'Active EBS subscription with valid node and scanner'
            def nodeMo = dps.node().name('LTE02ERBS00001').neType('ERBS').build()
            def subMo = dps.subscription().
                    type(SubscriptionType.CELLTRACE).
                    administrationState(AdministrationState.ACTIVE).
                    taskStatus(TaskStatus.OK).
                    nodes(nodeMo).
                    build()
            dps.scanner().
                    nodeName(nodeMo).
                    name('PREDEF.10003.CELLTRACE').
                    processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
                    status(ScannerStatus.ACTIVE).
                    subscriptionId(subMo).
                    fileCollectionEnabled(true).
                    build()
            def streamScannerMo = dps.scanner().
                    nodeName(nodeMo).
                    name('PREDEF.10004.CELLTRACE').
                    processType(ProcessType.HIGH_PRIORITY_CELLTRACE).
                    status(ScannerStatus.INACTIVE).
                    subscriptionId(0).
                    fileCollectionEnabled(false).
                    ropPeriod(ropPeriod).
                    build()

        and: 'Add Process Request to fileCollectionActiveTasksCache'
            def processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(nodeMo.getFdn(), 900, ProcessType
                    .NORMAL_PRIORITY_CELLTRACE.name()).build()
            fileCollectionActiveTasksCache.addProcessRequest(processRequestVO)

        when: 'Received the Dps Attribute Changed Event Listener'
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('status', ScannerStatus.INACTIVE.name(), scannerStatus.name(),
                    streamScannerMo))

        then: 'Old file collection not stopped'
            fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size())

        and: 'ProcessRequestVO not added to the FileCollectionScheduledRecoveryCache'
            fileCollectionScheduledRecoveryCache.size() == old(fileCollectionScheduledRecoveryCache.size())

        and:
            0 * timer.cancel()

        where: 'Update notification received for status attribute. New Attribute is #scannerStatus and old attribute is INACTIVE'
            ropPeriod | scannerStatus
            900       | ScannerStatus.ERROR
            900       | ScannerStatus.ERROR
            900       | ScannerStatus.ACTIVE
            60        | ScannerStatus.ERROR
            60        | ScannerStatus.ERROR
            60        | ScannerStatus.ACTIVE

    }

    @Unroll
    def 'Scanner update EBS scanner2 : Does not stop file collection on another EBS scanner1 if its status is ACTIVE and fileCollectionEnabled is true'() {
        given: 'Active EBS subscription with valid node and scanner'
            def nodeMo = dps.node().name('LTE02ERBS00001').neType('ERBS').build()
            def subMo = dps.subscription().
                    type(SubscriptionType.CELLTRACE).
                    administrationState(AdministrationState.ACTIVE).
                    taskStatus(TaskStatus.OK).
                    nodes(nodeMo).
                    build()
            dps.scanner().
                    nodeName(nodeMo).
                    name('PREDEF.10003.CELLTRACE').
                    processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
                    status(ScannerStatus.ACTIVE).
                    subscriptionId(subMo).
                    fileCollectionEnabled(true).
                    build()
            def streamScannerMo = dps.scanner().
                    nodeName(nodeMo).
                    name('PREDEF.10004.CELLTRACE').
                    processType(ProcessType.HIGH_PRIORITY_CELLTRACE).
                    status(scannerStatus).
                    subscriptionId(0).
                    fileCollectionEnabled(newFileCollectionEnabled).
                    ropPeriod(ropPeriod).
                    build()

        and: 'Add Process Request to fileCollectionActiveTasksCache'
            def processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(nodeMo.getFdn(), 900, ProcessType
                    .NORMAL_PRIORITY_CELLTRACE.name()).build()
            fileCollectionActiveTasksCache.addProcessRequest(processRequestVO)

        when: 'Received the Dps Attribute Changed Event Listener'
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('fileCollectionEnabled', currentFileCollectionEnabled, newFileCollectionEnabled,
                    streamScannerMo))

        then: 'Old file collection is not stopped'
            fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size())

        and: 'ProcessRequestVO not added to the FileCollectionScheduledRecoveryCache'
            fileCollectionScheduledRecoveryCache.size() == old(fileCollectionScheduledRecoveryCache.size())

        and:
            0 * timer.cancel()

        where: 'Update notification received for fileCollectionEnabled attribute. New Attribute is #newFileCollectionEnabled and old attribute is #currentFileCollectionEnabled'
            currentFileCollectionEnabled | newFileCollectionEnabled | ropPeriod | scannerStatus
            true                         | false                    | 900       | ScannerStatus.ERROR
            false                        | true                     | 900       | ScannerStatus.ERROR
            true                         | false                    | 900       | ScannerStatus.ACTIVE
            true                         | false                    | 60        | ScannerStatus.ERROR
            false                        | true                     | 60        | ScannerStatus.ERROR
            true                         | false                    | 60        | ScannerStatus.ACTIVE

    }

    @Unroll
    def 'Update File Collection if there is a change in the ROP period for the scanner with process type #processType and current ROP period #currentRopPeriod with scanner status #scannerStatus'() {
        given: 'An ACTIVE subscription with #nodeType node and valid scanner #scannerName'
            def nodeMO = dps.node().name(nodeName).neType(neType).build()
            def subscriptionMO = dps.subscription().
                    type(subscriptionType).
                    administrationState(AdministrationState.ACTIVE).
                    taskStatus(TaskStatus.OK).
                    nodes(nodeMO).
                    build()
            def scannerMO = dps.scanner().
                    nodeName(nodeMO).
                    name(scannerName).
                    processType(processType).
                    status(scannerStatus).
                    subscriptionId(subscriptionMO).
                    build()
            timer.info >> 900

        and: 'Add Process Request to fileCollectionActiveTasksCache for process type #processType'
            def processRequestVO = new ProcessRequestVO.ProcessRequestVOBuilder(nodeMO.getFdn(), currentRopPeriod, processType.name()).build()
            fileCollectionActiveTasksCache.addProcessRequest(processRequestVO)

        when: 'Received the Dps Attribute Changed Event Listener for ROP period'
            dpsScannerUpdateNotificationListener.onEvent(createAttributeChangeEvent('ropPeriod', currentRopPeriod, newRopPeriod, scannerMO))

        then: 'FileCollectionActiveTasksCache should have #expectedAdditionalEntriesInCache more entries'
            fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) + expectedAdditionalEntriesInCache

        where: 'Update notification is received on scanner #scannerName of process type #processType and status #scannerStatus for ROP period from #currentRopPeriod to #newRopPeriod'
            subscriptionType             | scannerStatus          | processType                           | scannerName               | currentRopPeriod | newRopPeriod | expectedAdditionalEntriesInCache | neType | nodeName
            SubscriptionType.CELLTRACE   | ScannerStatus.ACTIVE   | ProcessType.NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10003.CELLTRACE'  | 900              | 60           | 0                                | 'ERBS' | 'LTE01ERBS00005'
            SubscriptionType.CELLTRACE   | ScannerStatus.INACTIVE | ProcessType.NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10003.CELLTRACE'  | 900              | 60           | 0                                | 'ERBS' | 'LTE01ERBS00005'
            SubscriptionType.STATISTICAL | ScannerStatus.ACTIVE   | ProcessType.STATS                     | 'PREDEF.STATS'            | 900              | 60           | 0                                | 'ERBS' | 'LTE01ERBS00005'
            SubscriptionType.STATISTICAL | ScannerStatus.INACTIVE | ProcessType.STATS                     | 'PREDEF.STATS'            | 900              | 60           | 0                                | 'ERBS' | 'LTE01ERBS00005'
            SubscriptionType.STATISTICAL | ScannerStatus.ACTIVE   | ProcessType.STATS                     | 'USERDEF.T1.Cont.Y.STATS' | 900              | 60           | 0                                | 'ERBS' | 'LTE01ERBS00005'
            SubscriptionType.STATISTICAL | ScannerStatus.INACTIVE | ProcessType.STATS                     | 'USERDEF.T2.Cont.Y.STATS' | 900              | 60           | 0                                | 'ERBS' | 'LTE01ERBS00005'
            SubscriptionType.CELLTRAFFIC | ScannerStatus.ACTIVE   | ProcessType.CTR                       | 'PREDEF.20000.CTR'        | 900              | 60           | 0                                | 'RNC'  | 'RNC02'
            SubscriptionType.CELLTRAFFIC | ScannerStatus.INACTIVE | ProcessType.CTR                       | 'PREDEF.20000.CTR'        | 900              | 60           | 0                                | 'RNC'  | 'RNC02'
            SubscriptionType.GPEH        | ScannerStatus.ACTIVE   | ProcessType.REGULAR_GPEH              | 'PREDEF.30000.GPEH'       | 900              | 60           | 1                                | 'RNC'  | 'RNC02'
            SubscriptionType.GPEH        | ScannerStatus.INACTIVE | ProcessType.REGULAR_GPEH              | 'PREDEF.30000.GPEH'       | 900              | 60           | 0                                | 'RNC'  | 'RNC02'

    }


    private class Helper {
        public ScannerStatus scannerStatus
        public TaskStatus taskStatus

        Helper(final ScannerStatus scannerStatus, final TaskStatus taskStatus) {
            this.scannerStatus = scannerStatus
            this.taskStatus = taskStatus
        }
    }

    private boolean verifySubscriptionState(final def subscriptionMO, final String adminState, final String taskStatus) {
        return adminState == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) &&
                taskStatus == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE);
    }

    def createAttributeChangeEvent(String attribute, Object oldValue, Object newValue, ManagedObject scanner) {
        return new DpsAttributeChangedEvent(fdn: scanner.fdn, changedAttributes: [new AttributeChangeData(attribute, oldValue, newValue, null, null)])
    }

    def createSubScanner(node, sub) {
        dps.subScanner().subscriptionId(sub.poId).fdn(buildPmicSubScannerInfoFdn(node, sub)).build()
    }

    def buildPmicSubScannerInfoFdn(ManagedObject node, ManagedObject subscriptionUnderTest) {
        return pmSubScannerService.buildPmSubScannerFdn(node.fdn, EBS_CELLTRACE_SCANNER, subscriptionUnderTest.name)
    }
}
