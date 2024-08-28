/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.enodeb.statistics.resource

import javax.inject.Inject
import javax.ws.rs.core.Response

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.services.pm.initiation.api.FileCollectionHeartBeatServiceLocal
import com.ericsson.oss.services.pm.initiation.cache.api.FileCollectionChartData

class PMChartUIResourceSpec extends CdiSpecification {

    @ObjectUnderTest
    PMChartUIResource statisticsUIResource

    @Inject
    FileCollectionHeartBeatServiceLocal fileCollectionHeartBeatService

    def "getBytesStored should return 200 status code and data on request"() {
        given:
        final long now = System.currentTimeMillis()
        final FileCollectionChartData chartData1 = new FileCollectionChartData(now - 1 * 60 * 1000)
        final FileCollectionChartData chartData2 = new FileCollectionChartData(now - 1 * 60 * 1000)
        final FileCollectionChartData chartData3 = new FileCollectionChartData(now - 1 * 60 * 1000)

        final List<FileCollectionChartData> result = new ArrayList<FileCollectionChartData>()

        result.add(chartData1)
        result.add(chartData2)
        result.add(chartData3)

        fileCollectionHeartBeatService.getBytesStoredChartData(15, 5) >> result
        when:
        final Response response = statisticsUIResource.getBytesStored(15, 5)
        then:
        200 == response.getStatus()
        result == response.getEntity()
    }

    def "getFilesMissed should return 200 status code and data on request"() {
        given:
        final long now = System.currentTimeMillis()
        final FileCollectionChartData chartData1 = new FileCollectionChartData(now - 1 * 60 * 1000)
        final FileCollectionChartData chartData2 = new FileCollectionChartData(now - 1 * 60 * 1000)
        final FileCollectionChartData chartData3 = new FileCollectionChartData(now - 1 * 60 * 1000)

        final List<FileCollectionChartData> result = new ArrayList<FileCollectionChartData>()

        result.add(chartData1)
        result.add(chartData2)
        result.add(chartData3)

        fileCollectionHeartBeatService.getFilesMissedChartData(15, 5) >> result
        when:
        final Response response = statisticsUIResource.getFilesMissed(15, 5)
        then:
        200 == response.getStatus()
        result == response.getEntity()
    }
}

