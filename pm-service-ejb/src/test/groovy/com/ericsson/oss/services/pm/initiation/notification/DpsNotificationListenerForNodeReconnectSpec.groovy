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

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.services.pm.collection.notification.DpsNotificationListenerForNodeReconnect
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest

/***
 * This class will test for the lostSynchronization attribute change DPS notifications in CmFunction MO update.
 */
class DpsNotificationListenerForNodeReconnectSpec extends SkeletonSpec {

    @ObjectUnderTest
    DpsNotificationListenerForNodeReconnect dpsNotificationListenerForNodeReconnect
    @Inject
    @Modeled
    EventSender<MediationTaskRequest> eventSender

    def nodeFdn
    def siu02NodeFdn
    def tcu02NodeFdn

    def setup() {
        nodeFdn = "NetworkElement=LTE01ERBS00001"
        def node = nodeUtil.builder("").fdn(nodeFdn).build()
        scannerUtil.builder("USERDEF.TEST.Cont.Y.Stats", "LTE01ERBS00001")
                .status(ScannerStatus.ACTIVE)
                .subscriptionId(123122L)
                .scannerId("10010")
                .processType(ProcessType.STATS)
                .ropPeriod(300)
                .node(node).build()
        scannerUtil.builder("USERDEF.TEST2.Cont.Y.Stats", "LTE01ERBS00001")
                .status(ScannerStatus.ACTIVE)
                .subscriptionId(123123L)
                .scannerId("10011")
                .processType(ProcessType.STATS)
                .ropPeriod(900)
                .node(node).build()
        scannerUtil.builder("USERDEF.TEST3.Cont.Y.Stats", "LTE01ERBS00001")
                .status(ScannerStatus.ACTIVE)
                .subscriptionId(123124L)
                .scannerId("10012")
                .processType(ProcessType.STATS)
                .ropPeriod(900)
                .node(node).build()
        siu02NodeFdn = "NetworkElement=CORE16SIU02001"
        tcu02NodeFdn = "NetworkElement=CORE17TCU02001"
        nodeUtil.builder("").fdn(siu02NodeFdn).neType("SIU02").build()
        nodeUtil.builder("").fdn(tcu02NodeFdn).neType("TCU02").build()
    }

    @Unroll
    def "when dps Node reconnect notification received and old value lostSynchronization #oldValuelostSynchronization, it should not reconnect node and call file collection recovery"(String oldValuelostSynchronization) {
        given: "dps notification for Node Reconnect is created"
        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChangeEvent("lostSynchronization", oldValuelostSynchronization)

        when: "received the pm lostSynchronization change Event listener"
        dpsNotificationListenerForNodeReconnect.onEvent(attributeChangedEvent)

        then: "File Collection recoverFiles For Node should not called"
        0 * eventSender.send({ request -> request.nodeAddress == nodeFdn } as FileCollectionTaskRequest)
        where:
        oldValuelostSynchronization << ["SYNC_ON_DEMAND", "SYNCHRONIZED", null]
    }

    @Unroll
    def "when dps Node reconnect notification received and old value for lostSynchronization #oldValuelostSynchronization is valid date, it should sent reconnect file collection recovery"(String oldValuelostSynchronization) {
        given: "dps notification for Node Reconnect is created"
        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChangeEvent("lostSynchronization", oldValuelostSynchronization)

        when: "received the pm lostSynchronization change Event listener"
        dpsNotificationListenerForNodeReconnect.onEvent(attributeChangedEvent)

        then: "File Collection recoverFiles For Node should not called"
        2 * eventSender.send({ request -> request.nodeAddress == nodeFdn } as FileCollectionTaskRequest)
        where:
        oldValuelostSynchronization << ["Wed May 25 05:00:00 GMT+01:00 2016"]
    }

    @Unroll
    def "when dps Node reconnect notification received for SIU02 node and old value for lostSynchronization #oldValuelostSynchronization is valid date, it should  not send reconnect file collection recovery as Recovery on Node Reconnect is not supported"(String oldValuelostSynchronization) {
        given: "dps notification for Node Reconnect is created"
        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChangeEventforStn("lostSynchronization", oldValuelostSynchronization, siu02NodeFdn)

        when: "received the pm lostSynchronization change Event listener"
        dpsNotificationListenerForNodeReconnect.onEvent(attributeChangedEvent)

        then: "File Collection recoverFiles For Node should not called"
        0 * eventSender.send({ request -> request.nodeAddress == siu02NodeFdn } as FileCollectionTaskRequest)
        where:
        oldValuelostSynchronization << ["Wed May 25 05:00:00 GMT+01:00 2016"]
    }

    @Unroll
    def "when dps Node reconnect notification received for TCU02 node and old value for lostSynchronization #oldValuelostSynchronization is valid date, it should not send reconnect file collection recovery as Recovery on Node Reconnect is not supported"(String oldValuelostSynchronization) {
        given: "dps notification for Node Reconnect is created"
        DpsAttributeChangedEvent attributeChangedEvent = createAttributeChangeEventforStn("lostSynchronization", oldValuelostSynchronization, tcu02NodeFdn)

        when: "received the pm lostSynchronization change Event listener"
        dpsNotificationListenerForNodeReconnect.onEvent(attributeChangedEvent)

        then: "File Collection recoverFiles For Node should not called"
        0 * eventSender.send({ request -> request.nodeAddress == tcu02NodeFdn } as FileCollectionTaskRequest)
        where:
        oldValuelostSynchronization << ["Wed May 25 05:00:00 GMT+01:00 2016"]
    }

    private DpsAttributeChangedEvent createAttributeChangeEvent(String name = "lostSynchronization", String oldValue = "Wed May 25 05:00:00 GMT+01:00 2016", String newValue = null) {
        Collection<AttributeChangeData> attributeChangeData = [new AttributeChangeData(name, oldValue, newValue, null, null)]
        return new DpsAttributeChangedEvent(fdn: "$nodeFdn,CmFunction=1", changedAttributes: attributeChangeData)
    }

    private DpsAttributeChangedEvent createAttributeChangeEventforStn(String name = "lostSynchronization", String oldValue = "Wed May 25 05:00:00 GMT+01:00 2016", String newValue = null, final String stnnodeFdn) {
        Collection<AttributeChangeData> attributeChangeData = [new AttributeChangeData(name, oldValue, newValue, null, null)]
        return new DpsAttributeChangedEvent(fdn: stnnodeFdn + ",CmFunction=1", changedAttributes: attributeChangeData)
    }
}
