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

package com.ericsson.oss.services.pm.initiation.pmjobs.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.pmjob.PmJob;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.pmjobs.helper.subscriptions.SubscriptionInfo;
import com.ericsson.oss.services.pm.initiation.pmjobs.helper.subscriptions.SubscriptionInfoProviderFactory;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * Implementation of PmJobHelper
 */
public class PmJobHelperImpl implements PmJobHelper {


    @Inject
    private Logger logger;
    @Inject
    private NodeService nodeService;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;
    @Inject
    private SubscriptionInfoProviderFactory subInfoProviderFactory;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    /**
     * Build all Active Pm Job Names.
     *
     * @return - set of strings.
     * @throws DataAccessException
     *         - if an exception from database is thrown
     * @throws RuntimeDataAccessException
     *         - if a runtime exception from databse is thrown
     */
    @Override
    public Set<String> buildAllActivePmJobNames() throws DataAccessException, RuntimeDataAccessException {
        return buildAllPmJobNameBySubscriptionAdminState(AdministrationState.ACTIVE);
    }

    /**
     * Build all Inactive Pm Job Names.
     *
     * @return - set of strings.
     * @throws DataAccessException
     *         - if an exception from database is thrown
     * @throws RuntimeDataAccessException
     *         - if a runtime exception from databse is thrown
     */
    @Override
    public Set<String> buildAllInactivePmJobNames() throws DataAccessException, RuntimeDataAccessException {
        return buildAllPmJobNameBySubscriptionAdminState(AdministrationState.INACTIVE);
    }

    @Override
    public Set<String> getAllSubscriptionIdsSupportedByPmJob() throws DataAccessException {
        if (!dpsAvailabilityStatus.isAvailable()) {
            logger.warn("Failed to execute dps query, Dps not available");
            return Collections.emptySet();
        }
        final Set<Long> subscriptionIds = new TreeSet<>();
        final ProcessType[] supportedSubscriptionTypes = subInfoProviderFactory.getSupportedProcessTypesForPmJob();
        for (final ProcessType processType : supportedSubscriptionTypes) {
            subscriptionIds.addAll(subscriptionReadOperationService.findAllIdsBySubscriptionType(processType.getSubscriptionType()));
        }
        final Set<String> ids = new TreeSet<>();
        for (final Long subscriptionId : subscriptionIds) {
            ids.add(String.valueOf(subscriptionId));
        }
        return ids;
    }

    @Override
    public Set<Node> getAllPmJobSupportedNodes() throws DataAccessException {
        final Set<Node> supportedNodes = new TreeSet<>(new NodeComparator());
        final ProcessType[] supportedSubscriptionTypes = subInfoProviderFactory.getSupportedProcessTypesForPmJob();
        for (final ProcessType processType : supportedSubscriptionTypes) {
            final SubscriptionInfo subsInfo = subInfoProviderFactory.getSubscriptionInfo(processType);
            final List<Node> availableNodes = subsInfo.getSupportedNodesForPmJobs();
            supportedNodes.addAll(availableNodes);
        }
        return supportedNodes;
    }

    @Override
    public List<Node> getAllPmJobSupportedNodesBySubscriptionType(final SubscriptionType subscriptionType) throws DataAccessException {
        final SubscriptionInfo subsInfo = subInfoProviderFactory.getSubscriptionInfo(ProcessType.getProcessType(subscriptionType.name()));
        return subsInfo.getSupportedNodesForPmJobs();
    }

    @Override
    public List<Node> getAllPmJobSupportedNodesBySubscriptionTypeAndPmFunction(final SubscriptionType subscriptionType, final boolean pmFunction)
            throws DataAccessException {
        return getFilteredNodesByPmFunction(getAllPmJobSupportedNodesBySubscriptionType(subscriptionType), pmFunction);
    }

    @Override
    public boolean isPmJobSupported(final String processType) {
        final ProcessType[] supportedTypes = subInfoProviderFactory.getSupportedProcessTypesForPmJob();
        for (final ProcessType supportedType : supportedTypes) {
            if (supportedType.getSubscriptionType().name().equals(processType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isCommonFileCollectionSupported(final String processType) {
        return subInfoProviderFactory.getSubscriptionInfo(ProcessType.fromString(processType)).isCommonFileCollectionSupported();
    }

    private Set<String> buildAllPmJobNameBySubscriptionAdminState(final AdministrationState administrationState) throws DataAccessException {
        if (!dpsAvailabilityStatus.isAvailable()) {
            logger.warn("Failed to execute dps query, Dps not available");
            return Collections.emptySet();
        }
        final Set<String> setOfPmJobNames = new TreeSet<>();
        final ProcessType[] supportedSubscriptionTypes = subInfoProviderFactory.getSupportedProcessTypesForPmJob();
        for (final ProcessType processType : supportedSubscriptionTypes) {
            final SubscriptionType[] subscriptionTypes = {processType.getSubscriptionType()};
            final AdministrationState[] administrationStates = {administrationState};
            final List<Long> subscriptionIds = subscriptionReadOperationService
                    .findAllIdsBySubscriptionTypeAndAdminState(subscriptionTypes, administrationStates);
            final SubscriptionInfo subsInfo = subInfoProviderFactory.getSubscriptionInfo(processType);
            final List<Node> availableNodes = subsInfo.getSupportedNodesForPmJobs();
            for (final Long subscriptionId : subscriptionIds) {
                for (final Node node : availableNodes) {
                    setOfPmJobNames.add(PmJob.createName(String.valueOf(subscriptionId), node.getFdn(), processType));
                }
            }
        }
        return setOfPmJobNames;
    }

    /**
     * Filter nodes by pmFunction.
     *
     * @param nodes
     *         input nodes
     * @param pmFunction
     *         pmFunction
     *
     * @return filtered nodes by given pmFunction.
     */
    private List<Node> getFilteredNodesByPmFunction(final List<Node> nodes, final boolean pmFunction) {
        final List<Node> result = new ArrayList<>();
        for (final Node node : nodes) {
            if (pmFunction == nodeService.isPmFunctionEnabled(node.getFdn())) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * This Node comparator class is to differentiate between nodes
     */
    class NodeComparator implements Comparator<Node> {
        @Override
        public int compare(final Node node1, final Node node2) {
            return node1.equals(node2) ? 0 : 1;
        }
    }

}
