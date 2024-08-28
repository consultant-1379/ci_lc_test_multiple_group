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

package com.ericsson.oss.services.pm.initiation.scanner.master;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.cache.data.ValueHolder;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.CtumSubscription;
import com.ericsson.oss.pmic.dto.subscription.EventSubscription;
import com.ericsson.oss.pmic.dto.subscription.ResSubscription;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.UETraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.UetrSubscription;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.common.utils.KeyGenerator;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.initiation.cache.model.SubscriptionDataCacheV2;
import com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * Subscription can have n number of nodes but the Subcription data is same for all node.
 * SubscriptionManager gets subscription from DPS and stores it to temporary storage see {@link SubscriptionDataCacheWrapper}
 * and {@link SubscriptionDataCacheV2} , subsequent request for subscription will get data from Cache.
 */
@ApplicationScoped
public class SubscriptionManager {

    @Inject
    private Logger logger;
    @Inject
    private SubscriptionDataCacheWrapper subscriptionDataCacheWrapper;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    /**
     * This method gets the the subscription for the given subscriptionName from DPS & stores in a temporary cache to avoid frequent Database access .
     *
     * @param subscriptionName
     *         - the name of the subscription to retrieve
     * @param type
     *         - the type of subscription to retrieve
     *
     * @return - returns a Subscription Object
     * @throws DataAccessException
     *         - if an invalid input exception is generated
     * @throws InvalidSubscriptionException
     *         - if an unexpected exception is thrown while finding subscription or the retry mechanism exhausts all attempts.
     */
    public SubscriptionWrapper getSubscriptionWrapper(final String subscriptionName, final SubscriptionType type)
            throws InvalidSubscriptionException, DataAccessException {
        final String key = KeyGenerator.generateKey(subscriptionName, type.name());
        synchronized (this) {
            final SubscriptionWrapper subscriptionWrapper = getSubscriptionWrapperFromCache(key);
            if (!subscriptionWrapper.isValid()) {
                logger.info("Updating cache for subscription name : {}  with key : {} ", subscriptionName, key);
                return updateCacheWithSubscriptionDataFromDps(subscriptionName, type);
            }
            return subscriptionWrapper;
        }
    }

    /**
     * This method gets the the subscription for the given subscriptionId from DPS & stores in a temporary cache to avoid frequent Database access .
     *
     * @param subscriptionId
     *         - the poid of the subscription to retrieve
     *
     * @return - returns a Subscription Object
     * @throws DataAccessException
     *         - if an invalid input exception is generated
     * @throws RetryServiceException
     *         - if an unexpected exception is thrown while finding subscription or the retry mechanism exhausts all attempts.
     * @throws InvalidSubscriptionException
     *         - if the subscription could not be extracted from dps.
     */
    public SubscriptionWrapper getSubscriptionWrapperById(final Long subscriptionId)
            throws DataAccessException, InvalidSubscriptionException, RetryServiceException {
        final String key = KeyGenerator.generateKey(String.valueOf(subscriptionId));
        synchronized (this) {
            final SubscriptionWrapper subscriptionWrapper = getSubscriptionWrapperFromCache(key);
            if (!subscriptionWrapper.isValid()) {
                logger.info("Updating cache for subscription id :: {} with key : {} ", subscriptionId, key);
                return updateCacheWithSubscriptionDataFromDpsById(subscriptionId);
            }
            return subscriptionWrapper;
        }
    }

    /**
     * Removes the subscription from cache
     *
     * @param subscription
     *         - The subscription to remove
     */
    public void removeSubscriptionFromCache(final Subscription subscription) {
        subscriptionDataCacheWrapper.getCache().remove(KeyGenerator.generateKey(subscription.getName(), subscription.getType().name()));
        subscriptionDataCacheWrapper.getCache().remove(String.valueOf(subscription.getId()));
    }

    /**
     * Removes subscription from cache
     *
     * @param subscriptionName
     *         - Subscription name in the cache
     * @param subscriptionType
     *         - Subscription Type in the cache
     */
    public void removeSubscriptionFromCache(final String subscriptionName, final SubscriptionType subscriptionType) {
        final String key = KeyGenerator.generateKey(subscriptionName, subscriptionType.name());
        subscriptionDataCacheWrapper.getCache().remove(key);
    }

    private SubscriptionWrapper getSubscriptionWrapperFromCache(final String key) {
        final ValueHolder subscriptionWrapperHolder = subscriptionDataCacheWrapper.get(key);
        if (validateValueHolder(subscriptionWrapperHolder)) {
            return (SubscriptionWrapper) subscriptionWrapperHolder.getData().get(key);
        }
        return NullSubscriptionWrapper.getInstance();
    }

    private SubscriptionWrapper updateCacheWithSubscriptionDataFromDps(final String subscriptionName, final SubscriptionType subType)
            throws DataAccessException, InvalidSubscriptionException {
        final List<Subscription> subscriptions = subscriptionReadOperationService
                .findAllBySubscriptionNameAndModelInfo(subscriptionName, subType.getModelInfo(), true);
        for (final Subscription subscription : subscriptions) {
            if (subscription.getName().equals(subscriptionName)) {
                final Set<String> nodeFdns = getSubscriptionNodeFdns(subscription);
                if (!nodeFdns.isEmpty()) {
                    final SubscriptionWrapper subscriptionWrapper = new SubscriptionWrapper(getSubscriptionLight(subscription, subType), nodeFdns);
                    final ValueHolder holder = getSafeHolder(subscriptionWrapper);
                    subscriptionDataCacheWrapper.put(holder.getKey(), holder);
                    logger.debug("Cache content for subscription id  {} , Node count : {}", subscription.getId(), nodeFdns.size());
                    return subscriptionWrapper;
               }
            }
        }
        return NullSubscriptionWrapper.getInstance();
    }

    private SubscriptionWrapper updateCacheWithSubscriptionDataFromDpsById(final Long subscriptionId)
            throws DataAccessException, InvalidSubscriptionException, RetryServiceException {
        final Subscription subscription = subscriptionReadOperationService.findByIdWithRetry(subscriptionId, true);
        if (subscription == null) {
            return NullSubscriptionWrapper.getInstance();
        }
        final Set<String> nodeFdns = getSubscriptionNodeFdns(subscription);
        final SubscriptionWrapper subscriptionWrapper = new SubscriptionWrapper(getSubscriptionLight(subscription, subscription.getType()), nodeFdns);
        final ValueHolder holder = getSafeHolderById(subscriptionWrapper);
        subscriptionDataCacheWrapper.put(holder.getKey(), holder);
        logger.debug("Cache content for subscription id  {} , Node : {} with key : {}", subscription.getId(), Collections.emptyList(),
                holder.getKey());
        return subscriptionWrapper;
    }

    /**
     * @param subscriptionWrapper
     *         - subscription wrapper object to extract subscription information
     *
     * @return - an holder which will be version based , so upgrade safe. {@link ValueHolder} is constructed with key , version and Serializable
     * object and stored. The object is De-serializable only if the key & version is matched. e.g if the structure of serialized object is
     * changed & version is incremented , any object with previous version is safely ignored.
     */
    private ValueHolder getSafeHolder(final SubscriptionWrapper subscriptionWrapper) {
        final Map<String, Serializable> nameTosubscriptionWrapperMap = new HashMap<>();
        final String key = KeyGenerator.generateKey(subscriptionWrapper.getName(), subscriptionWrapper.getSubscriptionType().name());
        nameTosubscriptionWrapperMap.put(key, subscriptionWrapper);
        return new ValueHolder(key, SubscriptionWrapper.VERSION, nameTosubscriptionWrapperMap);
    }

    private ValueHolder getSafeHolderById(final SubscriptionWrapper subscriptionWrapper) {
        final Map<String, Serializable> nameTosubscriptionWrapperMap = new HashMap<>();
        final String key = KeyGenerator.generateKey(String.valueOf(subscriptionWrapper.getSubscriptionId()));
        nameTosubscriptionWrapperMap.put(key, subscriptionWrapper);
        return new ValueHolder(key, SubscriptionWrapper.VERSION, nameTosubscriptionWrapperMap);
    }

    private Subscription getSubscriptionLight(final Subscription subscription, final SubscriptionType subType) throws InvalidSubscriptionException {
        final String className = subscription.getClass().getName();
        try {
            final Class subscriptionClassType = Class.forName(className);
            final Subscription lightSubscription = (Subscription) subscriptionClassType.newInstance();
            lightSubscription.setId(subscription.getId());
            lightSubscription.setType(subType);
            lightSubscription.setName(subscription.getName());
            lightSubscription.setDescription(subscription.getDescription());
            lightSubscription.setAdministrationState(subscription.getAdministrationState());
            lightSubscription.setTaskStatus(subscription.getTaskStatus());
            lightSubscription.setPersistenceTime(subscription.getPersistenceTime());
            lightSubscription.setOwner(subscription.getOwner());
            lightSubscription.setUserType(subscription.getUserType());
            lightSubscription.setActivationTime(subscription.getActivationTime());
            lightSubscription.setUserActivationDateTime(subscription.getUserActivationDateTime());
            lightSubscription.setRop(subscription.getRop());
            updateSubscriptionWithAdditionalAttributesIfRequired(lightSubscription, subscription);
            return lightSubscription;
        } catch (final Exception e) {
            logger.error("Subscription Type {} not supported : {}", subscription.getType().name(), e.getMessage());
            throw new InvalidSubscriptionException(e.getMessage(), e);
        }
    }

    private void updateSubscriptionWithAdditionalAttributesIfRequired(final Subscription lightSubscription, final Subscription dpsSubscription) {
        if (lightSubscription instanceof UetrSubscription) {
            ((UetrSubscription) lightSubscription).setUeInfo(((UetrSubscription) dpsSubscription).getUeInfoList());
        } else if (lightSubscription instanceof EventSubscription) {
            ((EventSubscription) lightSubscription).setOutputMode(((EventSubscription) dpsSubscription).getOutputMode());
            if(lightSubscription instanceof CellTraceSubscription) {
                updateCellTraceSubscriptionWithAdditionalAttributes(((CellTraceSubscription) lightSubscription), ((CellTraceSubscription) dpsSubscription));
            }
        } else if (lightSubscription instanceof UETraceSubscription) {
            ((UETraceSubscription) lightSubscription).setOutputMode(((UETraceSubscription) dpsSubscription).getOutputMode());
        } else if (lightSubscription instanceof CtumSubscription) {
            ((CtumSubscription) lightSubscription).setOutputMode(((CtumSubscription) dpsSubscription).getOutputMode());
        }
    }

    private void updateCellTraceSubscriptionWithAdditionalAttributes(final CellTraceSubscription lightCellTraceSubscription,
                                                                     final CellTraceSubscription dpsCellTraceSubscription) {
        lightCellTraceSubscription.setCellTraceCategory(dpsCellTraceSubscription.getCellTraceCategory());
        lightCellTraceSubscription.setUniqueEventProducerIds(dpsCellTraceSubscription.getEventProducerIdsFromEvents());
        lightCellTraceSubscription.setUniqueEbsEventProducerIds(dpsCellTraceSubscription.getEbsEventProducerIdsFromEvents());
    }

    /**
     * @param subscriptionWrapperHolder
     *         - Value Holder to validate
     *
     * @return - an holder which will be version based , so upgrade safe. {@link ValueHolder} is constructed with key , version and Serializable
     * object and stored. The object is De-serializable only if the key & version is matched. e.g if the structure of serialized object is
     * changed & version is incremented , any object with previous version is safely ignored.
     */
    private boolean validateValueHolder(final ValueHolder subscriptionWrapperHolder) {
        return subscriptionWrapperHolder != null && subscriptionWrapperHolder.getVersion().equals(SubscriptionWrapper.VERSION);
    }

    private Set<String> getSubscriptionNodeFdns(final Subscription subscription) {
        final Set<String> nodeFdns = new HashSet<>();
        if (subscription instanceof ResourceSubscription) {
            nodeFdns.addAll(((ResourceSubscription) subscription).getNodesFdns());
            if (subscription.getType() == SubscriptionType.RES) {
                nodeFdns.addAll(((ResSubscription) subscription).getAttachedNodesFdn());
            }
        }
        return nodeFdns;
    }


    public  SubscriptionWrapper addOrUpdateCacheWithSubscriptionData( final Subscription subscription) throws InvalidSubscriptionException {
         final Set<String> nodeFdns = getSubscriptionNodeFdns(subscription);
         final SubscriptionWrapper subscriptionWrapper = new SubscriptionWrapper(getSubscriptionLight(subscription, subscription.getType()), nodeFdns);
         final ValueHolder holder = getSafeHolder(subscriptionWrapper);
         subscriptionDataCacheWrapper.put(holder.getKey(), holder);
         return subscriptionWrapper;

    }
}
