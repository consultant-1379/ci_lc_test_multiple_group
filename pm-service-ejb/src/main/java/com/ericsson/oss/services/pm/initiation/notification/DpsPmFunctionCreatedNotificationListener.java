/*
 * COPYRIGHT Ericsson 2017
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.notification;

import static com.ericsson.oss.services.pm.common.utils.PmFunctionConstants.FILE_COLLECTION_STATE;
import static com.ericsson.oss.services.pm.common.utils.PmFunctionConstants.NE_CONFIGURATION_MANAGER_STATE;
import static com.ericsson.oss.services.pm.common.utils.PmFunctionConstants.PM_ENABLED;
import static com.ericsson.oss.services.pm.common.utils.PmFunctionConstants.PM_FUNCTION_TYPE;
import static com.ericsson.oss.services.pm.common.utils.PmFunctionConstants.SCANNER_MASTER_STATE;

import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import com.ericsson.oss.pmic.impl.handler.InvokeInTransaction;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import org.slf4j.Logger;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectCreatedEvent;
import com.ericsson.oss.pmic.api.cache.PmFunctionData;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.services.model.ned.pm.function.FileCollectionState;
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState;
import com.ericsson.oss.services.model.ned.pm.function.ScannerMasterState;
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper;
import com.ericsson.oss.services.pm.common.notification.EventHandler;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.utils.FdnUtil;

/**
 * This class listens for the Data Persistence Service notifications for creation of PmFunction MO.
 */

@Startup
@Singleton
public class DpsPmFunctionCreatedNotificationListener implements EventHandler<DpsObjectCreatedEvent> {

    @Inject
    private Logger logger;

    @Inject
    private NodeService nodeService;

    @Inject
    private PmFunctionEnabledWrapper pmFunctionCache;

    /**
     * This method processes PmFunction CREATE notification and updates the cache
     *
     * @param event
     */
    @Override
    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void onEvent(final DpsObjectCreatedEvent event) {
        final String pmParentFdn = event.getFdn();
        final String nodeFdn = FdnUtil.getParentFromChildOfType(pmParentFdn, PM_FUNCTION_TYPE);
        if (!pmFunctionCache.containsFdn(nodeFdn)) {
            final PmFunctionData pmFunctionData = new PmFunctionData((Boolean) event.getAttributeValues().get(PM_ENABLED),
                    FileCollectionState.valueOf((String) event.getAttributeValues().get(FILE_COLLECTION_STATE)),
                    ScannerMasterState.valueOf((String) event.getAttributeValues().get(SCANNER_MASTER_STATE)),
                    NeConfigurationManagerState.valueOf((String) event.getAttributeValues().get(NE_CONFIGURATION_MANAGER_STATE)),
                    getNeType(nodeFdn));
            pmFunctionCache.addEntry(nodeFdn, pmFunctionData);
            logger.info("Creating and Updating PMFunction cache with {}, attributes : {} ", nodeFdn, event.getAttributeValues());
        }
    }

    private String getNeType(final String networkElementFdn) {
        String neType = null;
        try {
            final Node node = nodeService.findOneByFdn(networkElementFdn);
            neType = node.getNeType();
        } catch (final DataAccessException ex) {
            logger.error("Failed to retrieve Node details from DPS for nodeFdn : {}, due to {}", networkElementFdn, ex.getMessage());
        }
        return neType;
    }

    @Override
    public boolean isInterested(final DpsObjectCreatedEvent event) {
        return PM_FUNCTION_TYPE.equals(event.getType());
    }

    @Override
    public Class<DpsObjectCreatedEvent> getEventClass() {
        return DpsObjectCreatedEvent.class;
    }

}
