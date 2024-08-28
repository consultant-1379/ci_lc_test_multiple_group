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

package com.ericsson.oss.services.pm.initiation.task.factories.deactivation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.subscription.Subscription;
import org.slf4j.Logger;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.dto.subscription.EbmSubscription;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractNeConfigurationManagerTaskRequestCallbacks;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractSubscriptionTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.MediationTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.deactivation.qualifier.DeactivationTaskRequest;

/**
 * The Ebm subscription deactivation task request factory.
 */
@DeactivationTaskRequest(subscriptionType = EbmSubscription.class)
@ApplicationScoped
public class EbmSubscriptionDeactivationTaskRequestFactory extends AbstractSubscriptionTaskRequestFactory
        implements MediationTaskRequestFactory<EbmSubscription> {

    @Inject
    private Logger logger;

    /**
     * This method is to build Ebm Deactivation tasks in a subscription.
     *
     * @param nodes
     *         - nodes to create deactivation tasks for
     * @param subscription
     *         - susbcription to create deactivation tasks for
     *
     * @return - returns a list of mediation task requests for deactivation
     */
    @Override
    public List<MediationTaskRequest> createMediationTaskRequests(final List<Node> nodes, final EbmSubscription subscription,
                                                                  final boolean trackResponse) {
        final List<Scanner> scanners = new ArrayList<>();
        try {
            scanners.addAll(scannerService.findAllBySubscriptionId(subscription.getId()));
        } catch (final DataAccessException e) {
            logger.error("Was not able to find all the scanners for the Subscription with Id {}.", subscription.getId());
            logger.info("Was not able to find all the scanners for the Subscription with Id {}. {}", subscription.getId(), e);
        }

        final Map<String, String> neFdnsAndTypes = new HashMap<>(nodes.size());
        final Map<String, String> nodesFdnsToDeactivate = new HashMap<>(nodes.size());
        final Set<String> neFdnsWithNeConfigurationManagerDisabled = new HashSet<>();

        final List<MediationTaskRequest> tasks =
                buildNeConfigurationMediationTaskRequests(nodes, new AbstractNeConfigurationManagerTaskRequestCallbacks() {
                    @Override
                    public List<MediationTaskRequest> createMediationTaskRequest(final Node node) {
                        neFdnsAndTypes.put(node.getFdn(), node.getNeType());
                        return super.createMediationTaskRequest(node);
                    }

                    @Override
                    public void manageNodeWithNeConfigurationManagerDisabled(final Node node) {
                        neFdnsWithNeConfigurationManagerDisabled.add(node.getFdn());
                    }

                    @Override
                    public void tasksPostProcessing(final List<MediationTaskRequest> tasks) {
                        for (final Scanner scanner : scanners) {
                            final boolean neFdnsContainsKey = neFdnsAndTypes.containsKey(scanner.getNodeFdn());
                            if (neFdnsContainsKey && scanner.getStatus() == ScannerStatus.ACTIVE) {
                                createDeactivationTaskIfRequired(scanner.getNodeFdn(), subscription, neFdnsAndTypes, tasks,
                                                                 neFdnsWithNeConfigurationManagerDisabled, nodesFdnsToDeactivate);
                            } else if (neFdnsContainsKey) {
                                // Scanners should be cleaned to disassociated from subscription.
                                updateSubscriptionIdToZeroAndSetStatusToUnknown(scanner);
                            }
                        }
                    }
                });

        if (trackResponse) {
            addNodeFdnsToDeactivateToInitiationCache(subscription, nodesFdnsToDeactivate);
        }
        return tasks;
    }

    private void createDeactivationTaskIfRequired(final String nodeFdn, final Subscription subscription, final Map<String, String> neFdnsAndTypes,
                                                  final List<MediationTaskRequest> tasks, final Set<String> neFdnsWithNeConfigurationManagerDisabled,
                                                  final Map<String, String> nodesFdnsToDeactivate) {
        if (neFdnsWithNeConfigurationManagerDisabled.contains(nodeFdn)) {
            return;
        }
        tasks.add(createDeactivationTask(nodeFdn, subscription));
        nodesFdnsToDeactivate.put(nodeFdn, neFdnsAndTypes.get(nodeFdn));
    }
}
