package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource

import spock.lang.Unroll

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.sdk.security.accesscontrol.EAccessControl
import com.ericsson.oss.itpf.sdk.security.accesscontrol.ESecurityAction
import com.ericsson.oss.itpf.sdk.security.accesscontrol.ESecurityResource
import com.ericsson.oss.pmic.cdi.test.util.PmBaseSpec
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.services.pm.exception.RetryServiceException
import com.ericsson.oss.services.pm.initiation.common.accesscontrol.AccessControlResources
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService

class SubscriptionResourceCreateNameSpec extends PmBaseSpec {

    @ObjectUnderTest
    SubscriptionResource subscriptionResource

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService

    @Inject
    EAccessControl accessControl = Mock(EAccessControl)

    private static final String ERBS_STAT_SYS_DEF_SUBSCRIPTION = 'ERBS System Defined Statistical Subscription'
    private static final String CCTR_SYS_DEF_SUBSCRIPTION = 'ContinuousCellTraceSubscription'
    private static final String SOME_NAME = 'Some Name'

    def setup() {
        accessControl.isAuthorized(new ESecurityResource(AccessControlResources.SUBSCRIPTION), new ESecurityAction('create')) >> true
    }

    @Unroll
    def 'when createSingleSubscription is called with subscription name reserved for system defined subscription throws exception and will not save'() {
        given:
        def subscription = newSubscription(reservedName)

        when:
        subscriptionResource.createSingleSubscription(subscription)

        then:
        def exception = thrown(RetryServiceException)
        exception instanceof RetryServiceException
        exception.message == "Cannot use Reserved Subscription Name ${reservedName}"

        where:
        reservedName << [ERBS_STAT_SYS_DEF_SUBSCRIPTION, CCTR_SYS_DEF_SUBSCRIPTION]
    }

    def 'When createSingleSubscription is called with any other subscription name, subscription is saved'() {
        given:
        def subscription = newSubscription()

        when:
        subscriptionResource.createSingleSubscription(subscription)

        then:
        noExceptionThrown()
        subscriptionReadOperationService.findOneByExactName(SOME_NAME, false) != null
    }

    def newSubscription(name = SOME_NAME) {
        def subscription = new CellTraceSubscription()
        subscription.name = name
        subscription.type = SubscriptionType.CELLTRACE
        subscription.userType = UserType.USER_DEF
        return subscription
    }
}