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

package com.ericsson.oss.services.pm.collection.schedulers;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.pmic.dto.scanner.enums.ProcessType;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.collection.api.ProcessRequestVO;
import com.ericsson.oss.services.pm.collection.roptime.RopTimeInfo;
import com.ericsson.oss.services.pm.collection.task.factories.FileCollectionTaskRequestFactoryService;
import com.ericsson.oss.services.pm.collection.task.factories.qualifier.FileCollectionTaskRequestFactory;
import com.ericsson.oss.services.pm.initiation.cache.model.value.FileCollectionTaskWrapper;
import com.ericsson.oss.services.pm.initiation.util.RopTime;

/**
 * The type File collection task wrapper factory.
 *
 * @author eushmar This class is responsible for selecting file collection factory instance based on subscriptionType.
 */
@ApplicationScoped
public class FileCollectionTaskWrapperFactory {

    @Inject
    @Any
    protected Instance<FileCollectionTaskRequestFactoryService> fileCollectionTaskRequestFactoryService;

    @Inject
    private Logger logger;

    /**
     * Create file collection task request wrapper file collection task wrapper.
     *
     * @param processRequest
     *         - Process information
     * @param ropTime
     *         An instance of {@link RopTime} that represents the current Record Output Period time
     * @param ropTimeInfo
     *         An instance of {@link RopTimeInfo} that represents the Record Output Period time interval and the delay
     *
     * @return returns {@link FileCollectionTaskWrapper} with task and priority
     */
    public FileCollectionTaskWrapper createFileCollectionTaskRequestWrapper(final ProcessRequestVO processRequest, final RopTime ropTime,
                                                                            final RopTimeInfo ropTimeInfo) {
        final ProcessType processType = ProcessType.fromString(processRequest.getProcessType());

        final FileCollectionTaskRequestAnnotationLiteral selector = new FileCollectionTaskRequestAnnotationLiteral(
                SubscriptionType.valueOf(processType.getSubscriptionType().name()));
        final Instance<FileCollectionTaskRequestFactoryService> selectedInstance = fileCollectionTaskRequestFactoryService.select(selector);
        if (selectedInstance.isUnsatisfied()) {
            logger.error("Process Type: {} and Subscripiton Type: {} is not currently supported", processType, processType.getSubscriptionType());
            throw new UnsupportedOperationException("Process Type: " + processType + " and Subscripiton Type: " + processType.getSubscriptionType()
                    + " is not currently supported");
        }
        final FileCollectionTaskRequestFactoryService fileCollectionTaskRequestService = selectedInstance.get();
        return fileCollectionTaskRequestService.createFileCollectionTaskRequestWrapper(processRequest.getNodeAddress(), ropTime, ropTimeInfo);

    }

    /**
     * The type File collection task request annotation literal.
     */
    class FileCollectionTaskRequestAnnotationLiteral extends AnnotationLiteral<FileCollectionTaskRequestFactory>
            implements FileCollectionTaskRequestFactory {

        public static final long serialVersionUID = 1L;

        public final SubscriptionType typeOfSubscription;

        /**
         * Instantiates a new File collection task request annotation literal.
         *
         * @param subscriptionType
         *         the subscription type
         */
        FileCollectionTaskRequestAnnotationLiteral(final SubscriptionType subscriptionType) {
            this.typeOfSubscription = subscriptionType;
        }

        @Override
        public SubscriptionType subscriptionType() {
            return typeOfSubscription;
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof FileCollectionTaskRequestAnnotationLiteral
                    && ((FileCollectionTaskRequestAnnotationLiteral) other).typeOfSubscription == this.typeOfSubscription;
        }

        @Override
        public int hashCode() {
            return this.typeOfSubscription.hashCode();
        }

    }
}
