/*******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.initiation.notification

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.services.pm.collection.notification.handlers.initiationresponsecache.handlers.InitiationResponseCacheHelper
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache

class InitiationResponseCacheSpec extends SkeletonSpec {

    @ObjectUnderTest
    InitiationResponseCacheHelper objectUnderTest

    @Inject
    private PMICInitiationTrackerCache pmicInitiationTrackerCache

    private String subscriptionId
    private final Map<String, String> nodesToBeActivated = new HashMap<>()
    private final Map<String, String> nodesToBeDeactivated = new HashMap<>()
    private ManagedObject subscriptionMo;

    def setup() {
        subscriptionMo = statisticalSubscriptionBuilder.build()
        subscriptionId = String.valueOf(subscriptionMo.getPoId())

        nodesToBeActivated.put("NetworkElement=0", "RadioNode")
        nodesToBeActivated.put("NetworkElement=1", "RadioNode")
        nodesToBeActivated.put("NetworkElement=2", "RadioNode")
        nodesToBeDeactivated.put("NetworkElement=10", "RadioNode")
        nodesToBeDeactivated.put("NetworkElement=11", "RadioNode")
        nodesToBeDeactivated.put("NetworkElement=12", "RadioNode")

    }

    def "test should increase notification count on receving notification"() {
        given:
        pmicInitiationTrackerCache.startTrackingActivation(subscriptionId, "ACTIVATING", nodesToBeActivated, null)
        when:
        objectUnderTest.processInitiationResponseCacheForActivation(subscriptionId, "NetworkElement=0")
        objectUnderTest.processInitiationResponseCacheForActivation(subscriptionId, "NetworkElement=1")
        then:
        pmicInitiationTrackerCache.getTracker(subscriptionId).getTotalAmountOfReceivedNotifications() == 2
    }

    def "should not do anything when no initiation cache found"() {
        expect:
        !objectUnderTest.processInitiationResponseCacheForActivation("0", "NetworkElement=2")
    }

    def "should update subscription Admin state to ACTIVE and remove from cache when all notifications are received"() {
        given:
        pmicInitiationTrackerCache.startTrackingActivation(subscriptionId, "ACTIVATING", nodesToBeActivated, null)
        when:
        objectUnderTest.processInitiationResponseCacheForActivation(subscriptionId, "NetworkElement=0")
        objectUnderTest.processInitiationResponseCacheForActivation(subscriptionId, "NetworkElement=1")
        objectUnderTest.processInitiationResponseCacheForActivation(subscriptionId, "NetworkElement=2")
        then:
        pmicInitiationTrackerCache.getTracker(subscriptionId) == null
        configurableDps.build().getLiveBucket().findPoById(subscriptionMo.getPoId()).getAttribute("administrationState") == "ACTIVE"
    }

    def "should increase deactivation notification count on receiving notification"() {
        given:
        pmicInitiationTrackerCache.startTrackingDeactivation(subscriptionId, "DEACTIVATING", nodesToBeDeactivated, null)
        when:
        objectUnderTest.processInitiationResponseCacheForDeactivation(subscriptionId, "NetworkElement=10")
        objectUnderTest.processInitiationResponseCacheForDeactivation(subscriptionId, "NetworkElement=11")
        then:
        pmicInitiationTrackerCache.getTracker(subscriptionId).getTotalAmountOfReceivedNotifications() == 2
    }

    def "should update subscription Admin state to INACTIVE and remove from cache when all notifications are received"() {
        given:
        pmicInitiationTrackerCache.startTrackingDeactivation(subscriptionId, "DEACTIVATING", nodesToBeDeactivated, null)
        when:
        objectUnderTest.processInitiationResponseCacheForDeactivation(subscriptionId, "NetworkElement=10")
        objectUnderTest.processInitiationResponseCacheForDeactivation(subscriptionId, "NetworkElement=11")
        objectUnderTest.processInitiationResponseCacheForDeactivation(subscriptionId, "NetworkElement=12")
        then:
        pmicInitiationTrackerCache.getTracker(subscriptionId) == null
        configurableDps.build().getLiveBucket().findPoById(subscriptionMo.getPoId()).getAttribute("administrationState") == "INACTIVE"
    }

    def "should increase updating notification count on receiving notification"() {
        given:
        pmicInitiationTrackerCache.startTrackingActivation(subscriptionId, "UPDATING", nodesToBeActivated, null)
        pmicInitiationTrackerCache.startTrackingDeactivation(subscriptionId, "UPDATING", nodesToBeDeactivated, null)
        when:
        objectUnderTest.processInitiationResponseCacheForActivation(subscriptionId, "NetworkElement=0")
        objectUnderTest.processInitiationResponseCacheForDeactivation(subscriptionId, "NetworkElement=10")
        then:
        pmicInitiationTrackerCache.getTracker(subscriptionId).getTotalAmountOfReceivedNotifications() == 2
    }

    def "should update subscription Admin state to INACTIVE and remove from cache when all activation and deactivation notifications are received"() {
        given:
        configurableDps.build().getLiveBucket().findPoById(subscriptionMo.getPoId()).setAttribute("administrationState", "UPDATING")
        pmicInitiationTrackerCache.startTrackingActivation(subscriptionId, "UPDATING", nodesToBeActivated, null)
        pmicInitiationTrackerCache.startTrackingDeactivation(subscriptionId, "UPDATING", nodesToBeDeactivated, null)
        when:
        objectUnderTest.processInitiationResponseCacheForActivation(subscriptionId, "NetworkElement=0")
        objectUnderTest.processInitiationResponseCacheForActivation(subscriptionId, "NetworkElement=1")
        objectUnderTest.processInitiationResponseCacheForActivation(subscriptionId, "NetworkElement=2")
        and:
        objectUnderTest.processInitiationResponseCacheForDeactivation(subscriptionId, "NetworkElement=10")
        objectUnderTest.processInitiationResponseCacheForDeactivation(subscriptionId, "NetworkElement=11")
        objectUnderTest.processInitiationResponseCacheForDeactivation(subscriptionId, "NetworkElement=12")
        then:
        pmicInitiationTrackerCache.getTracker(subscriptionId) == null
        and: "subscription admin state be updated to INACTIVE because subscription has no more nodes"
        configurableDps.build().getLiveBucket().findPoById(subscriptionMo.getPoId()).getAttribute("administrationState") == "INACTIVE"
    }

    def "should update subscription Admin state to ACTIVE and remove from cache when all activation and deactivation notifications are received"() {
        given:
        subscriptionMo = statisticalSubscriptionBuilder.name("xyz").administrativeState(AdministrationState.UPDATING).addNode(nodeUtil.builder("123").build()).build()
        subscriptionId = subscriptionMo.getPoId() as String
        pmicInitiationTrackerCache.startTrackingActivation(subscriptionId, "UPDATING", nodesToBeActivated, null)
        pmicInitiationTrackerCache.startTrackingDeactivation(subscriptionId, "UPDATING", nodesToBeDeactivated, null)
        when:
        objectUnderTest.processInitiationResponseCacheForActivation(subscriptionId, "NetworkElement=0")
        objectUnderTest.processInitiationResponseCacheForActivation(subscriptionId, "NetworkElement=1")
        objectUnderTest.processInitiationResponseCacheForActivation(subscriptionId, "NetworkElement=2")
        and:
        objectUnderTest.processInitiationResponseCacheForDeactivation(subscriptionId, "NetworkElement=10")
        objectUnderTest.processInitiationResponseCacheForDeactivation(subscriptionId, "NetworkElement=11")
        objectUnderTest.processInitiationResponseCacheForDeactivation(subscriptionId, "NetworkElement=12")
        then:
        pmicInitiationTrackerCache.getTracker(subscriptionId) == null
        and: "subscription admin state be updated to ACTIVE because subscription has more nodes"
        configurableDps.build().getLiveBucket().findPoById(subscriptionMo.getPoId()).getAttribute("administrationState") == "ACTIVE"
    }

    def "no exception will be thrown if initiation tracker does not exist"() {
        when:
        objectUnderTest.processInitiationResponseCacheForActivation(subscriptionId, "NetworkElement=0")
        objectUnderTest.processInitiationResponseCacheForDeactivation(subscriptionId, "NetworkElement=10")
        then:
        noExceptionThrown()
    }
}
