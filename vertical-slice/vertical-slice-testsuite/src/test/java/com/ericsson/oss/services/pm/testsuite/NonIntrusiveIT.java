package com.ericsson.oss.services.pm.testsuite;

import java.io.IOException;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.cucumber.arquillian.api.GluePackages;
import com.ericsson.oss.cucumber.arquillian.runtime.ArquillianCucumberBlast;

import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.runtime.arquillian.api.Features;

@RunAsClient
@RunWith(ArquillianCucumberBlast.class)
@Features({"EventLookUp.feature", "CounterLookUp.feature", "EbmSubscription.feature", "StatisticalSubscription.feature", "CounterLookUpDeprecated.feature"})
@GluePackages
public class NonIntrusiveIT {

    private static final Logger logger = LoggerFactory.getLogger(NonIntrusiveIT.class);

    @BeforeClass
    public static void start() throws IOException, InterruptedException {
        logger.info("Test started");
    }

    @AfterClass
    public static void end() {
        logger.info("Test finished");
    }

    @Before(order = 1)
    public void beforeScenario() throws IOException {
        logger.info("<-------- Begin Scenario  -------->");
    }

    @After(order = 1)
    public void cleanUp() throws IOException {
        logger.info("<-------- Cleanup -------->");
    }

    @After(order = 2)
    public void afterScenario() throws IOException {
        logger.info("<-------- End Scenario -------->");
    }
}
