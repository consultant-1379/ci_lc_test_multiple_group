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

package com.ericsson.oss.services.pm.scheduling.cluster;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.sdk.cluster.MembershipChangeEvent;
import com.ericsson.oss.itpf.sdk.cluster.annotation.ServiceCluster;
import com.ericsson.oss.itpf.sdk.recording.EventLevel;
import com.ericsson.oss.itpf.sdk.recording.SystemRecorder;
import com.ericsson.oss.services.pm.common.startup.ClusterMembershipChangeAction;

/**
 * {@inheritDoc}
 */
@ApplicationScoped
public class ClusterMembershipListener implements MembershipListener {

    private static final String CLUSTER_MEMBERSHIP_EVENT = "CLUSTER_MEMBERSHIP_EVENT";
    private static final String CURRENT_PM_INSTANCE = "CURRENT_PM_INSTANCE";

    private static final Logger logger = LoggerFactory.getLogger(ClusterMembershipListener.class);

    private boolean master = true;

    @Inject
    private SystemRecorder systemRecorder;

    @Inject
    private ClusterMembershipChangeAction clusterMembershipChangeAction;

    /**
     * {@inheritDoc}
     */
    public void listenForMembershipChange(@Observes @ServiceCluster("PMServiceCluster") final MembershipChangeEvent event) {
        systemRecorder.recordEvent(CLUSTER_MEMBERSHIP_EVENT, EventLevel.COARSE, getClass().getSimpleName(), CURRENT_PM_INSTANCE, String.format(
                "Change in ClusterMembership master/slave : isMaster [%b], current cluster members: %s, members that left: %s",
                event.isMaster(), event.getAllClusterMembers(), event.getRemovedMembers()));
        if (event.isMaster()) {
            logger.debug("-------------------------- It's a master node. --------------------------");
            master = true;
            clusterMembershipChangeAction.executeAsMaster();
        } else {
            logger.debug("-------------------------- It's a slave node. --------------------------");
            master = false;
            clusterMembershipChangeAction.executeAsSlave();
        }

        final int numberOfMembers = event.getCurrentNumberOfMembers();
        logger.debug("-------------------------- Numbers of cluster members are [{}]", numberOfMembers);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isMaster() {
        return master;
    }
}
