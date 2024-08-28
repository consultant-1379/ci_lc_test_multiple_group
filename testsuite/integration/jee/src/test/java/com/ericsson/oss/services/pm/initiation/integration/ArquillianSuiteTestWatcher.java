package com.ericsson.oss.services.pm.initiation.integration;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArquillianSuiteTestWatcher extends TestWatcher {

    Logger log = LoggerFactory.getLogger(ArquillianSuiteTestWatcher.class);

    @Override
    protected void starting(final Description description) {
        super.starting(description);
        log.info("STARTING: {}.{}", description.getTestClass().getSimpleName(), description.getMethodName());
    }

    @Override
    protected void succeeded(final Description description) {
        super.succeeded(description);
        log.info("PASSED: {}.{}", description.getTestClass().getSimpleName(), description.getMethodName());
    }

    @Override
    protected void failed(final Throwable e, final Description description) {
        super.failed(e, description);
        log.info("FAILED: {}.{}. Cause: {}", description.getTestClass().getSimpleName(), description.getMethodName(), e.getMessage());
    }
}
