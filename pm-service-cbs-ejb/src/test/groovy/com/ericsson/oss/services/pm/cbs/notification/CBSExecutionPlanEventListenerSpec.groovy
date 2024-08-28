/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.cbs.notification

import static com.ericsson.oss.pmic.api.constants.ModelConstants.NetworkElementConstants.NETWORK_ELEMENT_ATTR_TECHNOLOGY_DOMAIN
import static com.ericsson.oss.pmic.cdi.test.util.Constants.ERBS
import static com.ericsson.oss.pmic.cdi.test.util.Constants.NODE_NAME_1
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_NAME
import static com.ericsson.oss.pmic.cdi.test.util.constant.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_NODELIST_IDENTITY

import spock.lang.Unroll

import javax.ejb.TimerService
import javax.inject.Inject
import javax.transaction.HeuristicMixedException
import javax.transaction.HeuristicRollbackException
import javax.transaction.RollbackException

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.context.ContextService
import com.ericsson.oss.itpf.sdk.core.annotation.EServiceRef
import com.ericsson.oss.itpf.sdk.security.accesscontrol.SecurityViolationException
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.TestManagedElementDpsUtils
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.cm.cmshared.dto.CmObject
import com.ericsson.oss.services.pm.cbs.events.CBSExecutionPlanEvent200
import com.ericsson.oss.services.pm.cbs.events.CBSResourceSubscription
import com.ericsson.oss.services.pm.dps.utility.UpdateResourceSubscription
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionManager
import com.ericsson.oss.services.topologyCollectionsService.exception.service.TopologyCollectionsServiceException
import com.ericsson.oss.services.topologySearchService.exception.TopologySearchQueryException
import com.ericsson.oss.services.topologySearchService.exception.TopologySearchServiceException
import com.ericsson.oss.services.topologySearchService.service.api.SearchExecutor
import com.ericsson.oss.services.topologySearchService.service.api.dto.NetworkExplorerResponse
import com.ericsson.oss.services.topologySearchService.service.datastructs.FixedSizeSet

class CBSExecutionPlanEventListenerSpec extends SkeletonSpec {

    static final def nodeNamesErbs = [
            'LTE01ERBS',
            'LTE02ERBS',
            'LTE03ERBS',
            'LTE04ERBS'
    ]

    static List<CBSResourceSubscription> resourceSubscriptionList = []

    @ObjectUnderTest
    CBSExecutionPlanEventListener cbsExecutionPlanEventListener

    @Inject
    SubscriptionManager subscriptionManager

    @ImplementationInstance
    ContextService context = new TestContextService()

    @Inject
    @EServiceRef
    SearchExecutor searchExecutor

    @Inject
    UpdateResourceSubscription updateResourceSubscription

    @Inject
    DataPersistenceService dataPersistenceService

    @ImplementationInstance
    TimerService timerService = [
            getTimers          : { [] },
            createIntervalTimer: { a, b, c -> null }
    ] as TimerService

    TestManagedElementDpsUtils managedElement = new TestManagedElementDpsUtils(configurableDps)
    ManagedObject subscription
    NetworkExplorerResponse queryResult

    def setup() {
        def nodeMO = dps.node().name(NODE_NAME_1).neType(ERBS).build()
        subscription = dps.subscription().
                type(SubscriptionType.STATISTICAL).
                name('subNameNe').
                administrationState(AdministrationState.ACTIVE).
                taskStatus(TaskStatus.OK).
                nodeListIdentity(1).
                nodes(nodeMO).
                build()

        subscriptionManager.getSubscriptionWrapper(subscription.getName(), SubscriptionType.STATISTICAL)

        resourceSubscriptionList << new CBSResourceSubscription(Long.toString(subscription.getPoId()), (String) subscription.getAttribute
                (PMIC_ATT_SUBSCRIPTION_NAME), 1)
    }

    def 'CBS Execution Plan Event received, should update node list when search criteria return empty object'() {

        given: 'a CBS Execution Plan Event'
        addErbsNodes()
        def query = 'some query'
        CBSExecutionPlanEvent200 cbsExecutionPlanEvent200 = new CBSExecutionPlanEvent200(query, resourceSubscriptionList)
        queryResult = createQueryResultFrom([], null)

        and: 'our mocks return the expected result'
        searchExecutor.search(query, _, _) >> queryResult

        when: 'a criteria based subscription execution plan is received'
        cbsExecutionPlanEventListener.receiveCbsExecutionPlanEvent(cbsExecutionPlanEvent200)

        then: 'node identity list should be updated'
        (subscription.getAttribute(PMIC_ATT_SUBSCRIPTION_NODELIST_IDENTITY) != old(subscription.getAttribute(PMIC_ATT_SUBSCRIPTION_NODELIST_IDENTITY)))

        and: 'should removed all nodes'
        subscription.getAssociations('nodes').size() == 0

        and: 'context\'s user is set back to original user value'
        context.getContextValue('X-Tor-UserID') == null
    }

    @Unroll
    def 'verify topology search service throw #exception, should get handle and skip update subscription Object'() {

        given: 'a CBS Execution Plan Event'
        def query = 'some query'
        addErbsNodes()
        CBSExecutionPlanEvent200 cbsExecutionPlanEvent200 = new CBSExecutionPlanEvent200(query, resourceSubscriptionList)

        and: 'our mocks return the expected result, throw exception'
        searchExecutor.search(query, _, _) >> exception

        when: 'a criteria based subscription execution plan is received'
        cbsExecutionPlanEventListener.receiveCbsExecutionPlanEvent(cbsExecutionPlanEvent200)

        then: 'node identity list should be not updated'
        (subscription.getAttribute(PMIC_ATT_SUBSCRIPTION_NODELIST_IDENTITY) == old(subscription.getAttribute
                (PMIC_ATT_SUBSCRIPTION_NODELIST_IDENTITY)))

        and: 'subscription update should not called'
        0 * updateResourceSubscription.updateResourceSubscription(subscription, _)

        where:
        exception << [TopologySearchQueryException, SecurityViolationException, TopologyCollectionsServiceException,
                      TopologySearchServiceException, TopologySearchServiceException, HeuristicMixedException,
                      HeuristicRollbackException, RollbackException]
    }

    @Unroll
    def 'CBS Execution Plan Event received, should not update node list when search criteria return null object'() {

        given: 'a CBS Execution Plan Event'
        addErbsNodes()
        def query = 'some query'
        CBSExecutionPlanEvent200 cbsExecutionPlanEvent200 = new CBSExecutionPlanEvent200(query, resourceSubscriptionList)
        queryResult = createQueryResultFrom(null, null)

        and: 'our mocks return the expected result'
        searchExecutor.search(query, _, _) >> queryResult

        when: 'a criteria based subscription execution plan is received'
        cbsExecutionPlanEventListener.receiveCbsExecutionPlanEvent(cbsExecutionPlanEvent200)

        then: 'node identity list should be not updated'
        (subscription.getAttribute(PMIC_ATT_SUBSCRIPTION_NODELIST_IDENTITY) == old(subscription.getAttribute
                (PMIC_ATT_SUBSCRIPTION_NODELIST_IDENTITY)))

        and: 'should removed all nodes'
        subscription.getAssociations('nodes').size() == 1

        and: 'context\'s user is set back to original user value'
        context.getContextValue('X-Tor-UserID') == null
    }

    @Unroll
    def 'CBS Execution Plan Event received, should update node list when query uses #moType and with valid list of subscriptions'() {

        given: 'a CBS Execution Plan Event'
        addErbsNodes()
        def query = 'some query'
        CBSExecutionPlanEvent200 cbsExecutionPlanEvent200 = new CBSExecutionPlanEvent200(query, resourceSubscriptionList)
        queryResult = createQueryResultFrom(nodeNamesErbs, moType)

        and: 'our mocks return the expected result'
        searchExecutor.search(query, _, _) >> queryResult

        when: 'a criteria based subscription execution plan is received'
        cbsExecutionPlanEventListener.receiveCbsExecutionPlanEvent(cbsExecutionPlanEvent200)

        then: 'node identity list should be updated'
        (subscription.getAttribute(PMIC_ATT_SUBSCRIPTION_NODELIST_IDENTITY) != old(subscription.getAttribute(PMIC_ATT_SUBSCRIPTION_NODELIST_IDENTITY)))

        and: 'associations are created from subscription to nodes'
        subscription.getAssociations('nodes').collect { ManagedObject it -> it.getName() } as Set == nodeNamesErbs as Set

        and: 'context\'s user is set back to original user value'
        context.getContextValue('X-Tor-UserID') == null

        where:
        moType << ['ManagedElement', 'NetworkElement']
    }

    NetworkExplorerResponse createQueryResultFrom(nodeNames, moType) {
        def cmObjects = createCmObjectsForNodes(nodeNames, moType)
        return new NetworkExplorerResponse(1L, 1L, cmObjects, null, null)
    }

    FixedSizeSet<CmObject> createCmObjectsForNodes(nodeNames, moType) {
        def cmObjects = null

        if (nodeNames == null) {
            cmObjects = null
        } else if (nodeNames.isEmpty()) {
            cmObjects = cmObjects = new FixedSizeSet<>(0)
        } else {
            cmObjects = cmObjects = new FixedSizeSet<>(nodeNames.size())
            nodeNames.each {
                def fdn = moType == null ? null : "${moType}=${it}"
                def mo = dataPersistenceService.getLiveBucket().findMoByFdn(fdn)
                def cmObject = new CmObject(fdn: fdn, poId: mo.poId, type: mo.type, name: mo.name, attributes: mo.allAttributes)
                cmObjects.add(cmObject)
            }
        }
        return cmObjects;
    }

    def buildQueryResultFromNodes(nodeName) {
        def cmObjects = new FixedSizeSet<>(1)
        def networkElementFdn = "NetworkElement=$nodeName"
        ManagedObject mo = dataPersistenceService.getLiveBucket().findMoByFdn(networkElementFdn)
        final CmObject node = new CmObject(fdn: networkElementFdn, poId: mo.poId, type: mo.type, name: mo.name, attributes: mo.allAttributes)
        cmObjects.add(node)
        return new NetworkExplorerResponse(1L, 1L, cmObjects, null, null)
    }

    private void addErbsNodes() {
        final List<String> technologyDomainList = Arrays.asList('EPS', 'UMTS');
        (0..3).each {
            def nodeName = nodeNamesErbs.get(it)
            def networkElementFdn = "NetworkElement=${nodeName}"
            def managedElementFdn = "ManagedElement=${nodeName}"
            ManagedObject netEl = nodeUtil.builder(nodeName).
                    fdn(networkElementFdn).
                    ctumEnabled(false).
                    globalCtumEnabled(false).
                    build()
            netEl.setAttribute(NETWORK_ELEMENT_ATTR_TECHNOLOGY_DOMAIN, technologyDomainList)
            ManagedObject me = managedElement.builder(nodeName).fdn(managedElementFdn).build()
            me.setTarget(netEl.getTarget())
        }
    }

    class TestContextService implements ContextService {
        private final Map<String, Serializable> data = new HashMap<>()

        @Override
        void setContextValue(final String contextParameterName, final Serializable contextData) {
            data.put(contextParameterName, contextData)
        }

        @Override
        def <T> T getContextValue(final String contextParameterName) {
            return data.get(contextParameterName) as T
        }

        @Override
        Map<String, Serializable> getContextData() {
            return data
        }
    }
}
