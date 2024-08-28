package com.ericsson.oss.services.pm.initiation.task.factories.activation

import static com.ericsson.oss.pmic.cdi.test.util.Constants.EBS_CELLTRACE_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_1

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.services.pm.exception.DataAccessException
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.generic.ScannerService

import spock.lang.Unroll

class CellTraceSubscriptionActivationTaskRequestFactoryExceptionSpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    CellTraceSubscriptionActivationTaskRequestFactory cellTraceSubscriptionActivationTaskRequestFactory

    @MockedImplementation
    ScannerService scannerService

    ManagedObject subscriptionMO
    ManagedObject nodeMO1
    CellTraceSubscription subscription

    def ebsEvents = [new EventInfo("Ebs Event", "Ebs Event Group")]

    @Unroll
    def 'Should not throw an exception if exception thrown by scanner service service'(){
        given: 'A subscription with a node with ebsEvents and one high priority ebsScanner'
            createNode()
            createCellTraceSubscription([nodeMO1] as ManagedObject[], [], true, ebsEvents)
            scannerUtil.builder(EBS_CELLTRACE_SCANNER, NODE_NAME_1).status(ScannerStatus.INACTIVE).subscriptionId("0").processType(ProcessType.HIGH_PRIORITY_CELLTRACE).build()
            scannerService.findAllByNodeFdnAndProcessTypeInReadTx(_, _) >> {throw new DataAccessException('')}

        when: 'scanner service is requested to find the ebs scanner'
            cellTraceSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(subscription.getNodes(),subscription,true);

        then: 'No exception is thrown'
            noExceptionThrown()

    }

    def createCellTraceSubscription(ManagedObject[] nodes, List<EventInfo> events = [], boolean streamClusterDeployed = false, List<EventInfo> ebsEvents = [], boolean isNran = false) {
        subscriptionMO = cellTraceSubscriptionBuilder.outputMode(OutputModeType.FILE.name()).name("AthloneArea").administrativeState(AdministrationState.ACTIVATING).build()
        dpsUtils.addAssociation(subscriptionMO, "nodes", nodes)
        subscription = subscriptionDao.findOneById(subscriptionMO.getPoId(), true) as CellTraceSubscription
        subscription.setEvents(events)
        subscription.setEbsEvents(ebsEvents)
        if (streamClusterDeployed) {
            if (ebsEvents.isEmpty()) {
                subscription.setCellTraceCategory(CellTraceCategory.CELLTRACE_AND_EBSL_FILE)
            } else {
                subscription.setCellTraceCategory(CellTraceCategory.CELLTRACE_AND_EBSL_STREAM)
            }
        } else if (isNran) {
            subscription.setCellTraceCategory(CellTraceCategory.CELLTRACE_NRAN)
        } else {
            subscription.setCellTraceCategory(CellTraceCategory.CELLTRACE)
        }
    }

    def createNode(NeConfigurationManagerState neConfigurationManagerState = NeConfigurationManagerState.ENABLED) {
        nodeMO1 = nodeUtil.builder(NODE_NAME_1).neConfigurationManagerState(neConfigurationManagerState).build()
    }
}
