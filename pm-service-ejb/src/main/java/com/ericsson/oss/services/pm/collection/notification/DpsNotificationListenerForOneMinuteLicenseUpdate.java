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

package com.ericsson.oss.services.pm.collection.notification;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.GPEH_ONE_MINUTE_LICENSE_UPDATE_EVENT;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.ejb.*;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent;
import com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus;
import com.ericsson.oss.pmic.dto.scanner.Scanner;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.pmic.dto.scanner.enums.ScannerStatus;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.common.notification.EventHandler;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionManager;
import com.ericsson.oss.services.pm.initiation.utils.FdnUtil;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionException;

/**
 * This is the entry point for DPS listener for handling changes in rnc feature state attribute
 */
@Startup
@Singleton
@Lock(LockType.READ)
public class DpsNotificationListenerForOneMinuteLicenseUpdate implements EventHandler<DpsAttributeChangedEvent> {

    private static final String RNC_FEATURE = "RncFeature";
    private static final String GPEH_CAP_INCR_RED_ROP_PER = "GpehCapIncrRedRopPer";
    private static final String FEATURE_STATE = "featureState";

    @Inject
    private Logger logger;
    @Inject
    private FdnUtil fdnUtil;
    @Inject
    private ScannerService scannerService;
    @Inject
    private MembershipListener membershipListener;
    @Inject
    private SubscriptionManager subscriptionManager;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private PmicDpsAvailabilityStatus dpsAvailabilityStatus;

    @Override
    public void onEvent(final DpsAttributeChangedEvent event) {
        final String nodeFdn = fdnUtil.getNodeFdn(event.getFdn());
        if (nodeFdn == null) {
            logger.error("Cannot process event with fdn: {}. Can't extract NetworkElement or MeContext or SubNetwork.", event.getFdn());
            return;
        }

        logger.debug("Received dps notification for One Minute License update, FDN: {}, Event data: {}", nodeFdn, event);
        final Set<AttributeChangeData> changedAttributes = event.getChangedAttributes();

        if (event.getFdn().contains(GPEH_CAP_INCR_RED_ROP_PER) && event.getFdn().contains(RNC_FEATURE)) {
            logger.debug("RncFeature - GpehCapIncrRedRopPer state has been updated for node {}.", nodeFdn);
            for (final AttributeChangeData changedData : changedAttributes) {

                final String attributeName = changedData.getName();
                final String oldValue = getStringFromChangedData(changedData.getOldValue());
                final String newValue = getStringFromChangedData(changedData.getNewValue());

                logger.debug("Attribute change details: Name - {}, Old Value - {}, New Value - {}", attributeName, oldValue, newValue);

                if (FEATURE_STATE.equals(attributeName)) {
                    systemRecorder.eventCoarse(GPEH_ONE_MINUTE_LICENSE_UPDATE_EVENT, nodeFdn,
                            "RncFeature for GPEH: Node license update notification received from DPS.");
                    logger.debug("Updating ROP period of active scanners on the node: {}", nodeFdn);
                    updateRopPeriodForActiveScanners(nodeFdn, "ACTIVATED".equals(newValue));
                    break;
                }
            }
        }
    }

    private void updateRopPeriodForActiveScanners(final String nodeFdn, final boolean featureStateActive) {
        if (!dpsAvailabilityStatus.isAvailable()) {
            logger.warn("Failed to update rop periods for active scanner {}, Dps not available", nodeFdn);
            return;
        }
        try {
            logger.debug("Retrieving scanners node:: {} for process type:: {}", nodeFdn, ProcessType.REGULAR_GPEH);
            final List<Scanner> availableScannersOnNode = scannerService
                    .findAllByNodeFdnAndSubscriptionIdAndProcessTypeInReadTx(Collections.singleton(nodeFdn), null, ProcessType.REGULAR_GPEH);
            logger.debug("RncFeature state: {}. Applying ROP period to scanners accordingly", featureStateActive);
            for (final Scanner scanner : availableScannersOnNode) {
                logger.debug("Verifying Scanner: {}", scanner.getName());
                if (ScannerStatus.ACTIVE == scanner.getStatus() && Scanner.isValidSubscriptionId(scanner.getSubscriptionId())) {
                    int ropPeriodToUpdate = 60;
                    if (!featureStateActive) {
                        final Subscription subscription = subscriptionManager.getSubscriptionWrapperById(scanner.getSubscriptionId())
                                .getSubscription();
                        ropPeriodToUpdate = subscription.getRop().getDurationInSeconds();
                    }
                    logger.debug("Updating Scanner: {} having old ROP Value: {} with new ROP Value: {}", scanner.getName(), scanner.getRopPeriod(),
                            ropPeriodToUpdate);
                    scanner.setRopPeriod(ropPeriodToUpdate);
                    scannerService.saveOrUpdate(scanner);
                }
            }
        } catch (final DataAccessException | RuntimeDataAccessException e) {
            logger.error("Unable to connect to DPS to fetch data. Error Message: {}", e.getMessage());
        } catch (final RetryServiceException | InvalidSubscriptionException pmicInvalidInputException) {
            logger.error("Invalid input. Error message: {}", pmicInvalidInputException.getMessage());
        }
    }

    private String getStringFromChangedData(final Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        return "";
    }

    @Override
    public boolean isInterested(final DpsAttributeChangedEvent event) {
        return event.getType().equals(RNC_FEATURE) && membershipListener.isMaster();
    }

    @Override
    public Class<DpsAttributeChangedEvent> getEventClass() {
        return DpsAttributeChangedEvent.class;
    }
}
