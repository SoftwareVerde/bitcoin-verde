package com.softwareverde.bitcoin.server.memory;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

public class LowMemoryMonitor {
    protected final Float _memoryThresholdPercent;
    protected final Runnable _memoryThresholdReachedCallback;

    protected void _registerCallback() {
        final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        final NotificationEmitter notificationEmitter = (NotificationEmitter) memoryBean;

        notificationEmitter.addNotificationListener(new NotificationListener() {
            @Override
            public void handleNotification(final Notification notification, final Object handback) {
                _memoryThresholdReachedCallback.run();
            }
        }, null, null);

        final List<MemoryPoolMXBean> memoryPoolBeans = ManagementFactory.getMemoryPoolMXBeans();
        for (final MemoryPoolMXBean memoryPoolBean : memoryPoolBeans) {
            if (memoryPoolBean.isUsageThresholdSupported()) {
                final MemoryUsage memoryUsage = memoryPoolBean.getUsage();
                final long memoryUsageMax = memoryUsage.getMax();
                memoryPoolBean.setUsageThreshold((long) (memoryUsageMax * _memoryThresholdPercent));
            }
        }
    }

    public LowMemoryMonitor(final Float memoryThresholdPercent, final Runnable memoryThresholdReachedCallback) {
        _memoryThresholdPercent = memoryThresholdPercent;
        _memoryThresholdReachedCallback = memoryThresholdReachedCallback;

        _registerCallback();
    }
}
