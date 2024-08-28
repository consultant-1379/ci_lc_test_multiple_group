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

package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource;

import static com.ericsson.oss.pmic.api.constants.ModelConstants.TECHNOLOGY_DOMAIN_5GS;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.CELLTRACE_LRAN_SUBSCRIPTION_ATTRIBUTES;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.CELLTRACE_NRAN_SUBSCRIPTION_ATTRIBUTES;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.EBM_SUBSCRIPTIONATTRIBUTES;
import static com.ericsson.oss.services.pm.initiation.model.utils.ModelDefiner.NE;
import static com.ericsson.oss.services.pm.initiation.model.utils.ModelDefiner.getUrnPattern;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.ericsson.oss.pmic.dto.node.enums.NetworkElementType;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.interceptor.Interceptors;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.security.accesscontrol.annotation.Authorize;
import com.ericsson.oss.pmic.api.modelservice.PmCapabilityReader;
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory;
import com.ericsson.oss.pmic.impl.handler.InvokeInTransactionInterceptor;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import com.ericsson.oss.services.pm.ebs.utils.EbsSubscriptionHelper;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.ServiceException;
import com.ericsson.oss.services.pm.initiation.common.ResponseData;
import com.ericsson.oss.services.pm.initiation.common.accesscontrol.AccessControlActions;
import com.ericsson.oss.services.pm.initiation.common.accesscontrol.AccessControlResources;
import com.ericsson.oss.services.pm.initiation.model.utils.ModelDefiner;
import com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants;
import com.ericsson.oss.services.pm.initiation.restful.AttributesForAttachedNodes;
import com.ericsson.oss.services.pm.initiation.restful.ResSubscriptionAttributes;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.oss.services.pm.modelservice.SubscriptionCapabilities;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import com.ericsson.services.pm.initiation.restful.api.CounterTableRow;
import com.ericsson.services.pm.initiation.restful.api.EventTableRow;
import com.ericsson.services.pm.initiation.restful.api.PmMetaDataLookupLocal;
import com.ericsson.services.pm.initiation.restful.api.PmMimVersionQuery;

/**
 * The Subscription component ui resource, used to retrieve counter,events and moInstances.
 */
@Path("/pmsubscription")
public class SubscriptionComponentUIResource {

    private static final List<String> DEFINERS = asList("OSS", "NE");

    @Inject
    Logger logger;
    @Inject
    PmMetaDataLookupLocal metaDataLookup;
    @Inject
    private PmCapabilityModelService pmCapabilityModelService;

    @Inject
    private EbsSubscriptionHelper ebsSubscriptionHelper;

    @Inject
    private PmCapabilityReader pmCapabilityReader;

    /**
     * GET request for counters. mim can be [node type]:[OSS Model Identity] or multiple combinations of these like this (with node type the same):
     * [nodeType1]:[ossModelIdentity1],[nodeType1]:[ossModelIdentity2],[nodeType1]:[ossModelIdentity3] or like this (with different node types):
     * [nodeType1]:[ossModelIdentity1],[nodeType2]:[ossModelIdentity5],[nodeType3]:[ossModelIdentity6]
     *
     * @param pmvq
     *         - captures all the mimVersions.
     * @param type
     *         the entity that defined the counters (defined the model of the counters), e.g. NE or OSS. This is optional
     *
     * @return the counters as JSON Response given a list of mimVersions and optional definer. If definer is not provided then counters in response
     * will not be filtered on definer.
     * @throws PfmDataException
     *         -will throw a PfmDataException
     * @throws ValidationException
     *         - iv validation fails
     */
    @GET
    @Path("/counters")
    @Produces("application/json")
    @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    public Response getCounters(@QueryParam("mim") final PmMimVersionQuery pmvq, @QueryParam("definer") final String type)
            throws PfmDataException, ValidationException {
        final Collection<CounterTableRow> result;
        if (DEFINERS.contains(type)) {//compatibility with old query param
            result = fetchCountersDeprecatedQuery(pmvq, type);
        } else {
            //here assuming if any node requires all node requires, else will need to pass the type down further and query for each nodeType.
            result = metaDataLookup.getCounters(pmvq, pmCapabilityReader.getSupportedModelDefinersForCounters(type), pmCapabilityReader.eventsRequiredForCounters(type),
                                                pmCapabilityReader.shouldSupportExternalCounterName(type, pmvq.getMimVersions()), pmCapabilityReader.getSupportedModelDefinersForFlexCounters(type));
        }
        return Response.status(Response.Status.OK).entity(result).build();
    }

    /**
     * method included only to support deprecated REST call used in TAF
     *      remove when TAF is updated
     */
    private Collection<CounterTableRow> fetchCountersDeprecatedQuery(final PmMimVersionQuery pmvq, final String type) throws PfmDataException, ValidationException {
        final ModelDefiner definer = ModelDefiner.getModelDefiner(type);
        final boolean eventsRequiredForCounters = definer == ModelDefiner.OSS;
        return metaDataLookup.getCounters(pmvq, getModelUrnsForModelDefinerDeprecatedQuery(pmvq.getMimVersions(), definer), eventsRequiredForCounters, true, getFlexModelUrnsForDeprecatedQuery(pmvq.getMimVersions(), definer));//true matches current behaviour
        //true value matches current behaviour
    }

    /**
     * method included only to support deprecated REST call used in TAF
     *      remove when TAF is updated
     */
    private List<String> getModelUrnsForModelDefinerDeprecatedQuery(final Set<NodeTypeAndVersion> nodeTypeAndVersions,
                                                                    final ModelDefiner modelDefiner) {
        if (!shouldUseMutipleModelUrns(nodeTypeAndVersions, modelDefiner)) {
            return singletonList(modelDefiner.getUrnPattern());
        }
        return ebsSubscriptionHelper.getEbsModelDefiners(CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_FILE);
    }

    /**
     * method included only to support deprecated REST call used in TAF
     *      remove when TAF is updated
     */
    private List<String> getFlexModelUrnsForDeprecatedQuery(final Set<NodeTypeAndVersion> nodeTypeAndVersions,
                                                            final ModelDefiner modelDefiner) {
        if (modelDefiner == NE) {
            return emptyList();
        } else if (isEbm(nodeTypeAndVersions)) {//ebm uses SGSN node type, cell trace does not
            return pmCapabilityReader.getSupportedModelDefinersForFlexCounters(EBM_SUBSCRIPTIONATTRIBUTES);
        }
        final String capabilityModel = nodeTypeAndVersions.stream()
                                                          .anyMatch(it ->
                                                                  it.getTechnologyDomain().contains(TECHNOLOGY_DOMAIN_5GS)
                                                          ) ? CELLTRACE_NRAN_SUBSCRIPTION_ATTRIBUTES : CELLTRACE_LRAN_SUBSCRIPTION_ATTRIBUTES;
        return pmCapabilityReader.getSupportedModelDefinersForFlexCounters(capabilityModel);
    }

    /**
     * method included only to support deprecated REST call used in TAF
     *      remove when TAF is updated
     */
    private boolean isEbm(final Set<NodeTypeAndVersion> nodeTypeAndVersions) {
        return nodeTypeAndVersions.stream().anyMatch(it -> it.getNodeType().equals(NetworkElementType.SGSNMME.getNeTypeString()));
    }

    /**
     * method included only to support deprecated REST call used in TAF
     *      remove when TAF is updated
     */
    private boolean shouldUseMutipleModelUrns(final Set<NodeTypeAndVersion> nodeTypeAndVersions,
                                              final ModelDefiner modelDefiner) {
        return modelDefiner == ModelDefiner.OSS &&
                nodeTypeAndVersions.stream().anyMatch(it -> it.getTechnologyDomain().contains(TECHNOLOGY_DOMAIN_5GS));
    }

    /**
     * @param pmMimVersionQuery
     *         - captures all the mimVersions.
     * @param eventFilterQueryParameter
     *         - will enable to fetch events specific to Tech domain, This is optional,and may present or not
     *
     * @return return events as Response given a list of mimVersions
     * @throws PfmDataException
     *         -will throw a PfmDataException
     * @throws ValidationException
     *         - iv validation fails
     */
    @GET
    @Path("/getEvent")
    @Produces("application/json")
    @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    public Response getEvents(@QueryParam("mim") final PmMimVersionQuery pmMimVersionQuery,
                              @QueryParam("eventFilter") final String eventFilterQueryParameter)
            throws PfmDataException, ValidationException {
        final String eventFilter = eventFilterQueryParameter == null ? "" : eventFilterQueryParameter;
        final Collection<EventTableRow> eventTableRows = metaDataLookup.getEvents(pmMimVersionQuery, eventFilter);
        return Response.status(Response.Status.OK).entity(eventTableRows).build();
    }

    /**
     * Get method for fetching mo instance from DPS based on supportedMOCs and added NetworkElement. ex.
     * [{"nodeName":"RNC06RBS01","moinstances":"AtmPort=1-1-1"},{"nodeName":"RNC06RBS01","moinstances":"AtmPort=1-1-5"}]
     *
     * @param pmMimVersionQuery
     *         captures all the mimVersions.
     * @param nodes
     *         the nodes
     * @param subscriptionType
     *         the subscription type
     * @param moClasses
     *         the managed object classes
     *
     * @return return list of moisntance
     * @throws DataAccessException
     *         -will throw a DataAccessException
     */
    @GET
    @Path("/moinstances")
    @Produces({MediaType.APPLICATION_JSON})
    @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @ReadOnly
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @Interceptors(InvokeInTransactionInterceptor.class)
    public Response getMOInstances(@QueryParam("mim") final PmMimVersionQuery pmMimVersionQuery, @QueryParam("nodes") final String nodes,
                                   @QueryParam("moClasses") final String moClasses, @QueryParam("subscriptionType") final String subscriptionType)
            throws DataAccessException {

        final Object moinstances = getManagedObjects(pmMimVersionQuery, nodes, moClasses, subscriptionType);
        logger.debug("supported moinstnces : {}", moinstances);
        return Response.status(Response.Status.OK).entity(moinstances).build();
    }

    /**
     * Get method for fetching mo instance from DPS based on supportedMOCs and added NetworkElement. ex.
     * [{"nodeName":"RNC06RBS01","moinstances":"UtranCell=1-1-1"},{"nodeName":"RNC06RBS01","moinstances":"UtranCell=1-1-5"}]
     *
     * @param pmMimVersionQuery
     *         captures all the mimVersions.
     * @param nodes
     *         the nodes
     * @param subscriptionType
     *         the subscription type
     * @param moClasses
     *         the managed object classes
     *
     * @return return list of moisntance
     * @throws DataAccessException
     *         -will throw a DataAccessException
     */
    @GET
    @Path("/cells")
    @Produces({MediaType.APPLICATION_JSON})
    @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @ReadOnly
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @Interceptors(InvokeInTransactionInterceptor.class)
    public Response getCells(@QueryParam("mim") final PmMimVersionQuery pmMimVersionQuery, @QueryParam("nodes") final String nodes,
                             @QueryParam("moClasses") final String moClasses, @QueryParam("subscriptionType") final String subscriptionType)
            throws DataAccessException {

        final Object cells = getManagedObjects(pmMimVersionQuery, nodes, moClasses, subscriptionType);
        logger.debug("supported cells : {}", cells);
        return Response.status(Response.Status.OK).entity(cells).build();
    }

    /**
     * Get method for fetching managed object from DPS based on supportedMOCs and added NetworkElement
     *
     * @param pmMimVersionQuery
     *         captures all the mimVersions.
     * @param nodes
     *         the nodes
     * @param subscriptionType
     *         the subscription type
     * @param moClasses
     *         the managed object classes
     *
     * @return return list of moisntance
     * @throws DataAccessException
     *         - DataAccessException
     */
    private Object getManagedObjects(final PmMimVersionQuery pmMimVersionQuery, final String nodes, final String moClasses,
                                     final String subscriptionType)
            throws DataAccessException {

        logger.debug("QUERY param - nodeType {}, nodes {}, moClassTypes {}, subscriptionType {}", pmMimVersionQuery.getMimVersions(), nodes,
                moClasses, subscriptionType);
        return metaDataLookup.getSupportedMoInstances(pmMimVersionQuery.getMimVersions(), nodes, moClasses, subscriptionType);
    }

    /**
     * GET request for filter counters based on subscriptionType, The supportedMOCs for subscriptionType is defined in PMICFunction capability which
     * can read based on mimInfo.
     *
     * @param subscriptionType
     *         - type of the subscription.
     * @param mimVersionQuery
     *         - captures all the mimVersions.
     * @param type
     *         the subscription capability type for which is used to fetch counters
     *
     * @return the counters as JSON Response will not be filtered on definer.
     * @throws PfmDataException
     *         -will throw a PfmDataException
     * @throws ValidationException
     *         - iv validation fails
     */
    @GET
    @Path("/counters/{subscriptionType}")
    @Produces("application/json")
    @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    public Response getCounters(@PathParam("subscriptionType") final String subscriptionType,
                                @QueryParam("mim") final PmMimVersionQuery mimVersionQuery, @QueryParam("definer") final String type)
            throws PfmDataException, ValidationException {
        final List<String> definers;
        final boolean supportExternalCounterName;
        if (DEFINERS.contains(type)) {
            supportExternalCounterName = true;
            definers = asList(getUrnPattern(type));
        } else {
            supportExternalCounterName = pmCapabilityReader.shouldSupportExternalCounterName(type, mimVersionQuery.getMimVersions());
            definers = pmCapabilityReader.getSupportedModelDefinersForCounters(type);
        }
        final Collection<CounterTableRow> result =
                metaDataLookup.getFilteredCountersForAllVersions(mimVersionQuery, definers, subscriptionType, supportExternalCounterName);
        return Response.status(Response.Status.OK).entity(result).build();
    }

    /**
     * GET request for countersubgroups for capability CounterSubGroups
     *
     * @param subscriptionType
     *         - type of the Subscription
     *
     * @return the counterssubgroups as JSON.
     */
    @GET
    @Path("/countersubgroups/{subscriptionType}")
    @Produces("application/json")
    @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    public Response getCounterSubGroups(@PathParam("subscriptionType") final String subscriptionType) {

        logger.debug("QUERY param - subscriptionType {}", subscriptionType);
        final Map<String, List<String>> result = metaDataLookup.getCounterSubGroups(subscriptionType);
        return Response.status(Response.Status.OK).entity(result).build();
    }

    /**
     * Gets cell traffic events for node type and version
     *
     * @param pmvq
     *         - captures all the mimVersions.
     * @param subscriptionType
     *         - type of the subscription.
     *
     * @return the Cell Traffic events as JSON.
     * @throws PfmDataException
     *         -will throw a PfmDataException
     * @throws ValidationException
     *         - iv validation fails
     */
    @GET
    @Path("/getCellTrafficEvents")
    @Produces("application/json")
    @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    public Response getCelltrafficEvents(@QueryParam("mim") final PmMimVersionQuery pmvq,
                                         @QueryParam("subscriptionType") final String subscriptionType)
            throws PfmDataException, ValidationException {
        final Map<String, Collection<EventTableRow>> result = new HashMap<>();
        result.put(PmMetaDataConstants.TRIGGER_EVENTS, metaDataLookup.getWideBandEventsForAllVersions(pmvq, subscriptionType));
        result.put(PmMetaDataConstants.NON_TRIGGER_EVENTS, metaDataLookup.getCellTrafficNonTriggerEventsForAllVersions(pmvq));
        return Response.status(Response.Status.OK).entity(result).build();
    }

    /**
     * Gets GPEH events for node type and version
     *
     * @param pmvq
     *         - captures all the mimVersions.
     * @param subscriptionType
     *         - type of the subscription.
     *
     * @return the GPEH events as JSON.
     * @throws PfmDataException
     *         -will throw a PfmDataException
     * @throws ValidationException
     *         - iv validation fails
     */
    @GET
    @Path("/getWcdmaEvents")
    @Produces("application/json")
    @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    public Response getWcdmaEvents(@QueryParam("mim") final PmMimVersionQuery pmvq, @QueryParam("subscriptionType") final String subscriptionType)
            throws PfmDataException, ValidationException {
        final Collection<EventTableRow> result = metaDataLookup.getWideBandEventsForAllVersions(pmvq, subscriptionType);
        return Response.status(Response.Status.OK).entity(result).build();
    }

    /**
     * Get NodeTypes supported for the subscriptionTypes and supported ROPs
     *
     * @return Response containing the supported SubscriptionTypes and ROPs
     */
    @GET
    @Path("/capabilities")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getNodesWithSupportedROPSBySubscriptionType() {

        final List<SubscriptionCapabilities> pmicNodeList = pmCapabilityModelService.getSupportedNetworkElements();

        logger.debug("Response of the supported SubscriptionTypes and ROPs {}", pmicNodeList);
        return Response.status(Status.OK).entity(pmicNodeList).build();
    }

    /**
     * GET request for Ue Fraction and Sampling Rate possible values for Res subscriptions. MIM is like this:
     * [nodeType1]:[ossModelIdentity1],[nodeType1]:[ossModelIdentity2],[nodeType1]:[ossModelIdentity3]
     *
     * @param pmvq
     *         - captures all the mimVersions.
     *
     * @return all Res related attributes
     * @throws ServiceException
     *         -will throw a ServiceException
     */
    @GET
    @Path("/resAttributes")
    @Produces("application/json")
    @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    public Response getResAttributes(@QueryParam("mim") final PmMimVersionQuery pmvq) throws ServiceException {
        final ResSubscriptionAttributes result = metaDataLookup.getResAttributes(pmvq);
        return Response.status(Response.Status.OK).entity(result).build();
    }

    /**
     * Gets count of attached nodes for a subscription
     *
     * @param attributesForAttachedNodes
     *         - object containing required attribute to calculate attached nodes size
     *
     * @return The Response containing the count of attached nodes
     * @throws DataAccessException
     *         - if any other data access exception is thrown.
     */
    @POST
    @Path("/getAttachedNodeCount")
    @Produces({MediaType.APPLICATION_JSON})
    @ReadOnly
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @Interceptors(InvokeInTransactionInterceptor.class)
    public Response getAttachedNodeCount(final AttributesForAttachedNodes attributesForAttachedNodes) throws DataAccessException {
        return Response.status(Status.OK).entity(metaDataLookup.getAttachedNodeCount(attributesForAttachedNodes)).build();
    }

    /**
     * Gets the MtrAccessTypes for the MtrSubscription
     *
     * @return The Response containing the Array of MtrAccessTypes
     */
    @GET
    @Path("/getMtrAccessTypes/")
    @Produces("application/json")
    public Response getMtrAccessTypes() {
        return Response.status(Status.OK).entity(metaDataLookup.getMtrAccessTypes()).build();
    }

    /**
     * List of non associated nodes for a subscription
     *
     * @param subscriptionId
     *         - subscriptionId containing required attribute to calculate attached nodes size
     *
     * @return The Response containing the list of non associated nodes
     * @throws DataAccessException
     *         - if any other data access exception is thrown.
     */

    @GET
    @Path("/{id}/getNonAssociatedNodes")
    @Produces({MediaType.APPLICATION_JSON})
    @ReadOnly
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @Interceptors(InvokeInTransactionInterceptor.class)
    public Response getNonAssociatedNodes(@PathParam("id") final long subscriptionId) throws DataAccessException {
        try {
            return Response.status(Status.OK).entity(metaDataLookup.getNonAssociatedNodes(subscriptionId)).build();
        } catch (final Exception e) {
            return setResponse(e.getMessage(), Status.BAD_REQUEST);
        }
    }

    @GET
    @Path("/{id}/getUsedRecordingReference")
    @Produces({MediaType.APPLICATION_JSON})
    @ReadOnly
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @Interceptors(InvokeInTransactionInterceptor.class)
    public Response getUsedRecordingReference(@PathParam("id") final long subscriptionId) {
        try {
            return Response.status(Status.OK).entity(metaDataLookup.getUsedRecordingReferences(subscriptionId)).build();
        } catch (final Exception e) {
            return setResponse(e.getMessage(), Status.BAD_REQUEST);
        }
    }

    private Response setResponse(final String message, Status status) {
        final ResponseData response = new ResponseData();
        response.setCode(status);
        response.setError(message);
        return Response.status(response.getCode()).entity(response).build();
    }
}
