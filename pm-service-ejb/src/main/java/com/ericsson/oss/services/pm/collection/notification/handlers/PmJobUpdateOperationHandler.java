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

package com.ericsson.oss.services.pm.collection.notification.handlers;

import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMJOB_STATUS_ERROR;
import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMJOB_STATUS_UNKNOWN;
import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMJOB_SUBSCRIPTION_PO_ID_ATTRIBUTE;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.datalayer.dps.exception.model.NotDefinedInModelException;
import com.ericsson.oss.pmic.dto.pmjob.PmJob;
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.services.pm.collection.notification.handlers.initiationresponsecache.handlers.InitiationResponseCacheHelper;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.PmJobNotFoundDataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.PmJobService;
import com.ericsson.oss.services.pm.initiation.model.resource.PMICJobStatus;
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionManager;
import com.ericsson.oss.services.pm.initiation.task.TaskStatusValidator;
import com.ericsson.oss.services.pm.initiation.task.qualifier.SubscriptionTaskStatusValidation;
import com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;

/**
 * This class can handle only behavior related to PmJob update event
 */
@Stateless
public class PmJobUpdateOperationHandler {

    @Inject
    private Logger logger;
    @Inject
    private PmJobService pmJobService;
    @Inject
    private SubscriptionManager subscriptionManager;
    @Inject
    @SubscriptionTaskStatusValidation
    private TaskStatusValidator<Subscription> taskStatusValidator;
    @Inject
    private FileCollectionOperationHelper fileCollectionOperationHelper;
    @Inject
    private InitiationResponseCacheHelper initiationResponseCacheHelper;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;
    @EJB
    private PmJobUpdateOperationHandler self;

    /**
     * On PmJob Update notification processes and changes the status of the subscription
     *
     * @param pmJobFdn
     *     PmJobFdn
     * @param updatedAttributes
     *     Updated attributes
     *
     * @throws PmJobNotFoundDataAccessException
     *     - will be thrown when searched PMICJobInfo not found in DPS
     */
    public void execute(final String pmJobFdn, final PMICJobInfoUpdateAttributeVO updatedAttributes) throws PmJobNotFoundDataAccessException {
        PmJob pmJob = null;
        try {
            pmJob = pmJobService.findOneByFdn(pmJobFdn);
        } catch (final DataAccessException | RuntimeDataAccessException exception) {
            logger.error("Cannot get PmJob with fdn {} from database for some underlying problem with DPS. {}", pmJobFdn, exception.getMessage());
            logger.info("Cannot get PmJob with fdn {} from database for some underlying problem with DPS.", pmJobFdn, exception);
        }

        if (pmJob == null) {
            logger.error("PmJob does not exist in DPS : Not processing PMICJobInfo OBJECT_UPDATE notification for {} ", pmJobFdn);
            throw new PmJobNotFoundDataAccessException(
                "PmJob does not exist in DPS hence not processing PMICJobInfo OBJECT_UPDATE notification for " + pmJobFdn);
        }
        final Long subscriptionId = pmJob.getSubscriptionId();
        try {
            updateFileCollectionBehaviourForPmJob(pmJob, updatedAttributes);
            boolean initiationTrackerWasProcessed = false;
            if (updatedAttributes.isStatusAttributeUpdated()) {
                initiationTrackerWasProcessed = processInitiationResponseCache(pmJob);
            }
            updateSubscriptionTaskStatus(updatedAttributes, initiationTrackerWasProcessed, subscriptionId);
        } catch (final NotDefinedInModelException exception) {
            logger.error("It appears that {} is not defined in the PmJob model??? Has the model been changed", PMJOB_SUBSCRIPTION_PO_ID_ATTRIBUTE,
                exception);
        } catch (final DataAccessException | InvalidSubscriptionException | RetryServiceException e) {
            logger.error("Failed to update File Collection behaviour for PmJob {}, seems to be an underlying problem in the DPS", pmJobFdn);
            logger.info("Failed to update File Collection behaviour for PmJob {}, seems to be an underlying problem in the DPS", pmJobFdn, e);
        }
    }

    private void updateFileCollectionBehaviourForPmJob(final PmJob pmJob,
                                                       final PMICJobInfoUpdateAttributeVO updatedAttributes)
        throws DataAccessException, InvalidSubscriptionException, RetryServiceException {
        final String nodeFdn = pmJob.getNodeFdn();
        final int ropPeriodInSeconds = pmJob.getRopPeriod();
        final ProcessType processType = pmJob.getProcessType();
        final Long subscriptionId = pmJob.getSubscriptionId();
        if (Subscription.isDefaultFileCollectionSupported(processType)) {
            if (isPmJobActive(pmJob, updatedAttributes) && subscriptionSupportsFileCollection(subscriptionId)) {
                logger.debug("PmJob status is ACTIVE for PmJobFDN : {} of subscription ID : {} so starting file collection for ROP period : {}",
                    pmJob.getFdn(), subscriptionId, ropPeriodInSeconds);
                fileCollectionOperationHelper.startFileCollection(ropPeriodInSeconds, nodeFdn, processType.name());
            } else {
                if (isPmJobOldStatusActive(updatedAttributes)) {
                    logger.debug("PmJobFDN  : {} has old status is {} which is changed to new status : {} of  subscription ID : {} "
                            + "and  process Type is : {}. so stopping file collection for ROP period : {}",
                        pmJob.getFdn(), updatedAttributes.getOldStatusValue(), updatedAttributes.getNewStatusValue(), subscriptionId,
                        processType, ropPeriodInSeconds);
                    fileCollectionOperationHelper.stopFileCollection(ropPeriodInSeconds, nodeFdn, processType.name());
                }
            }
        } else {
            logger.debug("PmJobFDN : {} belongs to process type : {} which is not using PMIC Common file collection mechanism!", pmJob.getFdn(),
                processType);
        }
    }

    private boolean subscriptionSupportsFileCollection(final Long subscriptionId)
        throws DataAccessException, RetryServiceException, InvalidSubscriptionException {
        final Subscription subscription = subscriptionManager.getSubscriptionWrapperById(subscriptionId).getSubscription();
        return subscriptionReadOperationService.doesSubscriptionSupportFileCollection(subscription);
    }

    private static boolean isPmJobOldStatusActive(final PMICJobInfoUpdateAttributeVO updatedAttributes) {
        final String oldStatusValue = updatedAttributes.getOldStatusValue();
        return isPmJobActive(oldStatusValue);
    }

    private static boolean isPmJobActive(final PmJob pmJob, final PMICJobInfoUpdateAttributeVO updatedAttributes) {
        if (updatedAttributes.isStatusAttributeUpdated()) {
            return isPmJobActive(updatedAttributes.getNewStatusValue());
        } else {
            return pmJob.getStatus() == PmJobStatus.ACTIVE;
        }
    }

    private static boolean isPmJobActive(final String pmJobStatus) {
        return PMICJobStatus.ACTIVE.equalsName(pmJobStatus);
    }

    private void updateSubscriptionTaskStatus(final PMICJobInfoUpdateAttributeVO updatedAttributes, final boolean initiationTrackerWasProcessed,
                                              final Long subscriptionId) {
        if (needsToUpdateStatus(updatedAttributes, initiationTrackerWasProcessed, subscriptionId)) {
            subscriptionTaskStatusNotUpdatedToError(updatedAttributes, subscriptionId);
        }
    }

    private void subscriptionTaskStatusNotUpdatedToError(final PMICJobInfoUpdateAttributeVO updatedAttributes, final Long subscriptionId) {
        final String newPmJobStatus = updatedAttributes.getNewStatusValue();
        try {
            final Subscription subscription = subscriptionManager.getSubscriptionWrapperById(subscriptionId).getSubscription();
            if (subscription == null) {
                logger.error("Cannot update subscription's admin state to ERROR because subscription cannot be found for id {}", subscriptionId);
                return;
            }

            if (isNewPmJobStatusErrorOrUnknown(newPmJobStatus)) {
                subscription.setTaskStatus(TaskStatus.ERROR);
            } else {
                final TaskStatus taskStatus = taskStatusValidator.getTaskStatus(subscription);
                subscription.setTaskStatus(taskStatus);
            }
            self.updateSubscriptionStatus(subscription);
        } catch (final DataAccessException | InvalidSubscriptionException | RetryServiceException e) {
            logger.warn("Cannot update subscription's admin state to ERROR. Subscription id: {}, Error message: {}", subscriptionId, e.getMessage());
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateSubscriptionStatus(final Subscription subscription) throws DataAccessException {
        subscriptionWriteOperationService.saveOrUpdate(subscription);
    }

    private static boolean needsToUpdateStatus(final PMICJobInfoUpdateAttributeVO updatedAttributes, final boolean initiationTrackerWasProcessed,
                                               final Long subscriptionId) {
        return updatedAttributes.isStatusAttributeUpdated() && !initiationTrackerWasProcessed
            && Scanner.isValidSubscriptionId(subscriptionId);
    }

    private static boolean isNewPmJobStatusErrorOrUnknown(final String newPmJobStatus) {
        return PMJOB_STATUS_ERROR.equals(newPmJobStatus) || PMJOB_STATUS_UNKNOWN.equals(newPmJobStatus);
    }

    private boolean processInitiationResponseCache(final PmJob pmJob) {
        final String subscriptionId = String.valueOf(pmJob.getSubscriptionId());
        final String nodeFdn = pmJob.getNodeFdn();
        return initiationResponseCacheHelper.processInitiationResponseCache(subscriptionId, nodeFdn);
    }

}
