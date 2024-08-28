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

import static java.util.Arrays.asList;

import static com.ericsson.oss.pmic.api.handler.PmMediationHandlerConstants.HandlerAttributeKey.FIVEG_RADIONODE;
import static com.ericsson.oss.pmic.dto.scanner.Scanner.MULTIPLE_EVENT_PRODUCER_HIGH_PRIORITY_CELLTRACE_SCANNER_PATTERN;
import static com.ericsson.oss.pmic.dto.scanner.Scanner.PREDEF_10005_CELLTRACE;
import static com.ericsson.oss.services.pm.initiation.common.Constants.PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.ejb.Stateless;

import com.ericsson.oss.pmic.api.selector.annotation.Selector;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;

/**
 * Auditor helper for CCTR subscriptions.
 */
@Selector(filter = "ContinuousCellTraceSubscriptionHelper")
@Stateless
public class ContinuousCellTraceSubscriptionHelper extends ResourceSubscriptionHelper {

    /**
     * Returns true if this subscription supports multiple event producers per node.
     *
     * @param subscription
     *         the {@link Subscription} to check.
     *
     * @return true if the subscription supports multiple event producers per node.
     */
    public static boolean hasMultipleEventProducersPerNode(final Subscription subscription) {
        return !PMIC_CONTINUOUSCELLTRACE_SUBSCRIPTION_NAME.equals(subscription.getName());
    }

    /**
     * Filter scanners from allScanners based on the subscription and supportedScannerNames.
     * -> IF name = ContinuousCellTraceSubscription -> filter only included PREDEF.10005.CELLTRACE scanners.
     * -> ELSE IF supportedScannerNames is not empty -> only include scanners with these names
     * -> ELSE include all PREDEF.[EP_ID].10005.CELLTRACE scanners
     *
     * @param allScanners
     *         {@link java.util.List} of all scanners.
     * @param supportedScannerNames
     *         {@link java.util.List} of all supported scanner names.
     * @param subscription
     *         {@link Subscription} the subscription.
     */
    public static void filterUnsupportedScanners(final List<Scanner> allScanners, final List<String> supportedScannerNames,
                                                 final Subscription subscription) {
        final boolean isNranSubscription = hasMultipleEventProducersPerNode(subscription);
        allScanners.removeIf(scanner -> !shouldIncludeScanner(scanner, supportedScannerNames, isNranSubscription));
    }

    private static boolean shouldIncludeScanner(final Scanner scanner, final List<String> supportedScannerNames,
                                                final boolean isNranSubscription) {
        if (isNranSubscription) {
            return isSupportedNranScanner(supportedScannerNames, scanner);
        }
        return supportedScannerNames.contains(scanner.getName());
    }

    private static boolean isSupportedNranScanner(final List<String> supportedScannerNames, final Scanner scanner) {
        return supportedScannerNames.isEmpty() || supportedScannerNames.contains(scanner.getName());
    }

    /**
     * Get the supported scanner names based on subscription and included events.
     * For CCTR :
     * -> if subscription name = ContinuousCellTraceSubscription then only support PREDEF.10005.CELLTRACE
     * -> if different name, get the supported event producers from capability
     * support scanners PREDEF.[EP_ID].10005.CELLTRACE populating EP_ID with ones read from capability.
     * -> if event producer ids not populated for subscription events, returns empty list
     *
     * @param subscription
     *         the {@link Subscription} to check.
     *
     * @return {@link java.util.List}
     * List of all supported 10005 scanners or empty list if can't determine.
     */
    public static List<String> getSupportedScannerNames(final CellTraceSubscription subscription) {
        if (!hasMultipleEventProducersPerNode(subscription)) {
            return asList(PREDEF_10005_CELLTRACE);
        }
        final Set<String> eventProducers = new HashSet<>();
        subscription.getEvents().forEach(event -> eventProducers.add(event.getEventProducerId()));
        final List<String> supportedScannerNames = new LinkedList<>();
        eventProducers.forEach(eventProducerId -> updateScannerNamesWithEventProducer(supportedScannerNames, eventProducerId));
        return supportedScannerNames;
    }

    private static void updateScannerNamesWithEventProducer(final List<String> supportedScannerNames, final String eventProducerId) {
        if (eventProducerId == null) {
            return;
        }
        supportedScannerNames.add(String.format(MULTIPLE_EVENT_PRODUCER_HIGH_PRIORITY_CELLTRACE_SCANNER_PATTERN, eventProducerId));
    }

    public boolean is5gRadioNode(final String neType) {
        return FIVEG_RADIONODE.equals(neType);
    }

}
