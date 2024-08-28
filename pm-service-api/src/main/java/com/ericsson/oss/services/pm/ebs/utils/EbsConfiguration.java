/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.ebs.utils;

/**
 * Ebs configuration.
 */
public interface EbsConfiguration {

    /**
     * Is ebsm enabled check.
     *
     * @return the true if ebsm is enabled
     */
    boolean isEbsmEnabled();

    /**
     * Is EBSStreamCluster Deployed check.
     *
     * @return the true if EBSStreamCluster is deployed
     */
    boolean isEbsStreamClusterDeployed();

    /**
     * Get EBSLFileIntervalRopInMinutes.
     *
     * @return the value of the interval Rop in minutes.
     */
    String getPmicEbslRopInMinutes();

    /**
     * Is EBSStreamCluster or ASRStreamCluster Deployed check.
     *
     * @return the true if EBSStreamCluster or ASRStreamCluster is deployed
     */
    boolean isEbsOrAsrStreamClusterDeployed();
}
