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

package com.ericsson.oss.services.pm.initiation.model.metadata.events;

import static com.ericsson.oss.services.pm.initiation.common.Constants.MANUAL_PROTOCOL_GROUPING;
import static com.ericsson.oss.services.pm.initiation.common.Constants.MODEL_VERSION_1_0_0;
import static com.ericsson.oss.services.pm.initiation.common.Constants.PMIC_CCTR_ERBS_NODE_NAME;
import static com.ericsson.oss.services.pm.initiation.common.Constants.PMIC_CCTR_EVENT_GROUP_NAME;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.modeling.common.info.ModelInfo;
import com.ericsson.oss.itpf.modeling.common.info.ModelVersionInfo;
import com.ericsson.oss.itpf.modeling.modelservice.direct.DirectModelAccess;
import com.ericsson.oss.itpf.modeling.modelservice.exception.ModelProcessingException;
import com.ericsson.oss.itpf.modeling.modelservice.exception.UnknownModelException;
import com.ericsson.oss.pmic.api.modelservice.PmCapabilityReader;
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo;
import com.ericsson.oss.pmic.subscription.enums.EventTypeFilter;
import com.ericsson.oss.services.pm.initiation.model.metadata.PmMetaDataHelper;
import com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_event.EventGroupType;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_event.EventType;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_event.PerformanceEventDefinition;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.services.pm.initiation.restful.api.EventTableRow;

/**
 * The type for Abstract Pm events look up.
 */
public abstract class AbstractPmEventsLookUp {

    protected static final String INTERNAL = "INTERNAL";

    protected static final String OTHER = "OTHER";
    private static final String COULD_NOT_FIND_ANY_EVENTS = "Could not find any events!";

    protected String nodeType;

    @Inject
    protected Logger log;

    @Inject
    protected DirectModelAccess directModelAccess;

    @Inject
    protected PmMetaDataHelper metaDataHelper;

    @Inject
    protected EventsPerMimVersionLocalCache eventsMimCache;

    @Inject
    protected PmCapabilityReader pmCapabilityReader;

    /**
     * This method would return List of eventInfo for given neType and version.
     *
     * @param eventInfo
     *         - List of EventInfo type
     * @param neTypeVersionSet
     *         - Set of NodeTypeAndVersion
     *
     * @return - returns a list of correct eventInfo
     */
    public List<EventInfo> getCorrectEventListForTheSpecifiedMims(final List<EventInfo> eventInfo, final Set<NodeTypeAndVersion> neTypeVersionSet) {
        log.debug("Checking if events {} are correct for the mim versions specified {}", eventInfo, neTypeVersionSet);

        final List<EventInfo> correctEventsList = new ArrayList<>();
        try {
            final Collection<EventTableRow> allEvents = getEventsForAllVersions(neTypeVersionSet);
            for (final EventInfo moClass : eventInfo) {
                final String correctGroupName = moClass.getGroupName();
                final String eventName = moClass.getName();
                if (allEvents.contains(new EventTableRow(eventName, correctGroupName))) {
                    correctEventsList.add(moClass);
                }
            }
        } catch (final PfmDataException e) {
            log.warn("No events were found for the supplied mim versions {}", neTypeVersionSet);
            log.debug(COULD_NOT_FIND_ANY_EVENTS, e);
        }

        log.debug("Corrected event list {}", correctEventsList);
        return correctEventsList;
    }

    /**
     * Gets events for all versions.
     *
     * @param nodeTypeAndVersionSet
     *         the node type and version set
     *
     * @return the events for all versions
     * @throws PfmDataException
     *         the pmic invalid input exception
     */
    public Collection<EventTableRow> getEventsForAllVersions(final Set<NodeTypeAndVersion> nodeTypeAndVersionSet) throws PfmDataException {
        final Collection<EventTableRow> events = new TreeSet<>();
        final List<NodeTypeAndVersion> invalidNodeTypeAndVersionList = new ArrayList<>();
        for (final NodeTypeAndVersion nodeTypeAndVersion : nodeTypeAndVersionSet) {
            try {
                final Collection<EventTableRow> eventsPerMim = getEventsForNodeTypeAndVersion(nodeTypeAndVersion);
                events.addAll(eventsPerMim);
            } catch (final UnknownModelException | ModelProcessingException e) {
                invalidNodeTypeAndVersionList.add(nodeTypeAndVersion);
                log.warn("No events found for ossModelIdentity {}", nodeTypeAndVersion);
                log.debug(COULD_NOT_FIND_ANY_EVENTS, e);
            }
        }
        log.debug("Found {} events for nodeTypes and ossModelIdentities: {}", events.size(), nodeTypeAndVersionSet);
        if (events.isEmpty()) {
            final String message = String.format("No events found for nodeTypes and ossModelIdentities: %s",
                    invalidNodeTypeAndVersionList.toString());
            throw new PfmDataException(message);
        }
        return events;
    }

    /**
     * This method gets events for all Node Type and Version
     *
     * @param nodeTypeAndVersion
     *         the neType and version info
     *
     * @return list of events
     */
    protected Collection<EventTableRow> getEventsForNodeTypeAndVersion(final NodeTypeAndVersion nodeTypeAndVersion) {
        nodeType = nodeTypeAndVersion.getNodeType();
        final String ossModelIdentity = nodeTypeAndVersion.getOssModelIdentity();
        log.debug("Starting searching events for node type {} and ossModelIdentity {}", nodeType, ossModelIdentity);

        final Collection<String> pfmEventModelUrns = metaDataHelper.getMetaDataUrnsFromModelService(nodeType, ossModelIdentity,
                PmMetaDataConstants.PFM_EVENT_PATTERN);
        if (nodeType.equals(PMIC_CCTR_ERBS_NODE_NAME)) {
            pfmEventModelUrns.add(PmMetaDataConstants.PFM_EVENT_PATTERN + nodeType + MANUAL_PROTOCOL_GROUPING + MODEL_VERSION_1_0_0);
        }
        log.debug("Got events from model service for ne type {} ossModelIdentity {} ", nodeType, ossModelIdentity);
        return getPMEvents(pfmEventModelUrns);
    }

    /**
     * This method will return the List of events
     *
     * @param modelUrns
     *         list of modelUrns
     *
     * @return list of events
     */
    protected Collection<EventTableRow> getPMEvents(final Collection<String> modelUrns) {
        final Collection<String> manualProtocolGroupModelUrns = filterManualProtocolGroupingURN(modelUrns);
        final Collection<EventTableRow> eventOssGroup = fetchEventsAndGroupsFromModelURN(modelUrns);
        if (eventOssGroup.isEmpty()) {
            log.debug("Event Oss Group is empty");
            return eventOssGroup;
        }

        final Collection<EventTableRow> eventProtocolGroup = fetchEventsAndGroupsFromModelURN(manualProtocolGroupModelUrns);

        if (eventProtocolGroup.isEmpty()) {
            return eventOssGroup;
        }

        return sortEventswithGroupAndName(eventOssGroup, eventProtocolGroup);

    }

    /**
     * This method will add the events to the EventsPerMimVersionLocalCache
     *
     * @param typeVersion
     *         the neType and version info
     *
     * @return list of events
     */
    protected Collection<EventTableRow> getAllPMEvents(final NodeTypeAndVersion typeVersion) {
        log.debug("Getting events for node type and version {} from the local cache", typeVersion);
        Collection<EventTableRow> moClassCollectionPerMim = eventsMimCache.get(typeVersion);
        if (moClassCollectionPerMim == null) {
            log.debug("No events for version {} in cache, getting the events from model service", typeVersion);
            moClassCollectionPerMim = getEventsForNodeTypeAndVersion(
                    new NodeTypeAndVersion(typeVersion.getNodeType(), typeVersion.getOssModelIdentity(), typeVersion.getTechnologyDomain()));
            eventsMimCache.put(typeVersion, moClassCollectionPerMim);
        }
        return moClassCollectionPerMim;
    }

    /**
     * This method would return List of eventInfo for given neType and version.
     *
     * @param eventInfo
     *         - List of EventInfo type
     * @param type
     *         - node Type
     * @param version
     *         - model Identity
     *
     * @return - returns a list of events name
     */
    public List<String> getApplicableEvents(final List<EventInfo> eventInfo, final String type, final String version) {
        final Collection<EventTableRow> allEvents = getAllPMEvents(new NodeTypeAndVersion(type, version));
        final List<String> applicableEventsStringList = new ArrayList<>();
        for (final EventInfo moClass : eventInfo) {
            if (allEvents.contains(new EventTableRow(moClass.getName(), moClass.getGroupName()))
                    && !applicableEventsStringList.contains(moClass.getName())) {
                applicableEventsStringList.add(moClass.getName());
            }
        }
        log.trace("Applicable events for node Type {} and version {} are {}", type, version, applicableEventsStringList);
        return applicableEventsStringList;
    }

    /**
     * Gets list of supported events.
     *
     * @param mapOfNeTypeModelIdentitySet
     *         the map of ne type model identity set
     *
     * @return the list of supported events
     */
    public List<EventInfo> getListOfSupportedEvents(final Map<String, Set<String>> mapOfNeTypeModelIdentitySet) {
        log.debug("Getting events for Model Identity {}", mapOfNeTypeModelIdentitySet);
        final List<EventInfo> listOfAllSupportedEvents = new ArrayList<>();
        final Set<EventInfo> setOfAllSupportedEvents = new HashSet<>();
        for (final Map.Entry<String, Set<String>> entry : mapOfNeTypeModelIdentitySet.entrySet()) {
            for (final String modelIdentity : entry.getValue()) {
                final Collection<String> pfmEventModelUrns = metaDataHelper.getMetaDataUrnsFromModelService(entry.getKey(), modelIdentity,
                        PmMetaDataConstants.PFM_EVENT_PATTERN);
                setOfAllSupportedEvents.addAll(getPMEventInfo(pfmEventModelUrns, PMIC_CCTR_EVENT_GROUP_NAME));
            }
        }
        log.debug("Found {} events for Model Identity: {}", setOfAllSupportedEvents.size(), mapOfNeTypeModelIdentitySet);

        listOfAllSupportedEvents.addAll(setOfAllSupportedEvents);
        return listOfAllSupportedEvents;
    }

    /**
     * Gets list of supported events for given technology domains
     *
     * @param modelIdentitiesPerNodeType
     *         the map of ne type model identity set
     * @param technologyDomainsPerNodeType
     *         supported technology domains per node type
     *
     * @return the list of supported events for technology domain
     */
    public List<EventInfo> getListOfSupportedEventsForTechnologyDomain(final Map<String, Set<String>> modelIdentitiesPerNodeType,
                                                                       final Map<String, Set<String>> technologyDomainsPerNodeType) {
        log.debug("Getting events for Model Identity {}", modelIdentitiesPerNodeType);
        final Set<EventInfo> setOfAllSupportedEvents = new HashSet<>();
        for (final Map.Entry<String, Set<String>> nodeTypeAndMoIds : modelIdentitiesPerNodeType.entrySet()) {
            final Set<String> supportedEventProducerIdsForNodeType = getSupportedEventProducerIdsForNodeType(
                    technologyDomainsPerNodeType, nodeTypeAndMoIds.getKey());
            final List<EventInfo> eventsForNodeType = getListOfSupportedEvents(Collections
                    .singletonMap( nodeTypeAndMoIds.getKey(),  nodeTypeAndMoIds.getValue()));
            if (!supportedEventProducerIdsForNodeType.isEmpty()) {
                eventsForNodeType.removeIf(event -> !shouldIncludeEvent(supportedEventProducerIdsForNodeType,
                        event.getEventProducerId()));
            }
            setOfAllSupportedEvents.addAll(eventsForNodeType);
        }
        log.debug("Found {} events for Model Identity: {}", setOfAllSupportedEvents.size(), modelIdentitiesPerNodeType);

        return new ArrayList<>(setOfAllSupportedEvents);
    }

    /**
     * Gets set of supported event producer ids for node type
     *
     * @param technologyDomainsPerNodeType
     *         the map of technology domains per node type
     * @param nodeType
     *         node Type
     *
     * @return supported event producer ids for nodeType
     */
    private Set<String> getSupportedEventProducerIdsForNodeType(final Map<String, Set<String>> technologyDomainsPerNodeType,
                                                                final String nodeType) {
        return pmCapabilityReader.getSupportedEventProducerIdsForTechnologyDomainsAndNodeType(
            technologyDomainsPerNodeType.get(nodeType), nodeType);
    }

    /**
     * This method checks if eventProducerId is null or empty string or supported.
     *
     * @param supportedEventProducerIds
     *         list of supported event producer ids
     * @param eventProducerId
     *         event producer id
     *
     * @return true if eventProducerId is null or empty string or supported
     */
    private boolean shouldIncludeEvent(final Collection<String> supportedEventProducerIds, final String eventProducerId) {
        return eventProducerId == null || eventProducerId.isEmpty() || supportedEventProducerIds.contains(eventProducerId);
    }

    /**
     * This method will read the events from node model service for an event group
     *
     * @param modelUrns
     *         list of modelUrns
     * @param groupName
     *         event group name
     *
     * @return list of eventInfo
     */
    protected Collection<EventInfo> getPMEventInfo(final Collection<String> modelUrns, final String groupName) {
        log.debug("Getting events for modelUrns {}", modelUrns);
        final Collection<EventInfo> result = new HashSet<>();
        for (final String modelUrn : modelUrns) {
            final ModelInfo modelInfo = ModelInfo.fromUrn(modelUrn);
            final PerformanceEventDefinition performanceEvents = directModelAccess.getAsJavaTree(modelInfo, PerformanceEventDefinition.class);
            for (final EventGroupType eventGroup : performanceEvents.getEventGroup()) {
                if (eventGroup.getName().equals(groupName)) {
                    for (final String eventName : eventGroup.getEvent()) {
                        result.add(new EventInfo(eventName, eventGroup.getName(), eventGroup.getEventProducerId()));
                    }
                }
            }
        }
        log.trace("Total events {} for modelUrns : {}", result, modelUrns);
        return result;
    }

    /**
     * This method will return the Manual protocol grouping urn events
     *
     * @param modelUrns
     *         list of modelUrns
     *
     * @return list of manual protocol groups
     */
    protected Collection<String> filterManualProtocolGroupingURN(final Collection<String> modelUrns) {
        final List<String> manualProtocolGrouping = new ArrayList<>();
        for (final String modelUrn : modelUrns) {
            log.debug("Model Urn : {}", modelUrn);
            if (modelUrn.contains(MANUAL_PROTOCOL_GROUPING)) {
                manualProtocolGrouping.add(modelUrn);
            }
        }
        modelUrns.removeAll(manualProtocolGrouping);
        log.debug("Manual Protocol Grouping Model Urn : {}", manualProtocolGrouping);
        return manualProtocolGrouping;
    }

    /**
     * This method will get the events for a list of model urn's
     *
     * @param modelUrns
     *         list of modelUrns
     *
     * @return list of events
     */

    protected Collection<EventTableRow> fetchEventsAndGroupsFromModelURN(final Collection<String> modelUrns) {
        final Collection<EventTableRow> result = new HashSet<>();
        for (final String modelUrn : modelUrns) {
            final ModelInfo modelInfo = ModelInfo.fromUrn(modelUrn);
            result.addAll(fetchEventsAndGroups(modelInfo));
        }
        return result;
    }

    /**
     * This method will arrange the events as per the manual event groups names
     *
     * @param eventOssGroup
     *         list of events
     * @param eventProtocolGroup
     *         list of manual protocol events
     *
     * @return result
     */
    protected Collection<EventTableRow> sortEventswithGroupAndName(final Collection<EventTableRow> eventOssGroup,
                                                                   final Collection<EventTableRow> eventProtocolGroup) {
        final Collection<EventTableRow> result = new HashSet<>();
        result.addAll(eventOssGroup);
        log.debug("eventOss Group Size {} eventProtocolGroup Size {} ", eventOssGroup.size(), eventProtocolGroup.size());

        for (final EventTableRow eventoss : eventOssGroup) {
            for (final EventTableRow eventprotocol : eventProtocolGroup) {
                if (eventoss.getEventName().equals(eventprotocol.getEventName())) {
                    result.add(eventprotocol);
                    if (eventoss.getSourceObject().equals(OTHER)) {
                        result.remove(eventoss);
                    }
                }

            }

        }
        return result;
    }

    /**
     * This method will read events from a specific profile from model serrvice. Ex: /CTR-profile/
     *
     * @param modelurn
     *         - full model URN of the file
     *
     * @return list of events of the given profile
     */
    protected Set<String> fetchProfileEvents(final String modelurn) {
        final Set<String> fetchProfileEvents = new HashSet<>();
        log.debug("Reading the profile events for urn: {}", modelurn);
        PerformanceEventDefinition perfEvents = null;
        try {
            perfEvents = directModelAccess.getAsJavaTree(modelurn, PerformanceEventDefinition.class);
            if (perfEvents == null) {
                log.debug("No profile events found ");
                return fetchProfileEvents;
            }
            for (final EventGroupType eventType : perfEvents.getEventGroup()) {
                for (final String event : eventType.getEvent()) {
                    fetchProfileEvents.add(event);
                }
            }
            log.debug("Total number  of events extracted {}", fetchProfileEvents.size());
        } catch (final UnknownModelException | ModelProcessingException e) {
            log.debug("Exception while reading the profile events", e);
        }
        return fetchProfileEvents;
    }

    /**
     * This method will fetch events from a specific profile from model info. Ex: /CTR-profile/
     *
     * @param modelInfo
     *         - full model URN of the file
     * @param profilePattern
     *         - get profile model URN
     * @param eventTypeFilter
     *         - filter type for events
     *
     * @return list of events of the given profile
     */
    protected Collection<EventTableRow> getProfileEvents(final ModelInfo modelInfo, final String profilePattern,
                                                         final EventTypeFilter eventTypeFilter) {
        final String modelurn = getProfileModelUrn(profilePattern, modelInfo.getVersion());
        final Set<String> profileEvents = fetchProfileEvents(modelurn);
        final PerformanceEventDefinition performanceEvents = directModelAccess.getAsJavaTree(modelInfo, PerformanceEventDefinition.class);
        return getEvents(performanceEvents, profileEvents, eventTypeFilter);
    }

    /**
     * This method will return events from a specific profile and groups event names and group names together. Ex: /CTR-profile/
     *
     * @param performanceEvents
     *         - fetch events from ne-defined profile
     * @param profileEvents
     *         - fetch events from specific profile.Ex:/CTR-profile/
     * @param eventTypeFilter
     *         - filter type for events
     *
     * @return list of events of the given profile
     */
    protected Collection<EventTableRow> getEvents(final PerformanceEventDefinition performanceEvents, final Set<String> profileEvents,
                                                  final EventTypeFilter eventTypeFilter) {
        final Set<String> events = new HashSet<>();
        switch (eventTypeFilter) {
            case TRIGGERED:
                getTriggeredEvents(performanceEvents.getEvent(), profileEvents, events);
                break;
            case NON_TRIGGERED:
                getNonTriggeredEvents(performanceEvents.getEvent(), profileEvents, events);
                break;
            case ALL:
                getAllEvents(performanceEvents.getEvent(), events);
                break;
            default:
                break;
        }
        final Collection<EventTableRow> result = prepareEventTableRowList(performanceEvents.getEventGroup(), profileEvents, events);
        for (final String event : events) {
            result.add(new EventTableRow(event));
        }
        log.debug("events for the profile {} ", result);
        return result;
    }

    /**
     * This method will return events from a specific profile and groups event names and group names together. Ex: /CTR-profile/
     *
     * @param performanceEvents
     *         - fetch events from ne-defined profile
     * @param profileEvents
     *         - fetch events from specific profile.Ex:/CTR-profile/
     * @param events
     *         - filter type for events
     */
    protected void getTriggeredEvents(final List<EventType> performanceEvents, final Set<String> profileEvents, final Set<String> events) {
        for (final EventType eventType : performanceEvents) {
            if (eventType.isTriggerEvent() && profileEvents.contains(eventType.getName())) {
                events.add(eventType.getName());
            }
        }
    }

    /**
     * This method will return events from a specific profile and groups event names and group names together. Ex: /CTR-profile/
     *
     * @param performanceEvents
     *         - fetch events from ne-defined profile
     * @param profileEvents
     *         - fetch events from specific profile.Ex:/CTR-profile/
     * @param events
     *         - filter type for events
     */
    protected void getNonTriggeredEvents(final List<EventType> performanceEvents, final Set<String> profileEvents, final Set<String> events) {
        for (final EventType eventType : performanceEvents) {
            if (!eventType.isTriggerEvent() && profileEvents.contains(eventType.getName())) {
                events.add(eventType.getName());
            }
        }
    }

    /**
     * This method will return events from a specific profile and groups event names and group names together. Ex: /CTR-profile/
     *
     * @param performanceEvents
     *         - fetch events from ne-defined profile
     * @param events
     *         - filter type for events
     */
    protected void getAllEvents(final List<EventType> performanceEvents, final Set<String> events) {
        for (final EventType eventType : performanceEvents) {
            events.add(eventType.getName());
        }
    }

    /**
     * This method will return events from a specific profile and groups event names and group names together. Ex: /CTR-profile/
     *
     * @param performanceEventsGroup
     *         - fetch events from ne-defined profile
     * @param profileEvents
     *         - fetch events from specific profile.Ex:/CTR-profile/
     * @param events
     *         - events from profile
     *
     * @return list of events of the given profile
     */
    protected Collection<EventTableRow> prepareEventTableRowList(final List<EventGroupType> performanceEventsGroup, final Set<String> profileEvents,
                                                                 final Set<String> events) {
        final Collection<EventTableRow> result = new HashSet<>();
        for (final EventGroupType eventGroup : performanceEventsGroup) {
            final List<String> eventNames = eventGroup.getEvent();
            for (final String eventName : eventNames) {
                if (profileEvents != null) {
                    if (events.contains(eventName)) {
                        events.remove(eventName);
                        result.add(new EventTableRow(eventName, eventGroup.getName()));
                    }
                } else {
                    events.remove(eventName);
                    result.add(new EventTableRow(eventName, eventGroup.getName()));
                }
            }
        }
        return result;
    }

    /**
     * This method will get the events for a list of model urn's
     *
     * @param profilePattern
     *         subscription's profile events
     * @param modelVersionInfo
     *         modelVersionInfo of the node
     *
     * @return list of events
     */
    protected String getProfileModelUrn(final String profilePattern, final ModelVersionInfo modelVersionInfo) {
        return String.format("%s%s%s%s", PmMetaDataConstants.PFM_EVENT_PATTERN, nodeType, profilePattern, modelVersionInfo);
    }

    /**
     * This method will return the List of events for a model
     *
     * @param modelInfo
     *         modelInfo of the Node
     *
     * @return list of events read from the model service
     */
    protected abstract Collection<EventTableRow> fetchEventsAndGroups(final ModelInfo modelInfo);

}
