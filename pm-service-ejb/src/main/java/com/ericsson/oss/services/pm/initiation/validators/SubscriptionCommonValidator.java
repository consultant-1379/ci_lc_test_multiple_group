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

package com.ericsson.oss.services.pm.initiation.validators;

import static com.ericsson.oss.itpf.modeling.schema.util.SchemaConstants.DPS_PRIMARYTYPE;
import static com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod.NOT_APPLICABLE;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.COUNTER_EVENTS_VALIDATION_APPLICABLE;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.MULTIPLE_SUBSCRIPTIONS_SUPPORTED;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUBSCRIPTION_ATTRIBUTES_SUFFIX;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SUPPORTED_SUBSCRIPTION_TYPES;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.cache.Cache;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.datalayer.dps.modeling.modelservice.typed.persistence.primarytype.PrimaryTypeAttributeSpecification;
import com.ericsson.oss.itpf.datalayer.dps.modeling.modelservice.typed.persistence.primarytype.PrimaryTypeSpecification;
import com.ericsson.oss.itpf.modeling.common.info.ModelInfo;
import com.ericsson.oss.itpf.modeling.modelservice.ModelService;
import com.ericsson.oss.itpf.modeling.modelservice.exception.ConstraintViolationException;
import com.ericsson.oss.itpf.sdk.cache.data.ValueHolder;
import com.ericsson.oss.pmic.api.modelservice.PmCapabilitiesLookupLocal;
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.ScheduleInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.UeType;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.common.RopUtil;
import com.ericsson.oss.services.pm.initiation.common.SubscriptionValidationResult;
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionDataCacheWrapper;
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionManager;
import com.ericsson.oss.services.pm.initiation.scanner.master.SubscriptionWrapper;
import com.ericsson.oss.services.pm.modelservice.PmCapabilities;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.oss.services.pm.services.exception.PmFunctionValidationException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;

/**
 * The Class SubscriptionCommonValidator, Utility class for Validation Methods
 */
public class SubscriptionCommonValidator {

    /**
     * List rop period invalid for subscription type and node
     */
    private static final String INVALID_ROP_PERIOD = "Invalid rop period %s for subscription type = %s and neType = %s";
    /**
     * RNC nodes cannot be mixed with other nodes in statistical subscription
     */
    private static final String RNC_NODE_TYPE = "RNC";
    /**
     * List of nodeType that don't support user defined subscriptions
     */
    private static final List<String> notAllowedNodeTypes = Arrays.asList("EPG", "FRONTHAUL-6080", "FRONTHAUL-6020", "SIU02", "SBG-IS", "TCU02",
            "VEPG", "vwMG", "WMG");
    /**
     * List of nodeType not supported by the subscription type
     */
    private static final String INVALID_NODE_TYPES = "Node Types not supported by Subscription Type";
    /**
     * List of nodes with null or empty string fdn
     */
    private static final String LOOPBACK_ADDRESS_REGEXP = "^localhost$|^127(?:\\.[0-9]+){0,2}\\.[0-9]+$|^(?:0*\\:)*?:?0*1$";
    private static final String NUMERIC_REGEX = "[0-9]+";
    /**
     * Check consistency between @class and subscription type parameters
     */
    private static final String INVALID_CLASS_AND_TYPE = "@class and type parameters are not equal: ";

    private static final String BLANK_REGEXP = "^\\s*$";

    private Map<String, Boolean> capabilitySupportMap;

    @Inject
    private Logger logger;
    @Inject
    private RopUtil ropUtil;
    @Inject
    private PmCapabilityModelService capabilityModelService;
    @Inject
    private ModelService modelService;

    @Inject
    private PmCapabilitiesLookupLocal pmCapabilitiesLookupLocal;

    @Inject
    private NodeService nodeService;
    @Inject
    private SubscriptionManager subscriptionManager;
    @Inject
    private SubscriptionDataCacheWrapper subscriptionDataCacheWrapper;

    /**
     * Checks if is stream info applicable. based on output mode
     *
     * @param outputModeType the output mode type
     * @param streamInfos    the stream infos
     * @return returns true if is has valid streamInfo and output combine value
     * @throws ValidationException thrown if eventSub is invalid
     */
    public boolean isStreamInfoApplicable(final OutputModeType outputModeType, final List<StreamInfo> streamInfos) throws ValidationException {
        boolean streaming = false;
        if (!isFileOutputMode(outputModeType, streamInfos)) {
            streaming = true;
            if (streamInfos == null || streamInfos.isEmpty()) {
                logger.error(NO_IP_OR_PORT_AVAILABLE);
                throw new ValidationException(NO_IP_OR_PORT_AVAILABLE);
            }
        }
        return streaming;
    }

    /**
     * This method checks whether the stream info validation (ipAddress and port
     * number validation) is required for the passed list of StreamInfo. For UERTT
     * there is a possibility that ipAddress and port number are kept blank in UI.
     * In this case this method return false as the validation is not required.
     *
     * @param outputModeType the output mode of the subscription.
     * @param streamInfos    List of StreamInfo for that subscription.
     * @return returns true if the subscription has valid streamInfo. Else false
     * @throws ValidationException if invalid arguments passed.
     */
    public boolean isStreamInfoCheckRequired(final OutputModeType outputModeType, final List<StreamInfo> streamInfos) throws ValidationException {
        boolean isStreamInfoCheckRequired = false;
        if (!isFileOutputMode(outputModeType, streamInfos)) {
            // Iterate through the list of StreamInfo and break the loop it at least one
            // StreamInfo is valid.
            for (final StreamInfo streamInfo : streamInfos) {
                if (!streamInfo.getIpAddress().isEmpty()) {
                    isStreamInfoCheckRequired = true;
                    break;
                }
            }
        }
        return isStreamInfoCheckRequired;
    }

    private boolean isFileOutputMode(final OutputModeType outputModeType,
                                     final List<StreamInfo> streamInfos) throws ValidationException {
        if (OutputModeType.FILE.equals(outputModeType)) {
            if (streamInfos != null && !streamInfos.isEmpty()) {
                logger.error(NOTVALID_EVENT_FILEMODE_PARAMS);
                throw new ValidationException(NOTVALID_EVENT_FILEMODE_PARAMS);
            }
            return true;
        }
        return false;
    }

    /**
     * Validate IP Addresses.
     *
     * @param streamInfo the stream info
     * @throws ValidationException the PMIC invalid input exception
     */
    public void validateIpAddresses(final List<StreamInfo> streamInfo) throws ValidationException {
        if (streamInfo.size() > 20) {
            logger.error(IP_PORT_EXCEEDES_LIMIT);
            throw new ValidationException(IP_PORT_EXCEEDES_LIMIT);
        }
        for (final StreamInfo stream : streamInfo) {
            if (!isIPV4Address(stream) && !isIPV6Address(stream)) {
                throw new ValidationException(INVALID_IP_FORMAT);
            }
            final Pattern loopbackAddressPattern = Pattern.compile(LOOPBACK_ADDRESS_REGEXP);
            if (loopbackAddressPattern.matcher(stream.getIpAddress()).matches()) {
                throw new ValidationException(INVALID_IP_FORMAT);
            }
        }
    }

    /**
     * Ensure subscription has correct UE Info data.
     *
     * @param ueInfos the UE Infos
     * @throws ValidationException if UE Info value is invalid
     */
    public void validateUeInfos(final List<UeInfo> ueInfos) throws ValidationException {
        logger.debug("Checking the UE Info value {} is valid", ueInfos);
        for (final UeInfo ueInfo : ueInfos) {
            validateUeInfo(ueInfo.getType(), ueInfo.getValue());
        }
    }

    private static void validateUeInfo(final UeType ueType, final String ueTraceValue) throws ValidationException {
        switch (ueType) {
            case IMEI:
                validateIMEI(ueTraceValue);
                break;
            case IMEI_SOFTWARE_VERSION:
                validateIMEISoftwareVersion(ueTraceValue);
                break;
            case IMSI:
                validateIMSI(ueTraceValue);
        }
    }

    private static void validateIMEI(final String imei) throws ValidationException {
        if (!(imei.matches(NUMERIC_REGEX) && imei.length() == 15)) {
            throw new ValidationException(INVALID_IMEI_FORMAT);
        }
    }

    private static void validateIMEISoftwareVersion(final String imeiSoftwareVersion) throws ValidationException {
        if (!(imeiSoftwareVersion.matches(NUMERIC_REGEX) && imeiSoftwareVersion.length() == 16)) {
            throw new ValidationException(INVALID_IMEI_SOFTWARE_VERSION_FORMAT);
        }
    }

    private static void validateIMSI(final String imsi) throws ValidationException {
        if (!(imsi.matches(NUMERIC_REGEX) && imsi.length() >= 5 && imsi.length() <= 15)) {
            throw new ValidationException(INVALID_IMSI_FORMAT);
        }
    }

    /**
     * Validate schedule info.
     *
     * @param subscription the subscription
     * @return - returns a SubscriptionValidationResult
     * @throws ValidationException - thrown if scheduleInfo/roPeriod is invalid
     */
    public SubscriptionValidationResult validateScheduleInfo(final Subscription subscription) throws ValidationException {

        if (subscription.getAdministrationState() == AdministrationState.ACTIVE) {
            return new SubscriptionValidationResult();
        }

        final ScheduleInfo scheduleInfo = subscription.getScheduleInfo();
        final RopPeriod ropPeriod = subscription.getRop();

        // Get the start, end and rop information from the scheduleInfo
        final Date currentTime = new Date();
        Date startDateTime = scheduleInfo.getStartDateTime() != null ? scheduleInfo.getStartDateTime() : currentTime;
        final Date endDateTime = scheduleInfo.getEndDateTime();

        // if end Date is filled in, it must be > than start date
        if (endDateTime != null) {
            if (endDateTime.before(currentTime)) {
                // end date should be after current time
                return new SubscriptionValidationResult(ENDTIME_GREATER_CURRENT_TIME, currentTime);
            } else if (endDateTime.compareTo(startDateTime) <= 0) {
                return new SubscriptionValidationResult(ENDTIME_GREATER_START_TIME);
            }

            if (ropPeriod != NOT_APPLICABLE) {
                if (startDateTime.before(currentTime)) {
                    startDateTime = currentTime;
                }
                final Date nextRop = ropUtil.nextRop(ropPeriod.getDurationInSeconds(), startDateTime);
                final Date nextRopPlusOne = ropUtil.nextRop(ropPeriod.getDurationInSeconds(), nextRop);

                if (endDateTime.compareTo(nextRopPlusOne) < 0) {
                    return new SubscriptionValidationResult(ENDTIME_EQUAL_OR_BEFORE_CURRENTTIME_PLUS_ROP, nextRopPlusOne);
                }
            }
        }
        // no error
        return new SubscriptionValidationResult();
    }

    /**
     * Validate subscription name.
     *
     * @param name    the name
     * @param pattern the pattern
     * @throws ValidationException - thrown if subscription name is invalid
     */
    public void validateSubscriptionName(final String name, final String pattern) throws ValidationException {
        if (name == null) {
            logger.error(SUBSCRIPTION_NAME_TAG_MISSING);
            throw new ValidationException(SUBSCRIPTION_NAME_TAG_MISSING);
        } else if (name.trim().isEmpty()) {
            logger.error(EMPTY_SUBSCRIPTION_NAME);
            throw new ValidationException(name + EMPTY_SUBSCRIPTION_NAME);
        } else if (!name.matches(pattern)) {
            logger.error("Name : {} {}", name, NOTVALID_SUBSCRIPTION_NAME);
            throw new ValidationException(name + NOTVALID_SUBSCRIPTION_NAME);
        }
    }

    /**
     * Validate subscription type.
     *
     * @param subscriptionType - type of subscription to validate that it is not null
     * @throws ValidationException - thrown if subscriptionType is invalid
     */
    public void validateSubscriptionType(final SubscriptionType subscriptionType) throws ValidationException {
        if (subscriptionType == null) {
            logger.error(NOTVALID_SUBSCRIPTION_TYPE);
            throw new ValidationException(NOTVALID_SUBSCRIPTION_TYPE);
        }
    }

    /**
     * Check if subscription type is equal to subscription class
     *
     * @param subscription - the subscription
     * @param <S>          - Subscription type
     * @throws ValidationException - thrown if subscriptionType is invalid
     */
    public static <S extends Subscription> void validateSubscriptionType(final S subscription) throws ValidationException {
        if (subscription.getType().getIdentifier() != subscription.getClass()) {
            throw new ValidationException(
                    INVALID_CLASS_AND_TYPE + "@Class= " + subscription.getClass().getName() + " Type= " + subscription.getType().toString());
        }
    }

    /**
     * Validate user type.
     *
     * @param userType - type of user to validate that it is not null
     * @throws ValidationException - thrown if userType is invalid
     */
    public void validateUserType(final UserType userType) throws ValidationException {
        if (userType == null) {
            logger.error(NOTVALID_USER_TYPE);
            throw new ValidationException(NOTVALID_USER_TYPE);
        }
    }

    /**
     * Checks if list of NodeType of the subscription is supported by the
     * SubscriptionType
     *
     * @param subscription - the subscription
     * @throws ValidationException - the PMIC invalid input exception
     */
    public void checkCapabilitySupportedNodesBySubscriptionType(final ResourceSubscription subscription) throws ValidationException {
        String nodeType;
        final Set<String> invalidNodeTypeList = new TreeSet<>();
        final Set<NodeTypeAndVersion> nodeTypeVersionList = subscription.getNodesTypeVersion();

        for (final NodeTypeAndVersion nodeTypeVersion : nodeTypeVersionList) {
            nodeType = nodeTypeVersion.getNodeType();
            final List<String> subscriptionTypeList = capabilityModelService.getSupportedCapabilityValues(nodeType, SUPPORTED_SUBSCRIPTION_TYPES);
            if (!subscriptionTypeList.contains(subscription.getType().toString())) {
                invalidNodeTypeList.add(nodeType);
            }
        }
        if (!invalidNodeTypeList.isEmpty()) {
            throw new ValidationException(INVALID_NODE_TYPES + invalidNodeTypeList);
        }
    }

    /**
     * Checks if RopPeriod of the subscription is supported by the SubscriptionType
     * and nodes
     *
     * @param subscription - the subscription
     * @throws ValidationException - the PMIC invalid input exception
     */
    public void checkCapabilitySupportedRopPeriod(final ResourceSubscription subscription) throws ValidationException {
        final RopPeriod subscriptionRop = subscription.getRop();
        final SubscriptionType subType = subscription.getType();
        final Set<NodeTypeAndVersion> nodesTypeVersion = subscription.getNodesTypeVersion();
        for (final NodeTypeAndVersion node : nodesTypeVersion) {
            final String neType = node.getNodeType();
            final List<Integer> suppRops = capabilityModelService.getNodeAndSubscriptionTypeSupportedRops(neType, subType);
            final int duration = subscriptionRop.getDurationInSeconds();
            if (!subscriptionRop.equals(NOT_APPLICABLE) && !suppRops.contains(duration)) {
                throw new ValidationException(String.format(INVALID_ROP_PERIOD, subscriptionRop.name(), subType.name(), neType));
            }
        }
    }

    /**
     * Detects incompatible counters among those provided from imported subscription
     *
     * @param subscriptionCounters - counters provided from imported subscription
     * @param nodeCounter          - counters compatible with nodes
     * @param nodeList             - nodes list provided in imported subscription
     * @throws ValidationException - thrown if some counters provided from imported subscription are
     *                             invalid or nodes list is empty
     */
    public static void detectsIncompatibleCounters(final List<CounterInfo> subscriptionCounters, final List<CounterInfo> nodeCounter,
                                                   final List<Node> nodeList) throws ValidationException {

        if (!subscriptionCounters.isEmpty() && nodeList.isEmpty()) {
            throw new ValidationException("Nodes list in imported subscription is empty");
        }

        final Set<CounterInfo> incompatible = new HashSet<>(subscriptionCounters);
        incompatible.removeAll(nodeCounter);

        throw new ValidationException("Counters provided from imported subscription are invalid: " + incompatible.toString());
    }

    private static boolean isIPV4Address(final StreamInfo streamInfo) {
        final String ipV4Regex = "\\A(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z";
        return streamInfo.getIpAddress().matches(ipV4Regex);
    }

    /**
     * checks if UeFractioon is in the allowed range
     *
     * @param subscription - the subscription
     * @throws ValidationException - if passed value is out of allowed range
     */
    public void checkUeFraction(final CellTraceSubscription subscription) throws ValidationException {

        checkUeFractionConstraints(subscription.getUeFraction());
    }

    private void checkUeFractionConstraints(final int ueFraction) throws ValidationException {

        final String NAMESPACE = "pmic_cell_subscription";
        final String NAME = "CellTraceSubscription";
        final String VERSION = "*";

        final ModelInfo modelInfo = new ModelInfo(DPS_PRIMARYTYPE, NAMESPACE, NAME, VERSION);
        final PrimaryTypeSpecification typeSpec = modelService.getTypedAccess().getEModelSpecification(modelInfo, PrimaryTypeSpecification.class);
        final PrimaryTypeAttributeSpecification attributeSpec = typeSpec.getAttributeSpecification("ueFraction");

        try {
            attributeSpec.getDataTypeSpecification().checkConstraints(ueFraction);
        } catch (final ConstraintViolationException e) {
            throw new ValidationException(e.getMessage() + " " + attributeSpec.getDescription());
        }
    }

    private static boolean isIPV6Address(final StreamInfo streamInfo) {
        final String ipV6Regex = "^\\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}"
                + "|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))"
                + "|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)"
                + "(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})"
                + "|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))"
                + "|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)"
                + "(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})"
                + "|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))"
                + "|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)"
                + "(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})"
                + "|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:)))"
                + "(%.+)?\\s*$";
        return streamInfo.getIpAddress().matches(ipV6Regex);
    }

    /**
     * Check counter verification capability for the nodes present in subscription.
     *
     * @param subscription the subscription
     * @return - returns whether capability is enabled for disabled for given target
     * type
     */
    public boolean isEventCounterVerificationNeeded(final ResourceSubscription subscription) {
        try {
            Set<String> nodeTypes = new HashSet<>();
            return subscription.getNodes().stream().allMatch(node -> {
                if (!nodeTypes.contains(node.getNeType())) {
                    nodeTypes.add(node.getNeType());
                    final Boolean isEventCounterVerificationNeeded = (Boolean) pmCapabilitiesLookupLocal.getCapabilityValue(node.getNeType(),
                        subscription.getType().name() + SUBSCRIPTION_ATTRIBUTES_SUFFIX, COUNTER_EVENTS_VALIDATION_APPLICABLE);
                    if (isEventCounterVerificationNeeded != null && !isEventCounterVerificationNeeded) {
                        logger.info("one of the nodes is not having counter validation. So skipping for this set and returning true");
                        return false;
                    }
                }
                return true;
            });
        } catch (final IllegalArgumentException | RuntimeDataAccessException e) {
            logger.error("Failed to get target Type for fdns {} due to {}", subscription.getNodesFdns(), e.getMessage());
            logger.info("Failed to get target Type due to", e);
        } catch (final Exception ex) {
            logger.error("Failed to get capability for fdns {}, due to {} returning default value in case of exception", subscription.getNodesFdns(),
                    ex.getMessage());
            logger.info("Failed to get capability for fdns {}, due to error returning default value in case of exception",
                    subscription.getNodesFdns(), ex);
        }
        return true;
    }

    /**
     * Check if there are incompatible node types with in a given subscription.
     *
     * @param subscription the subscription
     * @throws ValidationException - thrown if node types are incompatible
     */
    public void checkForInvalidNodeTypesInSubscription(final ResourceSubscription subscription) throws ValidationException {

        final Set<String> neTypesInSubscription = new HashSet<>();
        final Set<NodeTypeAndVersion> nodeTypeVersionList = subscription.getNodesTypeVersion();
        logger.debug("Checking for invalid node types in subscription: {}", nodeTypeVersionList);
        final Map<String, List<String>> supportedNodeTypesToBeAllowed = capabilityModelService
                .getNodeTypeAndSubscriptionSupportedOtherNodeTypes(subscription.getType());

        for (final NodeTypeAndVersion node : nodeTypeVersionList) {
            neTypesInSubscription.add(node.getNodeType());
        }

        for (final String neTypeNotAllowed : notAllowedNodeTypes) {
            if (neTypesInSubscription.contains(neTypeNotAllowed)) {
                throw new ValidationException("Not allowed nodeTypes are present in the subscription: " + neTypeNotAllowed);
            }
        }
        logger.debug("Checking for invalid node types in subscription: {}", subscription.getType());
        if (neTypesInSubscription.contains(RNC_NODE_TYPE)) {
            for (final String nodesType : neTypesInSubscription) {
                if (!nodesType.equals(RNC_NODE_TYPE)) {
                    throw new ValidationException(
                            "RNC cannot be combined with other NE types. ");
                }
            }

        }
        final Iterator<Entry<String, List<String>>> restrictedNodetypesList = supportedNodeTypesToBeAllowed.entrySet().iterator();
        while (restrictedNodetypesList.hasNext()) {
            final String nodeType = restrictedNodetypesList.next().getKey();
            if (neTypesInSubscription.contains(nodeType) && !supportedNodeTypesToBeAllowed.get(nodeType).containsAll(neTypesInSubscription)) {
                throw new ValidationException("Incompatible nodeTypes are present in the subscription for nodeType:" + nodeType);

            }
        }
    }

    /**
     * Check if there are nodes with pmEnabled set to false.
     *
     * @param nodes the list of nodes
     * @throws ValidationException - thrown if there are nodes with pmEnabled set to false
     */
    public void validatePmFunction(final List<Node> nodes) throws ValidationException {
        if (nodes == null) {
            return;
        }
        final List<Node> nodesWithPmDisabled = new ArrayList<>();
        for (final Node node : nodes) {
            populateNodesWithPmDisabled(nodesWithPmDisabled, node);
        }
        validateNodesWithPmDisabled(nodes, nodesWithPmDisabled);
    }

    private static void validateNodesWithPmDisabled(final List<Node> nodes, final List<Node> nodesWithPmDisabled) throws PmFunctionValidationException {
        if (!nodesWithPmDisabled.isEmpty()) {
            final String message = nodesWithPmDisabled.size() == nodes.size() ? "All nodes are unavailable due to PM Function disabled"
                    : "There are unavailable nodes due to PM Function disabled";
            throw new PmFunctionValidationException(nodesWithPmDisabled, message);
        }
    }

    private void populateNodesWithPmDisabled(final List<Node> nodesWithPmDisabled, final Node node) {
        if (!isPmFunctionEnabled(node)) {
            nodesWithPmDisabled.add(node);
        }
    }

    private static boolean isTechnologyDomainSupported(final Node node, final List<String> technologyDomainsSupportedBySubscription) {
        for (final String technologyDomainSupportedBySubscription : technologyDomainsSupportedBySubscription) {
            if (node.getTechnologyDomain().contains(technologyDomainSupportedBySubscription)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validate node imported with valid technologyDomain and pmFunctionEnabled.
     *
     * @param nodes                                    the list of nodes
     * @param technologyDomainsSupportedBySubscription the list of technologyDomain
     * @throws ValidationException - thrown if there are nodes with pmEnabled set to false
     */
    public void validateApplicableNodesForSubscription(final List<Node> nodes, final List<String> technologyDomainsSupportedBySubscription)
            throws ValidationException {
        final List<Node> nodesWithPmDisabled = new ArrayList<>();
        final List<String> nodesNamesWithUnsupportedTechnologyDomain = new ArrayList<>();
        for (final Node node : nodes) {
            if (!isTechnologyDomainSupported(node, technologyDomainsSupportedBySubscription)) {
                nodesNamesWithUnsupportedTechnologyDomain.add(node.getName());
            }
            populateNodesWithPmDisabled(nodesWithPmDisabled, node);
        }
        if(!nodesNamesWithUnsupportedTechnologyDomain.isEmpty()){
            logger.info("Unable to import subscription due to unsupported technology domain for {}", nodesNamesWithUnsupportedTechnologyDomain);
            throw new ValidationException("Unable to import subscription :Nodes with unsupported technology domain:"
                    +nodesNamesWithUnsupportedTechnologyDomain.toString());
        }
        validateNodesWithPmDisabled(nodes, nodesWithPmDisabled);
    }

    /**
     * Check if node has got pmEnabled set to false or not.
     *
     * @param node the node
     * @return - returns whether pmEnabled is enabled for given node
     */
    private boolean isPmFunctionEnabled(final Node node) {
        return nodeService.isPmFunctionEnabled(node.getFdn());
    }

    /**
     * check if counters supported provided in the import subscription
     *
     * @param neType         the neType
     * @param type           the susbcription type
     * @param listOfCounters the counters list
     * @param ropPeriod      the rop period
     * @throws ValidationException - thrown if counter not supported for this rop
     */
    public void validateCountersForNotSupportedRops(final String neType, final SubscriptionType type,
                                                    final List<CounterInfo> listOfCounters, final RopPeriod ropPeriod)
            throws ValidationException {
        final String countersSupportedRopsList = capabilityModelService.getNodeSupportedRopsForCounters(neType, type.name());
        if (countersSupportedRopsList != null) {
            final Set<String> counterMoClassSet = new HashSet<>();
            for (final CounterInfo counter : listOfCounters) {
                counterMoClassSet.add(counter.getMoClassType());
            }
            for (final String countersSupportedRopsArray : countersSupportedRopsList.split(",")) {
                final String[] countersSupportedRops = countersSupportedRopsArray.split(":");
                if (countersSupportedRops.length > 0 && counterMoClassSet.contains(countersSupportedRops[0])
                        && !countersSupportedRops[1].contains(String.valueOf(ropPeriod.getDurationInSeconds()))) {
                    throw new ValidationException(countersSupportedRops[0] + "counters not supported for this ROP :" + ropPeriod.toString());
                }
            }
        }
    }

    public static void validateDescription(final String description) throws ValidationException {
        if (isBlankDescription(description)) {
            throw new ValidationException("It is required to provide a justification in the description before activating a trace subscription.");
        }
    }

    private static boolean isBlankDescription(final String description) {
        return description == null || description.matches(BLANK_REGEXP);
    }

    public void validateMultipleSubscriptionAllowed(final String neType, final String capabilityModelName, final ResourceSubscription subscription) throws ValidationException {
        final Map<String, List<String>> activeSubscriptionHasNodeFdn = new HashMap<>();
        boolean supportedMultipleSubscription = true;
        if (capabilitySupportMap != null && capabilitySupportMap.get(neType) != null) {
            supportedMultipleSubscription = capabilitySupportMap.get(neType);
        } else {
            supportedMultipleSubscription = isCapabilitySupportMultipleSubscription(neType, capabilityModelName, MULTIPLE_SUBSCRIPTIONS_SUPPORTED);
        }
        if (!supportedMultipleSubscription) {
            final Iterator<Cache.Entry<String, ValueHolder>> cache = subscriptionDataCacheWrapper.getCache().iterator();
            while (cache.hasNext()) {
                final Cache.Entry<String, ValueHolder> values = cache.next();
                final String key = values.getKey();
                if (key.endsWith(subscription.getType().name())) {
                    final SubscriptionWrapper subscriptionWrapper = (SubscriptionWrapper) values.getValue().getData().get(key);
                    validateActiveSubscriptionHasNodeFdn(subscriptionWrapper.getAllNodeFdns(), subscription.getNodesFdns(), subscriptionWrapper.getName(), subscription.getName(), activeSubscriptionHasNodeFdn);
                }
            }
        }

        if (!activeSubscriptionHasNodeFdn.isEmpty()) {
            throw new ValidationException(String.format(SOME_NODES_ALREADY_ACTIVE, activeSubscriptionHasNodeFdn));
        } else {
            try {
                subscriptionManager.addOrUpdateCacheWithSubscriptionData(subscription);
            } catch (final Exception e) {
                logger.error("Error while adding or updating cache {}", e.getStackTrace());
            }
        }
    }

    private static Map<String, List<String>> validateActiveSubscriptionHasNodeFdn(final Set<String> nodesFdns, final Set<String> subscriptionNodeFdns, final String subscriptionName, final String currentSubscriptionName, final Map<String, List<String>> activeSubscriptionHasNodeFdn) {
        List<String> nodeList = null;
        if (!subscriptionName.equals(currentSubscriptionName) && nodesFdns != null) {
            nodesFdns.retainAll(subscriptionNodeFdns);
            if (!nodesFdns.isEmpty()) {
                nodeList = new ArrayList<>();
                nodeList.addAll(nodesFdns);
                activeSubscriptionHasNodeFdn.put(subscriptionName, nodeList);
            }
        }
        return activeSubscriptionHasNodeFdn;
    }

    private boolean isCapabilitySupportMultipleSubscription(final String neType, final String capabilityModelName, final String multipleSubscriptionSupported) {
        final PmCapabilities pmCapabilities = capabilityModelService.getCapabilityForTargetTypeByFunction(capabilityModelName, neType, multipleSubscriptionSupported);
        if (pmCapabilities == null || pmCapabilities.getTargetTypes().isEmpty() ||
                pmCapabilities.getTargetTypes().get(0).getCapabilities().isEmpty()) {
            return true;
        }
        if (capabilitySupportMap == null) {
            capabilitySupportMap = new HashMap<>();
        }
        final boolean capabilitySupported = (boolean) pmCapabilities.getTargetTypes().get(0).getCapabilities()
                .get(multipleSubscriptionSupported);
        capabilitySupportMap.put(neType, capabilitySupported);

        return capabilitySupported;
    }
}
