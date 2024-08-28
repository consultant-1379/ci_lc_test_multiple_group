/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.model.metadata;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo;
import com.ericsson.oss.pmic.impl.handler.InvokeInTransaction;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.model.metadata.mtr.PmMtrLookUp;
import com.ericsson.oss.services.pm.initiation.model.metadata.res.PmResLookUp;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class PmDataUtilLookupBean {
    @Inject
    private PmMtrLookUp pmMtrLookUp;

    @Inject
    private PmResLookUp pmResLookUp;

    @Inject
    private Logger logger;

    @ReadOnly
    @InvokeInTransaction
    public List<Node> mtrFetchAttachedNodesInReadTx(final Set<String> nodeFdns,
                                            final boolean isActivationEvent) throws DataAccessException {
        return pmMtrLookUp.fetchAttachedNodes(nodeFdns, isActivationEvent);
    }

    @ReadOnly
    @InvokeInTransaction
    public List<Node> resFetchAttachedNodesInReadTx(final List<CellInfo> cells, final boolean isApplyOnAllCells, final Set<String> nodeFdns,
                                            final boolean isActivationEvent) throws DataAccessException {
        return pmResLookUp.fetchAttachedNodes(cells, isApplyOnAllCells, nodeFdns, isActivationEvent);
    }
}