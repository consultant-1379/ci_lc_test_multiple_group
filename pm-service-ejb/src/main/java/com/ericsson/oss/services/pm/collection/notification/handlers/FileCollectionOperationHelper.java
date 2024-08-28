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

import java.util.regex.Pattern;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.api.handler.PmMediationHandlerConstants;
import com.ericsson.oss.services.pm.collection.api.FileCollectionTaskManagerLocal;
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;
import com.ericsson.oss.services.pm.initiation.util.RopTime;
import com.ericsson.oss.services.pm.time.TimeGenerator;

/**
 * This class provides helper method related to File Collection Operation
 */
@Stateless
public class FileCollectionOperationHelper {
    public static final Pattern PREDEF_10004_CELLTRACE_PATTERN = Pattern.compile(
            PmMediationHandlerConstants.HandlerAttribute.EBS_CELLTRACE_SCANNER_PATTERN);
    @Inject
    private Logger logger;
    @Inject
    private TimeGenerator timeGenerator;
    @Inject
    private FileCollectionTaskManagerLocal fileCollectionTaskManager;

    /**
     * Creates a {@link ProcessRequestVO} request that requests file collection to be started. Actual file collection takes a initial delay and Files
     * will be collected for the next available full Record Output Period.
     *
     * @param ropPeriodInSeconds
     *         - Record Output Period period represented in seconds
     * @param nodeAddress
     *         - the address of the node for file collection
     * @param processType
     *         - the process type
     */
    public void startFileCollection(final int ropPeriodInSeconds, final String nodeAddress, final String processType) {
        final ProcessRequestVO request = new ProcessRequestVO.ProcessRequestVOBuilder(nodeAddress, ropPeriodInSeconds, processType).build();
        logger.debug("Requesting to start FileCollection. Details: {}.", request);
        fileCollectionTaskManager.startFileCollection(request);
    }

    /**
     * Creates a {@link ProcessRequestVO} request that requests file collection to be stopped. No more file collection Jobs will be created for this
     * Node/Record Output Period/ProcessType. The jobs that are already in the queue to be collected will still be sent for collection
     *
     * @param ropPeriodInSeconds
     *         - Record Output Period represented in seconds
     * @param nodeAddress
     *         - the address of the node for file collection
     * @param processType
     *         - the process type
     */
    public void stopFileCollection(final int ropPeriodInSeconds, final String nodeAddress, final String processType) {
        final ProcessRequestVO request = new ProcessRequestVO.ProcessRequestVOBuilder(nodeAddress, ropPeriodInSeconds, processType).build();
        logger.debug("Requesting to stop FileCollection. Details: {}.", request);
        fileCollectionTaskManager.stopFileCollection(request);
    }

    /**
     * Update the file collection chache for new ROP period.
     *
     * @param ropPeriodInSeconds
     *         - Record Output Period represented in seconds
     * @param nodeAddress
     *         - the address of the node for file collection
     * @param processType
     *         - the process type
     * @param oldRopPeriodInSeconds
     *         - Old ROP period in seconds
     */
    public void updateFileCollectionForNewRopPeriod(final int ropPeriodInSeconds, final String nodeAddress, final String processType,
                                                    final int oldRopPeriodInSeconds) {
        final ProcessRequestVO request = new ProcessRequestVO.ProcessRequestVOBuilder(nodeAddress, ropPeriodInSeconds, processType).build();
        logger.debug("Requesting to update FileCollection. Details: {}", request);
        fileCollectionTaskManager.updateFileCollectionForNewRopPeriod(request, getCurrentROPEndTime(), oldRopPeriodInSeconds);
    }

    /**
     * Returns the end of the current quarter of the hour that is to be considered as a ROP boundary for file collection
     * to be stopped for current ROP and a new file collection request is to be started with the new ROP.
     * The switch from 1 minute to 15 minute or 15 to 1 minute file generation on the node happens only
     * at the 0, 15, 30 and 45th minute of the hour. Till that time, node will continue generating the files with the old ROP period.
     * Ex:
     * License Enabled:
     * On Node if the license state is enabled at 10:05, the node will continue to generate 15 minute ROP files till 10:15 which will be
     * collected by ENM at 10:20. At the same time, at 10:15, node will start generation of 1 minute ROP files which will be collected by
     * ENM starting 10:17
     * License Disabled:
     * On node if the license state is disabled at 11:05, it will continue to generate 1 minute ROP files till 11:15. At this time it will
     * stop 1 minute generation and will start 15 minute ROP file generation. The file for 15 minute ROP will be available at 11:30 and ENM
     * shall collect this file at 11:35.
     * For that reason, we divide the hour into 4 quarters of 15 minutes each and calculate its end time to effectively handle the switch.
     */
    private long getCurrentROPEndTime() {
        return new RopTime(timeGenerator.currentTimeMillis(), 900).getCurrentROPPeriodEndTime().getTime();
    }
}
