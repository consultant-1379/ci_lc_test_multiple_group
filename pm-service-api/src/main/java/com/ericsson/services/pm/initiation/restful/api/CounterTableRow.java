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

import java.util.Objects;

import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_measurement.ScannerType;

/**
 * The Counter table row class.
 */
public class CounterTableRow implements Comparable<CounterTableRow> {

    private final String counterName;
    private final String sourceObject;
    private final String description;
    private ScannerType scannerType;

    /**
     * Takes counterName, sourceObject, description, scannerType as input.
     * @param counterName
     *         - name of the counter
     * @param sourceObject
     *         - Source Object for counter
     * @param description
     *         - counter description
     * @param scannerType
     *         - scanner type of the counter
     */
    public CounterTableRow(final String counterName, final String sourceObject, final String description, final ScannerType scannerType) {
        this.counterName = counterName;
        this.sourceObject = sourceObject;
        this.description = description;
        this.scannerType = scannerType;
    }

    /**
     * Takes counterName, sourceObject as input.
     * @param counterName
     *         - name of the counter
     * @param sourceObject
     *         - Source Object for counter
     */
    public CounterTableRow(final String counterName, final String sourceObject) {
        this.counterName = counterName;
        this.sourceObject = sourceObject;
        description = "";
        scannerType = ScannerType.USER_DEFINED;
    }

    /**
     * @return the counterName
     */
    public String getCounterName() {
        return counterName;
    }

    /**
     * @return the sourceObject
     */
    public String getSourceObject() {
        return sourceObject;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return the scanner type
     */
    public ScannerType getScannerType() {
        return scannerType;
    }

    /**
     * @param scannerType
     *         - the scanner type
     */
    public void setScannerType(final ScannerType scannerType) {
        this.scannerType = scannerType;
    }

    public boolean hasSameNameAndSourceObject(final CounterTableRow anotherCounter) {
        return this.counterName.equals(anotherCounter.counterName)
                && this.sourceObject.equals(anotherCounter.sourceObject);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(counterName, sourceObject, description, scannerType);
    }

    /**
     * equals method for CounterTableRow, compares two CounterTableRow objects by counterName and sourceObject only as
     * it is being used to find a CounterTableRow object in an array in situations when the description is not available
     *
     * @param obj
     *         - object being compared to CounterTableRow
     */
    @Override
    public final boolean equals(final Object obj) {
        if (obj instanceof CounterTableRow) {
            final CounterTableRow other = (CounterTableRow) obj;
            return Objects.equals(this.counterName, other.counterName) && Objects.equals(this.sourceObject, other.sourceObject)
                    && Objects.equals(this.description, other.description) && Objects.equals(this.scannerType, other.scannerType);
        }
        return false;
    }

    /**
     * Compares two CounterTableRow objects based on counterName and sourceObject similar to equals
     *
     * @param other
     *         - other CounterTableRow object being compared.
     */
    @Override
    public int compareTo(final CounterTableRow other) {
        int result = sourceObject.compareTo(other.sourceObject);
        if (result == 0) {
            result = counterName.compareTo(other.counterName);
        }
        return result;
    }

    /**
     * Returns a String representing CounterTableRow object.
     *
     * @return - the String view of CounterTableRow.
     */
    @Override
    public String toString() {
        return "CounterTableRow{" + "counterName='" + getCounterName() + '\'' + ", sourceObject='" + getSourceObject() + '}';
    }
}
