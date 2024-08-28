/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2018
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.jmsreconnect

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.service.pm.jmsreconnect.JMSFailOverEvent
import com.ericsson.oss.service.pm.jmsreconnect.JMSFailoverHandler
import com.ericsson.oss.services.pm.initiation.events.PmicSubscriptionUpdate

class JmsHandoverNotificationSenderSpec extends SkeletonSpec {

    @ObjectUnderTest
    JMSFailoverHandler pmijmsFailoverHandler

    @Inject
    @Modeled
    EventSender<PmicSubscriptionUpdate> eventSender

    @ImplementationInstance
    JMSFailOverEvent event = mock(JMSFailOverEvent)

    def "verify that when handover event is received Notification is sent to external consumer"() {
        when:
        pmijmsFailoverHandler.handleFailOver(event)

        then: "PmicSubscriptionUpdate notification with two attributes are sent to Jms Topic"
        1 * eventSender.send({ PmicSubscriptionUpdate pmicSubscriptionUpdate ->
            pmicSubscriptionUpdate.getPmicSubscriptionChangedAttributeList()
                    .size() == 2
        })
    }
}
