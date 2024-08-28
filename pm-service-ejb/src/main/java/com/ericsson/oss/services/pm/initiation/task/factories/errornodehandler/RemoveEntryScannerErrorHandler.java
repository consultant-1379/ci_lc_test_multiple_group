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
package com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler;

import java.util.Map;
import java.util.regex.Pattern;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.collection.cache.PmFunctionOffErrorNodeCache;
import com.ericsson.oss.services.pm.initiation.task.qualifier.ErrorHandler;

/**
 * Handles the scenario where a scanner has become available on a node that had no available scanners when a subscription was activated.
 * If the scanner is now inactive and not assigned to a subscription and the node does not already have a scanner for this subscription,
 * the scanner will be activated and assigned to this subscription
 */
@ErrorHandler(processType = ErrorNodeCacheProcessType.REMOVE)
@ApplicationScoped
public class RemoveEntryScannerErrorHandler implements ScannerErrorHandler {

    private static final Pattern NUMERIC = Pattern.compile("[0-9]+");

    @Inject
    private Logger logger;

    @Inject
    private PmFunctionOffErrorNodeCache pmFunctionOffErrorNodeCache;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean process(final ErrorNodeCacheProcessType processType, final Map<String, Object> attributes) {
        final String nodeFdn = (String) attributes.get(ErrorNodeCacheAttributes.FDN);
        final String subscriptionIdStr = getSubscriptionId(attributes);
        if (subscriptionIdStr != null) {
            final long subscriptionIdLong = Long.parseLong(subscriptionIdStr);
            logger.info("Removing error entry for {} because the node scanners has been processed with subscriptionId {}", nodeFdn, subscriptionIdLong);
            return pmFunctionOffErrorNodeCache.removeErrorEntry(nodeFdn, subscriptionIdLong);
        } else {
            logger.info("Removing error entry for {} because the node scanners has been processed", nodeFdn);
        }
        return pmFunctionOffErrorNodeCache.removeErrorEntry(nodeFdn);
    }

    private String getSubscriptionId(final Map<String, Object> attributes) {
        final String subscriptionIdStr = (String) attributes.get(ErrorNodeCacheAttributes.SUBSCRIPTION_ID);
        if (subscriptionIdStr == null || !NUMERIC.matcher(subscriptionIdStr).matches()) {
            return null;
        }
        return subscriptionIdStr;
    }
}
