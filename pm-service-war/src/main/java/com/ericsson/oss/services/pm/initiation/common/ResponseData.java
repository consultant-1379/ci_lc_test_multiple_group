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

package com.ericsson.oss.services.pm.initiation.common;

import javax.ws.rs.core.Response.Status;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The Response data.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "response")
public class ResponseData {

    @XmlElement
    protected Status code;

    @XmlElement
    protected String error;

    @XmlElement
    private Object[] parameters;

    /**
     * Instantiates a new Response data.
     */
    public ResponseData() {
    }

    /**
     * Instantiates a new Response data.
     *
     * @param code
     *         the error code
     * @param error
     *         the error
     * @param parameters
     *         the parameters
     */
    public ResponseData(final Status code, final String error, final Object... parameters) {
        this.code = code;
        this.error = error;
        this.parameters = parameters;
    }

    /**
     * Gets error code.
     *
     * @return the error code
     */
    public Status getCode() {
        return code;
    }

    /**
     * Sets error code.
     *
     * @param code
     *         the error code to set
     */
    public void setCode(final Status code) {
        this.code = code;
    }

    /**
     * Gets error.
     *
     * @return the error
     */
    public String getError() {
        return error;
    }

    /**
     * Sets error.
     *
     * @param error
     *         the error to set
     */
    public void setError(final String error) {
        this.error = error;
    }

    /**
     * Get parameters object [ ].
     *
     * @return the object [ ]
     */
    public Object[] getParameters() {
        return parameters;
    }

    /**
     * Sets parameters.
     *
     * @param parameters
     *         the parameters
     */
    public void setParameters(final Object[] parameters) {
        this.parameters = parameters;
    }

}
