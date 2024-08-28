/*******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.common.systemdefined

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.subscription.capability.SubscriptionCapabilityReader
import com.ericsson.oss.services.pm.scheduling.impl.SystemDefinedSubscriptionAuditScheduler

class SubscriptionAuditorImplMockSpec extends SkeletonSpec {

    @ObjectUnderTest
    SystemDefinedSubscriptionAuditScheduler auditScheduler

    @ImplementationInstance
    SubscriptionCapabilityReader reader = Mock(SubscriptionCapabilityReader)

    @ImplementationInstance
    SubscriptionDao subscriptionDao = Mock(SubscriptionDao)

    def "TORF-197446 will not try to find a subscription if the subscription name from capability is null"() {
        given:
        final SystemDefinedPmCapabilities capabilities = new SystemDefinedPmCapabilities()
        capabilities.put('nameAsEnumString', 'SomeSubName')
        capabilities.put('countersEventsValidationApplicable', true)
        capabilities.addTargetType('NODE', 'ERBS')
        capabilities.put('type', 'STATISTICAL')
        reader.getSupportedSystemDefinedPmCapabilities() >> ['SomeSubName': [capabilities]]
        reader.getSystemDefinedSubscriptionAttributes(_, _) >> capabilities.pmCapabilities
        when:
        auditScheduler.onTimeout()
        then:
        noExceptionThrown()
        0 * subscriptionDao._(_)
    }
}