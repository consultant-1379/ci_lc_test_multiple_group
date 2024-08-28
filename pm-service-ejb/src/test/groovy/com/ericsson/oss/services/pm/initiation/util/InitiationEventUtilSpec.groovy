package com.ericsson.oss.services.pm.initiation.util

import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription
import com.ericsson.oss.pmic.dto.subscription.UETraceSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventUtils

class InitiationEventUtilSpec extends SkeletonSpec {

    @com.ericsson.cds.cdi.support.rule.ObjectUnderTest
    InitiationEventUtils initiationEventUtil

    @Override
    def autoAllocateFrom() {
        def packages = super.autoAllocateFrom()
        packages.addAll(['com.ericsson.oss.pmic.dao', 'com.ericsson.oss.pmic.dto'])
        return packages
    }

    def "getNodesForSubscription will return nodes of ResourceSubscription"() {
        given:
        def resSub = new StatisticalSubscription()
        resSub.setType(SubscriptionType.STATISTICAL)
        resSub.setNodes([new Node()])
        expect:
        initiationEventUtil.getNodesForSubscription(resSub).size() == 1
    }

    def "getNodesForSubscription will return nodes of PmJob supported subscription"() {
        given:
        def resSub = new UETraceSubscription()
        resSub.setType(SubscriptionType.UETRACE)
        nodeUtil.builder("1").neType("SGSN-MME").build()
        expect:
        initiationEventUtil.getNodesForSubscription(resSub).size() == 1
    }
}
