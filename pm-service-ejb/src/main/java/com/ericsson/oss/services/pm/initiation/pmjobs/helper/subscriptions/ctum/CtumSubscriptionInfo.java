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
package com.ericsson.oss.services.pm.initiation.pmjobs.helper.subscriptions.ctum;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.services.pm.common.constants.PmFeature;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.pmjobs.helper.subscriptions.SubscriptionInfo;
import com.ericsson.oss.services.pm.initiation.pmjobs.helper.subscriptions.qualifier.ProcessTypeValidation;

/**
 * Implementation class to provide Ctum subscription specific information
 */
@ProcessTypeValidation(processType = ProcessType.CTUM)
@ApplicationScoped
public class CtumSubscriptionInfo implements SubscriptionInfo {

    @Inject
    private NodeService nodeService;

    @Override
    public ProcessType getProcessType() {
        return ProcessType.CTUM;
    }

    @Override
    public List<Node> getSupportedNodesForPmJobs() throws DataAccessException {
        return nodeService.findAllByPmFeature(PmFeature.CTUM_FILE_COLLECTION, PmFeature.PMIC_JOB_INFO);
    }

    @Override
    public boolean isCommonFileCollectionSupported() {
        return true;
    }

}
