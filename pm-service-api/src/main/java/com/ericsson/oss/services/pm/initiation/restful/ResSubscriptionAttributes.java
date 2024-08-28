/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.restful;

import java.util.Map;
import java.util.Set;

import com.ericsson.oss.services.pm.initiation.restful.util.ResSubscriptionHelper;

/**
 * Class that holds required attributes allowed values to be sent to UI for selection
 */
public class ResSubscriptionAttributes {

    private Map<String, Set<Integer>> supportedSamplingRates;
    private Set<Integer> supportedResUeFraction;
    private Set<String> supportedResRmq;
    private Set<String> supportedResServices;
    private Map<String, String> resServiceCategoryMapping;
    private Set<Integer> supportedResSpreadingFactor;

    /**
     * @param supportedSamplingRates
     *         - the supportedSamplingRates to set
     * @param supportedResUeFraction
     *         - the supportedResUeFraction to set
     * @param supportedResRmq
     *         - the supportedResRmq to set
     * @param supportedResServices
     *         - the supportedResServices to set
     * @param supportedResSpreadingFactor
     *         - the supported spreading factor to set
     */
    public ResSubscriptionAttributes(final Map<String, Set<Integer>> supportedSamplingRates, final Set<Integer> supportedResUeFraction,
                                     final Set<String> supportedResRmq, final Set<String> supportedResServices,
                                     final Set<Integer> supportedResSpreadingFactor) {
        this.supportedSamplingRates = supportedSamplingRates;
        this.supportedResUeFraction = supportedResUeFraction;
        this.supportedResRmq = supportedResRmq;
        this.supportedResServices = supportedResServices;
        this.resServiceCategoryMapping = ResSubscriptionHelper.getResServiceCategoryMapping();
        this.supportedResSpreadingFactor = supportedResSpreadingFactor;
    }

    /**
     * @return the supportedSamplingRates
     */
    public Map<String, Set<Integer>> getSupportedSamplingRates() {
        return supportedSamplingRates;
    }

    /**
     * @param supportedSamplingRates
     *         the supportedSamplingRates to set
     */
    public void setSupportedSamplingRates(final Map<String, Set<Integer>> supportedSamplingRates) {
        this.supportedSamplingRates = supportedSamplingRates;
    }

    /**
     * @return the supportedResUeFraction
     */
    public Set<Integer> getSupportedResUeFraction() {
        return supportedResUeFraction;
    }

    /**
     * @param supportedResUeFraction
     *         the supportedResUeFraction to set
     */
    public void setSupportedResUeFraction(final Set<Integer> supportedResUeFraction) {
        this.supportedResUeFraction = supportedResUeFraction;
    }

    /**
     * @return the supportedResRmq
     */
    public Set<String> getSupportedResRmq() {
        return supportedResRmq;
    }

    /**
     * @param supportedResRmq
     *         the supportedResRmq to set
     */
    public void setSupportedResRmq(final Set<String> supportedResRmq) {
        this.supportedResRmq = supportedResRmq;
    }

    /**
     * @return the supportedResServices
     */
    public Set<String> getSupportedResServices() {
        return supportedResServices;
    }

    /**
     * @param supportedResServices
     *         the supportedResServices to set
     */
    public void setSupportedResServices(final Set<String> supportedResServices) {
        this.supportedResServices = supportedResServices;
    }

    /**
     * @return the resServiceCategoryMapping
     */
    public Map<String, String> getResServiceCategoryMapping() {
        return resServiceCategoryMapping;
    }

    /**
     * @param resServiceCategoryMapping
     *         the resServiceCategoryMapping to set
     */
    public void setResServiceCategoryMapping(final Map<String, String> resServiceCategoryMapping) {
        this.resServiceCategoryMapping = resServiceCategoryMapping;
    }

    /**
     * @return the supportedResSpreadingFactor
     */
    public Set<Integer> getSupportedResSpreadingFactor() {
        return supportedResSpreadingFactor;
    }

    /**
     * @param supportedResSpreadingFactor
     *         the supportedResSpreadingFactor to set
     */
    public void setSupportedResSpreadingFactor(final Set<Integer> supportedResSpreadingFactor) {
        this.supportedResSpreadingFactor = supportedResSpreadingFactor;
    }
}
