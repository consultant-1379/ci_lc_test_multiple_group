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

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.oss.pmic.api.modelservice.PmCapabilitiesLookupLocal
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus

import static com.ericsson.oss.pmic.cdi.test.util.Constants.ACTIVATING
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.MTR_SUBSCRIPTION_ATTRIBUTES
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE

import org.mockito.Mockito
import spock.lang.Unroll

import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.subscription.MtrSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionActivationTaskRequest

/**
 * Created by Eklavya on 11/29/18.
 */
class MtrSubscriptionActivationTaskRequestFactorySpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    private MtrSubscriptionActivationTaskRequestFactory mtrSubscriptionActivationTaskRequestFactory
    @Inject
    private PMICInitiationTrackerCache pmicInitiationTrackerCache
    @Inject
    private NodeDao nodeDao
    @Inject
    private SubscriptionDao subscriptionDao
    @ImplementationInstance
    TimerService timerService = Mock(TimerService)
    @ImplementationInstance
    TimeGenerator timer = Mockito.mock(TimeGenerator)
    @MockedImplementation
    PmCapabilitiesLookupLocal pmCapabilitiesLookupLocal

    @Override
    def autoAllocateFrom() {
        def packages = super.autoAllocateFrom()
        packages.addAll(['com.ericsson.oss.pmic.dao', 'com.ericsson.oss.pmic.dto'])
        return packages
    }

    def setup() {
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
        pmCapabilitiesLookupLocal.getDefaultCapabilityValue(MTR_SUBSCRIPTION_ATTRIBUTES,
                'multipleNeTypesList') >> ['BSC']
    }

    def "createActivationTasks should return size 0 tasks when no nodes and should not add to cache"() {
        given:
        def subMo = dps.subscription().type(SubscriptionType.MTR).name("subNameNe").administrationState(AdministrationState.ACTIVATING).build()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as MtrSubscription
        when:
        List<MediationTaskRequest> tasks = mtrSubscriptionActivationTaskRequestFactory.createMediationTaskRequests([], sub, true)
        then:
        tasks == []
        pmicInitiationTrackerCache.getAllTrackers().size() == 0
    }

    @Unroll
    def "will create tasks request for attached Nodes #activationOnAttachedNode"() {
        given:
        def nodeMO1 = nodeUtil.builder("MSC-BC-IS-18A-V202").neType("MSC-BC-IS").build()
        Map<String, String> bscNodeAttribute = new HashMap<>()
        bscNodeAttribute.put("connectedMsc", "NetworkElement=MSC-BC-IS-18A-V202")
        def subNodeMO1 = nodeUtil.builder("NetworkElement=BSC").neType("BSC").attributes(bscNodeAttribute).build()

        def subMo = dps.subscription().type(SubscriptionType.MTR).name("subNameNe").administrationState(AdministrationState.ACTIVATING).nodeListIdentity(1).nodes(nodeMO1)
                .attachedNodes(subNodeMO1).build()

        def nodes = nodeDao.findAllByNeType("MSC-BC-IS")
        def sub = subscriptionDao.findOneById(subMo.getPoId(), true) as MtrSubscription
        when:
        List<MediationTaskRequest> tasks = mtrSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, true)
        then:
        ACTIVATING == (String) subMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
        and: "task count will be 2"
        tasks.size() == 2
        tasks.each { assert it instanceof SubscriptionActivationTaskRequest }

        where: "used parameters for subscription and Eventcontext"
        neType      | activationOnAttachedNode
        'MSC-BC-IS' | false
        'BSC'       | true
    }

    def "will create tasks request for attached Nodes considering pool"() {
        given:
        def nodeMO1 = nodeUtil.builder("MSC-BC-IS-18A-V202").neType("MSC-BC-IS").build()
        Map<String, String> mscNodeAttribute = new HashMap<>()
        mscNodeAttribute.put("poolRefs", ["Pool=Pool1"])
        def nodeMO2 = nodeUtil.builder("MSC-BC-BSP-18A-V202").neType("MSC-BC-BSP").attributes(mscNodeAttribute).build()
        Map<String, String> bscNodeAttribute = new HashMap<>()
        bscNodeAttribute.put("connectedMsc", "NetworkElement=MSC-BC-IS-18A-V202")
        bscNodeAttribute.put("mscPoolRefs", ["Pool=Pool1"])
        def subNodeMO1 = nodeUtil.builder("NetworkElement=BSC").neType("BSC").attributes(bscNodeAttribute).build()

        def subMo = dps.subscription().type(SubscriptionType.MTR).name("subNameNe").administrationState(AdministrationState.ACTIVATING).nodeListIdentity(1).nodes(nodeMO1,nodeMO2)
                .attachedNodes(subNodeMO1).build()

        def nodes = nodeDao.findAllByNeType("MSC-BC-IS","MSC-BC-BSP")
        def sub = subscriptionDao.findOneById(subMo.getPoId(), true) as MtrSubscription
        when:
        List<MediationTaskRequest> tasks = mtrSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, true)
        then:
        ACTIVATING == (String) subMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
        and: "task count will be 3"
        tasks.size() == 3
        tasks.each { assert it instanceof SubscriptionActivationTaskRequest }
    }

    def "will create tasks request for attached Nodes considering connectedMscs"() {
        given:
        def nodeMO1 = nodeUtil.builder("MSC-BC-IS-18A-V202").neType("MSC-BC-IS").build()
        def nodeMO2 = nodeUtil.builder("MSC-BC-BSP-18A-V202").neType("MSC-BC-BSP").build()
        Map<String, String> bscNodeAttribute = new HashMap<>()
        bscNodeAttribute.put("connectedMscs", ["NetworkElement=MSC-BC-IS-18A-V202","NetworkElement=MSC-BC-BSP-18A-V202"])
        def subNodeMO1 = nodeUtil.builder("NetworkElement=BSC").neType("BSC").attributes(bscNodeAttribute).build()

        def subMo = dps.subscription().type(SubscriptionType.MTR).name("subNameNe").administrationState(AdministrationState.ACTIVATING).nodeListIdentity(1).nodes(nodeMO1,nodeMO2)
                .attachedNodes(subNodeMO1).build()

        def nodes = nodeDao.findAllByNeType("MSC-BC-IS","MSC-BC-BSP")
        def sub = subscriptionDao.findOneById(subMo.getPoId(), true) as MtrSubscription
        when:
        List<MediationTaskRequest> tasks = mtrSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, true)
        then:
        ACTIVATING == (String) subMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
        and: "task count will be 3"
        tasks.size() == 3
        tasks.each { assert it instanceof SubscriptionActivationTaskRequest }
    }


    def "will return ACTIVE_ERROR when node doesn't have attached BSC"() {
        given:
        def nodeMO1 = nodeUtil.builder("MSC-BC-IS-18A-V202").neType("MSC-BC-IS").build()
        def nodeMO2 = nodeUtil.builder("MSC-BC-BSP-18A-V202").neType("MSC-BC-BSP").build()
        Map<String, String> bscNodeAttribute = new HashMap<>()
        bscNodeAttribute.put("connectedMsc", "NetworkElement=MSC-BC-IS-18A-V202")
        def subNodeMO1 = nodeUtil.builder("BSC").neType("BSC").attributes(bscNodeAttribute).build()
        def subMo = dps.subscription().type(SubscriptionType.MTR).name("subNameNe").administrationState(AdministrationState.ACTIVATING).nodeListIdentity(1).nodes(nodeMO1, nodeMO2)
                .attachedNodes(subNodeMO1).build()

        def nodes = nodeDao.findAllByNeType("MSC-BC-IS", "MSC-BC-BSP")
        def sub = subscriptionDao.findOneById(subMo.getPoId(), true) as MtrSubscription
        when:
        List<MediationTaskRequest> tasks = mtrSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, true)
        then:
        ACTIVATING == (String) subMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
        and: "task count will be 2"
        tasks.size() == 2
        subMo.getAttribute("taskStatus") == TaskStatus.ERROR.name()
        tasks.each { assert it instanceof SubscriptionActivationTaskRequest }
    }

    def "will create task request when node NeConfigurationManagerState is DISABLED"() {
        given:
        def nodeMO1 = nodeUtil.builder("MSC-BC-IS-18A-V202").neType("MSC-BC-IS").neConfigurationManagerState(NeConfigurationManagerState.DISABLED).build()
        Map<String, String> bscNodeAttribute = new HashMap<>()
        bscNodeAttribute.put("connectedMsc", "NetworkElement=MSC-BC-IS-18A-V202")
        def subNodeMO1 = nodeUtil.builder("NetworkElement=BSC").neType("BSC").attributes(bscNodeAttribute).build()

        def subMo = dps.subscription().type(SubscriptionType.MTR).name("subNameNe").administrationState(AdministrationState.ACTIVATING).nodeListIdentity(1).nodes(nodeMO1)
                .attachedNodes(subNodeMO1).build()

        def nodes = nodeDao.findAllByNeType("MSC-BC-IS")
        def sub = subscriptionDao.findOneById(subMo.getPoId(), true) as MtrSubscription
        when:
        List<MediationTaskRequest> tasks = mtrSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, true)
        then:
        ACTIVATING == (String) subMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
        and: "task count will be 1"
        tasks.size() == 1
        tasks.each { assert it instanceof SubscriptionActivationTaskRequest }
    }

    def "will create task request when node with multiple attached nodes"() {
        given:
        def nodeMO1 = nodeUtil.builder("MSC-BC-IS-18A-V202").neType("MSC-BC-IS").neConfigurationManagerState(NeConfigurationManagerState.ENABLED).build()
        Map<String, String> bscNodeAttribute = new HashMap<>()
        bscNodeAttribute.put("connectedMsc", "NetworkElement=MSC-BC-IS-18A-V202")
        def subNodeMO1 = nodeUtil.builder("NetworkElement=BSC1").neType("BSC").attributes(bscNodeAttribute).build()
        def subNodeMO2 = nodeUtil.builder("NetworkElement=BSC2").neType("BSC").attributes(bscNodeAttribute).build()

        def subMo = dps.subscription().type(SubscriptionType.MTR).name("subNameNe").administrationState(AdministrationState.ACTIVATING).nodeListIdentity(1).nodes(nodeMO1)
                .attachedNodes(subNodeMO1, subNodeMO2).build()

        def nodes = nodeDao.findAllByNeType("MSC-BC-IS")
        def sub = subscriptionDao.findOneById(subMo.getPoId(), true) as MtrSubscription
        when:
        List<MediationTaskRequest> tasks = mtrSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, true)
        then:
        ACTIVATING == (String) subMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
        and: "task count will be 3"
        tasks.size() == 3
        tasks.each { assert it instanceof SubscriptionActivationTaskRequest }
    }

    def "will create task request when node is added to an active subscription without sending duplicate request for attached bsc node already activated"() {
        given:
        def nodeMO1 = nodeUtil.builder("MSC-BC-IS-18A-V202").neType("MSC-BC-IS").neConfigurationManagerState(NeConfigurationManagerState.ENABLED).build()
        def nodeMO2 = nodeUtil.builder("MSC-BC-BSP-18A-V202").neType("MSC-BC-BSP").build()

        Map<String, String> bscNodeAttribute = new HashMap<>()
        bscNodeAttribute.put("connectedMscs", ["NetworkElement=MSC-BC-BSP-18A-V202","NetworkElement=MSC-BC-IS-18A-V202"])

        Map<String, String> bscNodeAttribute1 = new HashMap<>()
        bscNodeAttribute1.put("connectedMsc", "NetworkElement=MSC-BC-BSP-18A-V202")


        def subNodeMO1 = nodeUtil.builder("NetworkElement=BSC1").neType("BSC").attributes(bscNodeAttribute).build()
        def subNodeMO2 = nodeUtil.builder("NetworkElement=BSC2").neType("BSC").attributes(bscNodeAttribute1).build()

        def subMo = dps.subscription().type(SubscriptionType.MTR).name("subNameNe").administrationState(AdministrationState.ACTIVATING).nodeListIdentity(1).nodes(nodeMO1, nodeMO2)
                .attachedNodes(subNodeMO1, subNodeMO2).build()

        scannerUtil.builder("USERDEF-subNameNe.Cont.Y.MTR", "NetworkElement=BSC1").node(subNodeMO1).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()

        def nodes = nodeDao.findAllByNeType("MSC-BC-BSP")
        def sub = subscriptionDao.findOneById(subMo.getPoId(), true) as MtrSubscription
        when:
        List<MediationTaskRequest> tasks = mtrSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, true)
        then:
        ACTIVATING == (String) subMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
        and: "task count will be 2"
        tasks.size() == 2
        tasks.each { assert it instanceof SubscriptionActivationTaskRequest }
    }

}


