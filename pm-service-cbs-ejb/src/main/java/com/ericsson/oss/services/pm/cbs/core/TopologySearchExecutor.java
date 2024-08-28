/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.cbs.core;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.CBS_AUDIT_EXECUTION;
import static com.ericsson.oss.services.pm.initiation.common.Constants.CBS_AUDIT_NO_CMOBECTS_FOUND;
import static com.ericsson.oss.services.pm.initiation.common.Constants.CBS_AUDIT_RETURNLIST_EMPTY;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.context.ContextService;
import com.ericsson.oss.itpf.sdk.core.annotation.EServiceRef;
import com.ericsson.oss.services.pm.cbs.exceptions.CBSException;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.topologySearchService.service.api.SearchExecutor;
import com.ericsson.oss.services.topologySearchService.service.api.dto.NetworkExplorerResponse;

/**
 * Executor for performing topology search queries
 */
@Stateless
public class TopologySearchExecutor {

    private static final String X_TOR_USER_ID = "X-Tor-UserID";
    private static final String ADMINISTRATOR = "administrator";

    @EServiceRef
    private SearchExecutor searchExecutor;
    @Inject
    private Logger logger;
    @Inject
    private ContextService context;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    /**
     * Execute the search query on topology search service. This method returns null in case
     * com.ericsson.oss.services.topologySearchService.service.api. SearchExecutor.search() api fails to execute because of incorrect syntax of
     * searchQuery, the correct user is not propagated through context or fails to resolve remote ejb call.
     *
     * @param searchQuery
     *         -search query string to be executed
     * @param requiredAttribute
     *         - Attribute Required for query
     *
     * @return return value is {@link NetworkExplorerResponse}
     * @throws CBSException
     *         - thrown if network explorer response is null
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public NetworkExplorerResponse executeQuery(final String searchQuery, final String requiredAttribute) {
        final String originalUser = context.getContextValue(X_TOR_USER_ID);
        try {
            context.setContextValue(X_TOR_USER_ID, ADMINISTRATOR);
            final NetworkExplorerResponse networkExplorerResponse = searchExecutor.search(searchQuery, ADMINISTRATOR, requiredAttribute);
            if (networkExplorerResponse == null) {
                systemRecorder.eventCoarse(CBS_AUDIT_EXECUTION, searchQuery, CBS_AUDIT_RETURNLIST_EMPTY);
                logger.error("Search Executor response is null for Search query {}", searchQuery);
                throw new CBSException("Search Executor response is null for Search query " + searchQuery);
            } else if (networkExplorerResponse.getErrorCode() > 0) {
                systemRecorder.eventCoarse(CBS_AUDIT_EXECUTION, searchQuery, CBS_AUDIT_NO_CMOBECTS_FOUND);
                logger.error("Search Executor return error :{} for search query :{}", searchQuery, networkExplorerResponse.getErrorCode());
                throw new CBSException(
                        "Search Executor response return error " + networkExplorerResponse.getErrorCode() + " for search query " + searchQuery);
            }

            logger.info("Search query :{}, result size :{}", searchQuery, networkExplorerResponse.getCompleteResultSetSize());
            context.setContextValue(X_TOR_USER_ID, originalUser);
            return networkExplorerResponse;
        } catch (Exception exception) {
            context.setContextValue(X_TOR_USER_ID, originalUser);
            logger.error("Exception {} during executeQuery: {}, requiredAttributes: {} ", exception.getMessage(), searchQuery, requiredAttribute);
            logger.info("Exception during executeQuery: {}, requiredAttributes: {} ", searchQuery, requiredAttribute, exception);
            throw new CBSException("Exception during executeQuery: " + searchQuery);
        }
    }

}
