package com.softwareverde.concurrent.pool;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.concurrent.pool.cached.CachedThreadPool;
import com.softwareverde.util.Container;
import com.softwareverde.util.timer.NanoTimer;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class CachedThreadPoolTests extends UnitTest {
    @Test
    public void should_execute_tasks_asynchronously() throws Exception {
        // Setup
        final Integer taskCount = 8;
        final CachedThreadPool cachedThreadPool = new CachedThreadPool(taskCount, 10000L);
        cachedThreadPool.start();
        Assert.assertEquals(taskCount, cachedThreadPool.getThreadCount());

        final Long executionTime;
        final AtomicInteger executionCount = new AtomicInteger(0);

        // Action
        for (int i = 0; i < taskCount; ++i) {
            cachedThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1000L);
                    }
                    catch (final Exception exception) { }
                    finally {
                        synchronized (executionCount) {
                            executionCount.incrementAndGet();
                            executionCount.notifyAll();
                        }
                    }
                }
            });
        }
        Assert.assertEquals(taskCount, cachedThreadPool.getThreadCount());

        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        while (executionCount.get() < taskCount) {
            synchronized (executionCount) {
                try {
                    executionCount.wait();
                }
                catch (final Exception exception) { }
            }
        }

        nanoTimer.stop();
        executionTime = nanoTimer.getMillisecondsElapsed().longValue();

        // Assert
        System.out.println("Execution Time: " + executionTime);
        Assert.assertEquals(taskCount, Integer.valueOf(executionCount.get()));
        Assert.assertTrue(executionTime <= 1100L);

        cachedThreadPool.stop();
        Assert.assertEquals(Integer.valueOf(0), cachedThreadPool.getThreadCount());
    }

    @Test
    public void should_execute_tasks_asynchronously_when_tasks_saturate_pool() throws Exception {
        // Setup
        final Integer taskCount = 32;
        final Integer threadCount = (taskCount / 2);
        final CachedThreadPool cachedThreadPool = new CachedThreadPool(threadCount, 10000L);
        cachedThreadPool.start();
        Assert.assertEquals(threadCount, cachedThreadPool.getThreadCount());

        final Long executionTime;
        final AtomicInteger executionCount = new AtomicInteger(0);
        final Container<Integer> maxActiveThreadCount = new Container<>(0);

        // Action
        final NanoTimer queueTaskTimer = new NanoTimer();
        queueTaskTimer.start();

        for (int i = 0; i < taskCount; ++i) {
            cachedThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (maxActiveThreadCount) {
                        maxActiveThreadCount.value = Math.max(maxActiveThreadCount.value, cachedThreadPool.getActiveThreadCount());
                    }

                    try {
                        Thread.sleep(1000L);
                    }
                    catch (final Exception exception) { }
                    finally {
                        synchronized (executionCount) {
                            executionCount.incrementAndGet();
                            executionCount.notifyAll();
                        }
                    }
                }
            });
        }

        queueTaskTimer.stop();
        Assert.assertEquals(threadCount, cachedThreadPool.getThreadCount());
        Assert.assertTrue(queueTaskTimer.getMillisecondsElapsed() <= 100L);

        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        while (executionCount.get() < taskCount) {
            synchronized (executionCount) {
                try {
                    executionCount.wait();
                }
                catch (final Exception exception) { }
            }
        }

        nanoTimer.stop();
        executionTime = nanoTimer.getMillisecondsElapsed().longValue();

        // Assert
        System.out.println("Execution Time: " + executionTime);
        System.out.println("Max Active Thread Count: " + maxActiveThreadCount.value);
        Assert.assertEquals(threadCount, maxActiveThreadCount.value);
        Assert.assertEquals(taskCount, Integer.valueOf(executionCount.get()));
        Assert.assertTrue(executionTime <= 2100L);

        cachedThreadPool.stop();
        Assert.assertEquals(Integer.valueOf(0), cachedThreadPool.getThreadCount());
    }
}
