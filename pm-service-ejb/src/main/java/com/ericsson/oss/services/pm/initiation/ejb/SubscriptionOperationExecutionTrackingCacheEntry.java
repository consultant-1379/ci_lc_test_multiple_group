/*******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.initiation.ejb;

import java.util.Date;
import java.util.List;

import com.ericsson.oss.pmic.dto.node.Node;

/**
 * Value Object for extracting entries from {@link SubscriptionOperationExecutionTrackingCacheWrapper}.
 */
public class SubscriptionOperationExecutionTrackingCacheEntry {

    private final Long subscriptionId;
    private final String operation;
    private final Date createdDate;
    private final List<Node> nodes;

    /**
     * Constructor
     *
     * @param subscriptionId
     *         - the subscription ID
     * @param operation
     *         - action to perform
     * @param createdDate
     *         - date this entry was created in the cache
     * @param nodes
     *         - the list of nodes to activate/deactivate. This list can be empty.
     */
    public SubscriptionOperationExecutionTrackingCacheEntry(final Long subscriptionId, final String operation, final Date createdDate,
                                                            final List<Node> nodes) {
        this.subscriptionId = subscriptionId;
        this.operation = operation;
        this.createdDate = createdDate;
        this.nodes = nodes;
    }

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public String getOperation() {
        return operation;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public List<Node> getNodes() {
        return nodes;
    }
}
