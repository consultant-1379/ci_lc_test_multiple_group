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

package com.ericsson.oss.services.pm.collection.notification.handlers;

import static com.ericsson.oss.services.pm.collection.notification.handlers.FileCollectionOperationHelper.PREDEF_10004_CELLTRACE_PATTERN;

import java.util.Collections;
import java.util.List;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.collection.notification.handlers.initiationresponsecache.handlers.InitiationResponseCacheHelper;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.PmSubScannerService;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionManager;
import com.ericsson.oss.services.pm.scheduling.impl.DelayedTaskStatusValidator;
import com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * This class can handle only behavior related to scanner update event
 */
@Stateless
public class ScannerUpdateOperationHandler {

    @Inject
    private Logger logger;
    @Inject
    private ScannerService scannerService;
    @Inject
    private PmSubScannerService subScannerService;
    @Inject
    private SubscriptionManager subscriptionManager;
    @Inject
    private FileCollectionOperationHelper fileCollectionOperationHelper;
    @Inject
    private InitiationResponseCacheHelper initiationResponseCacheHelper;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private DelayedTaskStatusValidator delayedTaskStatusValidator;
    @Inject
    private ConfigurationChangeListener configurationChangeListener;

    /**
     * Performs any necessary operations that should be performed when a notification is received that a scanner has been updated in DPS
     *
     * @param scannerFdn
     *     - The scanner's fdn
     * @param updatedAttributes
     *     - The updated attributes object
     */
    public void execute(final String scannerFdn, final PMICScannerInfoUpdateAttributeVO updatedAttributes) {
        final Scanner scanner;
        try {
            scanner = scannerService.findOneByFdn(scannerFdn);
            if (scanner == null) {
                logger.error("Scanner does not exists in DPS : Not processing PMICScannerInfo OBJECT_UPDATE notification for {} ", scannerFdn);
                return;
            }
            if (!isEbsScanner(scanner.getName()) && scannerSupportsFileCollection(scanner)) {// Celltrace scanner PREDEF_10004_CELLTRACE -streaming)
                updateFileCollectionBehaviourForScanner(scanner, updatedAttributes);
            }
            if (isScannerAttributesUpdated(updatedAttributes)) {
                if (Scanner.isValidSubscriptionId(scanner.getSubscriptionId())) {
                    initiationResponseCacheHelper.processInitiationResponseCache(String.valueOf(scanner.getSubscriptionId()), scanner.getNodeFdn());
                    delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(scanner.getSubscriptionId(), scanner.getNodeFdn());
                    if (ScannerStatus.INACTIVE == scanner.getStatus()) {
                        updateScannerInDps(scanner);
                    }
                } else if (isEbsScanner(scanner.getName())) {
                    subScannerService.findAllByParentScannerFdn(scanner.getFdn()).forEach(subScanner ->
                        delayedTaskStatusValidator.scheduleDelayedTaskStatusValidation(subScanner.getSubscriptionId(), scanner.getNodeFdn())
                    );
                }
            }
            if (ProcessType.REGULAR_GPEH.equals(scanner.getProcessType())) {
                processScannerROPPeriodUpdate(scanner, updatedAttributes);
            }
        } catch (final DataAccessException | RuntimeDataAccessException | InvalidSubscriptionException | RetryServiceException e) {
            logger.error("Failed to update scanner Operation {}. {}", scannerFdn, e.getMessage());
            logger.info("Failed to update scanner Operation {} .", scannerFdn, e);
        }
    }

    /**
     * Performs Scanner update operation in DPS
     *
     * @param scanner
     *     - The scanner's object
     */
    public void updateScannerInDps(final Scanner scanner) throws DataAccessException {
        scanner.setSubscriptionId(Subscription.UNKNOWN_SUBSCRIPTION_ID);
        scannerService.saveOrUpdate(scanner);
    }

    private boolean scannerSupportsFileCollection(final Scanner scanner) throws DataAccessException, RetryServiceException, InvalidSubscriptionException {
        if (!scanner.hasAssignedSubscriptionId()) {
            return true;// File collection should happen for PRE-DEF or USER-DEF scanners that is not associated to subscription
        }
        final Subscription subscription = subscriptionManager.getSubscriptionWrapperById(scanner.getSubscriptionId()).getSubscription();
        return subscriptionReadOperationService.doesSubscriptionSupportFileCollection(subscription);
    }

    private static boolean isEbsScanner(final String scannerName) {
        return PREDEF_10004_CELLTRACE_PATTERN.matcher(scannerName).matches();
    }

    private boolean isScannerAttributesUpdated(final PMICScannerInfoUpdateAttributeVO updatedAttributes) {
        return updatedAttributes.isStatusAttributeUpdated() || subscriptionIdChangedDuringMigration(updatedAttributes);
    }

    private boolean subscriptionIdChangedDuringMigration(final PMICScannerInfoUpdateAttributeVO updatedAttributes) {
        return updatedAttributes.isSubscriptionIdUpdated() && configurationChangeListener.getPmMigrationEnabled();
    }

    private void processScannerROPPeriodUpdate(final Scanner scanner, final PMICScannerInfoUpdateAttributeVO updatedAttributes) {
        if (updatedAttributes.isRopPeriodUpdated() && ScannerStatus.ACTIVE == scanner.getStatus()) {
            logger.debug("ROP period updated for an active DPS Scanner with FDN: {} of subscription ID: {}. Updating the File collection to "
                + "ROP period: {}", scanner.getFdn(), scanner.getSubscriptionId(), updatedAttributes.getNewRopPeriodValue());
            fileCollectionOperationHelper.updateFileCollectionForNewRopPeriod(updatedAttributes.getNewRopPeriodValue(), scanner.getNodeFdn(),
                scanner.getProcessType().name(), updatedAttributes.getOldRopPeriodValue());
        }
    }

    /**
     * Start or Stop file collection for the rop
     */
    private void updateFileCollectionBehaviourForScanner(final Scanner scanner, final PMICScannerInfoUpdateAttributeVO updatedAttributes)
        throws DataAccessException {
        if (updatedAttributes.isFileCollectionEnabledAttributeUpdated() || updatedAttributes.isStatusAttributeUpdated()) {
            final String nodeFdn = scanner.getNodeFdn();
            final int ropPeriodInSeconds = scanner.getRopPeriod();
            final ProcessType processType = scanner.getProcessType();
            if (isFileCollectionEnabled(scanner, updatedAttributes) && isScannerActive(scanner, updatedAttributes)) {
                logger.debug(
                    "Scanner status is ACTIVE and File Collection enabled for scannerFDN : {} associated to subscription ID : {},"
                        + " so starting file collection for ROP period : {}",
                    scanner.getFdn(), scanner.getSubscriptionId(), ropPeriodInSeconds);
                fileCollectionOperationHelper.startFileCollection(ropPeriodInSeconds, nodeFdn, processType.name());
            } else if (isNoActiveScannersWithFileCollectionEnabledOnNodeForGivenProcessTypeAndRopPeriod(nodeFdn, processType, ropPeriodInSeconds)) {
                logger.debug("No more active scanners for node {} with type {} and ROP {}. Removing from file collection node list", nodeFdn,
                    processType, ropPeriodInSeconds);
                fileCollectionOperationHelper.stopFileCollection(ropPeriodInSeconds, nodeFdn, processType.name());
            }
        }
    }

    private static boolean isFileCollectionEnabled(final Scanner scanner, final PMICScannerInfoUpdateAttributeVO updatedAttributes) {
        if (updatedAttributes.isFileCollectionEnabledAttributeUpdated()) {
            return updatedAttributes.isFileCollectionEnabledNewValue();
        } else {
            return scanner.isFileCollectionEnabled();
        }
    }

    private static boolean isScannerActive(final Scanner scanner, final PMICScannerInfoUpdateAttributeVO updatedAttributes) {
        if (updatedAttributes.isStatusAttributeUpdated()) {
            return isScannerActive(updatedAttributes.getNewStatusValue());
        } else {
            return ScannerStatus.ACTIVE == scanner.getStatus();
        }
    }

    private static boolean isScannerActive(final String newScannerStatus) {
        return ScannerStatus.ACTIVE.name().equalsIgnoreCase(newScannerStatus);
    }

    private boolean isNoActiveScannersWithFileCollectionEnabledOnNodeForGivenProcessTypeAndRopPeriod(final String nodeFdn,
                                                                                                     final ProcessType processType,
                                                                                                     final int ropPeriodInSeconds)
        throws DataAccessException {
        final ProcessType[] processTypes;
        if (processType.getSubscriptionType().isOneOf(SubscriptionType.CELLTRACE, SubscriptionType.CONTINUOUSCELLTRACE)) {
            processTypes = new ProcessType[]{ProcessType.NORMAL_PRIORITY_CELLTRACE, ProcessType.HIGH_PRIORITY_CELLTRACE};
        } else {
            processTypes = new ProcessType[]{processType};
        }
        final ScannerStatus[] scannerStatuses = {ScannerStatus.ACTIVE};
        final List<Scanner> scanners = scannerService.findAllByNodeFdnAndProcessTypeAndRopDurationAndScannerStatusAndFileCollection(
            Collections.singleton(nodeFdn), processTypes, RopPeriod.fromSeconds(ropPeriodInSeconds), scannerStatuses, true);
        return !(scanners != null && !scanners.isEmpty());
    }
}
