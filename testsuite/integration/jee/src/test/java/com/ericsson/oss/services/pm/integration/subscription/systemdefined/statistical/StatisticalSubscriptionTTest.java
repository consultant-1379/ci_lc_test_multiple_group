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

package com.ericsson.oss.services.pm.integration.subscription.systemdefined.statistical;

import static org.awaitility.Awaitility.await;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import org.awaitility.Duration;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionTestUtils;
import com.ericsson.oss.services.pm.integration.test.steps.NetworkElementSteps;
import com.ericsson.oss.services.pm.integration.test.steps.SubscriptionEjbSteps;

/**
 * Test for verify System Defined Subscription behaviour on Audit.
 */
@RunWith(Arquillian.class)
public class StatisticalSubscriptionTTest extends InputBaseArquillian {

    @Inject
    private NetworkElementSteps networkElementSteps;

    @Inject
    private SubscriptionEjbSteps subscriptionEjbSteps;

    @Inject
    private SubscriptionTestUtils testUtils;

    @Test
    @InSequence(2)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void cleanupAnyExistingNodesAndSubscription() {
        networkElementSteps.deleteAllNodes();
        subscriptionEjbSteps.deleteAllSubscription();
    }

    @Test
    @InSequence(3)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void on_audit_stats_subscription_for_nodeType_should_be_created() throws Exception {
        networkElementSteps.createNodes("ERBS/SystemDefinedSubscriptionNodes.json");
        networkElementSteps.createNodes("RadioNode/SystemDefinedSubscriptionNodes.json");

        subscriptionEjbSteps.invokeSysDefSubscriptionAudit();

        final List<String> sytemDefinedsubsciptionNames = Arrays
                .asList("ERBS System Defined Statistical Subscription", "RadioNode System Defined Statistical Subscription");

        // wait and assert subscription creation
        await().pollDelay(Duration.FIVE_SECONDS).pollInterval(Duration.FIVE_SECONDS).atMost(Duration.ONE_MINUTE).until(new Callable<Boolean>() {
            boolean isFinished = true;

            @Override
            public Boolean call() throws DataAccessException {
                for (final String name : sytemDefinedsubsciptionNames) {
                    if (!testUtils.existsBySubscriptionName(name)) {
                        isFinished = false;
                    }
                }
                return isFinished;
            }
        });

        for (final String name : sytemDefinedsubsciptionNames) {
            final StatisticalSubscription subscription = (StatisticalSubscription) testUtils.findSubscriptionByExactName(name, true);
            Assert.assertEquals(2, subscription.getNodes().size());
        }

        networkElementSteps.changePmFunctionValue("NetworkElement=SYS_DEF_SUB_ERBS_NODE_01", false);
        networkElementSteps.changePmFunctionValue("NetworkElement=SYS_DEF_SUB_RADIONODE_NODE_01", false);

        subscriptionEjbSteps.invokeSysDefSubscriptionAudit();

        // wait and assert subscription subscription is updated and node is removed.
        await().pollDelay(Duration.FIVE_SECONDS).pollInterval(Duration.FIVE_SECONDS).atMost(Duration.ONE_MINUTE).until(new Callable<Boolean>() {
            boolean isFinished = true;

            @Override
            public Boolean call() throws DataAccessException {
                for (final String name : sytemDefinedsubsciptionNames) {
                    final StatisticalSubscription subscription = (StatisticalSubscription) testUtils.findSubscriptionByExactName(name, true);
                    if (subscription.getNodes().size() != 1) {
                        isFinished = false;
                    }
                }
                return isFinished;
            }
        });

        networkElementSteps.changePmFunctionValue("NetworkElement=SYS_DEF_SUB_ERBS_NODE_02", false);
        networkElementSteps.changePmFunctionValue("NetworkElement=SYS_DEF_SUB_RADIONODE_NODE_02", false);
        subscriptionEjbSteps.invokeSysDefSubscriptionAudit();

        // wait and assert subscription subscription is updated and node is removed.
        await().pollDelay(Duration.FIVE_SECONDS).pollInterval(Duration.FIVE_SECONDS).atMost(Duration.ONE_MINUTE).until(new Callable<Boolean>() {
            boolean isFinished = true;

            @Override
            public Boolean call() throws DataAccessException {
                for (final String name : sytemDefinedsubsciptionNames) {
                    final StatisticalSubscription subscription = (StatisticalSubscription) testUtils.findSubscriptionByExactName(name, true);
                    if (subscription.getNodes().size() != 0) {
                        isFinished = false;
                    }
                }
                return isFinished;
            }
        });

        subscriptionEjbSteps.invokeSysDefSubscriptionAudit();

        // wait and assert subscription subscription is deleted.
        await().pollDelay(Duration.FIVE_SECONDS).pollInterval(Duration.FIVE_SECONDS).atMost(Duration.ONE_MINUTE).until(new Callable<Boolean>() {
            boolean isFinished = true;

            @Override
            public Boolean call() throws DataAccessException {
                for (final String name : sytemDefinedsubsciptionNames) {
                    if (testUtils.existsBySubscriptionName(name)) {
                        isFinished = false;
                    }
                }
                return isFinished;
            }
        });
    }

    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void On_audit_stats_subscription_for_nodeType_should_be_deleted_if_all_has_pm_function_disabled() throws Exception {

    }
}
