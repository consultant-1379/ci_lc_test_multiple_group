/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.collection.api;

import java.io.Serializable;
import java.util.Objects;

import com.ericsson.oss.services.pm.initiation.cache.model.value.ProcessType;

/**
 * Value Object to carry information about a processType being requested to either start or stop File Collection for a Network Element on a given
 * ropPeriod.
 */
public class ProcessRequestVO implements Serializable {

    private static final long serialVersionUID = -8399448125161853665L;
    private final String nodeAddress;
    private final int ropPeriod;
    private final String processType;
    private final Long startTime;
    private final Long endTime;
    private final String subscriptionType;

    private ProcessRequestVO(final ProcessRequestVOBuilder builder) {
        nodeAddress = builder.nodeAddress;

        ropPeriod = builder.ropPeriod;
        processType = builder.processType;
        startTime = builder.startTime;
        endTime = builder.endTime;
        subscriptionType = ProcessType.valueOf(processType) != ProcessType.OTHER ? ProcessType.valueOf(processType).getSubscriptionType().name() :
                null;
    }

    /**
     * Returns the node address.
     *
     * @return - the address of the node
     */
    public String getSubscriptionType() {
        return subscriptionType;
    }

    /**
     * Returns the node address.
     *
     * @return - the address of the node
     */
    public String getNodeAddress() {
        return nodeAddress;
    }

    /**
     * Returns the ROP period.
     *
     * @return - the Record Output Period
     */
    public int getRopPeriod() {
        return ropPeriod;
    }

    /**
     * Returns the processType.
     *
     * @return - returns the request process type
     */
    public String getProcessType() {
        return processType;
    }

    /**
     * Returns the start time of the process request in milliseconds.
     *
     * @return - returns the start time of the process request
     */
    public Long getStartTime() {
        return startTime;
    }

    /**
     * Returns the end time of the process request in milliseconds.
     *
     * @return - Returns the end time of the process request in milliseconds.
     */
    public Long getEndTime() {
        return endTime;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ProcessRequestVO)) {
            return false;
        }
        final ProcessRequestVO that = (ProcessRequestVO) obj;
        return ropPeriod == that.ropPeriod &&
                Objects.equals(nodeAddress, that.nodeAddress) &&
                Objects.equals(subscriptionType, that.subscriptionType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeAddress, ropPeriod, subscriptionType);
    }

    @Override
    public String toString() {
        return new StringBuilder("ProcessRequestVO [nodeAddress=")
                .append(nodeAddress)
                .append(", ropPeriod=")
                .append(ropPeriod)
                .append(", subscriptionType=")
                .append(subscriptionType)
                .append(", startTime=")
                .append(startTime)
                .append(", endTime=")
                .append(endTime)
                .append("]").toString();
    }

    /**
     * Process request vo builder.
     */
    public static class ProcessRequestVOBuilder {
        private final String nodeAddress;
        private final int ropPeriod;
        private final String processType;
        private Long startTime;
        private Long endTime;

        /**
         * Instantiates a new Process request vo builder.
         *
         * @param nodeAddress
         *         the node address
         * @param ropPeriod
         *         the record output period
         * @param processType
         *         the process type
         */
        public ProcessRequestVOBuilder(final String nodeAddress, final int ropPeriod, final String processType) {
            this.nodeAddress = nodeAddress;
            this.ropPeriod = ropPeriod;
            this.processType = processType;
        }

        /**
         * Start time process request vo builder.
         *
         * @param startTime
         *         the start time
         *
         * @return the process request vo builder
         */
        public ProcessRequestVOBuilder startTime(final long startTime) {
            this.startTime = startTime;
            return this;
        }

        /**
         * End time process request vo builder.
         *
         * @param endTime
         *         the end time
         *
         * @return the process request vo builder
         */
        public ProcessRequestVOBuilder endTime(final long endTime) {
            this.endTime = endTime;
            return this;
        }

        /**
         * Build process request vo.
         *
         * @return the process request vo
         */
        public ProcessRequestVO build() {
            return new ProcessRequestVO(this);
        }
    }

}
