package com.ericsson.oss.services.pm.initiation.notification

import spock.lang.Unroll

import javax.inject.Inject
import java.util.concurrent.TimeUnit

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.ScheduleInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.pm.common.utils.KeyGenerator
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener
import com.ericsson.oss.services.pm.initiation.ejb.ResourceSubscriptionNodeInitiation
import com.ericsson.oss.services.pm.initiation.notification.events.Activate
import com.ericsson.oss.services.pm.initiation.notification.events.Deactivate
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEvent
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionDataCacheWrapper
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionManager

class ResourceSubscriptionNodeInitiationSpec extends SkeletonSpec {

    private static final String LTE01_ERBS00001 = "LTE01ERBS00001"
    private static final String LTE01_ERBS00002 = "LTE01ERBS00002"
    private static final String LTE01_ERBS00003 = "LTE01ERBS00003"
    private static final String NE_ERBS_1 = "NetworkElement=LTE01ERBS00001"
    private static final String NE_ERBS_2 = "NetworkElement=LTE01ERBS00002"
    private static final String NE_ERBS_3 = "NetworkElement=LTE01ERBS00003"

    @ObjectUnderTest
    ResourceSubscriptionNodeInitiation objectUnderTest

    @Inject
    SubscriptionManager manager

    @Inject
    SubscriptionDataCacheWrapper subscriptionDataCacheWrapper

    @ImplementationInstance
    @Activate
    private InitiationEvent activationEvent = Mock(InitiationEvent)

    @ImplementationInstance
    @Deactivate
    private InitiationEvent deactivationEvent = Mock(InitiationEvent)

    @ImplementationInstance
    private ConfigurationChangeListener configurationChangeListener = Mock()

    @Inject
    NodeDao nodeDao

    @Inject
    SubscriptionDao subscriptionDao

    def "updating an active subscription with new node will remove subscription manager cache entry and send activation event"() {
        given:
            def node1MO = dps.node().name(LTE01_ERBS00001).build()
            def node2MO = dps.node().name(LTE01_ERBS00002).build()
            def subMO = dps.subscription().
                type(SubscriptionType.STATISTICAL).
                name("Test").
                operationalState(OperationalState.RUNNING).
                taskStatus(TaskStatus.OK).
                administrationState(AdministrationState.ACTIVE).
                nodes(node1MO).build()
            List<Node> nodes = nodeDao.findAllByFdn([node1MO.getFdn(), node2MO.getFdn()])
            manager.getSubscriptionWrapper(subMO.getName(), SubscriptionType.STATISTICAL)
            String key = KeyGenerator.generateKey(subMO.getName(), SubscriptionType.STATISTICAL.name());
            assert subscriptionDataCacheWrapper.get(key) != null
            def subscription = subscriptionDao.findOneById(subMO.getPoId(), true)
        when:
            objectUnderTest.activateOrDeactivateNodesOnActiveSubscription(subscription as ResourceSubscription, nodes, [])
        then:
            1 * activationEvent.execute(nodes, subscription)
    }

    def "removing node from an active subscription will remove subscription manager cache entry and send deactivation events"() {
        given:
           def node1MO = dps.node().name(LTE01_ERBS00001).pmFunction(true).pmEnabled(true).build()
            def subscriptionMO = dps.subscription().
                type(SubscriptionType.STATISTICAL).
                name("Test").
                operationalState(OperationalState.RUNNING).
                taskStatus(TaskStatus.OK).
                administrationState(AdministrationState.ACTIVE).
                nodes(node1MO).build()
            def node2MO = dps.node().name(LTE01_ERBS00002).pmFunction(false).pmEnabled(false).build()
            def node3MO = dps.node().name(LTE01_ERBS00003).pmFunction(true).pmEnabled(true).build()
            configurationChangeListener.getPmMigrationEnabled() >> migration
            List<Node> nodes = nodeDao.findAllByFdn([node1MO.getFdn(), node2MO.getFdn(), node3MO.getFdn()], true)
            List<Node> expectedNodes = nodeDao.findAllByFdn(expectedDeactivationEvents, true)
            manager.getSubscriptionWrapper(subscriptionMO.getName(), SubscriptionType.STATISTICAL)
            assert subscriptionDataCacheWrapper.get(KeyGenerator.generateKey(subscriptionMO.getName(), SubscriptionType.STATISTICAL.name())) != null
            def subscription = subscriptionDao.findOneById(subscriptionMO.getPoId(), true)
        when:
            objectUnderTest.activateOrDeactivateNodesOnActiveSubscription(subscription as ResourceSubscription, [], nodes)

        then:
            subscriptionDataCacheWrapper.get(KeyGenerator.generateKey(subscriptionMO.getName(), SubscriptionType.STATISTICAL.name())) != null
            1 * deactivationEvent.execute(expectedNodes, subscription)

        where:
            migration | expectedDeactivationEvents
            false     | [NE_ERBS_1, NE_ERBS_2, NE_ERBS_3]
            true      | [NE_ERBS_1, NE_ERBS_3] // LTE01ERBS00002 pmFunction is false
    }

    @Unroll
    def "updating a subscription that is not ACTIVE will do nothing"() {
        given:
            def nodeMO = dps.node().name("2").build()
            def subMO = dps.subscription().
                type(SubscriptionType.STATISTICAL).
                administrationState(adminState as AdministrationState).
                nodes(nodeMO).
                build()
            StatisticalSubscription subscription = subscriptionDao.findOneById(subMO.getPoId(), true) as StatisticalSubscription
        when:
            objectUnderTest.activateOrDeactivateNodesOnActiveSubscription(subscription, subscription.getNodes(), [])
        then:
            0 * deactivationEvent._
            0 * activationEvent._
        where:
            adminState << AdministrationState.values().minus([AdministrationState.ACTIVE])
    }

    def "updating a subscription that is NotRunning will do nothing"() {
        given:
            def nodeMO = dps.node().name("2").build()
            def subMO = dps.subscription().
                type(SubscriptionType.STATISTICAL).
                administrationState(AdministrationState.ACTIVE).
                operationalState(OperationalState.NA).
                nodes(nodeMO).
                scheduleInfo(new ScheduleInfo(null, new Date(new Date().getTime() - TimeUnit.HOURS.toMillis(2)))).
                deactivationTime(new Date()).
                build()
            StatisticalSubscription subscription = subscriptionDao.findOneById(subMO.getPoId(), true) as StatisticalSubscription
        when:
            objectUnderTest.activateOrDeactivateNodesOnActiveSubscription(subscription, subscription.getNodes(), [])
        then:
            0 * deactivationEvent._
            0 * activationEvent._
    }

}
