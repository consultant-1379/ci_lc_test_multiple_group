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

package com.ericsson.oss.services.pm.initiation.task.factories.deactivation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.RpmoSubscription;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractNeConfigurationManagerTaskRequestCallbacks;
import com.ericsson.oss.services.pm.initiation.task.factories.AbstractSubscriptionTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.MediationTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.task.factories.deactivation.qualifier.DeactivationTaskRequest;

/**
 * The RPMO subscription deactivation task request factory.
 */
@DeactivationTaskRequest(subscriptionType = RpmoSubscription.class)
@ApplicationScoped
public class RpmoSubscriptionDeactivationTaskRequestFactory extends AbstractSubscriptionTaskRequestFactory
        implements MediationTaskRequestFactory<RpmoSubscription> {
    @Inject
    private Logger logger;

    @Override
    public List<MediationTaskRequest> createMediationTaskRequests(final List<Node> nodes, final RpmoSubscription subscription,
                                                                  final boolean trackResponse) {
        logger.debug("Deactivating RPMO Subscription {}", subscription.getName());

        final Map<String, String> nodesFdnsToDeactivate = new HashMap<>(nodes.size());
        // For each node in the list, create a deactivation task or one or more resumption tasks for that node,
        // add it/them to the task list
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

        logger.info("For Deactivation total tasks count: {} and trackResponse:{}", tasks.size(), trackResponse);
        if (trackResponse) {
            addNodeFdnsToDeactivateToInitiationCache(subscription, nodesFdnsToDeactivate);
        }

        if (tasks.isEmpty()) {
            return Collections.emptyList();
        } else {
            return tasks;
        }
    }


}
