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

package com.ericsson.oss.services.pm.initiation.notification

import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_PERSISTENCE_TIME
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionOperationConstant.SCANNER_MODEL_NAME
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionOperationConstant.SCANNER_UE_MODEL_NAME
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.CTR
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.CTUM
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.EVENTJOB
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.HIGH_PRIORITY_CELLTRACE
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.NORMAL_PRIORITY_CELLTRACE
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.REGULAR_GPEH
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.STATS
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.UETR
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.UETRACE
import static com.ericsson.oss.services.pm.initiation.common.Constants.PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME

import javax.ejb.Timer
import javax.ejb.TimerConfig
import javax.ejb.TimerService
import javax.inject.Inject
import org.mockito.Mockito
import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.CctrSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.CellTraceSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.CellTrafficSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.CtumSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.EbmSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.GpehSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.StatisticalSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.SubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.UeTraceSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.UetrSubscriptionBuilder
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.initiation.events.PmicSubscriptionUpdate
import com.ericsson.oss.services.pm.initiation.notification.events.ActivationEvent
import com.ericsson.oss.services.pm.initiation.notification.model.InitiationScheduleModel
import com.ericsson.oss.services.pm.scheduling.impl.InitiationSchedulerServiceBean

class DpsNotificationListenerForInitiationSpec extends PmServiceEjbSkeletonSpec {

    private static final NODE_NAME_1 = "SGSN-16A-V1-CP02201"
    private static final NODE_NAME_2 = "SGSN-16A-V1-CP02202"
    private static final long PO_ID = 1l

    @ImplementationInstance
    protected TimerService timerService = Mock(TimerService)

    @ObjectUnderTest
    DpsNotificationListenerForInitiation dpsNotificationListenerForInitiation

    @Inject
    @Modeled
    private EventSender<MediationTaskRequest> eventSender

    @Inject
    @Modeled
    private EventSender<PmicSubscriptionUpdate> pmicSubscriptionUpdateEventSender;

    @Inject
    private InitiationSchedulerServiceBean initiationSchedulerServiceBean

    @Inject
    private ActivationEvent activationEvent
    @ImplementationInstance
    TimeGenerator timegenerator = Mockito.mock(TimeGenerator)

    private List<ManagedObject> nodes

    private ManagedObject subscriptionMO

    def setup() {
        timerService.getTimers() >> []
        Mockito.when(timegenerator.currentTimeMillis()).thenReturn(System.currentTimeMillis())
    }

    @Unroll
    def "when administration state is changed from INACTIVE to ACTIVATING for #subscriptionType, should not process events that are not subscription #type and should not create timer"() {
        given: "Stats subscription,nodes and scanners exists in dps "
        Date startTime = getStartTime()
        SubscriptionBuilder subscriptionBuilder = builder.newInstance(dpsUtils)
        subscriptionMO = addSubscription(subscriptionBuilder, AdministrationState.ACTIVATING, startTime)
        nodes = addNodes()
        and: "Attribute Change Event is created"
        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChangesForNonPmicSubscriptionType(type, nameSpace, attributeName, oldValue, newValue)
        when: "Listener receives Attribute Change Event"
        boolean eventRouted = dpsNotificationListenerForInitiation.isInterested(attributeChangedEvent)
        dpsNotificationListenerForInitiation.onEvent(attributeChangedEvent)
        then: " Activating timer is created  "
        eventRouted == false
        0 * timerService.createSingleActionTimer(_ as Date, { TimerConfig timerConfig -> timerConfig.info.toString().contains("eventType=ACTIVATING") } as TimerConfig)
        where:
        builder                              | subscriptionType                   | type                 | nameSpace           | attributeName | oldValue   | newValue
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | "CmFunction"         | "OSS_NE_CM_DEF"     | "syncStatus"  | "TOPOLOGY" | "SYNCHRONIZED"
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | "CmFunction"         | "OSS_NE_CM_DEF"     | "syncStatus"  | "TOPOLOGY" | "SYNCHRONIZED"
        StatisticalSubscriptionBuilder.class | 'Statistical Subscription'         | "CmFunction"         | "OSS_NE_CM_DEF"     | "syncStatus"  | "TOPOLOGY" | "SYNCHRONIZED"
        CctrSubscriptionBuilder.class        | 'ContinuousCellTrace Subscription' | "CmFunction"         | "OSS_NE_CM_DEF"     | "syncStatus"  | "TOPOLOGY" | "SYNCHRONIZED"
        EbmSubscriptionBuilder.class         | 'Ebm Subscription'                 | "CmFunction"         | "OSS_NE_CM_DEF"     | "syncStatus"  | "TOPOLOGY" | "SYNCHRONIZED"
        CellTrafficSubscriptionBuilder.class | 'CellTraffic Subscription'         | "CmFunction"         | "OSS_NE_CM_DEF"     | "syncStatus"  | "TOPOLOGY" | "SYNCHRONIZED"
        UetrSubscriptionBuilder.class        | 'Uetr Subscription'                | "CmFunction"         | "OSS_NE_CM_DEF"     | "syncStatus"  | "TOPOLOGY" | "SYNCHRONIZED"
        GpehSubscriptionBuilder.class        | 'Gpeh Subscription'                | "CmFunction"         | "OSS_NE_CM_DEF"     | "syncStatus"  | "TOPOLOGY" | "SYNCHRONIZED"
        UeTraceSubscriptionBuilder.class     | 'UeTrace Subscription'             | "CmFunction"         | "OSS_NE_CM_DEF"     | "syncStatus"  | "TOPOLOGY" | "SYNCHRONIZED"
        CtumSubscriptionBuilder.class        | 'Ctum Subcscription'               | "CmFunction"         | "OSS_NE_CM_DEF"     | "syncStatus"  | "TOPOLOGY" | "SYNCHRONIZED"
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | "PMICScannerInfo"    | "pmic_subscription" | "status"      | "ACTIVE"   | "UNKNOWN"
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | "PMICSubScannerInfo" | "pmic_subscription" | "status"      | "ACTIVE"   | "UNKNOWN"
        StatisticalSubscriptionBuilder.class | 'Statistical Subscription'         | "PMICScannerInfo"    | "pmic_subscription" | "status"      | "ACTIVE"   | "UNKNOWN"
        CctrSubscriptionBuilder.class        | 'ContinuousCellTrace Subscription' | "PMICScannerInfo"    | "pmic_subscription" | "status"      | "ACTIVE"   | "UNKNOWN"
        EbmSubscriptionBuilder.class         | 'Ebm Subscription'                 | "PMICScannerInfo"    | "pmic_subscription" | "status"      | "ACTIVE"   | "UNKNOWN"
        CellTrafficSubscriptionBuilder.class | 'CellTraffic Subscription'         | "PMICScannerInfo"    | "pmic_subscription" | "status"      | "ACTIVE"   | "UNKNOWN"
        UetrSubscriptionBuilder.class        | 'Uetr Subscription'                | "PMICScannerInfo"    | "pmic_subscription" | "status"      | "ACTIVE"   | "UNKNOWN"
        GpehSubscriptionBuilder.class        | 'Gpeh Subscription'                | "PMICScannerInfo"    | "pmic_subscription" | "status"      | "ACTIVE"   | "UNKNOWN"
        UeTraceSubscriptionBuilder.class     | 'UeTrace Subscription'             | "PMICUeScannerInfo"  | "pmic_subscription" | "status"      | "ACTIVE"   | "UNKNOWN"
        CtumSubscriptionBuilder.class        | 'Ctum Subcscription'               | "PMICScannerInfo"    | "pmic_subscription" | "status"      | "ACTIVE"   | "UNKNOWN"
    }

    @Unroll
    def "when administration state is changed from INACTIVE to ACTIVATING for #subscriptionType, should not process invalid event type and should not create timer"() {
        given: "Stats subscription,nodes and scanners exists in dps "
        Date startTime = getStartTime()
        SubscriptionBuilder subscriptionBuilder = builder.newInstance(dpsUtils)
        subscriptionMO = addSubscription(subscriptionBuilder, AdministrationState.ACTIVATING, startTime)
        nodes = addNodes()
        (processType in [UETRACE, CTUM]) ? createPmicJobInfo(subscriptionMO, nodes) : dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanners(subscriptionMO.getPoId(), nodes, processType, scannerName, type)
        and: "Attribute Change Event is created"
        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChangesForNonPmicSubscriptionType()
        when: "Listener receives Attribute Change Event"
        boolean eventRouted = dpsNotificationListenerForInitiation.isInterested(attributeChangedEvent)
        dpsNotificationListenerForInitiation.onEvent(attributeChangedEvent)
        then: " Activating timer is created  "
        eventRouted == false
        0 * timerService.createSingleActionTimer(_ as Date, { TimerConfig timerConfig -> timerConfig.info.toString().contains("eventType=ACTIVATING") } as TimerConfig)
        where:
        builder                              | subscriptionType                   | processType               | scannerName                 | type
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'    | SCANNER_MODEL_NAME
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10004.CELLTRACE'    | SCANNER_MODEL_NAME
        StatisticalSubscriptionBuilder.class | 'Statistical Subscription'         | STATS                     | 'USERDEF-Test.Cont.Y.STATS' | SCANNER_MODEL_NAME
        CctrSubscriptionBuilder.class        | 'ContinuousCellTrace Subscription' | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10005.CELLTRACE'    | SCANNER_MODEL_NAME
        EbmSubscriptionBuilder.class         | 'Ebm Subscription'                 | EVENTJOB                  | 'PREDEF.EBMLOG.EBM'         | SCANNER_MODEL_NAME
        CellTrafficSubscriptionBuilder.class | 'CellTraffic Subscription'         | CTR                       | 'PREDEF.20000.CTR'          | SCANNER_MODEL_NAME
        UetrSubscriptionBuilder.class        | 'Uetr Subscription'                | UETR                      | 'PREDEF.10000.UETR'         | SCANNER_UE_MODEL_NAME
        GpehSubscriptionBuilder.class        | 'Gpeh Subscription'                | REGULAR_GPEH              | 'PREDEF.30000.GPEH'         | SCANNER_MODEL_NAME
        UeTraceSubscriptionBuilder.class     | 'UeTrace Subscription'             | UETRACE                   | ''                          | SCANNER_MODEL_NAME
        CtumSubscriptionBuilder.class        | 'Ctum Subcscription'               | CTUM                      | ''                          | SCANNER_MODEL_NAME
    }

    @Unroll
    def "when administration state is changed from INACTIVE to SCHEDULED for #subscriptionType, should create one activating timer and one deactivating timer "() {
        given: "Stats subscription,nodes and scanners exists in dps"
        def (Date startTime, Date endTime) = getStartAndEndTime(30)
        SubscriptionBuilder subscriptionBuilder = builder.newInstance(dpsUtils)
        subscriptionMO = addSubscription(subscriptionBuilder, AdministrationState.SCHEDULED, startTime, endTime)
        nodes = addNodes()
        (processType in [UETRACE, CTUM]) ? createPmicJobInfo(subscriptionMO, nodes) : dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanners(subscriptionMO.getPoId(), nodes, processType, scannerName, type)
        and: "Attribute Change Event is created"
        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChanges(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, "INACTIVE", "SCHEDULED")
        attributeChangedEvent.setPoId(PO_ID)
        when: "Listener receives Attribute Change Event"
        boolean eventRouted = dpsNotificationListenerForInitiation.isInterested(attributeChangedEvent)
        dpsNotificationListenerForInitiation.onEvent(attributeChangedEvent)
        then: "timers should be created for activation and deactivation "
        eventRouted == true
        1 * timerService.createSingleActionTimer(_ as Date, { TimerConfig timerConfig -> timerConfig.info.toString().contains("eventType=ACTIVATING") } as TimerConfig)
        1 * timerService.createSingleActionTimer(_ as Date, { TimerConfig timerConfig -> timerConfig.info.toString().contains("eventType=DEACTIVATING") } as TimerConfig)

        where:
        builder                              | subscriptionType                   | processType               | scannerName                 | type
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'    | SCANNER_MODEL_NAME
        StatisticalSubscriptionBuilder.class | 'Statistical Subscription'         | STATS                     | 'USERDEF-Test.Cont.Y.STATS' | SCANNER_MODEL_NAME
        CctrSubscriptionBuilder.class        | 'ContinuousCellTrace Subscription' | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10005.CELLTRACE'    | SCANNER_MODEL_NAME
        EbmSubscriptionBuilder.class         | 'Ebm Subscription'                 | EVENTJOB                  | 'PREDEF.EBMLOG.EBM'         | SCANNER_MODEL_NAME
        CellTrafficSubscriptionBuilder.class | 'CellTraffic Subscription'         | CTR                       | 'PREDEF.20000.CTR'          | SCANNER_MODEL_NAME
        UetrSubscriptionBuilder.class        | 'Uetr Subscription'                | UETR                      | 'PREDEF.10000.UETR'         | SCANNER_UE_MODEL_NAME
        GpehSubscriptionBuilder.class        | 'Gpeh Subscription'                | REGULAR_GPEH              | 'PREDEF.30000.GPEH'         | SCANNER_MODEL_NAME
        UeTraceSubscriptionBuilder.class     | 'UeTrace Subscription'             | UETRACE                   | ''                          | SCANNER_MODEL_NAME
        CtumSubscriptionBuilder.class        | 'Ctum Subcscription'               | CTUM                      | ''                          | SCANNER_MODEL_NAME
    }

    @Unroll
    def "when administration state is changed from INACTIVE to SCHEDULED for #subscriptionType with only start time, should create activating timer  only"() {
        given: "Stats subscription,nodes and scanners exists in dps"
        Date startTime = getStartTime()
        SubscriptionBuilder subscriptionBuilder = builder.newInstance(dpsUtils)
        subscriptionMO = addSubscription(subscriptionBuilder, AdministrationState.SCHEDULED, startTime)
        nodes = addNodes()
        (processType in [UETRACE, CTUM]) ? createPmicJobInfo(subscriptionMO, nodes) : dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanners(subscriptionMO.getPoId(), nodes, processType, scannerName, type)
        and: "Attribute Change Event is created"

        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChanges(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, "INACTIVE", "SCHEDULED")
        attributeChangedEvent.setPoId(PO_ID)
        when: "Listener receives Attribute Change Event"
        boolean eventRouted = dpsNotificationListenerForInitiation.isInterested(attributeChangedEvent)
        dpsNotificationListenerForInitiation.onEvent(attributeChangedEvent)
        then: "timer should be created for activation with no deactivation timer "
        eventRouted == true
        1 * timerService.createSingleActionTimer(_ as Date, { TimerConfig timerConfig -> timerConfig.info.toString().contains("eventType=ACTIVATING") } as TimerConfig)
        0 * timerService.createSingleActionTimer(_ as Date, { TimerConfig timerConfig -> timerConfig.info.toString().contains("eventType=DEACTIVATING") } as TimerConfig)

        where:
        builder                              | subscriptionType                   | processType               | scannerName                 | type
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'    | SCANNER_MODEL_NAME
        StatisticalSubscriptionBuilder.class | 'Statistical Subscription'         | STATS                     | 'USERDEF-Test.Cont.Y.STATS' | SCANNER_MODEL_NAME
        CctrSubscriptionBuilder.class        | 'ContinuousCellTrace Subscription' | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10005.CELLTRACE'    | SCANNER_MODEL_NAME
        EbmSubscriptionBuilder.class         | 'Ebm Subscription'                 | EVENTJOB                  | 'PREDEF.EBMLOG.EBM'         | SCANNER_MODEL_NAME
        CellTrafficSubscriptionBuilder.class | 'CellTraffic Subscription'         | CTR                       | 'PREDEF.20000.CTR'          | SCANNER_MODEL_NAME
        UetrSubscriptionBuilder.class        | 'Uetr Subscription'                | UETR                      | 'PREDEF.10000.UETR'         | SCANNER_UE_MODEL_NAME
        GpehSubscriptionBuilder.class        | 'Gpeh Subscription'                | REGULAR_GPEH              | 'PREDEF.30000.GPEH'         | SCANNER_MODEL_NAME
        UeTraceSubscriptionBuilder.class     | 'UeTrace Subscription'             | UETRACE                   | ''                          | SCANNER_MODEL_NAME
        CtumSubscriptionBuilder.class        | 'Ctum Subcscription'               | CTUM                      | ''                          | SCANNER_MODEL_NAME
    }

    @Unroll
    def "when administration state is changed from INACTIVE to ACTIVATING for #subscriptionType, should create one Activation timer  "() {
        given: "Stats subscription,nodes and scanners exists in dps "
        Date startTime = getStartTime()
        SubscriptionBuilder subscriptionBuilder = builder.newInstance(dpsUtils)
        subscriptionMO = addSubscription(subscriptionBuilder, AdministrationState.ACTIVATING, startTime)
        nodes = addNodes()
        (processType in [UETRACE, CTUM]) ? createPmicJobInfo(subscriptionMO, nodes) : dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanners(subscriptionMO.getPoId(), nodes, processType, scannerName, type)
        and: "Attribute Change Event is created"
        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChanges(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, "INACTIVE", "ACTIVATING")
        attributeChangedEvent.setPoId(PO_ID)
        when: "Listener receives Attribute Change Event"
        boolean eventRouted = dpsNotificationListenerForInitiation.isInterested(attributeChangedEvent)
        dpsNotificationListenerForInitiation.onEvent(attributeChangedEvent)
        then: " Activating timer is created  "
        eventRouted == true
        1 * timerService.createSingleActionTimer(_ as Date, { TimerConfig timerConfig -> timerConfig.info.toString().contains("eventType=ACTIVATING") } as TimerConfig)

        where:
        builder                              | subscriptionType                   | processType               | scannerName                 | type
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'    | SCANNER_MODEL_NAME
        StatisticalSubscriptionBuilder.class | 'Statistical Subscription'         | STATS                     | 'USERDEF-Test.Cont.Y.STATS' | SCANNER_MODEL_NAME
        CctrSubscriptionBuilder.class        | 'ContinuousCellTrace Subscription' | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10005.CELLTRACE'    | SCANNER_MODEL_NAME
        EbmSubscriptionBuilder.class         | 'Ebm Subscription'                 | EVENTJOB                  | 'PREDEF.EBMLOG.EBM'         | SCANNER_MODEL_NAME
        CellTrafficSubscriptionBuilder.class | 'CellTraffic Subscription'         | CTR                       | 'PREDEF.20000.CTR'          | SCANNER_MODEL_NAME
        UetrSubscriptionBuilder.class        | 'Uetr Subscription'                | UETR                      | 'PREDEF.10000.UETR'         | SCANNER_UE_MODEL_NAME
        GpehSubscriptionBuilder.class        | 'Gpeh Subscription'                | REGULAR_GPEH              | 'PREDEF.30000.GPEH'         | SCANNER_MODEL_NAME
        UeTraceSubscriptionBuilder.class     | 'UeTrace Subscription'             | UETRACE                   | ''                          | SCANNER_MODEL_NAME
        CtumSubscriptionBuilder.class        | 'Ctum Subcscription'               | CTUM                      | ''                          | SCANNER_MODEL_NAME
    }

    @Unroll
    def "when administration state is changed from ACTIVE to DEACTIVATING for #subscriptionType, should cancel the deactivation timer  "() {
        given: "Stats subscription,nodes and scanners exists in dps"
        def (Date startTime, Date endTime) = getStartAndEndTime(15)
        SubscriptionBuilder subscriptionBuilder = builder.newInstance(dpsUtils)
        subscriptionMO = addSubscription(subscriptionBuilder, AdministrationState.DEACTIVATING, startTime, endTime)
        nodes = addNodes()
        (processType in [UETRACE, CTUM]) ? createPmicJobInfo(subscriptionMO, nodes) : dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanners(subscriptionMO.getPoId(), nodes, processType, scannerName, true, type)
        and: "Attribute Change Event is created"
        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChanges(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, "ACTIVE", "DEACTIVATING")
        attributeChangedEvent.setPoId(PO_ID)
        and: "Create a timer"
        Timer originalTimer = Mock(Timer)
        when: "timer exists in timer service"
        timerService.getTimers() >> [originalTimer]
        originalTimer.getInfo() >> new InitiationScheduleModel(PO_ID, AdministrationState.DEACTIVATING)
        and: "Listener receives Attribute Change Event"
        boolean eventRouted = dpsNotificationListenerForInitiation.isInterested(attributeChangedEvent)
        dpsNotificationListenerForInitiation.onEvent(attributeChangedEvent)
        then: "timer is cancelled"
        eventRouted == true
        1 * originalTimer.cancel()
        and: "Deactivation tasks are sent"
        2 * eventSender.send(_ as MediationTaskRequest)

        where:
        builder                              | subscriptionType                   | processType               | scannerName                 | type
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'    | SCANNER_MODEL_NAME
        StatisticalSubscriptionBuilder.class | 'Statistical Subscription'         | STATS                     | 'USERDEF-Test.Cont.Y.STATS' | SCANNER_MODEL_NAME
        CctrSubscriptionBuilder.class        | 'ContinuousCellTrace Subscription' | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10005.CELLTRACE'    | SCANNER_MODEL_NAME
        EbmSubscriptionBuilder.class         | 'Ebm Subscription'                 | EVENTJOB                  | 'PREDEF.EBMLOG.EBM'         | SCANNER_MODEL_NAME
        CellTrafficSubscriptionBuilder.class | 'CellTraffic Subscription'         | CTR                       | 'PREDEF.20000.CTR'          | SCANNER_MODEL_NAME
        UetrSubscriptionBuilder.class        | 'Uetr Subscription'                | UETR                      | 'PREDEF.10000.UETR'         | SCANNER_UE_MODEL_NAME
        GpehSubscriptionBuilder.class        | 'Gpeh Subscription'                | REGULAR_GPEH              | 'PREDEF.30000.GPEH'         | SCANNER_MODEL_NAME
        UeTraceSubscriptionBuilder.class     | 'UeTrace Subscription'             | UETRACE                   | ''                          | SCANNER_MODEL_NAME
        CtumSubscriptionBuilder.class        | 'Ctum Subcscription'               | CTUM                      | ''                          | SCANNER_MODEL_NAME
    }

    @Unroll
    def "when administration state is changed from UPDATING to DEACTIVATING for #subscriptionType, should cancel the deactivation timer  "() {
        given: "Stats subscription,nodes and scanners exists in dps"
        def (Date startTime, Date endTime) = getStartAndEndTime(15)
        SubscriptionBuilder subscriptionBuilder = builder.newInstance(dpsUtils)
        subscriptionMO = addSubscription(subscriptionBuilder, AdministrationState.DEACTIVATING, startTime, endTime)
        nodes = addNodes()
        (processType in [UETRACE, CTUM]) ? createPmicJobInfo(subscriptionMO, nodes) : dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanners(subscriptionMO.getPoId(), nodes, processType, scannerName, true, type)
        and: "Attribute Change Event is created"
        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChanges(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, "UPDATING", "DEACTIVATING")
        attributeChangedEvent.setPoId(PO_ID)
        and: "Create a timer"
        Timer originalTimer = Mock(Timer)
        when: "timer exists in timer service"
        timerService.getTimers() >> [originalTimer]
        originalTimer.getInfo() >> new InitiationScheduleModel(PO_ID, AdministrationState.DEACTIVATING)
        and: "Listener receives Attribute Change Event"
        dpsNotificationListenerForInitiation.onEvent(attributeChangedEvent)
        then: "timer is cancelled"
        1 * originalTimer.cancel()
        and: "Deactivation tasks are sent"
        2 * eventSender.send(_ as MediationTaskRequest)

        where:
        builder                              | subscriptionType                   | processType               | scannerName                 | type
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'    | SCANNER_MODEL_NAME
        StatisticalSubscriptionBuilder.class | 'Statistical Subscription'         | STATS                     | 'USERDEF-Test.Cont.Y.STATS' | SCANNER_MODEL_NAME
        CctrSubscriptionBuilder.class        | 'ContinuousCellTrace Subscription' | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10005.CELLTRACE'    | SCANNER_MODEL_NAME
        EbmSubscriptionBuilder.class         | 'Ebm Subscription'                 | EVENTJOB                  | 'PREDEF.EBMLOG.EBM'         | SCANNER_MODEL_NAME
        CellTrafficSubscriptionBuilder.class | 'CellTraffic Subscription'         | CTR                       | 'PREDEF.20000.CTR'          | SCANNER_MODEL_NAME
        UetrSubscriptionBuilder.class        | 'Uetr Subscription'                | UETR                      | 'PREDEF.10000.UETR'         | SCANNER_UE_MODEL_NAME
        GpehSubscriptionBuilder.class        | 'Gpeh Subscription'                | REGULAR_GPEH              | 'PREDEF.30000.GPEH'         | SCANNER_MODEL_NAME
        UeTraceSubscriptionBuilder.class     | 'UeTrace Subscription'             | UETRACE                   | ''                          | SCANNER_MODEL_NAME
        CtumSubscriptionBuilder.class        | 'Ctum Subcscription'               | CTUM                      | ''                          | SCANNER_MODEL_NAME
    }

    @Unroll
    def "when administration state is changed from SCHEDULED to INACTIVE for #subscriptionType, should cancel the timers  "() {
        given: "Stats subscription,nodes and scanners exists in dps"
        def (Date startTime, Date endTime) = getStartAndEndTime(30)
        SubscriptionBuilder subscriptionBuilder = builder.newInstance(dpsUtils)
        subscriptionMO = addSubscription(subscriptionBuilder, AdministrationState.INACTIVE, startTime, endTime)
        nodes = addNodes()
        (processType in [UETRACE, CTUM]) ? createPmicJobInfo(subscriptionMO, nodes) : dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanners(subscriptionMO.getPoId(), nodes, processType, scannerName, type)
        and: "Attribute Change Event is created"
        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChanges(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, "SCHEDULED", "INACTIVE")
        attributeChangedEvent.setPoId(PO_ID)
        and: "Create a timer"
        Timer originalTimer = Mock(Timer)
        when: "timer exists in timer service"
        timerService.getTimers() >> [originalTimer]
        InitiationScheduleModel initiationScheduleModel = new InitiationScheduleModel(PO_ID, AdministrationState.ACTIVATING)
        originalTimer.getInfo() >> initiationScheduleModel
        and: "Listener receives Attribute Change Event"
        boolean eventRouted = dpsNotificationListenerForInitiation.isInterested(attributeChangedEvent)
        dpsNotificationListenerForInitiation.onEvent(attributeChangedEvent)
        then: "timer is cancelled"
        eventRouted == true
        1 * originalTimer.cancel()

        where:
        builder                              | subscriptionType                   | processType               | scannerName                 | type
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'    | SCANNER_MODEL_NAME
        StatisticalSubscriptionBuilder.class | 'Statistical Subscription'         | STATS                     | 'USERDEF-Test.Cont.Y.STATS' | SCANNER_MODEL_NAME
        CctrSubscriptionBuilder.class        | 'ContinuousCellTrace Subscription' | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10005.CELLTRACE'    | SCANNER_MODEL_NAME
        EbmSubscriptionBuilder.class         | 'Ebm Subscription'                 | EVENTJOB                  | 'PREDEF.EBMLOG.EBM'         | SCANNER_MODEL_NAME
        CellTrafficSubscriptionBuilder.class | 'CellTraffic Subscription'         | CTR                       | 'PREDEF.20000.CTR'          | SCANNER_MODEL_NAME
        UetrSubscriptionBuilder.class        | 'Uetr Subscription'                | UETR                      | 'PREDEF.10000.UETR'         | SCANNER_UE_MODEL_NAME
        GpehSubscriptionBuilder.class        | 'Gpeh Subscription'                | REGULAR_GPEH              | 'PREDEF.30000.GPEH'         | SCANNER_MODEL_NAME
        UeTraceSubscriptionBuilder.class     | 'UeTrace Subscription'             | UETRACE                   | ''                          | SCANNER_MODEL_NAME
        CtumSubscriptionBuilder.class        | 'Ctum Subcscription'               | CTUM                      | ''                          | SCANNER_MODEL_NAME
    }

    @Unroll
    def "Activating 2 nodes one with PMFunction off for #subscriptionType, check one MediationTasks Request sent"() {
        given: "Stats subscription,nodes and scanners exists in dps"
        SubscriptionBuilder subscriptionBuilder = builder.newInstance(dpsUtils)
        setAdditionalAttributes(subscriptionBuilder);
        subscriptionMO = addSubscription(subscriptionBuilder, AdministrationState.ACTIVATING, null, null, subName)
        nodes = addNodes(true, false)
        (processType in [UETRACE, CTUM]) ? createPmicJobInfo(subscriptionMO, nodes) : dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanners(subscriptionMO.getPoId(), nodes, processType, scannerName, type)
        and: "Initiation schedule model is created"
        InitiationScheduleModel initiationScheduleModel = new InitiationScheduleModel(PO_ID, AdministrationState.ACTIVATING)
        Timer timer = mock(Timer)
        timer.getInfo() >> initiationScheduleModel
        when: "timeout occurs in initiation scheduler service"
        initiationSchedulerServiceBean.timeout(timer)
        then: "only one activation task is sent "
        1 * eventSender.send(_ as MediationTaskRequest)

        where:
        builder                              | subscriptionType                   | processType               | scannerName                 | type               | subName
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'    | SCANNER_MODEL_NAME | 'Test'
        StatisticalSubscriptionBuilder.class | 'Statistical Subscription'         | STATS                     | 'USERDEF-Test.Cont.Y.STATS' | SCANNER_MODEL_NAME | 'Test'
        CctrSubscriptionBuilder.class        | 'ContinuousCellTrace Subscription' | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10005.CELLTRACE'    | SCANNER_MODEL_NAME | PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME
        EbmSubscriptionBuilder.class         | 'Ebm Subscription'                 | EVENTJOB                  | 'PREDEF.EBMLOG.EBM'         | SCANNER_MODEL_NAME | 'Test'
        CellTrafficSubscriptionBuilder.class | 'CellTraffic Subscription'         | CTR                       | 'PREDEF.20000.CTR'          | SCANNER_MODEL_NAME | 'Test'
        GpehSubscriptionBuilder.class        | 'Gpeh Subscription'                | REGULAR_GPEH              | 'PREDEF.30000.GPEH'         | SCANNER_MODEL_NAME | 'Test'
        UeTraceSubscriptionBuilder.class     | 'UeTrace Subscription'             | UETRACE                   | ''                          | SCANNER_MODEL_NAME | 'Test'
        CtumSubscriptionBuilder.class        | 'Ctum Subcscription'               | CTUM                      | ''                          | SCANNER_MODEL_NAME | 'Test'
    }

    @Unroll
    def "Activating 2 nodes with PMFunction off for #subscriptionType - check zero MediationTasks Request sent"() {
        given: "Stats subscription,nodes and scanners exists in dps"
        SubscriptionBuilder subscriptionBuilder = builder.newInstance(dpsUtils)
        subscriptionMO = addSubscription(subscriptionBuilder, AdministrationState.ACTIVATING, null, null, subName)
        nodes = [nodeUtil.builder(NODE_NAME_1).pmEnabled(false).build(), nodeUtil.builder(NODE_NAME_2).pmEnabled(false).build()]
        (processType in [UETRACE, CTUM]) ? createPmicJobInfo(subscriptionMO, nodes) : dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanners(subscriptionMO.getPoId(), nodes, processType, scannerName, type)
        and: "Initiation schedule model is created"
        InitiationScheduleModel initiationScheduleModel = new InitiationScheduleModel(PO_ID, AdministrationState.ACTIVATING)
        Timer timer = mock(Timer)
        timer.getInfo() >> initiationScheduleModel
        when: "timeout occurs in initiation scheduler service"
        initiationSchedulerServiceBean.timeout(timer)
        then: "no activation task is sent "
        0 * eventSender.send(_ as MediationTaskRequest)

        where:
        builder                              | subscriptionType                   | processType               | scannerName                 | type                  | subName
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'    | SCANNER_MODEL_NAME    | 'Test'
        StatisticalSubscriptionBuilder.class | 'Statistical Subscription'         | STATS                     | 'USERDEF-Test.Cont.Y.STATS' | SCANNER_MODEL_NAME    | 'Test'
        CctrSubscriptionBuilder.class        | 'ContinuousCellTrace Subscription' | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10005.CELLTRACE'    | SCANNER_MODEL_NAME    | PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME
        EbmSubscriptionBuilder.class         | 'Ebm Subscription'                 | EVENTJOB                  | 'PREDEF.EBMLOG.EBM'         | SCANNER_MODEL_NAME    | 'Test'
        CellTrafficSubscriptionBuilder.class | 'CellTraffic Subscription'         | CTR                       | 'PREDEF.20000.CTR'          | SCANNER_MODEL_NAME    | 'Test'
        UetrSubscriptionBuilder.class        | 'Uetr Subscription'                | UETR                      | 'PREDEF.10000.UETR'         | SCANNER_UE_MODEL_NAME | 'Test'
        GpehSubscriptionBuilder.class        | 'Gpeh Subscription'                | REGULAR_GPEH              | 'PREDEF.30000.GPEH'         | SCANNER_MODEL_NAME    | 'Test'
        UeTraceSubscriptionBuilder.class     | 'UeTrace Subscription'             | UETRACE                   | ''                          | SCANNER_MODEL_NAME    | 'Test'
        CtumSubscriptionBuilder.class        | 'Ctum Subcscription'               | CTUM                      | ''                          | SCANNER_MODEL_NAME    | 'Test'
    }

    @Unroll
    def "On Timeout, Deactivating the subscription with 2 PMFunction OFF nodes for #subscriptionType, check zero MediationTasks Request sent"() {
        given: "Stats subscription,nodes and active scanners exists in dps"
        SubscriptionBuilder subscriptionBuilder = builder.newInstance(dpsUtils)
        subscriptionMO = addSubscription(subscriptionBuilder, AdministrationState.DEACTIVATING)
        nodes = addNodes(false, false)
        (processType in [UETRACE, CTUM]) ? createPmicJobInfo(subscriptionMO, nodes) : dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanners(subscriptionMO.getPoId(), nodes, processType, scannerName, true, type)
        and: "Initiation schedule model is created"
        InitiationScheduleModel initiationScheduleModel = new InitiationScheduleModel(PO_ID, AdministrationState.DEACTIVATING)
        Timer timer = mock(Timer)
        timer.getInfo() >> initiationScheduleModel
        when: "timeout occurs in initiation scheduler service"
        initiationSchedulerServiceBean.timeout(timer)
        then:
        subscriptionMO.getAttribute('administrationState') == AdministrationState.DEACTIVATING.toString()
        and: "deactivation tasks are sent even for pm function off nodes if they have active scanners"
        0 * eventSender.send(_ as MediationTaskRequest)

        where:
        builder                              | subscriptionType                   | processType               | scannerName                 | type               | name
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'    | SCANNER_MODEL_NAME | 'Test'
        StatisticalSubscriptionBuilder.class | 'Statistical Subscription'         | STATS                     | 'USERDEF-Test.Cont.Y.STATS' | SCANNER_MODEL_NAME | 'Test'
        CctrSubscriptionBuilder.class        | 'ContinuousCellTrace Subscription' | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10005.CELLTRACE'    | SCANNER_MODEL_NAME | PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME
        EbmSubscriptionBuilder.class         | 'Ebm Subscription'                 | EVENTJOB                  | 'PREDEF.EBMLOG.EBM'         | SCANNER_MODEL_NAME | 'Test'
        CellTrafficSubscriptionBuilder.class | 'CellTraffic Subscription'         | CTR                       | 'PREDEF.20000.CTR'          | SCANNER_MODEL_NAME | 'Test'
        GpehSubscriptionBuilder.class        | 'Gpeh Subscription'                | REGULAR_GPEH              | 'PREDEF.30000.GPEH'         | SCANNER_MODEL_NAME | 'Test'
        UeTraceSubscriptionBuilder.class     | 'UeTrace Subscription'             | UETRACE                   | ''                          | SCANNER_MODEL_NAME | 'Test'
        CtumSubscriptionBuilder.class        | 'Ctum Subcscription'               | CTUM                      | ''                          | SCANNER_MODEL_NAME | 'Test'
    }

    @Unroll
    def " On Timeout for #subscriptionType, should execute activation Event and create activation MediationTasks Requests"() {
        given: "Stats subscription,nodes and scanners exists in dps"
        SubscriptionBuilder subscriptionBuilder = builder.newInstance(dpsUtils)
        setAdditionalAttributes(subscriptionBuilder);
        subscriptionMO = addSubscription(subscriptionBuilder, AdministrationState.ACTIVATING, null, null, subName)
        nodes = addNodes()
        (processType in [UETRACE, CTUM]) ? createPmicJobInfo(subscriptionMO, nodes) : dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanners(subscriptionMO.getPoId(), nodes, processType, scannerName, type)
        and: "Initiation schedule model is created"
        InitiationScheduleModel initiationScheduleModel = new InitiationScheduleModel(PO_ID, AdministrationState.ACTIVATING)
        Timer timer = mock(Timer)
        timer.getInfo() >> initiationScheduleModel
        when: "timeout occurs in initiation scheduler service"
        initiationSchedulerServiceBean.timeout(timer)
        then: "2 activation task are sent"
        2 * eventSender.send(_ as MediationTaskRequest)
        and: "Persistence time of subscripton is updated"
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_PERSISTENCE_TIME) != old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_PERSISTENCE_TIME))

        where:
        builder                              | subscriptionType                   | processType               | scannerName                 | type               | subName
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'    | SCANNER_MODEL_NAME | 'Test'
        StatisticalSubscriptionBuilder.class | 'Statistical Subscription'         | STATS                     | 'USERDEF-Test.Cont.Y.STATS' | SCANNER_MODEL_NAME | 'Test'
        CctrSubscriptionBuilder.class        | 'ContinuousCellTrace Subscription' | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10005.CELLTRACE'    | SCANNER_MODEL_NAME | PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME
        EbmSubscriptionBuilder.class         | 'Ebm Subscription'                 | EVENTJOB                  | 'PREDEF.EBMLOG.EBM'         | SCANNER_MODEL_NAME | 'Test'
        CellTrafficSubscriptionBuilder.class | 'CellTraffic Subscription'         | CTR                       | 'PREDEF.20000.CTR'          | SCANNER_MODEL_NAME | 'Test'
        GpehSubscriptionBuilder.class        | 'Gpeh Subscription'                | REGULAR_GPEH              | 'PREDEF.30000.GPEH'         | SCANNER_MODEL_NAME | 'Test'
        UeTraceSubscriptionBuilder.class     | 'UeTrace Subscription'             | UETRACE                   | ''                          | SCANNER_MODEL_NAME | 'Test'
        CtumSubscriptionBuilder.class        | 'Ctum Subcscription'               | CTUM                      | ''                          | SCANNER_MODEL_NAME | 'Test'
    }

    @Unroll
    def "Scheduled subscription state is changed to activating for #subscriptionType "() {
        given: "Stats subscription,nodes and scanners exists in dps"
        SubscriptionBuilder subscriptionBuilder = builder.newInstance(dpsUtils)
        setAdditionalAttributes(subscriptionBuilder);
        subscriptionMO = addSubscription(subscriptionBuilder, AdministrationState.SCHEDULED, null, null, subName)
        nodes = addNodes()
        (processType in [UETRACE, CTUM]) ? createPmicJobInfo(subscriptionMO, nodes) : dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanners(subscriptionMO.getPoId(), nodes, processType, scannerName, type)
        when: "activation event is executed "
        activationEvent.execute(PO_ID)
        then: "should call activate event"
        subscriptionMO.getAttribute('administrationState') == AdministrationState.ACTIVATING.toString()
        and: "two activation tasks are sent "
        2 * eventSender.send(_ as MediationTaskRequest)

        where:
        builder                              | subscriptionType                   | processType               | scannerName                 | type               | subName
        CellTraceSubscriptionBuilder.class   | 'CellTrace Subscription'           | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'    | SCANNER_MODEL_NAME | 'Test'
        StatisticalSubscriptionBuilder.class | 'Statistical Subscription'         | STATS                     | 'USERDEF-Test.Cont.Y.STATS' | SCANNER_MODEL_NAME | 'Test'
        CctrSubscriptionBuilder.class        | 'ContinuousCellTrace Subscription' | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10005.CELLTRACE'    | SCANNER_MODEL_NAME | PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME
        EbmSubscriptionBuilder.class         | 'Ebm Subscription'                 | EVENTJOB                  | 'PREDEF.EBMLOG.EBM'         | SCANNER_MODEL_NAME | 'Test'
        CellTrafficSubscriptionBuilder.class | 'CellTraffic Subscription'         | CTR                       | 'PREDEF.20000.CTR'          | SCANNER_MODEL_NAME | 'Test'
        GpehSubscriptionBuilder.class        | 'Gpeh Subscription'                | REGULAR_GPEH              | 'PREDEF.30000.GPEH'         | SCANNER_MODEL_NAME | 'Test'
        UeTraceSubscriptionBuilder.class     | 'UeTrace Subscription'             | UETRACE                   | ''                          | SCANNER_MODEL_NAME | 'Test'
        CtumSubscriptionBuilder.class        | 'Ctum Subcscription'               | CTUM                      | ''                          | SCANNER_MODEL_NAME | 'Test'
    }

    @Unroll
    def "when administration state is changed from ACTIVATING to ACTIVE for CellTrace Subscription,should sent the Notification Message "() {
        given: "Stats subscription,nodes and scanners exists in dps"
        CellTraceSubscriptionBuilder subscriptionBuilder = CellTraceSubscriptionBuilder.newInstance(dpsUtils)
        subscriptionBuilder.cellTraceCategory(celltraceCategory.name())
        subscriptionMO = addCellTraceSubscription(subscriptionBuilder, AdministrationState.ACTIVE)
        nodes = addNodes()
        dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanners(subscriptionMO.getPoId(), nodes, processType, scannerName, true, type)
        and: "Attribute Change Event is created"
        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChanges(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, "ACTIVATING", "ACTIVE")
        attributeChangedEvent.setPoId(PO_ID)
        when: "Listener receives Attribute Change Event"
        boolean eventRouted = dpsNotificationListenerForInitiation.isInterested(attributeChangedEvent)
        dpsNotificationListenerForInitiation.onEvent(attributeChangedEvent)
        then: "event is Routed"
        eventRouted == true
        and: "Notification to external consumer is send"
        times * pmicSubscriptionUpdateEventSender.send(_ as PmicSubscriptionUpdate)
        where:
        celltraceCategory                              | times | processType               | scannerName                 | type
        CellTraceCategory.CELLTRACE                    | 0     | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'    | SCANNER_MODEL_NAME
        CellTraceCategory.CELLTRACE_AND_EBSL_FILE      | 1     | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'    | SCANNER_MODEL_NAME
        CellTraceCategory.CELLTRACE_AND_EBSL_STREAM    | 1     | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10004.CELLTRACE'    | SCANNER_MODEL_NAME
        CellTraceCategory.EBSL_STREAM                  | 1     | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10004.CELLTRACE'    | SCANNER_MODEL_NAME
        CellTraceCategory.ASR                          | 0     | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10004.CELLTRACE'    | SCANNER_MODEL_NAME
        CellTraceCategory.ESN                          | 0     | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10004.CELLTRACE'    | SCANNER_MODEL_NAME
        CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_FILE | 1     | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.DU.10001.CELLTRACE' | SCANNER_MODEL_NAME
    }

    @Unroll
    def "when administration state is changed from DEACTIVATING to INACTIVE for CellTrace Subscription,should sent the Notification Message "() {
        given: "Stats subscription,nodes and scanners exists in dps"
        CellTraceSubscriptionBuilder subscriptionBuilder = CellTraceSubscriptionBuilder.newInstance(dpsUtils)
        subscriptionBuilder.cellTraceCategory(celltraceCategory.name())
        subscriptionMO = addCellTraceSubscription(subscriptionBuilder, AdministrationState.INACTIVE)
        nodes = addNodes()
        dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanners(subscriptionMO.getPoId(), nodes, processType, scannerName, true, type)
        and: "Attribute Change Event is created"
        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChanges(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, "DEACTIVATING", "INACTIVE")
        attributeChangedEvent.setPoId(PO_ID)
        when: "Listener receives Attribute Change Event"
        boolean eventRouted = dpsNotificationListenerForInitiation.isInterested(attributeChangedEvent)
        dpsNotificationListenerForInitiation.onEvent(attributeChangedEvent)
        then: "event is Routed"
        eventRouted == true
        and: "Notification to external consumer is send"
        times * pmicSubscriptionUpdateEventSender.send(_ as PmicSubscriptionUpdate)
        where:
        celltraceCategory                              | times | processType               | scannerName                   | type
        CellTraceCategory.CELLTRACE                    | 0     | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'      | SCANNER_MODEL_NAME
        CellTraceCategory.CELLTRACE_AND_EBSL_FILE      | 1     | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'      | SCANNER_MODEL_NAME
        CellTraceCategory.CELLTRACE_AND_EBSL_STREAM    | 1     | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10004.CELLTRACE'      | SCANNER_MODEL_NAME
        CellTraceCategory.EBSL_STREAM                  | 1     | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10004.CELLTRACE'      | SCANNER_MODEL_NAME
        CellTraceCategory.ASR                          | 0     | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10004.CELLTRACE'      | SCANNER_MODEL_NAME
        CellTraceCategory.ESN                          | 0     | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10004.CELLTRACE'      | SCANNER_MODEL_NAME
        CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_FILE | 1     | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.CUUP.10002.CELLTRACE' | SCANNER_MODEL_NAME
    }

    @Unroll
    def "when administration state is changed from UPDATING to INACTIVE for CellTrace Subscription,should sent the Notification Message "() {
        given: "Stats subscription,nodes and scanners exists in dps"
        CellTraceSubscriptionBuilder subscriptionBuilder = CellTraceSubscriptionBuilder.newInstance(dpsUtils)
        subscriptionBuilder.cellTraceCategory(celltraceCategory.name())
        subscriptionMO = addCellTraceSubscription(subscriptionBuilder, AdministrationState.INACTIVE)
        nodes = addNodes()
        dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanners(subscriptionMO.getPoId(), nodes, processType, scannerName, true, type)
        and: "Attribute Change Event is created"
        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChanges(PMIC_ATT_SUBSCRIPTION_ADMINSTATE, "DEACTIVATING", "INACTIVE")
        attributeChangedEvent.setPoId(PO_ID)
        when: "Listener receives Attribute Change Event"
        boolean eventRouted = dpsNotificationListenerForInitiation.isInterested(attributeChangedEvent)
        dpsNotificationListenerForInitiation.onEvent(attributeChangedEvent)
        then: "event is Routed"
        eventRouted == true
        and: "Notification to external consumer is send"
        times * pmicSubscriptionUpdateEventSender.send(_ as PmicSubscriptionUpdate)
        where:
        celltraceCategory                              | times | processType               | scannerName                   | type
        CellTraceCategory.CELLTRACE                    | 0     | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'      | SCANNER_MODEL_NAME
        CellTraceCategory.CELLTRACE_AND_EBSL_FILE      | 1     | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.10001.CELLTRACE'      | SCANNER_MODEL_NAME
        CellTraceCategory.CELLTRACE_AND_EBSL_STREAM    | 1     | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10004.CELLTRACE'      | SCANNER_MODEL_NAME
        CellTraceCategory.EBSL_STREAM                  | 1     | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10004.CELLTRACE'      | SCANNER_MODEL_NAME
        CellTraceCategory.ASR                          | 0     | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10004.CELLTRACE'      | SCANNER_MODEL_NAME
        CellTraceCategory.ESN                          | 0     | HIGH_PRIORITY_CELLTRACE   | 'PREDEF.10004.CELLTRACE'      | SCANNER_MODEL_NAME
        CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_FILE | 1     | NORMAL_PRIORITY_CELLTRACE | 'PREDEF.CUCP.10000.CELLTRACE' | SCANNER_MODEL_NAME
    }

    private DpsAttributeChangedEvent createAttributeChanges(administrationState, oldValue, newValue) {
        Collection<AttributeChangeData> attributeChangeData = [new AttributeChangeData(administrationState, oldValue, newValue, null, null)]
        return new DpsAttributeChangedEvent(subscriptionMO.getNamespace(), subscriptionMO.getType(), subscriptionMO.getVersion(), subscriptionMO
                .getPoId(), subscriptionMO.getFdn(), configurableDps.build().getLiveBucket().getName(), attributeChangeData)
    }

    private DpsAttributeChangedEvent createAttributeChangesForNonPmicSubscriptionType(attributeName = "syncStatus", oldValue = "TOPOLOGY", newValue = "SYNCHRONIZED", nameSpace = "OSS_NE_CM_DEF", type = "CmFunction") {
        Collection<AttributeChangeData> attributeChangeData = [new AttributeChangeData(attributeName, oldValue, newValue, null, null)]
        return new DpsAttributeChangedEvent(nameSpace, type, "1.0.1", nodes[0].poId, nodes[0].getFdn(), configurableDps.build().getLiveBucket().getName(), attributeChangeData)
    }

    private static List getStartAndEndTime(int timeDifference) {
        Calendar calendar = Calendar.getInstance()
        Date startTime = calendar.getTime()
        calendar.add(Calendar.MINUTE, timeDifference)
        Date endTime = calendar.getTime()
        [startTime, endTime]
    }

    private static Date getStartTime() {
        Calendar calendar = Calendar.getInstance()
        Date startTime = calendar.getTime()
        startTime
    }

    def createScanners(
            final long subId,
            final List<ManagedObject> nodes, ProcessType processType, String scannerName, Boolean scannersActive = false, String type) {
        List<ManagedObject> scanners
        scanners = [scannerUtil.builder(scannerName, NODE_NAME_1).subscriptionId(subId).status((scannersActive) ? ScannerStatus.ACTIVE : ScannerStatus.INACTIVE)
                            .processType(processType).type(type).build(),
                    scannerUtil.builder(scannerName, NODE_NAME_2).subscriptionId(subId).status((scannersActive) ? ScannerStatus.ACTIVE : ScannerStatus.INACTIVE)
                            .processType(processType).type(type).build()]
        (0..nodes.size() - 1).each {
            dpsUtils.addAssociation(nodes[it], "scanners", scanners[it])
        }
    }

    def createPmicJobInfo(ManagedObject subscriptionMO, ArrayList<ManagedObject> nodes) {
        nodes.each {
            pmJobBuilder.nodeName(it as ManagedObject).processType(subscriptionMO).subscriptionId(subscriptionMO).build()
        }

    }

    private ArrayList<ManagedObject> addNodes(boolean node1PmFunctionOn = true, boolean node2PmFunctionOn = true) {
        nodes = [nodeUtil.builder(NODE_NAME_1).ossModelIdentity('6607-651-025').neType('SGSN-MME').pmEnabled(node1PmFunctionOn).build(), nodeUtil.builder(NODE_NAME_2).ossModelIdentity('6607-651-025').neType('SGSN-MME').pmEnabled(node2PmFunctionOn).build()]
        nodes
    }

    private ManagedObject addSubscription(SubscriptionBuilder builder, AdministrationState administrationState, Date startTime = null, Date endTime = null, String name = 'Test') {
        subscriptionMO = builder.name(name).administrativeState(administrationState).persistenceTime(startTime).scheduleInfo(startTime, endTime)
                .addEvent('counterGroup', 'counterName').build()
        subscriptionMO
    }

    private ManagedObject addCellTraceSubscription(CellTraceSubscriptionBuilder builder, AdministrationState administrationState) {
        subscriptionMO = builder.name("Test").administrativeState(administrationState).persistenceTime(startTime).scheduleInfo(null, null)
                .addEvent('counterGroup', 'counterName').build()
        subscriptionMO
    }


    private setAdditionalAttributes(final SubscriptionBuilder subscriptionBuilder) {
        final Map<String, Object> additionalAttributes = new HashMap<>();
        additionalAttributes.put("outputMode", OutputModeType.FILE_AND_STREAMING.name());
        subscriptionBuilder.setAdditionalAttributes(additionalAttributes);
    }
}
