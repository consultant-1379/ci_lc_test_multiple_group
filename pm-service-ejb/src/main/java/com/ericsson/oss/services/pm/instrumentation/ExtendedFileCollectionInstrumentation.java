
/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.instrumentation;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Lock;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.ericsson.oss.itpf.sdk.instrument.MetricsUtil;
import com.ericsson.oss.itpf.sdk.instrument.annotation.InstrumentedBean;
import com.ericsson.oss.itpf.sdk.instrument.annotation.MonitoredAttribute;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;
import com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant;
import com.ericsson.oss.services.pm.collection.events.FileCollectionResult;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;
import com.ericsson.oss.services.pm.initiation.cache.model.value.ProcessType;
import com.ericsson.oss.services.pm.scheduling.cluster.MembershipListener;

/**
 * Extended file collection instrumentation class.
 */
@ApplicationScoped
@InstrumentedBean(displayName = "Extended File Collection Instrumentation")
@SuppressWarnings("PMD")
public class ExtendedFileCollectionInstrumentation {


    public static final String STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP =
            "file_collection_instrumentation.statistical_file_collection_duration.per_rop";

    public static final String STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE =
            "file_collection_instrumentation.statistical_file_collection_duration.per_node";

    public static final String CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP =
            "file_collection_instrumentation.celltrace_file_collection_duration.per_rop";

    public static final String CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE =
            "file_collection_instrumentation.celltrace_file_collection_duration.per_node";

    public static final String SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP =
            "file_collection_instrumentation.single_rop_recovery.statistical_file_collection_duration.per_rop";

    public static final String SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE =
            "file_collection_instrumentation.single_rop_recovery.statistical_file_collection_duration.per_node";

    public static final String SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP =
            "file_collection_instrumentation.single_rop_recovery.celltrace_file_collection_duration";

    public static final String SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE =
            "file_collection_instrumentation.single_rop_recovery.celltrace_file_collection_duration.per_node";

    public static final String SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP =
            "file_collection_instrumentation.scheduled_recovery.statistical_file_collection_duration.per_rop";

    public static final String SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE =
            "file_collection_instrumentation.scheduled_recovery.statistical_file_collection_duration.per_node";

    public static final String STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION =
            "file_collection_instrumentation.startup_recovery.statistical_file_collection_duration";

    public static final String STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE =
            "file_collection_instrumentation.startup_recovery.statistical_file_collection_duration.per_node";

    public static final String STATISTICAL_FILE_COLLECTION_COLLECTED_FILES =
            "file_collection_instrumentation.statistical_file_collection.collected_files";

    public static final String STATISTICAL_FILE_COLLECTION_MISSED_FILES = "file_collection_instrumentation.statistical_file_collection.missed_files";

    public static final String CELLTRACE_FILE_COLLECTION_COLLECTED_FILES =
            "file_collection_instrumentation.celltrace_file_collection.collected_files";

    public static final String CELLTRACE_FILE_COLLECTION_MISSED_FILES = "file_collection_instrumentation.celltrace_file_collection.missed_files";

    public static final String SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES =
            "file_collection_instrumentation.single_rop_recovery.statistical_file_collection.collected_files";

    public static final String SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES =
            "file_collection_instrumentation.single_rop_recovery.statistical_file_collection.missed_files";

    public static final String SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_COLLECTED_FILES =
            "file_collection_instrumentation.single_rop_recovery.celltrace_file_collection.collected_files";

    public static final String SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_MISSED_FILES =
            "file_collection_instrumentation.single_rop_recovery.celltrace_file_collection.missed_files";

    public static final String SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES =
            "file_collection_instrumentation.scheduled_recovery.statistical_file_collection.collected_files";

    public static final String SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES =
            "file_collection_instrumentation.scheduled_recovery.statistical_file_collection.missed_files";

    public static final String STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES =
            "file_collection_instrumentation.startup_recovery.statistical_file_collection.collected_files";

    public static final String STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES =
            "file_collection_instrumentation.startup_recovery.statistical_file_collection.missed_files";

    public static final String EBM_FILE_COLLECTION_DURATION_PER_ROP = "file_collection_instrumentation.ebm_file_collection_duration.per_rop";

    public static final String EBM_FILE_COLLECTION_DURATION_PER_NODE = "file_collection_instrumentation.ebm_file_collection_duration.per_node";

    public static final String EBM_FILE_COLLECTION_COLLECTED_FILES = "file_collection_instrumentation.ebm_file_collection.collected_files";

    public static final String EBM_FILE_COLLECTION_MISSED_FILES = "file_collection_instrumentation.ebm_file_collection.missed_files";

    public static final String SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_COLLECTED_FILES =
            "file_collection_instrumentation.single_rop_recovery.ebm_file_collection.collected_files";

    public static final String SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_MISSED_FILES =
            "file_collection_instrumentation.single_rop_recovery.ebm_file_collection.missed_files";

    public static final String SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_ROP =
            "file_collection_instrumentation.single_rop_recovery.ebm_file_collection_duration";

    public static final String SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_NODE =
            "file_collection_instrumentation.single_rop_recovery.ebm_file_collection_duration.per_node";

    private static final String STATISTICAL_FILE_COLLECTION_TASK_GROUP = "statistical_file_collection_task_group";
    private static final String CELLTRACE_FILE_COLLETION_TASK_GROUP = "celltrace_file_colletion_task_group";
    private static final String SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_TASK_GROUP = "single_rop_recovery_"
            + STATISTICAL_FILE_COLLECTION_TASK_GROUP;
    private static final String SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLETION_TASK_GROUP = "single_rop_recovery_"
            + CELLTRACE_FILE_COLLETION_TASK_GROUP;
    private static final String SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_TASK_GROUP = "scheduled_recovery_"
            + STATISTICAL_FILE_COLLECTION_TASK_GROUP;
    private static final String STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_TASK_GROUP = "startup_recovery_"
            + STATISTICAL_FILE_COLLECTION_TASK_GROUP;

    private static final String EBM_FILE_COLLECTION_TASK_GROUP = "ebm_file_collection_task_group";
    private static final String SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_TASK_GROUP = "single_rop_recovery_" + EBM_FILE_COLLECTION_TASK_GROUP;

    private static final String SEPARATOR = "|";
    private static final String SIMPLE_DATE_FORMAT = "yyyyMMdd.HHmm";
    private final Map<String, FileCollectionTaskGroup> taskGroups = new ConcurrentHashMap<>();
    private final Map<String, Timer.Context> tasks = new ConcurrentHashMap<>();
    private final Deque<ScheduledFileRecoveryTaskGroup> scheduledFileRecoveryTaskGroups = new LinkedBlockingDeque<>();
    private MetricRegistry metricRegistry;
    private ScheduledFileRecoveryTaskGroup startupFileRecoveryTaskGroup;
    @Inject
    private Logger log;
    @Inject
    private MembershipListener membershipListener;

    /**
     * On init, initialises the metric registry
     */
    @PostConstruct
    public void onInit() {
        metricRegistry = MetricsUtil.getRegistry();
    }

    /**
     * On destroy, clears the task groups and tasks
     */
    @PreDestroy
    public void onDestroy() {
        taskGroups.clear();
        tasks.clear();
    }

    private boolean isTimerMetricEnabled() {
        return metricRegistry != null; // see MetricsUtil.getRegistry() for details
    }

    /**
     * This method based on usage of the taskId formatting rules defined in CellTraceFileCollectionTaskFactory and
     * StatisticalFileCollectionTaskFactory.
     *
     * @param fileCollectionTaskId
     *         - the id of the file collection task to get Record Output Period start time for
     *
     * @return - returns the Record Output Period Start Time
     */
    protected Long extractRopStartTimeFromFileCollectionTaskId(final String fileCollectionTaskId) {
        return getRopValue(fileCollectionTaskId);
    }

    /**
     * This method based on usage of the taskId formatting rules defined in CellTraceFileCollectionTaskFactory and
     * StatisticalFileCollectionTaskFactory.
     *
     * @param fileCollectionTaskId
     *         - the id of the file collection task to get Record Output Period time for
     *
     * @return - returns the Record Output Period
     */
    protected Long extractRopPeriodFromFileCollectionTaskId(final String fileCollectionTaskId) {
        return getRopValue(fileCollectionTaskId);
    }

    private Long getRopValue(final String fileCollectionTaskId) {
        final int ropStartTimeValueStart = fileCollectionTaskId.indexOf("|ropStartTime=") + "|ropStartTime=".length();
        if (ropStartTimeValueStart < 0) {
            return null;
        }
        final int ropStartTimeValueEnd = fileCollectionTaskId.indexOf('|', ropStartTimeValueStart);
        if (ropStartTimeValueEnd < 0) {
            return null;
        }
        final String value = fileCollectionTaskId.substring(ropStartTimeValueStart, ropStartTimeValueEnd);
        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    /**
     * This method based on usage of the taskId formatting rules defined in CellTraceFileCollectionTaskFactory and
     * StatisticalFileCollectionTaskFactory.
     *
     * @param fileCollectionTaskId
     *         - the id of the file collection task
     *
     * @return - returns true if the task is of type Stats
     */
    protected boolean isStatisticalFileCollectionTask(final String fileCollectionTaskId) {
        return fileCollectionTaskId != null && fileCollectionTaskId.contains(SubscriptionType.STATISTICAL.name());
    }

    /**
     * This method based on usage of the taskId formatting rules defined in CellTraceFileCollectionTaskFactory and
     * StatisticalFileCollectionTaskFactory.
     *
     * @param fileCollectionTaskId
     *         - the id of the file collection task
     *
     * @return - returns true if the task is of type Cell Trace
     */
    protected boolean isCellTraceFileCollectionTask(final String fileCollectionTaskId) {
        return fileCollectionTaskId != null && fileCollectionTaskId.contains(SubscriptionType.CELLTRACE.name());
    }

    /**
     * This method based on usage of the taskId formatting rules defined in CellTraceFileCollectionTaskFactory and
     * StatisticalFileCollectionTaskFactory.
     *
     * @param fileCollectionTaskId
     *         - the id of the file collection task
     *
     * @return - returns true if the task is of type EBM
     */
    protected boolean isEbmFileCollectionTask(final String fileCollectionTaskId) {
        return fileCollectionTaskId != null && fileCollectionTaskId.contains(SubscriptionType.EBM.name());
    }

    /**
     * This method based on usage of the taskId formatting rules defined in FileCollectionSingleRopRecoveryTaskFactory.
     *
     * @param fileCollectionTaskId
     *         - the id of the file collection task
     *
     * @return - returns true if the task is a Single Rop Recovery Task
     */
    protected boolean isSingleRopRecoveryTask(final String fileCollectionTaskId) {
        return fileCollectionTaskId != null
                && fileCollectionTaskId.contains(FileCollectionConstant.FILE_COLLECTION_SINGLE_ROP_RECOVERY_TASK_ID_PREFIX);
    }

    /**
     * This method based on usage of the taskId formatting rules defined in StatisticalFileCollectionTaskFactory.
     *
     * @param fileCollectionTaskId
     *         - the id of the file collection task
     *
     * @return - returns true if the task is a Scheduled Recovery Task
     */
    protected boolean isScheduledRecoveryTask(final String fileCollectionTaskId) {
        return fileCollectionTaskId != null && fileCollectionTaskId.contains(FileCollectionConstant.FILE_COLLECTION_DELTA_RECOVERY_TASK_ID_PREFIX);
    }

    /**
     * Checks if file collection task is startup file recovery task.
     *
     * @param fileCollectionTaskId
     *         - the id of the file collection task
     *
     * @return - returns true if the task is a Startup File Recovery Task
     */
    protected boolean isStartupFileRecoveryTask(final String fileCollectionTaskId) {
        return fileCollectionTaskId != null
                && fileCollectionTaskId.contains(FileCollectionConstant.FILE_COLLECTION_RECOVERY_ON_STARTUP_TASK_ID_PREFIX);
    }

    /**
     * Gets nodes addresses for file recovery task.
     *
     * @param processRequests
     *         the process requests
     *
     * @return the nodes addresses for file recovery task
     */
    protected Set<String> getNodesAddressesForFileRecoveryTask(final Set<ProcessRequestVO> processRequests) {
        final Set<String> nodesAddresses = new HashSet<>();
        for (final ProcessRequestVO processRequest : processRequests) {
            if (ProcessType.STATS.name().equals(processRequest.getProcessType())) { // see RecoveryHandler.recoverFilesForNode
                nodesAddresses.add(processRequest.getNodeAddress());
            }
        }
        return nodesAddresses;
    }

    /**
     * Creates startup file recovery task group.
     *
     * @param processRequests
     *         the process requests
     */
    @Lock
    public void startupFileRecoveryTaskGroupStarted(final Set<ProcessRequestVO> processRequests) {
        if (!isTimerMetricEnabled() || !membershipListener.isMaster()) {
            return;
        }
        final Set<String> nodesAddresses = getNodesAddressesForFileRecoveryTask(processRequests);
        if (nodesAddresses.isEmpty()) {
            return;
        }
        final Timer.Context taskGroupTimerContext = metricRegistry.timer(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION).time();
        startupFileRecoveryTaskGroup =
                new ScheduledFileRecoveryTaskGroup(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_TASK_GROUP, nodesAddresses, taskGroupTimerContext);
    }

    /**
     * Creates scheduled file recovery task group.
     *
     * @param processRequests
     *         the process requests
     */
    @Lock
    public void scheduledFileRecoveryTaskGroupStarted(final Set<ProcessRequestVO> processRequests) {
        if (!isTimerMetricEnabled() || !membershipListener.isMaster()) {
            return;
        }
        final Set<String> nodesAddresses = getNodesAddressesForFileRecoveryTask(processRequests);
        if (nodesAddresses.isEmpty()) {
            return;
        }
        final String taskGroupId = SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_TASK_GROUP + scheduledFileRecoveryTaskGroups.size();
        final Timer.Context taskGroupTimerContext = metricRegistry.timer(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).time();
        final ScheduledFileRecoveryTaskGroup taskGroup = new ScheduledFileRecoveryTaskGroup(taskGroupId, nodesAddresses, taskGroupTimerContext);
        scheduledFileRecoveryTaskGroups.add(taskGroup);
    }

    /**
     * @param fileCollectionTaskRequest
     *         the file collection task request
     */
    @Lock
    public void scheduledFileRecoveryTaskStarted(final FileCollectionTaskRequest fileCollectionTaskRequest) {
        if (!isTimerMetricEnabled() || !membershipListener.isMaster()) {
            return;
        }
        if (isStartupFileRecoveryTask(fileCollectionTaskRequest.getJobId())) {
            startupFileRecoveryTaskGroup.getNodesAddresses().remove(fileCollectionTaskRequest.getNodeAddress());
            startupFileRecoveryTaskGroup.getTasksIds().add(fileCollectionTaskRequest.getJobId());
            fileCollectionTaskStarted(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE, fileCollectionTaskRequest.getJobId());
        } else if (isScheduledRecoveryTask(fileCollectionTaskRequest.getJobId())) {
            for (final ScheduledFileRecoveryTaskGroup taskGroup : scheduledFileRecoveryTaskGroups) {
                if (taskGroup.getNodesAddresses().contains(fileCollectionTaskRequest.getNodeAddress())) {
                    taskGroup.getNodesAddresses().remove(fileCollectionTaskRequest.getNodeAddress());
                    taskGroup.getTasksIds().add(fileCollectionTaskRequest.getJobId());
                    break;
                }
            }
            fileCollectionTaskStarted(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE, fileCollectionTaskRequest.getJobId());
        }
    }

    /**
     * @param fileCollectionTasksIds
     *         the file collection tasks ids
     */
    @Lock
    public void fileCollectionTasksStarted(final Set<String> fileCollectionTasksIds) {
        if (!isTimerMetricEnabled() || !membershipListener.isMaster()) {
            return;
        }
        for (final String fileCollectionTaskId : fileCollectionTasksIds) {
            fileCollectionTaskStarted(fileCollectionTaskId);
        }
    }

    /**
     * @param fileCollectionTaskId
     *         the file collection task id
     */
    @Lock
    public void fileCollectionTaskStarted(final String fileCollectionTaskId) {
        if (!isTimerMetricEnabled() || !membershipListener.isMaster()) {
            return;
        }

        if (isSingleRopRecoveryTask(fileCollectionTaskId)) {
            singleRopRecoveryTaskStarted(fileCollectionTaskId);
        } else {
            if (isStatisticalFileCollectionTask(fileCollectionTaskId)) {
                fileCollectionTaskGroupStarted(STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP, STATISTICAL_FILE_COLLECTION_TASK_GROUP,
                        fileCollectionTaskId);
                fileCollectionTaskStarted(STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE, fileCollectionTaskId);
            } else if (isCellTraceFileCollectionTask(fileCollectionTaskId)) {
                fileCollectionTaskGroupStarted(CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP, CELLTRACE_FILE_COLLETION_TASK_GROUP, fileCollectionTaskId);
                fileCollectionTaskStarted(CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE, fileCollectionTaskId);
            } else if (isEbmFileCollectionTask(fileCollectionTaskId)) {
                fileCollectionTaskGroupStarted(EBM_FILE_COLLECTION_DURATION_PER_ROP, EBM_FILE_COLLECTION_TASK_GROUP, fileCollectionTaskId);
                fileCollectionTaskStarted(EBM_FILE_COLLECTION_DURATION_PER_NODE, fileCollectionTaskId);
            }
        }
    }

    /**
     * @param timerName
     *         the timer name
     * @param fileCollectionTaskId
     *         the file collection task id
     */
    protected void fileCollectionTaskStarted(final String timerName, final String fileCollectionTaskId) {
        final Timer.Context taskTimerContext = metricRegistry.timer(timerName).time();
        tasks.put(timerName + SEPARATOR + "taskId = " + fileCollectionTaskId, taskTimerContext);
    }

    /**
     * @param fileCollectionTaskId
     *         the file collection task id
     */
    protected void singleRopRecoveryTaskStarted(final String fileCollectionTaskId) {
        if (isStatisticalFileCollectionTask(fileCollectionTaskId)) {
            fileCollectionTaskGroupStarted(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP,
                    SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_TASK_GROUP, fileCollectionTaskId);
            fileCollectionTaskStarted(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE, fileCollectionTaskId);
        } else if (isCellTraceFileCollectionTask(fileCollectionTaskId)) {
            fileCollectionTaskGroupStarted(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP,
                    SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLETION_TASK_GROUP, fileCollectionTaskId);
            fileCollectionTaskStarted(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE, fileCollectionTaskId);
        } else if (isEbmFileCollectionTask(fileCollectionTaskId)) {
            fileCollectionTaskGroupStarted(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_ROP,
                    SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_TASK_GROUP, fileCollectionTaskId);
            fileCollectionTaskStarted(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_NODE, fileCollectionTaskId);
        }
    }

    /**
     * @param timerName
     *         the timer name
     * @param fileCollectionGroupType
     *         the file collection group type
     * @param fileCollectionTaskId
     *         the file collection task id
     */
    protected void fileCollectionTaskGroupStarted(final String timerName, final String fileCollectionGroupType, final String fileCollectionTaskId) {
        final Long ropStartTimeInMilliseconds = extractRopStartTimeFromFileCollectionTaskId(fileCollectionTaskId);
        if (ropStartTimeInMilliseconds == null) {
            log.error("Incorrect fileCollectionTaskId, couldn't extract the start rop time from it: {}", fileCollectionTaskId);
            return;
        }
        final Long ropPeriodInMilliseconds = extractRopPeriodFromFileCollectionTaskId(fileCollectionTaskId);
        if (ropPeriodInMilliseconds == null) {
            log.error("Incorrect fileCollectionTaskId, couldn't extract the rop period from it: {}", fileCollectionTaskId);
            return;
        }
        final Date ropStartTime = new Date(ropStartTimeInMilliseconds);
        final Date ropEndTime = new Date(ropStartTimeInMilliseconds + ropPeriodInMilliseconds);
        final String ropTime = new StringBuilder(new SimpleDateFormat(SIMPLE_DATE_FORMAT).format(ropStartTime))
                .append("-")
                .append(new SimpleDateFormat(SIMPLE_DATE_FORMAT).format(ropEndTime))
                .toString();
        final String taskGroupId = fileCollectionGroupType + ropTime;
        final FileCollectionTaskGroup fileCollectionTaskGroup = taskGroups.computeIfAbsent(taskGroupId, k -> createFileCollectionTaskGroup(taskGroupId, timerName));
        fileCollectionTaskGroup.getTasksIds().add(fileCollectionTaskId);
    }

    private FileCollectionTaskGroup createFileCollectionTaskGroup(final String taskGroupId, final String timerName) {
        final Timer.Context taskGroupTimerContext = metricRegistry.timer(timerName).time();
        return new FileCollectionTaskGroup(taskGroupId, taskGroupTimerContext);
    }

    /**
     * @param fileCollectionTaskId
     *         the file collection task id
     */
    @Lock
    public void fileCollectionTaskEnded(final String fileCollectionTaskId) {
        if (!isTimerMetricEnabled() || !membershipListener.isMaster()) {
            return;
        }
        if (isStartupFileRecoveryTask(fileCollectionTaskId)) {
            startupFileRecoveryTaskGroupEnded(fileCollectionTaskId);
            fileCollectionTaskEnded(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE, fileCollectionTaskId);
        } else if (isScheduledRecoveryTask(fileCollectionTaskId)) {
            scheduledFileRecoveryTaskGroupEnded(fileCollectionTaskId);
            fileCollectionTaskEnded(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE, fileCollectionTaskId);
        } else if (isSingleRopRecoveryTask(fileCollectionTaskId)) {
            singleRopRecoveryTaskEnded(fileCollectionTaskId);
        } else {
            if (isStatisticalFileCollectionTask(fileCollectionTaskId)) {
                fileCollectionTaskGroupEnded(STATISTICAL_FILE_COLLECTION_TASK_GROUP, fileCollectionTaskId);
                fileCollectionTaskEnded(STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE, fileCollectionTaskId);
            } else if (isCellTraceFileCollectionTask(fileCollectionTaskId)) {
                fileCollectionTaskGroupEnded(CELLTRACE_FILE_COLLETION_TASK_GROUP, fileCollectionTaskId);
                fileCollectionTaskEnded(CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE, fileCollectionTaskId);
            } else if (isEbmFileCollectionTask(fileCollectionTaskId)) {
                fileCollectionTaskGroupEnded(EBM_FILE_COLLECTION_TASK_GROUP, fileCollectionTaskId);
                fileCollectionTaskEnded(EBM_FILE_COLLECTION_DURATION_PER_NODE, fileCollectionTaskId);
            }
        }
    }

    /**
     * @param fileCollectionResult
     *         the file collection result
     */
    @Lock
    public void fileCollectionTaskEnded(final FileCollectionResult fileCollectionResult) {
        if (!isTimerMetricEnabled() || !membershipListener.isMaster()) {
            return;
        }
        fileCollectionTaskEnded(fileCollectionResult.getJobId());
        if (isStartupFileRecoveryTask(fileCollectionResult.getJobId())) {
            increaseCounter(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES, size(fileCollectionResult.fileCollectionSuccess));
            increaseCounter(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES, size(fileCollectionResult.fileCollectionFailure));
        } else if (isScheduledRecoveryTask(fileCollectionResult.getJobId())) {
            increaseCounter(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES, size(fileCollectionResult.fileCollectionSuccess));
            increaseCounter(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES, size(fileCollectionResult.fileCollectionFailure));
        } else if (isSingleRopRecoveryTask(fileCollectionResult.getJobId())) {
            if (isStatisticalFileCollectionTask(fileCollectionResult.getJobId())) {
                increaseCounter(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES, size(fileCollectionResult.fileCollectionSuccess));
                increaseCounter(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES, size(fileCollectionResult.fileCollectionFailure));
            } else if (isCellTraceFileCollectionTask(fileCollectionResult.getJobId())) {
                increaseCounter(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_COLLECTED_FILES, size(fileCollectionResult.fileCollectionSuccess));
                increaseCounter(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_MISSED_FILES, size(fileCollectionResult.fileCollectionFailure));
            } else if (isEbmFileCollectionTask(fileCollectionResult.getJobId())) {
                increaseCounter(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_COLLECTED_FILES, size(fileCollectionResult.fileCollectionSuccess));
                increaseCounter(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_MISSED_FILES, size(fileCollectionResult.fileCollectionFailure));
            }
        } else {
            if (isStatisticalFileCollectionTask(fileCollectionResult.getJobId())) {
                increaseCounter(STATISTICAL_FILE_COLLECTION_COLLECTED_FILES, size(fileCollectionResult.fileCollectionSuccess));
                increaseCounter(STATISTICAL_FILE_COLLECTION_MISSED_FILES, size(fileCollectionResult.fileCollectionFailure));
            } else if (isCellTraceFileCollectionTask(fileCollectionResult.getJobId())) {
                increaseCounter(CELLTRACE_FILE_COLLECTION_COLLECTED_FILES, size(fileCollectionResult.fileCollectionSuccess));
                increaseCounter(CELLTRACE_FILE_COLLECTION_MISSED_FILES, size(fileCollectionResult.fileCollectionFailure));
            } else if (isEbmFileCollectionTask(fileCollectionResult.getJobId())) {
                increaseCounter(EBM_FILE_COLLECTION_COLLECTED_FILES, size(fileCollectionResult.fileCollectionSuccess));
                increaseCounter(EBM_FILE_COLLECTION_MISSED_FILES, size(fileCollectionResult.fileCollectionFailure));
            }
        }
    }

    /**
     * @param timerName
     *         the timer name
     * @param fileCollectionTaskId
     *         the file collection task id
     */
    protected void fileCollectionTaskEnded(final String timerName, final String fileCollectionTaskId) {
        final Timer.Context timerContext = tasks.remove(timerName + SEPARATOR + "taskId = " + fileCollectionTaskId);
        if (timerContext != null) {
            timerContext.stop();
        }
    }

    /**
     * @param fileCollectionTaskId
     *         the file collection task id
     */
    protected void startupFileRecoveryTaskGroupEnded(final String fileCollectionTaskId) {
        if (startupFileRecoveryTaskGroup == null) {
            return;
        }
        startupFileRecoveryTaskGroup.getTasksIds().remove(fileCollectionTaskId);
        if (startupFileRecoveryTaskGroup.getTasksIds().isEmpty()) {
            startupFileRecoveryTaskGroup.getTimerContext().stop();
        }
    }

    /**
     * @param fileCollectionTaskId
     *         the file collection task id
     */
    protected void scheduledFileRecoveryTaskGroupEnded(final String fileCollectionTaskId) {
        boolean removed = false;
        final Iterator<ScheduledFileRecoveryTaskGroup> iterator = scheduledFileRecoveryTaskGroups.iterator();
        while (iterator.hasNext()) {
            final ScheduledFileRecoveryTaskGroup taskGroup = iterator.next();
            if (!removed) {
                removed = taskGroup.getTasksIds().remove(fileCollectionTaskId);
            }
            if (taskGroup.getTasksIds().isEmpty()) {
                taskGroup.getTimerContext().stop();
                iterator.remove();
            }
        }
    }

    private int size(final List<?> list) {
        return list == null || list.isEmpty() ? 0 : list.size();
    }

    /**
     * Increase counter.
     *
     * @param name
     *         the counter name
     * @param n
     *         the increment value
     */
    protected void increaseCounter(final String name, final int n) {
        metricRegistry.counter(name).inc(n);
    }

    /**
     * @param fileCollectionTaskId
     *         the file collection task id
     */
    protected void singleRopRecoveryTaskEnded(final String fileCollectionTaskId) {
        if (isStatisticalFileCollectionTask(fileCollectionTaskId)) {
            fileCollectionTaskGroupEnded(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_TASK_GROUP, fileCollectionTaskId);
            fileCollectionTaskEnded(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE, fileCollectionTaskId);
        } else if (isCellTraceFileCollectionTask(fileCollectionTaskId)) {
            fileCollectionTaskGroupEnded(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLETION_TASK_GROUP, fileCollectionTaskId);
            fileCollectionTaskEnded(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE, fileCollectionTaskId);
        } else if (isEbmFileCollectionTask(fileCollectionTaskId)) {
            fileCollectionTaskGroupEnded(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_TASK_GROUP, fileCollectionTaskId);
            fileCollectionTaskEnded(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_NODE, fileCollectionTaskId);
        }
    }

    /**
     * @param fileCollectionGroupType
     *         the file collection group type
     * @param fileCollectionTaskId
     *         the file collection task id
     */
    protected void fileCollectionTaskGroupEnded(final String fileCollectionGroupType, final String fileCollectionTaskId) {
        final Long ropStartTimeInMilliseconds = extractRopStartTimeFromFileCollectionTaskId(fileCollectionTaskId);
        if (ropStartTimeInMilliseconds == null) {
            log.error("Incorrect fileCollectionTaskId, couldn't extract the start rop time from it: {}", fileCollectionTaskId);
            return;
        }
        final Long ropPeriodInMilliseconds = extractRopPeriodFromFileCollectionTaskId(fileCollectionTaskId);
        if (ropPeriodInMilliseconds == null) {
            log.error("Incorrect fileCollectionTaskId, couldn't extract the rop period from it: {}", fileCollectionTaskId);
            return;
        }
        final Date ropStartTime = new Date(ropStartTimeInMilliseconds);
        final Date ropEndTime = new Date(ropStartTimeInMilliseconds + ropPeriodInMilliseconds);
        final String ropTime = new StringBuilder(new SimpleDateFormat(SIMPLE_DATE_FORMAT).format(ropStartTime))
                .append("-")
                .append(new SimpleDateFormat(SIMPLE_DATE_FORMAT).format(ropEndTime))
                .toString();
        final String fileCollectionTaskGroupId = fileCollectionGroupType + ropTime;
        final FileCollectionTaskGroup taskGroup = taskGroups.get(fileCollectionTaskGroupId);
        if (taskGroup != null) {
            taskGroup.getTasksIds().remove(fileCollectionTaskId);
            if (taskGroup.getTasksIds().isEmpty()) {
                taskGroup.getTimerContext().stop();
                taskGroups.remove(fileCollectionTaskGroupId);
            }
        }
    }

    /**
     * Gets count statistical file collection duration per rop.
     *
     * @return the count statistical file collection duration per rop
     */
    @MonitoredAttribute(displayName = STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP + ".count", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountStatisticalFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getCount();
    }

    /**
     * Gets max statistical file collection duration per rop.
     *
     * @return the max statistical file collection duration per rop
     */
    @MonitoredAttribute(displayName = STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP + ".max", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxStatisticalFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMax();
    }

    /**
     * Gets median statistical file collection duration per rop.
     *
     * @return the median statistical file collection duration per rop
     */
    @MonitoredAttribute(displayName = STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP + ".median", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianStatisticalFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMedian();
    }

    /**
     * Gets min statistical file collection duration per rop.
     *
     * @return the min statistical file collection duration per rop
     */
    @MonitoredAttribute(displayName = STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP + ".min", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinStatisticalFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMin();
    }

    /**
     * Gets count statistical file collection duration per node.
     *
     * @return the count statistical file collection duration per node
     */
    @MonitoredAttribute(displayName = STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE + ".count", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountStatisticalFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getCount();
    }

    /**
     * Gets max statistical file collection duration per node.
     *
     * @return the max statistical file collection duration per node
     */
    @MonitoredAttribute(displayName = STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE + ".max", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxStatisticalFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMax();
    }

    /**
     * Gets median statistical file collection duration per node.
     *
     * @return the median statistical file collection duration per node
     */
    @MonitoredAttribute(displayName = STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE + ".median", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianStatisticalFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMedian();
    }

    /**
     * Gets min statistical file collection duration per node.
     *
     * @return the min statistical file collection duration per node
     */
    @MonitoredAttribute(displayName = STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE + ".min", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinStatisticalFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMin();
    }

    /**
     * Gets count cell trace file collection duration per rop.
     *
     * @return the count cell trace file collection duration per rop
     */
    @MonitoredAttribute(displayName = CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP + ".count", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountCellTraceFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP).getCount();
    }

    /**
     * Gets max cell trace file collection duration per rop.
     *
     * @return the max cell trace file collection duration per rop
     */
    @MonitoredAttribute(displayName = CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP + ".max", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxCellTraceFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMax();
    }

    /**
     * Gets median cell trace file collection duration per rop.
     *
     * @return the median cell trace file collection duration per rop
     */
    @MonitoredAttribute(displayName = CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP + ".median", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianCellTraceFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMedian();
    }

    /**
     * Gets min cell trace file collection duration per rop.
     *
     * @return the min cell trace file collection duration per rop
     */
    @MonitoredAttribute(displayName = CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP + ".min", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinCellTraceFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMin();
    }

    /**
     * Gets count cell trace file collection duration per node.
     *
     * @return the count cell trace file collection duration per node
     */
    @MonitoredAttribute(displayName = CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE + ".count", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountCellTraceFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE).getCount();
    }

    /**
     * Gets max cell trace file collection duration per node.
     *
     * @return the max cell trace file collection duration per node
     */
    @MonitoredAttribute(displayName = CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE + ".max", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxCellTraceFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMax();
    }

    /**
     * Gets median cell trace file collection duration per node.
     *
     * @return the median cell trace file collection duration per node
     */
    @MonitoredAttribute(displayName = CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE + ".median", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianCellTraceFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMedian();
    }

    /**
     * Gets min cell trace file collection duration per node.
     *
     * @return the min cell trace file collection duration per node
     */
    @MonitoredAttribute(displayName = CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE + ".min", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinCellTraceFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMin();
    }

    /**
     * Gets count single rop recovery statistical file collection duration per rop.
     *
     * @return the count single rop recovery statistical file collection duration per rop
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP + ".count",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountSingleRopRecoveryStatisticalFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getCount();
    }

    /**
     * Gets max single rop recovery statistical file collection duration per rop.
     *
     * @return the max single rop recovery statistical file collection duration per rop
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP + ".max",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxSingleRopRecoveryStatisticalFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMax();
    }

    /**
     * Gets median single rop recovery statistical file collection duration per rop.
     *
     * @return the median single rop recovery statistical file collection duration per rop
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP + ".median",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianSingleRopRecoveryStatisticalFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMedian();
    }

    /**
     * Gets min single rop recovery statistical file collection duration per rop.
     *
     * @return the min single rop recovery statistical file collection duration per rop
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP + ".min",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinSingleRopRecoveryStatisticalFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMin();
    }

    /**
     * Gets count single rop recovery statistical file collection duration per node.
     *
     * @return the count single rop recovery statistical file collection duration per node
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE + ".count",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountSingleRopRecoveryStatisticalFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getCount();
    }

    /**
     * Gets max single rop recovery statistical file collection duration per node.
     *
     * @return the max single rop recovery statistical file collection duration per node
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE + ".max",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxSingleRopRecoveryStatisticalFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMax();
    }

    /**
     * Gets median single rop recovery statistical file collection duration per node.
     *
     * @return the median single rop recovery statistical file collection duration per node
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE + ".median",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianSingleRopRecoveryStatisticalFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMedian();
    }

    /**
     * Gets min single rop recovery statistical file collection duration per node.
     *
     * @return the min single rop recovery statistical file collection duration per node
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE + ".min",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinSingleRopRecoveryStatisticalFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMin();
    }

    /**
     * Gets count single rop recovery cell trace file collection duration per rop.
     *
     * @return the count single rop recovery cell trace file collection duration per rop
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP + ".count",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountSingleRopRecoveryCellTraceFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP).getCount();
    }

    /**
     * Gets max single rop recovery cell trace file collection duration per rop.
     *
     * @return the max single rop recovery cell trace file collection duration per rop
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP + ".max",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxSingleRopRecoveryCellTraceFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMax();
    }

    /**
     * Gets median single rop recovery cell trace file collection duration per rop.
     *
     * @return the median single rop recovery cell trace file collection duration per rop
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP + ".median",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianSingleRopRecoveryCellTraceFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMedian();
    }

    /**
     * Gets min single rop recovery cell trace file collection duration per rop.
     *
     * @return the min single rop recovery cell trace file collection duration per rop
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP + ".min",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinSingleRopRecoveryCellTraceFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMin();
    }

    /**
     * Gets count single rop recovery cell trace file collection duration per node.
     *
     * @return the count single rop recovery cell trace file collection duration per node
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE + ".count",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountSingleRopRecoveryCellTraceFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE).getCount();
    }

    /**
     * Gets max single rop recovery cell trace file collection duration per node.
     *
     * @return the max single rop recovery cell trace file collection duration per node
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE + ".max",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxSingleRopRecoveryCellTraceFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMax();
    }

    /**
     * Gets median single rop recovery cell trace file collection duration per node.
     *
     * @return the median single rop recovery cell trace file collection duration per node
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE + ".median",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianSingleRopRecoveryCellTraceFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMedian();
    }

    /**
     * Gets min single rop recovery cell trace file collection duration per node.
     *
     * @return the min single rop recovery cell trace file collection duration per node
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE + ".min",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinSingleRopRecoveryCellTraceFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMin();
    }

    /**
     * Gets count scheduled recovery statistical file collection duration per rop.
     *
     * @return the count scheduled recovery statistical file collection duration per rop
     */
    @MonitoredAttribute(displayName = SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP + ".count",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountScheduledRecoveryStatisticalFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getCount();
    }

    /**
     * Gets max scheduled recovery statistical file collection duration per rop.
     *
     * @return the max scheduled recovery statistical file collection duration per rop
     */
    @MonitoredAttribute(displayName = SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP + ".max",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxScheduledRecoveryStatisticalFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMax();
    }

    /**
     * Gets median scheduled recovery statistical file collection duration per rop.
     *
     * @return the median scheduled recovery statistical file collection duration per rop
     */
    @MonitoredAttribute(displayName = SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP + ".median",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianScheduledRecoveryStatisticalFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMedian();
    }

    /**
     * Gets min scheduled recovery statistical file collection duration per rop.
     *
     * @return the min scheduled recovery statistical file collection duration per rop
     */
    @MonitoredAttribute(displayName = SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP + ".min",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinScheduledRecoveryStatisticalFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMin();
    }

    /**
     * Gets count scheduled recovery statistical file collection duration per node.
     *
     * @return the count scheduled recovery statistical file collection duration per node
     */
    @MonitoredAttribute(displayName = SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE + ".count",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountScheduledRecoveryStatisticalFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getCount();
    }

    /**
     * Gets max scheduled recovery statistical file collection duration per node.
     *
     * @return the max scheduled recovery statistical file collection duration per node
     */
    @MonitoredAttribute(displayName = SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE + ".max",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxScheduledRecoveryStatisticalFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMax();
    }

    /**
     * Gets median scheduled recovery statistical file collection duration per node.
     *
     * @return the median scheduled recovery statistical file collection duration per node
     */
    @MonitoredAttribute(displayName = SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE + ".median",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianScheduledRecoveryStatisticalFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMedian();
    }

    /**
     * Gets min scheduled recovery statistical file collection duration per node.
     *
     * @return the min scheduled recovery statistical file collection duration per node
     */
    @MonitoredAttribute(displayName = SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE + ".min",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinScheduledRecoveryStatisticalFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMin();
    }

    /**
     * Gets count startup recovery statistical file collection duration.
     *
     * @return the count startup recovery statistical file collection duration
     */
    @MonitoredAttribute(displayName = STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION + ".count",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountStartupRecoveryStatisticalFileCollectionDuration() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION).getCount();
    }

    /**
     * Gets max startup recovery statistical file collection duration.
     *
     * @return the max startup recovery statistical file collection duration
     */
    @MonitoredAttribute(displayName = STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION + ".max", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxStartupRecoveryStatisticalFileCollectionDuration() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION).getSnapshot().getMax();
    }

    /**
     * Gets median startup recovery statistical file collection duration.
     *
     * @return the median startup recovery statistical file collection duration
     */
    @MonitoredAttribute(displayName = STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION + ".median",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianStartupRecoveryStatisticalFileCollectionDuration() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION).getSnapshot().getMedian();
    }

    /**
     * Gets min startup recovery statistical file collection duration.
     *
     * @return the min startup recovery statistical file collection duration
     */
    @MonitoredAttribute(displayName = STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION + ".min", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinStartupRecoveryStatisticalFileCollectionDuration() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION).getSnapshot().getMin();
    }

    /**
     * Gets count startup recovery statistical file collection duration per node.
     *
     * @return the count startup recovery statistical file collection duration per node
     */
    @MonitoredAttribute(displayName = STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE + ".count",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountStartupRecoveryStatisticalFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getCount();
    }

    /**
     * Gets max startup recovery statistical file collection duration per node.
     *
     * @return the max startup recovery statistical file collection duration per node
     */
    @MonitoredAttribute(displayName = STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE + ".max",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxStartupRecoveryStatisticalFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMax();
    }

    /**
     * Gets median startup recovery statistical file collection duration per node.
     *
     * @return the median startup recovery statistical file collection duration per node
     */
    @MonitoredAttribute(displayName = STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE + ".median",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianStartupRecoveryStatisticalFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMedian();
    }

    /**
     * Gets min startup recovery statistical file collection duration per node.
     *
     * @return the min startup recovery statistical file collection duration per node
     */
    @MonitoredAttribute(displayName = STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE + ".min",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinStartupRecoveryStatisticalFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMin();
    }

    /**
     * Gets the number of statistical file collection collected files.
     *
     * @return the number of statistical file collection collected files
     */
    @MonitoredAttribute(displayName = STATISTICAL_FILE_COLLECTION_COLLECTED_FILES, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getFileCollectionInstrumentationStatisticalFileCollectionCollectedFiles() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(STATISTICAL_FILE_COLLECTION_COLLECTED_FILES).getCount();
    }

    /**
     * Gets the number of  statistical file collection missed files.
     *
     * @return the number of statistical file collection missed files
     */
    @MonitoredAttribute(displayName = STATISTICAL_FILE_COLLECTION_MISSED_FILES, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getStatisticalFileCollectionMissedFiles() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(STATISTICAL_FILE_COLLECTION_MISSED_FILES).getCount();
    }

    /**
     * Gets the number of cell trace file collection collected files.
     *
     * @return the number of cell trace file collection collected files
     */
    @MonitoredAttribute(displayName = CELLTRACE_FILE_COLLECTION_COLLECTED_FILES, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCellTraceFileCollectionCollectedFiles() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(CELLTRACE_FILE_COLLECTION_COLLECTED_FILES).getCount();
    }

    /**
     * Gets the number of cell trace file collection missed files.
     *
     * @return the number of cell trace file collection missed files
     */
    @MonitoredAttribute(displayName = CELLTRACE_FILE_COLLECTION_MISSED_FILES, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCellTraceFileCollectionMissedFiles() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(CELLTRACE_FILE_COLLECTION_MISSED_FILES).getCount();
    }

    /**
     * Gets the number of single rop recovery statistical file collection collected files.
     *
     * @return the number of single rop recovery statistical file collection collected files
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES,
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getSingleRopRecoveryStatisticalFileCollectionCollectedFiles() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES).getCount();
    }

    /**
     * Gets the number of single rop recovery statistical file collection missed files.
     *
     * @return the number of single rop recovery statistical file collection missed files
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getSingleRopRecoveryStatisticalFileCollectionMissedFiles() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(SINGLE_ROP_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES).getCount();
    }

    /**
     * Gets the number of single rop recovery cell trace file collection collected files.
     *
     * @return the number of single rop recovery cell trace file collection collected files
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_COLLECTED_FILES, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getSingleRopRecoveryCellTraceFileCollectionCollectedFiles() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_COLLECTED_FILES).getCount();
    }

    /**
     * Gets the number of single rop recovery cell trace file collection missed files.
     *
     * @return the number of single rop recovery cell trace file collection missed files
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_MISSED_FILES, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getSingleRopRecoveryCellTraceFileCollectionMissedFiles() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(SINGLE_ROP_RECOVERY_CELLTRACE_FILE_COLLECTION_MISSED_FILES).getCount();
    }

    /**
     * Gets the number of scheduled recovery statistical file collection collected files.
     *
     * @return the number of scheduled recovery statistical file collection collected files
     */
    @MonitoredAttribute(displayName = SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getScheduledRecoveryStatisticalFileCollectionCollectedFiles() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES).getCount();
    }

    /**
     * Gets the number of scheduled recovery statistical file collection missed files.
     *
     * @return the number of scheduled recovery statistical file collection missed files
     */
    @MonitoredAttribute(displayName = SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getScheduledRecoveryStatisticalFileCollectionMissedFiles() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(SCHEDULED_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES).getCount();
    }

    /**
     * Gets the number of startup recovery statistical file collection collected files.
     *
     * @return the number of startup recovery statistical file collection collected files
     */
    @MonitoredAttribute(displayName = STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getStartupRecoveryStatisticalFileCollectionCollectedFiles() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_COLLECTED_FILES).getCount();
    }

    /**
     * Gets the number of startup recovery statistical file collection missed files.
     *
     * @return the number of startup recovery statistical file collection missed files
     */
    @MonitoredAttribute(displayName = STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getStartupRecoveryStatisticalFileCollectionMissedFiles() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(STARTUP_RECOVERY_STATISTICAL_FILE_COLLECTION_MISSED_FILES).getCount();
    }

    /**
     * Gets count ebm file collection duration per rop.
     *
     * @return the count ebm file collection duration per rop
     */
    @MonitoredAttribute(displayName = EBM_FILE_COLLECTION_DURATION_PER_ROP + ".count", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountEbmFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(EBM_FILE_COLLECTION_DURATION_PER_ROP).getCount();
    }

    /**
     * Gets max ebm file collection duration per rop.
     *
     * @return the max ebm file collection duration per rop
     */
    @MonitoredAttribute(displayName = EBM_FILE_COLLECTION_DURATION_PER_ROP + ".max", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxEbmFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(EBM_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMax();
    }

    /**
     * Gets median ebm file collection duration per rop.
     *
     * @return the median ebm file collection duration per rop
     */
    @MonitoredAttribute(displayName = EBM_FILE_COLLECTION_DURATION_PER_ROP + ".median", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianEbmFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(EBM_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMedian();
    }

    /**
     * Gets min ebm file collection duration per rop.
     *
     * @return the min ebm file collection duration per rop
     */
    @MonitoredAttribute(displayName = EBM_FILE_COLLECTION_DURATION_PER_ROP + ".min", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinEbmFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(EBM_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMin();
    }

    /**
     * Gets count ebm file collection duration per node.
     *
     * @return the count ebm file collection duration per node
     */
    @MonitoredAttribute(displayName = EBM_FILE_COLLECTION_DURATION_PER_NODE + ".count", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountEbmFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(EBM_FILE_COLLECTION_DURATION_PER_NODE).getCount();
    }

    /**
     * Gets max ebm file collection duration per node.
     *
     * @return the max ebm file collection duration per node
     */
    @MonitoredAttribute(displayName = EBM_FILE_COLLECTION_DURATION_PER_NODE + ".max", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxEbmFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(EBM_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMax();
    }

    /**
     * Gets median ebm file collection duration per node.
     *
     * @return the median ebm file collection duration per node
     */
    @MonitoredAttribute(displayName = EBM_FILE_COLLECTION_DURATION_PER_NODE + ".median", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianEbmFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(EBM_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMedian();
    }

    /**
     * Gets min ebm file collection duration per node.
     *
     * @return the min ebm file collection duration per node
     */
    @MonitoredAttribute(displayName = EBM_FILE_COLLECTION_DURATION_PER_NODE + ".min", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinEbmFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(EBM_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMin();
    }

    /**
     * Gets count single rop recovery ebm file collection duration per rop.
     *
     * @return the count single rop recovery ebm file collection duration per rop
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_ROP + ".count",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountSingleRopRecoveryEbmFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_ROP).getCount();
    }

    /**
     * Gets max single rop recovery ebm file collection duration per rop.
     *
     * @return the max single rop recovery ebm file collection duration per rop
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_ROP + ".max",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxSingleRopRecoveryEbmFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMax();
    }

    /**
     * Gets median single rop recovery ebm file collection duration per rop.
     *
     * @return the median single rop recovery ebm file collection duration per rop
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_ROP + ".median",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianSingleRopRecoveryEbmFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMedian();
    }

    /**
     * Gets min single rop recovery ebm file collection duration per rop.
     *
     * @return the min single rop recovery ebm file collection duration per rop
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_ROP + ".min",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinSingleRopRecoveryEbmFileCollectionDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_ROP).getSnapshot().getMin();
    }

    /**
     * Gets count single rop recovery ebm file collection duration per node.
     *
     * @return the count single rop recovery ebm file collection duration per node
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_NODE + ".count",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountSingleRopRecoveryEbmFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_NODE).getCount();
    }

    /**
     * Gets max single rop recovery ebm file collection duration per node.
     *
     * @return the max single rop recovery ebm file collection duration per node
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_NODE + ".max",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxSingleRopRecoveryEbmFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMax();
    }

    /**
     * Gets median single rop recovery ebm file collection duration per node.
     *
     * @return the median single rop recovery ebm file collection duration per node
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_NODE + ".median",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianSingleRopRecoveryEbmFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMedian();
    }

    /**
     * Gets min single rop recovery ebm file collection duration per node.
     *
     * @return the min single rop recovery ebm file collection duration per node
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_NODE + ".min",
            visibility = MonitoredAttribute.Visibility.ALL, interval = MonitoredAttribute.Interval.ONE_MIN,
            units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinSingleRopRecoveryEbmFileCollectionDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_DURATION_PER_NODE).getSnapshot().getMin();
    }

    /**
     * Gets the number of ebm file collection collected files.
     *
     * @return the number of ebm file collection collected files
     */
    @MonitoredAttribute(displayName = EBM_FILE_COLLECTION_COLLECTED_FILES, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getEbmFileCollectionCollectedFiles() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(EBM_FILE_COLLECTION_COLLECTED_FILES).getCount();
    }

    /**
     * Gets the number of ebm file collection missed files.
     *
     * @return the number of ebm file collection missed files
     */
    @MonitoredAttribute(displayName = EBM_FILE_COLLECTION_MISSED_FILES, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getEbmFileCollectionMissedFiles() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(EBM_FILE_COLLECTION_MISSED_FILES).getCount();
    }

    /**
     * Gets the number of single rop recovery ebm file collection collected files.
     *
     * @return the number of single rop recovery ebm file collection collected files
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_COLLECTED_FILES, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getSingleRopRecoveryEbmFileCollectionCollectedFiles() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_COLLECTED_FILES).getCount();
    }

    /**
     * Gets the number of single rop recovery ebm file collection missed files.
     *
     * @return the number of single rop recovery ebm file collection missed files
     */
    @MonitoredAttribute(displayName = SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_MISSED_FILES, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getSingleRopRecoveryEbmFileCollectionMissedFiles() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(SINGLE_ROP_RECOVERY_EBM_FILE_COLLECTION_MISSED_FILES).getCount();
    }

    /**
     * The  File collection task group.
     */
    public class FileCollectionTaskGroup {
        private final String groupId;
        private final Timer.Context timerContext;
        private final Set<String> tasksIds = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

        /**
         * Instantiates a new File collection task group.
         *
         * @param groupId
         *         the group id
         * @param timerContext
         *         the timer context
         */
        public FileCollectionTaskGroup(final String groupId, final Timer.Context timerContext) {
            if (groupId == null) {
                throw new IllegalArgumentException("groupId should be not null");
            }
            this.groupId = groupId;
            if (timerContext == null) {
                throw new IllegalArgumentException("timerContext should be not null");
            }
            this.timerContext = timerContext;
        }

        /**
         * Gets group id.
         *
         * @return the group id
         */
        public String getGroupId() {
            return groupId;
        }

        /**
         * Gets timer context.
         *
         * @return the timer context
         */
        public Timer.Context getTimerContext() {
            return timerContext;
        }

        /**
         * Gets tasks ids.
         *
         * @return the tasks ids
         */
        public Set<String> getTasksIds() {
            return tasksIds;
        }

    }

    /**
     * The Scheduled file recovery task group.
     */
    public class ScheduledFileRecoveryTaskGroup extends FileCollectionTaskGroup {

        private final Set<String> nodesAddresses = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

        /**
         * Instantiates a new Scheduled file recovery task group.
         *
         * @param groupId
         *         the group id
         * @param nodesAddresses
         *         the nodes addresses
         * @param timerContext
         *         the timer context
         */
        public ScheduledFileRecoveryTaskGroup(final String groupId, final Set<String> nodesAddresses, final Timer.Context timerContext) {
            super(groupId, timerContext);
            if (nodesAddresses == null) {
                throw new IllegalArgumentException("nodesAddresses should be not null");
            }
            this.nodesAddresses.addAll(nodesAddresses);
        }

        /**
         * Gets nodes addresses.
         *
         * @return the nodes addresses
         */
        public Set<String> getNodesAddresses() {
            return nodesAddresses;
        }
    }
}
