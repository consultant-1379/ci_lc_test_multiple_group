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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;

/**
 * This class represents the mim query information passed to the pm-service by REST during the GET request for getCounters. See
 * SubscriptionComponentUIResource in pm-service-war.
 *
 * @author enichyl
 */
public class PmMimVersionQuery {

    private static final String EMPTY_STRING = "";

    private Set<NodeTypeAndVersion> mimVersions;

    /**
     * Instantiates a new Pm mim version query.
     */
    public PmMimVersionQuery() {
        mimVersions = new HashSet<>();
    }

    /**
     * This constructor parses the mim query string as passed through REST.
     *
     * @param mim
     *         - meta information model used to populate mimVersions with NodeTypeAndVersion Objects
     */
    public PmMimVersionQuery(final String mim) {
        mimVersions = new HashSet<>();
        processMim(mim);
    }

    /**
     * Instantiates a new Pm mim version query.
     *
     * @param mimVersions
     *         - Set of NodeTypeAndVersion Objects used to populate this
     *         beans mimVersions set.
     */
    public PmMimVersionQuery(final Set<NodeTypeAndVersion> mimVersions) {
        super();
        this.mimVersions = mimVersions;
    }

    private void processMim(final String mim) {
        final String query = mim.replaceAll("mim=", "");
        final String[] mimArray = query.split(",");
        for (final String queryInput : mimArray) {
            final NodeTypeAndVersion counterQuery = convertQueryInputToNodeTypeAndVersion(queryInput);
            mimVersions.add(counterQuery);
        }
    }

    private NodeTypeAndVersion convertQueryInputToNodeTypeAndVersion(final String queryInput) {
        final int nodeTypeIndex = 0;
        final int versionIndex = 1;
        final int techDomainIndex = 2;
        final int sizeOfTypeVersionWithNeTypeVersionTechDomain = 3;
        final int sizeOfTypeVersionWithNeTypeVersion = 2;
        final String[] typeAndVersion = queryInput.split(":");
        final String nodeType = typeAndVersion[nodeTypeIndex];
        String version;
        List<String> technologyDomainList;

        if (typeAndVersion.length == sizeOfTypeVersionWithNeTypeVersionTechDomain) {
            version = typeAndVersion[versionIndex];
            final String techDomain = typeAndVersion[techDomainIndex];
            technologyDomainList = splitTechnologyDomainAsList(techDomain);
        } else if (typeAndVersion.length == sizeOfTypeVersionWithNeTypeVersion) {
            version = typeAndVersion[1];
            technologyDomainList = createEmptyTechnologyDomainList();
        } else {
            version = EMPTY_STRING;
            technologyDomainList = createEmptyTechnologyDomainList();
        }
        return new NodeTypeAndVersion(nodeType, version, technologyDomainList);
    }

    private List<String> createEmptyTechnologyDomainList() {
        return new ArrayList<>();
    }

    private List<String> splitTechnologyDomainAsList(final String techDomain) {
        final String[] techDomainArray = techDomain.split("#");
        final List<String> technologyDomainList = new ArrayList<>(techDomainArray.length);
        for (final String technology : techDomainArray) {
            technologyDomainList.add(technology);
        }
        return technologyDomainList;
    }

    /**
     * Gets mim versions.
     *
     * @return the mimVersions
     */
    public Set<NodeTypeAndVersion> getMimVersions() {
        return mimVersions;
    }

    /**
     * Sets mim versions.
     *
     * @param mimVersions
     *         the mimVersions to set
     */
    public void setMimVersions(final Set<NodeTypeAndVersion> mimVersions) {
        this.mimVersions = mimVersions;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {

        final StringBuilder strBuilder = new StringBuilder();
        for (final NodeTypeAndVersion mimVersion : mimVersions) {
            strBuilder.append(mimVersion.toString());
            strBuilder.append(",");
        }
        return strBuilder.substring(0, strBuilder.length() - 1);
    }
}
