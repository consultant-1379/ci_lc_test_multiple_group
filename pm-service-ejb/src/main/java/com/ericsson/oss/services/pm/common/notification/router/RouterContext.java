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

package com.ericsson.oss.services.pm.common.notification.router;

import javax.enterprise.inject.Alternative;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * The Router context.
 */
@Alternative
public class RouterContext extends InitialContext {

    /**
     * Instantiates a new Router context.
     *
     * @throws NamingException
     *         thrown name is invalid
     */
    public RouterContext() throws NamingException {

        //currently no implementation
    }
}
