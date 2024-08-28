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

package com.ericsson.oss.services.pm.initiation.integration.util.events;

import java.io.File;
import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.integration.test.helpers.PibHelper;

@Singleton
@Startup
public class ConfigurationChangeManager {

    protected static final String TEST_DIRECTORY = "src" + File.separator + "test" + File.separator + "resources" + File.separator;
    protected static final String TEST_SYM_DIRECTORY = TEST_DIRECTORY + "symfiles" + File.separator;
    protected static final String FILE_DIRECTORY = TEST_DIRECTORY + "files" + File.separator;
    @Inject
    Logger logger;
    @Inject
    private PibHelper pibHelper;

    @PostConstruct
    public void changeConfigurationParameters() {
        try {
            final String symbolicLinkVolume = "symbolicLinkVolume";
            final String pmicEventsSymbolicLinkVolume = "pmicEventsSymbolicLinkVolume";
            final String pmicNfsShare = "pmicNfsShare";

            logger.debug("Sending notification for changing modeled property {} ", symbolicLinkVolume);
            pibHelper.updateParameter(symbolicLinkVolume, TEST_SYM_DIRECTORY, String.class);

            logger.debug("Sending notification for changing modeled property {} ", pmicEventsSymbolicLinkVolume);
            pibHelper.updateParameter(pmicEventsSymbolicLinkVolume, TEST_SYM_DIRECTORY, String.class);

            logger.debug("Sending notification for changing modeled property {} ", pmicNfsShare);
            pibHelper.updateParameter(pmicNfsShare, FILE_DIRECTORY, String.class);

        } catch (final Exception e) {
            logger.error("Can not change config param ", e);
        }
    }
}
