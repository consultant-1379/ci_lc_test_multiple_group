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

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.transaction.TransactionSynchronizationRegistry;

import org.slf4j.Logger;

import com.ericsson.oss.services.pm.exception.DataAccessException;

@Stateless
public class SubscriptionDaoRollbackVerify {

    @Inject
    private CustomSubscriptionDao subscriptionDao;

    @Resource
    private TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    @Inject
    private Logger logger;

    /**
     * Forces DPS to throw application exception which does not roll back the transaction. Whether we catch this exception or propagate it, the
     * transaction will not be rolled back.
     *
     * @return - Transaction status.
     */
    public int forceDpsToThrowNonRollbackExceptionAndReturnTransactionStatus() {
        try {
            subscriptionDao.findOneByExactName("ThrowDpsException", false);
        } catch (DataAccessException e) {
            logger.error(e.getMessage(), e); //do nothing
        }
        return transactionSynchronizationRegistry.getTransactionStatus();
    }

    /**
     * Forces DPS to throw Rollback exception and catches it. This proves that even though we catch RuntimException, the transaction will be rolled
     * back anyhow because DPS marked it for rollback.
     *
     * @return Transaction status.
     */
    public int forceDpsToThrowRollbackExceptionAndReturnTransactionStatus() {
        try {
            subscriptionDao.existsByFdn("ThrowDpsException");
        } catch (Exception e) {
            logger.error(e.getMessage(), e); //do nothing
        }
        return transactionSynchronizationRegistry.getTransactionStatus();
    }

}
