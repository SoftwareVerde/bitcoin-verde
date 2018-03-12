package com.softwareverde.bitcoin.util;

import com.softwareverde.io.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

public class JvmUtil {
    public static void clearSystemOutLine() {
        System.out.print("\33[1A\33[2K");
    }

    public static void printMemoryUsage() {
        final Runtime runtime = Runtime.getRuntime();
        final Long maxMemory = runtime.maxMemory();
        final Long freeMemory = runtime.freeMemory();
        final Long reservedMemory = runtime.totalMemory();
        final Long currentMemoryUsage = reservedMemory - freeMemory;

        final Long toMegabytes = 1048576L;
        Logger.log((currentMemoryUsage/toMegabytes) +"mb / "+ (maxMemory/toMegabytes) +"mb ("+ String.format("%.2f", (currentMemoryUsage.floatValue() / maxMemory.floatValue() * 100.0F)) +"%)");
    }

    public static void checkForDeadlockedThreads() {
        final ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        final long[] threadIds = bean.findDeadlockedThreads(); // Returns null if no threads are deadlocked.

        if (threadIds != null) {
            final ThreadInfo[] threadInfo = bean.getThreadInfo(threadIds);

            for (final ThreadInfo info : threadInfo) {
                final StackTraceElement[] stack = info.getStackTrace();
                for (final StackTraceElement stackTraceElement : stack) {
                    Logger.log(stackTraceElement);
                }
            }
        }
    }
}
