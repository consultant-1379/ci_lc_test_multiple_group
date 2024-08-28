/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.collection.cache.utils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;

/**
 * Class used to define sorting order of know {@link com.ericsson.oss.services.pm.initiation.input.model.subscription.SubscriptionType} when
 * {@link com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest} are sent with event sender.
 */
public class FileCollectionSorter {

    /**
     * Sort file collection task requests
     *
     * @param tasks
     *         - the FileCollectionTaskRequests to sort
     */
    public void sortFileCollectionTaskRequests(final List<FileCollectionTaskWrapper> tasks) {
        Collections.sort(tasks, Comparator.comparing((FileCollectionTaskWrapper object) ->
                sortedPosition(object.getFileCollectionTaskRequest().getSubscriptionType()))
        );
    }

    private int sortedPosition(final String subscriptionType) {
        try {
            return FileCollectionSortOrder.valueOf(subscriptionType).ordinal();
        } catch (final IllegalArgumentException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private enum FileCollectionSortOrder {
        STATISTICAL,
        CELLTRACE,
        CONTINUOUSCELLTRACE,
        UETRACE,
        EBM,
        CTUM,
        EVENTS,
        GPEH,
        EBS,
        UETR,
        CTR,
        MOINSTANCE,
        CELLTRAFFIC,
        CELLRELATION,
        RESOURCE,
        RES,
        BSCRECORDINGS,
        MTR,
        RPMO,
        RTT
    }
}
