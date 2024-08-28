package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource

import static com.ericsson.oss.pmic.cdi.test.util.Constants.*

import javax.ws.rs.core.Response

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus
import com.ericsson.oss.pmic.dto.scanner.PmProcessReportEntry
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.initiation.common.ResponseData
import com.ericsson.oss.services.pm.initiation.enodeb.pmprocess.resource.NodeReportTableHeaderInfo
import com.ericsson.oss.services.pm.initiation.enodeb.pmprocess.resource.PmProcessReportResource

class PmProcessReportResourceSpec extends SkeletonSpec {

    @ObjectUnderTest
    PmProcessReportResource pmProcessReportResource

    def nodeNames = [SGSN_NODE_NAME_1, SGSN_NODE_NAME_2, SGSN_NODE_NAME_3]
    def nodes = []

    def "get report for node will return all scanners and pmjobs for that node with subscription and node information"() {
        given:
        def nodeMO = dps.node().name("node_name").build()
        def subscriptionMO = dps.subscription().type(SubscriptionType.STATISTICAL).build()
        def subscriptionMO1 = dps.subscription().type(SubscriptionType.CTUM).build()

        def predefStatsScannerMO = dps.scanner().
                nodeName(nodeMO).
                name("PREDEF.STATS").
                processType(ProcessType.STATS).
                status(ScannerStatus.ERROR).
                errorCode(5 as short).
                build()
        def userdefStatsScannerMO = dps.scanner().
                nodeName(nodeMO).
                name("USERDEF-Test.Cont.Y.STATS").
                processType(ProcessType.STATS).
                status(ScannerStatus.ACTIVE).
                errorCode(0 as short).
                subscriptionId(subscriptionMO).
                build()
        def celltraceScannerMO = dps.scanner().
                nodeName(nodeMO).
                name("PREDEF.10000.CELLTRACE").
                processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
                status(ScannerStatus.INACTIVE).
                errorCode(0 as short).
                build()

        def pmJobMO = dps.pmJob().
                nodeName(nodeMO).
                processType(ProcessType.CTUM).
                subscriptionId(subscriptionMO1).
                status(PmJobStatus.ACTIVE).
                build()

        and:
        def predefStatsScannerReport = new PmProcessReportEntry(predefStatsScannerMO.getPoId(), nodeMO.getFdn(), null, "ERROR", "1", "STATS",
                "0", predefStatsScannerMO.getFdn(), predefStatsScannerMO.getName(), 5 as short,
                null, true)
        def userdefStatsScannerReport = new PmProcessReportEntry(userdefStatsScannerMO.getPoId(), nodeMO.getFdn(), "Test", "ACTIVE", "1", "STATS",
                subscriptionMO.getPoId() as String, userdefStatsScannerMO.getFdn(),
                userdefStatsScannerMO.getName(), 0 as short,
                "STATISTICAL", false)
        def celltraceScannerMOReport = new PmProcessReportEntry(celltraceScannerMO.getPoId(), nodeMO.getFdn(), null, "INACTIVE", "1", "CELLTRACE",
                "0", celltraceScannerMO.getFdn(), celltraceScannerMO.getName(), 0 as short,
                null, false)
        def pmJobMOReport = new PmProcessReportEntry(pmJobMO.getPoId(), nodeMO.getFdn(), "Test", "ACTIVE", "1000", "CTUM",
                subscriptionMO1.getPoId() as String, pmJobMO.getFdn(), pmJobMO.getName(), 0 as short,
                "CTUM", false)

        when:
        List<PmProcessReportEntry> reportList = pmProcessReportResource.getPmProcessInfoForNodes("ne", nodeMO.getPoId() as String).
                getEntity() as List<PmProcessReportEntry>
        then:
        reportList == [predefStatsScannerReport, userdefStatsScannerReport, celltraceScannerMOReport, pmJobMOReport]
    }

    def "get report for subscription will return all scanners and pmjobs for that subscription  with subscription and nodes information"() {
        given:
        def nodeMO = dps.node().name("node_name").build()
        def subscriptionMO = dps.subscription().type(SubscriptionType.STATISTICAL).build()
        def subscriptionMO1 = dps.subscription().type(SubscriptionType.CTUM).build()

        dps.scanner().
                nodeName(nodeMO).
                name("PREDEF.STATS").
                processType(ProcessType.STATS).
                status(ScannerStatus.ERROR).
                errorCode(5 as short).
                build()
        def userdefStatsScannerMO = dps.scanner().
                nodeName(nodeMO).
                name("USERDEF-Test.Cont.Y.STATS").
                processType(ProcessType.STATS).
                status(ScannerStatus.ACTIVE).
                errorCode(0 as short).
                subscriptionId(subscriptionMO).
                build()
        dps.scanner().
                nodeName(nodeMO).
                name("PREDEF.10000.CELLTRACE").
                processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
                status(ScannerStatus.INACTIVE).
                errorCode(0 as short).
                build()

        dps.pmJob().
                nodeName(nodeMO).
                processType(ProcessType.CTUM).
                subscriptionId(subscriptionMO1).
                status(PmJobStatus.ACTIVE).
                build()

        and:
        def userdefStatsScannerReport = new PmProcessReportEntry(userdefStatsScannerMO.getPoId(), nodeMO.getFdn(), "Test", "ACTIVE", "1", "STATS",
                subscriptionMO.getPoId() as String, userdefStatsScannerMO.getFdn(),
                userdefStatsScannerMO.getName(), 0 as short,
                "STATISTICAL", false)
        when:
        List<PmProcessReportEntry> reportList = pmProcessReportResource.getPmProcessInfoForNodes("sub", subscriptionMO.getPoId() as String).
                getEntity() as List<PmProcessReportEntry>
        then:
        reportList == [userdefStatsScannerReport]
    }

    def "get report for all nodes will return all scanners and pmjobs with error"() {
        given:
        def nodeMO = dps.node().name("node_name").build()
        def subscriptionMO = dps.subscription().type(SubscriptionType.STATISTICAL).build()
        def subscriptionMO1 = dps.subscription().type(SubscriptionType.CTUM).build()

        def predefStatsScannerMO = dps.scanner().
                nodeName(nodeMO).
                name("PREDEF.STATS").
                processType(ProcessType.STATS).
                status(ScannerStatus.ERROR).
                errorCode(5 as short).
                build()
        dps.scanner().
                nodeName(nodeMO).
                name("USERDEF-Test.Cont.Y.STATS").
                processType(ProcessType.STATS).
                status(ScannerStatus.ACTIVE).
                errorCode(0 as short).
                subscriptionId(subscriptionMO).
                build()
        dps.scanner().
                nodeName(nodeMO).
                name("PREDEF.10000.CELLTRACE").
                processType(ProcessType.NORMAL_PRIORITY_CELLTRACE).
                status(ScannerStatus.INACTIVE).
                errorCode(0 as short).
                build()

        dps.pmJob().
                nodeName(nodeMO).
                processType(ProcessType.CTUM).
                subscriptionId(subscriptionMO1).
                status(PmJobStatus.ACTIVE).
                build()

        and:
        def predefStatsScannerReport = new PmProcessReportEntry(predefStatsScannerMO.getPoId(), nodeMO.getFdn(), null, "ERROR", "1", "STATS",
                "0", predefStatsScannerMO.getFdn(), predefStatsScannerMO.getName(), 5 as short,
                null, true)
        when:
        List<PmProcessReportEntry> reportList = pmProcessReportResource.getPmProcessInfoForNodes("list", null).
                getEntity() as List<PmProcessReportEntry>
        then:
        reportList == [predefStatsScannerReport]
    }

    def "get report for all nodes will return NO_CONTENT if no scanners/pmjobs exist"() {
        when:
        ResponseData responseData = pmProcessReportResource.getPmProcessInfoForNodes("list", null).
                getEntity() as ResponseData
        then:
        responseData.getCode() == Response.Status.NO_CONTENT
        responseData.getError() == "There were no scanners/pmjos found"
    }

    def "get report for subscription will return NO_CONTENT if subscription does not exist exist"() {
        when:
        ResponseData responseData = pmProcessReportResource.getPmProcessInfoForNodes("sub", "123").
                getEntity() as ResponseData
        then:
        responseData.getCode() == Response.Status.NO_CONTENT
        responseData.getError() == "There were no scanners/pmjos found"
    }

    def "get report for node will return NOT_FOUND if node does not exist exist"() {
        when:
        ResponseData responseData = pmProcessReportResource.getPmProcessInfoForNodes("ne", "123").
                getEntity() as ResponseData
        then:
        responseData.getCode() == Response.Status.NOT_FOUND
        responseData.getError() == "Node with id [123] does not exist!"
    }

    def "get report will return BAD_REQUEST poid is not valid"() {
        when:
        ResponseData responseData = pmProcessReportResource.getPmProcessInfoForNodes("ne", "aaa").
                getEntity() as ResponseData
        then:
        responseData.getCode() == Response.Status.BAD_REQUEST
    }

    def "get report will return BAD_REQUEST type is not valid"() {
        when:
        ResponseData responseData = pmProcessReportResource.getPmProcessInfoForNodes(null, "123").
                getEntity() as ResponseData
        then:
        responseData.getCode() == Response.Status.BAD_REQUEST
    }

    def "get response will return the NodeReportTableHeaderInfo containing corbaErrors, processType, processStatus"() {
        given:
        def nodeReportTableHeaderInfo = new NodeReportTableHeaderInfo()
        when:
        def response = pmProcessReportResource.getNodeProcessHeaders().getEntity() as NodeReportTableHeaderInfo
        then:
        response.getProcessTypeList() == nodeReportTableHeaderInfo.getProcessTypeList()
        response.getProcessStatusList() == nodeReportTableHeaderInfo.getProcessStatusList()
        response.getNodeScannerErrors().size() == nodeReportTableHeaderInfo.getNodeScannerErrors().size()
    }
}
