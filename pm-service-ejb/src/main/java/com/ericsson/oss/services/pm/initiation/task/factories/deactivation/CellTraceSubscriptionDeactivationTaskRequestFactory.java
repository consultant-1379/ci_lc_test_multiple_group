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

package com.ericsson.oss.services.pm.initiation.task.factories.deactivation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import org.slf4j.Logger;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.services.pm.ebs.utils.EbsSubscriptionHelper;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.PmSubScannerService;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractNeConfigurationManagerTaskRequestCallbacks;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractSubscriptionTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.MediationTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.deactivation.qualifier.DeactivationTaskRequest;

/**
 * The Cell trace subscription deactivation task request factory.
 */
@DeactivationTaskRequest(subscriptionType = CellTraceSubscription.class)
@ApplicationScoped
public class CellTraceSubscriptionDeactivationTaskRequestFactory extends AbstractSubscriptionTaskRequestFactory
        implements MediationTaskRequestFactory<CellTraceSubscription> {

    @Inject
    private Logger logger;

    @Inject
    private PmSubScannerService subScannerService;

    @Inject
    private EbsSubscriptionHelper ebsSubscriptionHelper;

    /**
     * This method is to build cell trace deactivation tasks for all nodes or few set of nodes in a subscription.
     *
     * @param nodes
     *         - nodes to create deactivation tasks for
     * @param subscription
     *         - subscription to create deactivation tasks for
     *
     * @return - list of mediation task requests for deactivation
     */
    @Override
    @SuppressWarnings("PMD.ExcessiveMethodLength")
    public List<MediationTaskRequest> createMediationTaskRequests(final List<Node> nodes, final CellTraceSubscription subscription,
                                                                  final boolean trackResponse) {
        final List<Scanner> scanners = new ArrayList<>();
        final List<Scanner> ebsScanners = new ArrayList<>();
        try {
            scanners.addAll(scannerService.findAllBySubscriptionId(subscription.getId()));
            ebsScanners.addAll(subScannerService.findAllParentScannerBySubscriptionIdInReadTx(subscription.getId()));
        } catch (final DataAccessException e) {
            logger.error("Was not able to find all the scanners for the Subscription with Id {}.", subscription.getId());
            logger.info("Was not able to find all the scanners for the Subscription with Id {}.", subscription.getId(), e);
        }
        final Map<String, Integer> nodeFdnExpectedNotificationsCountMap = new HashMap<>();
        final Map<String, String> neFdnsAndTypes = new HashMap<>(nodes.size());
        final Set<String> neFdnsWithNeConfigurationManagerDisabled = new HashSet<>();
        final Map<String, String> nodesFdnsToDeactivate = new HashMap<>(nodes.size());
        final Map<String, MediationTaskRequest> nodeFdnsToTask = new HashMap<>();
        final List<MediationTaskRequest> tasks =
                buildNeConfigurationMediationTaskRequests(nodes, new AbstractNeConfigurationManagerTaskRequestCallbacks() {
                    @Override
                    public List<MediationTaskRequest> createMediationTaskRequest(final Node node) {
                        neFdnsAndTypes.put(node.getFdn(), node.getNeType());
                        return super.createMediationTaskRequest(node);
                    }

                    @Override
                    public void manageNodeWithNeConfigurationManagerDisabled(final Node node) {
                        neFdnsWithNeConfigurationManagerDisabled.add(node.getFdn());
                    }

                    @Override
                    public void tasksPostProcessing(final List<MediationTaskRequest> tasks) {
                        // The order of methods prepareEbsScannerDeactivationTaskRequest and prepareCellTraceScannerDeactivationTaskRequest is
                        // important because
                        // if the subscription category is CELLTRACE_AND_EBSL_STREAM, EBSL_STREAM, ASR OR ESN CELLTRACE_AND_EBSN_STREAM, NRAN_EBSN_STREAM we want to send an
                        // SubscriptionUpdateTaskRequest, as
                        // each
                        // node High Priority 10004 scanner might be shared by multiple subscriptions, which might trigger a different mediation flow.
                        prepareEbsScannerDeactivationTaskRequest(subscription, ebsScanners, neFdnsAndTypes, nodeFdnsToTask, nodesFdnsToDeactivate,
                                nodeFdnExpectedNotificationsCountMap, neFdnsWithNeConfigurationManagerDisabled);

                        prepareCellTraceScannerDeactivationTaskRequest(subscription, scanners, neFdnsAndTypes, nodeFdnsToTask, nodesFdnsToDeactivate,
                                nodeFdnExpectedNotificationsCountMap, neFdnsWithNeConfigurationManagerDisabled);

                        tasks.addAll(nodeFdnsToTask.values());
                    }
                });

        if (trackResponse) {
            addNodeFdnsToDeactivateToInitiationCache(subscription, nodesFdnsToDeactivate, nodeFdnExpectedNotificationsCountMap);
        }
        return tasks;
    }

    private void prepareEbsScannerDeactivationTaskRequest(final CellTraceSubscription subscription, final List<Scanner> ebsScanners,
                                                          final Map<String, String> neFdnsAndTypes,
                                                          final Map<String, MediationTaskRequest> nodeFdnsToTasks,
                                                          final Map<String, String> nodesFdnsToDeactivate,
                                                          final Map<String, Integer> nodeFdnExpectedNotificationsCountMap,
                                                          final Set<String> neFdnsWithNeConfigurationManagerDisabled) {
        for (final Scanner scanner : ebsScanners) {
            final boolean neFdnsContainsKey = neFdnsAndTypes.containsKey(scanner.getNodeFdn());
            if (neFdnsContainsKey && scanner.getStatus() == ScannerStatus.ACTIVE) {
                if (neFdnsWithNeConfigurationManagerDisabled.contains(scanner.getNodeFdn())) {
                    continue;
                }
                createDeactivationTaskIfRequired(scanner, nodeFdnsToTasks, subscription, nodeFdnExpectedNotificationsCountMap,
                                                 nodesFdnsToDeactivate, neFdnsAndTypes, DeactivationType.SELECTIVE);
            } else if (neFdnsContainsKey) {
                ebsSubscriptionHelper.deleteSubScanner(scanner);
            }
        }
    }


    private void prepareCellTraceScannerDeactivationTaskRequest(final CellTraceSubscription subscription, final List<Scanner> scanners,
                                                                final Map<String, String> neFdnsAndTypes,
                                                                final Map<String, MediationTaskRequest> nodeFdnsToTasks,
                                                                final Map<String, String> nodesFdnsToDeactivate,
                                                                final Map<String, Integer> nodeFdnExpectedNotificationsCountMap,
                                                                final Set<String> neFdnsWithNeConfigurationManagerDisabled) {
        for (final Scanner scanner : scanners) {
            final boolean neFdnsContainsKey = neFdnsAndTypes.containsKey(scanner.getNodeFdn());
            if (neFdnsContainsKey && scanner.getStatus() == ScannerStatus.ACTIVE) {
                if (neFdnsWithNeConfigurationManagerDisabled.contains(scanner.getNodeFdn())) {
                    continue;
                }
                createDeactivationTaskIfRequired(scanner, nodeFdnsToTasks, subscription, nodeFdnExpectedNotificationsCountMap,
                                                 nodesFdnsToDeactivate, neFdnsAndTypes, DeactivationType.STANDARD);
            } else if (neFdnsContainsKey){
                // Scanners should be cleaned to disassociated from subscription.
                updateSubscriptionIdToZeroAndSetStatusToUnknown(scanner);
            }
        }
    }

    private void createDeactivationTaskIfRequired(final Scanner scanner, final Map<String, MediationTaskRequest> nodeFdnsToTasks,
                                                  final CellTraceSubscription subscription, final Map<String, Integer> nodeFdnExpectedNotificationsCountMap,
                                                  final Map<String, String> nodesFdnsToDeactivate, final Map<String, String> neFdnsAndTypes,
                                                  final DeactivationType deactivationType) {
        if (!nodeFdnsToTasks.containsKey(scanner.getNodeFdn())) {
            nodeFdnsToTasks.put(scanner.getNodeFdn(), createDeactivationRequest(scanner.getNodeFdn(), subscription, deactivationType));
        }
        nodesFdnsToDeactivate.put(scanner.getNodeFdn(), neFdnsAndTypes.get(scanner.getNodeFdn()));
        setNumberOfExpectedNotifications(nodeFdnExpectedNotificationsCountMap, scanner.getNodeFdn());
    }

    private MediationTaskRequest createDeactivationRequest(final String nodeFdn, final Subscription subscription, final DeactivationType deactivationType) {
        if (deactivationType == DeactivationType.STANDARD) {
            return createDeactivationTask(nodeFdn, subscription);
        } else {
            return createSelectiveDeactivationTask(nodeFdn, subscription);
        }
    }

    private void setNumberOfExpectedNotifications(final Map<String, Integer> nodeFdnExpectedNotificationsCountMap, final String nodeFdn) {
        Integer numberExpectedNotification = nodeFdnExpectedNotificationsCountMap.get(nodeFdn);
        final Integer initialCountOfNotifications = 1;
        numberExpectedNotification = numberExpectedNotification == null ? initialCountOfNotifications : ++numberExpectedNotification;
        nodeFdnExpectedNotificationsCountMap.put(nodeFdn, numberExpectedNotification);
    }

    private enum DeactivationType {
        STANDARD,
        SELECTIVE
    }
}
