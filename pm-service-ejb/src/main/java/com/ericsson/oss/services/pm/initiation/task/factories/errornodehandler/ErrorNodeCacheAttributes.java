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

package com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler;

import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE;

/**
 * Attributes for use by the error node cache
 */
public class ErrorNodeCacheAttributes {

    public static final String STORED_REQUESTS_KEY = "storedKey";
    public static final String FDN = "fdn";
    public static final String NAME = "name";
    public static final String PROCESS_TYPE_ATTRIBUTE = "processType";
    public static final String STATUS = "status";
    public static final String SUBSCRIPTION_ID = SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE;
    public static final String STORED_REQUESTS = "storedRequests";
    public static final String ERROR_HANDLER_PROCESS_TYPE = "errorHandlerProcessType";

    private ErrorNodeCacheAttributes() { }
}
