/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2014
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.integration;

import static org.junit.Assert.assertEquals;

import static com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionAttributes.ADMIN_STATE;
import static com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionAttributes.ID;
import static com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionAttributes.PERSISTENCE_TIME;
import static com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionAttributes.START_TIME;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.ejb.Stateless;
import javax.inject.Inject;

import org.junit.Assert;
import org.slf4j.Logger;

import com.ericsson.oss.pmic.dao.SubscriptionDao;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionDataReader;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionInfo;
import com.ericsson.oss.services.pm.listeners.IntegrationRequestResponseHolder;
import com.ericsson.oss.services.pm.test.requests.PmServiceRequestsTypes;
import com.ericsson.oss.services.pm.test.requests.SubscriptionOperationRequest;

@Stateless
public class SubscriptionTestUtils {

    @Inject
    Logger logger;

    @Inject
    IntegrationRequestResponseHolder testResponseHolder;

    @Inject
    SubscriptionOperationMessageSender sender;

    @Inject
    private SubscriptionDao subscriptionDao;

    public Map<String, Map<String, Object>> findSubscriptions(final String dataFile) throws InterruptedException {
        logger.debug("Finding subscriptions for the test Data File {}.", dataFile);
        testResponseHolder.clear();
        final SubscriptionOperationRequest createMessage = new SubscriptionOperationRequest(PmServiceRequestsTypes.LIST, null, null, dataFile);

        final CountDownLatch countDown = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(countDown);

        sender.sendTestOperationMessageToPmService(createMessage);

        countDown.await(15, TimeUnit.SECONDS);
        final Map<String, Map<String, Object>> response = new HashMap<>();
        response.putAll(testResponseHolder.getAllSubs());
        logger.debug("Found subscriptions {}.", response);
        return response;
    }

    public Subscription findSubscriptionByName(final String subscriptionName) throws DataAccessException {
        return subscriptionDao.findOneByExactName(subscriptionName, true);
    }

    public boolean existsBySubscriptionName(final String subscriptionName) throws DataAccessException {
        return subscriptionDao.existsBySubscriptionName(subscriptionName);
    }

    public Subscription findSubscriptionByExactName(final String subscriptionName, final Boolean loadAssociations) throws DataAccessException {
        return subscriptionDao.findOneByExactName(subscriptionName, loadAssociations);
    }

    public Map<String, Object> activate(final String name, final Map<String, Object> subscriptionAttr) throws InterruptedException {
        testResponseHolder.clear();
        final String id = (String) subscriptionAttr.get(ID.name());
        final Date persistenceTime = (Date) subscriptionAttr.get(PERSISTENCE_TIME.name());
        logger.debug("Activating subscritpion {} with id {} and persistenceTime {}. Subscription details: {}.", name, id, persistenceTime,
                subscriptionAttr);

        final SubscriptionOperationRequest activateMessage = new SubscriptionOperationRequest(PmServiceRequestsTypes.ACTIVATE, null, subscriptionAttr,
                getClass().getSimpleName());

        final CountDownLatch countDown = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(countDown);

        sender.sendTestOperationMessageToPmService(activateMessage);

        countDown.await(15, TimeUnit.SECONDS);
        final Map<String, Object> response = testResponseHolder.getAllSubs().get(name);
        logger.debug("Response from subscription {} activation: {}.", name, response);
        return response;
    }

    public void assertActivationSucceed(String id, final String name, final Map<String, Object> initiationResult) {
        logger.info("Assert id {} for {}", id, initiationResult);
        if(null == id || null != initiationResult.get(ID.name())){
            return;
        } else {
            assertEquals(id, initiationResult.get(ID.name()));
        }
        logger.info("Id passed");
        final Date startTime = (Date) initiationResult.get(START_TIME.name());
        if (startTime == null) {
            assertAdministrationState(id, name, initiationResult, AdministrationState.ACTIVATING);
        } else {
            assertAdministrationState(id, name, initiationResult, AdministrationState.SCHEDULED);
        }
    }

    public Map<String, Object> deactivate(final String name, final Map<String, Object> subscriptionAttr) throws InterruptedException {

        final String id = (String) subscriptionAttr.get(ID.name());
        final Date persistenceTime = (Date) subscriptionAttr.get(PERSISTENCE_TIME.name());
        logger.debug("Deactivating subscription {} with id {} and persistenceTime {}. Subscription details: {}.", name, id, persistenceTime,
                subscriptionAttr);
        final SubscriptionOperationRequest deactivateMessage = new SubscriptionOperationRequest(PmServiceRequestsTypes.DEACTIVATE, null,
                subscriptionAttr, getClass().getSimpleName());

        final CountDownLatch countDown = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(countDown);

        sender.sendTestOperationMessageToPmService(deactivateMessage);

        countDown.await(30, TimeUnit.SECONDS);
        final Map<String, Object> response = testResponseHolder.getAllSubs().get(name);
        logger.debug("Response from subscritpion {} deactivation: {}.", name, response);
        return response;
    }

    public void assertAdministrationState(final String id, final String name, final Map<String, Object> initiationResult,
                                          final AdministrationState state) {
        assertEquals(id, initiationResult.get(ID.name()));
        assertEquals(String.format("Subscription %s should be %s. result: %s.", name, state.name(), initiationResult), state.name(),
                initiationResult.get(ADMIN_STATE.name()));
    }

    public boolean checkAdministrationState(final String id, final String name, final Map<String, Object> initiationResult,
                                            final AdministrationState state) {
        return id.equals(initiationResult.get(ID.name())) && state.name().equals(initiationResult.get(ADMIN_STATE.name()));
    }

    public void deleteSubscriptions(final String dataFile) throws InterruptedException {
        logger.debug("Deleting  Subscritpions.");
        final SubscriptionOperationRequest deleteMessage = new SubscriptionOperationRequest(PmServiceRequestsTypes.DELETE, null, null, dataFile);

        final CountDownLatch cl2 = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(cl2);

        sender.sendTestOperationMessageToPmService(deleteMessage);

        cl2.await(15, TimeUnit.SECONDS);
    }

    public void createSubscriptions(final String dataFile) throws InterruptedException {
        logger.debug("Creating Subscritpions.");
        testResponseHolder.clear();
        final SubscriptionOperationRequest createMessage = new SubscriptionOperationRequest(PmServiceRequestsTypes.CREATE, null, null, dataFile);

        final SubscriptionDataReader sdr = new SubscriptionDataReader(createMessage.getSubscriptionDataFile());
        final List<SubscriptionInfo> subscriptionList = sdr.getSubscriptionList();

        final CountDownLatch cl1 = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(cl1);

        sender.sendTestOperationMessageToPmService(createMessage);

        cl1.await(30, TimeUnit.SECONDS);
        final Map<String, Map<String, Object>> createdSubscriptions = testResponseHolder.getAllSubs();
        Assert.assertEquals(subscriptionList.size(), testResponseHolder.getAllSubs().size());
        final Set<String> subscriptionNames = createdSubscriptions.keySet();
        for (final String name : subscriptionNames) {
            final Map<String, Object> subscriptionAttr = createdSubscriptions.get(name);
            Assert.assertEquals(null, subscriptionAttr.get("Exception"));
            Assert.assertEquals(null, subscriptionAttr.get("ExceptionMessage"));
        }
        logger.debug("Created Subscriptions {}.", subscriptionNames);
    }

    public void addNodesToSubscriptions(final List<String> nodesToBeAdded, final String... subscriptionNames) throws InterruptedException {
        logger.debug("adding node {} to Subscritpions {}.", nodesToBeAdded, Arrays.asList(subscriptionNames));
        testResponseHolder.clear();
        final SubscriptionOperationRequest updateMessage = new SubscriptionOperationRequest(PmServiceRequestsTypes.ADD_NODE_TO_SUBSCRIPTION, null,
                null, null);
        updateMessage.setSubscriptionToRunActionOn(Arrays.asList(subscriptionNames));
        updateMessage.setNodesToBeAddedOrRemoved(nodesToBeAdded);

        final CountDownLatch cl1 = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(cl1);

        sender.sendTestOperationMessageToPmService(updateMessage);

        cl1.await(15, TimeUnit.SECONDS);
    }

    public void removeNodesFromSubscriptions(final List<String> nodesToBeRemoved, final String... subscriptionNames) throws InterruptedException {
        logger.debug("Removing node {} from Subscritpions {}.", nodesToBeRemoved, Arrays.asList(subscriptionNames));
        testResponseHolder.clear();
        final SubscriptionOperationRequest updateMessage = new SubscriptionOperationRequest(PmServiceRequestsTypes.REMOVE_NODE_FROM_SUBSCRIPTION,
                null, null, null);
        updateMessage.setSubscriptionToRunActionOn(Arrays.asList(subscriptionNames));
        updateMessage.setNodesToBeAddedOrRemoved(nodesToBeRemoved);

        final CountDownLatch cl1 = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(cl1);

        sender.sendTestOperationMessageToPmService(updateMessage);

        cl1.await(15, TimeUnit.SECONDS);
    }

    public void assertActivationSucceed(final String id, final String name, final Subscription subscription) {
        logger.debug("Assert id {} for {}", id, subscription);
        assertEquals(id, subscription.getIdAsString());
        logger.debug("Id passed");
        final Date startTime = subscription.getScheduleInfo().getStartDateTime();
        if (startTime == null) {
            assertAdministrationState(id, name, subscription, AdministrationState.ACTIVATING);
        } else {
            assertAdministrationState(id, name, subscription, AdministrationState.SCHEDULED);
        }
    }

    public void assertAdministrationState(final String id, final String name, final Subscription subscription, final AdministrationState state) {
        assertEquals(id, subscription.getIdAsString());
        assertEquals(String.format("Subscription %s should be %s. result: %s.", name, state.name(), subscription), state,
                subscription.getAdministrationState());
    }

    public void assertNumberOfNodesInSubscription(final int expectedNumberOfNodes, final String subscriptionName) throws DataAccessException {
        final ResourceSubscription subscription = (ResourceSubscription) findSubscriptionByName(subscriptionName);
        logger.debug("Subscription {}.", subscription);
        if (subscription != null) {
            logger.debug("Subscription{} Node {}.",subscription.getName(), subscription.getNumberOfNodes());
            assertEquals(expectedNumberOfNodes, subscription.getNumberOfNodes());
        }
    }
}
