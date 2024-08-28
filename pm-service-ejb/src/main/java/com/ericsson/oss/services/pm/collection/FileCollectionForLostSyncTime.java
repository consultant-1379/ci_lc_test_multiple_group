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

package com.ericsson.oss.services.pm.collection;

import static com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants.ONE_HOUR_IN_MILLISECONDS;

import java.util.Date;

import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.util.TimeGenerator;
import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.initiation.util.RopTime;

/**
 * Class to check File collection needed for lost sync time.
 */
public class FileCollectionForLostSyncTime {

    @Inject
    private Logger logger;

    @Inject
    private TimeGenerator timeGenerator;

    @Inject
    private SupportedRopTimes supportedRopTimes;

    /**
     * Algorithm description: Since File Collection Trigger occurs after a specified amount of time specified by
     * ropTimeInfo.getCollectionDelayInMilliSecond() the number of ROP to collect when node reconnects depends
     * on when the reconnection occurs related to that trigger. If reconnection occurs after File Collection Trigger
     * for the current ROP, the file has been lost, so the ROP must be included in the count, otherwise it has to be
     * excluded. The same for the ROP in which the synch was lost. One way to accomplish this is to subtract
     * ropTimeInfo.getCollectionDelayInMilliSecond() from both times: this way the results surely fall in a ROP
     * that has to be recovered. Then algorithm uses actual synch ROP start time and unsynch ROP start time
     * (i.e. normalized values) in order to calculate the exact amount of ROPs.
     *
     * @param fromTime
     *            - time when sync was lost
     * @param ropSizeInSeconds
     *            - duration of the Record Output Period in seconds
     * @param ropRecoveryPeriodInHours
     *            - recovery period for current Record Output Period in hours.
     * @return - returns number of Record Output Period to be collected
     */
    public int getTotalRopsToCollect(final Date fromTime, final int ropSizeInSeconds, final int ropRecoveryPeriodInHours) {
        try {
            final RopTimeInfo ropTimeInfo = supportedRopTimes.getRopTime(ropSizeInSeconds);
            final long currentTime = fromTime.getTime() - ropTimeInfo.getCollectionDelayInMilliSecond();
            final long ropEndTime = timeGenerator.currentTimeMillis() - ropTimeInfo.getCollectionDelayInMilliSecond();
            if (currentTime <= 0 || ropEndTime <= 0) {
                return 0;
            }
            final RopTime startRopTime = new RopTime(new Date(currentTime).getTime(), ropTimeInfo.getRopTimeInSeconds());
            final RopTime endRopTime = new RopTime(new Date(ropEndTime).getTime(), ropTimeInfo.getRopTimeInSeconds());
            long totalMilliSec = endRopTime.getCurrentRopStartTimeInMilliSecs() - startRopTime.getCurrentRopStartTimeInMilliSecs();
            final long ropRecoveryMilliSec = ropRecoveryPeriodInHours * ONE_HOUR_IN_MILLISECONDS;
            if (totalMilliSec > ropRecoveryMilliSec) {
                totalMilliSec = ropRecoveryMilliSec;
            }
            return (int) (totalMilliSec / ropTimeInfo.getRopTimeInMilliSecond());
        } catch (final Exception e) {
            logger.debug("Error while calculating total Rops to collect: ", e);
            logger.info("Error while calculating total Rops to collect: {}", e.getMessage());
            return 0;
        }
    }
}
