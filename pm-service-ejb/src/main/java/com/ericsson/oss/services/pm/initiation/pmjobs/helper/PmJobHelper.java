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

package com.ericsson.oss.services.pm.initiation.pmjobs.helper;

import java.util.List;
import java.util.Set;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.DataAccessException;

/**
 * Helper class to provide information to be used in PmJob Sync for all PmJob Supported subscriptions
 */
public interface PmJobHelper {

    /**
     * Build all possible active PmJob Names for the nodes and active subscriptions available in the system
     *
     * @return Set of active PmJob names
     * @throws DataAccessException
     *         - thrown if the database cannot be reached
     */
    Set<String> buildAllActivePmJobNames() throws DataAccessException;

    /**
     * Build all possible in-active PmJob Names for the nodes and in-active subscriptions available in the system
     *
     * @return Set of in-active PmJob names
     * @throws DataAccessException
     *         - thrown if the database cannot be reached
     */
    Set<String> buildAllInactivePmJobNames() throws DataAccessException;

    /**
     * Gets all the subscription ids which includes active and in-active subscriptions and supported by PmJob
     *
     * @return Set of all subscription id
     * @throws DataAccessException
     *         - thrown if the database cannot be reached
     * @throws DataAccessException
     *         - if any Exception is thrown when accessing Data Access layer.
     */
    Set<String> getAllSubscriptionIdsSupportedByPmJob() throws DataAccessException;

    /**
     * Gets all the nodes needed for PmJob
     *
     * @return Set of supported nodes
     * @throws DataAccessException
     *         if database cannot be reached
     */
    Set<Node> getAllPmJobSupportedNodes() throws DataAccessException;

    /**
     * Gets all the PmJobSupported nodes for the given subscription type
     *
     * @param subscriptionType
     *         Subscription Type
     *
     * @return List of Supported Nodes
     * @throws DataAccessException
     *         if database cannot be reached
     */
    List<Node> getAllPmJobSupportedNodesBySubscriptionType(final SubscriptionType subscriptionType) throws DataAccessException;

    /**
     * Gets all the PmJobSupported nodes filtered by given pmFunction for the given subscription type
     *
     * @param subscriptionType
     *         Subscription Type
     * @param pmFunction
     *         pmFunction
     *
     * @return List of Supported Nodes filtered by given pmFunction
     * @throws DataAccessException
     *         - if any Exception is thrown when accessing Data Access layer.
     */
    List<Node> getAllPmJobSupportedNodesBySubscriptionTypeAndPmFunction(final SubscriptionType subscriptionType, final boolean pmFunction)
            throws DataAccessException;

    /**
     * Checks if PmJob is supported for the given process type
     *
     * @param processType
     *         Process Type
     *
     * @return true if supported else false
     */
    boolean isPmJobSupported(final String processType);

    /**
     * Checks if PMIC wide file collection is supported or not. For Ex. Ctum is supporting Common file collection mechanism but UeTrace uses there own
     * scheduler to perform file collection.
     *
     * @param processType
     *         Process Type
     *
     * @return true if supported otherwise false
     */
    boolean isCommonFileCollectionSupported(final String processType);
}
