package com.ericsson.oss.services.pm.services.generic

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.PersistenceTrackingState
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription
import com.ericsson.oss.services.pm.exception.DataAccessException

class SubscriptionReadOperationServiceMockSpec extends SkeletonSpec {

    @Inject
    SubscriptionWriteOperationService subscriptionWriteOperationService

    @Inject
    SubscriptionReadOperationService subscriptionReadOperationService

    @ImplementationInstance
    SubscriptionDao subscriptionDao = Mock(SubscriptionDao)

    def "save will update tracker to error if an exception is thrown from DPS"() {
        given:
        StatisticalSubscription sub = new StatisticalSubscription()
        subscriptionDao.saveOrUpdate(sub) >> { throw new DataAccessException("x") }
        when:
        subscriptionWriteOperationService.saveOrUpdate(sub, "123")
        then:
        noExceptionThrown()
        subscriptionReadOperationService.getTrackingStatus("123").getState() == PersistenceTrackingState.ERROR
        subscriptionReadOperationService.getTrackingStatus("123").getErrorMessage() == "Subscription null could not be created. Please try again."
    }

}
