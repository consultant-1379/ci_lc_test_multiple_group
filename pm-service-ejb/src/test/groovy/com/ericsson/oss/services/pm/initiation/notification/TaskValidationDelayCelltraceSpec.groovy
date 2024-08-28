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
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE

import org.mockito.Mockito

import javax.ejb.TimerService

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.scheduling.impl.DelayedTaskStatusValidator

class TaskValidationDelayCelltraceSpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    DelayedTaskStatusValidator delayedTaskStatusValidator

    @ImplementationInstance
    TimerService timerService = Mock(TimerService)

    @ImplementationInstance
    TimeGenerator timer = Mockito.mock(TimeGenerator)

    private List<ManagedObject> nodes
    private ManagedObject subscriptionMO

    def setup() {
        nodes = [
                nodeUtil.builder(NODE_NAME_1).build(),
                nodeUtil.builder(NODE_NAME_2).build(),
                nodeUtil.builder(NODE_NAME_3).build()
        ]
    }

    def "The subscription goes to ERROR after timeout if there are no scanners"() {
        given: "Two nodes and no scanners"
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
        subscriptionMO = cellTraceSubscriptionBuilder.name("Test").taskStatus(TaskStatus.OK).administrativeState(AdministrationState.ACTIVE).build()
        dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(subscriptionMO.poId)
        when: "The validation timer times out"
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis() + 30000)
        delayedTaskStatusValidator.validateTaskStatusAdminState()
        then: "The subscription goes to ACTIVE/ERROR"
        verifySubscriptionState(ACTIVE, ERROR)
    }

    def "The subscription goes to ERROR after timeout if there are two nodes and one scanner"() {
        given: "Two nodes and one scanner"
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
        subscriptionMO = cellTraceSubscriptionBuilder.name("Test").taskStatus(TaskStatus.OK).administrativeState(AdministrationState.ACTIVE).build()
        dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanner()
        delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(subscriptionMO.poId)
        when: "The validation timer times out"
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis() + 30000)
        delayedTaskStatusValidator.validateTaskStatusAdminState()
        then: "The subscription goes to ACTIVE/ERROR"
        verifySubscriptionState(ACTIVE, ERROR)
    }

    def "The subscription goes from ERROR to OK after timeout if there are two nodes and two scanners"() {
        given: "Two nodes and two scanners"
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
        subscriptionMO = cellTraceSubscriptionBuilder.name("Test").taskStatus(TaskStatus.ERROR).administrativeState(AdministrationState.ACTIVE).build()
        dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanner()
        createScanner(NODE_NAME_2)
        delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(subscriptionMO.poId)
        when: "The validation timer times out"
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis() + 30000)
        delayedTaskStatusValidator.validateTaskStatusAdminState()
        then: "The subscription goes to ACTIVE/OK"
        verifySubscriptionState(ACTIVE, OK)
    }

    def "The subscription goes from NA to OK after timeout if there are two nodes and two scanners"() {
        given: "Two nodes and two scanners"
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
        subscriptionMO = cellTraceSubscriptionBuilder.name("Test").taskStatus(TaskStatus.NA).administrativeState(AdministrationState.ACTIVE).build()
        dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanner()
        createScanner(NODE_NAME_2)
        delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(subscriptionMO.poId)
        when: "The validation timer times out"
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis() + 30000)
        delayedTaskStatusValidator.validateTaskStatusAdminState()
        then: "The subscription goes to ACTIVE/OK"
        verifySubscriptionState(ACTIVE, OK)
    }

    def "The subscription goes to ERROR after timeout if there are two nodes and three scanners"() {
        given: "Two nodes and two scanners"
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
        subscriptionMO = cellTraceSubscriptionBuilder.name("Test").taskStatus(TaskStatus.NA).administrativeState(AdministrationState.ACTIVE).build()
        dpsUtils.addAssociation(subscriptionMO, "nodes", nodes.get(0), nodes.get(1))
        createScanner()
        createScanner(NODE_NAME_2)
        createScanner(NODE_NAME_3)
        delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(subscriptionMO.poId)
        when: "The validation timer times out"
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis() + 30000)
        delayedTaskStatusValidator.validateTaskStatusAdminState()
        then: "The subscription goes to ACTIVE/ERROR"
        verifySubscriptionState(ACTIVE, ERROR)
    }

    def "The subscription is deactivated/deleted if there are no nodes"() {
        given: "No nodes and no scanners"
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
        subscriptionMO = cellTraceSubscriptionBuilder.name("Test").taskStatus(TaskStatus.NA).administrativeState(AdministrationState.ACTIVE).build()
        delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(subscriptionMO.poId)
        when: "The validation timer times out"
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis() + 30000)
        delayedTaskStatusValidator.validateTaskStatusAdminState()
        then: "The subscription goes to INACTIVE and the task status stays the same"
        assert verifySubscriptionState(INACTIVE, NA)
    }

    private boolean verifySubscriptionState(final String adminState, final String taskStatus) {
        return adminState == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) &&
                taskStatus == (String) subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)
    }

    private createScanner(final String nodeName = NODE_NAME_1) {
        scannerUtil.builder("PREDEF.10000.CELLTRACE", nodeName)
                .subscriptionId(subscriptionMO.getPoId())
                .status(ScannerStatus.ACTIVE)
                .processType(ProcessType.NORMAL_PRIORITY_CELLTRACE)
                .build()
    }
}
