/*
 * ------------------------------------------------------------------------------
 *  ********************************************************************************
 *  * COPYRIGHT Ericsson  2016
 *  *
 *  * The copyright to the computer program(s) herein is the property of
 *  * Ericsson Inc. The programs may be used and/or copied only with written
 *  * permission from Ericsson Inc. or in accordance with the terms and
 *  * conditions stipulated in the agreement/contract under which the
 *  * program(s) have been supplied.
 *  *******************************************************************************
 *  *----------------------------------------------------------------------------
 */

package com.ericsson.oss.services.pm.initiation.model.metadata;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.pmic.util.CollectionUtil;
import com.ericsson.oss.services.pm.common.constants.PmFeature;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.services.pm.initiation.restful.api.CounterTableRow;
import com.ericsson.services.pm.initiation.restful.api.EventBasedCounterTableRow;
import com.ericsson.services.pm.initiation.restful.api.EventTableRow;
import com.ericsson.services.pm.initiation.restful.api.PmMimVersionQuery;

/**
 * This class is responsible to fetch EBS counters on request
 *
 * @author eolgase
 */
public class EbsCounterLookUp {

    private static final String SUPPORTED_EBS_COUNTER_GROUPS = "supportedEbsCounterGroup";

    @Inject
    private Logger log;

    @Inject
    private PmCapabilityModelService pmCapabilityModelService;

    /**
     * This method will return a set of all EBS counters supported for the provided mimVersion.
     *
     * @param pmMimVersionQuery
     *         mimVersion to get EBS counters for
     * @param countersForAllEvents
     *         a set of all counters for the mimVersion
     * @param supportedEventsList
     *         a collection of supported events for the mimVersion
     *
     * @return Return a set of EBS counters on given mimVersion
     */

    public Set<CounterTableRow> getEbsCounters(final PmMimVersionQuery pmMimVersionQuery, final Set<CounterTableRow> countersForAllEvents,
                                               final Collection<EventTableRow> supportedEventsList) {
        Set<CounterTableRow> counters = countersForAllEvents;
        final Set<NodeTypeAndVersion> mimVersions = pmMimVersionQuery.getMimVersions();
        final List<String> ebsSupportedNodes = pmCapabilityModelService.getSupportedNodeTypesForPmFeatureCapability(PmFeature.CELLTRACE_FILE_COLLECTION);
        if (CollectionUtil.isNullOrEmpty(ebsSupportedNodes)) {
            return counters;
        }
        log.debug("Supported ebs nodes list : {}", ebsSupportedNodes);
        for (final NodeTypeAndVersion mimVersion : mimVersions ) {
            if(ebsSupportedNodes.contains(mimVersion.getNodeType())) {
                counters = getFilteredEBSCounters(counters);
                counters = getFilteredEventForEBSCounters(counters, supportedEventsList);
                break;
            }
        }
        return counters;
    }

    /**
     * Filter EBS counter set based on supported events for them
     *
     * @param counters
     *         - EBS counter set to filter
     * @param events
     *         - a collection of supported events
     *
     * @return - a set of filtered EBS counters based on supported events for them
     */
    private Set<CounterTableRow> getFilteredEventForEBSCounters(final Set<CounterTableRow> counters, final Collection<EventTableRow> events) {
        final Set<CounterTableRow> filteredEBSCounters = new TreeSet<>();
        final Set<String> supportedEventsNamesList = getSortedEventNamesList(events);

        for (final CounterTableRow counter : counters) {
            if (hasExistingEventsOnly(supportedEventsNamesList, counter)) {
                filteredEBSCounters.add(counter);
            }
        }
        return filteredEBSCounters;
    }

    /**
     * Verify whether provided EBS counter belongs to events that are in supported events set
     *
     * @param supportedEventsNamesList
     *         - a set of supported events names
     * @param counter
     *         - an EBS counter to verify
     *
     * @return true if the EBS counter belongs to supported events only
     */
    private boolean hasExistingEventsOnly(final Set<String> supportedEventsNamesList, final CounterTableRow counter) {

        final Pattern eventNamePattern = Pattern.compile("^//\\w+/(\\w+)/");

        for (final String counterEvent : ((EventBasedCounterTableRow) counter).getBasedOnEvent()) {
            final Matcher matcher = eventNamePattern.matcher(counterEvent);
            if (matcher.find()) {
                final String counterEventName = matcher.group(1);
                if (!supportedEventsNamesList.contains(counterEventName)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Retrieve events names as a set
     *
     * @param events
     *         - List of supported events
     *
     * @return - a list of unique events names
     */
    private Set<String> getSortedEventNamesList(final Collection<EventTableRow> events) {
        final Set<String> eventsList = new TreeSet<>();
        for (final EventTableRow event : events) {
            eventsList.add(event.getEventName());
        }
        return eventsList;
    }

    /**
     * Filter a list of EBS counters based on supported EBS counters groups
     *
     * @param counters
     *         - a list of EBS counters
     *
     * @return - a filtered list of EBS counters based on supported EBS groups
     */
    private Set<CounterTableRow> getFilteredEBSCounters(final Set<CounterTableRow> counters) {
        final Set<CounterTableRow> filteredEBSCounters = new TreeSet<>();
        final List<String> supportedGroups = pmCapabilityModelService.getSupportedCapabilityValues(null, SUPPORTED_EBS_COUNTER_GROUPS);
        for (final CounterTableRow counter : counters) {
            if (supportedGroups.contains(counter.getSourceObject())) {
                filteredEBSCounters.add(counter);
            }
        }
        return filteredEBSCounters;
    }
}