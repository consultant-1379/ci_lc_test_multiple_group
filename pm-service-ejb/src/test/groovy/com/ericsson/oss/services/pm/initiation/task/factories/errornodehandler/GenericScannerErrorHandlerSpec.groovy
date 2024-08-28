/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler

import static com.ericsson.oss.pmic.cdi.test.util.Constants.*

import javax.ejb.SessionContext
import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.configuration.InjectionProperties
import com.ericsson.cds.cdi.support.rule.ImplementationClasses
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.TestDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.node.TestNetworkElementDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.scanner.TestScannerDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.CellTraceSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.StatisticalSubscriptionBuilder
import com.ericsson.oss.pmic.dao.ScannerDao
import com.ericsson.oss.services.pm.collection.cache.PmFunctionOffErrorNodeCache
import com.ericsson.oss.services.pm.collection.cache.StartupRecoveryMonitor
import com.ericsson.oss.services.pm.collection.recovery.StartupRecovery

class GenericScannerErrorHandlerSpec extends SkeletonSpec {

    @ObjectUnderTest
    private GenericScannerErrorHandler scannerErrorHandler

    @ImplementationClasses
    def classes = [StartupRecoveryMonitor.class]

    @Inject
    private PmFunctionOffErrorNodeCache errorNodeCache

    @Inject
    @Modeled
    EventSender<MediationTaskRequest> eventSender

    @Inject
    private StartupRecovery startupRecovery

    @ImplementationInstance
    SessionContext sessionContext = Mock()

    @ImplementationInstance
    TimerService timerService = Mock(TimerService)

    @Inject
    ScannerDao scannerDao

    private List<ManagedObject> nodes
    TestDpsUtils testDpsUtils = new TestDpsUtils(configurableDps)
    TestScannerDpsUtils scanner = new TestScannerDpsUtils(configurableDps)
    TestNetworkElementDpsUtils node = new TestNetworkElementDpsUtils(configurableDps)
    CellTraceSubscriptionBuilder celltraceBuilder = new CellTraceSubscriptionBuilder(testDpsUtils)
    StatisticalSubscriptionBuilder statsBuilder = new StatisticalSubscriptionBuilder(testDpsUtils)

    @Override
    def addAdditionalInjectionProperties(InjectionProperties injectionProperties) {
        super.addAdditionalInjectionProperties(injectionProperties)
        injectionProperties.autoLocateFrom('com.ericsson.oss.services.pm')
    }

    def setup() {
        sessionContext.getBusinessObject(StartupRecovery.class) >> startupRecovery
        timerService.getTimers() >> []
    }

    def "When startup recovery has finished, the PmFunction Deleted entry is processed"() {
        given: "Startup recovery has not finished and a handler task ready to process"
        nodes = [node.builder(NODE_NAME_1).build(), node.builder(NODE_NAME_2).build()]
        scannerErrorHandler.process(ErrorNodeCacheProcessType.PM_FUNCTION_DELETED, getPmDeletedAttributes())
        errorNodeCache.addNodeWithPmFunctionOff(NETWORK_ELEMENT_1, 1L)
        when: "Startup recovery indicates that it's finished"
        startupRecovery.checkMembership()
        then: "The stored requests are cleared"
        0 == errorNodeCache.removeStoredRequests().size()
        and: "The error entry is removed from the cache"
        0 == errorNodeCache.getErrorEntry(NETWORK_ELEMENT_1).size()
    }

    private Map<String, Object> getPmDeletedAttributes() {
        final Map<String, Object> attributes = new HashMap<>()
        attributes.put(ErrorNodeCacheAttributes.FDN, NETWORK_ELEMENT_1)
        return attributes
    }
}
