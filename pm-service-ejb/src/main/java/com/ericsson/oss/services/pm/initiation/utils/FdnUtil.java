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

package com.ericsson.oss.services.pm.initiation.utils;

import static com.ericsson.oss.pmic.api.constants.ModelConstants.OssTopModelConstants.OSS_TOP_ME_CONTEXT_TYPE;
import static com.ericsson.oss.pmic.api.constants.ModelConstants.OssTopModelConstants.OSS_TOP_SUBNETWORK_TYPE;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.Node;

/**
 * The Fdn utility class.
 */
public class FdnUtil {
    private static final char COMMA = ',';
    private static final char EQUALS = '=';

    @Inject
    private Logger logger;

    /**
     * Gets parent from child of type.
     *
     * @param fdn
     *         the fully distinguished name
     * @param childType
     *         the child type
     *
     * @return the parent from child of type
     */
    public static String getParentFromChildOfType(final String fdn, final String childType) {
        return fdn.substring(0, fdn.lastIndexOf(COMMA + childType));
    }

    /**
     * Gets parent fully distinguished name from child.
     *
     * @param parentMoName
     *         the parent mo name
     * @param fdn
     *         the fully distinguished name
     *
     * @return the parent fully distinguished name from child
     */
    public String getParentFdnFromChild(final String parentMoName, final String fdn) {
        final Pattern pattern = Pattern.compile(".*" + parentMoName + "=(\\w*)(?=,)");
        final Matcher matcher = pattern.matcher(fdn);
        if (matcher.find()) {
            return matcher.group();
        } else {
            logger.error("Fdn {} has no parent MO by the name {}", fdn, parentMoName);
            return null;
        }
    }

    /**
     * Gets direct parent fully distinguished name from child.
     *
     * @param fdn
     *         the fully distinguished name
     *
     * @return the direct parent fully distinguished name from child
     */
    public String getDirectParentFdnFromChild(final String fdn) {
        return fdn.substring(0, fdn.lastIndexOf(COMMA));
    }

    /**
     * Gets root parent fully distinguished name from child.
     *
     * @param fdn
     *         the fully distinguished name
     *
     * @return the root parent fully distinguished name from child
     */
    public String getRootParentFdnFromChild(final String fdn) {
        return fdn.substring(0, fdn.indexOf(COMMA));
    }

    /**
     * Extracts nodeFdn from a variety of fdns. Supported strings are: NetworkElement=[abc],<br>
     * MeContext=[abc],<br>
     * Subnetwork=[abc],
     *
     * @param fdn
     *         - fdn formatted string
     *
     * @return - NetworkElement=[abc]
     */
    public String getNodeFdn(final String fdn) {
        if (fdn.contains(Node.NODE_MODEL_TYPE)) {
            final int from = fdn.indexOf(Node.NODE_MODEL_TYPE);
            final int toIndex = fdn.indexOf(COMMA, from);
            return fdn.substring(from, toIndex);
        }
        if (fdn.contains(OSS_TOP_ME_CONTEXT_TYPE)) {
            final int from = fdn.indexOf(EQUALS, fdn.indexOf(OSS_TOP_ME_CONTEXT_TYPE));
            final int toIndex = fdn.indexOf(COMMA, from);
            return Node.NETWORK_ELEMENT_FDN_KEY + fdn.substring(from + 1, toIndex);
        }
        if (fdn.contains(OSS_TOP_SUBNETWORK_TYPE)) {
            final int from = fdn.indexOf(EQUALS, fdn.indexOf(OSS_TOP_SUBNETWORK_TYPE));
            final int toIndex = fdn.indexOf(COMMA, from);
            return Node.NETWORK_ELEMENT_FDN_KEY + fdn.substring(from + 1, toIndex);
        }
        return null;
    }

}
