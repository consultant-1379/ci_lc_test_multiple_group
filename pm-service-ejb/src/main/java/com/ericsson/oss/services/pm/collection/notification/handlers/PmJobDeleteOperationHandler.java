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

package com.ericsson.oss.services.pm.collection.notification.handlers;

import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.pmjob.PmJob;
import com.ericsson.oss.services.pm.initiation.cache.model.value.ProcessType;
import com.ericsson.oss.services.pm.initiation.model.resource.PMICJobStatus;

/**
 * This class can handle only behavior related to PmJob deleted event
 */
@Stateless
public class PmJobDeleteOperationHandler {

    @Inject
    private Logger logger;
    @Inject
    private FileCollectionOperationHelper fileCollectionOperationHelper;

    /**
     * Get executed on receiving PmJob delete notification
     *
     * @param pmJobOperationVO
     *         Value Object for PmJob Operation
     */
    public void execute(final PmJobOperationVO pmJobOperationVO) {
        stopFileCollectionForPmJob(pmJobOperationVO);
    }

    /**
     * The file collection will be stopped only in case it is really started
     *
     * @param pmJobOperationVO
     *         - PmJob Operation
     */
    private void stopFileCollectionForPmJob(final PmJobOperationVO pmJobOperationVO) {
        final String nodeFdn = PmJob.getNodeFdnFromPmJobFdn(pmJobOperationVO.getPmJobFdn());
        final String processType = ProcessType.getProcessType(pmJobOperationVO.getProcessType()).name();
        if (isPmJobActive(pmJobOperationVO.getPmJobStatus())) {
            logger.debug("PmJob {} is ACTIVE but removed hence removing file collection of process type {} for node {} ",
                    pmJobOperationVO.getPmJobFdn(), processType, nodeFdn);
            fileCollectionOperationHelper.stopFileCollection(pmJobOperationVO.getRopTimeInSeconds(), nodeFdn, pmJobOperationVO.getProcessType());
        }

    }

    private boolean isPmJobActive(final String pmJobStatus) {
        return PMICJobStatus.ACTIVE.equalsName(pmJobStatus);
    }

}
