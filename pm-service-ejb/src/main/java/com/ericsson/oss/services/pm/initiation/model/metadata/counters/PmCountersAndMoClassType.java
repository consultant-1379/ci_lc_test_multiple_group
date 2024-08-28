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

import java.util.Objects;


/**
 * The  Pm counters and mo class type.
 */
public class PmCountersAndMoClassType {

    private String counterName;
    private String moClassName;

    /**
     * Instantiates a new Pm counters and mo class type.
     *
     * @param counterName
     *         the counter name
     * @param moClassName
     *         the mo class name
     */
    public PmCountersAndMoClassType(final String counterName, final String moClassName) {
        this.moClassName = moClassName;
        this.counterName = counterName;
    }

    /**
     * Instantiates a new Pm counters and mo class type.
     */
    public PmCountersAndMoClassType() {
    }

    /**
     * Gets counter name.
     *
     * @return the counterName
     */
    public String getCounterName() {
        return counterName;
    }

    /**
     * Gets mo class name.
     *
     * @return the moClassName
     */
    public String getMoClassName() {
        return moClassName;
    }

    @Override
    public final int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (moClassName == null ? 0 : moClassName.hashCode());
        result = prime * result + (counterName == null ? 0 : counterName.hashCode());
        return result;
    }

    @Override
    public final boolean equals(final Object obj) {
        if (obj instanceof PmCountersAndMoClassType) {
            final PmCountersAndMoClassType other = (PmCountersAndMoClassType) obj;
            return Objects.equals(this.moClassName, other.moClassName) && Objects.equals(this.counterName, other.counterName);
        }
        return false;
    }

    @Override
    public String toString() {
        return counterName + ":" + moClassName;
    }

}
