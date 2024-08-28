/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.integration.test.stubs;

import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_NAME;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_ATT_SUBSCRIPTION_TYPE;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_NAME_POSTFIX_CONT_STATS;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_NAME_PREFIX_USERDEF;
import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMJOB_MODEL_NAME;
import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMJOB_MODEL_NAME_SPACE;
import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMJOB_STATUS_ATTRIBUTE;
import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMJOB_SUBSCRIPTION_PO_ID_ATTRIBUTE;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.datalayer.dps.DataBucket;
import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService;
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject;
import com.ericsson.oss.itpf.datalayer.dps.persistence.PersistenceObject;
import com.ericsson.oss.itpf.datalayer.dps.query.Query;
import com.ericsson.oss.itpf.datalayer.dps.query.QueryBuilder;
import com.ericsson.oss.itpf.datalayer.dps.query.TypeContainmentRestrictionBuilder;
import com.ericsson.oss.mediation.sdk.event.MediationTaskRequest;
import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.initiation.model.resource.PMICScannerStatus;
import com.ericsson.oss.services.pm.initiation.scanner.data.PMICScannerData;
import com.ericsson.oss.services.pm.initiation.scanner.data.ScannerCreationHelperBean;
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionActivationTaskRequest;
import com.ericsson.oss.services.pm.initiation.tasks.SubscriptionDeactivationTaskRequest;
import com.ericsson.oss.services.pm.integration.test.constants.TestConstants;

@Stateless
public class PmMediationServiceStub {

    @Inject
    private Logger logger;

    @EJB(lookup = "java:/datalayer/DataPersistenceService")
    private DataPersistenceService dps;

    @Inject
    private ScannerCreationHelperBean scannerCreationHelperBean;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void handleSubscriptionDeactivationTaskRequest(final MediationTaskRequest subscriptionDeactivationTaskRequest) {
        final String nodeAddress = subscriptionDeactivationTaskRequest.getNodeAddress();
        final String subscriptionId = ((SubscriptionDeactivationTaskRequest) subscriptionDeactivationTaskRequest).getSubscriptionId();
        logger.debug("SubscriptionDeactivationTaskRequest {} {}", nodeAddress, subscriptionId);

        final DataBucket liveBucket = dps.getLiveBucket();
        final PersistenceObject subscriptionMo = liveBucket.findPoById(Long.parseLong(subscriptionId));

        if (subscriptionMo == null) {
            logger.debug("subscriptionMo does not Exist[{}]", subscriptionId);
            return;
        }
        final String subscriptionType = subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TYPE);

        if (SubscriptionType.STATISTICAL.name().equals(subscriptionType) ||
                SubscriptionType.RES.name().equals(subscriptionType) ||
                SubscriptionType.BSCRECORDINGS.name().equals(subscriptionType)) {
            final QueryBuilder queryBuilder = dps.getQueryBuilder();
            final Query<TypeContainmentRestrictionBuilder> query = queryBuilder.createTypeQuery("pmic_subscription", "PMICScannerInfo", nodeAddress);
            final Iterator<ManagedObject> iterator = liveBucket.getQueryExecutor().execute(query);
            while (iterator.hasNext()) {
                final ManagedObject scannerMo = iterator.next();
                logger.debug("Scanner Mo FDN [{}], SubscriptonId [{}]", scannerMo.getFdn(), scannerMo.getAttribute("subscriptionId"));
                if (scannerMo.getAttribute("subscriptionId").equals(subscriptionId)) {
                    logger.debug("Deleting Scanner Mo {}", scannerMo.getFdn());
                    liveBucket.deletePo(scannerMo);
                }
            }
        } else if (SubscriptionType.UETRACE.name().equals(subscriptionType)) {
            final QueryBuilder queryBuilder = dps.getQueryBuilder();
            final Query<TypeContainmentRestrictionBuilder> query =
                    queryBuilder.createTypeQuery(PMJOB_MODEL_NAME_SPACE, PMJOB_MODEL_NAME, nodeAddress);
            final Iterator<ManagedObject> iterator = liveBucket.getQueryExecutor().execute(query);
            while (iterator.hasNext()) {
                final ManagedObject scannerMo = iterator.next();
                logger.debug("Scanner Mo FDN [{}], SubscriptonId [{}]", scannerMo.getFdn(),
                        scannerMo.getAttribute(PMJOB_SUBSCRIPTION_PO_ID_ATTRIBUTE));
                if (scannerMo.getAttribute(PMJOB_SUBSCRIPTION_PO_ID_ATTRIBUTE).equals(subscriptionId)) {
                    logger.debug("Updating Scanner Mo {} to INACTIVE status", scannerMo.getFdn());
                    scannerMo.setAttribute(PMJOB_STATUS_ATTRIBUTE, "INACTIVE");
                }
            }
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void handleSubscriptionActivationTaskRequest(final MediationTaskRequest subscriptionActivationTaskRequest) {
        final String nodeAddress = subscriptionActivationTaskRequest.getNodeAddress();
        final String subscriptionId = ((SubscriptionActivationTaskRequest) subscriptionActivationTaskRequest).getSubscriptionId();
        logger.debug("SubscriptionActivationTaskRequest {} {}", nodeAddress, subscriptionId);

        final DataBucket liveBucket = dps.getLiveBucket();
        final PersistenceObject subscriptionMo = liveBucket.findPoById(Long.parseLong(subscriptionId));

        if (subscriptionMo == null) {
            logger.debug("subscriptionMo does not Exist[{}]", subscriptionId);
            return;
        }
        final String subscriptionType = subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_TYPE);
        final String subscriptionName = subscriptionMo.getAttribute(PMIC_ATT_SUBSCRIPTION_NAME);

        if (SubscriptionType.STATISTICAL.name().equals(subscriptionType) ||
                SubscriptionType.RES.name().equals(subscriptionType) ||
                SubscriptionType.BSCRECORDINGS.name().equals(subscriptionType)) {
            final String scannerName = SubscriptionType.BSCRECORDINGS.name().equals(subscriptionType)
                    ? SCANNER_NAME_PREFIX_USERDEF + subscriptionName + TestConstants.SCANNER_NAME_POSTFIX_CONT_BSC_RECORDINGS
                    : SCANNER_NAME_PREFIX_USERDEF + subscriptionName + SCANNER_NAME_POSTFIX_CONT_STATS;
            logger.debug("Scanner [{}] [{}] [{}] ", subscriptionType, subscriptionName, scannerName);
            final ManagedObject scannerObject = liveBucket.findMoByFdn(nodeAddress + TestConstants.PMIC_SCANNER_INFO_SUFFIX + scannerName);
            logger.debug("Scanner Exist[{}]", scannerObject);
            if (scannerObject == null) {
                final Map<String, Object> scannerattr =
                        createScannerAttributesMap(900, subscriptionId, PMICScannerStatus.ACTIVE, "1", scannerName, ProcessType.STATS.name());
                final PMICScannerData scannerData = new PMICScannerData(scannerName, Arrays.asList(getNodeNameFromFdn(nodeAddress)), scannerattr);
                scannerCreationHelperBean.createScannerOnNodes(scannerData);
            } else {
                logger.debug("Updating Scanner Mo {} to ACTIVE status", scannerObject.getFdn());
                scannerObject.setAttribute(TestConstants.PMIC_SCANNER_INFO_ATTRIBUTE_STATUS, "ACTIVE");
                scannerObject.setAttribute(TestConstants.PMIC_SCANNER_INFO_ATTRIBUTE_SUB_POID, subscriptionId);
            }
        } else if (SubscriptionType.UETRACE.name().equals(subscriptionType)) {
            final String scannerName = SCANNER_NAME_PREFIX_USERDEF + subscriptionId + "-" + getNodeNameFromFdn(nodeAddress) + ".UETRACE";
            logger.debug("Scanner [{}] [{}] [{}] ", subscriptionType, subscriptionName, scannerName);
            ManagedObject scannerObject = liveBucket.findMoByFdn(nodeAddress + TestConstants.PMIC_JOB_INFO_SUFFIX + scannerName);
            logger.debug("Scanner Exist[{}]", scannerObject);
            if (scannerObject == null) {
                final Map<String, Object> scannerattr = createScannerAttributesMap(900, subscriptionId, PMICScannerStatus.INACTIVE, scannerName,
                        scannerName, ProcessType.UETRACE.name());
                final PMICScannerData scannerData = new PMICScannerData(scannerName, Arrays.asList(getNodeNameFromFdn(nodeAddress)), scannerattr);
                scannerCreationHelperBean.createScannerOnNodes(scannerData);
                scannerObject = liveBucket.findMoByFdn(nodeAddress + TestConstants.PMIC_JOB_INFO_SUFFIX + scannerName);
                if (scannerObject != null) {
                    scannerObject.setAttribute(PMJOB_STATUS_ATTRIBUTE, "ACTIVE");
                    scannerObject.setAttribute(PMJOB_SUBSCRIPTION_PO_ID_ATTRIBUTE, subscriptionId);
                }
            } else {
                scannerObject.setAttribute(PMJOB_STATUS_ATTRIBUTE, "ACTIVE");
                scannerObject.setAttribute(PMJOB_SUBSCRIPTION_PO_ID_ATTRIBUTE, subscriptionId);
            }
        }
    }

    public Map<String, Object> createScannerAttributesMap(final int ropPeriod, final String subscrptionId, final PMICScannerStatus scannerStatus,
            final String scannerID, final String scannerName, final String processType) {

        final Map<String, Object> scannerAttributes = new HashMap<String, Object>();
        scannerAttributes.put(TestConstants.PMIC_SCANNER_INFO_ATTRIBUTE_NAME, scannerName);
        scannerAttributes.put(TestConstants.PMIC_SCANNER_INFO_ATTRIBUTE_ROP, ropPeriod);
        scannerAttributes.put(TestConstants.PMIC_SCANNER_INFO_ATTRIBUTE_SUB_POID, subscrptionId);
        scannerAttributes.put(TestConstants.PMIC_SCANNER_INFO_ATTRIBUTE_STATUS, scannerStatus.name());
        scannerAttributes.put(TestConstants.PMIC_SCANNER_INFO_ATTRIBUTE_ID, scannerID);
        scannerAttributes.put(TestConstants.PMIC_SCANNER_INFO_ATTRIBUTE_PROCESS_TYPE, processType);
        return scannerAttributes;
    }

    private String getNodeNameFromFdn(final String nodeFdn) {
        return nodeFdn.substring(nodeFdn.indexOf('=') + 1);
    }

}
