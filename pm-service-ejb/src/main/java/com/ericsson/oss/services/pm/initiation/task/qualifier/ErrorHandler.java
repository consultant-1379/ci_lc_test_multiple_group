/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.initiation.task.qualifier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.inject.Qualifier;

import com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler.ErrorNodeCacheProcessType;

/**
 * This annotation is used by instances of the @ScannerErrorHandler interface.
 * The parameter of the annotation tells @ScannerErrorHandler which instance
 * to choose for a given process type
 */
@Qualifier
@Retention(RUNTIME)
@Target({TYPE, FIELD, PARAMETER})
public @interface ErrorHandler {
    /**
     * Specify the process type that allows @GenericScannerErrorHandler to choose between @ScannerErrorHandler instances
     *
     * @return - The ProcessType parameter of the class's annotation
     */
    ErrorNodeCacheProcessType processType() default ErrorNodeCacheProcessType.DEFAULT;
}
