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

import static java.lang.String.format;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.PMJOB_DPS_NOTIFICATION;
import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMJOB_MODEL_NAME;
import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMJOB_STATUS_ATTRIBUTE;

import java.util.Set;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import com.ericsson.oss.pmic.impl.handler.InvokeInTransaction;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import org.slf4j.Logger;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent;
import com.ericsson.oss.services.pm.collection.notification.handlers.PMICJobInfoUpdateAttributeVO;
import com.ericsson.oss.services.pm.collection.notification.handlers.PmJobUpdateOperationHandler;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.common.notification.EventHandler;
import com.ericsson.oss.services.pm.exception.PmJobNotFoundDataAccessException;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * This class listens for the Data Persistence Service notifications for update of PMICPobInfo MO.
 */
@Stateless
public class DpsPmJobUpdateNotificationListener implements EventHandler<DpsAttributeChangedEvent> {

    @Inject
    private Logger logger;
    @Inject
    private MembershipListener membershipListener;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private PmJobUpdateOperationHandler pmJobUpdateOperationHandler;

    /**
     * This method processes PMICPobInfo UPDATE notification and triggers below actions : 1) check if the pmicJobStatus is updated 2) Get PMICJobInfo
     * MO for the given fdn 3) Update task status of subscription
     *
     * @param event
     */
    @Override
    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void onEvent(final DpsAttributeChangedEvent event) {

        final String pmJobFdn = event.getFdn();
        final Set<AttributeChangeData> changedAttributes = event.getChangedAttributes();
        final PMICJobInfoUpdateAttributeVO updateAttributes = new PMICJobInfoUpdateAttributeVO();

        for (final AttributeChangeData changedData : changedAttributes) {
            if (PMJOB_STATUS_ATTRIBUTE.equals(changedData.getName())) {
                final String attribute = changedData.getName();
                final String oldValue = (String) changedData.getOldValue();
                final String newValue = (String) changedData.getNewValue();
                updateAttributes.setStatusAttributeUpdated(true);
                updateAttributes.setNewStatusValue(newValue);
                updateAttributes.setOldStatusValue(oldValue);
                final String message = getMessage(pmJobFdn, attribute, oldValue, newValue);
                systemRecorder.eventCoarse(PMJOB_DPS_NOTIFICATION, pmJobFdn, message);
                try {
                    pmJobUpdateOperationHandler.execute(pmJobFdn, updateAttributes);
                } catch (final PmJobNotFoundDataAccessException exception) {
                    logger.error("PMICJobInfo not found for pmJobFdn {}", pmJobFdn);
                    logger.info("PMICJobInfo not found for pmJobFdn {}", pmJobFdn, exception);
                }
            } else {
                logger.info(getMessage(pmJobFdn, changedData.getName(), changedData.getOldValue(), changedData.getNewValue()));
            }
        }
    }

    private String getMessage(final Object pmJobFdn, final Object attribute, final Object oldValue, final Object newValue) {
        return format("PMICJobInfo update notification for PmJobFdn : %s, AttributeName : %s, updated from %s to %s ",
                pmJobFdn, attribute, oldValue, newValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInterested(final DpsAttributeChangedEvent event) {
        return PMJOB_MODEL_NAME.equals(event.getType()) && membershipListener.isMaster();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<DpsAttributeChangedEvent> getEventClass() {
        return DpsAttributeChangedEvent.class;
    }
}
