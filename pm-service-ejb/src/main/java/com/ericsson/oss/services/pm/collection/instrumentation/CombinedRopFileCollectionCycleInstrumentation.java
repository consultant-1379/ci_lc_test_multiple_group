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

package com.ericsson.oss.services.pm.collection.instrumentation;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJBException;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;

import com.ericsson.oss.itpf.sdk.recording.EventLevel;
import com.ericsson.oss.itpf.sdk.recording.SystemRecorder;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.services.pm.initiation.constants.PmicLogCommands;

/**
 * FileCollectionInstrumentation class contains information of ROP collection statistics for 1 min and 15 min ROP E.g. while pm-service is running
 * this class would hold the collection statistics for a complete ROP. External system monitoring tool will use this data to show collection
 * statistics on the interval of 1 minute,.
 *
 * @author ekamkal
 */
@ApplicationScoped
@Interceptors(CombinedRopFileCollectionInterceptor.class)
public class CombinedRopFileCollectionCycleInstrumentation {

    @Inject
    @FileCollectionInstrumentationQualifier(cycleId = "oneMinuteRopFileCollectionCycleInstrumentation", ropPeriodInSeconds = 60)
    private FileCollectionCycleInstrumentation oneMinuteRopFileCollectionCycleInstrumentation;

    @Inject
    @FileCollectionInstrumentationQualifier(cycleId = "fiveMinuteRopFileCollectionCycleInstrumentation", ropPeriodInSeconds = 300)
    private FileCollectionCycleInstrumentation fiveMinuteRopFileCollectionCycleInstrumentation;

    @Inject
    @FileCollectionInstrumentationQualifier(cycleId = "fifteenMinuteRopFileCollectionCycleInstrumentation", ropPeriodInSeconds = 900)
    private FileCollectionCycleInstrumentation fifteenMinuteRopFileCollectionCycleInstrumentation;

    @Inject
    @FileCollectionInstrumentationQualifier(cycleId = "thirtyMinuteRopFileCollectionCycleInstrumentation", ropPeriodInSeconds = 1800)
    private FileCollectionCycleInstrumentation thirtyMinuteRopFileCollectionCycleInstrumentation;

    @Inject
    @FileCollectionInstrumentationQualifier(cycleId = "oneHourRopFileCollectionCycleInstrumentation", ropPeriodInSeconds = 3600)
    private FileCollectionCycleInstrumentation oneHourRopFileCollectionCycleInstrumentation;

    @Inject
    @FileCollectionInstrumentationQualifier(cycleId = "twelveHourRopFileCollectionCycleInstrumentation", ropPeriodInSeconds = 43200)
    private FileCollectionCycleInstrumentation twelveHourRopFileCollectionCycleInstrumentation;

    @Inject
    @FileCollectionInstrumentationQualifier(cycleId = "twentyFourHourRopFileCollectionCycleInstrumentation", ropPeriodInSeconds = 86400)
    private FileCollectionCycleInstrumentation twentyFourHourRopFileCollectionCycleInstrumentation;

    @Inject
    private SystemRecorder systemRecorder;

    private final Map<Integer, FileCollectionCycleInstrumentation> ropToBean = new HashMap<>();

    @PostConstruct
    public void initialize() {
        ropToBean.put(RopPeriod.ONE_MIN.getDurationInSeconds(), oneMinuteRopFileCollectionCycleInstrumentation);
        ropToBean.put(RopPeriod.FIVE_MIN.getDurationInSeconds(), fiveMinuteRopFileCollectionCycleInstrumentation);
        ropToBean.put(RopPeriod.FIFTEEN_MIN.getDurationInSeconds(), fifteenMinuteRopFileCollectionCycleInstrumentation);
        ropToBean.put(RopPeriod.THIRTY_MIN.getDurationInSeconds(), thirtyMinuteRopFileCollectionCycleInstrumentation);
        ropToBean.put(RopPeriod.ONE_HOUR.getDurationInSeconds(), oneHourRopFileCollectionCycleInstrumentation);
        ropToBean.put(RopPeriod.TWELVE_HOUR.getDurationInSeconds(), twelveHourRopFileCollectionCycleInstrumentation);
        ropToBean.put(RopPeriod.ONE_DAY.getDurationInSeconds(), twentyFourHourRopFileCollectionCycleInstrumentation);
    }

    /**
     * Returns the rop interval object for {@link FileCollectionCycleInstrumentation}. This will be used in {@link
     * FileCollectionInstrumentation} to expose the 15 minute rop values.
     *
     * @return rop interval object for {@link FileCollectionCycleInstrumentation}.
     */
    public FileCollectionCycleInstrumentation getFileCollectionCycleInstrumentation(final int ropInSeconds) {
        return ropToBean.get(ropInSeconds);
    }

    /**
     * Setting Instrumentation data for successful and failed file collection. Updating start and end times for current ROP
     *
     * @param ropPeriodInSeconds
     *            - the rop period in seconds
     * @param numberOfSuccessfulHits
     *            - number of successful files collected
     * @param numberOfFailedHits
     *            - number of files that failed to be collected
     * @param aggregatedBytesStored
     *            - number of bytes stored
     * @param aggregatedBytesTransferred
     *            - number of bytes transferred
     */
    public void updateDataForSuccessAndFailure(final long ropPeriodInSeconds, final long numberOfSuccessfulHits,
            final long numberOfFailedHits, final long aggregatedBytesStored,
            final long aggregatedBytesTransferred) {
        getFileCollectionCycleInstrumentation((int) ropPeriodInSeconds).updateDataForSuccessAndFailure(numberOfSuccessfulHits, numberOfFailedHits,
                aggregatedBytesStored, aggregatedBytesTransferred);
    }

    /**
     * Write data for DDC current ROP been instrumented when graceful shutdown occure
     */
    @PreDestroy
    protected void onDestroy() {
        try {
            for (final Integer ropInSeconds : ropToBean.keySet()) {
                getFileCollectionCycleInstrumentation(ropInSeconds).writeOutCurrentBufferedDate();
            }
        } catch (final Exception ex) {
            systemRecorder.recordEvent(PmicLogCommands.PMIC_PRE_DESTROY.getDescription(), EventLevel.COARSE, this.getClass()
                    .getSimpleName(),
                    "INSTRUMENTATION EXCEPTION", "onDestroy");

            throw new EJBException("Failed to execute onDestroy", ex);
        }
    }

    /**
     * Reset the counters for the current ROP been instrumented
     *
     * @param ropTimeInSeconds
     *            - the rop period in seconds
     */
    public void resetCurrentROP(final int ropTimeInSeconds) {
        getFileCollectionCycleInstrumentation(ropTimeInSeconds).resetCurrentROP();
    }
}
