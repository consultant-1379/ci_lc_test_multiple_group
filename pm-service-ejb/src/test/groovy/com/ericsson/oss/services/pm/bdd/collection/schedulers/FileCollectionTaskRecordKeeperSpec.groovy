/*******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.services.pm.bdd.collection.schedulers

import static com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant.LAST_FILE_COLLECTION_TASKS_CREATED_FOR_ROP
import static com.ericsson.oss.services.pm.collection.constants.FileCollectionConstant.LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.services.pm.collection.cache.FileCollectionLastRopData
import com.ericsson.oss.services.pm.initiation.util.RopTime

class FileCollectionTaskRecordKeeperSpec extends CdiSpecification {

    @ObjectUnderTest
    FileCollectionLastRopData fileCollectionLastRopData

    static def now = new Date().getTime()

    def "recording last ROP start time for MTR creation will save to local space even if cache is not available"() {
        when:
        fileCollectionLastRopData.recordRopStartTimeForTaskCreation(new RopTime(now, 900))
        fileCollectionLastRopData.cache.remove(LAST_FILE_COLLECTION_TASKS_CREATED_FOR_ROP + 900)
        then:
        fileCollectionLastRopData.counterRopRecords.get(LAST_FILE_COLLECTION_TASKS_CREATED_FOR_ROP + 900) == new RopTime(now, 900).getCurrentRopStartTimeInMilliSecs()
    }

    def "recording last ROP start time for MTR sending will save to local space even if cache is not available"() {
        when:
        fileCollectionLastRopData.recordRopStartTimeForTaskSending(new RopTime(now, 900))
        fileCollectionLastRopData.cache.remove(LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP + 900)
        then:
        fileCollectionLastRopData.counterRopRecords.get(LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP + 900) == new RopTime(now, 900).getCurrentRopStartTimeInMilliSecs()
    }

    def "resyncLocalRecords will reset local records to 0 if cache throws errors"() {
        given:
        def now = new Date().getTime()
        fileCollectionLastRopData.recordRopStartTimeForTaskSending(new RopTime(now, 900))
        fileCollectionLastRopData.recordRopStartTimeForTaskSending(new RopTime(now, 60))
        fileCollectionLastRopData.recordRopStartTimeForTaskCreation(new RopTime(now, 900))
        fileCollectionLastRopData.cache.remove(LAST_FILE_COLLECTION_TASKS_CREATED_FOR_ROP + 900)
        fileCollectionLastRopData.cache.remove(LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP + 900)
        fileCollectionLastRopData.cache.remove(LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP + 60)
        when:
        fileCollectionLastRopData.resyncLocalRecords()
        then: "local entries will be reset to 0L if the cache throws exceptions"
        fileCollectionLastRopData.counterRopRecords.get(LAST_FILE_COLLECTION_TASKS_CREATED_FOR_ROP + 900) == 0L
        fileCollectionLastRopData.counterRopRecords.get(LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP + 900) == 0L
        fileCollectionLastRopData.counterRopRecords.get(LAST_FILE_COLLECTION_TASKS_SENT_FOR_ROP + 60) == 0L
    }
}
