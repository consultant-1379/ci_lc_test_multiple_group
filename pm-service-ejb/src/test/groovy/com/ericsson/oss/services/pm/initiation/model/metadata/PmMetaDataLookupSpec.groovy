/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.model.metadata

import spock.lang.Shared
import spock.lang.Unroll

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.services.pm.initiation.model.metadata.events.PmEventsLookUp
import com.ericsson.oss.services.pm.initiation.model.metadata.moinstances.PmMoinstancesLookUp
import com.ericsson.services.pm.initiation.restful.api.EventTableRow
import com.ericsson.services.pm.initiation.restful.api.PmMimVersionQuery

class PmMetaDataLookupSpec extends CdiSpecification {

    @ObjectUnderTest
    PmMetaDataLookup pmMetaDataLookup

    @MockedImplementation
    PmEventsLookUp pmEventsLookUp

    @MockedImplementation
    PmMimVersionQuery pmMimVersionQuery

    @MockedImplementation
    PmMoinstancesLookUp pmMoinstancesLookUp

    @Shared
    def lratEvent = new EventTableRow('lrat event', 'group', 'Lrat')

    @Shared
    def vtfRatEvent = new EventTableRow('vtfRatEvent', 'group', 'VTFrat')

    @Shared
    def eventWithUndefinedProducerId = new EventTableRow('event with no event producer ID', 'group')

    @Shared
    def eventWithOtherProducerId = new EventTableRow('event with other producer ID', 'group', 'other')

    @Shared
    def cucpEvent = new EventTableRow('CUCP event', 'group', 'CUCP')

    @Shared
    def cuupEvent = new EventTableRow('CUUP event', 'group', 'CUUP')

    @Shared
    def duEvent = new EventTableRow('DU event', 'group', 'DU')

    @Unroll
    def 'Filtering events with filter: #eventFilter'() {

        given: 'events from multiple event producers'
        pmEventsLookUp.getEventsForAllVersions(_) >> [lratEvent, vtfRatEvent, eventWithUndefinedProducerId, eventWithOtherProducerId, cucpEvent, cuupEvent, duEvent]
        when: 'getEvents is called with filter: #eventFilter'
        final Collection<EventTableRow> filteredEventTableRows = pmMetaDataLookup.getEvents(pmMimVersionQuery, eventFilter)
        then: 'the correct events are returned'
        filteredEventTableRows.size() == expectedEventTableRows.size()
        assert (filteredEventTableRows.containsAll(expectedEventTableRows))
        where: 'the following event filters are applied'
        eventFilter     || expectedEventTableRows
        null            || [lratEvent, vtfRatEvent, eventWithUndefinedProducerId, eventWithOtherProducerId, cucpEvent, cuupEvent, duEvent]
        'cellTraceLRan' || [lratEvent, vtfRatEvent, eventWithUndefinedProducerId, eventWithOtherProducerId]
        'cellTraceNRan' || [cuupEvent, cuupEvent, duEvent]
    }

    def 'pmMoInstanceLookup is called when getSupportedMoInstances is reequested'(){
        given: 'A defined moClass, nodeType and subscriptionType'
            def moClass = 'Aal0TpVccTp'
            def nodes = 'RNC01'
            def subscriptionType = 'MOINSTANCE'
        when:
            pmMetaDataLookup.getSupportedMoInstances(pmMimVersionQuery.getMimVersions(),nodes,moClass,subscriptionType)
        then:
            1 * pmMoinstancesLookUp.getMoinstances(pmMimVersionQuery.getMimVersions(),nodes,moClass,subscriptionType)
    }
}
