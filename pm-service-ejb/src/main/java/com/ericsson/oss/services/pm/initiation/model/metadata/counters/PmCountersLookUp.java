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

package com.ericsson.oss.services.pm.initiation.model.metadata.counters;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.PMFUNCTIONS_RES_SUPPORTEDCOUNTERS_NAME;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.PMFUNCTIONS_SUPPORTEDCOUNTERS_NAME;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.PMFUNCTIONS_UNSUPPORTEDCOUNTERS_NAME;
import static com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants.NE_DEFINED_PATTERN;
import static com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants.OSS_DEFINED_PATTERN;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import org.slf4j.Logger;

import com.ericsson.oss.itpf.modeling.common.info.ModelInfo;
import com.ericsson.oss.itpf.modeling.modelservice.direct.DirectModelAccess;
import com.ericsson.oss.itpf.modeling.modelservice.exception.ModelProcessingException;
import com.ericsson.oss.itpf.modeling.modelservice.exception.UnknownModelException;
import com.ericsson.oss.itpf.modeling.schema.gen.oss_common.LifeCycleType;
import com.ericsson.oss.pmic.api.counters.PmCountersLifeCycleResolver;
import com.ericsson.oss.pmic.api.modelservice.PmCapabilityReader;
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.model.metadata.PMICModelDeploymentValidator;
import com.ericsson.oss.services.pm.initiation.model.metadata.PmMetaDataHelper;
import com.ericsson.oss.services.pm.initiation.model.utils.PmMetaDataConstants;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_measurement.Measurement;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_measurement.MeasurementGroupType;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_measurement.MeasurementReferenceType;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_measurement.MoClassType;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_measurement.PerformanceMeasurementDefinition;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_measurement.ScannerType;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.services.pm.initiation.restful.api.CounterTableRow;
import com.ericsson.services.pm.initiation.restful.api.EventBasedCounterTableRow;

/**
 * This class can get counters and its description from model service for CPP model
 */
public class PmCountersLookUp {

    private static final String SUPPORTED_MO_CLASSES = "supportedMOCs";
    private static final String COUNTER_SUB_GROUPS = "CounterSubGroups";
    private static final String COULD_N0T_FIND_ANY_COUNTERS = "Could not find any counters!";
    private static final String UNSPECIFIED_RAT_TYPE_KEY = "COMMON";

    @Inject
    private Logger log;
    @Inject
    private DirectModelAccess directModelAccess;
    @Inject
    private PmMetaDataHelper metaDataHelper;
    @Inject
    private PmCapabilityModelService pmCapabilitiesLookup;
    @Inject
    private PmicTechnologyDomainHelper pmicTechDomainHelper;
    @Inject
    private PmCountersLifeCycleResolver pmCountersLifeCycleResolver;
    @Inject
    private PMICModelDeploymentValidator pmicModelDeploymentValidator;
    @Inject
    private PmCapabilityReader pmCapabilityReader;

    /**
     * Returns filtered counters based on technologyDomain of the NodeTypeAndVersion. If technologyDomain is null or empty then counters are filtered
     * based on the technologyDomain of the neType fetched from model service.
     *
     * @param nodeAndVersionSet
     *         - captures all the mimVersions.
     * @param supportedModelDefiners
     *         the string names of the supported model definers
     * @param supportExternalCounterName
     *        boolean whether external counter names are required
     * @return collection of CounterTableRow Object
     * @throws PfmDataException
     *         - thrown if No counters found for mim versions
     */
    public Set<CounterTableRow> getCountersForAllVersions(final Set<NodeTypeAndVersion> nodeAndVersionSet,
                                                          final List<String> supportedModelDefiners, final boolean supportExternalCounterName)
            throws PfmDataException {

        final Set<CounterTableRow> counters = new TreeSet<>();

        final List<NodeTypeAndVersion> invalidNodeTypeAndVersionList = new ArrayList<>();
        final String counterUrnPatternPrefix = PmMetaDataConstants.PFM_MEASUREMENT_PATTERN;

        boolean isFirstTime = true;
        Boolean isEventCounterVerificationNeeded = true;
        for (final NodeTypeAndVersion nodeTypeAndVersion : nodeAndVersionSet) {
            try {
                if (isEventCounterVerificationNeeded) {
                    final String neType = nodeTypeAndVersion.getNodeType();
                    isEventCounterVerificationNeeded = pmicModelDeploymentValidator.isCounterValidationSupportedForGivenTargetType(neType);
                    log.debug("Setting the boolean isEventCounterVerificationNeeded to {} for nodetype {}", isEventCounterVerificationNeeded,
                            nodeTypeAndVersion.getNodeType());
                }
                isFirstTime = updatePredefinedCounters(counters, counterUrnPatternPrefix, supportedModelDefiners, nodeTypeAndVersion, isFirstTime, supportExternalCounterName);
            } catch (final ModelProcessingException | UnknownModelException e) {
                invalidNodeTypeAndVersionList.add(nodeTypeAndVersion);
                log.warn("No counters found for mim {}", nodeTypeAndVersion);
                log.debug(COULD_N0T_FIND_ANY_COUNTERS, e);
            }
        }
        log.debug("Found {} co for mimVersions: {}", counters.size(), nodeAndVersionSet);
        if (counters.isEmpty() && isEventCounterVerificationNeeded) {
            final String message = String.format("No counters found for mim versions %s", invalidNodeTypeAndVersionList.toString());
            throw new PfmDataException(message);
        }
        return counters;
    }

    /**
     * Get flex counters for the given nodeTypesAndVersions
     * @param nodeAndVersionSet
     *      the node types for fecthing flex counters
     * @param supportedModelFlexDefiners
     *      the model urns for flex counters
     * @param supportExternalCounterName
     *      whether external counter names should be used
     * @return
     *      Set of counters
     */
    public Set<CounterTableRow> getFlexCounters(final Set<NodeTypeAndVersion> nodeAndVersionSet,
                                                final List<String> supportedModelFlexDefiners, final boolean supportExternalCounterName) {
        if (supportedModelFlexDefiners.isEmpty()) {
            return emptySet();
        }
        final Set<CounterTableRow> counters = new TreeSet<>();
        nodeAndVersionSet.forEach(pmvq -> counters.addAll(getFlexCounters(pmvq, supportedModelFlexDefiners, supportExternalCounterName)));
        log.info("Found {} flex counters for nodeTypes: {}", counters.size(), nodeAndVersionSet);
        return counters;
    }

    private Set<CounterTableRow> getFlexCounters(final NodeTypeAndVersion nodeTypeAndVersion, final List<String> supprotedFlexModelDefiner, final boolean supportExternalCounterName) {
        final Set<CounterTableRow> counters = new TreeSet<>();
        for (final String definer : supprotedFlexModelDefiner) {
            try{
                final String pfmModelVersion = pmCapabilityReader.getLatestCapabilityVersion("pfm_measurement", nodeTypeAndVersion.getNodeType(), definer);
                if (pfmModelVersion != null) {
                    final String pfmModelUrl = PmMetaDataConstants.PFM_MEASUREMENT_PATTERN + nodeTypeAndVersion.getNodeType() + definer + pfmModelVersion;
                    final Set<CounterTableRow> countersPerNF = getAllPMFlexCounters(nodeTypeAndVersion, Arrays.asList(pfmModelUrl), supportExternalCounterName);
                    counters.addAll(countersPerNF);
                }
            }
            catch (final ModelProcessingException | UnknownModelException e) {
                log.warn("No counters found for mim {}", nodeTypeAndVersion);
                log.debug(COULD_N0T_FIND_ANY_COUNTERS, e);
            }
        }
        return counters;
    }

    private boolean updatePredefinedCounters(final Set<CounterTableRow> counters, final String counterUrnPatternPrefix,
                                             final List<String> requestedDefiners, final NodeTypeAndVersion nodeTypeAndVersion,
                                             final boolean isFirstTime, final boolean supportExternalCounterName) {
        boolean isUpdatedPredefinedCounters = isFirstTime;
        for (final String definer : requestedDefiners) {
            final Set<CounterTableRow> countersPerMim = getAllPMCounters(nodeTypeAndVersion, counterUrnPatternPrefix, definer, supportExternalCounterName);
            counters.addAll(countersPerMim);
            if (!isUpdatedPredefinedCounters) {
                updatePredefinedCounters(counters, countersPerMim);
            }
            isUpdatedPredefinedCounters = false;
        }
        return isUpdatedPredefinedCounters;
    }

    /**
     * If a counter scanner_type is Predefined in at least one model, the scanner_type in the list will be updated accordingly.
     *
     * @param counters
     * @param countersPerMim
     */
    private void updatePredefinedCounters(final Set<CounterTableRow> counters, final Set<CounterTableRow> countersPerMim) {
        for (final CounterTableRow counterMim : countersPerMim) {
            if (!counterMim.getScannerType().equals(ScannerType.PRIMARY) && !counterMim.getScannerType().equals(ScannerType.SECONDARY)) {
                continue;
            }
            updateScannerTypeOnAllCounters(counters, counterMim);
        }
    }

    private void updateScannerTypeOnAllCounters(final Set<CounterTableRow> countersToUpdate, final CounterTableRow counterWithScannerType) {
        for (final CounterTableRow counter : countersToUpdate) {
            if (counter.hasSameNameAndSourceObject(counterWithScannerType) && counter.getScannerType() !=  counterWithScannerType.getScannerType()) {
                log.debug("Update scanner_type for counter : {} {}", counter.getSourceObject(), counter.getCounterName());
                counter.setScannerType(counterWithScannerType.getScannerType());
                break;
            }
        }
    }

    /**
     * Returns filtered counters for NodeTypeAndVersion counters are filtered based on the technologyDomain of the NodeTypeAndVersion. If
     * technologyDomain is null or empty then filters the counters based on the technologyDomain of the neType fetched from model service.
     *
     * @param typeVersion
     * @param pfmMetaDataPrefix
     * @param definer
     *
     * @return collection of CounterTableRow Object
     */
    Set<CounterTableRow> getAllPMCounters(final NodeTypeAndVersion typeVersion, final String pfmMetaDataPrefix,
                                                  final String definer, final Boolean supportExternalCounterName) {
        log.debug("Getting counters for node type and version {} from the local cache", typeVersion);
        final String pfmMetaDataPattern = pfmMetaDataPrefix + "(.*)" + definer;
        log.debug("pfmMetaDataPattern for meta {}", pfmMetaDataPattern);
        return filterByRats(getRatToCountersMap(typeVersion, pfmMetaDataPattern, supportExternalCounterName), getTechnologyDomain(typeVersion));
    }

    private Set<String> getTechnologyDomain(final NodeTypeAndVersion typeVersion) {
        final Set<String> technologyDomainSet = new HashSet<>();
        final List<String> technologyDomainList = typeVersion.getTechnologyDomain();
        if (technologyDomainList != null && !technologyDomainList.isEmpty()) {
            technologyDomainSet.addAll(technologyDomainList);
        } else {
            technologyDomainSet.addAll(pmicTechDomainHelper.getTechnologyDomain(typeVersion.getNodeType()));
        }
        return technologyDomainSet;
    }

    private Set<CounterTableRow> getAllPMFlexCounters(final NodeTypeAndVersion typeVersion, final Collection<String> pfmMeasurementModelUrns, final boolean supportExternalCounterName) {
        return filterByRats(getPMCountersForModelUrns(pfmMeasurementModelUrns, typeVersion, supportExternalCounterName),  getTechnologyDomain(typeVersion));
    }

    private Map<String, Set<CounterTableRow>> getRatToCountersMap(final NodeTypeAndVersion typeVersion, final String pfmMetaDataPattern,
                                                                  final boolean supportExternalCounterName) {
        log.debug("Starting searching for counters for ossModelIdentity {} with filter {}", typeVersion.getOssModelIdentity(), pfmMetaDataPattern);
        final Collection<String> pfmMeasurementModelUrns =
                metaDataHelper.getMetaDataUrnsFromModelService(typeVersion.getNodeType(), typeVersion.getOssModelIdentity(), pfmMetaDataPattern);
        log.info("Got moClasses from model service for ne type {} ossModelIdentity {} ", typeVersion.getNodeType(), typeVersion.getOssModelIdentity());
        return getPMCountersForModelUrns(pfmMeasurementModelUrns, typeVersion, supportExternalCounterName);
    }

    private Set<CounterTableRow> filterByRats(final Map<String, Set<CounterTableRow>> countersToRATMap, final Set<String> supportedRats) {
        if (countersToRATMap != null) {
            final Set<CounterTableRow> result = new TreeSet<>();
            final Set<CounterTableRow> unspecifiedRatTypes = countersToRATMap.get(UNSPECIFIED_RAT_TYPE_KEY);
            if (unspecifiedRatTypes != null) {
                result.addAll(unspecifiedRatTypes);
            }
            if (supportedRats != null) {
                for (final String ratKey : supportedRats) {
                    final Set<CounterTableRow> rats = countersToRATMap.get(ratKey);
                    if (rats != null) {
                        result.addAll(rats);
                    }
                }
            }
            return result;
        }
        return emptySet();
    }

    private Map<String, Set<CounterTableRow>> getPMCountersForModelUrns(final Collection<String> modelUrns,
                                                                        final NodeTypeAndVersion typeVersion, final boolean supportExternalCounterName) {
        final Map<String, Set<CounterTableRow>> countersToRATMap = new ConcurrentHashMap<>();
        countersToRATMap.put(UNSPECIFIED_RAT_TYPE_KEY, new HashSet<>());
        final List<String> nonSupportedCounters = getNonSupportedStatsCounters(PMFUNCTIONS_UNSUPPORTEDCOUNTERS_NAME);
        final List<String> supportedCounterLifeCycles = getSupportedCounterLifeCycles(typeVersion);

        for (final String modelUrn : modelUrns) {
            final ModelInfo modelInfo = ModelInfo.fromUrn(modelUrn);
            final PerformanceMeasurementDefinition perfMeasurements = directModelAccess.getAsJavaTree(modelInfo,
                    PerformanceMeasurementDefinition.class);

            final List<MeasurementGroupType> measurementsGroup = perfMeasurements.getMeasurementGroup();
            final Map<PmCountersAndMoClassType, PmCounterAttributes> counterMoClassToAttributes = new HashMap<>();

            for (final MoClassType moClassType : perfMeasurements.getMoClass()) {
                processMeasurements(countersToRATMap, nonSupportedCounters, measurementsGroup, counterMoClassToAttributes, moClassType,
                        supportedCounterLifeCycles);
            }

            if (!measurementsGroup.isEmpty()) {
                processMeasurementGroups(countersToRATMap, nonSupportedCounters, measurementsGroup, counterMoClassToAttributes,
                        supportedCounterLifeCycles, supportExternalCounterName);
            }
        }
        return countersToRATMap;
    }

    private List<String> getSupportedCounterLifeCycles(final NodeTypeAndVersion nodeTypeAndVersion) {
        return pmCountersLifeCycleResolver.getSupportedCounterLifeCyclesForTechnologyDomains(nodeTypeAndVersion.getNodeType(),
                nodeTypeAndVersion.getTechnologyDomain());
    }

    private void processMeasurements(final Map<String, Set<CounterTableRow>> countersToRATMap, final List<String> nonSupportedCounters,
                                     final List<MeasurementGroupType> measurementsGroup,
                                     final Map<PmCountersAndMoClassType, PmCounterAttributes> counterMoClassToAttributes,
                                     final MoClassType moClassType, final List<String> supportedCounterLifeCycles) {
        final List<Measurement> measurements = moClassType.getMeasurement();
        for (final Measurement counter : measurements) {
            if (measurementsGroup.isEmpty()) {
                if (isLifeCycleSupportedForCounter(counter.getLifeCycle(), supportedCounterLifeCycles)
                        && isSupportedCounter(nonSupportedCounters, counter.getName())) {
                    final CounterTableRow counterTableRow = makeCounterTable(counter.getExternalName(), moClassType.getName(), counter.getDesc(),
                            counter.getScanner(), counter.getBasedOnEvent());
                    fillRatMap(countersToRATMap, counter.getGeneration(), counterTableRow);
                }
            } else {
                final PmCountersAndMoClassType pmCountersAndMoClass = new PmCountersAndMoClassType(counter.getName(), moClassType.getName());
                final PmCounterAttributes pmCounterAttributes = new PmCounterAttributes(counter.getDesc(), counter.getLifeCycle(),
                        counter.getBasedOnEvent(), counter.getGeneration(), counter.getExternalName(), counter.getScanner());
                if (counterMoClassToAttributes.containsKey(pmCountersAndMoClass)) {
                    final PmCounterAttributes existingPmCounterAttributes = counterMoClassToAttributes.get(pmCountersAndMoClass);
                    existingPmCounterAttributes.getBasedOnEvent().addAll(pmCounterAttributes.getBasedOnEvent());
                } else {
                    counterMoClassToAttributes.put(pmCountersAndMoClass, pmCounterAttributes);
                }
            }
        }
    }

    private void processMeasurementGroups(final Map<String, Set<CounterTableRow>> countersToRatMap,
                                          final List<String> nonSupportedCounters, final List<MeasurementGroupType> measurementsGroup,
                                          final Map<PmCountersAndMoClassType, PmCounterAttributes> counterMoClassToAttributes,
                                          final List<String> supportedCounterLifeCycles, final boolean supportExternalCounterName) {
        // the same counter can be in different groups and in different MO class
        for (final MeasurementGroupType measurementGroupType : measurementsGroup) {
            final String groupName = measurementGroupType.getName();
            final List<MeasurementReferenceType> measurementsRef = measurementGroupType.getMeasurement();
            for (final MeasurementReferenceType measurementRef : measurementsRef) {
                final PmCountersAndMoClassType pmCountersAndMoClass = new PmCountersAndMoClassType(measurementRef.getMeasurementName(),
                        measurementRef.getMoClassName());
                final PmCounterAttributes pmCounterAttributes = counterMoClassToAttributes.get(pmCountersAndMoClass);
                final String externalCounterName = pmCounterAttributes.getExternalCounterName();
                final String measurementName = (supportExternalCounterName && externalCounterName != null) ? externalCounterName : measurementRef.getMeasurementName();
                if (isLifeCycleSupportedForCounter(pmCounterAttributes.getLifeCycle(), supportedCounterLifeCycles)
                        && isSupportedCounter(nonSupportedCounters, externalCounterName)) {
                    final CounterTableRow counterTableRow = makeCounterTable(measurementName, groupName, pmCounterAttributes.getDescription(),
                            pmCounterAttributes.getScannerType(), pmCounterAttributes.getBasedOnEvent());
                    fillRatMap(countersToRatMap, pmCounterAttributes.getGeneration(), counterTableRow);
                }
            }
        }
    }

    private boolean isLifeCycleSupportedForCounter(final LifeCycleType lifeCycleType, final List<String> supportedCounterLifeCycles) {
        return supportedCounterLifeCycles.contains(lifeCycleType.name());
    }

    private boolean isSupportedCounter(final List<String> nonSupportedCounters, final String counterName) {
        return !(nonSupportedCounters.contains(counterName));
    }

    private void fillRatMap(final Map<String, Set<CounterTableRow>> map, final List<String> ratList, final CounterTableRow counterTableRow) {
        if (ratList != null && !ratList.isEmpty()) {
            for (final String ratKey : ratList) {
                if (map.containsKey(ratKey)) {
                    map.get(ratKey).add(counterTableRow);
                } else {
                    final HashSet<CounterTableRow> counterTableRowEntry = new HashSet<>(singletonList(counterTableRow));
                    map.put(ratKey, counterTableRowEntry);
                }
            }
        } else {
            map.get(UNSPECIFIED_RAT_TYPE_KEY).add(counterTableRow);
        }
    }

    private CounterTableRow makeCounterTable(final String counterName, final String moClassTypeOrGroupName, final String description,
                                             final ScannerType scannerType, final List<String> basedOnEvents) {
        if (basedOnEvents.isEmpty()) {
            return new CounterTableRow(counterName, moClassTypeOrGroupName, description, scannerType);
        } else {
            return new EventBasedCounterTableRow(counterName, moClassTypeOrGroupName, description, scannerType, basedOnEvents);
        }
    }

    /**
     * @param counters
     *         - list of counters to check if applicable to the node
     * @param type
     *         - node type used to create NodeTypeAndVersion object to retrieve pm counters
     * @param version
     *         - node version used to create NodeTypeAndVersion object to retrieve pm counters
     *
     * @return - get a list of counter names
     */
    public List<String> getApplicableCounters(final List<CounterInfo> counters, final String type, final String version) {
        return getApplicableCounters(counters, type, version, new ArrayList<String>());
    }

    /**
     * This method return the list of counters as string containg group:counter1,counter2
     * @param counters
     *         - list of counters to check if applicable to the node
     * @param type
     *         - node type used to create NodeTypeAndVersion object to retrieve pm counters
     * @param version
     *         - node version used to create NodeTypeAndVersion object to retrieve pm counters
     * @param technologyDomain
     *         - technologyDomain
     *
     * @return - get a list of counter names strings as group:counter1,counter2....
     */
    public List<String> getApplicableCounters(final List<CounterInfo> counters, final String type, final String version,
                                              final List<String> technologyDomain) {
        final NodeTypeAndVersion typeAndVersion = new NodeTypeAndVersion(type, version, technologyDomain);
        final Set<NodeTypeAndVersion> typeAndVersionSet = new HashSet<>();
        typeAndVersionSet.add(typeAndVersion);

        final List<String> result = getApplicableCountersAsString(counters, typeAndVersionSet);
        log.trace("Applicable counters for node Type {} and version {} are {}", type, version, result);
        return result;
    }

    /**
     * @param counters
     *         - Collection of counters to check if applicable to the node
     * @param typeAndVersionSet
     *         - Set of Type and Version to be checked for
     *
     * @return - get a list of filtered counters
     */
    public List<CounterInfo> getApplicableCounters(final Collection<CounterInfo> counters, final Set<NodeTypeAndVersion> typeAndVersionSet) {
        final Set<CounterTableRow> allCounters = getSupportedCounters(typeAndVersionSet);
        final List<CounterInfo> filteredCounters = new ArrayList<>();
        for (final CounterInfo counter : counters) {
            if (allCounters.contains(new CounterTableRow(counter.getName(), counter.getMoClassType()))) {
                filteredCounters.add(counter);
            }
        }
        log.trace("Applicable counters are {}", filteredCounters);
        return filteredCounters;
    }

    /**
     * @param counters
     *         - Collection of counters to check if applicable to the node
     * @param typeAndVersionSet
     *         - Set of Type and Version to be checked for
     *
     * @return - get a list of counter names strings as group:counter1,counter2....
     */
    public List<String> getApplicableCountersAsString(final Collection<CounterInfo> counters, final Set<NodeTypeAndVersion> typeAndVersionSet) {
        final Set<CounterTableRow> allCounters = getSupportedCounters(typeAndVersionSet);
        final Map<String, StringBuilder> applicableCountersGroups = new HashMap<>();
        for (final CounterInfo counter : counters) {
            if (allCounters.contains(new CounterTableRow(counter.getName(), counter.getMoClassType()))) {
                StringBuilder applicableCountersGroup = applicableCountersGroups.get(counter.getMoClassType());
                if (applicableCountersGroup == null) {
                    applicableCountersGroup = new StringBuilder(counter.getMoClassType()).append(":").append(counter.getName());
                    applicableCountersGroups.put(counter.getMoClassType(), applicableCountersGroup);
                } else {
                    applicableCountersGroup.append(",").append(counter.getName());
                }
            }
        }
        final List<String> result = new ArrayList<>(applicableCountersGroups.size());
        for (final StringBuilder applicableCountersGroup : applicableCountersGroups.values()) {
            result.add(applicableCountersGroup.toString());
        }

        log.trace("Applicable counters are {}", result);
        return result;
    }

    private Set<CounterTableRow> getSupportedCounters(final Set<NodeTypeAndVersion> typeAndVersionSet) {
        final Set<CounterTableRow> allCounters = new TreeSet<>();
        for (final NodeTypeAndVersion typeAndVersion : typeAndVersionSet) {
            if (typeAndVersion.getTechnologyDomain() == null) {
                typeAndVersion.setTechnologyDomain(new ArrayList<>());
            }
            final String counterUrnPatternPrefix = PmMetaDataConstants.PFM_MEASUREMENT_PATTERN;
            //here we are use value "true" as it will retain current behaviour
            allCounters.addAll(getAllPMCounters(typeAndVersion, counterUrnPatternPrefix, NE_DEFINED_PATTERN, true));
            allCounters.addAll(getAllPMCounters(typeAndVersion, counterUrnPatternPrefix, OSS_DEFINED_PATTERN, true));
        }
        return allCounters;
    }

    /**
     * @param counterInfo
     *         - list of counter info objects to verify is correct for specified meta information models
     * @param nodesTypeVersionSet
     *         - set of NodeType And Version objects
     * @param supportedModelDefiners
     * the supported model definers as strings
     * @param supportedFlexModelDefiners
     * the supported flex model definers as strings
     *
     * @return - returns the corrected counters list
     */
    public List<CounterInfo> getCorrectCounterListForTheSpecifiedMims(final List<CounterInfo> counterInfo,
                                                                      final Set<NodeTypeAndVersion> nodesTypeVersionSet,
                                                                      final List<String> supportedModelDefiners,
                                                                      final List<String> supportedFlexModelDefiners,
                                                                      final boolean supportExternalCounterNames) {
        log.debug("Checking if counters {} are correct for the mim versions specified {}", counterInfo, nodesTypeVersionSet);

        final List<CounterInfo> correctCountersList = new ArrayList<>();
        try {
            //here we are use value "true" as it will retain current behaviour
            final Collection<CounterTableRow> allCounters = getCountersForAllVersions(nodesTypeVersionSet, supportedModelDefiners, supportExternalCounterNames);
            final Collection<CounterTableRow> flexCounters = getFlexCounters(nodesTypeVersionSet, supportedFlexModelDefiners, supportExternalCounterNames);
            for (final CounterInfo moClass : counterInfo) {
                if (allCounters.contains(new CounterTableRow(moClass.getName(), moClass.getMoClassType())) ||
                        flexCounters.contains(new CounterTableRow(moClass.getName(), moClass.getMoClassType()))) {
                    correctCountersList.add(moClass);
                }
            }
        } catch (final PfmDataException e) {
            log.warn("No counters were found for the supplied mim versions {}", nodesTypeVersionSet);
            log.debug(COULD_N0T_FIND_ANY_COUNTERS, e);
        }
        log.debug("Corrected counter list {}", correctCountersList);
        return correctCountersList;
    }

    /**
     * Returns filtered counters based on supported MOClassTypes. The supportedMOCs is defined in PMICFunction capability which can read based on node
     * type and subscriptionType.
     *
     * @param nodeAndVersionSet
     *         - captures all the mimVersions.
     * @param modelDefiners
     *         the entity that defined the counters (defined the model of the counters), e.g. NE or OSS. This is optional
     * @param subscriptionType
     *         - type of the subscription.
     *
     * @return collection of CounterTableRow Object
     * @throws PfmDataException
     *         - thrown if No counters found for mim versions
     */
    public Set<CounterTableRow> getFilteredCountersForAllVersions(final Set<NodeTypeAndVersion> nodeAndVersionSet,
                                                                  final List<String> modelDefiners,
                                                                  final String subscriptionType,
                                                                  final boolean supportsExternalCounterNames)
            throws PfmDataException {
        final Set<CounterTableRow> counters = new TreeSet<>();

        final List<NodeTypeAndVersion> invalidNodeTypeAndVersionList = new ArrayList<>();

        for (final NodeTypeAndVersion nodeTypeAndVersion : nodeAndVersionSet) {
            final Set<CounterTableRow> counterTableRows = new TreeSet<>();
            try {
                final List<String> supportedMOClasses = pmCapabilitiesLookup.getSupportedMOCsCapabilityValues(nodeTypeAndVersion.getNodeType(),
                        SUPPORTED_MO_CLASSES, subscriptionType);
                log.info("Supported MOcs {} found in capability for subscriptionType {} and nodeType {}", supportedMOClasses, subscriptionType,
                        nodeTypeAndVersion.getNodeType());
                //here we are use value "true" as it will retain current behaviour
                for (final String definer : modelDefiners) {
                    final Set<CounterTableRow> countersPerMim =
                            getAllPMCounters(nodeTypeAndVersion, PmMetaDataConstants.PFM_MEASUREMENT_PATTERN, definer, supportsExternalCounterNames);
                    counterTableRows.addAll(countersPerMim);
                }

                for (final CounterTableRow counter : counterTableRows) {
                    if (supportedMOClasses.contains(counter.getSourceObject())) {
                        counters.add(counter);
                        log.debug("Found Counter {} in Supported MOcs {} found in capability for subscriptionType {} and nodeType {}",
                                counter.getSourceObject(), supportedMOClasses, subscriptionType, nodeTypeAndVersion.getNodeType());
                    }
                }

            } catch (final ModelProcessingException | UnknownModelException e) {
                invalidNodeTypeAndVersionList.add(nodeTypeAndVersion);
                log.warn("No counters found for mim {}", nodeTypeAndVersion);
                log.debug(COULD_N0T_FIND_ANY_COUNTERS, e);
            }
        }
        log.debug("Found {} co for mimVersions: {}", counters.size(), nodeAndVersionSet);
        if (counters.isEmpty()) {
            final String message = String.format("No counters found for mim versions %s", invalidNodeTypeAndVersionList.toString());
            throw new PfmDataException(message);
        }
        return counters;
    }

    /**
     * Returns Moinstance/Statistical countersubgroups
     *
     * @param subscriptionType
     *         - type of the Subscription
     *
     * @return Map of countersubgroup name as key and List of counters as value
     */
    public Map<String, List<String>> getCounterSubGroups(final String subscriptionType) {
        if (subscriptionType.equals(SubscriptionType.MOINSTANCE.name())) {
            return pmCapabilitiesLookup.getSupportedCounterSubGroups(COUNTER_SUB_GROUPS);
        } else if (subscriptionType.equals(SubscriptionType.RES.name())) {
            return pmCapabilitiesLookup.getSupportedCounterSubGroups(PMFUNCTIONS_RES_SUPPORTEDCOUNTERS_NAME);
        } else {
            return pmCapabilitiesLookup.getSupportedCounterSubGroups(PMFUNCTIONS_SUPPORTEDCOUNTERS_NAME);
        }
    }

    private List<String> getNonSupportedStatsCounters(final String capabilityName) {
        final List<String> result = new ArrayList<>();
        final Map<String, List<String>> capabilityList = pmCapabilitiesLookup.getSupportedCounterSubGroups(capabilityName);
        if (capabilityList == null) {
            return emptyList();
        }
        for (final Entry<String, List<String>> capability : capabilityList.entrySet()) {
            result.addAll(capability.getValue());
        }
        return result;
    }

}
