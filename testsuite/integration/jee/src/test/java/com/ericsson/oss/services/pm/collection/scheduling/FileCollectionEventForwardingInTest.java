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

package com.ericsson.oss.services.pm.collection.scheduling;

import static org.awaitility.Awaitility.await;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;

import org.awaitility.Duration;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender;
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled;
import com.ericsson.oss.services.pm.collection.events.FileCollectionEvent;
import com.ericsson.oss.services.pm.collection.events.FileCollectionResult;
import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;
import com.ericsson.oss.services.pm.listeners.FileCollectionEventForwardingListener;

@RunWith(Arquillian.class)
public class FileCollectionEventForwardingInTest extends InputBaseArquillian {

    @Inject
    @Modeled
    private EventSender<FileCollectionResult> fileCollectionResultSender;

    @Inject
    @Modeled
    private EventSender<FileCollectionEvent> fileCollectionEventSender;

    @Inject
    private FileCollectionEventForwardingListener fcForwardingListener;

    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void should_forward_file_collection_event_to_queue() throws InterruptedException {
        fcForwardingListener.clear();

        final List<FileCollectionResult> jobList = new ArrayList<>();

        final FileCollectionResult fileCollectionResultJob1 = new FileCollectionResult();
        fileCollectionResultJob1.setJobId("1234");
        fileCollectionResultJob1.setNodeAddress("NetworkElement=Node1");
        jobList.add(fileCollectionResultJob1);

        final FileCollectionResult fileCollectionResultJob2 = new FileCollectionResult();
        fileCollectionResultJob2.setJobId("12345");
        fileCollectionResultJob2.setNodeAddress("NetworkElement=Node2");
        jobList.add(fileCollectionResultJob2);

        final FileCollectionResult fileCollectionResultJob3 = new FileCollectionResult();
        fileCollectionResultJob3.setJobId("12346");
        fileCollectionResultJob3.setNodeAddress("NetworkElement=Node2");
        jobList.add(fileCollectionResultJob3);

        fileCollectionResultSender.send(fileCollectionResultJob1, "//global/ClusteredFileCollectionResultNotificationQueue");

        fileCollectionResultSender.send(fileCollectionResultJob2, "//global/ClusteredFileCollectionResultNotificationQueue");

        fileCollectionResultSender.send(fileCollectionResultJob3, "//global/ClusteredFileCollectionResultNotificationQueue");

        await().pollDelay(Duration.ONE_SECOND).pollInterval(Duration.ONE_SECOND).atMost(Duration.TEN_SECONDS)
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        return fcForwardingListener.getTotalJobsReceived() == 3;
                    }
                });

        Assert.assertEquals(3, fcForwardingListener.getTotalJobsReceived());
        Assert.assertEquals(1, fcForwardingListener.getNumberOfJobsReceivedforSpecificNodeAddress("NetworkElement=Node1"));
        Assert.assertEquals(2, fcForwardingListener.getNumberOfJobsReceivedforSpecificNodeAddress("NetworkElement=Node2"));
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void should_forward_file_collection_event_to_topic() throws InterruptedException {
        fcForwardingListener.clear();

        final List<FileCollectionEvent> eventList = new ArrayList<>();

        final FileCollectionEvent fileCollectionEvent1 = new FileCollectionEvent();
        fileCollectionEvent1.setNodeAddress("NetworkElement=Node1");
        eventList.add(fileCollectionEvent1);

        final FileCollectionEvent fileCollectionEvent2 = new FileCollectionEvent();
        fileCollectionEvent2.setNodeAddress("NetworkElement=Node2");
        eventList.add(fileCollectionEvent2);

        final FileCollectionEvent fileCollectionEvent3 = new FileCollectionEvent();
        fileCollectionEvent3.setNodeAddress("NetworkElement=Node2");
        eventList.add(fileCollectionEvent3);

        fileCollectionEventSender.send(fileCollectionEvent1);

        fileCollectionEventSender.send(fileCollectionEvent2);

        fileCollectionEventSender.send(fileCollectionEvent3);

        await().pollDelay(Duration.ONE_SECOND).pollInterval(Duration.ONE_SECOND).atMost(Duration.TEN_SECONDS)
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        return fcForwardingListener.getTotalEventsReceived() == 3;
                    }
                });

        Assert.assertEquals(3, fcForwardingListener.getTotalEventsReceived());
        Assert.assertEquals(1, fcForwardingListener.getNumberOfEventsReceivedforSpecificNodeAddress("NetworkElement=Node1"));
        Assert.assertEquals(2, fcForwardingListener.getNumberOfEventsReceivedforSpecificNodeAddress("NetworkElement=Node2"));
    }
}
