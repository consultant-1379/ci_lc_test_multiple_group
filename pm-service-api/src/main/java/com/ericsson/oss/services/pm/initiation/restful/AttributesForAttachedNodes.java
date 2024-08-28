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
package com.ericsson.oss.services.pm.initiation.restful;

import java.util.List;
import java.util.Set;

import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo;

/**
 * Class that holds required attributes for attached Nodes size calculation.
 */
public class AttributesForAttachedNodes {

    private List<CellInfo> cells;
    private boolean applyOnAllCells;
    private Set<String> nodeFdns;

    /**
     * Default Constructor for AttributesForAttachedNodes.
     */
    public AttributesForAttachedNodes() {

    }

    /**
     * Constructor for AttributesForAttachedNodes.
     *
     * @param cells
     *         - the cell
     * @param applyOnAllCells
     *         - the applyonAllCells
     * @param nodeFdns
     *         - NetworkElement fdn
     */
    public AttributesForAttachedNodes(final List<CellInfo> cells, final boolean applyOnAllCells, final Set<String> nodeFdns) {
        this.cells = cells;
        this.applyOnAllCells = applyOnAllCells;
        this.nodeFdns = nodeFdns;
    }

    /**
     * Return Cells.
     *
     * @return the cells
     */
    public List<CellInfo> getCells() {
        return cells;
    }

    /**
     * Set Cells
     *
     * @param cells
     *         the cells to set
     */
    public void setCells(final List<CellInfo> cells) {
        this.cells = cells;
    }

    /**
     * @return the applyOnAllCells
     */
    public boolean getApplyOnAllCells() {
        return applyOnAllCells;
    }

    /**
     * Set applyOnAllCells.
     *
     * @param applyOnAllCells
     *         the applyOnAllCells to set
     */
    public void setApplyOnAllCells(final boolean applyOnAllCells) {
        this.applyOnAllCells = applyOnAllCells;
    }

    /**
     * Return collection of nodeFdn.
     *
     * @return the nodeFdns
     */
    public Set<String> getNodeFdns() {
        return nodeFdns;
    }

    /**
     * Set the node fdn collection.
     *
     * @param nodeFdns
     *         the nodeFdns to set
     */
    public void setNodeFdns(final Set<String> nodeFdns) {
        this.nodeFdns = nodeFdns;
    }

}
