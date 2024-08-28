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

package com.ericsson.oss.services.pm.initiation.pmjobs.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.pmjob.PmJob;
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.PmJobService;
import com.ericsson.oss.services.pm.initiation.pmjobs.helper.PmJobHelper;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * Processor class which finds the delta to PmJobs to be activated, deactivated and deleted. In Result, System can operate on the delta to bring ENM
 * in sync for PmJobs. This class takes care of creating the PmJobs of the new nodes added to the system
 */
@Singleton
public class PmJobSyncProcessor {

    @Inject
    private Logger logger;
    @Inject
    private PmJobHelper pmJobHelper;
    @Inject
    private PmJobService pmJobService;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    private Set<String> expectedPmJobNamesForActivePmJobSubscriptions;

    private Set<String> expectedPmJobNamesForInactivePmJobSubscriptions;

    private Set<String> existingPmJobSuportingSubscriptionIds;

    private Set<Node> existingPmJobSupportedNodes;

    /**
     * Sync PM Jobs for all the subscription and nodes available which is supported by PmJobs
     *
     * @return PmJobSyncResult
     */
    public PmJobSyncResult syncPmJobs() {
        logger.info("Processing and finding Delta PmJobs!");
        PmJobSyncResult pmJobSyncResult = null;
        try {
            expectedPmJobNamesForActivePmJobSubscriptions = pmJobHelper.buildAllActivePmJobNames();
            expectedPmJobNamesForInactivePmJobSubscriptions = pmJobHelper.buildAllInactivePmJobNames();
            existingPmJobSuportingSubscriptionIds = pmJobHelper.getAllSubscriptionIdsSupportedByPmJob();
            existingPmJobSupportedNodes = pmJobHelper.getAllPmJobSupportedNodes();
            pmJobSyncResult = filterPmJobsToProcess();
        } catch (final DataAccessException | RuntimeDataAccessException exception) {
            logger.error("Failed to connect to DPS: {}", exception.getMessage());
            logger.info("Failed to connect to DPS", exception);
        }
        return pmJobSyncResult;
    }

    private PmJobSyncResult filterPmJobsToProcess() {
        final PmJobSyncResult syncProcessResult = new PmJobSyncResult();
        final List<String> pmJobsToBeActivated = new ArrayList<>();
        final List<String> pmJobsToBeDeactivated = new ArrayList<>();
        final List<String> pmJobsToBeDeleted = new ArrayList<>();
        findDeltaPmJobsNeedsProcessingByComparisonFromDPS(pmJobsToBeActivated, pmJobsToBeDeactivated, pmJobsToBeDeleted);
        if (!expectedPmJobNamesForActivePmJobSubscriptions.isEmpty()) {
            pmJobsToBeActivated.addAll(expectedPmJobNamesForActivePmJobSubscriptions);
        }
        //New nodes added and Subscription is inactive then following code will create PmJobs for new node
        if (!expectedPmJobNamesForInactivePmJobSubscriptions.isEmpty()) {
            pmJobsToBeDeactivated.addAll(expectedPmJobNamesForInactivePmJobSubscriptions);
        }
        logger.debug("PmJobs For: activation-> {}, deactivation-> {}, deletion-> {}", pmJobsToBeActivated, pmJobsToBeDeactivated, pmJobsToBeDeleted);
        syncProcessResult.setPmJobsToActivate(processAndUpdateResult(pmJobsToBeActivated, existingPmJobSupportedNodes));
        syncProcessResult.setPmJobsToDeactivate(processAndUpdateResult(pmJobsToBeDeactivated, existingPmJobSupportedNodes));
        syncProcessResult.setPmJobsToDelete(pmJobsToBeDeleted);
        return syncProcessResult;
    }

    private void findDeltaPmJobsNeedsProcessingByComparisonFromDPS(final List<String> pmJobsToBeActivated, final List<String> pmJobsToBeDeactivated,
                                                                   final List<String> pmJobsToBeDeleted) {
        if (!dpsAvailabilityStatus.isAvailable()) {
            logger.warn("Failed to find delta PmJobs, Dps not available");
            return;
        }
        try {
            final List<PmJob> pmJobs = pmJobService.findAll();
            for (final PmJob pmJob : pmJobs) {
                final String pmJobName = pmJob.getName();
                if (expectedPmJobNamesForActivePmJobSubscriptions.contains(pmJobName)) {
                    handleCaseWhenPmJobShouldBeActive(pmJob.getStatus(), pmJobName, pmJobsToBeActivated);
                } else if (expectedPmJobNamesForInactivePmJobSubscriptions.contains(pmJobName)) {
                    handleCaseWhenPmJobShouldBeInactive(pmJob.getStatus(), pmJobName, pmJobsToBeDeactivated);
                } else {
                    handleCaseWhenPmJobShouldBeDeletedOrIgnored(pmJob, pmJobName, pmJobsToBeDeleted);
                }
            }
        } catch (final DataAccessException exception) {
            logger.error("Failed to connect to DPS: {}", exception.getMessage());
            logger.info("Failed to connect to DPS", exception);
        }
    }

    private void handleCaseWhenPmJobShouldBeActive(final PmJobStatus pmJobStatus, final String pmJobName, final List<String> pmJobsToBeActivated) {
        if (isPmJobNeedsToBeActivated(pmJobStatus)) {
            logger.debug("PMICJobInfo {} will be activated", pmJobName);
            pmJobsToBeActivated.add(pmJobName);
        }
        expectedPmJobNamesForActivePmJobSubscriptions.remove(pmJobName);
    }

    private void handleCaseWhenPmJobShouldBeInactive(final PmJobStatus pmJobStatus, final String pmJobName,
                                                     final List<String> pmJobsToBeDeactivated) {
        if (isPmJobNeedsToBeDeactivated(pmJobStatus)) {
            logger.debug("PMICJobInfo {} will be deactivated", pmJobName);
            pmJobsToBeDeactivated.add(pmJobName);
        }
        expectedPmJobNamesForInactivePmJobSubscriptions.remove(pmJobName);

    }

    private void handleCaseWhenPmJobShouldBeDeletedOrIgnored(final PmJob pmJob, final String pmJobName, final List<String> pmJobsToBeDeleted) {
        final String subscriptionId = String.valueOf(pmJob.getSubscriptionId());
        if (existingPmJobSuportingSubscriptionIds.contains(subscriptionId)) {
            logger.debug("PMICJobInfo {} will be ignored because its subscription state might be as ACTIVATING, SCHEDULED, DEACTIVATING, UPDATING ",
                    pmJobName);
        } else {
            //Subscription is already deleted hence PmJobInfo also needs to be deleted
            pmJobsToBeDeleted.add(pmJob.getFdn());
            logger.debug("PMICJobInfo {} will be deleted", pmJobName);
        }
    }

    private boolean isPmJobNeedsToBeActivated(final PmJobStatus pmJobStatus) {
        return pmJobStatus != PmJobStatus.ACTIVE;
    }

    private boolean isPmJobNeedsToBeDeactivated(final PmJobStatus pmJobStatus) {
        return pmJobStatus != PmJobStatus.INACTIVE;
    }

    private List<SyncResultVO> processAndUpdateResult(final List<String> pmJobNames, final Set<Node> existingSupportedNodes) {
        final Map<String, List<Node>> subsIdToNodesMap = new HashMap<>();
        for (final String pmJobName : pmJobNames) {
            logger.debug("PmJobName ->{}", pmJobName);
            final Map<String, Node> nodeIdentityToPMICNdeInfoMap = prepareNodeIdentityToPMICNodeInfoMap(existingSupportedNodes);
            final String subscriptionId = PmJob.getSubscriptionIdFromPmJobName(pmJobName);
            final Node node = nodeIdentityToPMICNdeInfoMap.get(PmJob.getNodeIdentityFromPmJobName(pmJobName));
            logger.debug("Node: NodeFdn->{}", node.getFdn());
            List<Node> nodesList;
            if (subsIdToNodesMap.containsKey(subscriptionId)) {
                nodesList = subsIdToNodesMap.get(subscriptionId);
                nodesList.add(node);
            } else {
                nodesList = new ArrayList<>();
                nodesList.add(node);
            }
            subsIdToNodesMap.put(subscriptionId, nodesList);
        }
        final List<SyncResultVO> listToBeUpdated = new ArrayList<>();
        for (final Map.Entry<String, List<Node>> entry : subsIdToNodesMap.entrySet()) {
            try {
                final Subscription subscription = subscriptionReadOperationService.findOneById(Long.parseLong(entry.getKey()));
                final SyncResultVO syncResultVO = new SyncResultVO(subscription, entry.getValue());
                listToBeUpdated.add(syncResultVO);
            } catch (final DataAccessException exception) {
                logger.error("Unable to connected to DPS while fetching the Subscription by id: {}", exception.getMessage());
                logger.info("Unable to connected to DPS while fetching the Subscription by id", exception);
            }
        }
        return listToBeUpdated;
    }

    private Map<String, Node> prepareNodeIdentityToPMICNodeInfoMap(final Set<Node> existingSupportedNodes) {
        final Map<String, Node> nodeIdentityToNodeFdnMap = new HashMap<>();
        for (final Node node : existingSupportedNodes) {
            nodeIdentityToNodeFdnMap.put(PmJob.getNodeIdentityFromNodeFdn(node.getFdn()), node);
        }
        return nodeIdentityToNodeFdnMap;
    }

}
