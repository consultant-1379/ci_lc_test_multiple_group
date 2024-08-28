/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.collection.api;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This data structure would handle nodes with multiple rop times
 *
 * @author eushmar
 */
public class ProcessTypesAndRopInfo implements Serializable {

    private static final long serialVersionUID = 8209112731463635266L;
    private final Map<Integer, Set<String>> ropAndProcessTypes = new HashMap<>();

    /**
     * Add record output period info and process type.
     *
     * @param ropPeriod
     *         the record output period
     * @param processType
     *         the process type
     */
    public void addRopInfoAndProcessType(final Integer ropPeriod, final String processType) {
        if (ropAndProcessTypes.containsKey(ropPeriod)) {
            ropAndProcessTypes.get(ropPeriod).add(processType);
        } else {
            final Set<String> processTypes = new HashSet<>();
            processTypes.add(processType);
            ropAndProcessTypes.put(ropPeriod, processTypes);
        }
    }

    /**
     * Gets record output period and process types.
     *
     * @return the ropAndProcessTypes
     */
    public Map<Integer, Set<String>> getRopAndProcessTypes() {
        return ropAndProcessTypes;
    }

    /**
     * Gets process types.
     *
     * @param ropPeriod
     *         the record output period
     *
     * @return the process types
     */
    public Set<String> getProcessTypes(final Integer ropPeriod) {
        if (ropAndProcessTypes.containsKey(ropPeriod)) {
            return ropAndProcessTypes.get(ropPeriod);
        }
        return new HashSet<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "ProcessTypesAndRopInfo [ropAndProcessTypes=" + ropAndProcessTypes + "]";
    }
}
