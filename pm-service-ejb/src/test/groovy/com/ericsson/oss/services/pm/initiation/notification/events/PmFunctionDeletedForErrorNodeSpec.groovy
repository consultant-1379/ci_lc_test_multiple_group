/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2016
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */
package com.ericsson.oss.services.pm.initiation.notification.events

import static com.ericsson.oss.pmic.cdi.test.util.Constants.*
import static com.ericsson.oss.pmic.cdi.test.util.constant.PmFunctionConstants.*

import spock.lang.Unroll

import javax.ejb.Timer
import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectDeletedEvent
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.PmBaseSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.MoinstanceSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.StatisticalSubscriptionBuilder
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.impl.counters.PmCountersLifeCycleResolverImpl
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilityReaderImpl
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.collection.cache.PmFunctionOffErrorNodeCache
import com.ericsson.oss.services.pm.collection.cache.StartupRecoveryMonitorLocal
import com.ericsson.oss.services.pm.initiation.ejb.CounterConflictServiceImpl
import com.ericsson.oss.services.pm.initiation.notification.DpsPmFunctionDeleteNotificationListener
import com.ericsson.oss.services.pm.initiation.rest.response.ConflictingCounterGroup
import com.ericsson.oss.services.pm.initiation.rest.response.ConflictingNodeCounterInfo

class PmFunctionDeletedForErrorNodeSpec extends PmBaseSpec {

    private static final long subscriptionId = 1L;

    @ObjectUnderTest
    private DpsPmFunctionDeleteNotificationListener objectUnderTest;

    @Inject
    private PmFunctionOffErrorNodeCache errorNodeCache;

    @Inject
    private PmFunctionEnabledWrapper pmFunctionCache;

    @ImplementationInstance
    TimerService timerService = Mock(TimerService)

    @ImplementationInstance
    Timer timer = Mock(Timer)

    @Inject
    CounterConflictServiceImpl counterConflictCacheService

    @Inject
    SubscriptionDao subscriptionDao

    @Inject
    NodeDao nodeDao

    @ImplementationInstance
    StartupRecoveryMonitorLocal startupRecoveryMonitor = mock(StartupRecoveryMonitorLocal)

    @ImplementationClasses
    def classes = [PmCapabilityReaderImpl.class, PmCountersLifeCycleResolverImpl.class]

    def setup() {
        startupRecoveryMonitor.isStartupRecoveryDone() >> true
    }

    List<CounterInfo> counters = [
            new CounterInfo("pmLicDlCapActual", "BbProcessingResource"),
            new CounterInfo("pmLicDlCapDistr", "BbProcessingResource"),
            new CounterInfo("pmLicDlPrbCapActual", "BbProcessingResource"),
            new CounterInfo("pmAdmNrRrcUnknownArpRatio", "AdmissionControl")
    ]

    def "When the pmFunction MO is deleted for a node that is in the subscription error cache, the subcription is validated"() {
        given: "One node added as an error entry to the active task cache"
        errorNodeCache.addNodeWithPmFunctionOff(NETWORK_ELEMENT_1, subscriptionId);
        timerService.getTimers() >> []
        when: "A pmFunction delete notification is received"
        final DpsObjectDeletedEvent deletedEvent = new DpsObjectDeletedEvent(PM_FUNCTION_NAME_SPACE, PM_FUNCTION_TYPE, "2.1.0", 0L,
                NETWORK_ELEMENT_1 + "," + PM_FUNCTION_MO_NAME, "theBucketName", false, null);
        objectUnderTest.onEvent(deletedEvent);
        then: "The error entry is removed from the cache"
        0 == errorNodeCache.getErrorEntry(NETWORK_ELEMENT_1).size();
    }

    def "When the pmFunction MO is deleted for a node that is not in the subscription error cache, the subcription is not validated"() {
        given: "A different node added as an error entry to the active task cache"
        errorNodeCache.addNodeWithPmFunctionOff(NETWORK_ELEMENT_2, subscriptionId);
        timerService.getTimers() >> []
        when: "A pmFunction delete notification is received for a node that is not in the cache"
        final DpsObjectDeletedEvent deletedEvent = new DpsObjectDeletedEvent(PM_FUNCTION_NAME_SPACE, PM_FUNCTION_TYPE, "2.1.0", 0L,
                NETWORK_ELEMENT_1 + "," + PM_FUNCTION_MO_NAME, "theBucketName", false, null);
        objectUnderTest.onEvent(deletedEvent);
        then: "The error entry for the other network element is still in the cache"
        1 == errorNodeCache.getErrorEntry(NETWORK_ELEMENT_2).size();
    }

    def "When the pmFunction MO is deleted, remove nodeFdn from Cache"() {
        given: "One node added as an error entry to the active task cache"
        errorNodeCache.addNodeWithPmFunctionOff(NETWORK_ELEMENT_1, subscriptionId);
        timerService.getTimers() >> [timer];
        timer.getInfo() >> subscriptionId;
        when: "A pmFunction delete notification is received"
        final DpsObjectDeletedEvent deletedEvent = new DpsObjectDeletedEvent(PM_FUNCTION_NAME_SPACE, PM_FUNCTION_TYPE, "2.1.0", 0L,
                NETWORK_ELEMENT_1 + "," + PM_FUNCTION_MO_NAME, "theBucketName", false, null);
        objectUnderTest.onEvent(deletedEvent);
        then: "The error entry is removed from the cache"
        0 == errorNodeCache.getErrorEntry(NETWORK_ELEMENT_1).size();
    }

    def "When the pmFunction MO is deleted and the timer is created for a different subscription, it is created for this subscription"() {
        given: "One node added as an error entry to the active task cache and an active timer for a different subscription"
        errorNodeCache.addNodeWithPmFunctionOff(NETWORK_ELEMENT_1, subscriptionId);
        timerService.getTimers() >> [timer];
        timer.getInfo() >> Long.toString(nonExistentSubId);
        when: "A pmFunction delete notification is received"
        final DpsObjectDeletedEvent deletedEvent = new DpsObjectDeletedEvent(PM_FUNCTION_NAME_SPACE, PM_FUNCTION_TYPE, "2.1.0", 0L,
                NETWORK_ELEMENT_1 + "," + PM_FUNCTION_MO_NAME, "theBucketName", false, null);
        objectUnderTest.onEvent(deletedEvent);
        then: "The error entry is removed from the cache"
        0 == errorNodeCache.getErrorEntry(NETWORK_ELEMENT_1).size();
    }

    @Unroll
    def "When the pmFunction MO is deleted for a node that is in the counter conflict cache for #subType.getSimpleName() subscription, the cache is updated"() {
        given: "entry exists in the subscriptionCountersCache and subscriptionNodesCache"
        ManagedObject nodeMO1 = nodeUtil.builder(NODE_NAME_1).neType(NetworkElementType.ERBS).ossModelIdentity("18.Q2-J.1.280").build()
        ManagedObject nodeMO2 = nodeUtil.builder(NODE_NAME_2).neType(NetworkElementType.ERBS).ossModelIdentity("18.Q2-J.1.280").pmEnabled(false).build()
        ManagedObject nodeMO3 = nodeUtil.builder(NODE_NAME_3).neType(NetworkElementType.ERBS).ossModelIdentity("18.Q2-J.1.280").build()

        def subMo = builder.newInstance(dpsUtils).name("Test").administrativeState(AdministrationState.ACTIVE).nodes(nodeMO1, nodeMO2).counters(counters as CounterInfo[]).build()
        def subscription = subscriptionDao.findOneById(subMo.getPoId(), true)
        counterConflictCacheService.addNodesAndCounters([
                nodeMO1.getFdn(),
                nodeMO2.getFdn(),
                nodeMO3.getFdn()] as Set, counters, "activeSub")

        timerService.getTimers() >> []
        when: "A pmFunction delete notification is received"
        final DpsObjectDeletedEvent deletedEvent = new DpsObjectDeletedEvent(PM_FUNCTION_NAME_SPACE, PM_FUNCTION_TYPE, "2.1.0", 0L,
                nodeMO1.getFdn() + "," + PM_FUNCTION_MO_NAME, "theBucketName", false, null);
        objectUnderTest.onEvent(deletedEvent);
        then: "subscription nodes cache is updated but subscription counters cache remains unmodified"
        ConflictingNodeCounterInfo result = counterConflictCacheService.getConflictingCountersInSubscription(subscription as StatisticalSubscription)
        result.getNodes() == [nodeMO2.getFdn()] as Set
        result.getCounterEventInfo() containsAll([
                new ConflictingCounterGroup("BbProcessingResource", [
                        "pmLicDlCapActual",
                        "pmLicDlCapDistr",
                        "pmLicDlPrbCapActual"] as Set),
                new ConflictingCounterGroup("AdmissionControl", ["pmAdmNrRrcUnknownArpRatio"] as Set)
        ])
        where:
        subType << [StatisticalSubscription, MoinstanceSubscription]
        builder << [StatisticalSubscriptionBuilder, MoinstanceSubscriptionBuilder]
    }

    @Unroll
    def "When node is deleted from ENM and this is the last node in the counter conflict cache, #subType.getSimpleName() will not have conflicts"() {
        given: "entry exists in the subscriptionCountersCache and subscriptionNodesCache"
        ManagedObject nodeMO1 = nodeUtil.builder(NODE_NAME_1).build()
        ManagedObject nodeMO2 = nodeUtil.builder(NODE_NAME_2).pmEnabled(false).build()
        ManagedObject nodeMO3 = nodeUtil.builder(NODE_NAME_3).build()
        timerService.getTimers() >> []

        def subMo = builder.newInstance(dpsUtils).name("Test").administrativeState(AdministrationState.ACTIVE).nodes(nodeMO1, nodeMO2, nodeMO3).counters(counters as CounterInfo[]).build()
        def subscription = subscriptionDao.findOneById(subMo.getPoId(), true)

        and: "existing entry has one commong node and all common counters"
        counterConflictCacheService.addNodesAndCounters([nodeMO1.getFdn()] as Set, counters, "123")

        when: "The common node is removed from ENM"
        final DpsObjectDeletedEvent deletedEvent = new DpsObjectDeletedEvent(PM_FUNCTION_NAME_SPACE, PM_FUNCTION_TYPE, "2.1.0", 0L,
                nodeMO1.getFdn() + "," + PM_FUNCTION_MO_NAME, "theBucketName", false, null);
        objectUnderTest.onEvent(deletedEvent);
        then: "counter conflcit entries are removed"
        ConflictingNodeCounterInfo result = counterConflictCacheService.getConflictingCountersInSubscription(subscription)
        result.getNodes().isEmpty()
        result.getCounterEventInfo().isEmpty()
        where:
        subType << [StatisticalSubscription, MoinstanceSubscription]
        builder << [StatisticalSubscriptionBuilder, MoinstanceSubscriptionBuilder]
    }

    def "When the pmFunction MO is deleted for a node that is in the pm function cache, the entry is removed"() {
        given: "Some nodes added to pmFunction cache"
        pmFunctionCache.addEntry(NETWORK_ELEMENT_1, true);
        pmFunctionCache.addEntry(NETWORK_ELEMENT_2, true);
        pmFunctionCache.addEntry(NETWORK_ELEMENT_3, true);
        timerService.getTimers() >> []
        when: "A pmFunction delete notification is received"
        final DpsObjectDeletedEvent deletedEvent = new DpsObjectDeletedEvent(PM_FUNCTION_NAME_SPACE, PM_FUNCTION_TYPE, "2.1.0", 0L,
                NETWORK_ELEMENT_1 + "," + PM_FUNCTION_MO_NAME, "theBucketName", false, null);
        objectUnderTest.onEvent(deletedEvent);
        then: "The cache doesn't contain NETWORK_ELEMENT_1"
        false == pmFunctionCache.containsFdn(NETWORK_ELEMENT_1);
    }
}