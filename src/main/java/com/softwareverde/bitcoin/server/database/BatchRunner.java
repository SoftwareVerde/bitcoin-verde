package com.softwareverde.bitcoin.server.database;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.util.Container;

public class BatchRunner<T> {
    public interface Batch<T> {
        void run(List<T> batchItems) throws Exception;
    }

    protected final Integer _batchSize;

    public BatchRunner(final Integer batchSize) {
        _batchSize = batchSize;
    }

    public void run(final List<T> totalCollection, final Batch<T> batch) throws DatabaseException {
        final int batchSize = _batchSize;
        final int batchCount = (int) Math.ceil(totalCollection.getSize() / (double) batchSize);

        final Thread[] threads = new Thread[batchCount];
        final Container<Exception> exceptionContainer = new Container<Exception>();
        for (int i = 0; i < batchCount; ++i) {
            final int batchId = i;
            final Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    final MutableList<T> bathedItems = new MutableList<T>(batchSize);
                    for (int j = 0; j < batchSize; ++j) {
                        final int index = ((batchId * batchSize) + j);
                        if (index >= totalCollection.getSize()) { break; }
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
            throw new DatabaseException(exception);
        }
    }
}
