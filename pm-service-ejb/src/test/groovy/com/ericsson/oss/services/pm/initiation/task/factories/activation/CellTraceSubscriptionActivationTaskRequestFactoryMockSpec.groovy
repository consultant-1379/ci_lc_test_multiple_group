package com.ericsson.oss.services.pm.initiation.task.factories.activation

import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_10000_CELLTRACE_SCANNER

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.ScannerDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.scanner.Scanner
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.pm.exception.DataAccessException
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache

class CellTraceSubscriptionActivationTaskRequestFactoryMockSpec extends SkeletonSpec {

    @ObjectUnderTest
    CellTraceSubscriptionActivationTaskRequestFactory cellTraceSubscriptionActivationTaskRequestFactory

    @ImplementationInstance
    PMICInitiationTrackerCache pmicInitiationTrackerCache = Mock(PMICInitiationTrackerCache)

    @Inject
    NodeDao nodeDao

    @Inject
    SubscriptionDao subscriptionDao

    @ImplementationInstance
    ScannerDao scannerDao = Mock(ScannerDao)

    @Override
    def autoAllocateFrom() {
        def packages = super.autoAllocateFrom()
        packages.addAll(['com.ericsson.oss.pmic.dao', 'com.ericsson.oss.pmic.dto'])
        return packages
    }

    def "will update subscription task status to error if an exception is thrown from dps, but will create tasks for nodes that did not throw exception"() {
        given:
        def nodeMO1 = nodeUtil.builder("1").build()
        def nodeMO2 = nodeUtil.builder("2").build()
        def nodeMO3 = nodeUtil.builder("3").build()
        def subMo = cellTraceSubscriptionBuilder.name("Test").addEvent("a", "b").nodes(nodeMO1, nodeMO2, nodeMO3).administrativeState(AdministrationState.ACTIVATING).build()

        def nodes = nodeDao.findAll()
        def sub = subscriptionDao.findOneById(subMo.getPoId()) as CellTraceSubscription

        def scanner1 = new Scanner()
        scanner1.setSubscriptionId(sub.getId())
        scanner1.setStatus(ScannerStatus.ACTIVE)
        scanner1.setName(PREDEF_10000_CELLTRACE_SCANNER)
        scanner1.setNodeName("1")
        scanner1.setProcessType(ProcessType.NORMAL_PRIORITY_CELLTRACE)

        def scanner2 = new Scanner()
        scanner2.setSubscriptionId(sub.getId())
        scanner2.setStatus(ScannerStatus.ACTIVE)
        scanner2.setName(PREDEF_10000_CELLTRACE_SCANNER)
        scanner2.setNodeName("3")
        scanner2.setProcessType(ProcessType.NORMAL_PRIORITY_CELLTRACE)
        scannerDao.findAllByNodeFdnAndProcessType(Collections.singleton(nodeMO1.getFdn()), ProcessType.NORMAL_PRIORITY_CELLTRACE) >> [scanner1]
        scannerDao.findAllByNodeFdnAndProcessType(Collections.singleton(nodeMO2.getFdn()), ProcessType.NORMAL_PRIORITY_CELLTRACE) >> {
            throw new DataAccessException("SomeMessage")
        }
        scannerDao.findAllByNodeFdnAndProcessType(Collections.singleton(nodeMO3.getFdn()), ProcessType.NORMAL_PRIORITY_CELLTRACE) >> [scanner2]
        when:
        def tasks = cellTraceSubscriptionActivationTaskRequestFactory.createMediationTaskRequests(nodes, sub, true)
        then: "tasks will be created for the nodes that did not throw exception"
        tasks.size() == 2
        subMo.getAttribute("taskStatus") == TaskStatus.ERROR.name()
        1 * pmicInitiationTrackerCache.startTrackingActivation(sub.getIdAsString(), AdministrationState.ACTIVATING.name(), [(nodeMO1.getFdn()): 'ERBS', (nodeMO3.getFdn()): 'ERBS'], [(nodeMO1.getFdn()): 1, (nodeMO3.getFdn()): 1]);
    }

}
