/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.notification

import static com.ericsson.oss.mediation.ftp.jca.api.error.FtpErrors.*

import spock.lang.Unroll

import javax.cache.Cache
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache
import com.ericsson.oss.mediation.ftp.jca.api.error.FtpErrors
import com.ericsson.oss.pmic.cdi.test.util.SkeletonSpec
import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType
import com.ericsson.oss.services.pm.bdd.collection.utils.FileCollectionResultCreator
import com.ericsson.oss.services.pm.collection.cache.FileCollectionTaskCacheWrapper
import com.ericsson.oss.services.pm.collection.events.FileCollectionResult
import com.ericsson.oss.services.pm.collection.instrumentation.FileCollectionCycleInstrumentation
import com.ericsson.oss.services.pm.collection.instrumentation.FileCollectionStatistics
import com.ericsson.oss.services.pm.collection.notification.FileCollectionResultNotificationListener
import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest
import com.ericsson.oss.services.pm.generic.NodeService
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionResultStatistics
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper
import com.ericsson.oss.services.pm.initiation.util.constants.TimeConstants

/**
 +-----------------+----------------+------------------+-------------------+-----------------------------------------------------------------------------------------------------------------------------------+--------------+-------------------+
 | HasSuccessFiles | HasFailedFiles | RecoverInNextRop | IsTaskStatusError | ERRROR                                                                                                                            | SendRecovery | UpdateResultCache |
 +-----------------+----------------+------------------+-------------------+-----------------------------------------------------------------------------------------------------------------------------------+--------------+-------------------+
 | NO              | YES            | TRUE             | YES               | all except SOURCE_LOCATION_FILE_NOT_AVAILABLE, SYSTEM_INTERNAL_UNRECOVERABLE_ERROR,,FILE_ALREADY_EXISTS, FILE_SIZE_LIMIT_EXCEEDED | YES          | YES               |
 +-----------------+----------------+------------------+-------------------+-----------------------------------------------------------------------------------------------------------------------------------+--------------+-------------------+
 | NO              | YES            | NO               | YES               | all except SOURCE_LOCATION_FILE_NOT_AVAILABLE,,SYSTEM_INTERNAL_UNRECOVERABLE_ERROR,,FILE_ALREADY_EXISTS,,FILE_SIZE_LIMIT_EXCEEDED | NO           | YES               |
 +-----------------+----------------+------------------+-------------------+-----------------------------------------------------------------------------------------------------------------------------------+--------------+-------------------+
 | NO              | NO             | YES              | NO                | no error                                                                                                                          | NO           | NO                |
 +-----------------+----------------+------------------+-------------------+-----------------------------------------------------------------------------------------------------------------------------------+--------------+-------------------+
 | YES             | NO             | YES              | NO                | no error                                                                                                                          | NO           | YES               |
 +-----------------+----------------+------------------+-------------------+-----------------------------------------------------------------------------------------------------------------------------------+--------------+-------------------+
 | YES             | NO             | NO               | NO                | no error                                                                                                                          | NO           | YES               |
 +-----------------+----------------+------------------+-------------------+-----------------------------------------------------------------------------------------------------------------------------------+--------------+-------------------+
 | NO              | YES            | YES              | YES               | FILE_SIZE_LIMIT_EXCEEDED                                                                                                          | NO           | YES               |
 +-----------------+----------------+------------------+-------------------+-----------------------------------------------------------------------------------------------------------------------------------+--------------+-------------------+
 | NO              | YES            | YES              | YES               | SYSTEM_INTERNAL_UNRECOVERABLE_ERROR, FILE_ALREADY_EXISTS, SOURCE_LOCATION_FILE_NOT_AVAILABLE                                      | NO           | NO                |
 +-----------------+----------------+------------------+-------------------+-----------------------------------------------------------------------------------------------------------------------------------+--------------+-------------------+
 | NO              | NO             | YES              | YES               | COULD_NOT_CONNECT_TO_NODE,COULD_NOT_LIST_FILES                                                                                    | YES          | NO                |
 +-----------------+----------------+------------------+-------------------+-----------------------------------------------------------------------------------------------------------------------------------+--------------+-------------------+
 | NO              | NO             | YES              | YES               | all except COULD_NOT_CONNECT_TO_NODE,COULD_NOT_LIST_FILES                                                                         | NO           | NO                |
 +-----------------+----------------+------------------+-------------------+-----------------------------------------------------------------------------------------------------------------------------------+--------------+-------------------+
 | NO              | NO             | NO               | YES               | all                                                                                                                               | NO           | NO                |
 +-----------------+----------------+------------------+-------------------+-----------------------------------------------------------------------------------------------------------------------------------+--------------+-------------------+
 */
class FileCollectionResultNotificationListenerSpec extends SkeletonSpec implements FileCollectionResultCreator {
    private static final String NODE_ADDRESS = "NetworkElement=101010"
    private static final String TASK_ID = "XXX"

    private static final String NO_MEDIATION_AUTONOMY_ENABLE_NODE_FDN = "NetworkElement=LTE01ERBS0001"
    private static final String CISCO_NODE = "CISCO-ASR9000-01"
    private static final String ERBS_NODE = "LTE01ERBS0001"
    private static final String SIU02_NODE = "CORE16SIU02001"
    private static final String SIU02_NODE_FDN = "NetworkElement=CORE16SIU02001"

    private static final boolean RECOVER_IN_NEXT_ROP = true
    private static final boolean DONT_RECOVER_IN_NEXT_ROP = false

    private final long ropStartTime = System.currentTimeMillis() - TimeConstants.ONE_MINUTE_IN_MILLISECONDS * 15

    private static final boolean WITH_SUCCESSFUL_FILES = true
    private static final boolean NO_SUCCESSFUL_FILES = false

    private static final boolean WITH_FAILED_FILES = true
    private static final boolean NO_FAILED_FILES = false

    @ObjectUnderTest
    FileCollectionResultNotificationListener resultNotificationListener

    @Inject
    FileCollectionTaskCacheWrapper collectionTaskCache

    @Inject
    @NamedCache("PMICFileCollectionResultCache")
    private Cache<String, FileCollectionResultStatistics> fileCollectionResultStatisticsCache;

    @Inject
    FileCollectionCycleInstrumentation fileCollectionCycleInstrumentation

    @Inject
    FileCollectionStatistics fileCollectionStatistics

    @Inject
    private NodeService nodeService;

    @Unroll
    def "failure to collect files for statistical subscription with error #error adds new recovery task to the collection task cache "(FtpErrors error) {
        given: "File Collection Result is created"
        FileCollectionResult result = createFileCollectionResult(ropStartTime, RECOVER_IN_NEXT_ROP, NO_SUCCESSFUL_FILES, WITH_FAILED_FILES,
                SubscriptionType.STATISTICAL, error)

        when: "File Collection Result Received by Listener"
        resultNotificationListener.receiveFileCollectionResultEvent(result)

        and: "Recovery Task is retrieved from cache"
        Set<FileCollectionTaskWrapper> cachedTasks = collectionTaskCache.getAllTasks()
        FileCollectionTaskWrapper recoveryTask = cachedTasks.iterator().next()
        FileCollectionTaskRequest request = recoveryTask.getFileCollectionTaskRequest()
        String statistic = fileCollectionResultStatisticsCache.iterator().next().key;

        then: "Recovery task information should be valid"
        ropStartTime == request.getRopStartTime()
        NODE_ADDRESS == request.getNodeAddress()
        SubscriptionType.STATISTICAL.name() == request.getSubscriptionType()
        request.getJobId().contains(TASK_ID)
        !request.isRecoverInNextRop()

        and: "Recovery task should be added to FileCollectionTaskCache and file collection failure statistics is added to Statistics Cache"
        collectionTaskCache.size() == 1
        fileCollectionResultStatisticsCache.size() == 1
        statistic.contains("failure")

        where: "For All values of subscription type"
        error << FtpErrors.values().minus(notRecoverableErrors)
    }

    @Unroll
    def "failure to collect files for Statistical Subscription with error #error will not send recovery job if recovery is false but will update result cache "(FtpErrors error) {
        given: "File Collection Result is created with failures and recovery is false"
        FileCollectionResult result = createFileCollectionResult(ropStartTime, DONT_RECOVER_IN_NEXT_ROP, NO_SUCCESSFUL_FILES, WITH_FAILED_FILES,
                SubscriptionType.STATISTICAL, error)

        when: "File Collection Result Received by Listener"
        resultNotificationListener.receiveFileCollectionResultEvent(result)
        String statistic = fileCollectionResultStatisticsCache.iterator().next().key;

        then: "Recovery task should be added to PMICFileCollectionResultCache but no recovery should be added to PMICFileCollectionTaskCache"
        collectionTaskCache.size() == 0
        fileCollectionResultStatisticsCache.size() == 1
        statistic.contains("failure")

        where: "For All values of subscription type and recoverable error codes"
        error << FtpErrors.values().minus(notRecoverableErrors)
    }

    @Unroll
    def "failure to collect files for Statistical Subscription with error #error will not send recovery job if recovery is true and Singe Rop Recovery support is not there but will update result cache "(FtpErrors error) {
        given: "File Collection Result is created with failures and recovery is false"
        addNodes()
        FileCollectionResult result = createFileCollectionResult(ropStartTime, RECOVER_IN_NEXT_ROP, NO_SUCCESSFUL_FILES, WITH_FAILED_FILES,
                SubscriptionType.STATISTICAL, error)
        and: "set nodeFdn,neType to result for which Singe Rop Recovery support is not there "
        result.setNodeAddress(SIU02_NODE_FDN)
        result.setNeType("SIU02")
        when: "File Collection Result Received by Listener"
        resultNotificationListener.receiveFileCollectionResultEvent(result)
        String statistic = fileCollectionResultStatisticsCache.iterator().next().key;

        then: "Recovery task should be added to PMICFileCollectionResultCache but no recovery should be added to PMICFileCollectionTaskCache"
        collectionTaskCache.size() == 0
        fileCollectionResultStatisticsCache.size() == 1
        statistic.contains("failure")

        where: "For All values of subscription type and recoverable error codes"
        error << FtpErrors.values().minus(notRecoverableErrors)
    }

    @Unroll
    def "file collection for #subscriptionType with no files does not create a recovery task"(SubscriptionType subscriptionType) {

        given: "File Collection Result is created"
        FileCollectionResult result = createFileCollectionResult(ropStartTime, RECOVER_IN_NEXT_ROP, NO_SUCCESSFUL_FILES, NO_FAILED_FILES,
                subscriptionType, null)

        when: "File Collection Result Received by Listener"
        resultNotificationListener.receiveFileCollectionResultEvent(result)

        then:
        "File Collection Task cache should be empty and no file collection results statistics should be in File Collection Results Statistics" +
                " Cache"
        collectionTaskCache.size() == 0
        fileCollectionResultStatisticsCache.size() == 0

        where: "For All values of subscription type"
        subscriptionType << SubscriptionType.values();

    }

    @Unroll
    def " file collection successful for #subscriptionType, recovery task should not be created"(SubscriptionType subscriptionType) {

        given: "File Collection Result is created"
        FileCollectionResult result = createFileCollectionResult(ropStartTime, RECOVER_IN_NEXT_ROP, WITH_SUCCESSFUL_FILES, NO_FAILED_FILES,
                subscriptionType, null)

        when: "File Collection Result Received by Listener"
        resultNotificationListener.receiveFileCollectionResultEvent(result)
        String statistic = fileCollectionResultStatisticsCache.iterator().next().key;

        then:
        "File Collection task cache should be empty  and file collection success statistics should be added to File Collection Results " +
                "Statistics Cache"
        collectionTaskCache.size() == 0
        fileCollectionResultStatisticsCache.size() == 1
        statistic.contains("success")

        where: "For All values of subscription type"
        subscriptionType << SubscriptionType.values();

    }

    @Unroll
    def "file recovery successful for #subscriptionType, recovery task should not be created"(SubscriptionType subscriptionType) {

        given: "File Collection Result is created"
        FileCollectionResult result = createFileCollectionResult(ropStartTime, DONT_RECOVER_IN_NEXT_ROP, WITH_SUCCESSFUL_FILES,
                NO_FAILED_FILES, subscriptionType, null)

        when: "File Collection Result Received by Listener"
        resultNotificationListener.receiveFileCollectionResultEvent(result)
        String statistic = fileCollectionResultStatisticsCache.iterator().next().key;

        then:
        "File Collection task cache should be empty and file collection success statistics should be added to File Collection Results " +
                "Statistics Cache"
        collectionTaskCache.size() == 0
        fileCollectionResultStatisticsCache.size() == 1
        statistic.contains("success")

        where: "For All values of subscription type"
        subscriptionType << SubscriptionType.values();
    }

    @Unroll
    def "file recovery failed for SubscriptionType.CELLTRACE, recovery task should not be created if error is FILE_SIZE_LIMIT_EXCEEDED"() {

        given: "File Collection Result is created"
        FileCollectionResult result = createFileCollectionResult(ropStartTime, RECOVER_IN_NEXT_ROP, NO_SUCCESSFUL_FILES,
                WITH_FAILED_FILES, SubscriptionType.CELLTRACE, FILE_SIZE_LIMIT_EXCEEDED)

        when: "File Collection Result Received by Listener"
        resultNotificationListener.receiveFileCollectionResultEvent(result)
        String statistic = fileCollectionResultStatisticsCache.iterator().next().key;

        then:
        "File Collection Task cache should be empty and file collection result cache is updated for the UI with failure"
        collectionTaskCache.size() == 0
        fileCollectionResultStatisticsCache.size() == 1
        statistic.contains("failure")
    }

    @Unroll
    def "file recovery failed for SubscriptionType.STATISTICAL, processing should not recover or put in result cache if error is #error"(FtpErrors error) {

        given: "File Collection Result is created"
        FileCollectionResult result = createFileCollectionResult(ropStartTime, RECOVER_IN_NEXT_ROP, NO_SUCCESSFUL_FILES,
                WITH_FAILED_FILES, SubscriptionType.STATISTICAL, error)

        when: "File Collection Result Received by Listener"
        resultNotificationListener.receiveFileCollectionResultEvent(result)

        then:
        "File Collection Task cache should be empty and no file collection failure statistics should be in File Collection Results Statistics Cache"
        collectionTaskCache.size() == 0
        fileCollectionResultStatisticsCache.size() == 0

        where: "For All values of subscription type"
        error << [SYSTEM_INTERNAL_UNRECOVERABLE_ERROR, FILE_ALREADY_EXISTS, SOURCE_LOCATION_FILE_NOT_AVAILABLE]
    }

    @Unroll
    def "file recovery failed for SubscriptionType.STATISTICAL, should create recovery task but will not record it in result cache if error is #error and no successfule or failed files collected"(FtpErrors error) {

        given: "File Collection Result is created with recovery set to TRUE, error task status set to #error and no successful or failed files"
        FileCollectionResult result = createFileCollectionResult(ropStartTime, RECOVER_IN_NEXT_ROP, NO_SUCCESSFUL_FILES,
                NO_FAILED_FILES, SubscriptionType.STATISTICAL, error)

        when: "File Collection Result Received by Listener"
        resultNotificationListener.receiveFileCollectionResultEvent(result)

        then:
        "PMICFileCollectionTaskCache should be updated with recovery job, Result cache should be empty for ui graphs"
        collectionTaskCache.size() == 1
        fileCollectionResultStatisticsCache.size() == 0

        where: "For All values of subscription type and error codes:COULD_NOT_CONNECT_TO_NODE and COULD_NOT_LIST_FILES"
        error << [COULD_NOT_CONNECT_TO_NODE, COULD_NOT_LIST_FILES]
    }

    @Unroll
    def "file recovery failed for SubscriptionType.STATISTICAL, should create recovery task but will not record it in result cache if error is #error and no successful or failed files collected asd"(FtpErrors error) {

        given: "File Collection Result is created with recovery set to TRUE, error task status set to #error and no successful or failed files"
        FileCollectionResult result = createFileCollectionResult(ropStartTime, RECOVER_IN_NEXT_ROP, NO_SUCCESSFUL_FILES,
                NO_FAILED_FILES, SubscriptionType.STATISTICAL, error)

        when: "File Collection Result Received by Listener"
        resultNotificationListener.receiveFileCollectionResultEvent(result)

        then:
        "PMICFileCollectionTaskCache should be updated with recovery job, Result cache should be empty for ui graphs"
        collectionTaskCache.size() == 0
        fileCollectionResultStatisticsCache.size() == 0

        where: "For All values of subscription type and error codes:COULD_NOT_CONNECT_TO_NODE and COULD_NOT_LIST_FILES"
        error << FtpErrors.values().minus([COULD_NOT_CONNECT_TO_NODE, COULD_NOT_LIST_FILES])
    }

    @Unroll
    def "file recovery failed for SubscriptionType.STATISTICAL, should not create recovery task and will not record it in result cache if error is #error and no success or failed files collected if recovery is false"(FtpErrors error) {

        given: "File Collection Result is created with recovery set to FALSE, error task status set to #error and no successful or failed files"
        FileCollectionResult result = createFileCollectionResult(ropStartTime, DONT_RECOVER_IN_NEXT_ROP, NO_SUCCESSFUL_FILES,
                NO_FAILED_FILES, SubscriptionType.STATISTICAL, error)

        when: "File Collection Result Received by Listener"
        resultNotificationListener.receiveFileCollectionResultEvent(result)

        then:
        "PMICFileCollectionTaskCache should be empty, Result cache should be empty for ui graphs"
        collectionTaskCache.size() == 0
        fileCollectionResultStatisticsCache.size() == 0

        where: "For All values of subscription type and error codes:COULD_NOT_CONNECT_TO_NODE and COULD_NOT_LIST_FILES"
        error << FtpErrors.values()
    }

    @Unroll
    def "Recovery Task #resultOfTest added to queue if Mediation Autonomy is enabled on node"() {
        given: "Node created in dps"
        addNodes()
        and: "File Collection Result is created"
        FileCollectionResult result = createFileCollectionResult(ropStartTime, RECOVER_IN_NEXT_ROP, NO_SUCCESSFUL_FILES, WITH_FAILED_FILES,
                SubscriptionType.STATISTICAL, NO_CONNECTION_KEY_FOUND)
        and: "A nodeFdn is set to result"
        result.setNodeAddress(nodeFdn)
        when: "File Collection Result Received by Listener"
        resultNotificationListener.receiveFileCollectionResultEvent(result)
        then: "Mediation Autonomy #resultOfTest enable"
        isMediationAutonomy == nodeService.isMediationAutonomyEnabled(result.neType, false)
        and: "task #resultOfTest created and added to the cache"
        collectionTaskCache.size() == fileCollectionSize
        where:
        fileCollectionSize | isMediationAutonomy | nodeFdn                               | resultOfTest
        1                  | false               | NO_MEDIATION_AUTONOMY_ENABLE_NODE_FDN | "is"
    }

    @Unroll
    def "If file recovery failed with error FILE_ALREADY_EXISTS, processing should not recover file or update in failed count statistics for #subscriptionType"(SubscriptionType subscriptionType) {

        given: "File Collection Result is created"
        FileCollectionResult result = createFileCollectionResult(ropStartTime, RECOVER_IN_NEXT_ROP, NO_SUCCESSFUL_FILES,
                WITH_FAILED_FILES, subscriptionType, FILE_ALREADY_EXISTS)

        when: "File Collection Result Received by Listener"
        resultNotificationListener.receiveFileCollectionResultEvent(result)

        then:
        "File Collection Task cache should be empty and no file collection failure statistics should be in File Collection Results Statistics Cache"
        collectionTaskCache.size() == 0
        fileCollectionResultStatisticsCache.size() == 0
        fileCollectionCycleInstrumentation.getNumberOfFilesFailedCurrentROP() == 0
        fileCollectionStatistics.getCombinedNumberOfFilesFailed() == 0

        where: "For All values of subscription type"
        subscriptionType << SubscriptionType.values();
    }

    def static notRecoverableErrors = [SOURCE_LOCATION_FILE_NOT_AVAILABLE, SYSTEM_INTERNAL_UNRECOVERABLE_ERROR,
                                       FILE_ALREADY_EXISTS, FILE_SIZE_LIMIT_EXCEEDED, FILE_ALREADY_COLLECTED]

    def addNodes() {
        nodeUtil.builder(CISCO_NODE).pmEnabled(true).neType(NetworkElementType.CISCOASR9000.getNeTypeString()).build()
        nodeUtil.builder(ERBS_NODE).pmEnabled(true).neType(NetworkElementType.ERBS).build()
        nodeUtil.builder(SIU02_NODE).pmEnabled(true).neType("SIU02").build()
    }
}
