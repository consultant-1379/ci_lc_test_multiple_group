/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.testsuite;

import org.eu.ingwar.tools.arquillian.extension.suite.annotations.ArquillianSuiteDeployment;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.PomEquippedResolveStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ArquillianSuiteDeployment
public class Artifacts {

    private static final Logger LOGGER = LoggerFactory.getLogger(Artifacts.class);

    private static final PomEquippedResolveStage BOM = Maven.configureResolver().loadPomFromFile("pom.xml");

    @Deployment(name = "pm_service")
    public static Archive<?> deployPMService() {
        LOGGER.debug("******Creating pm-service ear for test******");
        return BOM.resolve("com.ericsson.oss.pmic:pm-service-ear:ear:?").withoutTransitivity().asSingle(EnterpriseArchive.class);
    }
}
