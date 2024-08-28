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

package com.ericsson.oss.services.pm.initiation.ejb;

import static com.ericsson.oss.services.pm.initiation.constants.ApplicationMessages.PMIC_PARAMETER_ERROR;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.ebs.utils.EbsConfigurationListener;
import com.ericsson.oss.services.pm.initiation.api.ReadPMICConfigurationLocal;
import com.ericsson.oss.services.pm.initiation.config.listener.CBSConfigurationChangeListener;
import com.ericsson.oss.services.pm.initiation.config.listener.MoInstanceConfigurationChangeListener;
import com.ericsson.oss.services.pm.services.exception.ConfigurationParameterException;

/**
 * Class for Reading pmic configuration.
 */
@Stateless
@TransactionManagement(TransactionManagementType.CONTAINER)
@Local(ReadPMICConfigurationLocal.class)
public class ReadPMICConfigurationImpl implements ReadPMICConfigurationLocal {

    @Inject
    private CBSConfigurationChangeListener cbsConfigurationChangeListener;

    @Inject
    private EbsConfigurationListener ebsConfigurationListener;

    @Inject
    private MoInstanceConfigurationChangeListener moInstanceConfigurationChangeListener;

    @Inject
    private Logger logger;

    /*
     * (non-Javadoc)
     * @see com.ericsson.oss.services.pm.initiation.api.ReadPMICConfigurationLocal #getConfigParamValue(java.lang.String)
     */
    @Override
    public String getConfigParamValue(final String pibParam) throws ConfigurationParameterException {
        try {
            final PMICConfigParameter configParameter = PMICConfigParameter.fromValue(pibParam);
            switch (configParameter) {
                case CBSENABLED:
                    return String.valueOf(cbsConfigurationChangeListener.getCbsEnabled());
                case MAXNOOFCBSALLOWED:
                    return String.valueOf(cbsConfigurationChangeListener.getMaxNoOfCBSAllowed());
                case PMICEBSMENABLED:
                    return String.valueOf(ebsConfigurationListener.isEbsmEnabled());
                case PMICEBSSTREAMCLUSTERDEPLOYED:
                    return String.valueOf(ebsConfigurationListener.isEbsStreamClusterDeployed());
                case PMICEBSLROPINMINUTES:
                    return String.valueOf(ebsConfigurationListener.getPmicEbslRopInMinutes());
                case MAXNOOFMOINSTANCEALLOWED:
                    return String.valueOf(moInstanceConfigurationChangeListener.getMaxNoOfMOInstanceAllowed());
                default:
                    break;
            }
        } catch (final IllegalArgumentException exception) {
            logger.error("Invalid pibParam: {}", pibParam);
        }
        throw new ConfigurationParameterException(String.format(PMIC_PARAMETER_ERROR, pibParam));
    }
}
