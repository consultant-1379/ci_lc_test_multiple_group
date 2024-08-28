/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.cbs.service.api;

import javax.ejb.Local;

/**
 * This API provides method to trigger audit Subscriptions and holds the business logic to audit subscriptions.
 */
@Local
public interface CBSAuditCoreLocal {

    /**
     * Audits CBS enabled Subscriptions by retrieving the subscriptions from DPS.
     */
    void auditSubscriptions();
}
