
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

package com.ericsson.oss.services.pm.initiation.integration.subscription;


import static org.awaitility.Awaitility.await;

import static com.ericsson.oss.pmic.api.constants.ModelConstants.SubscriptionConstants.CONTINUOUS_CELLTRACE_SUBSCRIPTION_NRAN_NAME;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import org.awaitility.Duration;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.ContinuousCellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionOperationMessageSender;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionTestUtils;
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;
import com.ericsson.oss.services.pm.integration.test.steps.NetworkElementSteps;
import com.ericsson.oss.services.pm.integration.test.steps.SubscriptionEjbSteps;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

@RunWith(Arquillian.class)
public class ContinuousCelltraceSubscription5GSITTest extends InputBaseArquillian {


    @Inject
    Logger logger;

    @Inject
    private SubscriptionTestUtils testUtils;

    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;

    @Inject
    private SubscriptionOperationMessageSender sender;

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    @Inject
    private SubscriptionEjbSteps subscriptionEjbSteps;

    @Inject
    private NetworkElementSteps networkElementSteps;

    @Test
    @InSequence(3)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void cleanupAnyExistingNodesAndSubscription() {
        networkElementSteps.deleteAllNodes();
        subscriptionEjbSteps.deleteAllSubscription();
    }

    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void createNodeForTest() throws Exception {
        networkElementSteps.createNodes("RadioNode_5GS/SystemDefinedSubscriptionNodes.json");
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void should_call_cctrAudit_and_Should_Not_Null_CCTRSubscriptionObject()
            throws NumberFormatException, DataAccessException {
        subscriptionEjbSteps.invokeSysDefSubscriptionAudit();
        await().pollDelay(Duration.ONE_SECOND).pollInterval(Duration.ONE_SECOND).atMost(Duration.ONE_MINUTE).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws InterruptedException, DataAccessException {
                return testUtils.existsBySubscriptionName(CONTINUOUS_CELLTRACE_SUBSCRIPTION_NRAN_NAME);
            }
        });
        final ContinuousCellTraceSubscription continuousCellTraceSubscription = (ContinuousCellTraceSubscription) testUtils
                .findSubscriptionByExactName(CONTINUOUS_CELLTRACE_SUBSCRIPTION_NRAN_NAME, false);
        logger.info("{} Subscription name {} created Sucessfully", CONTINUOUS_CELLTRACE_SUBSCRIPTION_NRAN_NAME, continuousCellTraceSubscription.getName());
        Assert.assertNotNull("CCTR Object not created ", continuousCellTraceSubscription);
    }

    @Test
    @InSequence(6)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void should_call_RemoveNode_InactiveSubscription() throws InterruptedException, NumberFormatException, DataAccessException {
        final ContinuousCellTraceSubscription continuousCellTraceSubscription = (ContinuousCellTraceSubscription) testUtils
                .findSubscriptionByExactName(CONTINUOUS_CELLTRACE_SUBSCRIPTION_NRAN_NAME, false);
        logger.debug("Remove node Node3 from inactive subscription ");
        final List<String> nodesToBeRemoved = Collections.singletonList("NetworkElement=Node4");
        testUtils.removeNodesFromSubscriptions(nodesToBeRemoved, continuousCellTraceSubscription.getName());
    }

    @Test
    @InSequence(7)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void assert_should_call_RemoveNode_ActiveSubscription() throws InterruptedException {
        assertSubscriptionAdminState(AdministrationState.INACTIVE);
        nodeCreationHelperBean.deleteAllSubscriptionsAndNodes();
    }

    private void assertSubscriptionAdminState(final AdministrationState adminState) throws InterruptedException {
        waitUntil(adminState, new Duration(60, TimeUnit.SECONDS));
    }

}
