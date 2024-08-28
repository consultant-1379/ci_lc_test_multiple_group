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

package com.ericsson.oss.services.pm.initiation.notification;

import static com.ericsson.oss.pmic.api.constants.ModelConstants.PMICSubScannerInfoConstants.PMIC_SUB_SCANNER_INFO_TYPE;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.SCANNER_DPS_NOTIFICATION;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE;

import javax.ejb.*;
import javax.inject.Inject;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectCreatedEvent;
import com.ericsson.oss.pmic.impl.handler.InvokeInTransaction;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.common.notification.EventHandler;
import com.ericsson.oss.services.pm.initiation.notification.handlers.PmSubScannerCreateOperationHandler;

/**
 * This class listens for the Data Persistence Service notifications for creation of PMICSubScannerInfo MO.
 */
@Startup
@Singleton
@Lock(LockType.READ)
public class DpsPmSubScannerCreateNotificationListener implements EventHandler<DpsObjectCreatedEvent> {

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    @Inject
    private PmSubScannerCreateOperationHandler subScannerCreateOperationHandler;

    @Override
    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void onEvent(final DpsObjectCreatedEvent event) {
        final String subScannerFdn = event.getFdn();
        final String subscriptionId = (String) event.getAttributeValues().get(SCANNER_SUBSCRIPTION_PO_ID_ATTRIBUTE);

        systemRecorder.eventCoarse(SCANNER_DPS_NOTIFICATION,
                subScannerFdn, "PMICSubScannerInfo OBJECT_CREATE notification received from DPS for Fdn : {}, Subscription ID : {}",
                subScannerFdn, subscriptionId);
        subScannerCreateOperationHandler.execute(subScannerFdn, subscriptionId);
    }

    @Override
    public Class<DpsObjectCreatedEvent> getEventClass() {
        return DpsObjectCreatedEvent.class;
    }

    @Override
    public boolean isInterested(final DpsObjectCreatedEvent event) {
        return PMIC_SUB_SCANNER_INFO_TYPE.equals(event.getType());
    }
}
