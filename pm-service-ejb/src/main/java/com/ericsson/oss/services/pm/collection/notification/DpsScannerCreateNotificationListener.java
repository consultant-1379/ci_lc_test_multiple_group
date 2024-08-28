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

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.SCANNER_DPS_NOTIFICATION;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.PROCESS_TYPE_ATTRIBUTE;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_FILE_COLLECTION_ENABLED_ATTRIBUTE;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_MODEL_NAME;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_MTR_MODEL_NAME;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_ROP_PERIOD_ATTRIBUTE;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_STATUS_ATTRIBUTE;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_UE_MODEL_NAME;

import javax.ejb.*;
import javax.inject.Inject;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectCreatedEvent;
import com.ericsson.oss.pmic.impl.handler.InvokeInTransaction;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import com.ericsson.oss.services.pm.collection.notification.handlers.ScannerCreateOperationHandler;
import com.ericsson.oss.services.pm.collection.notification.handlers.ScannerOperationVO;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.common.notification.EventHandler;

/**
 * This class listens for the Data Persistence Service notifications for creation of PMICScannerInfo MO.
 */
@Startup
@Singleton
@Lock(LockType.READ)
public class DpsScannerCreateNotificationListener implements EventHandler<DpsObjectCreatedEvent> {

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private ScannerCreateOperationHandler scannerCreateHandler;

    /**
     * This method processes PMICScannerInfo CREATE notification and triggers below actions : 1) Creates FileCollectionTaskManager timer to create
     * FileCollection tasks. 2) Creates FileCollectionTaskSender timer to send FileCollection tasks to mediation. 2) If timer exists already add node
     * fdn
     * to file collection list to collect files from the node this scanner is created on.
     *
     * @param event
     */
    @Override
    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void onEvent(final DpsObjectCreatedEvent event) {
        final String scannerFdn = event.getFdn();
        final String scannerStatus = (String) event.getAttributeValues().get(SCANNER_STATUS_ATTRIBUTE);
        final String subscriptionId = (String) event.getAttributeValues().get(SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE);
        final String processType = (String) event.getAttributeValues().get(PROCESS_TYPE_ATTRIBUTE);
        final Integer ropPeriodInSeconds = (Integer) event.getAttributeValues().get(SCANNER_ROP_PERIOD_ATTRIBUTE);
        final boolean fileCollectionEnabled = (boolean) event.getAttributeValues().get(SCANNER_FILE_COLLECTION_ENABLED_ATTRIBUTE);
        final ScannerOperationVO scannerVO = new ScannerOperationVO(scannerStatus, subscriptionId, ropPeriodInSeconds, scannerFdn, processType);
        scannerVO.setFileCollectionEnabled(fileCollectionEnabled);
        systemRecorder.eventCoarse(SCANNER_DPS_NOTIFICATION, scannerFdn, "PMICScannerInfo create notification for ScannerFdn : {}," +
                " ScannerStatus : {}, Subscription  ID : {}, ropPeriodInSeconds : {} ", scannerFdn, scannerStatus, subscriptionId,
            ropPeriodInSeconds);
        scannerCreateHandler.execute(scannerVO);
    }

    @Override
    public boolean isInterested(final DpsObjectCreatedEvent event) {
        return SCANNER_MODEL_NAME.equals(event.getType()) || SCANNER_UE_MODEL_NAME.equals(event.getType())
                || SCANNER_MTR_MODEL_NAME.equals(event.getType());
    }

    @Override
    public Class<DpsObjectCreatedEvent> getEventClass() {
        return DpsObjectCreatedEvent.class;
    }
}
