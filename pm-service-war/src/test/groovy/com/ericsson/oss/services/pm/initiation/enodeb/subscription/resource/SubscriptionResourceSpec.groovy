package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource

import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.oss.pmic.impl.modelservice.PmCapabilityReaderImpl

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.INTERNAL_SERVER_EXCEPTION

import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.PaginatedList
import com.ericsson.oss.pmic.dto.scanner.Scanner
import com.ericsson.oss.pmic.dto.scanner.ScannerUe
import com.ericsson.oss.pmic.dto.subscription.*
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.NodeInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.ScheduleInfo
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo
import com.ericsson.oss.pmic.dto.subscription.enums.*
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper
import com.ericsson.oss.services.pm.initiation.common.ResponseData
import com.ericsson.oss.services.pm.initiation.model.metadata.PMICModelDeploymentValidator

class SubscriptionResourceSpec extends SkeletonSpec {

    @ObjectUnderTest
    SubscriptionResource subscriptionResource

    @MockedImplementation
    PmFunctionEnabledWrapper mockedPmFunctionEnabledWrapper

    @MockedImplementation
    PMICModelDeploymentValidator MockedPMICModelDeploymentValidator

    @ImplementationClasses
    def classes =  [PmCapabilityReaderImpl]


    def "when hasNodesAndCellsConflict is called for CellTraficSubscription with CellInfo on one node"() {
        given:
        def node = nodeUtil.builder("Node1").build()
        def subMO = cellTrafficSubscriptionBuilder.name("Test").addNode(node).setAdditionalAttributes([cellInfoList: [[nodeName: "Node1", utranCellId: "111"]]]).build()

        when:
        def result = subscriptionResource.hasNodesAndCellsConflict(subMO.getPoId() as String)
        then:
        result.getEntity() == false
    }

    def "when hasNodesAndCellsConflict is called for CellTraficSubscription with two cells on same node"() {
        given:
        def node = nodeUtil.builder("Node1").build()
        def subMO = cellTrafficSubscriptionBuilder.name("Test").addNode(node).setAdditionalAttributes([cellInfoList: [[nodeName: "Node1", utranCellId: "111"], [nodeName: "Node1", utranCellId: "112"]]]).build()

        when:
        def result = subscriptionResource.hasNodesAndCellsConflict(subMO.getPoId() as String)
        then:
        result.getEntity() == false
    }

    def "when hasNodesAndCellsConflict is called for CellTraficSubscription with extra CellInfo on different node"() {
        given:
        def node = nodeUtil.builder("Node1").build()
        def subMO = cellTrafficSubscriptionBuilder.name("Test").addNode(node).setAdditionalAttributes([cellInfoList: [[nodeName: "Node1", utranCellId: "111"], [nodeName: "Node2", utranCellId: "111"]]]).build()

        when:
        def result = subscriptionResource.hasNodesAndCellsConflict(subMO.getPoId() as String)
        then:
        result.getEntity() == true
    }

    def "when hasNodesAndCellsConflict is called for CellTraficSubscription with extra node and different CellInfo"() {
        given:
        def node = nodeUtil.builder("Node1").build()
        def node2 = nodeUtil.builder("Node2").build()
        def subMO = cellTrafficSubscriptionBuilder.name("Test").addNode(node).addNode(node2).setAdditionalAttributes([cellInfoList: [[nodeName: "Node1", utranCellId: "111"], [nodeName: "Node1", utranCellId: "111"]]]).build()

        when:
        def result = subscriptionResource.hasNodesAndCellsConflict(subMO.getPoId() as String)
        then:
        result.getEntity() == true
    }

    def "when hasNodesAndCellsConflict is called for GpehSubscription with CellInfo on one node with no CellsSupported or applyOnAllCells "() {
        given:
        def node = nodeUtil.builder("Node1").build()
        def subMO = gpehSubscriptionBuilder.name("Test").addNode(node).setAdditionalAttributes([cellInfoList: [[nodeName: "Node1", utranCellId: "111"]], applyOnAllCells: false, cellsSupported: false]).build()

        when:
        def result = subscriptionResource.hasNodesAndCellsConflict(subMO.getPoId() as String)
        then:
        result.getEntity() == false
    }

    def "when hasNodesAndCellsConflict is called for GpehSubscription with CellInfo on one node with no cellsSupported "() {
        given:
        def node = nodeUtil.builder("Node1").build()
        def subMO = gpehSubscriptionBuilder.name("Test").addNode(node).setAdditionalAttributes([cellInfoList: [[nodeName: "Node1", utranCellId: "111"]], applyOnAllCells: true, cellsSupported: false]).build()

        when:
        def result = subscriptionResource.hasNodesAndCellsConflict(subMO.getPoId() as String)
        then:
        result.getEntity() == false
    }

    def "when hasNodesAndCellsConflict is called for GpehSubscription with CellInfo on one node"() {
        given:
        def node = nodeUtil.builder("Node1").build()
        def subMO = gpehSubscriptionBuilder.name("Test").addNode(node).setAdditionalAttributes([cells: [[nodeName: "Node1", utranCellId: "111"]], applyOnAllCells: false, cellsSupported: true]).build()

        when:
        def result = subscriptionResource.hasNodesAndCellsConflict(subMO.getPoId() as String)
        then:
        result.getEntity() == false
    }

    def "when hasNodesAndCellsConflict is called for GpehSubscription with two cells on same node "() {
        given:
        def node = nodeUtil.builder("Node1").build()
        def subMO = gpehSubscriptionBuilder.name("Test").addNode(node).setAdditionalAttributes([cells: [[nodeName: "Node1", utranCellId: "111"], [nodeName: "Node1", utranCellId: "112"]], applyOnAllCells: false, cellsSupported: true]).build()

        when:
        def result = subscriptionResource.hasNodesAndCellsConflict(subMO.getPoId() as String)
        then:
        result.getEntity() == false
    }

    def "when hasNodesAndCellsConflict is called for GpehSubscription with with extra CellInfo on different node "() {
        given:
        def node = nodeUtil.builder("Node1").build()
        def subMO = gpehSubscriptionBuilder.name("Test").addNode(node).setAdditionalAttributes([cells: [[nodeName: "Node1", utranCellId: "111"], [nodeName: "Node2", utranCellId: "112"]], applyOnAllCells: false, cellsSupported: true]).build()

        when:
        def result = subscriptionResource.hasNodesAndCellsConflict(subMO.getPoId() as String)
        then:
        result.getEntity() == true
    }

    def "when hasNodesAndCellsConflict is called for GpehSubscription with with extra node and different CellInfo"() {
        given:
        def node = nodeUtil.builder("Node1").build()
        def node2 = nodeUtil.builder("Node2").build()
        def subMO = gpehSubscriptionBuilder.name("Test").addNode(node).addNode(node2).setAdditionalAttributes([cells: [[nodeName: "Node1", utranCellId: "111"], [nodeName: "Node1", utranCellId: "112"]], applyOnAllCells: false, cellsSupported: true]).build()

        when:
        def result = subscriptionResource.hasNodesAndCellsConflict(subMO.getPoId() as String)
        then:
        result.getEntity() == true
    }

    def "when hasNodesAndCellsConflict is called for GpehSubscription with with extra node and same CellInfo"() {
        given:
        def node = nodeUtil.builder("Node1").build()
        def node2 = nodeUtil.builder("Node2").build()
        def subMO = gpehSubscriptionBuilder.name("Test").addNode(node).addNode(node2).setAdditionalAttributes([cells: [[nodeName: "Node1", utranCellId: "111"], [nodeName: "Node1", utranCellId: "111"]], applyOnAllCells: false, cellsSupported: true]).build()

        when:
        def result = subscriptionResource.hasNodesAndCellsConflict(subMO.getPoId() as String)
        then:
        result.getEntity() == true
    }

    def "when hasNodesAndCellsConflict is called for StatisticalSubscription"() {
        given:
        nodeUtil.builder("Node1").build()
        def subMO = statisticalSubscriptionBuilder.name("Test").build()

        when:
        def result = subscriptionResource.hasNodesAndCellsConflict(subMO.getPoId() as String)
        then:
        result.getEntity() == true
    }

    @Unroll
    def "when hasNodesAndCellsConflict is called with invalid subscription, exception will be thrown"() {
        when:
        subscriptionResource.hasNodesAndCellsConflict(input)
        then:
        thrown(IllegalArgumentException)
        where:
        input << ["0", "", "-1"]
    }

    def "when hasNodesAndCellsConflict is called for subscription that does not exist, 404 will be returned"() {
        when:
        def result = subscriptionResource.hasNodesAndCellsConflict("123")
        then:
        result.getStatus() == 404
    }

    def "getSubscriptionIdByName will return subId"() {
        given:
        def subMO = statisticalSubscriptionBuilder.name("Hello").build()
        when:
        def result = subscriptionResource.getSubscriptionIdByName("Hello")
        then:
        result.getEntity() == subMO.getPoId() as String
        result.getStatus() == 200
    }

    def "getSubscriptionIdByName will return 0"() {
        when:
        def result = subscriptionResource.getSubscriptionIdByName("Hello")
        then:
        result.getStatus() == 200
        result.getEntity() == "0"
    }

    @Unroll
    def "getNodesFromSubscription should return_a paginated list when the subscription exists"() {
        given:
        def nodeMo = nodeUtil.builder("1").build()
        def nodeMo1 = nodeUtil.builder("2").build()
        def subMo = statisticalSubscriptionBuilder.addNode(nodeMo).addNode(nodeMo1).build()
        when:
        def result = subscriptionResource.getNodesFromSubscription(subMo.getPoId() as String, page, pageSize)
        then:
        (result.getEntity() as PaginatedList).getItems().size() == items
        (result.getEntity() as PaginatedList).getPage() == page
        (result.getEntity() as PaginatedList).getPageSize() == pageSize
        where:
        items << [1, 2, 1]
        page << [1, 1, 2]
        pageSize << [1, 10, 1]
    }

    def "getNodesFromSubscription should return a 404 status when subscription is null or not an instance of a resource subscription"() {
        given:
        def subMo = ueTraceSubscriptionBuilder.build()
        when:
        def result = subscriptionResource.getNodesFromSubscription(subMo.getPoId() as String, 1, 10)
        then:
        result.getStatus() == 404
    }

    @Unroll
    def "getInvalidFeatureStateNodes should return #returnDescription when Node1 has uertt feature state #featureStatusNode1 and Node2 has uertt feature state #featureStatusNode2"() {
        given:
        nodeUtil.builder("Node1").setUerttFeatureMorequired(featureMoRequired).setUerttFeatureState(featureStatusNode1).build()
        nodeUtil.builder("Node2").setUerttFeatureMorequired(featureMoRequired).setUerttFeatureState(featureStatusNode2).build()
        when:
        def result = subscriptionResource.getInvalidFeatureStateNodes("UETR", "NetworkElement=Node1,NetworkElement=Node2")
        then:
        ((List<String>) (result.entity)).size() == resultListSize;
        where:
        featureStatusNode1 | featureStatusNode2 | returnDescription | featureMoRequired | resultListSize
        "ACTIVATED"        | "ACTIVATED"        | " empty list "    | true              | 0
        "ACTIVATED"        | "DEACTIVATED"      | " list of nodes"  | true              | 1
        "DEACTIVATED"      | "DEACTIVATED"      | " list of nodes"  | true              | 2
        "NOT AVAILABLE"    | "NOT AVAILABLE"    | " list of nodes"  | false             | 2
    }

    @Unroll
    def "getIpConflictStatus should return #output when #description"() {
        given: "node and scanner object created in dps"
        def scannerName = "PREDEF.10016.UETR"
        def node1 = "Node1"
        def node2 = "Node2"
        def subId = "123458584"
        def processType = "UETR"
        nodeUtil.builder(node1).build()
        nodeUtil.builder(node2).build()
        def scannerAtributes1 = [name                 : scannerName,
                                 nodeName             : node1,
                                 status               : "ACTIVE",
                                 processType          : processType,
                                 fileCollectionEnabled: false,
                                 ropPeriod            : 900,
                                 subscriptionId       : subId,
                                 streamInfo           : streamInfoNode1] as Map<String, Object>
        def scannerAtributes2 = [name                 : scannerName,
                                 nodeName             : node2,
                                 status               : "ACTIVE",
                                 processType          : processType,
                                 fileCollectionEnabled: false,
                                 ropPeriod            : 900,
                                 subscriptionId       : subId,
                                 streamInfo           : streamInfoNode2] as Map<String, Object>
        createScannerInDps(node1, scannerAtributes1, scannerName)
        createScannerInDps(node2, scannerAtributes2, scannerName)
        when: "getIpConflictStatus method is called with two node fdn as argument"
        def result = subscriptionResource.getIpConflictStatus("NetworkElement=Node1,NetworkElement=Node2")
        then:
        result.entity == output;
        where:
        description                                             | streamInfoNode1                                 | streamInfoNode2                                 | output
        "StreamInfo is available in scanners for both nodes"    | [ipAddress: "1.2.3.4", port: 12, portOffset: 0] | [ipAddress: "1.2.3.4", port: 12, portOffset: 0] | true
        "StreamInfo is available for scanner in only one node " | [ipAddress: "1.2.3.4", port: 12, portOffset: 0] | null                                            | false
        "StreamInfo is not available in scanners in both nodes" | null                                            | null                                            | false
    }

    def "getSubscriptionFilteredList will return correctly filtered subscriptions"() {
        given:
        def nodeMO = nodeUtil.builder("1").build()
        statisticalSubscriptionBuilder
                .name("Subscription Name")
                .scheduleInfo(new Date(), new Date())
                .description("Donald Duck")
                .addNode(nodeMO)
                .addCounter(new CounterInfo("Name", "MoClass"))
                .build()
        cellTraceSubscriptionBuilder
                .cellTraceCategory(CellTraceCategory.CELLTRACE_AND_EBSL_STREAM.name())
                .name("Subscription Name")
                .scheduleInfo(new Date(), new Date())
                .description("Donald Duck")
                .addNode(nodeMO)
                .build()
        ueTraceSubscriptionBuilder
                .traceReference("ABC")
                .ueInfo(new UeInfo(UeType.IMEI, "123"))
                .nodeInfoList([new NodeInfo(NodeGrouping.ENODEB, TraceDepth.MAXIMUM, [])])
                .name("Subscription Name")
                .scheduleInfo(new Date(), new Date())
                .description("Donald Duck")
                .build()
        when:
        def result = subscriptionResource.getSubscriptionFilteredList(null, null, null)
        then:
        result.getEntity().each { it ->
            def sub = it as Subscription
            if (sub.getType() == SubscriptionType.STATISTICAL) {
                assert ((StatisticalSubscription) sub).getCounters().size() == 0
                assert ((StatisticalSubscription) sub).getNumberOfNodes() == 1
            } else if (sub.getType() == SubscriptionType.CELLTRACE) {
                assert ((CellTraceSubscription) sub).getCellTraceCategory() == CellTraceCategory.CELLTRACE_AND_EBSL_STREAM
                assert ((CellTraceSubscription) sub).getNumberOfNodes() == 1
            } else {
                assert ((UETraceSubscription) sub).getTraceReference() == "ABC"
                assert ((UETraceSubscription) sub).getUeInfo() == null
                assert ((UETraceSubscription) sub).getNodeInfoList() == []
            }
            assert sub.getName() == "Subscription Name"
            assert sub.getDescription() == "Donald Duck"
            assert sub.getScheduleInfo() == new ScheduleInfo()
        }
    }

    @Unroll
    def "getSubscriptionFilteredListWithManagedElementFDNs will return EBM subscription even if names or statuses are null, empty or contain null or empty strings"() {
        given:
        ebmSubscriptionBuilder.name("Sub1").build()
        when:
        def result = subscriptionResource.getSubscriptionFilteredListWithManagedElementFDNs(names, type, statuses)
        then:
        noExceptionThrown()
        (result.getEntity() as List).size() == 1
        where:
        names      | type  | statuses
        [""]       | "EBM" | ["ACTIVE", "INACTIVE"]
        []         | "EBM" | ["ACTIVE", "INACTIVE"]
        ["", null] | "EBM" | ["ACTIVE", "INACTIVE"]
        null       | "EBM" | ["ACTIVE", "INACTIVE"]
        [null]     | "EBM" | ["ACTIVE", "INACTIVE"]
        ["Sub1"]   | "EBM" | []
        ["Sub1"]   | "EBM" | [""]
        ["Sub1"]   | "EBM" | [null]
        ["Sub1"]   | "EBM" | ["", null]
        ["Sub1"]   | "EBM" | null
        [""]       | "EBM" | []
        []         | "EBM" | [""]
        ["", null] | "EBM" | [null]
        null       | "EBM" | ["", null]
        [null]     | "EBM" | null
    }

    def createScannerInDps(def nodeName, Map<String, Object> attributes, String scannerName) {
        configurableDps.addManagedObject()
                .withFdn(String.format("NetworkElement=%s,PMICUeScannerInfo=%s", nodeName, scannerName))
                .name(scannerName)
                .namespace(Scanner.SCANNER_MODEL_NAMESPACE)
                .version(Scanner.SCANNER_MODEL_VERSION)
                .type(ScannerUe.SCANNER_MODEL_TYPE)
                .addAttributes(attributes)
                .build()
    }

    def "get will retrieve nodes with populated PmFunction and selected neTypes "() {
        given:
        def nodeMO = nodeUtil.builder("1").build()
        ManagedObject sub = statisticalSubscriptionBuilder
                .name("Subscription Name")
                .scheduleInfo(new Date(), new Date())
                .description("Donald Duck")
                .addNode(nodeMO)
                .addCounter(new CounterInfo("Name", "MoClass"))
                .build()
        and:
        mockedPmFunctionEnabledWrapper.isPmFunctionEnabled(_) >> pmEnabled
        when:
        def result = subscriptionResource.get(String.valueOf(sub.getPoId()))
        then:
        ((StatisticalSubscription) result.getEntity()).getNodes()[0].pmFunction == pmEnabled
        ((StatisticalSubscription) result.getEntity()).getSelectedNeTypes()[0] == selectedNetypes
        where:
        pmEnabled | selectedNetypes
        true      | "ERBS"
        false     | "ERBS"
    }

    @Unroll
    def "getAvailableNodes will return correct list of available Nodes depending on adminState"() {
        given: "nodes and subscription in dps"
        def nodeMO1 = nodeUtil.builder("1").build()
        def nodeMO2 = nodeUtil.builder("2").build()

        dps.subscription().type(ResSubscription.class)
                .name("Subscription Name 2")
                .administrationState(adminState)
                .scheduleInfo(new ScheduleInfo(new Date(), new Date()))
                .description("Uncle Scrooge")
                .nodes(Arrays.asList(nodeMO1, nodeMO2))
                .build()

        when: "invoking getAvailableNodes for a creating subscription"
        def result = subscriptionResource.getAvailableNodes("RES", "0", [nodeMO1.getFdn(), nodeMO2.getFdn()] as Set<String>)
        then: "correct list of available nodes is returned"
        ((Set<String>) result.getEntity()).size() == ExpectedResult

        where:
        adminState                       | ExpectedResult
        AdministrationState.ACTIVE       | 0
        AdministrationState.ACTIVATING   | 0
        AdministrationState.DEACTIVATING | 0
        AdministrationState.INACTIVE     | 2

    }

    def "getAvailableNodes will not filter nodes from subscription itself"() {
        given: "nodes and active subscription in dps"
        def nodeMO1 = nodeUtil.builder("1").build()
        def nodeMO2 = nodeUtil.builder("2").build()
        ManagedObject sub1 = dps.subscription().type(ResSubscription.class)
                .name("Subscription Name 1")
                .administrationState(AdministrationState.ACTIVE)
                .scheduleInfo(new ScheduleInfo(new Date(), new Date()))
                .description("Uncle Scrooge")
                .nodes(Arrays.asList(nodeMO1, nodeMO2))
                .build()
        when: "invoking getAvailableNodes for the subscription itself"
        def result = subscriptionResource.getAvailableNodes("RES", String.valueOf(sub1.getPoId()), [nodeMO1.getFdn(), nodeMO2.getFdn()] as Set<String>)
        then: "nodes are returned as available"
        ((Set<String>) result.getEntity()).size() == 2
    }

    def "getAvailableNodes will return list size 1 if 1 nodes already in another Active RES subscription"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").build()
        def nodeMO2 = nodeUtil.builder("2").build()

        dps.subscription().type(ResSubscription.class)
                .name("Subscription Name 2")
                .administrationState(AdministrationState.ACTIVE)
                .scheduleInfo(new ScheduleInfo(new Date(), new Date()))
                .description("Uncle Scrooge")
                .nodes(Arrays.asList(nodeMO1))
                .build()


        when:
        def result = subscriptionResource.getAvailableNodes("RES", "0", [nodeMO1.getFdn(), nodeMO2.getFdn()] as Set<String>)
        then:
        ((Set<String>) result.getEntity()).size() == 1
    }

    def "getAvailableNodes will return invalid input in request if subscriptionType doesn't exist"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").build()
        def nodeMO2 = nodeUtil.builder("2").build()

        dps.subscription().type(ResSubscription.class)
                .name("Subscription Name 2")
                .scheduleInfo(new ScheduleInfo(new Date(), new Date()))
                .description("Uncle Scrooge")
                .nodes(Arrays.asList(nodeMO1, nodeMO2))
                .build()
        when:
        def result = subscriptionResource.getAvailableNodes("NORES", "0", [nodeMO1.getFdn(), nodeMO2.getFdn()] as Set<String>)
        then:
        ((ResponseData) result.getEntity()).getError() == INTERNAL_SERVER_EXCEPTION.getMessage()
    }

    @Unroll
    def "get Subscription will return correct pmFunction values for available Nodes"() {
        given: "nodes and subscription in dps"
        def nodeMO1 = nodeUtil.builder("1").build()
        def nodeMO2 = nodeUtil.builder("2").build()

        ManagedObject sub2 = dps.subscription().type(SubscriptionType.STATISTICAL)
                .name("Subscription Name 2")
                .administrationState(adminState)
                .scheduleInfo(new ScheduleInfo(new Date(), new Date()))
                .description("Test Sub")
                .nodes(Arrays.asList(nodeMO1, nodeMO2))
                .build()

        when: "invoking getAvailableNodes for a creating subscription"
        def result = subscriptionResource.get(String.valueOf(sub2.getPoId())).entity
        then: "correct list of available nodes is returned"
        result.getNodes().each {
            it.getPmFunction() != null
            it.getPmFunction() != "N/A"
        }

        where:
        adminState                       | ExpectedResult
        AdministrationState.ACTIVE       | 0
        AdministrationState.ACTIVATING   | 0
        AdministrationState.DEACTIVATING | 0
        AdministrationState.INACTIVE     | 2

    }

}