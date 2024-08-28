/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2016
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.bdd.collection.schedulers

import static com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant.LAST_UETRACE_FILE_COLLECTION_TASKS_CREATED_FOR_ROP

import spock.lang.Unroll

import javax.ejb.Timer
import javax.ejb.TimerService
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.sdk.cluster.MembershipChangeEvent
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.cdi.test.util.builder.node.TestNetworkElementDpsUtils
import com.ericsson.oss.pmic.cdi.test.util.builder.subscription.UeTraceSubscriptionBuilder
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus
import com.ericsson.oss.services.pm.collection.cache.FileCollectionLastRopData
import com.ericsson.oss.services.pm.collection.cache.FileCollectionTaskCacheWrapper
import com.ericsson.oss.services.pm.collection.schedulers.UeTraceFileCollectionTaskManager
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener
import com.ericsson.oss.services.pm.initiation.util.RopTime
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener
import com.ericsson.oss.services.pm.time.TimeGenerator

class UeTraceFileCollectionTaskManagerSpec extends SkeletonSpec {

    @ObjectUnderTest
    UeTraceFileCollectionTaskManager ueTraceFileCollectionTaskManager

    @Inject
    private FileCollectionTaskCacheWrapper fileCollectionTaskCache

    @ImplementationInstance
    ConfigurationChangeListener configurationChangeListener = Mock(ConfigurationChangeListener)

    @Inject
    private EventSender<MediationTaskRequest> eventSender

    @ImplementationInstance
    TimerService timerService = Mock(TimerService)

    @ImplementationInstance
    TimeGenerator timeGenerator = Mock(TimeGenerator)

    @ImplementationInstance
    protected MembershipListener listener = new MembershipListener() {
        private boolean isMaster = true

        @Override
        void listenForMembershipChange(MembershipChangeEvent event) {
            isMaster = event.isMaster()
        }

        @Override
        boolean isMaster() {
            return isMaster
        }
    }

    @Inject
    private FileCollectionLastRopData fileCollectionLastRopData

    TestNetworkElementDpsUtils testNetworkElementDpsUtils = new TestNetworkElementDpsUtils(configurableDps)
    static SimpleDateFormat simpleDateFormat = new SimpleDateFormat('dd/MM/yyyy HH:mm:ss')
    static long currentTime = simpleDateFormat.parse('22/09/2000 12:07:30').getTime()
    static long recordedTime = simpleDateFormat.parse('22/09/2000 11:52:30').getTime()
    Timer oneMinuteTimer = Mock(Timer)
    Timer fifteenMinutetimer = Mock(Timer)

    def sgsnNodes = ['SGSN-16A-V1-CP0201', 'SGSN-16A-V1-CP0202']
    def erbsNodes = ['LTE01ERBS0001', 'LTE01ERBS0002']
    def radioNodes = ['LTE01DG200001', 'LTE01DG200002']
    def mixedModeRadioNodes = ['LTE01NR200001', 'LTE01NR200002']

    def NUMBER_OF_NODES = sgsnNodes.size() + erbsNodes.size() + radioNodes.size() + mixedModeRadioNodes.size()
    int FIFTEEN_MINUTES_IN_SECONDS = TimeUnit.MINUTES.toSeconds(15)
    int ONE_MINUTE_IN_SECONDS = TimeUnit.MINUTES.toSeconds(1)

    def setup() {
        fifteenMinutetimer.getInfo() >> FIFTEEN_MINUTES_IN_SECONDS
        oneMinuteTimer.getInfo() >> ONE_MINUTE_IN_SECONDS
    }

    def setupTimeStamps(long currentTime, long recordedTime) {
        timeGenerator.currentTimeMillis() >> currentTime
        fileCollectionLastRopData.recordRopStartTimeForUetraceTaskCreation(new RopTime(recordedTime, FIFTEEN_MINUTES_IN_SECONDS))
        fileCollectionLastRopData.recordRopStartTimeForUetraceTaskCreation(new RopTime(recordedTime, ONE_MINUTE_IN_SECONDS))
    }

    def 'Creating tasks for UE trace subscription'() {
        given: 'a network with some SGSN, ERBS and RadioNodes'
            addNodes()
        and: 'fixed time stamps'
            setupTimeStamps(currentTime, recordedTime)
        and: 'an active UE trace subscription'
            createSubscriptionBuilder(AdministrationState.ACTIVE, OperationalState.RUNNING).build()
        and: 'UE trace collection is enabled'
            configurationChangeListener.getUeTraceCollectionEnabled() >> true

        when: 'file collection is triggered'
            ueTraceFileCollectionTaskManager.onTimeout(fifteenMinutetimer)

        then: 'new tasks are created for all nodes in the network'
            fileCollectionTaskCache.size() == old(fileCollectionTaskCache.size()) + NUMBER_OF_NODES
        and: 'each task has UeTrace collection task priority of 7 '
            def expectedPriorityForUeTraceFileCollection = 7
        and : 'type as UETRACE with expected rop period'
            fileCollectionTaskCache.allTasks.each {
                it.fileCollectionTaskRequest.ropPeriod == FIFTEEN_MINUTES_IN_SECONDS
                it.fileCollectionTaskRequest.subscriptionType == SubscriptionType.UETRACE
                it.priority == expectedPriorityForUeTraceFileCollection
            }
    }

    def 'Creating tasks for UE trace subscription with no nodes present'() {
        given: 'a network with no nodes'
        and: 'fixed time stamps'
            setupTimeStamps(currentTime, recordedTime)
        and: 'an active UE trace subscription'
            createSubscriptionBuilder(AdministrationState.ACTIVE, OperationalState.RUNNING).build()
        and: 'UE trace collection is enabled'
            configurationChangeListener.getUeTraceCollectionEnabled() >> true

        when: 'file collection is triggered'
            ueTraceFileCollectionTaskManager.onTimeout(fifteenMinutetimer)

        then: 'no new tasks are created'
            fileCollectionTaskCache.size() == old(fileCollectionTaskCache.size())
    }

    @Unroll
    def 'Creating tasks for UE trace subscription when server is #description'() {
        given: 'a network with a node with EPS technologyDomain'
            createNodeMO('LTE01dg2ERBS00001', 'RadioNode', ['EPS'])
        and: 'fixed time stamps'
            setupTimeStamps(currentTime, recordedTime)
        and: 'an active UE trace subscription'
            createSubscriptionBuilder(AdministrationState.ACTIVE, OperationalState.RUNNING).build()
        and: 'UE trace collection is enabled'
            configurationChangeListener.getUeTraceCollectionEnabled() >> true
        and: 'server is #description'
            MembershipChangeEvent changeEvent = Mock()
            changeEvent.isMaster() >> isMaster
            listener.listenForMembershipChange(changeEvent)

        when: 'file collection is triggered'
            ueTraceFileCollectionTaskManager.onTimeout(fifteenMinutetimer)

        then: '#expectedNumberOfTasks new task(s) are created'
            fileCollectionTaskCache.size() == old(fileCollectionTaskCache.size()) + expectedNumberOfTasks

        where: 'server is running in #description mode'
            isMaster | description || expectedNumberOfTasks
            true     | 'master'    || 1
            false    | 'slave'     || 0
    }

    @Unroll
    def 'Creating tasks for UE trace subscription with scanner in state #administrativeState'() {
        given: 'a network with a node with EPS technologyDomain'
            createNodeMO('LTE01dg2ERBS00001', 'RadioNode', ['EPS'])
        and: 'fixed time stamps'
            setupTimeStamps(currentTime, recordedTime)
        and: 'an active UE trace subscription'
            createSubscriptionBuilder(AdministrationState.ACTIVE, OperationalState.RUNNING).build()
        and: 'UE trace collection is enabled'
            configurationChangeListener.getUeTraceCollectionEnabled() >> true

        when: 'file collection is triggered'
            ueTraceFileCollectionTaskManager.onTimeout(fifteenMinutetimer)

        then: 'one new task is created'
            fileCollectionTaskCache.size() == old(fileCollectionTaskCache.size()) + 1

        where: 'scanner has an administrative state of #administrativeState'
            administrativeState << AdministrationState.values()
    }

    @Unroll
    def 'Creating tasks for UE trace subscription when UE trace pib parameter is set to #ueTraceCollectionEnabled and node is of type #neType, technologyDomains #technologyDomains'() {
        given: 'a network with a node with type #neType with #technologyDomains'
            createNodeMO('LTE02', neType, technologyDomains)
        and: 'fixed time stamps'
            setupTimeStamps(currentTime, recordedTime)
        and: 'an active UE trace subscription'
            createSubscriptionBuilder(AdministrationState.ACTIVE, OperationalState.RUNNING).build()
        and: 'UE trace pib parameter is set to #ueTraceCollectionEnabled'
            configurationChangeListener.getUeTraceCollectionEnabled() >> ueTraceCollectionEnabled

        when: 'file collection is triggered'
            ueTraceFileCollectionTaskManager.onTimeout(fifteenMinutetimer)

        then: '#expectedNumberOfTasks new task(s) are created'
            fileCollectionTaskCache.size() == old(fileCollectionTaskCache.size()) + expectedNumberOfTasks

        where: 'UE trace pib parameter is set to #ueTraceCollectionEnabled and network element is of type #neType with technologyDomain(s) #technologyDomains'
            ueTraceCollectionEnabled | neType      | technologyDomains || expectedNumberOfTasks
            true                     | 'ERBS'      | []                || 1
            true                     | 'ERBS'      | ['EPS']           || 1
            true                     | 'RadioNode' | []                || 1
            true                     | 'RadioNode' | ['EPS']           || 1
            true                     | 'RadioNode' | ['UMTS']          || 0
            true                     | 'RadioNode' | ['5GS']           || 1
            true                     | 'RadioNode' | ['EPS', 'UMTS']   || 1
            true                     | 'RadioNode' | ['EPS', '5GS']    || 1
            true                     | 'RadioNode' | ['UMTS', '5GS']   || 1
            true                     | 'SGSN-MME'  | []                || 1
            true                     | 'SGSN-MME'  | ['UMTS']          || 1
            true                     | 'SGSN-MME'  | ['EPS']           || 1
            true                     | 'SGSN-MME'  | ['EPS', 'UMTS']   || 1
            false                    | 'RadioNode' | ['EPS']           || 1
    }

    @Unroll
    def 'Creating tasks for UE trace subscription when no subscription is available, UE trace pib parameter is set to #ueTraceCollectionEnabled and node is of type #neType with technologyDomain(s) #technologyDomains'() {
        given: 'a network with a node of type #neType with technologyDomain(s) #technologyDomains'
            createNodeMO('LTE01dg2ERBS00001', neType, technologyDomains)
        and: 'fixed time stamps'
            setupTimeStamps(currentTime, recordedTime)
        and: 'no active UE trace subscription'
        and: 'UE trace pib parameter is set to #ueTraceCollectionEnabled'
            configurationChangeListener.getUeTraceCollectionEnabled() >> ueTraceCollectionEnabled

        when: 'file collection is triggered'
            ueTraceFileCollectionTaskManager.onTimeout(fifteenMinutetimer)

        then: '#expectedNumberOfTasks new task(s) are created'
            fileCollectionTaskCache.size() == old(fileCollectionTaskCache.size()) + expectedNumberOfTasks

        where: 'UE trace pib parameter is set to #ueTraceCollectionEnabled and network element is of type #neType with technologyDomain(s) #technologyDomains'
            ueTraceCollectionEnabled | neType      | technologyDomains || expectedNumberOfTasks
            true                     | 'ERBS'      | []                || 1
            true                     | 'ERBS'      | ['EPS']           || 1
            true                     | 'RadioNode' | []                || 1
            true                     | 'RadioNode' | ['EPS']           || 1
            true                     | 'RadioNode' | ['UMTS']          || 0
            true                     | 'RadioNode' | ['5GS']           || 1
            true                     | 'RadioNode' | ['EPS', 'UMTS']   || 1
            true                     | 'RadioNode' | ['EPS', '5GS']    || 1
            true                     | 'RadioNode' | ['UMTS', '5GS']   || 1
            true                     | 'SGSN-MME'  | []                || 0
            true                     | 'SGSN-MME'  | ['UMTS']          || 0
            true                     | 'SGSN-MME'  | ['EPS']           || 0
            true                     | 'SGSN-MME'  | ['EPS', 'UMTS']   || 0
            false                    | 'RadioNode' | ['EPS']           || 0
    }

    @Unroll
    def 'Creating tasks for UE trace subscription with output mode #outputMode and UE trace pib parameter is set to #ueTraceCollectionEnabled'() {
        given: 'a network with a node with EPS technologyDomain'
            createNodeMO('LTE01dg2ERBS00001', 'RadioNode', ['EPS'])
        and: 'fixed time stamps'
            setupTimeStamps(currentTime, recordedTime)
        and: 'an active UE trace subscription with output mode #outputMode'
            createSubscriptionBuilder(AdministrationState.ACTIVE, OperationalState.RUNNING).outputMode(outputMode).build()
        and: 'UE trace pib parameter is set to #ueTraceCollectionEnabled'
            configurationChangeListener.getUeTraceCollectionEnabled() >> ueTraceCollectionEnabled

        when: 'file collection is triggered'
            ueTraceFileCollectionTaskManager.onTimeout(fifteenMinutetimer)

        then: '#expectedNumberOfTasks task(s) are created'
            fileCollectionTaskCache.size() == old(fileCollectionTaskCache.size()) + expectedNumberOfTasks

        where: 'UE trace pib parameter is set to #ueTraceCollectionEnabled and subscription output mode is #outputMode'
            ueTraceCollectionEnabled | outputMode                        || expectedNumberOfTasks
            false                    | OutputModeType.FILE               || 1
            false                    | OutputModeType.STREAMING          || 0
            false                    | OutputModeType.FILE_AND_STREAMING || 1
            true                     | OutputModeType.FILE               || 1
            true                     | OutputModeType.STREAMING          || 1
            true                     | OutputModeType.FILE_AND_STREAMING || 1
    }

    @Unroll
    def 'Creating tasks when test time is #testTime, rop period is #ropPeriod seconds and last recorded collection was #lastRecordedNumRopsAgo rops ago'() {
        given: 'a network with a node'
            createNodeMO('LTE01DG200001', 'RadioNode', ['EPS'])
        and: 'fixed time stamps'
            def ropTime = new RopTime(getTimeInMillis(testTime), ropPeriod)
            def lastRecordedRopTime = ropTime.getLastROP(lastRecordedNumRopsAgo)
            setupTimeStamps(getTimeInMillis(testTime), lastRecordedRopTime.currentRopStartTimeInMilliSecs)
        and: 'no active UE trace subscription'
        and: 'UE trace collection is enabled'
            configurationChangeListener.getUeTraceCollectionEnabled() >> true

        when: 'file collection is triggered'
            ropPeriod == FIFTEEN_MINUTES_IN_SECONDS ? ueTraceFileCollectionTaskManager.onTimeout(fifteenMinutetimer) : ueTraceFileCollectionTaskManager.onTimeout(oneMinuteTimer)

        then: '#description'
            def expectedNumberOfTasks = (expectCurrentRopCollection ? 1 : 0 ) + (expectRecoveryCollection ? 1 : 0 )
            fileCollectionTaskCache.size() == expectedNumberOfTasks
        and: 'an event is only sent when recovery task is included'
            if (expectRecoveryCollection) {
                1 * eventSender.send(_ as List<FileCollectionTaskRequest>, _)
            }

        and: 'Recorded time is updated'
            fileCollectionLastRopData.getFromCache(LAST_UETRACE_FILE_COLLECTION_TASKS_CREATED_FOR_ROP + ropPeriod) == getTimeInMillis(expectedRecordedTime)

        where: 'test time is #testTime, rop period is #ropPeriod seconds and last recorded collection was #lastRecordedNumRopsAgo rops ago'
            testTime              | ropPeriod | lastRecordedNumRopsAgo || expectedRecordedTime  || expectCurrentRopCollection || expectRecoveryCollection || description
            '15/05/2016 12:07:30' | 900       | 1                      || '15/05/2016 12:00:00' || true                       || false                    || 'will not create and send recovery. Will create task for current ROP'
            '15/05/2016 12:07:30' | 900       | 2                      || '15/05/2016 12:00:00' || true                       || true                     || 'will create and send recovery. Will create task for current ROP'
            '15/05/2016 12:07:29' | 900       | 0                      || '15/05/2016 12:00:00' || false                      || false                    || 'will not create and send recovery. Will not create task for current ROP as current time is not past the middle of current ROP'
            '15/05/2016 12:07:29' | 900       | 2                      || '15/05/2016 11:45:00' || false                      || true                     || 'will create and send recovery. Will not create for current ROP as current time is not past the middle of current ROP'
            '15/05/2016 12:01:30' | 60        | 1                      || '15/05/2016 12:01:00' || true                       || false                    || 'will not create and send recovery. Will create task for current ROP'
            '15/05/2016 12:01:30' | 60        | 2                      || '15/05/2016 12:01:00' || true                       || true                     || 'will create and send recovery. Will create task for current ROP'
            '15/05/2016 12:01:29' | 60        | 0                      || '15/05/2016 12:01:00' || false                      || false                    || 'will not create and send recovery. Will not create task for current ROP as current time is not past the middle of current ROP'
            '15/05/2016 12:01:29' | 60        | 2                      || '15/05/2016 12:00:00' || false                      || true                     || 'will create and send recovery. Will not create for current ROP as current time is not past the middle of current ROP'
    }

    @Unroll
    def 'Creating tasks when preconditions are correct, no UE trace subscription is available, UE trace collection is disabled'() {
        given: 'a network with a node'
            createNodeMO('LTE01DG200001', 'RadioNode', ['EPS'])
        and: 'fixed time stamps'
            setupTimeStamps(getTimeInMillis(testTime), getTimeInMillis(recordedRopTime))
        and: 'no active UE trace subscription'
        and: 'UE trace collection is disabled'
            configurationChangeListener.getUeTraceCollectionEnabled() >> false

        when: 'file collection is triggered'
            ropPeriod == FIFTEEN_MINUTES_IN_SECONDS ? ueTraceFileCollectionTaskManager.onTimeout(fifteenMinutetimer) : ueTraceFileCollectionTaskManager.onTimeout(oneMinuteTimer)

        then: 'no new task should be created'
            fileCollectionTaskCache.size() == 0
        and: 'no events are sent'
            0 * eventSender.send(_ as List<FileCollectionTaskRequest>)
        and: 'recorded time is updated'
            fileCollectionLastRopData.getFromCache(LAST_UETRACE_FILE_COLLECTION_TASKS_CREATED_FOR_ROP + ropPeriod) == getTimeInMillis(expectedRecordedTime)

        where: 'recordedRopTime is #recordedRopTime, testTime is #testTime and ropPeriod is #ropPeriod seconds'
            recordedRopTime       | testTime              | ropPeriod || expectedRecordedTime
            '22/09/1900 23:59:59' | '15/05/2016 12:07:30' | 900       || '15/05/2016 12:00:00'
    }

    @Unroll
    def 'Creating tasks for a node with ossModelIdentity - #ossModelIdentity'() {
        given: 'a network with a node'
            createNodeMO('LTE01DG200001', 'RadioNode', ['EPS'], ossModelIdentity)
        and: 'no active UE trace subscription'
        and: 'UE trace collection is enabled'
            configurationChangeListener.getUeTraceCollectionEnabled() >> true
        and: 'rop period is set'
            def ropTime = new RopTime(System.currentTimeMillis(), FIFTEEN_MINUTES_IN_SECONDS)
            timeGenerator.currentTimeMillis() >> (ropTime.getCurrentRopStartTimeInMilliSecs() + TimeUnit.MINUTES.toMillis(11))
            fileCollectionLastRopData.recordRopStartTimeForUetraceTaskCreation(ropTime.getLastROP(1))

        when: 'file collection is triggered'
            ueTraceFileCollectionTaskManager.onTimeout(fifteenMinutetimer)

        then: '#expectedNumberOfTasks task(s) are created'
            fileCollectionTaskCache.size() == old(fileCollectionTaskCache.size()) + expectedNumberOfTasks

        where: 'ossModelIdentity of the node is "#ossModelIdentity"'
            ossModelIdentity               || expectedNumberOfTasks
            '6607-651-025'                 || 1
            'Must be set to a valid value' || 0
            ''                             || 0
            null                           || 0
    }

    def 'Starting UE trace file collection task management for a given rop period'(){
        when: 'UE trace file collection task management is started'
            ueTraceFileCollectionTaskManager.startUeTraceFileCollectionTaskManagement(RopPeriod.FIFTEEN_MIN);
        then: 'it uses the timer service to create an interval timer for 15 minutes'
            1 * timerService.createIntervalTimer(_, _ , { it.getInfo() == FIFTEEN_MINUTES_IN_SECONDS } )
    }

    def 'Checking the created timers'() {
        given: 'a timer service with 2 timers'
            timerService.getTimers() >> [fifteenMinutetimer, oneMinuteTimer]
        expect: 'the task manager to return the collection intervals for those timers'
            ueTraceFileCollectionTaskManager.getTimerInfos() == [FIFTEEN_MINUTES_IN_SECONDS, ONE_MINUTE_IN_SECONDS]
    }

    def addNodes() {
        sgsnNodes.each {
            testNetworkElementDpsUtils.builder(it).pmEnabled(true).build()
        }
        erbsNodes.each {
            testNetworkElementDpsUtils.builder(it).pmEnabled(true).build()
        }
        radioNodes.each {
            testNetworkElementDpsUtils.builder(it).pmEnabled(true).build()
        }
        mixedModeRadioNodes.each {
            testNetworkElementDpsUtils.builder(it).neType('RadioNode').technologyDomain(Arrays.asList('EPS', '5GS')).pmEnabled(true).build()
        }
    }

    static long getTimeInMillis(String time) {
        simpleDateFormat.parse(time).getTime()
    }

    def createNodeMO(String nodeName, String neType, List<String> technologyDomains, String ossModelIdentity = '6607-651-025') {
        def nodeMO = testNetworkElementDpsUtils.builder(nodeName).ossModelIdentity(ossModelIdentity).build()
        def nodeAttr = new HashMap<>()
        nodeAttr.put('neType', neType)
        nodeAttr.put('technologyDomain', technologyDomains)
        nodeMO.setAttributes(nodeAttr)
    }

    def createSubscriptionBuilder(AdministrationState administrationState, OperationalState operationalState) {
        (UeTraceSubscriptionBuilder) ueTraceSubscriptionBuilder.name('UeTraceSubscription').administrativeState(administrationState).operationalState(operationalState)
                .taskStatus(TaskStatus.OK)
    }
}
