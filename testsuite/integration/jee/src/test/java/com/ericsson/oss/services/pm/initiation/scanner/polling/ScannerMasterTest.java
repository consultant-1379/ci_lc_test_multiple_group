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

package com.ericsson.oss.services.pm.initiation.scanner.polling;

import static com.ericsson.oss.services.pm.initiation.util.constants.ProcessTypeConstant.SCANNER_NAME_PREFIX_PREDEF;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_MODEL_NAME;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_NAME_POSTFIX_CONT_STATS;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_NAME_PREFIX_USERDEF;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.datalayer.dps.notification.event.AttributeChangeData;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsAttributeChangedEvent;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectCreatedEvent;
import com.ericsson.oss.itpf.datalayer.dps.notification.event.DpsObjectDeletedEvent;
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender;
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.services.pm.initiation.events.NodeScannerInfo;
import com.ericsson.oss.services.pm.initiation.events.ScannerPollingResult;
import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;
import com.ericsson.oss.services.pm.initiation.integration.SubscriptionOperationMessageSender;
import com.ericsson.oss.services.pm.initiation.model.resource.PMICScannerInfo;
import com.ericsson.oss.services.pm.initiation.model.resource.PMICScannerStatus;
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;
import com.ericsson.oss.services.pm.initiation.node.data.NodeData;
import com.ericsson.oss.services.pm.initiation.node.data.NodeDataReader;
import com.ericsson.oss.services.pm.initiation.scanner.data.PMICScannerData;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionDataReader;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionInfo;
import com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant;
import com.ericsson.oss.services.pm.test.requests.PmServiceRequestsTypes;
import com.ericsson.oss.services.pm.test.requests.SubscriptionOperationRequest;

@RunWith(Arquillian.class)
public class ScannerMasterTest extends InputBaseArquillian {

    final String className = getClass().getSimpleName();
    private final List<PMICScannerData> scannerList = new ArrayList<PMICScannerData>();
    Logger logger = LoggerFactory.getLogger(ScannerMasterTest.class);
    @Inject
    @Modeled
    private EventSender<ScannerPollingResult> sender;
    @Inject
    private SubscriptionOperationMessageSender operationSender;
    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;

    @Test
    @InSequence(3)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void createNodesForTest() throws InterruptedException {
        nodeCreationHelperBean.deleteAllSubscriptionsAndNodes();
        nodeCreationHelperBean.createNodes(className);
        nodeCreationHelperBean.createNodes(className + "_celltrace");
    }

    @Test
    @InSequence(4)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void createSubscriptionsForTest() throws InterruptedException {
        testResponseHolder.clear();
        operationSender.sendTestOperationMessageToPmService(new SubscriptionOperationRequest(PmServiceRequestsTypes.CREATE, null, null, className));
        operationSender.sendTestOperationMessageToPmService(
                new SubscriptionOperationRequest(PmServiceRequestsTypes.CREATE, null, null, className + "_celltrace"));
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Master_should_updated_scanner_id_in_dps() throws InterruptedException {
        final String nodeName = getTestNodes(className).get(0);
        final String subName = getTestSubscriptions(className).get(0);
        final String scannerName = SCANNER_NAME_PREFIX_USERDEF + subName + SCANNER_NAME_POSTFIX_CONT_STATS;
        final String nodeFdn = "NetworkElement=" + nodeName;
        final String scannerFdn = nodeFdn + "," + SCANNER_MODEL_NAME + "=" + scannerName;

        final PMICScannerInfo pmicScannerInfo = new PMICScannerInfo();
        pmicScannerInfo.id = "1";
        pmicScannerInfo.nodeName = nodeName;
        pmicScannerInfo.name = scannerName;
        pmicScannerInfo.status = PMICScannerStatus.ACTIVE;
        pmicScannerInfo.processType = ProcessType.STATS.name();

        // Create scanner in DPS
        createScannerOnNode(pmicScannerInfo, subName, SubscriptionType.STATISTICAL);

        // Create scanner polling result for scanner on real node
        final String scannerIdOnNode = "2";
        final List<NodeScannerInfo> scannerList = Arrays.asList(
                new NodeScannerInfo(Integer.parseInt(scannerIdOnNode), scannerName, PMICScannerStatus.ACTIVE.name(), ProcessType.STATS.name()));
        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Send scanner polling result
        sender.send(scannerPollingResult);
        final CountDownLatch cl = new CountDownLatch(1);
        dpsNotificationListener.setAttChangedCountDownLatch(cl);
        cl.await(60, TimeUnit.SECONDS);
        final DpsAttributeChangedEvent dpsAttrChanged = dpsNotificationListener.getDpsAttributeChangedEventForNode(scannerFdn);
        verifyScannerIdUpdated(dpsAttrChanged);

    }

    @Test
    @InSequence(6)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Master_should_delete_scanner_from_DPS_if_sub_INACTIVE() throws InterruptedException {
        final String nodeName = getTestNodes(className).get(1);
        final String subName = getTestSubscriptions(className).get(1);
        final String scannerName = SCANNER_NAME_PREFIX_USERDEF + subName + SCANNER_NAME_POSTFIX_CONT_STATS;
        final String nodeFdn = "NetworkElement=" + nodeName;
        final String scannerFdn = nodeFdn + "," + SCANNER_MODEL_NAME + "=" + scannerName;

        final PMICScannerInfo pmicScannerInfo = new PMICScannerInfo();
        pmicScannerInfo.id = "1";
        pmicScannerInfo.nodeName = nodeName;
        pmicScannerInfo.name = scannerName;
        pmicScannerInfo.status = PMICScannerStatus.ACTIVE;
        pmicScannerInfo.processType = ProcessType.STATS.name();

        // Create scanner in DPS
        createScannerOnNode(pmicScannerInfo, subName, SubscriptionType.STATISTICAL);

        // Create scanner polling result for scanner on real node
        final List<NodeScannerInfo> scannerList = Arrays.asList(
                new NodeScannerInfo(10003, "PREDEF.10003.CELLTRACE", PMICScannerStatus.ACTIVE.name(), ProcessType.NORMAL_PRIORITY_CELLTRACE.name()));
        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Send scanner polling result
        sender.send(scannerPollingResult);
        final CountDownLatch cl = new CountDownLatch(1);
        dpsNotificationListener.setObjDeletedCountDownLatch(cl);
        cl.await(60, TimeUnit.SECONDS);
        final DpsObjectDeletedEvent dpsObjDeleted = dpsNotificationListener.getDpsObjectDeletedEventForNode(scannerFdn);
        verifyScannerDeleted(scannerFdn, dpsObjDeleted);
    }

    @Test
    @InSequence(7)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Master_should_delete_scanner_from_dps_if_node_not_in_subscription() throws InterruptedException {
        final String nodeName = getTestNodes(className).get(2);
        final String subName = getTestSubscriptions(className).get(2);
        final String scannerName = SCANNER_NAME_PREFIX_USERDEF + subName + SCANNER_NAME_POSTFIX_CONT_STATS;
        final String nodeFdn = "NetworkElement=" + nodeName;
        final String scannerFdn = nodeFdn + "," + SCANNER_MODEL_NAME + "=" + scannerName;

        final PMICScannerInfo pmicScannerInfo = new PMICScannerInfo();
        pmicScannerInfo.id = "1";
        pmicScannerInfo.nodeName = nodeName;
        pmicScannerInfo.name = scannerName;
        pmicScannerInfo.status = PMICScannerStatus.ACTIVE;
        pmicScannerInfo.processType = ProcessType.STATS.name();

        // Create scanner in DPS
        createScannerOnNode(pmicScannerInfo, subName, SubscriptionType.STATISTICAL);

        // Create scanner polling result for scanner on real node
        final List<NodeScannerInfo> scannerList = Arrays.asList(
                new NodeScannerInfo(10003, "PREDEF.10003.CELLTRACE", PMICScannerStatus.ACTIVE.name(), ProcessType.NORMAL_PRIORITY_CELLTRACE.name()));
        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Send scanner polling result
        sender.send(scannerPollingResult);
        final CountDownLatch cl = new CountDownLatch(1);
        dpsNotificationListener.setObjDeletedCountDownLatch(cl);
        cl.await(60, TimeUnit.SECONDS);
        final DpsObjectDeletedEvent dpsObjDeleted = dpsNotificationListener.getDpsObjectDeletedEventForNode(scannerFdn);
        verifyScannerDeleted(scannerFdn, dpsObjDeleted);
    }

    @Test
    @InSequence(8)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Master_should_delete_scanner_from_dps_if_subscription_doesnt_exist() throws InterruptedException {
        final String nodeName = getTestNodes(className).get(3);
        final String subName = "SubscriptionThatDoesntExist";
        final String scannerName = SCANNER_NAME_PREFIX_USERDEF + subName + SCANNER_NAME_POSTFIX_CONT_STATS;
        final String nodeFdn = "NetworkElement=" + nodeName;
        final String scannerFdn = nodeFdn + "," + SCANNER_MODEL_NAME + "=" + scannerName;

        final PMICScannerInfo pmicScannerInfo = new PMICScannerInfo();
        pmicScannerInfo.id = "1";
        pmicScannerInfo.nodeName = nodeName;
        pmicScannerInfo.name = scannerName;
        pmicScannerInfo.status = PMICScannerStatus.ACTIVE;
        pmicScannerInfo.processType = ProcessType.STATS.name();

        // Create scanner in DPS
        createScannerOnNode(pmicScannerInfo, subName, SubscriptionType.STATISTICAL);

        // Create scanner polling result for scanner on real node
        final List<NodeScannerInfo> scannerList = Arrays.asList(
                new NodeScannerInfo(10003, "PREDEF.10003.CELLTRACE", PMICScannerStatus.ACTIVE.name(), ProcessType.NORMAL_PRIORITY_CELLTRACE.name()));
        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Send scanner polling result
        sender.send(scannerPollingResult);
        final CountDownLatch cl = new CountDownLatch(1);
        dpsNotificationListener.setObjDeletedCountDownLatch(cl);
        cl.await(60, TimeUnit.SECONDS);
        final DpsObjectDeletedEvent dpsObjDeleted = dpsNotificationListener.getDpsObjectDeletedEventForNode(scannerFdn);
        verifyScannerDeleted(scannerFdn, dpsObjDeleted);
    }

    @Test
    @InSequence(9)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Master_should_create_scanner_in_dps_if_sub_ACTIVE() throws InterruptedException {
        final String nodeName = getTestNodes(className).get(12);
        final String subName = getTestSubscriptions(className).get(9);
        final String scannerName = SCANNER_NAME_PREFIX_USERDEF + subName + SCANNER_NAME_POSTFIX_CONT_STATS;
        final String nodeFdn = "NetworkElement=" + nodeName;
        final String scannerFdn = nodeFdn + "," + SCANNER_MODEL_NAME + "=" + scannerName;

        // Create scanner polling result for scanner on node
        final NodeScannerInfo scanner1 = new NodeScannerInfo(1, scannerName, PMICScannerStatus.ACTIVE.name(), ProcessType.STATS.name());

        final List<NodeScannerInfo> scannerList = new ArrayList<>();
        scannerList.add(scanner1);

        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Send scanner polling result
        sender.send(scannerPollingResult);
        final CountDownLatch cl = new CountDownLatch(1);
        dpsNotificationListener.setObjCreatedCountDownLatch(cl);
        cl.await(60, TimeUnit.SECONDS);
        final DpsObjectCreatedEvent dpsObjCreated = dpsNotificationListener.getDpsObjectCreatedEventForNode(scannerFdn);
        verifyScannerCreated(scannerFdn, dpsObjCreated);
    }

    @Test
    @InSequence(10)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Master_should_add_PREDEF_scanner_to_dps() throws InterruptedException {
        final String nodeName = getTestNodes(className).get(4);
        final String scannerName = SCANNER_NAME_PREFIX_PREDEF + "STATS";
        final String nodeFdn = "NetworkElement=" + nodeName;
        final String scannerFdn = nodeFdn + "," + SCANNER_MODEL_NAME + "=" + scannerName;

        // Create scanner polling result for scanner on real node
        final String scannerIdOnNode = "1";
        final List<NodeScannerInfo> scannerList = Arrays.asList(
                new NodeScannerInfo(Integer.parseInt(scannerIdOnNode), scannerName, PMICScannerStatus.ACTIVE.name(), ProcessType.STATS.name()));
        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Send scanner polling result
        sender.send(scannerPollingResult);
        final CountDownLatch cl = new CountDownLatch(1);
        dpsNotificationListener.setObjCreatedCountDownLatch(cl);
        cl.await(60, TimeUnit.SECONDS);
        final DpsObjectCreatedEvent dpsObjCreated = dpsNotificationListener.getDpsObjectCreatedEventForNode(scannerFdn);
        verifyScannerCreated(scannerFdn, dpsObjCreated);
    }

    @Test
    @InSequence(11)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Master_should_send_activation_for_ACTIVE_dps_scanner_not_on_node() throws InterruptedException {
        final String nodeName = getTestNodes(className).get(5);
        final String subName = getTestSubscriptions(className).get(3);
        final String scannerName = SCANNER_NAME_PREFIX_USERDEF + subName + SCANNER_NAME_POSTFIX_CONT_STATS;
        final String nodeFdn = "NetworkElement=" + nodeName;

        final PMICScannerInfo pmicScannerInfo = new PMICScannerInfo();
        pmicScannerInfo.id = "1";
        pmicScannerInfo.nodeName = nodeName;
        pmicScannerInfo.name = scannerName;
        pmicScannerInfo.status = PMICScannerStatus.ACTIVE;
        pmicScannerInfo.processType = ProcessType.STATS.name();

        // Create scanner in DPS
        createScannerOnNode(pmicScannerInfo, subName, SubscriptionType.STATISTICAL);

        // Create scanner polling result for real node
        final List<NodeScannerInfo> scannerList = Arrays.asList(
                new NodeScannerInfo(10003, "PREDEF.10003.CELLTRACE", PMICScannerStatus.ACTIVE.name(), ProcessType.NORMAL_PRIORITY_CELLTRACE.name()));
        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Send scanner polling result
        sender.send(scannerPollingResult);
        final CountDownLatch cl = new CountDownLatch(1);
        tasksListener.setActivationCountdownLatch(cl);
        cl.await(60, TimeUnit.SECONDS);

        final int activationJobsSentToNode = tasksListener.getNumberOfActivationTasksReceived(nodeFdn);

        Assert.assertEquals(1, activationJobsSentToNode);
    }

    @Test
    @InSequence(12)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Master_should_send_delete_event_if_sub_INACTIVE() throws InterruptedException {
        final String nodeName = getTestNodes(className).get(6);
        final String subName = getTestSubscriptions(className).get(4);
        final String scannerName = SCANNER_NAME_PREFIX_USERDEF + subName + SCANNER_NAME_POSTFIX_CONT_STATS;
        final String nodeFdn = "NetworkElement=" + nodeName;

        // Create scanner polling result for real node
        final String scannerIdOnNode = "2";
        final NodeScannerInfo scanner1 = new NodeScannerInfo(Integer.parseInt(scannerIdOnNode), scannerName, PMICScannerStatus.ACTIVE.name(),
                ProcessType.STATS.name());
        final List<NodeScannerInfo> scannerList = new ArrayList<NodeScannerInfo>();
        scannerList.add(scanner1);
        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Create scanner on node in DPS
        final List<String> nodeList = new ArrayList<>();
        nodeList.add(nodeName);
        final Map<String, Object> scannerattr = createScannerAttributesMap(60, "100000", PMICScannerStatus.ACTIVE, "1", scannerName, "STATS");
        final PMICScannerData pmicscannerData = new PMICScannerData(scannerName, nodeList, scannerattr);
        scannerCreationHelperBean.createScannerOnNodes(pmicscannerData);

        // Send scanner polling result
        sender.send(scannerPollingResult);
        final CountDownLatch cl = new CountDownLatch(1);
        tasksListener.setScannerDeletionTaskCL(cl);
        cl.await(60, TimeUnit.SECONDS);

        final int scannerDeletionTasksReceived = tasksListener.getNumberOfScannerDeletionTasksReceived(nodeFdn);

        Assert.assertEquals(1, scannerDeletionTasksReceived);
    }

    @Test
    @InSequence(13)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Master_should_send_delete_event_if_node_not_in_sub() throws InterruptedException {
        final String nodeName = getTestNodes(className).get(2);
        final String subName = getTestSubscriptions(className).get(2);
        final String scannerName = SCANNER_NAME_PREFIX_USERDEF + subName + SCANNER_NAME_POSTFIX_CONT_STATS;
        final String nodeFdn = "NetworkElement=" + nodeName;

        // Create scanner polling result for real node
        final String scannerIdOnNode = "2";
        final NodeScannerInfo scanner1 = new NodeScannerInfo(Integer.parseInt(scannerIdOnNode), scannerName, PMICScannerStatus.ACTIVE.name(),
                ProcessType.STATS.name());
        final List<NodeScannerInfo> scannerList = new ArrayList<NodeScannerInfo>();
        scannerList.add(scanner1);
        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Create scanner on node in DPS
        final List<String> nodeList = new ArrayList<>();
        nodeList.add(nodeName);
        final Map<String, Object> scannerattr = createScannerAttributesMap(60, "100000", PMICScannerStatus.ACTIVE, "1", scannerName, "STATS");
        final PMICScannerData pmicscannerData = new PMICScannerData(scannerName, nodeList, scannerattr);
        scannerCreationHelperBean.createScannerOnNodes(pmicscannerData);

        // Send scanner polling result
        sender.send(scannerPollingResult);
        final CountDownLatch cl = new CountDownLatch(1);
        tasksListener.setScannerDeletionTaskCL(cl);
        cl.await(60, TimeUnit.SECONDS);

        final int scannerDeletionTasksReceived = tasksListener.getNumberOfScannerDeletionTasksReceived(nodeFdn);

        Assert.assertEquals(1, scannerDeletionTasksReceived);
    }

    @Test
    @InSequence(14)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Master_should_send_delete_event_if_sub_doesnt_exist() throws InterruptedException {
        final long startTime = System.currentTimeMillis();

        final String nodeName = getTestNodes(className).get(7);
        final String subName = "SubscriptionThatDoesntExist";
        final String scannerName = SCANNER_NAME_PREFIX_USERDEF + subName + SCANNER_NAME_POSTFIX_CONT_STATS;
        final String nodeFdn = "NetworkElement=" + nodeName;

        // Create scanner polling result for real node
        final String scannerIdOnNode = "2";
        final NodeScannerInfo scanner1 = new NodeScannerInfo(Integer.parseInt(scannerIdOnNode), scannerName, PMICScannerStatus.ACTIVE.name(),
                ProcessType.STATS.name());
        final List<NodeScannerInfo> scannerList = new ArrayList<NodeScannerInfo>();
        scannerList.add(scanner1);
        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Create scanner on node in DPS
        final List<String> nodeList = new ArrayList<>();
        nodeList.add(nodeName);
        final Map<String, Object> scannerattr = createScannerAttributesMap(60, "100000", PMICScannerStatus.ACTIVE, "1", scannerName, "STATS");
        final PMICScannerData pmicscannerData = new PMICScannerData(scannerName, nodeList, scannerattr);
        scannerCreationHelperBean.createScannerOnNodes(pmicscannerData);

        // Send scanner polling result
        sender.send(scannerPollingResult);
        final CountDownLatch cl = new CountDownLatch(1);
        tasksListener.setScannerDeletionTaskCL(cl);
        cl.await(60, TimeUnit.SECONDS);

        final int scannerDeletionTasksReceived = tasksListener.getNumberOfScannerDeletionTasksReceived(nodeFdn);

        final long endTime = System.currentTimeMillis();
        logger.debug("Scanner_Master_should_send_deactivate_event_if_sub_doesnt_exist took " + (endTime - startTime));

        Assert.assertEquals(1, scannerDeletionTasksReceived);

        final CountDownLatch c2 = new CountDownLatch(1);
        dpsNotificationListener.setObjDeletedCountDownLatch(c2);
    }

    @Test
    @InSequence(15)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Master_should_send_activation_event_if_scanner_is_ERROR_and_node_INACTIVE() throws InterruptedException {
        final String nodeName = getTestNodes(className).get(8);
        final String subName = getTestSubscriptions(className).get(5);
        final String scannerName = SCANNER_NAME_PREFIX_USERDEF + subName + SCANNER_NAME_POSTFIX_CONT_STATS;
        final String nodeFdn = "NetworkElement=" + nodeName;
        final String scannerId = "2";

        // Create scanner on node in DPS
        final List<String> nodeList = new ArrayList<>();
        nodeList.add(nodeName);
        final Map<String, Object> scannerattr = createScannerAttributesMap(60, "100000", PMICScannerStatus.ERROR, scannerId, scannerName, "STATS");
        final PMICScannerData pmicscannerData = new PMICScannerData(scannerName, nodeList, scannerattr);
        scannerCreationHelperBean.createScannerOnNodes(pmicscannerData);

        // Create scanner polling result for real node
        final NodeScannerInfo scanner1 = new NodeScannerInfo(Integer.parseInt(scannerId), scannerName, PMICScannerStatus.INACTIVE.name(),
                ProcessType.STATS.name());
        final List<NodeScannerInfo> scannerList = new ArrayList<NodeScannerInfo>();
        scannerList.add(scanner1);
        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Send scanner polling result
        sender.send(scannerPollingResult);
        final CountDownLatch cl = new CountDownLatch(1);
        tasksListener.setActivationCountdownLatch(cl);
        cl.await(60, TimeUnit.SECONDS);

        final int activationJobsSentToNode = tasksListener.getNumberOfActivationTasksReceived(nodeFdn);

        Assert.assertEquals(1, activationJobsSentToNode);
    }

    @Test
    @InSequence(16)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Master_should_send_activation_event_if_scanner_is_UNKNOWN_and_node_INACTIVE() throws InterruptedException {
        final String nodeName = getTestNodes(className).get(11);
        final String subName = getTestSubscriptions(className).get(8);
        final String scannerName = SCANNER_NAME_PREFIX_USERDEF + subName + SCANNER_NAME_POSTFIX_CONT_STATS;
        final String nodeFdn = "NetworkElement=" + nodeName;
        final String scannerId = "2";

        // Create scanner on node in DPS
        final List<String> nodeList = new ArrayList<>();
        nodeList.add(nodeName);
        final Map<String, Object> scannerattr = createScannerAttributesMap(60, "100000", PMICScannerStatus.UNKNOWN, scannerId, scannerName,
                "UNKNOWN");
        final PMICScannerData pmicscannerData = new PMICScannerData(scannerName, nodeList, scannerattr);
        scannerCreationHelperBean.createScannerOnNodes(pmicscannerData);

        // Create scanner polling result for real node
        final NodeScannerInfo scanner1 = new NodeScannerInfo(Integer.parseInt(scannerId), scannerName, PMICScannerStatus.INACTIVE.name(),
                ProcessType.STATS.name());
        final List<NodeScannerInfo> scannerList = new ArrayList<NodeScannerInfo>();
        scannerList.add(scanner1);
        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Send scanner polling result
        sender.send(scannerPollingResult);
        final CountDownLatch cl = new CountDownLatch(1);
        tasksListener.setActivationCountdownLatch(cl);
        cl.await(60, TimeUnit.SECONDS);

        final int activationJobsSentToNode = tasksListener.getNumberOfActivationTasksReceived(nodeFdn);

        Assert.assertEquals(1, activationJobsSentToNode);
    }

    @Test
    @InSequence(17)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Master_should_update_dps_if_scanner_is_not_ACTIVE_but_node_is_ACTIVE() throws InterruptedException {
        final String nodeName = getTestNodes(className).get(9);
        final String subName = getTestSubscriptions(className).get(6);
        final String scannerName = SCANNER_NAME_PREFIX_USERDEF + subName + SCANNER_NAME_POSTFIX_CONT_STATS;
        final String nodeFdn = "NetworkElement=" + nodeName;
        final String scannerFdn = nodeFdn + "," + SCANNER_MODEL_NAME + "=" + scannerName;
        final String scannerId = "2";

        // Create scanner on node in DPS
        final List<String> nodeList = new ArrayList<>();
        nodeList.add(nodeName);
        final Map<String, Object> scannerattr = createScannerAttributesMap(60, "100000", PMICScannerStatus.ERROR, scannerId, scannerName, "STATS");
        final PMICScannerData pmicscannerData = new PMICScannerData(scannerName, nodeList, scannerattr);
        scannerCreationHelperBean.createScannerOnNodes(pmicscannerData);

        // Create scanner polling result for real node
        final NodeScannerInfo scanner1 = new NodeScannerInfo(Integer.parseInt(scannerId), scannerName, PMICScannerStatus.ACTIVE.name(),
                ProcessType.STATS.name());
        final List<NodeScannerInfo> scannerList = new ArrayList<NodeScannerInfo>();
        scannerList.add(scanner1);
        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Send scanner polling result
        sender.send(scannerPollingResult);
        final CountDownLatch cl = new CountDownLatch(1);
        dpsNotificationListener.setAttChangedCountDownLatch(cl);
        cl.await(60, TimeUnit.SECONDS);
        final DpsAttributeChangedEvent dpsAttrChanged = dpsNotificationListener.getDpsAttributeChangedEventForNode(scannerFdn);
        verifyScannerStatusUpdated(dpsAttrChanged, "ACTIVE");
    }

    @Test
    @InSequence(18)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Master_should_deactivate_duplicate_scanners_on_node() throws InterruptedException {
        final String nodeName = getTestNodes(className).get(10);
        final String subName = getTestSubscriptions(className).get(7);
        final String scannerName = SCANNER_NAME_PREFIX_USERDEF + subName + SCANNER_NAME_POSTFIX_CONT_STATS;
        final String nodeFdn = "NetworkElement=" + nodeName;

        // Create scanner on node in DPS
        final List<String> nodeList = new ArrayList<>();
        nodeList.add(nodeName);
        final Map<String, Object> scannerattr = createScannerAttributesMap(60, "100000", PMICScannerStatus.ACTIVE, "7", scannerName, "STATS");
        final PMICScannerData pmicscannerData = new PMICScannerData(scannerName, nodeList, scannerattr);
        scannerCreationHelperBean.createScannerOnNodes(pmicscannerData);

        // Create scanner polling result for real node
        final NodeScannerInfo scanner1 = new NodeScannerInfo(4, scannerName, PMICScannerStatus.ACTIVE.name(), ProcessType.STATS.name());
        final NodeScannerInfo scanner2 = new NodeScannerInfo(7, scannerName, PMICScannerStatus.ACTIVE.name(), ProcessType.STATS.name());
        final NodeScannerInfo scanner3 = new NodeScannerInfo(5, scannerName, PMICScannerStatus.ACTIVE.name(), ProcessType.STATS.name());
        final NodeScannerInfo scanner4 = new NodeScannerInfo(6, scannerName, PMICScannerStatus.ACTIVE.name(), ProcessType.STATS.name());
        final List<NodeScannerInfo> scannerList = new ArrayList<>();
        scannerList.add(scanner1);
        scannerList.add(scanner2);
        scannerList.add(scanner3);
        scannerList.add(scanner4);
        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Send scanner polling result
        final CountDownLatch cl = new CountDownLatch(3);
        tasksListener.setScannerDeletionTaskCL(cl);
        sender.send(scannerPollingResult);
        cl.await(60, TimeUnit.SECONDS);
        final int scannerDeletionTasksReceived = tasksListener.getNumberOfScannerDeletionTasksReceived(nodeFdn);
        Assert.assertEquals(3, scannerDeletionTasksReceived);
    }

    @Test
    @InSequence(19)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void Scanner_Master_should_not_create_scanner_in_dps_if_parent_node_does_not_exist() throws Exception {
        final String nodeName = "nodeThatDoesNotExist";
        final String scannerName = SCANNER_NAME_PREFIX_PREDEF + "STATS";
        final String nodeFdn = "NetworkElement=" + nodeName;
        final String scannerFdn = nodeFdn + "," + SCANNER_MODEL_NAME + "=" + scannerName;

        // Create scanner polling result for scanner on real node
        final String scannerIdOnNode = "1";
        final NodeScannerInfo scanner1 = new NodeScannerInfo(Integer.parseInt(scannerIdOnNode), scannerName, PMICScannerStatus.ACTIVE.name(),
                ProcessType.STATS.name());

        final List<NodeScannerInfo> scannerList = new ArrayList<NodeScannerInfo>();
        scannerList.add(scanner1);
        final ScannerPollingResult scannerPollingResult = new ScannerPollingResult(nodeFdn, scannerList);
        scannerPollingResult.setFailed(false);

        // Send scanner polling result
        sender.send(scannerPollingResult);
        final CountDownLatch cl = new CountDownLatch(1);
        dpsNotificationListener.setObjCreatedCountDownLatch(cl);
        cl.await(10, TimeUnit.SECONDS);
        final DpsObjectCreatedEvent dpsObjCreated = dpsNotificationListener.getDpsObjectCreatedEventForNode(scannerFdn);
        Assert.assertNull(dpsObjCreated);
    }

    @Test
    @InSequence(21)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void cleanupNodesAndScanners() {
        nodeCreationHelperBean.deleteAllNodes();
        for (final PMICScannerData scanner : scannerList) {
            scannerCreationHelperBean.deleScannersFromNode(scanner);
        }
    }

    /**
     * @return
     */
    private List<String> getTestNodes(final String fileName) {
        final NodeDataReader ndr = new NodeDataReader();
        final List<NodeData> nodeDataList = ndr.getNodeData(fileName);
        final List<String> nodeNames = new ArrayList<>();
        for (final NodeData nodeData : nodeDataList) {
            nodeNames.add(nodeData.getNodeName());
        }
        return nodeNames;
    }

    private List<String> getTestSubscriptions(final String fileName) {
        final SubscriptionDataReader sdr = new SubscriptionDataReader(fileName);
        final List<SubscriptionInfo> subInfoList = sdr.getSubscriptionList();
        final List<String> subNames = new ArrayList<>();
        for (final SubscriptionInfo subInfo : subInfoList) {
            subNames.add(subInfo.getName());
        }
        return subNames;
    }

    /**
     * @param dpsAttrChanged
     */
    private void verifyScannerIdUpdated(final DpsAttributeChangedEvent dpsAttrChanged) {
        Assert.assertNotNull("No scanner id was updated in DPS", dpsAttrChanged);
        final Set<AttributeChangeData> changedAttr = dpsAttrChanged.getChangedAttributes();
        for (final AttributeChangeData attributeChangeData : changedAttr) {
            if (attributeChangeData.getName().equals(SubscriptionOperationConstant.SCANNER_ID)) {
                Assert.assertEquals("2", attributeChangeData.getNewValue());
                Assert.assertEquals("1", attributeChangeData.getOldValue());
            }
        }
    }

    private void verifyScannerStatusUpdated(final DpsAttributeChangedEvent dpsAttrChanged, final String newStatus) {
        Assert.assertNotNull("No scanner status was updated in DPS", dpsAttrChanged);
        final Set<AttributeChangeData> changedAttr = dpsAttrChanged.getChangedAttributes();
        for (final AttributeChangeData attributeChangeData : changedAttr) {
            if (attributeChangeData.getName().equals(SubscriptionOperationConstant.SCANNER_STATUS_ATTRIBUTE)) {
                Assert.assertEquals(newStatus, attributeChangeData.getNewValue());
            }
        }
    }

    private void verifyScannerDeleted(final String scannerFdn, final DpsObjectDeletedEvent dpsObjectDeleted) {
        Assert.assertNotNull("No scanner was deleted from DPS", dpsObjectDeleted);
        final String deletedScanner = dpsObjectDeleted.getFdn();
        Assert.assertEquals(scannerFdn, deletedScanner);
    }

    private void verifyScannerCreated(final String scannerFdn, final DpsObjectCreatedEvent dpsObjectCreated) {
        Assert.assertNotNull("No scanner was created in DPS", dpsObjectCreated);
        final String createdScanner = dpsObjectCreated.getFdn();
        Assert.assertEquals(scannerFdn, createdScanner);
    }

    private void createScannerOnNode(final PMICScannerInfo pmicScannerInfo, final String subscriptionName, final SubscriptionType subscriptionType)
            throws InterruptedException {
        final List<String> nodeList = Arrays.asList(pmicScannerInfo.nodeName);

        final String subscriptionID = getSubscriptionIdMatchingSubscriptionName(subscriptionName, subscriptionType);
        Assert.assertNotNull("Cannot extract subscription id for subscription because it is null", subscriptionID);

        final Map<String, Object> scannerAttributesMap = createScannerAttributesMap(60, subscriptionID, pmicScannerInfo);
        final PMICScannerData pmicScannerData = new PMICScannerData(pmicScannerInfo.name, nodeList, scannerAttributesMap);
        scannerCreationHelperBean.createScannerOnNodes(pmicScannerData);
        waitForScannerToBeCreated(pmicScannerData.getNodes().size());
    }

    private void updateScannerOnDps(final PMICScannerInfo pmicScannerInfo, final String subscriptionName, final SubscriptionType subscriptionType)
            throws InterruptedException {
        scannerCreationHelperBean.updateScannerAttributesOnNode(new PMICScannerData(pmicScannerInfo.name, Arrays.asList(pmicScannerInfo.nodeName),
                createScannerAttributesMap(60, getSubscriptionIdMatchingSubscriptionName(subscriptionName, subscriptionType), pmicScannerInfo)));
    }
}
