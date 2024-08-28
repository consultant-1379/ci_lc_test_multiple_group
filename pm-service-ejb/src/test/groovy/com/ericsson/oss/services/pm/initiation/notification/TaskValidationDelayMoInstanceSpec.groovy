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
import org.mockito.Mockito
import spock.lang.Unroll

import javax.ejb.TimerService

import static com.ericsson.oss.pmic.cdi.test.util.Constants.ACTIVE
import static com.ericsson.oss.pmic.cdi.test.util.Constants.ERROR
import static com.ericsson.oss.pmic.cdi.test.util.Constants.INACTIVE
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NA
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_1
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_2
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_3
import static com.ericsson.oss.pmic.cdi.test.util.Constants.OK
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE

class TaskValidationDelayMoInstanceSpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    DelayedTaskStatusValidator delayedTaskStatusValidator

    @ImplementationInstance
    TimerService timerService = Mock(TimerService)

    @ImplementationInstance
    TimeGenerator timer = Mockito.mock(TimeGenerator)

    List<ManagedObject> nodes
    ManagedObject subscriptionMO

    def setup() {
        nodes = [nodeUtil.builder(NODE_NAME_1).build(), nodeUtil.builder(NODE_NAME_2).build(), nodeUtil.builder(NODE_NAME_3).build()]
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis(), System.currentTimeMillis() + 30000)
    }

    @Unroll
    def 'The subscription task status goes from #startStatus to #expectedTaskStatus after timeout if there are #scanners.size() scanners'() {
        given: 'Two nodes and #scanners.size() scanners'
            subscriptionMO = moinstanceSubscriptionBuilder.name('Test').taskStatus(startStatus).administrativeState(AdministrationState.ACTIVE).build()
            dpsUtils.addAssociation(subscriptionMO, 'nodes', nodes.get(0), nodes.get(1))
            createScanners(scanners)
            delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(subscriptionMO.poId)
        when: 'The validation timer times out'
            delayedTaskStatusValidator.validateTaskStatusAdminState()
        then: 'The subscription goes to ACTIVE/ERROR'
            verifySubscriptionState(ACTIVE, expectedTaskStatus)

        where:
            startStatus   | scanners                                || expectedTaskStatus
            TaskStatus.OK | []                                      || ERROR
            TaskStatus.OK | [NODE_NAME_1]                           || ERROR
            TaskStatus.OK | [NODE_NAME_1, NODE_NAME_2]              || OK
            TaskStatus.NA | [NODE_NAME_1, NODE_NAME_2]              || OK
            TaskStatus.NA | [NODE_NAME_1, NODE_NAME_2, NODE_NAME_3] || ERROR
    }

    def 'The subscription is deactivated/deleted if there are no nodes'() {
        given: 'No nodes and no scanners'
            subscriptionMO = moinstanceSubscriptionBuilder.name('Test').taskStatus(TaskStatus.NA).administrativeState(AdministrationState.ACTIVE).build()
            delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(subscriptionMO.poId)
        when: 'The validation timer times out'
            delayedTaskStatusValidator.validateTaskStatusAdminState()
        then: 'The subscription is deleted or it goes to INACTIVE and the task status stays the same'
            verifySubscriptionState(INACTIVE, NA)
    }

    def verifySubscriptionState = { expectedAdminState, expectedTaskStatus ->
        subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE) == expectedAdminState &&
                subscriptionMO.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE) == expectedTaskStatus
    }

    def createScanners = { nodeNames ->
        nodeNames.each {
            scannerUtil.builder('USERDEF.TEST.Cont.Y.Stats', it)
                    .subscriptionId(subscriptionMO.getPoId())
                    .status(ScannerStatus.ACTIVE)
                    .processType(ProcessType.STATS)
                    .build()
        }
    }
}
