/*
 * ------------------------------------------------------------------------------
 * *******************************************************************************
 * * COPYRIGHT Ericsson 2016
 * *
 * * The copyright to the computer program(s) herein is the property of
 * * Ericsson Inc. The programs may be used and/or copied only with written
 * * permission from Ericsson Inc. or in accordance with the terms and
 * * conditions stipulated in the agreement/contract under which the
 * * program(s) have been supplied.
 * *******************************************************************************
 * *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.collection.instrumentation;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.slf4j.Logger;

/**
 * This class creates {@link FileCollectionCycleInstrumentation} object depending on the {@link FileCollectionInstrumentationQualifier}
 *
 * @author esrekiz
 */
public class FileCollectionCycleInstrumentationProducer {

    private static final String RETURNING_INSTANCE_FOR_PARENTHESES = "Returning instance for {} with instance id {}";

    @Inject
    private Logger logger;

    /**
     * Returns the {@link FileCollectionCycleInstrumentation} object for one minute rop
     *
     * @param fileCollectionCycleInstrumentation
     *            {@link FileCollectionCycleInstrumentation} object that injected runtime.
     * @return {@link FileCollectionCycleInstrumentation} object for one minute rop
     */
    @Produces
    @FileCollectionInstrumentationQualifier(cycleId = "oneMinuteRopFileCollectionCycleInstrumentation", ropPeriodInSeconds = 60)
    public FileCollectionCycleInstrumentation getOneMinFileCollectionCycleInstrumentation(
            final FileCollectionCycleInstrumentation fileCollectionCycleInstrumentation) {
        fileCollectionCycleInstrumentation.setCycleId("oneMinuteRopFileCollectionCycleInstrumentation");
        fileCollectionCycleInstrumentation.setRopPeriodInSeconds(60);
        logger.debug(RETURNING_INSTANCE_FOR_PARENTHESES, "oneMinuteRopFileCollectionCycleInstrumentation",
                System.identityHashCode(fileCollectionCycleInstrumentation));
        return fileCollectionCycleInstrumentation;
    }

    /**
     * Returns the {@link FileCollectionCycleInstrumentation} object for one minute rop
     *
     * @param fileCollectionCycleInstrumentation
     *            {@link FileCollectionCycleInstrumentation} object that injected runtime.
     * @return {@link FileCollectionCycleInstrumentation} object for one minute rop
     */
    @Produces
    @FileCollectionInstrumentationQualifier(cycleId = "fiveMinuteRopFileCollectionCycleInstrumentation", ropPeriodInSeconds = 300)
    public FileCollectionCycleInstrumentation getFiveMinFileCollectionCycleInstrumentation(
            final FileCollectionCycleInstrumentation fileCollectionCycleInstrumentation) {
        fileCollectionCycleInstrumentation.setCycleId("fiveMinuteRopFileCollectionCycleInstrumentation");
        fileCollectionCycleInstrumentation.setRopPeriodInSeconds(300);
        logger.debug(RETURNING_INSTANCE_FOR_PARENTHESES, "fiveMinuteRopFileCollectionCycleInstrumentation",
                System.identityHashCode(fileCollectionCycleInstrumentation));
        return fileCollectionCycleInstrumentation;
    }

    /**
     * Returns the {@link FileCollectionCycleInstrumentation} object for fifteen minute rop
     *
     * @param fileCollectionCycleInstrumentation
     *            {@link FileCollectionCycleInstrumentation} object that injected runtime.
     * @return {@link FileCollectionCycleInstrumentation} object for fifteen minute rop
     */
    @Produces
    @FileCollectionInstrumentationQualifier(cycleId = "fifteenMinuteRopFileCollectionCycleInstrumentation", ropPeriodInSeconds = 900)
    public FileCollectionCycleInstrumentation getFifteenMinFileCollectionCycleInstrumentation(
            final FileCollectionCycleInstrumentation fileCollectionCycleInstrumentation) {
        fileCollectionCycleInstrumentation.setCycleId("fifteenMinuteRopFileCollectionCycleInstrumentation");
        fileCollectionCycleInstrumentation.setRopPeriodInSeconds(900);
        logger.debug(RETURNING_INSTANCE_FOR_PARENTHESES, "fifteenMinuteRopFileCollectionCycleInstrumentation",
                System.identityHashCode(fileCollectionCycleInstrumentation));
        return fileCollectionCycleInstrumentation;
    }

    /**
     * Returns the {@link FileCollectionCycleInstrumentation} object for fifteen minute rop
     *
     * @param fileCollectionCycleInstrumentation
     *            {@link FileCollectionCycleInstrumentation} object that injected runtime.
     * @return {@link FileCollectionCycleInstrumentation} object for fifteen minute rop
     */
    @Produces
    @FileCollectionInstrumentationQualifier(cycleId = "thirtyMinuteRopFileCollectionCycleInstrumentation", ropPeriodInSeconds = 1800)
    public FileCollectionCycleInstrumentation getThirtyMinFileCollectionCycleInstrumentation(
            final FileCollectionCycleInstrumentation fileCollectionCycleInstrumentation) {
        fileCollectionCycleInstrumentation.setCycleId("thirtyMinuteRopFileCollectionCycleInstrumentation");
        fileCollectionCycleInstrumentation.setRopPeriodInSeconds(1800);
        logger.debug(RETURNING_INSTANCE_FOR_PARENTHESES, "thirtyMinuteRopFileCollectionCycleInstrumentation",
                System.identityHashCode(fileCollectionCycleInstrumentation));
        return fileCollectionCycleInstrumentation;
    }

    /**
     * Returns the {@link FileCollectionCycleInstrumentation} object for fifteen minute rop
     *
     * @param fileCollectionCycleInstrumentation
     *            {@link FileCollectionCycleInstrumentation} object that injected runtime.
     * @return {@link FileCollectionCycleInstrumentation} object for fifteen minute rop
     */
    @Produces
    @FileCollectionInstrumentationQualifier(cycleId = "oneHourRopFileCollectionCycleInstrumentation", ropPeriodInSeconds = 3600)
    public FileCollectionCycleInstrumentation getOneHourFileCollectionCycleInstrumentation(
            final FileCollectionCycleInstrumentation fileCollectionCycleInstrumentation) {
        fileCollectionCycleInstrumentation.setCycleId("oneHourRopFileCollectionCycleInstrumentation");
        fileCollectionCycleInstrumentation.setRopPeriodInSeconds(3600);
        logger.debug(RETURNING_INSTANCE_FOR_PARENTHESES, "oneHourRopFileCollectionCycleInstrumentation",
                System.identityHashCode(fileCollectionCycleInstrumentation));
        return fileCollectionCycleInstrumentation;
    }

    /**
     * Returns the {@link FileCollectionCycleInstrumentation} object for fifteen minute rop
     *
     * @param fileCollectionCycleInstrumentation
     *            {@link FileCollectionCycleInstrumentation} object that injected runtime.
     * @return {@link FileCollectionCycleInstrumentation} object for fifteen minute rop
     */
    @Produces
    @FileCollectionInstrumentationQualifier(cycleId = "twelveHourRopFileCollectionCycleInstrumentation", ropPeriodInSeconds = 43200)
    public FileCollectionCycleInstrumentation getTwelveHourFileCollectionCycleInstrumentation(
            final FileCollectionCycleInstrumentation fileCollectionCycleInstrumentation) {
        fileCollectionCycleInstrumentation.setCycleId("twelveHourRopFileCollectionCycleInstrumentation");
        fileCollectionCycleInstrumentation.setRopPeriodInSeconds(43200);
        logger.debug(RETURNING_INSTANCE_FOR_PARENTHESES, "twelveHourRopFileCollectionCycleInstrumentation",
                System.identityHashCode(fileCollectionCycleInstrumentation));
        return fileCollectionCycleInstrumentation;
    }

    /**
     * Returns the {@link FileCollectionCycleInstrumentation} object for fifteen minute rop
     *
     * @param fileCollectionCycleInstrumentation
     *            {@link FileCollectionCycleInstrumentation} object that injected runtime.
     * @return {@link FileCollectionCycleInstrumentation} object for twentyFour Hour rop
     */
    @Produces
    @FileCollectionInstrumentationQualifier(cycleId = "twentyFourHourRopFileCollectionCycleInstrumentation", ropPeriodInSeconds = 86400)
    public FileCollectionCycleInstrumentation getTwentyFourHourRopFileCollectionCycleInstrumentation(
            final FileCollectionCycleInstrumentation fileCollectionCycleInstrumentation) {
        fileCollectionCycleInstrumentation.setCycleId("twentyFourHourRopFileCollectionCycleInstrumentation");
        fileCollectionCycleInstrumentation.setRopPeriodInSeconds(86400);
        logger.debug(RETURNING_INSTANCE_FOR_PARENTHESES, "twentyFourHourRopFileCollectionCycleInstrumentation",
                System.identityHashCode(fileCollectionCycleInstrumentation));
        return fileCollectionCycleInstrumentation;
    }
}
