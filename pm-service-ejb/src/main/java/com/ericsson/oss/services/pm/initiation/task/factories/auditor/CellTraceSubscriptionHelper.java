/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.task.factories.auditor;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.regex.Pattern.compile;

import static com.ericsson.oss.pmic.api.handler.PmMediationHandlerConstants.HandlerAttribute.EBS_CELLTRACE_SCANNER;
import static com.ericsson.oss.pmic.api.handler.PmMediationHandlerConstants.HandlerAttribute.NORMAL_PRIORITY_CELLTRACE_SCANNER_PATTERN;
import static com.ericsson.oss.pmic.dto.scanner.Scanner.MULTIPLE_EVENT_PRODCUER_10004_SCANNER_NAME;
import static com.ericsson.oss.pmic.dto.scanner.Scanner.MULTIPLE_EVENT_PRODUCER_NORMAL_PRIORITY_CELLTRACE_SCANNER_PATTERN;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_NRAN;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_FILE;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.NRAN_EBSN_STREAM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.ejb.Stateless;
import javax.inject.Inject;

import com.ericsson.oss.pmic.api.selector.annotation.Selector;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerType;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory;
import com.ericsson.oss.services.pm.ebs.utils.EbsSubscriptionHelper;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;

/**
 * Audits the resource subscriptions to ensure that all expected scannerNames are present in DPS.
 */
@Selector(filter = "CellTraceSubscriptionHelper")
@Stateless
public class CellTraceSubscriptionHelper extends ResourceSubscriptionHelper {

    private static final Pattern EBSL_STREAM_SCANNER = compile(EBS_CELLTRACE_SCANNER);
    private static final Pattern CELLTRACE_SCANNER_FOR_LRAN = compile(NORMAL_PRIORITY_CELLTRACE_SCANNER_PATTERN);

    @Inject
    private EbsSubscriptionHelper ebsSubscriptionHelper;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;
    @Inject
    private CellTraceErrorNodeProcessor cellTraceScannerAuditor;

    @Override
    protected ErroneousNodes getNodesWithMissingAndDuplicateScanners(final List<Scanner> subscriptionScanners,
                                                                     final SubscriptionAuditorCriteria subscriptionAuditorCriteria,
                                                                     final Subscription subscription) {
        final CellTraceSubscription cellTraceSubscription = (CellTraceSubscription) subscription;
        final ErroneousNodes erroneousNodes = new ErroneousNodes();
        logger.debug("building node - scanner mapping for subscription nodes {}", cellTraceSubscription.getNodesFdns().size());
        final Map<String, List<Scanner>> nodeFdnsAndScanners = getAssociatedScannersPerNode(subscriptionScanners, cellTraceSubscription.getNodesFdns());
        logger.debug("finished building node - scanner mapping for subscription nodes {}", nodeFdnsAndScanners.size());
        final List<Pattern> expectedScannersPerNode = getSupportedScannerNamesForCellTraceCategory(cellTraceSubscription);
        cellTraceScannerAuditor.updateErrorScanners(expectedScannersPerNode, nodeFdnsAndScanners, erroneousNodes, cellTraceSubscription.getNodes(),
            isCellTraceNran(cellTraceSubscription.getCellTraceCategory()), scannerService);
        logger.debug("found {} incorrect scanners and {} nodes with missing scanners associated with the subscription {}",
            erroneousNodes.getNodesWithDuplicateScanners(), erroneousNodes.getNodesWithMissingScanners(), cellTraceSubscription.getName());
        return erroneousNodes;
    }

    private Map<String, List<Scanner>> getAssociatedScannersPerNode(final List<Scanner> scanners, final Set<String> nodeFdns) {
        final Map<String, List<Scanner>> scannersPerNode = new HashMap<>();
        for (final Scanner scanner : scanners) {
            if (nodeFdns.contains(scanner.getNodeFdn()) && !isCctrScanner(scanner)) {
                scannersPerNode.computeIfAbsent(scanner.getNodeFdn(), k -> new LinkedList<>()).add(scanner);
            } else {
                scanner.setSubscriptionId(Subscription.UNKNOWN_SUBSCRIPTION_ID);
                updateScanner(scanner);
            }
        }
        return scannersPerNode;
    }

    private boolean isCctrScanner(final Scanner scanner) {
        return ScannerType.HIGH_PRIORITY_CELLTRACE_10005.getNamePatterns()[0].matcher(scanner.getName()).find();
    }

    /**
     * Check if the subscription is cellTrace Nran.
     *
     * @param cellTraceCategory
     *     category in celltrace subscription.
     *
     * @return true if the subscription is celltrace nran, false otherwise.
     */
    public boolean isCellTraceNran(final CellTraceCategory cellTraceCategory) {
        return cellTraceCategory != null
            && cellTraceCategory.isOneOf(CELLTRACE_NRAN, CELLTRACE_NRAN_AND_EBSN_FILE, CELLTRACE_NRAN_AND_EBSN_STREAM, NRAN_EBSN_STREAM);
    }

    private void updateScanner(final Scanner scanner) {
        try {
            scannerService.saveOrUpdateWithRetry(scanner);
        } catch (final DataAccessException | RetryServiceException exception) {
            logger.info("Error while removing Cell Trace Subscription id from scanner {} : {}", scanner, exception.getMessage());
            logger.debug("Exception from update scanner", exception);
        }
    }

    /**
     * Get the supported scanner names based on subscription and included events.
     * For Cell Trace
     * -> if subscription cellTraceCategory=CELLTRACE_NRAN then support scannerNames PREDEF.[EP_ID].1000[0-3].CELLTRACE populating EP_ID with unique event producer ids from subscriptions events.
     * -> if subscription cellTraceCategory=CELLTRACE or CELLTRACE_AND_EBSL_FILE then support 1 of PREDEF.1000[0-3].CELLTRACE per node
     * -> if subscritpion cellTraceCategory=EBSL_STREAM, ASR, or ESN then support only PREDEF.10004.CELLTRACE per node
     * -> if subscription cellTraceCategory=CELLTRACE_AND_EBSL_STREAM support one PREDEF.1000[0-3].CELLTRACE and PREDEF.10004.CELLTRACE per node
     * -> if subscription cellTraceCategory=CELLTRACE_NRAN_AND_EBSN_STREAM then support PREDEF.DU/CUUP/CUCP/1000[0~3].CELLTRACE and PREDEF.DU/CUUP/CUCP/10004.CELLTRACE per node
     * -> if subscription cellTraceCategory=NRAN_EBSN_STREAM then support PREDEF.DU/CUUP/CUCP/10004.CELLTRACE per node
     *
     * @param subscription
     *     the {@link Subscription} to check.
     *
     * @return {@link java.util.List}
     * List of all supported scannerNames or empty list if can't determine.
     */
    public List<Pattern> getSupportedScannerNamesForCellTraceCategory(final CellTraceSubscription subscription) {
        final List<Pattern> scannerPatterns = getSupportedNormalPriorityScannerNamesForCellTraceCategory(subscription);
        if (ebsSubscriptionHelper.isEbsStream(subscription)) {
            scannerPatterns.addAll(getSupportedHighPriorityScannerNamesForCellTraceCategory(subscription));
        }
        return scannerPatterns;
    }

    private List<Pattern> getNranScanners(final CellTraceSubscription subscription) {
        final List<Pattern> supportedScannerNames = new LinkedList<>();
        final Set<String> eventProducers = subscription.getEventProducerIdsFromEvents();
        eventProducers.forEach(eventProducerId ->
            supportedScannerNames.add(compile(format(MULTIPLE_EVENT_PRODUCER_NORMAL_PRIORITY_CELLTRACE_SCANNER_PATTERN, eventProducerId)))
        );
        return supportedScannerNames;
    }

    private List<Pattern> getEbsNranScanners(final CellTraceSubscription subscription) {
        final List<Pattern> supportedScannerNames = new LinkedList<>();
        final Set<String> eventProducers = subscription.getEbsEventProducerIdsFromEvents();
        eventProducers.forEach(eventProducerId ->
            supportedScannerNames.add(compile(format(MULTIPLE_EVENT_PRODCUER_10004_SCANNER_NAME, eventProducerId)))
        );
        return supportedScannerNames;
    }

    /**
     * see {@link #getSupportedScannerNamesForCellTraceCategory(CellTraceSubscription)}.
     *
     * @param subscription
     *     the {@link Subscription} to check.
     *
     * @return {@link java.util.List}
     * List of all supported normal priority scannerNames or empty list if can't determine.
     */
    public List<Pattern> getSupportedNormalPriorityScannerNamesForCellTraceCategory(final CellTraceSubscription subscription) {
        if (ebsSubscriptionHelper.isEbsStreamOnlyCategory(subscription)) {
            return new ArrayList<>();
        } else if (isCellTraceNran(subscription.getCellTraceCategory())) {
            return getNranScanners(subscription);
        } else {
            return new ArrayList<>(asList(CELLTRACE_SCANNER_FOR_LRAN));
        }
    }

    public List<Pattern> getSupportedHighPriorityScannerNamesForCellTraceCategory(final CellTraceSubscription subscription) {
        if (isCellTraceNran(subscription.getCellTraceCategory())) {
            return getEbsNranScanners(subscription);
        }
        return new ArrayList<>(asList(EBSL_STREAM_SCANNER));
    }
}
