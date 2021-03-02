package com.softwareverde.bitcoin.server.database;

import com.softwareverde.concurrent.Pin;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;

public class BatchRunner<T> {
    public interface Batch<T> {
        void run(List<T> batchItems) throws Exception;
    }

    protected final Boolean _asynchronousExecutionIsEnabled;
    protected final Integer _maxItemCountPerBatch;
    protected final Integer _maxConcurrentThreadCount;
    protected final ThreadPool _threadPool;

    protected void _executeAsynchronously(final Runnable[] runnables, final Container<Exception> exceptionContainer) throws DatabaseException {
        final int batchCount = runnables.length;
        final int threadCount = Math.min(runnables.length, _maxConcurrentThreadCount);

        final CachedThreadPool createdThreadPool;
        final ThreadPool threadPool;
        if (_threadPool == null) {
            createdThreadPool = new CachedThreadPool(threadCount, 0L);
            createdThreadPool.start();
            threadPool = createdThreadPool;
        }
        else {
            createdThreadPool = null;
            threadPool = _threadPool;
        }

        try {
            final Pin[] pins = new Pin[batchCount];
            for (int i = 0; i < batchCount; ++i) {
                final Runnable runnable = runnables[i];
                final Pin pin = new Pin();
                pins[i] = pin;

                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            runnable.run();
                        }
                        catch (final Exception exception) {
                            exceptionContainer.value = exception;
                        }
                        finally {
                            pin.release();
                        }
                    }
                });
            }

            for (int i = 0; i < batchCount; ++i) {
                final Pin pin = pins[i];
                pin.waitForRelease(Long.MAX_VALUE);
            }
        }
        catch (final InterruptedException exception) {
            throw new DatabaseException(exception);
        }
        finally {
            if (createdThreadPool != null) {
                createdThreadPool.stop();
            }
        }
    }

    public BatchRunner(final Integer maxItemCountPerBatch) {
        this(maxItemCountPerBatch, false);
    }

    public BatchRunner(final Integer maxItemCountPerBatch, final Boolean executeAsynchronously) {
        this(maxItemCountPerBatch, executeAsynchronously, null);
    }

    public BatchRunner(final Integer maxItemCountPerBatch, final Boolean executeAsynchronously, final Integer maxConcurrentThreadCount) {
        _maxItemCountPerBatch = maxItemCountPerBatch;
        _asynchronousExecutionIsEnabled = executeAsynchronously;
        _maxConcurrentThreadCount = Util.coalesce(maxConcurrentThreadCount, Integer.MAX_VALUE);
        _threadPool = null;
    }

    public BatchRunner(final Integer maxItemCountPerBatch, final ThreadPool threadPool) {
        _maxItemCountPerBatch = maxItemCountPerBatch;
        _asynchronousExecutionIsEnabled = true;
        _maxConcurrentThreadCount = Integer.MAX_VALUE;
        _threadPool = threadPool;
    }

    public void run(final List<T> totalCollection, final Batch<T> batch) throws DatabaseException {
        final int totalItemCount = totalCollection.getCount();

        final int itemCountPerBatch;
        final int batchCount;
        if (totalItemCount <= _maxItemCountPerBatch) {
            itemCountPerBatch = totalItemCount;
            batchCount = 1;
        }
        else {
            itemCountPerBatch = _maxItemCountPerBatch;
            batchCount = (int) Math.ceil(totalItemCount / (double) itemCountPerBatch);
        }

        final Runnable[] runnables = new Runnable[batchCount];
        final Container<Exception> exceptionContainer = new Container<Exception>();
        for (int i = 0; i < batchCount; ++i) {
            final int batchId = i;
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    final MutableList<T> bathedItems = new MutableList<T>(itemCountPerBatch);
                    for (int j = 0; j < itemCountPerBatch; ++j) {
                        final int index = ((batchId * itemCountPerBatch) + j);
                        if (index >= totalItemCount) { break; }
                        final T item = totalCollection.get(index);
                        bathedItems.add(item);
                    }
                    if (bathedItems.isEmpty()) { return; }

                    try {
                        batch.run(bathedItems);
                    }
                    catch (final Exception exception) {
                        exceptionContainer.value = exception;
                    }
                }
            };
            runnables[i] = runnable;
        }

        if (_asynchronousExecutionIsEnabled) {
            _executeAsynchronously(runnables, exceptionContainer);
        }
        else {
            for (final Runnable runnable : runnables) {
                if (exceptionContainer.value != null) { break; }

                runnable.run();
            }
        }

        if (exceptionContainer.value != null) {
            if (exceptionContainer.value instanceof DatabaseException) {
                throw ((DatabaseException) exceptionContainer.value);
            }
            else {
                throw new DatabaseException(exceptionContainer.value);
            }
        }
    }

    public Integer getItemCountPerBatch() {
        return _maxItemCountPerBatch;
    }

    public Boolean asynchronousExecutionIsEnabled() {
        return _asynchronousExecutionIsEnabled;
    }
}
