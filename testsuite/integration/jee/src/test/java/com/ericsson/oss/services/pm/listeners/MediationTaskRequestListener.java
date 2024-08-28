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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import javax.ejb.AccessTimeout;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled;
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionDeltaRecoveryTaskRequest;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;
import com.ericsson.oss.services.pm.initiation.tasks.ScannerDeletionTaskRequest;
import com.ericsson.oss.services.pm.initiation.tasks.ScannerPollingTaskRequest;
import com.ericsson.oss.services.pm.initiation.tasks.ScannerResumptionTaskRequest;
import com.ericsson.oss.services.pm.initiation.tasks.ScannerSuspensionTaskRequest;
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionActivationTaskRequest;
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionDeactivationTaskRequest;
import com.ericsson.oss.services.pm.integration.test.stubs.PmMediationServiceStub;

@Singleton
@Lock(LockType.READ)
public class MediationTaskRequestListener {

    private final Map<String, List<MediationTaskRequest>> tasksReceived = new ConcurrentHashMap<>();
    @Inject
    Logger logger;
    private CountDownLatch scannerPollingCL;
    private CountDownLatch fileCollectionTaskCL;
    private CountDownLatch startupRecoveryFileCollectionTaskCL;
    private CountDownLatch recoveryOneRopFileCollectionTaskCL;
    private CountDownLatch filecollectionRecoveryCL;
    private CountDownLatch nodeReconnectRecoveryCL;
    private CountDownLatch activationCL;
    private CountDownLatch deactivationCL;
    private CountDownLatch cellTraceCollectionCL;
    private CountDownLatch filecollectionRecoveryForListOfNodesCL;

    private CountDownLatch scannerDeletionTaskCL;

    private List<String> recoveryNodeList;

    private Boolean useStubbedMediation = false;

    @Inject
    private PmMediationServiceStub pmMediationServiceStub;

    @Lock(LockType.WRITE)
    @AccessTimeout(value = 60000)
    public void processFileCollectionTask(@Observes @Modeled(acceptSubclasses = true) final MediationTaskRequest task) {
        logger.debug("********** MediationTaskRequest for node {} received. Task type is {} Details: {} ********* ", task.getNodeAddress(),
                task.getClass().getSimpleName(), task);
        List<MediationTaskRequest> nodeTasks = tasksReceived.get(task.getNodeAddress());
        if (nodeTasks == null) {
            nodeTasks = new CopyOnWriteArrayList<>();
            tasksReceived.put(task.getNodeAddress(), nodeTasks);
        }
        nodeTasks.add(task);

        if (task instanceof FileCollectionDeltaRecoveryTaskRequest) {
            if (recoveryNodeList.contains(task.getNodeAddress()) && filecollectionRecoveryForListOfNodesCL != null) {
                recoveryNodeList.remove(task.getNodeAddress());
                filecollectionRecoveryForListOfNodesCL.countDown();
            } else if (filecollectionRecoveryCL != null) {
                filecollectionRecoveryCL.countDown();
            }
        } else if (task instanceof ScannerPollingTaskRequest && scannerPollingCL != null) {
            scannerPollingCL.countDown();
        } else if (task instanceof SubscriptionActivationTaskRequest) {
            if (useStubbedMediation) {
                pmMediationServiceStub.handleSubscriptionActivationTaskRequest(task);
            }
            if (activationCL != null) {
                activationCL.countDown();
            }
        } else if (task instanceof SubscriptionDeactivationTaskRequest) {
            if (useStubbedMediation) {
                pmMediationServiceStub.handleSubscriptionDeactivationTaskRequest(task);
            }
            if (deactivationCL != null) {
                deactivationCL.countDown();
            }
        } else if (task instanceof ScannerDeletionTaskRequest && scannerDeletionTaskCL != null) {
            scannerDeletionTaskCL.countDown();
        } else if (task instanceof FileCollectionTaskRequest) {
            logger.debug("Task is instance of FileCollectionTask with taskId {} ", task.getJobId());
            final FileCollectionTaskRequest fileCollectionTaskRequest = (FileCollectionTaskRequest) task;
            if (SubscriptionType.CELLTRACE.name().equals(fileCollectionTaskRequest.getSubscriptionType()) && cellTraceCollectionCL != null) {
                cellTraceCollectionCL.countDown();
            } else {
                if (task.getJobId().contains(FileCollectionConstant.FILE_COLLECTION_TASK_ID_PREFIX) && fileCollectionTaskCL != null) {
                    fileCollectionTaskCL.countDown();
                } else if (task.getJobId().contains(FileCollectionConstant.FILE_COLLECTION_SINGLE_ROP_RECOVERY_TASK_ID_PREFIX)
                        && recoveryOneRopFileCollectionTaskCL != null) {
                    recoveryOneRopFileCollectionTaskCL.countDown();
                } else if (task.getJobId().contains(FileCollectionConstant.FILE_COLLECTION_RECOVERY_ON_STARTUP_TASK_ID_PREFIX)
                        && startupRecoveryFileCollectionTaskCL != null) {
                    startupRecoveryFileCollectionTaskCL.countDown();
                } else if (task.getJobId().contains(FileCollectionConstant.FILE_COLLECTION_RECOVERY_ON_NODE_RECONNECT_TASK_ID_PREFIX)
                        && nodeReconnectRecoveryCL != null) {
                    nodeReconnectRecoveryCL.countDown();
                } else if (task.getJobId().contains(FileCollectionConstant.FILE_COLLECTION_TASK_ID_PREFIX)
                        && ((FileCollectionTaskRequest) task).isRecoverInNextRop() && recoveryOneRopFileCollectionTaskCL != null) {
                    recoveryOneRopFileCollectionTaskCL.countDown();
                } else {
                    logger.debug("No matching predefined file Collection Task Prefix, Task Prefix value: [{}] ", task.getJobId());
                }
            }
        }
    }

    public int getNumberOfCellTraceFileCollectionTasks(final String nodeAddress) {
        int numberOfTasks = 0;
        final List<MediationTaskRequest> nodeTasks = tasksReceived.get(nodeAddress);
        if (nodeTasks != null) {
            for (final MediationTaskRequest mediationTaskRequest : nodeTasks) {
                if (mediationTaskRequest instanceof FileCollectionTaskRequest) {
                    final FileCollectionTaskRequest fileCollectionTaskRequest = (FileCollectionTaskRequest) mediationTaskRequest;
                    if (SubscriptionType.CELLTRACE.name().equals(fileCollectionTaskRequest.getSubscriptionType())
                            && fileCollectionTaskRequest.isRecoverInNextRop()) {
                        numberOfTasks++;
                    }
                }
            }
        }
        logger.debug("Found {} CellTrace non recovery jobs for node {}.", tasksReceived, nodeAddress);
        return numberOfTasks;
    }

    public List<MediationTaskRequest> getCellTraceTasksReceived(final String nodeAddress) {
        final List<MediationTaskRequest> nodeTasks = tasksReceived.get(nodeAddress);
        final List<MediationTaskRequest> cellTraceTasks = new ArrayList<MediationTaskRequest>();
        if (nodeTasks != null) {
            for (final MediationTaskRequest task : nodeTasks) {
                if (task instanceof FileCollectionTaskRequest) {
                    final FileCollectionTaskRequest fileCollectionTaskRequest = (FileCollectionTaskRequest) task;
                    if (SubscriptionType.CELLTRACE.name().equals(fileCollectionTaskRequest.getSubscriptionType())) {
                        cellTraceTasks.add(task);
                    }
                }
            }
        }
        return cellTraceTasks;

    }

    public int getTotalCellTraceFileCollectionNonRecoveryTask() {
        int total = 0;
        for (final List<MediationTaskRequest> nodeTasks : tasksReceived.values()) {
            for (final MediationTaskRequest mediationTaskRequest : nodeTasks) {
                if (mediationTaskRequest instanceof FileCollectionTaskRequest) {
                    final FileCollectionTaskRequest fileCollectionTaskRequest = (FileCollectionTaskRequest) mediationTaskRequest;
                    if (SubscriptionType.CELLTRACE.name().equals(fileCollectionTaskRequest.getSubscriptionType())
                            && fileCollectionTaskRequest.isRecoverInNextRop()) {
                        total++;
                    }
                }
            }
        }
        logger.debug("Found total {} CellTrace Non recovery file collection tasks.", total);
        return total;
    }

    /**
     * @param nodeAddress
     *
     * @return Counts the total number of task including normal and recovery for a particular nodeAddress
     */
    public int getNumberOfTasks(final String nodeAddress) {
        int numberOfTasks = 0;
        final List<MediationTaskRequest> tasksNode = tasksReceived.get(nodeAddress);
        if (tasksNode != null) {
            numberOfTasks = tasksNode.size();
        }
        logger.debug("Found {} total tasks sent for node {}.", numberOfTasks, nodeAddress);
        return numberOfTasks;
    }

    public int getNumberOfFileCollectionNonRecoveryTasks(final String nodeAddress) {
        int numberOfTasks = 0;
        final List<MediationTaskRequest> tasksNode = tasksReceived.get(nodeAddress);
        if (tasksNode != null) {
            for (final MediationTaskRequest mediationTaskRequest : tasksNode) {
                if (mediationTaskRequest instanceof FileCollectionTaskRequest
                        && ((FileCollectionTaskRequest) mediationTaskRequest).isRecoverInNextRop()) {
                    numberOfTasks++;
                }
            }
        }
        logger.debug("Found {} non recovery tasks sent for node {}.", numberOfTasks, nodeAddress);
        return numberOfTasks;
    }

    public int getNumberOfActivationTasksReceived(final String nodeAddress) {
        logger.debug("getting MediationTaskRequest for node {} ********* ", nodeAddress);
        int numberOfTasks = 0;
        final List<MediationTaskRequest> tasksNode = tasksReceived.get(nodeAddress);
        if (tasksNode != null) {
            for (final MediationTaskRequest mediationTaskRequest : tasksNode) {
                if (mediationTaskRequest instanceof SubscriptionActivationTaskRequest) {
                    final SubscriptionActivationTaskRequest performanceMonitoringActivationTask = (SubscriptionActivationTaskRequest) mediationTaskRequest;
                    if (performanceMonitoringActivationTask.getJobId().contains(SubscriptionType.STATISTICAL.name()) ||
                            performanceMonitoringActivationTask.getJobId().contains(SubscriptionType.CELLTRACE.name()) ||
                            performanceMonitoringActivationTask.getJobId().contains(SubscriptionType.RES.name())) {
                        numberOfTasks++;
                    }
                }
            }
        }
        logger.debug("Found Stats/Celltrace activation tasks for node {}.", numberOfTasks, nodeAddress);
        return numberOfTasks;
    }

    public int getNumberOfDeactivationTasksReceived(final String nodeAddress) {
        int numberOfTasks = 0;
        final List<MediationTaskRequest> tasks = tasksReceived.get(nodeAddress);
        if (tasks != null) {
            for (final MediationTaskRequest mediationTaskRequest : tasks) {
                if (mediationTaskRequest instanceof SubscriptionActivationTaskRequest) {
                    final SubscriptionDeactivationTaskRequest performanceMonitoringDeactivationTask = (SubscriptionDeactivationTaskRequest) mediationTaskRequest;
                    if (performanceMonitoringDeactivationTask.getJobId().contains(SubscriptionType.STATISTICAL.name()) ||
                            performanceMonitoringDeactivationTask.getJobId().contains(SubscriptionType.RES.name())) {
                        numberOfTasks++;
                    }
                }
            }
        }
        logger.debug("Found {} deactivation tasks for node {}.", numberOfTasks, nodeAddress);
        return numberOfTasks;
    }

    public int getNumberOfScannerResumptionTasksReceived(final String nodeAddress) {
        final List<MediationTaskRequest> tasks = tasksReceived.get(nodeAddress);
        if (tasks == null) {
            return 0;
        }
        int tasksNumber = 0;
        for (final MediationTaskRequest mediationTaskRequest : tasks) {
            if (mediationTaskRequest instanceof ScannerResumptionTaskRequest) {
                tasksNumber++;
            }
        }
        logger.debug("Found scanner resumption tasks for node {}.", tasksNumber, nodeAddress);
        return tasksNumber;
    }

    public int getNumberOfScannerSuspensionTasksReceived(final String nodeAddress) {
        final List<MediationTaskRequest> tasks = tasksReceived.get(nodeAddress);
        if (tasks == null) {
            return 0;
        }
        int tasksNumber = 0;
        for (final MediationTaskRequest mediationTaskRequest : tasks) {
            if (mediationTaskRequest instanceof ScannerSuspensionTaskRequest) {
                tasksNumber++;
            }
        }
        logger.debug("Found scanner suspension tasks for node {}.", tasksNumber, nodeAddress);
        return tasksNumber;
    }

    public int getNumberOfScannerDeletionTasksReceived(final String nodeAddress) {
        final List<MediationTaskRequest> tasks = tasksReceived.get(nodeAddress);
        if (tasks == null) {
            return 0;
        }
        int tasksNumber = 0;
        for (final MediationTaskRequest mediationTaskRequest : tasks) {
            if (mediationTaskRequest instanceof ScannerDeletionTaskRequest) {
                tasksNumber++;
            }
        }
        logger.debug("Found scanner deletion tasks for node {}.", tasksNumber, nodeAddress);
        return tasksNumber;
    }

    /**
     * @param nodeAddress
     *
     * @return Returns the recovery tasks for a particular nodeAddress
     */

    public List<MediationTaskRequest> getRecoveryTasks(final String nodeAddress) {
        logger.debug("Finding recovery tasks for node {} in current list.", nodeAddress);
        final List<MediationTaskRequest> recoveryTasks = new ArrayList<MediationTaskRequest>();
        final List<MediationTaskRequest> tasksNode = tasksReceived.get(nodeAddress);
        if (tasksNode != null) {
            for (final MediationTaskRequest task : tasksNode) {
                if (task instanceof FileCollectionDeltaRecoveryTaskRequest
                        || task instanceof FileCollectionTaskRequest && !((FileCollectionTaskRequest) task).isRecoverInNextRop()) {
                    recoveryTasks.add(task);
                }
            }
        }
        logger.debug("Found recovery tasks for node {}: {}.", nodeAddress, recoveryTasks);
        return recoveryTasks;
    }

    public List<MediationTaskRequest> getRecoveryTasks(final String nodeAddress, final String taskIdPrefix) {
        logger.debug("Finding recovery tasks for node {} in current list.", nodeAddress);
        final List<MediationTaskRequest> recoveryTasks = new ArrayList<MediationTaskRequest>();
        final List<MediationTaskRequest> tasksNode = tasksReceived.get(nodeAddress);
        if (tasksNode != null) {
            for (final MediationTaskRequest task : tasksNode) {
                if (task instanceof FileCollectionDeltaRecoveryTaskRequest
                        || task instanceof FileCollectionTaskRequest && !((FileCollectionTaskRequest) task).isRecoverInNextRop()) {
                    if (task.getJobId().contains(taskIdPrefix)) {
                        recoveryTasks.add(task);
                    }
                }
            }
        }
        logger.debug("Found recovery tasks for node {}: {}.", nodeAddress, recoveryTasks);
        return recoveryTasks;
    }

    /**
     * @param nodeAddress
     *
     * @return Counts the total number of recovery tasks for a particular nodeAddress
     */
    public int getNumberOfRecoveryTasks(final String nodeAddress, final String taskIdPrefix) {
        int numberOfRecoveryTasks = 0;
        logger.debug("Finding number of recovery tasks for node {} in current list.", nodeAddress);
        final List<MediationTaskRequest> recoveryTasksNode = getRecoveryTasks(nodeAddress);
        for (final MediationTaskRequest recoveryTask : recoveryTasksNode) {
            if (recoveryTask.getJobId().contains(taskIdPrefix)) {
                numberOfRecoveryTasks++;
                logger.debug("Found recovery task for node {} with prefix {}: {}.", nodeAddress, taskIdPrefix, recoveryTask.getJobId());
            }
        }
        logger.debug("Found {} recovery tasks for node {} with prefix {}.", numberOfRecoveryTasks, nodeAddress, taskIdPrefix);
        return numberOfRecoveryTasks;
    }

    /**
     * @param nodeAddress
     *
     * @return the list of tasks sent for this nodeAddress
     */

    public List<ScannerPollingTaskRequest> getScannerPollingTasks(final String nodeAddress) {
        final List<MediationTaskRequest> mtrList = tasksReceived.get(nodeAddress);
        final List<ScannerPollingTaskRequest> spjList = new ArrayList<>();
        if (mtrList != null) {
            for (final MediationTaskRequest mediationTaskRequest : mtrList) {
                if (mediationTaskRequest instanceof ScannerPollingTaskRequest) {
                    spjList.add((ScannerPollingTaskRequest) mediationTaskRequest);
                }
            }
        }
        logger.debug("Found Scannerpolling tasks sent for node {}: {}", spjList, nodeAddress);
        return spjList;
    }

    public int getTotalRecoveryTasksReceived() {
        logger.debug("Current tasks list size is {}.", tasksReceived.size());
        int total = 0;
        for (final Entry<String, List<MediationTaskRequest>> tasksNode : tasksReceived.entrySet()) {
            total += this.getNumberOfRecoveryTasks(tasksNode.getKey());
        }
        return total;
    }

    /**
     * @param nodeAddress
     *
     * @return Counts the total number of recovery tasks for a particular nodeAddress
     */
    public int getNumberOfRecoveryTasks(final String nodeAddress) {
        int numberOfRecoveryTasks = 0;
        logger.debug("Finding number of recovery tasks for node {} in current list.", nodeAddress);
        final List<MediationTaskRequest> tasksNode = tasksReceived.get(nodeAddress);
        if (tasksNode != null) {
            for (final MediationTaskRequest task : tasksNode) {
                if (task instanceof FileCollectionTaskRequest && !((FileCollectionTaskRequest) task).isRecoverInNextRop()) {
                    numberOfRecoveryTasks++;
                    logger.debug("Found recovery task for node {}: {}.", nodeAddress, task.getJobId());
                }
            }
        }
        logger.debug("Found {} recovery tasks for node {}.", numberOfRecoveryTasks, nodeAddress);
        return numberOfRecoveryTasks;
    }

    public int getTotalTasksReceived() {
        logger.debug("Current tasks list size is {}.", tasksReceived.size());
        int total = 0;
        for (final List<MediationTaskRequest> tasksNode : tasksReceived.values()) {
            total += tasksNode.size();
        }
        return total;
    }

    public int getTotalFileCollectionNonRecoveryTask() {
        int total = 0;
        for (final List<MediationTaskRequest> tasksNode : tasksReceived.values()) {
            for (final MediationTaskRequest mediationTaskRequest : tasksNode) {
                if (mediationTaskRequest instanceof FileCollectionTaskRequest
                        && ((FileCollectionTaskRequest) mediationTaskRequest).isRecoverInNextRop()) {
                    total++;
                }
            }
        }
        logger.debug("Found {} Non recovery file collection tasks.", total, tasksReceived);
        return total;
    }

    @Lock(LockType.WRITE)
    public void clear() {
        tasksReceived.clear();
    }

    public void setScannerPollingCountDownLatch(final CountDownLatch cl) {
        scannerPollingCL = cl;
    }

    public void setFileCollectionTaskCountDownLatch(final CountDownLatch cl) {
        fileCollectionTaskCL = cl;
    }

    public void setStartUpRecoveryFileCollectionTaskCountDownLatch(final CountDownLatch cl) {
        startupRecoveryFileCollectionTaskCL = cl;
    }

    public void setRecoveryOneRopFileCollectionTaskCountDownLatch(final CountDownLatch cl) {
        recoveryOneRopFileCollectionTaskCL = cl;
    }

    public void setNodeReconnectRecoveryTaskCountDownLatch(final CountDownLatch cl) {
        nodeReconnectRecoveryCL = cl;
    }

    public void setFileCollectionRecoveryTaskCountDownLatch(final CountDownLatch cl) {
        filecollectionRecoveryCL = cl;
    }

    public void setActivationCountdownLatch(final CountDownLatch cl) {
        activationCL = cl;
    }

    public void setDeactivationCountdownLatch(final CountDownLatch cl) {
        deactivationCL = cl;
    }

    public void setCellTraceCollectionCL(final CountDownLatch cellTraceCollectionCL) {
        this.cellTraceCollectionCL = cellTraceCollectionCL;
    }

    /**
     * @param cl
     */
    public void setFileCollectionRecoveryTaskCountDownLatchForListOfNodes(final CountDownLatch cl, final List<String> nodes) {
        recoveryNodeList = nodes;
        filecollectionRecoveryForListOfNodesCL = cl;
    }

    public void setScannerDeletionTaskCL(final CountDownLatch scannerDeletionTaskCL) {
        this.scannerDeletionTaskCL = scannerDeletionTaskCL;
    }

    @Lock(LockType.READ)
    public void setUseStubbedMediation(final Boolean useStubbedMediation) {
        this.useStubbedMediation = useStubbedMediation;
    }
}