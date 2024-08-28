/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.task.factories.auditor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.Stateless;
import javax.enterprise.inject.Default;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.recording.EventLevel;
import com.ericsson.oss.itpf.sdk.recording.SystemRecorder;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.generic.PmSubScannerService;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.constants.PmicLogCommands;
import com.ericsson.oss.services.pm.initiation.notification.events.Activate;
import com.ericsson.oss.services.pm.initiation.notification.events.Deactivate;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEvent;
import com.ericsson.oss.services.pm.initiation.task.TaskStatusValidator;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * Audits the resource subscriptions to ensure that all expected scanners are present in DPS
 */
@Default
@Stateless
public class ResourceSubscriptionHelper {

    @Inject
    protected Logger logger;
    @Inject
    protected NodeService nodeService;
    @Inject
    protected ScannerService scannerService;
    @Inject
    @Activate
    protected InitiationEvent activationEvent;
    @Inject
    @Deactivate
    protected InitiationEvent deactivationEvent;
    @Inject
    protected SystemRecorder systemRecorder;
    @Inject
    protected PmSubScannerService subScannerService;
    @Inject
    protected SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    @SubscriptionTaskStatusValidation
    private TaskStatusValidator<Subscription> taskStatusValidator;

    /**
     * Audits the resource subscriptions to ensure that all expected scanners are present in DPS
     *
     * @param subscriptionAuditorCriteria
     *         - The criteria to narrow down which subscription to audit
     * @param subscriptionId
     *         - poid of subscriptionObjects
     */
    public void audit(final SubscriptionAuditorCriteria subscriptionAuditorCriteria, final long subscriptionId) {
        try {
            final Subscription activeSubscription = subscriptionReadOperationService.findOneById(subscriptionId, true);
            if (!activeSubscription.hasAdministrationState(AdministrationState.ACTIVE)) {
                logger.info("The Subscription status changed from Active. Audit is skipped for: {}", activeSubscription.getName());
                return;
            }
            if (subscriptionAuditorCriteria.shouldAuditSubscription(activeSubscription)) {
                checkSubscriptionForScannerManagement(subscriptionAuditorCriteria, activeSubscription);
                checkSubscriptionForExtraCriteria(activeSubscription);
            }
        } catch (final DataAccessException exception) {
            logger.error("DataAccessException:{}", exception.getMessage());
            logger.info("DataAccessException", exception);
        }
    }

    /**
     * Gets Active Subscription
     *
     * @param subscriptionType
     *     - type of subscription
     *
     * @return list of subscriptionIds
     */
    public List<Long> getActiveSubscriptionIds(final SubscriptionType subscriptionType) {
        try {
            final List<Long> subscriptionIds = subscriptionReadOperationService.findAllIdsBySubscriptionType(subscriptionType);
            logger.info("Auditing {}, Found {} subscriptions", subscriptionType, subscriptionIds.size());
            return subscriptionIds;
        } catch (final DataAccessException exception) {
            logger.error("DataAccessException:{}", exception.getMessage());
            logger.info("DataAccessException", exception);
        }
        return Collections.emptyList();
    }

    /**
     * Method finds nodes in subscription with missing or duplicate scanenrs and sends activation/deacitvation
     * requests as necessary.
     *
     * @param subscriptionAuditorCriteria
     *         auditor criteria.
     * @param subscription
     *         the subscription.
     *
     * @throws DataAccessException
     *         - throws dps data access exception
     */
    void checkSubscriptionForScannerManagement(final SubscriptionAuditorCriteria subscriptionAuditorCriteria,
                                               final Subscription subscription) throws DataAccessException {
        final ErroneousNodes nodesWithMissingAndDuplicateScanners = getNodesWithMissingAndDuplicateScanners(subscription,
            subscriptionAuditorCriteria);
        if (!nodesWithMissingAndDuplicateScanners.getNodesWithMissingScanners().isEmpty()) {
            sendActivationEvents(subscription, nodesWithMissingAndDuplicateScanners.getNodesWithMissingScanners());
        }
        if (subscriptionAuditorCriteria.deactivateDuplicateScanners()
            && !nodesWithMissingAndDuplicateScanners.getNodesWithDuplicateScanners().isEmpty()) {
            sendDeactivationEvents(subscription, nodesWithMissingAndDuplicateScanners.getNodesWithDuplicateScanners());
        }
        if (subscription.getTaskStatus().isOneOf(TaskStatus.ERROR) && nodesWithMissingAndDuplicateScanners.getNodesWithMissingScanners().isEmpty()
            && nodesWithMissingAndDuplicateScanners.getNodesWithDuplicateScanners().isEmpty()) {
            taskStatusValidator.validateTaskStatusAndAdminState(subscription);
        }
    }

    /**
     * Holder for extra checking required for subscription types extending Resource. Default no processing
     *
     * @param subscription
     *         - the subscription
     */
    protected void checkSubscriptionForExtraCriteria(final Subscription subscription) {
        //implemented by sub-classes where neeeded, no default action.
    }

    /**
     * Get lists of nodes for subscription with missing or duplicate scanners.
     *
     * @param subscription
     *         the subscription.
     * @param subscriptionAuditorCriteria
     *         auditor criteria.
     *
     * @return {@link ErroneousNodes} error nodes object.
     * @throws DataAccessException
     *         - throws dps data access exception
     */
    ErroneousNodes getNodesWithMissingAndDuplicateScanners(final Subscription subscription,
                                                           final SubscriptionAuditorCriteria subscriptionAuditorCriteria)
        throws DataAccessException {
        final List<Node> nodes = getNodesFromSubscription(subscription);
        logger.info("Found {} nodes for subscription {}", nodes.size(), subscription.getName());
        logger.debug("Found nodes {}", nodes);
        final List<Scanner> scanners = scannerService.findAllBySubscriptionIdInNewReadTx(subscription.getId());
        if (subscriptionAuditorCriteria.isSharedScannerSubscription(subscription)) {
            logger.info("Found Scanner {}", scanners.size());
            scanners.addAll(subScannerService.findAllParentScannerBySubscriptionIdInReadTx(subscription.getId()));
            logger.debug("Found Scanner including shared scanner {}", scanners.size());
        }
        final ErroneousNodes nodesWithMissingAndDuplicateScanners = getNodesWithMissingAndDuplicateScanners(scanners, subscriptionAuditorCriteria,
            subscription);
        logger.info("Found {} missing scanners, {} duplicate scanners", nodesWithMissingAndDuplicateScanners.getNodesWithMissingScanners().size(),
            nodesWithMissingAndDuplicateScanners.getNodesWithDuplicateScanners().size());
        logger.debug("Found missing and duplicate scanners:{}", nodesWithMissingAndDuplicateScanners);
        return nodesWithMissingAndDuplicateScanners;
    }

    /**
     * Identify which nodes have missing or duplicate scanners and update ErroreousNodes object with these.
     *
     * @param subscriptionScanners
     *         the list of scanners from the sbuscription.
     * @param subscriptionAuditorCriteria
     *         the subscriptionAuditorCriteria for subscription.
     * @param subscription
     *         the subscription.
     *
     * @return ErroreousNodes.
     * @throws DataAccessException
     *         - throws dps data access exception
     */
    protected ErroneousNodes getNodesWithMissingAndDuplicateScanners(final List<Scanner> subscriptionScanners,
                                                                     final SubscriptionAuditorCriteria subscriptionAuditorCriteria,
                                                                     final Subscription subscription) throws DataAccessException {
        final ErroneousNodes erroneousNodes = new ErroneousNodes();
        final Map<String, Integer> nodeFdnToCountOfActualScanners = getCountOfActualScannersPerNodeFdn(subscriptionScanners, subscription);
        final List<Node> nodes = getNodesFromSubscription(subscription);
        for (final Node node : nodes) {
            if (nodeFdnToCountOfActualScanners.containsKey(node.getFdn())) {
                updateErrorNodesWhenAssociateScannersFound(node, subscription, nodeFdnToCountOfActualScanners, erroneousNodes, subscriptionAuditorCriteria);
            } else { // scanner is not allocated
                erroneousNodes.addNodesWithMissingScanners(node);
            }
        }
        return erroneousNodes;
    }

    /**
     * Gets Nodes from Subscription
     *
     * @param subscription
     *         - The subscription
     *
     * @return nodes
     */
    protected List<Node> getNodesFromSubscription(final Subscription subscription) {
        return ((ResourceSubscription) subscription).getNodes();
    }

    /**
     * Send activation event for subscription with nodes.
     *
     * @param subscription
     *         the subscription.
     * @param nodesWithMissingScanners
     *         the nodes.
     */
    protected void sendActivationEvents(final Subscription subscription, final List<Node> nodesWithMissingScanners) {
        activationEvent.execute(nodesWithMissingScanners, subscription);
        log("activation", subscription, nodesWithMissingScanners);
    }

    /**
     * Send deactivation event for subscription with nodes.
     *
     * @param subscription
     *         the subscription.
     * @param nodesWithDuplicateScanners
     *         the nodes.
     */
    protected void sendDeactivationEvents(final Subscription subscription, final List<Node> nodesWithDuplicateScanners) {
        deactivationEvent.execute(nodesWithDuplicateScanners, subscription);
        log("deactivation", subscription, nodesWithDuplicateScanners);
    }

    private void log(final String action, final Subscription subscription, final List<Node> nodes) {
        systemRecorder.recordEvent(PmicLogCommands.PMIC_SUBSCRIPTION_AUDIT.getDescription(), EventLevel.COARSE, getClass().getSimpleName(),
            "PMICService",
            String.format("Initiated %s tasks for (%s) nodes for subscription (%s)", action, nodes.size(), subscription.getName()));
        logger.info("Initiated {} {} tasks. They may not be sent, e.g. if PmFunction is off", action, nodes.size());
        logger.debug("Initiated {} tasks for nodes:{}", action, nodes);
    }

    void updateErrorNodesWhenAssociateScannersFound(final Node node, final Subscription subscription, final Map<String, Integer> nodeFdnsToScannerCount,
                                                    final ErroneousNodes erroneousNodes, final SubscriptionAuditorCriteria subscriptionAuditorCriteria) {
        final int expectedScannersPerNode = subscriptionAuditorCriteria.getExpectedNumberOfScannersPerNode(subscription, node, Collections.emptyMap());
        if (nodeFdnsToScannerCount.get(node.getFdn()) < expectedScannersPerNode) {
            erroneousNodes.addNodesWithMissingScanners(node);
        } else if (nodeFdnsToScannerCount.get(node.getFdn()) > expectedScannersPerNode) {
            erroneousNodes.addNodesWithDuplicateScanners(node);
        }
    }

    /**
     * Return map of node FDNs and expected count of scanners per each nodeFdn.
     */
    private Map<String, Integer> getCountOfActualScannersPerNodeFdn(final List<Scanner> subscriptionScanners, final Subscription subscription) {
        final Set<String> nodeFdns = getNodeFdnsFromSubscription(subscription);
        final Map<String, Integer> scannersPerNode = new HashMap<>();
        for (final Scanner scanner : subscriptionScanners) {
            updateScannersPerNode(scanner, nodeFdns, scannersPerNode);
        }
        return scannersPerNode;
    }

    void updateScannersPerNode(final Scanner scanner, final Set<String> nodeFdns, final Map<String, Integer> scannersPerNode) {
        final String nodeFdn = scanner.getNodeFdn();
        final Integer numberOfScanners = scannersPerNode.get(nodeFdn);
        if (nodeFdns.contains(nodeFdn)) {
            if (numberOfScanners == null) {
                scannersPerNode.put(nodeFdn, 1);
            } else {
                scannersPerNode.put(nodeFdn, numberOfScanners + 1);
            }
        } else {
            updateSubscriptionIdToZeroAndSetStatusToUnknown(scanner);
        }
    }

    /**
     * Gets nodes Fdns from Subscription
     *
     * @param subscription
     *         - The Subscription
     *
     * @return Set of nodes fdns
     */
    protected Set<String> getNodeFdnsFromSubscription(final Subscription subscription) {
        return ((ResourceSubscription) subscription).getNodesFdns();
    }

    /**
     * Updates the scanner with Id zero and Status to unknown
     *
     * @param scanner
     *         - The Scanner
     */
    private void updateSubscriptionIdToZeroAndSetStatusToUnknown(final Scanner scanner) {
        logger.info("updating Scanner Status to UNKNOWN and SubscriptionID to 0 for scanner {} ", scanner.getFdn());
        scanner.setSubscriptionId(Subscription.UNKNOWN_SUBSCRIPTION_ID);
        scanner.setStatus(ScannerStatus.UNKNOWN);
        try {
            scannerService.saveOrUpdateWithRetry(scanner);
        } catch (final RetryServiceException | DataAccessException exception) {
            logger.error("Cannot update Scanner Status to UNKNOWN and SubscriptionID to 0 for scanner {}. Exception message: {}", scanner.getFdn(),
                exception.getMessage());
            logger.info("Cannot update Scanner Status to UNKNOWN and SubscriptionID to 0 for scanner [{}].", scanner.getFdn(), exception);
        }
    }
}
