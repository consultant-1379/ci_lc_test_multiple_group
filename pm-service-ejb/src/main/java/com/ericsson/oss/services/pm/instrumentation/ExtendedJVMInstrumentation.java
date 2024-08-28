
/*
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */

package com.ericsson.oss.services.pm.instrumentation;

import java.util.Map;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.jvm.FileDescriptorRatioGauge;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.ericsson.oss.itpf.sdk.instrument.MetricsUtil;
import com.ericsson.oss.itpf.sdk.instrument.annotation.InstrumentedBean;
import com.ericsson.oss.itpf.sdk.instrument.annotation.MonitoredAttribute;

/**
 * The Extended jvm instrumentation class.
 */
@ApplicationScoped
@InstrumentedBean(displayName = "Extended JVM Instrumentation")
public class ExtendedJVMInstrumentation {

    private final MetricRegistry metricRegistry = MetricsUtil.getRegistry();

    /**
     * Post Construct registers file descriptor ratio and threads metrics.
     */
    @PostConstruct
    public void register() {
        if (isTimerMetricEnabled()) {
            registerAll("extended_jvm_instrumentation.threads", new ThreadStatesGaugeSet());
            try {
                metricRegistry.register("extended_jvm_instrumentation.file_descriptor_ratio", new FileDescriptorRatioGauge());
            } catch (final IllegalArgumentException e) {
                // skip if the metric already exists otherwise rethrow the exception
                if (!e.getMessage().contains("already exists")) {
                    throw e;
                }
            }
        }
    }

    private boolean isTimerMetricEnabled() {
        return metricRegistry != null; // see MetricsUtil.getRegistry() for details
    }

    private void registerAll(final String prefix, final MetricSet metrics) {
        for (final Map.Entry<String, Metric> entry : metrics.getMetrics().entrySet()) {
            if (entry.getValue() instanceof MetricSet) {
                registerAll(MetricRegistry.name(prefix, entry.getKey()), (MetricSet) entry.getValue());
            } else {
                try {
                    metricRegistry.register(MetricRegistry.name(prefix, entry.getKey()), entry.getValue());
                } catch (final IllegalArgumentException e) {
                    // skip if the metric already exists otherwise rethrow the exception
                    if (!e.getMessage().contains("already exists")) {
                        throw e;
                    }
                }
            }
        }
    }

    /**
     * Gets file descriptor ratio.
     *
     * @return the file descriptor ratio
     */
    @MonitoredAttribute(displayName = "extended_jvm_instrumentation.file_descriptor_ratio", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public Double getFileDescriptorRatio() {
        if (!isTimerMetricEnabled()) {
            return 0d;
        }
        return (Double) metricRegistry.getGauges().get("extended_jvm_instrumentation.file_descriptor_ratio").getValue();
    }

    /**
     * Gets threads daemon count.
     *
     * @return the threads daemon count
     */
    @MonitoredAttribute(displayName = "extended_jvm_instrumentation.threads.daemon.count", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public Integer getThreadsDaemonCount() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return (Integer) metricRegistry.getGauges().get("extended_jvm_instrumentation.threads.daemon.count").getValue();
    }

    /**
     * Gets threads deadlocks count.
     *
     * @return the threads deadlocks count
     */
    @MonitoredAttribute(displayName = "extended_jvm_instrumentation.threads.deadlocks.count", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public Integer getThreadsDeadlocksCount() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        final Set deadlocks = (Set) metricRegistry.getGauges().get("extended_jvm_instrumentation.threads.deadlocks").getValue();
        return deadlocks.size();
    }

    /**
     * Get threads deadlocks string [ ].
     *
     * @return the string [ ]
     */
    @MonitoredAttribute(displayName = "extended_jvm_instrumentation.threads.deadlocks", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public String[] getThreadsDeadlocks() {
        if (!isTimerMetricEnabled()) {
            return new String[]{};
        }
        final Set<String> deadlocks = (Set<String>) metricRegistry.getGauges().get("extended_jvm_instrumentation.threads.deadlocks").getValue();
        return deadlocks.toArray(new String[deadlocks.size()]);
    }

    /**
     * Gets threads new count.
     *
     * @return the threads new count
     */
    @MonitoredAttribute(displayName = "extended_jvm_instrumentation.threads.new.count", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public Integer getThreadsNewCount() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return (Integer) metricRegistry.getGauges().get("extended_jvm_instrumentation.threads.new.count").getValue();
    }

    /**
     * Gets threads runnable count.
     *
     * @return the threads runnable count
     */
    @MonitoredAttribute(displayName = "extended_jvm_instrumentation.threads.runnable.count", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public Integer getThreadsRunnableCount() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return (Integer) metricRegistry.getGauges().get("extended_jvm_instrumentation.threads.runnable.count").getValue();
    }

    /**
     * Gets threads blocked count.
     *
     * @return the threads blocked count
     */
    @MonitoredAttribute(displayName = "extended_jvm_instrumentation.threads.blocked.count", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public Integer getThreadsBlockedCount() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return (Integer) metricRegistry.getGauges().get("extended_jvm_instrumentation.threads.blocked.count").getValue();
    }

    /**
     * Gets threads waiting count.
     *
     * @return the threads waiting count
     */
    @MonitoredAttribute(displayName = "extended_jvm_instrumentation.threads.waiting.count", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public Integer getThreadsWaitingCount() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return (Integer) metricRegistry.getGauges().get("extended_jvm_instrumentation.threads.waiting.count").getValue();
    }

    /**
     * Gets threads timed waiting count.
     *
     * @return the threads timed waiting count
     */
    @MonitoredAttribute(displayName = "extended_jvm_instrumentation.threads.timed_waiting.count", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public Integer getThreadsTimedWaitingCount() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return (Integer) metricRegistry.getGauges().get("extended_jvm_instrumentation.threads.timed_waiting.count").getValue();
    }

    /**
     * Gets threads terminated count.
     *
     * @return the threads terminated count
     */
    @MonitoredAttribute(displayName = "extended_jvm_instrumentation.threads.terminated.count", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public Integer getThreadsTerminatedCount() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return (Integer) metricRegistry.getGauges().get("extended_jvm_instrumentation.threads.terminated.count").getValue();
    }

}
