/*******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.initiation.notification.events

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.exception.SubscriptionNotFoundDataAccessException
import com.ericsson.oss.services.pm.generic.NodeService
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService

class DeactivationEventNonFlowSpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    DeactivationEvent objectUnderTest

    @Inject
    SubscriptionOperationExecutionTrackingCacheWrapper subscriptionInitiationCacheWrapper

    @Inject
    NodeService nodeService

    @ImplementationInstance
    SubscriptionReadOperationService subscriptionReadOperationService = Mock(SubscriptionReadOperationService)

    @ImplementationInstance
    SubscriptionWriteOperationService subscriptionServiceWriteOperation = Mock(SubscriptionWriteOperationService)

    @ImplementationInstance
    InitiationEventUtils InitiationEventUtil = Spy(InitiationEventUtils)

    ManagedObject subscriptionMO, nodeMO1, nodeMO2, nodeMO3
    def counters = [new CounterInfo("pmLicDlCapActual", "BbProcessingResource"),
                    new CounterInfo("pmLicDlCapDistr", "BbProcessingResource"),
                    new CounterInfo("pmLicDlPrbCapActual", "BbProcessingResource"),
                    new CounterInfo("pmAdmNrRrcUnknownArpRatio", "AdmissionControl")]

    def setup() {
        nodeMO1 = nodeUtil.builder("LTE01ERBS00001").build()
        nodeMO2 = nodeUtil.builder("LTE01ERBS00002").build()
        nodeMO3 = nodeUtil.builder("LTE01ERBS00003").build()
    }

    def "verify that when deactivating subscription, if exception is thrown, the initiation cache entry will not be removed"() {
        given: "Statistical Subscription exists in DPS with valid nodes"
        def subscription = new StatisticalSubscription()
        subscription.setType(SubscriptionType.STATISTICAL)
        subscription.setUserType(UserType.SYSTEM_DEF)
        subscription.setAdministrationState(AdministrationState.ACTIVE)
        subscription.setId(1L)
        subscriptionReadOperationService.findByIdWithRetry(1L, true) >> subscription
        initiationEventUtil.getNodesForSubscription(subscription) >> []
        and: "Exception is thrown when subscription's admin state is updated"
        subscriptionServiceWriteOperation.updateAttributes(subscription.getId(), _ as Map) >> {
            throw new SubscriptionNotFoundDataAccessException("x")
        }
        when: "subscription is deactivated"
        objectUnderTest.execute(1L)
        then: "there is a single entry in the cache because the execution of deactivation logic could not be performed"
        subscriptionInitiationCacheWrapper.getAllEntries().size() == 1
    }

    def "verify that when deactivating subscription, the initiation cache entry will be empty if with id does not exist in DPS"() {
        when: "subscription is deactivated"
        objectUnderTest.execute(123L)
        then: "initiation cache is empty"
        subscriptionInitiationCacheWrapper.getAllEntries().isEmpty()
    }

    def "verify that when deactivating subscription, the initiation cache entry will be empty if subscription has no nodes to deactivate"() {
        given: "Statistical Subscription exists in DPS"
        def subscription = new StatisticalSubscription()
        subscription.setAdministrationState(AdministrationState.DEACTIVATING)
        subscription.setId(123L)
        subscription.setUserType(UserType.USER_DEF)
        subscription.setType(SubscriptionType.STATISTICAL)
        subscription.setName("testSub")
        subscriptionReadOperationService.findByIdWithRetry(123L, true) >> subscription
        initiationEventUtil.getNodesForSubscription(subscription) >> []
        when: "subscription is deactivated"
        objectUnderTest.execute(123L)
        then: "initiation cache is empty"
        subscriptionInitiationCacheWrapper.getAllEntries().isEmpty()
    }

    def "verify that when deactivating subscription, the initiation cache entry will be empty even if no tasks were sent"() {
        given: "Celltrace Subscription exists in DPS with no events"
        def subscription = new CellTraceSubscription()
        subscription.setAdministrationState(AdministrationState.DEACTIVATING)
        subscription.setId(1234L)
        subscription.setUserType(UserType.USER_DEF)
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setName("testSub")
        subscriptionReadOperationService.findByIdWithRetry(1234L, true) >> subscription
        Node node = new Node()
        node.setFdn("NetworkElement=TestNode")
        initiationEventUtil.getNodesForSubscription(subscription) >> [node]
        when: "subscription is deactivated"
        objectUnderTest.execute(1234L)
        then: "initiation cache is empty"
        subscriptionInitiationCacheWrapper.getAllEntries().isEmpty()
    }

    def "verify that when removing nodes from subscription, the initiation cache will be not empty if exception is thrown when updating admin state"() {
        given: "Statistical Subscription exists in DPS with valid nodes"
        //no scanners so task creation will fail
        def subscription = new StatisticalSubscription()
        subscription.setType(SubscriptionType.STATISTICAL)
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(AdministrationState.ACTIVE)
        subscription.setId(1L)
        subscriptionReadOperationService.findByIdWithRetry(1L, true) >> subscription
        and: "Exception is thrown when subscription is updated"
        subscriptionServiceWriteOperation.updateAttributes(subscription.getId(), _ as Map) >> {
            throw new SubscriptionNotFoundDataAccessException("x")
        }
        when: "2 nodes are removed to subscription"
        objectUnderTest.execute(nodeService.findAllByFdn([nodeMO2.getFdn(), nodeMO3.getFdn()]), subscription)
        then: "entry will remain in the initiation cache"
        subscriptionInitiationCacheWrapper.getAllEntries().size() == 1
    }
}
