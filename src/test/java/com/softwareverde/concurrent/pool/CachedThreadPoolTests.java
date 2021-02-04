package com.softwareverde.concurrent.pool;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.concurrent.Pin;
import com.softwareverde.concurrent.pool.cached.CachedThreadPool;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.timer.NanoTimer;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class CachedThreadPoolTests extends UnitTest {
    protected static void assertPoolExecutesConcurrently(final CachedThreadPool threadPool, final Integer concurrentCount, final Long sleepTime, final Long maxExecutionTime, final Long maxExecutionTimeBuffer) throws Exception {
        // Setup
        final AtomicInteger completionCount = new AtomicInteger(0);
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try { Thread.sleep(sleepTime); }
                catch (final Exception exception) { }
                synchronized (completionCount) {
                    completionCount.incrementAndGet();
                    completionCount.notifyAll();
                }
            }
        };
        final NanoTimer nanoTimer = new NanoTimer();

        threadPool.start();

        // Action
        nanoTimer.start();
        for (int i = 0; i < concurrentCount; ++i) {
            threadPool.execute(runnable);
        }

        while (completionCount.get() < concurrentCount) {
            synchronized (completionCount) {
                completionCount.wait(100L);
            }
        }

        nanoTimer.stop();
        Logger.info(completionCount.get() + " of " + concurrentCount + " complete: " + nanoTimer.getMillisecondsElapsed() + "ms.");

        System.out.println("Alive Threads: " + CachedThreadPool.getAliveThreadCount());
        Assert.assertEquals(Integer.valueOf(Math.min(concurrentCount, threadPool.getMaxThreadCount())), CachedThreadPool.getAliveThreadCount());

        // Assert
        threadPool.stop();
        Assert.assertTrue(nanoTimer.getMillisecondsElapsed() < (maxExecutionTime + maxExecutionTimeBuffer));

        Thread.sleep(1000L);
        System.out.println("Alive Threads: " + CachedThreadPool.getAliveThreadCount());
        Assert.assertEquals(Integer.valueOf(0), CachedThreadPool.getAliveThreadCount());
    }

    @Test
    public void should_not_block_when_threads_available() throws Exception {
        CachedThreadPoolTests.assertPoolExecutesConcurrently(new CachedThreadPool(3, 1000L), 3, 1000L, 1000L, 100L);
    }

    @Test
    public void should_block_when_queue_is_exhausted() throws Exception {
        CachedThreadPoolTests.assertPoolExecutesConcurrently(new CachedThreadPool(128, 100L), 20, 1000L, 1000L, 250L);
        CachedThreadPoolTests.assertPoolExecutesConcurrently(new CachedThreadPool(2, 1000L), 20, 1000L, 10000L, 250L);
        CachedThreadPoolTests.assertPoolExecutesConcurrently(new CachedThreadPool(8, 1000L), 800, 100L, 10000L, 750L);
    }

    @Test
    public void should_keep_used_threads_alive_until_timeout() throws Exception {
        // Setup
        final CachedThreadPool cachedThreadPool = new CachedThreadPool(8, 2000L);
        cachedThreadPool.start();
        for (int i = 0; i < 4; ++i) {
            cachedThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    try { Thread.sleep(100L); } catch (final Exception exception) { }
                }
            });
        }

        Assert.assertEquals(Integer.valueOf(4), CachedThreadPool.getAliveThreadCount());

        // Action
        Thread.sleep(2250L);

        // Assert
        Assert.assertEquals(Integer.valueOf(0), CachedThreadPool.getAliveThreadCount());
    }

    @Test
    public void should_migrate_long_running_thread_out_of_pool() throws Exception {
        // Action
        final CachedThreadPool cachedThreadPool = new CachedThreadPool(1, 1000L, 2000L);
        cachedThreadPool.start();

        final Pin longThreadPin = new Pin();
        final Pin shortThreadPin = new Pin();

        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        // Action
        cachedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try { Thread.sleep(3000L); } catch (final Exception exception) { }
                System.out.println("Long done.");
                longThreadPin.release();
            }
        });

        cachedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try { Thread.sleep(1000L); } catch (final Exception exception) { }
                System.out.println("Short done.");
                shortThreadPin.release();
            }
        });

        Logger.info("Short accepted after " + nanoTimer.getMillisecondsElapsed() + "ms.");

        Assert.assertEquals(Integer.valueOf(1), cachedThreadPool.getLongRunningThreadCount());

        longThreadPin.waitForRelease();
        shortThreadPin.waitForRelease();

        nanoTimer.stop();

        // Assert
        final long msElapsed = nanoTimer.getMillisecondsElapsed().longValue();
        Logger.info("MsElapsed: " + msElapsed);
        Assert.assertTrue(msElapsed < 3250L);

        Thread.sleep(1500L);
        Assert.assertEquals(Integer.valueOf(0), CachedThreadPool.getAliveThreadCount());
    }

    @Test
    public void should_reset_interrupted_flags_set_by_tasks() throws Exception {
        // Setup
        final CachedThreadPool cachedThreadPool = new CachedThreadPool(1, 10000L);
        cachedThreadPool.start();

        final Container<Long> threadId = new Container<>();
        final Container<Double> msElapsed0 = new Container<>();
        final Container<Double> msElapsed1 = new Container<>();
        final Pin pin0 = new Pin();
        final Pin pin1 = new Pin();

        // Action
        cachedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                final Thread thread = Thread.currentThread();
                threadId.value = thread.getId();
                Assert.assertFalse(thread.isInterrupted());

                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();
                try { Thread.sleep(1000L); } catch (final Exception exception) { }
                nanoTimer.stop();

                // Pollute the interrupted flag for the next runnable...
                thread.interrupt();

                msElapsed0.value = nanoTimer.getMillisecondsElapsed();

                pin0.release();
            }
        });

        cachedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                final Thread thread = Thread.currentThread();
                Assert.assertEquals(threadId.value, Long.valueOf(thread.getId()));
                Assert.assertFalse(thread.isInterrupted());

                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();
                try { Thread.sleep(1000L); } catch (final Exception exception) { }
                nanoTimer.stop();

                thread.interrupt();

                msElapsed1.value = nanoTimer.getMillisecondsElapsed();

                pin1.release();
            }
        });

        pin0.waitForRelease(3000L);
        pin1.waitForRelease(3000L);

        cachedThreadPool.stop();

        // Assert
        Assert.assertTrue(msElapsed0.value >= 1000L);
        Assert.assertTrue(msElapsed1.value >= 1000L);
    }
}
