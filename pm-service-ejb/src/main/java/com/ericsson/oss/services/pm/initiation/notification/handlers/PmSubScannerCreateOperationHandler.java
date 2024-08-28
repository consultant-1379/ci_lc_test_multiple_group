/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.notification.handlers;

import java.util.Date;
import java.util.Map;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import com.ericsson.oss.services.pm.exception.SubscriptionNotFoundDataAccessException;
import org.slf4j.Logger;

import com.ericsson.oss.pmic.api.utils.FdnUtil;
import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.services.pm.collection.notification.handlers.initiationresponsecache.handlers.InitiationResponseCacheHelper;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.task.TaskStatusValidator;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;

/**
 * This class can handle behavior related to PmSubScanner created event
 */
@Stateless
public class PmSubScannerCreateOperationHandler {

    @Inject
    private Logger logger;
    @Inject
    private MembershipListener membershipListener;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;
    @Inject
    @SubscriptionTaskStatusValidation
    private TaskStatusValidator<Subscription> taskStatusValidator;
    @Inject
    private InitiationResponseCacheHelper initiationResponseCacheHelper;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;
    @EJB
    private PmSubScannerCreateOperationHandler self;
    /**
     * On Scanner create notification this method will update initiation cache and starts file collection
     *
     * @param subScannerFdn
     *         Sub Scanner FDN
     * @param subscriptionId
     *         subscriptionId
     */
    public void execute(final String subScannerFdn, final String subscriptionId) {
        if (membershipListener.isMaster()) {
            try {
                final String nodeFdn = FdnUtil.getRootParentFdnFromChild(subScannerFdn);
                logger.debug("processing notification for {} : {}", subScannerFdn, nodeFdn);
                final boolean hasInitiationResponseBeenProcessed = initiationResponseCacheHelper
                        .processInitiationResponseCacheForActivation(subscriptionId, nodeFdn);
                if (!hasInitiationResponseBeenProcessed) {
                    calculateAndUpdateSubscriptionTaskStatus(subscriptionId);
                }
            } catch (final DataAccessException dae) {
                logger.error("There was an error extracting subscription with id {} from database when trying to calculate "
                        + "subscription's task status. Scanner fdn: {}", subscriptionId, subScannerFdn);
            }
        }
    }

    private void calculateAndUpdateSubscriptionTaskStatus(final String subscriptionId) throws DataAccessException {
        if (!dpsAvailabilityStatus.isAvailable()) {
            logger.warn("Failed to update SubscriptionTaskStatus for {}, Dps not available", subscriptionId);
            return;
        }
        if (Subscription.isValidSubscriptionId(subscriptionId)) {
            final Subscription subscription = subscriptionReadOperationService.findOneById(Long.parseLong(subscriptionId), false);
            final TaskStatus taskStatus = taskStatusValidator.getTaskStatus(subscription);
            subscription.setTaskStatus(taskStatus);
            final Map<String, Object> map = Subscription.getMapWithPersistenceTime();
            map.put(Subscription.Subscription220Attribute.taskStatus.name(), taskStatus.name());
            self.updateAttribute(subscription, map);
            subscription.setPersistenceTime((Date) map.get(Subscription.Subscription220Attribute.persistenceTime.name()));
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateAttribute(final Subscription subscription, final Map<String, Object> map) throws SubscriptionNotFoundDataAccessException {
        subscriptionWriteOperationService.updateAttributes(subscription.getId(), map);
    }
}
