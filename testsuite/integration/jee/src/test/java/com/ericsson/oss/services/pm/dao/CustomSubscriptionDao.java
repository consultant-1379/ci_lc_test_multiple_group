/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.dao;

import java.util.HashMap;
import java.util.Map;
import javax.ejb.EJB;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.inject.Alternative;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.datalayer.dps.DataBucket;
import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService;
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject;
import com.ericsson.oss.pmic.dao.versant.SubscriptionDaoImpl;
import com.ericsson.oss.pmic.dao.versant.mapper.qualifier.DpsExceptionsQualifier;
import com.ericsson.oss.pmic.dto.subscription.StatisticalSubscription;
import com.ericsson.oss.pmic.dto.subscription.Subscription;
import com.ericsson.oss.pmic.dto.subscription.cdts.ScheduleInfo;
import com.ericsson.oss.pmic.dto.subscription.enums.AdministrationState;
import com.ericsson.oss.pmic.dto.subscription.enums.RopPeriod;
import com.ericsson.oss.services.pm.exception.DataAccessException;

@Alternative
@DpsExceptionsQualifier
public class CustomSubscriptionDao extends SubscriptionDaoImpl {

    @EJB(lookup = DataPersistenceService.JNDI_LOOKUP_NAME)
    private DataPersistenceService dataPersistenceService;

    @Inject
    private Logger logger;

    @Inject
    private Instance<CustomSubscriptionDao> self;

    /**
     * This method will force DPS to throw a DpsApplicationException if subscriptionName = ThrowDpsException
     *
     * @param subscriptionName
     * @param loadAssociations
     *
     * @return
     * @throws IllegalArgumentException
     * @throws DataAccessException
     */
    @Override
    public Subscription findOneByExactName(final String subscriptionName, final boolean loadAssociations)
            throws IllegalArgumentException, DataAccessException {
        if (subscriptionName.equals("ThrowDpsException")) {
            dataPersistenceService.getQueryBuilder().createTypeQuery("invalid Namespace", "invalid Type");
        }
        return super.findOneByExactName(subscriptionName, loadAssociations); // exception will be thrown from line above.
    }

    /**
     * This method is allowing you to Whitebox dataPersistenceService.getLiveBucket()
     *
     * @param subscriptionId
     *         - subID
     * @param loadAssociations
     *         - loadAssociations
     *
     * @return
     * @throws IllegalArgumentException
     * @throws DataAccessException
     */
    @Override
    public Subscription findOneById(final Long subscriptionId, final boolean loadAssociations) throws IllegalArgumentException, DataAccessException {
        dataPersistenceService.getLiveBucket();
        return super.findOneById(subscriptionId, loadAssociations);
    }

    /**
     * Will force DPS to rollback the transaction if fdn = ThrowDpsException
     *
     * @param fdn
     *
     * @return
     * @throws IllegalArgumentException
     * @throws DataAccessException
     */
    @Override
    public boolean existsByFdn(final String fdn) throws IllegalArgumentException, DataAccessException {
        if (fdn.equals("ThrowDpsException")) {
            final DataBucket liveDataBucket = dataPersistenceService.getLiveBucket();
            final ManagedObject subMo = liveDataBucket
                    .findMoByFdn(StatisticalSubscription.STATISTICAL_SUBSCRIPTION_MODEL_TYPE + "=ThrowDpsException");
            if (subMo != null) {
                liveDataBucket.deletePo(subMo);
            }
            final Long poid = self.get().saveStatisticalSubscriptionInDPS();
            self.get().updateAdminStateToActivating(poid);
        }
        return super.existsByFdn(fdn);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Long saveStatisticalSubscriptionInDPS() {
        final DataBucket liveDataBucket = dataPersistenceService.getLiveBucket();
        final Map<String, Object> attributes = new HashMap<>();
        final Map<String, Object> scheduleInfo = new HashMap<>();
        scheduleInfo.put(ScheduleInfo.ScheduleInfo100Attributes.startDateTime.name(), null);
        scheduleInfo.put(ScheduleInfo.ScheduleInfo100Attributes.endDateTime.name(), null);
        attributes.put(Subscription.Subscription220Attribute.rop.name(), RopPeriod.FIFTEEN_MIN.name());
        attributes.put(Subscription.Subscription220Attribute.scheduleInfo.name(), scheduleInfo);

        final ManagedObject managedObject = liveDataBucket.getMibRootBuilder()
                .namespace(StatisticalSubscription.STATISTICAL_SUBSCRIPTION_MODEL_NAMESPACE)
                .type(StatisticalSubscription.STATISTICAL_SUBSCRIPTION_MODEL_TYPE)
                .version(StatisticalSubscription.STATISTICAL_SUBSCRIPTION_MODEL_VERSION).name("ThrowDpsException").addAttributes(attributes)
                .parent(null).create();
        return managedObject.getPoId();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateAdminStateToActivating(final Long poid) {
        final DataBucket liveDataBucket = dataPersistenceService.getLiveBucket();
        ManagedObject managedObject = (ManagedObject) liveDataBucket.findPoById(poid);
        self.get().updateAdminStateToDeactivating(poid);
        managedObject.setAttribute(Subscription.Subscription220Attribute.administrationState.name(), AdministrationState.ACTIVATING.name());
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateAdminStateToDeactivating(final Long poid) {
        final DataBucket liveDataBucket = dataPersistenceService.getLiveBucket();
        ManagedObject managedObject = (ManagedObject) liveDataBucket.findPoById(poid);
        managedObject.setAttribute(Subscription.Subscription220Attribute.administrationState.name(), AdministrationState.ACTIVATING.name());
    }
}
