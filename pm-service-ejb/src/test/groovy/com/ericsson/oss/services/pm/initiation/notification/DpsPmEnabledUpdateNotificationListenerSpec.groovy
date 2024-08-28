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

import static com.ericsson.oss.pmic.cdi.test.util.Constants.*
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE

import spock.lang.Unroll

import javax.ejb.Timer
import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.api.cache.PmFunctionData
import com.ericsson.oss.pmic.dao.ScannerDao
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.collection.cache.StartupRecoveryMonitorLocal
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener
import com.ericsson.oss.services.pm.initiation.ejb.GroovyTestUtils
import com.ericsson.oss.services.pm.initiation.notification.events.Deactivate
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEvent
import com.ericsson.oss.services.pm.initiation.notification.handlers.InitiationTrackerCacheEntryRemovedHandler
import com.ericsson.oss.services.pm.initiation.notification.handlers.ResPmFunctionHelper
import com.ericsson.oss.services.pm.initiation.schedulers.DelayedPmFunctionUpdateProcessor
import com.ericsson.oss.services.pm.initiation.tasks.ScannerDeletionTaskRequest
import com.ericsson.oss.services.pm.initiation.tasks.ScannerPollingTaskRequest
import com.ericsson.oss.services.pm.initiation.tasks.ScannerSuspensionTaskRequest
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionActivationTaskRequest
import com.ericsson.oss.services.pm.initiation.utils.PmFunctionUtil

/***
 * This class will test for the pmEnabled attribute change DPS notifications in PmFunction MO update.
 */
class DpsPmEnabledUpdateNotificationListenerSpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    DpsPmEnabledUpdateNotificationListener dpspmEnabledUpdateNotificationListener

    @MockedImplementation
    TimerService timerService

    @MockedImplementation
    Timer timer

    @MockedImplementation
    StartupRecoveryMonitorLocal startupRecoveryMonitor

    @MockedImplementation
    ConfigurationChangeListener configurationChangeListener

    @MockedImplementation
    @Deactivate
    InitiationEvent deactivationEvent

    @Inject
    private InitiationTrackerCacheEntryRemovedHandler trackerCacheEntryRemovedHandler

    @Inject
    private DpsPmEnabledUpdateNotificationProcessor dpsPmEnabledUpdateNotificationProcessor

    @ImplementationInstance
    DelayedPmFunctionUpdateProcessor delayedUpdateProcessor = [
            scheduleDelayedPmFunctionUpdateProcessor: { a, b, c -> dpsPmEnabledUpdateNotificationProcessor.processPmFunctionChange(a, b, c) }
    ] as DelayedPmFunctionUpdateProcessor

    @Inject
    PmFunctionEnabledWrapper pmFunctionCache

    @Inject
    EventSender<MediationTaskRequest> eventSender

    @Inject
    GroovyTestUtils testUtils

    @Inject
    ScannerDao scannerDao

    @MockedImplementation
    ResPmFunctionHelper resPmFunctionHelper

    def nodeMo
    def nodeFdn

    def setup() {
        nodeFdn = 'NetworkElement=LTE01ERBS00001'
        nodeMo = nodeUtil.builder('').fdn(nodeFdn).build()
        timerService.timers >> []
    }

    def 'Before Attribute Changed Event is processed, no nodes in the cache'() {
        expect: 'pmFunctionCache should not contain node'
            !pmFunctionCache.containsFdn(nodeFdn)
    }

    @Unroll
    def 'when pmFunction is #pmEnabled the information is updated in the pm function cache.'() {
        given: 'pmEnabled Attribute change event is created'
            def attributeChangedEvent = createAttributeChangeEvent(pmEnabled, nodeFdn)

        and: 'pm Migration is ON / OFF'
            configurationChangeListener.pmMigrationEnabled >> migration

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(nodeFdn, new PmFunctionData(true))

        when: 'received the pm function change Event listener'
            dpspmEnabledUpdateNotificationListener.onEvent(attributeChangedEvent)

        then: 'pmFunctionCache updated with new FDN'
            pmFunctionCache.isPmFunctionEnabled(nodeFdn) == pmEnabled

        where:
            migration || pmEnabled
            true      || true
            true      || false
            false     || true
            false     || false
    }

    def 'when pmFunction is enabled a scanner polling task is sent for the node'() {
        given: 'pmEnabled Attribute change event is created'
            def attributeChangedEvent = createAttributeChangeEvent(true, nodeFdn)

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(nodeFdn, new PmFunctionData(true))

        when: 'received the pm function change Event listener'
            dpspmEnabledUpdateNotificationListener.onEvent(attributeChangedEvent)

        then: 'call Scanner polling Task for pmEnabled fdn at once'
            1 * eventSender.send({ request -> request.nodeAddress == nodeFdn } as ScannerPollingTaskRequest)
    }

    def 'When a pmFunction ON notification is received and the subscription is not found, scanner polling is sent'() {
        given: 'one node added to the active task cache'
            nodeUtil.builder(NODE_NAME_1).build()

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(NETWORK_ELEMENT_1, new PmFunctionData(true))

        when: 'a pmFunction enabled notification is received'
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(true, NETWORK_ELEMENT_1))

        then: 'scanner polling is sent'
            1 * eventSender.send({ polling -> polling.nodeAddress == NETWORK_ELEMENT_1 } as ScannerPollingTaskRequest)
    }

    def 'when a pm funtion ON notification is received for an Celltrace subscription'() {
        given:
            def nodeFdn = NE_PREFIX + NODE_NAME_1
            cellTraceSubscriptionBuilder.name('CellSub').addEvent('group', 'event').build()
            def nodeMO = nodeUtil.builder(NODE_NAME_1).build()
            def scannerMo = createScanner(NODE_NAME_1, 'PREDEF.10000.CELLTRACE', ProcessType.NORMAL_PRIORITY_CELLTRACE)
            nodeMO.addAssociation('nodes', scannerMo)

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(nodeFdn, new PmFunctionData(true))

        when:
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(true, nodeFdn))

        then: 'no Activation MTR is sent'
            0 * eventSender.send(_ as SubscriptionActivationTaskRequest)

        and: 'scanner polling is sent'
            1 * eventSender.send({ polling -> polling.nodeAddress == nodeFdn } as ScannerPollingTaskRequest)
    }

    def 'when a pm funtion ON notification is received for an Stats subscription, scanner Polling'() {
        given:
            def nodeFdn = NE_PREFIX + NODE_NAME_1
            createSubscription(SubscriptionType.STATISTICAL)
            nodeUtil.builder(NODE_NAME_1).build()

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(nodeFdn, new PmFunctionData(true))

        when:
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(true, nodeFdn))

        then: 'no Activation MTR is sent'
            0 * eventSender.send(_ as SubscriptionActivationTaskRequest)

        and: 'scanner polling is sent'
            1 * eventSender.send({ polling -> polling.nodeAddress == nodeFdn } as ScannerPollingTaskRequest)
    }

    def 'when a pm funtion ON notification is received for an EBM subscription, scanner polling is sent'() {
        given:
            def nodeFdn = NE_PREFIX + SGSN_NODE_NAME_1
            createSubscription(SubscriptionType.EBM)
            def erbsNodeMo = nodeUtil.builder(SGSN_NODE_NAME_1).neType('SGSN-MME').build()
            def scannerMO = createScanner(SGSN_NODE_NAME_1, 'PREDEF.EBMLOG.EBM', ProcessType.EVENTJOB)
            erbsNodeMo.addAssociation('scanners', scannerMO)

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(nodeFdn, new PmFunctionData(true))

        when:
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(true, nodeFdn))

        then: 'no Activation MTR is sent'
            0 * eventSender.send(_ as SubscriptionActivationTaskRequest)

        and: 'scanner polling is sent'
            1 * eventSender.send({ polling -> polling.nodeAddress == nodeFdn } as ScannerPollingTaskRequest)
    }

    def 'when a pm funtion ON notification is received for a CCTR subscription, Scanner polling is sent'() {
        given:
            cctrSubscriptionBuilder.addEvent('Groupname', 'event').build()
            def erbsNodeMo = nodeUtil.builder(NODE_NAME_1).neType('ERBS').build()
            def scannerMO = createScanner(NODE_NAME_1, 'PREDEF.10005.CELLTRACE', ProcessType.HIGH_PRIORITY_CELLTRACE)
            erbsNodeMo.addAssociation('scanners', scannerMO)

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(NE_PREFIX + NODE_NAME_1, new PmFunctionData(true))

        when:
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(true, NE_PREFIX + NODE_NAME_1))

        then: 'no Activation MTR is sent'
            0 * eventSender.send(_ as SubscriptionActivationTaskRequest)

        and: 'scanner polling is sent'
            1 * eventSender.send({ polling -> polling.nodeAddress == NE_PREFIX + NODE_NAME_1 } as ScannerPollingTaskRequest)
    }

    def 'when a pmFunction ON notification is received for a UETRACE and CTUM subscription, scanner polling is sent'() {
        given:
            ueTraceSubscriptionBuilder.build()
            ctumSubscriptionBuilder.build()
            nodeUtil.builder(SGSN_NODE_NAME_1).neType('SGSN-MME').build()

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(NE_PREFIX + SGSN_NODE_NAME_1, new PmFunctionData(true))

        when:
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(true, NE_PREFIX + SGSN_NODE_NAME_1))

        then: 'scanner polling job is sent'
            1 * eventSender.send({ polling -> polling.nodeAddress == NE_PREFIX + SGSN_NODE_NAME_1 } as ScannerPollingTaskRequest)
    }

    def 'when a pmFunction ON notification is received for a MoInstance subscription, scanner polling is sent'() {
        given:
            def nodeFdn = NE_PREFIX + 'RNC06RBS01'
            moinstanceSubscriptionBuilder.build()
            nodeUtil.builder('RNC06RBS01').neType(NetworkElementType.RNC).build()

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(nodeFdn, new PmFunctionData(true))

        when:
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(true, nodeFdn))

        then: 'scanner polling job is sent'
            1 * eventSender.send({ polling -> polling.nodeAddress == nodeFdn } as ScannerPollingTaskRequest)
    }

    def 'when a pm function ON notification is received for 2 EBS subscriptions in ACTIVE-ERROR, Scanner polling is sent'() {
        given: 'EBS Subscription'
            def node = dps.node().name(NODE_NAME_1).neType('ERBS').ossModelIdentity('16B-G.1.281').pmEnabled(true).build()
            def ebsSubscriptionMO = createCellTraceSubscrption('EbsSubscription', CellTraceCategory.EBSL_STREAM)

        and: 'EBS Subscription already activated for Node'
            createCellTraceSubscrption('EbsSubscription1', CellTraceCategory.EBSL_STREAM)

        and: 'EBS Scanner is available for selection'
            createEbsScanner(NODE_NAME_1)

        and: 'startup Recovery is done'
            startupRecoveryMonitor.startupRecoveryDone >> true

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(node.fdn, new PmFunctionData(true))

        when: 'PM function ON dps notification is received'
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(true, node.fdn))

        then: 'Scanner polling is sent'
            1 * eventSender.send({ polling -> polling.nodeAddress == node.fdn } as ScannerPollingTaskRequest)

        and: 'Subscription AdminState and TaskStatus'
            ACTIVE == ebsSubscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            ERROR == ebsSubscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String
    }

    def 'when a pm function ON notification is received for Two EBS subscriptions in ACTIVE-ERROR, Scanner polling is sent'() {
        given: 'EBS Subscription'
            def node = dps.node().name(NODE_NAME_1).neType('ERBS').ossModelIdentity('16B-G.1.281').pmEnabled(true).build()
            def ebsSubscriptionMo = createCellTraceSubscrption('EbsSubscription', CellTraceCategory.EBSL_STREAM)
        and: 'EBS Subscription already activated for Node'
            createCellTraceSubscrption('EbsSubscription1', CellTraceCategory.EBSL_STREAM)

        and: 'EBS Scanner is available for selection'
            createEbsScanner(NODE_NAME_1)

        and: 'startup Recovery is done'
            startupRecoveryMonitor.startupRecoveryDone >> true

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(node.fdn, new PmFunctionData(true))

        when: 'PM function ON dps notification is received'
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(true, node.fdn))

        and: 'first initation event completed and Removed from InitiationTrackerCache'
            trackerCacheEntryRemovedHandler.execute(String.valueOf(ebsSubscriptionMo.poId))

        then: 'scanner polling is sent'
            1 * eventSender.send({ polling -> polling.nodeAddress == node.fdn } as ScannerPollingTaskRequest)

        and: 'Subscription AdminState and TaskStatus'
            ACTIVE == ebsSubscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) as String
            ERROR == ebsSubscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) as String
    }

    def 'when a pm function ON notification is received for two nodes in Two EBS subscriptions in ACTIVE-ERROR, Scanner polling is sent'() {
        given: 'EBS Subscription'
            def node = dps.node().name(NODE_NAME_1).neType('ERBS').ossModelIdentity('16B-G.1.281').pmEnabled(true).build()
            def ebsSubscriptionMo = createCellTraceSubscrption('EbsSubscription', CellTraceCategory.EBSL_STREAM)

        and: 'EBS Subscription already activated with different node'
            def node2 = dps.node().name(NODE_NAME_2).neType('ERBS').ossModelIdentity('16B-G.1.281').pmEnabled(true).build()
            createCellTraceSubscrption('EbsSubscription1', CellTraceCategory.EBSL_STREAM)

        and: 'EBS Scanner is available for selection for both nodes'
            createEbsScanner(NODE_NAME_1)
            createEbsScanner(NODE_NAME_2)

        and: 'startup Recovery is done'
            startupRecoveryMonitor.startupRecoveryDone >> true

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(node.fdn, new PmFunctionData(true))
            pmFunctionCache.addEntry(node2.fdn, new PmFunctionData(true))

        when: 'PM function ON dps notification is received'
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(true, node.fdn))
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(true, node2.fdn))

        and: 'first initation event completed and Removed from InitiationTrackerCache'
            trackerCacheEntryRemovedHandler.execute(String.valueOf(ebsSubscriptionMo.poId))

        then: 'scanner polling is sent'
            1 * eventSender.send({ polling -> polling.nodeAddress == node.fdn } as ScannerPollingTaskRequest)
    }

    def 'when a pm function ON notification is received for EBS subcription with already active events on Node, scanner polling is sent'() {
        given: 'EBS Subscription'
            def node = dps.node().name(NODE_NAME_1).neType('ERBS').ossModelIdentity('16B-G.1.281').pmEnabled(true).build()
            createCellTraceSubscrption('EbsSubscription1', CellTraceCategory.EBSL_STREAM)

        and: 'EBS Subscription already activated for Node'
            createCellTraceSubscrption('EbsSubscription2', CellTraceCategory.EBSL_STREAM)

        and: 'EBS Scanner is available for selection'
            createEbsScanner(NODE_NAME_1)

        and: 'startup Recovery is done'
            startupRecoveryMonitor.startupRecoveryDone >> true

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(node.fdn, new PmFunctionData(true))

        when: 'PM function ON dps notification is received'
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(true, node.fdn))

        then: 'scanner polling is sent'
            1 * eventSender.send({ polling -> polling.nodeAddress == node.fdn } as ScannerPollingTaskRequest)
    }

    def 'when a pm function ON notification is received for Statistical subcription and Migration is disabled and unknown scanner is created, scanner polling is sent'() {
        given: 'a Statistical Subscription'
            def nodeFdn = NE_PREFIX + NODE_NAME_1
            createSubscription(SubscriptionType.STATISTICAL)
            nodeUtil.builder(NODE_NAME_1).build()

        and: 'startup Recovery is done'
            startupRecoveryMonitor.startupRecoveryDone >> true

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(nodeFdn, new PmFunctionData(true))

        when: 'PM function ON dps notification is received'
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(true, nodeFdn))

        then: 'scanner polling is sent'
            1 * eventSender.send({ polling -> polling.nodeAddress == nodeFdn } as ScannerPollingTaskRequest)
    }

    def 'when a pm function ON notification is received for Statistical subcription and Migration is enabled and unknown scanner is not created, scanner polling is sent'() {
        given: 'Statistical Subscription'
            def nodeFdn = NE_PREFIX + NODE_NAME_1
            def statsSubscriptionMo = createSubscription(SubscriptionType.STATISTICAL)
            nodeUtil.builder(NODE_NAME_1).build()

        and: 'startup Recovery is done'
            startupRecoveryMonitor.startupRecoveryDone >> true

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(nodeFdn, new PmFunctionData(true))

        when: 'PM function ON dps notification is received'
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(true, nodeFdn))

        then: 'scanner polling is sent'
            1 * eventSender.send({ polling -> polling.nodeAddress == nodeFdn } as ScannerPollingTaskRequest)

        and: 'PMICScannerInfo is not created with UNKNOWN status'
            scannerDao.countByNodeFdnAndProcessTypeAndSubscriptionIdAndScannerStatus([nodeFdn],
                    [ProcessType.STATS] as ProcessType[],
                    [
                            statsSubscriptionMo.poId
                    ], ScannerStatus.UNKNOWN) == 0
    }

    @Unroll
    def 'when pm function turns OFF, all active, error and unknown USERDEF scanners on the node will be deactivated/deleted if Migration is OFF'() {
        given: 'a Statistical Subscription'
            def statsSubscriptionMo = createSubscription(SubscriptionType.STATISTICAL, 'Test1')
            def statsSubscriptionMo1 = createSubscription(SubscriptionType.STATISTICAL, 'Test2')
            def statsSubscriptionMo2 = createSubscription(SubscriptionType.STATISTICAL, 'Test3')
            def statsSubscriptionMo3 = createSubscription(SubscriptionType.STATISTICAL, 'Test4')

        and:
            def pmEnabledBeahviour = PmFunctionUtil.getPmFunctionConfig()
            def eventDeleteNumber = PmFunctionUtil.PmFunctionPropertyValue.PM_FUNCTION_LEGACY == pmEnabledBeahviour ? expectedEventSent : 0
            def eventDeactivateNumber = (PmFunctionUtil.PmFunctionPropertyValue.PM_FUNCTION_TORF_255692 == pmEnabledBeahviour) ? expectedEventSent : 0
            def node = dps.node().name('1').build()
            createScanner(node, name, processType, statsSubscriptionMo.poId)
            createScanner(node, "${name}ERR", processType, statsSubscriptionMo1.poId, ScannerStatus.ERROR)
            createScanner(node, "${name}UNK", processType, statsSubscriptionMo2.poId, ScannerStatus.UNKNOWN)
            createScanner(node, "${name}INA", processType, statsSubscriptionMo3.poId, ScannerStatus.INACTIVE)

        and: 'pm Migration is ON / OFF'
            configurationChangeListener.pmMigrationEnabled >> migration

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(node.fdn, new PmFunctionData(true))

        when:
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(false, node.fdn))

        then:
            3 * eventDeleteNumber * eventSender.send(_ as ScannerDeletionTaskRequest)
            eventDeactivateNumber * deactivationEvent.execute(_ as List<Node>, { it.id == statsSubscriptionMo.poId } as Subscription)
            eventDeactivateNumber * deactivationEvent.execute(_ as List<Node>, { it.id == statsSubscriptionMo1.poId } as Subscription)
            eventDeactivateNumber * deactivationEvent.execute(_ as List<Node>, { it.id == statsSubscriptionMo2.poId } as Subscription)
            0 * deactivationEvent.execute(_ as List<Node>, { it.id == statsSubscriptionMo3.poId } as Subscription)
        where:
            migration | name                        | processType       | expectedEventSent
            true      | 'USERDEF-TEST.Cont.Y.STATS' | ProcessType.STATS | 0
            false     | 'USERDEF-TEST.Cont.Y.STATS' | ProcessType.STATS | 1
    }

    @Unroll
    def 'when pm function turns OFF, all active, error and unknown PREDEF scanners on the node will be deactivated if Migration is OFF'() {
        given:
            def node = dps.node().name('1').build()
            def scanner = createScanner(node, name, processType,'121332')
            def scanner1 = createScanner(node, "${name}ERR", processType, '121332', ScannerStatus.ERROR, )
            def scanner2 = createScanner(node, "${name}UNK", processType, '121332', ScannerStatus.UNKNOWN)
            def scanner3 = createScanner(node, "${name}INA", processType, '121332', ScannerStatus.INACTIVE)

        and: 'pm Migration is ON / OFF'
            configurationChangeListener.pmMigrationEnabled >> migration

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(node.fdn, new PmFunctionData(true))

        when:
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(false, node.fdn))

        then:
            expectedEventSent * eventSender.send({ task -> task.scannerId == scanner.poId as String } as ScannerSuspensionTaskRequest)
            expectedEventSent * eventSender.send({ task -> task.scannerId == scanner1.poId as String } as ScannerSuspensionTaskRequest)
            expectedEventSent * eventSender.send({ task -> task.scannerId == scanner2.poId as String } as ScannerSuspensionTaskRequest)
            0 * eventSender.send({ task -> task.scannerId == scanner3.poId as String } as ScannerSuspensionTaskRequest)

        where:
            migration | name                     | processType                           | expectedEventSent
            false     | 'PREDEF.STATS'           | ProcessType.STATS                     | 1
            false     | 'PREDEF.10000.CELLTRACE' | ProcessType.NORMAL_PRIORITY_CELLTRACE | 1
            false     | 'PREDEF.10005.CELLTRACE' | ProcessType.HIGH_PRIORITY_CELLTRACE   | 1
            false     | 'PREDEF.30000.GPEH'      | ProcessType.REGULAR_GPEH              | 1
            false     | 'PREDEF.30001.GPEH'      | ProcessType.OPTIMIZER_GPEH            | 1
            false     | 'PREDEF.EBMLOG.EBM'      | ProcessType.EVENTJOB                  | 1
            false     | 'PREDEF.20000.CTR'       | ProcessType.CTR                       | 1
            false     | 'PREDEF.10000.UETR'      | ProcessType.UETR                      | 1
            true      | 'PREDEF.STATS'           | ProcessType.STATS                     | 0
            true      | 'PREDEF.10000.CELLTRACE' | ProcessType.NORMAL_PRIORITY_CELLTRACE | 0
            true      | 'PREDEF.10005.CELLTRACE' | ProcessType.HIGH_PRIORITY_CELLTRACE   | 0
            true      | 'PREDEF.30000.GPEH'      | ProcessType.REGULAR_GPEH              | 0
            true      | 'PREDEF.30001.GPEH'      | ProcessType.OPTIMIZER_GPEH            | 0
            true      | 'PREDEF.EBMLOG.EBM'      | ProcessType.EVENTJOB                  | 0
            true      | 'PREDEF.20000.CTR'       | ProcessType.CTR                       | 0
            true      | 'PREDEF.10000.UETR'      | ProcessType.UETR                      | 0
    }

    @Unroll
    def 'when pm function turns OFF, ebs scanners on the node will be suspended if Migration is OFF, pmicsubscannerinfo will be used to update pmfunctionerror node cache'() {
        given:
            createCellTraceSubscrption()
            def node = dps.node().name(NODE_NAME_1).neType('ERBS').ossModelIdentity('16B-G.1.281').pmEnabled(true).build()
            def scanner = createEbsScanner(NODE_NAME_1)
            dps.subScanner().subscriptionId(123456789L).fdn(buildPmicSubScannerInfoFdn(node, 'EbsSubcription1')).build()
            dps.subScanner().subscriptionId(987654321L).fdn(buildPmicSubScannerInfoFdn(node, 'EbsSubcription2')).build()
            dps.subScanner().subscriptionId(111222333L).fdn(buildPmicSubScannerInfoFdn(node, 'EbsSubcription3')).build()

        and: 'startup Recovery is done'
            startupRecoveryMonitor.startupRecoveryDone >> true

        and: 'pm Migration is ON / OFF'
            configurationChangeListener.pmMigrationEnabled >> migration

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(node.fdn, new PmFunctionData(true))

        when: 'DPS PM Function OFF notification is received'
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(false, node.fdn))

        then: 'scanner Suspension Request shoud be sent'
            expectedEventSent * eventSender.send({ task -> task.scannerId == scanner.poId as String } as ScannerSuspensionTaskRequest)

        where:
            migration | expectedEventSent
            false     | 1
            true      | 0
    }

    @Unroll
    def 'when pm function turns OFF, all active PmJobs on the node will be suspended if Migration is OFF'() {
        given:
            def node = dps.node().name('1').build()
            def sub = createSubscription(SubscriptionType.UETRACE)
            def sub1 = createSubscription(SubscriptionType.CTUM)
            dps.pmJob().nodeName(node).processType(ProcessType.UETRACE).subscriptionId(sub).status(PmJobStatus.ACTIVE).build()
            dps.pmJob().nodeName(node).processType(ProcessType.CTUM).subscriptionId(sub1).status(PmJobStatus.ACTIVE).build()

        and: 'Pm Migration is ON / OFF'
            configurationChangeListener.pmMigrationEnabled >> migration

        and: 'pmFunctionCache is populated'
            pmFunctionCache.addEntry(node.fdn, new PmFunctionData(true))

        when:
            dpspmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(false, node.fdn))

        then:
            expectedEventSent * deactivationEvent.execute(_ as List<Node>, _ as Subscription)

        where:
            migration | expectedEventSent
            false     | 2
            true      | 0
    }

    def createCellTraceSubscrption(name = 'Test', cellTraceCategory = CellTraceCategory.CELLTRACE) {
        subscriptionBuilder(SubscriptionType.CELLTRACE, name).cellTraceCategory(cellTraceCategory).build()
    }

    def createSubscription(type, name = 'Test') {
        subscriptionBuilder(type, name).build()
    }

    def subscriptionBuilder(type, name) {
        dps.subscription()
           .type(type)
           .name(name)
           .administrationState(AdministrationState.ACTIVE)
           .taskStatus(TaskStatus.ERROR)
    }

    def createAttributeChangeEvent(boolean newValue = true, String nodeFdn) {
        Collection<AttributeChangeData> attributeChangeData = [
                new AttributeChangeData('pmEnabled', !newValue, newValue, null, null)
        ]
        return new DpsAttributeChangedEvent(fdn: "${nodeFdn},PmFunction=1", changedAttributes: attributeChangeData)
    }

    def createEbsScanner(nodeName, name = EBS_CELLTRACE_SCANNER, status = ScannerStatus.ACTIVE) {
        createScanner(nodeName, name, ProcessType.HIGH_PRIORITY_CELLTRACE, 0L, status, false)
    }

    def createScanner(nodeName, name, processType, subscriptionId = 0L, status = ScannerStatus.ACTIVE, fileCollecitonEnabled = true) {
        dps.scanner()
           .nodeName(nodeName)
           .name(name)
           .processType(processType)
           .status(status)
           .subscriptionId(subscriptionId)
           .fileCollectionEnabled(fileCollecitonEnabled)
           .build()
    }

    def buildPmicSubScannerInfoFdn(ManagedObject node, String subscriptionName) {
        return node.fdn + COMMA + SCANNER_PREFIX + EBS_CELLTRACE_SCANNER + COMMA + 'PMICSubScannerInfo=' + subscriptionName;
    }
}
