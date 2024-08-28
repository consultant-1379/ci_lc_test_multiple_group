/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.task.factories.auditor;

import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.HIGH_PRIORITY_CELLTRACE;
import static com.ericsson.oss.pmic.dto.scanner.enums.ProcessType.NORMAL_PRIORITY_CELLTRACE;
import static java.util.Arrays.asList;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.ScannerService;

/**
 * Class contains methods to identify nodes which have missing or incorrect scanners.
 */
public class CellTraceErrorNodeProcessor {

    @Inject
    protected Logger logger;

    void updateErrorScanners(final List<Pattern> supportedScanners,
                             final Map<String, List<Scanner>> nodeFdnsAndScanners,
                             final ErroneousNodes erroneousNodes,
                             final List<Node> subscriptionNodes,
                             final boolean isCellTraceNran,
                             final ScannerService scannerService) {
        for (final Node node : subscriptionNodes) {
            final List<Scanner> scannersForNode = nodeFdnsAndScanners.get(node.getFdn());
            if (scannersForNode == null) {
                erroneousNodes.addNodesWithMissingScanners(node);
            } else if (isDuplicateScannerNode(scannersForNode, supportedScanners)) {
                erroneousNodes.addNodesWithDuplicateScanners(node);
            } else if (isMissingScannerNode(node, scannersForNode, supportedScanners, scannerService, isCellTraceNran)) {
                erroneousNodes.addNodesWithMissingScanners(node);
            }
        }
    }

    private boolean isDuplicateScannerNode(final List<Scanner> scannersForNode, final List<Pattern> expectedScanners) {
        if (scannersNotSupported(expectedScanners, scannersForNode)) {
            return true;
        }
        return containsDuplicateScanners(new LinkedList<>(expectedScanners), scannersForNode);
    }

    private boolean scannersNotSupported(final List<Pattern> supportedScanners, final List<Scanner> scanners) {
        for (final Scanner scanner : scanners) {
            if (supportedScanners.stream()
                    .noneMatch(pattern -> pattern.matcher(scanner.getName()).find())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsDuplicateScanners(final List<Pattern> supportedScanners, final List<Scanner> scanners) {
        if (supportedScanners.size() < scanners.size()) {
            return true;
        }
        for (final Scanner scanner : scanners) {
            if (!supportedScanners.removeIf(pattern -> pattern.matcher(scanner.getName()).matches())) {
                return true;
            }
        }
        return false;
    }

    private boolean isMissingScannerNode(final Node node, final List<Scanner> scannersForNode, final List<Pattern> expectedScanners,
                                         final ScannerService scannerService, final boolean isCellTraceNran) {
        final List<Pattern> expectedScannersCopy = new LinkedList<>(expectedScanners);
        return scannersMissingFromNode(expectedScannersCopy, scannersForNode) &&
                shouldAddNodeToMissingScannerNodesBasedOnDpsScanners(node, expectedScannersCopy, scannerService, isCellTraceNran);
    }

    private boolean scannersMissingFromNode(final List<Pattern> supportedScanners, final List<Scanner> scannersForNode) {
        removeAlreadySelectedScanners(supportedScanners, scannersForNode);
        return !supportedScanners.isEmpty();
    }

    private void removeAlreadySelectedScanners(final List<Pattern> supportedScanners, final List<Scanner> scannersForNode) {
        for (final Scanner scanner : scannersForNode) {
            supportedScanners.removeIf(pattern -> pattern.matcher(scanner.getName()).find());
        }
    }

    boolean shouldAddNodeToMissingScannerNodesBasedOnDpsScanners(final Node node, final List<Pattern> missingScanners,
                                                                 final ScannerService scannerService,
                                                                 final boolean isCellTraceNran) {
        if (!isCellTraceNran) {
            return true;
        }
        try {
            final List<Scanner> scannersFromDps =
                    scannerService.findAllByNodeFdnAndProcessTypeInReadTx(asList(node.getFdn()), NORMAL_PRIORITY_CELLTRACE, HIGH_PRIORITY_CELLTRACE);
            return scannersFromDps.isEmpty() || scannersFromDps.stream()
                    .anyMatch(scanner -> scannerMatchesMissingScannerPattern(scanner, missingScanners));
        } catch (final DataAccessException exception) {
            logger.error("Error {} accessing database to fecth scanners for node {}", exception.getMessage(), node.getFdn(), exception);
            return true;
        }
    }

    private boolean scannerMatchesMissingScannerPattern(final Scanner scanner, final List<Pattern> missingScanners) {
        for (final Pattern pattern : missingScanners) {
            if (pattern.matcher(scanner.getName()).find()) {
                return true;
            }
        }
        return false;
    }

}
