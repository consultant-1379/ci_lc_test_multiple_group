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

package com.ericsson.oss.services.pm.initiation.subscription.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubscriptionDataReader {

    private final static Logger logger = LoggerFactory.getLogger(SubscriptionDataReader.class);

    private final static String SUBSCRIPTION_DATA_FILE_DIRECTORY = "/subscription_data/";
    private final static String SUBSCRIPTION_DATA_FILE_EXTENSION = ".xlsx";

    private final String subscriptionDataFile;

    public SubscriptionDataReader(final String subscriptionDataFile) {
        this.subscriptionDataFile = SUBSCRIPTION_DATA_FILE_DIRECTORY + subscriptionDataFile + SUBSCRIPTION_DATA_FILE_EXTENSION;
    }

    public List<SubscriptionInfo> getSubscriptionList() {
        final List<SubscriptionInfo> subsData = new ArrayList<>();
        XSSFWorkbook workbook = null;
        try {
            logger.trace("Reading {} subscription data file ", subscriptionDataFile);
            // Get the workbook instance for XLS file
            workbook = new XSSFWorkbook(getClass().getResourceAsStream(subscriptionDataFile));

            // Get first sheet from the workbook
            final XSSFSheet sheet = workbook.getSheetAt(0);

            // Iterate through each rows from first sheet
            final Iterator<Row> rowIterator = sheet.iterator();
            rowIterator.next();

            while (rowIterator.hasNext()) {
                final Row row = rowIterator.next();
                // For each row, iterate through each columns
                final Iterator<Cell> cellIterator = row.cellIterator();
                logger.trace("Reading row {} subscription from data file: {}.", row.getRowNum(), subscriptionDataFile);

                final SubscriptionInfo subsInfo = new SubscriptionInfo();
                if (cellIterator.hasNext()) {
                    subsInfo.addName(cellIterator.next().getStringCellValue()).addDescription(cellIterator.next().getStringCellValue())
                            .addOwner(cellIterator.next().getStringCellValue()).addRopInfo((int) cellIterator.next().getNumericCellValue())
                            .addUserType(cellIterator.next().getStringCellValue()).addJobStatus(cellIterator.next().getStringCellValue())
                            .addSubsType(cellIterator.next().getStringCellValue()).addOpState(cellIterator.next().getStringCellValue())
                            .addAdminState(cellIterator.next().getStringCellValue()).addIsPnP(cellIterator.next().getBooleanCellValue())
                            .addFilterOnME(cellIterator.next().getBooleanCellValue()).addFilterOnMF(cellIterator.next().getBooleanCellValue())
                            .addNodes(cellIterator.next().getStringCellValue()).addMoAndCounter(cellIterator.next().getStringCellValue());
                    if (subsInfo.getSubscriptionType().name().equals("UETR")) {
                        logger.trace("Added additional UETR subscription attributes");
                        subsInfo.addUeInfoList(cellIterator.next().getStringCellValue()).addOutputMode(cellIterator.next().getStringCellValue())
                                .addIpAddress(cellIterator.next().getStringCellValue()).addPort((int) cellIterator.next().getNumericCellValue())
                                .addPortOffset((int) cellIterator.next().getNumericCellValue());
                    }
                    if (subsInfo.isPnP() && cellIterator.hasNext()) {
                        logger.trace("Added additional Criteria Based Subscription attributes");
                        subsInfo.addCbs(cellIterator.next().getBooleanCellValue()).addCriteriaSpecification(cellIterator.next().getStringCellValue());
                    }
                    if (cellIterator.hasNext()) {
                        logger.trace("Added additional celltrace attributes");
                        subsInfo.addOutputMode(cellIterator.next().getStringCellValue())
                                .addUeFraction((int) cellIterator.next().getNumericCellValue())
                                .addIsAsnEnabled(cellIterator.next().getBooleanCellValue()).addIpAddress(cellIterator.next().getStringCellValue())
                                .addPort((int) cellIterator.next().getNumericCellValue());

                    }
                    if (cellIterator.hasNext()) {
                        subsInfo.addStartTimeOffset((int) cellIterator.next().getNumericCellValue());
                        logger.trace("Added startTimeOffset to SubscriptionInfo {}. Data File {}.", subsInfo, subscriptionDataFile);
                        if (cellIterator.hasNext()) {
                            subsInfo.addEndTimeOffset((int) cellIterator.next().getNumericCellValue());
                            logger.trace("Added endTimeOffset to SubscriptionInfo {}. Data File {}.", subsInfo, subscriptionDataFile);
                        }
                    }
                }
                logger.trace("Read row {} and created SubscriptionInfo {}. Data File {}.", row.getRowNum(), subsInfo, subscriptionDataFile);
                subsData.add(subsInfo);
            }

        } catch (final Exception e) {
            logger.error("Subscription data file {} can not be read", subscriptionDataFile, e);
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (final IOException e) {
                }
            }
        }

        logger.debug("Found {} subscriptions for test", subsData.size());
        return subsData;
    }

    public List<SubscriptionInfo> getUeTraceSubscriptionList() {
        final List<SubscriptionInfo> subsData = new ArrayList<>();
        XSSFWorkbook workbook = null;
        try {
            logger.trace("Reading {} subscription data file ", subscriptionDataFile);
            // Get the workbook instance for XLS file
            workbook = new XSSFWorkbook(getClass().getResourceAsStream(subscriptionDataFile));

            // Get first sheet from the workbook
            final XSSFSheet sheet = workbook.getSheetAt(0);

            // Iterate through each rows from first sheet
            final Iterator<Row> rowIterator = sheet.iterator();
            rowIterator.next();

            while (rowIterator.hasNext()) {
                final Row row = rowIterator.next();
                // For each row, iterate through each columns
                final Iterator<Cell> cellIterator = row.cellIterator();
                logger.trace("Reading row {} subscription from data file: {}.", row.getRowNum(), subscriptionDataFile);

                final SubscriptionInfo subsInfo = new SubscriptionInfo();
                if (cellIterator.hasNext()) {
                    subsInfo
                            .addName(cellIterator.next().getStringCellValue())
                            .addDescription(cellIterator.next().getStringCellValue())
                            .addOwner(cellIterator.next().getStringCellValue())
                            .addRopInfo((int) cellIterator.next().getNumericCellValue())
                            .addUserType(cellIterator.next().getStringCellValue())
                            .addSubsType(cellIterator.next().getStringCellValue())
                            .addAdminState(cellIterator.next().getStringCellValue())
                            .addJobStatus(cellIterator.next().getStringCellValue())
                            .addOutputMode(cellIterator.next().getStringCellValue())
                            .addIpAddress(cellIterator.next().getStringCellValue())
                            .addPort((int) cellIterator.next().getNumericCellValue())
                            .addUeInfoType(cellIterator.next().getStringCellValue())
                            .addUeInfoValue((int) cellIterator.next().getNumericCellValue())
                            .addNodeInfoGrouping(cellIterator.next().getStringCellValue())
                            .addTraceDepth(cellIterator.next().getStringCellValue())
                            .addInterfaceType(cellIterator.next().getStringCellValue());
                }
                logger.trace("Read row {} and created SubscriptionInfo {}. Data File {}.", row.getRowNum(), subsInfo, subscriptionDataFile);
                subsData.add(subsInfo);
            }

        } catch (final Exception e) {
            logger.error("Subscription data file {} can not be read", subscriptionDataFile, e);
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (final IOException e) {
                }
            }
        }

        logger.debug("Found {} subscriptions for test", subsData.size());
        return subsData;
    }
}
