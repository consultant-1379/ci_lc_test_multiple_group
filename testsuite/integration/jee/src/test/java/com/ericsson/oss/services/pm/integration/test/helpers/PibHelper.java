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

package com.ericsson.oss.services.pm.integration.test.helpers;


import java.util.HashMap;
import java.util.Map;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.services.test.servicehelper.remote.PibHandler;

@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
public class PibHelper {

    @Inject
    private Logger logger;

    @EJB(lookup = PibHandler.JNDI_NAME)
    private PibHandler pibHandler;

    public void updateParameter(String paramName, String paramValue, Class<?> propertyType) throws Exception {
        logger.debug("UPDATE PIB Param [{}] Type [{}] to New Value [{}]", paramName, paramValue, propertyType);
        final Map<String, String> configuration = new HashMap();
        configuration.put(PibHandler.SERVICE_IDENTIFIER_KEY, "pm-service-jar");
        configuration.put(PibHandler.PARAM_NAME_KEY, paramName);
        configuration.put(PibHandler.PARAM_VALUE_KEY, paramValue);
        pibHandler.updateParameter(configuration);
    }

}
