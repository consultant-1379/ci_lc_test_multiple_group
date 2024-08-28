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

package com.ericsson.oss.services.pm.initiation.processor

import org.mockito.Mockito

import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.util.CollectionUtil
import com.ericsson.oss.pmic.util.TimeGenerator
import com.ericsson.oss.services.pm.initiation.model.metadata.res.PmResLookUp
import com.ericsson.oss.services.pm.initiation.processor.activation.ActivationProcessor
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService

class BaseProcessorSpec extends SkeletonSpec {
    @ObjectUnderTest
    ActivationProcessor activationProcessor
    @Inject
    SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    NodeDao nodeDao
    @ImplementationInstance
    PmResLookUp mockedPmResLookUp = Mock(PmResLookUp)
    @Inject
    @Modeled
    EventSender<MediationTaskRequest> eventSender;
    @ImplementationInstance
    TimerService timerService = Mock(TimerService)
    @ImplementationInstance
    TimeGenerator timer = Mockito.mock(TimeGenerator)

    def setup() {
        Mockito.when(timer.currentTimeMillis()).thenReturn(System.currentTimeMillis())
    }
    def ManagedObject RNC01
    def ManagedObject RNC02
    def ManagedObject RNC03

    ManagedObject nodeMO1, nodeMO2, nodeMO3

    static final RES_SUBSCRIPTION_NAME = "Test_RES"

    def ManagedObject attachedNode1Mo
    def ManagedObject attachedNode2Mo
    def ManagedObject attachedNode3Mo

    def rnc1NodeAttributes = [
        name             : "RNC01",
        networkElementId : "RNC01",
        fdn              : "NetworkElement=RNC01",
        platformType     : "CPP",
        neType           : "RNC",
        nodeModelIdentity: "16B-V.7.1659",
        ossModelIdentity : "16B-V.7.1659",
        ossPrefix        : "MeContext=RNC01",
        pmFunction       : true]
    def rnc2NodeAttributes = [
        name             : "RNC02",
        networkElementId : "RNC02",
        fdn              : "NetworkElement=RNC02",
        platformType     : "CPP",
        neType           : "RNC",
        nodeModelIdentity: "16B-V.7.1659",
        ossModelIdentity : "16B-V.7.1659",
        ossPrefix        : "MeContext=RNC02",
        pmFunction       : true]
    def rnc3NodeAttributes = [
        name             : "RNC03",
        networkElementId : "RNC03",
        fdn              : "NetworkElement=RNC03",
        platformType     : "CPP",
        neType           : "RNC",
        nodeModelIdentity: "16B-V.7.1659",
        ossModelIdentity : "16B-V.7.1659",
        ossPrefix        : "MeContext=RNC03",
        pmFunction       : true]

    def rbs1NodeAttributes = [
        name             : "RBS1",
        platformType     : "CPP",
        neType           : "RBS",
        nodeModelIdentity: "16A-U.4.210",
        ossModelIdentity : "16A-U.4.210",
        controllingRnc   : "NetworkElement=RNC01",
        pmFunction       : true]
    def rbs2NodeAttributes = [
        name             : "RBS2",
        platformType     : "CPP",
        neType           : "RBS",
        nodeModelIdentity: "16A-U.4.210",
        ossModelIdentity : "16A-U.4.210",
        controllingRnc   : "NetworkElement=RNC02",
        pmFunction       : true]
    def rbs3NodeAttributes = [
        name             : "RBS3",
        platformType     : "CPP",
        neType           : "RBS",
        nodeModelIdentity: "16A-U.4.210",
        ossModelIdentity : "16A-U.4.210",
        controllingRnc   : "NetworkElement=RNC03",
        pmFunction       : true]

    def buildNodesForRes() {
        attachedNode1Mo = dps.node().name(rbs1NodeAttributes.name).attributes(rbs1NodeAttributes).build()
        attachedNode2Mo = dps.node().name(rbs2NodeAttributes.name).attributes(rbs2NodeAttributes).build()
        attachedNode3Mo = dps.node().name(rbs3NodeAttributes.name).attributes(rbs3NodeAttributes).build()

        RNC01 = dps.node().name(rnc1NodeAttributes.name).attributes(rnc1NodeAttributes).build()
        RNC02 = dps.node().name(rnc2NodeAttributes.name).attributes(rnc2NodeAttributes).build()
        RNC03 = dps.node().name(rnc3NodeAttributes.name).attributes(rnc3NodeAttributes).build()
    }

    def Node RncNode1() {
        def node = new Node()
        node.setNeType("RNC")
        node.setNetworkElementId("RNC01")
        node.setFdn("NetworkElement=RNC01")
        node.setOssModelIdentity("16B-V.7.1659")
        node.setName("RNC01")
        node.setId(RNC01.getPoId())
        node.setTechnologyDomain(["EPS"])
        node.setPmFunction(true)
        return node
    }

    def Node RncNode2() {
        def node = new Node()
        node.setNeType("RNC")
        node.setNetworkElementId("RNC02")
        node.setFdn("NetworkElement=RNC02")
        node.setOssModelIdentity("16B-V.7.1659")
        node.setName("RNC02")
        node.setId(RNC02.getPoId())
        node.setTechnologyDomain(["EPS"])
        node.setPmFunction(true)
        return node
    }

    def Node RncNode3() {
        def node = new Node()
        node.setNeType("RNC")
        node.setNetworkElementId("RNC03")
        node.setFdn("NetworkElement=RNC03")
        node.setOssModelIdentity("16B-V.7.1659")
        node.setName("RNC03")
        node.setId(RNC03.getPoId())
        node.setTechnologyDomain(["EPS"])
        node.setPmFunction(true)
        return node
    }

    List<Node> attachedNodesList() {
        def nodeIds = [
            attachedNode2Mo.getPoId(),
            attachedNode3Mo.getPoId()
        ]
        return nodeDao.findAllById(CollectionUtil.toList(nodeIds))
    }

    def buildScannersForRes(String[] nodes, long subscrId) {
        for (String node : nodes) {
            scannerUtil.builder("USERDEF-Test_RES.Cont.Y.STATS", node)
                .subscriptionId(subscrId)
                .status(ScannerStatus.ACTIVE)
                .processType(ProcessType.STATS)
                .build()
        }
    }
}