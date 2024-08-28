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

package com.ericsson.oss.services.pm.collection.notification.handlers.initiationresponsecache.handlers

import spock.lang.Unroll

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache

class InitiationResponseCacheHelperSpec extends CdiSpecification {

    @ObjectUnderTest
    InitiationResponseCacheHelper objectUnderTest

    @Inject
    private PMICInitiationTrackerCache pmicInitiationTrackerCache;

    def subscriptionId = '123'
    def nodeType = 'some node type'

    @Unroll
    def 'Subscription #action on node #nodeFdn'() {
        given: 'a subscription that is #action on node A'
            if (action == 'activating') {
                pmicInitiationTrackerCache.startTrackingActivation(subscriptionId, 'DOES NOT MATTER', ['node A': nodeType])
            } else {
                pmicInitiationTrackerCache.startTrackingDeactivation(subscriptionId, 'ACTIVE', ['node A': nodeType])
            }

        when: 'a scanner status change for node #nodeFdn is received'
            def successfullyProcessed = objectUnderTest.processInitiationResponseCache(subscriptionId, nodeFdn)

        then: 'the notification is processed : #expectToBeProcessed'
            successfullyProcessed == expectToBeProcessed

        where: 'the following parameters are used'
            action         | nodeFdn      || expectToBeProcessed
            'activating'   | 'node A'     || true
            'activating'   | 'other node' || false
            'deactivating' | 'node A'     || true
            'deactivating' | 'other node' || false
    }


}