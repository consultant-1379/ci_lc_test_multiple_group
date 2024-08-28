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

package com.ericsson.oss.services.pm.listeners;

import static com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState.ACTIVE;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.NE_TYPE_VALUE;
import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.NODE_VERSION_14B;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.inject.Inject;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.pmic.dao.NodeDao;
import com.ericsson.oss.pmic.dao.SubscriptionDao;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.CellTraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.ResourceSubscription;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.UETraceSubscription;
import com.ericsson.oss.pmic.dto.subscription.UetrSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CriteriaSpecification;
import com.ericsson.oss.pmic.dto.subscription.cdts.NodeInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.ScheduleInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo;
import com.ericsson.oss.pmic.dto.subscription.cdts.UeInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.NodeGrouping;
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.pmic.dto.subscription.enums.TraceDepth;
import com.ericsson.oss.pmic.dto.subscription.enums.UeType;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;
import com.ericsson.oss.services.pm.collection.recovery.FileCollectionJobCacheForRecovery;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.SubscriptionNotFoundDataAccessException;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionAttributes;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionDataReader;
import com.ericsson.oss.services.pm.initiation.subscription.data.SubscriptionInfo;
import com.ericsson.oss.services.pm.initiation.subscription.data.exception.SubscriptionTypeNotSupportedException;
import com.ericsson.oss.services.pm.services.exception.ConcurrentSubscriptionUpdateException;
import com.ericsson.oss.services.pm.services.exception.InvalidSubscriptionOperationException;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;
import com.ericsson.oss.services.pm.test.requests.PmServiceRequestsTypes;
import com.ericsson.oss.services.pm.test.requests.SubscriptionOperationRequest;

@SuppressWarnings("deprecation")
@MessageDriven(activationConfig = {@ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/SubscriptionoperationsQueue")})
@TransactionManagement(TransactionManagementType.CONTAINER)
public class PmServiceIntegrationRequestsListener implements MessageListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource(mappedName = "java:/queue/SubscriptionUpdatesQueue")
    private Queue queue;

    @Resource(mappedName = "java:/ConnectionFactory")
    private ConnectionFactory cf;

    @Inject
    private SubscriptionDao subscriptionDao;
    @Inject
    private NodeDao nodeDao;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;
    @Inject
    private FileCollectionJobCacheForRecovery fileCollectionRecoverySchedulerChanger;

    @Override
    public void onMessage(final Message message) {
        logger.debug("Pm Service operation request received... ");
        final ObjectMessage textMessage = (ObjectMessage) message;
        SubscriptionOperationRequest serializedSubscriptionOperationInfo = null;
        String subscriptionOp = null;

        try {
            if (!message.getJMSRedelivered()) {
                final Object object = textMessage.getObject();
                if (object instanceof SubscriptionOperationRequest) {
                    serializedSubscriptionOperationInfo = (SubscriptionOperationRequest) object;
                    subscriptionOp = serializedSubscriptionOperationInfo.getSubscriptionOperation();
                }

                logger.trace("{} request received from test client..", subscriptionOp);
                switch (PmServiceRequestsTypes.valueOf(subscriptionOp)) {
                    case ACTIVATE:
                    case DEACTIVATE:
                        logger.trace("{} subscription", subscriptionOp);
                        initiateSubscription(serializedSubscriptionOperationInfo);
                        break;

                    case CREATE:
                        logger.trace("CREATE subscription ...");
                        createSubscription(serializedSubscriptionOperationInfo);
                        break;

                    case CREATE_UE:
                        logger.trace("CREATE UE Trace subscription ...");
                        createUeTraceSubscription(serializedSubscriptionOperationInfo);
                        break;

                    case DELETE:
                        logger.trace("DELETE subscription ...");
                        deleteSubscriptions(serializedSubscriptionOperationInfo);
                        break;

                    case DELETE_UE:
                        logger.trace("DELETE UE Trace subscription ...");
                        deleteUeTraceSubscriptions(serializedSubscriptionOperationInfo);
                        break;

                    case UPDATE_SUB_PERSISTENCE_SERVICE:
                        logger.trace("UPDATE_SUB_PERSISTENCE_SERVICE ...");
                        updateSubscriptionsViaSubsPersistenceService(serializedSubscriptionOperationInfo);
                        break;

                    case DELETE_SUB_PERSISTENCE_SERVICE:
                        logger.trace("DELETE_SUB_PERSISTENCE_SERVICE ...");
                        deleteSubscriptionsViaSubsPersistenceService(serializedSubscriptionOperationInfo);
                        break;
                    case ADD_NODE_TO_SUBSCRIPTION:
                        logger.trace("ADD_NODE_TO_SUBSCRIPTION ...");
                        addNodesToSubscription(serializedSubscriptionOperationInfo);
                        break;

                    case REMOVE_NODE_FROM_SUBSCRIPTION:
                        logger.trace("REMOVE_NODE_ACTIVE_SUBSCRIPTION ...");
                        removeNodesFromSubscription(serializedSubscriptionOperationInfo);
                        break;

                    case LIST:
                        logger.trace("LIST subscription ...");
                        listSubscriptions(serializedSubscriptionOperationInfo);
                        break;

                    case LIST_ACTIVE:
                        logger.trace("LIST_ACTIVE subscription ...");
                        listAllActiveSubscription();
                        break;

                    default:
                        logger.warn("Operation {} not supported ...", subscriptionOp);
                        break;
                }
            }
        } catch (final Exception e) {
            logger.error("Error occured while executing subscription operation ", e);

        }
    }


    private void listSubscriptions(final SubscriptionOperationRequest subscriptionOperationInfo) {
        final String subscriptionDataFileName = subscriptionOperationInfo.getSubscriptionDataFile();
        final SubscriptionDataReader sdr = new SubscriptionDataReader(subscriptionDataFileName);
        final List<SubscriptionInfo> subscriptionList = sdr.getSubscriptionList();
        final List<String> specificSubscriptionList = subscriptionOperationInfo.getSubscriptionToRunActionOn();
        final Map<String, Map<String, Object>> responseMap = new HashMap<>();
        for (final SubscriptionInfo subscriptionInfo : subscriptionList) {
            if (specificSubscriptionList == null || specificSubscriptionList.contains(subscriptionInfo.getName())) {
                try {
                    final Subscription s = subscriptionDao.findOneByExactName(subscriptionInfo.getName(), true);
                    responseMap.put(subscriptionInfo.getName(), getAttributesToBeReturned(s));
                } catch (final Exception e) {
                    logger.error("Can not create subscription from file {} ", e);
                    responseMap.put(subscriptionInfo.getName(), getErrorResponseForSubscription(e));
                }
            }
        }

        sendResponse(responseMap, null, null);
    }

    private void listAllActiveSubscription() {
        try {
            final AdministrationState[] administrationStates = {ACTIVE};
            final List<Subscription> subscriptions = subscriptionDao.findAllBySubscriptionTypeAndAdministrationState(null, administrationStates,
                    true);
            sendResponse(getSubscriptionsAttributesToBeReturned(subscriptions.toArray(new Subscription[subscriptions.size()])), null, null);
        } catch (final Exception e) {
            logger.error("Can not list active subscriptions from file ", e);
            sendResponse(new HashMap<String, Map<String, Object>>(), null, null);
        }

    }

    private void initiateSubscription(final SubscriptionOperationRequest subscriptionOperationInfo) {
        final Map<String, Map<String, Object>> responseMap = new HashMap<>();
        final Long id = Long
                .valueOf((String) subscriptionOperationInfo.getAttributesSubscriptionToBeCreatedWith().get(SubscriptionAttributes.ID.name()));
        final String name = (String) subscriptionOperationInfo.getAttributesSubscriptionToBeCreatedWith().get(SubscriptionAttributes.NAME.name());
        final Date persistenceTime = (Date) subscriptionOperationInfo.getAttributesSubscriptionToBeCreatedWith()
                .get(SubscriptionAttributes.PERSISTENCE_TIME.name());
        try {
            final PmServiceRequestsTypes requestType = PmServiceRequestsTypes.valueOf(subscriptionOperationInfo.getSubscriptionOperation());
            final Subscription sub = subscriptionReadOperationService.findByIdWithRetry(id, true);
            if (requestType.equals(PmServiceRequestsTypes.ACTIVATE)) {
                logger.debug("Integration testsuite is activating subscription {}", name);
                subscriptionWriteOperationService.activate(sub, persistenceTime);
            } else if (requestType.equals(PmServiceRequestsTypes.DEACTIVATE)) {
                logger.debug("Integration testsuite is deactivating subscription {}", name);
                subscriptionWriteOperationService.deactivate(sub, persistenceTime);
            }
            responseMap.put(sub.getName(), getAttributesToBeReturned(sub));
        } catch (final Exception e) {
            responseMap.put(name, getErrorResponseForSubscription(e));
        }
        sendResponse(responseMap, null, null);
    }

    private void deleteSubscriptions(final SubscriptionOperationRequest subscriptionOperationInfo) {
        final String subscriptionDataFileName = subscriptionOperationInfo.getSubscriptionDataFile();
        final SubscriptionDataReader sdr = new SubscriptionDataReader(subscriptionDataFileName);
        final List<SubscriptionInfo> subscriptionList = sdr.getSubscriptionList();
        final List<String> specificSubscriptionList = subscriptionOperationInfo.getSubscriptionToRunActionOn();
        final Map<String, Map<String, Object>> responseMap = new HashMap<>();
        for (final SubscriptionInfo subscriptionInfo : subscriptionList) {
            if (specificSubscriptionList == null || specificSubscriptionList.contains(subscriptionInfo.getName())) {
                try {
                    logger.debug("Integration testsuite is deleting subscription {}", subscriptionInfo.getName());
                    final Subscription subscription = subscriptionDao.findOneByExactName(subscriptionInfo.getName(), true);
                    subscriptionDao.delete(subscription);
                    responseMap.put(subscriptionInfo.getName(), getAttributesToBeReturned(subscription));
                } catch (final Exception e) {
                    logger.error("Can not delete subscription", e);
                    responseMap.put(subscriptionInfo.getName(), getErrorResponseForSubscription(e));
                }
            }

        }
        sendResponse(responseMap, null, null);
    }

    private void deleteUeTraceSubscriptions(final SubscriptionOperationRequest subscriptionOperationInfo) {
        final String subscriptionDataFileName = subscriptionOperationInfo.getSubscriptionDataFile();
        final SubscriptionDataReader sdr = new SubscriptionDataReader(subscriptionDataFileName);
        final List<SubscriptionInfo> subscriptionList = sdr.getUeTraceSubscriptionList();
        final List<String> specificSubscriptionList = subscriptionOperationInfo.getSubscriptionToRunActionOn();
        final Map<String, Map<String, Object>> responseMap = new HashMap<>();
        for (final SubscriptionInfo subscriptionInfo : subscriptionList) {
            if (specificSubscriptionList == null || specificSubscriptionList.contains(subscriptionInfo.getName())) {
                try {
                    logger.debug("Integration testsuite is deleting subscription {}", subscriptionInfo.getName());
                    final Subscription subscription = subscriptionDao.findOneByExactName(subscriptionInfo.getName(), true);
                    subscriptionDao.delete(subscription);
                    responseMap.put(subscriptionInfo.getName(), getAttributesToBeReturned(subscription));
                } catch (final Exception e) {
                    logger.error("Can not delete subscription", e);
                    responseMap.put(subscriptionInfo.getName(), getErrorResponseForSubscription(e));
                }
            }

        }
        sendResponse(responseMap, null, null);
    }

    private void deleteSubscriptionsViaSubsPersistenceService(final SubscriptionOperationRequest subscriptionOperationInfo) {
        final String subscriptionDataFileName = subscriptionOperationInfo.getSubscriptionDataFile();
        final SubscriptionDataReader sdr = new SubscriptionDataReader(subscriptionDataFileName);
        final List<SubscriptionInfo> subscriptionList = sdr.getSubscriptionList();
        final List<String> specificSubscriptionList = subscriptionOperationInfo.getSubscriptionToRunActionOn();
        final Map<String, Map<String, Object>> responseMap = new HashMap<>();
        for (final SubscriptionInfo subscriptionInfo : subscriptionList) {
            if (specificSubscriptionList == null || specificSubscriptionList.contains(subscriptionInfo.getName())) {
                try {
                    logger.debug("Integration testsuite is deleting subscription {}", subscriptionInfo.getName());
                    final Subscription subscription = subscriptionDao.findOneByExactName(subscriptionInfo.getName(), true);
                    subscriptionWriteOperationService.delete(subscription);
                    responseMap.put(subscriptionInfo.getName(), getAttributesToBeReturned(subscription));
                } catch (final Exception e) {
                    logger.error("Can not delete subscription", e);
                    responseMap.put(subscriptionInfo.getName(), getErrorResponseForSubscription(e));
                }
            }

        }
        sendResponse(responseMap, null, null);
    }

    private void updateSubscriptionsViaSubsPersistenceService(final SubscriptionOperationRequest subscriptionOperationInfo) {
        final String subscriptionDataFileName = subscriptionOperationInfo.getSubscriptionDataFile();
        final SubscriptionDataReader sdr = new SubscriptionDataReader(subscriptionDataFileName);
        final List<SubscriptionInfo> subscriptionList = sdr.getSubscriptionList();
        final List<String> specificSubscriptionList = subscriptionOperationInfo.getSubscriptionToRunActionOn();
        final Map<String, Map<String, Object>> responseMap = new HashMap<>();
        for (final SubscriptionInfo subscriptionInfo : subscriptionList) {
            if (specificSubscriptionList == null || specificSubscriptionList.contains(subscriptionInfo.getName())) {
                try {
                    logger.debug("Integration testsuite is updating subscription {}", subscriptionInfo);
                    final Subscription subscription = subscriptionDao.findOneByExactName(subscriptionInfo.getName(), true);
                    subscriptionWriteOperationService.saveOrUpdate(subscription, "123");
                    responseMap.put(subscriptionInfo.getName(), getAttributesToBeReturned(subscription));
                } catch (final Exception e) {
                    logger.error("Can not delete subscription", e);
                    responseMap.put(subscriptionInfo.getName(), getErrorResponseForSubscription(e));
                }
            }

        }
        sendResponse(responseMap, null, null);
    }

    private Map<String, Object> getErrorResponseForSubscription(final Exception e) {
        final Map<String, Object> map = new HashMap<>();
        map.put("Exception", e.getClass().getSimpleName());
        map.put("ExceptionMessage", e.getMessage());
        return map;
    }

    private void createUeTraceSubscription(final SubscriptionOperationRequest subscriptionOperationInfo) {
        final String subscriptionDataFileName = subscriptionOperationInfo.getSubscriptionDataFile();
        final SubscriptionDataReader sdr = new SubscriptionDataReader(subscriptionDataFileName);
        final List<SubscriptionInfo> subscriptionList = sdr.getUeTraceSubscriptionList();
        final List<String> specificSubscriptionList = subscriptionOperationInfo.getSubscriptionToRunActionOn();
        final Map<String, Map<String, Object>> responseMap = new HashMap<>();
        for (final SubscriptionInfo subscriptionInfo : subscriptionList) {
            if (specificSubscriptionList == null || specificSubscriptionList.contains(subscriptionInfo.getName())) {
                Subscription sub = null;
                try {
                    sub = SubscriptionType.fromString(subscriptionInfo.getSubscriptionType().name()).getIdentifier().newInstance();
                } catch (final IllegalAccessException | InstantiationException e) {
                    logger.error("Subscription Type not supported", e);
                }
                try {
                    resolveSubscriptionType(sub, subscriptionInfo);
                } catch (final SubscriptionTypeNotSupportedException e1) {
                    logger.error("Subscription Type not supported", e1);
                } catch (final DataAccessException e) {
                    logger.error("DataAccessException caught:", e);
                }

                persistSubscriptionAndPrepareResponse(subscriptionDataFileName, responseMap, subscriptionInfo, sub);
            }
        }
        sendResponse(responseMap, null, null);
    }

    private void persistSubscriptionAndPrepareResponse(final String subscriptionDataFileName, final Map<String, Map<String, Object>> responseMap,
                                                       final SubscriptionInfo subscriptionInfo, final Subscription sub) {
        try {
            logger.debug("Integration testsuite is persisting subscription {}", subscriptionInfo);
            subscriptionWriteOperationService.saveOrUpdate(sub);
            final Subscription s = subscriptionDao.findOneByExactName(subscriptionInfo.getName(), true);
            responseMap.put(subscriptionInfo.getName(), getAttributesToBeReturned(s));
        } catch (final Exception e) {
            logger.error("Can not create subscription from file {} ", subscriptionDataFileName, e);
            responseMap.put(subscriptionInfo.getName(), getErrorResponseForSubscription(e));
        }
    }

    private void resolveSubscriptionType(final Subscription sub, final SubscriptionInfo subscriptionInfo)
            throws SubscriptionTypeNotSupportedException, DataAccessException {
        switch (subscriptionInfo.getSubscriptionType()) {
            case STATISTICAL:
                createStatisticalSubscription((StatisticalSubscription) sub, subscriptionInfo);
                break;

            case CELLTRACE:
                createCellTraceSubscription((CellTraceSubscription) sub, subscriptionInfo);
                break;

            case UETRACE:
                createUeTraceSubscription((UETraceSubscription) sub, subscriptionInfo);
                break;

            case UETR:
                createUetrSubscription((UetrSubscription) sub, subscriptionInfo);
                break;

            default:
                throw new SubscriptionTypeNotSupportedException("Not Valid SubscriptionType : " + subscriptionInfo.getSubscriptionType());
        }
    }

    private void createSubscription(final SubscriptionOperationRequest subscriptionOperationInfo) {
        final String subscriptionDataFileName = subscriptionOperationInfo.getSubscriptionDataFile();
        final SubscriptionDataReader sdr = new SubscriptionDataReader(subscriptionDataFileName);
        final Map<String, Object> extraSubscriptionAttributes = subscriptionOperationInfo.getAttributesSubscriptionToBeCreatedWith();
        final List<SubscriptionInfo> subscriptionList = sdr.getSubscriptionList();
        final List<String> specificSubscriptionList = subscriptionOperationInfo.getSubscriptionToRunActionOn();
        final Map<String, Map<String, Object>> responseMap = new HashMap<>();
        for (final SubscriptionInfo subscriptionInfo : subscriptionList) {
            if (specificSubscriptionList == null || specificSubscriptionList.contains(subscriptionInfo.getName())) {
                Subscription sub = null;
                try {
                    sub = SubscriptionType.fromString(subscriptionInfo.getSubscriptionType().name()).getIdentifier().newInstance();
                } catch (final IllegalAccessException | InstantiationException e) {
                    logger.error("Subscription Type not supported", e);
                }
                createBasicSubscription(sub, subscriptionInfo);
                setCustomizedAttr(sub, extraSubscriptionAttributes);
                try {
                    resolveSubscriptionType(sub, subscriptionInfo);
                } catch (final SubscriptionTypeNotSupportedException e1) {
                    logger.error("Subscription Type not supported", e1);
                } catch (final DataAccessException e) {
                    logger.error("DataAccessException caught:", e);
                }

                persistSubscriptionAndPrepareResponse(subscriptionDataFileName, responseMap, subscriptionInfo, sub);
            }
        }
        sendResponse(responseMap, null, null);
    }

    private void createBasicSubscription(final Subscription sub, final SubscriptionInfo subscriptionInfo) {
        sub.setName(subscriptionInfo.getName());
        sub.setDescription(subscriptionInfo.getDescription());
        sub.setOwner(subscriptionInfo.getOwner());
        sub.setRop(RopPeriod.fromString(subscriptionInfo.getRopInfo().name()));
        sub.setUserType(UserType.fromString(subscriptionInfo.getUserType().name()));
        sub.setTaskStatus(TaskStatus.fromString(subscriptionInfo.getJobStatus().name()));
        sub.setType(SubscriptionType.fromString(subscriptionInfo.getSubscriptionType().name()));
        sub.setOperationalState(OperationalState.fromString(subscriptionInfo.getOpState().name()));
        sub.setAdministrationState(AdministrationState.fromString(subscriptionInfo.getAdminState().name()));
        sub.setScheduleInfo(new ScheduleInfo());
        sub.getScheduleInfo().setStartDateTime(subscriptionInfo.getStartTime());
        sub.getScheduleInfo().setEndDateTime(subscriptionInfo.getEndTime());
        logger.trace("Added basic subscription attributes");
    }

    private void createStatisticalSubscription(final StatisticalSubscription sub, final SubscriptionInfo subscriptionInfo)
            throws DataAccessException {
        populateResourceSubscription(sub, subscriptionInfo);
        logger.trace("Added all attributes successfully to create a statistical subscripiton {}", subscriptionInfo.toString());
    }

    private void createCellTraceSubscription(final CellTraceSubscription sub, final SubscriptionInfo subscriptionInfo)
            throws DataAccessException {
        populateResourceSubscription(sub, subscriptionInfo);
        populateAdditionalCellTraceAttributes(sub, subscriptionInfo);
        logger.trace("Added all attributes successfully to create a celltrace subscripiton {}", subscriptionInfo.toString());
    }

    private void createUetrSubscription(final UetrSubscription sub, final SubscriptionInfo subscriptionInfo)
            throws DataAccessException {
        populateResourceSubscription(sub, subscriptionInfo);
        populateAdditionalUetrAttributes(sub, subscriptionInfo);
        logger.trace("Added all attributes successfully to create a uetr subscription {}", subscriptionInfo.toString());
    }

    private void createUeTraceSubscription(final UETraceSubscription sub, final SubscriptionInfo subscriptionInfo) {
        populateUeTraceSubscription(sub, subscriptionInfo);
        logger.trace("Added all attributes successfully to create a uetrace subscripiton {}", subscriptionInfo.toString());
    }

    private void populateResourceSubscription(final ResourceSubscription sub, final SubscriptionInfo subscriptionInfo)
            throws DataAccessException {
        sub.setPnpEnabled(subscriptionInfo.isPnP());
        sub.setFilterOnManagedElement(subscriptionInfo.isFileterOnME());
        sub.setFilterOnManagedFunction(subscriptionInfo.isFileterOnMF());
        setEventsOrCounters(sub, subscriptionInfo);
        setNodes(sub, subscriptionInfo);
        if (subscriptionInfo.getCbs()) {
            sub.setCbs(subscriptionInfo.getCbs());
            sub.setCriteriaSpecification(subscriptionInfo.getCriteriaSpecification());
        } else {
            sub.setCbs(false);
            sub.setCriteriaSpecification(new ArrayList<CriteriaSpecification>());
            sub.setNodeListIdentity(0);
        }
        logger.trace("Added Nodes, counter or event information");
    }

    private void populateAdditionalUetrAttributes(final UetrSubscription sub, final SubscriptionInfo subscriptionInfo) {
        sub.setUeInfo(subscriptionInfo.getUeInfoList());
        sub.setOutputMode(OutputModeType.fromString(subscriptionInfo.getOutputMode().name()));
        final StreamInfo streamInfo = new StreamInfo(subscriptionInfo.getIpAddress(), subscriptionInfo.getPort(), subscriptionInfo.getPortOffset());
        final List<StreamInfo> streamInfos = new ArrayList<>();
        streamInfos.add(streamInfo);
        sub.setStreamInfoList(streamInfos);
        logger.trace("Added ueInfo and streamInfo");
    }

    private void populateUeTraceSubscription(final UETraceSubscription sub, final SubscriptionInfo subscriptionInfo) {
        sub.setName(subscriptionInfo.getName());
        sub.setType(SubscriptionType.fromString(subscriptionInfo.getSubscriptionType().name()));
        sub.setDescription(subscriptionInfo.getDescription());
        sub.setOwner(subscriptionInfo.getOwner());
        sub.setRop(RopPeriod.fromString(subscriptionInfo.getRopInfo().name()));
        sub.setUserType(UserType.fromString(subscriptionInfo.getUserType().name()));
        sub.setAdministrationState(AdministrationState.fromString(subscriptionInfo.getAdminState().name()));
        sub.setTaskStatus(TaskStatus.fromString(subscriptionInfo.getJobStatus().name()));
        sub.setOutputMode(OutputModeType.fromString(subscriptionInfo.getOutputMode().name()));
        final StreamInfo streamInfo = new StreamInfo();
        streamInfo.setPort(subscriptionInfo.getPort());
        streamInfo.setIpAddress(subscriptionInfo.getIpAddress());
        sub.setStreamInfo(streamInfo);
        final UeInfo ueInfo = new UeInfo();
        ueInfo.setType(UeType.fromString(subscriptionInfo.getUeInfoType().name()));
        ueInfo.setValue(Integer.toString(subscriptionInfo.getUeInfoValue()));
        sub.setUeInfo(ueInfo);
        final List<NodeInfo> nodeInfoList = new ArrayList<>();
        final NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setNodeGrouping(NodeGrouping.fromString(subscriptionInfo.getNodeInfoType().name()));
        nodeInfo.setTraceDepth(TraceDepth.fromString(subscriptionInfo.getTraceDepth().name()));
        final List<String> interfaceTypes = new ArrayList<>();
        final String interfaceType = subscriptionInfo.getInterfaceType();
        interfaceTypes.add(interfaceType);
        nodeInfo.setInterfaceTypes(interfaceTypes);
        nodeInfoList.add(nodeInfo);
        sub.setNodeInfoList(nodeInfoList);
    }

    private void populateAdditionalCellTraceAttributes(final CellTraceSubscription sub, final SubscriptionInfo subscriptionInfo) {
        sub.setOutputMode(OutputModeType.fromString(subscriptionInfo.getOutputMode().name()));
        sub.setUeFraction(subscriptionInfo.getUeFraction());
        sub.setAsnEnabled(subscriptionInfo.isAsnEnbled());
        final List<StreamInfo> cellTraceStreamInfo = new ArrayList<>();
        final StreamInfo streamInfo = new StreamInfo();
        streamInfo.setPort(subscriptionInfo.getPort());
        streamInfo.setIpAddress(subscriptionInfo.getIpAddress());
        cellTraceStreamInfo.add(streamInfo);
        sub.setStreamInfoList(cellTraceStreamInfo);
        logger.trace("Added additional cell trace information");
    }

    private void setCustomizedAttr(final Subscription subscription, final Map<String, Object> customizedAttr) {
        if (customizedAttr != null) {
            final Set<String> customizedAttrName = customizedAttr.keySet();
            for (final String attr : customizedAttrName) {
                switch (SubscriptionAttributes.valueOf(attr)) {
                    case START_TIME:
                        final ScheduleInfo si = new ScheduleInfo();
                        si.setStartDateTime((Date) customizedAttr.get(attr));
                        subscription.setScheduleInfo(si);
                        break;
                    case ADMIN_STATE:
                        subscription.setAdministrationState(AdministrationState.fromString((String) customizedAttr.get(attr)));
                        break;

                    case ACTIVATION_TIME:
                        subscription.setActivationTime((Date) customizedAttr.get(attr));
                        break;

                    case PERSISTENCE_TIME:
                        subscription.setPersistenceTime((Date) customizedAttr.get(attr));
                        break;

                    default:
                        break;
                }
            }
        }
    }

    private Map<String, Map<String, Object>> getSubscriptionsAttributesToBeReturned(final Subscription... subscriptions) {
        final Map<String, Map<String, Object>> subscriptionsAttributes = new HashMap<>();
        for (final Subscription subscription : subscriptions) {
            subscriptionsAttributes.put(subscription.getName(), getAttributesToBeReturned(subscription));
        }
        return subscriptionsAttributes;
    }

    private Map<String, Object> getAttributesToBeReturned(final Subscription sub) {
        final Map<String, Object> attr = new HashMap<>();
        final SubscriptionAttributes[] s = SubscriptionAttributes.values();
        for (final SubscriptionAttributes subscriptionAttributes : s) {
            switch (subscriptionAttributes) {
                case START_TIME:
                    if (sub.getScheduleInfo() != null) {
                        attr.put(subscriptionAttributes.name(), sub.getScheduleInfo().getStartDateTime());
                    }
                    break;
                case NAME:
                    attr.put(subscriptionAttributes.name(), sub.getName());
                    break;
                case DESCRIPTION:
                    attr.put(subscriptionAttributes.name(), sub.getDescription());
                    break;
                case TASK_STATUS:
                    attr.put(subscriptionAttributes.name(), sub.getTaskStatus());
                    break;

                case ADMIN_STATE:
                    attr.put(subscriptionAttributes.name(), sub.getAdministrationState().name());
                    break;

                case ROP_INTERVAL:
                    attr.put(subscriptionAttributes.name(), sub.getRop());
                    break;

                case ID:
                    attr.put(subscriptionAttributes.name(), sub.getIdAsString());
                    break;
                case PERSISTENCE_TIME:
                    attr.put(subscriptionAttributes.name(), sub.getPersistenceTime());
                    break;

                case COUNTERS:
                    if (sub instanceof StatisticalSubscription) {
                        attr.put(subscriptionAttributes.name(), ((StatisticalSubscription) sub).getCounters());
                    }
                    break;
                case EVENTS:
                    if (sub instanceof CellTraceSubscription) {
                        attr.put(subscriptionAttributes.name(), ((CellTraceSubscription) sub).getEvents());
                    }
                    break;
                case NODES:
                    if (sub instanceof ResourceSubscription) {
                        attr.put(subscriptionAttributes.name(), ((ResourceSubscription) sub).getNodes());
                    }
                    break;
                case CBS:
                    if (sub instanceof ResourceSubscription) {
                        attr.put(subscriptionAttributes.name(), ((ResourceSubscription) sub).isCbs());
                    }
                    break;
                case CRITERIA_SPECIFICATION:
                    if (sub instanceof ResourceSubscription) {
                        attr.put(subscriptionAttributes.name(), ((ResourceSubscription) sub).getCriteriaSpecification());
                    }
                    break;
                case NODE_LIST_IDENTITY:
                    if (sub instanceof ResourceSubscription) {
                        attr.put(subscriptionAttributes.name(), ((ResourceSubscription) sub).getNodeListIdentity());
                    }
                    break;
                case TYPE:
                    attr.put(subscriptionAttributes.name(), sub.getType().name());
                    break;
                default:
                    break;
            }

        }

        return attr;
    }

    private void setEventsOrCounters(final Subscription subscription, final SubscriptionInfo subInfo) {
        final ResourceSubscription sub = (ResourceSubscription) subscription;
        logger.trace("Counters/Events to add {}", subInfo.getCounters());
        if (subscription instanceof StatisticalSubscription) {
            ((StatisticalSubscription) sub).getCounters().addAll(subInfo.getCounters());
        } else if (subscription instanceof CellTraceSubscription) {
            ((CellTraceSubscription) sub).getEvents().addAll(subInfo.getEvents());
        } else if (subscription instanceof UetrSubscription) {
            ((UetrSubscription) sub).getEvents().addAll(subInfo.getEvents());
        }
    }

    private void addNodesToSubscription(final SubscriptionOperationRequest subscriptionOperationRequest)
            throws DataAccessException, RetryServiceException, SubscriptionNotFoundDataAccessException, InvalidSubscriptionOperationException,
            ConcurrentSubscriptionUpdateException {
        final Map<String, Map<String, Object>> responseMap = new HashMap<>();
        final List<String> subscriptionToBeUpdated = subscriptionOperationRequest.getSubscriptionToRunActionOn();
        for (final String subscriptionName : subscriptionToBeUpdated) {
            final ResourceSubscription s = (ResourceSubscription) subscriptionDao.findOneByExactName(subscriptionName, true);
            final List<Node> susbNodeList = s.getNodes();
            final List<String> nodeFdnList = subscriptionOperationRequest.getNodesToBeAddedOrRemoved();
            final List<Node> nodesToBeAdded = nodeDao.findAllByFdn(nodeFdnList);
            logger.debug("Integration testsuite is adding nodes to subscription {}, Nodes: {}", subscriptionName, nodesToBeAdded);
            susbNodeList.addAll(nodesToBeAdded);
            s.setNodes(susbNodeList);
            subscriptionWriteOperationService.saveOrUpdate(s);
        }
        sendResponse(responseMap, null, null);
    }

    private void removeNodesFromSubscription(final SubscriptionOperationRequest subscriptionOperationRequest)
            throws DataAccessException, RetryServiceException, SubscriptionNotFoundDataAccessException {
        final Map<String, Map<String, Object>> responseMap = new HashMap<>();
        final List<String> subscriptionToBeUpdated = subscriptionOperationRequest.getSubscriptionToRunActionOn();
        for (final String subscriptionName : subscriptionToBeUpdated) {
            final ResourceSubscription s = (ResourceSubscription) subscriptionDao.findOneByExactName(subscriptionName, true);
            final List<Node> susbNodeList = s.getNodes();
            final List<String> nodeFdnList = subscriptionOperationRequest.getNodesToBeAddedOrRemoved();
            final List<Node> nodesToBeRemoved = nodeDao.findAllByFdn(nodeFdnList);
            logger.debug("Integration testsuite is removing nodes from subscription {}, Nodes: {}", subscriptionName, nodesToBeRemoved);
            susbNodeList.removeAll(nodesToBeRemoved);
            logger.trace("Total nodes for the subscription {}", susbNodeList);
            s.setNodes(susbNodeList);
            subscriptionWriteOperationService.saveOrUpdate(s);
        }
        sendResponse(responseMap, null, null);
    }

    private List<Node> getPMICNodeInfoList(final List<String> nodesList) throws DataAccessException {
        final List<Node> subNodeList = new ArrayList<>();
        for (final String nodeFdn : nodesList) {
            logger.trace("looking for node {} in DPS ", nodeFdn);
            final long poID = nodeDao.findOneByFdn(nodeFdn.trim()).getId();
            final Node pmiNode = new Node();
            pmiNode.setFdn(nodeFdn);
            pmiNode.setId(poID);
            pmiNode.setOssModelIdentity(NODE_VERSION_14B);
            pmiNode.setNeType(NE_TYPE_VALUE);
            subNodeList.add(pmiNode);
        }

        return subNodeList;
    }

    private void setNodes(final Subscription subscription, final SubscriptionInfo subInfo) throws DataAccessException {
        final ResourceSubscription sub = (ResourceSubscription) subscription;

        final List<String> nodesList = subInfo.getNodes();
        logger.trace("Node String list {}", nodesList);
        final List<Node> subNodeList = getPMICNodeInfoList(nodesList);
        sub.setNodes(subNodeList);
    }

    private void sendResponse(final Serializable response) {
        Connection connection = null;
        MessageProducer producer = null;
        Session session = null;
        try {
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            producer = session.createProducer(queue);
            final ObjectMessage message = session.createObjectMessage();
            message.setObject(response);
            producer.send(message);
            producer.close();
        } catch (final Exception e) {
            logger.warn("Error in sending message {} to queue: {}", response, e.getMessage());
        } finally {
            closeSession(session);
            closeConnection(connection);
        }
    }

    public void sendResponse(final Map<String, Map<String, Object>> returnedAttr, final Map<String, Object> receivedAttr, final String subsDataFile) {
        sendResponse(new SubscriptionOperationRequest(PmServiceRequestsTypes.RETURNED, returnedAttr, receivedAttr, subsDataFile));
    }

    private void closeConnection(final Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (final Exception e) {
                logger.warn("Error closing connection. Message: {}", e.getMessage());
            }
        }
    }

    private void closeSession(final Session session) {
        if (session != null) {
            try {
                session.close();
            } catch (final Exception e) {
                logger.warn("Error closing session. Message: {}", e.getMessage());
            }
        }
    }
}
