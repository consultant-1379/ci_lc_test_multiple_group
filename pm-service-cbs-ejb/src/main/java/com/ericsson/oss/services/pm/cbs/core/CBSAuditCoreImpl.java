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

package com.ericsson.oss.services.pm.cbs.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import com.ericsson.oss.pmic.impl.handler.InvokeInTransaction;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender;
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled;
import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CriteriaSpecification;
import com.ericsson.oss.services.pm.cbs.events.CBSExecutionPlanEvent200;
import com.ericsson.oss.services.pm.cbs.events.CBSResourceSubscription;
import com.ericsson.oss.services.pm.cbs.service.api.CBSAuditCoreLocal;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * This class holds the business logic to audit Subscriptions.
 */
@Stateless
public class CBSAuditCoreImpl implements CBSAuditCoreLocal {

    private static final String CBS_EXECUTION_PLAN_EVENT_QUEUE = "//global/ClusteredCBSExecutionPlanEventQueue";

    @Inject
    private Logger logger;
    @Inject
    @Modeled
    private EventSender<CBSExecutionPlanEvent200> cbsExecutionPlanEventSender;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    @Override
    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void auditSubscriptions() {
        if (!dpsAvailabilityStatus.isAvailable()) {
            logger.warn("Failed to audit CBS subscription, Dps not available");
            return;
        }
        try {
            final List<ResourceSubscription> cbsSubscriptions = subscriptionReadOperationService.findAllCriteriaBasedResourceSubscriptions(true);
            if (cbsSubscriptions.isEmpty()) {
                logger.info("There are no CBS Subscription in the system");
            } else {
                logger.info("Number of Criteria Based Subscription :[{}]", cbsSubscriptions.size());
                final Map<String, List<ResourceSubscription>> criteriaIdToSubscriptionDtoMap = buildExecutionPlan(cbsSubscriptions);
                logger.debug(" Query String Map Plan size {}", criteriaIdToSubscriptionDtoMap.size());
                sendExecutionPlanEvent(criteriaIdToSubscriptionDtoMap);
            }
        } catch (final DataAccessException e) {
            logger.error("Cannot list CBS subscriptions. DataAccessException thrown with message: {}", e.getMessage());
            logger.info("Cannot list CBS subscriptions.", e);
        }
    }

    private Map<String, List<ResourceSubscription>> buildExecutionPlan(final List<ResourceSubscription> cbsSubscriptions) {

        final Map<String, List<ResourceSubscription>> criteriaIdToSubscriptionDtoMap = new HashMap<>();
        for (final ResourceSubscription resourceSubscription : cbsSubscriptions) {
            if (resourceSubscription.getCriteriaSpecification() != null && !resourceSubscription.getCriteriaSpecification().isEmpty()) {
                for (final CriteriaSpecification criteriaSpec : resourceSubscription.getCriteriaSpecification()) {
                    if (criteriaIdToSubscriptionDtoMap.containsKey(criteriaSpec.getCriteriaIdString())) {
                        criteriaIdToSubscriptionDtoMap.get(criteriaSpec.getCriteriaIdString()).add(resourceSubscription);
                    } else {
                        criteriaIdToSubscriptionDtoMap.put(criteriaSpec.getCriteriaIdString(),
                                new ArrayList<>(Collections.singletonList(resourceSubscription)));
                    }
                }
            }
        }

        return criteriaIdToSubscriptionDtoMap;
    }

    /**
     * This method builds cbsExecutionPlanEvent object with attributes queryString and cbs enabled ResourceSubscription list.
     */
    private void sendExecutionPlanEvent(final Map<String, List<ResourceSubscription>> criteriaIdToSubscriptionDtoMap) {
        for (final Map.Entry<String, List<ResourceSubscription>> entry : criteriaIdToSubscriptionDtoMap.entrySet()) {
            final List<CBSResourceSubscription> cbsResourceSubscriptions = populateCBSResourceSubscriptions(entry.getValue());
            final CBSExecutionPlanEvent200 cbsExecutionPlanEvent200 = new CBSExecutionPlanEvent200(entry.getKey(), cbsResourceSubscriptions);
            sendEvent(cbsExecutionPlanEvent200);
        }
    }

    private List<CBSResourceSubscription> populateCBSResourceSubscriptions(final List<ResourceSubscription> cbsResourceSubscrptionList) {
        final List<CBSResourceSubscription> cbsResourceSubscriptions = new ArrayList<>();
        for (final ResourceSubscription resourceSubscription : cbsResourceSubscrptionList) {
            logger.debug("ResourceSubscription Id {} name {} nodeListIndentity {}", resourceSubscription.getId(), resourceSubscription.getName(),
                    resourceSubscription.getNodeListIdentity());
            final CBSResourceSubscription cbsResourceSubscription = new CBSResourceSubscription(resourceSubscription.getIdAsString(),
                    resourceSubscription.getName(), resourceSubscription.getNodeListIdentity());
            cbsResourceSubscriptions.add(cbsResourceSubscription);
        }
        return cbsResourceSubscriptions;
    }

    private void sendEvent(final CBSExecutionPlanEvent200 cbsExecutionPlanEvent200) {
        try {
            cbsExecutionPlanEventSender.send(cbsExecutionPlanEvent200, CBS_EXECUTION_PLAN_EVENT_QUEUE);
            logger.info("CBSExecutionPlanEvent {}  sent", cbsExecutionPlanEvent200);
        } catch (final Exception excep) {
            logger.error(" Exception  {} occurred while sending CBSExecutionPlanEvent {} to queue {}", cbsExecutionPlanEvent200,
                    CBS_EXECUTION_PLAN_EVENT_QUEUE, excep);
        }
    }
}
