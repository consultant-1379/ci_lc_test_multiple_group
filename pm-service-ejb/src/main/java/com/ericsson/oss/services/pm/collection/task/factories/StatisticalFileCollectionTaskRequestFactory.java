/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.collection.task.factories;

import static com.ericsson.oss.pmic.api.collection.constants.FileCollectionModelledConfigConstants.Statistical.PROP_FILE_COLLECTION_PRIORITY_FOR_ROP;
import static com.ericsson.oss.services.pm.initiation.utils.CommonUtil.parseAndUpdateFileCollectionPriorityForRop;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.ericsson.oss.itpf.sdk.config.annotation.ConfigurationChangeNotification;
import com.ericsson.oss.itpf.sdk.config.annotation.Configured;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant;
import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo;
import com.ericsson.oss.services.pm.collection.task.factories.qualifier.FileCollectionTaskRequestFactory;
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest;
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;
import com.ericsson.oss.services.pm.initiation.config.listener.AbstractConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.util.RopTime;

/**
 * The Statistical file collection task request factory.
 */
@ApplicationScoped
@FileCollectionTaskRequestFactory(subscriptionType = SubscriptionType.STATISTICAL)
public class StatisticalFileCollectionTaskRequestFactory extends AbstractConfigurationChangeListener
    implements FileCollectionTaskRequestFactoryService {
    private static final String NODE_FDN_STRING = "|nodeFdn=";
    private static final String ROP_START_TIME_STRING = "|ropStartTime=";
    private static final String ROP_PERIOD_STRING = "|ropPeriod=";
    private static final String RECOVER_IN_NEXT_ROP_STRING = "|recoverInNextRop=";
    private static final String UNDERSCORE = "_";
    private static final String PIPE = "|";
    private static final Integer DEFAULT_PRIORITY = 6;

    @Inject
    @Configured(propertyName = PROP_FILE_COLLECTION_PRIORITY_FOR_ROP)
    private String pmicStatisticalFileCollectionPriorityForROP;

    protected final Map<Integer, Integer> ropToPriorityMap = new HashMap<>();

    @Override
    public FileCollectionTaskWrapper createFileCollectionTaskRequestWrapper(final String nodeFdn, final RopTime ropTime,
                                                                            final RopTimeInfo ropTimeInfo) {
        final String taskId = JobIdBuilder.buildFileCollectionJobId(nodeFdn, ropTime.getCurrentRopStartTimeInMilliSecs(), ropTime.getRopPeriod());
        final FileCollectionTaskRequest fileCollectionTaskRequest = new FileCollectionTaskRequest(nodeFdn, taskId,
            SubscriptionType.STATISTICAL.name(), ropTime.getCurrentRopStartTimeInMilliSecs(), ropTime.getRopPeriod(), true);

        final FileCollectionTaskWrapper fileCollectionTaskWrapper = new FileCollectionTaskWrapper(fileCollectionTaskRequest,
            ropTime.getCurrentROPPeriodEndTime(), ropTimeInfo);
        parseAndUpdateFileCollectionPriorityForRop(ropToPriorityMap, DEFAULT_PRIORITY, pmicStatisticalFileCollectionPriorityForROP,
            PROP_FILE_COLLECTION_PRIORITY_FOR_ROP);
        fileCollectionTaskWrapper.setPriority(ropToPriorityMap.get((int) ropTimeInfo.getRopTimeInSeconds()));
        return fileCollectionTaskWrapper;
    }

    public Integer getPriority(final int ropInSeconds) {
        return ropToPriorityMap.get(ropInSeconds);
    }

    void listenForFileCollectionPriorityForRop(
        @Observes @ConfigurationChangeNotification(
            propertyName = PROP_FILE_COLLECTION_PRIORITY_FOR_ROP) final String fileCollectionPriorityForRop) {
        logChange(PROP_FILE_COLLECTION_PRIORITY_FOR_ROP, pmicStatisticalFileCollectionPriorityForROP, fileCollectionPriorityForRop);
        pmicStatisticalFileCollectionPriorityForROP = fileCollectionPriorityForRop;
        parseAndUpdateFileCollectionPriorityForRop(ropToPriorityMap, DEFAULT_PRIORITY, fileCollectionPriorityForRop,
            PROP_FILE_COLLECTION_PRIORITY_FOR_ROP);
    }

    /**
     * Job id builder.
     */
    public static final class JobIdBuilder {

        private JobIdBuilder() {
            // utility class should not be instantiated.
        }

        /**
         * Creates JobIb for STATS file collection task
         *
         * @param nodeFdn
         *     - Fdn of the node
         * @param ropStartTime
         *     - Rop start time
         * @param ropPeriodInMilliseconds
         *     - Rop period in millseconds (i.e 900000 for 15 minutes rop)
         *
         * @return {@link String} JobId
         */
        public static String buildFileCollectionJobId(final String nodeFdn, final long ropStartTime, final long ropPeriodInMilliseconds) {
            return new StringBuilder(SubscriptionType.STATISTICAL.name()).append(UNDERSCORE)
                .append(FileCollectionConstant.FILE_COLLECTION_TASK_ID_PREFIX).append(NODE_FDN_STRING).append(nodeFdn)
                .append(ROP_START_TIME_STRING).append(ropStartTime).append(ROP_PERIOD_STRING).append(ropPeriodInMilliseconds)
                .append(RECOVER_IN_NEXT_ROP_STRING).append(true).append(PIPE).append(UUID.randomUUID()).toString();
        }
    }
}
