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
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_MODEL_NAME;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_MTR_MODEL_NAME;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_ROP_PERIOD_ATTRIBUTE;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_STATUS_ATTRIBUTE;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_UE_MODEL_NAME;

import javax.ejb.*;
import javax.inject.Inject;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectDeletedEvent;
import com.ericsson.oss.pmic.impl.handler.InvokeInTransaction;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import com.ericsson.oss.services.pm.collection.notification.handlers.ScannerDeleteOperationHandler;
import com.ericsson.oss.services.pm.collection.notification.handlers.ScannerOperationVO;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.common.notification.EventHandler;

/**
 * This class listens for the Data Persistence Service notifications for deletion of PMICScannerInfo MO.
 */
@Startup
@Singleton
@Lock(LockType.READ)
public class DpsScannerDeleteNotificationListener implements EventHandler<DpsObjectDeletedEvent> {

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    @Inject
    private ScannerDeleteOperationHandler scannerDeleteHandler;

    /**
     * This method processes PMICScannerInfo DELETE notification and triggers below actions : 1) Delete node from the collection list to stop file
     * collection. 2) Stop FileCollectionTaskManager timer if no more active scanner exist. 3) Stop FileCollectionTaskSender timer if no more active
     * scanner exist.
     *
     * @param event
     */
    @Override
    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void onEvent(final DpsObjectDeletedEvent event) {
        if (event.getAttributeValues().keySet().isEmpty()) {
            return;
        }
        final String scannerFdn = event.getFdn();
        final Integer ropPeriodInSeconds = (Integer) event.getAttributeValues().get(SCANNER_ROP_PERIOD_ATTRIBUTE);
        final String scannerStatus = (String) event.getAttributeValues().get(SCANNER_STATUS_ATTRIBUTE);
        final String subscriptionId = (String) event.getAttributeValues().get(SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE);
        final String processType = (String) event.getAttributeValues().get(PROCESS_TYPE_ATTRIBUTE);

        systemRecorder.eventCoarse(
                        SCANNER_DPS_NOTIFICATION, scannerFdn,"PMICScannerInfo delete notification for ScannerFdn : {}, ScannerStatus : {}," +
                " Subscription ID : {}, ropPeriodInSeconds : {} ", scannerFdn, scannerStatus, subscriptionId, ropPeriodInSeconds);

        final ScannerOperationVO scannerVO = new ScannerOperationVO(scannerStatus, subscriptionId, ropPeriodInSeconds, scannerFdn, processType);
        scannerDeleteHandler.execute(scannerVO);        
    }

    @Override
    public boolean isInterested(final DpsObjectDeletedEvent event) {
        return SCANNER_MODEL_NAME.equals(event.getType()) || SCANNER_UE_MODEL_NAME.equals(event.getType())
                || SCANNER_MTR_MODEL_NAME.equals(event.getType());
    }

    @Override
    public Class<DpsObjectDeletedEvent> getEventClass() {
        return DpsObjectDeletedEvent.class;
    }
}
