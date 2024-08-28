/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2017
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */
package com.ericsson.oss.services.pm.initiation.validators;

import static com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription.CellTraceSubscription210Attribute;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.ASR;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_AND_EBSL_FILE;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_AND_EBSL_STREAM;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_NRAN;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_FILE;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.CELLTRACE_NRAN_AND_EBSN_STREAM;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.EBSL_STREAM;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.ESN;
import static com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory.NRAN_EBSN_STREAM;
import static com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType.STREAMING;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.COUNTERS_NOT_ALLOWED_FOR_EBS_FILE_WITH_STREAMING_OUTPUTMODE;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.COUNTERS_NOT_ALLOWED_FOR_STANDARD_CELLTRACE;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.EBS_STREAMINFO_REQUIRED_FORMATTER;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.EXPORT_NOT_SUPPORTED_FOR_ESN_SUBSCRIPTION_FORMATTER;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.IMPORTING_COUNTERS_EVENTS_WITHOUT_NODES;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.IMPORT_NOT_SUPPORTED_FOR_ESN_SUBSCRIPTION_FORMATTER;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_CELLTRACE_CATEGORY;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_EVENTS_FOR_IMPORT;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_FILE_AND_STREAM_CELLTRACE_CATEGORY;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_FILE_CELLTRACE_CATEGORY;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_NULL_CELLTRACE_CATEGORY;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_STREAM_CELLTRACE_CATEGORY;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.INVALID_SUBSCRITPION_TO_ACTIVATE;
import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.UNABLE_TO_ACTIVATE_ESN;
import static com.ericsson.oss.services.pm.initiation.utils.CommonUtil.isNotEmptyCollection;
import static com.ericsson.oss.services.pm.initiation.utils.CommonUtil.isNullOrEmptyCollection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.EventInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.ebs.utils.EbsSubscriptionHelper;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.oss.services.pm.services.exception.ValidationException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;

/**
 * This class validates CellTrace Subscription
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.CELLTRACE)
public class CellTraceSubscriptionValidator extends EventSubscriptionValidator<CellTraceSubscription> {

    @Inject
    private EbsSubscriptionHelper ebsSubscriptionHelper;

    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;

    @Override
    public void validate(final CellTraceSubscription subscription) throws ValidationException {
        super.validate(subscription);
        validateEbsCounters(subscription);
        validateCelltraceCategory(subscription);
    }

    @Override
    public void validateImport(final CellTraceSubscription subscription) throws ValidationException, PfmDataException {
        validateUnSupportImportSubscription(subscription);
        super.validateImport(subscription);
        validateEventsForImportedSubscription(subscription);
        subscriptionCommonValidator.checkUeFraction(subscription);

        final List<CounterInfo> counterInfoList = subscription.getEbsCounters();
        final List<EventInfo> eventInfoList = subscription.getEvents();
        final List<EventInfo> ebsEventInfoList = subscription.getEbsEvents();

        if (hasAnyEventsOrCounters(counterInfoList, ebsEventInfoList, eventInfoList) && isNullOrEmptyCollection(subscription.getNodes())) {
            throw new ValidationException(IMPORTING_COUNTERS_EVENTS_WITHOUT_NODES);
        }

        if (isNotEmptyCollection(counterInfoList)) {
            if (!ebsSubscriptionHelper.isCellTraceEbs(subscription)) {
                throw new ValidationException(COUNTERS_NOT_ALLOWED_FOR_STANDARD_CELLTRACE);
            } else if (subscription.getCellTraceCategory() == CELLTRACE_AND_EBSL_FILE && subscription.getOutputMode() == STREAMING) {
                throw new ValidationException(COUNTERS_NOT_ALLOWED_FOR_EBS_FILE_WITH_STREAMING_OUTPUTMODE.replace("{}", subscription.getName()));
            }
            populateSubscriptionWithMissedEventsBasedOnCounters(subscription);
        }

        validateCelltraceCategory(subscription);
    }

    private static boolean hasAnyEventsOrCounters(final List<CounterInfo> counterInfoList, final List<EventInfo> ebsEventInfoList,
                                                  final List<EventInfo> eventInfoList) {
        return isNotEmptyCollection(counterInfoList) || isNotEmptyCollection(eventInfoList) || isNotEmptyCollection(ebsEventInfoList);
    }

    @Override
    public void validateExport(final CellTraceSubscription subscription) throws ValidationException {
        super.validateExport(subscription);
        if (subscription.getIsExported() && subscription.getCellTraceCategory().isOneOf(CellTraceCategory.ESN)) {
            throw new ValidationException(String.format(EXPORT_NOT_SUPPORTED_FOR_ESN_SUBSCRIPTION_FORMATTER, subscription.getName()));
        }
    }

    @Override
    public void validateActivation(final CellTraceSubscription subscription) throws ValidationException {
        super.validateActivation(subscription);
        validateOnlyOneSubscriptionCanBeActive(subscription);
        validateEventsAreNeededForActivation(subscription);
        validateStreamClusterIsNeededForActivation(subscription);
        validateEbsCounters(subscription);
    }

    private void validateEbsCounters(final CellTraceSubscription subscription) throws ValidationException {
        if (!ebsSubscriptionHelper.isCellTraceEbs(subscription) || !subscription.getIsImported()) {
            return;
        }
        final List<String> modelDefiners = ebsSubscriptionHelper.getEbsModelDefiners(subscription.getCellTraceCategory());
        final List<CounterInfo> correctCounters;
        final List<String> flexModelDefiners = ebsSubscriptionHelper.getEbsFlexModelDefiners(subscription.getCellTraceCategory());
        correctCounters = subscriptionPfmData.getCorrectCounters(subscription.getName(), subscription.getEbsCounters(),
            subscription.getNodesTypeVersion(), modelDefiners, flexModelDefiners, true);
        if (correctCounters.size() != subscription.getEbsCounters().size()) {
            SubscriptionCommonValidator.detectsIncompatibleCounters(subscription.getEbsCounters(), correctCounters, subscription.getNodes());
        }
    }

    private void validateEventsAreNeededForActivation(final CellTraceSubscription subscription) throws ValidationException {
        if (subscriptionMissingRequiredEvents(subscription)) {
            throw new ValidationException(String.format(INVALID_SUBSCRITPION_TO_ACTIVATE, subscription.getName()));
        }
    }

    private boolean subscriptionMissingRequiredEvents(final CellTraceSubscription subscription) {
        if (ebsSubscriptionHelper.isEbsStream(subscription)) {
            return subscription.getEbsEvents().isEmpty();
        }
        return subscription.getEvents().isEmpty();
    }

    private void validateStreamClusterIsNeededForActivation(final CellTraceSubscription subscription) throws ValidationException {
        if (ebsSubscriptionHelper.isEbsStream(subscription) && subscription.getEbsStreamInfoList().isEmpty()) {
            throw new ValidationException(String.format(EBS_STREAMINFO_REQUIRED_FORMATTER, subscription.getName()));
        }
    }

    private void validateOnlyOneSubscriptionCanBeActive(final CellTraceSubscription subscription) throws ValidationException {
        if (subscription.getCellTraceCategory() == CellTraceCategory.ESN) {
            final String activeEsnSubscriptionName = getAlreadyActiveEsnSubscriptionName();
            if (activeEsnSubscriptionName != null) {
                throw new ValidationException(String.format(UNABLE_TO_ACTIVATE_ESN, activeEsnSubscriptionName));
            }
        }
    }

    private static void validateUnSupportImportSubscription(final CellTraceSubscription subscription) throws ValidationException {
        if (subscription.getIsImported() && subscription.getCellTraceCategory().isOneOf(CellTraceCategory.ESN)) {
            throw new ValidationException(String.format(IMPORT_NOT_SUPPORTED_FOR_ESN_SUBSCRIPTION_FORMATTER, subscription.getName()));
        }
    }

    private void populateSubscriptionWithMissedEventsBasedOnCounters(final CellTraceSubscription subscription)
            throws ValidationException, PfmDataException {
        final Set<EventInfo> ebsEvents = subscriptionPfmData.validateEventBasedCounters(subscription.getNodesTypeVersion(),
                ebsSubscriptionHelper.getEbsModelDefiners(subscription.getCellTraceCategory()),
                subscription.getEbsCounters(), ebsSubscriptionHelper.getEbsSubscriptionCapability(subscription.getCellTraceCategory()));
        if (ebsSubscriptionHelper.isEbsStream(subscription)) {
            final Set<EventInfo> eventsWithoutDuplicates = new HashSet<>(subscription.getEvents());
            subscription.setEvents(new ArrayList<>(eventsWithoutDuplicates));
            subscription.setEbsEvents(new ArrayList<>(ebsEvents));
        } else {
            ebsEvents.addAll(subscription.getEvents());
            subscription.setEvents(new ArrayList<>(ebsEvents));
        }
    }

    private void validateEventsForImportedSubscription(final CellTraceSubscription subscription) throws PfmDataException {
        List<EventInfo> correctEvents = new ArrayList<>();
        if (ebsSubscriptionHelper.isEbsStream(subscription)) {
            final List<EventInfo> correctEbsEvents = subscriptionPfmData.getCorrectEvents(subscription.getName(), subscription.getEbsEvents(),
                    subscription.getNodesTypeVersion());
            compareEvents(correctEbsEvents, subscription.getEbsEvents());
            if (isNotEmptyCollection(subscription.getEvents())) {
                correctEvents = subscriptionPfmData.getCorrectEvents(subscription.getName(), subscription.getEvents(),
                        subscription.getNodesTypeVersion());
            }
        } else {
            correctEvents = subscriptionPfmData
                    .getCorrectEvents(subscription.getName(), subscription.getEvents(), subscription.getNodesTypeVersion());
        }
        compareEvents(correctEvents, subscription.getEvents());
    }

    /**
     * Validate celltrace category.
     *
     * @param subscription the subscription
     * @throws ValidationException the validation exception
     */
    private void validateCelltraceCategory(final CellTraceSubscription subscription) throws ValidationException {
        final CellTraceCategory cellTraceCategory = subscription.getCellTraceCategory();
        final List<CounterInfo> counterInfos = subscription.getEbsCounters();
        final List<EventInfo> ebsEventInfos = subscription.getEbsEvents();
        final List<EventInfo> eventInfos = subscription.getEvents();

        if (cellTraceCategory == null) {
            throw new ValidationException(String.format(INVALID_NULL_CELLTRACE_CATEGORY, subscription.getName()));
        } else if (isNotEmptyCollection(ebsEventInfos) && isNotEmptyCollection(eventInfos)) {
            validateCellTraceFileAndEbsStreamCategory(subscription);
        } else if (isNotEmptyCollection(ebsEventInfos)) {
            validateEbsStreamOnlyCategory(subscription);
        } else if (isNotEmptyCollection(counterInfos)) {
            validateCellTraceAndEbsFileCategory(subscription, cellTraceCategory);
        } else if (isNotEmptyCollection(eventInfos)) {
            validateCellTraceFileCategory(subscription, cellTraceCategory);
        }
    }

    private static void validateCellTraceFileCategory(final CellTraceSubscription subscription, final CellTraceCategory cellTraceCategory)
            throws ValidationException {
        if (!cellTraceCategory.isOneOf(CELLTRACE, CELLTRACE_NRAN)) {
            throw new ValidationException(
                    String.format(INVALID_CELLTRACE_CATEGORY, subscription.getName(), Arrays.asList(CELLTRACE, CELLTRACE_NRAN)));
        }
    }

    private static void validateCellTraceAndEbsFileCategory(final CellTraceSubscription subscription, final CellTraceCategory cellTraceCategory)
            throws ValidationException {
        if (!cellTraceCategory.isOneOf(CELLTRACE_AND_EBSL_FILE, CELLTRACE_NRAN_AND_EBSN_FILE)) {
            throw new ValidationException(String.format(
                    INVALID_FILE_CELLTRACE_CATEGORY, subscription.getName(), Arrays.asList(CELLTRACE_AND_EBSL_FILE, CELLTRACE_NRAN_AND_EBSN_FILE)));
        }
    }

    private void validateEbsStreamOnlyCategory(final CellTraceSubscription subscription) throws ValidationException {
        if (!ebsSubscriptionHelper.isEbsStreamOnlyCategory(subscription)) {
            throw new ValidationException(String.format(INVALID_STREAM_CELLTRACE_CATEGORY, subscription.getName(), EBSL_STREAM, NRAN_EBSN_STREAM, ASR, ESN));
        }
    }

    private void validateCellTraceFileAndEbsStreamCategory(final CellTraceSubscription subscription) throws ValidationException {
        if (!ebsSubscriptionHelper.isBothEbsStreamAndFileCategory(subscription)) {
            throw new ValidationException(String.format(INVALID_FILE_AND_STREAM_CELLTRACE_CATEGORY, subscription.getName(),
                    CELLTRACE_AND_EBSL_STREAM, CELLTRACE_NRAN_AND_EBSN_STREAM, ASR, ESN));
        }
    }

    /**
     * Compare size of correctEvents and given events.
     *
     * @param correctEvents events from subscriptionPfmData
     * @param events        events from subscription
     */
    private static void compareEvents(final List<EventInfo> correctEvents, final List<EventInfo> events) throws PfmDataException {
        if (correctEvents.size() != events.size()) {
            final Set<EventInfo> incompatible = new HashSet<>(events);
            incompatible.removeAll(correctEvents);
            throw new PfmDataException(INVALID_EVENTS_FOR_IMPORT + incompatible.toString());
        }
    }

    private String getAlreadyActiveEsnSubscriptionName() throws ValidationException {
        try {
            final AdministrationState[] administrationStates = {AdministrationState.ACTIVATING,
                    AdministrationState.ACTIVE,
                    AdministrationState.UPDATING};
            final Map<String, List<Object>> attributes = new HashMap<>();
            attributes.put(CellTraceSubscription210Attribute.cellTraceCategory.name(), Collections.<Object>singletonList(ESN.name()));
            final List<Subscription> subscriptions = subscriptionReadOperationService
                    .findAllWithSubscriptionTypeAndMatchingAttributes(SubscriptionType.CELLTRACE, attributes, false);
            for (final Subscription subscription : subscriptions) {
                if (subscription.getAdministrationState().isOneOf(administrationStates)) {
                    return subscription.getName();
                }
            }
            return null;
        } catch (final DataAccessException exception) {
            throw new ValidationException(exception.getMessage());
        }
    }
}
