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

package com.ericsson.oss.services.pm.ebs.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.api.selector.annotation.Selector;
import com.ericsson.oss.pmic.dto.subscription.enums.CellTraceCategory;
import com.ericsson.oss.services.pm.initiation.task.factories.auditor.CellTraceSubscriptionHelper;

import com.ericsson.oss.itpf.sdk.resources.Resource;
import com.ericsson.oss.itpf.sdk.resources.Resources;
import com.ericsson.oss.pmic.dto.subscription.cdts.StreamInfo;

/**
 * This Class gets Stream ipV4/ipv6 VIP information for NBI Streaming App's Subscription.
 */
@ApplicationScoped
public class EbsStreamInfoResolver {

    public static final String GLOBAL_PROP_ABSOLUTE_FILE_PATH = "GLOBAL_PROP_ABSOLUTE_FILE_PATH";

    private static final List<String> STR_VIPs = Arrays.asList(
            "str_STR_vip_ipaddress", "str_STR_vip_ipaddress_2", "str_STR_vip_ipaddress_3",
            "str_STR_vip_ipv6address", "str_STR_vip_ipv6address_2", "str_STR_vip_ipv6address_3");
    private static final List<String> RPMO_IPS = Arrays.asList("eba_EBA_vip_ipaddress");
    private static final int DEFAULT_STR_PORT = 10101;
    private static final int DEFAULT_EBSN_STR_PORT = 10102;

    @Inject
    @Selector(filter = "CellTraceSubscriptionHelper")
    private CellTraceSubscriptionHelper cellTraceSubscriptionHelper;

    @Inject
    private Logger logger;

    private static String stripSubNetMask(final String vipAddress) {
        if (vipAddress.contains("/")) {
            return vipAddress.substring(0, vipAddress.indexOf('/'));
        }
        return vipAddress;
    }

    /**
     * The default global.properties file is /ericsson/tor/data/global.properties.
     * The global.properties location can be overwritten with the system parameter
     */
    private static String getPropertiesFilePath() {
        final String fileLocation = System.getProperty(GLOBAL_PROP_ABSOLUTE_FILE_PATH);
        return fileLocation == null ? getDefaultPath() : fileLocation.trim();
    }

    private static String getDefaultPath() {
        final StringBuilder propsPath = new StringBuilder();
        propsPath.append("/ericsson").append("/tor").append("/data").append("/global.properties");
        return propsPath.toString();
    }

    /**
     * The default Streaming port is 10101. The port can be overwritten with the system parameter
     */
    private static int getStreamingPort() {
        final String strPort = System.getProperty("STR_PORT");
        return strPort == null ? DEFAULT_STR_PORT : Integer.parseInt(strPort);
    }

    /**
     * The default Streaming port is 10102. The port can be overwritten with the system parameter
     */
    private static int getEbsnStreamingPort() {
        final String strPort = System.getProperty("EBSN_STR_PORT");
        return strPort == null ? DEFAULT_EBSN_STR_PORT : Integer.parseInt(strPort);
    }

    /**
     * Return the Streaming Destination for Streaming Subscription.
     *
     * @return List of Stream Info
     */
    public List<StreamInfo> getStreamingDestination(final boolean... isRpmo) {
        return getStreamInfos(getStreamingPort(), isRpmo);
    }

    /**
     * Return the Streaming Destination for Streaming Subscription.
     *  Port is selected based on category of cell trace subscription
     *
     * @return List of Stream Info
     */
    public List<StreamInfo> getStreamingDestination(final CellTraceCategory cellTraceCategory) {
        final int port = cellTraceSubscriptionHelper.isCellTraceNran(cellTraceCategory) ? getEbsnStreamingPort() : getStreamingPort();
        return getStreamInfos(port);
    }

    private List<StreamInfo> getStreamInfos(final int port, final boolean ... isRpmo) {
        final List<StreamInfo> nbiStreamInfoList = new ArrayList<>();
        final Properties properties = loadPropertiesFromFile();
        logger.debug("Properties read from file {}.", properties);
        final List<String> ipList = (isRpmo.length > 0 && isRpmo[0]) ? RPMO_IPS : STR_VIPs;
        for (final String vipName : ipList) {
            final String vipAddress = properties.getProperty(vipName);
            if (vipAddress != null) {
                nbiStreamInfoList.add(new StreamInfo(stripSubNetMask(vipAddress), port));
            }
        }
        return nbiStreamInfoList;
    }

    private Properties loadPropertiesFromFile() {
        final Properties strProperties = new Properties();
        InputStream inputStream = null;
        logger.debug("Reading Properties from {}.", getPropertiesFilePath());
        final Resource resource = Resources.getFileSystemResource(getPropertiesFilePath());
        if (resource.exists()) {
            inputStream = resource.getInputStream();
        }
        if (inputStream != null) {
            try {
                strProperties.load(inputStream);
            } catch (final IOException e) {
                logger.error("Exception in loading parameters from input stream {}", e);
            }
            Resources.safeClose(inputStream);
        }
        return strProperties;
    }
}
