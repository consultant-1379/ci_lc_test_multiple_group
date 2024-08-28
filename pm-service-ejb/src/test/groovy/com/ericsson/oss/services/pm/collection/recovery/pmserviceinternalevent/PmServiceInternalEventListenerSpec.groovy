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

package com.ericsson.oss.services.pm.collection.recovery.pmserviceinternalevent

import spock.lang.Unroll

import javax.cache.Cache
import javax.enterprise.inject.Instance
import javax.inject.Inject

import com.ericsson.cds.cdi.support.rule.ImplementationInstance
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.itpf.sdk.cache.annotation.NamedCache
import com.ericsson.oss.services.pm.collection.api.FileCollectionTaskSenderLocal
import com.ericsson.oss.services.pm.collection.task.factories.FileCollectionTaskRequestFactoryService
import com.ericsson.oss.services.pm.collection.task.factories.StatisticalFileCollectionTaskRequestFactory
import com.ericsson.oss.services.pm.common.events.PmServiceInternalEvent
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper

class PmServiceInternalEventListenerSpec extends CdiSpecification {

    @ObjectUnderTest
    PmServiceInternalEventListener internalEventListener

    @Inject
    @NamedCache("PMICFileCollectionActiveTaskListCache")
    private Cache<String, Map<String, Object>> cache

    @Inject
    private FileCollectionTaskSenderLocal fileCollectionTaskSender;

    @ImplementationInstance
    Instance<FileCollectionTaskRequestFactoryService> collectionTaskRequestFactoryServiceInstance = [
            select       : { a -> this.collectionTaskRequestFactoryServiceInstance },
            isUnsatisfied: { -> false },
            get          : {
                StatisticalFileCollectionTaskRequestFactory statisticalFileCollectionTaskRequestFactory = new StatisticalFileCollectionTaskRequestFactory();
                return statisticalFileCollectionTaskRequestFactory
            }
    ] as Instance<FileCollectionTaskRequestFactoryService>

    def buildCache(String node, int rop) {
        cache.put(node + rop, ["nodeAddress": node, "ropPeriod": rop, "processType": "STATS", "startTime": new Date().time, "endTime": new Date().time])
    }

    @Unroll
    def "Expected File Collection tasks should be sent when mediation service lost #rops"() {
        given: "Active file collection task exists in the cache"
        nodesInActiveCache.each { node -> rops.each { rop -> buildCache(node, rop) } }

        when: "lost mediation service event received"
        def event = new PmServiceInternalEvent()
        event.lostMediationServiceDateTime = new Date().parse('yyyy/MM/dd hh:mm:ss', lostMediationServiceDateTime).time
        event.setNodeFdns(nodesHandledByMspm as Set)

        internalEventListener.receivePmServiceInternalEvent(event)

        then: "file recovery tasks should be sent"
        expectedInvocations * fileCollectionTaskSender.sendFileCollectionTasks(*_) >> { arguments ->
            final List<FileCollectionTaskWrapper> fileCollectionTaskWrapperList = arguments[0]
            assert fileCollectionTaskWrapperList.size() == expectedNodes.size()
        }

        where:
        nodesInActiveCache | nodesHandledByMspm | rops      | lostMediationServiceDateTime | expectedInvocations | expectedNodes            | expectedRopStartTimes
        ["Node1", "Node2"] | ["Node1", "Node2"] | [900]     | '2018/08/08 10:19:59'        | 1                   | ["Node1": 1, "Node2": 1] | ["2018/08/08 09:45:00": 2]
        ["Node1", "Node2"] | ["Node1", "Node2"] | [900]     | '2018/08/08 10:20:01'        | 1                   | ["Node1": 1, "Node2": 1] | ["2018/08/08 10:00:00": 2]
        ["Node1", "Node2"] | ["Node1", "Node2"] | [60]      | '2018/08/08 10:20:01'        | 1                   | ["Node1": 1, "Node2": 1] | ["2018/08/08 10:18:00": 2]
        ["Node1", "Node2"] | ["Node1", "Node2"] | [60]      | '2018/08/08 10:20:59'        | 1                   | ["Node1": 1, "Node2": 1] | ["2018/08/08 10:18:00": 2]
        ["Node1", "Node2"] | ["Node1", "Node2"] | [60]      | '2018/08/08 10:21:01'        | 1                   | ["Node1": 1, "Node2": 1] | ["2018/08/08 10:19:00": 2]
        ["Node1", "Node2"] | ["Node1", "Node2"] | [900, 60] | '2018/08/08 10:20:01'        | 2                   | ["Node1": 2, "Node2": 2] | ["2018/08/08 10:00:00": 2, "2018/08/08 10:18:00": 2]
        ["Node1", "Node2"] | ["Node1"]          | [900, 60] | '2018/08/08 10:20:01'        | 2                   | ["Node1": 2]             | ["2018/08/08 10:00:00": 1, "2018/08/08 10:18:00": 1]
        ["Node1"]          | ["Node1", "Node2"] | [900, 60] | '2018/08/08 10:20:01'        | 2                   | ["Node1": 2]             | ["2018/08/08 10:00:00": 1, "2018/08/08 10:18:00": 1]
        ["Node1"]          | []                 | [900, 60] | '2018/08/08 10:20:01'        | 0                   | [:]                      | [:]
        []                 | ["Node1"]          | [900, 60] | '2018/08/08 10:20:01'        | 0                   | [:]                      | [:]
        []                 | []                 | [900, 60] | '2018/08/08 10:20:01'        | 0                   | [:]                      | [:]
        ["Node1", "Node2"] | ["Node1"]          | []        | '2018/08/08 10:20:01'        | 0                   | [:]                      | [:]
    }

}
