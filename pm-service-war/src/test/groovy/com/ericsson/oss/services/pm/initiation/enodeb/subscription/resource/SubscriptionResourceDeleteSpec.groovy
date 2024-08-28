/*******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource

import spock.lang.Shared
import spock.lang.Unroll

import javax.inject.Inject
import javax.ws.rs.core.Response

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.sdk.security.accesscontrol.EAccessControl
import com.ericsson.oss.pmic.cdi.test.util.PmBaseSpec
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService

class SubscriptionResourceDeleteSpec extends PmBaseSpec {

    private static final NODE_NAME_CUSTOM_NODE = "Node_NoOssModelIdentity_NoCounters";

    @ObjectUnderTest
    private SubscriptionResource objectUnderTest;

    @ImplementationInstance
    private EAccessControl eAccessControl = Mock(EAccessControl)

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService

    @Shared
    def customNode

    @ImplementationInstance
    PmFunctionEnabledWrapper mockedPmFunctionEnabledWrapper = Mock(PmFunctionEnabledWrapper)

    def setup() {
        eAccessControl.isAuthorized(_, _) >> true

        customNode = nodeUtil.builder(NODE_NAME_CUSTOM_NODE).ossModelIdentity(null).build()
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> true
    }

    @Unroll

    def "will delete successfully a subscription with nodes without ossModelIdentity and not supporting counters"() {
        given: "statistical subscription"

        def subMO = statisticalSubscriptionBuilder.name("TestStatSub").node(customNode).userType(UserType.USER_DEF).owner("Administrator").administrativeState(AdministrationState.INACTIVE).taskStatus(TaskStatus.OK).build()

        when: "the subscription is deleted"
        def response = objectUnderTest.deleteSingleSubscription(subMO.getPoId() as String)

        then: "the result of the delete is OK (200)"
        response.getStatus() == Response.Status.OK.getStatusCode()

        and: "the subscription is not found"
        subscriptionReadOperationService.existsByFdn(subMO.getFdn()) == false
    }
}
