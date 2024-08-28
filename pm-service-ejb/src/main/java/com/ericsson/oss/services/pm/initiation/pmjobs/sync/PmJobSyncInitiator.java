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

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.PMJOB_SYNCING_STARTED;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import com.ericsson.oss.pmic.impl.handler.InvokeInTransaction;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import org.slf4j.Logger;

import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.generic.PmJobService;
import com.ericsson.oss.services.pm.initiation.notification.events.Activate;
import com.ericsson.oss.services.pm.initiation.notification.events.Deactivate;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEvent;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;

/**
 * This class will be executed every 15 minutes and will be started by PMICMasterPollingScheduler Entry class for PmJobPolling to bring the system in
 * a sync state between the expected active/in-active PmJobs and actual active/in-active PmJobs
 */
@Stateless
public class PmJobSyncInitiator {

    @Inject
    private Logger logger;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private PmJobSyncProcessor jobPollingProcessor;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;
    @Inject
    private PmJobService pmJobService;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;
    @Inject
    @Activate
    private InitiationEvent activationEvent;
    @Inject
    @Deactivate
    private InitiationEvent deactivationEvent;
    @Inject
    private NodeService nodeService;
    @EJB
    private PmJobSyncInitiator self;

    /**
     * Starts sync operation for the PmJobs
     */
    @LogProfiler(name = "Pm Job sync for all pmJobs", ignoreExecutionTimeLowerThan = 1L)
    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void startPmJobSyncing() {
        systemRecorder.eventCoarse(PMJOB_SYNCING_STARTED, "All Nodes",
                "PmJob Syncing has started, will send a activation request for all nodes who has inactive pm jobs for active subscriptions");
        final PmJobSyncResult processorResult = jobPollingProcessor.syncPmJobs();
        if (processorResult != null) {
            self.handleSubscriptionStateAndSendTasks(processorResult);
        }

    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void handleSubscriptionStateAndSendTasks(final PmJobSyncResult processorResult) {
        filterPmFunctionOffNodes(processorResult);
        changeSubscriptionStateAndSendPmJobActivationTask(processorResult.getPmJobsToActivate());
        changeSubscriptionStateAndSendPmJobDeactivationTask(processorResult.getPmJobsToDeactivate());
        deletePmJobs(processorResult.getPmJobsToDelete());
    }

    private void filterPmFunctionOffNodes(final PmJobSyncResult processorResult) {
        processorResult.setPmJobsToActivate(getSyncResultVosForPmFunctionOnNodesOnly(processorResult.getPmJobsToActivate()));
        processorResult.setPmJobsToDeactivate(getSyncResultVosForPmFunctionOnNodesOnly(processorResult.getPmJobsToDeactivate()));
    }

    private List<SyncResultVO> getSyncResultVosForPmFunctionOnNodesOnly(final List<SyncResultVO> syncResultVOs) {
        final List<SyncResultVO> filteredPmJobsToActivate = new ArrayList<>();
        for (final SyncResultVO syncResultVO : syncResultVOs) {
            final Iterator<Node> iterator = syncResultVO.getNodesToBeUpdated().iterator();
            while (iterator.hasNext()) {
                if (!nodeService.isPmFunctionEnabled(iterator.next().getFdn())) {
                    iterator.remove();
                }
            }
            if (!syncResultVO.getNodesToBeUpdated().isEmpty()) {
                filteredPmJobsToActivate.add(syncResultVO);
            }
        }
        return filteredPmJobsToActivate;
    }

    private void changeSubscriptionStateAndSendPmJobActivationTask(final List<SyncResultVO> resultVOs) {
        for (final SyncResultVO resultVO : resultVOs) {
            changeSubscriptionAdminStateToUpdating(resultVO.getSubscription());
            sendActivationTasksForNodes(resultVO.getSubscription(), resultVO.getNodesToBeUpdated());
        }
    }

    private void changeSubscriptionStateAndSendPmJobDeactivationTask(final List<SyncResultVO> resultVOs) {
        for (final SyncResultVO resultVO : resultVOs) {
            changeSubscriptionAdminStateToUpdating(resultVO.getSubscription());
            sendDeactivationTasksForNodes(resultVO.getSubscription(), resultVO.getNodesToBeUpdated());
        }
    }

    private void deletePmJobs(final List<String> pmJobFdns) {
        for (final String pmJobFdn : pmJobFdns) {
            if (!dpsAvailabilityStatus.isAvailable()) {
                logger.warn("Failed to delete pmjob for {}, Dps not available", pmJobFdns);
            }
            try {
                pmJobService.deleteWithRetry(pmJobFdn);
            } catch (final DataAccessException | RetryServiceException e) {
                logger.error("Couldn't delete pmjob with fdn {}. Exception Message: {}", pmJobFdn, e.getMessage());
                logger.info("Couldn't delete pmjob with fdn {}.", pmJobFdn, e);
            }
        }
    }

    private void changeSubscriptionAdminStateToUpdating(final Subscription subscription) {
        if (!dpsAvailabilityStatus.isAvailable()) {
            logger.warn("Failed to update subscription for {}, Dps not available", subscription.getName());
        }
        try {
            subscription.setAdministrationState(AdministrationState.UPDATING);
            final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
            map.put(Subscription.Subscription220Attribute.administrationState.name(), AdministrationState.UPDATING.name());
            subscriptionWriteOperationService.updateAttributes(subscription.getId(), map);
            subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));
        } catch (final DataAccessException e) {
            logger.warn("Couldn't update subscription's admin state to Updating. Continuing execution... Exception: {}", e.getMessage());
            logger.info("Exception stacktrace:", e);
        }
    }

    private void sendActivationTasksForNodes(final Subscription subscription, final List<Node> nodes) {
        logger.debug("Subscription [{}] will be activated on node", subscription.getName());
        activationEvent.execute(nodes, subscription);

    }

    private void sendDeactivationTasksForNodes(final Subscription subscription, final List<Node> nodes) {
        logger.debug("Subscription [{}] will be deactivated on node", subscription.getName());
        deactivationEvent.execute(nodes, subscription);
    }

}
