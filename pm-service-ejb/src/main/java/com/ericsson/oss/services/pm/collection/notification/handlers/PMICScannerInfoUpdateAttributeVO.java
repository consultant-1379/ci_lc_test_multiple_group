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

/**
 * The Pmic scanner info update attribute vo, for checking if attributes have been updated.
 */
public class PMICScannerInfoUpdateAttributeVO {

    private boolean isFileCollectionEnabledAttributeUpdated;

    private boolean isStatusAttributeUpdated;

    private boolean isSubscriptionIdUpdated;

    private boolean fileCollectionEnabledNewValue;

    private boolean isRopPeriodUpdated;

    private String newStatusValue;

    private String oldStatusValue;

    private Integer newRopPeriodValue;

    private Integer oldRopPeriodValue;

    /**
     * @return the isFileCollectionEnabledAttributeUpdated
     */
    public boolean isFileCollectionEnabledAttributeUpdated() {
        return isFileCollectionEnabledAttributeUpdated;
    }

    /**
     * @param isFileCollectionEnabledAttributeUpdated
     *         the isFileCollectionEnabledAttributeUpdated to set
     */
    public void setFileCollectionEnabledAttributeUpdated(final boolean isFileCollectionEnabledAttributeUpdated) {
        this.isFileCollectionEnabledAttributeUpdated = isFileCollectionEnabledAttributeUpdated;
    }

    /**
     * @return the isSubscriptionIdUpdated
     */
    public boolean isSubscriptionIdUpdated() {
        return isSubscriptionIdUpdated;
    }

    /**
     * @param isSubscriptionIdUpdated
     *         the isSubscriptionIdUpdated to set
     */
    public void setSubscriptionIdUpdated(final boolean isSubscriptionIdUpdated) {
        this.isSubscriptionIdUpdated = isSubscriptionIdUpdated;
    }

    /**
     * @return the isStatusAttributeUpdated
     */
    public boolean isStatusAttributeUpdated() {
        return isStatusAttributeUpdated;
    }

    /**
     * @param isStatusAttributeUpdated
     *         the isStatusAttributeUpdated to set
     */
    public void setStatusAttributeUpdated(final boolean isStatusAttributeUpdated) {
        this.isStatusAttributeUpdated = isStatusAttributeUpdated;
    }

    /**
     * @return the newFileCollectionEnabledValue
     */
    public boolean isFileCollectionEnabledNewValue() {
        return fileCollectionEnabledNewValue;
    }

    /**
     * @param fileCollectionEnabledNewValue
     *         the newFileCollectionEnabledValue to set
     */
    public void setFileCollectionEnabledNewValue(final boolean fileCollectionEnabledNewValue) {
        this.fileCollectionEnabledNewValue = fileCollectionEnabledNewValue;
    }

    /**
     * @return the newStatusValue
     */
    public String getNewStatusValue() {
        return newStatusValue;
    }

    /**
     * @param newStatusValue
     *         the newStatusValue to set
     */
    public void setNewStatusValue(final String newStatusValue) {
        this.newStatusValue = newStatusValue;
    }

    public String getOldStatusValue() {
        return oldStatusValue;
    }

    public void setOldStatusValue(final String oldStatusValue) {
        this.oldStatusValue = oldStatusValue;
    }

    public boolean isRopPeriodUpdated() {
        return isRopPeriodUpdated;
    }

    public void setRopPeriodUpdated(final boolean isRopPeriodUpdated) {
        this.isRopPeriodUpdated = isRopPeriodUpdated;
    }

    public Integer getNewRopPeriodValue() {
        return newRopPeriodValue;
    }

    public void setNewRopPeriodValue(final Integer newRopPeriodValue) {
        this.newRopPeriodValue = newRopPeriodValue;
    }

    public Integer getOldRopPeriodValue() {
        return oldRopPeriodValue;
    }

    public void setOldRopPeriodValue(final Integer oldRopPeriodValue) {
        this.oldRopPeriodValue = oldRopPeriodValue;
    }
}
