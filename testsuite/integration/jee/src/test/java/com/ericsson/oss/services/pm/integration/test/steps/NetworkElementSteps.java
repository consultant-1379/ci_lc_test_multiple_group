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

package com.ericsson.oss.services.pm.integration.test.steps;

import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;

import org.junit.Assert;
import org.slf4j.Logger;

import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;
import com.ericsson.oss.services.pm.initiation.node.data.NodeData;
import com.ericsson.oss.services.pm.integration.test.helpers.JSonUtils;

public class NetworkElementSteps {

    @Inject
    private Logger logger;

    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;

    public List<NodeData> createNodes(final String filePath) throws Exception {
        List<NodeData> nodeDataList = Arrays.asList(JSonUtils.getJsonMapperObjectFromFile(filePath, NodeData[].class));
        logger.debug("Create Nodes: found nodes {}", nodeDataList);
        Assert.assertNotNull(nodeDataList);
        for (final NodeData node : nodeDataList) {
            nodeCreationHelperBean.createNode(node);
        }
        return nodeDataList;
    }

    public void changePmFunctionValue(final String nodeFdn, final boolean pmEnabled) {
        nodeCreationHelperBean.changePmFunctionValue(nodeFdn, pmEnabled);
    }

    public void deleteAllNodes() {
        logger.debug("Removing nodes from the system");
        nodeCreationHelperBean.deleteAllNodes();
    }
}
