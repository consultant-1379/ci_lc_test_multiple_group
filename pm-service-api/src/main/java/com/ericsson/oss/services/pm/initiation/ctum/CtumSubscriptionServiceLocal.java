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

package com.ericsson.oss.services.pm.initiation.ctum;

import java.util.List;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.services.pm.exception.ServiceException;

/**
 * This API provides general methods CTUM Subscriptions and holds the business logic.
 */
public interface CtumSubscriptionServiceLocal {

    /**
     * CTUM subscription created when ENM install/upgrade and there are some SGSN-MME nodes present.
     */
    void ctumAudit();

    /**
     * Get list of nodes available for Ctum Subscription
     *
     * @return list of SGSN-MME nodes
     * @throws ServiceException
     *         - if nodes cannot be extracted from Database
     */
    List<Node> getSupportedNodesForCtumWithPmFunctionOnAndNonEmptyOssModelIdentity() throws ServiceException;
}
