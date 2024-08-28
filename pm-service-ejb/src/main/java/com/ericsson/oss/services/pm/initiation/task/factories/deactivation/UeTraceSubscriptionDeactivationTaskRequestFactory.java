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

package com.ericsson.oss.services.pm.initiation.task.factories.deactivation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.pmic.dto.subscription.UETraceSubscription;
import com.ericsson.oss.services.pm.initiation.pmjobs.sync.PmJobSynchronizer;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractNeConfigurationManagerTaskRequestCallbacks;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractSubscriptionTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.MediationTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.deactivation.qualifier.DeactivationTaskRequest;

/**
 * The Ue trace subscription deactivation task request factory.
 */
@DeactivationTaskRequest(subscriptionType = UETraceSubscription.class)
@ApplicationScoped
public class UeTraceSubscriptionDeactivationTaskRequestFactory extends AbstractSubscriptionTaskRequestFactory
        implements MediationTaskRequestFactory<UETraceSubscription> {

    @Inject
    private Logger logger;

    @Inject
    private PmJobSynchronizer pmJobSynchronizer;

    /**
     * This method is to build ue trace deactivation tasks for all nodes in a subscription.
     *
     * @param nodes
     *         : nodes having Node type of list
     * @param subscription
     *         : subscription is having UETraceType of subscription
     * @param trackResponse
     *         : trackResponse identify task response
     *
     * @return List
     */
    @Override
    public List<MediationTaskRequest> createMediationTaskRequests(final List<Node> nodes, final UETraceSubscription subscription,
                                                                  final boolean trackResponse) {
        final Map<String, String> nodesFdnsToDeactivate = new HashMap<>(nodes.size());
        final List<MediationTaskRequest> tasks =
                buildNeConfigurationMediationTaskRequests(nodes, new AbstractNeConfigurationManagerTaskRequestCallbacks() {
                    @Override
                    public List<MediationTaskRequest> createMediationTaskRequest(final Node node) {
                        final List<MediationTaskRequest> nodeTasks = new ArrayList<>();
                        final String nodeFdn = node.getFdn();
                        nodesFdnsToDeactivate.put(node.getFdn(), node.getNeType());
                        final MediationTaskRequest task = createDeactivationTask(node.getFdn(), subscription);
                        logger.debug("For Deactivation Adding task for task id: {} and fdn:{}", task.getJobId(), nodeFdn);
                        nodeTasks.add(task);
                        return nodeTasks;
                    }

                    @Override
                    public void manageNodeWithNeConfigurationManagerDisabled(final Node node) {
                        nodesFdnsToDeactivate.remove(node.getFdn());
                    }
                });

        final int ropPeriodInSec = subscription.getRop().getDurationInSeconds();
        pmJobSynchronizer.syncAllPmJobsInDPSForSubscription(subscription.getId(), ropPeriodInSec, nodesFdnsToDeactivate.keySet(),
                ProcessType.UETRACE);

        logger.info("For Deactivation total tasks count: {} and trackResponse:{}", tasks.size(), trackResponse);
        if (trackResponse) {
            addNodeFdnsToDeactivateToInitiationCache(subscription, nodesFdnsToDeactivate);
        }
        return tasks;
    }
}
