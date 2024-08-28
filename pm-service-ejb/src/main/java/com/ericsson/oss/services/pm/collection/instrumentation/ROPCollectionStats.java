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

package com.ericsson.oss.services.pm.collection.instrumentation;

/**
 * Rop collection stats class.
 */
public class ROPCollectionStats {

    private long numberOfFilesCollected;
    private long numberOfFilesFailed;
    private long numberOfBytesStored;
    private long numberOfBytesTransferred;
    private long collectionStartTime;
    private long collectionEndTime;

    /**
     * Returns the count of files collected
     *
     * @return the numberOfFilesCollected
     */
    public long getNumberOfFilesCollected() {
        return numberOfFilesCollected;
    }

    /**
     * Sets the count of files Collected to the value passed as a parameter. Note that a call to this method will overwrite the current value.
     *
     * @param numberOfFilesCollected
     *         - The value to set for the metric of files collected
     */
    public void setNumberOfFilesCollected(final long numberOfFilesCollected) {
        this.numberOfFilesCollected = numberOfFilesCollected;
    }

    /**
     * Increases the count of files collected by a single unit (1)
     */
    public void incrementNumberOfFilesCollected() {
        incrementNumberOfFilesCollectedBy(1);
    }

    /**
     * Increases the count of files collected by the increment passed as a parameter.
     *
     * @param increment
     *         -The number of units to increment the count of files collected
     */
    public void incrementNumberOfFilesCollectedBy(final long increment) {
        numberOfFilesCollected += increment;
    }

    /**
     * Returns the count of files that failed to be collected
     *
     * @return the numberOfFilesFailed
     */
    public long getNumberOfFilesFailed() {
        return numberOfFilesFailed;
    }

    /**
     * Sets the count of files that failed to be Collected to the value passed as a parameter. Note that a call to this method will overwrite the
     * current value.
     *
     * @param numberOfFilesFailed
     *         - The value to set for the metric of files that failed to be collected
     */
    public void setNumberOfFilesFailed(final long numberOfFilesFailed) {
        this.numberOfFilesFailed = numberOfFilesFailed;
    }

    /**
     * Increases the count of files that failed to be collected by a single unit (1)
     */
    public void incrementNumberOfFilesFailed() {
        incrementNumberOfFilesFailedBy(1);
    }

    /**
     * Increases the count of files that failed to be collected by the increment passed as a parameter.
     *
     * @param increment
     *         - the number of files failed for current Record Output Period to set
     */
    public void incrementNumberOfFilesFailedBy(final long increment) {
        numberOfFilesFailed += increment;
    }

    /**
     * Returns the count of bytes stored
     *
     * @return the numberOfBytesStored
     */
    public long getNumberOfBytesStored() {
        return numberOfBytesStored;
    }

    /**
     * Sets the number of bytes stored to the value passed as a parameter. Note that this method will overwrite the current value.
     *
     * @param numberOfBytesStored
     *         - The number of bytes stored
     */
    public void setNumberOfBytesStored(final long numberOfBytesStored) {
        this.numberOfBytesStored = numberOfBytesStored;
    }

    /**
     * Increases the count of bytes stored by the increment passed as a parameter.
     *
     * @param numberOfBytesStored
     *         - the number of bytes stored current Record Output Period to add to the current count
     */
    public void incrementNumberOfBytesStored(final long numberOfBytesStored) {
        this.numberOfBytesStored += numberOfBytesStored;
    }

    /**
     * Returns the number of bytes transferred
     *
     * @return the numberOfBytesTransferred
     */
    public long getNumberOfBytesTransferred() {
        return numberOfBytesTransferred;
    }

    /**
     * Sets the number of bytes transferred to the value passed as a parameter. Note that this method will overwrite the current value.
     *
     * @param numberOfBytesTransferred
     *         - The number of bytes transfered
     */
    public void setNumberOfBytesTransferred(final long numberOfBytesTransferred) {
        this.numberOfBytesTransferred = numberOfBytesTransferred;
    }

    /**
     * Increases the count of bytes transferred by the increment passed as a parameter.
     *
     * @param numberOfBytesTransferred
     *         - the number of bytes transfered for current Record Output Period to set
     */
    public void incrementNumberOfBytesTransferred(final long numberOfBytesTransferred) {
        this.numberOfBytesTransferred += numberOfBytesTransferred;
    }

    /**
     * Returns the collection start t
     *
     * @return the Record Output Period start time
     */
    public long getCollectionStartTime() {
        return collectionStartTime;
    }

    /**
     * Sets the Record Output Period start time to the given value. The parameter received must represent a timestamp in milliseconds
     *
     * @param collectionStartTime
     *         - The Record Output Period start time in milliseconds
     */
    public void setCollectionStartTime(final long collectionStartTime) {
        this.collectionStartTime = collectionStartTime;
    }

    /**
     * Returns the Record Output Period start time in milliseconds
     *
     * @return the Record Output Period end time
     */
    public long getCollectionEndTime() {
        return collectionEndTime;
    }

    /**
     * Sets the Record Output Period end time to the given value. The parameter received must represent a timestamp in milliseconds
     *
     * @param collectionEndTime
     *         - The Record Output Period end time in milliseconds
     */
    public void setCollectionEndTime(final long collectionEndTime) {
        this.collectionEndTime = collectionEndTime;
    }

    /**
     * Reset the state of all the metrics maintained by this instance by initializing them to zero:
     */
    public void resetCounters() {
        numberOfFilesCollected = 0;
        numberOfFilesFailed = 0;
        numberOfBytesStored = 0;
        numberOfBytesTransferred = 0;
        collectionStartTime = 0;
        collectionEndTime = 0;

    }

}
