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

package com.ericsson.oss.services.pm.initiation.task.factories.deactivation;

import com.ericsson.oss.pmic.dto.subscription.ContinuousCellTraceSubscription;
import com.ericsson.oss.services.pm.initiation.task.factories.deactivation.qualifier.DeactivationTaskRequest;

/**
 * The type ContinuousCelltrace subscription deactivation task request factory.
 */
@DeactivationTaskRequest(subscriptionType = ContinuousCellTraceSubscription.class)
public class CCTRSubscriptionDeactivationTaskRequestFactory extends CellTraceSubscriptionDeactivationTaskRequestFactory {

}
