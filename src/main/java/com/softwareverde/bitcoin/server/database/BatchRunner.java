package com.softwareverde.bitcoin.server.database;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;

public class BatchRunner<T> {
    public interface Batch<T> {
        void run(List<T> batchItems) throws Exception;
    }

    protected final Boolean _asynchronousExecutionIsEnabled;
    protected final Integer _maxItemCountPerBatch;

    protected void _executeAsynchronously(final Runnable[] runnables, final Container<Exception> exceptionContainer) throws DatabaseException {
        final int batchCount = runnables.length;

        final Thread[] threads = new Thread[batchCount];
        for (int i = 0; i < batchCount; ++i) {
            final Runnable runnable = runnables[i];
            final Thread thread = new Thread(runnable);
            threads[i] = thread;

            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(final Thread thread, final Throwable exception) {
                    Logger.debug(exception);
                }
            });
            thread.setName("BatchRunner");
            thread.start();
        }
        try {
            for (int i = 0; i < batchCount; ++i) {
                threads[i].join();
            }
        }
        catch (final InterruptedException exception) {
            for (int i = 0; i < batchCount; ++i) {
                threads[i].interrupt();
            }

            try {
                for (int i = 0; i < batchCount; ++i) {
                    threads[i].join();
                }
            }
            catch (final Exception suppressedException) {
                exception.addSuppressed(suppressedException);
            }

            throw new DatabaseException(exception);
        }
    }

    public BatchRunner(final Integer maxItemCountPerBatch) {
        this(maxItemCountPerBatch, false);
    }

    public BatchRunner(final Integer maxItemCountPerBatch, final Boolean executeAsynchronously) {
        _maxItemCountPerBatch = maxItemCountPerBatch;
        _asynchronousExecutionIsEnabled = executeAsynchronously;
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
