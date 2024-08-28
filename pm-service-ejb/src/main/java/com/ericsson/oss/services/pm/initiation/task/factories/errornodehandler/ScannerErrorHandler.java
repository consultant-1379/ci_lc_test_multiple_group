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
package com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler;

import java.util.Map;

/**
 * The implementers of this interface are meant to resolve problems on nodes that are causing a subscription to be in ERROR
 * When a scanner update notification is received this resolve method is triggered
 */
public interface ScannerErrorHandler {

    /**
     * Attempt to resolve problems on nodes that are causing a subscription to be in ERROR
     *
     * @param processType
     *         - A string to determine which handler instance to choose
     * @param attributes
     *         - Any attributes that are required
     *
     * @return true if the problem was resolved
     */
    boolean process(final ErrorNodeCacheProcessType processType, final Map<String, Object> attributes);
}
