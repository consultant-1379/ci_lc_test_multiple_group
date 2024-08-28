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

package com.ericsson.oss.services.pm.initiation.utils;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Initiation timing info for network element.
 */
public class InitiationTimingInfoForNe {

    private final AtomicInteger numberOfNodes = new AtomicInteger(1);

    private final int neInitiationTimeout;

    private final int neReplyWorstDelay;

    /**
     * Instantiates a new Initiation timing info for network element.
     *
     * @param neInitiationTimeoutValue
     *         the network element initiation timeout value
     * @param neReplyWorstDelayValue
     *         the network element reply worst delay value
     */
    public InitiationTimingInfoForNe(final int neInitiationTimeoutValue, final int neReplyWorstDelayValue) {
        neInitiationTimeout = neInitiationTimeoutValue;
        neReplyWorstDelay = neReplyWorstDelayValue;
    }

    /**
     * Gets number of nodes.
     *
     * @return the number of nodes
     */
    public int getNumberOfNodes() {
        return numberOfNodes.get();
    }

    /**
     * Gets network element timeout.
     *
     * @return the network element timeout
     */
    public int getNeTimeout() {
        return neInitiationTimeout;
    }

    /**
     * Gets network element reply worst delay.
     *
     * @return the network element reply worst delay
     */
    public int getNeReplyWorstDelay() {
        return neReplyWorstDelay;
    }

    /**
     * Increment number of nodes.
     */
    public void incrementNumberOfNodes() {
        numberOfNodes.incrementAndGet();
    }
}
