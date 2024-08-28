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
package com.ericsson.oss.services.pm.initiation.task.factories.auditor;

import java.util.ArrayList;
import java.util.List;

import com.ericsson.oss.pmic.dto.node.Node;

/**
 * Class to hold node in error with either missing or duplicate scanners.
 */
class ErroneousNodes {
    private final List<Node> nodesWithMissingScanners;
    private final List<Node> nodesWithDuplicateScanners;

    /**
     * Constructor.
     */
    ErroneousNodes() {
        nodesWithMissingScanners = new ArrayList<>();
        nodesWithDuplicateScanners = new ArrayList<>();
    }

    public List<Node> getNodesWithMissingScanners() {
        return nodesWithMissingScanners;
    }

    public List<Node> getNodesWithDuplicateScanners() {
        return nodesWithDuplicateScanners;
    }

    /**
     * Add nodes that do not have scanners. These nodes will have to be activated for the subscription in question.
     *
     * @param node
     *         - node
     */
    void addNodesWithMissingScanners(final Node node) {
        nodesWithMissingScanners.add(node);
    }

    /**
     * Add nodes that have more scanners associated to our subscription then they should have. Some or all of such nodes will have to be
     * deactivated.
     *
     * @param node
     *         - node
     */
    void addNodesWithDuplicateScanners(final Node node) {
        nodesWithDuplicateScanners.add(node);
    }
}
