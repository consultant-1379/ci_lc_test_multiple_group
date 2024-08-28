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

import static com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_ACTIVATE_NODES
import static com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_DEACTIVATE_NODES
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE

import javax.inject.Inject
import java.util.concurrent.TimeUnit

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.sdk.cluster.MembershipChangeEvent
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.pmic.dto.subscription.enums.*
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.PmServiceEjbFullSpec
import com.ericsson.oss.services.pm.initiation.cache.EbsSubscriptionInitiationData
import com.ericsson.oss.services.pm.initiation.cache.EbsSubscriptionInitiationQueue
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventType
import com.ericsson.oss.services.pm.initiation.schedulers.SubscriptionIntermediaryStateTimer
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener

class SubscriptionIntermediaryStateTimerSpec extends PmServiceEjbFullSpec {

    @ObjectUnderTest
    private SubscriptionIntermediaryStateTimer objectUnderTest

    @Inject
    private PMICInitiationTrackerCache pmicInitiationTrackerCache

    @Inject
    private SubscriptionOperationExecutionTrackingCacheWrapper subscriptionOperationExecutionTrackingCacheWrapper

    @Inject
    private EbsSubscriptionInitiationQueue ebsSubscriptionsInitiationQueue

    @Inject
    private TimeGenerator timeGenerator

    @Inject
    private SubscriptionDao subscriptionDao

    @Inject
    private MembershipListener membershipListener

    MembershipChangeEvent changeEvent = Mock(MembershipChangeEvent)

    AdministrationState[] adminStatesArray = [AdministrationState.ACTIVATING, AdministrationState.DEACTIVATING, AdministrationState.UPDATING]

    def 'do not change admin state for ACTIVATING/DEACTIVATING/UPDATING subscriptions present in PMICInitiationResponseCache'() {
        given: 'subscriptions in PMICInitiationResponseCache with ACTIVATING/DEACTIVATING/UPDATING state'
            def subscriptionActivatingMoForCache = statisticalSubscriptionBuilder.name('subActivating').administrativeState(AdministrationState.ACTIVATING).build()
            def subscriptionDeactivatingMoForCache = statisticalSubscriptionBuilder.name('subDeactivating').administrativeState(AdministrationState.DEACTIVATING).build()
            def subscriptionUpdatingMoForCache = statisticalSubscriptionBuilder.name('subUpdating').administrativeState(AdministrationState.UPDATING).build()

            def nodes = ['NetworkElement=0': 'RadioNode', 'NetworkElement=1': 'RadioNode', 'NetworkElement=2': 'RadioNode']

            pmicInitiationTrackerCache.startTrackingActivation(String.valueOf(subscriptionActivatingMoForCache.poId), AdministrationState.ACTIVATING.name(), nodes, null)
            pmicInitiationTrackerCache.startTrackingDeactivation(String.valueOf(subscriptionDeactivatingMoForCache.poId), AdministrationState.DEACTIVATING.name(), nodes, null)
            pmicInitiationTrackerCache.startTrackingActivation(String.valueOf(subscriptionUpdatingMoForCache.poId), AdministrationState.UPDATING.name(), nodes, null)

        when: 'timer executes'
            objectUnderTest.execute()

        then: 'no action is taken'
            def subscriptionsInHungState = subscriptionDao.findAllBySubscriptionTypeAndAdministrationState(null, adminStatesArray, true)
            3 == subscriptionsInHungState.size()
            assert subscriptionsInHungState.find { it.name == 'subActivating' }
            assert subscriptionsInHungState.find { it.name == 'subDeactivating' }
            assert subscriptionsInHungState.find { it.name == 'subUpdating' }
    }

    def 'change subscription hung in ACTIVATING/DEACTIVATING/UPDATING state to ACTIVE/INACTIVE/ACTIVE state if not present in PMICInitiationResponseCache'() {
        given: 'subscriptions hung in ACTIVATING, DEACTIVATING and UPDATING state(for > 5 mins) not present in PMICInitiationResponseCache'
            def subscriptionActivatingMo = dps.subscription()
                    .type(SubscriptionType.STATISTICAL)
                    .name('subHungActivating')
                    .administrationState(AdministrationState.ACTIVATING)
                    .taskStatus(TaskStatus.ERROR)
                    .userActivationDateTime(new Date(timeGenerator.currentTimeMillis() - TimeUnit.MINUTES.toMillis(6)))
                    .build()
            def subscriptionDeactivatingMo = dps.subscription()
                    .type(SubscriptionType.STATISTICAL)
                    .name('subHungDeactivating')
                    .administrationState(AdministrationState.DEACTIVATING)
                    .taskStatus(TaskStatus.ERROR)
                    .userDeActivationDateTime(new Date(timeGenerator.currentTimeMillis() - TimeUnit.MINUTES.toMillis(6)))
                    .build()
            def subscriptionUpdatingMo = dps.subscription()
                    .type(SubscriptionType.STATISTICAL)
                    .name('subHungUpdating')
                    .administrationState(AdministrationState.UPDATING)
                    .taskStatus(TaskStatus.NA)
                    .userActivationDateTime(new Date(timeGenerator.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10)))
                    .build()

        and: 'subscriptions in PMICInitiationResponseCache with ACTIVATING/DEACTIVATING/UPDATING state'
            def subscriptionActivatingMoForCache = statisticalSubscriptionBuilder.name('subActivating')
                    .administrativeState(AdministrationState.ACTIVATING)
                    .build()
            def subscriptionDeactivatingMoForCache = statisticalSubscriptionBuilder.name('subDeactivating')
                    .administrativeState(AdministrationState.DEACTIVATING)
                    .build()
            def subscriptionUpdatingMoForCache = statisticalSubscriptionBuilder.name('subUpdating')
                    .administrativeState(AdministrationState.UPDATING)
                    .build()

            def nodes = ['NetworkElement=0': 'RadioNode', 'NetworkElement=1': 'RadioNode', 'NetworkElement=2': 'RadioNode']

            pmicInitiationTrackerCache.startTrackingActivation(String.valueOf(subscriptionActivatingMoForCache.poId), AdministrationState.ACTIVATING.name(), nodes, null)
            pmicInitiationTrackerCache.startTrackingDeactivation(String.valueOf(subscriptionDeactivatingMoForCache.poId), AdministrationState.DEACTIVATING.name(), nodes, null)
            pmicInitiationTrackerCache.startTrackingActivation(String.valueOf(subscriptionUpdatingMoForCache.poId), AdministrationState.UPDATING.name(), nodes, null)

        when: 'timer executes'
            objectUnderTest.execute()

        then: 'subscription hung in ACTIVATING state is set to ACTIVE'
            AdministrationState.ACTIVE.name() == (String) subscriptionActivatingMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            TaskStatus.OK.name() == (String) subscriptionActivatingMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'subscription hung in DEACTIVATING state is set to INACTIVE'
            AdministrationState.INACTIVE.name() == (String) subscriptionDeactivatingMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            TaskStatus.OK.name() == (String) subscriptionActivatingMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'subscription hung in UPDATING state is set to ACTIVE'
            AdministrationState.ACTIVE.name() == (String) subscriptionUpdatingMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            TaskStatus.OK.name() == (String) subscriptionActivatingMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'no action is taken for subscriptions in PMICInitiationResponseCache'
            List<Subscription> subscriptionsInHungState = subscriptionDao.findAllBySubscriptionTypeAndAdministrationState(null, adminStatesArray, true)
            3 == subscriptionsInHungState.size()
            assert subscriptionsInHungState.find { it.name == 'subActivating' }
            assert subscriptionsInHungState.find { it.name == 'subDeactivating' }
            assert subscriptionsInHungState.find { it.name == 'subUpdating' }
    }

    def 'change subscription hung in ACTIVATING state to ACTIVE state if not present in PMICInitiationResponseCache'() {
        given: 'subscriptions hung in ACTIVATING state(for > 5 mins) not present in PMICInitiationResponseCache'
            def subscriptionActivatingMo = dps.subscription().type(SubscriptionType.STATISTICAL).name('subHungActivating').administrationState(AdministrationState.ACTIVATING).taskStatus(TaskStatus.NA)
                    .userActivationDateTime(new Date(timeGenerator.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10))).build()

        and: 'subscriptions in ACTIVATING state(< 5 mins) not present in PMICInitiationResponseCache'
            def subscriptionActivatingTwoMinutesMo = dps.subscription().type(SubscriptionType.STATISTICAL).name('subInActivatingStateForTwoMinutes').administrationState(AdministrationState.ACTIVATING).taskStatus(TaskStatus.ERROR)
                    .userActivationDateTime(new Date(timeGenerator.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2))).build()

        and: 'subscriptions in PMICInitiationResponseCache with ACTIVATING state'
            def subscriptionActivatingMoForCache = statisticalSubscriptionBuilder.name('subActivating').administrativeState(AdministrationState.ACTIVATING).build()
            def nodes = ['NetworkElement=0': 'RadioNode', 'NetworkElement=1': 'RadioNode', 'NetworkElement=2': 'RadioNode']
            pmicInitiationTrackerCache.startTrackingActivation(String.valueOf(subscriptionActivatingMoForCache.poId), AdministrationState.ACTIVATING.name(), nodes, null)

        and: 'subscriptions in SubscriptionOperationExecutionTrackingCacheWrapper with ACTIVATING state'
            def subscriptionActivatingMoForOperationCache = statisticalSubscriptionBuilder.name('subStartedActivating').administrativeState(AdministrationState.ACTIVATING).build()
            subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionActivatingMoForOperationCache.poId, OPERATION_ACTIVATE_NODES)

        and: 'subscriptions in ebsSubscriptionsInitiationQueue with ACTIVATING state'
            def ebsSubscriptionActivatingMoInQueue = cellTraceSubscriptionBuilder.name('ebsSubStartedActivating').administrativeState(AdministrationState.ACTIVATING).build()
            ebsSubscriptionsInitiationQueue.addToQueue(new EbsSubscriptionInitiationData([], createEbsSubscription(ebsSubscriptionActivatingMoInQueue.poId, AdministrationState.ACTIVATING), InitiationEventType.SUBSCRIPTION_ACTIVATION))

        when: 'timer executes'
            objectUnderTest.execute()

        then: 'act on subscription that got into ACTIVATING state for <5 minutes , but not present in PMICInitiationResponseCache '
            AdministrationState.ACTIVATING.name() == (String) subscriptionActivatingTwoMinutesMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            TaskStatus.ERROR.name() == (String) subscriptionActivatingTwoMinutesMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'act on subscription that got into ACTIVATING state for >5 minutes , but not present in PMICInitiationResponseCache '
            AdministrationState.ACTIVE.name() == (String) subscriptionActivatingMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            TaskStatus.OK.name() == (String) subscriptionActivatingMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'no action is taken for subscription that got into ACTIVATING state, but present in PMICInitiationResponseCache/SubscriptionOperationExecutionTrackingCacheWrapper/ebsSubscriptionsInitiationQueue'
            def subscriptionsInHungState = subscriptionDao.findAllBySubscriptionTypeAndAdministrationState(null, adminStatesArray, true);
            4 == subscriptionsInHungState.size()
            assert subscriptionsInHungState.find { it.name == 'subInActivatingStateForTwoMinutes' }
            assert subscriptionsInHungState.find { it.name == 'subActivating' }
            assert subscriptionsInHungState.find { it.name == 'subStartedActivating' }
            assert subscriptionsInHungState.find { it.name == 'ebsSubStartedActivating' }
    }

    def 'change subscription hung in DEACTIVATING state to INACTIVE state if not present in PMICInitiationResponseCache'() {
        given: 'subscriptions hung in DEACTIVATING state(for > 5 mins) not present in PMICInitiationResponseCache'
            def subscriptionDeactivatingMo = dps.subscription().type(SubscriptionType.STATISTICAL).name('subHungDeactivating').administrationState(AdministrationState.DEACTIVATING).taskStatus(TaskStatus.NA)
                    .userDeActivationDateTime(new Date(timeGenerator.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10))).build()

        and: 'subscriptions in DEACTIVATING state(< 5 mins) not present in PMICInitiationResponseCache'
            def subscriptionDeactivatingTwoMinutesMo = dps.subscription().type(SubscriptionType.STATISTICAL).name('subInDeactivatingStateForTwoMinutes').administrationState(AdministrationState.DEACTIVATING).taskStatus(TaskStatus.ERROR)
                    .userDeActivationDateTime(new Date(timeGenerator.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2))).build()

        and: 'subscriptions in PMICInitiationResponseCache with DEACTIVATING state'
            def subscriptionDeactivatingMoForCache = statisticalSubscriptionBuilder.name('subDeactivating').administrativeState(AdministrationState.DEACTIVATING).build()
            def nodes = ['NetworkElement=0': 'RadioNode', 'NetworkElement=1': 'RadioNode', 'NetworkElement=2': 'RadioNode']
            pmicInitiationTrackerCache.startTrackingDeactivation(String.valueOf(subscriptionDeactivatingMoForCache.poId), AdministrationState.DEACTIVATING.name(), nodes, null)

        and: 'subscriptions in SubscriptionOperationExecutionTrackingCacheWrapper with ACTIVATING state'
            def subscriptionDeactivatingMoForOperationCache = statisticalSubscriptionBuilder.name('subStartedDeactivating').administrativeState(AdministrationState.DEACTIVATING).build()
            subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionDeactivatingMoForOperationCache.poId, OPERATION_DEACTIVATE_NODES)

        and: 'subscriptions in ebsSubscriptionsInitiationQueue with DEACTIVATING state'
            def ebsSubscriptionDeactivatingMoInQueue = cellTraceSubscriptionBuilder.name('ebsSubStartedDeactivating').administrativeState(AdministrationState.DEACTIVATING).build()
            ebsSubscriptionsInitiationQueue.addToQueue(new EbsSubscriptionInitiationData([], createEbsSubscription(ebsSubscriptionDeactivatingMoInQueue.poId, AdministrationState.DEACTIVATING), InitiationEventType.SUBSCRIPTION_DEACTIVATION))

        when: 'timer executes'
            objectUnderTest.execute()

        then: 'act on subscription that got into DEACTIVATING state for <5 minutes , but not present in PMICInitiationResponseCache '
            AdministrationState.DEACTIVATING.name() == (String) subscriptionDeactivatingTwoMinutesMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            TaskStatus.ERROR.name() == (String) subscriptionDeactivatingTwoMinutesMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'act on subscription that got into DEACTIVATING state for >5 minutes , but not present in PMICInitiationResponseCache '
            AdministrationState.INACTIVE.name() == (String) subscriptionDeactivatingMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            TaskStatus.OK.name() == (String) subscriptionDeactivatingMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'no action is taken for subscription that got into DEACTIVATING state, but present in PMICInitiationResponseCache/SubscriptionOperationExecutionTrackingCacheWrapper/ebsSubscriptionsInitiationQueue'
            def subscriptionsInHungState = subscriptionDao.findAllBySubscriptionTypeAndAdministrationState(null, adminStatesArray, true)
            4 == subscriptionsInHungState.size()
            assert subscriptionsInHungState.find { it.name == 'subInDeactivatingStateForTwoMinutes' }
            assert subscriptionsInHungState.find { it.name == 'subDeactivating' }
            assert subscriptionsInHungState.find { it.name == 'subStartedDeactivating' }
            assert subscriptionsInHungState.find { it.name == 'ebsSubStartedDeactivating' }
    }

    def 'change subscription hung in UPDATING state to ACTIVE state if not present in PMICInitiationResponseCache'() {
        given: 'subscriptions hung in UPDATING state(for > 5 mins) not present in PMICInitiationResponseCache'
            def subscriptionUpdatingMo = dps.subscription().type(SubscriptionType.STATISTICAL).name('subHungUpdating').administrationState(AdministrationState.UPDATING).taskStatus(TaskStatus.NA)
                    .userActivationDateTime(new Date(timeGenerator.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10))).build()

        and: 'subscriptions in UPDATING state(< 5 mins) not present in PMICInitiationResponseCache'
            def subscriptionUpdatingTwoMinutesMo = dps.subscription().type(SubscriptionType.STATISTICAL).name('subInUpdatingStateForTwoMinutes').administrationState(AdministrationState.UPDATING).taskStatus(TaskStatus.ERROR)
                    .userActivationDateTime(new Date(timeGenerator.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2))).build()

        and: 'subscriptions in PMICInitiationResponseCache with UPDATING state'
            def subscriptionUpdatingMoForCache = statisticalSubscriptionBuilder.name('subUpdating').administrativeState(AdministrationState.UPDATING).build()
            def nodes = ['NetworkElement=0': 'RadioNode', 'NetworkElement=1': 'RadioNode', 'NetworkElement=2': 'RadioNode']
            pmicInitiationTrackerCache.startTrackingActivation(String.valueOf(subscriptionUpdatingMoForCache.poId), AdministrationState.UPDATING.name(), nodes, null)

        and: 'subscriptions in SubscriptionOperationExecutionTrackingCacheWrapper with UPDATING state'
            def subscriptionUpdatingMoForOperationCache = statisticalSubscriptionBuilder.name('subStartedUpdating').administrativeState(AdministrationState.UPDATING).build()
            subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionUpdatingMoForOperationCache.poId, OPERATION_ACTIVATE_NODES)

        and: 'subscriptions in ebsSubscriptionsInitiationQueue with UPDATING state'
            def ebsSubscriptionUpdatingMoInQueue = cellTraceSubscriptionBuilder.name('ebsSubStartedUpdating').administrativeState(AdministrationState.UPDATING).build()
            ebsSubscriptionsInitiationQueue.addToQueue(new EbsSubscriptionInitiationData([], createEbsSubscription(ebsSubscriptionUpdatingMoInQueue.poId, AdministrationState.ACTIVATING), InitiationEventType.SUBSCRIPTION_ACTIVATION))

        when: 'timer executes'
            objectUnderTest.execute()

        then: 'act on subscription that got into UPDATING state for <5 minutes , but not present in PMICInitiationResponseCache '
            AdministrationState.ACTIVE.name() == (String) subscriptionUpdatingTwoMinutesMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            TaskStatus.OK.name() == (String) subscriptionUpdatingTwoMinutesMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'act on subscription that got into UPDATING state for >5 minutes , but not present in PMICInitiationResponseCache '
            AdministrationState.ACTIVE.name() == (String) subscriptionUpdatingMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
            TaskStatus.OK.name() == (String) subscriptionUpdatingMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TASKSTATUSTYPE)

        and: 'no action is taken for subscription that got into UPDATING state, but present in PMICInitiationResponseCache/SubscriptionOperationExecutionTrackingCacheWrapper/ebsSubscriptionsInitiationQueue'
            def subscriptionsInHungState = subscriptionDao.findAllBySubscriptionTypeAndAdministrationState(null, adminStatesArray, true)
            3 == subscriptionsInHungState.size()
            assert subscriptionsInHungState.find { it.name == 'subUpdating' }
            assert subscriptionsInHungState.find { it.name == 'subStartedUpdating' }
            assert subscriptionsInHungState.find { it.name == 'ebsSubStartedUpdating' }
    }

    def 'no action is taken if memberships is not Master'() {
        given: 'membership not master'
            changeEvent.isMaster() >> false
            membershipListener.listenForMembershipChange(changeEvent)

        and: 'subscriptions hung in ACTIVATING, DEACTIVATING and UPDATING state not present in PMICInitiationResponseCache'
            statisticalSubscriptionBuilder.name('subHungActivating').administrativeState(AdministrationState.ACTIVATING).build()
            statisticalSubscriptionBuilder.name('subHungDeactivating').administrativeState(AdministrationState.DEACTIVATING).build()
            statisticalSubscriptionBuilder.name('subHungUpdating').administrativeState(AdministrationState.UPDATING).build()

        and: 'subscriptions in PMICInitiationResponseCache with ACTIVATING/DEACTIVATING/UPDATING state'
            def subscriptionActivatingMoForCache = statisticalSubscriptionBuilder.name('subActivating').administrativeState(AdministrationState.ACTIVATING).build()
            def subscriptionDeactivatingMoForCache = statisticalSubscriptionBuilder.name('subDeactivating').administrativeState(AdministrationState.DEACTIVATING).build()
            def subscriptionUpdatingMoForCache = statisticalSubscriptionBuilder.name('subUpdating').administrativeState(AdministrationState.UPDATING).build()

            final nodes = ['NetworkElement=0': 'RadioNode', 'NetworkElement=1':'RadioNode', 'NetworkElement=2': 'RadioNode']

            pmicInitiationTrackerCache.startTrackingActivation(String.valueOf(subscriptionActivatingMoForCache.poId), AdministrationState.ACTIVATING.name(), nodes, null)
            pmicInitiationTrackerCache.startTrackingDeactivation(String.valueOf(subscriptionDeactivatingMoForCache.poId), AdministrationState.DEACTIVATING.name(), nodes, null)
            pmicInitiationTrackerCache.startTrackingActivation(String.valueOf(subscriptionUpdatingMoForCache.poId), AdministrationState.UPDATING.name(), nodes, null)

        when: 'timer executes'
            objectUnderTest.execute()

        then: 'no action is taken'
            def subscriptionsInHungState = subscriptionDao.findAllBySubscriptionTypeAndAdministrationState(null, adminStatesArray, true)
            6 == subscriptionsInHungState.size()
            assert subscriptionsInHungState.find { it.name == 'subActivating' }
            assert subscriptionsInHungState.find { it.name == 'subDeactivating' }
            assert subscriptionsInHungState.find { it.name == 'subUpdating' }
            assert subscriptionsInHungState.find { it.name == 'subHungActivating' }
            assert subscriptionsInHungState.find { it.name == 'subHungDeactivating' }
            assert subscriptionsInHungState.find { it.name == 'subHungUpdating' }
    }

    def createEbsSubscription(Long poid, AdministrationState state) {
        final Subscription subscription = new CellTraceSubscription()
        subscription.id = poid
        subscription.type = SubscriptionType.CELLTRACE
        subscription.administrationState = state
        subscription.userType = UserType.SYSTEM_DEF
        subscription.cellTraceCategory = CellTraceCategory.EBSL_STREAM
        return subscription
    }
}
