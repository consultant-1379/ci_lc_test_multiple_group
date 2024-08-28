/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.scheduler

import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.services.pm.exception.DataAccessException
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.schedulers.SubscriptionIntermediaryStateTimer

class SubscriptionIntermediaryStateTimerMockSpec extends SkeletonSpec {

    @ObjectUnderTest
    private SubscriptionIntermediaryStateTimer objectUnderTest

    @Inject
    private PMICInitiationTrackerCache pmicInitiationTrackerCache

    @ImplementationInstance
    SubscriptionDao subscriptionDao = Mock(SubscriptionDao)

    private ManagedObject subscriptionActivatingMoForCache
    private ManagedObject subscriptionDeactivatingMoForCache
    private ManagedObject subscriptionUpdatingMoForCache

    private ManagedObject subscriptionActivatingMo
    private ManagedObject subscriptionDeactivatingMo
    private ManagedObject subscriptionUpdatingMo

    def setup() {
        subscriptionActivatingMoForCache = statisticalSubscriptionBuilder.name('subActivating').administrativeState(AdministrationState.ACTIVATING).build()
        subscriptionDeactivatingMoForCache = statisticalSubscriptionBuilder.name('subDeactivating').administrativeState(AdministrationState.DEACTIVATING).build()
        subscriptionUpdatingMoForCache = statisticalSubscriptionBuilder.name('subUpdating').administrativeState(AdministrationState.UPDATING).build()

        def nodes = ['NetworkElement=0': 'RadioNode', 'NetworkElement=1': 'RadioNode', 'NetworkElement=2': 'RadioNode']

        pmicInitiationTrackerCache.startTrackingActivation(String.valueOf(subscriptionActivatingMoForCache.getPoId()), AdministrationState.ACTIVATING.name(), nodes, null)
        pmicInitiationTrackerCache.startTrackingDeactivation(String.valueOf(subscriptionDeactivatingMoForCache.getPoId()), AdministrationState.DEACTIVATING.name(), nodes, null)
        pmicInitiationTrackerCache.startTrackingActivation(String.valueOf(subscriptionUpdatingMoForCache.getPoId()), AdministrationState.UPDATING.name(), nodes, null)

        subscriptionActivatingMo = statisticalSubscriptionBuilder.name('subHungActivating').administrativeState(AdministrationState.ACTIVATING).build()
        subscriptionDeactivatingMo = statisticalSubscriptionBuilder.name('subHungDeactivating').administrativeState(AdministrationState.DEACTIVATING).build()
        subscriptionUpdatingMo = statisticalSubscriptionBuilder.name('subHungUpdating').administrativeState(AdministrationState.UPDATING).build()
    }

    def 'no action is taken if exception is raised when searching for subscription in intermediary state'() {
        given: 'exception is raised'
            subscriptionDao.findAllBySubscriptionTypeAndAdministrationState(*_) >> { throw exception.newInstance('exception message') }

        when: 'timer executes'
            objectUnderTest.execute()

        then: 'no action is taken'
            AdministrationState.ACTIVATING.name() == (String) subscriptionActivatingMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            AdministrationState.DEACTIVATING.name() == (String) subscriptionDeactivatingMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            AdministrationState.UPDATING.name() == (String) subscriptionUpdatingMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)

            AdministrationState.ACTIVATING.name() == (String) subscriptionActivatingMoForCache.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            AdministrationState.DEACTIVATING.name() == (String) subscriptionDeactivatingMoForCache.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            AdministrationState.UPDATING.name() == (String) subscriptionUpdatingMoForCache.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)

        where:
            exception << [RuntimeDataAccessException, DataAccessException]
    }
}
