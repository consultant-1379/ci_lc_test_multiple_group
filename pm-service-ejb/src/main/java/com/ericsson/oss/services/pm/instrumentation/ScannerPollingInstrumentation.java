
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Lock;
import javax.enterprise.context.ApplicationScoped;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.ericsson.oss.itpf.sdk.instrument.MetricsUtil;
import com.ericsson.oss.itpf.sdk.instrument.annotation.InstrumentedBean;
import com.ericsson.oss.itpf.sdk.instrument.annotation.MonitoredAttribute;

/**
 * Class for Scanner polling instrumentation.
 */
@ApplicationScoped
@InstrumentedBean(displayName = "Scanner Pooling Instrumentation")
public class ScannerPollingInstrumentation {

    public static final String SCANNER_POLLING_DURATION_PER_ROP = "scanner_polling_duration.per_rop";
    public static final String SCANNER_POLLING_DURATION_PER_NODE = "scanner_polling_duration.per_node";
    public static final String SCANNER_POLLING_SUCCEEDED_TASKS = "scanner_polling.succeeded_tasks";
    public static final String SCANNER_POLLING_FAILED_TASKS = "scanner_polling.failed_tasks";

    private static final String SEPARATOR = "|";
    private final Map<String, Deque<Timer.Context>> tasks = new ConcurrentHashMap<>();
    private final Deque<ScannerPoolingTaskGroup> taskGroups = new LinkedBlockingDeque<>();
    private MetricRegistry metricRegistry;

    /**
     * Post construct method, initialises metricRegistry
     */
    @PostConstruct
    public void onInit() {
        metricRegistry = MetricsUtil.getRegistry();
    }

    /**
     * Pre destroy method, clears task groups and tasks
     */
    @PreDestroy
    public void onDestroy() {
        taskGroups.clear();
        tasks.clear();
    }

    private boolean isTimerMetricEnabled() {
        return metricRegistry != null; // see MetricsUtil.getRegistry() for details
    }

    /**
     * @param nodeFdns
     *         - set of node fully distinguished names
     */
    @Lock
    public void scannerPollingTaskStarted(final Set<String> nodeFdns) {
        if (!isTimerMetricEnabled() || nodeFdns == null || nodeFdns.isEmpty()) {
            return;
        }
        final Timer.Context timerContext = metricRegistry.timer(SCANNER_POLLING_DURATION_PER_ROP).time();
        taskGroups.add(new ScannerPoolingTaskGroup(timerContext, nodeFdns));

        for (final String nodeFdn : nodeFdns) {
            scannerPollingTaskStarted(nodeFdn);
        }
    }

    /**
     * @param nodeFdn
     *         - the fully distinguished name of the node
     */
    @Lock
    public void scannerPollingTaskStarted(final String nodeFdn) {
        if (!isTimerMetricEnabled() || nodeFdn == null || nodeFdn.isEmpty()) {
            return;
        }
        final Timer.Context taskTimerContext = metricRegistry.timer(SCANNER_POLLING_DURATION_PER_NODE).time();
        Deque<Timer.Context> taskTimerContexts = tasks.get(SCANNER_POLLING_DURATION_PER_NODE + SEPARATOR + "nodeFdn = " + nodeFdn);
        if (taskTimerContexts == null) {
            taskTimerContexts = new ArrayDeque<>(5);
            tasks.put(SCANNER_POLLING_DURATION_PER_NODE + SEPARATOR + "nodeFdn = " + nodeFdn, taskTimerContexts);
        }
        taskTimerContexts.addLast(taskTimerContext);
    }

    /**
     * @param nodeFdn
     *         - the fully distinguished name of the node
     */
    @Lock
    public void scannerPollingTaskEnded(final String nodeFdn) {
        scannerPollingTaskEnded(nodeFdn, true);
    }

    private void scannerPollingTaskEnded(final String nodeFdn, final boolean succeeded) {
        if (!isTimerMetricEnabled() || nodeFdn == null || nodeFdn.isEmpty()) {
            return;
        }
        countExecutedScannerPollingTask(succeeded);
        scannerPollingTaskGroupEnded(nodeFdn);

        final Deque<Timer.Context> taskTimerContexts = tasks.get(SCANNER_POLLING_DURATION_PER_NODE + SEPARATOR + "nodeFdn = " + nodeFdn);
        if (taskTimerContexts != null && !taskTimerContexts.isEmpty()) {
            final Timer.Context taskTimerContext = taskTimerContexts.removeFirst();
            if (succeeded) {
                taskTimerContext.stop();
            }
        }
    }

    /**
     * @param nodeFdn
     *         - the fully distinguished name of the node
     */
    @Lock
    public void scannerPollingTaskFailed(final String nodeFdn) {
        scannerPollingTaskEnded(nodeFdn, false);
    }

    private void countExecutedScannerPollingTask(final boolean succeeded) {
        metricRegistry.counter(succeeded ? SCANNER_POLLING_SUCCEEDED_TASKS : SCANNER_POLLING_FAILED_TASKS).inc();
    }

    private void scannerPollingTaskGroupEnded(final String nodeFdn) {
        /*
         * Assumption: the task groups are executing in strict order.
         * We have set of the fdns called nodeFdns, if a fdn has been removed from nodeFdns it means that we need to
         * remove it from next taskGroup following the order of calls.
         */
        boolean removed = false;
        final Iterator<ScannerPoolingTaskGroup> iterator = taskGroups.iterator();
        while (iterator.hasNext()) {
            final ScannerPoolingTaskGroup taskGroup = iterator.next();
            if (!removed) {
                removed = taskGroup.getNodeFdns().remove(nodeFdn);
            }
            if (taskGroup.getNodeFdns().isEmpty()) {
                iterator.remove();
                taskGroup.getTimerContext().stop();
            }
        }
    }

    /**
     * @return - returns the count of scannerPollingDuration per record output period
     */
    @MonitoredAttribute(displayName = SCANNER_POLLING_DURATION_PER_ROP + ".count", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountScannerPollingDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SCANNER_POLLING_DURATION_PER_ROP).getCount();
    }

    /**
     * @return - returns the maximum scannerPollingDuration per record output period
     */
    @MonitoredAttribute(displayName = SCANNER_POLLING_DURATION_PER_ROP + ".max", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxScannerPollingDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SCANNER_POLLING_DURATION_PER_ROP).getSnapshot().getMax();
    }

    /**
     * @return - returns the median scannerPollingDuration per record output period
     */
    @MonitoredAttribute(displayName = SCANNER_POLLING_DURATION_PER_ROP + ".median", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianScannerPollingDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SCANNER_POLLING_DURATION_PER_ROP).getSnapshot().getMedian();
    }

    /**
     * @return - returns the minimum scannerPollingDuration per record output period
     */
    @MonitoredAttribute(displayName = SCANNER_POLLING_DURATION_PER_ROP + ".min", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinScannerPollingDurationPerRop() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SCANNER_POLLING_DURATION_PER_ROP).getSnapshot().getMin();
    }

    /**
     * @return - returns the count of scannerPollingDuration per node
     */
    @MonitoredAttribute(displayName = SCANNER_POLLING_DURATION_PER_NODE + ".count", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getCountScannerPollingDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SCANNER_POLLING_DURATION_PER_NODE).getCount();
    }

    /**
     * @return - returns the maximum scannerPollingDuration per node
     */
    @MonitoredAttribute(displayName = SCANNER_POLLING_DURATION_PER_NODE + ".max", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMaxScannerPollingDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SCANNER_POLLING_DURATION_PER_NODE).getSnapshot().getMax();
    }

    /**
     * @return - returns the media scannerPollingDuration per node
     */
    @MonitoredAttribute(displayName = SCANNER_POLLING_DURATION_PER_NODE + ".median", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public double getMedianScannerPollingDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SCANNER_POLLING_DURATION_PER_NODE).getSnapshot().getMedian();
    }

    /**
     * @return - returns the minimum scannerPollingDuration per node
     */
    @MonitoredAttribute(displayName = SCANNER_POLLING_DURATION_PER_NODE + ".min", visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN, units = MonitoredAttribute.Units.NANO_SECONDS)
    public long getMinScannerPollingDurationPerNode() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.timer(SCANNER_POLLING_DURATION_PER_NODE).getSnapshot().getMin();
    }

    /**
     * @return - returns the number of scanner polling succeeded tasks
     */
    @MonitoredAttribute(displayName = SCANNER_POLLING_SUCCEEDED_TASKS, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getScannerPollingSucceededTasks() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(SCANNER_POLLING_SUCCEEDED_TASKS).getCount();
    }

    /**
     * @return - returns the number of scanner polling failed tasks
     */
    @MonitoredAttribute(displayName = SCANNER_POLLING_FAILED_TASKS, visibility = MonitoredAttribute.Visibility.ALL,
            interval = MonitoredAttribute.Interval.ONE_MIN)
    public long getScannerPollingFailedTasks() {
        if (!isTimerMetricEnabled()) {
            return 0;
        }
        return metricRegistry.counter(SCANNER_POLLING_FAILED_TASKS).getCount();
    }

    /**
     * Scanner pooling task group
     */
    public class ScannerPoolingTaskGroup {
        private final Timer.Context timerContext;
        private final Set<String> nodeFdns;

        /**
         * @param timerContext
         *         - timer info object
         * @param nodeFdns
         *         - set of node fully distinguished names
         */
        public ScannerPoolingTaskGroup(final Timer.Context timerContext, final Set<String> nodeFdns) {
            this.timerContext = timerContext;
            this.nodeFdns = nodeFdns;
        }

        public Timer.Context getTimerContext() {
            return timerContext;
        }

        public Set<String> getNodeFdns() {
            return nodeFdns;
        }
    }

}
