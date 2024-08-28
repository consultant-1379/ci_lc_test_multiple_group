/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2017
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

import static com.ericsson.oss.pmic.cdi.test.util.Constants.NETWORK_ELEMENT_1

import javax.ejb.TimerService
import javax.inject.Inject

import spock.lang.Unroll

import org.mockito.Mockito

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.SpyImplementation
import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectCreatedEvent
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectDeletedEvent
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.api.cache.PmFunctionData
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.model.ned.pm.function.FileCollectionState
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState
import com.ericsson.oss.services.model.ned.pm.function.ScannerMasterState
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper
import com.ericsson.oss.services.pm.common.utils.PmFunctionConstants
import com.ericsson.oss.services.pm.config.task.factories.PmConfigTaskRequestFactory
import com.ericsson.oss.services.pm.initiation.notification.DpsPmEnabledUpdateNotificationListener
import com.ericsson.oss.services.pm.initiation.notification.DpsPmEnabledUpdateNotificationProcessor
import com.ericsson.oss.services.pm.initiation.notification.DpsPmFunctionCreatedNotificationListener
import com.ericsson.oss.services.pm.initiation.notification.DpsPmFunctionDeleteNotificationListener
import com.ericsson.oss.services.pm.initiation.notification.handlers.ResPmFunctionHelper
import com.ericsson.oss.services.pm.initiation.scanner.polling.ScannerPollingTaskSender
import com.ericsson.oss.services.pm.initiation.schedulers.DelayedPmFunctionUpdateProcessor
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener

class PmFunctionEventHandlingSpec extends SkeletonSpec {

    @Inject
    DpsPmEnabledUpdateNotificationListener dpsPmEnabledUpdateNotificationListener

    @Inject
    DpsPmFunctionCreatedNotificationListener dpsPmFunctionCreatedNotificationListener

    @Inject
    DpsPmFunctionDeleteNotificationListener dPSPmFunctionDeleteNotificationListener

    @Inject
    FileCollectionActiveTaskCacheWrapper fileCollectionActiveTasksCache

    @Inject
    PmFunctionEnabledWrapper pmFunctionCache

    @MockedImplementation
    TimerService timerService

    @MockedImplementation
    ScannerPollingTaskSender scannerPollingTaskSender

    @SpyImplementation
    PmConfigTaskRequestFactory pmConfigTaskRequestFactory

    @ImplementationInstance
    MembershipListener membershipListener = Mockito.mock(MembershipListener)

    @SpyImplementation
    DpsPmEnabledUpdateNotificationProcessor dpsPmEnabledUpdateNotificationProcessor

    @MockedImplementation
    ResPmFunctionHelper resPmFunctionHelper

    @ImplementationInstance
    DelayedPmFunctionUpdateProcessor delayedUpdateProcessor = [
            scheduleDelayedPmFunctionUpdateProcessor: { a, b, c -> dpsPmEnabledUpdateNotificationProcessor.processPmFunctionChange(a, b, c) }
    ] as DelayedPmFunctionUpdateProcessor

    private List<ManagedObject> nodes
    private ManagedObject subscriptionMO
    private static final String NE_ROUTER = 'NetworkElement=CORE66Router667501'

    def setup() {
        timerService.timers >> []
    }

    def 'when pm function update ON event is received before pmfunction object created and pmFunction cache is empty, pmFunction cache should be update with on value'() {
        given: 'pmEnabled Attribute change event is created'
            def attributeChangedEvent = createAttributeChangeEvent(true, NETWORK_ELEMENT_1)

        and: 'pmEnabled Object created event is created'
            def objectCreatedChangedEvent = createObjectCreatedEvent(NETWORK_ELEMENT_1)

        when: 'Update listener is called before Object created listener'
            dpsPmEnabledUpdateNotificationListener.onEvent(attributeChangedEvent)
            dpsPmFunctionCreatedNotificationListener.onEvent(objectCreatedChangedEvent)

        then: 'pmFunction cache contains entry'
            pmFunctionCache.containsFdn(NETWORK_ELEMENT_1)

        and: 'the value of the entry is true'
            pmFunctionCache.isPmFunctionEnabled(NETWORK_ELEMENT_1)
    }

    def 'when pm function has the entry set to false and created object event is received before pmfunction update event to true, pmFunction cache should have entry set to true'() {
        given: 'pmEnabled Object created event is created'
            def objectCreatedChangedEvent = createObjectCreatedEvent(NETWORK_ELEMENT_1)

        and: 'pmEnabled Attribute change event is created'
            def attributeChangedEvent = createAttributeChangeEvent(true, NETWORK_ELEMENT_1)

        and: 'pmFunction already contains the entry OFF'
            pmFunctionCache.addEntry(NETWORK_ELEMENT_1, false)

        when: 'Object created listener is called before Update listener'
            dpsPmFunctionCreatedNotificationListener.onEvent(objectCreatedChangedEvent)
            dpsPmEnabledUpdateNotificationListener.onEvent(attributeChangedEvent)

        then: 'pmFunction cache contains entry'
            pmFunctionCache.containsFdn(NETWORK_ELEMENT_1)

        and: 'the value of the entry is true'
            pmFunctionCache.isPmFunctionEnabled(NETWORK_ELEMENT_1)
    }

    def 'when pm function has the entry set to false and pmfunction update event to on is received before created object event, pmFunction cache should have entry set to true'() {
        given: 'pmEnabled Attribute change event is created'
            def attributeChangedEvent = createAttributeChangeEvent(true, NETWORK_ELEMENT_1)

        and: 'pmEnabled Object created event is created'
            def objectCreatedChangedEvent = createObjectCreatedEvent(NETWORK_ELEMENT_1)

        and: 'pmFunction already contains the entry OFF'
            pmFunctionCache.addEntry(NETWORK_ELEMENT_1, false)

        when: 'Update listener is called before Object created listener'
            dpsPmEnabledUpdateNotificationListener.onEvent(attributeChangedEvent)
            dpsPmFunctionCreatedNotificationListener.onEvent(objectCreatedChangedEvent)

        then: 'pmFunction cache contains entry'
            pmFunctionCache.containsFdn(NETWORK_ELEMENT_1)

        and: 'the value of the entry is true'
            pmFunctionCache.isPmFunctionEnabled(NETWORK_ELEMENT_1)
    }

    def 'when pm function has the entry set to true and pmfunction update event to false is received before delete object event, pmFunction cache should be empty'() {
        given: 'pmEnabled Attribute change event OFF is created'
            def attributeChangedEvent = createAttributeChangeEvent(false, NETWORK_ELEMENT_1)

        and: 'pmEnabled Object deleted event is created'
            def dpsObjectDeletedEvent = createObjectDeletedEvent(NETWORK_ELEMENT_1)

        and: 'pmFunction already contains the entry ON'
            pmFunctionCache.addEntry(NETWORK_ELEMENT_1, true)

        when: 'update listener is called before Delete listener'
            dpsPmEnabledUpdateNotificationListener.onEvent(attributeChangedEvent)
            dPSPmFunctionDeleteNotificationListener.onEvent(dpsObjectDeletedEvent)

        then: 'pmFunction cache does not contain entry'
            !pmFunctionCache.containsFdn(NETWORK_ELEMENT_1)
    }

    def 'when pm function has the entry set to true and delete object event is received before pmfunction update event, pmFunction cache should be empty but it is not'() {
        given: 'pmEnabled Object deleted event is created'
            def dpsObjectDeletedEvent = createObjectDeletedEvent(NETWORK_ELEMENT_1)
        and: 'pmEnabled Attribute change event OFF is created'
            def attributeChangedEvent = createAttributeChangeEvent(false, NETWORK_ELEMENT_1)

        and: 'pmFunction already contains the entry ON'
            pmFunctionCache.addEntry(NETWORK_ELEMENT_1, true)

        when: 'Delete listener is called before Object update listener'
            dPSPmFunctionDeleteNotificationListener.onEvent(dpsObjectDeletedEvent)
            dpsPmEnabledUpdateNotificationListener.onEvent(attributeChangedEvent)

        then: 'pmFunction cache contains entry. it should be empty but it is acceptable that entry is still there'
            pmFunctionCache.containsFdn(NETWORK_ELEMENT_1)
    }

    def 'when pmFunction is created at default, pmEnable is set to true, fileCollectionActiveTasksCache entry is correctly updated'() {
        given: 'A node with pmFunction on attached to one subscription'
            nodes = [nodeUtil.builder(NETWORK_ELEMENT_1).fdn(NETWORK_ELEMENT_1).build()]
            subscriptionMO = dps.subscription().type(subscriptionType).name('Test').administrationState(AdministrationState.ACTIVE).taskStatus(TaskStatus.OK).nodes(nodes).build()

        and: 'Scanners and PmJobs exist in DPS'
            addScanners(ScannerStatus.ACTIVE)
            if (subscriptionType != SubscriptionType.CELLTRACE) {
                addPmJobs(PmJobStatus.ACTIVE)
            }

        and:
            'instance is ' + mastership
            Mockito.when(membershipListener.master).thenReturn(mastership)

        when: 'create listener is called'
            dpsPmFunctionCreatedNotificationListener.onEvent(createObjectCreatedEvent(NETWORK_ELEMENT_1))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(NETWORK_ELEMENT_1, false, true, true, true)

        when: 'update listener is called to set pmEnabled to true and fileCollectionState to ENABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_1,
                    [(PmFunctionConstants.PM_ENABLED):
                             ['OLD_VALUE': false, 'NEW_VALUE': true]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(NETWORK_ELEMENT_1, true, true, true, true)

        and: 'sendScannerPollingTaskForNode should be invoked'
            (mastership ? 1 : 0) * scannerPollingTaskSender.sendScannerPollingTaskForNode(_ as String)

        and: 'fileCollectionActiveTasksCache entry should NOT be inserted'
            fileCollectionActiveTasksCache.processRequests.collect {
                it.nodeAddress
            } as Collection == []
            fileCollectionActiveTasksCache.size() == 0

        and: 'entry is not an error Entry'
            !fileCollectionActiveTasksCache.containsKey(NETWORK_ELEMENT_1)

        when: 'update listener is called to set fileCollectionState to DISABLED'
                dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_1,
                        [(PmFunctionConstants.FILE_COLLECTION_STATE):
                                 ['OLD_VALUE': FileCollectionState.ENABLED.name(), 'NEW_VALUE': FileCollectionState.DISABLED.name()]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(NETWORK_ELEMENT_1, true, false, true, true)

        and: 'sendScannerPollingTaskForNode should NOT be invoked'
            0 * scannerPollingTaskSender.sendScannerPollingTaskForNode(_ as String)

        and: 'fileCollectionActiveTasksCache entry should NOT be present'
            fileCollectionActiveTasksCache.processRequests.collect {
                it.nodeAddress
            } as Collection == []
            fileCollectionActiveTasksCache.size() == 0

        where:
            subscriptionType << [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE]
            mastership << [true, false]
    }

    def 'when pmFunction is created at default, and single changes occur fileCollectionActiveTasksCache entry is correctly updated'() {
        given: 'A node with pmFunction on attached to one subscription'
            nodes = [nodeUtil.builder(NETWORK_ELEMENT_1).fdn(NETWORK_ELEMENT_1).build()]
            subscriptionMO = dps.subscription().type(subscriptionType).name('Test').administrationState(AdministrationState.ACTIVE).taskStatus(TaskStatus.OK).nodes(nodes).build()

        and: 'Scanners and PmJobs exist in DPS'
            addScanners(ScannerStatus.ACTIVE)
            if (subscriptionType != SubscriptionType.CELLTRACE) {
                addPmJobs(PmJobStatus.ACTIVE)
            }

        and:
            'instance is ' + mastership
            Mockito.when(membershipListener.master).thenReturn(mastership)

        when: 'create listener is called'
            dpsPmFunctionCreatedNotificationListener.onEvent(createObjectCreatedEvent(NETWORK_ELEMENT_1))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(NETWORK_ELEMENT_1, false, true, true, true)

        when: 'update listener is called to set fileCollectionState to DISABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_1,
                    [(PmFunctionConstants.FILE_COLLECTION_STATE):
                             ['OLD_VALUE': FileCollectionState.ENABLED.name(),
                              'NEW_VALUE': FileCollectionState.DISABLED.name()]
                    ]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(NETWORK_ELEMENT_1, false, false, true, true)

        and: 'fileCollectionActiveTasksCache entry should NOT be inserted'
            fileCollectionActiveTasksCache.processRequests.collect {
                it.nodeAddress
            } as Collection == []
            fileCollectionActiveTasksCache.size() == 0

        when: 'update listener is called to set pmEnabled attribute to true'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_1,
                    [(PmFunctionConstants.PM_ENABLED): ['OLD_VALUE': false, 'NEW_VALUE': true]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(NETWORK_ELEMENT_1, true, false, true, true)

        and: 'sendScannerPollingTaskForNode should be invoked'
            (mastership ? 1 : 0) * scannerPollingTaskSender.sendScannerPollingTaskForNode(_ as String)

        and: 'fileCollectionActiveTasksCache entry NOT should be inserted'
            fileCollectionActiveTasksCache.processRequests.collect {
                it.nodeAddress
            } as Collection == []
            fileCollectionActiveTasksCache.size() == 0

        when: 'update listener is called to set fileCollectionState to ENABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_1,
                    [(PmFunctionConstants.FILE_COLLECTION_STATE):
                             ['OLD_VALUE': FileCollectionState.DISABLED.name(),
                              'NEW_VALUE': FileCollectionState.ENABLED.name()]
                    ]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(NETWORK_ELEMENT_1, true, true, true, true)

        and: 'fileCollectionActiveTasksCache entry should be inserted'
            fileCollectionActiveTasksCache.processRequests.collect {
                it.nodeAddress
            } as Collection == (mastership ? [NETWORK_ELEMENT_1] : [])
            fileCollectionActiveTasksCache.size() == (mastership ? 1 : 0)

        and: 'entry is not an error Entry'
            !fileCollectionActiveTasksCache.containsKey(NETWORK_ELEMENT_1)

        and: 'sendScannerPollingTaskForNode should NOT be invoked'
            0 * scannerPollingTaskSender.sendScannerPollingTaskForNode(_ as String)

        when: 'update listener is called to set fileCollectionState to ENABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_1,
                    [(PmFunctionConstants.FILE_COLLECTION_STATE):
                             ['OLD_VALUE': FileCollectionState.ENABLED.name(),
                              'NEW_VALUE': FileCollectionState.DISABLED.name()]
                    ]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(NETWORK_ELEMENT_1, true, false, true, true)

        and: 'fileCollectionActiveTasksCache entry should be removed'
            fileCollectionActiveTasksCache.processRequests.collect {
                it.nodeAddress
            } as Collection == []
            fileCollectionActiveTasksCache.size() == 0

        and: 'entry is not an error Entry'
            !fileCollectionActiveTasksCache.containsKey(NETWORK_ELEMENT_1)

        and: 'sendScannerPollingTaskForNode should NOT be invoked'
            0 * scannerPollingTaskSender.sendScannerPollingTaskForNode(_ as String)

        where:
            subscriptionType << [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE]
            mastership << [true, false]
    }

    def 'when pmFunction is created at default, pmEnabled is false and fileCollectionState switches from DISABLED to ENABLED no cache entry is inserted'() {
        given: 'A node with pmFunction on attached to one subscription'
            nodes = [nodeUtil.builder(NETWORK_ELEMENT_1).fdn(NETWORK_ELEMENT_1).pmEnabled(true).build()]
            subscriptionMO = dps.subscription().type(subscriptionType).name('Test').administrationState(AdministrationState.ACTIVE).taskStatus(TaskStatus.OK).nodes(nodes).build()

        and: 'Scanners and PmJobs exist in DPS'
            addScanners(ScannerStatus.ACTIVE)
            if (subscriptionType != SubscriptionType.CELLTRACE) {
                addPmJobs(PmJobStatus.ACTIVE)
            }

        and:
            'instance is ' + mastership
            Mockito.when(membershipListener.master).thenReturn(mastership)

        when: 'create listener is called'
            dpsPmFunctionCreatedNotificationListener.onEvent(createObjectCreatedEvent(NETWORK_ELEMENT_1))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(NETWORK_ELEMENT_1, false, true, true, true)

        when: 'update listener is called to set fileCollectionState to DISABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_1,
                    [(PmFunctionConstants.FILE_COLLECTION_STATE): ['OLD_VALUE': FileCollectionState.ENABLED.name(), 'NEW_VALUE': FileCollectionState.DISABLED.name()]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(NETWORK_ELEMENT_1, false, false, true, true)

        and: 'fileCollectionActiveTasksCache entry should NOT be inserted'
            fileCollectionActiveTasksCache.processRequests.collect {
                it.nodeAddress
            } as Collection == []
            fileCollectionActiveTasksCache.size() == 0

        and: 'no error entry is inserted'
            !fileCollectionActiveTasksCache.containsKey(NETWORK_ELEMENT_1)

        and: 'sendScannerPollingTaskForNode should NOT be invoked'
            0 * scannerPollingTaskSender.sendScannerPollingTaskForNode(_ as String)

        when: 'update listener is called to set fileCollectionState to ENABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_1,
                    [(PmFunctionConstants.PM_ENABLED)           : ['OLD_VALUE': false, 'NEW_VALUE': true],
                     (PmFunctionConstants.FILE_COLLECTION_STATE):
                             ['OLD_VALUE': FileCollectionState.DISABLED.name(), 'NEW_VALUE': FileCollectionState.ENABLED.name()]
                    ]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(NETWORK_ELEMENT_1, true, true, true, true)

        and: 'sendScannerPollingTaskForNode should be invoked'
            (mastership ? 1 : 0) * scannerPollingTaskSender.sendScannerPollingTaskForNode(_ as String)

        and: 'fileCollectionActiveTasksCache entry should NOT be inserted'
            fileCollectionActiveTasksCache.processRequests.collect {
                it.nodeAddress
            } as Collection == []
            fileCollectionActiveTasksCache.size() == 0

        and: 'no error entry is inserted'
            !fileCollectionActiveTasksCache.containsKey(NETWORK_ELEMENT_1)

        where:
            subscriptionType << [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE]
            mastership << [true, false]
    }

    def 'when pmFunction is created at default, when pmEnable is true and fileCollectionState is ENABLED and pmFunction switch to false, cache entry is inserted and updated to errorEntry'() {
        given: 'A node with pmFunction on attached to one subscription'
            nodes = [nodeUtil.builder(NETWORK_ELEMENT_1).fdn(NETWORK_ELEMENT_1).build()]
            subscriptionMO = dps.subscription().type(subscriptionType).name('Test').administrationState(AdministrationState.ACTIVE).taskStatus(TaskStatus.OK).nodes(nodes).build()

        and: 'Scanners exist in DPS'
            addScanners(ScannerStatus.ACTIVE)
            if (subscriptionType != SubscriptionType.CELLTRACE) {
                addPmJobs(PmJobStatus.ACTIVE)
            }

        and:
            'instance is ' + mastership
            Mockito.when(membershipListener.master).thenReturn(mastership)

        when: 'create listener is called'
           dpsPmFunctionCreatedNotificationListener.onEvent(createObjectCreatedEvent(NETWORK_ELEMENT_1))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(NETWORK_ELEMENT_1, false, true, true, true)

        when: 'update listener is called to set pmEnabled to true and fileCollectionState to ENABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_1,
                    [(PmFunctionConstants.PM_ENABLED): ['OLD_VALUE': false, 'NEW_VALUE': true]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(NETWORK_ELEMENT_1, true, true, true, true)

        and: 'sendScannerPollingTaskForNode should be invoked'
            (mastership ? 1 : 0) * scannerPollingTaskSender.sendScannerPollingTaskForNode(_ as String)

        and: 'fileCollectionActiveTasksCache entry should NOT be inserted'
            fileCollectionActiveTasksCache.processRequests.collect {
                it.nodeAddress
            } as Collection == []
            fileCollectionActiveTasksCache.size() == 0

        when: 'update listener is called to set pmEnabled to false'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_1,
                    [(PmFunctionConstants.PM_ENABLED): ['OLD_VALUE': true, 'NEW_VALUE': false]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(NETWORK_ELEMENT_1, false, true, true, true)

        and: 'fileCollectionActiveTasksCache errorEntry should be inserted'
            fileCollectionActiveTasksCache.containsKey(NETWORK_ELEMENT_1) == mastership

        and: 'fileCollectionActiveTasksCache entry should NOT be present'
            fileCollectionActiveTasksCache.processRequests.collect {
                it.nodeAddress
            } as Collection == []
            fileCollectionActiveTasksCache.size() == 0

        when: 'update listener is called to set fileCollectionState to DIABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(NETWORK_ELEMENT_1,
                    [(PmFunctionConstants.FILE_COLLECTION_STATE): ['OLD_VALUE': FileCollectionState.ENABLED.name(), 'NEW_VALUE': FileCollectionState.DISABLED.name()]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(NETWORK_ELEMENT_1, false, false, true, true)

        and: 'fileCollectionActiveTasksCache entry should remain an errorEntry'
            fileCollectionActiveTasksCache.containsKey(NETWORK_ELEMENT_1) == mastership

        and: 'fileCollectionActiveTasksCache entry should NOT be present'
            fileCollectionActiveTasksCache.processRequests.collect {
                it.nodeAddress
            } as Collection == []
            fileCollectionActiveTasksCache.size() == 0

        where:
            subscriptionType << [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE]
            mastership << [true, false]
    }

    @Unroll
    def 'on Mediation Autonomy node when mastership is #mastership, when pmFunction is created with pmEnabled true and fileCollectionStateState switches from DISABLED to ENABLED'() {
        given: 'A Mediation Autonomy node with pmFunction having pmEnabled true'
            nodes = [nodeUtil.builder(nodeFdn).fdn(nodeFdn).neType(neType).pmEnabled(true).fileCollectionState(FileCollectionState.DISABLED).build()]

        and: 'a subscription involving the node'
            subscriptionMO = dps.subscription().type(SubscriptionType.STATISTICAL).name('Test').administrationState(AdministrationState.ACTIVE).taskStatus(TaskStatus.OK).nodes(nodes).build()

        and: 'active scanners exist in DPS'
            addScanners(ScannerStatus.ACTIVE)

        and: 'mastership is #mastership'
            Mockito.when(membershipListener.master).thenReturn(mastership)

        when: 'create listener is called'
            dpsPmFunctionCreatedNotificationListener.onEvent(createObjectCreatedEvent(nodeFdn, true, false, true, true))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, false, true, true)

        when: 'update listener is called to set fileCollectionState to ENABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(nodeFdn,
                    [(PmFunctionConstants.FILE_COLLECTION_STATE): ['OLD_VALUE': FileCollectionState.DISABLED.name(), 'NEW_VALUE': FileCollectionState.ENABLED.name()]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, true, true)

        and: 'PmConfigTaskRequest MTR is #mtrState'
            (mastership ? 1 : 0) * pmConfigTaskRequestFactory.createFileCollectionConfigTask(_ as String, true)

        and: 'PmEnabledUpdateNotification is #pmEnabledUpdateNotificationState'
            if (mastership) {
                1 * dpsPmEnabledUpdateNotificationProcessor.processPmFunctionChange(nodeFdn, {
                    ((PmFunctionData) it).fileCollectionState == FileCollectionState.ENABLED
                }, { ((PmFunctionData) it).fileCollectionState == FileCollectionState.DISABLED })
            } else {
                0 * dpsPmEnabledUpdateNotificationProcessor.processPmFunctionChange(_, _, _)
            }

        where:
            mastership | nodeFdn   | neType       || mtrState   | pmEnabledUpdateNotificationState
            true       | NE_ROUTER | 'Router6675' || 'sent'     | 'processed'
            false      | NE_ROUTER | 'Router6675' || 'not sent' | 'not processed'
    }

    @Unroll
    def 'on Mediation Autonomy node when mastership is #mastership, when pmFunction is created with pmEnabled true and fileCollectionStateState switches from ENABLED to DISABLED'() {
        given: 'A Mediation Autonomy node with pmFunction having pmEnabled true'
            nodes = [nodeUtil.builder(nodeFdn).fdn(nodeFdn).neType(neType).pmEnabled(true).build()]

        and: 'a subscription involving the node'
            subscriptionMO = dps.subscription().type(SubscriptionType.STATISTICAL).name('Test').administrationState(AdministrationState.ACTIVE).taskStatus(TaskStatus.OK).nodes(nodes).build()

        and: 'active scanners exist in DPS'
            addScanners(ScannerStatus.ACTIVE)

        and: 'mastership is #mastership'
            Mockito.when(membershipListener.master).thenReturn(mastership)

        when: 'create listener is called'
            dpsPmFunctionCreatedNotificationListener.onEvent(createObjectCreatedEvent(nodeFdn, true, true, true, true))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, true, true)

        when: 'update listener is called to set fileCollectionState to DISABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(nodeFdn,
                    [(PmFunctionConstants.FILE_COLLECTION_STATE): ['OLD_VALUE': FileCollectionState.ENABLED.name(), 'NEW_VALUE': FileCollectionState.DISABLED.name()]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, false, true, true)

        and: 'PmConfigTaskRequest MTR is #mtrState'
            (mastership ? 1 : 0) * pmConfigTaskRequestFactory.createFileCollectionConfigTask(_ as String, false)

        and: 'PmEnabledUpdateNotification is #pmEnabledUpdateNotificationState'
            if (mastership) {
                1 * dpsPmEnabledUpdateNotificationProcessor.processPmFunctionChange(nodeFdn, {
                    ((PmFunctionData) it).fileCollectionState == FileCollectionState.DISABLED
                }, { ((PmFunctionData) it).fileCollectionState == FileCollectionState.ENABLED })
            } else {
                0 * dpsPmEnabledUpdateNotificationProcessor.processPmFunctionChange(_, _, _)
            }

        where:
            mastership | nodeFdn   | neType       || mtrState   | pmEnabledUpdateNotificationState
            true       | NE_ROUTER | 'Router6675' || 'sent'     | 'processed'
        false      | NE_ROUTER | 'Router6675' || 'not sent' | 'not processed'
    }

    @Unroll
    def 'on #type node when mastership is #mastership, when pmFunction is created with pmEnabled true and neConfigurationManagerState enabled and scannerMasterState switches from DISABLED to ENABLED'() {
        given: 'A #type node with pmFunction having pmEnabled true and scannerMasterState disabled'
            nodes = [nodeUtil.builder(nodeFdn).fdn(nodeFdn).neType(neType).pmEnabled(true).scannerMasterState(ScannerMasterState.DISABLED).build()]

        and: 'mastership is #mastership'
            Mockito.when(membershipListener.master).thenReturn(mastership)

        when: 'create listener is called'
            dpsPmFunctionCreatedNotificationListener.onEvent(createObjectCreatedEvent(nodeFdn, true, true, false, true))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, false, true)

        when: 'update listener is called to set scannerMasterState to ENABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(nodeFdn,
                    [(PmFunctionConstants.SCANNER_MASTER_STATE): ['OLD_VALUE': ScannerMasterState.DISABLED.name(), 'NEW_VALUE': ScannerMasterState.ENABLED.name()]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, true, true)

        and: '#mtrType MTR is #mtrState'
            (mastership ? 1 : 0) * (type == 'Mediation Autonomy' ? 0 : 1) * scannerPollingTaskSender.sendScannerPollingTaskForNode(_ as String)
            (mastership ? 1 : 0) * (type == 'Mediation Autonomy' ? 1 : 0) * pmConfigTaskRequestFactory.createScannerMasterConfigTask(_ as String, true)

        and: 'PmEnabledUpdateNotification is #pmEnabledUpdateNotificationState'
            if (mastership) {
                1 * dpsPmEnabledUpdateNotificationProcessor.processPmFunctionChange(nodeFdn, {
                    ((PmFunctionData) it).scannerMasterState == ScannerMasterState.ENABLED
                }, { ((PmFunctionData) it).scannerMasterState == ScannerMasterState.DISABLED })
            } else {
                0 * dpsPmEnabledUpdateNotificationProcessor.processPmFunctionChange(_, _, _)
            }

        where:
            mastership | nodeFdn           | neType       | type                     || mtrType               | mtrState   | pmEnabledUpdateNotificationState
            true       | NETWORK_ELEMENT_1 | 'ERBS'       | 'not Mediation Autonomy' || 'scanner polling'     | 'sent'     | 'processed'
            true       | NE_ROUTER         | 'Router6675' | 'Mediation Autonomy'     || 'PmConfigTaskRequest' | 'sent'     | 'processed'
            false      | NETWORK_ELEMENT_1 | 'ERBS'       | 'not Mediation Autonomy' || 'scanner polling'     | 'not sent' | 'not processed'
            false      | NE_ROUTER         | 'Router6675' | 'Mediation Autonomy'     || 'PmConfigTaskRequest' | 'not sent' | 'not processed'
    }

    @Unroll
    def 'on #type node when mastership is #mastership, when pmFunction is created with pmEnabled true and neConfigurationManagerState enabled and scannerMasterState switches from ENABLED to DISABLED'() {
        given: 'A #type node with pmFunction having pmEnabled true'
            nodes = [nodeUtil.builder(nodeFdn).fdn(nodeFdn).neType(neType).pmEnabled(true).build()]

        and: 'instance is #mastership'
            Mockito.when(membershipListener.master).thenReturn(mastership)

        when: 'create listener is called'
            dpsPmFunctionCreatedNotificationListener.onEvent(createObjectCreatedEvent(nodeFdn, true, true, true, true))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, true, true)

        when: 'update listener is called to set scannerMasterState to DISABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(nodeFdn,
                    [(PmFunctionConstants.SCANNER_MASTER_STATE): ['OLD_VALUE': ScannerMasterState.ENABLED.name(), 'NEW_VALUE': ScannerMasterState.DISABLED.name()]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, false, true)

        and: '#mtrType MTR is #mtrState'
            0 * scannerPollingTaskSender.sendScannerPollingTaskForNode(_ as String)
            (mastership ? 1 : 0) * (type == 'Mediation Autonomy' ? 1 : 0) * pmConfigTaskRequestFactory.createScannerMasterConfigTask(_ as String, false)

        and: 'PmEnabledUpdateNotification is #pmEnabledUpdateNotificationState'
            if (mastership) {
                1 * dpsPmEnabledUpdateNotificationProcessor.processPmFunctionChange(nodeFdn, {
                    ((PmFunctionData) it).scannerMasterState == ScannerMasterState.DISABLED
                }, { ((PmFunctionData) it).scannerMasterState == ScannerMasterState.ENABLED })
            } else {
                0 * dpsPmEnabledUpdateNotificationProcessor.processPmFunctionChange(_, _, _)
            }

        where:
            mastership | nodeFdn           | neType       | type                     || mtrType               | mtrState   | pmEnabledUpdateNotificationState
            true       | NETWORK_ELEMENT_1 | 'ERBS'       | 'not Mediation Autonomy' || 'scanner polling'     | 'not sent' | 'processed'
            true       | NE_ROUTER         | 'Router6675' | 'Mediation Autonomy'     || 'PmConfigTaskRequest' | 'sent'     | 'processed'
            false      | NETWORK_ELEMENT_1 | 'ERBS'       | 'not Mediation Autonomy' || 'scanner polling'     | 'not sent' | 'not processed'
            false      | NE_ROUTER         | 'Router6675' | 'Mediation Autonomy'     || 'PmConfigTaskRequest' | 'not sent' | 'not processed'
    }

    @Unroll
    def 'on #type node when mastership is #mastership, when pmFunction is created with pmEnabled true and neConfigurationManagerState disabled and scannerMasterState switches from DISABLED to ENABLED'() {
        given: 'A #type node with pmFunction having pmEnabled true and scannerMasterState disabled'
            nodes = [nodeUtil.builder(nodeFdn).fdn(nodeFdn).neType(neType).pmEnabled(true).scannerMasterState(ScannerMasterState.DISABLED).neConfigurationManagerState(NeConfigurationManagerState.DISABLED).build()]

        and: 'mastership is #mastership'
            Mockito.when(membershipListener.master).thenReturn(mastership)

        when: 'create listener is called'
            dpsPmFunctionCreatedNotificationListener.onEvent(createObjectCreatedEvent(nodeFdn, true, true, false, false))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, false, false)

        when: 'update listener is called to set scannerMasterState to ENABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(nodeFdn,
                    [(PmFunctionConstants.SCANNER_MASTER_STATE): ['OLD_VALUE': ScannerMasterState.DISABLED.name(), 'NEW_VALUE': ScannerMasterState.ENABLED.name()]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, true, false)

        and: '#mtrType MTR is #mtrState'
            (mastership ? 1 : 0) * (type == 'Mediation Autonomy' ? 0 : 1) * scannerPollingTaskSender.sendScannerPollingTaskForNode(_ as String)
            (mastership ? 1 : 0) * (type == 'Mediation Autonomy' ? 1 : 0) * pmConfigTaskRequestFactory.createScannerMasterConfigTask(_ as String, true)

        where:
            mastership | nodeFdn           | neType       | type                     || mtrType               | mtrState
            true       | NETWORK_ELEMENT_1 | 'ERBS'       | 'not Mediation Autonomy' || 'scanner polling'     | 'sent'
            true       | NE_ROUTER         | 'Router6675' | 'Mediation Autonomy'     || 'PmConfigTaskRequest' | 'sent'
            false      | NETWORK_ELEMENT_1 | 'ERBS'       | 'not Mediation Autonomy' || 'scanner polling'     | 'not sent'
            false      | NE_ROUTER         | 'Router6675' | 'Mediation Autonomy'     || 'PmConfigTaskRequest' | 'not sent'
    }

    @Unroll
    def 'on #type node when mastership is #mastership, when pmFunction is created with pmEnabled true and neConfigurationManagerState disabled and scannerMasterState switches from ENABLED to DISABLED'() {
        given: 'A #type node with pmFunction having pmEnabled true and neConfigurationManagerState disabled'
            nodes = [nodeUtil.builder(nodeFdn).fdn(nodeFdn).neType(neType).pmEnabled(true).neConfigurationManagerState(NeConfigurationManagerState.DISABLED).build()]

        and: 'mastership is #mastership'
            Mockito.when(membershipListener.master).thenReturn(mastership)

        when: 'create listener is called'
            dpsPmFunctionCreatedNotificationListener.onEvent(createObjectCreatedEvent(nodeFdn, true, true, true, false))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, true, false)

        when: 'update listener is called to set scannerMasterState to DISABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(nodeFdn,
                    [(PmFunctionConstants.SCANNER_MASTER_STATE): ['OLD_VALUE': ScannerMasterState.ENABLED.name(), 'NEW_VALUE': ScannerMasterState.DISABLED.name()]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, false, false)

        and: '#mtrType MTR is #mtrState'
            0 * scannerPollingTaskSender.sendScannerPollingTaskForNode(_ as String)
            (mastership ? 1 : 0) * (type == 'Mediation Autonomy' ? 1 : 0) * pmConfigTaskRequestFactory.createScannerMasterConfigTask(_ as String, false)

        where:
            mastership | nodeFdn           | neType       | type                     || mtrType               | mtrState
            true       | NETWORK_ELEMENT_1 | 'ERBS'       | 'not Mediation Autonomy' || 'scanner polling'     | 'not sent'
            true       | NE_ROUTER         | 'Router6675' | 'Mediation Autonomy'     || 'PmConfigTaskRequest' | 'sent'
            false      | NETWORK_ELEMENT_1 | 'ERBS'       | 'not Mediation Autonomy' || 'scanner polling'     | 'not sent'
            false      | NE_ROUTER         | 'Router6675' | 'Mediation Autonomy'     || 'PmConfigTaskRequest' | 'not sent'
    }

    @Unroll
    def 'when mastership is #mastership, when pmFunction is created with pmEnabled true and scannerMasterState enabled and neConfigurationManagerState switches from DISABLED to ENABLED'() {
        given: 'A node with pmFunction having pmEnabled true and neConfigurationManagerState disabled'
            nodes = [nodeUtil.builder(nodeFdn).fdn(nodeFdn).neType(neType).pmEnabled(true).neConfigurationManagerState(NeConfigurationManagerState.DISABLED).build()]

        and: 'mastership is #mastership'
            Mockito.when(membershipListener.master).thenReturn(mastership)

        when: 'create listener is called'
            dpsPmFunctionCreatedNotificationListener.onEvent(createObjectCreatedEvent(nodeFdn, true, true, true, false))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, true, false)

        when: 'update listener is called to set neConfigurationManagerState to ENABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(nodeFdn,
                    [(PmFunctionConstants.NE_CONFIGURATION_MANAGER_STATE): ['OLD_VALUE': NeConfigurationManagerState.DISABLED.name(), 'NEW_VALUE': NeConfigurationManagerState.ENABLED.name()]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, true, true)

        and: 'scanner Polling MTR is #mtrState'
            (mastership ? 1 : 0) * scannerPollingTaskSender.sendScannerPollingTaskForNode(_ as String)

        and: 'pmEnabledUpdateNotification is #pmEnabledUpdateNotificationState'
            interaction {
                if (mastership) {
                    processPmFunctionChangeIsCalledWithCorrectParameters(nodeFdn)
                } else {
                    processPmFunctionChangeIsNotCalled()
                }
            }

        where:
            mastership | nodeFdn           | neType || mtrState   | pmEnabledUpdateNotificationState
            true       | NETWORK_ELEMENT_1 | 'ERBS' || 'sent'     | 'processed'
            false      | NETWORK_ELEMENT_1 | 'ERBS' || 'not sent' | 'not processed'
    }

    @Unroll
    def 'when mastership is #mastership, when pmFunction is created with pmEnabled true and scannerMasterState enabled and neConfigurationManagerState switches from ENABLED to DISABLED, scanner polling MTR is not sent'() {
        given: 'A #type node with pmFunction having pmEnabled true'
            nodes = [nodeUtil.builder(nodeFdn).fdn(nodeFdn).neType(neType).pmEnabled(true).build()]

        and: 'mastership is #mastership'
            Mockito.when(membershipListener.master).thenReturn(mastership)

        when: 'create listener is called'
            dpsPmFunctionCreatedNotificationListener.onEvent(createObjectCreatedEvent(nodeFdn, true, true, true, true))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, true, true)

        when: 'update listener is called to set neConfigurationManagerState to DISABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(nodeFdn,
                    [(PmFunctionConstants.NE_CONFIGURATION_MANAGER_STATE): ['OLD_VALUE': NeConfigurationManagerState.ENABLED.name(), 'NEW_VALUE': NeConfigurationManagerState.DISABLED.name()]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, true, false)

        and: 'scanner polling MTR is not sent'
            0 * scannerPollingTaskSender.sendScannerPollingTaskForNode(_ as String)

        where:
            mastership | nodeFdn           | neType
            true       | NETWORK_ELEMENT_1 | 'ERBS'
            false      | NETWORK_ELEMENT_1 | 'ERBS'
    }

    @Unroll
    def 'when mastership is #mastership, when pmFunction is created with pmEnabled true and scannerMasterState disabled and neConfigurationManagerState switches from DISABLED to ENABLED'() {
        given: 'A node with pmFunction having pmEnabled true, scannerMasterState disabled and neConfigurationManagerState disabled'
            nodes = [nodeUtil.builder(nodeFdn).fdn(nodeFdn).neType(neType).pmEnabled(true).scannerMasterState(ScannerMasterState.DISABLED).neConfigurationManagerState(NeConfigurationManagerState.DISABLED).build()]

        and: 'mastership is #mastership'
            Mockito.when(membershipListener.master).thenReturn(mastership)

        when: 'create listener is called'
            dpsPmFunctionCreatedNotificationListener.onEvent(createObjectCreatedEvent(nodeFdn, true, true, false, false))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, false, false)

        when: 'update listener is called to set neConfigurationManagerState to ENABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(nodeFdn,
                    [(PmFunctionConstants.NE_CONFIGURATION_MANAGER_STATE): ['OLD_VALUE': NeConfigurationManagerState.DISABLED.name(), 'NEW_VALUE': NeConfigurationManagerState.ENABLED.name()]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, false, true)

        and: 'Scanner Polling MTR is #mtrState'
            (mastership ? 1 : 0) * scannerPollingTaskSender.sendScannerPollingTaskForNode(_ as String)

        and: 'pmEnabledUpdateNotificationState is #pmEnabledUpdateNotificationState'
            interaction {
                if (mastership) {
                    processPmFunctionChangeIsCalledWithCorrectParameters(nodeFdn)
                } else {
                    processPmFunctionChangeIsNotCalled()
                }
            }

        where:
            mastership | nodeFdn           | neType || mtrState   | pmEnabledUpdateNotificationState
            true       | NETWORK_ELEMENT_1 | 'ERBS' || 'sent'     | 'processed'
            false      | NETWORK_ELEMENT_1 | 'ERBS' || 'not sent' | 'not processed'
    }

    @Unroll
    def 'when mastership is #mastership, when pmFunction is created with pmEnabled true and scannerMasterState disabled and neConfigurationManagerState switches from ENABLED to DISABLED, scanner polling MTR is not sent'() {
        given: 'A node with pmFunction having pmEnabled true'
            nodes = [nodeUtil.builder(nodeFdn).fdn(nodeFdn).neType(neType).pmEnabled(true).scannerMasterState(ScannerMasterState.DISABLED).build()]

        and: 'instance is #mastership'
            Mockito.when(membershipListener.master).thenReturn(mastership)

        when: 'create listener is called'
            dpsPmFunctionCreatedNotificationListener.onEvent(createObjectCreatedEvent(nodeFdn, true, true, false, true))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, false, true)

        when: 'update listener is called to set neConfigurationManagerState to DISABLED'
            dpsPmEnabledUpdateNotificationListener.onEvent(createAttributeChangeEvent(nodeFdn,
                    [(PmFunctionConstants.NE_CONFIGURATION_MANAGER_STATE): ['OLD_VALUE': NeConfigurationManagerState.ENABLED.name(), 'NEW_VALUE': NeConfigurationManagerState.DISABLED.name()]]))

        then: 'pmFunctionCache is updated'
            isPmFunctionCacheUpdated(nodeFdn, true, true, false, false)

        and: 'scannerPolling MTR is not sent'
            0 * scannerPollingTaskSender.sendScannerPollingTaskForNode(_ as String)

        where:
            mastership | nodeFdn           | neType
            true       | NETWORK_ELEMENT_1 | 'ERBS'
            false      | NETWORK_ELEMENT_1 | 'ERBS'
    }

    static DpsAttributeChangedEvent createAttributeChangeEvent(final boolean newValue, final String nodeFdn) {
        final Collection<AttributeChangeData> attributeChangeData = [
                new AttributeChangeData('pmEnabled', !newValue, newValue, null, null)
        ]
        return new DpsAttributeChangedEvent(fdn: "$nodeFdn,PmFunction=1", changedAttributes: attributeChangeData)
    }

    static DpsAttributeChangedEvent createAttributeChangeEvent(final String nodeFdn, final Map<String, Map<String, Object>> attributes) {
        def attributeChangeData = []
        attributes.each { key, value ->
            attributeChangeData.add(new AttributeChangeData(key, value.get('OLD_VALUE'), value.get('NEW_VALUE'), null, null))
        }
        return new DpsAttributeChangedEvent(fdn: "$nodeFdn,PmFunction=1", changedAttributes: attributeChangeData)
    }

    private static DpsObjectCreatedEvent createObjectCreatedEvent(final String nodeFdn) {
        return createObjectCreatedEvent(nodeFdn, false, true, true, true)
    }

    private static DpsObjectCreatedEvent createObjectCreatedEvent(final String nodeFdn, final boolean isPmEnabled,
                                                                  final boolean isFileCollectionStateEnabled, final boolean isScannerMasterStateEnabled, final boolean isNeConfigurationManagerStateEnabled) {
        def attributes = ['pmEnabled': isPmEnabled] as Map<String, Object>
        if (isFileCollectionStateEnabled) {
            attributes.put('fileCollectionState', FileCollectionState.ENABLED.name())
        } else {
            attributes.put('fileCollectionState', FileCollectionState.DISABLED.name())
        }
        if (isScannerMasterStateEnabled) {
            attributes.put('scannerMasterState', ScannerMasterState.ENABLED.name())
        } else {
            attributes.put('scannerMasterState', ScannerMasterState.DISABLED.name())
        }
        if (isNeConfigurationManagerStateEnabled) {
            attributes.put('neConfigurationManagerState', NeConfigurationManagerState.ENABLED.name())
        } else {
            attributes.put('neConfigurationManagerState', NeConfigurationManagerState.DISABLED.name())
        }
        return createObjectCreatedEvent(nodeFdn, attributes)
    }

    private static DpsObjectCreatedEvent createObjectCreatedEvent(final String nodeFdn, final Map<String, Object> attributes) {
        return new DpsObjectCreatedEvent('namespace', 'type', 'version', 123L, nodeFdn + ',PmFunction=1', 'theBucketName', true, attributes)
    }

    private static DpsObjectDeletedEvent createObjectDeletedEvent(final String nodeFdn) {
        return new DpsObjectDeletedEvent('namespace', 'type', 'version', 123L, nodeFdn + ',PmFunction=1', 'theBucketName', false, null)
    }

    boolean isPmFunctionCacheUpdated(final String nodeFdn, final boolean isPmEnabled,
                                     final boolean isFileCollectionStateEnabled, final boolean isScannerMasterStateEnabled, final boolean isNeConfigurationManagerStateEnabled) {
        return pmFunctionCache.containsFdn(nodeFdn) &&
                pmFunctionCache.isPmFunctionEnabled(nodeFdn) == isPmEnabled &&
                pmFunctionCache.isFileCollectionStateEnabled(nodeFdn) == isFileCollectionStateEnabled &&
                pmFunctionCache.isScannerMasterStateEnabled(nodeFdn) == isScannerMasterStateEnabled &&
                pmFunctionCache.isNeConfigurationManagerStateEnabled(nodeFdn) == isNeConfigurationManagerStateEnabled
    }

    def addScanners(ScannerStatus status) {
        ProcessType processType = getSubscriptionTypeProcessTypeMap().get(SubscriptionType.valueOf(subscriptionMO.getAttribute('type') as String))
        nodes.each {
            String scannerName = getProcessTypeScannerNameMap().get(processType)
            scannerUtil.builder(scannerName, it.name).subscriptionId(subscriptionMO.getPoId()).status(status).processType(processType).build()
        }
    }

    def addPmJobs(PmJobStatus status) {
        ProcessType processType = getSubscriptionTypeProcessTypeMap().get(SubscriptionType.valueOf(subscriptionMO.getAttribute('type') as String))
        nodes.each {
            dps.pmJob().nodeName(it).processType(processType).subscriptionId(subscriptionMO).status(status).build()
        }
    }

    def processPmFunctionChangeIsCalledWithCorrectParameters(nodeFdn) {
        1 * dpsPmEnabledUpdateNotificationProcessor.processPmFunctionChange(nodeFdn, {
            ((PmFunctionData) it).neConfigurationManagerState == NeConfigurationManagerState.ENABLED
        }, { ((PmFunctionData) it).neConfigurationManagerState == NeConfigurationManagerState.DISABLED })
    }

    def processPmFunctionChangeIsNotCalled() {
        0 * dpsPmEnabledUpdateNotificationProcessor.processPmFunctionChange(_, _, _)
    }

}
