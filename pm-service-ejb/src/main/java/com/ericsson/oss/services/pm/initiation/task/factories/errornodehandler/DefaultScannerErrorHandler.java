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
import javax.enterprise.context.ApplicationScoped;

import com.ericsson.oss.services.pm.initiation.task.qualifier.ErrorHandler;

/**
 * {@inheritDoc}
 */
@ErrorHandler(processType = ErrorNodeCacheProcessType.DEFAULT)
@ApplicationScoped
public class DefaultScannerErrorHandler implements ScannerErrorHandler {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean process(final ErrorNodeCacheProcessType processType, final Map<String, Object> attributes) {
        return false;
    }
}
