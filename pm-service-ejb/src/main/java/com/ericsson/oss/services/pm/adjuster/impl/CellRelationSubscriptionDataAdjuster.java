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
package com.ericsson.oss.services.pm.adjuster.impl;

import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.CellRelationSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.MoTypeInstanceInfo;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterQualifier;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.SystemPropertiesService;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEventType;

/**
 * CellRelationSubscriptionPfmDataAdjuster.
 */
@SubscriptionDataAdjusterQualifier(subscriptionClass = CellRelationSubscription.class)
@ApplicationScoped
public class CellRelationSubscriptionDataAdjuster extends StatisticalSubscriptionParentDataAdjuster<CellRelationSubscription> {

    private static final String MANAGED_ELEMENT = ",ManagedElement=1,RncFunction=1";
    private static final String RNC_NS = "RNC_NODE_MODEL";
    @Inject
    private Logger logger;
    @Inject
    private SystemPropertiesService systemPropertiesService;

    @Override
    public boolean shouldUpdateSubscriptionDataOnInitiationEvent(final List<Node> nodes, final CellRelationSubscription subscription,
                                                                 final InitiationEventType initiationEventType) {
        if ((initiationEventType != InitiationEventType.SUBSCRIPTION_ACTIVATION
                && InitiationEventType.ADD_NODES_TO_SUBSCRIPTION != initiationEventType) || nodes.isEmpty()) {
            return false;
        }
        final List<String> nodeFdnsWithInstances = new ArrayList<>();
        final List<String> counterGroups = fetchCounterGroupsInSubscription(subscription.getCounters());
        final List<MoTypeInstanceInfo> moTypeInstanceInfos = subscription.getMoTypeInstances();
        for (final Node node : nodes) {
            try {
                final List<String> cellsInNode = isCellsAvailableForNodeFdns(node, subscription.getCells());
                if (cellsInNode.isEmpty()) {
                    logger.info("Instances will not be updated for the node: {} in the subscription: {} ", node.getName(),
                            subscription.getName());
                    continue;
                }
                final List<MoTypeInstanceInfo> applicableCounterMoTypeInfo = fetchMoInstances(counterGroups, node, cellsInNode);
                if (applicableCounterMoTypeInfo.isEmpty()) {
                    logger.debug("MoInstances will not be updated for the Node : {} in the subscription: {} ", node, subscription.getName());
                } else {
                    logger.debug(" Updating the Instances for the Node: {} for the Subscription: {}", node.getName(), subscription.getId());
                    moTypeInstanceInfos.addAll(applicableCounterMoTypeInfo);
                    subscription.setMoTypeInstances(moTypeInstanceInfos);
                    nodeFdnsWithInstances.add(node.getName());
                }
            } catch (final Exception e) {
                logger.error("Error while udpating the MoTypeInstances for the Node : {}", node.getName());
            }
        }
        return !nodeFdnsWithInstances.isEmpty();
    }

    /**
     * To get the Cells for the Node
     *
     * @param node
     *         - Node Data
     * @param availableNodeWithCell
     *         - List of CellInfo from the Subscription
     *
     * @return List of Strings :Cells for the Node
     */
    protected List<String> isCellsAvailableForNodeFdns(final Node node, final List<CellInfo> availableNodeWithCell) {
        final List<String> cellSelected = new ArrayList<>();
        for (final CellInfo cell : availableNodeWithCell) {
            if (cell.getNodeName().equals(node.getName())) {
                cellSelected.add(cell.getUtranCellId());
                logger.debug("Cell for node is selected {} .", cell.getNodeName());
            }
        }
        return cellSelected;
    }

    /**
     * To convert the CounterInfos to List of CounterGroups
     *
     * @param selectedCountersInSub
     *         -List of CounterInfo
     *
     * @return List of Strings containing the CounterGroups selected
     */
    private List<String> fetchCounterGroupsInSubscription(final List<CounterInfo> selectedCountersInSub) {
        final List<String> counterGroupSelected = new ArrayList<>();
        for (final CounterInfo counterInfo : selectedCountersInSub) {
            final String counterGroupName = counterInfo.getMoClassType();
            if (!counterGroupSelected.contains(counterGroupName)) {
                logger.debug("Counter Group selected : {} ", counterGroupName);
                counterGroupSelected.add(counterGroupName);
            }
        }
        if (counterGroupSelected.isEmpty()) {
            throw new IllegalArgumentException(" CounterMos in selected Counters doesnot exists or Empty!");
        } else {
            return counterGroupSelected;
        }

    }

    /**
     * To fetch the MoInstance for the selected CounterGroups and cells
     *
     * @param counterGroups
     *         - List of CounterGroups
     * @param node
     *         - List of Node
     * @param cells
     *         - List of CellInfo
     *
     * @return List of MoTypeInstanceInfo containing formatted counter information
     */
    public List<MoTypeInstanceInfo> fetchMoInstances(final List<String> counterGroups, final Node node, final List<String> cells) {
        final List<MoTypeInstanceInfo> counterMoTypeInfos = new ArrayList<>();
        for (final String counterGroup : counterGroups) {
            try {
                final String nodeName = node.getOssPrefix();
                final String parentFDN = nodeName + MANAGED_ELEMENT;
                logger.debug("fetch the Instances of CounterGroup :{} in the ns : {} with the parent fdn as : {}", counterGroup, RNC_NS, parentFDN);
                final List<String> moTypeInstances = systemPropertiesService.findAllMoInstancesOnManagedElementWithNamespaceAndType(parentFDN, RNC_NS,
                        counterGroup, nodeName);
                final List<String> applicableInstances = getCellsFilter(moTypeInstances, cells, parentFDN);
                if (!applicableInstances.isEmpty()) {
                    counterMoTypeInfos.add(new MoTypeInstanceInfo(node.getName(), applicableInstances));
                }
            } catch (final DataAccessException e) {
                logger.info("Data Access Exception {}", e);
            }
        }
        return counterMoTypeInfos;
    }

    /**
     * Filter the CounterInstances against the Selected cells
     *
     * @param allConterTypeInstances
     *         - List of CounterTypeInstances
     * @param selectedCells
     *         - List of Cells Selected
     * @param parentFdn
     *         - ParentFdn
     *
     * @return List of Strings containing formatted CounterInstance
     */
    private List<String> getCellsFilter(final List<String> allConterTypeInstances, final List<String> selectedCells, final String parentFdn) {
        final List<String> applicableInstanceList = new ArrayList<>();
        for (final String counterTypeInstance : allConterTypeInstances) {
            for (final String cellSelected : selectedCells) {
                if (counterTypeInstance.contains(cellSelected)) {
                    final String moInstanceName = counterTypeInstance.split(parentFdn + ',')[1];
                    applicableInstanceList.add(moInstanceName);

                }
            }
        }
        return applicableInstanceList;
    }

}