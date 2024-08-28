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
package com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler;

import static com.ericsson.oss.services.pm.initiation.task.factories.errornodehandler.ErrorNodeCacheAttributes.FDN;

import java.util.Map;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.collection.cache.PmFunctionOffErrorNodeCache;
import com.ericsson.oss.services.pm.collection.cache.StartupRecoveryMonitorLocal;
import com.ericsson.oss.services.pm.initiation.task.qualifier.ErrorHandler;

/**
 * This class chooses which implementation of the @ScanerErrorHandler interface to invoke based on
 * the process type of the received scanner notification
 */
@ApplicationScoped
public class GenericScannerErrorHandler implements ScannerErrorHandler {

    @Inject
    private Logger logger;

    @SuppressWarnings("PMD.UnusedPrivateField")
    @Inject
    @Any
    private Instance<ScannerErrorHandler> scannerErrorHandler;

    @Inject
    private PmFunctionOffErrorNodeCache pmFunctionOffErrorNodeCache;

    @Inject
    private StartupRecoveryMonitorLocal startupRecoveryMonitor;

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public boolean process(final ErrorNodeCacheProcessType processType, final Map<String, Object> attributes) {
        final String fdn = (String) attributes.get(FDN);
        if (startupRecoveryMonitor.isStartupRecoveryDone()) {
            final ErrorHandlerAnnotationLiteral selector = new ErrorHandlerAnnotationLiteral(processType);
            final Instance<ScannerErrorHandler> selectedInstance = scannerErrorHandler.select(selector);
            if (selectedInstance.isUnsatisfied()) {
                logger.info("Process Type: {} from fdn : {} is not currently supported. Resolution cannot continue", processType, fdn);
                return false;
            }
            final ScannerErrorHandler scannerErrorHandlerInstance = selectedInstance.get();
            return scannerErrorHandlerInstance.process(processType, attributes);
        }
        logger.info("Startup recovery is not yet finished. Caching task for {} until it's finished", fdn);
        pmFunctionOffErrorNodeCache.storeRequest(processType, attributes);
        return true;
    }

    /**
     * This class is used to build an @AnnotationLiteral for use in selecting a @ScanerErrorHandler instance
     */
    @SuppressWarnings("all")
    class ErrorHandlerAnnotationLiteral extends AnnotationLiteral<ErrorHandler>
            implements ErrorHandler {
        private static final long serialVersionUID = 5370297097468178066L;
        private final ErrorNodeCacheProcessType processType;

        /**
         * Create an instance  of the annotation with the given process type
         *
         * @param processType
         *         - Example STATISTICAL or CELLTRACE
         */
        ErrorHandlerAnnotationLiteral(final ErrorNodeCacheProcessType processType) {
            this.processType = processType;
        }

        @Override
        public ErrorNodeCacheProcessType processType() {
            return processType;
        }
    }
}