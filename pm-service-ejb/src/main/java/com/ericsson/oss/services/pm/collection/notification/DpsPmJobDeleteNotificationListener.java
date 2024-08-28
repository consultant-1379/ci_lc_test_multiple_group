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

package com.ericsson.oss.services.pm.collection.notification;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.PMJOB_DPS_NOTIFICATION;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.PROCESS_TYPE_ATTRIBUTE;
import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMJOB_MODEL_NAME;
import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMJOB_ROP_PERIOD_ATTRIBUTE;
import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMJOB_STATUS_ATTRIBUTE;
import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMJOB_SUBSCRIPTION_PO_ID_ATTRIBUTE;

import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectDeletedEvent;
import com.ericsson.oss.services.pm.collection.notification.handlers.PmJobDeleteOperationHandler;
import com.ericsson.oss.services.pm.collection.notification.handlers.PmJobOperationVO;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.common.notification.EventHandler;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * This class listens for the Data Persistence Service notifications for deletion of PMICJobInfo MO.
 */
@Startup
@Singleton
@Lock(LockType.READ)
public class DpsPmJobDeleteNotificationListener implements EventHandler<DpsObjectDeletedEvent> {

    @Inject
    private MembershipListener membershipListener;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private PmJobDeleteOperationHandler pmJobDeleteOperationHandler;

    /**
     * This method processes PMICJobInfo DELETE notification
     *
     * @param event
     *         DpsObjectDeletedEvent
     */
    @Override
    public void onEvent(final DpsObjectDeletedEvent event) {
        final String pmJobFdn = event.getFdn();
        final Integer ropPeriodInSeconds = (Integer) event.getAttributeValues().get(PMJOB_ROP_PERIOD_ATTRIBUTE);
        final String pmJobStatus = (String) event.getAttributeValues().get(PMJOB_STATUS_ATTRIBUTE);
        final String subscriptionId = (String) event.getAttributeValues().get(PMJOB_SUBSCRIPTION_PO_ID_ATTRIBUTE);
        final String processType = (String) event.getAttributeValues().get(PROCESS_TYPE_ATTRIBUTE);
        systemRecorder.eventCoarse(PMJOB_DPS_NOTIFICATION, pmJobFdn,
                "PMICJobInfo delete notification for PmJobFdn : {}, PmJobStatus : {}, Subscription ID : {},"
                        + " ropPeriodInSeconds : {} ",
                pmJobFdn, pmJobStatus, subscriptionId, ropPeriodInSeconds);
        final PmJobOperationVO pmJobVO = new PmJobOperationVO(pmJobStatus, subscriptionId, ropPeriodInSeconds, pmJobFdn, processType);
        pmJobDeleteOperationHandler.execute(pmJobVO);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInterested(final DpsObjectDeletedEvent event) {
        return PMJOB_MODEL_NAME.equals(event.getType()) && membershipListener.isMaster();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<DpsObjectDeletedEvent> getEventClass() {
        return DpsObjectDeletedEvent.class;
    }
}
