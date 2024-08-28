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

package com.ericsson.oss.services.pm.initiation.model.metadata;

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.UNABLE_TO_ACTIVATE_SUBSCRIPTION;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.inject.Inject;

import com.ericsson.oss.external.modeling.schema.gen.ext_integrationpointlibrary.IntegrationPoint;
import com.ericsson.oss.itpf.sdk.core.util.StringUtils;
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.pmic.dto.subscription.MtrSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.MtrAccessType;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.ebs.utils.EbsSubscriptionHelper;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.initiation.custom.annotation.ModelDeploymentMetaData;
import com.ericsson.oss.services.pm.initiation.custom.annotation.ModelDeploymentValidation;
import com.ericsson.oss.services.pm.initiation.model.metadata.counters.PmCountersLookUp;
import com.ericsson.oss.services.pm.initiation.model.metadata.events.PmCellTrafficNonTriggerEventsLookUp;
import com.ericsson.oss.services.pm.initiation.model.metadata.events.PmCellTrafficTriggerEventsLookUp;
import com.ericsson.oss.services.pm.initiation.model.metadata.events.PmEventsLookUp;
import com.ericsson.oss.services.pm.initiation.model.metadata.events.PmGpehEventsLookUp;
import com.ericsson.oss.services.pm.initiation.model.metadata.events.PmUetrEventsLookUp;
import com.ericsson.oss.services.pm.initiation.model.metadata.moinstances.PmMoinstancesLookUp;
import com.ericsson.oss.services.pm.initiation.model.metadata.mtr.PmMtrLookUp;
import com.ericsson.oss.services.pm.initiation.model.metadata.res.PmResLookUp;
import com.ericsson.oss.services.pm.initiation.restful.AttributesForAttachedNodes;
import com.ericsson.oss.services.pm.initiation.restful.ResSubscriptionAttributes;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.services.pm.initiation.restful.api.CounterTableRow;
import com.ericsson.services.pm.initiation.restful.api.EventTableRow;
import com.ericsson.services.pm.initiation.restful.api.MetaDataType;
import com.ericsson.services.pm.initiation.restful.api.PmMetaDataLookupLocal;
import com.ericsson.services.pm.initiation.restful.api.PmMimVersionQuery;

/**
 * This class is responsible to fetch moClass, counters and its description from model service for CPP model with version 3.12.0
 */

@Stateless
@Local(PmMetaDataLookupLocal.class)
public class PmMetaDataLookup implements PmMetaDataLookupLocal {

    private static final String REQUEST_NRAT_PRODUCER_EVENTS = "cellTraceNRan";

    private static final Set<String> EVENT_PRODUCER_IDS_FOR_5_GS;

    static {
        //TODO EEITSIK, Replace this with model/capability driven solution, as per TORF-328808
        EVENT_PRODUCER_IDS_FOR_5_GS = new HashSet<>(3);
        EVENT_PRODUCER_IDS_FOR_5_GS.add("CUCP");
        EVENT_PRODUCER_IDS_FOR_5_GS.add("CUUP");
        EVENT_PRODUCER_IDS_FOR_5_GS.add("DU");
    }

    @Inject
    private PmCountersLookUp pmCountersLookUp;
    @Inject
    private PmEventsLookUp pmEventsLookUp;
    @Inject
    private PmMoinstancesLookUp pmMoinstancesLookUp;
    @Inject
    private EbsCounterLookUp ebsCounterLookUp;
    @Inject
    private PmCellTrafficNonTriggerEventsLookUp pmCellTrafficNonTriggerEventsLookUp;
    @Inject
    private PmCellTrafficTriggerEventsLookUp pmCellTrafficTriggerEventsLookUp;
    @Inject
    private PmUetrEventsLookUp pmUetrEventsLookUp;
    @Inject
    private PmGpehEventsLookUp pmGpehEventsLookUp;
    @Inject
    private PmResLookUp pmResLookUp;
    @Inject
    private PmMtrLookUp pmMtrLookUp;
    @Inject
    private EbsSubscriptionHelper ebsSubscriptionHelper;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    private static void removeEventsBasedOnEventFilter(final String eventFilter, final Collection<EventTableRow> eventTableRows) {
        final boolean onlyIncludeNranEvents = REQUEST_NRAT_PRODUCER_EVENTS.equals(eventFilter);
        eventTableRows.removeIf(eventTableRow -> !shouldIncludeEvent(eventTableRow, onlyIncludeNranEvents));
    }

    private static boolean isNRanEvent(final EventTableRow eventTableRow) {
        return eventTableRow.getEventProducerId() != null && EVENT_PRODUCER_IDS_FOR_5_GS.contains(eventTableRow.getEventProducerId());
    }

    private static boolean shouldIncludeEvent(final EventTableRow eventTableRow, final boolean onlyIncludeNranEvents) {
        if (onlyIncludeNranEvents) {
            return isNRanEvent(eventTableRow);
        }
        return !isNRanEvent(eventTableRow);
    }

    @Override
    @ModelDeploymentValidation
    @ModelDeploymentMetaData(value = MetaDataType.COUNTERS)
    public Collection<CounterTableRow> getCounters(final PmMimVersionQuery pmMimVersionQuery, final List<String> modelUrns,
                                                   final boolean eventsRequiredForCounters, final boolean supportExternalCounterName, final List<String> flexModelUrns)
            throws PfmDataException, ValidationException {
        Set<CounterTableRow> countersForAllVersions =
                pmCountersLookUp.getCountersForAllVersions(pmMimVersionQuery.getMimVersions(), modelUrns, supportExternalCounterName);
        if (eventsRequiredForCounters) {
            countersForAllVersions.addAll(pmCountersLookUp.getFlexCounters(pmMimVersionQuery.getMimVersions(), flexModelUrns, supportExternalCounterName));
            final Collection<EventTableRow> supportedEventsList = pmEventsLookUp.getEventsForAllVersions(pmMimVersionQuery.getMimVersions());
            countersForAllVersions = ebsCounterLookUp.getEbsCounters(pmMimVersionQuery, countersForAllVersions, supportedEventsList);
        }
        return countersForAllVersions;
    }

    @Override
    @ModelDeploymentValidation
    @ModelDeploymentMetaData(value = MetaDataType.EVENTS)
    public Collection<EventTableRow> getEvents(final PmMimVersionQuery pmMimVersionQuery, final String eventFilter)
            throws PfmDataException, ValidationException {
        final Collection<EventTableRow> eventTableRows = pmEventsLookUp.getEventsForAllVersions(pmMimVersionQuery.getMimVersions());
        if (!StringUtils.isEmpty(eventFilter)) {
            removeEventsBasedOnEventFilter(eventFilter, eventTableRows);
        }
        return eventTableRows;
    }

    @Override
    public List<String> getApplicableCounters(final List<CounterInfo> moClassList, final String type, final String version) {
        return pmCountersLookUp.getApplicableCounters(moClassList, type, version);
    }

    @Override
    public List<String> getApplicableEvents(final List<EventInfo> moClassList, final String type, final String version) {
        return pmEventsLookUp.getApplicableEvents(moClassList, type, version);
    }

    @Override
    public List<CounterInfo> getCorrectCounterListForTheSpecifiedMims(final List<CounterInfo> counterInfo,
                                                                      final Set<NodeTypeAndVersion> nodesTypeVersion,
                                                                      final List<String> supportedModelDefiners, final List<String> supportedFlexModelDefiners,
                                                                      final boolean supportExternalCounterNames) {
        return pmCountersLookUp.getCorrectCounterListForTheSpecifiedMims(counterInfo, nodesTypeVersion, supportedModelDefiners, supportedFlexModelDefiners, supportExternalCounterNames);
    }

    @Override
    public List<EventInfo> getCorrectEventListForTheSpecifiedMims(final List<EventInfo> eventInfos, final Set<NodeTypeAndVersion> nodesTypeVersion) {
        return pmEventsLookUp.getCorrectEventListForTheSpecifiedMims(eventInfos, nodesTypeVersion);
    }

    @Override
    public IntegrationPoint getEventsIntegrationPoint(final String integrationPointName) {
        return pmEventsLookUp.fetchEventsIntegrationPoint(integrationPointName);
    }

    @Override
    public Collection<EventTableRow> getEventsForAllVersions(final Set<NodeTypeAndVersion> nodesTypeVersion) throws PfmDataException {
        return pmEventsLookUp.getEventsForAllVersions(nodesTypeVersion);
    }

    @Override
    public Object getSupportedMoInstances(final Set<NodeTypeAndVersion> nodeAndVersionSet, final String nodes, final String moClasses,
                                          final String subscriptionType)
            throws DataAccessException {
        return pmMoinstancesLookUp.getMoinstances(nodeAndVersionSet, nodes, moClasses, subscriptionType);
    }

    @Override
    @ModelDeploymentValidation
    @ModelDeploymentMetaData(value = MetaDataType.COUNTERS)
    public Collection<CounterTableRow> getFilteredCountersForAllVersions(final PmMimVersionQuery pmMimVersionQuery,
                                                                         final List<String> modelDefiners,
                                                                         final String subscriptionType,
                                                                         final boolean supportExternalCounterNames)
            throws PfmDataException, ValidationException {
        return pmCountersLookUp.getFilteredCountersForAllVersions(pmMimVersionQuery.getMimVersions(), modelDefiners,
                                                                  subscriptionType, supportExternalCounterNames);
    }

    @Override
    public Map<String, List<String>> getCounterSubGroups(final String subscriptionType) {
        return pmCountersLookUp.getCounterSubGroups(subscriptionType);
    }

    @Override
    @ModelDeploymentValidation
    @ModelDeploymentMetaData(value = MetaDataType.EVENTS)
    public Collection<EventTableRow> getCellTrafficNonTriggerEventsForAllVersions(final PmMimVersionQuery pmMimVersionQuery)
            throws PfmDataException, ValidationException {
        return pmCellTrafficNonTriggerEventsLookUp.getEventsForAllVersions(pmMimVersionQuery.getMimVersions());
    }

    @Override
    @ModelDeploymentValidation
    @ModelDeploymentMetaData(value = MetaDataType.EVENTS)
    public Collection<EventTableRow> getWideBandEventsForAllVersions(final PmMimVersionQuery pmMimVersionQuery, final String subscriptionType)
            throws PfmDataException, ValidationException {
        if (subscriptionType.equals(SubscriptionType.UETR.name())) {
            return pmUetrEventsLookUp.getEventsForAllVersions(pmMimVersionQuery.getMimVersions());
        } else if (subscriptionType.equals(SubscriptionType.GPEH.name())) {
            return pmGpehEventsLookUp.getEventsForAllVersions(pmMimVersionQuery.getMimVersions());
        } else if (subscriptionType.equals(SubscriptionType.CELLTRAFFIC.name())) {
            return pmCellTrafficTriggerEventsLookUp.getEventsForAllVersions(pmMimVersionQuery.getMimVersions());
        }

        return Collections.emptySet();
    }

    @Override
    @ModelDeploymentValidation
    public ResSubscriptionAttributes getResAttributes(final PmMimVersionQuery pmMimVersionQuery) throws PfmDataException {
        return pmResLookUp.getResAttributes(pmMimVersionQuery.getMimVersions());

    }

    @Override
    public int getAttachedNodeCount(final AttributesForAttachedNodes attributesForAttachedNodes) throws DataAccessException {
        return pmResLookUp.fetchAttachedNodes(attributesForAttachedNodes.getCells(), attributesForAttachedNodes.getApplyOnAllCells(),
                attributesForAttachedNodes.getNodeFdns(), true).size();
    }

    @Override
    public Map<String, MtrAccessType[]> getMtrAccessTypes() {
        return pmMtrLookUp.getMtrAccessTypes();
    }

    @Override
    public List<String> getNonAssociatedNodes(final long subscriptionId) throws DataAccessException, RetryServiceException, ValidationException {
        final MtrSubscription subscription = (MtrSubscription) subscriptionReadOperationService.findByIdWithRetry(subscriptionId, true);

        if (subscription.getNodes() == null || subscription.getNodes().isEmpty()) {
            throw new ValidationException(String.format(UNABLE_TO_ACTIVATE_SUBSCRIPTION, subscription.getName()));
        }
        return pmMtrLookUp.getNonAssociatedNodes(subscription.getNodes(), subscription.getNodesFdns(), true);
    }

    @Override
    public Set<Integer> getUsedRecordingReferences(final long subscriptionId) throws DataAccessException, RetryServiceException, ValidationException {
        final MtrSubscription subscription = (MtrSubscription) subscriptionReadOperationService.findByIdWithRetry(subscriptionId, true);
        if (subscription.getNodes() == null || subscription.getNodes().isEmpty()) {
            throw new ValidationException(String.format(UNABLE_TO_ACTIVATE_SUBSCRIPTION, subscription.getName()));
        }
        return pmMtrLookUp.getUsedRecordingReferences(subscription);
    }
}
