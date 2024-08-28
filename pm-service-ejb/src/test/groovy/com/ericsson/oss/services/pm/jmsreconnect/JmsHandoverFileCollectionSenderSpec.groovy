package com.ericsson.oss.services.pm.jmsreconnect

import javax.inject.Inject
import java.text.SimpleDateFormat

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.service.pm.jmsreconnect.JMSFailOverEvent
import com.ericsson.oss.service.pm.jmsreconnect.JMSFailoverHandler
import com.ericsson.oss.service.pm.jmsreconnect.JMSLostFileCollectionTasksHandler
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper
import com.ericsson.oss.services.pm.collection.cache.FileCollectionTaskCacheWrapper
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper
import com.ericsson.oss.services.pm.initiation.util.RopTime
import com.ericsson.oss.services.pm.time.TimeGenerator

class JmsHandoverFileCollectionSenderSpec extends SkeletonSpec {

    @ObjectUnderTest
    JMSFailoverHandler pmijmsFailoverHandler

    @Inject
    @Modeled
    EventSender<MediationTaskRequest> eventSender

    @Inject
    FileCollectionTaskCacheWrapper fileCollectionTaskCache

    @Inject
    FileCollectionActiveTaskCacheWrapper fileCollectionActiveTasksCache

    @Inject
    JMSLostFileCollectionTasksHandler pmiJMSLostFileCollectionTasksHandler

    @Inject
    SupportedRopTimes supportedRopTimes;

    @ImplementationInstance
    TimeGenerator timeGenerator = Mock(TimeGenerator)

    @ImplementationInstance
    JMSFailOverEvent event = mock(JMSFailOverEvent)

    static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")

    def "verify that when handover event is received all valid tasks are sent for collection"() {
        given: "4 Celltrace, Stats and Ctr tasks from previous rops and 2 Celltrace, Stats and Ctr tasks for current rop"
        timeGenerator.currentTimeMillis() >> getTimeInMillis("15/05/2016 12:37:45")
        RopTime currentRop = new RopTime(timeGenerator.currentTimeMillis(), 900)
        RopTime lastRop = new RopTime(timeGenerator.currentTimeMillis(), 900).getLastROP(1)
        RopTime twoRopsAgo = new RopTime(timeGenerator.currentTimeMillis(), 900).getLastROP(2)
        createFileCollectionTasks(2, [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.CELLTRAFFIC], 900, currentRop);
        createFileCollectionTasks(2, [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.CELLTRAFFIC], 900, lastRop);
        createFileCollectionTasks(2, [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.CELLTRAFFIC], 900, twoRopsAgo);
        pmiJMSLostFileCollectionTasksHandler.setSentFileCollectionTasks("900", fileCollectionTaskCache.getAllTasksSorted());
        and: "Node exists in dps"
        nodeUtil.builder('1').pmEnabled(true).build()
        when:
        pmijmsFailoverHandler.handleFailOver(event)
        pmijmsFailoverHandler.sendLostFileCollectionTasks()
        then: "tasks are sent in bulk grouped by subscription type"
        3 * eventSender.send({ result -> result.size() == 4 } as List<FileCollectionTaskRequest>, _)
    }

    def "verify that when handover event is received all valid tasks are sent for collection from fileCollectionTaskCache if fileCollectionTaskIds empty"() {
        given: "4 Celltrace, Stats and Ctr tasks from previous rops and 2 Celltrace, Stats and Ctr tasks for current rop"
        timeGenerator.currentTimeMillis() >> getTimeInMillis("15/05/2016 12:37:45")
        RopTime currentRop = new RopTime(timeGenerator.currentTimeMillis(), 900)
        RopTime lastRop = new RopTime(timeGenerator.currentTimeMillis(), 900).getLastROP(1)
        RopTime twoRopsAgo = new RopTime(timeGenerator.currentTimeMillis(), 900).getLastROP(2)
        createFileCollectionTasks(2, [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.CELLTRAFFIC], 900, currentRop);
        createFileCollectionTasks(2, [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.CELLTRAFFIC], 900, lastRop);
        createFileCollectionTasks(2, [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.CELLTRAFFIC], 900, twoRopsAgo);

        pmiJMSLostFileCollectionTasksHandler.setSentFileCollectionTasks("900", getEmptyFileCollectionTaskFromCache());

        and: "Node exists in dps"
        nodeUtil.builder('1').pmEnabled(true).build()
        when:
        pmijmsFailoverHandler.handleFailOver(event)
        pmijmsFailoverHandler.sendLostFileCollectionTasks()
        then: "tasks are sent in bulk grouped by subscription type"
        3 * eventSender.send({ result -> result.size() == 4 } as List<FileCollectionTaskRequest>, _)
    }

    def "verify that when handover event is received no tasks will be sent for collection if there are only tasks in the cache for the current rop"() {
        given:
        timeGenerator.currentTimeMillis() >> getTimeInMillis("15/05/2016 12:37:45")
        RopTime currentRop = new RopTime(timeGenerator.currentTimeMillis(), 900)
        createFileCollectionTasks(2, [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.CELLTRAFFIC], 900, currentRop);
        and: "Node exists in dps"
        nodeUtil.builder('1').pmEnabled(true).build()
        when:
        pmijmsFailoverHandler.handleFailOver(event)
        then: "tasks are sent in bulk grouped by subscription type"
        0 * eventSender.send(_ as List<FileCollectionTaskRequest>, _)
    }

    def "verify that when handover event is received all valid tasks of one minutes and fifteen minutes are sent for collection"() {
        given: "4 Celltrace, Stats and Ctr tasks from previous rops and 2 Celltrace, Stats and Ctr tasks for current rop"
        timeGenerator.currentTimeMillis() >> getTimeInMillis("15/05/2016 12:37:45")
        RopTime currentRop = new RopTime(timeGenerator.currentTimeMillis(), 900)
        RopTime lastRop = new RopTime(timeGenerator.currentTimeMillis(), 900).getLastROP(1)
        RopTime twoRopsAgo = new RopTime(timeGenerator.currentTimeMillis(), 900).getLastROP(2)
        createFileCollectionTasks(2, [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE, SubscriptionType.CELLTRAFFIC], 900, currentRop);
        createFileCollectionTasks(2, [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE], 900, lastRop);
        createFileCollectionTasks(2, [SubscriptionType.STATISTICAL, SubscriptionType.CELLTRACE], 900, twoRopsAgo);
        createFileCollectionTasks(2, [SubscriptionType.EBM], 60, lastRop);
        createFileCollectionTasks(2, [SubscriptionType.EBM], 60, twoRopsAgo);

        pmiJMSLostFileCollectionTasksHandler.setSentFileCollectionTasks("900", getFileCollectionTaskFromCache(900));
        and: "Node exists in dps"
        nodeUtil.builder('1').pmEnabled(true).build()
        when:
        pmijmsFailoverHandler.handleFailOver(event)
        pmijmsFailoverHandler.sendLostFileCollectionTasks()
        then: "tasks are sent in bulk grouped by subscription type"
        3 * eventSender.send({ result -> result.size() == 4 } as List<FileCollectionTaskRequest>, _)
    }

    def createFileCollectionTasks(int count, List<SubscriptionType> subscriptionTypes, int ropPeriod, RopTime ropTime) {
        subscriptionTypes.each {
            for (int i = 0; i < count; i++) {
                FileCollectionTaskRequest fileCollectionTaskRequest = new FileCollectionTaskRequest('NetworkElement=1', UUID.randomUUID().
                        toString(), it.name(), ropTime.getCurrentRopStartTimeInMilliSecs(), ropPeriod, false);
                FileCollectionTaskWrapper collectionTask = new FileCollectionTaskWrapper(fileCollectionTaskRequest,
                        ropTime.getCurrentROPPeriodEndTime(), supportedRopTimes.getRopTime(ropPeriod))
                fileCollectionTaskCache.addTask(collectionTask)
            }
        }
    }

    private static long getTimeInMillis(String time) {
        return simpleDateFormat.parse(time).getTime()
    }

    private List<FileCollectionTaskWrapper> getFileCollectionTaskFromCache(Long ropPeriod) {
        List<FileCollectionTaskWrapper> taskList = fileCollectionTaskCache.getAllTasksSorted();
        List<FileCollectionTaskWrapper> ropSpecificList = new ArrayList<>();
        for (FileCollectionTaskWrapper task : taskList) {
            if (task.getRopTimeInfo().getRopTimeInSeconds() == ropPeriod) {
                ropSpecificList.add(task);
            }
        }
        return ropSpecificList;
    }

    private List<FileCollectionTaskWrapper> getEmptyFileCollectionTaskFromCache() {
        List<FileCollectionTaskWrapper> ropSpecificList = new ArrayList<>();
        return ropSpecificList;
    }
}
