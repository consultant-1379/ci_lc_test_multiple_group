/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.services.pm.initiation.restful.api;

import java.util.List;

import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_measurement.ScannerType;

/**
 * The  Event based counter table row.
 */
public class EventBasedCounterTableRow extends CounterTableRow {

    private final List<String> basedOnEvent;

    /**
     * Instantiates a new Event based counter table row.
     *
     * @param counterName
     *         the counter name
     * @param sourceObject
     *         the source object
     * @param description
     *         the description
     * @param scannerType
     *         the scanner type
     * @param basedOnEvent
     *         the event the counter maps to
     */
    public EventBasedCounterTableRow(final String counterName, final String sourceObject, final String description, final ScannerType scannerType,
                                     final List<String> basedOnEvent) {
        super(counterName, sourceObject, description, scannerType);
        this.basedOnEvent = basedOnEvent;
    }

    /**
     * Gets based on event.
     *
     * @return the BasedOnEvent
     */
    public List<String> getBasedOnEvent() {
        return basedOnEvent;
    }

    /**
     * Returns a String representing EventBasedCounterTableRow object.
     *
     * @return - the String view of EventBasedCounterTableRow.
     */
    @Override
    public String toString() {
        return "EventBasedCounterTableRow{" + "counterName='" + getCounterName() + '\'' + ", sourceObject='" + getSourceObject() + ", basedOnEvent="
                + getBasedOnEvent() + '}';
    }
}
