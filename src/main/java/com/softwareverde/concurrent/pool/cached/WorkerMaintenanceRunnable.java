package com.softwareverde.concurrent.pool.cached;

import com.softwareverde.logging.Logger;

import java.util.Iterator;
import java.util.Map;

public class WorkerMaintenanceRunnable implements Runnable {
    protected final CachedThreadPool _cachedThreadPool;

    public WorkerMaintenanceRunnable(final CachedThreadPool cachedThreadPool) {
        _cachedThreadPool = cachedThreadPool;
    }

    @Override
    public void run() {
        final Thread thread = Thread.currentThread();
        while (! thread.isInterrupted()) {
            long nextSleepMs = 1000L;

            final Iterator<Map.Entry<Long, CachedThread>> iterator = _cachedThreadPool._runningThreads.entrySet().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<Long, CachedThread> entry = iterator.next();
                final Long threadId = entry.getKey();
                final CachedThread cachedThread = entry.getValue();
                if (cachedThread == null) { continue; }

                final Long taskTime = cachedThread.getTaskTime();
                final Long idleTime = cachedThread.getIdleTime();

                // Logger.trace("Worker Thread " + cachedThread.getId() + " taskMs=" + taskTime + " idleMs=" + idleTime);

                if (taskTime > _cachedThreadPool._longRunningThreshold) {
                    iterator.remove();
                    _cachedThreadPool._longRunningThreads.put(threadId, cachedThread);
                    cachedThread.dieWhenDone();
                    Logger.trace("Migrated thread to long running threads.");

                    final Runnable pendingRunnable = _cachedThreadPool._pendingExecutes.poll();
                    if (pendingRunnable != null) {
                        final CachedThread newCachedThread = _cachedThreadPool._threadFactory.newThread(_cachedThreadPool);
                        _cachedThreadPool._runningThreads.put(newCachedThread.getId(), newCachedThread);
                        if (! newCachedThread.isAlive()) {
                            newCachedThread.start();
                        }
                        try {
                            newCachedThread.execute(pendingRunnable);
                        }
                        catch (final Exception exception) {
                            Logger.warn(exception);
                        }
                    }
                }
                else if (idleTime > _cachedThreadPool._maxIdleTime) {
                    cachedThread.dieWhenDone();
                    cachedThread.interrupt();
                }
                else {
                    final long msUntilIdle = (_cachedThreadPool._maxIdleTime - idleTime);
                    final long msUntilLongRunning = (_cachedThreadPool._longRunningThreshold - taskTime);

                    nextSleepMs = Math.min(nextSleepMs, msUntilIdle);
                    nextSleepMs = Math.min(nextSleepMs, msUntilLongRunning);
                }
            }

            // Logger.trace("nextSleepMs=" + nextSleepMs);
            nextSleepMs = Math.max(100L, nextSleepMs); // Only operate in periods of 100ms in order to prevent using excessive resources on maintenance.

            try { Thread.sleep(nextSleepMs); }
            catch (final InterruptedException exception) { break; }
        }

        Logger.trace("CachedThreadPool - Dispatch Thread exiting.");
    }
}
