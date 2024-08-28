/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.scanner.polling

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.common.utils.KeyGenerator
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionDataCacheWrapper
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionManager
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionWrapper

class SubscriptionManagerSpec extends SkeletonSpec {

    @ObjectUnderTest
    SubscriptionManager subscriptionManager

    @Inject
    SubscriptionDao subscriptionDao

    @Inject
    SubscriptionDataCacheWrapper subscriptionDataCacheWrapper

    @Override
    def autoAllocateFrom() {
        def packages = super.autoAllocateFrom()
        packages.addAll(['com.ericsson.oss.pmic.dao', 'com.ericsson.oss.pmic.dto'])
        return packages
    }

    def "getSubscription will extract subscription from database if cache doesn't contain such entry"() {
        given:
        def node = nodeUtil.builder("erbsNode").build()
        def sub = statisticalSubscriptionBuilder.name("Stats").addNode(node).build()

        def subscription = subscriptionDao.findOneById(sub.getPoId(), true)
        def key = KeyGenerator.generateKey("Stats", "STATISTICAL");
        when:
        def result = subscriptionManager.getSubscriptionWrapper("Stats", SubscriptionType.STATISTICAL)
        then:
        result.getSubscription().getId() == subscription.getId()
        result.getSubscription().getType() == subscription.getType()
        result.getSubscription().getName() == subscription.getName()
        result.getSubscription().getDescription() == subscription.getDescription()
        result.getSubscription().getAdministrationState() == subscription.getAdministrationState()
        result.getSubscription().getTaskStatus() == subscription.getTaskStatus()
        result.getSubscription().getPersistenceTime() == subscription.getPersistenceTime()
        result.getSubscription().getOwner() == subscription.getOwner()
        result.getSubscription().getUserType() == subscription.getUserType()
        result.getSubscription().getActivationTime() == subscription.getActivationTime()
        result.getSubscription().getUserActivationDateTime() == subscription.getUserActivationDateTime()
        result.getSubscription().getRop() == subscription.getRop()
        subscriptionDataCacheWrapper.get(key).getData().get(key) != null
    }

    def "subscription will be extracted from cache is exists"() {
        given:
        def node = nodeUtil.builder("erbsNode").build()
        def sub = statisticalSubscriptionBuilder.name("Stats").addNode(node).build()

        def subscription = subscriptionDao.findOneById(sub.getPoId(), true)
        def key = KeyGenerator.generateKey("Stats", "STATISTICAL")
        subscriptionManager.getSubscriptionWrapper("Stats", SubscriptionType.STATISTICAL)
        and: "subscription is deleted from dps"
        subscriptionDao.delete(subscription)
        when:
        def result = subscriptionManager.getSubscriptionWrapper("Stats", SubscriptionType.STATISTICAL)
        then:
        result.getSubscription().getId() == subscription.getId()
        result.getSubscription().getType() == subscription.getType()
        result.getSubscription().getName() == subscription.getName()
        result.getSubscription().getDescription() == subscription.getDescription()
        result.getSubscription().getAdministrationState() == subscription.getAdministrationState()
        result.getSubscription().getTaskStatus() == subscription.getTaskStatus()
        result.getSubscription().getPersistenceTime() == subscription.getPersistenceTime()
        result.getSubscription().getOwner() == subscription.getOwner()
        result.getSubscription().getUserType() == subscription.getUserType()
        result.getSubscription().getActivationTime() == subscription.getActivationTime()
        result.getSubscription().getUserActivationDateTime() == subscription.getUserActivationDateTime()
        result.getSubscription().getRop() == subscription.getRop()

        with(((SubscriptionWrapper) subscriptionDataCacheWrapper.get(key).getData().get(key)).getSubscription(), {
            it.getId() == subscription.getId()
            it.getType() == subscription.getType()
            it.getName() == subscription.getName()
            it.getDescription() == subscription.getDescription()
            it.getAdministrationState() == subscription.getAdministrationState()
            it.getTaskStatus() == subscription.getTaskStatus()
            it.getPersistenceTime() == subscription.getPersistenceTime()
            it.getOwner() == subscription.getOwner()
            it.getUserType() == subscription.getUserType()
            it.getActivationTime() == subscription.getActivationTime()
            it.getUserActivationDateTime() == subscription.getUserActivationDateTime()
        })
        ((SubscriptionWrapper) subscriptionDataCacheWrapper.get(key).getData().get(key)).getAllNodeFdns() == ["NetworkElement=erbsNode"] as Set
    }
}
