/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.collection.mountpoints;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.config.listener.processors.PmicNfsShareListValueProcessor;

/**
 * This class is an implementation of {@link DestinationMountPointConfigSource} having its underlying configuration source the Modeled Configuration
 * provided by SFWK
 */
@ApplicationScoped
public class DestinationMountPointConfigSourceFromModeledConfiguration implements DestinationMountPointConfigSource {

    @Inject
    private Logger logger;

    @Inject
    private ConfigurationChangeListener configurationChangeListener;

    @Inject
    private PmicNfsShareListValueProcessor pmicNfsShareListValueProcessor;

    private List<String> mountPointsList;

    /*
     * (non-Javadoc)
     * @see com.ericsson.oss.services.pm.collection.DestinationMountPointConfigSource#getBalancedMountPoints()
     */
    @Override
    public List<String> getAvailableFileCollectionMountPoints() {
        if (mountPointsList == null) {
            reload();
        }

        return mountPointsList;
    }

    /*
     * (non-Javadoc)
     * @see com.ericsson.oss.services.pm.collection.DestinationMountPointConfigSource#reload()
     */
    @Override
    public void reload() {
        String pmicNfsShareListToUse = null;

        logger.debug("Reloading configuration for directory destination for file collection");

        if (isExternalConfigurationForPmicNfsShareAvailable()) {
            pmicNfsShareListToUse = getExternalConfigurationValueForPmicNfsShare();

            logger.debug("Reloaded destination directory with value set externally. Will use: {}", pmicNfsShareListToUse);
        }
        mountPointsList = pmicNfsShareListValueProcessor.process(pmicNfsShareListToUse);
    }

    /**
     * Quick check just to see if value set externally is available
     *
     * @return True if a value was injected externally and now we're aware of it. False otherwise
     */
    private boolean isExternalConfigurationForPmicNfsShareAvailable() {
        return getExternalConfigurationValueForPmicNfsShare() != null;
    }

    private String getExternalConfigurationValueForPmicNfsShare() {
        String externalValueForPmicNfsShare = null;

        final String pmicNfsShareList = configurationChangeListener.getPmicNfsShareList();
        if (pmicNfsShareList != null && !"".equals(pmicNfsShareList)) {
            externalValueForPmicNfsShare = pmicNfsShareList;
        }

        return externalValueForPmicNfsShare;
    }

}
