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

package com.ericsson.oss.services.pm.deployments;

import org.eu.ingwar.tools.arquillian.extension.suite.annotations.ArquillianSuiteDeployment;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.Archive;

import com.ericsson.oss.services.pm.collection.recovery.FileCollectionJobCacheForRecovery;
import com.ericsson.oss.services.pm.dao.SubscriptionCreatorHelper;
import com.ericsson.oss.services.pm.initiation.integration.subscription.UeTraceFileCollectionManagerITTest;
import com.ericsson.oss.services.pm.initiation.integration.util.events.ConfigurationChangeManager;
import com.ericsson.oss.services.pm.initiation.scanner.data.PMICScannerData;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionAttributes;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionDataReader;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionInfo;
import com.ericsson.oss.services.pm.initiation.subscription.data.exception.SubscriptionTypeNotSupportedException;
import com.ericsson.oss.services.pm.initiation.timer.DummyTimer;
import com.ericsson.oss.services.pm.listeners.PmServiceIntegrationRequestsListener;
import com.ericsson.oss.services.pm.test.requests.PmServiceRequestsTypes;
import com.ericsson.oss.services.pm.test.requests.SubscriptionOperationRequest;

/**
 * Deployment point for all the deployable entity. If any new ear/war/jar/rar etc to be deployed add it here as deployment. All test classes must be
 * added to test_app deployment. And if anything needs to be added in pmservice add in pm_service deployment
 *
 * @author ekamkal
 */
@ArquillianSuiteDeployment
public class Deployments {

    @Deployment(name = "pm_service_deployment")
    public static Archive<?> deployPMService() {
        return PmServiceDeployment.createPmServiceDeploymentWithTestBeans("PMSERVICE", FileCollectionJobCacheForRecovery.class,
                SubscriptionTypeNotSupportedException.class, PmServiceIntegrationRequestsListener.class,
                SubscriptionDataReader.class, SubscriptionInfo.class, SubscriptionOperationRequest.class, SubscriptionAttributes.class,
                PmServiceRequestsTypes.class, ConfigurationChangeManager.class, PMICScannerData.class,
                UeTraceFileCollectionManagerITTest.class, DummyTimer.class, SubscriptionCreatorHelper.class);
    }
}
