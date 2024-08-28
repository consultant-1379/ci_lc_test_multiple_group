
package com.ericsson.oss.services.pm.integration.subscription.res;

import java.util.List;

import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.oss.pmic.dto.subscription.ResSubscription;
import com.ericsson.oss.services.pm.initiation.node.data.NodeData;
import com.ericsson.oss.services.pm.integration.subscription.BaseSubscriptionRest;
import com.ericsson.oss.services.pm.integration.test.steps.SubscriptionEjbSteps;

/**
 * Created by egridav on 2/21/18.
 */
@RunWith(Arquillian.class)
public class ResSubscriptionITTest extends BaseSubscriptionRest {
    private static final String INPUT_NODE_JSON_FILE = "ResSubscription/ResSubscriptionNodes.json";
    private static final String BASIC_RES_SUBSCRIPTION_JSON_FILE = "ResSubscription/ResSubscription.json";

    private static List<NodeData> nodeDataList;

    @Inject
    private SubscriptionEjbSteps subscriptionEjbSteps;

    /**
     * Sets the up. Converts Node json file to NodeData Array
     *
     * @throws Exception
     *             the exception
     */
    @Before
    public void setUp() throws Exception {
        nodeDataList = setUp(INPUT_NODE_JSON_FILE);
    }

    /**
     * Setup Test Environment, Add Nodes for Test.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(2)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void setupEnv() throws Exception {
        for (final NodeData node : nodeDataList) {
            nodeCreationHelperBeanEJB.createNode(node);
        }
    }

    /**
     * Test res subscription with counters.
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(3)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testResSubscriptionCreate() throws Exception {
        testSubscription(BASIC_RES_SUBSCRIPTION_JSON_FILE, ResSubscription.class, com.ericsson.oss.pmic.dto.subscription.ResSubscription.class,
                false);
    }

    /**
     * Cleanup Test Environment by Deleting added Nodes
     *
     * @throws Exception
     *             the exception
     */
    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void cleanupEnv() throws Exception {
        subscriptionEjbSteps.deleteAllSubscription();
        for (final NodeData node : nodeDataList) {
            nodeCreationHelperBeanEJB.deleteNode(node);
        }
    }
}
