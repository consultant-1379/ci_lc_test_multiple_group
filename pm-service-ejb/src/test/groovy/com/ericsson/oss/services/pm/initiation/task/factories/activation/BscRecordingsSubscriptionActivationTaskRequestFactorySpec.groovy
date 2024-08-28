/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.task.factories.activation

import static com.ericsson.oss.pmic.cdi.test.util.Constants.ACTIVATING
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.BscRecordingsSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionActivationTaskRequest

/**
 * Created by Eklavya on 9/28/18.
 */
class BscRecordingsSubscriptionActivationTaskRequestFactorySpec extends PmServiceEjbSkeletonSpec {
    @ObjectUnderTest
    private BscRecordingsSubscriptionActivationTaskRequestFactory bscRecordingsSubscriptionActivationTaskRequestFactory

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

    def "createActivationTasks should return size 0 tasks when no nodes and should not add to cache"() {
        given:
        def subMo = dps.subscription().type(SubscriptionType.BSCRECORDINGS).name("subNameNe").administrationState(AdministrationState.ACTIVATING).build()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as BscRecordingsSubscription
        when:
        List<MediationTaskRequest> tasks = bscRecordingsSubscriptionActivationTaskRequestFactory.createMediationTaskRequests([], sub, true)
        then:
        tasks == []
        pmicInitiationTrackerCache.getAllTrackers().size() == 0
    }

    def "will create tasks and track USERDEF subscription"() {
        given:
        def nodeMO1 = nodeUtil.builder("GSM02BSC01").neType(NetworkElementType.BSC).build()
        def nodeMO2 = nodeUtil.builder("GSM02BSC02").neType(NetworkElementType.BSC).build()
        def subMo = dps.subscription().type(SubscriptionType.BSCRECORDINGS).name("subNameNe").administrationState(AdministrationState.ACTIVATING).nodeListIdentity(2).nodes(nodeMO1, nodeMO2).build()
        scannerUtil.builder("USERDEF-subNameNe.Cont.Y.BSCRECORDINGS", "GSM02BSC01").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.INACTIVE).build()
        scannerUtil.builder("USERDEF-subNameNe.Cont.Y.BSCRECORDINGS", "GSM02BSC02").node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.INACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as BscRecordingsSubscription
        when:
        List<MediationTaskRequest> tasks = bscRecordingsSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, true)
        then:
        ACTIVATING == (String) subMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
        and: "task count will be 2"
        tasks.size() == 2
        pmicInitiationTrackerCache.getTracker(sub.getIdAsString()).getNodesFdnsToBeActivated() == [nodeMO1.getFdn(), nodeMO2.getFdn()] as Set
        tasks.each { assert it instanceof SubscriptionActivationTaskRequest }
    }

}


