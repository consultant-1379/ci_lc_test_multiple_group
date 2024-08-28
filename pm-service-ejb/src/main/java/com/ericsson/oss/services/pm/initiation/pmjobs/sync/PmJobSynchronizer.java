/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.pmjobs.sync;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.pmjob.PmJob;
import com.ericsson.oss.pmic.dto.pmjob.enums.PmJobStatus;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.PmJobService;

/**
 * This class provides the sync methods for PmJobs
 */
public class PmJobSynchronizer {

    @Inject
    private Logger logger;

    @Inject
    private PmJobService pmJobService;

    /**
     * Sync All PmJobs in DPS for the given subscription. If Pm job is not available then this method will create PmJob and set the state as UNKNOWN.
     *
     * @param subscription
     *         Subscription object with nodes loaded (if has nodes)
     * @param nodeFdns
     *         Set of nodes
     */
    public void syncAllPmJobsInDPSForSubscription(final Subscription subscription, final Set<String> nodeFdns) {
        syncAllPmJobsInDPSForSubscription(subscription.getId(), subscription.getRop().getDurationInSeconds(), nodeFdns,
                ProcessType.getProcessType(subscription.getType().name()));
    }

    /**
     * Sync all PmJob in DPS for the given Subscription
     *
     * @param subscriptionId
     *         Subscription Id
     * @param ropPeriodInSeconds
     *         Rop period is seconds
     * @param nodeFdns
     *         Set of nodes on which sync is needed
     * @param processType
     *         Process Type
     */
    public void syncAllPmJobsInDPSForSubscription(final Long subscriptionId, final int ropPeriodInSeconds, final Set<String> nodeFdns,
                                                  final ProcessType processType) {
        logger.debug("Sync PmJobs in DPS!");
        final Map<String, String> pmJobNamesToNodeFdn = new HashMap<>();
        for (final String nodeFdn : nodeFdns) {
            final String pmJobName = PmJob.createName(subscriptionId.toString(), nodeFdn, processType);
            pmJobNamesToNodeFdn.put(pmJobName, nodeFdn);
        }
        try {
            final List<PmJob> pmJobs = pmJobService.findAllBySubscriptionId(subscriptionId);
            final Iterator<PmJob> moItr = pmJobs.iterator();
            while (!pmJobNamesToNodeFdn.isEmpty() && moItr.hasNext()) {
                final PmJob pmJob = moItr.next();
                if (pmJobNamesToNodeFdn.containsKey(pmJob.getName())) {
                    pmJobNamesToNodeFdn.remove(pmJob.getName());
                    logger.debug("No update needed for PmJob {} in DPS!", pmJob.getName());
                }
            }
        } catch (final DataAccessException | RuntimeDataAccessException exception) {
            logger.error("Exception received from DPS: {}", exception.getMessage());
            logger.info("Exception received from DPS.", exception);
        }
        // create new PmJobInfo
        createPmJobs(pmJobNamesToNodeFdn, ropPeriodInSeconds, subscriptionId, processType);
    }

    private void createPmJobs(final Map<String, String> pmJobFdnsToNodeFdn, final int ropPeriodInSeconds, final Long subscriptionId,
                              final ProcessType processType) {
        for (final Map.Entry<String, String> entry : pmJobFdnsToNodeFdn.entrySet()) {
            final PmJob pmJob = new PmJob();
            pmJob.setProcessType(processType);
            pmJob.setStatus(PmJobStatus.UNKNOWN);
            pmJob.setSubscriptionId(subscriptionId);
            pmJob.setId(entry.getKey());
            pmJob.setName(entry.getKey());
            pmJob.setRopPeriod(ropPeriodInSeconds);
            pmJob.setErrorCode((short) 0);
            pmJob.setNodeName(Node.getNodeNameFromNodeFdn(entry.getValue()));
            try {
                pmJobService.saveOrUpdateWithRetry(pmJob);
                logger.info("PmJob {} created in DPS.", entry.getKey());
            } catch (final DataAccessException | RetryServiceException e) {
                logger.error("DataAccessException was thrown while trying to create Unknown PmJob [{}] in database. Exception: {}",
                        entry.getKey(), e.getMessage());
                logger.info("DataAccessException was thrown while trying to create Unknown PmJob.", e);
            }
        }
    }
}
