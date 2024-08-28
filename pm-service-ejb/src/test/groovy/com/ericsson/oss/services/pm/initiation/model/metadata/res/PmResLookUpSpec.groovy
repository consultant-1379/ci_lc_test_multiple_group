/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2018
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.initiation.model.metadata.res

import javax.inject.Inject

import com.ericsson.cds.cdi.support.configuration.InjectionProperties
import com.ericsson.cds.cdi.support.providers.custom.model.ModelPattern
import com.ericsson.cds.cdi.support.providers.custom.model.RealModelServiceProvider
import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.versant.mapper.node.NodeMapper
import com.ericsson.oss.pmic.dto.Entity
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilitiesLookup
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.generic.NodeServiceImpl
import com.ericsson.oss.services.pm.initiation.restful.ResSubscriptionAttributes
import com.ericsson.oss.services.pm.services.exception.PfmDataException

class PmResLookUpSpec extends SkeletonSpec {

    static def filteredModels = [new ModelPattern("dps_primarytype", "RNC_NODE_MODEL", "ResMeasControl", ".*"),
                                 new ModelPattern("oss_edt", "RNC_NODE_MODEL", "SupportedResServices", ".*"),
                                 new ModelPattern("oss_edt", "RNC_NODE_MODEL", "SupportedResMeasQuantities", ".*"),
                                 new ModelPattern("cfm_miminfo", "RNC", ".*", ".*"),
                                 new ModelPattern("oss_targetversion", "NODE%3aRNC", ".*", ".*"),
                                 new ModelPattern("oss_capability", "global", "RES_SubscriptionAttributes", ".*"),
                                 new ModelPattern("oss_capability", "global", "PMICFunctions", ".*"),
                                 new ModelPattern("oss_capabilitysupport", "RNC", "PMICFunctions", ".*")]

    static RealModelServiceProvider realModelServiceProvider = new RealModelServiceProvider(filteredModels)

    @Override
    def addAdditionalInjectionProperties(InjectionProperties injectionProperties) {
        injectionProperties.addInjectionProvider(realModelServiceProvider)
        injectionProperties.autoLocateFrom('com.ericsson.oss.services.pm.initiation')
    }

    @ObjectUnderTest
    PmResLookUp pmResLookUp

    @ImplementationInstance
    NodeDao nodeDao = Stub(NodeDao)

    @Inject
    NodeMapper nodeMapper

    @Inject
    PmFunctionEnabledWrapper pmFunctionCache

    @ImplementationClasses
    def classes = [NodeServiceImpl.class, PmCapabilitiesLookup.class]

    ManagedObject RNC01 = nodeUtil.builder("RNC01").pmEnabled(false).build()
    ManagedObject RNC02 = nodeUtil.builder("RNC02").pmEnabled(true).build()

    ManagedObject RNC01RBS01 = nodeUtil.builder("RNC01RBS01").pmEnabled(false).build()
    ManagedObject RNC01RBS02 = nodeUtil.builder("RNC01RBS02").pmEnabled(true).build()

    ManagedObject RNC02RBS01 = nodeUtil.builder("RNC02RBS01").pmEnabled(false).build()
    ManagedObject RNC02RBS02 = nodeUtil.builder("RNC02RBS02").pmEnabled(true).build()

    Map<String, ManagedObject[]> attachedNodeMos = [(RNC01.getFdn()): [RNC01RBS01, RNC01RBS02],
                                                    (RNC02.getFdn()): [RNC02RBS01, RNC02RBS02]]

    def "When getResAttributes is called for supported node mims it returns correct attributes"() {
        given: " a set of supported mim versions"
        def versions = ["15B-V.5.4658", "16A-V.6.940", "16B-V.7.1659", "17A-V.8.1349"]
        when: "getResAttributes is called "
        ResSubscriptionAttributes result = pmResLookUp.getResAttributes(getNodeAndVersionSet(versions))

        then:
        result.supportedResUeFraction.size() == 5
        result.supportedSamplingRates.size() == 4
        result.supportedSamplingRates.get("SPEECH").size() == 12
        result.supportedSamplingRates.get("INTERACTIVE").size() == 12
        result.supportedSamplingRates.get("VIDEO").size() == 12
        result.supportedSamplingRates.get("STREAMING").size() == 12
        result.supportedResRmq.size() == 6
        result.supportedResServices.size() == 22
        result.supportedResSpreadingFactor == [4, 8, 16, 32, 64, 128, 256] as Set<Integer>
    }

    def "When getResAttributes is called for an unsupported node mims it throws exception"() {
        given: "an unsupported mim version"
        def versions = ["99A-V.9.9999"]
        when: "getResAttributes is called "
        pmResLookUp.getResAttributes(getNodeAndVersionSet(versions))
        then:
        PfmDataException ex = thrown()
        ex.getMessage().contains("No target version information exists for given target model identity 99A-V.9.9999")
    }

    def getNodeAndVersionSet(List<String> versions) {
        List<String> technologyDomainList = []
        technologyDomainList.add("UMTS")
        Set<NodeTypeAndVersion> nodeTypeAndVersionSet = new HashSet<>()
        for (String version : versions) {
            nodeTypeAndVersionSet.add(new NodeTypeAndVersion("RNC", version, technologyDomainList))
        }
        return nodeTypeAndVersionSet
    }

    def "When fetchAttachedNodes is called return list of attached nodes based on PmFunction on both rnc and rbs"() {
        given: "Res subscription fetch attached nodes"
        nodeDao.fetchAttachedNodes(_, _, _, _) >> { args -> return getAttachedNodes(args[1] as Collection<String>) }
        pmFunctionCache.addEntry(RNC01.fdn, false)
        pmFunctionCache.addEntry(RNC02.fdn, true)
        pmFunctionCache.addEntry(RNC01RBS01.fdn, false)
        pmFunctionCache.addEntry(RNC01RBS02.fdn, true)
        pmFunctionCache.addEntry(RNC02RBS01.fdn, false)
        pmFunctionCache.addEntry(RNC02RBS02.fdn, true)
        when: "fetchAttachedNodes is called"
        def result = pmResLookUp.fetchAttachedNodes([] as List<CellInfo>, true, [RNC01.fdn, RNC02.fdn] as Set<String>, true)
        then:
        result.size() == 1
        result[0].fdn == RNC02RBS02.fdn
    }

    def getAttachedNodes(Collection<String> rncNodeFdns) {
        List<Node> attachedNodes = new ArrayList<Node>();
        for (String fdn : rncNodeFdns) {
            attachedNodes.addAll(convertPOIntoNodes(attachedNodeMos.get(fdn)))
        }
        return attachedNodes
    }

    def convertPOIntoNodes(List<ManagedObject> nodeMos) {
        List<Node> nodes = new ArrayList<Node>();
        for (ManagedObject nodeMo : nodeMos) {
            Entity entity = new Entity("NetworkElement", Long.valueOf(nodeMo.getPoId()), nodeMo.getFdn(), nodeMo.getAllAttributes())
            Node node = nodeMapper.toDto(entity)
            nodes.add(node)
        }
        return nodes
    }
}