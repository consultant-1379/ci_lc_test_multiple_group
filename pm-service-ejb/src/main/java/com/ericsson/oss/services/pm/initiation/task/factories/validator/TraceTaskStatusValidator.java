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

package com.ericsson.oss.services.pm.initiation.task.factories.validator;

import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.common.constants.PmFeature;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.generic.PmJobService;
import com.ericsson.oss.services.pm.initiation.task.TaskStatusValidator;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;

public abstract class TraceTaskStatusValidator extends AbstractTaskStatusValidator implements TaskStatusValidator<Subscription> {

    @Inject
    Logger logger;

    @Inject
    private PmCapabilityModelService capabilityAccess;

    @Inject
    private NodeService nodeService;

    @Inject
    private PmJobService pmJobService;


    @Override
    protected boolean isTaskStatusError(final Subscription subscription) throws DataAccessException {
        final List<String> supportedNodeTypes = capabilityAccess.getSupportedNodeTypesForPmFeatureCapability(getPmFeatureForSupportedNodes(),
                PmFeature.PMIC_JOB_INFO);
        if (supportedNodeTypes.isEmpty()) {
            logger.error("Cannot calculate task status for CTUM because internal logic doesn't find any valid node types.");
            return true;
        }
        final String[] nodeTypes = supportedNodeTypes.toArray(new String[supportedNodeTypes.size()]);
        final int nodeCount = nodeService.countByNeTypeAndPmFunction(nodeTypes, true);
        // if number of active jobs does not equal number of nodes in subscription, then subscription is in error
        final int pmJobCount = pmJobService.countAllBySubscriptionIdAndPmJobStatus(subscription.getId(), PmJobStatus.ACTIVE);
        logger.debug("PM Function ON Node count is {} and PmJob count is {}", nodeCount, pmJobCount);
        return pmJobCount != nodeCount;
    }

    @Override
    public void validateTaskStatusAndAdminState(final Subscription subscription, final Set<String> nodesToBeVerified) {
        validateTaskStatusAndAdminState(subscription);
    }

    @Override
    public void validateTaskStatusAndAdminState(final Subscription subscription, final String nodeToBeVerified) {
        validateTaskStatusAndAdminState(subscription);
    }

    abstract PmFeature getPmFeatureForSupportedNodes();
}
