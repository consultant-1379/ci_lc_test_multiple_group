/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2014
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.rest.response;

import java.util.Set;

/**
 * The Conflicting counter group for tracking counter conflicts.
 */
public class ConflictingCounterGroup {

    private String groupName;
    private Set<String> eventCounterNames;

    /**
     * @param groupName
     *         - name of the counter group
     * @param eventCounterNames
     *         - set of counter names
     */
    public ConflictingCounterGroup(final String groupName, final Set<String> eventCounterNames) {
        super();
        this.groupName = groupName;
        this.eventCounterNames = eventCounterNames;
    }

    /**
     * @return the groupName
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * @param groupName
     *         the groupName to set
     */
    public void setGroupName(final String groupName) {
        this.groupName = groupName;
    }

    /**
     * @return the counters
     */
    public Set<String> getEventCounterNames() {
        return eventCounterNames;
    }

    /**
     * @param counters
     *         the counters to set
     */
    public void setEventCounterNames(final Set<String> counters) {
        eventCounterNames = counters;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }

        final ConflictingCounterGroup otherCounterInfo = (ConflictingCounterGroup) obj;

        return (groupName == otherCounterInfo.groupName || groupName != null && groupName.equals(otherCounterInfo.getGroupName()))
                && (eventCounterNames == otherCounterInfo.eventCounterNames || eventCounterNames != null && eventCounterNames
                .equals(otherCounterInfo.getEventCounterNames()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (groupName == null ? 0 : groupName.hashCode());
        result = prime * result + (eventCounterNames == null ? 0 : eventCounterNames.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return new StringBuilder("ConflictingCounterGroup [groupName=")
                .append(groupName)
                .append(", eventCounterNames=")
                .append(eventCounterNames)
                .append("]").toString();
    }

}
