/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.dao;

import static com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian.PM_SERVICE_TEST;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.ejb.EJB;
import javax.inject.Inject;

import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.ericsson.oss.itpf.datalayer.dps.BucketProperties;
import com.ericsson.oss.itpf.datalayer.dps.DataBucket;
import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService;
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject;
import com.ericsson.oss.pmic.dao.NodeDao;
import com.ericsson.oss.pmic.dao.SubscriptionDao;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.CellTrafficSubscription;
import com.ericsson.oss.pmic.dto.subscription.ContinuousCellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.CtumSubscription;
import com.ericsson.oss.pmic.dto.subscription.EbmSubscription;
import com.ericsson.oss.pmic.dto.subscription.GpehSubscription;
import com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.UETraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.UetrSubscription;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;

@RunWith(Arquillian.class)
@Transactional
public class SubscriptionDaoPerformanceIt {

    private static final String MANAGED_ELEMENT = "ManagedElement";
    private static final String SGSN_MME_TOP = "SgsnMmeTop";
    private static ObjectMapper mapper = new ObjectMapper();
    private static MetricRegistry metricRegistry = new MetricRegistry();
    private static StringBuilder reportBuilder = new StringBuilder();
    @Inject
    private SubscriptionDao subscriptionDao;
    @Inject
    private NodeDao nodeDao;
    @EJB(lookup = DataPersistenceService.JNDI_LOOKUP_NAME)
    private DataPersistenceService dataPersistenceService;
    @Inject
    private Logger logger;
    @Inject
    private SubscriptionCreatorHelper subscriptionCreatorHelper;
    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;

    @BeforeClass
    public static void beforeClass() throws IOException {
        //pretify the output
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        //sort properties alphabetically
        mapper.configure(SerializationConfig.Feature.SORT_PROPERTIES_ALPHABETICALLY, true);
        //When reading JSON from file and deserializing to POJO, fail if there are unknown properties
        mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    @Test
    @InSequence(1)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void addNodes() throws DataAccessException {
        reportBuilder.append(System.lineSeparator())
                .append("========================================================================================").append(System.lineSeparator())
                .append("=============================PMIC SUBSCRIPTION DAO PERFORMANCE==========================").append(System.lineSeparator())
                .append("========================================================================================");
        nodeCreationHelperBean.deleteAll(Subscription.SUBSCRIPTION_MODEL_NAMESPACE, Subscription.SUBSCRIPTION_MODEL_TYPE);
        nodeCreationHelperBean.deleteAll(Node.NODE_MODEL_NAMESPACE, Node.NODE_MODEL_TYPE);
        nodeCreationHelperBean.deleteAll(SGSN_MME_TOP, MANAGED_ELEMENT);
        logger.info("Persisting nodes in preparation for test-execution.");
        final DataBucket liveBucket = dataPersistenceService.getDataBucket("Live", BucketProperties.SUPPRESS_MEDIATION);
        ;
        for (int i = 1; i <= 100; i++) {
            //############### ERBS NODES ##################//
            final Map<String, Object> nodeMoAttributes = new HashMap<>();
            nodeMoAttributes.put(Node.NetworkElement200Attribute.neType.name(), "ERBS");
            final ManagedObject nodeMO = liveBucket.getMibRootBuilder().namespace(Node.NODE_MODEL_NAMESPACE).version(Node.NODE_MODEL_VERSION)
                    .name("LTE01ERBS0000" + i).type(Node.NODE_MODEL_TYPE).addAttributes(nodeMoAttributes).parent(null).create();

            //############### ERBS PM FUNCTION ##################//
            final Map<String, Object> nodePmFunctionAttributes = new HashMap<>();
            nodePmFunctionAttributes.put(Node.PmFunction100Attribute.pmEnabled.name(), Boolean.TRUE);
            final ManagedObject pmFunctionERBS = liveBucket.getMibRootBuilder().namespace(Node.PM_FUNCTION_NAMESPACE)
                    .version(Node.PM_FUNCTION_MODEL_VERSION).type(Node.PM_FUNCTION_TYPE).addAttributes(nodePmFunctionAttributes).parent(nodeMO)
                    .create();

            //############### SGSN NODES ##################//
            final Map<String, Object> sgsnNodeMoAttributes = new HashMap<>();
            sgsnNodeMoAttributes.put(Node.NetworkElement200Attribute.neType.name(), "SGSN-MME");
            sgsnNodeMoAttributes.put(Node.NetworkElement200Attribute.ossModelIdentity.name(), "4322-940-032");
            sgsnNodeMoAttributes.put(Node.NetworkElement200Attribute.ossPrefix.name(), "ENM");
            sgsnNodeMoAttributes.put(Node.NetworkElement200Attribute.platformType.name(), "CPP");
            sgsnNodeMoAttributes.put(Node.NetworkElement200Attribute.networkElementId.name(), "CORE-SGSN_123" + i);
            final ManagedObject sgsnNodeMO = liveBucket.getMibRootBuilder().namespace(Node.NODE_MODEL_NAMESPACE).version(Node.NODE_MODEL_VERSION)
                    .name("CORE-SGSN_123" + i).type(Node.NODE_MODEL_TYPE).addAttributes(sgsnNodeMoAttributes).parent(null).create();

            //############### SGSN MANAGED ELEMENT ##################//
            final Map<String, Object> sgsnManagedElementAttributes = new HashMap<>();
            sgsnManagedElementAttributes.put("neType", "SGSN-MME");
            sgsnManagedElementAttributes.put("managedElementId", "SGSN-16A-CP02-V304" + i);
            sgsnManagedElementAttributes.put("platformType", "CPP");
            final ManagedObject managedElement = liveBucket.getMibRootBuilder().namespace(SGSN_MME_TOP).version("1.4.1").type(MANAGED_ELEMENT)
                    .addAttributes(sgsnManagedElementAttributes).parent(null).create();

            //############### SGSN SGSN ##################//
            final Map<String, Object> sgsnMmeAttributes = new HashMap<>();
            sgsnMmeAttributes.put("sgsnMmeId", "1");
            final ManagedObject sgsnMmme = liveBucket.getMibRootBuilder().namespace("Sgsn_Mme").version("2.122.0").type("SgsnMme")
                    .addAttributes(sgsnMmeAttributes).parent(managedElement).create();

            //############### SGSN PLMN ##################//
            final Map<String, Object> plmnAttributes = new HashMap<>();
            plmnAttributes.put("mobileCountryCode", "111");
            plmnAttributes.put("mobileNetworkCode", "163");
            plmnAttributes.put("plmnName", String.valueOf(i));
            plmnAttributes.put("supportsLte", Boolean.TRUE);
            final ManagedObject plmn = liveBucket.getMibRootBuilder().namespace("Sgsn_Mme").version("2.122.0").type("PLMN")
                    .addAttributes(plmnAttributes).parent(sgsnMmme).create();

            //############### SGSN PM FUNCTION ##################//
            final Map<String, Object> sgsnNodePmFunctionAttributes = new HashMap<>();
            sgsnNodePmFunctionAttributes.put(Node.PmFunction100Attribute.pmEnabled.name(), Boolean.TRUE);
            final ManagedObject pmFunctionSgsn = liveBucket.getMibRootBuilder().namespace(Node.PM_FUNCTION_NAMESPACE)
                    .version(Node.PM_FUNCTION_MODEL_VERSION).type(Node.PM_FUNCTION_TYPE).addAttributes(sgsnNodePmFunctionAttributes)
                    .parent(sgsnNodeMO).create();
        }
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void saveStatisticalSubscription() throws IOException, DataAccessException {
        final StatisticalSubscription statisticalSubscription = mapper.readValue(
                this.getClass().getClassLoader().getResourceAsStream("/data/subscription/StatisticalSubscription.json"),
                StatisticalSubscription.class);
        statisticalSubscription.setNodes(nodeDao.findAllByNeType("ERBS"));
        final Timer timer = metricRegistry.timer("Stats");

        for (int i = 0; i < 10; i++) {
            statisticalSubscription.setId(0L);

            final Timer.Context context = timer.time();
            subscriptionDao.saveOrUpdate(statisticalSubscription);
            context.stop();
            //cleanup
            subscriptionDao.delete(statisticalSubscription);
        }

        reportBuilder.append(System.lineSeparator());
        reportBuilder.append(String.format("Save StatisticalSubscription took: %s millis",
                (metricRegistry.timer("Stats").getSnapshot().get75thPercentile() / (1000 * 1000))));
        Assert.assertTrue(statisticalSubscription.getId() != 0L);
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void saveCelltraceSubscription() throws IOException, DataAccessException {
        final CellTraceSubscription cellTraceSubscription = mapper.readValue(
                this.getClass().getClassLoader().getResourceAsStream("/data/subscription/CellTraceSubscription.json"), CellTraceSubscription.class);
        cellTraceSubscription.setNodes(nodeDao.findAllByNeType("ERBS"));
        final Timer timer = metricRegistry.timer("CellTraceSubscription");

        for (int i = 0; i < 10; i++) {
            cellTraceSubscription.setId(0L);

            final Timer.Context context = timer.time();
            subscriptionDao.saveOrUpdate(cellTraceSubscription);
            context.stop();
            //cleanup
            subscriptionDao.delete(cellTraceSubscription);
        }

        reportBuilder.append(System.lineSeparator());
        reportBuilder.append(String.format("Save CellTraceSubscription took: %s millis",
                (metricRegistry.timer("CellTraceSubscription").getSnapshot().get75thPercentile() / (1000 * 1000))));
        Assert.assertTrue(cellTraceSubscription.getId() != 0L);
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void saveMoInstanceSubscription() throws IOException, DataAccessException {
        final MoinstanceSubscription moinstanceSubscription = mapper.readValue(
                this.getClass().getClassLoader().getResourceAsStream("/data/subscription/MoinstanceSubscription.json"), MoinstanceSubscription.class);
        moinstanceSubscription.setNodes(nodeDao.findAllByNeType("ERBS"));
        final Timer timer = metricRegistry.timer("MoinstanceSubscription");

        for (int i = 0; i < 10; i++) {
            moinstanceSubscription.setId(0L);

            final Timer.Context context = timer.time();
            subscriptionDao.saveOrUpdate(moinstanceSubscription);
            context.stop();
            //cleanup
            subscriptionDao.delete(moinstanceSubscription);
        }

        reportBuilder.append(System.lineSeparator());
        reportBuilder.append(String.format("Save MoinstanceSubscription took: %s millis",
                (metricRegistry.timer("MoinstanceSubscription").getSnapshot().get75thPercentile() / (1000 * 1000))));
        Assert.assertTrue(moinstanceSubscription.getId() != 0L);
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void saveEbmSubscription() throws IOException, DataAccessException {
        final EbmSubscription ebmSubscription = mapper
                .readValue(this.getClass().getClassLoader().getResourceAsStream("/data/subscription/EbmSubscription.json"), EbmSubscription.class);
        ebmSubscription.setNodes(nodeDao.findAllByNeType("ERBS"));
        final Timer timer = metricRegistry.timer("EbmSubscription");

        for (int i = 0; i < 10; i++) {
            ebmSubscription.setId(0L);

            final Timer.Context context = timer.time();
            subscriptionDao.saveOrUpdate(ebmSubscription);
            context.stop();
            //cleanup
            subscriptionDao.delete(ebmSubscription);
        }

        reportBuilder.append(System.lineSeparator());
        reportBuilder.append(String.format("Save EbmSubscription took: %s millis",
                (metricRegistry.timer("EbmSubscription").getSnapshot().get75thPercentile() / (1000 * 1000))));
        Assert.assertTrue(ebmSubscription.getId() != 0L);
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void saveGpehSubscription() throws IOException, DataAccessException {
        final GpehSubscription gpehSubscription = mapper
                .readValue(this.getClass().getClassLoader().getResourceAsStream("/data/subscription/GpehSubscription.json"), GpehSubscription.class);
        gpehSubscription.setNodes(nodeDao.findAllByNeType("ERBS"));
        final Timer timer = metricRegistry.timer("GpehSubscription");

        for (int i = 0; i < 10; i++) {
            gpehSubscription.setId(0L);

            final Timer.Context context = timer.time();
            subscriptionDao.saveOrUpdate(gpehSubscription);
            context.stop();
            //cleanup
            subscriptionDao.delete(gpehSubscription);
        }

        reportBuilder.append(System.lineSeparator());
        reportBuilder.append(String.format("Save GpehSubscription took: %s millis",
                (metricRegistry.timer("GpehSubscription").getSnapshot().get75thPercentile() / (1000 * 1000))));
        Assert.assertTrue(gpehSubscription.getId() != 0L);
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void saveUetrSubscription() throws IOException, DataAccessException {
        final UetrSubscription uetrSubscription = mapper
                .readValue(this.getClass().getClassLoader().getResourceAsStream("/data/subscription/UetrSubscription.json"), UetrSubscription.class);
        uetrSubscription.setNodes(nodeDao.findAllByNeType("ERBS"));
        final Timer timer = metricRegistry.timer("UetrSubscription");

        for (int i = 0; i < 10; i++) {
            uetrSubscription.setId(0L);

            final Timer.Context context = timer.time();
            subscriptionDao.saveOrUpdate(uetrSubscription);
            context.stop();
            //cleanup
            subscriptionDao.delete(uetrSubscription);
        }

        reportBuilder.append(System.lineSeparator());
        reportBuilder.append(String.format("Save UetrSubscription took: %s millis",
                (metricRegistry.timer("UetrSubscription").getSnapshot().get75thPercentile() / (1000 * 1000))));
        Assert.assertTrue(uetrSubscription.getId() != 0L);
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void saveUetraceSubscription() throws IOException, DataAccessException {
        final UETraceSubscription ueTraceSubscription = mapper.readValue(
                this.getClass().getClassLoader().getResourceAsStream("/data/subscription/UETraceSubscription.json"), UETraceSubscription.class);
        final Timer timer = metricRegistry.timer("UETraceSubscription");

        for (int i = 0; i < 10; i++) {
            ueTraceSubscription.setId(0L);

            final Timer.Context context = timer.time();
            ueTraceSubscription.setTraceReference(subscriptionDao.generateUniqueTraceReference(ueTraceSubscription.getOutputMode()));
            subscriptionDao.saveOrUpdate(ueTraceSubscription);
            context.stop();
            //cleanup
            subscriptionDao.delete(ueTraceSubscription);
        }

        reportBuilder.append(System.lineSeparator());
        reportBuilder.append(String.format("Save UETraceSubscription took: %s millis",
                (metricRegistry.timer("UETraceSubscription").getSnapshot().get75thPercentile() / (1000 * 1000))));
        Assert.assertTrue(ueTraceSubscription.getId() != 0L);
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void saveCctrSubscription() throws IOException, DataAccessException {
        final ContinuousCellTraceSubscription continuousCellTraceSubscription = mapper.readValue(
                this.getClass().getClassLoader().getResourceAsStream("/data/subscription/ContinuousCellTraceSubscription.json"),
                ContinuousCellTraceSubscription.class);
        final Timer timer = metricRegistry.timer("ContinuousCellTraceSubscription");

        for (int i = 0; i < 10; i++) {
            continuousCellTraceSubscription.setId(0L);

            final Timer.Context context = timer.time();
            final List<Node> sgsnMMENodes = nodeDao.findAllByNeTypeAndPmFunction(Arrays.asList("SGSN-MME", "ERBS"), true);
            continuousCellTraceSubscription.setNodes(sgsnMMENodes);
            subscriptionDao.saveOrUpdate(continuousCellTraceSubscription);
            context.stop();
            //cleanup
            subscriptionDao.delete(continuousCellTraceSubscription);
        }

        reportBuilder.append(System.lineSeparator());
        reportBuilder.append(String.format("Save ContinuousCellTraceSubscription took: %s millis",
                (metricRegistry.timer("ContinuousCellTraceSubscription").getSnapshot().get75thPercentile() / (1000 * 1000))));
        Assert.assertTrue(continuousCellTraceSubscription.getId() != 0L);
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void saveCtumSubscription() throws IOException, DataAccessException {
        final CtumSubscription ctumSubscription = mapper
                .readValue(this.getClass().getClassLoader().getResourceAsStream("/data/subscription/CtumSubscription.json"), CtumSubscription.class);
        final Timer timer = metricRegistry.timer("CtumSubscription");

        for (int i = 0; i < 10; i++) {
            ctumSubscription.setId(0L);

            final Timer.Context context = timer.time();
            final List<Node> sgsnMMENodes = nodeDao.findAllByNeTypeAndPmFunction(Collections.singletonList("SGSN-MME"), true);

            ctumSubscription.setNodes(sgsnMMENodes);
            subscriptionDao.saveOrUpdate(ctumSubscription);
            context.stop();
            //cleanup
            subscriptionDao.delete(ctumSubscription);
        }

        reportBuilder.append(System.lineSeparator());
        reportBuilder.append(String.format("Save CtumSubscription took: %s millis",
                (metricRegistry.timer("CtumSubscription").getSnapshot().get75thPercentile() / (1000 * 1000))));
        Assert.assertTrue(ctumSubscription.getId() != 0L);
    }

    @SuppressWarnings("unchecked")
    @Test
    @OperateOnDeployment(PM_SERVICE_TEST)
    @InSequence(5)
    public void test_performance_of_find_allSubscriptions() throws DataAccessException {
        persistOneOfEachSubscriptionInDatabase();

        List<Long> record = new ArrayList<>();
        final int tries = 10;
        for (int i = 0; i < tries; i++) {
            final Long start = System.nanoTime();
            subscriptionDao.findAll(false);
            record.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        }
        Collections.sort(record);
        reportBuilder.append(System.lineSeparator());
        reportBuilder.append(String.format("GET ALL SUBSCRIPTIONS WITH ALL ATTRIBUTES TIME: MIN:%s MID:%s MAX:%s", record.get(0),
                record.get(tries / 2), record.get(tries - 1)));

        record = new ArrayList<>();
        for (int i = 0; i < tries; i++) {
            final Long start = System.nanoTime();
            final List<Enum> baseAttributes = new ArrayList<>();
            baseAttributes.addAll(Arrays.asList(Subscription.Subscription220Attribute.values()));
            //remove CDT as projection query doesn't work on complex data types
            baseAttributes.remove(Subscription.Subscription220Attribute.scheduleInfo);
            final Map<SubscriptionType, Iterable<Enum>> extraAttributesPerSubscriptionType = new HashMap<>();
            extraAttributesPerSubscriptionType.put(SubscriptionType.EBM, Arrays.<Enum>asList(EbmSubscription.EbmSubscription120Attribute.ebsEnabled,
                    EbmSubscription.EbmSubscription120Attribute.compressionEnabled));
            subscriptionDao.findAll(baseAttributes, extraAttributesPerSubscriptionType);
            record.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        }
        Collections.sort(record);
        reportBuilder.append(System.lineSeparator());
        reportBuilder.append(String.format("GET ALL SUBSCRIPTIONS WITH SELECT ATTRIBUTES TIME: MIN:%s MID:%s MAX:%s", record.get(0),
                record.get(tries / 2), record.get(tries - 1)));
    }

    @Test
    @InSequence(100)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void cleanUpNodes() throws DataAccessException {

        reportBuilder.append(System.lineSeparator())
                .append("========================================================================================").append(System.lineSeparator())
                .append("=============================PMIC SUBSCRIPTION DAO PERFORMANCE==========================").append(System.lineSeparator())
                .append("========================================================================================");

        logger.info(reportBuilder.toString());
        nodeCreationHelperBean.deleteAll(Subscription.SUBSCRIPTION_MODEL_NAMESPACE, Subscription.SUBSCRIPTION_MODEL_TYPE);
        nodeCreationHelperBean.deleteAll(Node.NODE_MODEL_NAMESPACE, Node.NODE_MODEL_TYPE);
        nodeCreationHelperBean.deleteAll(SGSN_MME_TOP, MANAGED_ELEMENT);
    }

    private void persistOneOfEachSubscriptionInDatabase() throws DataAccessException {
        //Save subscriptions in DB
        final StatisticalSubscription subscription = new StatisticalSubscription();
        subscriptionCreatorHelper.populateStatisticalSubscriptionDTOWithManyCounters(subscription);

        final List<Node> nodes = nodeDao.findAll();
        subscription.setNodes(nodes);

        subscriptionDao.saveOrUpdate(subscription);

        final CellTraceSubscription cellTraceSubscription = new CellTraceSubscription();
        subscriptionCreatorHelper.populateCellTraceSubscriptionDTOWithManyEventsAndEbsCounters(cellTraceSubscription);
        cellTraceSubscription.setNodes(nodes);

        subscriptionDao.saveOrUpdate(cellTraceSubscription);

        final CellTrafficSubscription cellTrafficSubscription = new CellTrafficSubscription();
        subscriptionCreatorHelper.populateCellTrafficSubscriptionDTOWithManyEvents(cellTrafficSubscription);
        cellTrafficSubscription.setNodes(nodes);

        subscriptionDao.saveOrUpdate(cellTrafficSubscription);

        final EbmSubscription ebmSubscription = new EbmSubscription();
        subscriptionCreatorHelper.populateEbmSubscriptionDTOWithManyEbsCountersAndEvents(ebmSubscription);
        ebmSubscription.setNodes(nodes);

        subscriptionDao.saveOrUpdate(ebmSubscription);

        final CtumSubscription ctumSubscription = new CtumSubscription();
        subscriptionCreatorHelper.populateCtumSubscriptionDTO(ctumSubscription);

        subscriptionDao.saveOrUpdate(ctumSubscription);

        final UETraceSubscription ueTraceSubscription = new UETraceSubscription();
        subscriptionCreatorHelper.populateUeTraceSubscriptionDTO(ueTraceSubscription);

        subscriptionDao.saveOrUpdate(ueTraceSubscription);

        final UetrSubscription uetrSubscription = new UetrSubscription();
        subscriptionCreatorHelper.populateUetrSubscriptionDTOWithManyEvents(uetrSubscription);
        uetrSubscription.setNodes(nodes);

        subscriptionDao.saveOrUpdate(uetrSubscription);

        final MoinstanceSubscription moinstanceSubscription = new MoinstanceSubscription();
        subscriptionCreatorHelper.populateMoInstanceSubscriptionDTOWithManyCountersAndMoInstances(moinstanceSubscription);
        moinstanceSubscription.setNodes(nodes);

        subscriptionDao.saveOrUpdate(moinstanceSubscription);

        final GpehSubscription gpehSubscription = new GpehSubscription();
        subscriptionCreatorHelper.populateGpehSubscriptionDTOWithManyEventsAndCells(gpehSubscription);
        gpehSubscription.setNodes(nodes);

        subscriptionDao.saveOrUpdate(gpehSubscription);
        //All subscriptions are saved in DB
    }
}
