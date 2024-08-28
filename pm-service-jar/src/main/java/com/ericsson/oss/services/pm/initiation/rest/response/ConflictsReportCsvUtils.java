/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2015
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.services.pm.initiation.rest.response;

import java.util.List;
import java.util.Set;

/**
 * Utils to generate Counter conflicts Report csv File
 */
public class ConflictsReportCsvUtils {

    static final String CSV_DELIMITER = ",";
    static final String CSV_SEPARATOR = ";";
    static final String CSV_CR = "\n";
    static final String CSV_BLOCK_SEPARATOR = "\n\n\n";

    private String getHeader(final String subscriptionName) {
        return "Report generated for inactive subscription:" + CSV_CR + subscriptionName;
    }

    private String getListTitle() {
        return "Conflicting Subscriptions:" + CSV_CR;
    }

    private String getSubscriptionList(final Set<String> subscriptionList) {
        final StringBuilder result = new StringBuilder(subscriptionList.size());
        for (final String name : subscriptionList) {
            result.append(name + CSV_CR);
        }
        return result.toString();
    }

    private String getTableHeaders() {
        return "Conflicting Subscription" + CSV_DELIMITER + "Conflicting nodes (node1;node2...)" + CSV_DELIMITER + "Conflicting group" + CSV_DELIMITER
                + "Conflicting counters (counter1;counter2...)" + CSV_CR;
    }

    /**
     * Adds a csv formatted block in the conflicts Report table consisting of Conflicting Subscription Name, conflicting nodes group, conflicting
     * counter group, conflicting counters (separated by semicolon)
     *
     * @param conflictsTable
     *         - The table StringBuilder
     * @param conflictingSubscriptionId
     *         - The subscription name
     * @param conflictingNodes
     *         - the conflicting nodes
     * @param conflictingCounters
     *         - the conflicting counters
     */
    public void addTableEntry(final StringBuilder conflictsTable, final String conflictingSubscriptionId, final List<String> conflictingNodes,
                              final List<String> conflictingCounters) {
        conflictsTable.append(conflictingSubscriptionId + CSV_DELIMITER);
        for (int i = 0; i < conflictingNodes.size() - 1; i++) {
            conflictsTable.append(conflictingNodes.get(i).substring(15) + CSV_SEPARATOR);
        }
        conflictsTable.append(conflictingNodes.get(conflictingNodes.size() - 1).substring(15) + CSV_DELIMITER);
        for (final String countersString : conflictingCounters) {
            final String[] counters = countersString.split(":");
            conflictsTable
                    .append(counters[0] + CSV_DELIMITER + counters[1].replace(CSV_DELIMITER, CSV_SEPARATOR) + CSV_CR + CSV_DELIMITER + CSV_DELIMITER);
        }
        conflictsTable.append(CSV_CR);

    }

    /**
     * @param subscriptionName
     *         - The subscription name
     * @param subscriptionList
     *         - The List StringBuilder
     * @param table
     *         - The table StringBuilder
     *
     * @return - The generated report String
     */
    public String getReport(final String subscriptionName, final Set<String> subscriptionList, final StringBuilder table) {
        return getHeader(subscriptionName) + CSV_BLOCK_SEPARATOR + getListTitle() + getSubscriptionList(subscriptionList) + CSV_BLOCK_SEPARATOR
                + getTableHeaders() + table.toString();

    }

}
