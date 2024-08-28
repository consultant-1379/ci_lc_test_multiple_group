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

package com.ericsson.oss.services.pm.collection.schedulers;

import static com.ericsson.oss.pmic.dto.node.enums.NetworkElementType.SGSNMME;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.UETRACE_SUBSCRIPTION_ATTRIBUTES;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.pmic.subscription.capability.SubscriptionCapabilityReader;
import com.ericsson.oss.services.pm.collection.api.FileCollectionTaskSenderLocal;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionLastRopData;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionTaskCacheWrapper;
import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.collection.task.factories.UeTraceFileCollectionTaskRequestFactory;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;
import com.ericsson.oss.services.pm.common.constants.PmFeature;
import com.ericsson.oss.services.pm.common.logging.PMICLog;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.util.RopTime;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.oss.services.pm.time.TimeGenerator;

/**
 * This class is responsible for creating the UE trace {@link FileCollectionTaskRequest}'s for all nodes that support UE Trace in the system
 * per ROP. It can create a timer for all supported ROP's defined by {@link RopPeriod}.
 *
 * @author enichyl
 */
@Stateless
public class UeTraceFileCollectionTaskManager {

    private static final long TIMER_INTERVAL_IN_SECONDS = 15;
    private static final long TIMER_INTERVAL_IN_MILLIS = TimeUnit.SECONDS.toMillis(TIMER_INTERVAL_IN_SECONDS);

    @Inject
    private PmCapabilityModelService pmCapabilityModelService;

    @Inject
    private NodeService nodeService;

    @Inject
    private SubscriptionCapabilityReader subscriptionCapabilityReader;

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    @Inject
    private UeTraceFileCollectionTaskRequestFactory ueTraceFileCollectionTaskRequestFactory;

    @Inject
    private FileCollectionTaskCacheWrapper fileCollectionTaskCache;

    @Inject
    private Logger logger;

    @Resource
    private TimerService timerService;

    @Inject
    private MembershipListener membershipListener;

    @Inject
    private ConfigurationChangeListener configurationChangeListener;

    @Inject
    private TimeGenerator timeGenerator;

    @Inject
    private CollectionSchedulerHelper collectionSchedulerHelper;

    @Inject
    private SystemRecorderWrapperLocal systemRecorder;

    @Inject
    private FileCollectionLastRopData fileCollectionLastRopData;

    @Inject
    private FileCollectionTaskSenderLocal fileCollectionTaskSender;

    @Inject
    private SupportedRopTimes supportedRopTimes;

    /**
     * Method to be executed on expiration of the timer. It will (if UE Trace is enabled on the system, if there are active UE Trace subscriptions,
     * and the current pm-service instance is the master instance) create {@link FileCollectionTaskRequest}'s for all nodes that support UE Trace in
     * the system, for the ROP specified by the {@link Timer} parameter passed, and then add them to the FileCollectionTaskCache via the
     * {@link FileCollectionTaskCacheWrapper}. It also supports a recovery of one ROP if the previous ROP did not create tasks.
     *
     * @param timer
     *            {@link Timer} - timer created by the TimerService for the specific interval
     */
    @Timeout
    @LogProfiler(name = "Sending UEtrace file collection tasks", ignoreExecutionTimeLowerThan = 1L)
    public void onTimeout(final Timer timer) {
        final int ropTimePeriodInSeconds = (Integer) timer.getInfo();
        final long taskGenerationStartTime = timeGenerator.currentTimeMillis();
        if (membershipListener.isMaster()) {
            logger.debug("Creating UE Trace File Collection tasks. Is master {}, is UeTrace enabled {}", membershipListener.isMaster(),
                    configurationChangeListener.getUeTraceCollectionEnabled());
            final RopTime currentRopTime = new RopTime(taskGenerationStartTime, ropTimePeriodInSeconds);
            final boolean createTasksForCurrentRop = fileCollectionLastRopData.shouldCreateUetraceTasksForCurrentRop(currentRopTime);
            final boolean createTasksForPreviousRop = fileCollectionLastRopData.shouldCreateUetraceTasksForPreviousRop(currentRopTime);
            try {
                createTasks(ropTimePeriodInSeconds, taskGenerationStartTime, createTasksForCurrentRop, createTasksForPreviousRop, currentRopTime);
            } catch (final DataAccessException | RuntimeDataAccessException e) {
                systemRecorder.eventCoarse(PMICLog.Event.SYS_DEF_SUBSCRIPTION_SCHEDULE_FAILED, "System Defined Subscriptions",
                        "System Defined Subscriptions Audit Failed. " + e.getMessage());
                logger.info("Exception stacktrace: ", e);
            }
        }
    }

    private void createTasks(final int ropTimePeriodInSeconds, final long taskGenerationStartTime, final boolean createTasksForCurrentRop,
            final boolean createTasksForPreviousRop, final RopTime currentRopTime) throws DataAccessException {
        if (createTasksForPreviousRop || createTasksForCurrentRop) {
            systemRecorder.eventCoarse(PMICLog.Event.BUILD_FILE_COLLECTION_TASK_LIST, getClass().getSimpleName(),
                    "Creating file collection tasks for rop {} in master node. Currently there are {} tasks in fileCollectionTaskCache "
                            + "Tasks Creation for Current Rop: {} and for Previous Rop: {}", ropTimePeriodInSeconds, fileCollectionTaskCache.size(),
                    createTasksForCurrentRop, createTasksForPreviousRop);
            if (shouldCreateUeTraceTasks()) {
                if (createTasksForPreviousRop) {
                    sendFileCollectionTasksForPreviousRop(ropTimePeriodInSeconds, taskGenerationStartTime, currentRopTime.getLastROP(1));
                }
                if (createTasksForCurrentRop) {
                    createFileCollectionTasksForAllSupportedNodes(ropTimePeriodInSeconds, currentRopTime, taskGenerationStartTime);
                }
            } else {
                logger.info("No UE Trace FileCollectionTask requests will be created for this rop {} because there are either no active "
                        + "Ue Trace subscriptions with OutputMode File or UeTraceCollection configuration parameter is disabled.",
                        ropTimePeriodInSeconds);
            }
            fileCollectionLastRopData
                    .recordRopStartTimeForUetraceTaskCreation(createTasksForCurrentRop ? currentRopTime : currentRopTime.getLastROP(1));
        } else {
            logger.trace("No UE trace tasks to create for rop {} at this time.", ropTimePeriodInSeconds);
        }
    }

    private void sendFileCollectionTasksForPreviousRop(final int ropTimePeriodInSeconds, final long currentTime,
            final RopTime ropTime) throws DataAccessException {
        final List<FileCollectionTaskWrapper> createdTasks =
                createFileCollectionTasksForAllSupportedNodes(ropTimePeriodInSeconds, ropTime, currentTime);
        if (!createdTasks.isEmpty()) {
            fileCollectionTaskSender.sendFileCollectionTasks(createdTasks, ropTime.getLastROP(1));
        }
    }

    private boolean shouldCreateUeTraceTasks() {
        return configurationChangeListener.getUeTraceCollectionEnabled() || thereIsAnActiveUeTraceSubscription();
    }

    /**
     * The method checks in whether there exist any active UETrace subscriptions and output mode type is not STREAMING.
     *
     * @return true if exist active UETrace subscriptions and file collection applicable for atleast one Uetrace subscription.
     */
    private boolean thereIsAnActiveUeTraceSubscription() {
        try {
            return subscriptionReadOperationService.areThereAnyActiveSubscriptionsWithSubscriptionTypeForFileCollection(SubscriptionType.UETRACE);
        } catch (final DataAccessException | RuntimeDataAccessException e) {
            logger.info(e.getMessage(), e);
            return false;
        }
    }

    private boolean shouldExcludeSgsnMme(final Node node, final boolean thereIsAnActiveUeTraceSubscription) {

        if (node.getNeType().equalsIgnoreCase(SGSNMME.getNeTypeString()) && configurationChangeListener.getUeTraceCollectionEnabled()
                && !thereIsAnActiveUeTraceSubscription) {
            logger.debug("Ignore SGSN-MME Node: {}", node.getName());
            return true;
        }
        return false;
    }

    private List<FileCollectionTaskWrapper> createFileCollectionTasksForAllSupportedNodes(final int ropTimePeriodInSeconds,
            final RopTime ropForWhichTasksShouldBeCreated,
            final long currentTime)
            throws DataAccessException {
        final List<String> ueTraceSupportedNodeTypes = pmCapabilityModelService
                .getSupportedNodeTypesForPmFeatureCapability(PmFeature.UETRACE_FILE_COLLECTION);
        final List<String> mixedModeSupportedNodeTypes = pmCapabilityModelService
                .getSupportedNodeTypesForPmFeatureCapability(PmFeature.SUPPORTED_MIXED_MODE_TECHNOLOGY);
        final List<Node> supportedNodes = nodeService
                .findAllByNeType(ueTraceSupportedNodeTypes.toArray(new String[ueTraceSupportedNodeTypes.size()]));

        final boolean thereIsAnActiveUeTraceSubscription = thereIsAnActiveUeTraceSubscription();

        final List<FileCollectionTaskWrapper> fileCollectionTasks = new ArrayList<>();
        for (final Node node : supportedNodes) {
            if (shouldExcludeSgsnMme(node, thereIsAnActiveUeTraceSubscription)
                    || !isSupportedNode(node, mixedModeSupportedNodeTypes)
                    || !nodeService.isFileCollectionEnabled(node.getFdn())) {
                continue;
            }
            fileCollectionTasks.addAll(createFileCollectionTaskRequestAndAddToCache(node, ropTimePeriodInSeconds, ropForWhichTasksShouldBeCreated));
        }
        final RopTimeInfo ropTimeInfo = supportedRopTimes.getRopTime(ropTimePeriodInSeconds);
        final RopTime ropTime = new RopTime(currentTime, ropTimePeriodInSeconds);
        systemRecorder.eventCoarse(PMICLog.Event.BUILD_FILE_COLLECTION_TASK_LIST, getClass().getSimpleName(),
                "Created {} Ue Trace FileCollectionTask requests for ROP {} to be collected at {}.", fileCollectionTasks.size(),
                ropTimePeriodInSeconds,
                new Date(ropTime.getCurrentROPPeriodEndTime().getCurrentRopStartTimeInMilliSecs() + ropTimeInfo.getCollectionDelayInMilliSecond()));
        return fileCollectionTasks;
    }

    private boolean isSupportedNode(final Node node, final List<String> mixedModeSupportedNodeTypes) {
        return Node.isValidOssModelIdentity(node.getOssModelIdentity()) && isSupportedTechnologyDomain(node, mixedModeSupportedNodeTypes);
    }

    private boolean isSupportedTechnologyDomain(final Node node, final List<String> mixedModeSupportedNodeTypes) {
        if (mixedModeSupportedNodeTypes.contains(node.getNeType())) {
            return isSupportedTechnologyDomain(node.getTechnologyDomain(),
                    subscriptionCapabilityReader.getSupportedTechnologyDomainsForSubscriptionCapabilityModel(UETRACE_SUBSCRIPTION_ATTRIBUTES));
        }
        return true;
    }

    private boolean isSupportedTechnologyDomain(final List<String> nodeTechnologyDomains, final Collection<String> supportedTechnologyDomainsForSubscriptionType) {
        return nodeTechnologyDomains.isEmpty() || supportedTechnologyDomainsForSubscriptionType.stream().anyMatch(nodeTechnologyDomains::contains);
    }

    /**
     * Will create the file collection task request for the next node in the iterator and rop period, then add this to the cache. Will log any
     * exceptions thrown and continue
     *
     * @param supportedNode
     * @param ropTimePeriodInSeconds
     * @param ropTime
     * @return Created task as element in Collection
     */
    private Collection<FileCollectionTaskWrapper> createFileCollectionTaskRequestAndAddToCache(final Node supportedNode,
            final int ropTimePeriodInSeconds,
            final RopTime ropTime) {
        final Collection<FileCollectionTaskWrapper> createdTasks = new HashSet<>();
        try {
            final RopTimeInfo ropTimeInfo = supportedRopTimes.getRopTime(ropTimePeriodInSeconds);
            final FileCollectionTaskRequest ueTraceFileCollectionRequest = ueTraceFileCollectionTaskRequestFactory
                    .createFileCollectionTaskRequest(supportedNode.getFdn(), ropTime.getCurrentRopStartTimeInMilliSecs(), ropTime.getRopPeriod());
            final FileCollectionTaskWrapper fileCollectionTaskWrapper = new FileCollectionTaskWrapper(ueTraceFileCollectionRequest,
                    ropTime.getCurrentROPPeriodEndTime(), ropTimeInfo);
            fileCollectionTaskCache.addTask(fileCollectionTaskWrapper);
            createdTasks.add(fileCollectionTaskWrapper);
        } catch (final NoSuchElementException noSuchElement) {
            logger.error("Failed to get next node from Iterator, possibly nodes were deleted by separate application: {}",
                    noSuchElement.getMessage());
            logger.info("Failed to get next node from Iterator, possibly nodes were deleted by separate application", noSuchElement);
        } catch (final Exception e) {
            logger.error("An exception happened while trying to create file collection tasks and add them to the cache:{}", e.getMessage());
            logger.info("An exception happened while trying to create file collection tasks and add them to the cache", e);
        }
        return createdTasks;
    }

    /**
     * This method is responsible for telling this class to start its management of creating ue trace file collection tasks. It creates the timer for
     * this class based on the ROP time passed, so that file collection tasks will be created and added to the cache on expiration.
     *
     * @param ropTime
     *            - Record Output Period time to create timer for UeTrace File Collection
     * @return {@link Future}<{@link Boolean}> - a future to determine on completion of this Asynchronous method, whether UE Trace Task Management
     *         started successfully or not. True indicates started successfully, false indicates it failed to start for some reason
     */
    @Asynchronous
    public Future<Boolean> startUeTraceFileCollectionTaskManagement(final RopPeriod ropTime) {
        return new AsyncResult<>(createTimer(ropTime));
    }

    private boolean createTimer(final RopPeriod ropTime) {
        logger.info("Creating UE Trace file collection task manager timer for rop period {}", ropTime.getDurationInSeconds());
        final long initialDelay = collectionSchedulerHelper.getTaskCreationTimerInitialDealay(ropTime.getDurationInSeconds());
        final TimerConfig timerConfig = new TimerConfig(ropTime.getDurationInSeconds(), false);
        try {
            timerService.createIntervalTimer(initialDelay, TIMER_INTERVAL_IN_MILLIS, timerConfig);
            systemRecorder.eventCoarse(PMICLog.Event.CREATE_FILE_COLLECTION_TASK_MANAGER_TIMER, getClass().getSimpleName(),
                    "Created UE Trace file collection task manager timer with interval {}(seconds) for ROP period {} to start "
                            + "creating file collection tasks at {}",
                    TIMER_INTERVAL_IN_SECONDS, ropTime.getDurationInSeconds(),
                    new Date(timeGenerator.currentTimeMillis() + initialDelay));
            return true;
        } catch (final IllegalArgumentException | IllegalStateException | EJBException e) {
            logger.error("An Error occurred trying to create the timer for UeTraceFileCollectionTaskManager for ropTime {}: {}", ropTime,
                    e);
            return false;
        }
    }

    public void stopUeTraceFileCollectionTaskManagement(final RopPeriod ropTime) {
        getAndCancelTimer(ropTime.getDurationInSeconds());
    }

    private boolean getAndCancelTimer(final int ropTime) {
        boolean isTimerStopped = false;
        try {
            final Timer timer = getTimerFromTimerService(ropTime);
            if (timer != null) {
                logger.debug("Stopping {}s ROP  timer for Ue Trace File collection", timer.getInfo());
                timer.cancel();
                logger.debug("Stopped {}s ROP  timer for Ue Trace File collection", timer.getInfo());
                isTimerStopped = true;
            }
        } catch (final Exception e) {
            logger.error("Failed to stop timer for Ue Trace roptime {} Exception {}", ropTime, e);
        }

        return isTimerStopped;
    }

    /**
     * Method to get all the timer infos for all timers created by this class. Each timer info is an Integer which represents the interval duration
     * for this timer. Can be used to determine if a timer is running for a certain interval. Will not expose the Timer itself.
     *
     * @return {@link List}<{@link Integer}> - represents all the unique timer intervals for this class
     */
    public List<Integer> getTimerInfos() {
        final List<Integer> timerInfos = new ArrayList<>();
        for (final Timer timer : timerService.getTimers()) {
            timerInfos.add((Integer) timer.getInfo());
        }
        return timerInfos;
    }

    private Timer getTimerFromTimerService(final int ropTime) {
        final Collection<Timer> timers = timerService.getTimers();
        for (final Timer timer : timers) {
            if (timer.getInfo().equals(ropTime)) {
                return timer;
            }
        }
        return null;
    }

}
