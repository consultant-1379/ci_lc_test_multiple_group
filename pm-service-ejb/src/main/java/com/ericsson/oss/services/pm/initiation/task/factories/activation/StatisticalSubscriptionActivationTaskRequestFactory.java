/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.task.factories.activation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.services.model.ned.pm.function.FileCollectionState;
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper;
import com.ericsson.oss.services.pm.collection.notification.handlers.FileCollectionStateUpdateHandler;
import com.ericsson.oss.services.pm.common.systemdefined.SystemDefinedSubscriptionManager;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.initiation.scanner.lifecycle.task.factories.resumption.ScannerResumptionTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractNeConfigurationManagerTaskRequestCallbacks;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractSubscriptionTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.MediationTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.activation.qualifier.ActivationTaskRequest;

/**
 * The Statistical subscription activation task request factory.
 */
@ActivationTaskRequest(subscriptionType = StatisticalSubscription.class)
@ApplicationScoped
public class StatisticalSubscriptionActivationTaskRequestFactory extends AbstractSubscriptionTaskRequestFactory
        implements MediationTaskRequestFactory<StatisticalSubscription> {

    @Inject
    private Logger logger;

    @Inject
    private ScannerResumptionTaskRequestFactory scannerResumption;

    @Inject
    private SystemDefinedSubscriptionManager systemDefinedSubscriptionManager;

    @Inject
    private FileCollectionStateUpdateHandler fileCollectionStateUpdateHandler;

    @Inject
    private PmFunctionEnabledWrapper pmFunctionCache;

    @Override
    public List<MediationTaskRequest> createMediationTaskRequests(final List<Node> nodes, final StatisticalSubscription subscription,
                                                                  final boolean trackResponse) {
        logger.info("Resolve counters and create activation task for stats for {} nodes", nodes.size());

        final Map<String, String> nodesFdnsToActivate = new HashMap<>(nodes.size());
        final Map<String, Integer> nodeFdnExpectedNotificationMap = new HashMap<>(nodes.size());
        final List<MediationTaskRequest> tasks =
                buildNeConfigurationMediationTaskRequests(nodes, new AbstractNeConfigurationManagerTaskRequestCallbacks() {
                    @Override
                    public List<MediationTaskRequest> createMediationTaskRequest(final Node node) {
                        final List<MediationTaskRequest> mediationTaskRequests = createActivationTasks(node, subscription);
                        if (!mediationTaskRequests.isEmpty()) {
                            logger.info("Created {} Mediation Task Requests", mediationTaskRequests.size());
                            // Prepare entry for cache.
                            nodesFdnsToActivate.put(node.getFdn(), node.getNeType());
                            logger.debug("added entry to cache with key for {}", node.getFdn());
                            nodeFdnExpectedNotificationMap.put(node.getFdn(), mediationTaskRequests.size());
                        }
                        return mediationTaskRequests;
                    }

                    @Override
                    public void manageNodeWithNeConfigurationManagerDisabled(final Node node) {
                        final String nodeFdn = node.getFdn();
                        nodesFdnsToActivate.remove(nodeFdn);
                        nodeFdnExpectedNotificationMap.remove(nodeFdn);
                        if (isUserDefinedStatisticalSubscription(subscription)) {
                            try {
                                scannerService.createUnknownUserdefStatisticalScannerIfNotExist(subscription, nodeFdn);
                            } catch (final DataAccessException | RetryServiceException e) {
                                logger.error(
                                        "Couldn't create UNKNOWN userdef stats scanner for subscription {}" +
                                                " with id {} on node {}. Exception message: {}",
                                        subscription.getId(), subscription.getId(), nodeFdn, e.getMessage());
                            }
                        }
                    }
                });

        if (trackResponse && !nodesFdnsToActivate.isEmpty()) {
            addNodeFdnsToActivateToInitiationCache(subscription, nodesFdnsToActivate, nodeFdnExpectedNotificationMap);
        }

        if (tasks.isEmpty()) {
            return Collections.emptyList();
        } else {
            return tasks;
        }
    }

    private List<MediationTaskRequest> createActivationTasks(final Node node, final Subscription subscription) {
        if (isUserDefinedStatisticalSubscription(subscription)) {
            return Collections.singletonList(activateScanner(node, subscription));
        } else if (isSystemDefinedStatisticalSubscription(subscription)) {
            if (systemDefinedSubscriptionManager.hasSubscriptionPredefinedScanner(subscription.getName())) {
                try {
                    // It is needed to search on the subscription the proper scannerId to be resumed, if any
                    final List<Scanner> predefScanners = findThePredefScanners(node.getFdn(), subscription);
                    return checkPredefScannersAndCreateScannerResumptionTasksAsNeeded(node, predefScanners, subscription);
                } catch (final RetryServiceException | DataAccessException | RuntimeDataAccessException ex) {
                    logger.warn("Unable to retrieve the predef scanner from DPS for subscription {} with id {}. Exception Message: {}",
                            subscription.getName(), subscription.getId(), ex.getMessage());
                    logger.info("Unable to retrieve the predef scanner from DPS", ex);
                    subscription.setTaskStatus(TaskStatus.ERROR);
                }
            } else {
                return Collections.singletonList(activateScanner(node, subscription));
            }
        }
        return Collections.emptyList();
    }

    private MediationTaskRequest activateScanner(final Node node, final Subscription subscription) {
        logger.debug("activateScanner on node {} for user def statistical subscription {}", node.getFdn(), subscription.getName());
        return super.createActivationTask(node.getFdn(), subscription);
    }

    private List<MediationTaskRequest> checkPredefScannersAndCreateScannerResumptionTasksAsNeeded(final Node node,
                                                                                                  final List<Scanner> predefScanners,
                                                                                                  final Subscription subscription)
            throws RetryServiceException, DataAccessException {

        if (!predefScanners.isEmpty()) {
            associateSubscriptionWithScanners(predefScanners, subscription);

            if (nodeService.isMediationAutonomyEnabled(node)) {
                updateFileCollectionScheduleForActiveScanners(node, predefScanners, subscription);
            }
            return createScannerResumptionTasksForInactiveScanners(node, subscription, predefScanners);
        }
        subscription.setTaskStatus(TaskStatus.ERROR);
        return Collections.emptyList();
    }

    private void associateSubscriptionWithScanners(final List<Scanner> scanners, final Subscription subscription)
            throws RetryServiceException, DataAccessException {
        for (final Scanner scanner : scanners) {
            // Associating the subscriptionId to scanner object
            scanner.setSubscriptionId(subscription.getId());
            scannerService.saveOrUpdateWithRetry(scanner);
        }
    }

    private void updateFileCollectionScheduleForActiveScanners(final Node node, final List<Scanner> scanners,
                                                               final Subscription subscription) {
        for (final Scanner scanner : scanners) {
            if (ScannerStatus.ACTIVE.equals(scanner.getStatus())) {
                logger.debug("Active Predef scanner found for mediation autonomy enabled node {}, subscriptionId {}, " +
                        "scheduling file collection for it", node.getFdn(), subscription.getId());
                final FileCollectionState fileCollectionState = pmFunctionCache.getEntry(node.getFdn()).getFileCollectionState();
                fileCollectionStateUpdateHandler.updateFileCollectionScheduleForNodeWithMediationAutonomy(node.getFdn(), fileCollectionState);
            }
        }
    }

    private List<MediationTaskRequest> createScannerResumptionTasksForInactiveScanners(final Node node,
                                                                                       final Subscription subscription,
                                                                                       final List<Scanner> predefScanners) {
        final List<String> inactiveScannerIds = getInactiveScannerIds(node, predefScanners, subscription);
        final List<MediationTaskRequest> mediationTaskRequests;
        if (systemDefinedSubscriptionManager.hasSubscriptionMultiplePredefinedOperations(subscription.getName())) {
            mediationTaskRequests = createOneScannerResumptionTaskForMultipleScanners(node, inactiveScannerIds);
        } else {
            mediationTaskRequests = createScannerResumptionTaskForEachScanner(node, inactiveScannerIds);
        }
        logger.debug("Created {} PM resume task Requests for node {}, subscriptionId {}, resuming it",
                mediationTaskRequests.size(), node.getFdn(), subscription.getId());
        return mediationTaskRequests;
    }

    private List<String> getInactiveScannerIds(final Node node, final List<Scanner> scanners,
                                               final Subscription subscription) {
        final List<String> inactiveScannerIds = new ArrayList<>(scanners.size());
        for (final Scanner scanner : scanners) {
            if (scanner.getStatus().isOneOf(ScannerStatus.INACTIVE, ScannerStatus.UNKNOWN)) {
                logger.debug("Inactive Predef scanner found for node {}, subscriptionId {}, resuming it", node.getFdn(), subscription.getId());
                inactiveScannerIds.add(Long.toString(scanner.getPoId()));
            }
        }
        return inactiveScannerIds;
    }

    private List<MediationTaskRequest> createScannerResumptionTaskForEachScanner(final Node node,
                                                                                 final List<String> scannerIds) {
        final ArrayList<MediationTaskRequest> mediationTaskRequests = new ArrayList<>(scannerIds.size());
        for (final String scannerId : scannerIds) {
            mediationTaskRequests.add(scannerResumption.createScannerResumeTask(node.getFdn(), scannerId));
            logger.debug("Created PM resume task {} for scannerid {}", mediationTaskRequests, scannerId);
        }
        return mediationTaskRequests;
    }

    private List<MediationTaskRequest> createOneScannerResumptionTaskForMultipleScanners(final Node node,
                                                                                         final List<String> scannerIds) {
        final ArrayList<MediationTaskRequest> mediationTaskRequests = new ArrayList<>(1);
        if (!scannerIds.isEmpty()) {
            mediationTaskRequests.add(scannerResumption.createScannerResumeTask(node.getFdn(), scannerIds));
            logger.debug("Created one PM resume task {} for scannerids {}", mediationTaskRequests, scannerIds);
        }
        return mediationTaskRequests;
    }
}
