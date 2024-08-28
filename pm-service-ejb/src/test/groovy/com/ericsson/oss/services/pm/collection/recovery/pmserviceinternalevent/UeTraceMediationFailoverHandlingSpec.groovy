/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.collection.recovery.pmserviceinternalevent

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.node.TestNetworkElementDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.UeTraceSubscriptionBuilder
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest
import com.ericsson.oss.services.pm.common.events.PmServiceInternalEvent
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener
import spock.lang.Unroll

import javax.inject.Inject

class UeTraceMediationFailoverHandlingSpec extends SkeletonSpec {

    @ObjectUnderTest
    PmServiceInternalEventListener internalEventListener

    @Inject
    private EventSender<MediationTaskRequest> eventSender

    @ImplementationInstance
    ConfigurationChangeListener configurationChangeListener = Mock(ConfigurationChangeListener)

    TestNetworkElementDpsUtils testNetworkElementDpsUtils = new TestNetworkElementDpsUtils(configurableDps)

    @Unroll
    def 'UeTrace File Collection tasks should be sent when mspm failover occurs'() {
        given: 'a network with some SGSN and RadioNode'
        createNodeMO(nodeName,neType,technologyDomain)
        and: 'an active UE trace subscription'
        createSubscriptionBuilder(AdministrationState.ACTIVE, OperationalState.RUNNING).build()
        and: 'UE trace collection is enabled'
        configurationChangeListener.getUeTraceCollectionEnabled() >> true
        when: "lost mediation service event received"
        def event = new PmServiceInternalEvent()
        event.lostMediationServiceDateTime = new Date().parse('yyyy/MM/dd hh:mm:ss', lostMediationServiceDateTime).time
        event.setNodeFdns(nodesHandledByMspm as Set)
        internalEventListener.receivePmServiceInternalEvent(event)

        then: 'new tasks are created for all nodes in the network'
        1 * eventSender.send(_ as List<FileCollectionTaskRequest>, _)

        where:
        nodesHandledByMspm                       | lostMediationServiceDateTime | expectedInvocations | nodeName             |   neType    |  technologyDomain
        ['NetworkElement=LTE01DG200001']         | '2018/08/08 10:19:59'        | 1                   | 'LTE01DG200001'      | 'RadioNode' |   ['EPS']
        ['NetworkElement=SGSN-16A-V1-CP0201']    | '2018/08/08 10:19:59'        | 1                   | 'SGSN-16A-V1-CP0201' | 'SGSN-MME'  |   ['EPS']
    }

    def createNodeMO(String nodeName, String neType, List<String> technologyDomains, String ossModelIdentity = '6607-651-025') {
        def nodeMO = testNetworkElementDpsUtils.builder(nodeName).ossModelIdentity(ossModelIdentity).build()
        def nodeAttr = new HashMap<>()
        nodeAttr.put('neType', neType)
        nodeAttr.put('technologyDomain', technologyDomains)
        nodeMO.setAttributes(nodeAttr)
    }

    def createSubscriptionBuilder(AdministrationState administrationState, OperationalState operationalState) {
        (UeTraceSubscriptionBuilder) ueTraceSubscriptionBuilder.name('UeTraceSubscription').administrativeState(administrationState).operationalState(operationalState)
                .taskStatus(TaskStatus.OK)
	}
}