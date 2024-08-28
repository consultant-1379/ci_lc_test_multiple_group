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

package com.ericsson.oss.services.pm.initiation.pmjobs.helper.subscriptions;

import java.util.List;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.services.pm.exception.DataAccessException;

/**
 * Provides the information related to Subscription
 */
public interface SubscriptionInfo {

    /**
     * Gets Process Type
     *
     * @return Process Type
     */
    ProcessType getProcessType();

    /**
     * Gets all the PmJob supported nodes for the subscription type
     *
     * @return List of supported nodes
     * @throws DataAccessException
     *         if database cannot be reached
     */
    List<Node> getSupportedNodesForPmJobs() throws DataAccessException;

    /**
     * Checks if PMIC wide file collection is supported or not. For Ex. Ctum is supporting Common file collection mechanism but UeTrace uses there own
     * scheduler to perform file collection.
     *
     * @return true if supported otherwise false
     */
    boolean isCommonFileCollectionSupported();
}
