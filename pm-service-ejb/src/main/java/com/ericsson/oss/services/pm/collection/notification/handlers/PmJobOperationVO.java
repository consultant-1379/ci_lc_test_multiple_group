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

package com.ericsson.oss.services.pm.collection.notification.handlers;

import com.ericsson.oss.services.pm.initiation.model.resource.PMICJobStatus;

/**
 * Value Object for PmJob Operations
 */
public class PmJobOperationVO {

    private final String pmJobFdn;
    private final String processType;
    private String pmJobStatus;
    private String subscriptionId;
    private Integer ropTimeInSeconds;

    /**
     * Constructor
     *
     * @param pmJobStatus
     *         PmJob Status
     * @param subscriptionId
     *         Subscription Id
     * @param ropTimeInSeconds
     *         Rop time in seconds
     * @param pmJobFdn
     *         PmJob FDN
     * @param processType
     *         Process Type
     */
    public PmJobOperationVO(final String pmJobStatus, final String subscriptionId, final Integer ropTimeInSeconds, final String pmJobFdn,
                            final String processType) {
        this.pmJobStatus = pmJobStatus;
        this.subscriptionId = subscriptionId;
        this.ropTimeInSeconds = ropTimeInSeconds;
        this.pmJobFdn = pmJobFdn;
        this.processType = processType;
    }

    /**
     * @return the pmJobStatus
     */
    public String getPmJobStatus() {
        return pmJobStatus;
    }

    /**
     * @param pmJobStatus
     *         the pmJobStatus to set
     */
    public void setPmJobStatus(final String pmJobStatus) {
        this.pmJobStatus = pmJobStatus;
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
     * @return the ropTimeInSeconds
     */
    public Integer getRopTimeInSeconds() {
        return ropTimeInSeconds;
    }

    /**
     * @param ropTimeInSeconds
     *         the ropTimeInSeconds to set
     */
    public void setRopTimeInSeconds(final Integer ropTimeInSeconds) {
        this.ropTimeInSeconds = ropTimeInSeconds;
    }

    /**
     * @return the pmJobFdn
     */
    public String getPmJobFdn() {
        return pmJobFdn;
    }

    /**
     * @return the processType
     */
    public String getProcessType() {
        return processType;
    }

    /**
     * @return if PmJob is Active
     */
    public boolean isPmJobActive() {
        return PMICJobStatus.ACTIVE.name().equalsIgnoreCase(getPmJobStatus());
    }
}
