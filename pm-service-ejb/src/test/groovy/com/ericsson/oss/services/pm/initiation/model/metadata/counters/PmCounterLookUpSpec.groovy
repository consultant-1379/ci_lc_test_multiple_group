package com.ericsson.oss.services.pm.initiation.model.metadata.counters

import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.impl.counters.PmCountersLifeCycleResolverImpl
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilityReaderImpl
import com.ericsson.oss.services.pm.PmServiceEjbFullSpec
import com.ericsson.oss.services.pm.initiation.model.utils.ModelDefiner
import com.ericsson.oss.services.pm.services.exception.PfmDataException
import com.ericsson.services.pm.initiation.restful.api.CounterTableRow
import com.ericsson.services.pm.initiation.restful.api.PmMimVersionQuery

class PmCounterLookUpSpec extends PmServiceEjbFullSpec {

    @ObjectUnderTest
    PmCountersLookUp pmCounterLookUp

    @ImplementationClasses
    def classes = [PmCapabilityReaderImpl.class, PmCountersLifeCycleResolverImpl.class]

    public static String ERBS_OSS_MODEL_IDENTITY = "18.Q2-J.1.280"
    public static String RADIONODE_OSS_MODEL_IDENTITY = "16B-R28GY"
    public static String RADIONODE_OSS_MODEL_IDENTITY_2 = "16B-R28DS"
    public static String RADIONODE_OSS_MODEL_IDENTITY_3 = "19.Q3-R41A26"
    public static String SGSN_MME_OSS_MODEL_IDENTITY = "16A-CP02"
    public static String FIVEGRADIONODE_OSS_MODEL_IDENTITY = "18.Q2-R1A852"

    public static String NODE_TYPE_ERBS = "ERBS"
    public static String NODE_TYPE_SGSN_MME = "SGSN-MME"
    public static String NODE_TYPE_RADIONODE = "RadioNode"
    public static String NODE_TYPE_5GRADIONODE = "5GRadioNode"

    @Unroll
    def "getCounters for #nodeType node type and technologyDomine=#technologyDomains should return #counterSize counters for given one mim version"() {
        given: "mim query for a given node type"
        PmMimVersionQuery pmvq = createMimQuery(nodeType, ossModelIdentity, technologyDomains)

        when: "a query is made to get counters"
        Collection<CounterTableRow> actualresult = pmCounterLookUp.getCountersForAllVersions(pmvq.getMimVersions(), [ModelDefiner.NE.urnPattern], false)

        then: "no exception is thrown and counters is not empty"
        noExceptionThrown()
        actualresult.size() == counterSize

        where:
        nodeType            | ossModelIdentity               | technologyDomains | counterSize
        NODE_TYPE_ERBS      | ERBS_OSS_MODEL_IDENTITY        | ["EPS"]           | 4071
        NODE_TYPE_ERBS      | ERBS_OSS_MODEL_IDENTITY        | ["UMTS"]          | 4071
        NODE_TYPE_ERBS      | ERBS_OSS_MODEL_IDENTITY        | ["GSM"]           | 4071
        NODE_TYPE_ERBS      | ERBS_OSS_MODEL_IDENTITY        | ["EPS", "GSM"]    | 4071
        NODE_TYPE_RADIONODE | RADIONODE_OSS_MODEL_IDENTITY_3 | ["EPS"]           | 4603
        NODE_TYPE_RADIONODE | RADIONODE_OSS_MODEL_IDENTITY_3 | ["5GS"]           | 830
        NODE_TYPE_RADIONODE | RADIONODE_OSS_MODEL_IDENTITY_3 | ["EPS", "5GS"]    | 5040
    }

    def "getCounters should return all counters for given one mim version wcdma radionode"() {
        given: "a radionode netype with mim query"
        final PmMimVersionQuery pmvqUMTS = createMimQuery(NODE_TYPE_RADIONODE, RADIONODE_OSS_MODEL_IDENTITY, ["UMTS"])
        final PmMimVersionQuery pmvqEPS = createMimQuery(NODE_TYPE_RADIONODE, RADIONODE_OSS_MODEL_IDENTITY, ["EPS"])
        when: "a query is made to get counters"
        Collection<CounterTableRow> actualResultUMTS = pmCounterLookUp.getCountersForAllVersions(pmvqUMTS.getMimVersions(), [ModelDefiner.NE.urnPattern], false)
        Collection<CounterTableRow> actualResultEPS = pmCounterLookUp.getCountersForAllVersions(pmvqEPS.getMimVersions(), [ModelDefiner.NE.urnPattern], false)
        then: "counter should not be empty"
        noExceptionThrown()
        actualResultUMTS.size() == 520
        actualResultEPS.size() == 2743
    }

    @Unroll
    def "get counters for #neType with one mim version=#ossModelIdentity and #modelDefiner definer should return the same counters from cache"() {
        given: "a node type with an oss model identity"
        PmMimVersionQuery pmvq = createMimQuery(neType, ossModelIdentity, technologyDomains)
        when: "a first query is made it's requested from model service"
        Collection<CounterTableRow> actualModelServiceResult = pmCounterLookUp.getCountersForAllVersions(pmvq.getMimVersions(), [modelDefiner.urnPattern], false)
        and: "a second query is made it's requested from counter local cache"
        Collection<CounterTableRow> actualCachedResult = pmCounterLookUp.getCountersForAllVersions(pmvq.getMimVersions(), [modelDefiner.urnPattern], false)
        then: "both counter from cache and model service are equal"
        noExceptionThrown()
        actualModelServiceResult.size() == modelServiceCounterSize
        actualCachedResult.size() == localCacheCounterSize
        actualModelServiceResult == actualCachedResult
        where:
        neType              | ossModelIdentity             | technologyDomains | modelDefiner     || modelServiceCounterSize | localCacheCounterSize
        NODE_TYPE_SGSN_MME  | SGSN_MME_OSS_MODEL_IDENTITY  | ["EPS", "UMTS"]   | ModelDefiner.NE  || 1043                    | 1043
        NODE_TYPE_SGSN_MME  | SGSN_MME_OSS_MODEL_IDENTITY  | ["EPS", "UMTS"]   | ModelDefiner.OSS || 283                     | 283
        NODE_TYPE_RADIONODE | RADIONODE_OSS_MODEL_IDENTITY | ["EPS"]           | ModelDefiner.NE  || 2743                    | 2743
        NODE_TYPE_RADIONODE | RADIONODE_OSS_MODEL_IDENTITY | ["EPS"]           | ModelDefiner.OSS || 652                     | 652
        NODE_TYPE_ERBS      | ERBS_OSS_MODEL_IDENTITY      | ["EPS"]           | ModelDefiner.NE  || 4071                    | 4071
        NODE_TYPE_ERBS      | ERBS_OSS_MODEL_IDENTITY      | ["EPS"]           | ModelDefiner.OSS || 732                     | 732
    }

    @Unroll
    def "get counters for #neType one mim version get=#ossModelIdentity OSS defined then get NE defined should not be equal"() {
        given: "mim query for nodeType"
        PmMimVersionQuery pmvq = createMimQuery(neType, ossModelIdentity, technologyDomains)
        when: "mim query for counters with OSS modelDefiner is made"
        Collection<CounterTableRow> actualOSSResult = pmCounterLookUp.getCountersForAllVersions(pmvq.getMimVersions(), [ModelDefiner.OSS.urnPattern], false)
        and: "mim query for counters with NE modelDefiner is made"
        Collection<CounterTableRow> actualNEResult = pmCounterLookUp.getCountersForAllVersions(pmvq.getMimVersions(), [ModelDefiner.NE.urnPattern], false)
        then: "OSS counter and NE counter for a nodeType returned should not be equal"
        actualOSSResult != actualNEResult
        actualOSSResult.size() == ossCounterSize
        actualNEResult.size() == neCounterSize
        where:
        neType              | ossModelIdentity             | technologyDomains || ossCounterSize | neCounterSize
        NODE_TYPE_SGSN_MME  | SGSN_MME_OSS_MODEL_IDENTITY  | ["EPS", "UMTS"]   || 283            | 1043
        NODE_TYPE_RADIONODE | RADIONODE_OSS_MODEL_IDENTITY | ["EPS"]           || 652            | 2743
        NODE_TYPE_ERBS      | ERBS_OSS_MODEL_IDENTITY      | ["EPS"]           || 732            | 4071
    }

    @Unroll
    def "get applicable counters for #neType returns only selected counters"() {
        when: "checking for applicable valid counter"
        List<String> actualResult = pmCounterLookUp.getApplicableCounters(selectedCounter, neType, ossModelIdentity, technologyDomains)
        then: "same counters should be returned and not be empty"
        selectedCounter.size() == actualResult.size()
        for (CounterInfo counterInfo : selectedCounter) {
            assert actualResult.contains(counterInfo.getMoClassType() + ":" + counterInfo.getName())
        }
        where:
        neType              | ossModelIdentity             | technologyDomains | selectedCounter
        NODE_TYPE_ERBS      | ERBS_OSS_MODEL_IDENTITY      | ["EPS"]           | [new CounterInfo("pmAdvCellSupDetection", "EUtranCellFDD"), new CounterInfo("pmEenbPktLostDlCa", "ExternalENodeBFunction")]
        NODE_TYPE_RADIONODE | RADIONODE_OSS_MODEL_IDENTITY | ["EPS"]           | [new CounterInfo("pmAdvCellSupDetection", "EUtranCellFDD"), new CounterInfo("pmHoPrepSuccLb", "UtranCellRelation")]
        NODE_TYPE_SGSN_MME  | SGSN_MME_OSS_MODEL_IDENTITY  | ["EPS"]           | [new CounterInfo("MME Attach", "MME"), new CounterInfo("MME ATTACH ABORT", "TAI")]
    }

    @Unroll
    def "get applicable counters for nodeType=#neType should return only selected counters and filter non-applicable/invalid counters "() {
        when: "validating applicable counters and it contains invalid counters"
        List<String> actualResult = pmCounterLookUp.getApplicableCounters(selectedCounter, neType, ossModelIdentity, technologyDomains)
        then: "invalid counters should be filtered  out"
        selectedCounter.size() != actualResult.size()
        for (CounterInfo counterInfo : selectedCounter) {
            if (counterInfo.getName().contains("invalid")) {
                assert !actualResult.contains(counterInfo.getMoClassType() + ":" + counterInfo.getName())
            } else {
                assert actualResult.contains(counterInfo.getMoClassType() + ":" + counterInfo.getName())
            }
        }
        where:
        neType              | ossModelIdentity             | technologyDomains | selectedCounter
        NODE_TYPE_ERBS      | ERBS_OSS_MODEL_IDENTITY      | ["EPS"]           | [new CounterInfo("pmAdvCellSupDetection", "EUtranCellFDD"), new CounterInfo("pmEenbPktLostDlCa", "ExternalENodeBFunction"), new CounterInfo("invalid-name", "group")]
        NODE_TYPE_RADIONODE | RADIONODE_OSS_MODEL_IDENTITY | ["EPS"]           | [new CounterInfo("pmAdvCellSupDetection", "EUtranCellFDD"), new CounterInfo("pmHoPrepSuccLb", "UtranCellRelation"), new CounterInfo("invalid-name", "group")]
        NODE_TYPE_SGSN_MME  | SGSN_MME_OSS_MODEL_IDENTITY  | ["EPS"]           | [new CounterInfo("MME Attach", "MME"), new CounterInfo("MME ATTACH ABORT", "TAI"), new CounterInfo("invalid-name", "group")]
    }

    @Unroll
    def "get counters should return all counters for multiple mim version for modeldefine=#modelDefiner"() {
        given: "given multiple versions mim query for a node type"
        PmMimVersionQuery pmvq = createMimQuery(NODE_TYPE_RADIONODE, RADIONODE_OSS_MODEL_IDENTITY, ["EPS"])
        pmvq = addMimQuery(pmvq, NODE_TYPE_RADIONODE, RADIONODE_OSS_MODEL_IDENTITY_2, ["EPS"])
        when: "get counter for multiple versions mim query for node type"
        Collection<CounterTableRow> actualResult = pmCounterLookUp.getCountersForAllVersions(pmvq.getMimVersions(), [modelDefiner.urnPattern], false)
        then: "no exception is thrown and counter not empty"
        noExceptionThrown()
        actualResult.size() == counterSize
        where:
        modelDefiner     | counterSize
        ModelDefiner.NE  | 2743
        ModelDefiner.OSS | 652
    }

    @Unroll
    def "get correct counter list should return all counters for multiple mim version for modeldefine=#modelDefiner"() {
        given: "given multiple versions mim query for the node type"
        PmMimVersionQuery pmvq = createMimQuery(NODE_TYPE_RADIONODE, RADIONODE_OSS_MODEL_IDENTITY, ["EPS"])
        pmvq = addMimQuery(pmvq, NODE_TYPE_RADIONODE, RADIONODE_OSS_MODEL_IDENTITY_2, ["EPS"])
        when: "correct counter list for mim versions is called for valid counters"
        Collection<CounterTableRow> actualResult = pmCounterLookUp.getCorrectCounterListForTheSpecifiedMims(inputCounterList, pmvq.getMimVersions(), [modelDefiner.urnPattern], Collections.emptyList(), false)
        then: "no exception is thrown and same counters are returned"
        noExceptionThrown()
        actualResult.size() == counterSize
        where:
        modelDefiner     | counterSize | inputCounterList                                                                                                           | useExternalName
        ModelDefiner.NE  | 2           | [new CounterInfo("pmZtemporary50", "BbProcessingResource"), new CounterInfo("bfdSessRxPackets", "BfdSessionIPv4")]         | false
        ModelDefiner.OSS | 2           | [new CounterInfo("pmAdvCellSupDetection", "EUtranCellFDD"), new CounterInfo("pmHoExeAttSrvccUeMeas", "UtranCellRelation")] | true
    }

    def "get counters for 5G RadioNode should not filter preliminary counters"() {
        given: "mim query for 5G Radio Node "
        PmMimVersionQuery pmvq = createMimQuery(NODE_TYPE_5GRADIONODE, FIVEGRADIONODE_OSS_MODEL_IDENTITY, ["EPS"])
        when: "a query is made to get counters"
        Collection<CounterTableRow> actualResult = pmCounterLookUp.getCountersForAllVersions(pmvq.getMimVersions(), [ModelDefiner.NE.urnPattern], false)
        then: "it should return counter list containing preliminary counters"
        !actualResult.isEmpty()
        actualResult.size() == 350
    }

    @Unroll
    def "get PMCounters for #neType and modelDefine=#modelDefiner should throw PfmDataException when querying with invalid oss model identity=#invalidOssModelIdentity"() {
        given: "mim query for invalid oss model identity"
        PmMimVersionQuery pmvq = createMimQuery(neType, invalidOssModelIdentity, ["EPS"])
        when: ""
        pmCounterLookUp.getCountersForAllVersions(pmvq.getMimVersions(), [modelDefiner.urnPattern], false)
        then: "exception should be thrown"
        def exception = thrown(PfmDataException.class)
        exception.getMessage().contains("No counters found for mim versions")
        where:
        neType              | modelDefiner     | invalidOssModelIdentity
        NODE_TYPE_ERBS      | ModelDefiner.NE  | "XXX-YYY-ZZZ"
        NODE_TYPE_ERBS      | ModelDefiner.OSS | "XXX-YYY-ZZZ"
        NODE_TYPE_ERBS      | ModelDefiner.NE  | null
        NODE_TYPE_RADIONODE | ModelDefiner.NE  | "XXX-YYY-ZZZ"
        NODE_TYPE_RADIONODE | ModelDefiner.OSS | "XXX-YYY-ZZZ"
        NODE_TYPE_RADIONODE | ModelDefiner.NE  | null
        NODE_TYPE_SGSN_MME  | ModelDefiner.NE  | "XXX-YYY-ZZZ"
        NODE_TYPE_SGSN_MME  | ModelDefiner.OSS | "XXX-YYY-ZZZ"
        NODE_TYPE_SGSN_MME  | ModelDefiner.NE  | null
    }

    def "get counter subgroup for subscription type mo-instance"() {
        when: "request for counters sub-groups for subscription type moinstance "
        Map<String, List<String>> counterSubGroupMap = pmCounterLookUp.getCounterSubGroups(SubscriptionType.MOINSTANCE.name())
        then: "should not throw exception and counter subgroup should not be empty"
        noExceptionThrown()
        counterSubGroupMap.keySet().size() == 2
        counterSubGroupMap.get("CPS").size() == 3
        counterSubGroupMap.get("F4/F5").size() == 8
    }

    private PmMimVersionQuery createMimQuery(String neType, String ossModelIdentity, List<String> technologyDomains) {
        PmMimVersionQuery pmMimVersionQuery = new PmMimVersionQuery()
        Set<NodeTypeAndVersion> mimVersions = new HashSet<>()
        NodeTypeAndVersion typeVersion = new NodeTypeAndVersion(neType, ossModelIdentity, technologyDomains)
        mimVersions.add(typeVersion)
        pmMimVersionQuery.setMimVersions(mimVersions)
        pmMimVersionQuery
    }

    private PmMimVersionQuery addMimQuery(PmMimVersionQuery pmMimVersionQuery, String neType, String ossModelIdentity, List<String> technologyDomains) {
        NodeTypeAndVersion typeVersion = new NodeTypeAndVersion(neType, ossModelIdentity, technologyDomains)
        pmMimVersionQuery.getMimVersions().add(typeVersion)
        pmMimVersionQuery
    }
}
