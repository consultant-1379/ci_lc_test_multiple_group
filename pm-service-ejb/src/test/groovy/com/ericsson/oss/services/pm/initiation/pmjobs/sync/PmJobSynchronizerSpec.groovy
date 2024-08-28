package com.ericsson.oss.services.pm.initiation.pmjobs.sync

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dao.PmJobDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType

class PmJobSynchronizerSpec extends SkeletonSpec {

    @ObjectUnderTest
    PmJobSynchronizer objectUnderTest

    @Inject
    SubscriptionDao subscriptionDao

    @Inject
    PmJobDao pmJobDao

    @Override
    def autoAllocateFrom() {
        def packages = super.autoAllocateFrom()
        packages.addAll(['com.ericsson.oss.pmic.dao', 'com.ericsson.oss.pmic.dto'])
        return packages
    }

    def "syncAllPmJobsInDPSForSubscription will create UNKNOWN pmJobs for subscription with nodes that do not have pmJobs"() {
        given:
        def nodeMo = nodeUtil.builder("1").neType(NetworkElementType.SGSNMME).build()
        def subMo = ueTraceSubscriptionBuilder.node(nodeMo).build()
        def sub = subscriptionDao.findOneById(subMo.getPoId(), true)
        when:
        objectUnderTest.syncAllPmJobsInDPSForSubscription(sub, [nodeMo.getFdn()] as Set)
        def result = pmJobDao.findAllBySubscriptionId(subMo.getPoId())
        then:
        result.size() == 1
        result.get(0).getStatus() == PmJobStatus.UNKNOWN
        result.get(0).getSubscriptionId() == subMo.getPoId()
        result.get(0).getNodeName() == nodeMo.getName()
    }

    def "syncAllPmJobsInDPSForSubscription will create UNKNOWN pmJobs for subscription with nodes that do not have pmJobs but will not tough others"() {
        given:
        def nodeMo = nodeUtil.builder("1").neType(NetworkElementType.SGSNMME).build()
        def nodeMo1 = nodeUtil.builder("2").neType(NetworkElementType.SGSNMME).build()
        def subMo = ueTraceSubscriptionBuilder.nodes(nodeMo, nodeMo1).build()
        pmJobBuilder.nodeName(nodeMo).processType(ProcessType.UETRACE).subscriptionId(subMo).status(PmJobStatus.ACTIVE).build()
        def sub = subscriptionDao.findOneById(subMo.getPoId(), true)
        when:
        objectUnderTest.syncAllPmJobsInDPSForSubscription(sub, [nodeMo.getFdn(), nodeMo1.getFdn()] as Set)
        def result = pmJobDao.findAllBySubscriptionId(subMo.getPoId())
        then:
        result.size() == 2
        result.get(1).getStatus() == PmJobStatus.UNKNOWN
        result.get(1).getSubscriptionId() == subMo.getPoId()
        result.get(1).getNodeName() == nodeMo1.getName()
    }

    def "syncAllPmJobsInDPSForSubscription1 will create UNKNOWN pmJobs for subscription with nodes that do not have pmJobs"() {
        given:
        def nodeMo = nodeUtil.builder("1").neType(NetworkElementType.SGSNMME).build()
        def subMo = ueTraceSubscriptionBuilder.node(nodeMo).build()
        when:
        objectUnderTest.syncAllPmJobsInDPSForSubscription(subMo.getPoId(), 900, [nodeMo.getFdn()] as Set, ProcessType.UETRACE)
        def result = pmJobDao.findAllBySubscriptionId(subMo.getPoId())
        then:
        result.size() == 1
        result.get(0).getStatus() == PmJobStatus.UNKNOWN
        result.get(0).getSubscriptionId() == subMo.getPoId()
        result.get(0).getNodeName() == nodeMo.getName()
    }

    def "syncAllPmJobsInDPSForSubscription1 will create UNKNOWN pmJobs for subscription with nodes that do not have pmJobs but will not tough others"() {
        given:
        def nodeMo = nodeUtil.builder("1").neType(NetworkElementType.SGSNMME).build()
        def nodeMo1 = nodeUtil.builder("2").neType(NetworkElementType.SGSNMME).build()
        def subMo = ueTraceSubscriptionBuilder.nodes(nodeMo, nodeMo1).build()
        pmJobBuilder.nodeName(nodeMo).processType(ProcessType.UETRACE).subscriptionId(subMo).status(PmJobStatus.ACTIVE).build()
        when:
        objectUnderTest.syncAllPmJobsInDPSForSubscription(subMo.getPoId(), 900, [nodeMo.getFdn(), nodeMo1.getFdn()] as Set, ProcessType.UETRACE)
        def result = pmJobDao.findAllBySubscriptionId(subMo.getPoId())
        then:
        result.size() == 2
        result.get(1).getStatus() == PmJobStatus.UNKNOWN
        result.get(1).getSubscriptionId() == subMo.getPoId()
        result.get(1).getNodeName() == nodeMo1.getName()
    }
}
