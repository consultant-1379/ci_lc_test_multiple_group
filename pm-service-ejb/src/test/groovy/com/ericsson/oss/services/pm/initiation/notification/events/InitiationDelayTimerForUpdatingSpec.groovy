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
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.UETR
import static com.ericsson.oss.services.pm.initiation.cache.constants.InitiationConstants.CACHED_RESOURCES_TO_BE_ACTIVATED
import static com.ericsson.oss.services.pm.initiation.cache.constants.InitiationConstants.CACHED_RESOURCES_TO_BE_DEACTIVATED
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE

import org.mockito.Mockito
import spock.lang.Unroll

import javax.inject.Inject
import java.util.concurrent.TimeUnit

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.ScannerDao
import com.ericsson.oss.pmic.dto.scanner.Scanner
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo
import com.ericsson.oss.pmic.dto.subscription.enums.*
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.schedulers.InitiationDelayTimer
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener
import com.ericsson.oss.services.pm.time.TimeGenerator

class InitiationDelayTimerForUpdatingSpec extends SkeletonSpec {

    static final def nodeNames = [NODE_NAME_1, NODE_NAME_2]

    @ObjectUnderTest
    InitiationDelayTimer initiationDelayTimer

    @Inject
    private PMICInitiationTrackerCache initiationTrackerCache

    @ImplementationInstance
    TimeGenerator timeGenerator = Mockito.mock(TimeGenerator.class)

    @ImplementationInstance
    MembershipListener membershipListener = Mock(MembershipListener)

    @Inject
    ScannerDao scannerDao;

    def nodes = []
    def scanners = []
    static final currentTime = System.currentTimeMillis()
    static final pastTime = currentTime - TimeUnit.MINUTES.toMillis(30)

    ManagedObject subscriptionMO
    Map<String, Map<String, String>> fdnMap

    def setup() {
        membershipListener.isMaster() >> true
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
        nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build()]
        fdnMap = [(CACHED_RESOURCES_TO_BE_DEACTIVATED): [(NETWORK_ELEMENT_1): 'ERBS', (NETWORK_ELEMENT_2): 'ERBS'],
                  (CACHED_RESOURCES_TO_BE_ACTIVATED)  : [(NETWORK_ELEMENT_3): 'ERBS']]
    }

    @Unroll
    def "When the update times out for pmJob supported type : #subscriptionType, with #pmJobActive pmJobs, the subscription goes to #expectedAdminState with #expectedTaskStatus task status"() {
        given: "Subscription exists in dps"
        subscriptionMO = dps.subscription().type(subscriptionType).name("Test").administrationState(AdministrationState.UPDATING).
                operationalState(OperationalState.RUNNING).taskStatus(TaskStatus.OK).
                build()
        ProcessType processType = getSubscriptionTypeProcessTypeMap().get(SubscriptionType.valueOf(subscriptionMO.getAttribute('type') as String))
        nodes.each { it ->
            dps.pmJob().nodeName(it as ManagedObject).processType(processType).subscriptionId(subscriptionMO).status(pmJobActive).build()
        }
        and: "Initiation Tracker is created with expired initiation time"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
        if (nodeList == CACHED_RESOURCES_TO_BE_DEACTIVATED) {
            initiationTrackerCache.startTrackingDeactivation(subscriptionMO.poId as String, AdministrationState.UPDATING.name(), fdnMap.get(nodeList))
        } else {
            initiationTrackerCache.startTrackingActivation(subscriptionMO.poId as String, AdministrationState.UPDATING.name(), fdnMap.get(nodeList))
        }
        when: "The tracker timer times out"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
        initiationDelayTimer.execute(null)

        then: "The subscription will go to INACTIVE/OK"
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == expectedAdminState
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == expectedTaskStatus
        and: "Initiation Tracker will be removed from cache"
        initiationTrackerCache.getTracker(subscriptionMO.poId as String) == null

        where:
        subscriptionType         | nodeList                           | expectedAdminState | expectedTaskStatus | pmJobActive
        SubscriptionType.CTUM    | CACHED_RESOURCES_TO_BE_DEACTIVATED | INACTIVE           | ERROR              | "ACTIVE"
        SubscriptionType.UETRACE | CACHED_RESOURCES_TO_BE_DEACTIVATED | INACTIVE           | ERROR              | "ACTIVE"
        SubscriptionType.CTUM    | CACHED_RESOURCES_TO_BE_ACTIVATED   | ACTIVE             | OK                 | "ACTIVE"
        SubscriptionType.UETRACE | CACHED_RESOURCES_TO_BE_ACTIVATED   | ACTIVE             | OK                 | "ACTIVE"
        SubscriptionType.CTUM    | CACHED_RESOURCES_TO_BE_DEACTIVATED | INACTIVE           | OK                 | "INACTIVE"
        SubscriptionType.UETRACE | CACHED_RESOURCES_TO_BE_DEACTIVATED | INACTIVE           | OK                 | "INACTIVE"
        SubscriptionType.CTUM    | CACHED_RESOURCES_TO_BE_ACTIVATED   | ACTIVE             | ERROR              | "INACTIVE"
        SubscriptionType.UETRACE | CACHED_RESOURCES_TO_BE_ACTIVATED   | ACTIVE             | ERROR              | "INACTIVE"
    }

    @Unroll
    def "When the update times out for pmJob supported type : #subscriptionType, when isMaster is false should do nothing"() {
        given: "Subscription exists in dps"
        subscriptionMO = dps.subscription().type(subscriptionType).name("Test").administrationState(AdministrationState.UPDATING).
                operationalState(OperationalState.RUNNING).taskStatus(TaskStatus.OK).
                build()
        ProcessType processType = getSubscriptionTypeProcessTypeMap().get(SubscriptionType.valueOf(subscriptionMO.getAttribute('type') as String))
        nodes.each { it ->
            dps.pmJob().nodeName(it as ManagedObject).processType(processType).subscriptionId(subscriptionMO).status("INACTIVE").build()
        }
        and: "Initiation Tracker is created with expired initiation time"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
        initiationTrackerCache.startTrackingDeactivation(subscriptionMO.poId as String, AdministrationState.UPDATING.name(), fdnMap.get(CACHED_RESOURCES_TO_BE_DEACTIVATED))

        when: "The tracker timer times out"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
        initiationDelayTimer.execute(null)

        then: "The subscription state will be unchanged"
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE))
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE))
        and: "Initiation Tracker will be unchanged"
        initiationTrackerCache.getTracker(subscriptionMO.poId as String).getTotalAmountOfReceivedNotifications() == 0
        and: "isMaster is set to false"
        membershipListener.isMaster() >> false

        where:
        subscriptionType << [SubscriptionType.CTUM, SubscriptionType.UETRACE]
    }

    @Unroll
    def "When the update times out for pmJob supported type : #subscriptionType, when initiation time is still valid should do nothing"() {
        given: "Subscription exists in dps"
        subscriptionMO = dps.subscription().type(subscriptionType).name("Test").administrationState(AdministrationState.UPDATING).
                operationalState(OperationalState.RUNNING).taskStatus(TaskStatus.OK).
                build()
        ProcessType processType = getSubscriptionTypeProcessTypeMap().get(SubscriptionType.valueOf(subscriptionMO.getAttribute('type') as String))
        nodes.each { it ->
            dps.pmJob().nodeName(it as ManagedObject).processType(processType).subscriptionId(subscriptionMO).status("INACTIVE").build()
        }
        and: "Initiation Tracker is created with valid initiation time"
        initiationTrackerCache.startTrackingDeactivation(subscriptionMO.poId as String, AdministrationState.UPDATING.name(), fdnMap.get(CACHED_RESOURCES_TO_BE_DEACTIVATED))

        when: "The tracker timer times out"
        initiationDelayTimer.execute(null)

        then: "The subscription state will be unchanged"
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE))
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE))
        and: "Initiation Tracker will be unchanged"
        initiationTrackerCache.getTracker(subscriptionMO.poId as String).getTotalAmountOfReceivedNotifications() == 0

        where:
        subscriptionType << [SubscriptionType.CTUM, SubscriptionType.UETRACE]
    }

    @Unroll
    def "When the update times out for #subscriptionType, the subscription goes to Inactive Ok when all nodes have been removed and the scanners are removed from dps"() {
        given: "Subscription exists in dps"

        subscriptionMO = dps.subscription().type(SubscriptionType.UETR).name("Test").administrationState(AdministrationState.UPDATING).
                operationalState(OperationalState.RUNNING).taskStatus(TaskStatus.OK).
                events(new EventInfo("group", "name")).ueInfoList(new UeInfo(UeType.IMEI, "214256543")).
                build()
        addScanners()
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)

        and: "Initiation Tracker is created with expired initiation time"
        initiationTrackerCache.startTrackingDeactivation(subscriptionMO.poId as String, AdministrationState.UPDATING.name(), fdnMap.get(CACHED_RESOURCES_TO_BE_DEACTIVATED))

        when: "The tracker timer times out"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
        initiationDelayTimer.execute(null)

        then: "The subscription will go to INACTIVE/OK"
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == INACTIVE
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == OK
        and: "Removed Node scanner will update to unknown in DPS"
        scannerDao.findAllBySubscriptionId(subscriptionMO.poId).each {
            !it.fdn.contains(NODE_NAME_1)
        }
        and: "Initiation Tracker will be removed from cache"
        initiationTrackerCache.getTracker(subscriptionMO.poId as String) == null

        where:
        subscriptionType << [SubscriptionType.UETR, SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.EBM, SubscriptionType.CONTINUOUSCELLTRACE,
                             SubscriptionType.MOINSTANCE, SubscriptionType.CELLTRAFFIC, SubscriptionType.GPEH]
    }

    @Unroll
    def "When the update times out for #subscriptionType, the subscription goes to Active ERROR because newly added node scanner is not Active "() {
        given: "Subscription exists in dps"

        subscriptionMO = dps.subscription().type(SubscriptionType.UETR).name("Test").administrationState(AdministrationState.UPDATING).
                operationalState(OperationalState.RUNNING).taskStatus(TaskStatus.OK).
                events(new EventInfo("group", "name")).ueInfoList(new UeInfo(UeType.IMEI, "214256543")).nodes(nodes).
                build()

        nodes += nodeUtil.builder(NODE_NAME_3).build()
        addScanners()
        addScanner(NODE_NAME_3, ScannerStatus.INACTIVE)
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)

        and: "Initiation Tracker is created with expired initiation time"
        initiationTrackerCache.startTrackingActivation(subscriptionMO.poId as String, AdministrationState.UPDATING.name(), fdnMap.get(
                CACHED_RESOURCES_TO_BE_ACTIVATED))

        when: "The tracker timer times out"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
        initiationDelayTimer.execute(null)

        then: "The subscription will go to ACTIVE/OK"
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == ACTIVE
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == ERROR
        and: "Removed Node scanner will update to unknown in DPS"
        scannerDao.findAllBySubscriptionId(subscriptionMO.poId).each {
            it.fdn.contains(NODE_NAME_3) && it.status == ScannerStatus.UNKNOWN
        }
        and: "Initiation Tracker will be removed from cache"
        initiationTrackerCache.getTracker(subscriptionMO.poId as String) == null

        where:
        subscriptionType << [SubscriptionType.UETR, SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.EBM,
                             SubscriptionType.CONTINUOUSCELLTRACE,
                             SubscriptionType.MOINSTANCE, SubscriptionType.CELLTRAFFIC, SubscriptionType.GPEH]
    }

    @Unroll
    def "When the update times out for #subscriptionType, the subscription goes to Active Ok when not all nodes have been removed "() {
        given: "Subscription exists in dps"

        subscriptionMO = dps.subscription().type(SubscriptionType.UETR).name("Test").administrationState(AdministrationState.UPDATING).
                operationalState(OperationalState.RUNNING).taskStatus(TaskStatus.OK).
                events(new EventInfo("group", "name")).ueInfoList(new UeInfo(UeType.IMEI, "214256543")).nodes(nodes).
                build()

        addScanners()

        and: "Initiation Tracker is created with expired initiation time"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
        fdnMap = [(CACHED_RESOURCES_TO_BE_DEACTIVATED): [(NETWORK_ELEMENT_1): 'ERBS']]
        initiationTrackerCache.startTrackingDeactivation(subscriptionMO.poId as String, AdministrationState.UPDATING.name(), fdnMap.get(CACHED_RESOURCES_TO_BE_DEACTIVATED))
        when: "The tracker timer times out"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
        initiationDelayTimer.execute(null)

        then: "The subscription will go to ACTIVE/OK"
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == ACTIVE
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == OK
        and: "Removed Node scanner will update to unknown in DPS"
        scannerDao.findAllBySubscriptionId(subscriptionMO.poId).each {
            !it.fdn.contains(NODE_NAME_1)
        }
        and: "Initiation Tracker will be removed from cache"
        initiationTrackerCache.getTracker(subscriptionMO.poId as String) == null
        cleanup:
        initiationTrackerCache.stopTracking(subscriptionMO.poId as String)
        where:
        subscriptionType << [SubscriptionType.UETR, SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.EBM, SubscriptionType.CONTINUOUSCELLTRACE,
                             SubscriptionType.MOINSTANCE, SubscriptionType.CELLTRAFFIC, SubscriptionType.GPEH]
    }

    @Unroll
    def "When the update times out for #subscriptionType,and isMaster is false should do nothing "() {
        given: "Subscription exists in dps"

        subscriptionMO = dps.subscription().type(SubscriptionType.UETR).name("Test").administrationState(AdministrationState.UPDATING).
                operationalState(OperationalState.RUNNING).taskStatus(TaskStatus.OK).
                events(new EventInfo("group", "name")).ueInfoList(new UeInfo(UeType.IMEI, "214256543")).nodes(nodes).
                build()

        addScanners()
        and: "Initiation Tracker is created with expired initiation time"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
        initiationTrackerCache.startTrackingDeactivation(subscriptionMO.poId as String, AdministrationState.UPDATING.name(), fdnMap.get(
                CACHED_RESOURCES_TO_BE_DEACTIVATED))

        when: "The tracker timer times out"
        Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
        initiationDelayTimer.execute(null)

        then: "The subscription state will be unchanged"
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE))
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        )
        and: "The scanners will be remain from DPS"
        scanners.each {
            configurableDps.build().getLiveBucket().findMoByFdn(it.fdn).each { it in scanners }
        }
        and: "Initiation Tracker will remain in cache"
        initiationTrackerCache.getTracker(subscriptionMO.poId as String) != null
        and: "isMaster is set to false"
        membershipListener.isMaster() >> false

        where:
        subscriptionType << [SubscriptionType.UETR, SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.EBM,
                             SubscriptionType.CONTINUOUSCELLTRACE,
                             SubscriptionType.MOINSTANCE, SubscriptionType.CELLTRAFFIC, SubscriptionType.GPEH]
    }

    @Unroll
    def "When the update times out for #subscriptionType, and initiation time is still valid should do nothing"() {
        given: "Subscription exists in dps"

        subscriptionMO = dps.subscription().type(SubscriptionType.UETR).name("Test").administrationState(AdministrationState.UPDATING).
                operationalState(OperationalState.RUNNING).taskStatus(TaskStatus.OK).
                events(new EventInfo("group", "name")).ueInfoList(new UeInfo(UeType.IMEI, "214256543")).nodes(nodes).
                build()
        addScanners()

        and: "Initiation Tracker is created with valid initiation time"
        initiationTrackerCache.startTrackingDeactivation(subscriptionMO.poId as String, AdministrationState.UPDATING.name(), fdnMap.get(CACHED_RESOURCES_TO_BE_DEACTIVATED))

        when: "The tracker timer times out"
        initiationDelayTimer.execute(null)

        then: "The subscription state will be unchanged"
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE))
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == old(subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE))
        and: "The scanners will be remain from DPS"
        scanners.each {
            configurableDps.build().getLiveBucket().findMoByFdn(it.fdn).each { it in scanners }
        }
        and: "Initiation Tracker will remain in cache"
        initiationTrackerCache.getTracker(subscriptionMO.poId as String) != null

        where:
        subscriptionType << [SubscriptionType.UETR, SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.EBM, SubscriptionType.CONTINUOUSCELLTRACE,
                             SubscriptionType.MOINSTANCE, SubscriptionType.CELLTRAFFIC, SubscriptionType.GPEH]
    }

    def addScanners() {
        scanners = nodeNames.collect {
            addScanner(it, ScannerStatus.ACTIVE)
        }
        (0..(scanners.size() - 1)).each {
            dpsUtils.addAssociation(nodes.get(it), 'scanners', scanners.get(it))
        }
    }

    private ManagedObject addScanner(String nodeName, ScannerStatus status) {
        ProcessType processType = getSubscriptionTypeProcessTypeMap().get(SubscriptionType.valueOf(subscriptionMO.getAttribute('type') as String))
        String scannerName = getProcessTypeScannerNameMap().get(processType)
        if (processType == UETR) {
            return dps.scanner().
                    scannerType(Scanner.PmicScannerType.PMICUeScannerInfo).
                    nodeName(nodeName).
                    name(scannerName).
                    processType(processType).
                    status(status).
                    subscriptionId(subscriptionMO).
                    build()
        } else {
            return dps.scanner().
                    scannerType(Scanner.PmicScannerType.PMICScannerInfo).
                    nodeName(nodeName).
                    name(scannerName).
                    processType(processType).
                    status(status).
                    subscriptionId(subscriptionMO).
                    build()
        }
    }

}
