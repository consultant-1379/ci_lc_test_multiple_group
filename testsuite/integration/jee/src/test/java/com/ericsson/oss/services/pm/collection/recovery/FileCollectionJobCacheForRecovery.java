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

package com.ericsson.oss.services.pm.collection.recovery;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;
import javax.ejb.Singleton;
import javax.enterprise.event.Event;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.config.provider.PropertyScope;
import com.ericsson.oss.itpf.sdk.config.provider.ProvidedProperty;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionScheduledRecoveryCacheWrapper;

/**
 * This class is added to pm-service ear so that at startup it can add nodes to FileCollection cache to test recovery scenario.
 *
 * @author ekamkal
 */
@Singleton
public class FileCollectionJobCacheForRecovery {

    @Inject
    private FileCollectionScheduledRecoveryCacheWrapper fileCollectionScheduledRecoveryCacheWrapper;

    @Inject
    private FileCollectionActiveTaskCacheWrapper fileCollectionActiveTaskCacheWrapper;

    @Inject
    private Event<ProvidedProperty> providedPropertyEvent;

    public void addNodesForScheduledRecoveryTest() {
        final int ropPeriod = 900;
        final String recoveryNodeFdn1 = "NetworkElement=WithInRecovery0001";
        final String recoveryNodeFdn2 = "NetworkElement=WithInRecovery0002";
        final String processType = ProcessType.STATS.name();
        final long twoHoursAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2);
        final long oneHourAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
        final ProcessRequestVO processRequest1 = new ProcessRequestVO.ProcessRequestVOBuilder(recoveryNodeFdn1, ropPeriod, processType)
                .startTime(twoHoursAgo).endTime(oneHourAgo).build();
        final ProcessRequestVO processRequest2 = new ProcessRequestVO.ProcessRequestVOBuilder(recoveryNodeFdn2, ropPeriod, processType)
                .startTime(twoHoursAgo).endTime(oneHourAgo).build();

        final String nonRecoveryNodeFdn1 = "NetworkElement=OutOfRecovery0001";
        final String nonRecoveryNodeFdn2 = "NetworkElement=OutOfRecovery0002";
        final long twentySixHoursAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(26);
        final long twentyFiveHoursAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(25);
        final ProcessRequestVO processRequest3 = new ProcessRequestVO.ProcessRequestVOBuilder(nonRecoveryNodeFdn1, ropPeriod, processType)
                .startTime(twentySixHoursAgo).endTime(twentyFiveHoursAgo).build();
        final ProcessRequestVO processRequest4 = new ProcessRequestVO.ProcessRequestVOBuilder(nonRecoveryNodeFdn2, ropPeriod, processType)
                .startTime(twentySixHoursAgo).endTime(twentyFiveHoursAgo).build();

        fileCollectionScheduledRecoveryCacheWrapper.addProcessRequest(processRequest1);
        fileCollectionScheduledRecoveryCacheWrapper.addProcessRequest(processRequest2);
        fileCollectionScheduledRecoveryCacheWrapper.addProcessRequest(processRequest3);
        fileCollectionScheduledRecoveryCacheWrapper.addProcessRequest(processRequest4);

        final String nodeFdnWithActiveScanner1 = "NetworkElement=NodeForFifteenMiute0001";
        final String nodeFdnWithActiveScanner2 = "NetworkElement=NodeForFifteenMiute0002";
        final long thirtyMinutesAgo = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30);

        final ProcessRequestVO processRequest5 = new ProcessRequestVO.ProcessRequestVOBuilder(nodeFdnWithActiveScanner1, ropPeriod, processType)
                .startTime(thirtyMinutesAgo).build();
        final ProcessRequestVO processRequest6 = new ProcessRequestVO.ProcessRequestVOBuilder(nodeFdnWithActiveScanner2, ropPeriod, processType)
                .startTime(thirtyMinutesAgo).build();
        fileCollectionActiveTaskCacheWrapper.addProcessRequest(processRequest5);
        fileCollectionActiveTaskCacheWrapper.addProcessRequest(processRequest6);
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, 30);
        final String time = cal.get(Calendar.HOUR_OF_DAY) + ":" + cal.get(Calendar.MINUTE) + ":" + cal.get(Calendar.SECOND);
        final String p = "scheduledRecoveryTime";
        providedPropertyEvent.fire(new ProvidedProperty(p, PropertyScope.GLOBAL, time, String.class));

    }
}
