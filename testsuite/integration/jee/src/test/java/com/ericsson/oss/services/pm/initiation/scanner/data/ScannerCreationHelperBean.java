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

package com.ericsson.oss.services.pm.initiation.scanner.data;

import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_MODEL_TYPE_SUBSCRIPTION;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionDPSConstant.PMIC_SUBSCRIPTION_NS;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_NAME_PMS_PROFILE_ID;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_NAME_POSTFIX_CONT_STATS;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_NAME_PREFIX_USERDEF;
import static com.ericsson.oss.services.pm.initiation.util.constants.SubscriptionOperationConstant.SCANNER_NAME_PREFIX_USERDEF_PMS;
import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMIC_JOB_TYPE_UETRACE;
import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMJOB_MODEL_NAME;
import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMJOB_MODEL_NAME_SPACE;
import static com.ericsson.oss.services.pm.initiation.utils.PmJobConstant.PMJOB_MODEL_VERSION;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.datalayer.dps.BucketProperties;
import com.ericsson.oss.itpf.datalayer.dps.DataBucket;
import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService;
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject;
import com.ericsson.oss.itpf.datalayer.dps.query.Query;
import com.ericsson.oss.itpf.datalayer.dps.query.QueryExecutor;
import com.ericsson.oss.itpf.datalayer.dps.query.Restriction;
import com.ericsson.oss.itpf.datalayer.dps.query.TypeRestrictionBuilder;
import com.ericsson.oss.services.pm.integration.test.constants.TestConstants;

/**
 * Helper class to create scanners under NetworkElement for testing.
 *
 * @author ekamkal
 */
@Singleton
@TransactionManagement(TransactionManagementType.CONTAINER)
public class ScannerCreationHelperBean {

    private static final String PMIC_SCANNER_INFO_TYPE = "PMICScannerInfo";
    private static final String PMIC_SCANNER_INFO_VERSION = "1.0.0";
    private static final String PMIC_NAMESPACE = "pmic_subscription";
    private static final String PMIC_SCANNER_INFO_ATTRIBUTE_NODE_NAME = "nodeName";
    private final static Logger logger = LoggerFactory.getLogger(ScannerCreationHelperBean.class);
    private final String NETWORK_ELEMENT = "NetworkElement=";

    @EJB(lookup = "java:/datalayer/DataPersistenceService")
    DataPersistenceService dps;

    public void createScannerOnNodes(final PMICScannerData scannerData) {
        final List<String> nodesList = scannerData.getNodes();
        final DataBucket bucket = dps.getDataBucket("Live", BucketProperties.SUPPRESS_MEDIATION);
        for (final String nodeName : nodesList) {
            final String nodeFdn = NETWORK_ELEMENT + nodeName;
            final ManagedObject networkElementMo = bucket.findMoByFdn(nodeFdn);
            logger.debug("Found network element to create scanner on {} ", networkElementMo);
            if (networkElementMo != null) {
                scannerData.getScannerAttributes().put(PMIC_SCANNER_INFO_ATTRIBUTE_NODE_NAME, networkElementMo.getName());
                final String subPoid = getSubIdForScanner(bucket, scannerData.getScannerName());
                if (subPoid != null) {
                    scannerData.getScannerAttributes().put(TestConstants.PMIC_SCANNER_INFO_ATTRIBUTE_SUB_POID, subPoid);
                }
                try {
                    final String scannerName = scannerData.getScannerName();
                    if (isUeTracePmicJobInfoScanner(scannerName)) {
                        deletePmicJobInfo(nodeFdn, scannerName);
                        createPmicJobInfo(networkElementMo, scannerName, scannerData.getScannerAttributes());
                    } else {
                        deletePmicScannerInfo(nodeFdn, scannerName);
                        createPmicScannerInfo(networkElementMo, scannerName, scannerData.getScannerAttributes());
                    }
                } catch (final Exception e) {
                    logger.error("Scanner can not be created ", e);
                }

            }
        }
    }

    public void deleScannersFromNode(final PMICScannerData scannerData) {
        final List<String> nodesList = scannerData.getNodes();
        for (final String nodeName : nodesList) {
            final String scannerName = scannerData.getScannerName();
            String scannerFdn = NETWORK_ELEMENT + nodeName + TestConstants.PMIC_SCANNER_INFO_SUFFIX + scannerName;
            if (isUeTracePmicJobInfoScanner(scannerName)) {
                scannerFdn = NETWORK_ELEMENT + nodeName + TestConstants.PMIC_JOB_INFO_SUFFIX + scannerName;
            }
            deleteScannerInfo(scannerFdn);
        }
    }

    public void updateScannerAttributesOnNode(final PMICScannerData scannerData) {
        final List<String> nodesList = scannerData.getNodes();
        final DataBucket bucket = dps.getDataBucket("Live", BucketProperties.SUPPRESS_MEDIATION);
        for (final String nodeName : nodesList) {
            final String scannerName = scannerData.getScannerName();
            String scannerFdn = NETWORK_ELEMENT + nodeName + TestConstants.PMIC_SCANNER_INFO_SUFFIX + scannerName;
            if (isUeTracePmicJobInfoScanner(scannerName)) {
                scannerFdn = NETWORK_ELEMENT + nodeName + TestConstants.PMIC_JOB_INFO_SUFFIX + scannerName;
            }
            final ManagedObject scannerMo = bucket.findMoByFdn(scannerFdn);
            if (scannerMo != null) {
                scannerMo.setAttributes(scannerData.getScannerAttributes());
                logger.debug("ScannerInfo ***  {}  *** updated ", scannerFdn);
            }
        }
    }

    private DataBucket getLiveBucketMediated(final String... optionalParameters) {
        return dps.getDataBucket("Live", optionalParameters);
    }

    private DataBucket getLiveBucketNotMediated() {
        return getLiveBucketMediated(BucketProperties.SUPPRESS_MEDIATION);
    }

    private void createPmicScannerInfo(final ManagedObject parentMo, final String scannerName, final Map<String, Object> attributes) {
        final ManagedObject scannerMO = getLiveBucketNotMediated().getMibRootBuilder().namespace(PMIC_NAMESPACE).type(PMIC_SCANNER_INFO_TYPE)
                .version(PMIC_SCANNER_INFO_VERSION).name(scannerName)
                .addAttributes(attributes)
                .parent(parentMo).create();
        logger.debug("PMICScannerInfo ***  {}  *** created", scannerMO.getFdn());
    }

    private void createPmicJobInfo(final ManagedObject parentMo, final String scannerName, final Map<String, Object> attributes) {
        final ManagedObject scannerMO = getLiveBucketNotMediated().getMibRootBuilder().namespace(PMJOB_MODEL_NAME_SPACE).type(PMJOB_MODEL_NAME)
                .version(PMJOB_MODEL_VERSION).name(scannerName)
                .addAttributes(attributes)
                .parent(parentMo).create();
        logger.debug("PMICJobInfo ***  {}  *** created", scannerMO.getFdn());
    }

    private void deletePmicScannerInfo(final String nodeFdn, final String scannerName) {
        deleteScannerInfo(nodeFdn + TestConstants.PMIC_SCANNER_INFO_SUFFIX + scannerName);
    }

    private void deletePmicJobInfo(final String nodeFdn, final String scannerName) {
        deleteScannerInfo(nodeFdn + TestConstants.PMIC_JOB_INFO_SUFFIX + scannerName);
    }

    private void deleteScannerInfo(final String scannerFdn) {
        final DataBucket bucket = getLiveBucketNotMediated();
        final ManagedObject scannerObject = bucket.findMoByFdn(scannerFdn);
        if (scannerObject != null) {
            logger.debug("Scanner was in DPS, deleting now {}  ", scannerObject);
            bucket.deletePo(scannerObject);
        }
    }

    private String getSubIdForScanner(final DataBucket bucket, final String scannerName) {
        final String subName = getSubNameFromScannerName(scannerName);
        final Query<TypeRestrictionBuilder> queryCriteria = dps.getQueryBuilder().createTypeQuery(PMIC_SUBSCRIPTION_NS, PMIC_MODEL_TYPE_SUBSCRIPTION);
        final TypeRestrictionBuilder builder = queryCriteria.getRestrictionBuilder();
        final Restriction name = builder.equalTo("name", subName);
        queryCriteria.setRestriction(name);
        final QueryExecutor queryExecutor = bucket.getQueryExecutor();

        final Iterator<ManagedObject> subMoIt = queryExecutor.execute(queryCriteria);
        if (subMoIt.hasNext()) {
            final ManagedObject subMo = subMoIt.next();
            if (subMo != null) {
                final String subPoid = Long.toString(subMo.getPoId());
                return subPoid;
            }
        }
        return null;
    }

    private String getSubNameFromScannerName(String scannerName) {
        String subName = "";
        int startIndex = 0;
        if (isPmsUserDefScanner(scannerName)) {
            startIndex = SCANNER_NAME_PREFIX_USERDEF_PMS.length();
            if (scannerName.contains(SCANNER_NAME_PMS_PROFILE_ID)) {
                scannerName = scannerName.substring(0, scannerName.indexOf(SCANNER_NAME_PMS_PROFILE_ID));
            }
        } else if (isEnmUserDefScanner(scannerName)) {
            startIndex = SCANNER_NAME_PREFIX_USERDEF.length();
        }

        final int endIndex = scannerName.endsWith(SCANNER_NAME_POSTFIX_CONT_STATS) ? scannerName.indexOf(SCANNER_NAME_POSTFIX_CONT_STATS)
                : scannerName.length();

        subName = scannerName.substring(startIndex, endIndex);
        return subName;
    }

    private boolean isEnmUserDefScanner(final String scannerName) {
        return scannerName.startsWith(SCANNER_NAME_PREFIX_USERDEF);
    }

    private boolean isPmsUserDefScanner(final String scannerName) {
        return scannerName.startsWith(SCANNER_NAME_PREFIX_USERDEF_PMS);
    }

    private boolean isUeTracePmicJobInfoScanner(final String scannerName) {
        return scannerName.endsWith(PMIC_JOB_TYPE_UETRACE);
    }

}
