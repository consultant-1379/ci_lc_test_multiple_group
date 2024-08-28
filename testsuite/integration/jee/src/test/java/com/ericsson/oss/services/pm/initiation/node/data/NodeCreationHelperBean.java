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

import static com.ericsson.oss.pmic.dto.node.Node.PM_FUNCTION_PREFIX;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.MANAGED_ELEMENT_ID;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.MANAGED_ELEMENT_TYPE;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.NETWORK_ELEMENT_ID;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.NETWORK_ELEMENT_NAMESPACE;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.NETWORK_ELEMENT_TYPE;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.NETWORK_ELEMENT_VERSION;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.NE_TYPE;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.NODE_VERSION_14B;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.OSS_MODEL_IDENTITY;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.OSS_PREFIX;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.OSS_PREFIX_VALUE;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.PLATFORM_TYPE;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.PM_ENABLE;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.PM_FUNCTION_ID;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.PM_FUNCTION_MODEL_NAME;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.PM_FUNCTION_NAMESPACE;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.PM_FUNCTION_VERSION;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.SGSN_MME_ID;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.SGSN_MME_MANAGED_ELEMENT_NAMESPACE;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.SGSN_MME_MANAGED_ELEMENT_VERSION;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.SGSN_MME_NAMESPACE;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.SGSN_MME_TYPE;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.SGSN_MME_VERSION;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.SGSN_NE_TYPE_VALUE;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.TECHNOLOGY_DOMAIN;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.datalayer.dps.BucketProperties;
import com.ericsson.oss.itpf.datalayer.dps.DataBucket;
import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService;
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject;
import com.ericsson.oss.itpf.datalayer.dps.persistence.PersistenceObject;
import com.ericsson.oss.itpf.datalayer.dps.query.Query;
import com.ericsson.oss.itpf.datalayer.dps.query.QueryBuilder;
import com.ericsson.oss.itpf.datalayer.dps.query.QueryExecutor;
import com.ericsson.oss.itpf.datalayer.dps.query.TypeRestrictionBuilder;
import com.ericsson.oss.pmic.dto.node.Node;

/**
 * This class helps creating any NetworkElement or any NetworkElement's child MO.
 *
 * @author ekamkal
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
public class NodeCreationHelperBean {

    /**
     *
     */
    private static final String PM_SOURCE_DIR = "sourceDir";
    private static final String PM_FUNCTION_MO_NAME = "PmFunction=1";
    private static final String PM_ENABLED = "pmEnabled";

    private final static String EAI_NAMESPACE = "MEDIATION";
    private final static String EAI_TYPE = "EntityAddressingInformation";

    private static final String PMIC_NAMESPACE = "pmic_subscription";
    private static final String PMIC_SUBSCRIPTION = "Subscription";

    @EJB(lookup = "java:/datalayer/DataPersistenceService")
    DataPersistenceService dps;

    @Inject
    NodeDataReader nodeDataReader;

    Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Returns the LiveBucket using the {@link BucketProperties#SUPPRESS_MEDIATION}
     *
     * @return
     */
    private DataBucket getLiveBucketSupressingMediation() {
        final DataBucket liveBucket = dps.getDataBucket("Live", BucketProperties.SUPPRESS_MEDIATION);
        return liveBucket;
    }

    public void createNode(final NodeData nodeData) {
        final Map<String, Object> mandatoryNetworkElementAttributes = new HashMap<String, Object>();
        mandatoryNetworkElementAttributes.put(OSS_MODEL_IDENTITY, nodeData.getOssModelIdentity());
        final ManagedObject networkElementMo = createNodeWithAttributes(nodeData, mandatoryNetworkElementAttributes);
        createPmFunctionMO(networkElementMo, true);
        if (SGSN_NE_TYPE_VALUE.equals(nodeData.getNeType())) {
            final ManagedObject managedElementMo = createSgsnMmeManagedElementMO(nodeData);
            final ManagedObject sgsnMmeMo = createSgsnMmeMO(managedElementMo);
            createSgsnPlmnMO(sgsnMmeMo);
        }
    }

    public void createNode(final NodeData nodeData, final boolean pmFunctionEnabled) {
        final Map<String, Object> mandatoryNetworkElementAttributes = new HashMap<String, Object>();
        mandatoryNetworkElementAttributes.put(OSS_MODEL_IDENTITY, NODE_VERSION_14B);
        final ManagedObject networkElementMo = createNodeWithAttributes(nodeData, mandatoryNetworkElementAttributes);
        createPmFunctionMO(networkElementMo, pmFunctionEnabled);
    }

    private void createNode(final NodeData nodeData, final String nodeVersion) {
        final Map<String, Object> mandatoryNetworkElementAttributes = new HashMap<String, Object>();
        mandatoryNetworkElementAttributes.put(OSS_MODEL_IDENTITY, nodeVersion);
        final ManagedObject networkElementMo = createNodeWithAttributes(nodeData, mandatoryNetworkElementAttributes);
        createPmFunctionMO(networkElementMo, true);
    }

    private ManagedObject createNodeWithAttributes(final NodeData nodeData, final Map<String, Object> attributes) {
        final DataBucket liveBucket = getLiveBucketSupressingMediation();
        attributes.put(NETWORK_ELEMENT_ID, nodeData.getNodeName());
        attributes.put(NE_TYPE, nodeData.getNeType());
        if (nodeData.getPlatformType() != null) {
            attributes.put(PLATFORM_TYPE, nodeData.getPlatformType());
        }
        if (nodeData.getTechnologyDomain() != null) {
            attributes.put(TECHNOLOGY_DOMAIN, nodeData.getTechnologyDomain());
        }
        attributes.put(OSS_PREFIX, OSS_PREFIX_VALUE);

        final ManagedObject networkElementMo = liveBucket.getMibRootBuilder().namespace(NETWORK_ELEMENT_NAMESPACE).type(NETWORK_ELEMENT_TYPE)
                .version(NETWORK_ELEMENT_VERSION).name(nodeData.getNodeName()).addAttributes(attributes).parent(null).create();
        logger.info("NetworkElement {} created with fdn: {} poId: {} ne_type: {}", networkElementMo, networkElementMo.getFdn(),
                networkElementMo.getPoId(), networkElementMo.getAllAttributes().get(NE_TYPE));
        return networkElementMo;
    }

    private void createPmFunctionMO(final ManagedObject networkElementMo, final boolean pmFunctionEnabled) {
        final DataBucket liveBucket = getLiveBucketSupressingMediation();
        final Map<String, Object> mandatoryPmFunctionAttributes = new HashMap<String, Object>();
        mandatoryPmFunctionAttributes.put(PM_FUNCTION_ID, "1");
        mandatoryPmFunctionAttributes.put(PM_ENABLE, pmFunctionEnabled);

        final ManagedObject pmFunctionMo = liveBucket.getMibRootBuilder().namespace(PM_FUNCTION_NAMESPACE).type(PM_FUNCTION_MODEL_NAME)
                .version(PM_FUNCTION_VERSION).name("1").addAttributes(mandatoryPmFunctionAttributes).parent(networkElementMo).create();
        logger.info("PmFunction {} created with fdn: {}", pmFunctionMo, pmFunctionMo.getFdn());
    }

    private ManagedObject createSgsnMmeManagedElementMO(final NodeData nodeData) {
        final DataBucket liveBucket = getLiveBucketSupressingMediation();
        final Map<String, Object> attributes = new HashMap<>();
        attributes.put(MANAGED_ELEMENT_ID, nodeData.getNodeName());
        attributes.put(NE_TYPE, nodeData.getNeType());

        final ManagedObject mo = liveBucket.getMibRootBuilder()
                .namespace(SGSN_MME_MANAGED_ELEMENT_NAMESPACE)
                .type(MANAGED_ELEMENT_TYPE)
                .version(SGSN_MME_MANAGED_ELEMENT_VERSION).name(nodeData.getNodeName()).addAttributes(attributes)
                .parent(null).create();
        logger.info("ManagedElement {} created with fdn: {}", mo, mo.getFdn());

        return mo;
    }

    private ManagedObject createSgsnMmeMO(final ManagedObject parentMo) {
        final DataBucket liveBucket = getLiveBucketSupressingMediation();
        final Map<String, Object> attributes = new HashMap<>();
        attributes.put(SGSN_MME_ID, "1");

        final ManagedObject mo = liveBucket.getMibRootBuilder()
                .namespace(SGSN_MME_NAMESPACE)
                .type(SGSN_MME_TYPE)
                .version(SGSN_MME_VERSION).name("1").addAttributes(attributes)
                .parent(parentMo).create();
        logger.info("SgsnMme {} created with fdn: {}", mo, mo.getFdn());

        return mo;
    }

    private void createSgsnPlmnMO(final ManagedObject parentMo) {
        final DataBucket liveBucket = getLiveBucketSupressingMediation();
        final Map<String, Object> attributes = new HashMap<>();
        attributes.put(Node.PlmnSgsn2720Attribute.mobileCountryCode.name(), "994");
        attributes.put(Node.PlmnSgsn2720Attribute.mobileNetworkCode.name(), "162");
        attributes.put(Node.PlmnSgsn2720Attribute.plmnName.name(), "");
        attributes.put(Node.PlmnSgsn2720Attribute.supportsLte.name(), true);

        final ManagedObject mo = liveBucket.getMibRootBuilder()
                .namespace(SGSN_MME_NAMESPACE)
                .type("PLMN")
                .version(SGSN_MME_VERSION).name("994..162").addAttributes(attributes)
                .parent(parentMo).create();
        logger.info("PLMN {} created with fdn: {}", mo, mo.getFdn());
    }

    public void deleteNode(final NodeData nodeData) {
        final DataBucket db = getLiveBucketSupressingMediation();
        final ManagedObject managedElementMo = db.findMoByFdn(MANAGED_ELEMENT_TYPE + "=" + nodeData.getNodeName());
        if (managedElementMo != null) {
            db.deletePo(managedElementMo);
        }
        final ManagedObject mo = db.findMoByFdn(NETWORK_ELEMENT_TYPE + "=" + nodeData.getNodeName());
        if (mo != null) {
            db.deletePo(mo);
        }
    }

    /**
     * Find network node by fdn.
     *
     * @param nodefdn
     *            the nodefdn
     * @return the managed object
     */
    public ManagedObject findNetworkNodeByFdn(final String nodefdn) {
        final DataBucket db = getLiveBucketSupressingMediation();
        return db.findMoByFdn(nodefdn);
    }

    public List<NodeData> createNodes(final String nodeDataFile) {
        try {
            final List<NodeData> nodesForTest = nodeDataReader.getNodeData(nodeDataFile);
            for (final NodeData nodeData : nodesForTest) {
                createNode(nodeData);
            }
            return nodesForTest;
        } catch (final Exception ex) {
            logger.error("Exception in createNodes {}", nodeDataFile, ex);
        }
        return null;
    }

    public void deleteAllSubscriptionsAndNodes() {
        try {
            deleteAllSubscription(10, 10);
            deleteAllNodes(10, 10);
        } catch (final Exception ex) {
            logger.error("Exception in deleteAllSubscriptionsAndNodes", ex);
        }
    }

    public List<NodeData> createNodes(final String nodeDataFile, final String nodeVersion) {
        final List<NodeData> nodesForTest = nodeDataReader.getNodeData(nodeDataFile);
        try {
            for (final NodeData nodeData : nodesForTest) {
                createNode(nodeData, nodeVersion);
            }
        } catch (final Exception ex) {
            logger.error("Exception in createNodes {} {}", nodeDataFile, nodeVersion, ex);
        }
        return nodesForTest;
    }

    public List<NodeData> createNodes(final String nodeDataFile, final int sheetIndex) {
        final List<NodeData> nodesForTest = nodeDataReader.getNodeData(nodeDataFile, sheetIndex);
        try {
            for (final NodeData nodeData : nodesForTest) {
                createNode(nodeData);
            }
        } catch (final Exception ex) {
            logger.error("Exception in createNodes {}", nodeDataFile, ex);
        }
        return nodesForTest;
    }

    public List<NodeData> createNodesWithPmFunctionOff(final String nodeDataFile) {
        final List<NodeData> nodesForTest = nodeDataReader.getNodeData(nodeDataFile);
        try {
            for (final NodeData nodeData : nodesForTest) {
                createNode(nodeData, false);
            }
        } catch (final Exception ex) {
            logger.error("Exception in createNodes {} pmEnabled : false", nodeDataFile, ex);
        }
        return nodesForTest;
    }

    public List<NodeData> getTestsNodesList(final String nodeDataFile) {
        return nodeDataReader.getNodeData(nodeDataFile);
    }

    public void deleteNodes(final String nodeDataFile) {
        final List<NodeData> nodesForTest = nodeDataReader.getNodeData(nodeDataFile);
        for (final NodeData nodeData : nodesForTest) {
            deleteNode(nodeData);
        }
        deleteAllEntityAddressInfo();
    }

    public void deleteOneNode(final String nodeName) {
        final DataBucket dataBucket = getLiveBucketSupressingMediation();
        final ManagedObject pmFunctionMo = dataBucket.findMoByFdn(NETWORK_ELEMENT_TYPE + "=" + nodeName +PM_FUNCTION_PREFIX);
        if (pmFunctionMo != null) {
            dataBucket.deletePo(pmFunctionMo);
        }
        final ManagedObject nodeMo = dataBucket.findMoByFdn(NETWORK_ELEMENT_TYPE + "=" + nodeName);
        if (nodeMo != null) {
            dataBucket.deletePo(nodeMo);
        } else {
            logger.error("Was not able to find node {}", NETWORK_ELEMENT_TYPE + "=" + nodeName);
        }
    }

    public void deleteAllEntityAddressInfo() {
        final QueryBuilder queryBuilder = dps.getQueryBuilder();
        final Query<TypeRestrictionBuilder> query = queryBuilder.createTypeQuery(EAI_NAMESPACE, EAI_TYPE);
        final Iterator<PersistenceObject> iterator = getLiveBucketSupressingMediation().getQueryExecutor().execute(query);
        while (iterator.hasNext()) {
            final PersistenceObject po = iterator.next();
            dps.getDataBucket("Live", BucketProperties.SUPPRESS_MEDIATION).deletePo(po);
        }
    }

    public void deleteAllSubscription() {
        logger.debug("Delete all subscription called");
        deleteAll(PMIC_NAMESPACE, PMIC_SUBSCRIPTION);
        logger.debug("Delete all subscription finished");
    }

    public void deleteAllNodes() {
        logger.debug("Delete all nodes called");
        deleteAll(PM_FUNCTION_NAMESPACE, PM_FUNCTION_MODEL_NAME);
        deleteAll(SGSN_MME_MANAGED_ELEMENT_NAMESPACE, MANAGED_ELEMENT_TYPE);
        deleteAll(NETWORK_ELEMENT_NAMESPACE, NETWORK_ELEMENT_TYPE);
        logger.debug("Delete all nodes finished");
    }

    public void deleteAll(final String nameSpace, final String type) {
        final DataBucket dataBucket = getLiveBucketSupressingMediation();
        final Query<TypeRestrictionBuilder> queryCriteria = dps.getQueryBuilder().createTypeQuery(nameSpace, type);
        final QueryExecutor queryExecutor = dataBucket.getQueryExecutor();
        final Iterator<ManagedObject> listOfPOs = queryExecutor.execute(queryCriteria);
        while (listOfPOs.hasNext()) {
            final ManagedObject managedObject = listOfPOs.next();
            logger.debug("deleting object {} ", managedObject.getFdn());
            try {
                dataBucket.deletePo(managedObject);
            } catch (final Exception e) {
                logger.error("Was unable to delete object {} from the DPS. Will attempt to clean up at start of next test. The cause was {}",
                        managedObject.getFdn(), e);
            }
        }
    }

    public void updateLastCollectionStartTimeHolder(final long time) {
        logger.debug("updating LastCollectionStartTimeHolder with time {} ", new Date(time));
        final DataBucket dataBucket = getLiveBucketSupressingMediation();
        final PersistenceObject lastCollectionStartTime = dataBucket.findMoByFdn("LastCollectionStartTimeHolder=LastCollectionStartTimeHolder");
        logger.debug("Found subscription manager {} ", lastCollectionStartTime);
        if (lastCollectionStartTime != null) {
            logger.debug("setting attribute lastCollectionStartTime of subscription manager");
            lastCollectionStartTime.setAttribute("lastCollectionStartTime", time);
        }
    }

    /**
     * The subscription deletion could fail because for some test case the pm-service is receiving the notification from DPS also after the test
     * execution, it means that we can have parallel access to the same MO. In order to reduce this issue the nodes should be deleted after the
     * subscription cleanup and a retry mechanism is needed.
     *
     * @param maxAttempsNum
     *            Number of attempt before throwing an exception
     * @param secToWaitBeforeRetry
     *            Seconds to wait before retrying.
     * @author ebialan
     */
    public void deleteAllSubscription(final int maxAttempsNum, final int secToWaitBeforeRetry) throws Exception {
        final CountDownLatch cl = new CountDownLatch(maxAttempsNum);
        while (cl.getCount() > 0) {
            try {
                logger.info("deleteAllSubscription is starting. Attempts available: {}", cl.getCount());
                cl.countDown();
                deleteAllSubscription();
                break;
            } catch (final Exception e) {
                logger.error("deleteAllSubscription is failed. Attempts available: {}", cl.getCount());
                e.printStackTrace();
                cl.await(secToWaitBeforeRetry, TimeUnit.SECONDS);
                if (cl.getCount() == 0) {
                    throw e;
                }
            }
        }
    }

    /**
     * The node deletion could fail because for some test case the pm-service is receiving the notification from DPS also after the test execution, it
     * means that we can have parallel access to the same MO.
     *
     * @param maxAttempsNum
     *            Number of attempt before throwing an exception
     * @param secToWaitBeforeRetry
     *            Seconds to wait before retrying.
     * @author ebialan
     */
    public void deleteAllNodes(final int maxAttempsNum, final int secToWaitBeforeRetry) throws Exception {
        final CountDownLatch cl = new CountDownLatch(maxAttempsNum);
        while (cl.getCount() > 0) {
            try {
                logger.info("deleteAllNodes is starting. Attempts available: {}", cl.getCount());
                cl.countDown();
                deleteAllNodes();
                break;
            } catch (final Exception e) {
                logger.error("deleteAllNodes is failed. Attempts available: {}", cl.getCount());
                e.printStackTrace();
                cl.await(secToWaitBeforeRetry, TimeUnit.SECONDS);
                if (cl.getCount() == 0) {
                    throw e;
                }
            }
        }
    }

    public void changePmFunctionValue(final String nodeFdn, final boolean pmEnabled) {
        final DataBucket dataBucket = getLiveBucketSupressingMediation();
        final String pmFunctionFdn = nodeFdn + "," + PM_FUNCTION_MO_NAME;
        final ManagedObject pmFunctionMO = dataBucket.findMoByFdn(pmFunctionFdn);
        pmFunctionMO.setAttribute(PM_ENABLED, pmEnabled);
    }

    public boolean getPmFunctionValue(final String nodeFdn) {
        final DataBucket dataBucket = getLiveBucketSupressingMediation();
        final String pmFunctionFdn = nodeFdn + "," + PM_FUNCTION_MO_NAME;
        final ManagedObject pmFunctionMO = dataBucket.findMoByFdn(pmFunctionFdn);
        return pmFunctionMO.getAttribute(PM_ENABLED);
    }

    public String getPmFunctionExtsourceDir(final String nodeFdn) {
        final DataBucket dataBucket = getLiveBucketSupressingMediation();
        final String pmFunctionFdn = nodeFdn + "," + PM_FUNCTION_MO_NAME;
        final ManagedObject pmFunctionMO = dataBucket.findMoByFdn(pmFunctionFdn);
        return pmFunctionMO.getAttribute(PM_SOURCE_DIR);
    }

    public void changePmFunctionExtsourceDir(final String nodeFdn, final String newSourceDir) {
        final DataBucket dataBucket = getLiveBucketSupressingMediation();
        final String pmFunctionFdn = nodeFdn + "," + PM_FUNCTION_MO_NAME;
        final ManagedObject pmFunctionMO = dataBucket.findMoByFdn(pmFunctionFdn);
        pmFunctionMO.setAttribute(PM_SOURCE_DIR, newSourceDir);
    }
}
