/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2017
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.dps.availability;

import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService;
import com.ericsson.oss.itpf.datalayer.dps.availability.DpsAvailabilityCallback;
import com.ericsson.oss.itpf.sdk.core.annotation.EServiceRef;
import com.ericsson.oss.itpf.sdk.core.retry.RetriableCommand;
import com.ericsson.oss.itpf.sdk.core.retry.RetriableCommandException;
import com.ericsson.oss.itpf.sdk.core.retry.RetryContext;
import com.ericsson.oss.itpf.sdk.core.retry.RetryManager;
import com.ericsson.oss.itpf.sdk.core.retry.RetryPolicy;

/**
 * This class gets called at startup to register a DPS availability callback using
 * {@link DataPersistenceService#registerDpsAvailabilityCallback(DpsAvailabilityCallback)}.
 * See {@see com.ericsson.oss.pmic.dao.availability.PmicDpsAvailabilityStatus}
 * and {@see PmicDpsAvailabilityCallback} for more information.
 */
@Singleton
@Startup
public class PmicDpsAvailabilityNotificationCallbackInitializer {
    private static final int REG_ATTEMPT_DELAY_MS = 30000;
    private static final int REG_ATTEMPT_INTERVAL_MS = 5000;
    private static final int MAX_REG_ATTEMPTS = 50;

    @EServiceRef
    private DataPersistenceService dataPersistenceService;

    @Inject
    private TimerService timer;

    @Inject
    private DpsAvailabilityCallback callback;

    @Inject
    private RetryManager retryManager;

    @Inject
    private Logger logger;

    /**
     * Uses {@link TimerService} to exec the callback's registration.
     */
    @PostConstruct
    public void startDpsNotificationCallbackRegistration() {
        startTimer(REG_ATTEMPT_DELAY_MS);
    }

    private void startTimer(final int delay) {
        logger.info("Starting timer to register DPS availability callback");

        final TimerConfig config = new TimerConfig();
        config.setPersistent(false);
        timer.createSingleActionTimer(delay, config);
    }

    /**
     * {@link TimerService} will execute this method to attempt callback's registration. In case of failure the timer will be restarted.
     */
    @Timeout
    public void registerDpsNotificationCallback() {

        logger.info("Starting RetryManager to register DPS availability callback.");

        final RetryPolicy retryPolicy = RetryPolicy.builder()
                .attempts(MAX_REG_ATTEMPTS)
                .waitInterval(REG_ATTEMPT_INTERVAL_MS, TimeUnit.MILLISECONDS)
                .retryOn(Exception.class)
                .build();
        try {
            retryManager.executeCommand(retryPolicy, new RegisterDpsCallbackCommand());
        } catch (RetriableCommandException ex) {
            logger.error("Failed to register DPS availabilty callback within: {} seconds",
                    MAX_REG_ATTEMPTS * REG_ATTEMPT_INTERVAL_MS / 1000);

        } catch (final Exception ex) {
            logger.error(
                    String.format("An unexpected %s occurred during DPS availability callback registration: %s.",
                            ex.getClass().getCanonicalName(), ex.getMessage()),
                    ex);
            startTimer(REG_ATTEMPT_INTERVAL_MS);
        }
    }

    /**
     * Command to register a DPS callback. Implements the {@link RetriableCommand} interface.
     */
    private class RegisterDpsCallbackCommand implements RetriableCommand<Object> {
        /**
         * {@inheritDoc}.
         */
        @SuppressWarnings("PMD.SignatureDeclareThrowsException")
        @Override
        public Object execute(final RetryContext retryContext) throws Exception {

            final int currentAttempt = retryContext.getCurrentAttempt();
            final String logMessage = String.format("Registering DPS availability callback for PMIC (attempt %d/%d).",
                    currentAttempt, MAX_REG_ATTEMPTS);

            if (currentAttempt == 1) {
                logger.info(logMessage);
            } else {
                logger.warn(logMessage);
            }

            dataPersistenceService.registerDpsAvailabilityCallback(callback);

            return null;
        }
    }
}
