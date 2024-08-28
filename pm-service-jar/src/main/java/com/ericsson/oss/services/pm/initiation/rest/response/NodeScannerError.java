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

package com.ericsson.oss.services.pm.initiation.rest.response;

/**
 * The Corba error class.
 */
public class NodeScannerError {

    private String name;

    private short code;

    private String description;

    /**
     * @param name
     *         - the name of the error
     * @param code
     *         - the error code
     * @param description
     *         - the error description
     */
    public NodeScannerError(final String name, final short code, final String description) {
        super();
        this.name = name;
        this.code = code;
        this.description = description;
    }

    /**
     * Empty constructor
     */
    public NodeScannerError() {
        super();
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name
     *         the name to set
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * @return the code
     */
    public short getCode() {
        return code;
    }

    /**
     * @param code
     *         the code to set
     */
    public void setCode(final short code) {
        this.code = code;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description
     *         the description to set
     */
    public void setDescription(final String description) {
        this.description = description;
    }

}
