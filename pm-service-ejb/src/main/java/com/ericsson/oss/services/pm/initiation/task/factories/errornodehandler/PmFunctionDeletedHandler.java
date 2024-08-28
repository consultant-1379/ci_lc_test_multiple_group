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

import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_ID;

import java.util.Map;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.collection.cache.PmFunctionOffErrorNodeCache;
import com.ericsson.oss.services.pm.initiation.ejb.CounterConflictServiceImpl;
import com.ericsson.oss.services.pm.initiation.task.qualifier.ErrorHandler;
import com.ericsson.oss.services.pm.scheduling.impl.DelayedTaskStatusValidator;

/**
 * Performs task status and admin state validation when a node that was causing a subscription to be in ERROR state is deleted.
 */
@ErrorHandler(processType = ErrorNodeCacheProcessType.PM_FUNCTION_DELETED)
@ApplicationScoped
public class PmFunctionDeletedHandler implements ScannerErrorHandler {

    @Inject
    private PmFunctionOffErrorNodeCache pmFunctionOffErrorNodeCache;

    @Inject
    private Logger logger;

    @Inject
    private DelayedTaskStatusValidator delayedTaskStatusValidator;

    @Inject
    private CounterConflictServiceImpl counterConflictCacheService;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean process(final ErrorNodeCacheProcessType processType, final Map<String, Object> attributes) {
        final String nodeFdn = (String) attributes.get(ErrorNodeCacheAttributes.FDN);
        counterConflictCacheService.removeNodeFromAllEntries(nodeFdn);
        final Map<String, Object> errorEntry = pmFunctionOffErrorNodeCache.getErrorEntry(nodeFdn);
        if (!errorEntry.isEmpty()) {
            logger.info("Removing error entry for {} because the node has been deleted", nodeFdn);
            pmFunctionOffErrorNodeCache.removeErrorEntry(nodeFdn);
            @SuppressWarnings("unchecked") final Set<Long> subIds = (Set<Long>) errorEntry.get(PMIC_ATT_SUBSCRIPTION_ID);
            for (final Long subId : subIds) {
                logger.debug("Starting timeout for validation of subscription {}", subId);
                delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(subId);
            }
        }
        return true;
    }
}
