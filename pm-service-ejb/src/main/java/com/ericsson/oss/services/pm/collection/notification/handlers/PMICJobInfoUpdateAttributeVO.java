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

/**
 * Value Object for updated attributes of PMICJobInfo
 */
public class PMICJobInfoUpdateAttributeVO {

    private boolean isStatusAttributeUpdated;
    private String newStatusValue;
    private String oldStatusValue;

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

    /**
     * @return the Old Status Value
     */
    public String getOldStatusValue() {
        return oldStatusValue;
    }

    /**
     * @param oldStatusValue
     *         the Old status value to set
     */
    public void setOldStatusValue(final String oldStatusValue) {
        this.oldStatusValue = oldStatusValue;
    }
}
