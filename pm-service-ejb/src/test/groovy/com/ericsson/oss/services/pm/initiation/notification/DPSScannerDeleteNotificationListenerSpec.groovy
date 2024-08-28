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
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionOperationConstant.*

import spock.lang.Unroll

import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectDeletedEvent
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.pm.collection.api.FileCollectionTaskManagerLocal
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper
import com.ericsson.oss.services.pm.collection.cache.FileCollectionScheduledRecoveryCacheWrapper
import com.ericsson.oss.services.pm.collection.notification.DpsScannerDeleteNotificationListener
import com.ericsson.oss.services.pm.collection.notification.handlers.ScannerOperationVO
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache

class DPSScannerDeleteNotificationListenerSpec extends SkeletonSpec {

    @ObjectUnderTest
    DpsScannerDeleteNotificationListener dpsScannerDeleteNotificationListener;

    @ImplementationInstance
    TimerService timerService = Mock(TimerService)

    @Inject
    private FileCollectionScheduledRecoveryCacheWrapper fileCollectionScheduledRecoveryCache;

    @Inject
    private FileCollectionActiveTaskCacheWrapper fileCollectionActiveTasksCache

    @Inject
    private FileCollectionTaskManagerLocal fileCollectionTaskManager;

    @Inject
    private PMICInitiationTrackerCache initiationTrackerCache

    private static final NODE_NAME_1 = "LTE01ERBS0001";
    private static final NODE_NAME_2 = "LTE01ERBS0002";

    private ManagedObject subscriptionMO
    private List<ManagedObject> nodes
    private ManagedObject scannerMo

    def setup() {
        subscriptionMO = statisticalSubscriptionBuilder.name("Subscription1").administrativeState(AdministrationState.ACTIVE).taskStatus(TaskStatus.OK).build();
        nodes = [nodeUtil.builder(NODE_NAME_1).build()]
        dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0))
    }

    @Unroll
    def "When a scanner deletion notification is received for #processType and if there are no more active scanners on node"(String processType) {

        DpsObjectDeletedEvent deleteObjectEvent = deleteScannerObjectEvent(nodes.get(0), subscriptionMO, processType)

        given: "dps notification for scanner deletion is created and first timer with required rop period and then timer with the corresponding subscription id is returned"
        initiationTrackerCache.startTrackingDeactivation(subscriptionMO.poId as String, AdministrationState.DEACTIVATING.name(), [(nodes.get(0).getFdn()): ERBS])
        fileCollectionActiveTasksCache.addProcessRequest(createProcessRequestVO(nodes.get(0), subscriptionMO, processType));

        when: "received the scanner deletion notification "
        dpsScannerDeleteNotificationListener.onEvent(deleteObjectEvent)

        then: "ProcessRequestVO is removed from fileCollectionActiveTasksCache"
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) - 1

        and: "ProcessRequestVO is added in fileCollectionScheduledRecoveryCache"
        fileCollectionScheduledRecoveryCache.size() == old(fileCollectionScheduledRecoveryCache.size()) + 1

        and: "Remove subscription from PMICInitiationTrackerCache"
        initiationTrackerCache.getTracker(subscriptionMO.getPoId() as String) == null

        where:
        processType << ["STATS", "PREDEF_STATS", "NORMAL_PRIORITY_CELLTRACE", "HIGH_PRIORITY_CELLTRACE", "EVENTJOB", "CTUM", "UETRACE", "UETR", "CTR",
                        "REGULAR_GPEH", "OPTIMIZER_GPEH", "CELLRELATION"]
    }

    @Unroll
    def "When the scanner deletion notification is received for #processType and if there are more active scanners present on node"(String processType) {

        ManagedObject subscriptionMo2 = statisticalSubscriptionBuilder.name("Subscription2").administrativeState(AdministrationState.ACTIVE).taskStatus(TaskStatus.OK).build();
        scannerMo = scannerUtil.builder(getScannerName(subscriptionMo2.getName(), processType), NODE_NAME_1).subscriptionId(subscriptionMo2.getPoId()).status(ACTIVE)
                .processType(processType).build()
        DpsObjectDeletedEvent deleteObjectEvent = deleteScannerObjectEvent(nodes.get(0), subscriptionMO, processType)
        ProcessRequestVO requestVo1 = createProcessRequestVO(nodes.get(0), subscriptionMO, processType)
        ProcessRequestVO requestVo2 = createProcessRequestVO(nodes.get(0), subscriptionMo2, processType)

        given: "fileCollectionActiveTasksCache is updated with ProcessRequestVO object"
        initiationTrackerCache.startTrackingDeactivation(subscriptionMO.poId as String, AdministrationState.DEACTIVATING.name(), [(NE_PREFIX + NODE_NAME_1): ERBS])
        fileCollectionActiveTasksCache.addProcessRequest(requestVo1);
        fileCollectionActiveTasksCache.addProcessRequest(requestVo2)
        when: "received the scanner deletion notification "
        dpsScannerDeleteNotificationListener.onEvent(deleteObjectEvent)

        then: "No stopFileCollection is called"
        0 * fileCollectionTaskManager.stopFileCollection(requestVo1);

        and: "No ProcessRequestVO to be removed from fileCollectionActiveTasksCache"
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size())

        and: "No ProcessRequestVO to be added in fileCollectionScheduledRecoveryCache"
        fileCollectionScheduledRecoveryCache.size() == old(fileCollectionScheduledRecoveryCache.size())

        and: "Remove subscription from PMICInitiationTrackerCache"
        initiationTrackerCache.getTracker(subscriptionMO.getPoId() as String) == null
        where:
        processType << ["STATS", "PREDEF_STATS", "NORMAL_PRIORITY_CELLTRACE", "HIGH_PRIORITY_CELLTRACE", "EVENTJOB", "CTUM", "UETRACE", "UETR", "CTR",
                        "REGULAR_GPEH", "OPTIMIZER_GPEH", "CELLRELATION"]
    }

    @Unroll
    def "When a subscription has two nodes, and scanner deletion notification is received for #processType for one node and if there are no more active scanners on that node "(String processType) {

        dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodeUtil.builder(NODE_NAME_2).build())
        DpsObjectDeletedEvent deleteObjectEvent = deleteScannerObjectEvent(nodes.get(0), subscriptionMO, processType)

        given: "fileCollectionActiveTasksCache is updated with ProcessRequestVO object"
        initiationTrackerCache.startTrackingDeactivation(subscriptionMO.poId as String, AdministrationState.DEACTIVATING.name(), [(NE_PREFIX + NODE_NAME_1): ERBS, (NE_PREFIX + NODE_NAME_2): ERBS])
        fileCollectionActiveTasksCache.addProcessRequest(createProcessRequestVO(nodes.get(0), subscriptionMO, processType));

        when: "received the scanner deletion notification "
        dpsScannerDeleteNotificationListener.onEvent(deleteObjectEvent)

        then: "ProcessRequestVO is removed from fileCollectionActiveTasksCache"
        fileCollectionActiveTasksCache.size() == old(fileCollectionActiveTasksCache.size()) - 1

        and: "ProcessRequestVO is added in fileCollectionScheduledRecoveryCache"
        fileCollectionScheduledRecoveryCache.size() == old(fileCollectionScheduledRecoveryCache.size()) + 1

        and: "Subscription will not be removed from initiation cache as another node is present"
        initiationTrackerCache.getTracker(subscriptionMO.getPoId() as String) != null
        where:
        processType << ["STATS", "PREDEF_STATS", "NORMAL_PRIORITY_CELLTRACE", "HIGH_PRIORITY_CELLTRACE", "EVENTJOB", "CTUM", "UETRACE", "UETR", "CTR",
                        "REGULAR_GPEH", "OPTIMIZER_GPEH", "CELLRELATION"]
    }

    private ProcessRequestVO createProcessRequestVO(ManagedObject nodeMo, ManagedObject subscriptionMo, String processType) {
        String scannerFdn = nodeMo.getFdn() + ",PMICScannerInfo=" + getScannerName(subscriptionMo.getName(), processType)
        Integer ropPeriodInSeconds = 900
        String scannerStatus = ACTIVE
        String subscriptionId = String.valueOf(subscriptionMO.poId)
        ScannerOperationVO scannerVO = new ScannerOperationVO(scannerStatus, subscriptionId, ropPeriodInSeconds, scannerFdn, processType)
        ProcessRequestVO requestVo = new ProcessRequestVO.ProcessRequestVOBuilder(nodes.get(0).fdn, scannerVO.getRopTimeInSeconds(), scannerVO.getProcessType()).build()
        return requestVo
    }

    private DpsObjectDeletedEvent deleteScannerObjectEvent(ManagedObject nodeMo, ManagedObject subscriptionMo, String processType) {
        String scannerFdn = nodeMo.getFdn() + COMMA + SCANNER_PREFIX + getScannerName(subscriptionMo.getName(), processType)
        long id = 10010
        String bucketName = LIVE;
        boolean mibRoot = false;
        Map<String, Object> scannerAttr = new HashMap<String, Object>()
        int rop = 900
        scannerAttr.put(SCANNER_ROP_PERIOD_ATTRIBUTE, rop)
        scannerAttr.put(SCANNER_STATUS_ATTRIBUTE, ACTIVE)
        scannerAttr.put(SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE, String.valueOf(subscriptionMO.poId))
        scannerAttr.put(SCANNER_FILE_COLLECTION_ENABLED_ATTRIBUTE, true)
        scannerAttr.put(SCANNER_NAME_ATTRIBUTE, getScannerName(subscriptionMo.getName(), processType))
        scannerAttr.put(PROCESS_TYPE_ATTRIBUTE, processType)
        return new DpsObjectDeletedEvent(SCANNER_MODEL_NAME_SPACE, SCANNER_MODEL_NAME, SCANNER_MODEL_VERSION, id, scannerFdn, bucketName, mibRoot, scannerAttr);
    }

    private static String getScannerName(String subscriptionName, String processType) {
        return "USERDEF." + subscriptionName + ".Cont.Y." + processType
    }
}
