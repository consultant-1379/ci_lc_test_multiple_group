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

package com.ericsson.oss.services.pm.initiation.task.factories.activation;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.regex.Pattern.compile;

import static com.ericsson.oss.pmic.api.handler.PmMediationHandlerConstants.HandlerAttribute.EBS_CELLTRACE_SCANNER;
import static com.ericsson.oss.pmic.dto.scanner.Scanner.MULTIPLE_EVENT_PRODCUER_10004_SCANNER_NAME;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.ACTIVATION_ERROR;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.NO_PROCESSES_WITH_STATUS_INACTIVE;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.api.selector.annotation.Selector;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.pmic.util.CollectionUtil;
import com.ericsson.oss.services.pm.common.logging.PMICLog.Operation;
import com.ericsson.oss.services.pm.ebs.utils.EbsSubscriptionHelper;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractNeConfigurationManagerTaskRequestCallbacks;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractSubscriptionTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.MediationTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.activation.qualifier.ActivationTaskRequest;
import com.ericsson.oss.services.pm.initiation.task.factories.auditor.CellTraceSubscriptionHelper;

/**
 * The Cell trace subscription activation task request factory.
 */
@ActivationTaskRequest(subscriptionType = CellTraceSubscription.class)
@ApplicationScoped
public class CellTraceSubscriptionActivationTaskRequestFactory extends AbstractSubscriptionTaskRequestFactory
    implements MediationTaskRequestFactory<CellTraceSubscription> {

    @Inject
    @Selector(filter = "CellTraceSubscriptionHelper")
    protected CellTraceSubscriptionHelper cellTraceSubscriptionHelper;
    @Inject
    private Logger logger;
    @Inject
    private EbsSubscriptionHelper ebsSubscriptionHelper;

    @Override
    public List<MediationTaskRequest> createMediationTaskRequests(final List<Node> nodes, final CellTraceSubscription cellTraceSubscription,
                                                                  final boolean trackResponse) {
        final Map<String, String> nodesFdnsToActivate = new HashMap<>(nodes.size());
        final Map<String, List<String>> eventProducerIdsWithNoAvailableScannersPerNode = new HashMap<>();
        final Map<String, List<String>> nodeFdnsForUnavailableEbsScanners = new HashMap<>();
        final Map<String, Integer> nodeFdnExpectedNotificationsCountMap = new HashMap<>();
        final List<MediationTaskRequest> tasks =
            buildNeConfigurationMediationTaskRequests(nodes, new AbstractNeConfigurationManagerTaskRequestCallbacks() {
                @Override
                public List<MediationTaskRequest> createMediationTaskRequest(final Node node) {
                    try {
                        final List<Scanner> scanners = getScannersForSubscriptionAndNode(node, cellTraceSubscription,
                            eventProducerIdsWithNoAvailableScannersPerNode, nodesFdnsToActivate, false, nodeFdnExpectedNotificationsCountMap);
                        scanners.addAll(getScannersForSubscriptionAndNode(node, cellTraceSubscription, nodeFdnsForUnavailableEbsScanners,
                            nodesFdnsToActivate, true, nodeFdnExpectedNotificationsCountMap));
                        return createActivationTask(cellTraceSubscription, nodesFdnsToActivate, node, scanners);
                    } catch (final DataAccessException | IllegalArgumentException e) {
                        addToUnavailableScannersForLogging(eventProducerIdsWithNoAvailableScannersPerNode, node.getFdn());
                        recordCreateMediationTaskRequestError(node, e, cellTraceSubscription);
                    }
                    return emptyList();
                }

                @Override
                public void manageNodeWithNeConfigurationManagerDisabled(final Node node) {
                    nodesFdnsToActivate.remove(node.getFdn());
                    nodeFdnExpectedNotificationsCountMap.remove(node.getFdn());
                }
            });
        logUnavailableScannerErrors(cellTraceSubscription, eventProducerIdsWithNoAvailableScannersPerNode, nodeFdnsForUnavailableEbsScanners);
        if (trackResponse) {
            addNodeFdnsToActivateToInitiationCache(cellTraceSubscription, nodesFdnsToActivate, nodeFdnExpectedNotificationsCountMap);
        }
        updateSubscriptionTaskStatusToErrorIfThereAreUnavilableScannersForNodes(eventProducerIdsWithNoAvailableScannersPerNode,
            nodeFdnsForUnavailableEbsScanners, cellTraceSubscription);
        return tasks;
    }


    private void recordCreateMediationTaskRequestError(final Node node, final Exception e, final CellTraceSubscription cellTraceSubscription) {
        systemRecorder.error(ACTIVATION_ERROR, cellTraceSubscription.getName(),
            "Failed Cell trace subscription " + cellTraceSubscription.getName() + " activation MTR for " + node.getFdn() + "Exception Message: "
                + e.getMessage(), Operation.ACTIVATION);
        logger.info("Failed Cell trace subscription {} activation MTR for node {}.", cellTraceSubscription.getName(), node.getFdn(), e);
    }

    private List<Scanner> getScannersForSubscriptionAndNode(final Node node, final CellTraceSubscription cellTraceSubscription,
                                                            final Map<String, List<String>> eventProducerIdsWithNoAvailableScannersPerNode,
                                                            final Map<String, String> nodesFdnsToActivate, final boolean requestForEbsScanners,
                                                            final Map<String, Integer> nodeFdnExpectedNotificationsCountMap)
        throws DataAccessException {
        if (!shouldFetchScanners(requestForEbsScanners, cellTraceSubscription)) {
            return new ArrayList<>();
        }
        final List<Pattern> expectedScannerPatterns = getExpectedScannerPatterns(requestForEbsScanners, cellTraceSubscription);
        final List<Scanner> availableScanners = findScannersToActivate(node, requestForEbsScanners, eventProducerIdsWithNoAvailableScannersPerNode,
            cellTraceSubscription, expectedScannerPatterns);
        updateScannerAttributesAndSetExpectedNotifications(cellTraceSubscription, nodesFdnsToActivate, nodeFdnExpectedNotificationsCountMap,
            node, availableScanners, requestForEbsScanners);
        return availableScanners;
    }

    private boolean shouldFetchScanners(final boolean requestForEbsScanners, final CellTraceSubscription cellTraceSubscription) {
        return requestForEbsScanners ? isEbsScannerActivation(cellTraceSubscription) : isNormalPriorityCellTraceRequired(cellTraceSubscription);
    }

    private boolean isNormalPriorityCellTraceRequired(final CellTraceSubscription cellTraceSubscription) {
        return !CollectionUtil.isNullOrEmpty(cellTraceSubscription.getEvents());
    }

    private boolean isEbsScannerActivation(final CellTraceSubscription cellTraceSubscription) {
        return ebsSubscriptionHelper.isEbsStream(cellTraceSubscription);
    }

    private List<Pattern> getExpectedScannerPatterns(final boolean requestForEbsScanners, final CellTraceSubscription cellTraceSubscription) {
        if (requestForEbsScanners) {
            return getExpectedEbsScannerNames(cellTraceSubscription);
        }
        return cellTraceSubscriptionHelper.getSupportedNormalPriorityScannerNamesForCellTraceCategory(cellTraceSubscription);
    }

    private List<Pattern> getExpectedEbsScannerNames(final CellTraceSubscription cellTraceSubscription) {
        final List<Pattern> expectedScannerNames = new LinkedList<>();
        if (ebsSubscriptionHelper.isEbsn(cellTraceSubscription)) {
            cellTraceSubscription.getEbsEventProducerIdsFromEvents().forEach(eventProducerId ->
                expectedScannerNames.add(compile(format(MULTIPLE_EVENT_PRODCUER_10004_SCANNER_NAME, eventProducerId)))
            );
        } else {
            expectedScannerNames.add(compile(EBS_CELLTRACE_SCANNER));
        }
        return expectedScannerNames;
    }

    @Override
    protected void addNodeFdnsToActivateToInitiationCache(final Subscription subscription,
                                                          final Map<String, String> nodesFdnsAndTypes,
                                                          final Map<String, Integer> nodeFdnAndNumberOfExpectedNotification) {
        if (!nodesFdnsAndTypes.isEmpty() || !nodeFdnAndNumberOfExpectedNotification.isEmpty()) {
            if (!subscription.getAdministrationState().isOneOf(AdministrationState.ACTIVATING, AdministrationState.UPDATING)
                && !ebsSubscriptionHelper.isEbsStream((CellTraceSubscription) subscription)) {
                logger.info("Mediation tasks created for Activation for {} nodes. {} subscription: {} with id {} has not been added to initiation" +
                        " response cache", nodesFdnsAndTypes.size(), subscription.getType(), subscription.getName(), subscription.getId());
            } else {
                logger.info("Mediation tasks created for Activation for {} nodes , adding {} subscription: {} with id {} to initiation response " +
                        "cache", nodesFdnsAndTypes.size(), subscription.getType(), subscription.getName(), subscription.getId());
                pmicInitiationTrackerCache.startTrackingActivation(subscription.getIdAsString(),
                    subscription.getAdministrationState().name(), nodesFdnsAndTypes, nodeFdnAndNumberOfExpectedNotification);
            }
        }
    }

    private List<MediationTaskRequest> createActivationTask(final CellTraceSubscription cellTraceSubscription, final Map<String, String> nodesFdnsToActivate,
                                                            final Node node, final List<Scanner> scanners) {
        final List<MediationTaskRequest> mtr = new ArrayList<>();
        if ((scanners != null && !scanners.isEmpty())) {
            final MediationTaskRequest task = createActivationTask(node.getFdn(), cellTraceSubscription);
            mtr.add(task);
            nodesFdnsToActivate.put(node.getFdn(), node.getNeType());
        }
        return mtr;
    }

    private void logUnavailableScannerErrors(final CellTraceSubscription cellTraceSubscription,
                                             final Map<String, List<String>> eventProducerIdsWithNoAvailableScannersPerNode,
                                             final Map<String, List<String>> nodeFdnsForUnavailableEbsScanners) {
        if (!eventProducerIdsWithNoAvailableScannersPerNode.isEmpty()) {
            systemRecorder.error(NO_PROCESSES_WITH_STATUS_INACTIVE, cellTraceSubscription.getName(),
                "Failed to activate normal scanners on nodes " + eventProducerIdsWithNoAvailableScannersPerNode, Operation.ACTIVATION);
        }
        if (!nodeFdnsForUnavailableEbsScanners.isEmpty()) {
            systemRecorder.error(NO_PROCESSES_WITH_STATUS_INACTIVE, cellTraceSubscription.getName(),
                "Failed to activate EBS scanners on nodes " + nodeFdnsForUnavailableEbsScanners, Operation.ACTIVATION);
        }
    }

    private void updateSubscriptionTaskStatusToErrorIfThereAreUnavilableScannersForNodes(
        final Map<String, List<String>> eventProducerIdsWithNoAvailableScannersPerNode,
        final Map<String, List<String>> nodeFdnsForUnavailableEbsScanners,
        final CellTraceSubscription subscription) {
        if (!eventProducerIdsWithNoAvailableScannersPerNode.isEmpty() || !nodeFdnsForUnavailableEbsScanners.isEmpty()) {
            final Set<String> nodeFdnsForUnavailableScanners = new HashSet<>();
            nodeFdnsForUnavailableScanners.addAll(eventProducerIdsWithNoAvailableScannersPerNode.keySet());
            nodeFdnsForUnavailableScanners.addAll(nodeFdnsForUnavailableEbsScanners.keySet());
            logger.warn("Couldn't create activation MTRs for subscription {} with id {} for nodes {} "
                    + "Will update subscription task status to ERROR.", subscription.getName(), subscription.getId(),
                nodeFdnsForUnavailableScanners);
            updateSubscriptionTaskStatusToError(subscription);
        }
    }

    /**
     * Set the subscriptionId and file collection attributes in the scanner
     * Set the number of expected notifications for the node (how many scanners we are going to activate for this node)
     *
     * @param cellTraceSubscription
     *     - Cell trace subscription MO
     * @param nodesFdnsToActivate
     *     - a map of nodes that would be activated
     * @param nodeFdnExpectedNotificationsCountMap
     *     - number of expected notifications to activate the required number of scanners for the node
     * @param node
     *     - node MO
     * @param scanners
     *     - available scanners to activate on node
     *
     * @throws DataAccessException
     */
    private void updateScannerAttributesAndSetExpectedNotifications(final CellTraceSubscription cellTraceSubscription,
                                                                    final Map<String, String> nodesFdnsToActivate,
                                                                    final Map<String, Integer> nodeFdnExpectedNotificationsCountMap, final Node node,
                                                                    final List<Scanner> scanners, final boolean isEbsScanner) throws DataAccessException {
        final String nodeFdn = node.getFdn();
        if (!CollectionUtil.isNullOrEmpty(scanners)) {
            for (final Scanner scanner : scanners) {
                updateScannersSubscriptionIdAndFileCollectionAttributes(cellTraceSubscription, scanner, isEbsScanner);
            }
            scannerService.saveOrUpdate(scanners);

            nodesFdnsToActivate.put(nodeFdn, node.getNeType());
            setNumberOfExpectedNotifications(nodeFdnExpectedNotificationsCountMap, nodeFdn, scanners.size());
        }
    }

    /**
     * Check if there is an inactive (or unknown) normal priority scanner, that does not belong to the current subscription.
     * If a scanner is assigned with the current subscription ID, it will be chosen irrespective of status.
     * This method returns list of scanners with one element, as long as the subscription-scanner association is one-to-one.
     * Overridden version can return more than one element if subscription-scanner association is one-to-many.
     *
     * @param node
     *     - the Node {@link Node}
     * @param eventProducerIdsWithNoAvailableScannersPerNode
     *     - a map of nodes to eventProducer Ids, for which inactive normal priority scanner was not found
     * @param cellTraceSubscription
     *     - The subscription being activated
     * @param expectedScannerPatterns
     *     - List of expected scanner patterns with event producerIds.
     *
     * @return - returns a scanner to be activated.
     * @throws DataAccessException
     *     - thrown if the database cannot be reached
     */
    protected List<Scanner> findScannersToActivate(final Node node, final boolean fetchEbsScanners,
                                                   final Map<String, List<String>> eventProducerIdsWithNoAvailableScannersPerNode,
                                                   final CellTraceSubscription cellTraceSubscription,
                                                   final List<Pattern> expectedScannerPatterns) throws DataAccessException {
        final String nodeFdn = node.getFdn();
        final ProcessType processType = fetchEbsScanners ? ProcessType.HIGH_PRIORITY_CELLTRACE : ProcessType.NORMAL_PRIORITY_CELLTRACE;
        final List<Scanner> allNormalPriorityScanners =
            scannerService.findAllByNodeFdnAndProcessTypeInReadTx(Collections.singleton(nodeFdn), processType);
        return getFreeScanners(expectedScannerPatterns, allNormalPriorityScanners, nodeFdn,
            eventProducerIdsWithNoAvailableScannersPerNode, cellTraceSubscription.getId());
    }

    /**
     * Select and get scanners specific to the supplied event producer Ids. If a scanner is not available for a event producer Id, then add the node
     * and event producerId details to the supplied map.
     * Example : PREDEF.(eventProducerId).1000[0-3].CELLTRACE
     *
     * @param expectedScannerPatterns
     *     - List of expected scanner patterns with event producerIds.
     * @param scannersToSelectFrom
     *     - all normal priority scanners from the node.
     * @param nodeFdn
     *     - node Fdn
     * @param eventProducerIdsWithNoAvailableScannersPerNode
     *     - a map of nodes to eventProducerIds, for which inactive normal scanner was not found
     * @param subscriptionId
     *     - subscription Id
     *
     * @return - a list of free scanners to associate to the subscription.
     */
    private List<Scanner> getFreeScanners(final List<Pattern> expectedScannerPatterns,
                                          final List<Scanner> scannersToSelectFrom,
                                          final String nodeFdn,
                                          final Map<String, List<String>> eventProducerIdsWithNoAvailableScannersPerNode,
                                          final Long subscriptionId) {
        final List<Scanner> chosenScanners = new ArrayList<>();
        for (final Pattern expectedScannerPattern : expectedScannerPatterns) {
            final List<Scanner> potentialScanners = scannersToSelectFrom.stream()
                .filter(scanner -> expectedScannerPattern.matcher(scanner.getName()).find())
                .collect(Collectors.toList());
            scannersToSelectFrom.removeAll(potentialScanners);
            final Optional<Scanner> chosenScanner = findScannerWithInactiveOrUnknownStatusFromFilteredList(subscriptionId, potentialScanners);
            if (chosenScanner.isPresent()) {
                chosenScanners.add(chosenScanner.get());
            } else {
                addToUnavailableScannersForLogging(eventProducerIdsWithNoAvailableScannersPerNode, nodeFdn, expectedScannerPattern);
            }
        }
        return chosenScanners;
    }

    /**
     * Find inactive scanners / scanners already assigned to the subscription.
     *
     * @param subscriptionId
     *     - subscription Id
     * @param availableScanners
     *     - available scanners
     *
     * @return
     */
    private Optional<Scanner> findScannerWithInactiveOrUnknownStatusFromFilteredList(final Long subscriptionId, final List<Scanner> availableScanners) {
        Optional<Scanner> chosenScanner = Optional.empty();
        for (final Scanner scanner : availableScanners) {
            if (scannerIsAvailable(subscriptionId, scanner)) {
                chosenScanner = Optional.of(scanner);
                break;
            }
        }
        return chosenScanner;
    }

    private boolean scannerIsAvailable(final Long subscriptionId, final Scanner scanner) {
        if (scanner.getProcessType().equals(ProcessType.HIGH_PRIORITY_CELLTRACE) || isNonAssignedInactiveOrUnknownScanner(scanner)) {
            return true;
        } else if (Objects.equals(subscriptionId, scanner.getSubscriptionId())) {
            logger.trace("Found celltrace scanner {} already belonging to current subscription {}. Will activate this scanner.",
                scanner.getName(), subscriptionId);
            return true;
        }
        return false;
    }

    /**
     * Add unavailable scanners for nodes with event producer Id to the supplied map.
     *
     * @param eventProducerIdsWithNoAvailableScannersPerNode
     *     - a map of nodes to eventProducerIds, for which free scanner was not found
     * @param nodeFdn
     *     - node Fdn
     * @param scannerPattern
     *     - set of event producer Ids.
     */
    private void addToUnavailableScannersForLogging(final Map<String, List<String>> eventProducerIdsWithNoAvailableScannersPerNode,
                                                    final String nodeFdn,
                                                    final Pattern scannerPattern) {
        if (!eventProducerIdsWithNoAvailableScannersPerNode.containsKey(nodeFdn)) {
            eventProducerIdsWithNoAvailableScannersPerNode.put(nodeFdn, new ArrayList<>());
        }
        eventProducerIdsWithNoAvailableScannersPerNode.get(nodeFdn).add(scannerPattern.toString());
    }

    /**
     * Add unavailable scanners for nodes to the supplied map.
     *
     * @param eventProducerIdsWithoutAvailableScannersPerNode
     *     - a map of nodes to eventProducer Ids, for which inactive scanner was not found
     * @param nodeFdn
     *     - node Fdn
     */
    protected void addToUnavailableScannersForLogging(final Map<String, List<String>> eventProducerIdsWithoutAvailableScannersPerNode,
                                                      final String nodeFdn) {
        if (!eventProducerIdsWithoutAvailableScannersPerNode.containsKey(nodeFdn)) {
            eventProducerIdsWithoutAvailableScannersPerNode.put(nodeFdn, new ArrayList<>());
        }
    }

    /**
     * To set the number of expected notifications.
     *
     * @param nodeFdnExpectedNotificationsCountMap
     * @param nodeFdn
     */
    private void setNumberOfExpectedNotifications(final Map<String, Integer> nodeFdnExpectedNotificationsCountMap, final String nodeFdn,
                                                  final int initialCountOfNotifications) {
        Integer numberExpectedNotifications = nodeFdnExpectedNotificationsCountMap.get(nodeFdn);
        numberExpectedNotifications = numberExpectedNotifications == null ? initialCountOfNotifications : numberExpectedNotifications + initialCountOfNotifications;
        nodeFdnExpectedNotificationsCountMap.put(nodeFdn, numberExpectedNotifications);
    }

    /**
     * Update dps scanners.
     *
     * @param cellTraceSubscription
     *     - Subscription Object
     * @param scanner
     *     -  scanner Object
     * @param isEbsScanner
     *     - is ebsScanner
     */
    protected void updateScannersSubscriptionIdAndFileCollectionAttributes(final CellTraceSubscription cellTraceSubscription, final Scanner scanner,
                                                                           final boolean isEbsScanner) {
        if (isEbsScanner) {
            scanner.setFileCollectionEnabled(false);
        } else {
            scanner.setSubscriptionId(cellTraceSubscription.getId());
            final boolean isFileCollectionEnabled = !OutputModeType.STREAMING.equals(cellTraceSubscription.getOutputMode());
            scanner.setFileCollectionEnabled(isFileCollectionEnabled);
        }
    }

}
