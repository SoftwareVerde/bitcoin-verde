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

    protected final Integer _maxItemCountPerBatch;

    public BatchRunner(final Integer maxItemCountPerBatch) {
        _maxItemCountPerBatch = maxItemCountPerBatch;
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

        final Thread[] threads = new Thread[batchCount];
        final Container<Exception> exceptionContainer = new Container<Exception>();
        for (int i = 0; i < batchCount; ++i) {
            final int batchId = i;
            final Thread thread = new Thread(new Runnable() {
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
            });
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
            if (exceptionContainer.value != null) {
                if (exceptionContainer.value instanceof DatabaseException) {
                    throw ((DatabaseException) exceptionContainer.value);
                }
                else {
                    throw new DatabaseException(exceptionContainer.value);
                }
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

    public Integer getItemCountPerBatch() {
        return _maxItemCountPerBatch;
    }
}
