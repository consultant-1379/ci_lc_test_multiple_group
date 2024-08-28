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

package com.ericsson.oss.services.pm.initiation.task.factories.activation;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.services.pm.initiation.task.factories.AbstractNeConfigurationManagerTaskRequestCallbacks;
import org.slf4j.Logger;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.RttSubscription;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractSubscriptionTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.MediationTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.activation.qualifier.ActivationTaskRequest;

/**
 * The RTT subscription activation task request factory.
 */
@ActivationTaskRequest(subscriptionType = RttSubscription.class)
@ApplicationScoped
public class RttSubscriptionActivationTaskRequestFactory extends AbstractSubscriptionTaskRequestFactory
        implements MediationTaskRequestFactory<RttSubscription> {

    @Inject
    private Logger logger;


    /**
     * This method is to build ue trace activation tasks for all nodes in a subscription.
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
    public List<MediationTaskRequest> createMediationTaskRequests(final List<Node> nodes, final RttSubscription subscription,
                                                                  final boolean trackResponse) {
        final Map<String, String> nodesFdnsToActivate = new HashMap<>(nodes.size());
        final Map<String, Integer> nodeFdnExpectedNotificationMap = new HashMap<>(nodes.size());
        final List<MediationTaskRequest> tasks =
                buildNeConfigurationMediationTaskRequests(nodes, new AbstractNeConfigurationManagerTaskRequestCallbacks() {
                    @Override
                    public List<MediationTaskRequest> createMediationTaskRequest(final Node node) {
                        final List<MediationTaskRequest> nodeTasks = new ArrayList<>();
                        final String nodeFdn = node.getFdn();
                        nodesFdnsToActivate.put(node.getFdn(), node.getNeType());
                        nodeFdnExpectedNotificationMap.put(node.getFdn(), 1);
                        final MediationTaskRequest task = createActivationTask(nodeFdn, subscription);
                        logger.debug("Activating Subscription [{}] type [{}] for node [{}], task id [{}]", subscription.getName(), subscription.getType(),
                                node.getFdn(), task.getJobId());
                        nodeTasks.add(task);
                        return nodeTasks;
                    }

                    @Override
                    public void manageNodeWithNeConfigurationManagerDisabled(final Node node) {
                        nodesFdnsToActivate.remove(node.getFdn());
                    }
                });


        if (trackResponse && !nodesFdnsToActivate.isEmpty()) {
            addNodeFdnsToActivateToInitiationCache(subscription, nodesFdnsToActivate, nodeFdnExpectedNotificationMap);
        }
        return tasks;
    }

}
