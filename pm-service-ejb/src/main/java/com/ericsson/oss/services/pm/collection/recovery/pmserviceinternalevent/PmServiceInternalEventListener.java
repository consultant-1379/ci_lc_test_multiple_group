/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.collection.recovery.pmserviceinternalevent;

import static com.ericsson.oss.pmic.dto.node.enums.NetworkElementType.SGSNMME;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.UETRACE_SUBSCRIPTION_ATTRIBUTES;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Event.SEND_FILE_COLLECTION_TASK_LIST;

import java.util.*;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.subscription.capability.SubscriptionCapabilityReader;
import com.ericsson.oss.services.pm.collection.api.FileCollectionTaskSenderLocal;
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;
import com.ericsson.oss.services.pm.collection.cache.FileCollectionActiveTaskCacheWrapper;
import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo;
import com.ericsson.oss.services.pm.collection.roptime.SupportedRopTimes;
import com.ericsson.oss.services.pm.collection.schedulers.FileCollectionTaskWrapperFactory;
import com.ericsson.oss.services.pm.collection.task.factories.UeTraceFileCollectionTaskRequestFactory;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;
import com.ericsson.oss.services.pm.common.constants.PmFeature;
import com.ericsson.oss.services.pm.common.events.PmServiceInternalEvent;
import com.ericsson.oss.services.pm.common.logging.PMICLog;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;
import com.ericsson.oss.services.pm.initiation.config.listener.ConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.util.RopTime;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import org.slf4j.Logger;



/**
 * The PmServiceInternalEvent Listener.
 */
@ApplicationScoped
public class PmServiceInternalEventListener {

    @Inject
    private Logger logger;
    @Inject
    private SupportedRopTimes supportedRopTimes;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private FileCollectionTaskSenderLocal fileCollectionTaskSender;
    @Inject
    private FileCollectionTaskWrapperFactory fileCollectionTaskFactory;
    @Inject
    private FileCollectionActiveTaskCacheWrapper fileCollectionActiveTasksCache;
    @Inject
    private PmCapabilityModelService pmCapabilityModelService;
    @Inject
    private NodeService nodeService;

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;


    @Inject
    private ConfigurationChangeListener configurationChangeListener;

    @Inject
    private SubscriptionCapabilityReader subscriptionCapabilityReader;

    @Inject
    private UeTraceFileCollectionTaskRequestFactory ueTraceFileCollectionTaskRequestFactory;
    /**
     * Observe PmServiceInternalEvent.
     *
     * @param pmServiceInternalEvent
     *            the PmServiceInternal Event
     */
    public void receivePmServiceInternalEvent(@Observes @Modeled final PmServiceInternalEvent pmServiceInternalEvent) {
        systemRecorder.eventCoarse(PMICLog.Event.BUILD_FILE_COLLECTION_TASK_LIST, getClass().getSimpleName(),
                "PmServiceInternalEvent with LostMediationServiceDateTime {} received for {} nodes.",
                pmServiceInternalEvent.getLostMediationServiceDateTime(), pmServiceInternalEvent.getNodeFdns().size());
        logger.debug("PmServiceInternalEvent {} received ", pmServiceInternalEvent);
        final long msFailoverTime = pmServiceInternalEvent.getLostMediationServiceDateTime();
        int fileCollectionRequestCount = 0;
        final Map<RopTime, List<FileCollectionTaskWrapper>> fileCollectionTaskWrapperMap = new HashMap<>();
        for (final String nodeFdn : pmServiceInternalEvent.getNodeFdns()) {
            final Set<ProcessRequestVO> processRequests = fileCollectionActiveTasksCache.getProcessRequestForRop(nodeFdn);
            logger.debug("Creating file collection tasks {}", processRequests.size());
            for (final ProcessRequestVO processRequestVO : processRequests) {
                final long ropPeriodInMillis = processRequestVO.getRopPeriod() * 1000L;
                final RopTimeInfo ropTimeInfo = supportedRopTimes.getRopTime(processRequestVO.getRopPeriod());
                final RopTime rop = getLostMediationRopTime(msFailoverTime, processRequestVO.getRopPeriod(), ropTimeInfo);
                logger.debug("Start rop time [{}], End rop time [{}], nodeName [{}]", rop.getCurrentRopStartTimeInMilliSecs(),
                        rop.getCurrentRopStartTimeInMilliSecs() + ropPeriodInMillis, nodeFdn);
                addFileCollectionTaskToMap(fileCollectionTaskWrapperMap, processRequestVO, ropTimeInfo, rop);
                fileCollectionRequestCount++;
            }
        }
        int uetraceTaskCount = addUetraceFileCollectionTasks(pmServiceInternalEvent.getNodeFdns(),fileCollectionTaskWrapperMap,msFailoverTime);
        fileCollectionRequestCount +=uetraceTaskCount;
        for (final Map.Entry<RopTime, List<FileCollectionTaskWrapper>> entry : fileCollectionTaskWrapperMap.entrySet()) {
            fileCollectionTaskSender.sendFileCollectionTasks(entry.getValue(), entry.getKey());
        }

        logger.info("For LostMediationService at {}, {} File collection  tasks sent.", pmServiceInternalEvent.getLostMediationServiceDateTime(),
                fileCollectionRequestCount);
        if (fileCollectionRequestCount > 0) {
            final String message = String.format("For LostMediationService at %s, %s File collection  tasks sent. ",
                    pmServiceInternalEvent.getLostMediationServiceDateTime(), fileCollectionRequestCount);
            systemRecorder.eventCoarse(SEND_FILE_COLLECTION_TASK_LIST, message, "Initiating LostMediationService File Collection");
        }
    }

    private int addUetraceFileCollectionTasks(final Set<String> nodeFdns, Map<RopTime, List<FileCollectionTaskWrapper>> fileCollectionTaskWrapperMap, final long msFailoverTime) {
        int taskCount = 0;
        final int ropTimePeriodInSeconds = RopPeriod.FIFTEEN_MIN.getDurationInSeconds();
        final RopTimeInfo ropTimeInfo = supportedRopTimes.getRopTime(ropTimePeriodInSeconds);
        if (shouldCreateUeTraceTasks()) {
            final RopTime ropTime =  getLostMediationRopTime(msFailoverTime, ropTimePeriodInSeconds,ropTimeInfo);
            try {
                final boolean activeUeTraceSubscriptionAvailable = checkActiveUeTraceSubscription();
                final List<String> ueTraceSupportedNodeTypes = pmCapabilityModelService
                        .getSupportedNodeTypesForPmFeatureCapability(PmFeature.UETRACE_FILE_COLLECTION);
                final List<String> mixedModeSupportedNodeTypes = pmCapabilityModelService
                        .getSupportedNodeTypesForPmFeatureCapability(PmFeature.SUPPORTED_MIXED_MODE_TECHNOLOGY);
                final List<Node> supportedNodes = nodeService
                        .findAllByNeType(ueTraceSupportedNodeTypes.toArray(new String[ueTraceSupportedNodeTypes.size()]));
                final List<Node> ueTraceSupportedNodes = filterUeTraceSupportedNodes(supportedNodes,nodeFdns);
                logger.debug("Processing {} nodes for uetrace file recovery",ueTraceSupportedNodes.size());
                taskCount = createUetraceFileCollectiontasks(fileCollectionTaskWrapperMap, ropTimeInfo,
                        ropTime, activeUeTraceSubscriptionAvailable, mixedModeSupportedNodeTypes, ueTraceSupportedNodes);
            } catch (DataAccessException e) {
                systemRecorder.eventCoarse(PMICLog.Event.BUILD_FILE_COLLECTION_TASK_LIST, "mspm failover",
                        "Failed to create Uetrace FileCollection Tasks " + e.getMessage());
                logger.error("Exception stacktrace: ", e);
            }
        }else {
            logger.info("No UE Trace FileCollectionTask requests will be created for this rop {} because there are either no active "
                            + "Ue Trace subscriptions with OutputMode File or UeTraceCollection configuration parameter is disabled.",
                    ropTimePeriodInSeconds);
        }
        return taskCount;
    }

    private int createUetraceFileCollectiontasks(Map<RopTime, List<FileCollectionTaskWrapper>> fileCollectionTaskWrapperMap, RopTimeInfo ropTimeInfo, RopTime ropTime, boolean thereIsAnActiveUeTraceSubscription, List<String> mixedModeSupportedNodeTypes, List<Node> ueTraceSupportedNodes) {
        int taskCount = 0;
        for (final Node node : ueTraceSupportedNodes) {
                if (shouldExcludeSgsnMme(node, thereIsAnActiveUeTraceSubscription)
                        || !isSupportedNode(node, mixedModeSupportedNodeTypes)
                        || !nodeService.isFileCollectionEnabled(node.getFdn())) {
                    logger.info("Node {} is not supported for uetrace recovery",node.getFdn());
                    continue;
                }
                if (!fileCollectionTaskWrapperMap.containsKey(ropTime)) {
                    fileCollectionTaskWrapperMap.put(ropTime, new ArrayList<FileCollectionTaskWrapper>());
                }
                fileCollectionTaskWrapperMap.get(ropTime).add(createFileCollectionTaskRequestAndAddToCache(node, ropTimeInfo, ropTime));
                taskCount++;
        }
        return taskCount;
    }

    private List<Node> filterUeTraceSupportedNodes(final List<Node> supportedNodes, final Set<String> nodeFdns) {
        List<Node> nodeList = new ArrayList<>();
        for(Node node : supportedNodes){
                if (node != null && nodeFdns.contains(node.getFdn())){
                    nodeList.add(node);
                }
        }
        return nodeList;
    }

    private FileCollectionTaskWrapper createFileCollectionTaskRequestAndAddToCache(final Node node, final RopTimeInfo ropTimeInfo, final RopTime ropTime) {

        final FileCollectionTaskRequest ueTraceFileCollectionRequest = ueTraceFileCollectionTaskRequestFactory
                .createFileCollectionTaskRequest(node.getFdn(), ropTime.getCurrentRopStartTimeInMilliSecs(), ropTime.getRopPeriod());
        return new FileCollectionTaskWrapper(ueTraceFileCollectionRequest,
                ropTime.getCurrentROPPeriodEndTime(), ropTimeInfo);
    }

    private boolean shouldCreateUeTraceTasks() {
        return configurationChangeListener.getUeTraceCollectionEnabled() || checkActiveUeTraceSubscription();
    }

    /**
     * The method checks in whether there exist any active UETrace subscriptions and output mode type is not STREAMING.
     *
     * @return true if exist active UETrace subscriptions and file collection applicable for atleast one Uetrace subscription.
     */
    private boolean checkActiveUeTraceSubscription() {
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
            logger.debug("Ignore SGSGN-MME Node: {}", node.getName());
            return true;
        }
        return false;
    }

    private boolean isSupportedNode(final Node node, final List<String> mixedModeSupportedNodeTypes) {
        return Node.isValidOssModelIdentity(node.getOssModelIdentity())
                && isSupportedTechnologyDomain(node, mixedModeSupportedNodeTypes);
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
     * Add a FileCollectionTask to Map for sending later
     *
     * @param fileCollectionTaskWrapperMap
     *            fileCollectionTaskWrapperMap
     * @param processRequestVO
     *            processRequestVO
     * @param ropTimeInfo
     *            ropTimeInfo
     * @param rop
     *            rop
     */
    private void addFileCollectionTaskToMap(
            final Map<RopTime, List<FileCollectionTaskWrapper>> fileCollectionTaskWrapperMap,
            final ProcessRequestVO processRequestVO,
            final RopTimeInfo ropTimeInfo, final RopTime rop) {
        final FileCollectionTaskWrapper fileCollectionTaskWrapper = fileCollectionTaskFactory
                .createFileCollectionTaskRequestWrapper(processRequestVO, rop, ropTimeInfo);
        if (!fileCollectionTaskWrapperMap.containsKey(rop)) {
            fileCollectionTaskWrapperMap.put(rop, new ArrayList<FileCollectionTaskWrapper>());
        }
        fileCollectionTaskWrapperMap.get(rop).add(fileCollectionTaskWrapper);
    }

    /**
     * Eg. If mediation lost at 10:21, Mediation Lost ROP is Last ROP, (10:15 + 00:05 < 10:21). If mediation lost at 10.19, Mediation Lost Rop is Last
     * to Last ROP, (10:15 + 00:05 > 10:19)
     *
     * @param lostMspmInstanceTime,
     *            mediation lost time
     * @param ropPeriod
     *            ropPeriod
     * @param ropTimeInfo
     *            ropTimeInfo
     * @return lostMediationRopTime
     */
    private RopTime getLostMediationRopTime(final long lostMspmInstanceTime, final int ropPeriod, final RopTimeInfo ropTimeInfo) {
        final RopTime lastROP = new RopTime(lostMspmInstanceTime, ropPeriod).getLastROP(1);
        final long ropEndTime = lastROP.getCurrentRopStartTimeInMilliSecs() + ropTimeInfo.getRopTimeInMilliSecond();
        if (ropEndTime + ropTimeInfo.getCollectionDelayInMilliSecond() > lostMspmInstanceTime) {
            return lastROP.getLastROP(1);
        } else {
            return lastROP;
        }
    }

}
