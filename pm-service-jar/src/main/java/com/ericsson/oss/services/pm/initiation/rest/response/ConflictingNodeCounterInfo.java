/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2014
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.rest.response;

import java.util.Collection;
import java.util.List;

/**
 * The type Conflicting node counter info for tracking counter conflicts.
 */
public class ConflictingNodeCounterInfo {

    private String subscriptionId;
    private Collection<String> nodes;
    private List<ConflictingCounterGroup> counterEventInfo;

    /**
     * @param subscriptionId
     *         - id of the subscription
     * @param nodes
     *         - list of nodes
     * @param counterEventInfo
     *         - list of counter groups
     */
    public ConflictingNodeCounterInfo(final String subscriptionId, final Collection<String> nodes,
                                      final List<ConflictingCounterGroup> counterEventInfo) {
        super();
        this.subscriptionId = subscriptionId;
        this.counterEventInfo = counterEventInfo;
        this.nodes = nodes;
    }

    /**
     * @return the subscriptionId
     */
    public String getSubscriptionId() {
        return subscriptionId;
    }

    /**
     * @param subscriptionId
     *         the subscriptionId to set
     */
    public void setSubscriptionId(final String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    /**
     * @return the counterEventInfo
     */
    public List<ConflictingCounterGroup> getCounterEventInfo() {
        return counterEventInfo;
    }

    /**
     * @param counterEventInfo
     *         the counterEventInfo to set
     */
    public void setCounterEventInfo(final List<ConflictingCounterGroup> counterEventInfo) {
        this.counterEventInfo = counterEventInfo;
    }

    /**
     * Get the subscription nodes that are used in at least one more subscription and at least one counter in this subscription is the same as the
     * common node to which subscription it belongs to. This makes this attribute with limited usability as you do not know which nodes have what
     * counters active in other subscriptions, but only that these are the nodes who hav at least one of our subscription counters active in other
     * subscriptions.
     *
     * @return the conflictingNodes
     */
    public Collection<String> getNodes() {
        return nodes;
    }

    /**
     * @param nodes
     *         - the conflicting nodes to set
     */
    public void setNodes(final List<String> nodes) {
        this.nodes = nodes;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }

        final ConflictingNodeCounterInfo otherCounterInfo = (ConflictingNodeCounterInfo) obj;

        return (subscriptionId == otherCounterInfo.subscriptionId
                || subscriptionId != null && subscriptionId.equals(otherCounterInfo.getSubscriptionId()))
                && (nodes == otherCounterInfo.nodes || nodes != null && nodes.equals(otherCounterInfo.getNodes()))
                && (counterEventInfo == otherCounterInfo.counterEventInfo
                || counterEventInfo != null && counterEventInfo.equals(otherCounterInfo.getCounterEventInfo()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (subscriptionId == null ? 0 : subscriptionId.hashCode());
        result = prime * result + (nodes == null ? 0 : nodes.hashCode());
        result = prime * result + (counterEventInfo == null ? 0 : counterEventInfo.hashCode());

        return result;
    }
}
