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


/**
 * This class represents a single entry in the Collection returned for {@link PmMetaDataLookupLocal#getEvents(PmMimVersionQuery)}
 *
 * @author enichyl
 */
public class EventTableRow implements Comparable<EventTableRow> {

    public static final String NO_EVENT_PRODUCER_ID = null;
    private final String eventName;
    private final String sourceObject;
    private final String eventProducerId;

    /**
     * Instantiates a new Event table row.
     *
     * @param eventName
     *         the event name
     * @param sourceObject
     *         the source object
     * @param eventProducerId
     *         the event producer id
     */
    public EventTableRow(final String eventName, final String sourceObject, final String eventProducerId) {
        this.eventName = eventName;
        this.sourceObject = sourceObject;
        this.eventProducerId = eventProducerId;
    }

    /**
     * Instantiates a new Event table row.
     *
     * @param eventName
     *         the event name
     * @param sourceObject
     *         the source object
     */
    public EventTableRow(final String eventName, final String sourceObject) {
        this(eventName, sourceObject, NO_EVENT_PRODUCER_ID);
    }

    /**
     * Instantiates a new Event table row.
     *
     * @param eventName
     *         the event name
     */
    public EventTableRow(final String eventName) {
        this(eventName, "OTHER", NO_EVENT_PRODUCER_ID);
    }

    /**
     * Gets event name.
     *
     * @return the eventName
     */
    public String getEventName() {
        return eventName;
    }

    /**
     * Gets source object.
     *
     * @return the source object
     */
    public String getSourceObject() {
        return sourceObject;
    }

    /**
     * Gets event producer Id object.
     *
     * @return the eventProducerId object
     */
    public String getEventProducerId() {
        return eventProducerId;
    }

    @Override
    public final int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (eventName == null ? 0 : eventName.hashCode());
        result = prime * result + (sourceObject == null ? 0 : sourceObject.hashCode());
        result = prime * result + (eventProducerId == null ? 0 : eventProducerId.hashCode());
        return result;
    }

    @Override
    public final boolean equals(final Object obj) {
        if (obj instanceof EventTableRow) {
            final EventTableRow other = (EventTableRow) obj;
            return Objects.equals(this.eventName, other.eventName) && Objects.equals(this.sourceObject, other.sourceObject) &&
                    Objects.equals(this.eventProducerId, other.eventProducerId);
        }
        return false;
    }


    @Override
    public int compareTo(final EventTableRow eventTableRow) {
        int result;
        if (eventTableRow.sourceObject == null || sourceObject == null) {
            result = eventName.compareTo(eventTableRow.eventName);
        } else {
            result = sourceObject.compareTo(eventTableRow.sourceObject);
            if (result == 0) {
                result = eventName.compareTo(eventTableRow.eventName);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "EventTableRow [eventName=" + eventName + ", sourceObject=" + sourceObject + ", eventProducerId=" + eventProducerId + "]";
    }
}
