/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.integration.subscription;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.cache.Cache;
import javax.ejb.Timer;
import javax.enterprise.event.Event;
import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache;
import com.ericsson.oss.itpf.sdk.config.provider.PropertyScope;
import com.ericsson.oss.itpf.sdk.config.provider.ProvidedProperty;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionTaskCacheWrapper;
import com.ericsson.oss.services.pm.collection.schedulers.UeTraceFileCollectionTaskManager;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;
import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionOperationMessageSender;
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;
import com.ericsson.oss.services.pm.initiation.node.data.NodeData;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionDataReader;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionInfo;
import com.ericsson.oss.services.pm.initiation.timer.DummyTimer;
import com.ericsson.oss.services.pm.test.requests.PmServiceRequestsTypes;
import com.ericsson.oss.services.pm.test.requests.SubscriptionOperationRequest;

/**
 * Integration test for the UeTrace file collection.
 * UeTrace can be enabled by either of the following two,
 * 1. PIB parameter set to true
 * 2. active uetrace subscription
 */
@RunWith(Arquillian.class)
public class UeTraceFileCollectionManagerITTest extends InputBaseArquillian {

    private static final String SUBSCRIPTION_DATAFILE = UeTraceFileCollectionManagerITTest.class.getSimpleName() + "_Subscription";

    private final Logger logger = LoggerFactory.getLogger(UeTraceFileCollectionManagerITTest.class);

    @Inject
    SubscriptionOperationMessageSender sender;

    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;

    @Inject
    private UeTraceFileCollectionTaskManager ueFileCollectionTaskManager;

    @Inject
    private FileCollectionTaskCacheWrapper fileCollectionTaskCache;

    @Inject
    private Event<ProvidedProperty> providedPropertyEvent;

    @Inject
    @NamedCache("PMICFileCollectionTaskCache")
    private Cache<String, FileCollectionTaskWrapper> cache;

    private List<NodeData> nodesForTest;

    private void cleanUpAndCreateNodes() throws InterruptedException {
        logger.debug("Adding nodes to the system");
        deleteAllNodes();
        nodesForTest = nodeCreationHelperBean.createNodes(getClass().getSimpleName(), 0);

    }

    private void deleteAllNodes() {
        logger.debug("Removing nodes from the system");
        nodeCreationHelperBean.deleteAllNodes();
    }

    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void ueTraceDisabledWithoutNode_NoFileCollectionRequest()
            throws InterruptedException {
        cleanUpSubscription();
        deleteAllNodes();
        changeUeTracePibProperty(false);
        final Set<FileCollectionTaskWrapper> fileCollectionsTasks = executeUeTraceManagerTimeoutMethod();
        final Set<FileCollectionTaskRequest> ueTraceFileCollectionTaskRequests = extractUeTraceTaskRequests(fileCollectionsTasks);
        logger.debug("Ue Trace File Collection tasks: {}", ueTraceFileCollectionTaskRequests);
        Assert.assertTrue(ueTraceFileCollectionTaskRequests.isEmpty());
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void ueTraceDisabledWithNodes_NoFileCollectionRequest()
            throws InterruptedException {
        cleanUpAndCreateNodes();
        cleanUpSubscription();
        changeUeTracePibProperty(false);
        final Set<FileCollectionTaskWrapper> fileCollectionsTasks = executeUeTraceManagerTimeoutMethod();
        final Set<FileCollectionTaskRequest> ueTraceFileCollectionTaskRequests = extractUeTraceTaskRequests(fileCollectionsTasks);
        logger.debug("Ue Trace File Collection tasks: {}", ueTraceFileCollectionTaskRequests);
        Assert.assertTrue(ueTraceFileCollectionTaskRequests.isEmpty());
    }

    @Test
    @InSequence(6)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void createUeTraceSubscription() throws InterruptedException {
        testResponseHolder.clear();
        final CountDownLatch cl1 = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(cl1);
        final SubscriptionOperationRequest createMessage =
                new SubscriptionOperationRequest(PmServiceRequestsTypes.CREATE_UE, null, null, SUBSCRIPTION_DATAFILE);
        sender.sendTestOperationMessageToPmService(createMessage);

        final SubscriptionDataReader sdr = new SubscriptionDataReader(createMessage.getSubscriptionDataFile());
        final List<SubscriptionInfo> subscriptionList = sdr.getUeTraceSubscriptionList();

        cl1.await(20, TimeUnit.SECONDS);

        logger.info("subscriptionList.size(): {}", subscriptionList.size());
        logger.info("testResponseHolder.getAllSubs().size(): {}", testResponseHolder.getAllSubs().size());

        final Map<String, Map<String, Object>> createdSubscriptions = testResponseHolder.getAllSubs();
        Assert.assertEquals(subscriptionList.size(), testResponseHolder.getAllSubs().size());

        for (final SubscriptionInfo subInfo : subscriptionList) {
            logger.debug("Created ue trace subscription with name : {}, adminState : {}, taskStatus : {}", subInfo.getName(),
                    subInfo.getAdminState(), subInfo.getJobStatus());
            final Map<String, Object> subscriptionAttr = createdSubscriptions.get(subInfo.getName());
            Assert.assertNotNull(subscriptionAttr);
            Assert.assertEquals(null, subscriptionAttr.get("Exception"));
            Assert.assertEquals(null, subscriptionAttr.get("ExceptionMessage"));
        }
    }

    @Test
    @InSequence(7)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void activeSubscriptionWithoutNode_NoFileCollectionRequestSent() throws InterruptedException {
        deleteAllNodes();
        changeUeTracePibProperty(false);
        final Set<FileCollectionTaskWrapper> fileCollectionsTasks = executeUeTraceManagerTimeoutMethod();
        final Set<FileCollectionTaskRequest> ueTraceFileCollectionTaskRequests = extractUeTraceTaskRequests(fileCollectionsTasks);
        logger.debug("Ue Trace File Collection tasks: {}", ueTraceFileCollectionTaskRequests);
        Assert.assertTrue(ueTraceFileCollectionTaskRequests.isEmpty());
    }

    @Test
    @InSequence(9)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void noSubscriptionAndPibEnabledWithoutNode_NoFileCollectionRequest() throws InterruptedException {
        deleteAllNodes();
        cleanUpSubscription();
        changeUeTracePibProperty(true);
        final Set<FileCollectionTaskWrapper> fileCollectionsTasks = executeUeTraceManagerTimeoutMethod();
        final Set<FileCollectionTaskRequest> ueTraceFileCollectionTaskRequests = extractUeTraceTaskRequests(fileCollectionsTasks);
        logger.debug("Ue Trace File Collection tasks: {}", ueTraceFileCollectionTaskRequests);
        Assert.assertTrue(ueTraceFileCollectionTaskRequests.isEmpty());
    }

    @Test
    @InSequence(11)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testTimerCanBeCreatedForUeTraceTaskManager() throws ExecutionException, InterruptedException {
        logger.debug("Creating Timer for UE Trace");
        final Future<Boolean> timerCreated = ueFileCollectionTaskManager.startUeTraceFileCollectionTaskManagement(RopPeriod.FIFTEEN_MIN);
        timerCreated.get();
        final List<Integer> timerInfos = ueFileCollectionTaskManager.getTimerInfos();
        logger.debug("All ue trace task manager timer intervals {}", timerInfos);
        Assert.assertTrue("Expected timer of interval 15 minutes to be created",
                timerInfos.contains((int) RopPeriod.FIFTEEN_MIN.getDurationInSeconds()));
    }

    private void cleanUpSubscription() throws InterruptedException {
        final SubscriptionOperationRequest deleteMessage =
                new SubscriptionOperationRequest(PmServiceRequestsTypes.DELETE_UE, null, null, SUBSCRIPTION_DATAFILE);

        testResponseHolder.clear();
        final CountDownLatch cl2 = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(cl2);

        sender.sendTestOperationMessageToPmService(deleteMessage);
        cl2.await(120, TimeUnit.SECONDS);
    }

    /**
     * @param fileCollectionsTasks
     *
     * @return
     */
    private Set<FileCollectionTaskRequest> extractUeTraceTaskRequests(final Set<FileCollectionTaskWrapper> fileCollectionsTasks) {
        final Set<FileCollectionTaskRequest> ueTraceRequests = new HashSet<>();
        for (final FileCollectionTaskWrapper fileCollectionTaskWrapper : fileCollectionsTasks) {
            final FileCollectionTaskRequest fileCollectionTaskRequest = fileCollectionTaskWrapper.getFileCollectionTaskRequest();
            if (fileCollectionTaskRequest.getSubscriptionType().equals("UETRACE")) {
                ueTraceRequests.add(fileCollectionTaskRequest);
            }
        }
        return ueTraceRequests;
    }

    private void changeUeTracePibProperty(final boolean enableUeTrace) {
        logger.debug("Setting UE Trace enabled to {}", enableUeTrace);
        final ProvidedProperty prop =
                new ProvidedProperty("ueTraceCollectionEnabled", PropertyScope.GLOBAL, String.valueOf(enableUeTrace), Boolean.class);
        providedPropertyEvent.fire(prop);
    }

    private Set<FileCollectionTaskWrapper> executeUeTraceManagerTimeoutMethod() {
        logger.debug("Clear the file collection task cache first");
        cache.clear();
        logger.debug("Explicitly calling ue trace task manager to populate file collection task cache");
        final Timer timer = new DummyTimer(900);
        ueFileCollectionTaskManager.onTimeout(timer);
        return fileCollectionTaskCache.getAllTasks();
    }
}
