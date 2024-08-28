/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.task.factories.activation

import com.ericsson.oss.pmic.dto.subscription.enums.UeType

import static com.ericsson.oss.pmic.cdi.test.util.Constants.ACTIVATING
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.RttSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionActivationTaskRequest

class RttSubscriptionActivationTaskRequestFactorySpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    private RttSubscriptionActivationTaskRequestFactory rttSubscriptionActivationTaskRequestFactory

    @Inject
    private PMICInitiationTrackerCache pmicInitiationTrackerCache

    @Inject
    private NodeDao nodeDao

    @Inject
    private SubscriptionDao subscriptionDao

    @Override
    def autoAllocateFrom() {
        def packages = super.autoAllocateFrom()
        packages.addAll(['com.ericsson.oss.pmic.dao', 'com.ericsson.oss.pmic.dto'])
        return packages
    }

    def events = [new EventInfo("ALL", "ALL")]


    def "createActivationTasks should return size 0 tasks when no nodes and should not add to cache"() {
        given:
        def subMo = dps.subscription().type(SubscriptionType.RTT).name("subNameNe").administrationState(AdministrationState.ACTIVATING).build()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as RttSubscription
        when:
        List<MediationTaskRequest> tasks = rttSubscriptionActivationTaskRequestFactory.createMediationTaskRequests([], sub, true)
        then:
        tasks == []
        pmicInitiationTrackerCache.getAllTrackers().size() == 0
    }

    def "will create tasks when subscription has UeInfo and track USERDEF subscription"() {
        given:
        def nodeMO1 = nodeUtil.builder("GSM02BSC01").neType(NetworkElementType.BSC).build()
        def nodeMO2 = nodeUtil.builder("GSM02BSC02").neType(NetworkElementType.BSC).build()
        def ueInfo = [new UeInfo(UeType.IMSI,"173021714253674"),new UeInfo(UeType.IMEI,"173021714253674")]
        def subMo = dps.subscription().type(SubscriptionType.RTT).name("subNameNe").administrationState(AdministrationState.ACTIVATING).nodeListIdentity(2).nodes(nodeMO1, nodeMO2).build()
        scannerUtil.builder("USERDEF-subNameNe.RTT", "GSM02BSC01").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.INACTIVE).build()
        scannerUtil.builder("USERDEF-subNameNe.RTT", "GSM02BSC02").node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.INACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as RttSubscription
        sub.setEvents(events)
        sub.setUeInfoList(ueInfo)

        when:
        List<MediationTaskRequest> tasks = rttSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, true)
        then:
        ACTIVATING == (String) subMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
        and: "task count will be 2"
        tasks.size() == 2
        pmicInitiationTrackerCache.getTracker(sub.getIdAsString()).getNodesFdnsToBeActivated() == [nodeMO1.getFdn(), nodeMO2.getFdn()] as Set
        tasks.each { assert it instanceof SubscriptionActivationTaskRequest }
    }

    def "createActivationTasks should return size 0 tasks when UeInfo is null or empty and should not add to cache"() {
        given:
        def nodeMO1 = nodeUtil.builder("GSM02BSC01").neType(NetworkElementType.BSC).build()
        def nodeMO2 = nodeUtil.builder("GSM02BSC02").neType(NetworkElementType.BSC).build()
        def ueInfo = null
        def subMo = dps.subscription().type(SubscriptionType.RTT).name("subNameNe").administrationState(AdministrationState.ACTIVATING).nodeListIdentity(2).nodes(nodeMO1, nodeMO2).build()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as RttSubscription
        sub.setEvents(events)
        sub.setUeInfoList(ueInfo)

        when:
        List<MediationTaskRequest> tasks = rttSubscriptionActivationTaskRequestFactory.createMediationTaskRequests([], sub, true)
        then:
        tasks == []
        pmicInitiationTrackerCache.getAllTrackers().size() == 0
    }

}
