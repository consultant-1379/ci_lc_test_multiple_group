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

import java.util.List;

import com.ericsson.oss.itpf.modeling.schema.gen.oss_common.LifeCycleType;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_measurement.ScannerType;

/**
 * The Pm counter attributes.
 */
public class PmCounterAttributes {
    private final String description;
    private final LifeCycleType lifeCycleType;
    private final List<String> basedOnEvents;
    private final List<String> generation;
    private final String externalCounterName;
    private final ScannerType scannerType;

    /**
     * Instantiates a new Pm counter attributes.
     *
     * @param description
     *         the counter description
     * @param lifeCycleType
     *         the counter life cycle type
     * @param basedOnEvent
     *         the based on event mapping of the counter
     * @param generation
     *         the counter generation
     * @param externalCounterName
     *         the external counter name
     * @param scannerType
     *         scanner type of the counter
     */
    public PmCounterAttributes(final String description, final LifeCycleType lifeCycleType, final List<String> basedOnEvent,
                               final List<String> generation, final String externalCounterName, final ScannerType scannerType) {
        this.lifeCycleType = lifeCycleType;
        this.description = description;
        this.basedOnEvents = basedOnEvent;
        this.generation = generation;
        this.externalCounterName = externalCounterName;
        this.scannerType = scannerType;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the life cycle.
     *
     * @return the lifeCycle
     */
    public LifeCycleType getLifeCycle() {
        return lifeCycleType;
    }

    /**
     * Gets the event mapping
     *
     * @return the based on event
     */
    public List<String> getBasedOnEvent() {
        return basedOnEvents;
    }

    /**
     * Gets the counter generation.
     *
     * @return the counter generation
     */
    public List<String> getGeneration() {
        return generation;
    }

    /**
     * @return the externalCounterName
     */
    public String getExternalCounterName() {
        return externalCounterName;
    }

    /**
     * @return the scanner type
     */
    public ScannerType getScannerType() {
        return scannerType;
    }
}
