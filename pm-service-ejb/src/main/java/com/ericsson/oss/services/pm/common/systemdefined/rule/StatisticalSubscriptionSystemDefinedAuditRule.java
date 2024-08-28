/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.common.systemdefined.rule;

import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.STATISTICAL_SUBSCRIPTIONATTRIBUTES;
import static com.ericsson.oss.services.pm.common.constants.PmCapabilityModelConstants.SYSTEM_DEFINED_STATISTICAL_SUBSCRIPTION_NAME;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.ericsson.oss.pmic.api.modelservice.PmCapabilityReader;
import com.ericsson.oss.pmic.dto.NodeTypeAndVersion;
import com.ericsson.oss.pmic.dto.node.Node;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.CounterInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.pmic.dto.subscription.enums.UserType;
import com.ericsson.oss.services.pm.initiation.model.metadata.counters.PmCountersLookUp;
import com.ericsson.oss.services.pm.initiation.util.constants.ProcessTypeConstant;
import com.ericsson.oss.services.pm.initiation.validators.annotation.SubscriptionValidatorQualifier;
import com.ericsson.oss.services.pm.modeling.schema.gen.pfm_measurement.ScannerType;
import com.ericsson.oss.services.pm.services.exception.PfmDataException;
import com.ericsson.services.pm.initiation.restful.api.CounterTableRow;

/**
 * This class applys audit rules Statistical Subscription
 */
@ApplicationScoped
@SubscriptionValidatorQualifier(subscriptionType = SubscriptionType.STATISTICAL)
public class StatisticalSubscriptionSystemDefinedAuditRule extends ResourceSubscriptionSystemDefinedAuditRule<StatisticalSubscription> {

    @Inject
    private PmCountersLookUp pmCountersLookUp;
    @Inject
    private PmCapabilityReader pmCapabilityReader;

    @Override
    public void applyRuleOnUpdate(final List<Node> nodes, final StatisticalSubscription subscription) {
        super.applyRuleOnUpdate(nodes, subscription);
        if (subscription.getUserType() != UserType.SYSTEM_DEF ||
            subscription.getName().contains("EPG") || subscription.getName().contains("BSC")) {
            return;
        }
        logger.trace("{} is a system defined statistical subscription, updating predef counters ", subscription.getName());
        subscription.setCounters(getListOfPredefStatisticsCounters(nodes, subscription.getName()));
    }

    @Override
    public void applyRuleOnCreate(final List<Node> nodes, final StatisticalSubscription subscription) {
        super.applyRuleOnCreate(nodes, subscription);
        logger.trace("{} is a system defined statistical subscription, updating predef counters ", subscription.getName());
        subscription.setCounters(getListOfPredefStatisticsCounters(nodes, subscription.getName()));
    }

    /**
     * Returns the list of the predefined statistics counters for a list of nodes
     *
     * @param nodes
     *     - list of nodes
     * @param subscriptionName
     *     - the name of the subscription
     *
     * @return - returns a list of supported counters converted from the collection of counters
     */
    private List<CounterInfo> getListOfPredefStatisticsCounters(final List<Node> nodes, final String subscriptionName) {

        if (nodes.isEmpty()) {
            logger.trace("Empty node list received, returning counter empty list");
            return Collections.emptyList();
        }

        // Using a Set in order to avoid duplicate counters
        logger.debug("Get Supported counters for {}", nodes);
        final String predefScannerName = findThePredefScannerFromSubscriptionName(subscriptionName);
        final Set<NodeTypeAndVersion> mimVersions = new HashSet<>();
        nodes.forEach(node -> mimVersions.add(new NodeTypeAndVersion(node.getNeType(), node.getOssModelIdentity(), node.getTechnologyDomain())));
        final Collection<CounterTableRow> counterTableRows = getCounterTableRowForNodeVersions(mimVersions);

        final Set<CounterInfo> counters = new HashSet<>();
        counterTableRows.forEach(counterTableRow -> {
            if (isPredefinedCounter(counterTableRow, predefScannerName)) {
                counters.add(new CounterInfo(counterTableRow.getCounterName(), counterTableRow.getSourceObject()));
            }
        });
        return new ArrayList<>(counters);
    }

    /**
     * Searches for the predefined scanner associated to a System Defined Statistical Subscription.
     * Subscription name shall be compliant to the following format: "(NodeType) (Predef_scanner) (System Defined Statistical Subscription)" where
     * (Predef_scanner) is optional. If not present the scanner name will be retrieved from DPS. When present the search on DPS will be restricted to
     * the scanner name Results for the following node types: - ERBS,RBS,RadioNode: empty string as the subscription name does not contain the predef
     * scanner - RNC: has predef scanner=PREDEF.PRIMARY.STATS and predef scanner=PREDEF.SECONDARY.STATS
     *
     * @param subscriptionName
     *     the subscription name
     *
     * @return empty string (ERBS,RBS,RadioNode) or the name of the predefined scanner (RNC)
     */
    private String findThePredefScannerFromSubscriptionName(final String subscriptionName) {
        String result = "";
        final StringTokenizer stringTokenizer = new StringTokenizer(subscriptionName, " ");
        final int numCount = stringTokenizer.countTokens();
        int count = numCount;

        while (stringTokenizer.hasMoreElements()) {
            final String elem = stringTokenizer.nextElement().toString();
            if (count == numCount - 1) {
                if (!SYSTEM_DEFINED_STATISTICAL_SUBSCRIPTION_NAME.contains(elem)) {
                    result = ProcessTypeConstant.SCANNER_NAME_PREFIX_PREDEF + elem.toUpperCase() + "." + ProcessTypeConstant.PMIC_SCANNER_TYPE_STATS;
                }
                break;
            }
            count--;
        }
        return result;
    }

    /**
     * Returns a collection of CounterTableRow with the predefined statistics counters applicable to the node
     *
     * @param nodeTypeAndVersions
     *     -  collection of  NodeTypeAndVersion
     *
     * @return - returns a collection of all supported statistic counters for the node
     */
    private Collection<CounterTableRow> getCounterTableRowForNodeVersions(final Set<NodeTypeAndVersion> nodeTypeAndVersions) {

        Set<CounterTableRow> counters = new TreeSet<>();
        try {
            counters = pmCountersLookUp.getCountersForAllVersions(nodeTypeAndVersions,
                pmCapabilityReader.getSupportedModelDefinersForCounters(STATISTICAL_SUBSCRIPTIONATTRIBUTES),
                pmCapabilityReader.shouldSupportExternalCounterName(STATISTICAL_SUBSCRIPTIONATTRIBUTES, nodeTypeAndVersions));
        } catch (final PfmDataException e) {
            logger.warn("No counters were found for the supplied mim versions {}", nodeTypeAndVersions);
            logger.debug("Exception details {} ", e);
        }
        return counters;
    }

    /**
     * Check if the counter is a predefined one Selection criteria applicable to all node types is that scannerType is matching with PRIMARY or
     * SECONDARY string from the scanner name.
     *
     * @param row
     *     - the data associated to the counter
     * @param predefScannerName
     *     - the name of the predefined scanner to be used to select the counter if matching the search criteria
     *
     * @return - returns true if it's predefined according to the matching criteria
     */
    private boolean isPredefinedCounter(final CounterTableRow row, final String predefScannerName) {
        // To be further enhanced: the Predef Scanner name could be retrieved and associated looking at the oss_capabilitysupport model
        if ("".equals(predefScannerName)) {
            return row.getScannerType().equals(ScannerType.PRIMARY);
        } else if (predefScannerName.contains(ScannerType.PRIMARY.value())) {
            return row.getScannerType().equals(ScannerType.PRIMARY);
        } else if (predefScannerName.contains(ScannerType.SECONDARY.value())) {
            return row.getScannerType().equals(ScannerType.SECONDARY);
        }
        return false;
    }

}
