/*
 * COPYRIGHT Ericsson 2017
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.common.eventsender;

import java.util.List;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.eventbus.EventConfiguration;
import com.ericsson.oss.itpf.sdk.eventbus.EventConfigurationBuilder;
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender;
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled;
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;

/**
 * Helper class for sending {@link com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest}
 */

@Stateless
@Local(FileCollectionSender.class)
public class FileCollectionTaskSenderImpl implements FileCollectionSender {
    @Inject
    @Modeled
    private EventSender<MediationTaskRequest> eventSender;

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void sendTasksOneByOne(final List<MediationTaskRequest> tasks, final int priority) {
        final EventConfiguration eventConfiguration = new EventConfigurationBuilder().priority(priority).build();
        for (final MediationTaskRequest taskRequest : tasks) {
            eventSender.send(taskRequest, eventConfiguration);
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void sendTasksBatch(final List<MediationTaskRequest> tasks) {
        eventSender.send(tasks);
    }

    @Override
    public void sendTasksBatch(final List<MediationTaskRequest> tasks, final int priority) {
        final EventConfiguration eventConfiguration =
            new EventConfigurationBuilder().addEventProperty("eventPriority", priority).priority(priority).build();
        eventSender.send(tasks, eventConfiguration);
    }
}
