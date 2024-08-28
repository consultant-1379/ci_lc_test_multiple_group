package com.ericsson.oss.services.pm.initiation.notification

import static com.ericsson.oss.pmic.cdi.test.util.Constants.DELAY_MS

import spock.lang.Unroll

import javax.ejb.TimerConfig
import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.DummyTimer
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.services.pm.initiation.schedulers.NodeRemovalHandler

/**
 * This class will test handling of Node Removal
 *
 * Created by eandpoz on 7/11/17.
 */
class NodeRemovalHandlerSpec extends SkeletonSpec {

    static final String TIMER_NAME = "Node_Removal_Timer"

    @ObjectUnderTest
    NodeRemovalHandler nodeRemovalHandlerSpec

    @Inject
    DpsPmFunctionDeleteNotificationListener listener

    @ImplementationInstance
    TimerService timerService = Mock(TimerService)

    def "On node removal numberOfNodes attribute is correctly updated for Resource Subscription"() {
        given: 'Add SGSN-MME nodes in DPS'
        def nodes = [
                nodeUtil.builder("ERBS_1").neType("ERBS").pmEnabled(true).build(),
                nodeUtil.builder("ERBS_2").neType("ERBS").pmEnabled(true).build(),
                nodeUtil.builder("ERBS_3").neType("ERBS").pmEnabled(true).build()
        ]
        and: 'add nodes to Stats and CellTrace Subscriptions'
        def sub = statisticalSubscriptionBuilder.name("Stats").addNodes(nodes).build()
        and:
        timerService.getTimers() >> []
        expect:
        liveBucket().findPoById(sub.getPoId()).getAttribute("numberOfNodes") == 3
        when: 'removing a node'
        def subNodes = sub.getAssociations().get('nodes');
        subNodes.remove(nodes[2])
        then: 'numberOfNodes unsynced with actual nodes size'
        sub.getAssociations().get('nodes').size() != liveBucket().findPoById(sub.getPoId()).getAttribute("numberOfNodes")
        when: 'receive notification'
        nodeRemovalHandlerSpec.updateNumberOfNodesInSubscriptions()
        then:
        liveBucket().findPoById(sub.getPoId()).getAttribute("numberOfNodes") == 2
        and:
        sub.getAssociations().get('nodes').size() == liveBucket().findPoById(sub.getPoId()).getAttribute("numberOfNodes")

    }

    @Unroll
    def "On setTimer timerService should be invoked #numInvoke times"() {
        given: ''
        timerService.getTimers() >> timers
        when: 'setTimer is invoked'
        nodeRemovalHandlerSpec.setTimer()
        then:
        numInvoke * timerService.createSingleActionTimer(_, _ as TimerConfig);
        where:
        timers                                                         | numInvoke
        [new DummyTimer(DELAY_MS, new TimerConfig(TIMER_NAME, false))] | 0
        []                                                             | 1
    }
}
