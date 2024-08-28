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

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import org.mockito.Mockito

import javax.ejb.TimerService

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.scheduling.impl.DelayedTaskStatusValidator

class TaskValidationDelayCctrSpec extends SkeletonSpec {

    @ObjectUnderTest
    DelayedTaskStatusValidator delayedTaskStatusValidator

    @MockedImplementation
    TimerService timerService

    @ImplementationInstance
    TimeGenerator timer = Mockito.mock(TimeGenerator)

    private Date persistenceTime = new Date(System.currentTimeMillis() - 10000)

    def 'If there are no nodes, the subscription administrative state must set to INACTIVE'() {
        given: 'No nodes in DPS and an incorrect node list identity'
            Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
            def subscriptionMO = cctrSubscriptionBuilder.persistenceTime(persistenceTime).nodeListIdentity(1).build()
            def subId = subscriptionMO.getPoId()
            delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(subId)
        when: 'The validation timer times out'
            Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis() + 30000)
            delayedTaskStatusValidator.validateTaskStatusAdminState()
        then: 'The subscription administrative state must set to INACTIVE'
            subscriptionMO.getAttribute(Subscription.Subscription220Attribute.administrationState.name()) == AdministrationState.INACTIVE.name()
    }
}
