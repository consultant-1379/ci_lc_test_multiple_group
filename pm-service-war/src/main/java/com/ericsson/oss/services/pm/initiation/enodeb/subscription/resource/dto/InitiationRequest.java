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

package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource.dto;

import java.util.Date;

/**
 * The Initiation request.
 */
public class InitiationRequest {

    private Date persistenceTime;

    /**
     * Instantiates a new Initiation request.
     */
    public InitiationRequest() {
    }

    /**
     * Instantiates a new Initiation request with persistence time.
     *
     * @param persistenceTime
     *         the persistence time
     */
    public InitiationRequest(final Date persistenceTime) {
        super();
        this.persistenceTime = persistenceTime;
    }

    /**
     * Gets persistence time.
     *
     * @return the persistence time
     */
    public Date getPersistenceTime() {
        return persistenceTime;
    }

    /**
     * Sets persistence time.
     *
     * @param persistenceTime
     *         the persistence time
     */
    public void setPersistenceTime(final Date persistenceTime) {
        this.persistenceTime = persistenceTime;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("InitiationRequest [persistenceTime=");
        builder.append(persistenceTime);
        builder.append("]");
        return builder.toString();
    }

}
