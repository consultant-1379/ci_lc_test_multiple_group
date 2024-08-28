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

package com.ericsson.oss.services.pm.initiation.model.metadata.moinstances

import com.ericsson.cds.cdi.support.configuration.InjectionProperties
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.api.constants.ModelConstants
import com.ericsson.oss.pmic.cdi.test.util.PmBaseSpec
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.subscription.cdts.MoinstanceInfo

class PmMoinstancesLookUpSpec extends PmBaseSpec {

    @ObjectUnderTest
    PmMoinstancesLookUp moinstancesLookUp

    @Override
    def addAdditionalInjectionProperties(InjectionProperties injectionProperties) {
        super.addAdditionalInjectionProperties(injectionProperties)
        injectionProperties.autoLocateFrom('com.ericsson.oss.services.pm.initiation')
    }

    def "When getSupportedMoInstances is called on an existing transport network fdn return MO instances"(MoinstanceInfo moInstanceInfoObj) {

        given: "A node name, node type and MO Class types for selected counters"
        String nodeName = "RNC06RBS01:RNC"
        String moClassTypes = "Aal1TpVccTp,Aal5TpVccTp"
        String subscriptionType = "MOINSTANCE"
        addManagedObjectInDps("NetworkElement=RNC06RBS01")

        when: "getSupportedMoInstances is called "
        List<MoinstanceInfo> moInstances = moinstancesLookUp.getMoinstances(getNodeAndVersionSet(), nodeName, moClassTypes, subscriptionType)

        then: "A List of MoInstanceInfo object is returned"
        moInstances.size() == 3
        moInstances.contains(moInstanceInfoObj)

        where:
        moInstanceInfoObj << [new MoinstanceInfo("RNC06RBS01", "Aal1TpVccTp=1-13-1"),
                              new MoinstanceInfo("RNC06RBS01", "Aal1TpVccTp=1-31-1"),
                              new MoinstanceInfo("RNC06RBS01", "Aal1TpVccTp=1-1-1")]
    }

    def "When getSupportedMoInstances is called on an existing RncFunction fdn return Cell instances"(MoinstanceInfo moInstanceInfoObj,
                                                                                                      final String subscriptionType) {

        given: "A node name, node type and MO Class types for selected counters"
        String nodeName = "RNC07RBS01:RNC"
        String moClassTypes = "UtranCell"
        addCellManagedObjectInDps("NetworkElement=RNC07RBS01")

        when: "getSupportedMoInstances is called "
        List<MoinstanceInfo> cellMoInstances = moinstancesLookUp.getMoinstances(getNodeAndVersionSet(), nodeName, moClassTypes, subscriptionType)

        then: "A List of MoInstanceInfo object for cell is returned"
        cellMoInstances.size() == 4
        cellMoInstances.contains(moInstanceInfoObj)

        where:
        moInstanceInfoObj                                    | subscriptionType
        new MoinstanceInfo("RNC07RBS01", "UtranCell=1-13-1") | "CELLTRAFFIC"
        new MoinstanceInfo("RNC07RBS01", "UtranCell=1-31-1") | "CELLTRAFFIC"
        new MoinstanceInfo("RNC07RBS01", "UtranCell=1-1-1")  | "CELLTRAFFIC"
        new MoinstanceInfo("RNC07RBS01", "UtranCell=1-1-2")  | "GPEH"
    }

    def "When getSupportedMoInstances is called on an existing GeranCellM fdn return Cell instances"(MoinstanceInfo moInstanceInfoObj,
                                                                                                     final String subscriptionType) {

        given: "A node name, node type and MO Class types for selected counters"
        String nodeName = "GSM02BSC03:BSC"
        String moClassTypes = "GeranCell"
        addCellManagedObjectInDpsBsc("NetworkElement=GSM02BSC03")

        when: "getSupportedMoInstances is called "
        List<MoinstanceInfo> cellMoInstances = moinstancesLookUp.getMoinstances(getNodeAndVersionSetForBsc(), nodeName, moClassTypes, subscriptionType)

        then: "A List of MoInstanceInfo object for cell is returned"
        cellMoInstances.size() == 4
        cellMoInstances.contains(moInstanceInfoObj)

        where:
        moInstanceInfoObj                                     | subscriptionType
        new MoinstanceInfo("GSM02BSC03", "GeranCell=173027O") | "RPMO"
        new MoinstanceInfo("GSM02BSC03", "GeranCell=1730271") | "RPMO"
        new MoinstanceInfo("GSM02BSC03", "GeranCell=1730272") | "RPMO"
        new MoinstanceInfo("GSM02BSC03", "GeranCell=1730273") | "RPMO"
    }

    def getNodeAndVersionSet() {
        List<String> technologyDomainList = []
        technologyDomainList.add("UMTS")
        NodeTypeAndVersion nodeTypeAndVersion = new NodeTypeAndVersion("RNC", "16B-V.7.1659", technologyDomainList);
        Set<NodeTypeAndVersion> nodeTypeAndVersionSet = new HashSet<>();
        nodeTypeAndVersionSet.add(nodeTypeAndVersion)
        return nodeTypeAndVersionSet
    }

    def getNodeAndVersionSetForBsc() {
        List<String> technologyDomainList = []
        technologyDomainList.add("GSM")
        NodeTypeAndVersion nodeTypeAndVersion = new NodeTypeAndVersion("BSC", "BSC-G19.Q1-R1V-APG43L-3.6.0-R7A", technologyDomainList);
        Set<NodeTypeAndVersion> nodeTypeAndVersionSet = new HashSet<>();
        nodeTypeAndVersionSet.add(nodeTypeAndVersion)
        return nodeTypeAndVersionSet
    }

    def addManagedObjectInDps(final String nodeFdn) {
        ManagedObject managedObject = nodeUtil.builder("RNC06RBS01").fdn(nodeFdn).namespace('OSS_NE_DEF').neType(NetworkElementType.RNC).build()
        //managedElement
        ManagedObject managedElement = configurableDps.addManagedObject().withFdn("NetworkElement=RNC06RBS01,ManagedElement=1").build()
        dpsUtils.addAssociation(managedObject, ModelConstants.OssTopModelConstants.OSS_TOP_NODE_ROOT_ENDPOINT, managedElement)

        //transportNetwork
        nodeUtil.builder("RNC06RBS01").fdn("NetworkElement=RNC06RBS01,ManagedElement=1,TransportNetwork=1").namespace('RNC_NODE_MODEL').build()
        //moInstances
        nodeUtil.builder("RNC06RBS01").
                fdn("NetworkElement=RNC06RBS01,ManagedElement=1,TransportNetwork=1,Aal1TpVccTp=1-13-1").
                namespace('RNC_NODE_MODEL').
                type('Aal1TpVccTp').
                attributes('name': '1-1-1', 'type': 'Aal1TpVccTp').
                build()
        nodeUtil.builder("RNC06RBS01").
                fdn("NetworkElement=RNC06RBS01,ManagedElement=1,TransportNetwork=1,Aal1TpVccTp=1-31-1").
                namespace('RNC_NODE_MODEL').
                type('Aal1TpVccTp').
                attributes('name': '1-1-1', 'type': 'Aal1TpVccTp').
                build()
        nodeUtil.builder("RNC06RBS01").
                fdn("NetworkElement=RNC06RBS01,ManagedElement=1,TransportNetwork=1,Aal1TpVccTp=1-1-1").
                namespace('RNC_NODE_MODEL').
                type('Aal1TpVccTp').
                attributes('name': '1-1-1', 'type': 'Aal1TpVccTp').
                build()
    }

    def addCellManagedObjectInDps(final String nodeFdn) {
        ManagedObject managedObject = nodeUtil.builder("RNC07RBS01").fdn(nodeFdn).namespace('OSS_NE_DEF').neType(NetworkElementType.RNC).build()
        //managedElement
        ManagedObject managedElement = configurableDps.addManagedObject().withFdn("NetworkElement=RNC07RBS01,ManagedElement=1").build()
        dpsUtils.addAssociation(managedObject, ModelConstants.OssTopModelConstants.OSS_TOP_NODE_ROOT_ENDPOINT, managedElement)

        //Rnc Function
        nodeUtil.builder("RNC07RBS01").fdn("NetworkElement=RNC07RBS01,ManagedElement=1,RncFunction=1").namespace('RNC_NODE_MODEL').build()
        //Cell Instances
        nodeUtil.builder("RNC07RBS01").
                fdn("NetworkElement=RNC07RBS01,ManagedElement=1,RncFunction=1,UtranCell=1-13-1").
                namespace('RNC_NODE_MODEL').
                type('UtranCell').
                attributes('name': '1-13-1', 'type': 'UtranCell').
                build()
        nodeUtil.builder("RNC07RBS01").
                fdn("NetworkElement=RNC07RBS01,ManagedElement=1,RncFunction=1,UtranCell=1-31-1").
                namespace('RNC_NODE_MODEL').
                type('UtranCell').
                attributes('name': '1-31-1', 'type': 'UtranCell').
                build()
        nodeUtil.builder("RNC07RBS01").
                fdn("NetworkElement=RNC07RBS01,ManagedElement=1,RncFunction=1,UtranCell=1-1-1").
                namespace('RNC_NODE_MODEL').
                type('UtranCell').
                attributes('name': '1-1-1', 'type': 'UtranCell').
                build()
        nodeUtil.builder("RNC07RBS01").
                fdn("NetworkElement=RNC07RBS01,ManagedElement=1,RncFunction=1,UtranCell=1-1-2").
                namespace('RNC_NODE_MODEL').
                type('UtranCell').
                attributes('name': '1-1-2', 'type': 'UtranCell').
                build()
    }

    def addCellManagedObjectInDpsBsc(final String nodeFdn) {
        ManagedObject managedObject = nodeUtil.builder("GSM02BSC03").fdn(nodeFdn).namespace('OSS_NE_DEF').neType(NetworkElementType.BSC).build()
        //managedElement
        ManagedObject managedElement = configurableDps.addManagedObject().withFdn("NetworkElement=GSM02BSC03,ManagedElement=1").build()
        dpsUtils.addAssociation(managedObject, ModelConstants.OssTopModelConstants.OSS_TOP_NODE_ROOT_ENDPOINT, managedElement)

        //Rnc Function
        nodeUtil.builder("GSM02BSC03").fdn("NetworkElement=GSM02BSC03,ManagedElement=1,BscFunction=1,BscM=1,GeranCellM=1").namespace('BscM').build()
        //Cell Instances
        nodeUtil.builder("GSM02BSC03").
                fdn("NetworkElement=GSM02BSC03,ManagedElement=1,BscFunction=1,BscM=1,GeranCellM=1,GeranCell=173027O").
                namespace('BscM').
                type('GeranCell').
                attributes('name': '173027O', 'type': 'GeranCell').
                build()
        nodeUtil.builder("GSM02BSC03").
                fdn("NetworkElement=GSM02BSC03,ManagedElement=1,BscFunction=1,BscM=1,GeranCellM=1,GeranCell=1730271").
                namespace('BscM').
                type('GeranCell').
                attributes('name': '1730271', 'type': 'GeranCell').
                build()
        nodeUtil.builder("GSM02BSC03").
                fdn("NetworkElement=GSM02BSC03,ManagedElement=1,BscFunction=1,BscM=1,GeranCellM=1,GeranCell=1730272").
                namespace('BscM').
                type('GeranCell').
                attributes('name': '1730272', 'type': 'GeranCell').
                build()
        nodeUtil.builder("GSM02BSC03").
                fdn("NetworkElement=GSM02BSC03,ManagedElement=1,BscFunction=1,BscM=1,GeranCellM=1,GeranCell=1730273").
                namespace('BscM').
                type('GeranCell').
                attributes('name': '1730273', 'type': 'GeranCell').
                build()
    }
}
