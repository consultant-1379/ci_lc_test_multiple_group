/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.model.metadata.events;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;

import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.services.pm.initiation.restful.api.EventTableRow;

/**
 * The type Events per mim version local cache.
 */
@Singleton
public class EventsPerMimVersionLocalCache {
    private final Map<NodeTypeAndVersion, Collection<EventTableRow>> eventsToMimMap;

    /**
     * Instantiates a new Events per mim version local cache.
     */
    public EventsPerMimVersionLocalCache() {
        eventsToMimMap = new HashMap<>();
    }

    /**
     * Get collection.
     *
     * @param nodeTypeAndVersionKey
     *         the node type and version key
     *
     * @return the pm mo class collection
     */
    @Lock(LockType.READ)
    public Collection<EventTableRow> get(final NodeTypeAndVersion nodeTypeAndVersionKey) {
        return eventsToMimMap.get(nodeTypeAndVersionKey);
    }

    /**
     * Put.
     *
     * @param nodeTypeAndVersionKey
     *         the node type and version key
     * @param pmMoClassCollectionValue
     *         the pm mo class collection value
     */
    @Lock(LockType.WRITE)
    public void put(final NodeTypeAndVersion nodeTypeAndVersionKey, final Collection<EventTableRow> pmMoClassCollectionValue) {
        eventsToMimMap.put(nodeTypeAndVersionKey, pmMoClassCollectionValue);
    }
}
