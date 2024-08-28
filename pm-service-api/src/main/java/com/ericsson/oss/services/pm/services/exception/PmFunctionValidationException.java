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

package com.ericsson.oss.services.pm.services.exception;

import java.util.List;

import com.ericsson.oss.pmic.dto.node.Node;

/**
 * This exception should be thrown if validation of PMFunction fails.
 */
public class PmFunctionValidationException extends ValidationException {

    private final List<Node> invalidNodes;

    /**
     * Constructor.
     *
     * @param invalidNodes
     *         - invalidNodes
     * @param message
     *         - message
     */
    public PmFunctionValidationException(final List<Node> invalidNodes, final String message) {
        super(message);
        this.invalidNodes = invalidNodes;
    }

    /**
     * Constructor.
     *
     * @param invalidNodes
     *         - invalidNodes
     * @param message
     *         - message
     * @param cause
     *         - cause
     */
    public PmFunctionValidationException(final List<Node> invalidNodes, final String message,
                                         final Throwable cause) {
        super(message, cause);
        this.invalidNodes = invalidNodes;
    }

    /**
     * @return the invalidNodes
     */
    public List<Node> getInvalidNodes() {
        return invalidNodes;
    }
}
