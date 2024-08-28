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

import static com.ericsson.oss.services.pm.common.utils.PmFunctionConstants.PM_FUNCTION_TYPE;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.CM_FUNCTION_MODEL_NAME;

import java.util.HashMap;
import java.util.Map;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectDeletedEvent;
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper;
import com.ericsson.oss.services.pm.common.notification.EventHandler;
import com.ericsson.oss.services.pm.initiation.schedulers.NodeRemovalHandler;
import com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler.ErrorNodeCacheAttributes;
import com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler.ErrorNodeCacheProcessType;
import com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler.ScannerErrorHandler;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * This class listens for the Data Persistence Service notifications for deletion of PMICScannerInfo MO.
 */
@Startup
@Singleton
@Lock(LockType.READ)
public class DpsPmFunctionDeleteNotificationListener implements EventHandler<DpsObjectDeletedEvent> {

    @Inject
    private Logger logger;

    @Inject
    private MembershipListener membershipListener;

    @Inject
    private ScannerErrorHandler scannerErrorHandler;

    @Inject
    private PmFunctionEnabledWrapper pmFunctionCache;

    @Inject
    private NodeRemovalHandler nodeRemovalHandler;

    /**
     * This method processes PmFunction DELETE notification and subscription validation after a timeout
     *
     * @param event
     */
    @Override
    public void onEvent(final DpsObjectDeletedEvent event) {
        final String pmOrCmFunctionFdn = event.getFdn();
        if (pmOrCmFunctionFdn.contains(",")) {
            logger.info("Delete notification received for {}", pmOrCmFunctionFdn);
            final String nodeFdn = pmOrCmFunctionFdn.substring(0, pmOrCmFunctionFdn.lastIndexOf(','));
            final Map<String, Object> attributes = new HashMap<>();
            attributes.put(ErrorNodeCacheAttributes.FDN, nodeFdn);
            scannerErrorHandler.process(ErrorNodeCacheProcessType.PM_FUNCTION_DELETED, attributes);
            pmFunctionCache.removeEntry(nodeFdn);
            nodeRemovalHandler.setTimer();
        }
    }

    @Override
    public boolean isInterested(final DpsObjectDeletedEvent event) {
        //The PmFunction notification is not always received so this listeners listens for CmFunction too
        return membershipListener.isMaster() && (PM_FUNCTION_TYPE.equals(event.getType()) || CM_FUNCTION_MODEL_NAME.equals(event.getType()));
    }

    @Override
    public Class<DpsObjectDeletedEvent> getEventClass() {
        return DpsObjectDeletedEvent.class;
    }
}
