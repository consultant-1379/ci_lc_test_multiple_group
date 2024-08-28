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

package com.ericsson.oss.services.pm.initiation.task.factories.deactivation;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.DEACTIVATE_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_MODEL_NAME;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_NAME_POSTFIX_CONT_STATS;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_NAME_PREFIX_USERDEF;

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
import com.ericsson.oss.services.pm.common.systemdefined.SystemDefinedSubscriptionManager;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.initiation.scanner.lifecycle.task.factories.suspension.ScannerSuspensionTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractNeConfigurationManagerTaskRequestCallbacks;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractSubscriptionTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.MediationTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.deactivation.qualifier.DeactivationTaskRequest;

/**
 * The Statistical subscription deactivation task request factory.
 */
@DeactivationTaskRequest(subscriptionType = StatisticalSubscription.class)
@ApplicationScoped
public class StatisticalSubscriptionDeactivationTaskRequestFactory extends AbstractSubscriptionTaskRequestFactory
        implements MediationTaskRequestFactory<StatisticalSubscription> {

    @Inject
    private Logger logger;

    @Inject
    private ScannerSuspensionTaskRequestFactory scannerSuspension;

    @Inject
    private SystemDefinedSubscriptionManager systemDefinedSubscriptionManager;

    @Override
    @SuppressWarnings({"PMD.ExcessiveMethodLength", "PMD.CyclomaticComplexity"})
    public List<MediationTaskRequest> createMediationTaskRequests(final List<Node> nodes, final StatisticalSubscription subscription,
                                                                  final boolean trackResponse) {
        logger.debug("Deactivating Statistical Subscription {}", subscription.getName());

        final Map<String, String> neFdnsAndTypes = new HashMap<>(nodes.size());
        final Map<String, Integer> nodeFdnExpectedNotificationMap = new HashMap<>(nodes.size());
        // For each node in the list, create a deactivation task or one or more resumption tasks for that node,
        // add it/them to the task list
        final List<MediationTaskRequest> tasks =
                buildNeConfigurationMediationTaskRequests(nodes, new AbstractNeConfigurationManagerTaskRequestCallbacks() {
                    @Override
                    public List<MediationTaskRequest> createMediationTaskRequest(final Node node) {
                        final List<MediationTaskRequest> nodeTasks = new ArrayList<>();
                        final String scannerFdn = buildUserDefScannerFdn(node.getFdn(), subscription);
                        if (!"".equals(scannerFdn)) {
                            Scanner scanner = null;
                            try {
                                scanner = scannerService.findOneByFdn(scannerFdn);
                            } catch (final DataAccessException | RuntimeDataAccessException e) {
                                logger.error("Could not find scanner in DPS for scannerFdn {}.", scannerFdn);
                                logger.info("Could not find scanner in DPS for scannerFdn {}. {}", scannerFdn, e);
                            }
                            if (scanner == null) {
                                systemRecorder.commandOngoing(DEACTIVATE_SUBSCRIPTION, subscription.getIdAsString(), "No scanner found on node: %s",
                                        node.getFdn());
                                return nodeTasks;
                            }
                        }
                        nodeTasks.addAll(
                                createDeactivationSuspensionTasksAndPrepareCacheEntries(node, subscription, neFdnsAndTypes,
                                        nodeFdnExpectedNotificationMap));
                        if (!nodeTasks.isEmpty()) {
                            logger.debug("{} new deactivation/suspension mtr requests for node {} : expected notifications={}",
                                    nodeTasks.size(), node.getFdn(), nodeFdnExpectedNotificationMap.get(node.getFdn()));
                        }
                        return nodeTasks;
                    }

                    @Override
                    public void manageNodeWithNeConfigurationManagerDisabled(final Node node) {
                        neFdnsAndTypes.remove(node.getFdn());
                        nodeFdnExpectedNotificationMap.remove(node.getFdn());
                    }
                });

        if (trackResponse && !neFdnsAndTypes.isEmpty()) {
            addNodeFdnsToDeactivateToInitiationCache(subscription, neFdnsAndTypes, nodeFdnExpectedNotificationMap);
        }

        if (tasks.isEmpty()) {
            return Collections.emptyList();
        } else {
            return tasks;
        }
    }

    private List<MediationTaskRequest> createDeactivationSuspensionTasksAndPrepareCacheEntries(final Node node, final Subscription subscription,
                                                                                               final Map<String, String> neFdnsAndTypes,
                                                                                               final Map<String, Integer> nodeFdnExpectedNotifMap) {
        if (isUserDefinedStatisticalSubscription(subscription)) {
            return Collections.singletonList(deactivateScanner(node, subscription, neFdnsAndTypes, nodeFdnExpectedNotifMap));
        } else if (isSystemDefinedStatisticalSubscription(subscription)) {
            if (systemDefinedSubscriptionManager.hasSubscriptionPredefinedScanner(subscription.getName())) {
                try {
                    // It is needed to search on the subscription the proper scannerId to be suspended
                    final List<Scanner> scanners = findThePredefScanners(node.getFdn(), subscription);
                    return suspendPredefScanners(node, scanners, subscription, neFdnsAndTypes, nodeFdnExpectedNotifMap);
                } catch (final DataAccessException | RuntimeDataAccessException ex) {
                    logger.warn("Unable to retrieve the predef scanner from DPS for subscription {}", subscription.getId());
                    logger.debug("Got exception {}", ex);
                }
            } else {
                return Collections.singletonList(deactivateScanner(node, subscription, neFdnsAndTypes, nodeFdnExpectedNotifMap));
            }
        }
        return Collections.emptyList();
    }

    private MediationTaskRequest deactivateScanner(final Node node, final Subscription subscription, final Map<String, String> neFdnsAndTypes,
                                                   final Map<String, Integer> nodeFdnExpectedNotifMap) {
        logger.debug("deactivateScanner on node {} for user def statistical subscription {}", node.getFdn(), subscription.getName());
        neFdnsAndTypes.put(node.getFdn(), node.getNeType());
        nodeFdnExpectedNotifMap.put(node.getFdn(), 1);
        return super.createDeactivationTask(node.getFdn(), subscription);
    }

    private List<MediationTaskRequest> suspendPredefScanners(final Node node, final List<Scanner> scanners, final Subscription subscription,
                                                             final Map<String, String> neFdnsAndTypes,
                                                             final Map<String, Integer> nodeFdnExpectedNotifMap) {
        if (!scanners.isEmpty()) {
            if (systemDefinedSubscriptionManager.hasSubscriptionMultiplePredefinedOperations(subscription.getName())) {
                return taskForMultiplePredefinedOperations(node, scanners, subscription, neFdnsAndTypes, nodeFdnExpectedNotifMap);
            } else {
                return tasksForSinglePredfinedOperations(node, scanners, subscription, neFdnsAndTypes, nodeFdnExpectedNotifMap);
            }
        }
        return Collections.emptyList();
    }

    private List<MediationTaskRequest> taskForMultiplePredefinedOperations(final Node node, final List<Scanner> scanners,
                                                                           final Subscription subscription,
                                                                           final Map<String, String> neFdnsAndTypes,
                                                                           final Map<String, Integer> nodeFdnExpectedNotifMap) {
        // Generate 1 suspension MTR for all the predef scanners of a node (scannerIdList is passed to MTR)
        final List<String> scannerIdList = new ArrayList<>(scanners.size());
        final ArrayList<MediationTaskRequest> task = new ArrayList<>(1);
        for (final Scanner scanner : scanners) {
            if (scanner.getStatus() == ScannerStatus.ACTIVE) {
                logger.debug("Active Predef scanner found for node {} and subsd {}, suspend it", node.getFdn(), subscription.getId());
                scannerIdList.add(Long.toString(scanner.getPoId()));
            } else {
                // Scanners should be cleaned to disassociated from subscription.
                updateSubscriptionIdToZeroAndSetStatusToUnknown(scanner);
            }
        }
        if (!scannerIdList.isEmpty()) {
            task.add(scannerSuspension.createScannerSuspensionTask(node.getFdn(), scannerIdList));
            neFdnsAndTypes.put(node.getFdn(), node.getNeType());
            logger.debug("added entry to cache with key for {}", node.getFdn());
            nodeFdnExpectedNotifMap.put(node.getFdn(), scannerIdList.size());
        }
        return task;
    }

    private List<MediationTaskRequest> tasksForSinglePredfinedOperations(final Node node, final List<Scanner> scanners,
                                                                         final Subscription subscription,
                                                                         final Map<String, String> neFdnsAndTypes,
                                                                         final Map<String, Integer> nodeFdnExpectedNotifMap) {
        // Generate 1 suspension MTR for each of predef scanners of a node (scannerId is passed to MTR)
        final ArrayList<MediationTaskRequest> tasks = new ArrayList<>(scanners.size());
        for (final Scanner scanner : scanners) {
            // When the predef scanner is already inactive no suspension MTR has to be generated
            if (scanner.getStatus() == ScannerStatus.ACTIVE) {
                logger.debug("Active Predef scanner found for node {} and subsd {}, suspend it", node.getFdn(), subscription.getId());
                final String scannerId = Long.toString(scanner.getPoId());
                tasks.add(scannerSuspension.createScannerSuspensionTask(node.getFdn(), scannerId));
                // Prepare entry for cache
                neFdnsAndTypes.put(node.getFdn(), node.getNeType());
                logger.debug("added entry to cache with key for {}", node.getFdn());
            } else {
                // Scanners should be cleaned to disassociated from subscription.
                updateSubscriptionIdToZeroAndSetStatusToUnknown(scanner);
            }
        }
        if (!tasks.isEmpty()) {
            nodeFdnExpectedNotifMap.put(node.getFdn(), tasks.size());
        }
        return tasks;
    }

    private String buildUserDefScannerFdn(final String nodeFdn, final Subscription subscription) {
        String scannerFdn = "";
        if (isSystemDefinedStatisticalSubscription(subscription)
                && systemDefinedSubscriptionManager.hasSubscriptionPredefinedScanner(subscription.getName())) {
            logger.debug("{} It's a System Defined Statistical, no need to search for scanner", subscription.getName());
        } else {
            scannerFdn = nodeFdn + "," + SCANNER_MODEL_NAME + "=";
            scannerFdn = scannerFdn.concat(SCANNER_NAME_PREFIX_USERDEF + subscription.getName() + SCANNER_NAME_POSTFIX_CONT_STATS);
            logger.debug("subscriptionId {} is a User Def Statistical, searching for scanner {}", subscription.getName(), scannerFdn);
        }
        return scannerFdn;
    }
}
