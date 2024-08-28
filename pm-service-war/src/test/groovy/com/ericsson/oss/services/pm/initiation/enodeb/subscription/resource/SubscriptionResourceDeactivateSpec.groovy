/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2016
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource

import spock.lang.Unroll

import javax.inject.Inject
import javax.ws.rs.core.Response

import com.ericsson.cds.cdi.support.configuration.InjectionProperties
import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.security.accesscontrol.EAccessControl
import com.ericsson.oss.itpf.sdk.security.accesscontrol.SecurityViolationException
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.node.TestNetworkElementDpsUtils
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.services.pm.initiation.common.ResponseData
import com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource.dto.InitiationRequest
import com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource.dto.InitiationResponse
import com.ericsson.oss.services.pm.services.exception.ConcurrentSubscriptionUpdateException
import com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionOperationException

class SubscriptionResourceDeactivateSpec extends SkeletonSpec {

    @ObjectUnderTest
    SubscriptionResource subscriptionResource

    @ImplementationInstance
    private EAccessControl eAccessControl = Mock(EAccessControl)

    private final Date persistenceTime = new Date()
    private final InitiationRequest initiationRequest = new InitiationRequest(persistenceTime)
    TestNetworkElementDpsUtils nodeBuilder = new TestNetworkElementDpsUtils(configurableDps)

    @Inject
    private SubscriptionDao subscriptionDao

    @Override
    def addAdditionalInjectionProperties(InjectionProperties injectionProperties) {
        super.addAdditionalInjectionProperties(injectionProperties)
        injectionProperties.autoLocateFrom('com.ericsson.oss.services.pm.initiation')
    }

    def setup() {
        eAccessControl.isAuthorized(_, _) >> true
    }

    def "When deactivate is called on a active subscription, the state is changed to DEACTIVATING in DPS"() {
        given: "An active subscription in DPS"
        ManagedObject subscription = createStatsSubscription(null, null, AdministrationState.ACTIVE)
        when: "The subscription is deactivated"
        Response response = subscriptionResource.deactivate(subscription.getPoId(), initiationRequest)
        final InitiationResponse initiationResponse = (InitiationResponse) response.getEntity()
        then: "It goes to DEACTIVATING in DPS"
        AdministrationState.DEACTIVATING == initiationResponse.getAdministrationState()
        response.getStatus() == Response.Status.OK.getStatusCode()
        AdministrationState.DEACTIVATING == subscriptionDao.findOneById(subscription.getPoId()).getAdministrationState()
    }

    def "When deactivate is called on a active subscription without counters, the state is changed to DEACTIVATING in DPS"() {
        given: "An active subscription in DPS"
        ManagedObject subscription = createStatsSubscriptionNoCounters(null, null, AdministrationState.ACTIVE)
        when: "The subscription is deactivated"
        Response response = subscriptionResource.deactivate(subscription.getPoId(), initiationRequest)
        final InitiationResponse initiationResponse = (InitiationResponse) response.getEntity()
        then: "It goes to DEACTIVATING in DPS"
        AdministrationState.DEACTIVATING == initiationResponse.getAdministrationState()
        response.getStatus() == Response.Status.OK.getStatusCode()
        AdministrationState.DEACTIVATING == subscriptionDao.findOneById(subscription.getPoId()).getAdministrationState()
    }

    def "When deactivate is called on a scheduled subscription, the state is changed to INACTIVE in DPS"() {
        given: "An scheduled subscription in DPS"
        ManagedObject subscription = createStatsSubscription(null, null, AdministrationState.SCHEDULED)
        when: "The subscription is deactivated"
        Response response = subscriptionResource.deactivate(subscription.getPoId(), initiationRequest)
        final InitiationResponse initiationResponse = (InitiationResponse) response.getEntity()
        then: "It goes to INACTIVE in DPS"
        AdministrationState.INACTIVE == initiationResponse.getAdministrationState()
        response.getStatus() == Response.Status.OK.getStatusCode()
        AdministrationState.INACTIVE == subscriptionDao.findOneById(subscription.getPoId()).getAdministrationState()

    }

    def "When deactivate is called on a valid CellTrace subscription, the state is changed to DEACTIVATING in DPS"() {
        given: "An active subscription in DPS"
        final ManagedObject subscription = cellTraceSubscriptionBuilder.name("sub")
                .administrativeState(AdministrationState.ACTIVE).addEvent("group", "event")
                .persistenceTime(persistenceTime).build()
        final ManagedObject node = nodeBuilder.builder("node1").build()
        dpsUtils.addAssociation(subscription, "nodes", node)
        when: "The subscription is deactivated"
        Response response = subscriptionResource.deactivate(subscription.getPoId(), initiationRequest)
        final InitiationResponse initiationResponse = (InitiationResponse) response.getEntity()
        then: "It goes to DEACTIVATING in DPS"
        AdministrationState.DEACTIVATING == initiationResponse.getAdministrationState()
        response.getStatus() == Response.Status.OK.getStatusCode()
        AdministrationState.DEACTIVATING == subscriptionDao.findOneById(subscription.getPoId()).getAdministrationState()
    }

    def "When deactivate is called on a valid EBM subscription, the state is changed to DEACTIVATING in DPS"() {
        given: "An inactive subscription in DPS"
        final ManagedObject subscription = ebmSubscriptionBuilder.name("sub")
                .administrativeState(AdministrationState.ACTIVE).addEvent("group", "event")
                .persistenceTime(persistenceTime).build()
        final ManagedObject node = nodeBuilder.builder("node1").build()
        dpsUtils.addAssociation(subscription, "nodes", node)
        when: "The subscription is activated"
        Response response = subscriptionResource.deactivate(subscription.getPoId(), initiationRequest)
        final InitiationResponse initiationResponse = (InitiationResponse) response.getEntity()
        then: "It goes to ACTIVATING in DPS"
        AdministrationState.DEACTIVATING == initiationResponse.getAdministrationState()
        response.getStatus() == Response.Status.OK.getStatusCode()
        AdministrationState.DEACTIVATING == subscriptionDao.findOneById(subscription.getPoId()).getAdministrationState()
    }

    def "When deactivate is called with an invalid subscription ID error is returned because subscription does not exist"() {
        given: "No subscription in DPS with the given ID"
        when: "The subscription is deactivated"
        def result = subscriptionResource.deactivate(1234L, initiationRequest)
        then:
        (result.getEntity() as ResponseData).getError() == String.format("Subscription %d not found.", 1234L)
    }

    @Unroll
    def "When deactivate is called with a #state subscription a InvalidSubscriptionOperationException is thrown"() {
        given: "A subscription in DPS with an admin state not equal to active or scheduled"
        final ManagedObject subscription = statisticalSubscriptionBuilder.name("statssub")
                .administrativeState(state).persistenceTime(persistenceTime).addCounter("CounterName", "counterGroup").build()
        final ManagedObject node = nodeBuilder.builder("node1").build()
        dpsUtils.addAssociation(subscription, "nodes", node)
        when: "The subscription is deactivated"
        subscriptionResource.deactivate(subscription.getPoId(), initiationRequest)

        then: "An exception is thrown"
        InvalidSubscriptionOperationException e = thrown(InvalidSubscriptionOperationException)
        e.getMessage() == "Subscription statssub with id 1 cannot be deactivated because administration state is " + state
        where:
        state << AdministrationState.values().findAll { (it != AdministrationState.ACTIVE && it != AdministrationState.SCHEDULED) }
    }

    @Unroll
    def "When deactivate is called with persistence time offset from the current time by #minuteDiff minutes a ConcurrentUpdateSubscriptionException is thrown"() {
        given: "An active subscription in DPS"
        final ManagedObject subscription = statisticalSubscriptionBuilder.name("statssub")
                .administrativeState(AdministrationState.ACTIVE).persistenceTime(persistenceTime).addCounter("CounterName", "counterGroup").build()
        final ManagedObject node = nodeBuilder.builder("node1").build()
        dpsUtils.addAssociation(subscription, "nodes", node)
        when: "The subscription is deactivated with persistence time offset from the current time"
        Calendar cal = Calendar.getInstance()

        cal.add(Calendar.MINUTE, minuteDiff)
        final InitiationRequest pastInitiationRequest = new InitiationRequest(cal.getTime())
        subscriptionResource.deactivate(subscription.getPoId(), pastInitiationRequest)

        then: "An exception is thrown"
        ConcurrentSubscriptionUpdateException e = thrown(ConcurrentSubscriptionUpdateException)
        e.getMessage() == String.format("Subscription %s  with id %s has changed by another user. Subscription PersistenceTime is %s "
                + "InitiationRequest persistenceTime is %s", subscription.getName(), subscription.getPoId(),  persistenceTime.getTime(),
                pastInitiationRequest.getPersistenceTime().getTime())
        where:
        minuteDiff << [-100, -10, -2, -1, 1, 2, 10, 100]
    }

    @Unroll
    def "When deactivate is called by unauthorized user, a SecurityViolationException is thrown"() {
        given: "An active subscription in DPS"
        ManagedObject subscription = createStatsSubscription(null, null, AdministrationState.ACTIVE)
        when: "The subscription is deactivated"
        subscriptionResource.deactivate(subscription.getPoId(), initiationRequest)
        then: "An exception is thrown"
        eAccessControl.isAuthorized(_, _) >> false
        thrown(SecurityViolationException)
    }

    private ManagedObject createStatsSubscription(
            final Date startTime = null, final Date endTime = null, AdministrationState administrationState = AdministrationState.ACTIVE) {
        final ManagedObject node = nodeBuilder.builder("node1").build()
        final ManagedObject subscription = statisticalSubscriptionBuilder.name("statssub")
                .administrativeState(administrationState).scheduleInfo(startTime, endTime)
                .persistenceTime(persistenceTime).addCounter("CounterName", "counterGroup").node(node).build()
        return subscription
    }

    private ManagedObject createStatsSubscriptionNoCounters(
            final Date startTime = null, final Date endTime = null, AdministrationState administrationState = AdministrationState.ACTIVE) {
        final ManagedObject node = nodeBuilder.builder("node2").build()
        final ManagedObject subscription = statisticalSubscriptionBuilder.name("statssubNoCounters")
                .administrativeState(administrationState).scheduleInfo(startTime, endTime)
                .persistenceTime(persistenceTime).node(node).build()
        return subscription
    }

}
