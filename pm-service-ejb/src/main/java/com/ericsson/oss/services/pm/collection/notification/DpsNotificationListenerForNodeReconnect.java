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

package com.ericsson.oss.services.pm.collection.notification;

import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.DEFAULT_FILE_RECOVERY_HOURS;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.NODE_RECONNECT_FILE_COLLECTION_RECOVERY;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.CM_FUNCTION_MODEL_NAME;
import static com.ericsson.oss.services.pm.initiation.utils.CommonUtil.isStringNullOrEmpty;
import static com.ericsson.oss.services.pm.model.PMCapability.SupportedRecoveryTypes.RECOVERY_ON_NODE_RECONNECT;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent;
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.impl.handler.InvokeInTransaction;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import com.ericsson.oss.services.pm.collection.FileCollectionForLostSyncTime;
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper;
import com.ericsson.oss.services.pm.collection.recovery.ScheduledRecovery;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.collection.task.factories.StatisticalRecoveryTaskRequestFactory;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.common.notification.EventHandler;
import com.ericsson.oss.services.pm.eventSender.PmEventSender;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.util.RopTime;
import com.ericsson.oss.services.pm.initiation.utils.FdnUtil;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * This is the entry point for DPS listener for handling changes in lostSynchronization attribute
 */
@Startup
@Singleton
@Lock(LockType.READ)
public class DpsNotificationListenerForNodeReconnect implements EventHandler<DpsAttributeChangedEvent> {
    private static final Logger logger = LoggerFactory.getLogger(DpsNotificationListenerForNodeReconnect.class);

    private static final String LOST_SYNCHRONIZATION = "lostSynchronization";
    private static final String TIME_FORMAT = ".*\\d{4}$";
    private static final String SIMPLE_DATE_FORMATTER = "EEE MMM dd HH:mm:ss z yyyy";
    private static final long DURATION_FOR_SCHEDULED_RECOVERY = 60L * 60 * 1000;
    private static final long MIN_NODE_RECONNECT_DURATION = 30L * 60 * 1000;

    @Inject
    private FdnUtil fdnUtil;
    @Inject
    private PmEventSender sender;
    @Inject
    private NodeService nodeService;
    @Inject
    private ScannerService scannerService;
    @Inject
    private ScheduledRecovery scheduledRecovery;
    @Inject
    private MembershipListener membershipListener;
    @Inject
    private SupportedRopTimes supportedRopTimesBean;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;
    @Inject
    private ConfigurationChangeListener configurationChangeListener;
    @Inject
    private FileCollectionForLostSyncTime fileCollectionForLostSyncTime;
    @Inject
    private FileCollectionActiveTaskCacheWrapper fileCollectionActiveTasksCache;
    @Inject
    private StatisticalRecoveryTaskRequestFactory statisticalRecoveryTaskRequestFactory;

    @Override
    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void onEvent(final DpsAttributeChangedEvent event) {
        final String networkElementFdn = fdnUtil.getRootParentFdnFromChild(event.getFdn());
        logger.info("Received dps notification for Node Reconnect, FDN {}, event {}", networkElementFdn, event);
        if (!nodeService.isRecoveryTypeSupported(networkElementFdn, RECOVERY_ON_NODE_RECONNECT.name())) {
            logger.debug("Ignored Node Reconnect sync as Node Reconnect Recovery is not supported for FDN {}, event {}", networkElementFdn, event);
            return;
        }
        if (!nodeService.isPmFunctionEnabled(networkElementFdn)) {
            logger.debug("Ignored Node Reconnect sync as pmFunction is disabled for FDN {}, event {}", networkElementFdn, event);
            return;
        }
        final Set<AttributeChangeData> changedAttributes = event.getChangedAttributes();
        for (final AttributeChangeData changedData : changedAttributes) {
            if (processChangedData(changedData, networkElementFdn)) {
                break;
            }
        }
    }

    private boolean processChangedData(final AttributeChangeData changedData, final String networkElementFdn) {
        final String attributeName = changedData.getName();
        final String oldValue = getStringFromChangedData(changedData.getOldValue());
        final String newValue = getStringFromChangedData(changedData.getNewValue());
        logger.debug("The changed attribute : Name - {}, Old Value - {}, New Value - {}", attributeName, oldValue, newValue);
        if (!LOST_SYNCHRONIZATION.equals(attributeName) || !isStringNullOrEmpty(newValue) || !"".equals(newValue)) {
            return false;
        }
        final boolean isConnectionRestored = Pattern.matches(TIME_FORMAT, oldValue);
        if (isConnectionRestored) {
            systemRecorder.eventCoarse(NODE_RECONNECT_FILE_COLLECTION_RECOVERY, networkElementFdn,
                    "Node reconnection notification received from DPS.");
            try {
                final DateFormat formatter = new SimpleDateFormat(SIMPLE_DATE_FORMATTER);
                final Date lostSyncDateTime = formatter.parse(oldValue);
                final int ropRecoveryPeriodInHours = scheduledRecovery.getFileRecoveryHoursInfo();
                logger.debug("Node reconnection notification received from DPS for fdn {}. Recover since: {}.", networkElementFdn,
                        lostSyncDateTime);
                if (isValidForNodeReconnectRecovery(networkElementFdn, lostSyncDateTime, ropRecoveryPeriodInHours)) {
                    processNodeReconnect(networkElementFdn, lostSyncDateTime, ropRecoveryPeriodInHours);
                } else {
                    logger.debug("Ignored Node Reconnect recovery for FDN {}", networkElementFdn);
                }
            } catch (final ParseException exception) {
                logger.error("Node reconnection Exception. lostSynchronization attribute is not in expected time stamp format", exception);
            }
        }
        return true;
    }

    private boolean isValidForNodeReconnectRecovery(final String networkElementFdn, final Date lostSyncDateTime, final int ropRecoveryPeriodInHours) {
        boolean triggerNodeReconnectRecovery = false;
        if (System.currentTimeMillis() - lostSyncDateTime.getTime() >= MIN_NODE_RECONNECT_DURATION) {
            triggerNodeReconnectRecovery = true;
            logger.debug("Node Reconnect lostSyncDateTime {} duration is more than 30 minutes for fdn {}", lostSyncDateTime, networkElementFdn);
        }
        final Calendar scheduledRecoveryTime = scheduledRecovery.getScheduledRecoveryCalendar();
        if (null == scheduledRecoveryTime) {
            return triggerNodeReconnectRecovery;
        }
        if (ropRecoveryPeriodInHours == Integer.valueOf(DEFAULT_FILE_RECOVERY_HOURS) && new Date().before(scheduledRecoveryTime.getTime())
                && scheduledRecoveryTime.getTime().getTime() - System.currentTimeMillis() <= DURATION_FOR_SCHEDULED_RECOVERY) {
            triggerNodeReconnectRecovery = false;
            logger.debug("Scheduled Recovery is scheduled at {} for fdn {}", scheduledRecoveryTime, networkElementFdn);
        }
        return triggerNodeReconnectRecovery;
    }

    private void processNodeReconnect(final String nodeFdn, final Date lostSyncDateTime, final int ropRecoveryPeriodInHours) {
        final Set<Integer> ropPeriodInSecondsSet = getValidRopPeriods(nodeFdn);
        for (final int ropPeriodInSeconds : ropPeriodInSecondsSet) {
            final int totalRopsToCollect = fileCollectionForLostSyncTime.getTotalRopsToCollect(lostSyncDateTime, ropPeriodInSeconds,
                    ropRecoveryPeriodInHours);
            if (totalRopsToCollect == 0) {
                logger.warn("Node {} does not satisfy any valid rop. lostSyncDateTime {}, total Rops calculated after sync failed {}, "
                        + "ropRecoveryPeriodInHours {}.", nodeFdn, lostSyncDateTime, totalRopsToCollect, ropRecoveryPeriodInHours);
                continue;
            }
            /**
             * New algorithm generates ropStart subtracting ropTimeInfo.getRopTimeInMilliSecond() (i.e. ROP duration in milliseconds) from
             * System.currentTimeMillis() that is the synch time. This is due to the fact that if the current ROP has to be recovered it will be
             * recovered by singleRopRecovery, thus avoiding cases in which recovery get the last ROP file before File Collection trigger occurs,
             * causing File Collection to fail (FILE is ALREADY PRESENT error).
             */
            final RopTime ropStart = new RopTime(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(ropPeriodInSeconds), ropPeriodInSeconds);
            final MediationTaskRequest nodeReconnectRecoveryFileCollectionTaskRequest =
                    statisticalRecoveryTaskRequestFactory.createFileCollectionRecoveryOnNodeReconnectTaskRequest(nodeFdn, ropStart.getTime(),
                            TimeUnit.SECONDS.toMillis(ropPeriodInSeconds), totalRopsToCollect);
            sender.sendTasksRegardlessOfPmFunctionState(Arrays.asList(nodeReconnectRecoveryFileCollectionTaskRequest));
            systemRecorder.eventCoarse(NODE_RECONNECT_FILE_COLLECTION_RECOVERY, nodeFdn,
                    "Successfully Node Reconnect File Collection Recovery task sent for " + nodeFdn);
        }
    }

    private Set<Integer> getValidRopPeriods(final String networkElementFdn) {
        final Set<ProcessRequestVO> processRequests = fileCollectionActiveTasksCache.getProcessRequestForRop(networkElementFdn);
        if (!processRequests.isEmpty()) {
            return getRopPeriodsFromProcessRequests(processRequests);
        }
        if (!dpsAvailabilityStatus.isAvailable()) {
            logger.warn("Failed to sent NodeReconnect for {}, Dps not available", networkElementFdn);
            return Collections.emptySet();
        }
        final ProcessType[] processTypes = { ProcessType.STATS };
        final ScannerStatus[] scannerStatuses = { ScannerStatus.ACTIVE };
        try {
            final List<Scanner> activeScanners = scannerService.findAllByNodeFdnAndProcessTypeAndRopDurationAndScannerStatusAndFileCollection(
                    Collections.singleton(networkElementFdn), processTypes, null, scannerStatuses, true);
            if (activeScanners == null || activeScanners.isEmpty()) {
                logger.info("No scanner found with status ACTIVE for node {}", networkElementFdn);
                return Collections.emptySet();
            }
            return getRopPeriodsFromScanners(activeScanners);
        } catch (final DataAccessException e) {
            logger.error("Unable to recover files for NetworkElement {} with error : {}", networkElementFdn, e.getMessage());
            logger.info("Exception details", e);
            return Collections.emptySet();
        }
    }

    private Set<Integer> getRopPeriodsFromProcessRequests(final Set<ProcessRequestVO> processRequests) {
        final Set<Integer> rops = new HashSet<>();
        for (final ProcessRequestVO processRequestVO : processRequests) {
            if (processRequestVO.getProcessType().equals(ProcessType.STATS.name())
                    && supportedRopTimesBean.isRopSupported(Long.valueOf(processRequestVO.getRopPeriod()))) {
                rops.add(processRequestVO.getRopPeriod());
            }
        }
        return rops;
    }

    private Set<Integer> getRopPeriodsFromScanners(final List<Scanner> activeScanners) {
        final Set<Integer> rops = new HashSet<>();
        for (final Scanner scanner : activeScanners) {
            if (Scanner.isValidSubscriptionId(scanner.getSubscriptionId())
                    && supportedRopTimesBean.isRopSupported(Long.valueOf(scanner.getRopPeriod()))) {
                rops.add(scanner.getRopPeriod());
            }
        }
        return rops;
    }

    private String getStringFromChangedData(final Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        return "";
    }

    @Override
    public boolean isInterested(final DpsAttributeChangedEvent event) {
        return CM_FUNCTION_MODEL_NAME.equals(event.getType()) && membershipListener.isMaster();
    }

    @Override
    public Class<DpsAttributeChangedEvent> getEventClass() {
        return DpsAttributeChangedEvent.class;
    }
}
