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

package com.ericsson.oss.services.pm.initiation.model.metadata.mtr;

import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.ACTIVATING;
import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.ACTIVE;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.MTR_SUBSCRIPTION_ATTRIBUTES;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.Collections;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.modeling.modelservice.typed.TypedModelAccess;
import com.ericsson.oss.pmic.api.modelservice.PmCapabilitiesLookupLocal;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.enums.MtrAccessType;
import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.utils.PmFunctionUtil;
import com.ericsson.oss.pmic.dto.subscription.MtrSubscription;
import com.ericsson.oss.pmic.dao.SubscriptionDao;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;

/**
 * This class is responsible to fetch all attributes related to Mtr subscription
 */
public class PmMtrLookUp {

    static final String ATTACHED_NODE_TYPES = "multipleNeTypesList";

    @Inject
    private Logger logger;

    @Inject
    private NodeService nodeService;

    @Inject
    private TypedModelAccess typedModelAccess;

    @Inject
    private PmFunctionUtil pmFunctionUtil;

    @Inject
    private PmCapabilitiesLookupLocal pmCapabilitiesLookupLocal;

    @Inject
    private SubscriptionDao subscriptionDao;

    /**
     * Gets MTR AccessTypes to be shown in the UI.
     *
     * @return Mtr Access Types
     */
    public Map<String, MtrAccessType[]> getMtrAccessTypes() {
        final Map<String, MtrAccessType[]> mtrAccessTypeMap = new HashMap<>();
        final MtrAccessType[] mtrAccessTypes = MtrAccessType.values();
        logger.debug("Access Types of mtr are : {} ", MtrAccessType.values());
        mtrAccessTypeMap.put("mtrAccessTypes", mtrAccessTypes);
        return mtrAccessTypeMap;
    }

    /**
     * Fetch list of attached nodes with PmFunction enabled
     *
     * @param nodeFdns
     *         the MTR nodeFdns
     * @param isActivationEvent
     *         isActivationEvent flag
     *
     * @return the list of attached nodes or empty list
     * @throws DataAccessException
     *         - if any other data access exception is thrown.
     * @throws IllegalArgumentException
     *         - if node Fdn is not a valid network element fdn.
     */
    @LogProfiler(name = "Fetch attached nodes")
    public List<Node> fetchAttachedNodes(final Set<String> nodeFdns,
                                         final boolean isActivationEvent)
            throws DataAccessException {
        final List<String> mtrSubscriptionAttachedNodeTypes = (List<String>) pmCapabilitiesLookupLocal
                .getDefaultCapabilityValue(MTR_SUBSCRIPTION_ATTRIBUTES, ATTACHED_NODE_TYPES);

        if (isActivationEvent) {
            pmFunctionUtil.filterNodeFdnsByPmFunctionOn(nodeFdns);
        }
        final List<Node> fetchedNodes = nodeService.fetchGranAttachedNodes(mtrSubscriptionAttachedNodeTypes, nodeFdns);
        pmFunctionUtil.filterNodesByPmFunctionOn(fetchedNodes);
        logger.debug("Found {} Attached Nodes", fetchedNodes.size());
        return fetchedNodes;
    }

    /**
     * Fetch set of used recording references
     *
     * @param subscription
     *         the MTR subscription
     *
     * @return the set of used recording references or empty set
     * @throws DataAccessException
     *         - if any other data access exception is thrown.
     */
    public Set<Integer> getUsedRecordingReferences(final MtrSubscription subscription) throws DataAccessException {
        final Set<Integer> usedRrNumber = new HashSet<>();
        final Integer recordingReference = subscription.getRecordingReference();
        final AdministrationState[] adminState = {ACTIVE, ACTIVATING};
        final SubscriptionType[] subscriptionType = {SubscriptionType.MTR};
        final List<Node> attachedNodes = fetchAttachedNodes(subscription.getNodesFdns(),true);
        try {
            final List<Subscription> mtrSubscriptions = subscriptionDao.findAllBySubscriptionTypeAndAdministrationState
                    (subscriptionType, adminState, true);
            for (final Subscription mtrSubscription : mtrSubscriptions) {
                if ((attachedNodes.stream().anyMatch(((MtrSubscription) mtrSubscription).getAttachedNodes()::contains))) {
                    usedRrNumber.add(((MtrSubscription) mtrSubscription).getRecordingReference());
                }
            }
        } catch (final DataAccessException ex) {
            logger.error("DataAccessException caught {}", ex.getMessage());
        }
        if (usedRrNumber.contains(recordingReference)) {
            logger.debug("Used RR Number list is {}",usedRrNumber);
            return usedRrNumber;
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * Fetch list of non attached nodes.
     *
     * @param nodeFdns
     *         the MTR nodeFdns
     * @param isActivationEvent
     *         isActivationEvent flag
     *
     * @return the list of attached nodes or empty list
     * @throws DataAccessException
     *         - if any other data access exception is thrown.
     * @throws IllegalArgumentException
     *         - if node Fdn is not a valid network element fdn.
     */
    @LogProfiler(name = "Fetch Non Associated nodes")
    public List<String> getNonAssociatedNodes(final List<Node> subNodes, final Set<String> nodeFdns, final boolean isActivationEvent)
            throws DataAccessException {
        final List<Node> attachedNodes = fetchAttachedNodes(nodeFdns, isActivationEvent);

        for (final Node node : attachedNodes) {
            if(!node.getConnectedMscs().isEmpty()) {
                for (String mscNode : node.getConnectedMscs()) {
                    nodeFdns.remove(mscNode);
                }
            }
            else {
                nodeFdns.remove(node.getConnectedMsc());
            }

            if(!node.getMscPoolRefs().isEmpty()){
                updateNodeFdnsForPool(node,subNodes,nodeFdns);
            }
        }
        logger.debug("List of non attached nodes {}", nodeFdns);
        return new ArrayList<>(nodeFdns);
    }

    private void updateNodeFdnsForPool(final Node attachedNode, final List<Node> subscriptionNodes, final Set<String> nodeFdns){
        for(Node subNode : subscriptionNodes) {
            if (attachedNode.getMscPoolRefs().stream().anyMatch(poolId -> subNode.getPoolRefs().contains(poolId))) {
                nodeFdns.remove(subNode.getFdn());
            }
        }
    }

}