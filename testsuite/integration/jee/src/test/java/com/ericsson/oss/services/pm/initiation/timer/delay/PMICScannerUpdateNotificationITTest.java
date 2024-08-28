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

package com.ericsson.oss.services.pm.initiation.timer.delay;

import static com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionAttributes.ID;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import org.awaitility.Duration;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionTestUtils;
import com.ericsson.oss.services.pm.initiation.model.resource.PMICScannerStatus;
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;
import com.ericsson.oss.services.pm.initiation.node.data.NodeData;
import com.ericsson.oss.services.pm.initiation.node.data.NodeDataReader;
import com.ericsson.oss.services.pm.initiation.scanner.data.PMICScannerData;
import com.ericsson.oss.services.pm.initiation.scanner.data.ScannerCreationHelperBean;

@RunWith(Arquillian.class)
public class PMICScannerUpdateNotificationITTest extends InputBaseArquillian {

    @Inject
    protected ScannerCreationHelperBean scannerCreationHelperBean;
    @Inject
    private Logger logger;
    @Inject
    private SubscriptionTestUtils testUtils;
    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;

    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void should_create_PMICScannerInfo() throws InterruptedException {
        nodeCreationHelperBean.deleteAllSubscriptionsAndNodes();
        nodeCreationHelperBean.createNodes(getClass().getSimpleName());
        testUtils.createSubscriptions(getClass().getSimpleName());

        final NodeDataReader ndr = new NodeDataReader();
        final List<NodeData> nodeDataList = ndr.getNodeData(getClass().getSimpleName());
        final List<String> nodeNames = new ArrayList<>();

        for (final NodeData nodeData : nodeDataList) {
            nodeNames.add(nodeData.getNodeName());
        }
        logger.debug("Nodes available {} ", nodeNames);
        if (nodeNames.size() != 3) {
            logger.error("Not sufficient nodes available for this test, please add atlease three nodes for this subscription");
            return;
        }

        final Map<String, Map<String, Object>> createdSubscriptions = testResponseHolder.getAllSubs();
        final Set<String> subscriptionNames = createdSubscriptions.keySet();
        for (final String name : subscriptionNames) {
            final Map<String, Object> subscriptionAttr = createdSubscriptions.get(name);
            final String id = (String) subscriptionAttr.get(ID.name());
            createScanner(nodeNames.get(0), id, name, PMICScannerStatus.ACTIVE);
            createScanner(nodeNames.get(1), id, name, PMICScannerStatus.ERROR);
            createScanner(nodeNames.get(2), id, name, PMICScannerStatus.UNKNOWN);
        }

        logger.debug("Waiting to verify the task status after scanner created with ERROR and UNKNOWN");
        waitUntilTaskStatus(TaskStatus.ERROR, new Duration(2, TimeUnit.MINUTES));

        final Map<String, Map<String, Object>> testSubscriptions = testUtils.findSubscriptions(getClass().getSimpleName());
        for (final String name : testSubscriptions.keySet()) {
            final Map<String, Object> subscriptionAttr = testSubscriptions.get(name);
            final String id = (String) subscriptionAttr.get(ID.name());
            updateScanner(nodeNames.get(1), id, name, PMICScannerStatus.ACTIVE);
            updateScanner(nodeNames.get(2), id, name, PMICScannerStatus.ACTIVE);
        }

        logger.debug("Waiting to verify the task status after scanner updated with ACTIVE");
        waitUntilTaskStatus(TaskStatus.OK, new Duration(2, TimeUnit.MINUTES));
    }

    private void createScanner(final String nodeName, final String subscripitonId, final String subscripitonName, final PMICScannerStatus status)
            throws InterruptedException {

        logger.debug("Creating scanner for Node {} with status {}", nodeName, status);
        final List<String> nodeNames = new ArrayList<>();
        nodeNames.add(nodeName);

        final String scannerName = "USERDEF-" + subscripitonName + ".CONT.Y.STATS";
        final Map<String, Object> scannerattr = createScannerAttributesMap(900, subscripitonId, status, "1", scannerName, "STATS");
        final PMICScannerData pmicscannerData = new PMICScannerData(scannerName, nodeNames, scannerattr);

        scannerCreationHelperBean.createScannerOnNodes(pmicscannerData);
        final CountDownLatch cl = new CountDownLatch(nodeNames.size());
        dpsNotificationListener.setObjCreatedCountDownLatch(cl);
        cl.await(20, TimeUnit.SECONDS);
    }

    private void updateScanner(final String nodeName, final String subscripitonId, final String subscripitonName, final PMICScannerStatus status)
            throws InterruptedException {

        logger.debug("Updating scanner for Node {} with status {}", nodeName, status);
        final List<String> nodeNames = new ArrayList<>();
        nodeNames.add(nodeName);

        final String scannerName = "USERDEF-" + subscripitonName + ".CONT.Y.STATS";
        final Map<String, Object> scannerattr = createScannerAttributesMap(900, subscripitonId, status, "1", scannerName, "STATS");
        final PMICScannerData pmicscannerData = new PMICScannerData("USERDEF-" + subscripitonName + ".CONT.Y.STATS", nodeNames, scannerattr);

        scannerCreationHelperBean.updateScannerAttributesOnNode(pmicscannerData);
        final CountDownLatch cl = new CountDownLatch(nodeNames.size());
        dpsNotificationListener.setAttChangedCountDownLatch(cl);
        cl.await(20, TimeUnit.SECONDS);
    }
}
