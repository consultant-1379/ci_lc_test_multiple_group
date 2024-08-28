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

package com.ericsson.oss.services.pm.collection.notification.handlers;

import com.ericsson.oss.services.pm.initiation.model.resource.PMICScannerStatus;

/**
 * The Scanner operation vo.
 */
public class ScannerOperationVO {

    private final String scannerFdn;
    private final String processType;
    private String scannerStatus;
    private String subscriptionId;
    private Integer ropTimeInSeconds;
    private boolean fileCollectionEnabled;

    /**
     * Instantiates a new Scanner operation vo.
     *
     * @param scannerStatus
     *         the scanner status
     * @param subscriptionId
     *         the subscription id
     * @param ropTimeInSeconds
     *         the record output period time in seconds
     * @param scannerFdn
     *         the scanner fully distinguished name
     * @param processType
     *         the process type
     */
    public ScannerOperationVO(final String scannerStatus, final String subscriptionId, final Integer ropTimeInSeconds, final String scannerFdn,
                              final String processType) {
        this.scannerStatus = scannerStatus;
        this.subscriptionId = subscriptionId;
        this.ropTimeInSeconds = ropTimeInSeconds;
        this.scannerFdn = scannerFdn;
        this.processType = processType;
    }

    /**
     * Gets scanner status.
     *
     * @return the scannerStatus
     */
    public String getScannerStatus() {
        return scannerStatus;
    }

    /**
     * Sets scanner status.
     *
     * @param scannerStatus
     *         the scannerStatus to set
     */
    public void setScannerStatus(final String scannerStatus) {
        this.scannerStatus = scannerStatus;
    }

    /**
     * Gets subscription id.
     *
     * @return the subscriptionId
     */
    public String getSubscriptionId() {
        return subscriptionId;
    }

    /**
     * Sets subscription id.
     *
     * @param subscriptionId
     *         the subscriptionId to set
     */
    public void setSubscriptionId(final String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    /**
     * Gets record output period time in seconds.
     *
     * @return the ropTimeInSeconds
     */
    public Integer getRopTimeInSeconds() {
        return ropTimeInSeconds;
    }

    /**
     * Sets record output period time in seconds.
     *
     * @param ropTimeInSeconds
     *         the ropTimeInSeconds to set
     */
    public void setRopTimeInSeconds(final Integer ropTimeInSeconds) {
        this.ropTimeInSeconds = ropTimeInSeconds;
    }

    /**
     * Gets scanner fully distinguished name.
     *
     * @return the scannerFdn
     */
    public String getScannerFdn() {
        return scannerFdn;
    }

    /**
     * @return returns true if file collection is enabled
     */
    public boolean isFileCollectionEnabled() {
        return fileCollectionEnabled;
    }

    /**
     * Sets file collection enabled.
     *
     * @param fileCollectionEnabled
     *         sets file collection enabled to true or false
     */
    public void setFileCollectionEnabled(final boolean fileCollectionEnabled) {
        this.fileCollectionEnabled = fileCollectionEnabled;
    }

    /**
     * Gets process type.
     *
     * @return the processType
     */
    public String getProcessType() {
        return processType;
    }

    /**
     * @return returns true if Scanner is Active
     */
    public boolean isScannerActive() {
        return getScannerStatus().equalsIgnoreCase(PMICScannerStatus.ACTIVE.name());
    }
}
