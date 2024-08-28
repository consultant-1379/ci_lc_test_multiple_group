/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.ejb;

import static com.ericsson.oss.services.pm.common.logging.PMICLog.Command.DELETE_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.initiation.common.Constants.PMIC_CTUM_SUBSCRIPTION_DESCRIPTION;
import static com.ericsson.oss.services.pm.initiation.common.Constants.PMIC_CTUM_SUBSCRIPTION_NAME;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.CtumSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.ScheduleInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.OperationalState;
import com.ericsson.oss.pmic.dto.subscription.enums.OutputModeType;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.TaskStatus;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;
import com.ericsson.oss.pmic.impl.handler.InvokeInTransaction;
import com.ericsson.oss.pmic.impl.handler.ReadOnly;
import com.ericsson.oss.pmic.profiler.logging.LogProfiler;
import com.ericsson.oss.pmic.util.CollectionUtil;
import com.ericsson.oss.services.pm.common.constants.PmFeature;
import com.ericsson.oss.services.pm.common.logging.PMICLog;
import com.ericsson.oss.services.pm.common.logging.SystemRecorderWrapperLocal;
import com.ericsson.oss.services.pm.exception.DataAccessException;
import com.ericsson.oss.services.pm.exception.RetryServiceException;
import com.ericsson.oss.services.pm.exception.RuntimeDataAccessException;
import com.ericsson.oss.services.pm.exception.ServiceException;
import com.ericsson.oss.services.pm.generic.NodeService;
import com.ericsson.oss.services.pm.initiation.ctum.CtumSubscriptionServiceLocal;
import com.ericsson.oss.services.pm.modelservice.PmCapabilityModelService;
import com.ericsson.oss.services.pm.services.generic.SubscriptionReadOperationService;
import com.ericsson.oss.services.pm.services.generic.SubscriptionWriteOperationService;

/**
 * Implementation of CtumSubscriptionServiceLocal to provide Ctum subscription related info.
 */
@Stateless
public class CtumSubscriptionServiceImpl implements CtumSubscriptionServiceLocal {

    @Inject
    private Logger logger;
    @Inject
    private NodeService nodeService;
    @Inject
    private SubscriptionReadOperationService subscriptionReadOperationService;
    @Inject
    private SystemRecorderWrapperLocal systemRecorder;
    @Inject
    private PmCapabilityModelService pmCapabilityModelService;
    @Inject
    private SubscriptionWriteOperationService subscriptionWriteOperationService;
    @EJB
    private CtumSubscriptionServiceLocal self;

    @Override
    @ReadOnly
    @InvokeInTransaction
    @Asynchronous
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @LogProfiler(name = "Ctum periodic audit")
    public void ctumAudit() {
        logger.info("Ctum Subscription audit execution");
        try {
            final List<Node> nodes = getSupportedNodesForCtumWithPmFunctionOnAndNonEmptyOssModelIdentity();
            final CtumSubscription ctumSubscription = (CtumSubscription) subscriptionReadOperationService
                    .findOneByExactName(PMIC_CTUM_SUBSCRIPTION_NAME, !nodes.isEmpty());
            if (ctumSubscription == null && !nodes.isEmpty()) {
                createCtumSubscription(nodes.size());
            }
            if (ctumSubscription != null) {
                if (nodes.isEmpty()) {
                    deleteCtumSubscription(ctumSubscription);
                } else {
                    final int numberOfNodes = ctumSubscription.getNumberOfNodes();
                    if (nodes.size() != numberOfNodes) {
                        ctumSubscription.setNumberOfNodes(nodes.size());
                        subscriptionWriteOperationService.manageSaveOrUpdate(ctumSubscription);
                        logger.info("Ctum subscription have been updated: number of nodes changed from {} to {} ", numberOfNodes, nodes.size());
                    }
                }
            }
        } catch (final DataAccessException | RuntimeDataAccessException | ServiceException exception) {
            systemRecorder.eventCoarse(PMICLog.Event.SYS_DEF_SUBSCRIPTION_SCHEDULE_FAILED, "System Defined Subscriptions",
                    "System Defined Subscriptions Audit Failed. " + exception.getMessage());
            logger.info("System Defined Subscriptions Audit Failed: {}", exception.getMessage(), exception);
        }
    }

    private void createCtumSubscription(final int nodeSize) throws ServiceException {
        logger.info("Ctum subscription doesn't exist in database and there are SGSN-MME nodes with pm function ON. "
                + "Ctum subscription will be created");
        final CtumSubscription ctumSubscription = new CtumSubscription();
        ctumSubscription.setName(PMIC_CTUM_SUBSCRIPTION_NAME);
        ctumSubscription.setType(SubscriptionType.CTUM);
        ctumSubscription.setDescription(PMIC_CTUM_SUBSCRIPTION_DESCRIPTION);
        ctumSubscription.setOutputMode(OutputModeType.FILE);
        ctumSubscription.setAdministrationState(AdministrationState.INACTIVE);
        ctumSubscription.setUserType(UserType.SYSTEM_DEF);
        ctumSubscription.setRop(RopPeriod.FIFTEEN_MIN);
        ctumSubscription.setOwner("PMIC");
        ctumSubscription.setTaskStatus(TaskStatus.NA);
        ctumSubscription.setOperationalState(OperationalState.NA);
        ctumSubscription.setScheduleInfo(new ScheduleInfo());
        ctumSubscription.setNumberOfNodes(nodeSize);
        try {
            subscriptionWriteOperationService.manageSaveOrUpdate(ctumSubscription);
            logger.info("CTUM Subscription has been created.");
        } catch (final DataAccessException | RetryServiceException exception) {
            logger.error("Error in CTUM create Subscription. Message: {}.", exception.getMessage());
            throw new ServiceException("Cannot create CTUM subscription", exception);
        }
    }

    private void deleteCtumSubscription(final CtumSubscription ctumSubscription) throws ServiceException {
        try {
            logger.info("Deleting Ctum subscription because there are no longer any SGSN-MME nodes with PmFunction On.");
            subscriptionWriteOperationService.deleteWithRetry(ctumSubscription);
            systemRecorder.commandFinishedSuccess(DELETE_SUBSCRIPTION, ctumSubscription.getIdAsString(), "Successfully deleted CTUM Subscription %s",
                    ctumSubscription.getIdAsString());
        } catch (final DataAccessException | RuntimeDataAccessException | IllegalArgumentException exception) {
            logger.error("Unable to audit deletion of CTUM subscription", exception);
            throw new ServiceException("Delete CTUM Subscription Failed", exception);
        }
    }

    @Override
    public List<Node> getSupportedNodesForCtumWithPmFunctionOnAndNonEmptyOssModelIdentity() throws ServiceException {
        final List<String> supportedNeTypes = pmCapabilityModelService.getSupportedNodeTypesForPmFeatureCapability(PmFeature.CTUM_FILE_COLLECTION,
                PmFeature.PMIC_JOB_INFO);
        if (CollectionUtil.isNullOrEmpty(supportedNeTypes)) {
            return Collections.emptyList();
        }
        try {
            final List<Node> sgsnNodes = nodeService.findAllByNeTypeAndPmFunction(supportedNeTypes, true);
            for (final Iterator<Node> iterator = sgsnNodes.iterator(); iterator.hasNext(); ) {
                final Node node = iterator.next();
                if (!Node.isValidOssModelIdentity(node.getOssModelIdentity())) {
                    iterator.remove();
                }
            }
            return sgsnNodes;
        } catch (final DataAccessException | RuntimeDataAccessException | IllegalArgumentException e) {
            throw new ServiceException("Cannot extract SGSN-MME nodes from Database.", e);
        }
    }
}
