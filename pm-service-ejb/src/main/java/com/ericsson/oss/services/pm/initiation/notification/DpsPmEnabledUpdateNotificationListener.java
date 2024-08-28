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

import java.util.Set;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent;
import com.ericsson.oss.pmic.api.cache.PmFunctionData;
import com.ericsson.oss.services.model.ned.pm.function.FileCollectionState;
import com.ericsson.oss.services.model.ned.pm.function.NeConfigurationManagerState;
import com.ericsson.oss.services.model.ned.pm.function.ScannerMasterState;
import com.ericsson.oss.services.pm.cache.PmFunctionEnabledWrapper;
import com.ericsson.oss.services.pm.common.notification.EventHandler;
import com.ericsson.oss.services.pm.common.utils.PmFunctionConstants;
import com.ericsson.oss.services.pm.initiation.schedulers.DelayedPmFunctionUpdateProcessor;
import com.ericsson.oss.services.pm.initiation.utils.FdnUtil;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * This class listens for the DPS notifications in PmFunction MO update. During node creation default value of pmEnable will be false hence no scanner
 * will be migrated. Once pmEnabled value is set to true this listener kick of the scanner migration for that node.
 */
@SuppressWarnings("PMD.TooManyFields")
@Startup
@Singleton
public class DpsPmEnabledUpdateNotificationListener implements EventHandler<DpsAttributeChangedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(DpsPmEnabledUpdateNotificationListener.class);

    @Inject
    private MembershipListener membershipListener;
    @Inject
    private PmFunctionEnabledWrapper pmFunctionCache;
    @Inject
    private DelayedPmFunctionUpdateProcessor delayedUpdateProcessor;

    @Override
    public void onEvent(final DpsAttributeChangedEvent event) {
        final String pmFunctionFDN = event.getFdn();
        final String nodeFdn = FdnUtil.getParentFromChildOfType(pmFunctionFDN, PM_FUNCTION_TYPE);
        final PmFunctionData avcPmFunctionData = new PmFunctionData(null, null, null, null);
        final PmFunctionData oldPmFunctionData = getCurrentPmFunctionData(nodeFdn);
        final Set<AttributeChangeData> changedAttributes = event.getChangedAttributes();
        for (final AttributeChangeData changedData : changedAttributes) {
            logger.info("Processing pmFunction PM_ENABLED attribute {} with value {} for Network Element FDN {}", changedData.getName(), changedData.getNewValue(),
                nodeFdn);
            initPmFunctionData(avcPmFunctionData, changedData.getName(), changedData.getNewValue());
            updatePmFunctionCache(nodeFdn, avcPmFunctionData);
        }
        if (membershipListener.isMaster()) {
            delayedUpdateProcessor.scheduleDelayedPmFunctionUpdateProcessor(nodeFdn, avcPmFunctionData, oldPmFunctionData);
        }
    }

    private void updatePmFunctionCache(final String nodeFdn, final PmFunctionData pmFunctionData) {
        if (pmFunctionData.getPmFunctionEnabled() != null) {
            pmFunctionCache.updateEntry(nodeFdn, pmFunctionData.getPmFunctionEnabled());
            logger.debug("Updating pmFunction cache PM_ENABLED attribute to {} for Network Element FDN {}", pmFunctionData.getPmFunctionEnabled(),
                    nodeFdn);
        }
        if (pmFunctionData.getFileCollectionState() != null) {
            pmFunctionCache.updateEntry(nodeFdn, pmFunctionData.getFileCollectionState());
            logger.debug("Updating pmFunction cache FILE_COLLECTION_STATE attribute to {} for Network Element FDN {}",
                    pmFunctionData.getFileCollectionState(), nodeFdn);
        }
        if (pmFunctionData.getScannerMasterState() != null) {
            pmFunctionCache.updateEntry(nodeFdn, pmFunctionData.getScannerMasterState());
            logger.debug("Updating pmFunction cache SCANNER_MASTER_STATE attribute to {} for Network Element FDN {}",
                    pmFunctionData.getScannerMasterState(), nodeFdn);
        }
        if (pmFunctionData.getNeConfigurationManagerState() != null) {
            pmFunctionCache.updateEntry(nodeFdn, pmFunctionData.getNeConfigurationManagerState());
            logger.debug("Updating pmFunction cache NE_CONFIGURATION_MANAGER_STATE attribute to {} for Network Element FDN {}",
                    pmFunctionData.getNeConfigurationManagerState(), nodeFdn);
        }
    }

    private void initPmFunctionData(final PmFunctionData pmFunctionData, final String attribute, final Object newValue) {
        switch (attribute) {
            case PmFunctionConstants.PM_ENABLED:
                pmFunctionData.setPmFunctionEnabled((Boolean) newValue);
                break;
            case PmFunctionConstants.FILE_COLLECTION_STATE:
                pmFunctionData.setFileCollectionState(FileCollectionState.valueOf((String) newValue));
                break;
            case PmFunctionConstants.SCANNER_MASTER_STATE:
                pmFunctionData.setScannerMasterState(ScannerMasterState.valueOf((String) newValue));
                break;
            case PmFunctionConstants.NE_CONFIGURATION_MANAGER_STATE:
                pmFunctionData.setNeConfigurationManagerState(NeConfigurationManagerState.valueOf((String) newValue));
                break;
            default:
                break;
        }
    }

    private PmFunctionData getCurrentPmFunctionData(final String nodeFdn) {
        return pmFunctionCache.getEntry(nodeFdn);
    }

    @Override
    public boolean isInterested(final DpsAttributeChangedEvent event) {
        return PM_FUNCTION_TYPE.equals(event.getType());
    }

    @Override
    public Class<DpsAttributeChangedEvent> getEventClass() {
        return DpsAttributeChangedEvent.class;
    }
}
