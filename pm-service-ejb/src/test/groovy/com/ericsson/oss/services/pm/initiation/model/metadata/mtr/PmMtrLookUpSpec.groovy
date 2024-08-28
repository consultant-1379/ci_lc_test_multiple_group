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

package com.ericsson.oss.services.pm.initiation.model.metadata.mtr

import javax.inject.Inject

import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dao.versant.mapper.node.NodeMapper
import com.ericsson.oss.pmic.dto.Entity
import com.ericsson.oss.pmic.dto.node.Node
import com.ericsson.oss.pmic.dto.subscription.MtrSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.MtrAccessType
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilitiesLookup
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.generic.NodeServiceImpl

class PmMtrLookUpSpec extends SkeletonSpec {

    @ObjectUnderTest
    PmMtrLookUp pmMtrLookUp

    @Inject
    PmFunctionEnabledWrapper pmFunctionCache

    @ImplementationInstance
    NodeDao nodeDao = Stub(NodeDao)

    @Inject
    private SubscriptionDao subscriptionDao

    @Inject
    NodeMapper nodeMapper

    @ImplementationClasses
    def classes = [NodeServiceImpl.class, PmCapabilitiesLookup.class]

    ManagedObject MSC = nodeUtil.builder("MSC-BC-IS-01").pmEnabled(true).build()
    ManagedObject MSC1BSC1 = nodeUtil.builder("GSM02BSC01").pmEnabled(false).build()

    Map<String, ManagedObject[]> attachedNodeMos = [(MSC.getFdn()): [MSC1BSC1]]

    def "when getMtrAccessTypes is called returns the list of MtrAccess Types in format of Map"() {
        when:
        final Map<String, MtrAccessType[]> accessTypesMap = pmMtrLookUp.getMtrAccessTypes()
        then:
        1 == accessTypesMap.size()
        accessTypesMap.containsKey("mtrAccessTypes")
        def respList = accessTypesMap.get("mtrAccessTypes")
        11 == respList.size()
    }

    def "When fetchAttachedNodes is called return list of attached nodes based on MSC and BSC"() {
        given: "MTR subscription fetch attached nodes"
        nodeDao.fetchAttachedNodes(_, _) >> { args -> return getAttachedNodes(args[1] as Collection<String>) }
        pmFunctionCache.addEntry(MSC.fdn, true)
        pmFunctionCache.addEntry(MSC1BSC1.fdn, true)

        when: "fetchAttachedNodes is called"
        def result = pmMtrLookUp.fetchAttachedNodes([MSC.fdn] as Set<String>, true)
        then:
        result.size() == 1
        result[0].fdn == MSC1BSC1.fdn
    }

    @Unroll
    def 'Check if given  RR number:#subRr for Subscription:#subName already exists'() {
        given: 'MTR subscription in DPS and attached nodes based on MSC and BSC'
        nodeDao.fetchAttachedNodes(_, _) >> { args -> return getAttachedNodes(args[1] as Collection<String>) }
        pmFunctionCache.addEntry(MSC.fdn, true)
        pmFunctionCache.addEntry(MSC1BSC1.fdn, true)
        createSubscription(dpsSubName, dpsSubState, dpsSubRr)

        when:
        def mtrSubMo = createSubscription(subName, subState, subRr)
        def mtrSub = subscriptionDao.findOneById(mtrSubMo.getPoId(), true) as MtrSubscription
        def result = pmMtrLookUp.getUsedRecordingReferences(mtrSub)

        then:
        assert result.size() == expectedRrSize

        where:
        subName | subRr | subState                     | dpsSubName | dpsSubRr | dpsSubState                    | expectedRrSize
        'sub_1' | 1     | AdministrationState.INACTIVE |'dps_sub_1' | 1        | AdministrationState.ACTIVE     | 1
        'sub_2' | 3     | AdministrationState.INACTIVE |'dps_sub_2' | 2        | AdministrationState.ACTIVATING | 0
    }

    def createSubscription(name, state, rr) {
        dps.subscription().type(SubscriptionType.MTR).name(name).administrationState(state).nodeListIdentity(1).nodes(MSC)
                .attachedNodes(MSC1BSC1).attributes("mtrAccessTypes": [MtrAccessType.LCS.name()], "recordingReference": rr).build()
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