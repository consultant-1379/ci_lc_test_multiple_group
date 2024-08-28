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

import static com.ericsson.oss.pmic.dto.scanner.Scanner.DOT_SEPERATOR;
import static com.ericsson.oss.pmic.dto.scanner.Scanner.HIGH_PRIORITY_CELLTRACE_EVENT_JOB_10005;
import static com.ericsson.oss.services.pm.initiation.task.factories.auditor.ContinuousCellTraceSubscriptionHelper.filterUnsupportedScanners;
import static com.ericsson.oss.services.pm.initiation.task.factories.auditor.ContinuousCellTraceSubscriptionHelper.getSupportedScannerNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerType;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.ContinuousCellTraceSubscription;
import com.ericsson.oss.services.pm.collection.notification.handlers.FileCollectionOperationHelper;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.scanner.operation.ScannerOperation;
import com.ericsson.oss.services.pm.initiation.task.factories.activation.qualifier.ActivationTaskRequest;

/**
 * The  ContinuousCelltrace subscription activation task request factory.
 */
@ActivationTaskRequest(subscriptionType = ContinuousCellTraceSubscription.class)
public class CCTRSubscriptionActivationTaskRequestFactory extends CellTraceSubscriptionActivationTaskRequestFactory {

    @Inject
    private Logger logger;
    @Inject
    private ScannerOperation scannerOperation;
    @Inject
    private FileCollectionOperationHelper fileCollectionOperationHelper;

    /**
     * Check if there is an inactive high priority scanner or scanners (e.g. 5G) available with INACTIVE status on PMICScannerInfo.
     * If a scanner is found with current subscription ID, it will be chosen irrespective of status.
     *
     * @param node
     *     - the Node {@link Node}
     * @param fetchEbsScanners
     *     - boolean value to identify scanner type, not implmented for CCTR
     * @param continuousCellTraceSubscription
     *     - the subscription being activated
     * @param expectedScannerPatterns
     *     - List of expected scanner patterns with event producerIds.
     *
     * @return - List of scanners
     * @throws DataAccessException
     *     - throws data access exception
     */
    @Override
    protected List<Scanner> findScannersToActivate(final Node node, final boolean fetchEbsScanners,
                                                   final Map<String, List<String>> eventProducerIdsWithNoAvailableScannersPerNode,
                                                   final CellTraceSubscription continuousCellTraceSubscription,
                                                   final List<Pattern> expectedScannerPatterns)
        throws DataAccessException {
        final List<Scanner> availableScannersOnNode = getScannersForSubscription(continuousCellTraceSubscription, node);
        List<Scanner> chosenScanners = new ArrayList<>();
        boolean isScannerActive = false;
        for (final Scanner scanner : availableScannersOnNode) {
            if (scanner == null) {
                continue;
            }
            if (!scanner.hasAssignedSubscriptionId() && ScannerStatus.ACTIVE == scanner.getStatus()) {
                logger.debug("Found unassigned & Active scanner {} for Continuous CellTrace Subscription {}", scanner.getName(),
                    continuousCellTraceSubscription.getId());
                updateScannersSubscriptionIdAndFileCollectionAttributes(continuousCellTraceSubscription, scanner, false);
                scannerService.saveOrUpdate(scanner);
                fileCollectionOperationHelper.startFileCollection(scanner.getRopPeriod(), scanner.getNodeFdn(), scanner.getProcessType().name());
                isScannerActive = true;
            } else {
                chosenScanners.add(scanner);
            }
        }
        if (!isScannerActive && chosenScanners.isEmpty()) {
            addToUnavailableScannersForLogging(eventProducerIdsWithNoAvailableScannersPerNode, node.getFdn());
        }
        return chosenScanners;
    }

    private List<Scanner> getScannersForSubscription(final CellTraceSubscription cellTraceSubscription, final Node node) throws DataAccessException {
        final String highPriorityScannerPostfix =
            HIGH_PRIORITY_CELLTRACE_EVENT_JOB_10005 + DOT_SEPERATOR + ScannerType.HIGH_PRIORITY_CELLTRACE_10005.getNamePostfix();
        final List<Scanner> availableScannersOnNode = scannerService
            .findAllByNameAndNodeFdn(ScannerType.HIGH_PRIORITY_CELLTRACE_10005.getNamePrefix(), highPriorityScannerPostfix, node.getFdn());
        filterUnsupportedScanners(availableScannersOnNode,
            getSupportedScannerNames(cellTraceSubscription), cellTraceSubscription);
        return availableScannersOnNode;
    }
}
