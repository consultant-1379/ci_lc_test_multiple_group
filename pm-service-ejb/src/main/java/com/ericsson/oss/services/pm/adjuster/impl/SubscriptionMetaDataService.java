/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.adjuster.impl;

import static com.ericsson.oss.services.pm.initiation.utils.CommonUtil.isNullOrEmptyCollection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.external.modeling.schema.gen.ext_integrationpointlibrary.EventIdToNameMapType;
import com.ericsson.oss.external.modeling.schema.gen.ext_integrationpointlibrary.EventMapType;
import com.ericsson.oss.external.modeling.schema.gen.ext_integrationpointlibrary.IntegrationPoint;
import com.ericsson.oss.pmic.api.modelservice.PmCapabilityReader;
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import com.ericsson.services.pm.initiation.restful.api.CounterTableRow;
import com.ericsson.services.pm.initiation.restful.api.EventBasedCounterTableRow;
import com.ericsson.services.pm.initiation.restful.api.EventTableRow;
import com.ericsson.services.pm.initiation.restful.api.PmMetaDataLookupLocal;
import com.ericsson.services.pm.initiation.restful.api.PmMimVersionQuery;

/**
 * Common class containing implementation methods for getting various subscription data.
 */
public class SubscriptionMetaDataService {

    private static final String ESN_INTEGRATION_POINT = "EsnLteRanApollo-ForwarderSubscriberDecoded";

    @Inject
    private Logger logger;

    @Inject
    private PmMetaDataLookupLocal counterOrEventLookup;

    @Inject
    private PmCapabilityReader pmCapabilityReader;

    /**
     * compares subscription counters with cached or then Model Service counters, remove all invalid counters prevents invalid modification of
     * counters.
     *
     * @param subscriptionName
     *         the subscription name
     * @param counters
     *         the counters
     * @param nodesTypeVersion
     *         the nodes type version
     * @param supportedModelDefiners
     *         the model definers as strings
     * @param supportedFlexModelDefiners
     *         the flex model definers as strings
     *
     * @return {@code List<CounterInfo>} correctCounters
     */
    public List<CounterInfo> getCorrectCounters(final String subscriptionName, final List<CounterInfo> counters,
                                                final Set<NodeTypeAndVersion> nodesTypeVersion,
                                                final List<String> supportedModelDefiners,
                                                final List<String> supportedFlexModelDefiners, final boolean supportExternalCounterNames) {
        if (isNullOrEmptyCollection(counters)) {
            logger.warn("Subscription {} has no {} counters. Possible reason: Last node has been removed from the active subscription",
                    subscriptionName, supportedModelDefiners);
            return new ArrayList<>();
        }
        logger.debug("Checking the {} counters {} selected for this {} subscription against the node versions {}",
                supportedModelDefiners, counters, subscriptionName, nodesTypeVersion);

        return counterOrEventLookup.getCorrectCounterListForTheSpecifiedMims(counters, nodesTypeVersion, supportedModelDefiners, supportedFlexModelDefiners, supportExternalCounterNames);
    }

    /**
     * compares subscription events with Model Service events, remove all invalid events, prevents invalid modification of events.
     *
     * @param subscriptionName
     *         the subscription name
     * @param events
     *         the events
     * @param nodesTypeVersion
     *         the nodes type version
     *
     * @return {@code List<EventInfo>} correctEvents
     */
    public List<EventInfo> getCorrectEvents(final String subscriptionName, final List<EventInfo> events,
                                            final Set<NodeTypeAndVersion> nodesTypeVersion) {
        if (isNullOrEmptyCollection(events)) {
            logger.warn("Subscription {} has no events. Possible reason: Last node has been removed from the active subscription",
                    subscriptionName);
            return new ArrayList<>();
        }
        logger.debug("Checking the events {} selected for {} subscription against the node versions {}",
                events, subscriptionName, nodesTypeVersion);
        return counterOrEventLookup.getCorrectEventListForTheSpecifiedMims(events, nodesTypeVersion);
    }

    /**
     * Check if there are counters based on events, then add these mandatory events inside the subscription if they are not present.
     *
     * @param nodesTypeVersion
     *         - query to get Meta Information Model versions
     * @param modelUrns
     *         - the model urns
     * @param counterInfoList
     *         - the list of counters in the subscription as CounterInfo Objects
     *
     * @return - the events list
     * @throws ValidationException
     *         - the PMIC invalid input exception
     * @throws PfmDataException
     *         - exception when dealing with pfm_counters/pfm_events
     */
    public Set<EventInfo> validateEventBasedCounters(final Set<NodeTypeAndVersion> nodesTypeVersion, final List<String> modelUrns,
                                                     final List<CounterInfo> counterInfoList, final String type) throws ValidationException, PfmDataException {

        final Set<String> allMandatoryEventNamesForCounters = checkMandatoryEvents(nodesTypeVersion, modelUrns, counterInfoList, type);
        Set<EventInfo> mandatoryEventInfoSet = new HashSet<>();
        if (!allMandatoryEventNamesForCounters.isEmpty()) {
            mandatoryEventInfoSet = getMandatoryEventsInfo(nodesTypeVersion, allMandatoryEventNamesForCounters);
        }
        return mandatoryEventInfoSet;
    }

    /**
     * Given a list of CounterInfo returns a Set of events associated with the given counters.
     *
     * @param nodesTypeVersion
     * @param modelUrns
     * @param counterInfoList
     *
     * @return Set of event names associated with the given counters
     * @throws PfmDataException
     * @throws ValidationException
     */
    private Set<String> checkMandatoryEvents(final Set<NodeTypeAndVersion> nodesTypeVersion, final List<String> modelUrns,
                                             final List<CounterInfo> counterInfoList, final String type) throws PfmDataException, ValidationException {

        final Set<String> allMandatoryEvents = new HashSet<>();

        if (isNullOrEmptyCollection(counterInfoList)) {
            return allMandatoryEvents;
        }

        final PmMimVersionQuery pmMimVersionQuery = new PmMimVersionQuery(nodesTypeVersion);

        /*
         * retrieves counters with related mandatory events
         */
        final Collection<CounterTableRow> allValidCounterList = counterOrEventLookup.getCounters(pmMimVersionQuery, modelUrns, true, true, pmCapabilityReader.getSupportedModelDefinersForFlexCounters(type));

        /*
         * we need the mandatory events related with only the selected counters
         */
        final Collection<CounterTableRow> actualCounterList = new HashSet<>();

        for (final CounterInfo counter : counterInfoList) {
            for (final CounterTableRow oldCounter : allValidCounterList) {
                if (oldCounter.getCounterName().contentEquals(counter.getName())) {
                    actualCounterList.add(oldCounter);
                }
            }
        }

        for (final CounterTableRow eventBasedCounter : actualCounterList) {
            for (final String mandatoryEvent : ((EventBasedCounterTableRow) eventBasedCounter).getBasedOnEvent()) {
                final String mandatoryEventName = mandatoryEvent.split("/")[3];
                allMandatoryEvents.add(mandatoryEventName);
            }
        }

        return allMandatoryEvents;
    }

    /**
     * returns a set of EvenfInfo based on a given set of event names.
     *
     * @param nodesTypeVersion
     * @param allMandatoryEventsForCounters
     *
     * @return a set of EvenfInfo
     * @throws PfmDataException
     */
    private Set<EventInfo> getMandatoryEventsInfo(final Set<NodeTypeAndVersion> nodesTypeVersion, final Set<String> allMandatoryEventsForCounters)
            throws PfmDataException {

        final Set<EventInfo> eventInfoSet = new HashSet<>();

        final Collection<EventTableRow> allEvents = counterOrEventLookup.getEventsForAllVersions(nodesTypeVersion);

        for (final String eventNameForCounter : allMandatoryEventsForCounters) {
            for (final EventTableRow correctEvent : allEvents) {
                if (correctEvent.getEventName().equals(eventNameForCounter)) {
                    eventInfoSet.add(new EventInfo(correctEvent.getEventName(), correctEvent.getSourceObject(), correctEvent.getEventProducerId()));
                }
            }
        }
        return eventInfoSet;
    }

    /**
     * Gets ESN applicable events.
     *
     * @param nodesTypeVersions
     *         the nodes type versions
     *
     * @return the esn applicable events
     */
    public List<EventInfo> getEsnApplicableEvents(final Set<NodeTypeAndVersion> nodesTypeVersions) {
        if (nodesTypeVersions.isEmpty()) {
            return new ArrayList<>(0);
        }
        final Set<EventInfo> eventInfos = new HashSet<>();
        try {
            final Set<String> supportedEsnEventNames = getIntegrationPointDestinationEventNames();
            final Collection<EventTableRow> eventTableRows = counterOrEventLookup.getEventsForAllVersions(nodesTypeVersions);
            for (final EventTableRow eventTableRow : eventTableRows) {
                if (supportedEsnEventNames.contains(eventTableRow.getEventName())) {
                    eventInfos.add(new EventInfo(eventTableRow.getEventName(), eventTableRow.getSourceObject()));
                }
            }
        } catch (final PfmDataException exception) {
            logger.warn("No events were found for the supplied mim versions {}", nodesTypeVersions);
            logger.debug("Could not find any events for ESN Subscription!", exception);
        }
        return new ArrayList<>(eventInfos);
    }

    private Set<String> getIntegrationPointDestinationEventNames() {
        logger.debug("Retrieving the EventNames for Event Stream NBI integration point");

        final Set<String> supportedEsnEventNames = new HashSet<>();
        try {
            final IntegrationPoint integrationPoint = counterOrEventLookup.getEventsIntegrationPoint(ESN_INTEGRATION_POINT);
            if (integrationPoint != null) {
                final EventMapType eventMapType = integrationPoint.getEventMap();
                if (eventMapType != null) {
                    final List<EventIdToNameMapType> eventIdToNameMappings = eventMapType.getMapping();
                    for (final EventIdToNameMapType eventIdToName : eventIdToNameMappings) {
                        supportedEsnEventNames.add(eventIdToName.getEventName());
                    }
                }
            }
        } catch (final Exception exception) {
            logger.warn("Could not retrieve supported events from IntegrationPoint for ESN Subscription!");
            logger.debug("Could not find any events for ESN Subscription!", exception);
        }
        return supportedEsnEventNames;
    }
}
