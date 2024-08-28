package com.ericsson.oss.services.pm.initiation.task.factories.deactivation

import static com.ericsson.oss.pmic.cdi.test.util.Constants.EBS_CELLTRACE_SCANNER
import static com.ericsson.oss.pmic.cdi.test.util.Constants.PREDEF_10000_CELLTRACE_SCANNER

import javax.inject.Inject

import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.dao.NodeDao
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState
import com.ericsson.oss.services.pm.PmServiceEjbSkeletonSpec
import com.ericsson.oss.services.pm.generic.PmSubScannerService
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionDeactivationTaskRequest
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionSelectiveDeactivationTaskRequest

class CelltraceSubscriptionDeactivationTaskRequestFactorySpec extends PmServiceEjbSkeletonSpec {

    @ObjectUnderTest
    CellTraceSubscriptionDeactivationTaskRequestFactory objectUnderTest

    @ImplementationInstance
    PMICInitiationTrackerCache pmicInitiationTrackerCache = Mock(PMICInitiationTrackerCache)

    @Inject
    NodeDao nodeDao

    @Inject
    SubscriptionDao subscriptionDao

    @Inject
    PmSubScannerService pmSubScannerService

    @Override
    def autoAllocateFrom() {
        def packages = super.autoAllocateFrom()
        packages.addAll(['com.ericsson.oss.pmic.dao', 'com.ericsson.oss.pmic.dto'])
        return packages
    }

    def event = new EventInfo('groupEvent', 'event')
    def ebsEvent = new EventInfo('ebsGroupEvent', 'ebsEvent')

    def 'Will create tasks for all nodes if each node has scanner with subscription ID'() {
        given:
            def nodeMo1 = dps.node().name('LTEERBS111111').neType('ERBS').neConfigurationManagerState(neConfigurationManagerState).build()
            def nodeMo2 = dps.node().name('LTEERBS222222').neType('ERBS').neConfigurationManagerState(neConfigurationManagerState).build()
            def subMo = createSubscription([nodeMo1, nodeMo2] as ManagedObject[], event)
            def scannerMo1 = createScanner(nodeMo1, subMo.poId, PREDEF_10000_CELLTRACE_SCANNER)
            def scannerMO2 = createScanner(nodeMo2, subMo.poId, PREDEF_10000_CELLTRACE_SCANNER)
            def nodes = nodeDao.findAll()
            def sub = subscriptionDao.findOneById(subMo.poId) as CellTraceSubscription

        when:
            def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)

        then:
            tasks.size() == taskNumber
            if (taskNumber > 0) {
                tasks.each {
                    assert it.jobId.contains('subscriptionId=' + sub.id)
                    assert (it.nodeAddress.contains(nodeMo1.fdn) || it.nodeAddress.contains(nodeMo2.fdn))
                    assert it.class == SubscriptionDeactivationTaskRequest.class
                }
            }
            if (trackerCalls > 0) {
                1 * pmicInitiationTrackerCache.startTrackingDeactivation(*_) >> { arguments ->
                    assert arguments[0] == sub.idAsString
                    assert arguments[1] == sub.administrationState.name()
                    assert arguments[2] == [(nodeMo1.fdn): 'ERBS', (nodeMo2.fdn): 'ERBS'] as Map
                    assert arguments[3] == [(nodeMo1.fdn): 1, (nodeMo2.fdn): 1] as Map
                }
            } else {
                0 * pmicInitiationTrackerCache.startTrackingDeactivation(_, _, _, _)
            }

        then: 'scanners will not be cleaned'
            scannerMo1.getAttribute('status') == ScannerStatus.ACTIVE.name()
            scannerMo1.getAttribute('subscriptionId') == subMo.poId as String
            scannerMO2.getAttribute('status') == ScannerStatus.ACTIVE.name()
            scannerMO2.getAttribute('subscriptionId') == subMo.poId as String

        where:
            neConfigurationManagerState          || taskNumber | trackerCalls
            NeConfigurationManagerState.ENABLED  || 2          | 1
            NeConfigurationManagerState.DISABLED || 0          | 0
    }

    def 'Will create a task for a node with 2 scanners with subscription ID, EBS Stream Cluster is deployed'() {
        given: 'Subscription with 1 ERBS node and 2 active scanners'
            def nodeMo1 = dps.node().name('LTEERBS123456').neType('ERBS').build()
            def subMo = createSubscription([nodeMo1], event, CellTraceCategory.CELLTRACE_AND_EBSL_STREAM, ebsEvent)
            def scannerMo1 = createScanner(nodeMo1, subMo.poId, PREDEF_10000_CELLTRACE_SCANNER)
            def scannerMo2 = createEbsScanner(nodeMo1)
            createSubScanner(scannerMo2, subMo)

            def nodes = nodeDao.findAll()
            def sub = subscriptionDao.findOneById(subMo.poId) as CellTraceSubscription

        when: 'Create Mediation Task Request without track response'
            def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)

        then: 'Create 1 task for the node with 2 notifications'
            tasks.size() == 1
            tasks.each {
                assert it.jobId.contains('subscriptionId=' + sub.id)
                assert (it.nodeAddress.contains(nodeMo1.fdn))
                assert it.class == SubscriptionSelectiveDeactivationTaskRequest.class
            }
            1 * pmicInitiationTrackerCache.startTrackingDeactivation(*_) >> { arguments ->
                assert arguments[0] == sub.idAsString
                assert arguments[1] == sub.administrationState.name()
                assert arguments[2] == [(nodeMo1.fdn): 'ERBS'] as Map
                assert arguments[3] == [(nodeMo1.fdn): 2] as Map
            }

        then: 'scanners will not be cleaned'
            scannerMo1.getAttribute('status') == ScannerStatus.ACTIVE.name()
            scannerMo1.getAttribute('subscriptionId') == subMo.poId as String
            scannerMo2.getAttribute('status') == ScannerStatus.ACTIVE.name()
            scannerMo2.getAttribute('subscriptionId') == '0'
    }

    def 'Will create tasks for all nodes if each node has scanner with subscription ID but will not track'() {
        given:
            def nodeMo1 = dps.node().name('LTEERBS111111').neType('ERBS').build()
            def nodeMo2 = dps.node().name('LTEERBS222222').neType('ERBS').build()
            def subMo = createSubscription([nodeMo1, nodeMo2] as ManagedObject[], event)
            def scannerMo1 = createScanner(nodeMo1, subMo.poId, PREDEF_10000_CELLTRACE_SCANNER)
            def scannerMo2 = createScanner(nodeMo2, subMo.poId, PREDEF_10000_CELLTRACE_SCANNER)
            def nodes = nodeDao.findAll()
            def sub = subscriptionDao.findOneById(subMo.poId) as CellTraceSubscription

        when:
            def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, false)

        then:
            tasks.size() == 2
            tasks.each {
                assert it.jobId.contains('subscriptionId=' + sub.id)
                assert (it.nodeAddress.contains(nodeMo1.fdn) || it.nodeAddress.contains(nodeMo2.fdn))
                assert it.class == SubscriptionDeactivationTaskRequest.class
            }
            0 * pmicInitiationTrackerCache.startTrackingDeactivation(*_)

        then: 'scanners will not be cleaned'
            scannerMo1.getAttribute('status') == ScannerStatus.ACTIVE.name()
            scannerMo1.getAttribute('subscriptionId') == subMo.poId as String
            scannerMo2.getAttribute('status') == ScannerStatus.ACTIVE.name()
            scannerMo2.getAttribute('subscriptionId') == subMo.poId as String
    }

    def 'Will create a task for a node with 2 scanners with subscription ID but will not track, Cell Trace and EBS-L Stream'() {
        given: 'a subscription with 1 ERBS node and 2 active scanners'
            def nodeMo1 = dps.node().name('LTEERBS123456').neType('ERBS').build()
            def subMo = createSubscription(nodeMo1, event, CellTraceCategory.CELLTRACE_AND_EBSL_STREAM, ebsEvent)
            def scannerMo1 = createScanner(nodeMo1, subMo.poId, PREDEF_10000_CELLTRACE_SCANNER)
            def scannerMo2 = createEbsScanner(nodeMo1)
            createSubScanner(scannerMo2, subMo)
            def nodes = nodeDao.findAll()
            def sub = subscriptionDao.findOneById(subMo.poId) as CellTraceSubscription

        when: 'Create Mediation Task Request without track response'
            def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, false)

        then: 'Create 1 task for the node with 2 notifications'
            tasks.size() == 1
            tasks.each {
                assert it.jobId.contains('subscriptionId=' + sub.id)
                assert (it.nodeAddress.contains(nodeMo1.fdn))
                assert it.class == SubscriptionSelectiveDeactivationTaskRequest.class
            }
            0 * pmicInitiationTrackerCache.startTrackingDeactivation(*_);

        then: 'scanners will not be cleaned'
            scannerMo1.getAttribute('status') == ScannerStatus.ACTIVE.name()
            scannerMo1.getAttribute('subscriptionId') == subMo.poId as String
            scannerMo2.getAttribute('status') == ScannerStatus.ACTIVE.name()
            scannerMo2.getAttribute('subscriptionId') == '0'
    }

    def 'Will create tasks only for nodes that have scanners with subscription id'() {
        given:
            def nodeMo1 = dps.node().name('LTEERBS111111').neType('ERBS').build()
            def nodeMo2 = dps.node().name('LTEERBS222222').neType('ERBS').build()
            def subMo = createSubscription([nodeMo1, nodeMo2] as ManagedObject[], event)
            def scannerMo1 = createScanner(nodeMo1, subMo.poId, PREDEF_10000_CELLTRACE_SCANNER)
            def scannerMo2 = createScanner(nodeMo2, 0L, PREDEF_10000_CELLTRACE_SCANNER)
            def nodes = nodeDao.findAll()
            def sub = subscriptionDao.findOneById(subMo.poId) as CellTraceSubscription

        when:
            def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, false)

        then:
            tasks.size() == 1
            tasks.each {
                assert it.jobId.contains('subscriptionId=' + sub.id)
                assert it.nodeAddress.contains(nodeMo1.fdn)
                assert it.class == SubscriptionDeactivationTaskRequest.class
            }

        then: 'scanners will not be cleaned'
            scannerMo1.getAttribute('status') == ScannerStatus.ACTIVE.name()
            scannerMo1.getAttribute('subscriptionId') == subMo.poId as String
            scannerMo2.getAttribute('status') == ScannerStatus.ACTIVE.name()
            scannerMo2.getAttribute('subscriptionId') == '0'
    }

    @Unroll
    def 'Will create tasks only for nodes that have scanners with subscription id for Cell Trace and EBS-L Stream'() {
        given: 'Subscription with 1 ERBS node and 2 active scanners'
            def nodeMo1 = dps.node().name('LTEERBS111111').neType('ERBS').build()
            def subMo = createSubscription(nodeMo1, event, CellTraceCategory.CELLTRACE_AND_EBSL_STREAM, ebsEvent)
            def scannerMo1 = createScanner(nodeMo1, subMo.poId, PREDEF_10000_CELLTRACE_SCANNER)
            def scannerMo2 = createEbsScanner(nodeMo1)
            def nodes = nodeDao.findAll()
            def sub = subscriptionDao.findOneById(subMo.poId) as CellTraceSubscription

        when: 'Create Mediation Task Request without track response'
            def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)

        then: 'Create 1 task for the node with 1 notification'
            tasks.size() == 1
            tasks.each {
                assert it.jobId.contains('subscriptionId=' + sub.id)
                assert (it.nodeAddress.contains(nodeMo1.fdn))
                assert it.class == SubscriptionDeactivationTaskRequest.class //because no PmicSubScannerInfo
            }
            1 * pmicInitiationTrackerCache.startTrackingDeactivation(*_) >> { arguments ->
                assert arguments[0] == sub.idAsString
                assert arguments[1] == sub.administrationState.name()
                assert arguments[2] == [(nodeMo1.fdn): 'ERBS'] as Map
                assert arguments[3] == [(nodeMo1.fdn): 1] as Map
            }

        then: 'scanners will not be cleaned'
            scannerMo1.getAttribute('status') == ScannerStatus.ACTIVE.name()
            scannerMo1.getAttribute('subscriptionId') == subMo.poId as String
            scannerMo2.getAttribute('status') == ScannerStatus.ACTIVE.name()
            scannerMo2.getAttribute('subscriptionId') == '0'
    }

    @Unroll
    def 'Will create tasks only for nodes that have ebs scanners with subscription id for Cell Trace and EBS-N Stream'() {
        given: 'a Cell Trace and EBS-N Streaming subscription with 1 node with #npEventProducers normal priority events and #ebsEventProducers ebs events'
            def nodeMo1 = dps.node().name('LTEERBS111111').neType('RadioNode').technologyDomain(['5GS']).build()
            def subscriptionMo = createSubscription(nodeMo1, createEventsForEventProducers(scannerEventProducers),
                                                    CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM, createEventsForEventProducers(ebsScannerEventProducers))
            def nodes = nodeDao.findAll()
            def sub = subscriptionDao.findOneById(subscriptionMo.poId) as CellTraceSubscription

        and: '#scannerEventProducers normal priority and #ebsScannerEventProducers high priority associated scanners'
            scannerEventProducers.forEach { eventProducerId ->
                createScanner(nodeMo1, subscriptionMo.poId, "PREDEF.${eventProducerId}.10000.CELLTRACE")
            }
            def ebsScanners = []
            ebsScannerEventProducers.forEach{ eventProducerId ->
                def scanner = createEbsScanner(nodeMo1, "PREDEF.${eventProducerId}.10004.CELLTRACE")
                createSubScanner(scanner, subscriptionMo)
                ebsScanners.add(scanner)
            }

        when: 'createMediationTaskRequests is executed'
            def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)

        then: '1 task for the node is created'
            tasks.size() == (expectedTasks ? 1 : 0)
            tasks.every { task ->
                task.jobId.contains('subscriptionId=' + sub.id) && task.nodeAddress.contains(nodeMo1.fdn) &&
                task.class == (ebsScannerEventProducers.empty ? SubscriptionDeactivationTaskRequest.class : SubscriptionSelectiveDeactivationTaskRequest.class)
            }
            if (expectedTasks) {
                1 * pmicInitiationTrackerCache.startTrackingDeactivation(sub.idAsString, sub.administrationState.name(), [(nodeMo1.fdn): 'RadioNode'] as Map, [(nodeMo1.fdn): expectedNotifications] as Map)
            } else {
                0 * pmicInitiationTrackerCache.startTrackingDeactivation(_)
            }

        and: 'scanners will not be cleaned'
            ebsScanners.every { scanner ->
                scanner.getAttribute('status') == ScannerStatus.ACTIVE.name()
                scanner.getAttribute('subscriptionId') == '0'
            }

        where:
            scannerEventProducers | ebsScannerEventProducers    || expectedNotifications    | expectedTasks
            ['DU']                | ['DU']                      || 2                        | true
            ['DU', 'CUUP']        | ['DU', 'CUUP']              || 4                        | true
            ['DU', 'CUUP', 'CUCP']| ['DU', 'CUUP', 'CUCP']      || 6                        | true
            []                    | ['DU']                      || 1                        | true
            ['DU', 'CUUP']        | []                          || 2                        | true
            []                    | []                          || 0                        | false
    }

    @Unroll
    def 'Will create tasks only for nodes that have scanners with subscription id for EBS-N Streaming only subscription'() {
        given: 'an EBS-N Streaming only subscription with 1 node and #scannerEventProducers ebs events'
            def nodeMo1 = dps.node().name('LTEERBS111111').neType('RadioNode').technologyDomain(['5GS']).build()
            def subscriptionMo = createSubscription(nodeMo1, [] as EventInfo[], CellTraceCategory.NRAN_EBSN_STREAM, createEventsForEventProducers(scannerEventProducers))

            def nodes = nodeDao.findAll()
            def sub = subscriptionDao.findOneById(subscriptionMo.poId) as CellTraceSubscription

        and: 'ebs streaming scanners with active sub scanners for #scannerEventProducers event producers'
            scannerEventProducers.forEach{ eventProducerId ->
                createSubScanner(createEbsScanner(nodeMo1, "PREDEF.${eventProducerId}.10004.CELLTRACE"), subscriptionMo)
            }

        when: 'create mediation task request is called'
            def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)

        then: '1 MTR is created for the node and #expectedNotifications expected notifications'
            tasks.size() == (expectMtr ? 1 : 0)
            tasks.every { it.jobId.contains('subscriptionId=' + sub.id) && it.nodeAddress.contains(nodeMo1.fdn) && it.class == SubscriptionSelectiveDeactivationTaskRequest.class }
            if (expectedNotifications > 0) {
                1 * pmicInitiationTrackerCache.startTrackingDeactivation(sub.idAsString, sub.administrationState.name(), [(nodeMo1.fdn): 'RadioNode'] as Map, [(nodeMo1.fdn): expectedNotifications])
            } else {
                0 * pmicInitiationTrackerCache.startTrackingDeactivation(_)
            }

        where:
            scannerEventProducers   || expectMtr    | expectedNotifications
            ['DU', 'CUCP', 'CUUP']  || true         | 3
            ['DU', 'CUUP']          || true         | 2
            ['DU']                  || true         | 1
            []                      || false        | 0
    }

    def createEventsForEventProducers(eventProducerIds) {
        def events = []
        eventProducerIds.forEach { eventProducerId ->
            events.add(new EventInfo("${eventProducerId}TestEvent", 'group', eventProducerId))
        }
        events.toArray(new EventInfo[events.size()])
    }

    def 'Will create tasks only for nodes that have scanners'() {
        given:
            def nodeMo1 = dps.node().name('LTEERBS111111').neType('ERBS').pmEnabled(true).build()
            def nodeMo2 = dps.node().name('LTEERBS222222').neType('ERBS').pmEnabled(true).build()
            def subMo = createSubscription([nodeMo1, nodeMo2] as ManagedObject[], event)
            createScanner(nodeMo1, subMo.poId, PREDEF_10000_CELLTRACE_SCANNER)

            def nodes = nodeDao.findAll()
            def sub = subscriptionDao.findOneById(subMo.poId) as CellTraceSubscription

        when:
            def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, false)

        then:
            tasks.size() == 1
            tasks.each {
                assert it.jobId.contains('subscriptionId=' + sub.id)
                assert it.nodeAddress.contains(nodeMo1.fdn)
                assert it.class == SubscriptionDeactivationTaskRequest.class
            }
    }

    @Unroll
    def 'Will not track if subscription admin state is not DEACTIVATING or UPDATING, but will create tasks.'() {
        given:
            def nodeMo1 = dps.node().name('LTEERBS111111').neType('ERBS').build()
            def nodeMo2 = dps.node().name('LTEERBS222222').neType('ERBS').build()
            def subMo = createSubscription([nodeMo1, nodeMo2] as ManagedObject[], event, CellTraceCategory.CELLTRACE, [] as EventInfo, adminstate)
            createScanner(nodeMo1, subMo.poId, PREDEF_10000_CELLTRACE_SCANNER)
            createScanner(nodeMo2, subMo.poId, PREDEF_10000_CELLTRACE_SCANNER)

            def nodes = nodeDao.findAll()
            def sub = subscriptionDao.findOneById(subMo.poId) as CellTraceSubscription

        when:
            def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)

        then:
            tasks.size() == 2
            tasks.eachWithIndex { it, index ->
                assert it.jobId.contains('subscriptionId=' + sub.id)
                assert it.class == SubscriptionDeactivationTaskRequest.class
                assert (it.nodeAddress.contains(nodeMo1.fdn) || it.nodeAddress.contains(nodeMo2.fdn))
            }
            startTrackingInvocationCount * pmicInitiationTrackerCache.startTrackingDeactivation(*_) >> { arguments ->
                assert arguments[0] == sub.idAsString
                assert arguments[1] == sub.administrationState.name()
                assert arguments[2] == [(nodeMo2.fdn): 'ERBS', (nodeMo1.fdn): 'ERBS'] as Map
                assert arguments[3] == [(nodeMo2.fdn): 1, (nodeMo1.fdn): 1] as Map
            }

        where:
            adminstate                       | startTrackingInvocationCount
            AdministrationState.DEACTIVATING | 1
            AdministrationState.UPDATING     | 1
            AdministrationState.ACTIVE       | 0
            AdministrationState.ACTIVATING   | 0
            AdministrationState.INACTIVE     | 0
            AdministrationState.SCHEDULED    | 0
    }

    @Unroll
    def 'Will not track if subscription admin state is not DEACTIVATING or UPDATING, but will create tasks for Cell Trace and EBS-L Stream'() {
        given: 'Subscription with 1 ERBS node and 2 active scanners'
            def nodeMo1 = dps.node().name('LTEERBS111111').neType('ERBS').build()
            def subMo = createSubscription(nodeMo1, event, CellTraceCategory.CELLTRACE_AND_EBSL_STREAM, ebsEvent, adminstate)
            createScanner(nodeMo1, subMo.poId, PREDEF_10000_CELLTRACE_SCANNER)
            createSubScanner(createEbsScanner(nodeMo1), subMo)

            def nodes = nodeDao.findAll()
            def sub = subscriptionDao.findOneById(subMo.poId) as CellTraceSubscription

            when:
                def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)

        then:
            tasks.size() == 1
            tasks.each {
                assert it.jobId.contains('subscriptionId=' + sub.id)
                assert it.nodeAddress.contains(nodeMo1.fdn)
                assert it.class == SubscriptionSelectiveDeactivationTaskRequest.class
            }
            startTrackingInvocationCount * pmicInitiationTrackerCache.startTrackingDeactivation(*_) >> { arguments ->
                assert arguments[0] == sub.idAsString
                assert arguments[1] == sub.administrationState.name()
                assert arguments[2] == [(nodeMo1.fdn): 'ERBS'] as Map
                assert arguments[3] == [(nodeMo1.fdn): 2] as Map
            }

        where:
            adminstate                       | startTrackingInvocationCount
            AdministrationState.DEACTIVATING | 1
            AdministrationState.UPDATING     | 1
            AdministrationState.ACTIVE       | 0
            AdministrationState.ACTIVATING   | 0
            AdministrationState.INACTIVE     | 0
            AdministrationState.SCHEDULED    | 0
    }

    def 'Will not create tasks and clean scanners if the scanner is not active but associated to subscription'() {
        given:
            def nodeMo1 = dps.node().name('LTEERBS111111').neType('ERBS').build()
            def nodeMo2 = dps.node().name('LTEERBS222222').neType('ERBS').build()
            def subMo = createSubscription([nodeMo2, nodeMo1] as ManagedObject[], event)
            def scannerMo1 = createScanner(nodeMo1, subMo.poId, PREDEF_10000_CELLTRACE_SCANNER)
            def scannerMo2 = createScanner(nodeMo2, subMo.poId, PREDEF_10000_CELLTRACE_SCANNER, ProcessType.NORMAL_PRIORITY_CELLTRACE, ScannerStatus.ERROR)

            def nodes = nodeDao.findAll()
            def sub = subscriptionDao.findOneById(subMo.poId) as CellTraceSubscription

        when:
            def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)

        then:
            tasks.size() == 1
            tasks.each {
                assert it.jobId.contains('subscriptionId=' + sub.id)
                assert it.nodeAddress.contains(nodeMo1.fdn)
                assert it.class == SubscriptionDeactivationTaskRequest.class
            }
            1 * pmicInitiationTrackerCache.startTrackingDeactivation(*_) >> { arguments ->
                assert arguments[0] == sub.idAsString
                assert arguments[1] == sub.administrationState.name()
                assert arguments[2] == [(nodeMo1.fdn): 'ERBS'] as Map
                assert arguments[3] == [(nodeMo1.fdn): 1] as Map
            }

        then: 'scanners will not be cleaned'
            scannerMo1.getAttribute('status') == ScannerStatus.ACTIVE.name()
            scannerMo1.getAttribute('subscriptionId') == subMo.poId as String
            scannerMo2.getAttribute('status') == ScannerStatus.UNKNOWN.name()
            scannerMo2.getAttribute('subscriptionId') == '0'
    }

    def 'Will not create tasks and clean ebs scanners if the scanner is not active but associated to subscription'() {
        given:
            def nodeMo1 = dps.node().name('LTEERBS111111').neType('ERBS').build()
            def nodeMo2 = dps.node().name('LTEERBS222222').neType('ERBS').build()
            def subMo = createSubscription([nodeMo1, nodeMo2] as ManagedObject[], [] as EventInfo[], CellTraceCategory.EBSL_STREAM, event)
            def scannerMo1 = createEbsScanner(nodeMo1)
            createSubScanner(scannerMo1, subMo)
            def scannerMo2 = createEbsScanner(nodeMo2, EBS_CELLTRACE_SCANNER, ScannerStatus.ERROR)
            createSubScanner(scannerMo2, subMo)

            def nodes = nodeDao.findAll()
            def sub = subscriptionDao.findOneById(subMo.poId) as CellTraceSubscription

        when:
            def tasks = objectUnderTest.createMediationTaskRequests(nodes, sub, true)

        then:
            tasks.size() == 1
            tasks.each {
                assert it.jobId.contains('subscriptionId=' + sub.id)
                assert it.nodeAddress.contains(nodeMo1.fdn)
                assert it.class == SubscriptionSelectiveDeactivationTaskRequest.class
            }
            1 * pmicInitiationTrackerCache.startTrackingDeactivation(*_) >> { arguments ->
                assert arguments[0] == sub.idAsString
                assert arguments[1] == sub.administrationState.name()
                assert arguments[2] == [(nodeMo1.fdn): 'ERBS'] as Map
                assert arguments[3] == [(nodeMo1.fdn): 1] as Map
            }

        then: 'scanners status will not be cleaned and PmicSubScannerInfo will be deleted for PmicScannerInfo not ACTIVE'
            scannerMo1.getAttribute('status') == ScannerStatus.ACTIVE.name()
            scannerMo1.getAttribute('subscriptionId') == '0'
            scannerMo2.getAttribute('status') == ScannerStatus.ERROR.name()
            scannerMo2.getAttribute('subscriptionId') == '0'
            def pmSubScanner1 = pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodeMo1, subMo))
            def pmSubScanner2 = pmSubScannerDao.findOneByFdn(buildPmicSubScannerInfoFdn(nodeMo2, subMo))
            with(pmSubScanner1) {
                assert getSubscriptionId() == subMo.poId
            }
            pmSubScanner2 == null
    }

    def createSubscription(nodes, events = [] as EventInfo[], cellTraceCategory = CellTraceCategory.CELLTRACE, ebsEvents = [] as EventInfo[], adminstratioState = AdministrationState.DEACTIVATING) {
        cellTraceSubscriptionBuilder.ebsEvents(ebsEvents)
                                    .cellTraceCategory(cellTraceCategory)
                                    .name('Test')
                                    .events(events)
                                    .nodes(nodes)
                                    .administrativeState(adminstratioState)
                                    .build()
    }

    def createScanner(node, subId, name, processType = ProcessType.NORMAL_PRIORITY_CELLTRACE, status = ScannerStatus.ACTIVE) {
        dps.scanner()
           .nodeName(node.name)
           .name(name)
           .processType(processType)
           .status(status)
           .subscriptionId(subId)
           .build()
    }

    def createSubScanner(scanner, subscription) {
        dps.subScanner()
           .fdn("${scanner.fdn},PMICSubScannerInfo=${subscription.name}")
           .subscriptionId(subscription.poId)
           .build()
    }

    def createEbsScanner(node, name = EBS_CELLTRACE_SCANNER, status = ScannerStatus.ACTIVE) {
        createScanner(node, 0L, name, ProcessType.HIGH_PRIORITY_CELLTRACE, status)
    }

    def buildPmicSubScannerInfoFdn(node, subscriptionUnderTest, scannerName = EBS_CELLTRACE_SCANNER) {
        pmSubScannerService.buildPmSubScannerFdn(node.fdn, scannerName, subscriptionUnderTest.name)
    }


}
