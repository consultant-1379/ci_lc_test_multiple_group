/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.task.factories.validator;

import static com.ericsson.oss.services.pm.initiation.task.factories.auditor.ContinuousCellTraceSubscriptionHelper.hasMultipleEventProducersPerNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

import com.ericsson.oss.pmic.api.selector.annotation.Selector;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerType;
import com.ericsson.oss.pmic.dto.subscription.ContinuousCellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.task.factories.auditor.CellTraceSubscriptionHelper;
import com.ericsson.oss.services.pm.initiation.task.factories.auditor.ContinuousCellTraceSubscriptionHelper;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * {@inheritDoc}.
 */
@SubscriptionTaskStatusValidation(subscriptionType = ContinuousCellTraceSubscription.class)
public class ContinuousCellTraceTaskStatusValidator extends ResourceTaskStatusValidator {

    private static final String HIGH_PRIORITY_SCANNER_POSTFIX =
            Scanner.HIGH_PRIORITY_CELLTRACE_EVENT_JOB_10005 + Scanner.DOT_SEPERATOR + ScannerType.HIGH_PRIORITY_CELLTRACE_10005.getNamePostfix();

    @Inject
    private ScannerService scannerService;

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    @Inject
    @Selector(filter = "CellTraceSubscriptionHelper")
    private CellTraceSubscriptionHelper cellTraceSubscriptionHelper;

    @Inject
    @Selector(filter = "ContinuousCellTraceSubscriptionHelper")
    private ContinuousCellTraceSubscriptionHelper continuousCellTraceSubscriptionHelper;

    @Override
    protected int getSubscriptionResourceCount(final int numberOfNodesInSubscription, final Subscription subscription) throws DataAccessException {
        ContinuousCellTraceSubscription continuousCellTraceSubscription = ((ContinuousCellTraceSubscription) subscription);
        if (continuousCellTraceSubscription.getNodes().isEmpty()) {
            continuousCellTraceSubscription = (ContinuousCellTraceSubscription) subscriptionReadOperationService
                    .findOneById(subscription.getId(), true);
        }
        if (hasMultipleEventProducersPerNode(continuousCellTraceSubscription)) {
            logger.debug("Multiple scanners per node in subscription.");
            return getExpectedNumberOfScannersForSubscription(continuousCellTraceSubscription);
        }
        return numberOfNodesInSubscription;
    }

    private int getExpectedNumberOfScannersForSubscription(final ContinuousCellTraceSubscription continuousCellTraceSubscription)
            throws DataAccessException {
        final Set<String> fivegRadioNodeFdns = new HashSet<>();
        final Set<String> otherNeTypesFdns = new HashSet<>();
        for (final Node node : continuousCellTraceSubscription.getNodes()) {
            if (continuousCellTraceSubscriptionHelper.is5gRadioNode(node.getNeType())) {
                fivegRadioNodeFdns.add(node.getFdn());
            } else {
                otherNeTypesFdns.add(node.getFdn());
            }
        }
        return getExpectedNumberOfScannersByNeType(continuousCellTraceSubscription, fivegRadioNodeFdns, otherNeTypesFdns);
    }

    private int getExpectedNumberOfScannersByNeType(final ContinuousCellTraceSubscription continuousCellTraceSubscription,
                                                    final Set<String> fivegRadioNodeFdns, final Set<String> otherNeTypesFdns)
            throws DataAccessException {
        int expectedNumberOfScanners = 0;
        if (!fivegRadioNodeFdns.isEmpty()) {
            final List<Scanner> allHighPriorityScanners = getAllHighPriorityScanners(fivegRadioNodeFdns);
            expectedNumberOfScanners = hasHighPriorityScanners(allHighPriorityScanners)
                    ? allHighPriorityScanners.size()
                    : fivegRadioNodeFdns.size();
        }
        if (!otherNeTypesFdns.isEmpty()) {
            final int uniqueEventProducerIdsCount = continuousCellTraceSubscription.getEventProducerIdsFromEvents().size();
            expectedNumberOfScanners += uniqueEventProducerIdsCount * otherNeTypesFdns.size();
        }
        return expectedNumberOfScanners;
    }

    private boolean hasHighPriorityScanners(final List<Scanner> allHighPriorityScanners) {
        return allHighPriorityScanners != null && !allHighPriorityScanners.isEmpty();
    }

    private List<Scanner> getAllHighPriorityScanners(final Set<String> nodeFdns) throws DataAccessException {
        return scannerService.findAllByNameAndNodeFdn(
                ScannerType.HIGH_PRIORITY_CELLTRACE_10005.getNamePrefix(), HIGH_PRIORITY_SCANNER_POSTFIX,
                nodeFdns.toArray(new String[0]));
    }
}
