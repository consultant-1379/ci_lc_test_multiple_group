/*******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.scheduler

import javax.inject.Inject
import java.text.SimpleDateFormat

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.services.pm.initiation.ejb.SubscriptionOperationExecutionTrackingCacheWrapper
import com.ericsson.oss.services.pm.initiation.notification.events.*
import com.ericsson.oss.services.pm.initiation.schedulers.SubscriptionInitiationManager
import com.ericsson.oss.services.pm.time.TimeGenerator

class SubscriptionInitiationManagerSpec extends SkeletonSpec {

    @ObjectUnderTest
    SubscriptionInitiationManager objectUnderTest

    @ImplementationInstance
    @Activate
    InitiationEvent activationEvent = Mock(ActivationEvent)

    @ImplementationInstance
    @Deactivate
    InitiationEvent deactivationEvent = Mock(DeactivationEvent)

    @ImplementationInstance
    TimeGenerator timeGenerator = Mock(TimeGenerator)

    @Inject
    SubscriptionOperationExecutionTrackingCacheWrapper subscriptionOperationExecutionTrackingCacheWrapper

    @Inject
    NodeDao nodeDao

    static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")

    ManagedObject subscriptionMO1, subscriptionMO2, subscriptionMO3, subscriptionMO4
    ManagedObject nodeMO1, nodeMO2
    List<Node> nodes

    def setup() {
        nodeMO1 = nodeUtil.builder("LTE01ERBS00001").build()
        nodeMO2 = nodeUtil.builder("LTE01ERBS00002").build()
        nodes = nodeDao.findAllByFdn([nodeMO1.getFdn(), nodeMO2.getFdn()])
        subscriptionMO1 = statisticalSubscriptionBuilder.name("sub1").addNode(nodeMO1).addNode(nodeMO2).build()
        subscriptionMO2 = statisticalSubscriptionBuilder.name("sub2").addNode(nodeMO1).addNode(nodeMO2).build()
        subscriptionMO3 = statisticalSubscriptionBuilder.name("sub3").addNode(nodeMO1).addNode(nodeMO2).build()
        subscriptionMO4 = statisticalSubscriptionBuilder.name("sub4").addNode(nodeMO1).addNode(nodeMO2).build()
    }

    def "if cache entry is not older than 5 min, no action will be taken"() {
        given: "entries exist in the cache"
        timeGenerator.currentTimeMillis() >> simpleDateFormat.parse("22/09/2000 12:00:00").getTime()
        subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionMO1.getPoId(), SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_ACTIVATE_SUBSCRIPTION)
        subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionMO2.getPoId(), SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_DEACTIVATE_SUBSCRIPTION)
        subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionMO3.getPoId(), SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_ACTIVATE_NODES, nodes)
        subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionMO4.getPoId(), SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_DEACTIVATE_NODES, nodes)
        when: "timer executes 2 minutes after entries were added to cache"
        timeGenerator.currentTimeMillis() >> simpleDateFormat.parse("22/09/2000 12:02:00").getTime()
        objectUnderTest.execute()
        then: "none of the entries will be eligible for execution"
        0 * activationEvent.execute(_ as List, _ as Subscription)
        0 * activationEvent.execute(_ as Long)
        0 * deactivationEvent.execute(_ as List, _ as Subscription)
        0 * deactivationEvent.execute(_ as Long)
    }

    def "if cache entry is older than 5 min, action will be executed"() {
        given: "entries exist in the cache"
        timeGenerator.currentTimeMillis() >> simpleDateFormat.parse("22/09/2000 12:00:00").getTime()
        subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionMO1.getPoId(), SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_ACTIVATE_SUBSCRIPTION)
        subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionMO2.getPoId(), SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_DEACTIVATE_SUBSCRIPTION)
        subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionMO3.getPoId(), SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_ACTIVATE_NODES, nodes)
        subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionMO4.getPoId(), SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_DEACTIVATE_NODES, nodes)
        when: "timer executes 10 minutes after entries were added to cache"
        timeGenerator.currentTimeMillis() >> simpleDateFormat.parse("22/09/2000 12:10:00").getTime()
        objectUnderTest.execute()
        then: "all of the entries will be eligible for execution"
        1 * activationEvent.execute(nodes, _ as Subscription)
        1 * activationEvent.execute(subscriptionMO1.getPoId())
        1 * deactivationEvent.execute(nodes, _ as Subscription)
        1 * deactivationEvent.execute(subscriptionMO2.getPoId())
    }

    def "cache entry will be removed if it is added for the 4th time"() {
        when:
        subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionMO1.getPoId(), SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_ACTIVATE_SUBSCRIPTION)
        subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionMO1.getPoId(), SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_ACTIVATE_SUBSCRIPTION)
        subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionMO1.getPoId(), SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_ACTIVATE_SUBSCRIPTION)
        subscriptionOperationExecutionTrackingCacheWrapper.addEntry(subscriptionMO1.getPoId(), SubscriptionOperationExecutionTrackingCacheWrapper.OPERATION_ACTIVATE_SUBSCRIPTION)
        then:
        subscriptionOperationExecutionTrackingCacheWrapper.getAllEntries().isEmpty()
    }
}
