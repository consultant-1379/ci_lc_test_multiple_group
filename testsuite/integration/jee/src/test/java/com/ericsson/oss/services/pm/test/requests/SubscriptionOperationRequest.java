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

package com.ericsson.oss.services.pm.test.requests;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class SubscriptionOperationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String subsDataFile;

    private final Map<String, Map<String, Object>> attributesToBeRetruned;

    private final Map<String, Object> attributesSubscriptionToBeCreatedWith;
    private final Map<String, List<String>> filterToApplyOnSubcriptionSearch;
    private String subscriptionOperation;
    private List<String> subscriptionToRunActionOn;
    private List<String> nodesToBeAddedOrRemoved;

    public SubscriptionOperationRequest(final PmServiceRequestsTypes subscriptionOperation,
                                        final Map<String, Map<String, Object>> attributesToBeRetruned,
                                        final Map<String, Object> attributesSubscriptionToBeCreatedWith,
                                        final Map<String, List<String>> filterToApplyOnSubcriptionSearch, final String subscriptionDataFile) {
        this.subscriptionOperation = subscriptionOperation.name();
        this.attributesToBeRetruned = attributesToBeRetruned;
        this.attributesSubscriptionToBeCreatedWith = attributesSubscriptionToBeCreatedWith;
        this.filterToApplyOnSubcriptionSearch = filterToApplyOnSubcriptionSearch;
        subsDataFile = subscriptionDataFile;
    }

    public SubscriptionOperationRequest(final PmServiceRequestsTypes subscriptionOperation,
                                        final Map<String, Map<String, Object>> attributesToBeRetruned,
                                        final Map<String, Object> attributesSubscriptionToBeCreatedWith,
                                        final String subscriptionDataFile) {
        this.subscriptionOperation = subscriptionOperation.name();
        this.attributesToBeRetruned = attributesToBeRetruned;
        this.attributesSubscriptionToBeCreatedWith = attributesSubscriptionToBeCreatedWith;
        filterToApplyOnSubcriptionSearch = null;
        subsDataFile = subscriptionDataFile;
    }

    /**
     * @return the allSubsData
     */
    public Map<String, Map<String, Object>> getReturnedSubscriptionAttributes() {
        return attributesToBeRetruned;
    }

    public Map<String, Object> getAttributesSubscriptionToBeCreatedWith() {
        return attributesSubscriptionToBeCreatedWith;
    }

    public String getSubscriptionDataFile() {
        return subsDataFile;
    }

    public List<String> getNodesToBeAddedOrRemoved() {
        return nodesToBeAddedOrRemoved;
    }

    public void setNodesToBeAddedOrRemoved(final List<String> nodesToBeAddedOrRemoved) {
        this.nodesToBeAddedOrRemoved = nodesToBeAddedOrRemoved;
    }

    /**
     * @return the subscriptionOperation
     */
    public String getSubscriptionOperation() {
        return subscriptionOperation;
    }

    /**
     * @return the subscriptionToRunActionOn
     */
    public List<String> getSubscriptionToRunActionOn() {
        return subscriptionToRunActionOn;
    }

    /**
     * @param subscriptionToRunActionOn
     *         the subscriptionToRunActionOn to set
     */
    public void setSubscriptionToRunActionOn(final List<String> subscriptionToRunActionOn) {
        this.subscriptionToRunActionOn = subscriptionToRunActionOn;
    }

}
