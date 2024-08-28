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

import static com.ericsson.oss.pmic.cdi.test.util.Constants.*
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE

import org.mockito.Mockito
import spock.lang.Unroll

import javax.inject.Inject
import java.util.concurrent.TimeUnit

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.*
import com.ericsson.oss.pmic.dao.ScannerDao
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus
import com.ericsson.oss.pmic.dto.scanner.Scanner
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.dto.subscription.enums.UeType
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.schedulers.InitiationDelayTimer
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener
import com.ericsson.oss.services.pm.time.TimeGenerator

class InitiationDelayTimerForDeactivationSpec extends PmServiceEjbSkeletonSpec {

    static final def nodeNames = [NODE_NAME_1, NODE_NAME_2]

    @ObjectUnderTest
    InitiationDelayTimer initiationDelayTimer

    @Inject
    DeactivationEvent deactivationEvent

    @Inject
    PMICInitiationTrackerCache initiationTrackerCache

    @Inject
    DataPersistenceService dataPersistenceService

    @ImplementationInstance
    MembershipListener listener = Mock(MembershipListener)

    @ImplementationInstance
    TimeGenerator timeGenerator = Mockito.mock(TimeGenerator.class)

    @Inject
    ScannerDao scannerDao;

    List<ManagedObject> nodes = []

    ManagedObject subscriptionMO
    def scanners = []

    def fdnMap
    static final currentTime = System.currentTimeMillis()
    static final pastTime = currentTime - TimeUnit.MINUTES.toMillis(30)

    def setup() {
        nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build()]
        fdnMap = [("NetworkElement=$NODE_NAME_1".toString()): 'ERBS', ("NetworkElement=$NODE_NAME_2".toString()): 'ERBS']
    }

    @Unroll
    def "When the deactivation times out for #builder.getSimpleName(), the subscription goes to INACTIVE and the scanners are removed from the subscription"() {
        given: "Subscription exists in dps"
        SubscriptionBuilder subscriptionBuilder = builder.newInstance(dpsUtils) as SubscriptionBuilder
        subscriptionMO = subscriptionBuilder.name("Test").administrativeState(AdministrationState.DEACTIVATING).taskStatus(TaskStatus.OK).addNode(nodes.get(0)).addNode(nodes.get(1)).build()
        addScanners()
        and: "Subscription deactivation is called"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
        deactivationEvent.execute(subscriptionMO.getPoId())
        listener.isMaster() >> true

        when: "The tracker timer times out"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
        initiationDelayTimer.execute(null)

        then: "The subscription will go to INACTIVE/OK"
        INACTIVE == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
        OK == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        and: "The scanners will be removed from DPS"
        scanners.each {
            dataPersistenceService.getLiveBucket().findMoByFdn(it.fdn) == null
        }
        and: "Initiation Tracker will be removed from cache"
        initiationTrackerCache.getTracker(subscriptionMO.poId as String) == null

        where:
        builder << [StatisticalSubscriptionBuilder.class, CellTraceSubscriptionBuilder.class, EbmSubscriptionBuilder.class, CctrSubscriptionBuilder.
                class, MoinstanceSubscriptionBuilder.class, CellTrafficSubscriptionBuilder.class]
    }

    def "When the deactivation times out for UETR, the subscription goes to INACTIVE and the scanners are removed from the subscription"() {
        given: "Subscription exists in dps"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
        SubscriptionBuilder subscriptionBuilder = UetrSubscriptionBuilder.newInstance(dpsUtils) as SubscriptionBuilder
        subscriptionMO = subscriptionBuilder.name("Test").administrativeState(AdministrationState.DEACTIVATING).taskStatus(TaskStatus.OK).build()
        addUeScanners()
        dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        and: "Subscription deactivation is called"
        deactivationEvent.execute(subscriptionMO.getPoId())
        listener.isMaster() >> true

        when: "The tracker timer times out"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
        initiationDelayTimer.execute(null)

        then: "The subscription will go to INACTIVE/OK"
        INACTIVE == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
        OK == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        and: "The scanners will be removed from DPS"
        scanners.each {
            dataPersistenceService.getLiveBucket().findMoByFdn(it.fdn) == null
        }
        and: "Initiation Tracker will be removed from cache"
        initiationTrackerCache.getTracker(subscriptionMO.poId as String) == null
    }

    @Unroll
    def "When the deactivation times out for pmJob supported type : #subscriptionType, with active pmJobs, the subscription goes to INACTIVE with ERROR task status"() {
        given: "Subscription exists in dps"
        ManagedObject node1 = nodeUtil.builder("SGSN1").neType("SGSN-MME").build()
        ManagedObject node2 = nodeUtil.builder("SGSN2").neType("SGSN-MME").build()

        subscriptionMO = dps.subscription().type(subscriptionType).name("Test").administrationState(AdministrationState.DEACTIVATING).
                taskStatus(TaskStatus.OK).nodes(node1, node2).build()
        ProcessType processType = getSubscriptionTypeProcessTypeMap().get(SubscriptionType.valueOf(subscriptionMO.getAttribute('type') as String))
        dps.pmJob().nodeName(node1).processType(processType).subscriptionId(subscriptionMO).status(PmJobStatus.ACTIVE.name()).build()
        processType = getSubscriptionTypeProcessTypeMap().get(SubscriptionType.valueOf(subscriptionMO.getAttribute('type') as String))
        dps.pmJob().nodeName(node2).processType(processType).subscriptionId(subscriptionMO).status(PmJobStatus.ACTIVE.name()).build()

        and: "Subscription deactivation is called"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
        deactivationEvent.execute(subscriptionMO.getPoId())
        listener.isMaster() >> true

        when: "The tracker timer times out"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
        initiationDelayTimer.execute(null)

        then: "The subscription will go to INACTIVE/ERROR"
        INACTIVE == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
        ERROR == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        and: "Initiation Tracker will be removed from cache"
        initiationTrackerCache.getTracker(subscriptionMO.poId as String) == null

        where:
        subscriptionType << [SubscriptionType.CTUM, SubscriptionType.UETRACE]
    }

    @Unroll
    def "When the deactivation times out for pmJob supported type : #subscriptionType, with inactive pmJobs, the subscription goes to INACTIVE with OK task status"() {
        given: "Subscription exists in dps"
        subscriptionMO = dps.subscription().type(subscriptionType).name("Test").administrationState(AdministrationState.DEACTIVATING).
                taskStatus(TaskStatus.OK).nodes(nodes).build()
        ProcessType processType = getSubscriptionTypeProcessTypeMap().get(SubscriptionType.valueOf(subscriptionMO.getAttribute('type') as String))
        nodes.each {
            dps.pmJob().nodeName(it as ManagedObject).processType(processType).subscriptionId(subscriptionMO).status(PmJobStatus.INACTIVE).build()
        }
        and: "Subscription deactivation is called"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
        deactivationEvent.execute(subscriptionMO.getPoId())
        listener.isMaster() >> true

        when: "The tracker timer times out"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
        initiationDelayTimer.execute(null)

        then: "The subscription will go to INACTIVE/OK"
        INACTIVE == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
        OK == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        and: "Initiation Tracker will be removed from cache"
        initiationTrackerCache.getTracker(subscriptionMO.poId as String) == null

        where:
        subscriptionType << [SubscriptionType.CTUM, SubscriptionType.UETRACE]
    }

    @Unroll
    def "When the deactivation times out for #subscriptionType and isMaster is false, subscription should remain unchanged"() {
        given: "Subscription exists in dps"
        subscriptionMO = dps.subscription().type(subscriptionType).name("Test").administrationState(AdministrationState.DEACTIVATING).
                taskStatus(TaskStatus.OK).nodes(nodes).build()
        addScanners()
        and: "isMaster is set to false"
        listener.isMaster() >> false
        and: "Subscription deactivation is called"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
        deactivationEvent.execute(subscriptionMO.getPoId())

        when: "The tracker timer times out"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
        initiationDelayTimer.execute(null)

        then: "The subscription will remain unchanged"
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE))
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE))
        and: "scanners will remain in DPS"
        scannerDao.findAllBySubscriptionId(subscriptionMO.poId).each {
            it.status == ScannerStatus.ACTIVE
        }

        where:
        subscriptionType << [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.EBM, SubscriptionType.CONTINUOUSCELLTRACE,
                             SubscriptionType.MOINSTANCE, SubscriptionType.CELLTRAFFIC, SubscriptionType.GPEH]
    }

    def "When the deactivation times out for UETR and isMaster is false, subscription should remain unchanged"() {
        given: "Subscription exists in dps"
        subscriptionMO = dps.subscription().type(SubscriptionType.UETR).name("Test").administrationState(AdministrationState.DEACTIVATING).
                taskStatus(TaskStatus.OK).ueInfoList(new UeInfo(UeType.IMEI, "214256543")).nodes(nodes).build()
        addUeScanners();
        and: "isMaster is set to false"
        listener.isMaster() >> false
        and: "Subscription deactivation is called"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
        deactivationEvent.execute(subscriptionMO.getPoId())

        when: "The tracker timer times out"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
        initiationDelayTimer.execute(null)

        then: "The subscription will remain unchanged"
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE))
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE))
        and: "scanners will remain in DPS"
        scannerDao.findAllBySubscriptionId(subscriptionMO.poId).each {
            it.status == ScannerStatus.ACTIVE
        }
    }

    def "On timeout execution for UETR and initiation time is still valid, subscription should remain unchanged"() {
        given: "Subscription exists in dps"
        subscriptionMO = dps.subscription().type(SubscriptionType.UETR).name("Test").administrationState(AdministrationState.DEACTIVATING).
                taskStatus(TaskStatus.OK).ueInfoList(new UeInfo(UeType.IMEI, "214256543")).nodes(nodes).build()
        addUeScanners();
        and: "Initiation Tracker is created with valid initiation time"
        timeGenerator.currentTimeMillis() >> System.currentTimeMillis()
        initiationTrackerCache.startTrackingDeactivation(subscriptionMO.poId as String, AdministrationState.DEACTIVATING.name(), fdnMap)
        and: "Subscription deactivation is called"
        deactivationEvent.execute(subscriptionMO.getPoId())
        listener.isMaster() >> true

        when: "The tracker timer times out"
        initiationDelayTimer.execute(null)

        then: "The subscription will remain unchanged"
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE))
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE))
        and: "scanners will remain in DPS"
        scannerDao.findAllBySubscriptionId(subscriptionMO.poId).each {
            it.status == ScannerStatus.ACTIVE
        }
    }

    @Unroll
    def "On timeout execution for #subscriptionType and initiation time is still valid, subscription should remain unchanged"() {
        given: "Subscription exists in dps"
        subscriptionMO = dps.subscription().type(subscriptionType).name("Test").administrationState(AdministrationState.DEACTIVATING).
                taskStatus(TaskStatus.OK).nodes(nodes).build()
        addScanners()
        and: "Initiation Tracker is created with valid initiation time"
        timeGenerator.currentTimeMillis() >> System.currentTimeMillis()
        initiationTrackerCache.startTrackingDeactivation(subscriptionMO.poId as String, AdministrationState.DEACTIVATING.name(), fdnMap)
        and: "Subscription deactivation is called"
        deactivationEvent.execute(subscriptionMO.getPoId())
        listener.isMaster() >> true

        when: "The tracker timer times out"
        initiationDelayTimer.execute(null)

        then: "The subscription will remain unchanged"
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE))
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE))
        and: "Scanner will not update to unknown in DPS"
        scannerDao.findAllBySubscriptionId(subscriptionMO.poId).each {
            it.status == ScannerStatus.ACTIVE
        }

        where:
        subscriptionType << [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.EBM, SubscriptionType.CONTINUOUSCELLTRACE,
                             SubscriptionType.MOINSTANCE, SubscriptionType.CELLTRAFFIC, SubscriptionType.GPEH]
    }

    @Unroll
    def "When the deactivation times out for pmJob supported type : #subscriptionType and isMaster is false, subscription should remain unchanged"() {
        given: "Subscription exists in dps"
        subscriptionMO = dps.subscription().type(subscriptionType).name("Test").administrationState(AdministrationState.DEACTIVATING).
                taskStatus(TaskStatus.OK).nodes(nodes).build()
        ProcessType processType = getSubscriptionTypeProcessTypeMap().get(SubscriptionType.valueOf(subscriptionMO.getAttribute('type') as String))
        nodes.each {
            dps.pmJob().nodeName(it as ManagedObject).processType(processType).subscriptionId(subscriptionMO).status(PmJobStatus.ACTIVE).build()
        }
        and: "isMaster is set to false"
        listener.isMaster() >> false
        and: "Subscription deactivation is called"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
        deactivationEvent.execute(subscriptionMO.getPoId())

        when: "The tracker timer times out"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
        initiationDelayTimer.execute(null)

        then: "The subscription will remain unchanged"
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE))
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE))

        where:
        subscriptionType << [SubscriptionType.CTUM, SubscriptionType.UETRACE]
    }

    @Unroll
    def "On timeout execution for pmJob supported type : #subscriptionType and initiation time is still valid, subscription should remain unchanged"() {
        given: "Subscription exists in dps"
        subscriptionMO = dps.subscription().type(subscriptionType).name("Test").administrationState(AdministrationState.DEACTIVATING).
                taskStatus(TaskStatus.OK).nodes(nodes).build()
        ProcessType processType = getSubscriptionTypeProcessTypeMap().get(SubscriptionType.valueOf(subscriptionMO.getAttribute('type') as String))
        nodes.each {
            dps.pmJob().nodeName(it as ManagedObject).processType(processType).subscriptionId(subscriptionMO).status(PmJobStatus.ACTIVE).build()
        }
        and: "Initiation Tracker is created with valid initiation time"
        timeGenerator.currentTimeMillis() >> System.currentTimeMillis()
        initiationTrackerCache.startTrackingDeactivation(subscriptionMO.poId as String, AdministrationState.DEACTIVATING.name(), fdnMap)
        and: "Subscription deactivation is called"
        deactivationEvent.execute(subscriptionMO.getPoId())
        listener.isMaster() >> true

        when: "The tracker timer times out"
        initiationDelayTimer.execute(null)

        then: "The subscription will remain unchanged"
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE))
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE))

        where:
        subscriptionType << [SubscriptionType.CTUM, SubscriptionType.UETRACE]

    }

    def addScanners() {
        ProcessType processType = getSubscriptionTypeProcessTypeMap().get(SubscriptionType.valueOf(subscriptionMO.getAttribute('type') as String))
        String scannerName = getProcessTypeScannerNameMap().get(processType)
        scanners = nodes.collect {
            dps.scanner().
                    scannerType(Scanner.PmicScannerType.PMICScannerInfo).
                    nodeName(it.getName()).
                    name(scannerName).
                    processType(processType).
                    status(ScannerStatus.ACTIVE).
                    subscriptionId(subscriptionMO).
                    build()
        }
    }

    def addUeScanners() {
        ProcessType processType = getSubscriptionTypeProcessTypeMap().get(SubscriptionType.valueOf(subscriptionMO.getAttribute('type') as String))
        String scannerName = getProcessTypeScannerNameMap().get(processType)

        scanners = nodes.collect {
            dps.scanner().
                    scannerType(Scanner.PmicScannerType.PMICUeScannerInfo).
                    nodeName(it.getName()).
                    name(scannerName).
                    processType(processType).
                    status(ScannerStatus.ACTIVE).
                    subscriptionId(subscriptionMO).
                    build()
        }
    }

    Map<String, String> scannerName = ["StatisticalSubscription"        : "USERDEF.Test.Cont.Y.Stats",
                                       "CellTraceSubscription"          : "PREDEF.10003.CELLTRACE",
                                       "EbmSubscription"                : "PREDEF.EBMLOG.EBM",
                                       "ContinuousCellTraceSubscription": "PREDEF.10005.CELLTRACE",
                                       "MoinstanceSubscription"         : "USERDEF.Test.Cont.Y.Stats",
                                       "CellTrafficSubscription"        : "PREDEF.20000.CTR"
    ]
}
