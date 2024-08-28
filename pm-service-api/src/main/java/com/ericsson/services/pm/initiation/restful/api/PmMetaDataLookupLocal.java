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

package com.ericsson.services.pm.initiation.restful.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ericsson.oss.external.modeling.schema.gen.ext_integrationpointlibrary.IntegrationPoint;
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.MtrAccessType;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.initiation.model.utils.ModelDefiner;
import com.ericsson.oss.services.pm.initiation.restful.AttributesForAttachedNodes;
import com.ericsson.oss.services.pm.initiation.restful.ResSubscriptionAttributes;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * This represents the bean to get pm meta data from model service e.g. events or counters
 *
 * @author enichyl
 */
public interface PmMetaDataLookupLocal {

    /**
     * Gets counters.
     *
     * @param pmvq
     *         captures all the mimVersions.
     * @param modelUrns
     *         the model urns
     * @param eventsRequiredForCounters
     *         boolean whether the counters are event based
     * @param supportExternalCounterName
     *         boolean whether the counters are using external name
     * @param flexModelDefiners
     *         the flex model urns
     * @return the counters
     * @throws PfmDataException
     *         PfmDataException
     * @throws ValidationException
     *         - if validation fails
     */
    Collection<CounterTableRow> getCounters(final PmMimVersionQuery pmvq, final List<String> modelUrns,
                                            final boolean eventsRequiredForCounters, final boolean supportExternalCounterName, final List<String> flexModelDefiners)
            throws PfmDataException, ValidationException;

    /**
     * Gets applicable counters.
     *
     * @param moClassList
     *         the managed object class list
     * @param type
     *         the type
     * @param version
     *         the version
     *
     * @return the applicable counters
     */
    List<String> getApplicableCounters(final List<CounterInfo> moClassList, final String type, final String version);

    /**
     * This is a generic method to get the metaData (like counter or events) from model service based on node type and its version from NetworkElement
     * MO.
     *
     * @param counterInfo
     *         - list of counter names and Managed Object class types
     * @param nodesTypeVersion
     *         - set of node types and Operations Support Systems model identity's
     * @param supportedModelDefiners
     *         - the supported model definers as strings
     *
     * @return - returns a list Of Counters
     */
    List<CounterInfo> getCorrectCounterListForTheSpecifiedMims(final List<CounterInfo> counterInfo, final Set<NodeTypeAndVersion> nodesTypeVersion,
                                                               final List<String> supportedModelDefiners, final List<String> supportedFlexModelDefiners,
                                                               final boolean supportExternalCounterNames);

    /**
     * This method would get the list of moClass and its events from model service based on node type and its version from NetworkElement MO.
     *
     * @param moClassList
     *         the managed object class list
     * @param type
     *         the type
     * @param version
     *         the version
     *
     * @return the applicable events
     */
    List<String> getApplicableEvents(final List<EventInfo> moClassList, final String type, final String version);

    /**
     * This is a generic method to get the metaData (like counter or events) from model service based on node type and its version from NetworkElement
     * MO.
     *
     * @param eventInfo
     *         - list of EventInfo Objects
     * @param nodesTypeVersion
     *         - set of node types and Operations Support Systems model identity's
     *
     * @return - returns a list of Events
     */
    List<EventInfo> getCorrectEventListForTheSpecifiedMims(List<EventInfo> eventInfo, Set<NodeTypeAndVersion> nodesTypeVersion);

    /**
     * Get the events from model service based on Meta Information Model version query containing on or more different Meta Information Model versions
     *
     * @param pmMimVersionQuery
     *         - query to get Meta Information Model versions
     * @param eventFilter
     *         - Filter to get events based on technologyDomain
     *
     * @return - returns a Collection of EventTableRow Objects
     * @throws PfmDataException
     *         - thrown if PmMimVersionQuery is invalid
     * @throws ValidationException
     *         - iv validation fails
     */
    Collection<EventTableRow> getEvents(final PmMimVersionQuery pmMimVersionQuery, final String eventFilter) throws
            PfmDataException, ValidationException;

    /**
     * Returns the list MoInstances for nodes based on selected nodes and counter in subscription.
     *
     * @param nodeAndVersionSet
     *         - set of NeType and OSSModelIdentity
     * @param nodes
     *         - selected nodes in susbcription object
     * @param moClasses
     *         - selected counter (MOClassTypes) in susbcription object
     * @param subscriptionType
     *         - selected subscription type in susbcription object
     *
     * @return the supported mo instances
     * @throws DataAccessException
     *         - thrown if the database cannot be reached
     */
    Object getSupportedMoInstances(final Set<NodeTypeAndVersion> nodeAndVersionSet, final String nodes, final String moClasses,
                                   final String subscriptionType)
            throws DataAccessException;

    /**
     * Returns filtered counters based on supported MOClassTypes. The supportedMOCs is defined in PMICFunction capability which can read based on node
     * type and subscriptionType.
     *
     * @param mimVersionQuery
     *         - captures all the mimVersions.
     * @param modelDefiner
     *         the entity that defined the counters (defined the model of the counters), e.g. NE or OSS. This is optional
     * @param subscriptionType
     *         - type of the subscription.
     *
     * @return List of CounterTableRow Objects
     * @throws PfmDataException
     *         - thrown if No counters found for mim versions
     * @throws ValidationException
     *         - iv validation fails
     */
    Collection<CounterTableRow> getFilteredCountersForAllVersions(final PmMimVersionQuery mimVersionQuery,
                                                                  final List<String> modelDefiner,
                                                                  final String subscriptionType,
                                                                  final boolean supportsExternalCounterNames)
            throws PfmDataException, ValidationException;

    /**
     * Returns the counter sub group for the capability CounterSubGroups
     *
     * @param subscriptionType
     *         - type of the Subscription
     *
     * @return Map where key is counter subgroup name and value is list of counters
     */
    Map<String, List<String>> getCounterSubGroups(final String subscriptionType);

    /**
     * Get the cell traffic non trigger events from model service based on Meta Information Model version query containing on or more different Meta
     * Information Model Service
     *
     * @param pmvq
     *         - query to get Meta Information Model versions
     *
     * @return - returns a Collection of Trigger and Non Trigger events as EventTableRow Objects
     * @throws PfmDataException
     *         - thrown if PmMimVersionQuery is invalid
     * @throws ValidationException
     *         - iv validation fails
     */
    Collection<EventTableRow> getCellTrafficNonTriggerEventsForAllVersions(final PmMimVersionQuery pmvq) throws PfmDataException, ValidationException;

    /**
     * Get the Wideband profile events from model service based on Meta Information Model version query containing on or more different Meta
     * Information Model Service
     *
     * @param pmvq
     *         - query to get Meta Information Model versions
     * @param subscriptionType
     *         - type of the Subscription
     *
     * @return - returns events as EventTableRow Objects
     * @throws PfmDataException
     *         - thrown if PmMimVersionQuery is invalid
     * @throws ValidationException
     *         - iv validation fails
     */
    Collection<EventTableRow> getWideBandEventsForAllVersions(final PmMimVersionQuery pmvq, final String subscriptionType)
            throws PfmDataException, ValidationException;

    /**
     * @param nodesTypeVersion
     *         -
     *
     * @return -
     * @throws PfmDataException
     *         -
     */
    Collection<EventTableRow> getEventsForAllVersions(Set<NodeTypeAndVersion> nodesTypeVersion) throws PfmDataException;

    /**
     * Get the Events Integration Point, for a given integration point name
     *
     * @param integrationPointName
     *         - the integration point name
     *
     * @return - events integration point
     */
    IntegrationPoint getEventsIntegrationPoint(final String integrationPointName);

    /**
     * @param pmvq
     *         - mim version query
     *
     * @return - res attributes values
     * @throws PfmDataException
     *         - thrown if PmMimVersionQuery is invalid
     */
    ResSubscriptionAttributes getResAttributes(PmMimVersionQuery pmvq) throws PfmDataException;

    /**
     * Gets count of attached nodes (WRAN).
     *
     * @param attributesForAttachedNodes
     *         - object containing required attribute to calculate attached nodes size
     *
     * @return - count of attached nodes
     * @throws DataAccessException
     *         - thrown if there is any issue in accessing dps
     */
    int getAttachedNodeCount(final AttributesForAttachedNodes attributesForAttachedNodes) throws DataAccessException;

    /**
     * Gets List of MtrAccessTypes.
     *
     * @return - Map with key as MtrAccessTypes and the value as the Array of MtrAccessTypes
     */
    Map<String, MtrAccessType[]> getMtrAccessTypes();

    /**
     * Gets Non associated nodes (GRAN).
     *
     * @param subscriptionId
     *         - object containing required attribute to find attached nodes
     *
     * @return - List of Non associated nodes
     * @throws DataAccessException
     *         - thrown if there is any issue in accessing dps
     */
    List<String> getNonAssociatedNodes(final long subscriptionId) throws DataAccessException, RetryServiceException, ValidationException;

    /**
     * Gets used recording references
     *
     * @param subscriptionId
     *         - object containing required attribute to find used recording references
     *
     * @return - Set of used recording references
     * @throws DataAccessException
     *         - thrown if there is any issue in accessing dps
     */
    Set<Integer> getUsedRecordingReferences(final long subscriptionId) throws DataAccessException , RetryServiceException,ValidationException;

}
