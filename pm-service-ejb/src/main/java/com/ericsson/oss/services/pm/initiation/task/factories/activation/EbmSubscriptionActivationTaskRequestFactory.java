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

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.ACTIVATION_ERROR;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.NO_EVENTS_EXISTS_FOR_ACTIVATION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.NO_PROCESSES_WITH_STATUS_INACTIVE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.dto.subscription.EbmSubscription;
import com.ericsson.oss.services.pm.common.logging.PMICLog;
import com.ericsson.oss.services.pm.common.logging.PMICLog.Operation;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractNeConfigurationManagerTaskRequestCallbacks;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractSubscriptionTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.MediationTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.activation.qualifier.ActivationTaskRequest;

/**
 * The Ebm subscription activation task request factory.
 */
@ActivationTaskRequest(subscriptionType = EbmSubscription.class)
@ApplicationScoped
public class EbmSubscriptionActivationTaskRequestFactory extends AbstractSubscriptionTaskRequestFactory
        implements MediationTaskRequestFactory<EbmSubscription> {

    public static final String PREDEF_EBMLOG_SCANNER = "PREDEF.EBMLOG.EBM";

    @Inject
    private Logger logger;

    @Override
    public List<MediationTaskRequest> createMediationTaskRequests(final List<Node> nodes, final EbmSubscription subscription,
                                                                  final boolean trackResponse) {
        if (subscription.getEventNames().isEmpty()) {
            systemRecorder.error(NO_EVENTS_EXISTS_FOR_ACTIVATION, subscription.getName(), "Activation failed", PMICLog.Operation.ACTIVATION);
            return Collections.emptyList();
        }
        return createTasks(nodes, subscription, trackResponse);
    }

    private List<MediationTaskRequest> createTasks(final List<Node> nodes, final EbmSubscription subscription, final boolean trackResponse) {
        final Map<String, String> nodesFdnsToActivate = new HashMap<>(nodes.size());
        final List<MediationTaskRequest> tasks =
                buildNeConfigurationMediationTaskRequests(nodes, new AbstractNeConfigurationManagerTaskRequestCallbacks() {
                    @Override
                    public List<MediationTaskRequest> createMediationTaskRequest(final Node node) {
                        final List<MediationTaskRequest> nodeTasks = new ArrayList<>();
                        try {
                            final Scanner scanner = findAvailableScanner(node.getFdn(), subscription.getId());
                            if (scanner == null) {
                                systemRecorder.error(NO_PROCESSES_WITH_STATUS_INACTIVE, subscription.getName(),
                                        "Failed to activate on node " + node.getFdn(),
                                        Operation.ACTIVATION);
                            } else {
                                scanner.setSubscriptionId(subscription.getId());
                                // Mark the status as UNKNOWN to allows active scanner on node to be reconfigured with events in Subscription.
                                if (scanner.getStatus() == ScannerStatus.ACTIVE) {
                                    scanner.setStatus(ScannerStatus.UNKNOWN);
                                }
                                scannerService.saveOrUpdateWithRetry(scanner);
                                final MediationTaskRequest task = createActivationTask(node.getFdn(), subscription);
                                nodeTasks.add(task);
                                nodesFdnsToActivate.put(node.getFdn(), node.getNeType());
                            }
                        } catch (final RetryServiceException | DataAccessException e) {
                            systemRecorder.error(ACTIVATION_ERROR,
                                    subscription.getName(),
                                    "There was a problem when creating activation MTR for " + node.getFdn() + " for Ebm subscription "
                                            + subscription.getName() + " with ID " + subscription.getId() + ": " + "Exception Message: "
                                            + e.getMessage(),
                                    Operation.ACTIVATION);
                            logger.info("Failed to create Task for Subscription {}", subscription.getName(), e);
                        }

                        return nodeTasks;
                    }

                    @Override
                    public void manageNodeWithNeConfigurationManagerDisabled(final Node node) {
                        nodesFdnsToActivate.remove(node.getFdn());
                    }
                });

        if (trackResponse) {
            addNodeFdnsToActivateToInitiationCache(subscription, nodesFdnsToActivate);
        }
        updateSubscriptionTaskStatusToErrorIfNodesToBeActivatedDoesNotEqualCreatedTasks(nodes, tasks, subscription);
        return tasks;
    }

    /**
     * Check if there is an EventJob scanner available with INACTIVE status on PMICScannerInfo. If a scanner is found with current subscription ID, it
     * will be chosen irrespective of status.
     */
    private Scanner findAvailableScanner(final String nodeFdn, final Long subscriptionId) throws DataAccessException, RuntimeDataAccessException {
        final List<Scanner> availableScannersOnNode = scannerService.findAllByNodeFdnAndProcessType(Collections.singleton(nodeFdn),
                ProcessType.EVENTJOB);
        Scanner chosenScanner = null;
        for (final Scanner scanner : availableScannersOnNode) {
            if (scanner.hasAssignedSubscriptionId() && Objects.equals(scanner.getSubscriptionId(), subscriptionId)) {
                logger.trace("Found EVENTJOB scanner {} already belonging to current subscription {}. Will activate this scanner.", scanner.getName(),
                        subscriptionId);
                return scanner;
            } else if (isNonAssignedScanner(scanner)) {
                chosenScanner = scanner;
            }
        }
        return chosenScanner;
    }

    private boolean isNonAssignedScanner(final Scanner scanner) {
        return !scanner.hasAssignedSubscriptionId() && PREDEF_EBMLOG_SCANNER.equals(scanner.getName());
    }

}
