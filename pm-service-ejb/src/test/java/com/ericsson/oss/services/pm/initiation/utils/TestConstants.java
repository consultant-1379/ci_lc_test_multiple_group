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

package com.ericsson.oss.services.pm.initiation.utils;

import java.io.File;

import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.services.fls.constants.DataType;

public final class TestConstants {
    public static final String TARGET_DIR = "target" + File.separator;

    public static final String STATS_FILE_COLLECTION_DIR = TARGET_DIR + "XML" + File.separator;
    public static final String STATS_SYMLINK_DIRECTORY = TARGET_DIR + "symlinks" + File.separator;

    public static final String STATS_FILE_COLLECTION_APG_DIR = TARGET_DIR + "asn1" + File.separator;

    public static final String DIR_1MIN = "1MIN";
    public static final String DIR_5MIN = "5MIN";
    public static final String DIR_30MIN = "30MIN";
    public static final String DIR_1HOUR = "1HOUR";
    public static final String DIR_12HOUR = "12HOUR";
    public static final String DIR_24HOUR = "24HOUR";

    public static final String STATS_FILE_COLLECTION_DIR_1MIN = STATS_FILE_COLLECTION_DIR + DIR_1MIN + File.separator;
    public static final String STATS_FILE_COLLECTION_DIR_5MIN = STATS_FILE_COLLECTION_DIR + DIR_5MIN + File.separator;
    public static final String STATS_FILE_COLLECTION_DIR_15MIN = STATS_FILE_COLLECTION_DIR;
    public static final String STATS_FILE_COLLECTION_DIR_30MIN = STATS_FILE_COLLECTION_DIR + DIR_30MIN + File.separator;
    public static final String STATS_FILE_COLLECTION_DIR_1HOUR = STATS_FILE_COLLECTION_DIR + DIR_1HOUR + File.separator;
    public static final String STATS_FILE_COLLECTION_DIR_12HOUR = STATS_FILE_COLLECTION_DIR + DIR_12HOUR + File.separator;
    public static final String STATS_FILE_COLLECTION_DIR_24HOUR = STATS_FILE_COLLECTION_DIR + DIR_24HOUR + File.separator;

    public static final String STATS_SYMLINK_DIR_1MIN = STATS_SYMLINK_DIRECTORY + DIR_1MIN + File.separator;
    public static final String STATS_SYMLINK_DIR_5MIN = STATS_SYMLINK_DIRECTORY + DIR_5MIN + File.separator;
    public static final String STATS_SYMLINK_DIR_15MIN = STATS_SYMLINK_DIRECTORY;
    public static final String STATS_SYMLINK_DIR_30MIN = STATS_SYMLINK_DIRECTORY + DIR_30MIN + File.separator;
    public static final String STATS_SYMLINK_DIR_1HOUR = STATS_SYMLINK_DIRECTORY + DIR_1HOUR + File.separator;
    public static final String STATS_SYMLINK_DIR_12HOUR = STATS_SYMLINK_DIRECTORY + DIR_12HOUR + File.separator;
    public static final String STATS_SYMLINK_DIR_24HOUR = STATS_SYMLINK_DIRECTORY + DIR_24HOUR + File.separator;

    public static final String CELLTRACE_FILE_COLLECTION_DIR = TARGET_DIR + "CELLTRACE/";

    public static final String EBM_FILE_COLLECTION_DIR = TARGET_DIR + "ebm/data/";

    public static final String BSCRECORDINGS_FILE_COLLECTION_DIR = TARGET_DIR + "BscRecordings/";

    public static final String CTUM_FILE_COLLECTION_DIR = TARGET_DIR + "CTUM/";

    public static final String UE_TRACE_FILE_COLLECTION_DIR = TARGET_DIR + "UETRACE/";

    public static final String CELLTRAFFIC_FILE_COLLECTION_DIR = TARGET_DIR + "CTR/";

    public static final String GPEH_FILE_COLLECTION_DIR = TARGET_DIR + "GPEH/";

    public static final String UETR_FILE_COLLECTION_DIR = TARGET_DIR + "UETR/";

    public static final String FIFTEEN_MINUTE_AND_ABOVE_TIMER = "FIFTEEN_MINUTE_AND_ABOVE_TIMER";
    public static final String ONE_MINUTE_TIMER = "ONE_MINUTE_TIMER";
    public static final String FIVE_MIN_TIMER = "FIVE_MIN_TIMER";

    public static final String SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER = "SYMLINK_FIVE_MINUTE_AND_ABOVE_TIMER";
    public static final String SYMLINK_ONE_MINUTE_TIMER = "SYMLINK_ONE_MINUTE_TIMER";

    public static final long STATS_1MIN_ROP = RopPeriod.ONE_MIN.getDurationInSeconds();
    public static final long STATS_5MIN_ROP = RopPeriod.FIVE_MIN.getDurationInSeconds();
    public static final long STATS_15MIN_ROP = RopPeriod.FIFTEEN_MIN.getDurationInSeconds();
    public static final long STATS_30MIN_ROP = RopPeriod.THIRTY_MIN.getDurationInSeconds();
    public static final long STATS_1HOUR_ROP = RopPeriod.ONE_HOUR.getDurationInSeconds();
    public static final long STATS_12HOUR_ROP = RopPeriod.TWELVE_HOUR.getDurationInSeconds();
    public static final long STATS_24HOUR_ROP = RopPeriod.ONE_DAY.getDurationInSeconds();

    public static final DataType DATATYPE_STATISTICAL = DataType.valueOf("STATISTICAL");
    public static final DataType DATATYPE_STATISTICAL_1MIN = DataType.valueOf("STATISTICAL_1MIN");
    public static final DataType DATATYPE_STATISTICAL_5MIN = DataType.valueOf("STATISTICAL_5MIN");
    public static final DataType DATATYPE_STATISTICAL_30MIN = DataType.valueOf("STATISTICAL_30MIN");
    public static final DataType DATATYPE_STATISTICAL_1HOUR = DataType.valueOf("STATISTICAL_1HOUR");
    public static final DataType DATATYPE_STATISTICAL_12HOUR = DataType.valueOf("STATISTICAL_12HOUR");
    public static final DataType DATATYPE_STATISTICAL_24HOUR = DataType.valueOf("STATISTICAL_24HOUR");

    private TestConstants() {
        // private constructor, utility class should not be instantiated.
    }

}
