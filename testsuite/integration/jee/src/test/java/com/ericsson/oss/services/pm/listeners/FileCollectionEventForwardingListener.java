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

package com.ericsson.oss.services.pm.listeners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ejb.Singleton;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled;
import com.ericsson.oss.services.pm.collection.events.FileCollectionEvent;
import com.ericsson.oss.services.pm.collection.events.FileCollectionResult;

@Singleton
public class FileCollectionEventForwardingListener {

    private final Map<String, List<FileCollectionResult>> jobsReceived = new HashMap<>();
    private final Map<String, List<FileCollectionEvent>> eventsReceived = new HashMap<>();
    @Inject
    private Logger logger;

    public void processMediationTaskRequest(
            @Observes @Modeled(channelId = "//global/ClusteredFileCollectionResultNotificationQueue") final FileCollectionResult job) {

        logger.debug("********** FileCollectionResult Forwarded {} received. ********* ", job);
        List<FileCollectionResult> jobsNode = jobsReceived.get(job.getNodeAddress());
        if (jobsNode == null) {
            jobsNode = new ArrayList<>();
            jobsReceived.put(job.getNodeAddress(), jobsNode);
        }
        jobsNode.add(job);
    }

    public void processFileCollectionEventRequest(
            @Observes @Modeled(channelId = "//global/FileCollectionResultNotificationTopic") final FileCollectionEvent event) {

        logger.debug("********** FileCollectionEvent Forwarded {} received. ********* ", event);
        List<FileCollectionEvent> nodeFileCollectionEvent = eventsReceived.get(event.getNodeAddress());
        logger.debug("File Collection Event {} for node address {}", nodeFileCollectionEvent, event.getNodeAddress());
        if (nodeFileCollectionEvent == null) {
            nodeFileCollectionEvent = new ArrayList<>();
            eventsReceived.put(event.getNodeAddress(), nodeFileCollectionEvent);
            logger.debug("Event {} updated with node address {}", eventsReceived, event.getNodeAddress());
        }
        nodeFileCollectionEvent.add(event);
    }

    public int getNumberOfJobsReceivedforSpecificNodeAddress(final String nodeAddress) {
        int numberOfJobs = 0;
        final List<FileCollectionResult> jobsNode = jobsReceived.get(nodeAddress);
        if (jobsNode != null) {
            numberOfJobs = jobsNode.size();
        }
        return numberOfJobs;
    }

    public int getNumberOfEventsReceivedforSpecificNodeAddress(final String nodeAddress) {
        int numberOfEvents = 0;
        final List<FileCollectionEvent> eventsNode = eventsReceived.get(nodeAddress);
        if (eventsNode != null) {
            numberOfEvents = eventsNode.size();
        }
        return numberOfEvents;
    }

    public int getTotalJobsReceived() {
        int total = 0;
        for (final List<FileCollectionResult> jobsNode : jobsReceived.values()) {
            total += jobsNode.size();
        }
        return total;
    }

    public int getTotalEventsReceived() {
        int total = 0;
        for (final List<FileCollectionEvent> eventsNode : eventsReceived.values()) {
            total += eventsNode.size();
        }
        return total;
    }

    public void clear() {
        jobsReceived.clear();
        eventsReceived.clear();
    }
}
