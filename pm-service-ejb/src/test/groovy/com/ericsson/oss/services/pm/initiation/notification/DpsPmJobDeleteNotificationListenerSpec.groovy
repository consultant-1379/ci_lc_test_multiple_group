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

import spock.lang.Unroll

import javax.cache.Cache
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectDeletedEvent
import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.services.model.ned.pm.function.FileCollectionState
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper
import com.ericsson.oss.services.pm.collection.notification.DpsPmJobDeleteNotificationListener
import com.ericsson.oss.services.pm.initiation.util.RopTime
import com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant

class DpsPmJobDeleteNotificationListenerSpec extends SkeletonSpec {

    @ObjectUnderTest
    def DpsPmJobDeleteNotificationListener deleteNotificationListener

    @Inject
    @NamedCache("PMICFileCollectionScheduledRecoveryCache")
    private Cache<String, Map<String, Object>> fileCollectionScheduleRecoveryCache;

    @Inject
    FileCollectionActiveTaskCacheWrapper fileCollectionActiveTaskCacheWrapper

    @Inject
    private PmFunctionEnabledWrapper pmFunctionCache;

    @Inject
    @NamedCache("FileCollectionActiveTaskCacheWrapper")
    private final Cache<String, Map<String, Object>> fileCollectionActiveTasksCache

    def String ACTIVE = 'ACTIVE'

    @Unroll
    def "DpsObjectDeletedEvent is received, it will not process request for #processType fdn not found in Cache"(String processType) {
        given: 'DpsObjectDeletedEvent is received'
        final DpsObjectDeletedEvent dpsObjectDeletedEvent = getObjectDeleteEvent(processType, ScannerStatus.ACTIVE.name())
        when: 'DpsObjectDeletedEvent is received'
        deleteNotificationListener.onEvent(dpsObjectDeletedEvent)
        then: 'It will not process because fdn not available'
        fileCollectionActiveTasksCache.size() == old((fileCollectionActiveTasksCache.size()))

        where:
        processType << ["STATS", "PREDEF_STATS", "NORMAL_PRIORITY_CELLTRACE", "HIGH_PRIORITY_CELLTRACE", "EVENTJOB", "CTUM", "UETRACE", "UETR", "CTR",
                        "REGULAR_GPEH", "OPTIMIZER_GPEH", "CELLRELATION"]
    }

    @Unroll
    def "DpsObjectDeletedEvent is received, it  will not process request for #processType while scanner status is INACTIVE"(String processType) {
        given: 'DpsObjectDeletedEvent is received'
        final DpsObjectDeletedEvent dpsObjectDeletedEvent = getObjectDeleteEvent(processType, ScannerStatus.INACTIVE.name())
        setupPMICInitiationResponseCache('NetworkElement=1', 60, processType)
        when: 'DpsObjectDeletedEvent is received'
        deleteNotificationListener.onEvent(dpsObjectDeletedEvent)
        then: 'It will not process because status is INACTIVE'
        fileCollectionScheduleRecoveryCache.size() == old(fileCollectionScheduleRecoveryCache.size())
        and:
        fileCollectionActiveTasksCache.size() == 0

        where:
        processType << ["STATS", "PREDEF_STATS", "NORMAL_PRIORITY_CELLTRACE", "HIGH_PRIORITY_CELLTRACE", "EVENTJOB", "CTUM", "UETRACE", "UETR", "CTR",
                        "REGULAR_GPEH", "OPTIMIZER_GPEH", "CELLRELATION"]
    }

    @Unroll
    def "DpsObjectDeletedEvent is received, it process request for #processType by adding it to fileCollectionScheduledRecoveryCache"(String processType) {
        given: 'DpsObjectDeletedEvent is received'
        final DpsObjectDeletedEvent dpsObjectDeletedEvent = getObjectDeleteEvent(processType, ScannerStatus.ACTIVE.name())
        setupPMICInitiationResponseCache('NetworkElement=1', 60, processType)
        when: 'DpsObjectDeletedEvent is received'
        deleteNotificationListener.onEvent(dpsObjectDeletedEvent)
        then: 'It will process request and cache will b updated with one task '
        fileCollectionScheduleRecoveryCache.size() == old((fileCollectionScheduleRecoveryCache.size() + 1))
        and:
        fileCollectionScheduleRecoveryCache.get('60_NetworkElement=1_' + processType).get('nodeAddress') == "NetworkElement=1"
        and:
        fileCollectionActiveTasksCache.size() == 0

        where:
        processType << ["STATS", "PREDEF_STATS", "NORMAL_PRIORITY_CELLTRACE", "HIGH_PRIORITY_CELLTRACE", "EVENTJOB", "CTUM", "UETRACE", "UETR", "CTR",
                        "REGULAR_GPEH", "OPTIMIZER_GPEH", "CELLRELATION"]
    }

    enum processTypes {
        STATS, CONTINUOUS_CELLTRACE, NORMAL_PRIORITY_CELLTRACE, HIGH_PRIORITY_CELLTRACE, CELLTRACE, CTUM, UETRACE, OTHER, EVENTJOB, PREDEF_STATS
    }

    def DpsObjectDeletedEvent getObjectDeleteEvent(final String processType, final String scannerStatus) {
        final String scannerFdn = "NetworkElement=1,PMICJobInfo=test"
        final int rop = 60
        final Map<String, Object> scannerAttr = new HashMap<String, Object>()
        scannerAttr.put(SubscriptionOperationConstant.SCANNER_ROP_PERIOD_ATTRIBUTE, rop)
        scannerAttr.put(SubscriptionOperationConstant.SCANNER_STATUS_ATTRIBUTE, scannerStatus)
        scannerAttr.put(SubscriptionOperationConstant.SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE, "1234")
        scannerAttr.put(SubscriptionOperationConstant.SCANNER_FILE_COLLECTION_ENABLED_ATTRIBUTE, true)
        scannerAttr.put(SubscriptionOperationConstant.PROCESS_TYPE_ATTRIBUTE, processType);

        final DpsObjectDeletedEvent dpsObjectDeletedEventEvent = new DpsObjectDeletedEvent(SubscriptionOperationConstant.SCANNER_MODEL_NAME_SPACE,
                SubscriptionOperationConstant.SCANNER_MODEL_NAME,
                SubscriptionOperationConstant.SCANNER_MODEL_VERSION, 10100l, scannerFdn, null, false, scannerAttr);
        return dpsObjectDeletedEventEvent
    }

    def setupPMICInitiationResponseCache(final String nodeAddress, final int ropPeriod, final String processType) {

        final RopTime ropTime = new RopTime(System.currentTimeMillis(), 60);
        final ProcessRequestVO requestVO = new ProcessRequestVO.ProcessRequestVOBuilder(nodeAddress, ropPeriod, processType).startTime(
                ropTime.getPreviousROPPeriodEndTime().getTime() - 1000).build();
        pmFunctionCache.updateEntry(nodeAddress, true)
        pmFunctionCache.updateEntry(nodeAddress, FileCollectionState.ENABLED)
        fileCollectionActiveTaskCacheWrapper.addProcessRequest(requestVO);
    }

}
