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


import static com.ericsson.oss.pmic.cdi.test.util.Constants.ACTIVE
import static com.ericsson.oss.pmic.cdi.test.util.Constants.ERROR
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NETWORK_ELEMENT_1
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NETWORK_ELEMENT_2
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_1
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_2
import static com.ericsson.oss.pmic.cdi.test.util.Constants.OK
import static com.ericsson.oss.pmic.cdi.test.util.Constants.SGSN_NODE_NAME_1
import static com.ericsson.oss.pmic.cdi.test.util.Constants.SGSN_NODE_NAME_2
import static com.ericsson.oss.services.pm.initiation.common.Constants.PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE

import org.mockito.Mockito
import spock.lang.Unroll

import javax.cache.Cache
import javax.ejb.TimerService
import javax.inject.Inject
import java.util.concurrent.TimeUnit

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.PmJobDao
import com.ericsson.oss.pmic.dao.ScannerDao
import com.ericsson.oss.pmic.dto.pmjob.PmJob
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus
import com.ericsson.oss.pmic.dto.scanner.Scanner
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.MoinstanceInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.dto.subscription.enums.UeType
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.services.pm.common.systemdefined.SystemDefinedSubscriptionManager
import com.ericsson.oss.services.pm.initiation.ejb.GroovyTestUtils
import com.ericsson.oss.services.pm.initiation.schedulers.InitiationDelayTimer
import com.ericsson.oss.services.pm.time.TimeGenerator

class InitiationDelayTimerForActivationSpec extends SkeletonSpec {

    @ObjectUnderTest
    private InitiationDelayTimer objectUnderTest

    @Inject
    private ActivationEvent activationEvent

    @Inject
    @NamedCache("PMICFileCollectionActiveTaskListCache")
    private Cache<String, Map<String, Object>> activeTaskCache

    @Inject
    private GroovyTestUtils testUtils

    @Inject
    PmJobDao pmJobDao

    @ImplementationInstance
    TimeGenerator timeGenerator = Mockito.mock(TimeGenerator.class)

    @ImplementationInstance
    TimerService timerService = Mock(TimerService)

    @Inject
    ScannerDao scannerDao

    @ImplementationInstance
    private SystemDefinedSubscriptionManager systemDefinedSubscriptionManager = Mock(SystemDefinedSubscriptionManager);

    private List<ManagedObject> nodes
    private ManagedObject subscriptionMO
    static final currentTime = System.currentTimeMillis()
    static final pastTime = currentTime - TimeUnit.MINUTES.toMillis(30)

    def setup() {
        timerService.getTimers() >> []
    }

    @Unroll
    def "When the activation times out, the #subscriptionType subscription goes to ACTIVE/ERROR if the scanners are INACTIVE"() {
        given: "Two nodes with pmFunction on attached to one subscription"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
            nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build()]
            subscriptionMO = dps.subscription().type(subscriptionType).name("Test").administrationState(AdministrationState.ACTIVATING).
                taskStatus(TaskStatus.OK).ueInfoList(new UeInfo(UeType.IMEI, "214256543")).nodes(nodes).build()

        and: "Scanners exist in DPS"
            addScanners(ScannerStatus.INACTIVE)

        when: "Subscription is activated"
            activationEvent.execute(subscriptionMO.getPoId())
        and: "The tracker timer times out"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
            objectUnderTest.execute(null)

        then: "The subscription will go to ACTIVE/ERROR"
            ACTIVE == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        and: "Both nodes will be removed from the tracker cache"
            0L == testUtils.getTotalNodesToBeActivated();

        where:
            subscriptionType << [SubscriptionType.UETR, SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.EBM,
                                 SubscriptionType.MOINSTANCE, SubscriptionType.CELLTRAFFIC, SubscriptionType.GPEH]
    }

    @Unroll
    def "When the activation times out, the #subscriptionType subscription goes to ACTIVE/ERROR if the pmJobs are INACTIVE"() {
        given: "Two nodes with pmFunction on attached to one subscription"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
            nodes = [nodeUtil.builder(SGSN_NODE_NAME_1).attributes(['neType': 'SGSN-MME']).build(), nodeUtil.builder(SGSN_NODE_NAME_2).attributes(['neType': 'SGSN-MME']).build()]
            subscriptionMO = dps.subscription().type(subscriptionType).name("Test").administrationState(AdministrationState.ACTIVATING).
                taskStatus(TaskStatus.OK).nodes(nodes).build()
        and: "PmJobs exist in DPS"
            ProcessType processType = getSubscriptionTypeProcessTypeMap().get(SubscriptionType.valueOf(subscriptionMO.getAttribute('type') as String))
            nodes.each {
                dps.pmJob().nodeName(it).processType(processType).subscriptionId(subscriptionMO).status(PmJobStatus.INACTIVE).build()
            }

        when: "Subscription is activated"
            activationEvent.execute(subscriptionMO.getPoId())
        and: "The tracker timer times out"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
            objectUnderTest.execute(null)

        then: "The subscription will go to ACTIVE/ERROR"
            ACTIVE == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        and: "Both nodes will be removed from the tracker cache"
            0L == testUtils.getTotalNodesToBeActivated()

        where:
            subscriptionType << [SubscriptionType.CTUM, SubscriptionType.UETRACE]
    }

    @Unroll
    def "When the activation times out, the #subscriptionType subscription goes to ACTIVE/OK if the scanners are ACTIVE"() {
        given: "Two nodes with pmFunction on attached to one subscription"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
            nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build()]
            subscriptionMO = dps.subscription()
                .type(subscriptionType)
                .name(subscriptionName)
                .administrationState(AdministrationState.ACTIVATING)
                .taskStatus(TaskStatus.OK)
                .nodes(nodes)
                .outputMode(OutputModeType.FILE_AND_STREAMING)
                .events([new EventInfo('event', 'EventGroup')])
                .build()
        and: "Scanners exist in DPS"
            addScanners(ScannerStatus.ACTIVE)

        when: "Subscription is activated"
            activationEvent.execute(subscriptionMO.getPoId())
        and: "Ebm Sets Active scanners to unknown when creating mediation task request so setting scanner back to active for test purposes"
            (subscriptionMO.getAttribute('type') == 'EBM') ? updateScannerStatus() : null
        and: "The tracker timer times out"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
            objectUnderTest.execute(null)

        then: "The subscription will go to ACTIVE/OK"
            ACTIVE == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            OK == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        and: "Both nodes will be removed from the tracker cache"
            0L == testUtils.getTotalNodesToBeActivated();
        and: "The active task cache will contain the process requests but no error entries"
            2 == activeTaskCache.size()
            null == activeTaskCache.get(NETWORK_ELEMENT_1)
            null == activeTaskCache.get(NETWORK_ELEMENT_2)

        where:
            subscriptionType                     | subscriptionName
            SubscriptionType.CONTINUOUSCELLTRACE | PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME
            SubscriptionType.CELLTRACE           | 'Test'
    }

    @Unroll
    def "When the activation times out, the #subscriptionType subscription goes to ACTIVE/OK if the pmJobs are ACTIVE"() {
        given: "Two nodes with pmFunction on attached to one subscription"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
            nodes = [nodeUtil.builder(SGSN_NODE_NAME_1).attributes(['neType': 'SGSN-MME']).build(),
                     nodeUtil.builder(SGSN_NODE_NAME_2).attributes(['neType': 'SGSN-MME']).build()]
            subscriptionMO = dps.subscription().type(subscriptionType).name("Test").administrationState(AdministrationState.ACTIVATING).
                taskStatus(TaskStatus.OK).nodes(nodes).build()
        and: "PmJobs exist in DPS"
            ProcessType processType = getSubscriptionTypeProcessTypeMap().get(SubscriptionType.valueOf(subscriptionMO.getAttribute('type') as String))
            nodes.each {
                dps.pmJob().nodeName(it).processType(processType).subscriptionId(subscriptionMO).status(PmJobStatus.ACTIVE).build()
            }

        when: "Subscription is activated"
            activationEvent.execute(subscriptionMO.getPoId())
        and: "The tracker timer times out"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
            objectUnderTest.execute(null)

        then: "The subscription will go to ACTIVE/OK"
            ACTIVE == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            OK == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        and: "Both nodes will be removed from the tracker cache"
            0L == testUtils.getTotalNodesToBeActivated()

        where:
            subscriptionType << [SubscriptionType.CTUM, SubscriptionType.UETRACE]
    }

    @Unroll
    def "When the activation times out, the #subscriptionType subscription goes to ACTIVE/ERROR if the scanners don't exist"() {
        given: "Two nodes with pmFunction on attached to one subscription"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
            nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build()]
            subscriptionMO = dps.subscription().type(subscriptionType).name("Test").administrationState(AdministrationState.ACTIVATING).
                taskStatus(TaskStatus.OK).ueInfoList(new UeInfo(UeType.IMEI, "214256543")).nodes(nodes).build()

        when: "Subscription is activated"
            activationEvent.execute(subscriptionMO.getPoId())
        and: "The tracker timer times out"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
            objectUnderTest.execute(null)

        then: "The subscription will go to ACTIVE/ERROR"
            ACTIVE == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        and: "Both nodes will be removed from the tracker cache"
            0L == testUtils.getTotalNodesToBeActivated();

        where:
            subscriptionType << [SubscriptionType.UETR, SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.EBM,
                                 SubscriptionType.MOINSTANCE, SubscriptionType.CELLTRAFFIC, SubscriptionType.GPEH]
    }

    @Unroll
    def "When the activation times out, the #subscriptionType subscription goes to ACTIVE/ERROR and creates the pmJobs with UNKNOWN state if the pmJobs dont exist"() {
        given: "Two nodes with pmFunction on attached to one subscription"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
            nodes = [nodeUtil.builder(SGSN_NODE_NAME_1).attributes(['neType': 'SGSN-MME']).build(),
                     nodeUtil.builder(SGSN_NODE_NAME_2).attributes(['neType': 'SGSN-MME']).build()]
            subscriptionMO = dps.subscription().type(subscriptionType).name("Test").administrationState(AdministrationState.ACTIVATING).
                taskStatus(TaskStatus.OK).nodes(nodes).build()

        when: "Subscription is activated"
            activationEvent.execute(subscriptionMO.getPoId())
        and: "The tracker timer times out"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
            objectUnderTest.execute(null)

        then: "The subscription will go to ACTIVE/ERROR"
            ACTIVE == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        and: "Both nodes will be removed from the tracker cache"
            0L == testUtils.getTotalNodesToBeActivated()
        and: "PmJobs have been created with UNKNOWN state"
            List<PmJob> pmjobs = pmJobDao.findAllBySubscriptionId(subscriptionMO.getPoId())
            for (PmJob job : pmjobs) {
                assert job.getStatus() == PmJobStatus.UNKNOWN
            }

        where:
            subscriptionType << [SubscriptionType.CTUM, SubscriptionType.UETRACE]
    }

    @Unroll
    def "When the activation times out, for #subscriptionType and nodes with pm function off, errorNodeCache will be populated"() {
        given: "Two nodes, one with pm function off, attached to one subscription"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
            nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).pmEnabled(false).build()]
            subscriptionMO = dps.subscription().type(subscriptionType).name("Test").administrationState(AdministrationState.ACTIVATING).
                taskStatus(TaskStatus.OK).nodes(nodes).build()
        and: "Scanners exist in DPS"
            addScanners(ScannerStatus.INACTIVE)

        when: "Subscription is activated"
            activationEvent.execute(subscriptionMO.getPoId())
        and: "The tracker timer times out"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
            objectUnderTest.execute(null)

        then: "The subscription will go to ACTIVE/ERROR"
            ACTIVE == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        and: "Both nodes will be removed from the tracker cache"
            0L == testUtils.getTotalNodesToBeActivated();
        and: "The active task cache will contain 1 entry"
            1 == activeTaskCache.size()

        where:
            subscriptionType << [SubscriptionType.UETR, SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.EBM,
                                 SubscriptionType.MOINSTANCE, SubscriptionType.CELLTRAFFIC, SubscriptionType.GPEH]
    }

    @Unroll
    def "When the activation times out, the #subscriptionType subscription goes to ACTIVE/ERROR if the scanners was't exist, new UserDefined Stats scanner will be create for Counter Based Subscription"() {
        given: "Two nodes with pmFunction on attached to one subscription"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
            MoinstanceInfo moInstance_1 = new MoinstanceInfo()
            moInstance_1.setNodeName(NODE_NAME_1)
            moInstance_1.setMoInstanceName("Aal2PathVccTp=b1a3")
            MoinstanceInfo moInstance_2 = new MoinstanceInfo();
            moInstance_2.setMoInstanceName("Aal2PathVccTp=b1a3")
            moInstance_2.setNodeName(NODE_NAME_2)
            List<MoinstanceInfo> moInstanceList = [moInstance_1, moInstance_2]
            nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build()]
            subscriptionMO = dps.subscription().type(subscriptionType).name("Test").
                administrationState(AdministrationState.ACTIVATING).moInstances(moInstanceList).
                taskStatus(TaskStatus.OK).nodes(nodes).nodes(nodes).build()
        when: "Subscription is activated"
            activationEvent.execute(subscriptionMO.getPoId())
        and: "The tracker timer times out"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
            objectUnderTest.execute(null)

        then: "Unknown State User Defined scanner will be created and assigned a subscription id."
            scannerDao.countByNodeFdnAndProcessTypeAndSubscriptionIdAndScannerStatus([nodes.get(0).getFdn(), nodes.get(1).getFdn()],
                [ProcessType.STATS] as ProcessType[], [subscriptionMO.getPoId()], ScannerStatus.UNKNOWN) == 2
        and: "The subscription will go to ACTIVE/ERROR"
            ACTIVE == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        where:
            subscriptionType << [SubscriptionType.STATISTICAL, SubscriptionType.MOINSTANCE]
    }

    @Unroll
    def "When the activation times out, the #subscriptionType subscription goes to ACTIVE/ERROR if the scanners was't exist, new UserDefined Stats scanner should not create for SYSTEM DEF Subscription"() {
        given: "Two nodes with pmFunction on attached to one subscription"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
            systemDefinedSubscriptionManager.hasSubscriptionPredefinedScanner(_) >> true
            nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build()]
            subscriptionMO = dps.subscription().type(subscriptionType).name("Test").userType(UserType.SYSTEM_DEF).
                administrationState(AdministrationState.ACTIVATING).taskStatus(TaskStatus.OK).nodes(nodes).build()
        and: "Scanners exist in DPS on one node and not on the other one"
            scannerUtil.builder("PREDEF.STATS", NODE_NAME_1).subscriptionId(subscriptionMO.getPoId()).status(ScannerStatus.INACTIVE)
                .processType(ProcessType.STATS).build()
        when: "Subscription is activated"
            activationEvent.execute(subscriptionMO.getPoId())
        and: "The tracker timer times out"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
            objectUnderTest.execute(null)

        then: "Unknown State User Defined scanner will not be created."
            scannerDao.countByNodeFdnAndProcessTypeAndSubscriptionIdAndScannerStatus([nodes.get(0).getFdn(), nodes.get(1).getFdn()],
                [ProcessType.STATS] as ProcessType[], [subscriptionMO.getPoId()], ScannerStatus.UNKNOWN) == 0
        and: "The subscription will go to ACTIVE/ERROR"
            ACTIVE == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        where:
            subscriptionType << [SubscriptionType.STATISTICAL]
    }

    @Unroll
    def "When the activation times out, the #subscriptionType subscription goes to ACTIVE/ERROR if the scanners was't exist, new UserDefined Stats scanner should not create for Event Based Subscription"() {
        given: "Two nodes with pmFunction on attached to one subscription"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
            nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build()]
            subscriptionMO = dps.subscription().type(subscriptionType).name("Test").administrationState(AdministrationState.ACTIVATING).
                taskStatus(TaskStatus.OK).nodes(nodes).build()

        when: "Subscription is activated"
            activationEvent.execute(subscriptionMO.getPoId())
        and: "The tracker timer times out"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
            objectUnderTest.execute(null)

        then: "Unknown State User Defined scanner will not be created."
            scannerDao.countByNodeFdnAndProcessTypeAndSubscriptionIdAndScannerStatus([nodes.get(0).getFdn(), nodes.get(1).getFdn()],
                [ProcessType.STATS] as ProcessType[], [subscriptionMO.getPoId()], ScannerStatus.UNKNOWN) == 0
        and: "The subscription will go to ACTIVE/ERROR"
            ACTIVE == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
        where:
            subscriptionType << [SubscriptionType.UETR, SubscriptionType.CELLTRACE, SubscriptionType.EBM,
                                 SubscriptionType.CELLTRAFFIC, SubscriptionType.GPEH]
    }

    def "When the activation times out, the UETR subscription should go to ACTIVE/ERROR if the scanners was't exist and No NullPointer Exception thrown"() {
        given: "Two nodes with pmFunction on attached to one subscription"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(pastTime)
            nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build()]
            subscriptionMO = dps.subscription().type(SubscriptionType.UETR).name("Test").administrationState(AdministrationState.ACTIVATING).
                taskStatus(TaskStatus.OK).nodes(nodes).build()
        and:
            "Should not have Predefined scanner created."
            scannerDao.countByNodeFdnAndProcessTypeAndSubscriptionIdAndScannerStatus([nodes.get(0).getFdn(), nodes.get(1).getFdn()],
                [ProcessType.UETR] as ProcessType[], [subscriptionMO.getPoId()], ScannerStatus.UNKNOWN) == 0
        when: "Subscription is activated"
            activationEvent.execute(subscriptionMO.getPoId())
        and: "The tracker timer times out"
            Mockito.when(timeGenerator.currentTimeMillis()).thenReturn(currentTime)
            objectUnderTest.execute(null)

        then: "The subscription will go to ACTIVE/ERROR And No Exception thrown"
            ACTIVE == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            ERROR == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
    }

    def addScanners(ScannerStatus status) {
        ProcessType processType = getSubscriptionTypeProcessTypeMap().get(SubscriptionType.valueOf(subscriptionMO.getAttribute('type') as String))
        nodes.each {
            String scannerName = getProcessTypeScannerNameMap().get(processType)
            scannerUtil.builder(scannerName, it.name).subscriptionId(subscriptionMO.getPoId()).status(status)
                .processType(processType).build()
        }
    }

    def updateScannerStatus() {
        List<Scanner> scanners = scannerDao.findAll()
        for (Scanner scanner : scanners) {
            scanner.setStatus(ScannerStatus.ACTIVE)
            scannerDao.saveOrUpdate(scanner)
        }
    }

    def Map<SubscriptionType, ProcessType> getSubscriptionTypeProcessTypeMap() {
        return [(SubscriptionType.STATISTICAL)        : ProcessType.STATS,
                (SubscriptionType.MOINSTANCE)         : ProcessType.STATS,
                (SubscriptionType.UETR)               : ProcessType.UETR,
                (SubscriptionType.CELLTRACE)          : ProcessType.NORMAL_PRIORITY_CELLTRACE,
                (SubscriptionType.CONTINUOUSCELLTRACE): ProcessType.HIGH_PRIORITY_CELLTRACE,
                (SubscriptionType.EBM)                : ProcessType.EVENTJOB,
                (SubscriptionType.UETRACE)            : ProcessType.UETRACE,
                (SubscriptionType.CELLTRAFFIC)        : ProcessType.CTR,
                (SubscriptionType.GPEH)               : ProcessType.REGULAR_GPEH,
                (SubscriptionType.CTUM)               : ProcessType.CTUM]
    }

    def Map<ProcessType, String> getProcessTypeScannerNameMap() {
        return [(ProcessType.STATS)                    : 'USERDEF.Test.Cont.Y.Stats',
                (ProcessType.NORMAL_PRIORITY_CELLTRACE): 'PREDEF.10001.CELLTRACE',
                (ProcessType.HIGH_PRIORITY_CELLTRACE)  : 'PREDEF.10005.CELLTRACE',
                (ProcessType.EVENTJOB)                 : 'PREDEF.EBMLOG.EBM',
                (ProcessType.UETR)                     : 'PREDEF.10000.UETR',
                (ProcessType.REGULAR_GPEH)             : 'PREDEF.30000.GPEH',
                (ProcessType.CTR)                      : 'PREDEF.10005.CTR']
    }
}
