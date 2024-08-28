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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ejb.Stateless;
import javax.inject.Inject;

import com.ericsson.oss.pmic.api.selector.annotation.Selector;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.dto.subscription.MtrSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.adjuster.impl.MtrSubscriptionDataAdjuster;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.ejb.ResourceSubscriptionNodeInitiation;
import com.ericsson.oss.services.pm.initiation.model.metadata.PmDataUtilLookupBean;

/**
 * Helper Class containing audit function for MTR Subscription
 */
@Selector(filter = "MtrSubscriptionHelper")
@Stateless
public class MtrSubscriptionHelper extends ResourceSubscriptionHelper {

    @Inject
    private PmDataUtilLookupBean pmDataUtilLookupBean;

    @Inject
    private ResourceSubscriptionNodeInitiation subscriptionNodeInitiation;

    private final Map<String, ScannerStatus> scanners = new HashMap<>();

    /**
     * Performs a filtering of target List removing all nodes coming from source
     * List based on Node Fdn only. Returns removed nodes. That's safer than using
     * removeAll because Node.equals() checks also fields which could change in time
     *
     * @param targetAttachedNodes
     *         - List to be filtered
     * @param sourceAttachedNodes
     *         - source list
     *
     * @return removed nodes
     */
    public static List<Node> filterByFdn(final List<Node> targetAttachedNodes, final List<Node> sourceAttachedNodes) {
        final Set<String> sourceFdns = MtrSubscriptionDataAdjuster.getNodeFdns(sourceAttachedNodes);
        final List<Node> removedNodes = new ArrayList<>(sourceAttachedNodes.size());
        final Iterator<Node> targetIterator = targetAttachedNodes.iterator();
        while (targetIterator.hasNext()) {
            final Node node = targetIterator.next();
            if (sourceFdns.contains(node.getFdn())) {
                removedNodes.add(node);
                targetIterator.remove();
            }
        }
        return removedNodes;
    }

    @Override
    protected List<Node> getNodesFromSubscription(final Subscription subscription) {
        return ((MtrSubscription) subscription).getAllNodes();
    }

    @Override
    protected Set<String> getNodeFdnsFromSubscription(final Subscription subscription) {
        final MtrSubscription mtrSubscription = (MtrSubscription) subscription;
        final Set<String> allNodesFdns = mtrSubscription.getNodesFdns();
        allNodesFdns.addAll(mtrSubscription.getAttachedNodesFdn());
        return allNodesFdns;
    }

    /**
     * Recalculates attached Nodes and updates subscription data accordingly.
     * Added/removed nodes will be included in erroneousNodes class for
     * activation/deactivation handling where subscription persistence will occur.
     */
    @Override
    protected void checkSubscriptionForExtraCriteria(final Subscription subscription) {

        final MtrSubscription mtrSubscription = (MtrSubscription) subscription;

        try {
            final List<Node> actualAttachedNodes = pmDataUtilLookupBean.mtrFetchAttachedNodesInReadTx(mtrSubscription.getNodesFdns(), true);
            final List<Node> attachedNodesFromSub = mtrSubscription.getAttachedNodes();
            final List<Node> attachedNodesToBeRemoved = new ArrayList<>(attachedNodesFromSub);
            final List<Node> attachedNodesToBeAdded = new ArrayList<>(actualAttachedNodes);
            final List<Node> associatedMscNodesList = new ArrayList<>();
            final List<Node> nonAssociatedMsc = getNonAssociatedMsc(actualAttachedNodes, mtrSubscription.getNodes(), associatedMscNodesList);
            filterByFdn(attachedNodesToBeAdded, attachedNodesFromSub);
            filterByFdn(attachedNodesToBeRemoved, actualAttachedNodes);

            if (!nonAssociatedMsc.isEmpty()) {
                mtrSubscription.setNodes(associatedMscNodesList);
                attachedNodesToBeRemoved.addAll(nonAssociatedMsc);
            }

            logger.info("ExtraCriteria Auditing for subscription {} - Found {} attached nodes to be added :: [{}] and {} attached nodes to be removed :: [{}]",
                    subscription.getName(), attachedNodesToBeAdded.size(), attachedNodesToBeAdded, attachedNodesToBeRemoved.size(), attachedNodesToBeRemoved);

            mtrSubscription.setAttachedNodes(actualAttachedNodes);
            final List<Scanner> scannerList = scannerService.findAllByNodeFdnAndSubscriptionIdAndScannerStatusInReadTx(
                    mtrSubscription.getNodesFdns(), Arrays.asList(mtrSubscription.getId()), ScannerStatus.ACTIVE);
            logger.debug("scannerList : {} ", scannerList);
            final List<Node> filteredNodesToBeAdded = filterAttachedNode(scannerList, attachedNodesToBeAdded, associatedMscNodesList);

            subscriptionNodeInitiation.activateOrDeactivateNodesOnActiveSubscription(mtrSubscription,
                    filteredNodesToBeAdded, attachedNodesToBeRemoved);

        } catch (final DataAccessException e) {
            logger.warn("Audit for MtrSubscription {} failed! Could not fetch attached Nodes from dps :: {}", subscription.getName(), e.getMessage());
            logger.debug("fetchAttachedNodes failed during auditing of MtrSubscription {} :: {}", subscription.getName(), e.getStackTrace());
        }
    }

    private List<Node> filterAttachedNode(final List<Scanner> scannerList, final List<Node> attachedNode, final List<Node> associatedMscNodesList) {

        final List<Node> nodesToBeAdded = new ArrayList<>();
        for (final Scanner scanner : scannerList) {
            for (final Node node : attachedNode) {
                if (scanner.getNodeFdn().equals(node.getConnectedMsc()) || node.getConnectedMscs().contains(scanner.getNodeFdn())) {
                    nodesToBeAdded.add(node);
                    continue;
                }

                if(!node.getMscPoolRefs().isEmpty()){
                    filterAttachedNodeForPool(associatedMscNodesList, nodesToBeAdded, scanner, node);
                }
            }
        }

        return nodesToBeAdded;

    }

    private void filterAttachedNodeForPool(final List<Node> associatedMscNodesList, final List<Node> nodesToBeAdded, final Scanner scanner, final Node node){
        for(Node subNode : associatedMscNodesList){
            if(node.getMscPoolRefs().stream().anyMatch(poolId -> subNode.getPoolRefs().contains(poolId)) && scanner.getNodeFdn().equals(subNode.getFdn())){
                nodesToBeAdded.add(node);
                break;
            }
        }
    }

    private List<Node> getNonAssociatedMsc(final List<Node> attachedNodes, final List<Node> nodesInSubscription,
                                           final List<Node> associatedMscNodesList) {
        final Set<String> connectedMscNodes = getConnectedMscNodesFdns(attachedNodes, nodesInSubscription);

        final List<Node> mscWithAssociationsRemoved = new ArrayList<>();

        associatedMscNodesList.addAll(nodesInSubscription.stream()
            .filter(node -> connectedMscNodes.contains(node.getFdn())).collect(Collectors.toList()));
        mscWithAssociationsRemoved.addAll(nodesInSubscription.stream()
            .filter(node -> !connectedMscNodes.contains(node.getFdn())).collect(Collectors.toList()));

        return mscWithAssociationsRemoved;
    }

    private Set<String> getConnectedMscNodesFdns(final List<Node> attachedNodes, final List<Node> nodesInSubscription) {
        final Set<String> connectedMscNodes = new HashSet<>();
        for (final Node node : attachedNodes) {
            if(!node.getConnectedMscs().isEmpty()) {
                connectedMscNodes.addAll(node.getConnectedMscs().stream().collect(Collectors.toList()));
            }
            else if(node.getConnectedMsc()!=null && !node.getConnectedMsc().isEmpty()){
                connectedMscNodes.add(node.getConnectedMsc());
            }

            if(!node.getMscPoolRefs().isEmpty()){
                updateConnectedMscNodesForPool(nodesInSubscription, node, connectedMscNodes);
            }
        }
        return connectedMscNodes;
    }

    private void updateConnectedMscNodesForPool(final List<Node> nodesInSubscription, final Node node, final Set<String> connectedMscNodes){
        for(Node subNode : nodesInSubscription){
            if(node.getMscPoolRefs().stream().anyMatch(poolId -> subNode.getPoolRefs().contains(poolId))){
                connectedMscNodes.add(subNode.getFdn());
            }
        }
    }

    @Override
    protected ErroneousNodes getNodesWithMissingAndDuplicateScanners(final List<Scanner> subscriptionScanners,
                                                                     final SubscriptionAuditorCriteria subscriptionAuditorCriteria,
                                                                     final Subscription subscription) {
        final ErroneousNodes erroneousNodes = new ErroneousNodes();
        final Map<String, Integer> nodeFdnToCountOfActualScanners = getCountOfActualScannersPerNodeFdn(
                subscriptionScanners, subscription, scanners);
        final List<Node> nodes = getNodesFromSubscription(subscription);

        for (final Node node : nodes) {
            if (nodeFdnToCountOfActualScanners.containsKey(node.getFdn())) {
                updateErrorNodesWhenAssociateScannersFound(node, subscription, nodeFdnToCountOfActualScanners, erroneousNodes, subscriptionAuditorCriteria);
            } else {
                updateErrorNodes(node, erroneousNodes, scanners, (MtrSubscription) subscription);
            }
        }

        return erroneousNodes;
    }

    private void updateErrorNodes(final Node node, final ErroneousNodes erroneousNodes, final Map<String, ScannerStatus> scanners, final MtrSubscription subscription) {
        if (!NetworkElementType.BSC.name().equals(node.getNeType()) || connectedMscScannerActive(node, scanners, subscription)) {
            erroneousNodes.addNodesWithMissingScanners(node);
        }
    }

    private boolean connectedMscScannerActive(final Node node, final Map<String, ScannerStatus> scanners, final MtrSubscription subscription) {
        boolean result = false;
        if (connectedMscScannerActiveForPool(node, scanners, subscription)) {
            return true;
        }

        if((!node.getConnectedMscs().isEmpty())) {
            if(node.getConnectedMscs().stream().anyMatch(mscNode -> ScannerStatus.ACTIVE == scanners.get(mscNode))){
                return true;
            }
        }
        else if(node.getConnectedMsc() != null){
            result = node.getConnectedMsc() != null && ScannerStatus.ACTIVE == scanners.get(node.getConnectedMsc());
        }

        return result;
    }

    private boolean connectedMscScannerActiveForPool(final Node node, final Map<String, ScannerStatus> scanners, final MtrSubscription subscription) {
        if(!node.getMscPoolRefs().isEmpty()) {
            for (Node subNode : subscription.getNodes()) {
                if (node.getMscPoolRefs().stream().anyMatch(poolId -> subNode.getPoolRefs().contains(poolId)) && ScannerStatus.ACTIVE == scanners.get(subNode.getFdn())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Return map of node FDNs and expected count of scanners per each nodeFdn.
     *
     * @param scanners
     */
    private Map<String, Integer> getCountOfActualScannersPerNodeFdn(final List<Scanner> subscriptionScanners, final Subscription subscription,
                                                                    final Map<String, ScannerStatus> scanners) {
        final Set<String> nodeFdns = getNodeFdnsFromSubscription(subscription);
        final Map<String, Integer> scannersPerNode = new HashMap<>();
        for (final Scanner scanner : subscriptionScanners) {
            updateScannersPerNode(scanner, nodeFdns, scannersPerNode);
            scanners.put(scanner.getNodeFdn(), scanner.getStatus());
        }
        return scannersPerNode;
    }
}
