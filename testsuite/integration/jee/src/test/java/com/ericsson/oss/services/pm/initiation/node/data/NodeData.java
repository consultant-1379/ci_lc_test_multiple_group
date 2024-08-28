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

package com.ericsson.oss.services.pm.initiation.node.data;

import java.util.List;

public class NodeData {

    private String nodeName;
    private String ipAddress;
    private String platformType;
    private String neType;
    private String ossModelIdentity;
    private List<String> technologyDomain;

    public NodeData() {
    }

    public NodeData(final String nodeName, final String ipAddress, final String platformType, final String neType, final String ossModelIdentity) {
        this.nodeName = nodeName;
        this.ipAddress = ipAddress;
        this.platformType = platformType;
        this.neType = neType;
        this.ossModelIdentity = ossModelIdentity;
    }

    /**
     * @return the nodeName
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * @param nodeName
     *         the nodeName to set
     */
    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
    }

    /**
     * @return the ipAddress
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * @param ipAddress
     *         the ipAddress to set
     */
    public void setIpAddress(final String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * @return the platformType
     */
    public String getPlatformType() {
        return platformType;
    }

    /**
     * @param platformType
     *         the platformType to set
     */
    public void setPlatformType(final String platformType) {
        this.platformType = platformType;
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

    @Override
    public String toString() {
        return "Node Name : " + nodeName + ", IP Address : " + ipAddress + ", Platform Type : " + platformType + ", NE Type : " + neType;
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
}
