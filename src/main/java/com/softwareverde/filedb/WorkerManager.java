package com.softwareverde.filedb;

import com.softwareverde.logging.Logger;
import com.softwareverde.util.Promise;
import com.softwareverde.util.timer.NanoTimer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class WorkerManager implements AutoCloseable {
    public interface UnsafeJob<T> {
        T run() throws Exception;
        default void andFinally() { }
    }

    public interface Job<T> extends UnsafeJob<T> {
        @Override
        T run();
    }

    public interface UnsafeTask {
        void run() throws Exception;
        default void andFinally() { }
    }
    public interface Task extends UnsafeTask {
        @Override
        void run();
    }

    public interface Work extends Runnable {
        void cancel();
    }

    protected static class WorkerThread extends Thread {
        protected final Worker _worker;

        public WorkerThread(final Worker worker) {
            super(worker);
            _worker = worker;

            this.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(final Thread thread, final Throwable exception) {
                    Logger.debug(exception);
                }
            });
        }

        public void shutdown() {
            _worker.exitAfterIdle();
        }
    }

    protected static class Worker implements Runnable {
        protected final BlockingQueue<Runnable> _workQueue;
        protected volatile Thread _executionThread = null;
        protected volatile boolean _exitAfterIdle = false;

        public Worker(final BlockingQueue<Runnable> workQueue) {
            _workQueue = workQueue;
        }

        public void exitAfterIdle() {
            _exitAfterIdle = true;
        }

        @Override
        public void run() {
            _executionThread = Thread.currentThread();

            while (true) {
                Runnable job = null;
                try {
                    job = _workQueue.poll(1L, TimeUnit.SECONDS);
                }
                catch (final InterruptedException exception) {
                    // Nothing.
                }
                if (job == null) {
                    if (_exitAfterIdle) { break; }
                    else { continue; }
                }

                try {
                    job.run();
                }
                catch (final Exception exception) {
                    Logger.debug(exception);
                }
            }

            _executionThread = null;
        }
    }

    protected final AtomicInteger _sharedIncompleteTaskCount = new AtomicInteger(0);
    protected final ThreadLocal<AtomicInteger> _threadLocalIncompleteTaskCount = ThreadLocal.withInitial(new Supplier<AtomicInteger>() {
        @Override
        public AtomicInteger get() {
            return new AtomicInteger(0);
        }
    });

    protected final BlockingQueue<Runnable> _workQueue;
    protected final int _workerCount;
    protected final AtomicBoolean _isShuttingDown = new AtomicBoolean(true);
    protected WorkerThread[] _threads = null;
    protected String _name;

    protected AtomicInteger _incrementIncompleteTaskCount() {
        final AtomicInteger threadLocalIncompleteTaskCount = _threadLocalIncompleteTaskCount.get();
        threadLocalIncompleteTaskCount.incrementAndGet();

        _sharedIncompleteTaskCount.incrementAndGet();
        return threadLocalIncompleteTaskCount;
    }

    protected void _decrementIncompleteTaskCount(final AtomicInteger threadLocalIncompleteTaskCount) {
        threadLocalIncompleteTaskCount.decrementAndGet();
        synchronized (threadLocalIncompleteTaskCount) {
            threadLocalIncompleteTaskCount.notifyAll();
        }

        _sharedIncompleteTaskCount.decrementAndGet();
        synchronized (_sharedIncompleteTaskCount) {
            _sharedIncompleteTaskCount.notifyAll();
        }
    }

    protected String _getThreadName(final int i) {
        String prefix = "";
        if (_name != null) {
            prefix = _name + " - ";
        }
        return (prefix + "Worker " + i);
    }

    protected void _start() {
        if (! _isShuttingDown.compareAndSet(true, false)) { return; }

        _threads = new WorkerThread[_workerCount];
        for (int i = 0; i < _workerCount; ++i) {
            final Worker worker = new Worker(_workQueue);
            final WorkerThread thread = new WorkerThread(worker);

            final String threadName = _getThreadName(i);
            thread.setName(threadName);

            _threads[i] = thread;
            thread.start();
        }
    }

    protected void _shutdown() {
        _isShuttingDown.set(true);

        final WorkerThread[] threads = _threads;
        if (threads != null) {
            for (final WorkerThread thread : threads) {
                thread.shutdown();
            }
        }
    }

    protected void _close(final Long timeoutMs) throws Exception {
        _shutdown();

        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final AtomicInteger incompleteTaskCount = _sharedIncompleteTaskCount;
        synchronized (incompleteTaskCount) {
            while (incompleteTaskCount.get() > 0) {
                nanoTimer.stop();
                final long timeoutRemaining = (timeoutMs - nanoTimer.getMillisecondsElapsed().longValue());
                incompleteTaskCount.wait(timeoutRemaining < 1L ? 1L : timeoutRemaining);
                if (timeoutRemaining < 1L) { break; }
            }
        }

        for (final Thread thread : _threads) {
            nanoTimer.stop();
            final long timeoutRemaining = (timeoutMs - nanoTimer.getMillisecondsElapsed().longValue());
            thread.join(timeoutRemaining < 1L ? 1L : timeoutRemaining);
            if (timeoutRemaining < 1L) { break; }
        }

        _threads = null;

        final int queueSize = _workQueue.size();
        if (queueSize > 0) {
            Logger.debug(_name + " had " + queueSize + " jobs in queue at close.");
        }

        _workQueue.clear();
    }

    public WorkerManager(final int workerCount, final int queueSize) {
        _workQueue = new LinkedBlockingQueue<>(queueSize);
        _workerCount = workerCount;
    }

    public void setName(final String name) {
        _name = name;

        if (_threads != null) {
            for (int i = 0; i < _workerCount; ++i) {
                final Thread thread = _threads[i];
                final String threadName = _getThreadName(i);
                thread.setName(threadName);
            }
        }
    }

    public synchronized void start() {
        _start();
    }

    protected <T> Work _prepareWork(final Promise<T> promise, final UnsafeJob<T> job) {
        return _prepareWork(promise, job, null);
    }

    protected <T> Work _prepareWork(final Promise<T> promise, final UnsafeTask task) {
        return _prepareWork(promise, null, task);
    }

    protected <T> Work _prepareWork(final Promise<T> promise, final UnsafeJob<T> job, final UnsafeTask task) {
        final AtomicInteger threadLocalIncompleteTaskCount = _incrementIncompleteTaskCount();
        final AtomicBoolean isComplete = new AtomicBoolean(false);

        return new Work() {
            @Override
            public void run() {
                try {
                    if (job != null) {
                        final T result;
                        try {
                            result = job.run();
                        }
                        finally {
                            try {
                                job.andFinally();
                            }
                            catch (final Exception exception) {
                                promise.setException(exception);
                            }
                        }
                        promise.setResult(result);
                    }
                    else if (task != null) {
                        try {
                            task.run();
                        }
                        finally {
                            try {
                                task.andFinally();
                            }
                            catch (final Exception exception) {
                                promise.setException(exception);
                            }
                        }
                        promise.setResult(null);
                    }
                }
                catch (final Exception exception) {
                    final Exception existingException = promise.getException();
                    if (existingException != null) {
                        exception.addSuppressed(existingException);
                    }

                    promise.setException(exception);
                    promise.setResult(null);
                }
                finally {
                    if (isComplete.compareAndSet(false, true)) {
                        _decrementIncompleteTaskCount(threadLocalIncompleteTaskCount);
                    }

                    final Exception exception = promise.getException();
                    if (exception != null) {
                        Logger.debug("Exception in worker: " + _name, exception);
                    }
                }
            }

            @Override
            public void cancel() {
                if (isComplete.compareAndSet(false, true)) {
                    _decrementIncompleteTaskCount(threadLocalIncompleteTaskCount);
                }
            }
        };
    }

    public boolean offerTask(final UnsafeTask task) {
        if (_isShuttingDown.get()) { throw new RuntimeException("Worker shutdown"); }

        final Promise<Void> promise = new Promise<>();
        final Work work = _prepareWork(promise, task);
        final boolean wasAccepted = _workQueue.offer(work);
        if (! wasAccepted) {
            work.cancel();
        }
        return wasAccepted;
    }

    public Promise<Void> submitTask(final UnsafeTask task) {
        if (_isShuttingDown.get()) { throw new RuntimeException("Worker shutdown"); }

        try {
            final Promise<Void> promise = new Promise<>();
            final Runnable work = _prepareWork(promise, task);
            _workQueue.put(work);
            return promise;
        }
        catch (final InterruptedException exception) {
            final Thread thread = Thread.currentThread();
            thread.interrupt();
            return null;
        }
    }

    public <T> Promise<T> submitJob(final UnsafeJob<T> job) {
        if (_isShuttingDown.get()) { throw new RuntimeException("Worker shutdown"); }

        try {
            final Promise<T> promise = new Promise<>();
            final Runnable work = _prepareWork(promise, job);
            _workQueue.put(work);
            return promise;
        }
        catch (final InterruptedException exception) {
            final Thread thread = Thread.currentThread();
            thread.interrupt();
            return null;
        }
    }

    public void waitForCompletion() throws InterruptedException {
        this.waitForCompletion(true);
    }

    public void waitForCompletion(final boolean waitForAllJobsAcrossThreads) throws InterruptedException {
        final AtomicInteger incompleteTaskCount = (waitForAllJobsAcrossThreads ? _sharedIncompleteTaskCount : _threadLocalIncompleteTaskCount.get());
        synchronized (incompleteTaskCount) {
            while (incompleteTaskCount.get() > 0) {
                incompleteTaskCount.wait();
            }
        }
    }

    public void shutdown() {
        _shutdown();
    }

    public void close(final Long timeoutMs) throws Exception {
        _close(timeoutMs);
    }

    @Override
    public void close() throws Exception {
        _close(Long.MAX_VALUE);
    }

    public int getQueueDepth() {
        return _workQueue.size();
    }

    public int getWorkerCount() {
        return _workerCount;
    }
}
