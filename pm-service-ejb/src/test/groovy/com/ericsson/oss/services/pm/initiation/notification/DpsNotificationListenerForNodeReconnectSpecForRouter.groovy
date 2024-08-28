/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.notification

import org.mockito.Mock

import spock.lang.Unroll

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.*
import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.services.pm.collection.FileCollectionForLostSyncTime
import com.ericsson.oss.services.pm.collection.notification.DpsNotificationListenerForNodeReconnect
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest

/***
 * This class will test for the lostSynchronization attribute change DPS notifications in CmFunction MO update.
 */
class DpsNotificationListenerForNodeReconnectSpecForRouter extends SkeletonSpec {

    @ObjectUnderTest
    DpsNotificationListenerForNodeReconnect dpsNotificationListenerForNodeReconnect
    @Inject
    @Modeled
    EventSender<MediationTaskRequest> eventSender
    @ImplementationInstance
    FileCollectionForLostSyncTime fileCollectionForLostSyncTime = Mock()

    def nodeFdn
    def node

    def setup() {
        nodeFdn = "NetworkElement=SPFRER60001"
        node = nodeUtil.builder("").fdn(nodeFdn).build()
    }

    @Unroll
    def "when dps Node reconnect notification received for node with different rops and old value for lostSynchronization #oldValuelostSynchronization is valid date, it should sent reconnect file collection recovery for correct rops"(String oldValuelostSynchronization) {
        given: "dps notification for Node Reconnect is created"
        scannerUtil.builder("USERDEF.TEST4.Cont.Y.Stats", "SPFRER60001").status(ScannerStatus.ACTIVE).subscriptionId(123125L).scannerId("10013")
        .processType(ProcessType.STATS).ropPeriod(86400).node(node).build()
        scannerUtil.builder("USERDEF.TEST.Cont.Y.Stats", "SPFRER60001").status(ScannerStatus.ACTIVE).subscriptionId(123122L).scannerId("10010")
        .processType(ProcessType.STATS).ropPeriod(300).node(node).build()
        scannerUtil.builder("USERDEF.TEST2.Cont.Y.Stats", "SPFRER60001").status(ScannerStatus.ACTIVE).subscriptionId(123123L).scannerId("10011")
        .processType(ProcessType.STATS).ropPeriod(900).node(node).build()
        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChangeEvent("lostSynchronization", oldValuelostSynchronization)

        and : "test"
        fileCollectionForLostSyncTime.getTotalRopsToCollect(_,86400,2) >> 2
        fileCollectionForLostSyncTime.getTotalRopsToCollect(_,900,2) >> 0
        fileCollectionForLostSyncTime.getTotalRopsToCollect(_,300,2) >> 3

        when: "received the pm lostSynchronization change Event listener"
        dpsNotificationListenerForNodeReconnect.onEvent(attributeChangedEvent)

        then: "File Collection recoverFiles For Node should called"
        1 * eventSender.send({ request -> request.nodeAddress == nodeFdn && request.ropPeriod == 300000L } as FileCollectionTaskRequest)
        0 * eventSender.send({ request -> request.nodeAddress == nodeFdn && request.ropPeriod == 900000L } as FileCollectionTaskRequest)
        1 * eventSender.send({ request -> request.nodeAddress == nodeFdn && request.ropPeriod == 86400000L } as FileCollectionTaskRequest)

        where:
        oldValuelostSynchronization << ["Wed May 25 05:00:00 GMT+01:00 2016"]
    }

    private DpsAttributeChangedEvent createAttributeChangeEvent(String name = "lostSynchronization", String oldValue = "Wed May 25 05:00:00 GMT+01:00 2016", String newValue = null) {
        Collection<AttributeChangeData> attributeChangeData = [new AttributeChangeData(name, oldValue, newValue, null, null)]
        return new DpsAttributeChangedEvent(fdn: "$nodeFdn,CmFunction=1", changedAttributes: attributeChangeData)
    }

}