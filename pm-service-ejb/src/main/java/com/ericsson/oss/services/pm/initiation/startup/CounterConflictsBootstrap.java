/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2014
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.startup;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.ejb.CounterConflictServiceImpl;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * On scanner handling on startup, we can use scanners to bootstrap the Active Node cache.
 */
@Stateless
public class CounterConflictsBootstrap {

    @Inject
    private Logger logger;
    @Inject
    private ScannerService scannerService;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private CounterConflictServiceImpl counterConflictCacheService;

    /**
     * emaddav: As part of scanner handling on startup, we can use scanners to bootstrap the Active Node cache.
     *
     * @return - returns true if ActiveCountersCache is activated, false if cache could not be populated/exception is thrown
     */
    @Asynchronous
    public Future<Boolean> bootstrapActiveCountersCache() {
        logger.debug("Bootstrapping Counter Conflicts caches ... ");

        final List<Scanner> scanners;
        try {
            scanners = scannerService.findAllByProcessType(ProcessType.STATS);
        } catch (final DataAccessException e) {
            logger.error("Cannot find all STATS scanners. DataAccessException thrown with message: {}", e.getMessage());
            logger.error("Cannot find all STATS scanners. ", e);
            return new AsyncResult<>(false);
        }

        final Map<Long, List<CounterInfo>> subCountersMap = new HashMap<>();
        for (final Scanner scanner : scanners) {
            addScannerToCounterConflictService(scanner, subCountersMap);
        }
        return new AsyncResult<>(true);
    }

    private void addScannerToCounterConflictService(final Scanner scanner, final Map<Long, List<CounterInfo>> subCountersMap) {
        /*
         * A lot of Subscriptions will repeat here, so cache a local Map helps reduce calls to Data Persistence Service
         */
        final String nodeFdn = scanner.getNodeFdn();
        logger.debug("Found Active Node: {}. Adding to Active Node Cache.", nodeFdn);

        final Long subId = scanner.getSubscriptionId();
        if (!scanner.hasAssignedSubscriptionId()) {
            return;
        }
        final String subscriptionName = Scanner.getSubscriptionNameFromScanner(scanner.getName());
        if (subscriptionName == null) {
            return;
        }
        if (subCountersMap.containsKey(subId)) {
            counterConflictCacheService.addNodesToExistingSubscriptionEntry(subscriptionName, Collections.singleton(nodeFdn));
        } else {
            try {
                final StatisticalSubscription subscription = (StatisticalSubscription) subscriptionReadOperationService.findByIdWithRetry(subId,
                        false);
                if (subscription == null) {
                    logger.warn("Was not able to add {} to the Active Node cache. Subscription with id {} associated to scanner {} does not exist.",
                            nodeFdn, subId, scanner.getFdn());
                    return;
                }
                subCountersMap.put(subId, subscription.getCounters());
                counterConflictCacheService.addNodesAndCounters(Collections.singleton(nodeFdn), subscription.getCounters(), subscriptionName);
            } catch (final DataAccessException | RetryServiceException e) {
                logger.warn("Was not able to add {} to the Active Node cache. ", nodeFdn);
                logger.info("Was not able to add {} to the Active Node cache. Exception stacktrace: ", nodeFdn, e);
            }
        }
        logger.debug("{} was added to the Active Node Cache.", nodeFdn);
    }

}
