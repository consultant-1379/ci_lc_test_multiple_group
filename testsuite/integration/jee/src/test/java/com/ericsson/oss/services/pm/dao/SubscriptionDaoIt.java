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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian.PM_SERVICE_TEST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ejb.EJB;
import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.datalayer.dps.BucketProperties;
import com.ericsson.oss.itpf.datalayer.dps.DataBucket;
import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService;
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject;
import com.ericsson.oss.pmic.dao.NodeDao;
import com.ericsson.oss.pmic.dao.SubscriptionDao;
import com.ericsson.oss.pmic.dao.versant.mapper.EntitiesDiff;
import com.ericsson.oss.pmic.dao.versant.mapper.qualifier.SubscriptionMapperQualifier;
import com.ericsson.oss.pmic.dao.versant.mapper.subscription.SubscriptionMapper;
import com.ericsson.oss.pmic.dto.Entity;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription.CellTraceSubscription210Attribute;
import com.ericsson.oss.pmic.dto.subscription.CellTrafficSubscription;
import com.ericsson.oss.pmic.dto.subscription.ContinuousCellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.CtumSubscription;
import com.ericsson.oss.pmic.dto.subscription.EbmSubscription;
import com.ericsson.oss.pmic.dto.subscription.GpehSubscription;
import com.ericsson.oss.pmic.dto.subscription.MoinstanceSubscription;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.UETraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.UetrSubscription;
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.initiation.node.data.NodeCreationHelperBean;

@RunWith(Arquillian.class)
@Transactional
public class SubscriptionDaoIt {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionDaoIt.class);

    @Inject
    private SubscriptionDao subscriptionDao;

    @Inject
    private NodeDao nodeDao;

    @EJB(lookup = DataPersistenceService.JNDI_LOOKUP_NAME)
    private DataPersistenceService dataPersistenceService;

    @Inject
    @SubscriptionMapperQualifier
    private SubscriptionMapper subscriptionMapper;

    @Inject
    private SubscriptionCreatorHelper subscriptionCreatorHelper;

    @Inject
    private NodeCreationHelperBean nodeCreationHelperBean;

    //    @EJB
    //    private SubscriptionDaoRollbackVerify subscriptionDaoRollbackVerify;

    @BeforeClass
    public static void startTestClass() {
        logger.info(System.lineSeparator() + "========================================================================================"
                + System.lineSeparator() + "====================================SUBSCRIPTION DAO IT================================="
                + System.lineSeparator() + "========================================================================================");
    }

    @AfterClass
    public static void endTestClass() {
        logger.info(System.lineSeparator() + "========================================================================================"
                + System.lineSeparator() + "====================================SUBSCRIPTION DAO IT================================="
                + System.lineSeparator() + "========================================================================================");
    }

    @Test
    @OperateOnDeployment(PM_SERVICE_TEST)
    @InSequence(1)
    public void cleanSubscriptionsAndCreateNodes() throws DataAccessException {
        nodeCreationHelperBean.deleteAll(Subscription.SUBSCRIPTION_MODEL_NAMESPACE, Subscription.SUBSCRIPTION_MODEL_TYPE);
        nodeCreationHelperBean.deleteAll(Node.NODE_MODEL_NAMESPACE, Node.NODE_MODEL_TYPE);

        //createNode
        DataBucket liveBucket = dataPersistenceService.getDataBucket("Live", BucketProperties.SUPPRESS_MEDIATION);
        final Map<String, Object> nodeMoAttributes = new HashMap<>();
        nodeMoAttributes.put(Node.NetworkElement200Attribute.neType.name(), "ERBS");
        ManagedObject nodeMO = liveBucket.getMibRootBuilder().namespace(Node.NODE_MODEL_NAMESPACE).version(Node.NODE_MODEL_VERSION)
                .name("LTE01ERBS00001").type(Node.NODE_MODEL_TYPE).addAttributes(nodeMoAttributes).parent(null).create();

        //createPmFunction
        final Map<String, Object> nodePmFunctionAttributes = new HashMap<>();
        nodePmFunctionAttributes.put(Node.PmFunction100Attribute.pmEnabled.name(), Boolean.TRUE);
        liveBucket.getMibRootBuilder().namespace(Node.PM_FUNCTION_NAMESPACE).version(Node.PM_FUNCTION_MODEL_VERSION).type(Node.PM_FUNCTION_TYPE)
                .addAttributes(nodePmFunctionAttributes).parent(nodeMO).create();
    }

    @Before
    public void beforeEachMethod() {
        nodeCreationHelperBean.deleteAll(Subscription.SUBSCRIPTION_MODEL_NAMESPACE, Subscription.SUBSCRIPTION_MODEL_TYPE);
    }

    @Test
    @OperateOnDeployment(PM_SERVICE_TEST)
    @InSequence(5)
    public void findAllWithSpecifiedAttributes() throws DataAccessException {
        CellTraceSubscription cellTraceSubscription = new CellTraceSubscription();
        subscriptionCreatorHelper.populateCellTraceSubscriptionDTO(cellTraceSubscription);
        cellTraceSubscription.setCellTraceCategory(CellTraceCategory.CELLTRACE_AND_EBSL_FILE);

        UETraceSubscription ueTraceSubscription = new UETraceSubscription();
        subscriptionCreatorHelper.populateUeTraceSubscriptionDTO(ueTraceSubscription);
        ueTraceSubscription.setTraceReference("11__11");

        subscriptionDao.saveOrUpdate(Arrays.asList(cellTraceSubscription, ueTraceSubscription));

        final List<Enum> baseAttributes = new ArrayList<>();
        baseAttributes.addAll(Arrays.asList(Subscription.Subscription220Attribute.values()));
        //remove CDT as projection query doesn't work on complex data types
        baseAttributes.remove(Subscription.Subscription220Attribute.scheduleInfo);
        final Map<SubscriptionType, Iterable<Enum>> extraAttributesPerSubscriptionType = new HashMap<>();
        extraAttributesPerSubscriptionType.put(SubscriptionType.CELLTRACE,
                Arrays.asList(new Enum[]{CellTraceSubscription210Attribute.cellTraceCategory}));
        extraAttributesPerSubscriptionType.put(SubscriptionType.UETRACE,
                Collections.<Enum>singletonList(UETraceSubscription.UeTraceSubscription220Attribute.traceReference));
        List<Subscription> result = subscriptionDao.findAll(baseAttributes, extraAttributesPerSubscriptionType);

        for (Subscription subscription : result) {
            if (subscription instanceof CellTraceSubscription) {
                assertEquals(CellTraceCategory.CELLTRACE_AND_EBSL_FILE, ((CellTraceSubscription) subscription).getCellTraceCategory());
            } else if (subscription instanceof UETraceSubscription) {
                assertTrue("11__11".equals(((UETraceSubscription) subscription).getTraceReference()));
            } else {
                fail();
            }
        }
    }

    @Test
    @OperateOnDeployment(PM_SERVICE_TEST)
    @InSequence(5)
    public void findAllWithSubscriptionTypeAndMatchingAttributes_test() throws DataAccessException {
        CellTraceSubscription cellTraceSubscription = new CellTraceSubscription();
        subscriptionCreatorHelper.populateCellTraceSubscriptionDTO(cellTraceSubscription);
        cellTraceSubscription.setCellTraceCategory(CellTraceCategory.CELLTRACE_AND_EBSL_STREAM);

        CellTraceSubscription cellTraceSubscriptionWithEbsDisabled = new CellTraceSubscription();
        subscriptionCreatorHelper.populateCellTraceSubscriptionDTO(cellTraceSubscriptionWithEbsDisabled);
        cellTraceSubscriptionWithEbsDisabled.setName("ToDelete");

        EbmSubscription ebmSubscription = new EbmSubscription();
        subscriptionCreatorHelper.populateEbmSubscriptionDTO(ebmSubscription);

        ContinuousCellTraceSubscription continuousCellTraceSubscription = new ContinuousCellTraceSubscription();
        subscriptionCreatorHelper.populateCellTraceSubscriptionDTO(continuousCellTraceSubscription);
        cellTraceSubscription.setCellTraceCategory(CellTraceCategory.CELLTRACE_AND_EBSL_STREAM);

        StatisticalSubscription statisticalSubscription = new StatisticalSubscription();
        subscriptionCreatorHelper.populateStatisticalSubscriptionDTO(statisticalSubscription);

        final List<Subscription> subscriptions = new ArrayList<>();
        subscriptions.add(cellTraceSubscription);
        subscriptions.add(ebmSubscription);
        subscriptions.add(continuousCellTraceSubscription);
        subscriptions.add(statisticalSubscription);
        subscriptions.add(cellTraceSubscriptionWithEbsDisabled);

        subscriptionDao.saveOrUpdate(subscriptions);
        final List<Subscription> expectedResult = subscriptionDao
                .findAllById(Arrays.asList(cellTraceSubscription.getId(), continuousCellTraceSubscription.getId()));

        Map<String, List<Object>> additionalAttributes = new HashMap<>();
        additionalAttributes.put(CellTraceSubscription210Attribute.cellTraceCategory.name(),
                Collections.singletonList(CellTraceCategory.CELLTRACE_AND_EBSL_STREAM));
        List<Subscription> result = subscriptionDao.findAllWithSubscriptionTypeAndMatchingAttributes(SubscriptionType.CELLTRACE, additionalAttributes,
                true);

        assertTrue(2 == result.size());
        assertArrayEquals(expectedResult.toArray(), result.toArray());
    }

    @SuppressWarnings("unchecked")
    @Test
    @OperateOnDeployment(PM_SERVICE_TEST)
    @InSequence(5)
    public void saveOrUpdate_Save_statistical_sub() throws DataAccessException {
        final StatisticalSubscription subscription = new StatisticalSubscription();
        subscriptionCreatorHelper.populateStatisticalSubscriptionDTO(subscription);

        subscription.setNodes(nodeDao.findAll());
        subscriptionDao.saveOrUpdate(subscription);

        DataBucket liveBucket = dataPersistenceService.getDataBucket("Live", BucketProperties.SUPPRESS_MEDIATION);
        ManagedObject subMO = (ManagedObject) liveBucket.findPoById(subscription.getId());
        final Entity subEntity = subscriptionCreatorHelper.toEntity(subMO.getType(), subMO.getPoId(), subMO.getFdn(), subMO.getAllAttributes(),
                subMO.getAssociations());
        final EntitiesDiff entitiesDiff = subscriptionMapper.doDiff(subscriptionMapper.toEntity(subscription), subEntity);
        assertSame(0, entitiesDiff.getAddedAssociations().size());
        assertSame(0, entitiesDiff.getRemovedAssociations().size());
        assertSame(0, entitiesDiff.getChangedAttributes().size());
        assertSame(1, subMO.getAssociations(ResourceSubscription.ResourceSubscription120Attribute.nodes.name()).size());
    }

    @Test
    @OperateOnDeployment(PM_SERVICE_TEST)
    @InSequence(5)
    public void saveOrUpdate_Celltrace_sub() throws DataAccessException {
        final CellTraceSubscription subscription = new CellTraceSubscription();
        subscriptionCreatorHelper.populateCellTraceSubscriptionDTO(subscription);

        subscription.setNodes(nodeDao.findAll());
        subscriptionDao.saveOrUpdate(subscription);

        DataBucket liveBucket = dataPersistenceService.getDataBucket("Live", BucketProperties.SUPPRESS_MEDIATION);
        ManagedObject subMO = (ManagedObject) liveBucket.findPoById(subscription.getId());
        final Entity subEntity = subscriptionCreatorHelper.toEntity(subMO.getType(), subMO.getPoId(), subMO.getFdn(), subMO.getAllAttributes(),
                subMO.getAssociations());
        final EntitiesDiff entitiesDiff = subscriptionMapper.doDiff(subscriptionMapper.toEntity(subscription), subEntity);
        assertSame(0, entitiesDiff.getAddedAssociations().size());
        assertSame(0, entitiesDiff.getRemovedAssociations().size());
        assertSame(0, entitiesDiff.getChangedAttributes().size());
    }

    @Test
    @OperateOnDeployment(PM_SERVICE_TEST)
    @InSequence(5)
    public void getCriteriaBasedSubscriptionCount() throws DataAccessException {
        final CellTraceSubscription subscription = new CellTraceSubscription();
        subscriptionCreatorHelper.populateCellTraceSubscriptionDTO(subscription);
        subscription.setCbs(true);

        subscriptionDao.saveOrUpdate(subscription);

        assertSame(1, subscriptionDao.countCriteriaBasedSubscriptions());
    }

    @Test
    @OperateOnDeployment(PM_SERVICE_TEST)
    @InSequence(5)
    public void saveOrUpdate_CellTraffic_sub() throws DataAccessException {
        final CellTrafficSubscription subscription = new CellTrafficSubscription();
        subscriptionCreatorHelper.populateCellTrafficSubscriptionDTO(subscription);

        subscription.setNodes(nodeDao.findAll());
        subscriptionDao.saveOrUpdate(subscription);
        DataBucket liveBucket = dataPersistenceService.getDataBucket("Live", BucketProperties.SUPPRESS_MEDIATION);

        ManagedObject subMO = (ManagedObject) liveBucket.findPoById(subscription.getId());
        final Entity subEntity = subscriptionCreatorHelper.toEntity(subMO.getType(), subMO.getPoId(), subMO.getFdn(), subMO.getAllAttributes(),
                subMO.getAssociations());
        final EntitiesDiff entitiesDiff = subscriptionMapper.doDiff(subscriptionMapper.toEntity(subscription), subEntity);
        assertSame(0, entitiesDiff.getAddedAssociations().size());
        assertSame(0, entitiesDiff.getRemovedAssociations().size());
        assertSame(0, entitiesDiff.getChangedAttributes().size());
    }

    @Test
    @OperateOnDeployment(PM_SERVICE_TEST)
    @InSequence(5)
    public void saveOrUpdate_Ebm_sub() throws DataAccessException {
        final EbmSubscription subscription = new EbmSubscription();
        subscriptionCreatorHelper.populateEbmSubscriptionDTO(subscription);

        subscription.setNodes(nodeDao.findAll());
        subscriptionDao.saveOrUpdate(subscription);
        DataBucket liveBucket = dataPersistenceService.getDataBucket("Live", BucketProperties.SUPPRESS_MEDIATION);

        ManagedObject subMO = (ManagedObject) liveBucket.findPoById(subscription.getId());
        final Entity subEntity = subscriptionCreatorHelper.toEntity(subMO.getType(), subMO.getPoId(), subMO.getFdn(), subMO.getAllAttributes(),
                subMO.getAssociations());
        final EntitiesDiff entitiesDiff = subscriptionMapper.doDiff(subscriptionMapper.toEntity(subscription), subEntity);
        assertSame(0, entitiesDiff.getAddedAssociations().size());
        assertSame(0, entitiesDiff.getRemovedAssociations().size());
        assertSame(0, entitiesDiff.getChangedAttributes().size());
    }

    @Test
    @OperateOnDeployment(PM_SERVICE_TEST)
    @InSequence(5)
    public void saveOrUpdate_Ctum_sub() throws DataAccessException {
        final CtumSubscription subscription = new CtumSubscription();
        subscriptionCreatorHelper.populateCtumSubscriptionDTO(subscription);

        subscription.setNodes(nodeDao.findAll());
        subscriptionDao.saveOrUpdate(subscription);
        DataBucket liveBucket = dataPersistenceService.getDataBucket("Live", BucketProperties.SUPPRESS_MEDIATION);

        ManagedObject subMO = (ManagedObject) liveBucket.findPoById(subscription.getId());
        final Entity subEntity = subscriptionCreatorHelper.toEntity(subMO.getType(), subMO.getPoId(), subMO.getFdn(), subMO.getAllAttributes(),
                subMO.getAssociations());
        final EntitiesDiff entitiesDiff = subscriptionMapper.doDiff(subscriptionMapper.toEntity(subscription), subEntity);
        assertSame(0, entitiesDiff.getAddedAssociations().size());
        assertSame(0, entitiesDiff.getRemovedAssociations().size());
        assertSame(0, entitiesDiff.getChangedAttributes().size());
    }

    @Test
    @OperateOnDeployment(PM_SERVICE_TEST)
    @InSequence(5)
    public void saveOrUpdate_Uetrace_sub() throws DataAccessException {
        final UETraceSubscription subscription = new UETraceSubscription();
        subscriptionCreatorHelper.populateUeTraceSubscriptionDTO(subscription);

        subscriptionDao.saveOrUpdate(subscription);
        DataBucket liveBucket = dataPersistenceService.getDataBucket("Live", BucketProperties.SUPPRESS_MEDIATION);

        ManagedObject subMO = (ManagedObject) liveBucket.findPoById(subscription.getId());
        final Entity subEntity = subscriptionCreatorHelper.toEntity(subMO.getType(), subMO.getPoId(), subMO.getFdn(), subMO.getAllAttributes(),
                subMO.getAssociations());
        final EntitiesDiff entitiesDiff = subscriptionMapper.doDiff(subscriptionMapper.toEntity(subscription), subEntity);
        assertSame(0, entitiesDiff.getAddedAssociations().size());
        assertSame(0, entitiesDiff.getRemovedAssociations().size());
        assertSame(0, entitiesDiff.getChangedAttributes().size());
    }

    @Test
    @OperateOnDeployment(PM_SERVICE_TEST)
    @InSequence(5)
    public void saveOrUpdate_Uetr_sub() throws DataAccessException {
        final UetrSubscription subscription = new UetrSubscription();
        subscriptionCreatorHelper.populateUetrSubscriptionDTO(subscription);

        subscription.setNodes(nodeDao.findAll());
        subscriptionDao.saveOrUpdate(subscription);
        DataBucket liveBucket = dataPersistenceService.getDataBucket("Live", BucketProperties.SUPPRESS_MEDIATION);

        ManagedObject subMO = (ManagedObject) liveBucket.findPoById(subscription.getId());
        final Entity subEntity = subscriptionCreatorHelper.toEntity(subMO.getType(), subMO.getPoId(), subMO.getFdn(), subMO.getAllAttributes(),
                subMO.getAssociations());
        final EntitiesDiff entitiesDiff = subscriptionMapper.doDiff(subscriptionMapper.toEntity(subscription), subEntity);
        assertSame(0, entitiesDiff.getAddedAssociations().size());
        assertSame(0, entitiesDiff.getRemovedAssociations().size());
        assertSame(0, entitiesDiff.getChangedAttributes().size());
    }

    @Test
    @OperateOnDeployment(PM_SERVICE_TEST)
    @InSequence(5)
    public void saveOrUpdate_MoInstance_sub() throws DataAccessException {
        final MoinstanceSubscription subscription = new MoinstanceSubscription();
        subscriptionCreatorHelper.populateMoInstanceSubscriptionDTO(subscription);

        subscription.setNodes(nodeDao.findAll());
        subscriptionDao.saveOrUpdate(subscription);
        DataBucket liveBucket = dataPersistenceService.getDataBucket("Live", BucketProperties.SUPPRESS_MEDIATION);

        ManagedObject subMO = (ManagedObject) liveBucket.findPoById(subscription.getId());
        final Entity subEntity = subscriptionCreatorHelper.toEntity(subMO.getType(), subMO.getPoId(), subMO.getFdn(), subMO.getAllAttributes(),
                subMO.getAssociations());
        final EntitiesDiff entitiesDiff = subscriptionMapper.doDiff(subscriptionMapper.toEntity(subscription), subEntity);
        assertSame(0, entitiesDiff.getAddedAssociations().size());
        assertSame(0, entitiesDiff.getRemovedAssociations().size());
        assertSame(0, entitiesDiff.getChangedAttributes().size());
    }

    //    @Test
    //    @OperateOnDeployment(PM_SERVICE_TEST)
    //    @InSequence(5)
    //    public void transactionWillRollBackIfDpsMarksTransactionForRollbackEvenIfWeDoNotPropagateTheException() {
    //        assertTrue(Status.STATUS_MARKED_ROLLBACK == subscriptionDaoRollbackVerify.forceDpsToThrowRollbackExceptionAndReturnTransactionStatus());
    //    }
    //
    //    @Test
    //    @OperateOnDeployment(PM_SERVICE_TEST)
    //    @InSequence(5)
    //    public void transactionWillNotRollBackIfWhileThrowingExceptionDpsWillNotMarkTransactionForRollback() {
    //        assertFalse(Status.STATUS_MARKED_ROLLBACK == subscriptionDaoRollbackVerify.forceDpsToThrowNonRollbackExceptionAndReturnTransactionStatus());
    //    }
    //
    //    @Test(expected = NonTransientDataAccessException.class)
    //    @OperateOnDeployment(PM_SERVICE_TEST)
    //    @InSequence(5)
    //    public void test_that_exception_interceptor_is_wrapping_the_exceptions_correctly() throws DataAccessException {
    //        final DataPersistenceService spyDPS = org.mockito.Mockito.spy(dataPersistenceService);
    //        when(spyDPS.getLiveBucket()).thenThrow(new DpsIllegalArgumentException("Exception", "Object"));
    //        Whitebox.setInternalState(subscriptionDao, "dataPersistenceService", spyDPS);
    //
    //        subscriptionDao.findOneById(123L, true);
    //    }

    @Test
    @OperateOnDeployment(PM_SERVICE_TEST)
    @InSequence(5)
    public void saveOrUpdate_Gpeh_sub() throws DataAccessException {
        final GpehSubscription subscription = new GpehSubscription();
        subscriptionCreatorHelper.populateGpehSubscriptionDTO(subscription);

        subscription.setNodes(nodeDao.findAll());
        subscriptionDao.saveOrUpdate(subscription);
        DataBucket liveBucket = dataPersistenceService.getDataBucket("Live", BucketProperties.SUPPRESS_MEDIATION);

        ManagedObject subMO = (ManagedObject) liveBucket.findPoById(subscription.getId());
        final Entity subEntity = subscriptionCreatorHelper.toEntity(subMO.getType(), subMO.getPoId(), subMO.getFdn(), subMO.getAllAttributes(),
                subMO.getAssociations());
        final EntitiesDiff entitiesDiff = subscriptionMapper.doDiff(subscriptionMapper.toEntity(subscription), subEntity);
        assertSame(0, entitiesDiff.getAddedAssociations().size());
        assertSame(0, entitiesDiff.getRemovedAssociations().size());
        assertSame(0, entitiesDiff.getChangedAttributes().size());
    }

    @Test
    @OperateOnDeployment(PM_SERVICE_TEST)
    @InSequence(5)
    public void testNodeNeTypes() throws DataAccessException {
        final List<Node> nodes = nodeDao.findAllByNeType("ERBS");
        assertFalse(nodes.isEmpty());
    }

    @Test
    @InSequence(100)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void cleanUp() {
        nodeCreationHelperBean.deleteAll(Subscription.SUBSCRIPTION_MODEL_NAMESPACE, Subscription.SUBSCRIPTION_MODEL_TYPE);
        nodeCreationHelperBean.deleteAll(Node.NODE_MODEL_NAMESPACE, Node.NODE_MODEL_TYPE);
    }
}
