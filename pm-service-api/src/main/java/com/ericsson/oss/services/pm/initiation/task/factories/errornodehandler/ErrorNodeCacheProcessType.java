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

import com.ericsson.oss.services.pm.initiation.cache.model.value.ProcessType;

/**
 * Functions that can be performed by the error node cache
 */
public enum ErrorNodeCacheProcessType {

    PM_FUNCTION_DELETED, REMOVE, DEFAULT;

    /**
     * Converts from the existing process type enum to the error node cache version
     * It allows this enum to expand independently of the default process types
     *
     * @param processType
     *         - The @ProcessType to convert
     *
     * @return the equivalent ErrorNodeCacheProcessType for the given ProcessType if available
     */
    public static ErrorNodeCacheProcessType convertFromProcessType(final ProcessType processType) {
        for (final ErrorNodeCacheProcessType type : ErrorNodeCacheProcessType.values()) {
            if (type.name().equals(processType.name())) {
                return type;
            }
        }
        return DEFAULT;
    }
}
