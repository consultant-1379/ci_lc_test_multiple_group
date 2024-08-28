/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.initiation.enodeb.subscription.resource.dto;

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
 -----------------------------------------------------------------------------*/

import java.io.Serializable;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

/**
 * NodeAttributes class
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(include = Inclusion.NON_NULL)
public class NodeAttributes implements Serializable {

    private static final long serialVersionUID = 317974716959056552L;

    @XmlElement(required = true)
    private String nodeType;

    @XmlElement(required = true)
    private String fdn;

    @XmlElement(required = true)
    private String poid;

    @XmlElement(required = false)
    private String mimInfo;

    @XmlElement(required = false)
    private String ossModelIdentity;

    @XmlElement(required = true)
    private String neType;

    @XmlElement(required = false)
    private List<String> technologyDomain;

    @XmlElement(required = false)
    private String ossPrefix;

    private String pmFunction;

    /**
     * Default constructor.
     */
    public NodeAttributes() {}

    /**
     * Constructor.
     *
     * @param nodeType
     *         - node type
     * @param fdn
     *         - fdn
     * @param poid
     *         - po id
     */
    public NodeAttributes(final String nodeType, final String fdn, final String poid) {
        this.nodeType = nodeType;
        this.fdn = fdn;
        this.poid = poid;
    }

    /**
     * @return the neType
     */
    public String getNodeType() {
        return nodeType;
    }

    /**
     * @param nodeType
     *         the nodeType to set
     */
    public void setNodeType(final String nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * @return the fdn
     */
    public String getFdn() {
        return fdn;
    }

    /**
     * @param fdn
     *         the fdn to set
     */
    public void setFdn(final String fdn) {
        this.fdn = fdn;
    }

    /**
     * @return the poid
     */
    public String getPoid() {
        return poid;
    }

    /**
     * @param poid
     *         the poid to set
     */
    public void setPoid(final String poid) {
        this.poid = poid;
    }

    /**
     * @return the mimInfo
     */
    public String getMimInfo() {
        return mimInfo;
    }

    /**
     * @param mimInfo
     *         the mimInfo to set
     */
    public void setMimInfo(final String mimInfo) {
        this.mimInfo = mimInfo;
    }

    /**
     * @return the ossModelIdentity
     */
    public String getOssModelIdentity() {
        return ossModelIdentity;
    }

    /**
     * @param ossModelIdentity
     *         the ossModelIdentity to set
     */
    public void setOssModelIdentity(final String ossModelIdentity) {
        this.ossModelIdentity = ossModelIdentity;
    }

    /**
     * @return the neType
     */
    public String getNeType() {
        return neType;
    }

    /**
     * @param neType
     *         the neType to set
     */
    public void setNeType(final String neType) {
        this.neType = neType;
    }

    /**
     * @return the pmFunction
     */
    public String getPmFunction() {
        return pmFunction;
    }

    /**
     * @param pmFunction
     *         the pmFunction to set
     */
    public void setPmFunction(final String pmFunction) {
        this.pmFunction = pmFunction;
    }

    /**
     * @return the technologyDomain
     */
    public List<String> getTechnologyDomain() {
        return technologyDomain;
    }

    /**
     * @param technologyDomain
     *         the technologyDomain to set
     */
    public void setTechnologyDomain(final List<String> technologyDomain) {
        this.technologyDomain = technologyDomain;
    }

    /**
     * @return the ossPrefix
     */
    public String getOssPrefix() {
        return ossPrefix;
    }

    /**
     * @param ossPrefix
     *         the ossPrefix to set
     */
    public void setOssPrefix(final String ossPrefix) {
        this.ossPrefix = ossPrefix;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("NodeAttributes [nodeType=");
        builder.append(nodeType);
        builder.append(", fdn=");
        builder.append(fdn);
        builder.append(", poid=");
        builder.append(poid);
        builder.append(", mimInfo=");
        builder.append(mimInfo);
        builder.append(", ossModelIdentity=");
        builder.append(ossModelIdentity);
        builder.append(", neType=");
        builder.append(neType);
        builder.append(", pmFunction=");
        builder.append(pmFunction);
        builder.append(", technologyDomain=");
        builder.append(technologyDomain);
        builder.append(", ossPrefix=");
        builder.append(ossPrefix);
        builder.append("]");
        return builder.toString();
    }
}

