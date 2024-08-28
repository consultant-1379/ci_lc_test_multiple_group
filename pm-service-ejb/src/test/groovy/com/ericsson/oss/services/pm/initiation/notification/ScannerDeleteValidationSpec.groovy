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

import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_1
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_2
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionOperationConstant.*

import org.mockito.Mockito

import javax.ejb.TimerConfig
import javax.ejb.TimerService

import com.ericsson.cds.cdi.support.configuration.InjectionProperties
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectDeletedEvent
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.TestDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.node.TestNetworkElementDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.scanner.TestScannerDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.StatisticalSubscriptionBuilder
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.collection.notification.DpsScannerDeleteNotificationListener
import com.ericsson.oss.services.pm.scheduling.impl.DelayedTaskStatusValidator

/***
 * This class will test for the pmEnabled attribute change DPS notifications in PmFunction MO update.
 */
class ScannerDeleteValidationSpec extends SkeletonSpec {

    @ObjectUnderTest
    DpsScannerDeleteNotificationListener scannerDeleteNotificationListener

    TestNetworkElementDpsUtils node = new TestNetworkElementDpsUtils(configurableDps);
    TestDpsUtils testDpsUtils = new TestDpsUtils(configurableDps);
    TestScannerDpsUtils scanner = new TestScannerDpsUtils(configurableDps);
    StatisticalSubscriptionBuilder builder = new StatisticalSubscriptionBuilder(testDpsUtils);

    @ImplementationInstance
    TimerService timerService = Mock(TimerService)

    @ImplementationInstance
    TimeGenerator timer = Mockito.mock(TimeGenerator)

    @ImplementationInstance
    DelayedTaskStatusValidator delayedTaskStatusValidator = Mock(DelayedTaskStatusValidator)

    private List<ManagedObject> nodes
    private ManagedObject subscriptionMO
    private TimerConfig timerConfig;
    private ManagedObject scannerMo;

    @Override
    def addAdditionalInjectionProperties(InjectionProperties injectionProperties) {
        super.addAdditionalInjectionProperties(injectionProperties)
        injectionProperties.autoLocateFrom('com.ericsson.oss.services.pm')
    }

    def setup() {
        timerService.getTimers() >> []
        subscriptionMO = builder.name("Test").taskStatus(TaskStatus.OK).administrativeState(AdministrationState.ACTIVATING).build();
        nodes = [node.builder(NODE_NAME_1).build(), node.builder(NODE_NAME_2).build()]
        testDpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        timerConfig = new TimerConfig(Long.toString(subscriptionMO.getPoId()), false);
        scannerMo = scanner
                .builder("USERDEF.TEST.Cont.Y.Stats", NODE_NAME_1)
                .subscriptionId(subscriptionMO.getPoId())
                .status(ScannerStatus.ACTIVE)
                .processType(ProcessType.STATS)
                .build();
    }

    def "when a stats scanner is deleted, a timer is created to validate that subscription."() {
        given: "A scanner is deleted"
        DpsObjectDeletedEvent objectDeletedEvent = createObjectDeletedEvent(subscriptionMO.getPoId(), scannerMo.getFdn())
        when: "A notification is received"
        scannerDeleteNotificationListener.onEvent(objectDeletedEvent)
        then: "a timer is created to validate that subscription"
        1 * delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(subscriptionMO.getPoId(), _ as String)
    }

    def "when a stats scanner is deleted, a timer is not created if the subscription ID is invalid."() {
        given: "A scanner is deleted"
        DpsObjectDeletedEvent objectDeletedEvent = createObjectDeletedEvent(0, scannerMo.getFdn())
        when: "A notification is received"
        scannerDeleteNotificationListener.onEvent(objectDeletedEvent)
        then: "a timer is created to validate that subscription"
        0 * delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(subscriptionMO.getPoId())
    }

    private DpsObjectDeletedEvent createObjectDeletedEvent(long subId, String scannerFdn) {
        final Map<String, Object> scannerAttr = new HashMap<String, Object>();
        scannerAttr.put(SCANNER_ROP_PERIOD_ATTRIBUTE, 900);
        scannerAttr.put(SCANNER_STATUS_ATTRIBUTE, ScannerStatus.ACTIVE.name());
        scannerAttr.put(SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE, Long.toString(subId));
        scannerAttr.put(SCANNER_FILE_COLLECTION_ENABLED_ATTRIBUTE, true);
        scannerAttr.put(PROCESS_TYPE_ATTRIBUTE, "STATS");
        return new DpsObjectDeletedEvent(SCANNER_MODEL_NAME_SPACE, SCANNER_MODEL_NAME,
                SCANNER_MODEL_VERSION, 10100l, scannerFdn, null, false, scannerAttr);
    }
}
