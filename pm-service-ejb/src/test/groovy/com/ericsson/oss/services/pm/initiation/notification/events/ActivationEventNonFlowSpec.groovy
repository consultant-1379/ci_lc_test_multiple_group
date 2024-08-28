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
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.UserType
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState
import com.ericsson.oss.services.pm.exception.DataAccessException
import com.ericsson.oss.services.pm.exception.SubscriptionNotFoundDataAccessException
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper

class ActivationEventNonFlowSpec extends SkeletonSpec {

    @ObjectUnderTest
    ActivationEvent objectUnderTest

    @Inject
    SubscriptionOperationExecutionTrackingCacheWrapper subscriptionInitiationCacheWrapper

    @ImplementationInstance
    SubscriptionDao subscriptionDao = Mock(SubscriptionDao)

    @Inject
    NodeDao nodeDao

    @Inject
    EventSender<MediationTaskRequest> eventSender

    ManagedObject subscriptionMO, nodeMO1, nodeMO2, nodeMO3
    def events = [new EventInfo("a", "b")]

    def "verify that when activating subscription, if exception is thrown, the initiation cache entry will not be removed"() {
        given: "Exception is thrown when subscription is extracted from DPS"
        subscriptionDao.findOneById(123L, true) >> { throw new DataAccessException("x") }
        when: "subscription is activated"
        objectUnderTest.execute(123L)
        then: "there is a single entry in the cache because the execution of activation logic could not be performed"
        subscriptionInitiationCacheWrapper.getAllEntries().size() == 1
    }

    def "verify that when activating subscription, the initiation cache entry will be empty if subscription has no nodes to activate"() {
        given: "Celltrace Subscription exists in DPS"
        CellTraceSubscription subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setId(123L)
        subscriptionDao.findOneById(123L, true) >> { subscription }
        when: "subscription is activated"
        objectUnderTest.execute(123L)
        then: "initiation cache is empty"
        subscriptionInitiationCacheWrapper.getAllEntries().isEmpty()
    }

    def "verify that when activating subscription, the initiation cache entry will be empty if subscription has only pm function off nodes"() {
        given: "Celltrace Subscription exists in DPS with pm function off nodes"
        nodeMO1 = nodeUtil.builder("LTE01ERBS00001").pmEnabled(false).build()
        CellTraceSubscription subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setId(123L)
        subscription.setNodes([nodeDao.findOneById(nodeMO1.getPoId())])
        subscriptionDao.findOneById(123L, true) >> { subscription }
        when: "subscription is activated"
        objectUnderTest.execute(123L)
        then: "initiation cache is empty"
        subscriptionInitiationCacheWrapper.getAllEntries().isEmpty()
    }

    def "verify that when activating subscription, the initiation cache entry will be empty if subscription has only NeConfigurationManager disabled nodes"() {
        given: "Celltrace Subscription exists in DPS with pm function off nodes"
        nodeMO1 = nodeUtil.builder("LTE01ERBS00001").neConfigurationManagerState(NeConfigurationManagerState.DISABLED).build()
        CellTraceSubscription subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setId(123L)
        subscription.setNodes([nodeDao.findOneById(nodeMO1.getPoId())])
        subscriptionDao.findOneById(123L, true) >> { subscription }
        when: "subscription is activated"
        objectUnderTest.execute(123L)
        then: "initiation cache is empty"
        subscriptionInitiationCacheWrapper.getAllEntries().isEmpty()
        and: "No MTR is sent"
        0 * eventSender.send(_ as MediationTaskRequest)
    }

    def "verify that when activating subscription, the initiation cache entry will be empty even if no task was sent"() {
        given: "Celltrace Subscription exists in DPS with valid nodes"
        nodeMO1 = nodeUtil.builder("LTE01ERBS00001").build()
        CellTraceSubscription subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setId(123L)
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(AdministrationState.ACTIVATING)
        subscription.setNodes([nodeDao.findOneById(nodeMO1.getPoId())])
        subscriptionDao.findOneById(123L, true) >> { subscription } //no counters so not tasks will be created
        when: "subscription is activated"
        objectUnderTest.execute(123L)
        then: "initiation cache is empty"
        subscriptionInitiationCacheWrapper.getAllEntries().isEmpty()
    }

    def "verify that when adding pm function off nodes to subscription, the initiation cache will be empty"() {
        given: "Celltrace Subscription exists in DPS with valid nodes"
        nodeMO1 = nodeUtil.builder("LTE01ERBS00001").pmEnabled(false).build()
        nodeMO2 = nodeUtil.builder("LTE01ERBS00002").pmEnabled(false).build()
        nodeMO3 = nodeUtil.builder("LTE01ERBS00003").pmEnabled(false).build()

        CellTraceSubscription subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setId(123L)
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(AdministrationState.ACTIVATING)
        subscription.setNodes(nodeDao.findAllByFdn([nodeMO1.getFdn(), nodeMO2.getFdn(), nodeMO3.getFdn()]))
        subscription.setEvents(events)
        when: "2 nodes are added to subscription"
        objectUnderTest.execute(nodeDao.findAllByFdn([nodeMO2.getFdn(), nodeMO3.getFdn()]), subscription)
        then: "no entries will remain in the initiation cache"
        subscriptionInitiationCacheWrapper.getAllEntries().isEmpty()
    }

    def "verify that when adding NeConfigurationManager disabled nodes to subscription, the initiation cache will be empty"() {
        given: "Celltrace Subscription exists in DPS with valid nodes"
        nodeMO1 = nodeUtil.builder("LTE01ERBS00001").neConfigurationManagerState(NeConfigurationManagerState.DISABLED).build()
        nodeMO2 = nodeUtil.builder("LTE01ERBS00002").neConfigurationManagerState(NeConfigurationManagerState.DISABLED).build()
        nodeMO3 = nodeUtil.builder("LTE01ERBS00003").neConfigurationManagerState(NeConfigurationManagerState.DISABLED).build()

        CellTraceSubscription subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setId(123L)
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(AdministrationState.ACTIVATING)
        subscription.setNodes(nodeDao.findAllByFdn([nodeMO1.getFdn(), nodeMO2.getFdn(), nodeMO3.getFdn()]))
        subscription.setEvents(events)
        when: "2 nodes are added to subscription"
        objectUnderTest.execute(nodeDao.findAllByFdn([nodeMO2.getFdn(), nodeMO3.getFdn()]), subscription)
        then: "no entries will remain in the initiation cache"
        subscriptionInitiationCacheWrapper.getAllEntries().isEmpty()
        and: "No MTR is sent"
        0 * eventSender.send(_ as MediationTaskRequest)
    }

    def "verify that when adding pm function off nodes to subscription, the initiation cache will not be empty if exception is thrown"() {
        given: "Celltrace Subscription exists in DPS with valid nodes"
        nodeMO1 = nodeUtil.builder("LTE01ERBS00001").pmEnabled(false).build()
        nodeMO2 = nodeUtil.builder("LTE01ERBS00002").pmEnabled(false).build()
        nodeMO3 = nodeUtil.builder("LTE01ERBS00003").pmEnabled(false).build()

        CellTraceSubscription subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setId(123L)
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(AdministrationState.ACTIVATING)
        subscription.setNodes(nodeDao.findAllByFdn([nodeMO1.getFdn(), nodeMO2.getFdn(), nodeMO3.getFdn()]))
        subscription.setEvents(events)
        and: "throw exception when service tries to update subscription admin state and task status to Active Error"
        subscriptionDao.updateSubscriptionAttributes(subscription.getId(), _ as Map) >> { throw new SubscriptionNotFoundDataAccessException("x") }
        when: "2 nodes are added to subscription"
        objectUnderTest.execute(nodeDao.findAllByFdn([nodeMO2.getFdn(), nodeMO3.getFdn()]), subscription)
        then: "One entries will remain in the initiation cache"
        subscriptionInitiationCacheWrapper.getAllEntries().size() == 1
    }

    def "verify that when adding NeConfigurationManager disabled nodes to subscription, the initiation cache will not be empty if exception is thrown"() {
        given: "Celltrace Subscription exists in DPS with valid nodes"
        nodeMO1 = nodeUtil.builder("LTE01ERBS00001").neConfigurationManagerState(NeConfigurationManagerState.DISABLED).build()
        nodeMO2 = nodeUtil.builder("LTE01ERBS00002").neConfigurationManagerState(NeConfigurationManagerState.DISABLED).build()
        nodeMO3 = nodeUtil.builder("LTE01ERBS00003").neConfigurationManagerState(NeConfigurationManagerState.DISABLED).build()

        CellTraceSubscription subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setId(123L)
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(AdministrationState.ACTIVATING)
        subscription.setNodes(nodeDao.findAllByFdn([nodeMO1.getFdn(), nodeMO2.getFdn(), nodeMO3.getFdn()]))
        subscription.setEvents(events)
        and: "throw exception when service tries to update subscription admin state and task status to Active Error"
        subscriptionDao.updateSubscriptionAttributes(subscription.getId(), _ as Map) >> { throw new SubscriptionNotFoundDataAccessException("x") }
        when: "2 nodes are added to subscription"
        objectUnderTest.execute(nodeDao.findAllByFdn([nodeMO2.getFdn(), nodeMO3.getFdn()]), subscription)
        then: "One entries will remain in the initiation cache"
        subscriptionInitiationCacheWrapper.getAllEntries().size() == 1
        and: "No MTR is sent"
        0 * eventSender.send(_ as MediationTaskRequest)
    }

    def "verify that when adding nodes to subscription, the initiation cache will be empty even if no tasks were created/sent"() {
        given: "Celltrace Subscription exists in DPS with valid nodes"
        nodeMO1 = nodeUtil.builder("LTE01ERBS00001").build()
        nodeMO2 = nodeUtil.builder("LTE01ERBS00002").build()
        nodeMO3 = nodeUtil.builder("LTE01ERBS00003").build()

        CellTraceSubscription subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setId(123L)
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(AdministrationState.ACTIVATING)
        subscription.setNodes(nodeDao.findAllByFdn([nodeMO1.getFdn(), nodeMO2.getFdn(), nodeMO3.getFdn()])) // no counters = no tasks
        when: "2 nodes are added to subscription"
        objectUnderTest.execute(nodeDao.findAllByFdn([nodeMO2.getFdn(), nodeMO3.getFdn()]), subscription)
        then: "no entries will remain in the initiation cache"
        subscriptionInitiationCacheWrapper.getAllEntries().isEmpty()
    }

    def "verify that when adding nodes to subscription, the initiation cache will not be empty even if no tasks were created/sent but exception is thrown"() {
        given: "Celltrace Subscription exists in DPS with valid nodes"
        nodeMO1 = nodeUtil.builder("LTE01ERBS00001").build()
        nodeMO2 = nodeUtil.builder("LTE01ERBS00002").build()
        nodeMO3 = nodeUtil.builder("LTE01ERBS00003").build()

        CellTraceSubscription subscription = new CellTraceSubscription()
        subscription.setType(SubscriptionType.CELLTRACE)
        subscription.setId(123L)
        subscription.setUserType(UserType.USER_DEF)
        subscription.setAdministrationState(AdministrationState.ACTIVATING)
        subscription.setNodes(nodeDao.findAllByFdn([nodeMO1.getFdn(), nodeMO2.getFdn(), nodeMO3.getFdn()])) // no counters = no tasks
        and: "throw exception when service tries to update subscription admin state and task status to Active Error"
        subscriptionDao.updateSubscriptionAttributes(subscription.getId(), _ as Map) >> { throw new SubscriptionNotFoundDataAccessException("x") }
        when: "2 nodes are added to subscription"
        objectUnderTest.execute(nodeDao.findAllByFdn([nodeMO2.getFdn(), nodeMO3.getFdn()]), subscription)
        then: "One entries will remain in the initiation cache"
        subscriptionInitiationCacheWrapper.getAllEntries().size() == 1
    }

    /**
     +--------------------------------------------------------------------------------------------------------------------------------------------+
     | InitiationCache is updated when activation starts and entry is removed when Tasks are sent or when subscription is updated to Active Error |
     +--------------------------------------------------------------------------------------------------------------------------------------------+
     | Action                                         | Will remove entry             | Why                                                       |
     +------------------------------------------------+-------------------------------+-----------------------------------------------------------+
     | Activate Valid                                 | No                            | Exception when setting activation time                    |
     +------------------------------------------------+-------------------------------+-----------------------------------------------------------+
     | Activate no nodes                              | Yes                           | -                                                         |
     +------------------------------------------------+-------------------------------+-----------------------------------------------------------+
     | Activate pm function off nodes                 | Yes                           | -                                                         |
     +------------------------------------------------+-------------------------------+-----------------------------------------------------------+
     | Activate NeConfigurationManager disabled nodes | Yes                           | -                                                         |
     +------------------------------------------------+-------------------------------+-----------------------------------------------------------+
     | Activate no tasks sent                         | Yes                           | -                                                         |
     +------------------------------------------------+-------------------------------+-----------------------------------------------------------+
     | Add Nodes pm function off                      | Yes                           | -                                                         |
     +------------------------------------------------+-------------------------------+-----------------------------------------------------------+
     | Add Nodes NeConfigurationManager disabled      | Yes                           | -                                                         |
     +------------------------------------------------+-------------------------------+-----------------------------------------------------------+
     | Add Nodes pm function off                      | No                            | Exception when updating tasks status to Error             |
     +------------------------------------------------+-------------------------------+-----------------------------------------------------------+
     | Add Nodes NeConfigurationManager disabled      | No                            | Exception when updating tasks status to Error             |
     +------------------------------------------------+-------------------------------+-----------------------------------------------------------+
     | Add Nodes no tasks created                     | No                            | -                                                         |
     +------------------------------------------------+-------------------------------+-----------------------------------------------------------+
     | Add Nodes no tasks created                     | Yes                           | Exception when updating tasks status to Error             |
     +------------------------------------------------+-------------------------------+-----------------------------------------------------------+
     */

}
