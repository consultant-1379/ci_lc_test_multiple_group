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

package com.ericsson.oss.services.pm.initiation.notification

import static com.ericsson.oss.pmic.cdi.test.util.Constants.EVENT_TYPE_CTUM
import static com.ericsson.oss.pmic.cdi.test.util.Constants.EVENT_TYPE_UETRACE
import static com.ericsson.oss.pmic.cdi.test.util.Constants.UETRACE
import static com.ericsson.oss.pmic.cdi.test.util.Constants.ACTIVE
import static com.ericsson.oss.pmic.cdi.test.util.Constants.ACTIVATING
import static com.ericsson.oss.pmic.cdi.test.util.Constants.UNKNOWN
import static com.ericsson.oss.pmic.cdi.test.util.Constants.ERROR
import static com.ericsson.oss.pmic.cdi.test.util.Constants.INACTIVE
import static com.ericsson.oss.pmic.cdi.test.util.Constants.DEACTIVATING
import static com.ericsson.oss.pmic.cdi.test.util.Constants.OK

import spock.lang.Unroll

import javax.ejb.Timer
import javax.ejb.TimerService
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.CtumSubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.SubscriptionBuilder
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.UeTraceSubscriptionBuilder
import com.ericsson.oss.pmic.dao.SubscriptionDao
import com.ericsson.oss.pmic.dto.subscription.Subscription
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.services.pm.bdd.collection.utils.DpsAttributeChangedEventCreator
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper
import com.ericsson.oss.services.pm.collection.notification.DpsPmJobUpdateNotificationListener
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache

class DpsPmJobUpdateNotificationListenerSpec extends SkeletonSpec implements DpsAttributeChangedEventCreator {

    def static PM_JOB_STATUS_ATTRIBUTE = 'status'
    def static NODE_TYPE = 'SGSN-MME'
    def static NODE_NAME_1 = 'Node A'
    def static NODE_NAME_2 = 'Node B'

    @ObjectUnderTest
    DpsPmJobUpdateNotificationListener objectUnderTest

    @Inject
    FileCollectionActiveTaskCacheWrapper fileCollectionActiveTasksCache

    @ImplementationInstance
    TimerService timerService = Mock(TimerService)
    Timer timer = Mock(Timer)

    @Inject
    PMICInitiationTrackerCache pmicInitiationTrackerCache

    @Inject
    SubscriptionDao subscriptionDao

    def nodeMO1

    def setup() {
        timerService.getTimers() >> [timer]
        timer.getInfo() >> []
        nodeMO1 = nodeUtil.builder(NODE_NAME_1).neType(NODE_TYPE).build()
    }

    @Unroll
    def '#administrationState a subscription that supports pm jobs with 1 node: pmJob status from #pmJobOldStatus to #pmJobNewStatus sets subscription status to #expectedSubsStatus, task status to #expectedTaskStatus'() {
        given: 'a UeTrace subscription'
            def subscriptionMO = UeTraceSubscriptionBuilder.newInstance(dpsUtils).build()
            def subscriptionId = subscriptionMO.poId
        and: 'an initiation tracker for #administrationState'
            setupPMICInitiationResponseCache(nodeMO1.fdn, NODE_TYPE, subscriptionId, administrationState)
        and: 'a pm job that just changed status to #pmJobNewStatus'
            ManagedObject pmJobMO1 = pmJobBuilder.nodeName(nodeMO1).processType(UETRACE).subscriptionId(subscriptionId).status(pmJobNewStatus).build()
            DpsAttributeChangedEvent dpsAttributeChangedEvent = createDpsAttributeChangedEvent(pmJobMO1.fdn, EVENT_TYPE_UETRACE, subscriptionId, PM_JOB_STATUS_ATTRIBUTE, pmJobOldStatus, pmJobNewStatus)

        when: 'the event is received'
            objectUnderTest.onEvent(dpsAttributeChangedEvent)

        then: 'the subscription state is #expectedSubsStatus'
            Subscription subscription = subscriptionDao.findOneById(subscriptionId, true)
            subscription.administrationState.name() == expectedSubsStatus
        and: 'the subscription task status is #expectedTaskStatus'
            subscription.taskStatus.name() == expectedTaskStatus
        and: 'the subscription is no longer tracked'
            pmicInitiationTrackerCache.isTracking(String.valueOf(subscriptionId)) == false

        where: 'the following action and status changes are applied'
            administrationState | pmJobOldStatus | pmJobNewStatus || expectedSubsStatus | expectedTaskStatus
            ACTIVATING          | INACTIVE       | ACTIVE         || ACTIVE             | OK
            ACTIVATING          | INACTIVE       | ERROR          || ACTIVE             | ERROR
            ACTIVATING          | INACTIVE       | UNKNOWN        || ACTIVE             | ERROR
            DEACTIVATING        | ACTIVE         | INACTIVE       || INACTIVE           | OK
            DEACTIVATING        | ACTIVE         | ERROR          || INACTIVE           | OK
            DEACTIVATING        | ACTIVE         | UNKNOWN        || INACTIVE           | OK

            // Test for TORF-221797
            // Operator Activates an Inactive Subscription with newly added nodes, at the end Subscription should be Active
            ACTIVATING          | UNKNOWN        | ACTIVE         || ACTIVE             | OK
            ACTIVATING          | UNKNOWN        | ERROR          || ACTIVE             | ERROR
            // This could be a race condition. SM sends activation but received deactivation notifications from dps. Still subscription should be active, which allows next SM to correct.
            ACTIVATING          | UNKNOWN        | INACTIVE       || ACTIVE             | ERROR

            // Operator Deactivates an Active Subscription with newly added nodes, at the end Subscription should be Inactive
            DEACTIVATING        | UNKNOWN        | INACTIVE       || INACTIVE           | OK
            DEACTIVATING        | UNKNOWN        | ERROR          || INACTIVE           | OK
            // This could be a race condition. SM sends deactivation but received activation notifications from dps. Still subscription should be inactive, which allows next SM to correct.
            DEACTIVATING        | UNKNOWN        | ACTIVE         || INACTIVE           | OK
    }

    @Unroll
    def '#administrationState a subscription that supports pm jobs with 2 nodes, receives #newStatus events for nodes #eventForNodes'() {
        given: 'a second node'
            def nodeMO2 = nodeUtil.builder(NODE_NAME_2).neType(NODE_TYPE).build()
        and: 'a UeTrace subscription that is #administrationState'
            SubscriptionBuilder subscriptionBuilder = UeTraceSubscriptionBuilder.newInstance(dpsUtils)
            def subscriptionMO = subscriptionBuilder.administrativeState(AdministrationState.valueOf(administrationState)).build()
            def subscriptionId = subscriptionMO.poId
        and: 'an initiation tracker for for both nodes'
            setupPMICInitiationResponseCache(nodeMO1.fdn, NODE_TYPE, subscriptionId, administrationState)
            setupPMICInitiationResponseCache(nodeMO2.fdn, NODE_TYPE, subscriptionId, administrationState)

        when: 'a pm job that just changed status to #pmJobNewStatus'
            eventForNodes.each({
                def pmJobMO = pmJobBuilder.nodeName(it).processType(UETRACE).subscriptionId(subscriptionId).status(newStatus).build()
                DpsAttributeChangedEvent dpsAttributeChangedEvent = createDpsAttributeChangedEvent(pmJobMO.fdn, EVENT_TYPE_UETRACE, subscriptionId, PM_JOB_STATUS_ATTRIBUTE, oldStatus, newStatus)
                objectUnderTest.onEvent(dpsAttributeChangedEvent)
            })

        then: 'the subscription state is #expectedSubsStatus'
            Subscription subscription = subscriptionDao.findOneById(subscriptionId, true)
            subscription.administrationState.name() == expectedSubscriptionStatus
        and: 'the subscription is no longer tracked'
            pmicInitiationTrackerCache.isTracking(String.valueOf(subscriptionId)) == expectedTracking

        where: 'the following action and status changes are applied'
            administrationState | eventForNodes              | oldStatus | newStatus || expectedSubscriptionStatus | expectedTracking
            ACTIVATING          | [NODE_NAME_1]              | INACTIVE  | ACTIVE    || ACTIVATING                 | true
            ACTIVATING          | [NODE_NAME_1, NODE_NAME_2] | INACTIVE  | ACTIVE    || ACTIVE                     | false
            DEACTIVATING        | [NODE_NAME_1]              | ACTIVE    | INACTIVE  || DEACTIVATING               | true
            DEACTIVATING        | [NODE_NAME_1, NODE_NAME_2] | ACTIVE    | INACTIVE  || INACTIVE                   | false
    }

    @Unroll
    def 'PmJob Status change from #pmJobOldStatus to #pmJobNewStatus without initiation tracker sets subscription task status to #expectedTaskStatus'() {

        given: 'a UeTrace subscription'
            def subscriptionMO = UeTraceSubscriptionBuilder.newInstance(dpsUtils).build()
            long subscriptionId = subscriptionMO.getPoId()
        and: 'a pm job that just changed status to #pmJobNewStatus'
            def pmJobMO1 = pmJobBuilder.nodeName(nodeMO1).processType(UETRACE).subscriptionId(subscriptionId).status(pmJobNewStatus).build()
            DpsAttributeChangedEvent dpsAttributeChangedEvent = createDpsAttributeChangedEvent(pmJobMO1.fdn, EVENT_TYPE_UETRACE, subscriptionId, PM_JOB_STATUS_ATTRIBUTE, pmJobOldStatus, pmJobNewStatus)

        when: 'the event is received'
            objectUnderTest.onEvent(dpsAttributeChangedEvent)

        then: 'the subscription task status should be #expectedTaskStatus'
            Subscription subscription = subscriptionDao.findOneById(subscriptionId, true)
            subscription.taskStatus.name() == expectedTaskStatus

        where: 'the following state changes occur'
            pmJobOldStatus | pmJobNewStatus || expectedTaskStatus
            UNKNOWN        | ACTIVE         || OK
            UNKNOWN        | ERROR          || ERROR
            UNKNOWN        | INACTIVE       || ERROR
            ACTIVE         | ERROR          || ERROR
            ACTIVE         | INACTIVE       || ERROR
            ACTIVE         | UNKNOWN        || ERROR
            INACTIVE       | ERROR          || ERROR
            INACTIVE       | ACTIVE         || OK
            INACTIVE       | UNKNOWN        || ERROR
    }

    @Unroll
    def 'File collection for Ctum subscription with output mode #outputMode'() {
        given: 'a Ctum subscription with output mode #outputMode'
            SubscriptionBuilder subscriptionBuilder = new CtumSubscriptionBuilder(dpsUtils)
            subscriptionBuilder.setAdditionalAttributes(['outputMode': outputMode])
            def subscriptionMO = subscriptionBuilder.build()
            long subscriptionId = subscriptionMO.getPoId()
        and: 'a pm job  that just changed status from INACTIVE to ACTIVE'
            def pmJobMO1 = pmJobBuilder.nodeName(nodeMO1).processType(subscriptionMO).subscriptionId(subscriptionId).status('ACTIVE').build()
            DpsAttributeChangedEvent attributeChangedEvent = createDpsAttributeChangedEvent(pmJobMO1.fdn, EVENT_TYPE_CTUM, subscriptionId, PM_JOB_STATUS_ATTRIBUTE, 'INACTIVE', 'ACTIVE')

        when: 'the event is received'
            objectUnderTest.onEvent(attributeChangedEvent)

        then: 'the task cache contains #expectedFileCollectionTasks file collection task(s)'
            fileCollectionActiveTasksCache.size() == expectedFileCollectionTasks

        where: 'the following output modes are used'
            outputMode           || expectedFileCollectionTasks
            'FILE'               || 1
            'FILE_AND_STREAMING' || 1
            'STREAMING'          || 0
    }

    def setupPMICInitiationResponseCache(final String nodeFdn, final String nodeType, final long subsId, final String administrationState) {
        if (administrationState == ACTIVATING) {
            pmicInitiationTrackerCache.startTrackingActivation(subsId as String, administrationState, [(nodeFdn): nodeType])
        } else {
            pmicInitiationTrackerCache.startTrackingDeactivation(subsId as String, administrationState, [(nodeFdn): nodeType])
        }
    }
}
