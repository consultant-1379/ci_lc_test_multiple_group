/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.services.pm.collection.instrumentation;

import static org.awaitility.Awaitility.await;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.core.util.JmxUtil;
import com.ericsson.oss.itpf.sdk.eventbus.model.EventSender;
import com.ericsson.oss.itpf.sdk.eventbus.model.annotation.Modeled;
import com.ericsson.oss.mediation.sdk.event.MediationTaskResult;
import com.ericsson.oss.pmic.dto.subscription.enums.SubscriptionType;
import com.ericsson.oss.services.pm.collection.events.FileCollectionFailure;
import com.ericsson.oss.services.pm.collection.events.FileCollectionResult;
import com.ericsson.oss.services.pm.collection.events.FileCollectionSuccess;
import com.ericsson.oss.services.pm.initiation.integration.InputBaseArquillian;

@RunWith(Arquillian.class)
public class FileCollectionInstrumentationIntTest extends InputBaseArquillian {
    public static final long BYTES_STORED = 512;
    public static final long BYTES_TRANSFERRED = 1024;

    private static String preamble = "com.ericsson.oss.services.pm.collection.instrumentation";
    private static String type = ":type=FileCollectionInstrumentation";
    private static String serviceID = "pm-service";
    @Inject
    Logger logger;
    @Inject
    @Modeled
    private EventSender<MediationTaskResult> sender;

    @Test
    @InSequence(2)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void sendFileCollectionResult() throws Exception {
        logger.debug("executing #sendFileCollectionResult");
        final Map<String, String> dstAndSrcFiles = new HashMap<String, String>();
        dstAndSrcFiles.put("testfile1.xml", "testxmlfile1.xml");
        dstAndSrcFiles.put("testfile2.xml", "testxmlfile2.xml");

        final FileCollectionSuccess fcs = new FileCollectionSuccess("testfile1.xml", BYTES_TRANSFERRED, BYTES_STORED);
        final FileCollectionFailure fcf = new FileCollectionFailure("testfile2.xml", "Failed to collect file", 100);

        final List<FileCollectionSuccess> fcsL = new ArrayList<>();
        final List<FileCollectionFailure> fcfL = new ArrayList<>();

        fcsL.add(fcs);
        fcfL.add(fcf);

        final FileCollectionResult fcr = new FileCollectionResult("NetworkElement=ERBS001", System.currentTimeMillis(),
                System.currentTimeMillis() + 1000, "taskId", SubscriptionType.STATISTICAL.name(), dstAndSrcFiles, "/c/pm_data", "/ericsson/pmic/pm1",
                System.currentTimeMillis(), 900, true, "ERBS", "3520-829-806", fcsL, fcfL);

        sender.send(fcr);
    }

    @Test
    @InSequence(3)
    @OperateOnDeployment(PM_SERVICE_TEST)
    public void testInstrumentationBeanIsInitialized() {
        logger.debug("executing #testInstrumentationBeanIsInitialized");
        await().atMost(2, TimeUnit.MINUTES).until(beanInfoChecked());
        Assert.assertTrue("Either ms or mi were null. Check logs for which", checkBeanInfo());
    }

    private Callable<Boolean> beanInfoChecked() { return () -> checkBeanInfo(); }

    private boolean checkBeanInfo() {
        try {
            final String mbname = String.format("%s.%s%s", preamble, serviceID, type);
            final ObjectName mbeanObjectName = new ObjectName(mbname);
            final MBeanServer ms = JmxUtil.locateMBeanServer();
            final MBeanInfo mi = ms.getMBeanInfo(mbeanObjectName);
            logger.info("ms: {}, mi: {}", ms, mi);
            return ms != null && mi != null;
        } catch (final Exception ex) {
            logger.error("Exception while checking bean info", ex);
        }
        return false;
    }

}
