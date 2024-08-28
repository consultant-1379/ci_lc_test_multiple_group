/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.schedulers;

import static com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants.THREE_MINUTES_IN_MILLISECONDS;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.pmjob.PmJob;
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;
import com.ericsson.oss.services.pm.collection.notification.handlers.FileCollectionOperationHelper;
import com.ericsson.oss.services.pm.common.systemdefined.SystemDefinedSubscriptionManager;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.exception.SubscriptionNotFoundDataAccessException;
import com.ericsson.oss.services.pm.generic.PmJobService;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.cache.PMICInitiationTrackerCache;
import com.ericsson.oss.services.pm.initiation.cache.data.InitiationTracker;
import com.ericsson.oss.services.pm.initiation.pmjobs.sync.PmJobSynchronizer;
import com.ericsson.oss.services.pm.initiation.task.TaskStatusValidator;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;
import com.ericsson.oss.services.pm.time.TimeGenerator;

/**
 * This class would handle the process of initiation timeout and change subscription states and scanner states based on success or failure for events
 * activation/deactivation, add/remove node from ACTIVE subscription
 */
@Stateless
public class InitiationDelayTimerHandler {

    @Inject
    private PMICInitiationTrackerCache pmicInitiationTrackerCache;
    @Inject
    private Logger logger;
    @Inject
    private FileCollectionOperationHelper fileCollectionOperationHelper;
    @Inject
    private TaskStatusValidator<Subscription> taskStatusValidator;
    @Inject
    private SubscriptionTimeout subscriptionTimeout;
    @Inject
    private PmJobSynchronizer pmJobSynchronizer;
    @Inject
    private TimeGenerator timeGenerator;
    @Inject
    private PmJobService pmJobService;
    @Inject
    private ScannerService scannerService;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private SystemDefinedSubscriptionManager systemDefinedSubscriptionManager;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;

    /**
     * Read cache and if time expires for activation/ deactivation event.
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void processInitiationTimeout() {
        // Read all entry in the cache
        final List<InitiationTracker> allInitiationTrackers = pmicInitiationTrackerCache.getAllTrackers();
        int totalEntriesInCache = 0;
        for (final InitiationTracker initiationTracker : allInitiationTrackers) {
            totalEntriesInCache++;
            if (isSubscriptionInitiationTimedOut(initiationTracker)) {
                logger.info("Subscription ID {} is expired for admin state {}." + " Nodes processed: {} of {}, {} left."
                        + " Notifications received: {} of {}, {} left.", initiationTracker.getSubscriptionId(),
                    initiationTracker.getSubscriptionAdministrationState(), initiationTracker.getTotalAmountOfProcessedNodes(),
                    initiationTracker.getTotalAmountOfNodes(), initiationTracker.getTotalAmountOfUnprocessedNodesLeft(),
                    initiationTracker.getTotalAmountOfReceivedNotifications(), initiationTracker.getTotalAmountOfExpectedNotifications(),
                    initiationTracker.getTotalExpectedNotificationsLeft());
                try {
                    if (handleInitiationTimedOut(initiationTracker)) {
                        pmicInitiationTrackerCache.stopTracking(initiationTracker.getSubscriptionId());
                        logger.info("Removed Subscription ID {} from cache ", initiationTracker.getSubscriptionId());
                    }
                } catch (final SubscriptionNotFoundDataAccessException exception) {
                    pmicInitiationTrackerCache.stopTracking(initiationTracker.getSubscriptionId());
                    logger.info("Removed Subscription ID {} with Admin State {}, from cache" + "because subscription no longer exists in DPS.",
                        initiationTracker.getSubscriptionId(), initiationTracker.getSubscriptionAdministrationState(), exception);
                } catch (final RetryServiceException | DataAccessException | RuntimeDataAccessException exception) {
                    logger.error("Could not handle initiation timer delay timeout for subscription {} with Admin State {}, "
                            + "due to an underlying issue when trying to access data with the DPS: {} ",
                        initiationTracker.getSubscriptionId(), initiationTracker.getSubscriptionAdministrationState(), exception.getMessage());
                    logger.info("Could not handle initiation timer delay timeout for subscription {} with Admin State {}, "
                            + "due to an underlying issue when trying to access data with the DPS. ", initiationTracker.getSubscriptionId(),
                        initiationTracker.getSubscriptionAdministrationState(), exception);
                }
            } else {
                logger.info("Subscription ID {} time still valid for event {}." + " Nodes processed: {} of {}, {} left."
                        + " Notifications received: {} of {}, {} left.", initiationTracker.getSubscriptionId(),
                    initiationTracker.getSubscriptionAdministrationState(), initiationTracker.getTotalAmountOfProcessedNodes(),
                    initiationTracker.getTotalAmountOfNodes(), initiationTracker.getTotalAmountOfUnprocessedNodesLeft(),
                    initiationTracker.getTotalAmountOfReceivedNotifications(), initiationTracker.getTotalAmountOfExpectedNotifications(),
                    initiationTracker.getTotalExpectedNotificationsLeft());
            }
        }
        logger.info("Processed Initiation timer delay for {} entries in cache ", totalEntriesInCache);
    }

    private boolean isSubscriptionInitiationTimedOut(final InitiationTracker initiationTracker) {
        final long commandInitiatedTime = initiationTracker.getLastReceivedNotificationTimeStamp();
        final long currentTime = timeGenerator.currentTimeMillis();
        return isOutsideInitialBufferPeriod(commandInitiatedTime, currentTime) && isOutsideNodeSpecificTimeoutPeriod(initiationTracker,
            commandInitiatedTime, currentTime);
    }

    private boolean isOutsideInitialBufferPeriod(final long commandInitiatedTime, final long currentTime) {
        return currentTime > commandInitiatedTime + THREE_MINUTES_IN_MILLISECONDS;
    }

    private boolean isOutsideNodeSpecificTimeoutPeriod(final InitiationTracker initiationTracker, final long commandInitiatedTime,
                                                       final long currentTime) {
        return currentTime > commandInitiatedTime + +subscriptionTimeout
            .getTotalTimeoutForAllSubscriptionNodes(initiationTracker.getUnprocessedNodesAndTypes());
    }

    /**
     * remove subscription object from cache. Handle Activation/Add node, Deactivation/Remove node
     */
    private boolean handleInitiationTimedOut(final InitiationTracker initiationTracker) throws RetryServiceException, DataAccessException {

        final AdministrationState adminState = AdministrationState.fromString(initiationTracker.getSubscriptionAdministrationState());
        if (adminState.isOneOf(AdministrationState.DEACTIVATING, AdministrationState.UPDATING, AdministrationState.ACTIVATING)) {
            final Subscription subscription = subscriptionReadOperationService
                .findByIdWithRetry(Long.valueOf(initiationTracker.getSubscriptionId()), true);
            if (subscription == null) {
                throw new SubscriptionNotFoundDataAccessException(
                    "Subscription with id [" + initiationTracker.getSubscriptionId() + "] does not exist");
            }
            if (adminState == AdministrationState.DEACTIVATING) {
                return handleDeactivationTimeOut(subscription);
            }
            if (adminState == AdministrationState.UPDATING) {
                return handleAddOrRemoveNodeOnTimeOut(initiationTracker, subscription);
            }
            if (adminState == AdministrationState.ACTIVATING) {
                return handleActivationTimeOut(initiationTracker, subscription);
            }
        }
        return false;
    }

    private boolean handleDeactivationTimeOut(final Subscription subscription) throws DataAccessException {
        logger.info("Processing deactivation timeout for subscription {} with id {}", subscription.getName(), subscription.getId());
        if (Subscription.isPmJobSupported(subscription.getType())) {
            return handleDeactivationTimeOutForPmJobs(subscription);
        } else {
            return handleDeactivationTimeOutForScanners(subscription);
        }
    }

    /**
     * Deactivating : If, any scanner exists for this subscription update subscription admin state to INACTIVE, update Task status to ERROR and
     * update those existing scanner status to ERROR to stop file collection. when Scanner Master runs -> delete scanners in node and
     * as well as in DPS If no scanner exists, update subscription admin state to INACTIVE update Task status to OK.
     */
    private boolean handleDeactivationTimeOutForScanners(final Subscription subscription) throws DataAccessException {
        updateScannersSubscriptionIdAndStatusToUnknown(subscription, null);
        subscription.setAdministrationState(AdministrationState.INACTIVE);
        subscription.setTaskStatus(TaskStatus.OK);
        final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
        map.put(Subscription.Subscription220Attribute.administrationState.name(), AdministrationState.INACTIVE.name());
        map.put(Subscription.Subscription220Attribute.taskStatus.name(), TaskStatus.OK.name());
        subscriptionWriteOperationService.updateAttributes(subscription.getId(), map);
        subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));
        return true;
    }

    private boolean handleDeactivationTimeOutForPmJobs(final Subscription subscription) throws DataAccessException {
        final List<PmJob> pmJobs = pmJobService.findAllBySubscriptionId(subscription.getId());
        final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
        if (areAllPmJobsWithStatus(pmJobs, PmJobStatus.INACTIVE)) {
            subscription.setAdministrationState(AdministrationState.INACTIVE);
            subscription.setTaskStatus(TaskStatus.OK);
            map.put(Subscription.Subscription220Attribute.administrationState.name(), AdministrationState.INACTIVE.name());
            map.put(Subscription.Subscription220Attribute.taskStatus.name(), TaskStatus.OK.name());
        } else {
            subscription.setAdministrationState(AdministrationState.INACTIVE);
            subscription.setTaskStatus(TaskStatus.ERROR);
            map.put(Subscription.Subscription220Attribute.administrationState.name(), AdministrationState.INACTIVE.name());
            map.put(Subscription.Subscription220Attribute.taskStatus.name(), TaskStatus.ERROR.name());
        }
        subscriptionWriteOperationService.updateAttributes(subscription.getId(), map);
        subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));
        return true;
    }

    private boolean areAllPmJobsWithStatus(final List<PmJob> pmJobs, final PmJobStatus expectedJobStatus) {
        for (final PmJob pmJob : pmJobs) {
            if (pmJob.getStatus() != expectedJobStatus) {
                return false;
            }
        }
        return true;
    }

    private boolean handleActivationTimeOut(final InitiationTracker initiationTracker, final Subscription subscription)
        throws DataAccessException {
        logger.info("Processing activation timeout for subscription {}", initiationTracker.getSubscriptionId());
        if (Subscription.isPmJobSupported(subscription.getType())) {
            return processActivationTimeOutForPmJobNodes(initiationTracker, subscription);
        } else {
            return processActivationTimeOutForScannerNodes(initiationTracker, subscription);
        }
    }

    /**
     * Activating : If, number_of_nodes == number_of_scanners, update subscription admin state to ACTIVE. Otherwise, (1) update subscription admin
     * state to ACTIVE (2) create missing scanners in DPS with status UNKNOWN
     */
    private boolean processActivationTimeOutForScannerNodes(final InitiationTracker initiationTracker, final Subscription subscription)
        throws DataAccessException {
        final Set<String> nodeFdnsToBeActivated = new HashSet<>(initiationTracker.getNodesFdnsToBeActivated());
        final List<Scanner> scanners = scannerService.findAllBySubscriptionId(subscription.getId());
        final Map<String, Scanner> unprocessedActiveScannersAndNodesForWhichNottificationsWereMissed = new HashMap<>();
        TaskStatus taskStatus = TaskStatus.OK;
        for (final Scanner scanner : scanners) {
            final String nodeFdn = scanner.getNodeFdn();
            if (ScannerStatus.ACTIVE != scanner.getStatus()) {
                taskStatus = TaskStatus.ERROR;
            } else if (initiationTracker.getUnprocessedNodesAndTypes().keySet().contains(nodeFdn)) {
                unprocessedActiveScannersAndNodesForWhichNottificationsWereMissed.put(nodeFdn, scanner);
            }
            nodeFdnsToBeActivated.remove(nodeFdn);
        }

        logger.trace("List of nodes {} that does not have PMICScannerInfo in dps from subscription {} with id {}", nodeFdnsToBeActivated,
            subscription.getName(), subscription.getId());
        if (!nodeFdnsToBeActivated.isEmpty() && subscription instanceof StatisticalSubscription && isStatisticalSubscriptionWithNoPredefScanner(
            subscription)) {
            final List<Scanner> createdScanners = createUnknownUserdefStatisticalScannersForSubscriptionAndNodes(subscription, nodeFdnsToBeActivated);
            if (!createdScanners.isEmpty()) {
                taskStatus = TaskStatus.ERROR;
            }
        }
        startFileCollectionForMissedNotifications(unprocessedActiveScannersAndNodesForWhichNottificationsWereMissed);
        subscriptionWriteOperationService.updateSubscriptionStateActivationTimeAndTaskStatus(subscription, AdministrationState.ACTIVE, taskStatus);
        return true;
    }

    private List<Scanner> createUnknownUserdefStatisticalScannersForSubscriptionAndNodes(final Subscription subscription,
                                                                                         final Set<String> nodeFdns) {
        final List<Scanner> createdScanners = new ArrayList<>(nodeFdns.size());
        for (final String nodeFdn : nodeFdns) {
            try {
                final Scanner scanner = scannerService.createUnknownUserdefStatisticalScannerIfNotExist(subscription, nodeFdn);
                createdScanners.add(scanner);
            } catch (final DataAccessException | RetryServiceException e) {
                logger.error("Couldn't create UNKNOWN stats scanner for subscription {} with id {} on node {}. Exception message: {}",
                    subscription.getId(), subscription.getId(), nodeFdn, e.getMessage());
                logger.info("Couldn't create UNKNOWN stats scanner for subscription {} with id {} on node {}.", subscription.getId(),
                    subscription.getId(), nodeFdn, e);
            }
        }
        return createdScanners;
    }

    private void startFileCollectionForMissedNotifications(final Map<String, Scanner> missedNotificationNodes) {
        logger.info("Starting file collection for nodes for which no notifications were received (if any):{}", missedNotificationNodes.keySet());
        for (final java.util.Map.Entry<String, Scanner> entry : missedNotificationNodes.entrySet()) {
            final String nodeFdn = entry.getKey();
            final Scanner scanner = entry.getValue();
            if (scanner.isFileCollectionEnabled()) {
                fileCollectionOperationHelper.startFileCollection(scanner.getRopPeriod(), nodeFdn, scanner.getProcessType().name());
            }
        }
    }

    private boolean processActivationTimeOutForPmJobNodes(final InitiationTracker initiationTracker, final Subscription subscription)
        throws DataAccessException {
        final Set<String> nodeFdnsToBeActivated = new HashSet<>(initiationTracker.getNodesFdnsToBeActivated());
        final List<PmJob> pmJobs = pmJobService.findAllBySubscriptionId(subscription.getId());
        TaskStatus taskStatus = TaskStatus.OK;
        for (final PmJob pmJob : pmJobs) {
            if (pmJob.getStatus().isOneOf(PmJobStatus.UNKNOWN, PmJobStatus.ERROR, PmJobStatus.INACTIVE)) {
                taskStatus = TaskStatus.ERROR;
            }
            nodeFdnsToBeActivated.remove(pmJob.getNodeFdn());
        }
        logger.trace("List of nodes {} that does not have PMICJobInfo in dps from subscription {} with id {}", nodeFdnsToBeActivated,
            subscription.getName(), subscription.getId());
        if (!nodeFdnsToBeActivated.isEmpty()) {
            pmJobSynchronizer.syncAllPmJobsInDPSForSubscription(subscription, nodeFdnsToBeActivated);
        }
        subscription.setAdministrationState(AdministrationState.ACTIVE);
        subscription.setTaskStatus(taskStatus);
        final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
        map.put(Subscription.Subscription220Attribute.administrationState.name(), AdministrationState.ACTIVE.name());
        map.put(Subscription.Subscription220Attribute.taskStatus.name(), taskStatus.name());
        subscriptionWriteOperationService.updateAttributes(subscription.getId(), map);
        subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));
        return true;

    }

    private boolean handleAddOrRemoveNodeOnTimeOut(final InitiationTracker initiationTracker, final Subscription subscription)
        throws DataAccessException {
        if (Subscription.isPmJobSupported(subscription.getType())) {
            return handleUpdatingSubscriptionOnTimeOutForPmJobs(initiationTracker, subscription);
        }
        final boolean handledRemove = handleRemoveNodeTimeOutForScanner(initiationTracker, subscription);
        final boolean handledAdd = handleAddNodeTimeOutForScanner(initiationTracker, subscription);
        return handledAdd || handledRemove;
    }

    private boolean handleUpdatingSubscriptionOnTimeOutForPmJobs(final InitiationTracker initiationTracker, final Subscription subscription)
        throws DataAccessException {
        final Set<String> nodesForActivation = initiationTracker.getNodesFdnsToBeActivated();
        final Set<String> nodesForDeactivation = initiationTracker.getNodesFdnsToBeDeactivated();
        if (nodesForActivation != null && !nodesForActivation.isEmpty()) {
            logger.info("Processing activation updating timeout for subscription {} ", initiationTracker.getSubscriptionId());
            validateAndUpdateSubscriptionForPmJobs(AdministrationState.ACTIVE, subscription);
        }
        if (nodesForDeactivation != null && !nodesForDeactivation.isEmpty()) {
            logger.info("Processing deactivation updating timeout for subscription {} ", initiationTracker.getSubscriptionId());
            validateAndUpdateSubscriptionForPmJobs(AdministrationState.INACTIVE, subscription);
        }
        return true;
    }

    private boolean validateAndUpdateSubscriptionForPmJobs(final AdministrationState adminState, final Subscription subscription)
        throws DataAccessException {
        final List<PmJob> pmJobs = pmJobService.findAllBySubscriptionId(subscription.getId());
        TaskStatus taskStatus = TaskStatus.OK;
        for (final PmJob pmJob : pmJobs) {
            if (setTaskStatusToError(adminState, pmJob)) {
                taskStatus = TaskStatus.ERROR;
            }
        }
        subscription.setTaskStatus(taskStatus);
        subscription.setAdministrationState(adminState);
        final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
        map.put(Subscription.Subscription220Attribute.taskStatus.name(), taskStatus.name());
        map.put(Subscription.Subscription220Attribute.administrationState.name(), adminState.name());
        subscriptionWriteOperationService.updateAttributes(subscription.getId(), map);
        subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));
        return true;
    }

    private boolean setTaskStatusToError(final AdministrationState adminState, final PmJob pmJob) {
        return (AdministrationState.ACTIVE == adminState && pmJob.getStatus()
            .isOneOf(PmJobStatus.UNKNOWN, PmJobStatus.ERROR, PmJobStatus.INACTIVE)) ||
            (AdministrationState.INACTIVE == adminState && pmJob.getStatus()
                .isOneOf(PmJobStatus.UNKNOWN, PmJobStatus.ERROR, PmJobStatus.ACTIVE));
    }

    private boolean handleAddNodeTimeOutForScanner(final InitiationTracker initiationTracker, final Subscription subscription)
        throws DataAccessException {
        if (initiationTracker.getNodesFdnsToBeActivated() == null) {
            return false;
        }
        logger.info("Processing add node timeout for subscription {}  with id {}", subscription.getName(), subscription.getId());
        return processActivationTimeOutForScannerNodes(initiationTracker, subscription);
    }

    private boolean handleRemoveNodeTimeOutForScanner(final InitiationTracker initiationTracker, final Subscription subscription)
        throws DataAccessException {
        final Set<String> nodeFdns = initiationTracker.getNodesFdnsToBeDeactivated();
        if (nodeFdns == null || nodeFdns.isEmpty()) {
            return false;
        }
        logger.info("Processing remove node timeout for subscription {} with id {}", subscription.getName(), subscription.getId());
        updateScannersSubscriptionIdAndStatusToUnknown(subscription, nodeFdns);
        AdministrationState administrationState = AdministrationState.INACTIVE;
        TaskStatus taskStatus = TaskStatus.OK;
        if (subscription instanceof ResourceSubscription && !((ResourceSubscription) subscription).getNodes().isEmpty()) {
            taskStatus = taskStatusValidator.getTaskStatus(subscription);
            administrationState = AdministrationState.ACTIVE;
        }
        subscription.setAdministrationState(administrationState);
        subscription.setTaskStatus(taskStatus);
        final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
        map.put(Subscription.Subscription220Attribute.administrationState.name(), administrationState.name());
        map.put(Subscription.Subscription220Attribute.taskStatus.name(), taskStatus.name());
        subscriptionWriteOperationService.updateAttributes(subscription.getId(), map);
        subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));
        return true;
    }

    private void updateScannersSubscriptionIdAndStatusToUnknown(final Subscription subscription, final Set<String> nodeFdns)
        throws DataAccessException {
        final List<Scanner> scanners = scannerService.findAllBySubscriptionId(subscription.getId());
        for (final Scanner scanner : scanners) {
            if (nodeFdns == null || nodeFdns.contains(scanner.getFdn())) {
                try {
                    scanner.setStatus(ScannerStatus.UNKNOWN);
                    scanner.setSubscriptionId(Subscription.UNKNOWN_SUBSCRIPTION_ID);
                    scannerService.saveOrUpdate(scanner);
                } catch (final DataAccessException | RuntimeDataAccessException e) {
                    logger.error("Cannot delete scanner {}. Exception message: {}", scanner.getFdn(), e.getMessage());
                    logger.info("Cannot delete scanner {}. Exception : {}", scanner.getFdn(), e);
                }
            }
        }
    }

    private boolean isStatisticalSubscriptionWithNoPredefScanner(final Subscription subscription) {
        // It returns true in case of statistical subscription USER_DEF or (v)EPG SYSTEM_DEF only.
        // False on all other SYSTEM_DEF statistical subscriptions as no scanner shall be created (the predefined scanner already exists)
        if (SubscriptionType.STATISTICAL == subscription.getType()) {
            boolean hasPredefScanner = false;
            if (UserType.SYSTEM_DEF == subscription.getUserType()) {
                hasPredefScanner = systemDefinedSubscriptionManager.hasSubscriptionPredefinedScanner(subscription.getName());
            }
            return UserType.USER_DEF.equals(subscription.getUserType()) || !hasPredefScanner;
        }
        return true;
    }
}
