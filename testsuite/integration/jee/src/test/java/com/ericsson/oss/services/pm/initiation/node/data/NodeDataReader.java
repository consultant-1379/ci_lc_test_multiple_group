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

import static com.ericsson.oss.services.pm.initiation.node.data.NodeCreationConstants.NODE_VERSION_14B;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.ejb.Singleton;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NodeDataReader {

    private final static String SUBSCRIPTION_DATA_FILE_DIRECTORY = "/subscription_data/";
    private final static String SUBSCRIPTION_DATA_FILE_EXTENSION = ".xlsx";

    private final static Logger logger = LoggerFactory.getLogger(NodeDataReader.class);

    public List<NodeData> getNodeData(final String nodeDataFile) {
        return getNodeData(nodeDataFile, 1);
    }

    public List<NodeData> getNodeData(final String nodeDataFile, final int sheetIndex) {
        final List<NodeData> nodeData = new ArrayList<>();
        XSSFWorkbook workbook = null;
        try {

            // Get the workbook instance for XLS file
            workbook = new XSSFWorkbook(getClass().getResourceAsStream(getNodeDataFilesAbsolutePath(nodeDataFile)));

            // Get first sheet from the workbook
            final XSSFSheet sheet = workbook.getSheetAt(sheetIndex);

            // Iterate through each rows from first sheet
            final Iterator<Row> rowIterator = sheet.iterator();
            rowIterator.next();

            while (rowIterator.hasNext()) {
                final Row row = rowIterator.next();
                // For each row, iterate through each columns
                final Iterator<Cell> cellIterator = row.cellIterator();

                String nodeName = null;
                String ipAddress = null;

                if (cellIterator.hasNext()) {
                    final Cell nodeNameCell = cellIterator.next();
                    nodeName = nodeNameCell.getStringCellValue();
                }

                if (cellIterator.hasNext()) {
                    final Cell ipAddressCell = cellIterator.next();
                    ipAddress = ipAddressCell.getStringCellValue();
                }

                String platformType = NodeCreationConstants.PLATFORM_TYPE_VALUE;
                String neType = NodeCreationConstants.NE_TYPE_VALUE;

                if (cellIterator.hasNext()) {
                    final Cell platformTypeCell = cellIterator.next();
                    platformType = platformTypeCell.getStringCellValue();
                }

                if (cellIterator.hasNext()) {
                    final Cell neTypeCell = cellIterator.next();
                    neType = neTypeCell.getStringCellValue();
                }

                if (cellIterator.hasNext()) {
                    final Cell ossModelIdentityCell = cellIterator.next();
                    final String ossModelIdentity = ossModelIdentityCell.getStringCellValue();
                    nodeData.add(new NodeData(nodeName.trim(), ipAddress.trim(), platformType.trim(), neType.trim(), ossModelIdentity.trim()));
                } else if (hasAllFields(nodeName, ipAddress, platformType, neType)) {
                    nodeData.add(new NodeData(nodeName.trim(), ipAddress.trim(), platformType.trim(), neType.trim(), NODE_VERSION_14B));
                }
            }

        } catch (final Exception e) {
            logger.error("Error reading node data", e);
        } finally {
            try {
                if (workbook != null) {
                    workbook.close();
                }
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
        return nodeData;
    }

    private boolean hasAllFields(final String nodeName, final String ipAddress, final String platformType, final String neType) {
        return nodeName != null && ipAddress != null && platformType != null && neType != null;
    }

    public String getNodeDataFilesAbsolutePath(final String fileName) {
        return SUBSCRIPTION_DATA_FILE_DIRECTORY + fileName + SUBSCRIPTION_DATA_FILE_EXTENSION;
    }
}
