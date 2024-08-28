package com.ericsson.oss.services.pm.initiation.task.factories.deactivation

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.oss.pmic.api.modelservice.PmCapabilitiesLookupLocal
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState

import static com.ericsson.oss.pmic.cdi.test.util.Constants.DEACTIVATING
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.MTR_SUBSCRIPTION_ATTRIBUTES
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ADMINSTATE

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest

import javax.inject.Inject
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.MtrSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionDeactivationTaskRequest

class MtrSubscriptionDeactivationTaskRequestFactorySpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    private MtrSubscriptionDeactivationTaskRequestFactory mtrSubscriptionDeactivationTaskRequestFactory

    @Inject
    private PMICInitiationTrackerCache pmicInitiationTrackerCache

    @Inject
    private NodeDao nodeDao;

    @Inject
    private SubscriptionDao subscriptionDao;

    @MockedImplementation
    PmCapabilitiesLookupLocal pmCapabilitiesLookupLocal

    def setup() {
        pmCapabilitiesLookupLocal.getDefaultCapabilityValue(MTR_SUBSCRIPTION_ATTRIBUTES,
                'multipleNeTypesList') >> ['BSC']
    }
    @Override
    def autoAllocateFrom() {
        def packages = super.autoAllocateFrom()
        packages.addAll(['com.ericsson.oss.pmic.dao', 'com.ericsson.oss.pmic.dto'])
        return packages
    }

    def "Will create tasks for all nodes if each node has USERDEF scanner with subscription name but will not track"() {
        given:
        def nodeMO1 = nodeUtil.builder("MSC-BC-IS-18A-V202").neType("MSC-BC-IS").build()
        def nodeMO2 = nodeUtil.builder("MSC-BC-BSP-18A-V202").neType("MSC-BC-BSP").build()

        def subMo = dps.subscription().type(SubscriptionType.MTR).name("Test").administrationState(AdministrationState.DEACTIVATING)
                .nodes(nodeMO1, nodeMO2).build()

        scannerUtil.builder("USERDEF-TEST.MTR", "MSC-BC-IS-18A-V202").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()
        scannerUtil.builder("USERDEF-TEST.MTR", "MSC-BC-BSP-18A-V202").node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.ACTIVE).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as MtrSubscription
        when:
        def tasks = mtrSubscriptionDeactivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, false)
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
        def nodeMO1 = nodeUtil.builder("MSC-BC-IS-18A-V202").neType("MSC-BC-IS").build()
        def nodeMO2 = nodeUtil.builder("MSC-BC-BSP-18A-V202").neType("MSC-BC-BSP").build()
        def subMo = dps.subscription().type(SubscriptionType.MTR).name("Test").administrationState(AdministrationState.DEACTIVATING).nodes(nodeMO1, nodeMO2).build()
        scannerUtil.builder("USERDEF-Test.MTR", "MSC-BC-IS-18A-V202").node(nodeMO1).subscriptionId(subMo).status(ScannerStatus.INACTIVE).build()
        scannerUtil.builder("USERDEF-Test.MTR", "MSC-BC-BSP-18A-V202").node(nodeMO2).subscriptionId(subMo).status(ScannerStatus.ERROR).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as MtrSubscription
        when:
        def tasks = mtrSubscriptionDeactivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, true)
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
        def subMo = dps.subscription().type(SubscriptionType.MTR).name("Test").administrationState(AdministrationState.DEACTIVATING).build()

        def sub = subscriptionDao.findOneById(subMo.getPoId()) as MtrSubscription
        when:
        def tasks = mtrSubscriptionDeactivationTaskRequestFactory.createMediationTaskRequests([], sub, true)
        then:
        tasks.size() == 0
        0 * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _)
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
        nodeUtil.builder("NetworkElement=BSC").neType("BSC").attributes(bscNodeAttribute).build()

        def subMo = dps.subscription().type(SubscriptionType.MTR).name("subNameNe").administrationState(AdministrationState.DEACTIVATING).nodeListIdentity(1).nodes(nodeMO1, nodeMO2)
                .attachedNodes([]).build()

        def nodes = nodeDao.findAllByNeType("MSC-BC-IS", "MSC-BC-BSP")
        def sub = subscriptionDao.findOneById(subMo.getPoId(), true) as MtrSubscription
        when:
        List<MediationTaskRequest> tasks = mtrSubscriptionDeactivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, true)
        then:
        DEACTIVATING == (String) subMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
        and: "task count will be 3"
        tasks.size() == 3
        tasks.each { assert it instanceof SubscriptionDeactivationTaskRequest }
    }

    def "will create tasks request for attached Nodes considering connectedMscs"() {
        given:
        def nodeMO1 = nodeUtil.builder("MSC-BC-IS-18A-V202").neType("MSC-BC-IS").build()
        def nodeMO2 = nodeUtil.builder("MSC-BC-BSP-18A-V202").neType("MSC-BC-BSP").build()
        Map<String, String> bscNodeAttribute = new HashMap<>()
        bscNodeAttribute.put("connectedMscs", ["NetworkElement=MSC-BC-IS-18A-V202","NetworkElement=MSC-BC-BSP-18A-V202"])
        def subNodeMO1 = nodeUtil.builder("NetworkElement=BSC").neType("BSC").attributes(bscNodeAttribute).build()

        def subMo = dps.subscription().type(SubscriptionType.MTR).name("subNameNe").administrationState(AdministrationState.DEACTIVATING).nodeListIdentity(1).nodes(nodeMO1,nodeMO2,subNodeMO1)
                .attachedNodes([]).build()

        def nodes = nodeDao.findAllByNeType("MSC-BC-IS","MSC-BC-BSP","BSC")
        def sub = subscriptionDao.findOneById(subMo.getPoId(), true) as MtrSubscription
        when:
        List<MediationTaskRequest> tasks = mtrSubscriptionDeactivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, true)
        then:
        DEACTIVATING == (String) subMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
        and: "task count will be 3"
        tasks.size() == 3
        tasks.each { assert it instanceof SubscriptionDeactivationTaskRequest }
    }

    def "will create task request when node is removed from an active subscription without sending request for attached bsc node which is attached to another MSC"() {
        given:
        def nodeMO1 = nodeUtil.builder("MSC-BC-IS-18A-V202").neType("MSC-BC-IS").neConfigurationManagerState(NeConfigurationManagerState.ENABLED).build()
        nodeUtil.builder("MSC-BC-BSP-18A-V202").neType("MSC-BC-BSP").build()

        Map<String, String> bscNodeAttribute = new HashMap<>()
        bscNodeAttribute.put("connectedMscs", ["NetworkElement=MSC-BC-BSP-18A-V202","NetworkElement=MSC-BC-IS-18A-V202"])

        Map<String, String> bscNodeAttribute1 = new HashMap<>()
        bscNodeAttribute1.put("connectedMsc", "NetworkElement=MSC-BC-BSP-18A-V202")


        def subNodeMO1 = nodeUtil.builder("NetworkElement=BSC1").neType("BSC").attributes(bscNodeAttribute).build()
        nodeUtil.builder("NetworkElement=BSC2").neType("BSC").attributes(bscNodeAttribute1).build()

        def subMo = dps.subscription().type(SubscriptionType.MTR).name("subNameNe").administrationState(AdministrationState.DEACTIVATING).nodeListIdentity(1).nodes(nodeMO1)
                .attachedNodes(subNodeMO1).build()

        def nodes = nodeDao.findAllByNeType("MSC-BC-BSP")
        def sub = subscriptionDao.findOneById(subMo.getPoId(), true) as MtrSubscription
        when:
        List<MediationTaskRequest> tasks = mtrSubscriptionDeactivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, true)
        then:
        DEACTIVATING == (String) subMo.getAttribute(PMIC_ATT_SUBSCRIPTION_ADMINSTATE)
        and: "task count will be 2"
        tasks.size() == 2
        tasks.each { assert it instanceof SubscriptionDeactivationTaskRequest }
    }


}


