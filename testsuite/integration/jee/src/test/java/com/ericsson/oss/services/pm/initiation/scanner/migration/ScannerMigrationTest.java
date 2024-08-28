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

package com.ericsson.oss.services.pm.initiation.scanner.migration;

import static org.awaitility.Awaitility.await;

import static com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionAttributes.NODES;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_MODEL_NAME;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_NAME_POSTFIX_CONT_STATS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.ejb.EJB;
import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectCreatedEvent;
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender;
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.services.pm.initiation.events.NodeScannerInfo;
import com.ericsson.oss.services.pm.initiation.events.ScannerPollingResult;
import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionOperationMessageSender;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionTestUtils;
import com.ericsson.oss.services.pm.initiation.model.resource.PMICScannerStatus;
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;
import com.ericsson.oss.services.pm.initiation.node.data.NodeData;
import com.ericsson.oss.services.pm.initiation.node.data.NodeDataReader;
import com.ericsson.oss.services.pm.initiation.scanner.data.PMICScannerData;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionDataReader;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionInfo;
import com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant;
import com.ericsson.oss.services.pm.integration.test.steps.PibSteps;
import com.ericsson.oss.services.pm.test.requests.PmServiceRequestsTypes;
import com.ericsson.oss.services.pm.test.requests.SubscriptionOperationRequest;


@RunWith(Arquillian.class)
public class ScannerMigrationTest extends InputBaseArquillian {

    private final String PM_MIGRATION_PIB_PROPERTY = "pmMigrationEnabled";
    private final List<PMICScannerData> scannerList = new ArrayList<PMICScannerData>();
    @Inject
    SubscriptionOperationMessageSender operationSender;
    @EJB(lookup = "java:/datalayer/DataPersistenceService")
    DataPersistenceService dps;
    @Inject
    @Modeled
    private EventSender<ScannerPollingResult> sender;
    @Inject
    private SubscriptionTestUtils testUtils;
    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;
    @Inject
    private PibSteps pibSteps;

    @Test
    @InSequence(2)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void createNodesForTest() throws InterruptedException {
        nodeCreationHelperBean.deleteAllSubscriptionsAndNodes();
        nodeCreationHelperBean.createNodesWithPmFunctionOff(getClass().getSimpleName());
    }

    @Test
    @InSequence(3)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void createSubscriptionsForTest() throws InterruptedException {
        testResponseHolder.clear();
        final SubscriptionOperationRequest createMessage = new SubscriptionOperationRequest(PmServiceRequestsTypes.CREATE, null, null, getClass()
                .getSimpleName());
        operationSender.sendTestOperationMessageToPmService(createMessage);

        final CountDownLatch cl1 = new CountDownLatch(1);
        testResponseHolder.setCountDownLatch(cl1);
        cl1.await(60, TimeUnit.SECONDS);
    }

    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Migration_should_add_PMS_scanner_with_file_collection_off() throws Exception {
        final String nodeName = getTestNodes().get(1);
        final String subName = getTestSubscriptions().get(0);
        final String nodeFdn = "NetworkElement=" + nodeName;

        // Add node to subscription
        final List<String> nodesToBeAdded = new ArrayList<>();
        nodesToBeAdded.add(nodeFdn);
        testUtils.addNodesToSubscriptions(nodesToBeAdded, subName);

        final String scannerNameOnNode = SubscriptionOperationConstant.SCANNER_NAME_PREFIX_USERDEF_PMS + subName
                + ".Profile=5000168.Continuous=Y.STATS";
        final String scannerNameInEnm = SubscriptionOperationConstant.SCANNER_NAME_PREFIX_USERDEF + subName + SCANNER_NAME_POSTFIX_CONT_STATS;
        final String scannerFdn = nodeFdn + "," + SCANNER_MODEL_NAME + "=" + scannerNameInEnm;

        // Create scanner polling result for scanner on real node
        final String scannerIdOnNode = "123";
        final NodeScannerInfo scanner1 = new NodeScannerInfo(Integer.parseInt(scannerIdOnNode), scannerNameOnNode, PMICScannerStatus.ACTIVE.name(),
                "STATS");
        final List<NodeScannerInfo> scannerList = new ArrayList<NodeScannerInfo>();
        scannerList.add(scanner1);
        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Turn Migration on
        turnOnMigration();
        turnPmFunctionON(nodeFdn);

        // Node is added and polled - Send scanner polling result
        sender.send(scannerPollingResult);
        final CountDownLatch cl = new CountDownLatch(1);
        dpsNotificationListener.setObjCreatedCountDownLatch(cl);
        cl.await(50, TimeUnit.SECONDS);
        final DpsObjectCreatedEvent dpsObjCreated = dpsNotificationListener.getDpsObjectCreatedEventForNode(scannerFdn);

        // Verify the scanner was created in DPS with File Collection disabled
        verifyScannerMigrated(nodeFdn, scannerFdn, dpsObjCreated);

        // Verify the node was added into the subscription
        verifyNodeAddedToSubscription();

        // Migration has finished - Turn Migration Off
        turnOffMigration();
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Migration_should_add_PREDEF_scanner_with_file_collection_off() throws Exception {
        final String nodeName = getTestNodes().get(1);
        final String scannerNameOnNode = SubscriptionOperationConstant.SCANNER_NAME_PREFIX_PREDEF
                + SubscriptionOperationConstant.PMIC_SCANNER_TYPE_STATS;
        final String nodeFdn = "NetworkElement=" + nodeName;
        final String scannerFdn = nodeFdn + "," + SCANNER_MODEL_NAME + "=" + scannerNameOnNode;

        // Create scanner polling result for scanner on real node
        final String scannerIdOnNode = "123";
        final NodeScannerInfo scanner1 = new NodeScannerInfo(Integer.parseInt(scannerIdOnNode), scannerNameOnNode, PMICScannerStatus.ACTIVE.name(),
                ProcessType.STATS.name());
        final List<NodeScannerInfo> scannerList = new ArrayList<NodeScannerInfo>();
        scannerList.add(scanner1);
        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Turn Migration on
        turnOnMigration();
        turnPmFunctionON(nodeFdn);

        // Node is added and polled - Send scanner polling result
        sender.send(scannerPollingResult);
        final CountDownLatch cl = new CountDownLatch(1);
        dpsNotificationListener.setObjCreatedCountDownLatch(cl);
        cl.await(30, TimeUnit.SECONDS);
        final DpsObjectCreatedEvent dpsObjCreated = dpsNotificationListener.getDpsObjectCreatedEventForNode(scannerFdn);

        // Verify the scanner was created in DPS with File Collection disabled
        verifyScannerMigrated(nodeFdn, scannerFdn, dpsObjCreated);

        // Migration has finished - Turn Migration Off
        turnOffMigration();
    }

    @Test
    @InSequence(6)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void cleanupNodesAndScanners() {
        nodeCreationHelperBean.deleteAllNodes();
        for (final PMICScannerData scanner : scannerList) {
            scannerCreationHelperBean.deleScannersFromNode(scanner);
        }
    }

    private void turnOnMigration() throws Exception {
        pibSteps.updateConfigParam(PM_MIGRATION_PIB_PROPERTY, "true", Boolean.class);
        await().atLeast(1, TimeUnit.SECONDS);
    }

    private void turnOffMigration() throws Exception {
        pibSteps.updateConfigParam(PM_MIGRATION_PIB_PROPERTY, "false", Boolean.class);
    }

    private void turnPmFunctionON(final String nodeFdn) throws InterruptedException {
        nodeCreationHelperBean.changePmFunctionValue(nodeFdn, true);
    }

    private void verifyScannerMigrated(final String nodeFdn, final String scannerFdn, final DpsObjectCreatedEvent dpsObjectCreated) {
        Assert.assertNotNull("No scanner was created in DPS", dpsObjectCreated);
        final String createdScanner = dpsObjectCreated.getFdn();
        Assert.assertEquals("Scanner " + scannerFdn + " was not created", scannerFdn, createdScanner);
        Assert.assertTrue("PmFunction is OFF for node " + nodeFdn, nodeCreationHelperBean.getPmFunctionValue(nodeFdn));
    }

    private void verifyNodeAddedToSubscription() throws InterruptedException {
        final Map<String, Map<String, Object>> testSubscriptions = testUtils.findSubscriptions(getClass().getSimpleName());

        Assert.assertNotNull("Could not find subscriptions", testSubscriptions);
        Assert.assertFalse("Could not find subscriptions", testSubscriptions.keySet().isEmpty());

        for (final String name : testSubscriptions.keySet()) {
            final Map<String, Object> subscriptionAttr = testSubscriptions.get(name);
            @SuppressWarnings("unchecked") final List<Node> nodes = (List<Node>) subscriptionAttr.get(NODES.name());
            Assert.assertEquals("New node was not added to subscription", 2, nodes.size());
        }
    }

    private List<String> getTestNodes() {
        final NodeDataReader ndr = new NodeDataReader();
        final List<NodeData> nodeDataList = ndr.getNodeData(getClass().getSimpleName());
        final List<String> nodeNames = new ArrayList<>();
        for (final NodeData nodeData : nodeDataList) {
            nodeNames.add(nodeData.getNodeName());
        }
        return nodeNames;
    }

    private List<String> getTestSubscriptions() {
        final SubscriptionDataReader sdr = new SubscriptionDataReader(getClass().getSimpleName());
        final List<SubscriptionInfo> subInfoList = sdr.getSubscriptionList();
        final List<String> subNames = new ArrayList<>();
        for (final SubscriptionInfo subInfo : subInfoList) {
            subNames.add(subInfo.getName());
        }
        return subNames;
    }

}
