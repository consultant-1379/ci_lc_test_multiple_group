/*******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.common

import static com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant.LAST_FILE_COLLECTION_TASKS_CREATED_FOR_ROP
import static com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant.LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP

import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.services.pm.collection.cache.FileCollectionLastRopData
import com.ericsson.oss.services.pm.common.startup.ClusterMembershipChangeAction
import com.ericsson.oss.services.pm.initiation.config.listener.DeploymentReadyConfigurationListener
import com.ericsson.oss.services.pm.initiation.util.RopTime

class ClusterMembershipChangeActionSpec extends CdiSpecification {

    @ObjectUnderTest
    ClusterMembershipChangeAction changeAction

    @Inject
    FileCollectionLastRopData fileCollectionLastRopData

    @ImplementationInstance
    DeploymentReadyConfigurationListener deploymentReadyConfigurationListener = Mock()

    def "when mastership is achieved, the file collection record keeping is resynchronized from cache"() {
        given:
        def now = new Date().getTime()
        fileCollectionLastRopData.recordRopStartTimeForTaskSending(new RopTime(now, 900))
        fileCollectionLastRopData.recordRopStartTimeForTaskSending(new RopTime(now, 60))
        fileCollectionLastRopData.recordRopStartTimeForTaskCreation(new RopTime(now, 900))
        fileCollectionLastRopData.cache.remove(LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP + 900)
        fileCollectionLastRopData.cache.remove(LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP + 60)
        deploymentReadyConfigurationListener.hasDeploymentReadyEventBeenTriggered() >> true
        when:
        changeAction.executeAsMaster()
        then:
        fileCollectionLastRopData.counterRopRecords.get(LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP + 900) == 0L
        fileCollectionLastRopData.counterRopRecords.get(LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP + 60) == 0L
        fileCollectionLastRopData.counterRopRecords.get(LAST_FILE_COLLECTION_TASKS_CREATED_FOR_ROP + 900) == new RopTime(now, 900).getCurrentRopStartTimeInMilliSecs()
    }
}
