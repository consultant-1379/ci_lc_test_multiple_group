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

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.subscription.BscRecordingsSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.task.factories.deactivation.BscRecordingsSubscriptionDeactivationTaskRequestFactory
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionDeactivationTaskRequest

/**
 * Created by Eklavya on 9/28/18.
 */
class BscRecordingsSubscriptionDeactivationTaskRequestFactorySpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    private BscRecordingsSubscriptionDeactivationTaskRequestFactory bscRecordingsSubscriptionDeactivationTaskRequestFactory

    @Inject
    private PMICInitiationTrackerCache pmicInitiationTrackerCache

    @Inject
    private NodeDao nodeDao;

    @Inject
    private SubscriptionDao subscriptionDao;

    @Override
    def autoAllocateFrom() {
        def packages = super.autoAllocateFrom()
        packages.addAll(['com.ericsson.oss.pmic.dao', 'com.ericsson.oss.pmic.dto'])
        return packages
    }

    def "Will create tasks for all nodes if each node has USERDEF scanner with subscription name but will not track"() {
        given:
        def nodeMO1 = nodeUtil.builder("GSM02BSC01").neType(NetworkElementType.BSC).build()
        def nodeMO2 = nodeUtil.builder("GSM02BSC02").neType(NetworkElementType.BSC).build()
        def subMo = dps.subscription().type(SubscriptionType.BSCRECORDINGS).name("Test").administrationState(AdministrationState.DEACTIVATING).nodes(nodeMO1, nodeMO2).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as BscRecordingsSubscription
        when:
        def tasks = bscRecordingsSubscriptionDeactivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, false)
        then:
        tasks.size() == 2
        tasks.each {
            assert it.getJobId().contains("subscriptionId=" + sub.getId())
            assert (it.getNodeAddress().contains(nodeMO1.getFdn()) || it.getNodeAddress().contains(nodeMO2.getFdn()))
            assert (it instanceof SubscriptionDeactivationTaskRequest)
        }
        0 * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _, _)
    }

    def "Will create tasks for all nodes if each node has USERDEF scanner with subscription name regardless of state of scanner"() {
        given:
        def nodeMO1 = nodeUtil.builder("GSM02BSC01").neType(NetworkElementType.BSC).build()
        def nodeMO2 = nodeUtil.builder("GSM02BSC02").neType(NetworkElementType.BSC).build()
        def subMo = dps.subscription().type(SubscriptionType.BSCRECORDINGS).name("Test").administrationState(AdministrationState.DEACTIVATING).nodes(nodeMO1, nodeMO2).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as BscRecordingsSubscription
        when:
        def tasks = bscRecordingsSubscriptionDeactivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, true)
        then:
        tasks.size() == 2
        tasks.each {
            assert it.getJobId().contains("subscriptionId=" + sub.getId())
            assert (it.getNodeAddress().contains(nodeMO1.getFdn()) || it.getNodeAddress().contains(nodeMO2.getFdn()))
            assert (it instanceof SubscriptionDeactivationTaskRequest)
        }

    }

    def "will not create any deactivation tasks if nodes do not exist"() {
        given:
        def subMo = dps.subscription().type(SubscriptionType.BSCRECORDINGS).name("Test").administrationState(AdministrationState.DEACTIVATING).build()

        def sub = subscriptionDao.findOneById(subMo.getPoId()) as BscRecordingsSubscription
        when:
        def tasks = bscRecordingsSubscriptionDeactivationTaskRequestFactory.createMediationTaskRequests([], sub, true)
        then:
        tasks.size() == 0
        0 * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _)
    }

}
