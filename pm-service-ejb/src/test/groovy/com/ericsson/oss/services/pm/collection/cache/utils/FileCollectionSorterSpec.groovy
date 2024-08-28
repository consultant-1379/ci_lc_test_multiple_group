package com.ericsson.oss.services.pm.collection.cache.utils

import com.ericsson.oss.services.pm.collection.tasks.FileCollectionTaskRequest
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper
import spock.lang.Specification

class FileCollectionSorterSpec extends Specification {

    def 'should return tasks sorted in the correct order'() {
        given: 'a collection of file collection tasks'
            def tasks = []
            ['CONTINUOUSCELLTRACE', 'EVENTS', 'STATISTICAL'].each({s ->
                def task = new FileCollectionTaskRequest('nodeFdn', 'taskId', s, 1, 1, true)
                tasks.add(new FileCollectionTaskWrapper(task, null, null))}
            )

        when: 'the tasks are sorted'
            new FileCollectionSorter().sortFileCollectionTaskRequests(tasks)

        then: 'the tasks are in the correct order'
            tasks[0].fileCollectionTaskRequest.subscriptionType == 'STATISTICAL'
            tasks[1].fileCollectionTaskRequest.subscriptionType == 'CONTINUOUSCELLTRACE'
            tasks[2].fileCollectionTaskRequest.subscriptionType == 'EVENTS'
    }

    def 'should return tasks sorted in the correct order, with unknown subscriptions at the end in an undefined order'() {
        given: 'a collection of file collection tasks'
            def tasks = []
            ['UNKNOWN_TYPE', 'CONTINUOUSCELLTRACE', 'STATISTICAL'].each({s ->
                def task = new FileCollectionTaskRequest('nodeFdn', 'taskId', s, 1, 1, true)
                tasks.add(new FileCollectionTaskWrapper(task, null, null))}
            )

        when: 'the tasks are sorted'
            new FileCollectionSorter().sortFileCollectionTaskRequests(tasks)

        then: 'the tasks are in the correct order'
            tasks[0].fileCollectionTaskRequest.subscriptionType == 'STATISTICAL'
            tasks[1].fileCollectionTaskRequest.subscriptionType == 'CONTINUOUSCELLTRACE'
            tasks[2].fileCollectionTaskRequest.subscriptionType == 'UNKNOWN_TYPE'
    }
}
