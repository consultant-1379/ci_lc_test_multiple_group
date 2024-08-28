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

package com.ericsson.oss.services.pm.initiation.notification;

import static java.lang.String.format;

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.PMFUNCTION_MEDIATION_CAPABILITY_NOT_SUPPRTED;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.PM_FUNCTION_DPS_NOTIFICATION;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.api.cache.PmFunctionData;
import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.pmjob.PmJob;
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerType;
import com.ericsson.oss.pmic.dto.subscanner.PmSubScanner;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;
import com.ericsson.oss.services.model.ned.pm.function.FileCollectionState;
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState;
import com.ericsson.oss.services.model.ned.pm.function.ScannerMasterState;
import com.ericsson.oss.services.pm.collection.cache.PmFunctionOffErrorNodeCache;
import com.ericsson.oss.services.pm.collection.notification.handlers.FileCollectionStateUpdateHandler;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.common.utils.PmFunctionConstants;
import com.ericsson.oss.services.pm.config.task.factories.PmConfigTaskRequestFactory;
import com.ericsson.oss.services.pm.ebs.utils.EbsSubscriptionHelper;
import com.ericsson.oss.services.pm.eventSender.PmEventSender;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.NodeNotFoundDataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.generic.PmJobService;
import com.ericsson.oss.services.pm.generic.PmSubScannerService;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.notification.events.Deactivate;
import com.ericsson.oss.services.pm.initiation.notification.events.InitiationEvent;
import com.ericsson.oss.services.pm.initiation.notification.handlers.ResPmFunctionHelper;
import com.ericsson.oss.services.pm.initiation.scanner.operation.ScannerOperation;
import com.ericsson.oss.services.pm.initiation.scanner.polling.ScannerPollingTaskSender;
import com.ericsson.oss.services.pm.initiation.tasks.PmFunctionUpdateTaskRequest;
import com.ericsson.oss.services.pm.initiation.utils.PmFunctionUtil;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * This class in invoked for managing PmFunction MO update.
 */
@SuppressWarnings("PMD.TooManyFields")
public class DpsPmEnabledUpdateNotificationProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DpsPmEnabledUpdateNotificationProcessor.class);

    @Inject
    private PmEventSender sender;
    @Inject
    private NodeService nodeService;
    @Inject
    private PmJobService pmJobService;
    @Inject
    private ScannerService scannerService;
    @Inject
    private ScannerOperation scannerOperation;
    @Inject
    @Deactivate
    private InitiationEvent deactivationEvent;
    @Inject
    private PmSubScannerService pmSubScannerService;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private EbsSubscriptionHelper ebsSubscriptionHelper;
    @Inject
    private ScannerPollingTaskSender scannerPollingTaskSender;
    @Inject
    private PmConfigTaskRequestFactory pmConfigTaskRequestFactory;
    @Inject
    private PmFunctionOffErrorNodeCache pmFunctionOffErrorNodeCache;
    @Inject
    private ConfigurationChangeListener configurationChangeListener;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private FileCollectionStateUpdateHandler fileCollectionStateUpdateHandler;
    @Inject
    private ResPmFunctionHelper resPmFunctionHelper;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;

    /**
     * Process PmfunctionData variation from cachePmFunctionData to avcPmFunctionData for a specific nodefdn
     *
     * @param nodeFdn
     *         - the fdn of the NetworkElement MO
     * @param avcPmFunctionData
     *         - avc PmFunctionData
     * @param oldPmFunctionData
     *         - old PmFunctionData
     */
    public void processPmFunctionChange(final String nodeFdn, final PmFunctionData avcPmFunctionData, final PmFunctionData oldPmFunctionData) {
        if (!dpsAvailabilityStatus.isAvailable()) {
            logger.warn("Failed to process pmFunction update for {}, Dps not available", nodeFdn);
            return;
        }
        handlePmFunctionChange(nodeFdn, avcPmFunctionData, oldPmFunctionData);
    }

    private void handlePmFunctionChange(final String nodeFdn, final PmFunctionData avcPmFunctionData, final PmFunctionData oldPmFunctionData) {
        final PmFunctionData updatedPmFunctionData = getUpdatedPmFunctionData(avcPmFunctionData, oldPmFunctionData);
        logger.info("Updated PmFunction Data NodeFDN {}, PM_ENABLED:{} - FILE_COLLECTION_STATE:{} - SCANNER_MASTER_STATE:{} - NE_CONFIGURATION_MANAGER_STATE:{}",
                nodeFdn, updatedPmFunctionData.isPmFunctionEnabled(), updatedPmFunctionData.getFileCollectionState(),
                updatedPmFunctionData.getScannerMasterState(), updatedPmFunctionData.getNeConfigurationManagerState());
        logger.info("avcPmFunctionData Data PM_ENABLED:{} ",  avcPmFunctionData.getPmFunctionEnabled());
        final Boolean pmFunctionEnabled = avcPmFunctionData.getPmFunctionEnabled();
        try {
            final Node node = nodeService.findOneByFdnInReadTx(nodeFdn);
            if (isNull(node)) {
                logger.error("Node not exists in DPS with fdn {}, so failed to process pmFunction update", nodeFdn);
                return;
            }
            final String pmFunctionMediationCapabilityValue = nodeService.getPmFunctionMediationCapability(node);
            if (!PMFUNCTION_MEDIATION_CAPABILITY_NOT_SUPPRTED.equalsIgnoreCase(pmFunctionMediationCapabilityValue)) {
                logger.debug("PMIC should manage PmFunction update sending PmFunctionUpdate MTR in case capability value is = {}",
                        pmFunctionMediationCapabilityValue);
                sendPmFunctionUpdateTaskRequest(nodeFdn, avcPmFunctionData, oldPmFunctionData);
            } else if (pmFunctionEnabled != null) {
                handlePmEnabledAttribute(node, pmFunctionEnabled, updatedPmFunctionData.getScannerMasterState() == ScannerMasterState.ENABLED);
            } else {
                handlePmFunctionEnabledChange(updatedPmFunctionData, avcPmFunctionData, node, nodeFdn);
            }
        } catch (final RuntimeDataAccessException | DataAccessException exception) {
            logger.error("Could not handle PmFunction Change for node {}", nodeFdn);
            logger.info("Could not handle PmFunction Change for node {}", nodeFdn, exception);
        }
    }

    private boolean isNull(final Node node) {
        return null == node;
    }

    private void handlePmFunctionEnabledChange(final PmFunctionData updatedPmFunctionData, final PmFunctionData avcPmFunctionData, final Node node, final String nodeFdn) {
        if (!updatedPmFunctionData.isPmFunctionEnabled()) {
            return;
        }
        final FileCollectionState fileCollectionState = avcPmFunctionData.getFileCollectionState();
        if (fileCollectionState != null) {
            fileCollectionStateUpdateHandler.handleFileCollectionStateAttribute(nodeFdn, fileCollectionState);
        }
        final ScannerMasterState scannerMasterState = avcPmFunctionData.getScannerMasterState();
        if (scannerMasterState != null) {
            handleScannerMasterStateAttribute(node, scannerMasterState);
        }
        final NeConfigurationManagerState neConfigurationManagerState = avcPmFunctionData.getNeConfigurationManagerState();
        if (neConfigurationManagerState != null) {
            handleNeConfigurationManagerState(nodeFdn, neConfigurationManagerState);
        }
    }

    private PmFunctionData getUpdatedPmFunctionData(final PmFunctionData avcPmFunctionData, final PmFunctionData oldPmFunctionData) {
        final boolean isPmEnabled = avcPmFunctionData.getPmFunctionEnabled() == null ? oldPmFunctionData.getPmFunctionEnabled() : avcPmFunctionData
                .getPmFunctionEnabled();
        final FileCollectionState fileCollectionState = avcPmFunctionData.getFileCollectionState() == null ? oldPmFunctionData
                .getFileCollectionState() : avcPmFunctionData.getFileCollectionState();
        final ScannerMasterState scannerMasterState = avcPmFunctionData.getScannerMasterState() == null ? oldPmFunctionData.getScannerMasterState()
                : avcPmFunctionData.getScannerMasterState();
        final NeConfigurationManagerState neConfigurationManagerState = avcPmFunctionData.getNeConfigurationManagerState() == null ? oldPmFunctionData
                .getNeConfigurationManagerState() : avcPmFunctionData.getNeConfigurationManagerState();
        return new PmFunctionData(isPmEnabled, fileCollectionState, scannerMasterState, neConfigurationManagerState);
    }

    private void sendPmFunctionUpdateTaskRequest(final String nodeFdn, final PmFunctionData avcPmFunctionData,
                                                 final PmFunctionData oldPmFunctionData) {
        final PmFunctionUpdateTaskRequest pmFunctionUpdateTaskRequest = new PmFunctionUpdateTaskRequest(nodeFdn, "pmfunction_mo_updated_" + nodeFdn);
        final Map<String, Object> updatedAttributes = getPmFunctionAttributesUpdatedValues(avcPmFunctionData);
        final Map<String, Object> oldAttributes = getPmFunctionAttributesOldValues(oldPmFunctionData);
        pmFunctionUpdateTaskRequest.setUpdatedAttributes(updatedAttributes);
        pmFunctionUpdateTaskRequest.setOldAttributesValues(oldAttributes);
        logger.debug("PmFunctionUpdateTaskRequest sent for node fdn: {} - with old & new attribute value: {}", nodeFdn, pmFunctionUpdateTaskRequest);
        sender.sendPmEvent(pmFunctionUpdateTaskRequest, true);
    }

    private Map<String, Object> getPmFunctionAttributesOldValues(final PmFunctionData oldPmFunctionData) {
        final Map<String, Object> pmFunctionAttributesOldValues = new HashMap<>();
        pmFunctionAttributesOldValues.put(PmFunctionConstants.PM_ENABLED, oldPmFunctionData.getPmFunctionEnabled());
        pmFunctionAttributesOldValues.put(PmFunctionConstants.FILE_COLLECTION_STATE, oldPmFunctionData.getFileCollectionState().name());
        pmFunctionAttributesOldValues.put(PmFunctionConstants.NE_CONFIGURATION_MANAGER_STATE,
                oldPmFunctionData.getNeConfigurationManagerState().name());
        pmFunctionAttributesOldValues.put(PmFunctionConstants.SCANNER_MASTER_STATE, oldPmFunctionData.getScannerMasterState().name());
        return pmFunctionAttributesOldValues;
    }


    private Map<String, Object> getPmFunctionAttributesUpdatedValues(final PmFunctionData avcPmFunctionData) {
        final Map<String, Object> pmFunctionUpdatedAttributesValues = new HashMap<>();
        if (avcPmFunctionData.getPmFunctionEnabled() != null) {
            pmFunctionUpdatedAttributesValues.put(PmFunctionConstants.PM_ENABLED, avcPmFunctionData.getPmFunctionEnabled());
        }
        if (avcPmFunctionData.getFileCollectionState() != null) {
            pmFunctionUpdatedAttributesValues.put(PmFunctionConstants.FILE_COLLECTION_STATE, avcPmFunctionData.getFileCollectionState().name());
        }
        if (avcPmFunctionData.getNeConfigurationManagerState() != null) {
            pmFunctionUpdatedAttributesValues.put(PmFunctionConstants.NE_CONFIGURATION_MANAGER_STATE,
                    avcPmFunctionData.getNeConfigurationManagerState().name());
        }
        if (avcPmFunctionData.getScannerMasterState() != null) {
            pmFunctionUpdatedAttributesValues.put(PmFunctionConstants.SCANNER_MASTER_STATE, avcPmFunctionData.getScannerMasterState().name());
        }
        return pmFunctionUpdatedAttributesValues;
    }

    private void handlePmEnabledAttribute(final Node node, final Boolean newValue, final boolean isScannerMasterStateEnabled) {
        final String nodeFdn = node.getFdn();
        try {
            systemRecorder.eventCoarse(PM_FUNCTION_DPS_NOTIFICATION, nodeFdn, "Processing PmFunction OBJECT_UPDATE notification received from DPS");
            if (newValue && isScannerMasterStateEnabled) {
                sendScannerPollingTaskUpdate(nodeFdn, PmFunctionConstants.PM_ENABLED, newValue);
                resPmFunctionHelper.handlePmFunctionEnabled(node);
            } else {
                if (PmFunctionUtil.PmFunctionPropertyValue.PM_FUNCTION_LEGACY == PmFunctionUtil.getPmFunctionConfig()) {
                    final Set<Long> subscriptionsToUpdateErrorNodeCache = suspendOrDeleteScannersForNode(nodeFdn);
                    final Set<Long> subscriptionsWithDeactivatedJobs = deactivatePmJobsForNode(nodeFdn);
                    subscriptionsToUpdateErrorNodeCache.addAll(subscriptionsWithDeactivatedJobs);
                    updatePmFunctionOffErrorNodeCache(nodeFdn, subscriptionsToUpdateErrorNodeCache);
                } else {
                    final Set<Long> subscriptionsWithDeactivatedScanners = deactivateScannersForNode(node);
                    final Set<Long> subscriptionsWithDeactivatedJobs = deactivatePmJobsForNode(nodeFdn);
                    logger.debug("Updated subscriptions {}", subscriptionsWithDeactivatedScanners.addAll(subscriptionsWithDeactivatedJobs));
                    updatePmFunctionOffErrorNodeCache(nodeFdn, subscriptionsWithDeactivatedScanners);
                }
            }
        } catch (final DataAccessException | RuntimeDataAccessException | RetryServiceException exception) {
            logger.error("Could not process cache error entry for {}", nodeFdn);
            logger.info("Could not process cache error entry for {}", nodeFdn, exception);
        }
    }

    private void handleScannerMasterStateAttribute(final Node node, final ScannerMasterState newValue) {
        if (nodeService.isScannerMasterSupported(node)) {
            if (nodeService.isMediationAutonomyEnabled(node)) {
                updateScannerMasterBehaviourForMediationAutonomy(node.getFdn(), newValue);
            } else if (ScannerMasterState.ENABLED == newValue) {
                sendScannerPollingTaskUpdate(node.getFdn(), PmFunctionConstants.SCANNER_MASTER_STATE, newValue);
            }
        }
    }

    private void updateScannerMasterBehaviourForMediationAutonomy(final String nodeFdn, final ScannerMasterState scannerMasterState) {
        MediationTaskRequest task = null;
        if (ScannerMasterState.ENABLED == scannerMasterState) {
            logger.info("Starting scanner polling on node {} with Mediation Autonomy", nodeFdn);
            task = pmConfigTaskRequestFactory.createScannerMasterConfigTask(nodeFdn, true);
        } else if (ScannerMasterState.DISABLED == scannerMasterState) {
            logger.info("Stopping scanner polling on node {} with Mediation Autonomy", nodeFdn);
            task = pmConfigTaskRequestFactory.createScannerMasterConfigTask(nodeFdn, false);
        }
        if (task != null) {
            sender.sendPmEvent(task, true);
        }
    }

    private void handleNeConfigurationManagerState(final String nodeFdn, final NeConfigurationManagerState newValue) {
        if (NeConfigurationManagerState.ENABLED == newValue) {
            sendScannerPollingTaskUpdate(nodeFdn, PmFunctionConstants.NE_CONFIGURATION_MANAGER_STATE, newValue);
        }
    }

    private boolean isMigrationGoingOn() {
        return configurationChangeListener.getPmMigrationEnabled();
    }

    private Set<Long> suspendOrDeleteScannersForNode(final String nodeFdn) throws DataAccessException {
        final Set<Long> subcriptionsForSuspendedOrDeletedScannersOnNode = new HashSet<>();
        final List<Scanner> scanners = scannerService.findAllByNodeFdnAndSubscriptionIdAndScannerStatusInReadTx(Collections.singleton(nodeFdn), null,
                ScannerStatus.ACTIVE, ScannerStatus.ERROR, ScannerStatus.UNKNOWN);
        for (final Scanner scanner : scanners) {
            updateDpsScannerAndSuspendOrDeleteNodeScanner(scanner);
            if (scanner.hasAssignedSubscriptionId()) {
                subcriptionsForSuspendedOrDeletedScannersOnNode.add(scanner.getSubscriptionId());
            } else if (ebsSubscriptionHelper.isEbsScanner(scanner)) {
                deleteAllSubScannersWhenPmFunctionDisabled(scanner, subcriptionsForSuspendedOrDeletedScannersOnNode);
            }
        }
        return subcriptionsForSuspendedOrDeletedScannersOnNode;
    }

    private Set<Long> deactivateScannersForNode(final Node node)
            throws DataAccessException, RetryServiceException {
        final List<Scanner> scanners = scannerService.findAllByNodeFdnAndSubscriptionIdAndScannerStatusInReadTx(Collections.singleton(node.getFdn()), null,
                ScannerStatus.ACTIVE, ScannerStatus.ERROR, ScannerStatus.UNKNOWN);
        final Set<Long> subscriptionsToBeAddedInErrorCache = new HashSet<>();
        final Set<Long> subscriptionsToDeactivate = suspendSystemDefinedScannersAndFindUserDefSubscriptionsToBeDeactivated(scanners,
                subscriptionsToBeAddedInErrorCache);
        final Set<Long> deactivatedSubcriptionsOnNode = deactivateUserDefSubscriptions(node, subscriptionsToDeactivate);
        logger.info("Number of deactivated subscriptions: {} - Number of subscription with suspended scanners: {}",
                deactivatedSubcriptionsOnNode.size(), subscriptionsToBeAddedInErrorCache.size());
        subscriptionsToBeAddedInErrorCache.addAll(deactivatedSubcriptionsOnNode);
        return subscriptionsToBeAddedInErrorCache;
    }

    private Set<Long> suspendSystemDefinedScannersAndFindUserDefSubscriptionsToBeDeactivated(final List<Scanner> scanners,
                                                                                             final Set<Long> subscriptionsToBeAddedInErrorCache) throws DataAccessException {
        final Set<Long> subscriptionsToDeactivate = new HashSet<>();
        for (final Scanner scanner : scanners) {
            final Long subId = scanner.getSubscriptionId();
            if (!isMigrationGoingOn()) {
                logger.debug("SubscriptionId: {} found for Scanner {} ", subId, scanner.getFdn());
                if (scanner.isEnmUserDefinedScanner()) {
                    subscriptionsToDeactivate.add(subId);
                } else if (Scanner.isValidSubscriptionId(subId) || ScannerType.HIGH_PRIORITY_CELLTRACE_10004 == scanner.getScannerType()) {
                    scannerOperation.suspendScannerOnTheNodeRegardlessOfPmFunction(scanner.getFdn(), scanner.getPoId());
                    deleteAllSubScannersWhenPmFunctionDisabled(scanner, subscriptionsToBeAddedInErrorCache);
                }
            }
        }
        return subscriptionsToDeactivate;
    }

    private void deleteAllSubScannersWhenPmFunctionDisabled(final Scanner scanner, final Set<Long> subscriptionIds) throws DataAccessException {
        final List<PmSubScanner> subScanners = pmSubScannerService.findAllByParentScannerFdnInReadTx(scanner.getFdn());
        for (PmSubScanner subScanner : subScanners) {
            pmSubScannerService.deleteByFdn(subScanner.getFdn());
            subscriptionIds.add(subScanner.getSubscriptionId());
        }
    }

    private Set<Long> deactivateUserDefSubscriptions(final Node node, final Set<Long> subscriptionsToDeactivate)
            throws RetryServiceException, DataAccessException {
        final Set<Long> deactivatedSubcriptionsOnNode = new HashSet<>();
        for (final Long subscriptionId : subscriptionsToDeactivate) {
            final Subscription subscription = subscriptionReadOperationService.findByIdWithRetry(subscriptionId, false);
            if (subscription == null) {
                logger.error("[Scanner] - Cannot deactivate node {} for subscription {}. No such subscription exists in DPS", node.getFdn(),
                        subscriptionId);
                continue;
            }
            if (isUserDefinedSubscription(subscription) && subscription instanceof ResourceSubscription) {
                logger.info("Deactivating scanners for subscription {}", subscription.getFdn());
                deactivationEvent.execute(new ArrayList<Node>(Arrays.asList(node)), subscription);
                deactivatedSubcriptionsOnNode.add(subscriptionId);
            }
        }
        return deactivatedSubcriptionsOnNode;
    }

    /**
     * Checks if the subscription is a user defined statistical subscription
     *
     * @param subscription
     *         - the subscription
     *
     * @return true/false
     */
    private boolean isUserDefinedSubscription(final Subscription subscription) {
        return UserType.USER_DEF.equals(subscription.getUserType()) && !EbsSubscriptionHelper.isASR(subscription);
    }

    private void updateDpsScannerAndSuspendOrDeleteNodeScanner(final Scanner scanner) throws DataAccessException {
        modifyDpsScannerStatus(scanner);
        if (isMigrationGoingOn()) {
            logger.info("Migration is ON. Scanner Suspension or Deletion will not be executed for scanner. {} ", scanner.getFdn());
        } else {
            suspendOrDeleteNodeScanner(scanner);
        }
    }

    private void suspendOrDeleteNodeScanner(final Scanner scanner) {
        if (scanner.isEnmUserDefinedScanner()) {
            scannerOperation.deleteScannerFromTheNodeRegardlessOfPmFunction(scanner.getFdn(), scanner.getId());
        } else if (ScannerType.HIGH_PRIORITY_CELLTRACE_10004 == scanner.getScannerType()
                || Scanner.isValidSubscriptionId(scanner.getSubscriptionId())) {
            scannerOperation.suspendScannerOnTheNodeRegardlessOfPmFunction(scanner.getFdn(), scanner.getPoId());
        }
    }

    private void modifyDpsScannerStatus(final Scanner scanner) throws DataAccessException {
        if (scanner.isEnmUserDefinedScanner() && scanner.getStatus() != ScannerStatus.UNKNOWN) {
            scanner.setStatus(ScannerStatus.UNKNOWN);
            scannerService.saveOrUpdate(scanner);
        }
    }

    private Set<Long> deactivatePmJobsForNode(final String nodeFdn)
            throws DataAccessException, RetryServiceException {
        final Set<Long> subcriptionsForDeactivatedJobOnNode = new HashSet<>();
        final Node node = nodeService.findOneByFdnInReadTx(nodeFdn);
        if (isNull(node)) {
            throw new NodeNotFoundDataAccessException("Node with fdn [" + nodeFdn + "] does not exist.");
        }
        final List<PmJob> pmJobs = pmJobService.findAllbyNodeFdnAndPmJobStatus(Collections.singleton(nodeFdn), PmJobStatus.ACTIVE);
        logger.debug("pmJobs {} found on node {} ", pmJobs, nodeFdn);
        for (final PmJob pmJob : pmJobs) {
            final Subscription subscription = subscriptionReadOperationService.findByIdWithRetry(pmJob.getSubscriptionId(), false);
            if (subscription == null) {
                logger.error("[pmJob] - Cannot deactivate node {} for subscription {}. No such subscription exists in DPS", node.getFdn(),
                        pmJob.getSubscriptionId());
                continue;
            }
            if (isMigrationGoingOn()) {
                logger.info("Migration is ON. Deactivation request will not be send for node {} of subscription {} ", nodeFdn, subscription.getFdn());
            } else {
                deactivationEvent.execute(Collections.singletonList(node), subscription);
            }
            subcriptionsForDeactivatedJobOnNode.add(pmJob.getSubscriptionId());
        }
        return subcriptionsForDeactivatedJobOnNode;
    }

    private void updatePmFunctionOffErrorNodeCache(final String nodeFdn, final Set<Long> subcriptionsId) {
        for (final Long subscriptionId : subcriptionsId) {
            logger.info("Added nodeFdn {} in error entry as pmFunction Off", nodeFdn);
            pmFunctionOffErrorNodeCache.addNodeWithPmFunctionOff(nodeFdn, subscriptionId);
        }
    }

    private void sendScannerPollingTaskUpdate(final String nodeFdn, final Object attributeName, final Object newValue) {
        final String message = getMessage(nodeFdn, attributeName, newValue);
        systemRecorder.eventCoarse(PM_FUNCTION_DPS_NOTIFICATION, nodeFdn, message);
        scannerPollingTaskSender.sendScannerPollingTaskForNode(nodeFdn);
    }

    private String getMessage(final Object fdn, final Object attribute, final Object newValue) {
        return format("PmFunction OBJECT_UPDATE notification received from DPS for PmFunctionFDN : %s, AttributeName : %s, updated to %s ", fdn,
                attribute, newValue);
    }
}
