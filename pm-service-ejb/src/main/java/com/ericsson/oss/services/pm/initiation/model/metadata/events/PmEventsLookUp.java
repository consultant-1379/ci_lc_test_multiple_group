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

package com.ericsson.oss.services.pm.initiation.model.metadata.events;

import static com.ericsson.oss.itpf.modeling.schema.util.SchemaConstants.EXT_INTEGRATION_POINT_LIBRARY;
import static com.ericsson.oss.itpf.modeling.schema.util.SchemaConstants.GLOBAL_MODEL_NAMESPACE;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ericsson.oss.external.modeling.schema.gen.ext_integrationpointlibrary.IntegrationPoint;
import com.ericsson.oss.itpf.modeling.common.info.ModelInfo;
import com.ericsson.oss.pmic.subscription.enums.EventTypeFilter;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_event.EventGroupType;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_event.PerformanceEventDefinition;
import com.ericsson.services.pm.initiation.restful.api.EventTableRow;

/**
 * The type Pm events look up.
 */
public class PmEventsLookUp extends AbstractPmEventsLookUp {

    /**
     * {@inheritDoc}
     */
    @Override
    protected Collection<EventTableRow> fetchEventsAndGroups(final ModelInfo modelInfo) {
        final PerformanceEventDefinition performanceEvents = directModelAccess.getAsJavaTree(modelInfo, PerformanceEventDefinition.class);
        return getEvents(performanceEvents, null, EventTypeFilter.ALL);
    }

    /**
     * Fetch events integration point.
     *
     * @param integrationPointName
     *         the integration point name
     *
     * @return the integration point
     */
    public IntegrationPoint fetchEventsIntegrationPoint(final String integrationPointName) {
        final ModelInfo integrationPointModelInfo = new ModelInfo(EXT_INTEGRATION_POINT_LIBRARY, GLOBAL_MODEL_NAMESPACE, integrationPointName);
        return directModelAccess.getAsJavaTree(integrationPointModelInfo, IntegrationPoint.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Collection<EventTableRow> prepareEventTableRowList(final List<EventGroupType> performanceEventsGroup, final Set<String> profileEvents,
                                                                 final Set<String> events) {
        final Collection<EventTableRow> result = new HashSet<>();
        for (final EventGroupType eventGroup : performanceEventsGroup) {
            final List<String> eventNames = eventGroup.getEvent();
            for (final String eventName : eventNames) {
                events.remove(eventName);
                result.add(new EventTableRow(eventName, eventGroup.getName(), eventGroup.getEventProducerId()));
            }
        }
        return result;
    }

}
