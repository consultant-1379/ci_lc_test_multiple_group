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

import static com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription.CellTraceSubscription210Attribute.cellTraceCategory;
import static com.ericsson.oss.pmic.dto.subscription.Subscription.Subscription220Attribute.administrationState;
import static com.ericsson.oss.pmic.dto.subscription.Subscription.UNKNOWN_SUBSCRIPTION_ID_AS_STRING;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.ACTIVATE_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.DEACTIVATE_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.DELETE_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.GET_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.POST_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.PUT_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.INTERNAL_SERVER_EXCEPTION;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.INVALID_INPUT;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.NO_NODE_FDN_IN_REQUEST;
import static com.ericsson.oss.services.pm.common.logging.PMICLog.Error.NO_SUBSCRIPTION_IN_REQUEST;
import static com.ericsson.oss.services.pm.common.utils.PmFunctionConstants.PM_FUNCTION_OFF;
import static com.ericsson.oss.services.pm.common.utils.PmFunctionConstants.PM_FUNCTION_ON;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.CBS_ALLOWED_EXCEEDED;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.NO_PERMISSION_ACCESS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.ericsson.oss.pmic.impl.handler.InvokeInTransaction;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import org.codehaus.jackson.map.annotate.JsonView;
import org.jboss.resteasy.annotations.providers.jaxb.Formatted;
import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.security.accesscontrol.SecurityViolationException;
import com.ericsson.oss.itpf.sdk.security.accesscontrol.annotation.Authorize;
import com.ericsson.oss.pmic.dto.PaginatedList;
import com.ericsson.oss.pmic.dto.PersistenceTrackingState;
import com.ericsson.oss.pmic.dto.SubscriptionPersistenceTrackingStatus;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.node.enums.NodeLicenseFeatureState;
import com.ericsson.oss.pmic.dto.pmjob.PmJob;
import com.ericsson.oss.pmic.dto.subscription.CellRelationSubscription;
import com.ericsson.oss.pmic.dto.subscription.CellTrafficSubscription;
import com.ericsson.oss.pmic.dto.subscription.ContinuousCellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.CtumSubscription;
import com.ericsson.oss.pmic.dto.subscription.GpehSubscription;
import com.ericsson.oss.pmic.dto.subscription.ResSubscription;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.UETraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CellInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.subscription.capability.SubscriptionCapabilityReader;
import com.ericsson.oss.pmic.util.CollectionUtil;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterLocal;
import com.ericsson.oss.services.pm.adjuster.SubscriptionDataAdjusterQualifier;
import com.ericsson.oss.services.pm.common.logging.PMICLog;
import com.ericsson.oss.services.pm.common.logging.PMICLog.Operation;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.exception.ServiceException;
import com.ericsson.oss.services.pm.exception.SubscriptionNotFoundDataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.generic.PmJobService;
import com.ericsson.oss.services.pm.generic.ScannerService;
import com.ericsson.oss.services.pm.initiation.api.CounterConflictService;
import com.ericsson.oss.services.pm.initiation.api.ReadPMICConfigurationLocal;
import com.ericsson.oss.services.pm.initiation.api.SubscriptionRestPollingHelperLocal;
import com.ericsson.oss.services.pm.initiation.common.ResponseData;
import com.ericsson.oss.services.pm.initiation.common.accesscontrol.AccessControlActions;
import com.ericsson.oss.services.pm.initiation.common.accesscontrol.AccessControlResources;
import com.ericsson.oss.services.pm.initiation.common.accesscontrol.AccessControlUtil;
import com.ericsson.oss.services.pm.initiation.ctum.CtumSubscriptionServiceLocal;
import com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource.dto.InitiationRequest;
import com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource.dto.InitiationResponse;
import com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource.dto.NodeAttributes;
import com.ericsson.oss.services.pm.initiation.rest.response.ConflictingNodeCounterInfo;
import com.ericsson.oss.services.pm.services.exception.CannotGetConflictingCountersException;
import com.ericsson.oss.services.pm.services.exception.ConcurrentSubscriptionUpdateException;
import com.ericsson.oss.services.pm.services.exception.ConfigurationParameterException;
import com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionOperationException;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;
import com.ericsson.services.pm.initiation.restful.api.PmMetaDataLookupLocal;

/**
 * The Subscription resource, used for subscription CRUD actions.
 */
@Path("/subscription")
@Stateless
public class SubscriptionResource {

    public static final String NAME = "Name";
    public static final String TYPE = "Type";
    public static final String ADMIN_STATUS = "Status";

    private static final String SUBSCRIPTION_NOT_FOUND_FORMATTER = "Subscription %s not found.";
    private static final String GETTING_SUBSCRIPTION_ID_FORMATTER = "Getting subscription with id {}.";
    private static final String GETTING_SUBSCRIPTION_ID_TO_EXPORT_FORMATTER = "Getting subscription with id {} to export.";
    private static final String MAX_NO_OF_CBS_ALLOWED = "MAXNOOFCBSALLOWED";
    private static final String VALIDATION_FAILED =
            "Validation of subscription {} failed. Continuing as the validation also filtered " + "counters/events.";
    private static final String ERR_VALIDATION_EXC = "Validation exception";

    @Inject
    private Logger log;
    @Inject
    private NodeService nodeService;
    @Inject
    private PmJobService pmJobService;
    @Context
    private HttpHeaders httpHeaders;
    @Inject
    private ScannerService scannerService;
    @Inject
    private AccessControlUtil accessControlUtil;
    @Inject
    private CtumSubscriptionServiceLocal ctumService;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private CounterConflictService counterConflictService;
    @Inject
    private ReadPMICConfigurationLocal readConfiguration;
    @Inject
    private SubscriptionRestPollingHelperLocal pollingHelper;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;
    @Inject
    @SubscriptionDataAdjusterQualifier
    private SubscriptionDataAdjusterLocal<Subscription> subscriptionDataAdjuster;
    @Inject
    private SubscriptionCapabilityReader subscriptionCapabilityReader;
    @Inject
    private PmMetaDataLookupLocal pmMetaDataLookupLocal;
    /**
     * Post method for create subscription
     *
     * @param subscription
     *         Subscription object
     *
     * @return Response object
     * @throws DataAccessException
     *         - if there is an error during access to DPS
     * @throws ServiceException
     *         - ServiceException
     */
    @POST
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Formatted
    @ImportPreProcess
    public Response createSingleSubscription(final Subscription subscription) throws DataAccessException, ServiceException {
        final String subscriptionName = subscription == null ? "" : subscription.getName();
        systemRecorder.commandStarted(POST_SUBSCRIPTION, subscriptionName, "POST started for subscription: %s", subscriptionName);
        final ResponseData response = new ResponseData();
        if (subscription == null) {
            systemRecorder.error(NO_SUBSCRIPTION_IN_REQUEST, POST_SUBSCRIPTION.getSource(), subscriptionName, Operation.CREATE);
            systemRecorder.commandFinishedError(POST_SUBSCRIPTION, subscriptionName, NO_SUBSCRIPTION_IN_REQUEST.getMessage());
            response.setCode(Status.BAD_REQUEST);
            response.setError(NO_SUBSCRIPTION_IN_REQUEST.getMessage());
            return Response.status(response.getCode()).entity(response).build();
        }
        try {
            accessControlUtil.checkSubscriptionTypeRoleBasedAccess(subscription.getType(), AccessControlActions.CREATE);
            validateSubscriptionCreate(subscription);
            subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription);

            if (subscription instanceof UETraceSubscription) {
                ((UETraceSubscription) subscription).setTraceReference(
                        subscriptionReadOperationService.generateUniqueTraceReference(((UETraceSubscription) subscription).getOutputMode()));
            }
            final String trackerId = subscriptionReadOperationService
                    .generateTrackingId(new SubscriptionPersistenceTrackingStatus(null, PersistenceTrackingState.PERSISTING));
            subscriptionWriteOperationService.saveOrUpdate(subscription, trackerId);
            log.debug("\n\nSubscriptionScheduleStartTime {} ", subscription.getScheduleInfo().getStartDateTime());
            log.debug("\n\nSubscriptionScheduleEndTime {}", subscription.getScheduleInfo().getEndDateTime());
            systemRecorder.commandFinishedSuccess(POST_SUBSCRIPTION, subscriptionName, "Started persisting subscription %s ", subscriptionName);
            return pollingHelper.getSubscriptionCreationResponse(trackerId);
        } catch (final Exception e) {
            recordCommandFinishedError(e, POST_SUBSCRIPTION, subscription.getName());
            throw e;
        }
    }

    private void validateSubscriptionCreate(final Subscription subscription) throws RetryServiceException, DataAccessException {
        if (isSystemDefinedSubscriptionReservedName(subscription.getName())) {
            throw new RetryServiceException("Cannot use Reserved Subscription Name " + subscription.getName());
        }

        if (subscription instanceof ContinuousCellTraceSubscription || subscription instanceof CtumSubscription) {
            throw new RetryServiceException("It is not allowed to create requested type of subscription manually.");
        }

        if (subscriptionReadOperationService.existsByFdn(subscription.getFdn())) {
            throw new RetryServiceException(String.format("Subscription with name '%s' already exists.", subscription.getName()));
        }

        checkSubscriptionNameAlreadyExists(subscription);

        if (httpHeaders != null && httpHeaders.getRequestHeader("X-isImported") != null) {
            subscription.setIsImported(Boolean.parseBoolean(httpHeaders.getRequestHeader("X-isImported").get(0)));
        }
    }

    private boolean isSystemDefinedSubscriptionReservedName(final String subscriptionName) {
        return subscriptionCapabilityReader.getAllSystemDefinedSubscriptionNames().contains(subscriptionName);
    }

    /**
     * Put method for updating the subscription
     *
     * @param subscriptionId
     *         -id of subscription to get counter conflicts for
     * @param subscription
     *         Subscription object
     *
     * @return Response response
     * @throws DataAccessException
     *         - If an exception is thrown while communicating with database
     * @throws ServiceException
     *         - if update validation fails. For example: subscription does not exist, subscription state is invalid for update operation or
     *         subscription was already modified by another user.
     */
    @PUT
    @Path("/{id}")
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Formatted
    public Response updateSingleSubscription(@PathParam("id") final String subscriptionId, final Subscription subscription)
            throws DataAccessException, ServiceException {
        systemRecorder.commandStarted(PUT_SUBSCRIPTION, subscriptionId, "PUT started for subscription: %s", subscriptionId);
        try {
            if (subscription == null) {
                systemRecorder.error(NO_SUBSCRIPTION_IN_REQUEST, PUT_SUBSCRIPTION.getSource(), subscriptionId, Operation.EDIT);
                systemRecorder.commandFinishedError(PUT_SUBSCRIPTION, subscriptionId, NO_SUBSCRIPTION_IN_REQUEST.getMessage());
                final ResponseData response = new ResponseData();
                response.setCode(Status.BAD_REQUEST);
                response.setError(NO_SUBSCRIPTION_IN_REQUEST.getMessage());
                return Response.status(response.getCode()).entity(response).build();
            }
            accessControlUtil.checkSubscriptionTypeRoleBasedAccess(subscription.getType(), AccessControlActions.UPDATE);
            subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription);

            final String trackerId = subscriptionReadOperationService
                    .generateTrackingId(new SubscriptionPersistenceTrackingStatus(null, subscription.getId(), PersistenceTrackingState.UPDATING));
            subscriptionWriteOperationService.saveOrUpdate(subscription, trackerId);
            systemRecorder.commandFinishedSuccess(PUT_SUBSCRIPTION, subscription.getName(), "Started updating subscription: %s", subscriptionId);
            return pollingHelper.getSubscriptionUpdateResponse(trackerId);
        } catch (final Exception e) {
            recordCommandFinishedError(e, PUT_SUBSCRIPTION, subscriptionId);
            throw e;
        }
    }

    /**
     * Delete method for deleting subscription
     *
     * @param subscriptionId
     *         -id of subscription to get counter conflicts for
     *
     * @return Response response
     * @throws RetryServiceException
     *         -will throw a PMIC Exception if there is any error during subscription delete
     * @throws DataAccessException
     *         - if an exception fro database is thrown
     * @throws InvalidSubscriptionOperationException
     *         - if subscription could not be deleted because admin state is not INACTIVE
     */
    @DELETE
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response deleteSingleSubscription(@PathParam("id") final String subscriptionId)
            throws RetryServiceException, DataAccessException, InvalidSubscriptionOperationException {
        try {
            systemRecorder.commandStarted(DELETE_SUBSCRIPTION, subscriptionId, "Deleting subscription: %s", subscriptionId);
            final Subscription subscription = subscriptionReadOperationService.findByIdWithRetry(Long.parseLong(subscriptionId), false);
            if (subscription == null) {
                return getNotFoundResponseForNullResult(subscriptionId);
            }
            accessControlUtil.checkSubscriptionTypeRoleBasedAccess(subscription.getType(), AccessControlActions.DELETE);
            if (Subscription.isPmJobSupported(subscription.getType())) {
                final List<PmJob> pmJobs = pmJobService.findAllBySubscriptionId(Long.valueOf(subscriptionId));
                for (final PmJob pmJob : pmJobs) {
                    pmJob.setSubscriptionId(Subscription.UNKNOWN_SUBSCRIPTION_ID);
                }
                pmJobService.saveOrUpdateWithRetry(pmJobs);
            }
            subscriptionWriteOperationService.deleteWithRetry(subscription);
            systemRecorder
                    .commandFinishedSuccess(DELETE_SUBSCRIPTION, subscription.getName(), "Successfully Deleted Subscription: %s", subscriptionId);
            return Response.status(Status.OK).entity(subscription).build();
        } catch (final Exception exception) {
            systemRecorder.error(INTERNAL_SERVER_EXCEPTION, DELETE_SUBSCRIPTION.getSource(), subscriptionId, Operation.DELETE);
            recordCommandFinishedError(exception, DELETE_SUBSCRIPTION, subscriptionId);
            throw exception;
        }
    }

    /**
     * Get method for checking status of subscription
     *
     * @param trackerId
     *         - the tracker id for the subscription
     *
     * @return Response status
     */
    @GET
    @Path("/status/{trackerId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ReadOnly
    @InvokeInTransaction @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response getStatus(@PathParam("trackerId") final String trackerId) {
        log.debug("Checking state of subscription with trackerId {}.", trackerId);
        return pollingHelper.getSubscriptionUpdateResponse(trackerId);
    }

    /**
     * Get method for fetching subscription
     *
     * @param subscriptionId
     *         -id of subscription to get counter conflicts for
     *
     * @return Response response
     * @throws DataAccessException
     *         - if an exception is thrown from the DB while counting CBS subscriptions
     * @throws ServiceException
     *         - if an exception is thrown from the service layer.
     */
    @GET
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_JSON})
    @JsonView(com.ericsson.oss.pmic.dto.subscription.SubscriptionView.DefaultView.class)
    @ReadOnly
    @InvokeInTransaction @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response get(@PathParam("id") final String subscriptionId) throws DataAccessException, ServiceException {
        log.info(GETTING_SUBSCRIPTION_ID_FORMATTER, subscriptionId);
        systemRecorder.commandStarted(GET_SUBSCRIPTION, subscriptionId, "Read subscription object of : %s", subscriptionId);
        final long start = System.currentTimeMillis();
        String duplicateId = subscriptionId;
        final boolean isDuplicateFlow = subscriptionId.contains("duplicate");
        if (isDuplicateFlow) {
            duplicateId = subscriptionId.substring(0, subscriptionId.indexOf("_duplicate"));
        }
        final Subscription subscription = subscriptionReadOperationService.findByIdWithRetry(Long.parseLong(duplicateId), true);
        if (subscription == null) {
            return getNotFoundResponseForNullResult(subscriptionId);
        }
        try {
            subscriptionDataAdjuster.correctSubscriptionData(subscription);
        } catch (final RuntimeDataAccessException e) {
            log.error(VALIDATION_FAILED, subscription.getId(), e.getMessage());
            log.info(ERR_VALIDATION_EXC, e);
        }
        if (subscription instanceof ResourceSubscription && ((ResourceSubscription) subscription).isCbs() && isDuplicateFlow) {
            final String maxNoOfCBSAllowed = readConfiguration.getConfigParamValue(MAX_NO_OF_CBS_ALLOWED);
            if (subscriptionReadOperationService.countCriteriaBasedSubscriptions() >= Integer.parseInt(maxNoOfCBSAllowed)) {
                return Response.serverError().entity(new ResponseData(Response.Status.INTERNAL_SERVER_ERROR, CBS_ALLOWED_EXCEEDED)).build();
            }
        } else if (subscription instanceof CtumSubscription) {
            ((CtumSubscription) subscription).setNodes(ctumService.getSupportedNodesForCtumWithPmFunctionOnAndNonEmptyOssModelIdentity());
        }
        systemRecorder
                .commandFinishedSuccess(GET_SUBSCRIPTION, subscription.getName(), "Successfully read Subscription: %s", subscriptionId);
        log.debug("Subscription with id {} found, converting to output format. Time taken: {}.", subscriptionId, System.currentTimeMillis() - start);
        return Response.status(Status.OK).entity(subscription).build();
    }

    /**
     * Get method for fetching subscription
     *
     * @param subscriptionId
     *         -id of subscription to get counter conflicts for
     *
     * @return Response response
     * @throws DataAccessException
     *         - if an exception is thrown from the DB while counting CBS subscriptions
     * @throws ServiceException
     *         - if an exception is thrown from the service layer.
     */
    @GET
    @Path("/exportsubscription/{id}")
    @Produces({MediaType.APPLICATION_JSON})
    @JsonView(com.ericsson.oss.pmic.dto.subscription.SubscriptionView.ExportView.class)
    @ReadOnly
    @InvokeInTransaction @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response export(@PathParam("id") final String subscriptionId) throws DataAccessException, ServiceException {
        log.debug(GETTING_SUBSCRIPTION_ID_TO_EXPORT_FORMATTER, subscriptionId);
        final long start = System.currentTimeMillis();
        String duplicateId = subscriptionId;
        final boolean isDuplicateFlow = subscriptionId.contains("duplicate");
        if (isDuplicateFlow) {
            duplicateId = subscriptionId.substring(0, subscriptionId.indexOf("_duplicate"));
        }
        final Subscription subscription = subscriptionReadOperationService.findByIdWithRetry(Long.parseLong(duplicateId), true);
        if (subscription == null) {
            return getNotFoundResponseForNullResult(subscriptionId);
        }
        try {
            subscription.setIsExported(true);
            subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription);
        } catch (final ValidationException | DataAccessException | RuntimeDataAccessException e) {
            log.error(VALIDATION_FAILED, subscription.getId(), e.getMessage());
            log.info(ERR_VALIDATION_EXC, e);
            throw e;
        }
        if (subscription instanceof ResourceSubscription && ((ResourceSubscription) subscription).isCbs() && isDuplicateFlow) {
            final String maxNoOfCBSAllowed = readConfiguration.getConfigParamValue(MAX_NO_OF_CBS_ALLOWED);
            if (subscriptionReadOperationService.countCriteriaBasedSubscriptions() >= Integer.parseInt(maxNoOfCBSAllowed)) {
                return Response.serverError().entity(new ResponseData(Response.Status.INTERNAL_SERVER_ERROR, CBS_ALLOWED_EXCEEDED)).build();
            }
        } else if (subscription instanceof CtumSubscription) {
            ((CtumSubscription) subscription).setNodes(ctumService.getSupportedNodesForCtumWithPmFunctionOnAndNonEmptyOssModelIdentity());
        }
        log.debug("Subscription with id {} found, converting to output format. Time taken: {}.", subscriptionId, System.currentTimeMillis() - start);
        return Response.status(Status.OK).entity(subscription).build();
    }

    /**
     * Get back a list of conflicting counters in this Subscription. If any of the counters in this Subscription are already active on a node in this
     * Subscription, report back those counters
     *
     * @param subscriptionId
     *         -id of subscription to get counter conflicts for
     *
     * @return - Response
     * @throws RetryServiceException
     *         - will throw a PMIC Exception if there is any error during getting a counter conflicts
     * @throws DataAccessException
     *         - if an exception from Database is thrown
     * @throws CannotGetConflictingCountersException
     *         if there is any error during getting a counter conflicts
     */
    @GET
    @Path("/{id}/counterconflicts")
    @Produces({MediaType.APPLICATION_JSON})
    @ReadOnly
    @InvokeInTransaction @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response getCounterConflicts(@PathParam("id") final String subscriptionId)
            throws RetryServiceException, DataAccessException, CannotGetConflictingCountersException {
        log.debug(GETTING_SUBSCRIPTION_ID_FORMATTER, subscriptionId);
        final ConflictingNodeCounterInfo conflictingCounters;
        try {
            final StatisticalSubscription subscription = (StatisticalSubscription) subscriptionReadOperationService
                    .findByIdWithRetry(Long.valueOf(subscriptionId), true);
            if (subscription == null) {
                return getNotFoundResponseForNullResult(subscriptionId);
            }
            subscriptionReadOperationService.adjustPfmSubscriptionData(subscription);
            final long startTime = System.currentTimeMillis();
            conflictingCounters = counterConflictService.getConflictingCountersInSubscription(subscription);
            log.info("getCounterConflicts Processing Time = {}", System.currentTimeMillis() - startTime);
        } catch (final ClassCastException exception) {
            throw new RetryServiceException("Subscription with id " + subscriptionId + "is not a Subscription containing counters");
        }
        if (conflictingCounters == null) {
            return getNotFoundResponseForNullResult(subscriptionId);
        }
        return Response.status(Status.OK).entity(conflictingCounters).build();
    }

    private void validateAndAdjustSubscriptionData(Subscription subscription) {
        try {
            subscriptionReadOperationService.validateAndAdjustSubscriptionData(subscription);
        } catch (final ValidationException | PfmDataException | DataAccessException | RuntimeDataAccessException e) {
            log.error(VALIDATION_FAILED, subscription.getId(), e.getMessage());
            log.info(ERR_VALIDATION_EXC, e);
        }
    }

    private Response getNotFoundResponseForNullResult(final String subscriptionId) {
        final ResponseData response = new ResponseData();
        response.setCode(Status.NOT_FOUND);
        response.setError(String.format(SUBSCRIPTION_NOT_FOUND_FORMATTER, subscriptionId));
        return Response.status(Status.NOT_FOUND).entity(response).build();
    }

    /**
     * Get method for getting list of subscriptions
     *
     * @param names
     *         - filter of subscription names
     * @param types
     *         - filter of subscription types
     * @param adminStatuses
     *         - filter of subscription admin statutes
     *
     * @return Response subscription filtered list
     * @throws ServiceException
     *         - if a database exception is thrown.
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @ReadOnly
    @InvokeInTransaction @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response getSubscriptionFilteredList(@QueryParam(NAME) final List<String> names, @QueryParam(TYPE) final List<String> types,
                                                @QueryParam(ADMIN_STATUS) final List<String> adminStatuses) throws ServiceException {
        return Response.status(Status.OK).entity(subscriptionReadOperationService.findAllFilteredBy(names, types, adminStatuses)).build();
    }

    /**
     * Get method for getting list of subscriptions having ebsCounters.
     *
     * @param type
     *         - filter of subscription types
     * @param adminStatus
     *         - filter of subscription admin statutes
     *
     * @return Response subscription filtered list-of subscriptions having ebsCounters, error if the type is not celltrace or ebm
     * @throws DataAccessException
     *         - if a database exception is thrown.
     */
    @GET
    @Path("/ebs")
    @Produces({MediaType.APPLICATION_JSON})
    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response getEbsSubscriptionsFilteredList(@QueryParam(TYPE) final String type, @QueryParam(ADMIN_STATUS) final String adminStatus)
            throws DataAccessException {
        final Map<String, List<Object>> attributes = new HashMap<>(2);
        if (adminStatus != null && !adminStatus.isEmpty()) {
            attributes.put(administrationState.name(), Collections.<Object>singletonList(adminStatus));
        }

        if (SubscriptionType.CELLTRACE.name().equals(type)) {
            final List<Object> celltraceCategories = new ArrayList<>();
            celltraceCategories.add(CellTraceCategory.CELLTRACE_AND_EBSL_STREAM.name());
            celltraceCategories.add(CellTraceCategory.CELLTRACE_AND_EBSL_FILE.name());
            celltraceCategories.add(CellTraceCategory.EBSL_STREAM.name());
            celltraceCategories.add(CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_FILE.name());
            celltraceCategories.add(CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM.name());
            celltraceCategories.add(CellTraceCategory.NRAN_EBSN_STREAM.name());
            attributes.put(cellTraceCategory.name(), celltraceCategories);
        }

        final List<Subscription> allSubscriptions;
        try {
            allSubscriptions = subscriptionReadOperationService
                    .findAllWithSubscriptionTypeAndMatchingAttributes(SubscriptionType.fromString(type), attributes, true);
        } catch (final IllegalArgumentException e) {
            final ResponseData response = new ResponseData();
            response.setCode(Status.FORBIDDEN);
            response.setError("No valid subscription type.");
            return Response.status(Status.FORBIDDEN).entity(response).build();
        }

        if (allSubscriptions.isEmpty()) {
            final ResponseData response = new ResponseData();
            response.setCode(Status.NO_CONTENT);
            response.setError("No subscriptions found.");
            return Response.status(Status.NO_CONTENT).entity(response).build();
        }
        return Response.status(Status.OK).entity(allSubscriptions).build();
    }

    /**
     * Get method for getting list of subscriptions which contains a MO with given fdn.
     *
     * @param names
     *         - filter of subscription names
     * @param type
     *         - filter of subscription type
     * @param adminStatuses
     *         - filter of subscription admin statutes
     *
     * @return Response subscription filtered list with managed element fd ns
     * @throws DataAccessException
     *         - if a database exception is thrown.
     */
    @GET
    @Path("/withMeFdn")
    @Produces({MediaType.APPLICATION_JSON})
    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response getSubscriptionFilteredListWithManagedElementFDNs(@QueryParam(NAME) final List<String> names, @QueryParam(TYPE) final String type,
                                                                      @QueryParam(ADMIN_STATUS) final List<String> adminStatuses)
            throws DataAccessException {
        if (!"EBM".equals(type)) {
            final ResponseData response = new ResponseData();
            response.setCode(Status.FORBIDDEN);
            response.setError("Unsupported subscription type for this REST endpoint.");
            return Response.status(Status.FORBIDDEN).entity(response).build();
        }

        final List<String> filteredNames = CollectionUtil.filterNullOrEmptyElements(names);
        final String[] subscriptionNames = getSubscriptionNamesAsArray(filteredNames);

        final List<String> filteredAdminStates = CollectionUtil.filterNullOrEmptyElements(adminStatuses);
        final AdministrationState[] administrationStates = getAdministrationStatesAsArray(filteredAdminStates);

        final SubscriptionType[] subscriptionTypes = {SubscriptionType.fromString(type)};

        final List<Subscription> subscriptions = subscriptionReadOperationService
                .findAllByNameAndSubscriptionTypeAndAdministrationState(subscriptionNames, subscriptionTypes, administrationStates, true);
        if (!subscriptions.isEmpty()) {
            for (final Subscription subscription : subscriptions) {
                if (subscription instanceof ResourceSubscription) {
                    final List<Node> nodes = ((ResourceSubscription) subscription).getNodes();
                    nodeService.enrichNodesWithManagedElementFdn(nodes);
                    ((ResourceSubscription) subscription).setNodes(nodes);
                }
            }
            return Response.ok().entity(subscriptions).build();
        }
        final ResponseData response = new ResponseData();
        response.setCode(Status.NOT_FOUND);
        response.setError("No subscriptions found.");
        return Response.status(Status.NOT_FOUND).entity(response).build();
    }

    private String[] getSubscriptionNamesAsArray(final List<String> filteredNames) {
        if (filteredNames != null && !filteredNames.isEmpty()) {
            return filteredNames.toArray(new String[filteredNames.size()]);
        }
        return new String[]{};
    }

    private AdministrationState[] getAdministrationStatesAsArray(final List<String> filteredAdminStates) {
        if (filteredAdminStates != null && !filteredAdminStates.isEmpty()) {
            return AdministrationState.fromStringsAsArray(filteredAdminStates);
        }
        return new AdministrationState[]{};
    }

    /**
     * Checks Subscription for counter conflicts
     * @deprecated no longer valid / not to be used
     * @param subscriptionId
     *         -id of subscription to check if it has any counter conflicts
     *
     * @return - Response
     * @throws RetryServiceException
     *         - if there is any error during checking counter conflicts
     * @throws CannotGetConflictingCountersException
     *         - if there is any error during checking counter conflicts
     */
    @Deprecated
    @GET
    @Path("/{id}/hasanycounterconflict")
    @Produces({MediaType.APPLICATION_JSON})
    @ReadOnly
    @InvokeInTransaction @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response hasAnyCounterConflict(@PathParam("id") final String subscriptionId)
            throws RetryServiceException, CannotGetConflictingCountersException {
        log.debug(GETTING_SUBSCRIPTION_ID_FORMATTER, subscriptionId);
        try {
            final StatisticalSubscription subscription = (StatisticalSubscription) subscriptionReadOperationService
                    .findByIdWithRetry(Long.valueOf(subscriptionId), true);
            if (subscription == null) {
                return getNotFoundResponseForNullResult(subscriptionId);
            }
            validateAndAdjustSubscriptionData(subscription);
            return Response.status(Status.OK).entity(counterConflictService.hasAnyCounterConflict(subscription)).build();
        } catch (final DataAccessException exception) {
            return getNotFoundResponseForNullResult(subscriptionId);
        } catch (final ClassCastException exception) {
            return Response.status(Status.OK).entity(false).build();

        }
    }

    /**
     * Checks Subscription for counters conflicts and returns list of Conflicting Subscriptions for every selected counter
     * @deprecated no longer valid / not to be used
     * @param subscriptionId
     *         -id of subscription to check if it has any counter conflicts
     *
     * @return - Response
     * @throws RetryServiceException
     *         - if there is any error during checking counter conflicts
     * @throws CannotGetConflictingCountersException
     *         - if there is any error during checking counter conflicts
     */
    @Deprecated
    @GET
    @Path("/{id}/getconflictingsubscriptionsforcounters")
    @Produces({MediaType.APPLICATION_JSON})
    @ReadOnly
    @InvokeInTransaction @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response getConflictingSubscriptionsForCountersGet(@PathParam("id") final String subscriptionId)
            throws RetryServiceException, CannotGetConflictingCountersException {
        return this.getConflictingSubscriptionsForCountersInternal(subscriptionId, "GET");
    }

    /**
     * * Checks Subscription for counters conflicts and returns list of Conflicting Subscriptions for every selected counter using POST
     *
     * @param subscriptionId
     *         -id of subscription to check if it has any counter conflicts
     *
     * @return - Response
     * @throws RetryServiceException
     *         - if there is any error during checking counter conflicts
     * @throws CannotGetConflictingCountersException
     *         - if there is any error during checking counter conflicts
     */
    @POST
    @Path("/{id}/getconflictingsubscriptionsforcounters")
    @Produces({MediaType.APPLICATION_JSON})
    @ReadOnly
    @InvokeInTransaction @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response getConflictingSubscriptionsForCounters(@PathParam("id") final String subscriptionId)
            throws RetryServiceException, CannotGetConflictingCountersException {
        return this.getConflictingSubscriptionsForCountersInternal(subscriptionId, "POST");
    }

    private Response getConflictingSubscriptionsForCountersInternal(final String subscriptionId, final String mode)
            throws RetryServiceException, CannotGetConflictingCountersException {
        log.debug("Getting subscription with id {} using {}.", subscriptionId, mode);
        try {
            final StatisticalSubscription subscription = (StatisticalSubscription) subscriptionReadOperationService
                    .findByIdWithRetry(Long.valueOf(subscriptionId), true);
            if (subscription == null) {
                return getNotFoundResponseForNullResult(subscriptionId);
            }
            validateAndAdjustSubscriptionData(subscription);
            return Response.status(Response.Status.OK).entity(counterConflictService.getConflictingSubscriptionsForCounters(subscription)).build();

        } catch (final DataAccessException exception) {
            return getNotFoundResponseForNullResult(subscriptionId);
        } catch (final ClassCastException exception) {
            return Response.status(Response.Status.OK).entity(new HashMap<>()).build();
        }
    }

    /**
     * Checks Subscription for counters conflicts and return Detailed Report
     *
     * @param subscriptionId
     *         -id of subscription to check if it has any counter conflicts
     *
     * @return - Response
     * @throws RetryServiceException
     *         - if there is any error during checking counter conflicts
     * @throws CannotGetConflictingCountersException
     *         - if there is any error during checking counter conflicts
     */
    @POST
    @Path("/{id}/getcounterconflictsreport")
    @Produces({MediaType.APPLICATION_OCTET_STREAM})
    @ReadOnly
    @InvokeInTransaction @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response getCounterConflictsReport(@PathParam("id") final String subscriptionId)
            throws RetryServiceException, CannotGetConflictingCountersException {
        log.debug(GETTING_SUBSCRIPTION_ID_FORMATTER, subscriptionId);
        try {
            final StatisticalSubscription subscription = (StatisticalSubscription) subscriptionReadOperationService
                    .findByIdWithRetry(Long.valueOf(subscriptionId), true);
            if (subscription == null) {
                return getNotFoundResponseForNullResult(subscriptionId);
            }
            validateAndAdjustSubscriptionData(subscription);
            return Response.status(Response.Status.OK).entity(counterConflictService.getCounterConflictsReport(subscription)).build();
        } catch (final DataAccessException exception) {
            return getNotFoundResponseForNullResult(subscriptionId);
        } catch (final ClassCastException exception) {
            return Response.status(Response.Status.OK).entity(new HashMap<>()).build();
        }
    }

    /**
     * Get method for getting list of nodes from given subscription
     *
     * @param subscriptionId
     *         -id of subscription to get counter conflicts for
     * @param page
     *         Page number
     * @param pageSize
     *         page size
     *
     * @return - Response
     * @throws DataAccessException
     *         - if any exception from Data Access layer is thrown
     * @throws RetryServiceException
     *         - if the retry mechanism fails to catch valid exception
     */
    @GET
    @Path("/{id}/nodes")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response getNodesFromSubscription(@PathParam("id") final String subscriptionId, @QueryParam("page") final int page,
                                             @QueryParam("pageSize") final int pageSize) throws DataAccessException, RetryServiceException {
        try {
            final PaginatedList nodeList = subscriptionReadOperationService.getNodesFromSubscription(Long.valueOf(subscriptionId), page, pageSize);
            return Response.status(Status.OK).entity(nodeList).build();
        } catch (final SubscriptionNotFoundDataAccessException e) {
            log.error("SubscriptionNotFoundDataAccessException thrown while getting nodes for given subscription id [{}]: {}", subscriptionId,
                    e.getMessage());
            log.info("Exception thrown while getting nodes for given subscription id [{}]. Stacktrace : ", subscriptionId, e);
            final ResponseData responseData = new ResponseData(Status.NOT_FOUND, e.getMessage(), null);
            return Response.status(responseData.getCode()).entity(responseData).build();
        }
    }

    /**
     * https://enmapache.athtem.eei.ericsson.se/pm-service/rest/subscription/nodePmEnabled
     *
     * @param nodesAttributes
     *         - list of node attributes
     *
     * @return - Response
     */
    @POST
    @Path("/nodePmEnabled")
    @Produces({MediaType.APPLICATION_JSON})
    @Consumes({MediaType.APPLICATION_JSON})
    @ReadOnly
    @InvokeInTransaction @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response getNodeWithPMFunction(final List<NodeAttributes> nodesAttributes) {
        log.debug("Request parameter {}", nodesAttributes);
        if (nodesAttributes == null || nodesAttributes.isEmpty()) {
            return Response.status(Status.BAD_REQUEST).entity(NO_NODE_FDN_IN_REQUEST.getMessage()).build();
        }
        final List<NodeAttributes> nodeAttributesListWithPMFunction = new ArrayList<>(nodesAttributes);
        for (final NodeAttributes nodeAttributes : nodeAttributesListWithPMFunction) {
            if (nodeService.isPmFunctionEnabled(nodeAttributes.getFdn())) {
                nodeAttributes.setPmFunction(PM_FUNCTION_ON);
            } else {
                nodeAttributes.setPmFunction(PM_FUNCTION_OFF);
            }
        }
        log.debug("Node with pmFunction {}", nodeAttributesListWithPMFunction);
        return Response.status(Status.OK).entity(nodeAttributesListWithPMFunction).build();
    }

    /**
     * Activate response.
     *
     * @param subscriptionId
     *         the subscription id
     * @param initiationRequest
     *         the initiation request
     *
     * @return the response
     * @throws DataAccessException
     *         - for Database access related exception
     * @throws ServiceException
     *         - if validation fails or any number of service exception
     * @throws RetryServiceException
     *         - if subscription with id could not be found and retry interceptor didn't know how to proceed.
     */
    @POST
    @Path("/{id}/activate")
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public Response activate(@PathParam("id") final long subscriptionId, final InitiationRequest initiationRequest)
            throws DataAccessException, ServiceException {
        systemRecorder
                .commandStarted(ACTIVATE_SUBSCRIPTION, String.valueOf(subscriptionId), "Activating Subscription %d. Request: %s.", subscriptionId,
                        initiationRequest);
        try {
            final Subscription subscription = subscriptionReadOperationService.findByIdWithRetry(subscriptionId, true);
            if (subscription == null) {
                return getNotFoundResponseForNullResult(String.valueOf(subscriptionId));
            }
            subscriptionReadOperationService.adjustPfmSubscriptionData(subscription);
            accessControlUtil.checkSubscriptionTypeRoleBasedAccess(subscription.getType(), AccessControlActions.EXECUTE);
            final Subscription updatedSubscription = subscriptionWriteOperationService.activate(subscription, initiationRequest.getPersistenceTime());
            final InitiationResponse initiationResponse = new InitiationResponse().buildFromSubscription(updatedSubscription);
            systemRecorder.commandFinishedSuccess(ACTIVATE_SUBSCRIPTION, String.valueOf(subscriptionId),
                    "Subscription %s with id %d executed activation command successfully. Request: %s. Response: %s.", subscription.getName(),
                    subscriptionId, initiationRequest, initiationResponse);
            return Response.status(Status.OK).entity(initiationResponse).build();
        } catch (final Exception e) {
            recordCommandFinishedError(e, ACTIVATE_SUBSCRIPTION, String.valueOf(subscriptionId));
            throw e;
        }
    }

    /**
     * Deactivate response.
     *
     * @param subscriptionId
     *         the subscription id
     * @param initiationRequest
     *         the initiation request
     *
     * @return the response
     * @throws RetryServiceException
     *         - if subscription with id could not be found and retry interceptor didn't know how to proceed.
     * @throws DataAccessException
     *         - if any database exception is thrown
     * @throws InvalidSubscriptionOperationException
     *         - if subscription cannot be deactivated
     * @throws ConcurrentSubscriptionUpdateException
     *         - if subscription's persistence time is not the same as the persistence time extracted from the payload
     */
    @POST
    @Path("/{id}/deactivate")
    @Produces({MediaType.APPLICATION_JSON})
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response deactivate(@PathParam("id") final long subscriptionId, final InitiationRequest initiationRequest)
            throws RetryServiceException, DataAccessException, InvalidSubscriptionOperationException, ConcurrentSubscriptionUpdateException {
        systemRecorder
                .commandStarted(DEACTIVATE_SUBSCRIPTION, String.valueOf(subscriptionId), "Deactivating Subscription %d. Request: %s.", subscriptionId,
                        initiationRequest);
        try {
            final Subscription subscription = subscriptionReadOperationService.findByIdWithRetry(subscriptionId, false);
            if (subscription == null) {
                return getNotFoundResponseForNullResult(String.valueOf(subscriptionId));
            }
            accessControlUtil.checkSubscriptionTypeRoleBasedAccess(subscription.getType(), AccessControlActions.EXECUTE);
            final Subscription updatedSubscription = subscriptionWriteOperationService
                    .deactivate(subscription, initiationRequest.getPersistenceTime());
            final InitiationResponse initiationResponse = new InitiationResponse().buildFromSubscription(updatedSubscription);
            systemRecorder.commandFinishedSuccess(DEACTIVATE_SUBSCRIPTION, String.valueOf(subscriptionId),
                    "Subscription %s with id %d executed deactivation command successfully. Request: %s. Response: %s.", subscription.getName(),
                    subscriptionId, initiationRequest, initiationResponse);
            return Response.status(Status.OK).entity(initiationResponse).build();
        } catch (final Exception e) {
            recordCommandFinishedError(e, DEACTIVATE_SUBSCRIPTION, String.valueOf(subscriptionId));
            throw e;
        }
    }

    /**
     * Record command finished error.
     *
     * @param exception
     *         the exception thrown
     * @param command
     *         the PMIC log command
     * @param resource
     *         the resource
     */
    protected void recordCommandFinishedError(final Exception exception, final PMICLog.Command command, final String resource) {
        final String message;
        if (exception instanceof EJBException) {
            final EJBException ejbException = (EJBException) exception;
            if (ejbException.getCausedByException() instanceof SecurityViolationException) {
                message = NO_PERMISSION_ACCESS;
            } else if (ejbException.getCausedByException() != null) {
                message = ejbException.getCausedByException().getMessage();
            } else {
                message = exception.getMessage();
            }
        } else if (exception instanceof SecurityViolationException) {
            message = NO_PERMISSION_ACCESS;
        } else {
            message = exception.getMessage();
        }
        systemRecorder.commandFinishedError(command, resource, message);
    }

    /**
     * Gets subscription id by name.
     *
     * @param subscriptionName
     *         the subscription name
     *
     * @return the subscription id by name
     */
    @GET
    @Path("/getIdByName/{subscriptionName}")
    @Produces({MediaType.APPLICATION_JSON})
    @ReadOnly
    @InvokeInTransaction @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response getSubscriptionIdByName(@PathParam("subscriptionName") final String subscriptionName) {
        systemRecorder.commandStarted(GET_SUBSCRIPTION, subscriptionName, "Read Subscription by subscriptionName %s.", subscriptionName);
        try {
            final List<Long> idsByExactName = subscriptionReadOperationService.findIdsByExactName(subscriptionName);
            if (idsByExactName.isEmpty()) {
                return Response.ok().entity(UNKNOWN_SUBSCRIPTION_ID_AS_STRING).build();
            } else if (idsByExactName.size() > 1) {
                log.warn("REST endpoint /getIdByName/<{}> found {} subscriptions with this name. Returning the first in the list with id: {}",
                        subscriptionName, idsByExactName.size(), idsByExactName.get(0));
            }
            systemRecorder
                    .commandStarted(GET_SUBSCRIPTION, subscriptionName, "successfully read Subscription by subscriptionName %s.", subscriptionName);
            return Response.ok().entity(String.valueOf(idsByExactName.get(0))).build();
        } catch (final DataAccessException e) {
            log.error("Exception {} thrown while finding subscription id by name [{}]: ", subscriptionName, e.getMessage());
            log.info("Exception thrown while finding subscription id by name [" + subscriptionName + "]. Stacktrace: ", e);
            final ResponseData response = new ResponseData();
            response.setCode(Status.INTERNAL_SERVER_ERROR);
            response.setError("Failed to find subscription ID by name " + subscriptionName);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(response).build();
        }
    }

    /**
     * Check cbs allowed response.
     *
     * @return the response
     * @throws ConfigurationParameterException
     *         - if access to configuration parameter is not available
     * @throws DataAccessException
     *         - if an exception is thrown from the DB while counting CBS subscriptions
     * @throws RuntimeDataAccessException
     *         - if an exception is thrown from the DB while counting CBS subscriptions
     */
    @GET
    @Path("/checkCbsAllowed")
    @Produces({MediaType.APPLICATION_JSON})
    @ReadOnly
    @InvokeInTransaction @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response checkCbsAllowed() throws ConfigurationParameterException, DataAccessException {
        final String maxNoOfCBSAllowed = readConfiguration.getConfigParamValue(MAX_NO_OF_CBS_ALLOWED);
        if (subscriptionReadOperationService.countCriteriaBasedSubscriptions() >= Integer.parseInt(maxNoOfCBSAllowed)) {
            return Response.serverError().entity(new ResponseData(Response.Status.INTERNAL_SERVER_ERROR, CBS_ALLOWED_EXCEEDED)).build();
        }
        return Response.ok().build();
    }

    /**
     * Checks Subscription for cells
     *
     * @param subscriptionId
     *         -id of subscription to check if it has any cells available
     *
     * @return - Response
     * @throws DataAccessException
     *         - if an exception is thrown from the DB while accessing DB
     * @throws RuntimeDataAccessException
     *         - if a retriable exception it thrown from DB while accessing DB
     */
    @GET
    @Path("/{id}/hasnodesandcellsconflict")
    @Produces({MediaType.APPLICATION_JSON})
    @ReadOnly
    @InvokeInTransaction @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response hasNodesAndCellsConflict(@PathParam("id") final String subscriptionId) throws DataAccessException {
        log.debug(GETTING_SUBSCRIPTION_ID_FORMATTER, subscriptionId);
        final Subscription sub = subscriptionReadOperationService.findOneById(Long.valueOf(subscriptionId), true);
        if (sub == null) {
            final ResponseData response = new ResponseData();
            response.setCode(Status.NOT_FOUND);
            response.setError(String.format(SUBSCRIPTION_NOT_FOUND_FORMATTER, subscriptionId));
            return Response.status(Status.NOT_FOUND).entity(response).build();
        }
        return Response.status(Status.OK).entity(!doesEachNodeHaveAtLeastOneCellInfo(sub)).build();
    }

    /**
     * Check whether the passed list of nodes has the feature enabled corresponds to the subscription type. The nodes which feature is not enabled
     * will be returned to UI.
     *
     * @param subscriptionType
     *         subscription type for which the feature state need to be checked. This parameter will be used to identify the feature mo name.
     * @param nodes
     *         The nodes for which the feature state need to be checked. This node names will be available in this string with comma separated.
     *         values.
     *
     * @return The response which has the list of nodes in which the feature is not activated.
     */
    @GET
    @Path("/getInvalidFeatureStateNodes")
    @Produces({MediaType.APPLICATION_JSON})
    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response getInvalidFeatureStateNodes(@QueryParam("subscriptionType") final String subscriptionType,
                                                @QueryParam("nodes") final String nodes) {
        final List<String> listOfNodeNames = new ArrayList<>(Arrays.asList(nodes.split(",")));
        final List<String> result = new ArrayList<>();
        log.debug("Going to validate the feature state for the nodes {} for the subscription type {} ", nodes, subscriptionType);
        for (final String nodeName : listOfNodeNames) {
            NodeLicenseFeatureState nodeLicenseFeatureState;
            try {
                nodeLicenseFeatureState = nodeService
                        .findNodeLicenseFeatureStateByNodeFdnAndSubscriptionType(nodeName, SubscriptionType.fromString(subscriptionType));
            } catch (final IllegalArgumentException | DataAccessException e) {
                log.error(" Exception occurred while checking the feature state for the node {} for the subscription type {}. So considering the "
                        + "feature state is not active for the node. Exception: {} ", nodeName, subscriptionType, e.getMessage());
                nodeLicenseFeatureState = NodeLicenseFeatureState.DEACTIVATED;
            }
            if (NodeLicenseFeatureState.DEACTIVATED == nodeLicenseFeatureState) {
                log.info("Feature state is not active for the node {} for the subscription type {} ", nodeName, subscriptionType);
                result.add(nodeName);
            }
        }
        return Response.status(Response.Status.OK).entity(result).build();
    }

    private boolean doesEachNodeHaveAtLeastOneCellInfo(final Subscription subscription) {
        if (subscription instanceof CellTrafficSubscription) {
            final CellTrafficSubscription sub = (CellTrafficSubscription) subscription;
            log.trace("Validating Nodes and Cells selected for CTR Subscription {}", subscription.getName());
            final Set<String> nodeNames = getNodeNames(sub.getNodes());
            final Set<String> nodeNamesFromCellTrafficInfos = getNodeNamesFromCellTrafficInfos(sub.getCellInfoList());
            return nodeNames.size() == nodeNamesFromCellTrafficInfos.size();
        } else if (subscription instanceof GpehSubscription) {
            final GpehSubscription sub = (GpehSubscription) subscription;
            log.debug("Validating Nodes and Cells selected for GPEH Subscription {}", subscription.getName());
            if (sub.isApplyOnAllCells() || !sub.isCellsSupported()) {
                return true;
            } else {
                final Set<String> nodeNames = getNodeNames(sub.getNodes());
                final Set<String> nodeNamesFromCellTrafficInfos = getNodeNamesFromCellTrafficInfos(sub.getCells());
                return nodeNames.size() == nodeNamesFromCellTrafficInfos.size();
            }
        } else if (subscription instanceof CellRelationSubscription) {
            final CellRelationSubscription sub = (CellRelationSubscription) subscription;
            log.debug("Validating Nodes and Cells selected for Cell Relation Subscription {}", subscription.getName());
            return sub.getNodes().size() == getNodeNamesFromCellTrafficInfos(sub.getCells()).size();
        } else if (subscription instanceof ResSubscription) {
            final ResSubscription sub = (ResSubscription) subscription;
            log.trace("Validating Nodes and Cells selected for Res Subscription {}", subscription.getName());
            return sub.isApplyOnAllCells() || (sub.getNodes().size() == getNodeNamesFromCellTrafficInfos(sub.getCells()).size());
        }
        return false;
    }

    /**
     * This method checks whether the Streaming destination is already set for all the nodes passed in the argument. The streaming destination will be
     * set in the scanner info object during activation.
     *
     * @param nodeFdnsConcatenated
     *         list of nodes for which the streaming destination need to be checked in the scannerInfo.
     *
     * @return FALSE if at least one node don't have the streaming destination set in the scannerInfo. TRUE otherwise
     * @throws DataAccessException
     *         if a database exception is thrown.
     */
    @GET
    @Path("/getIPConflictStatus")
    @Produces({MediaType.APPLICATION_JSON})
    @ReadOnly
    @InvokeInTransaction
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response getIpConflictStatus(@QueryParam("nodes") final String nodeFdnsConcatenated) throws DataAccessException {
        final List<String> nodeFdns = Arrays.asList(nodeFdnsConcatenated.split(","));
        if (scannerService
                .doesEachNodeHaveAtLeastOneActiveUetrStreamingScannerWithFileCollectionDisabledAndValidStreamInfoAndSubscriptionId(nodeFdns)) {
            return Response.status(Response.Status.OK).entity(true).build();
        }
        return Response.status(Response.Status.OK).entity(false).build();
    }

    /**
     * Gets available nodes (not included in any other subscription of the same type)
     *
     * @param subscriptionType
     *         - subscriptionType object. Cannot be null.
     * @param subscriptionId
     *         - the id of the Subscription
     * @param nodesFdn
     *         - the list of nodes fdn
     *
     * @return - the list of available nodes (fdns)
     */
    @POST
    @Path("/{subscriptionType}/{id}/getAvailableNodes")
    @Produces("application/json")
    @ReadOnly
    @InvokeInTransaction @Authorize(resource = AccessControlResources.SUBSCRIPTION, action = AccessControlActions.READ)
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Response getAvailableNodes(@PathParam("subscriptionType") final String subscriptionType, @PathParam("id") final String subscriptionId,
                                      final Set<String> nodesFdn) {
        if (nodesFdn == null || nodesFdn.isEmpty()) {
            systemRecorder.error(INVALID_INPUT, POST_SUBSCRIPTION.getSource(), subscriptionType, Operation.READ);
            return setResponse(INVALID_INPUT.getMessage());
        }
        try {
            subscriptionReadOperationService.getAvailableNodes(Long.parseLong(subscriptionId), SubscriptionType.valueOf(subscriptionType), nodesFdn);
            return Response.status(Response.Status.OK).entity(nodesFdn).build();
        } catch (IllegalArgumentException | RetryServiceException | DataAccessException e) {
            log.error("Error while getting available nodes {}", e);
            return setResponse(INTERNAL_SERVER_EXCEPTION.getMessage());
        }
    }

    private Response setResponse(final String message) {
        final ResponseData response = new ResponseData();
        response.setCode(Status.BAD_REQUEST);
        response.setError(message);
        return Response.status(response.getCode()).entity(response).build();
    }

    private Set<String> getNodeNames(final List<Node> nodes) {
        final Set<String> nodeNames = new HashSet<>();
        for (final Node node : nodes) {
            nodeNames.add(node.getName());
        }
        return nodeNames;
    }

    private Set<String> getNodeNamesFromCellTrafficInfos(final List<CellInfo> cellInfos) {
        final Set<String> nodeNames = new HashSet<>();
        for (final CellInfo cellInfo : cellInfos) {
            nodeNames.add(cellInfo.getNodeName());
        }
        return nodeNames;
    }

    private void checkSubscriptionNameAlreadyExists(final Subscription subscription) throws DataAccessException, RetryServiceException {
        if (subscription instanceof StatisticalSubscription && subscriptionReadOperationService
                    .existsBySubscriptionNameAndModelInfo(subscription.getName(), SubscriptionType.STATISTICAL.getModelInfo())) {
            throw new RetryServiceException(String.format("A statistical subscription with name '%s' already exists.", subscription.getName()));
        }
    }
}
