package com.ericsson.oss.services.pm.initiation.task.factories.validation

import spock.lang.Unroll

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject

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

import com.ericsson.oss.pmic.cdi.test.util.builder.node.TestNetworkElementDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.scanner.TestScannerDpsUtils
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.ContinuousCellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.initiation.task.factories.validator.SubscriptionTaskStatusValidator

class ContinuousCellTraceTaskStatusValidatorSpec extends PmServiceEjbSkeletonSpec {

    public TestNetworkElementDpsUtils node = new TestNetworkElementDpsUtils(configurableDps)

    public TestScannerDpsUtils scanner = new TestScannerDpsUtils(configurableDps)

    @ObjectUnderTest
    SubscriptionTaskStatusValidator objectUnderTest

    @Inject
    private SubscriptionDao subscriptionDao

    ContinuousCellTraceSubscription subscription

    def eventsWithEventProducerIds

    @Unroll
    def 'get task status for CCTR subscription having only RadioNodes will return #subscriptionTaskStatus if one of the scanner is #scannerStatus and other scanner status are ACTIVE'() {
        given: 'two RadioNodes'
        def radioNodeMo1 = nodeUtil.builder("RadioNode01").neType("RadioNode").build()
        def radioNodeMo2 = nodeUtil.builder("RadioNode02").neType("RadioNode").build()
        and: 'Some events are created with different eventProducerIds'
        eventsWithEventProducerIds = generateEvents(eventProducerIds)
        and: 'A ContinuousCellTraceSubscriptionNRAN subscription is created with two radio nodes with multiple event producers'
        def subscriptionMo = cctrSubscriptionBuilder.nodes(radioNodeMo1, radioNodeMo2).name('ContinuousCellTraceSubscriptionNRAN').events(eventsWithEventProducerIds as EventInfo[]).build()
        def subscription = subscriptionDao.findOneById(subscriptionMo.getPoId(), true) as ContinuousCellTraceSubscription
        and: 'There are multiple 10005 jobs for the RadioNodes'
        createScanners([radioNodeMo1, radioNodeMo2], subscriptionMo, scannerStatus)
        when: 'when getTaskStatus is invoked'
        final TaskStatus taskStatus = objectUnderTest.getTaskStatus(subscription)
        then: 'task status should be #expectedTaskStatus'
        taskStatus == expectedTaskStatus
        where:
        eventProducerIds                     | scannerStatus || expectedTaskStatus
        ['DU', 'CUCP', 'CUUP']               | 'ACTIVE'      || TaskStatus.OK
        ['DU', 'CUCP', 'CUUP']               | 'ERROR'       || TaskStatus.ERROR
        ['DU', 'CUCP', 'CUUP']               | 'UNKNOWN'     || TaskStatus.ERROR
        ['DU', 'CUCP', 'CUUP']               | 'INACTIVE'    || TaskStatus.ERROR
        ['DU', 'CUCP', 'CUUP', 'PU']         | 'ACTIVE'      || TaskStatus.ERROR
        ['DU', 'CUCP', 'CUUP', 'PU', 'CUUP'] | 'ACTIVE'      || TaskStatus.ERROR
        ['DU', 'CUCP', 'CUUP', 'PU']         | 'ERROR'       || TaskStatus.ERROR
    }

    def 'get task status for CCTR Nran subscription will return ERROR if no scanners available on node'() {
        given: 'One RadioNode and one 5GRadioNode with multiple event producers'
        def fivegRadioNodeMO1 = nodeUtil.builder('5GRadioNode001').neType('5GRadioNode').build()
        def radioNodeMo1 = nodeUtil.builder('RadioNode001').neType('RadioNode').build()
        and: 'Some events are created with different eventProducerIds'
        eventsWithEventProducerIds = generateEvents(['DU', 'CUUP', 'CUCP'])
        and: 'CCTR NRAN Subscription with one radio node and one 5GRadio node'
        def subscriptionMo = cctrSubscriptionBuilder.name('ContinuousCellTraceSubscriptionNRAN').nodes(fivegRadioNodeMO1, radioNodeMo1).events(eventsWithEventProducerIds as EventInfo[]).build()
        expect: 'Subscription task status should be ERROR'
        def subscription = subscriptionDao.findOneById(subscriptionMo.getPoId(), true) as ContinuousCellTraceSubscription
        objectUnderTest.getTaskStatus(subscription) == TaskStatus.ERROR
    }

    @Unroll
    def 'get task status for ContinuousCellTraceSubscription will return #taskStatus when scanner status is #scannerStatus'() {
        given: 'two nodes of some node type'
        def nodeMO1 = nodeUtil.builder('node01').build()
        def nodeMO2 = nodeUtil.builder('node02').build()

        and: 'CCTR subscription with two nodes'
        def subscriptionMo = cctrSubscriptionBuilder.name('ContinuousCellTraceSubscription').nodes(nodeMO1, nodeMO2).build()
        and: 'one high priority scanner for each node'
        createScanner('PREDEF.10005.CELLTRACE', nodeMO1, scannerStatus, subscriptionMo)
        createScanner('PREDEF.10005.CELLTRACE', nodeMO2, ScannerStatus.ACTIVE.name(), subscriptionMo)
        def subscription = subscriptionDao.findOneById(subscriptionMo.getPoId(), true) as ContinuousCellTraceSubscription
        expect: 'Subscription task status is #subscriptionTaskStatus when one of the scanner status is #scannerStatus'
        objectUnderTest.getTaskStatus(subscription).name() == taskStatus
        where:
        scannerStatus || taskStatus
        'ACTIVE'      || 'OK'
        'ERROR'       || 'ERROR'
        'INACTIVE'    || 'ERROR'
        'UNKNOWN'     || 'ERROR'
    }

    @Unroll
    def 'get task status for CCTR Nran subscription having only 5GRadioNodes will return #subscriptionTaskStatus if one of the scanner is #scannerStatus and other scanner status are ACTIVE'() {
        given: 'Two nodes with multiple event producers'
        def fivegRadioNodeMO1 = nodeUtil.builder('5GRadioNode1').neType('5GRadioNode').build()
        def fivegRadioNodeMO2 = nodeUtil.builder('5GRadioNode2').neType('5GRadioNode').build()
        and: 'CCTR Subscription with two nodes'
        def subscriptionMo = cctrSubscriptionBuilder.name('ContinuousCellTraceSubscriptionNRAN').nodes(fivegRadioNodeMO1, fivegRadioNodeMO2).build()
        and: 'Multiple 10005 jobs per node'
        createScanners([fivegRadioNodeMO1, fivegRadioNodeMO2], subscriptionMo, scannerStatus)
        def subscription = subscriptionDao.findOneById(subscriptionMo.getPoId(), true) as ContinuousCellTraceSubscription
        expect: "Subscription status is #subscriptionTaskStatus if one of the scanner status is #scannerStatus"
        objectUnderTest.getTaskStatus(subscription) == subscriptionTaskStatus
        where:
        scannerStatus || subscriptionTaskStatus
        'ACTIVE'      || TaskStatus.OK
        'ERROR'       || TaskStatus.ERROR
        'UNKNOWN'     || TaskStatus.ERROR
        'INACTIVE'    || TaskStatus.ERROR
    }

    @Unroll
    def 'ContinuousCellTraceSubscriptionNRAN with mixed RadioNode and 5GRadioNode will have one scanner per unique eventProducerId per node'() {
        given: 'one RadioNode and one 5GRadioNode'
        def nodeMO1 = nodeUtil.builder("RadioNode01").neType("RadioNode").build()
        def nodeMO2 = nodeUtil.builder("5GRadioNode01").neType("5GRadioNode").build()
        and: 'Some events are created'
        eventsWithEventProducerIds = generateEvents(eventProducerIds)
        and: 'A ContinuousCellTraceSubscriptionNRAN subscription with two nodes'
        def subscriptionMo = cctrSubscriptionBuilder.nodes(nodeMO1).name('ContinuousCellTraceSubscriptionNRAN').events(eventsWithEventProducerIds as EventInfo[]).build()
        and: 'Multiple 10005 jobs for the RadioNode and 5GRadioNode'
        createScanners([nodeMO1], subscriptionMo, scannerStatusForRadioNode)
        createScanners([nodeMO2], subscriptionMo, scannerStatusFor5GRadioNode)
        expect: 'all ACTIVE scanners to be returned for 5GRadioNode and only one scanner per unique eventProducerId for RadioNode'
        where:
        eventProducerIds                     | scannerStatusForRadioNode | scannerStatusFor5GRadioNode || expectedTaskStatus
        ['DU', 'CUCP', 'CUUP']               | 'ERROR'                   | 'OK'                        || TaskStatus.ERROR
        ['DU', 'CUCP', 'CUUP', 'PU']         | 'OK'                      | 'OK'                        || TaskStatus.OK
        ['DU', 'CUCP', 'CUUP', 'PU', 'CUUP'] | 'OK'                      | 'OK'                        || TaskStatus.OK
        ['DU', 'CUCP', 'CUCP', 'PU']         | 'OK'                      | 'ERROR'                     || TaskStatus.ERROR
        ['DU', 'CUCP', 'CUUP']               | 'ERROR'                   | 'ERROR'                     || TaskStatus.ERROR
    }

    def generateEvents(List<String> eventProducerIds) {
        def events = []
        eventProducerIds.each { eventProducerId -> events.add(new EventInfo("INTERNAL_EVENT_ADMISSION_BLOCKING_STARTED", "SESSION_ESTABLISHMENT_EVALUATION", eventProducerId)) }
        events
    }

    private void createScanners(List<ManagedObject> nodeMos, ManagedObject subscriptionMo, String scannerStatus) {

        nodeMos.each { nodeMo ->
            if (NetworkElementType.RADIONODE.equals(nodeMo.getAttribute('neType'))) {
                scannerUtil.builder("PREDEF.10005.CELLTRACE", nodeMo.getName()).node(nodeMo).status(scannerStatus)
                        .processType(ProcessType.HIGH_PRIORITY_CELLTRACE).build()
            }
            createScanner("PREDEF.DU.10005.CELLTRACE", nodeMo, scannerStatus, subscriptionMo)
            createScanner("PREDEF.CUUP.10005.CELLTRACE", nodeMo, ScannerStatus.ACTIVE.name(), subscriptionMo)
            createScanner("PREDEF.CUCP.10005.CELLTRACE", nodeMo, ScannerStatus.ACTIVE.name(), subscriptionMo)
        }
    }

    private void createScanner(String scannerName, ManagedObject nodeMo, String scannerStatus, ManagedObject subscriptionMo) {
        scannerUtil.builder(scannerName, nodeMo.getName()).node(nodeMo).status(scannerStatus).processType(ProcessType.HIGH_PRIORITY_CELLTRACE).subscriptionId(subscriptionMo).build()
    }

}
