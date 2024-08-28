/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.utils;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.services.pm.generic.NodeService;

/**
 * The PmFunction utility class.
 */
public class PmFunctionUtil {
    public static final String PM_FUNCTION_PROPERTY = "PM_FUNCTION_CONFIG";
    public static final String PM_ENABLED_CONFIG = System.getProperty(PM_FUNCTION_PROPERTY, PmFunctionPropertyValue.PM_FUNCTION_TORF_255692.name());

    private static final Logger logger = LoggerFactory.getLogger(PmFunctionUtil.class);

    @Inject
    private NodeService nodeService;

    /**
     * Get the system property used to define sw behavior when PmFunction field pmEnabled is set to false.
     * PM_ENABLED_CONFIG: Assuming we have a system property with following possible values:
     * <ul>
     * <li>1. LEGACY: in this case behaviour is same as before pmFuntion TORF-255692 implementation<br> </li>
     * <li>2. PM_FUNCTION_TORF_255692: in this case USERDEF subscription/scanner will be deactivated and nodes with pmEnabled=false will be removed
     * from subscription<br></li>
     * <li>3. PM_FUNCTION_TORF_255692_SUB_DELETION: in this case USERDEF subscription/scanner will be deactivated and nodes with pmEnabled=false will
     * be removed from subscription. When last node is removed then subscription is automatically deleted.</li>
     * </ul>
     *
     * @return String containing system property value in order to define software behavior.
     **/
    public static PmFunctionPropertyValue getPmFunctionConfig() {
        if (PM_ENABLED_CONFIG == null || PM_ENABLED_CONFIG.length() == 0) {
            logger.debug("PmFunctionPropertyValue: {}", PmFunctionPropertyValue.PM_FUNCTION_LEGACY.getValue());
            return PmFunctionPropertyValue.PM_FUNCTION_LEGACY;
        }
        try {
            final PmFunctionPropertyValue value = PmFunctionPropertyValue.valueOf(PM_ENABLED_CONFIG);
            logger.debug("PmFunctionPropertyValue: {}", value.getValue());
            return value;
        } catch (final Exception e) {
            logger.debug("PmFunctionPropertyValue: {}", PmFunctionPropertyValue.PM_FUNCTION_LEGACY.getValue());
            return PmFunctionPropertyValue.PM_FUNCTION_LEGACY;
        }
    }

    /**
     * Filter nodes by pmFunction.
     *
     * @param nodes
     *         input list of nodes
     */
    public void filterNodesByPmFunctionOn(final List<Node> nodes) {
        final Iterator<Node> nodesIterator = nodes.iterator();
        while (nodesIterator.hasNext()) {
            final Node node = nodesIterator.next();
            if (!nodeService.isPmFunctionEnabled(node.getFdn())) {
                nodesIterator.remove();
            }
        }
    }

    /**
     * Filter node Fdns by pmFunction.
     *
     * @param nodeFdns
     *         input nodeFdns
     */
    public void filterNodeFdnsByPmFunctionOn(final Set<String> nodeFdns) {
        final Iterator<String> nodeFdnsIterator = nodeFdns.iterator();
        while (nodeFdnsIterator.hasNext()) {
            final String nodeFdn = nodeFdnsIterator.next();
            if (!nodeService.isPmFunctionEnabled(nodeFdn)) {
                nodeFdnsIterator.remove();
            }
        }
    }

    /**
     * The PmFunctionPropertyValue enum class.
     */
    public enum PmFunctionPropertyValue {
        PM_FUNCTION_LEGACY("LEGACY"),
        PM_FUNCTION_TORF_255692("PM_FUNCTION_TORF_255692"); // TORF-255692
        private final String value;

        /**
         * PmFunctionPropertyValue constructor.
         *
         * @param value
         *         the value of the property
         */
        PmFunctionPropertyValue(final String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
