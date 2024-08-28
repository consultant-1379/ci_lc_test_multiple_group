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

import static java.lang.String.format;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.SCANNER_DPS_NOTIFICATION;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_FILE_COLLECTION_ENABLED_ATTRIBUTE;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_MODEL_NAME;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_MTR_MODEL_NAME;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_ROP_PERIOD_ATTRIBUTE;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_STATUS_ATTRIBUTE;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_UE_MODEL_NAME;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.UNKNOWN_SUBSCRIPTION_ID;

import java.util.Set;
import javax.ejb.*;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent;
import com.ericsson.oss.services.pm.collection.notification.handlers.PMICScannerInfoUpdateAttributeVO;
import com.ericsson.oss.services.pm.collection.notification.handlers.ScannerUpdateOperationHandler;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.common.notification.EventHandler;
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * This class listens for the Data Persistence Service notifications for update of PMICScannerInfo MO.
 */
@Startup
@Singleton
@Lock(LockType.READ)
public class DpsScannerUpdateNotificationListener implements EventHandler<DpsAttributeChangedEvent> {

    @Inject
    private Logger logger;
    @Inject
    private MembershipListener membershipListener;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private ScannerUpdateOperationHandler scannerUpdateHandler;
    @Inject
    private ConfigurationChangeListener configurationChangeListener;

    /**
     * This method processes PMICScannerInfo UPDATE notification and triggers below actions : 1) check if the scannerStatus is updated 2) Get
     * scannerMO for the given fdn 3) Update task status of subscription
     *
     * @param event
     */
    @Override
    public void onEvent(final DpsAttributeChangedEvent event) {

        final String scannerFdn = event.getFdn();
        final Set<AttributeChangeData> changedAttributes = event.getChangedAttributes();
        PMICScannerInfoUpdateAttributeVO updateAttributes = new PMICScannerInfoUpdateAttributeVO();

        String oldSubscriptionId = null;
        String newSubscriptionId = null;
        logger.debug("Processing scanner update notification with event: {}", event);
        for (final AttributeChangeData changedData : changedAttributes) {
            if (SCANNER_STATUS_ATTRIBUTE.equals(changedData.getName())) {
                processScannerStatusChange(changedData, updateAttributes, scannerFdn);
            } else if (SCANNER_FILE_COLLECTION_ENABLED_ATTRIBUTE.equals(changedData.getName())) {
                processScannerFileCollectionEnabledChange(changedData, updateAttributes, scannerFdn);
            } else if (SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE.equals(changedData.getName())) {
                processScannerSubscriptionIDChange(changedData, updateAttributes, scannerFdn);
                oldSubscriptionId = (String) changedData.getOldValue();
                newSubscriptionId = (String) changedData.getNewValue();
            } else if (SCANNER_ROP_PERIOD_ATTRIBUTE.equals(changedData.getName())) {
                processScannerROPPeriodChange(changedData, updateAttributes, scannerFdn);
            } else {
                logger.info(getMessage(scannerFdn, changedData.getName(), changedData.getOldValue(), changedData.getNewValue()));
            }
        }

        if (isToExecute(updateAttributes, oldSubscriptionId, newSubscriptionId)) {
            scannerUpdateHandler.execute(scannerFdn, updateAttributes);
        } else {
            logger.debug("Not processing PMICScannerInfo OBJECT_UPDATE notification for {} as "
                    + "status and fileCollectionEnabled attribute is not updated", scannerFdn);
        }
    }

    private void processScannerStatusChange(final AttributeChangeData changedData,
                                                                        final PMICScannerInfoUpdateAttributeVO updateAttributes,
                                                                        final String scannerFdn) {
        updateAttributes.setStatusAttributeUpdated(true);
        updateAttributes.setNewStatusValue((String) changedData.getNewValue());
        updateAttributes.setOldStatusValue((String) changedData.getOldValue());
        final String message = getMessage(scannerFdn, changedData.getName(), changedData.getOldValue(), changedData.getNewValue());
        systemRecorder.eventCoarse(SCANNER_DPS_NOTIFICATION, scannerFdn, message);
    }

    private void processScannerFileCollectionEnabledChange(final AttributeChangeData changedData,
                                                                                       final PMICScannerInfoUpdateAttributeVO updateAttributes,
                                                                                       final String scannerFdn) {
        updateAttributes.setFileCollectionEnabledAttributeUpdated(true);
        updateAttributes.setFileCollectionEnabledNewValue((boolean) changedData.getNewValue());
        final String message = getMessage(scannerFdn, changedData.getName(), changedData.getOldValue(), changedData.getNewValue());
        systemRecorder.eventCoarse(SCANNER_DPS_NOTIFICATION, scannerFdn, message);
    }

    private void processScannerSubscriptionIDChange(final AttributeChangeData changedData,
                                                                                final PMICScannerInfoUpdateAttributeVO updateAttributes,
                                                                                final String scannerFdn) {
        updateAttributes.setSubscriptionIdUpdated(true);
        final String message = getMessage(scannerFdn, changedData.getName(), changedData.getOldValue(), changedData.getNewValue());
        systemRecorder.eventCoarse(SCANNER_DPS_NOTIFICATION, scannerFdn, message);
    }

    private void processScannerROPPeriodChange(final AttributeChangeData changedData,
                                                                           final PMICScannerInfoUpdateAttributeVO updateAttributes,
                                                                           final String scannerFdn) {
        updateAttributes.setRopPeriodUpdated(true);
        updateAttributes.setNewRopPeriodValue((Integer) changedData.getNewValue());
        updateAttributes.setOldRopPeriodValue((Integer) changedData.getOldValue());
        final String message = getMessage(scannerFdn, changedData.getName(), changedData.getOldValue(), changedData.getNewValue());
        systemRecorder.eventCoarse(SCANNER_DPS_NOTIFICATION, scannerFdn, message);
    }

    private String getMessage(final Object scannerFdn, final Object attribute, final Object oldValue, final Object newValue) {
        return format(
                "PMICScannerInfo update notification for ScannerFdn : %s, AttributeName : %s, updated from %s to %s ",
                scannerFdn, attribute, oldValue, newValue);
    }

    private boolean isToExecute(final PMICScannerInfoUpdateAttributeVO updateAttributes, final String oldSubscriptionId,
                                final String newSubscriptionId) {
        return (updateAttributes.isFileCollectionEnabledAttributeUpdated() || updateAttributes.isStatusAttributeUpdated())
                || (configurationChangeListener.getPmMigrationEnabled() && updateAttributes.isSubscriptionIdUpdated())
                || (updateAttributes.isSubscriptionIdUpdated() && UNKNOWN_SUBSCRIPTION_ID.equals(oldSubscriptionId))
                || (updateAttributes.isSubscriptionIdUpdated() && UNKNOWN_SUBSCRIPTION_ID.equals(newSubscriptionId))
                || (updateAttributes.isRopPeriodUpdated());
    }

    @Override
    public boolean isInterested(final DpsAttributeChangedEvent event) {
        return (SCANNER_MODEL_NAME.equals(event.getType()) || SCANNER_UE_MODEL_NAME.equals(event.getType()) ||
                SCANNER_MTR_MODEL_NAME.equals(event.getType())) && membershipListener.isMaster();
    }

    @Override
    public Class<DpsAttributeChangedEvent> getEventClass() {
        return DpsAttributeChangedEvent.class;
    }
}
