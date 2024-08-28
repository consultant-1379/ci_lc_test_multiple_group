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
package com.ericsson.oss.services.pm.initiation.ejb

import javax.inject.Inject

import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache
import com.ericsson.oss.services.pm.initiation.cache.data.InitiationTracker

class GroovyTestUtils {

    @Inject
    private PMICInitiationTrackerCache trackerCache;

    public long getTotalNodesToBeActivated() {
        long totalNodes = 0;
        for (InitiationTracker initiationTracker : trackerCache.getAllTrackers()) {
            totalNodes += initiationTracker.getTotalAmountOfExpectedNotifications()
        }
        return totalNodes;
    }

    public InitiationTracker getTrackerEntry(final String subscriptionId) {
        return trackerCache.getTracker(subscriptionId)
    }
}
