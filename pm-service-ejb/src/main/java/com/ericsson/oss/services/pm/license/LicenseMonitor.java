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

package com.ericsson.oss.services.pm.license;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import static com.ericsson.oss.itpf.sdkutils.util.CommonUtil.isStringNullOrEmpty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PostConstruct;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ericsson.oss.itpf.sdk.licensing.LicensingService;
import com.ericsson.oss.itpf.sdk.licensing.Permission;

/**
 * License checker that monitors supported licenses periodically (every 5 minutes).
 * All supported licenses will be verified on the system startup, and will be cached. It delegates the verification to the
 * {@link LicensingService}, if there is some error when verifying, this implementation will assume the last known state for the respective license
 * name, if the number of errors is less than 11. For more than 11 consecutive errors verifying the same license name, it will return not allowed.
 */
@Startup
@Singleton
@Lock(LockType.READ)
public class LicenseMonitor implements LicenseChecker {

    private static final String EBSM_LICENCE_FAT_NUMBER = "FAT1023459";
    private static final String EBSL_LICENCE_FAT_NUMBER = "FAT1023527";
    private static final String ESN_LICENCE_FAT_NUMBER_A = "FAT1023777";
    private static final String ESN_LICENCE_FAT_NUMBER_B = "FAT1023630";
    private static final String EBSN_LICENCE_FAT_NUMBER = "FAT1024120";
    private static final String BSC_EVENT_LICENCE_FAT_NUMBER = "FAT1024150";


    // Here we define the supported licenses (using Sentinel Key ID as names).
    private static final List<String> supportedLicenses = unmodifiableList(asList(EBSM_LICENCE_FAT_NUMBER,
            EBSL_LICENCE_FAT_NUMBER,
            ESN_LICENCE_FAT_NUMBER_A,
            ESN_LICENCE_FAT_NUMBER_B,
            EBSN_LICENCE_FAT_NUMBER,
            BSC_EVENT_LICENCE_FAT_NUMBER));

    private static final int MAX_ERROR_COUNT = 11;
    private final Map<String, License> cachedLicenses = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> errorCounter = new ConcurrentHashMap<>();
    @Inject
    private Logger log;
    @Inject
    private LicensingService licensingService;

    /**
     * Initializes the license monitor, triggering the verification of all supported licenses.
     */
    @PostConstruct
    void init() {
        log.info("Updating license monitor error count.");
        initErrorCounter();
    }

    private void initErrorCounter() {
        for (final String licenseName : getSupportedLicenses()) {
            errorCounter.put(licenseName, new AtomicInteger(0));
        }
    }

    private int incrementAndGetErrorCount(final String licenseName) {
        return errorCounter.get(licenseName).incrementAndGet();
    }

    private void resetErrorCount(final String licenseName) {
        errorCounter.get(licenseName).set(0);
    }

    /**
     * Return a list of supported licenses.
     *
     * @return The list of license names.
     */
    List<String> getSupportedLicenses() {
        return supportedLicenses;
    }

    /**
     * Timeout method. Periodically verify the supported licenses.
     */
    @Schedule(hour = "*", minute = "*/5", persistent = false)
    void onSchedulerTimeout() {
        verifyNow();
    }

    private void verifyNow() {
        for (final String licenseName : getSupportedLicenses()) {
            cachedLicenses.put(licenseName, verifyNow(licenseName));
        }
    }

    private License verifyNow(final String licenseName) {
        try {
            log.debug("Verifing license name '{}'...", licenseName);
            final Permission permission = licensingService.validatePermission(licenseName);
            final boolean allowed = Permission.ALLOWED == permission;
            log.debug("License name '{}' verified - permission: '{}'.", licenseName, permission);
            resetErrorCount(licenseName);
            return new License(licenseName, allowed, "Verified with success.");

        } catch (final Exception exception) {

            final int errorCount = incrementAndGetErrorCount(licenseName);
            final License cachedLicense = cachedLicenses.get(licenseName);
            final License license;

            if (cachedLicense != null && errorCount <= MAX_ERROR_COUNT) {
                license = new License(licenseName, cachedLicense.isAllowed(), "Failed to verify license. Assuming the last known state.");

            } else {
                license = new License(licenseName, false, "Failed to verify license. Assuming not allowed.");
            }
            log.error("Unexpected error when verifing license '{}', errorCount={}.", license, errorCount, exception);
            return license;
        }
    }

    @Override
    public License verify(final String licenseName) {

        if (isStringNullOrEmpty(licenseName)) {
            throw new IllegalArgumentException("License name should not be null or empty.");
        }

        if (cachedLicenses.get(licenseName) == null) {
            verifyNow();
        }
        return cachedLicenses.get(licenseName) != null ? cachedLicenses.get(licenseName) : new License(licenseName, false, "Unknown license.");
    }

    @Override
    public Map<String, Map<String, Object>> verify(final List<String> licenseNames) {
        final Map<String, Map<String, Object>> licenses = new HashMap<>();
        for (final String licenseName : licenseNames) {
            if (isStringNullOrEmpty(licenseName)) {
                log.warn("licenseName is either empty or null");
            } else {
                licenses.put(licenseName, verify(licenseName).toMap());
            }
        }
        return licenses;
    }
}
