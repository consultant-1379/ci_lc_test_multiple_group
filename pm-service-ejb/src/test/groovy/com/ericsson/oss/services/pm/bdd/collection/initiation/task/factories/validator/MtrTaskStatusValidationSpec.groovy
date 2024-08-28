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

package com.ericsson.oss.services.pm.bdd.collection.initiation.task.factories.validator

import javax.ejb.TimerService

import spock.lang.Unroll

import org.mockito.Mockito

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.scanner.Scanner
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.scheduling.impl.DelayedTaskStatusValidator
import com.ericsson.oss.services.pm.initiation.task.factories.validator.MtrTaskStatusValidator

class MtrTaskStatusValidationSpec extends SkeletonSpec {

    @ObjectUnderTest
    DelayedTaskStatusValidator delayedTaskStatusValidator

    @ObjectUnderTest
    MtrTaskStatusValidator mtrTaskStatusValidator

    @MockedImplementation
    TimerService timerService

    @ImplementationInstance
    TimeGenerator timer = Mockito.mock(TimeGenerator)

    @Unroll
    def 'Should set task status to error if there are incorrect number of associated scanners'() {
        given: 'an MTR subscription with #nodes nodes, #attachedNodes attached nodes and #scanners associated scanners'
            def subscriptionMo = createSubscriptionNodesAndScanners(nodes, attachedNodes, startTaskStatus, scanners)

        and: 'delayed task status validation is scheduled'
            delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(subscriptionMo.poId)

        when: 'the validation timer times out'
            mtrTaskStatusValidator.validateTaskStatusAndAdminState(subscriptionDao.findAll()[0])

        then: 'the subscription task status should be set to #expectedTaskStatus'
            subscriptionMo.getAttribute(Subscription.Subscription220Attribute.taskStatus.name()) == expectedTaskStatus

        where:
            nodes   | attachedNodes | scanners  | startTaskStatus   || expectedTaskStatus
            1       | 1             | 2         | TaskStatus.ERROR  || TaskStatus.OK.name()
            1       | 1             | 2         | TaskStatus.OK     || TaskStatus.OK.name()
            2       | 0             | 2         | TaskStatus.ERROR  || TaskStatus.OK.name()
            2       | 1             | 2         | TaskStatus.ERROR  || TaskStatus.ERROR.name()
            1       | 2             | 2         | TaskStatus.OK     || TaskStatus.ERROR.name()
            2       | 0             | 1         | TaskStatus.OK     || TaskStatus.ERROR.name()
    }

    def 'Should set subscription administration state to INACTIVE if no nodes included'() {
        given: 'an MTR subscription with no nodes'
            def subscriptionMo = createSubscription()

        when: 'validation is executed'
            mtrTaskStatusValidator.validateTaskStatusAndAdminState(subscriptionDao.findAll()[0])

        then: 'the subscription task status should be set to #expectedTaskStatus'
            subscriptionMo.getAttribute(Subscription.Subscription220Attribute.administrationState.name()) == AdministrationState.INACTIVE.name()
    }

    def createSubscriptionNodesAndScanners(nodes, attachedNodes, startTaskStatus, scanners) {
        def subscriptionMo = createSubscription(startTaskStatus)
        def remainingScanners = scanners
        if (nodes > 0)
            remainingScanners = createNodesAndScanners(0, nodes, subscriptionMo, 'nodes', scanners)
        if (attachedNodes > 0)
            createNodesAndScanners(nodes, nodes + attachedNodes, subscriptionMo, 'attachedNodes', remainingScanners)
        return subscriptionMo
    }

    def createSubscription(startTaskStatus = TaskStatus.ERROR) {
        dps.subscription()
           .type(SubscriptionType.MTR)
           .administrationState(AdministrationState.ACTIVE)
           .taskStatus(startTaskStatus)
           .build()
    }

    def createNodesAndScanners(startNumber, numberOfNodes, subscriptionMo, endpoint, scanners) {
        ((startNumber + 1)..numberOfNodes).each {
            def node = dps.node()
                          .fdn("NetworkElement=node_${it}")
                          .neType('BSC')
                          .build()
            dpsUtils.addAssociation(subscriptionMo, endpoint, node)
            if (scanners > 0) {
                createScanner(node.name, "scanner_${it}", subscriptionMo.poId)
                scanners--
            }
        }
        return scanners
    }

    def createScanner(nodeName, scannerName, subId) {
        dps.scanner()
           .scannerType(Scanner.PmicScannerType.PMICScannerInfo)
           .nodeName(nodeName)
           .name(scannerName)
           .processType(ProcessType.MTR)
           .status(ScannerStatus.ACTIVE)
           .subscriptionId(subId)
           .build()
    }

}
