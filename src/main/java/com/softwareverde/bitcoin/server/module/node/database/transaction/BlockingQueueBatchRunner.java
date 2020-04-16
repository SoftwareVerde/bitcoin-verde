package com.softwareverde.bitcoin.server.module.node.database.transaction;

import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.timer.MilliTimer;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class BlockingQueueBatchRunner<T> extends Thread {

    public static <T> BlockingQueueBatchRunner<T> newInstance(final BatchRunner.Batch<T> batch) {
        final BatchRunner<T> batchRunner = new BatchRunner<T>(1024);
        final Container<Boolean> threadContinueContainer = new Container<Boolean>(true);
        final LinkedBlockingQueue<T> itemQueue = new LinkedBlockingQueue<T>();
        final AtomicLong executionTime = new AtomicLong(0L);

        final Runnable coreRunnable = new Runnable() {
            @Override
            public void run() {
                final int batchSize = batchRunner.getBatchSize();
                final MilliTimer executionTimer = new MilliTimer();

                while (threadContinueContainer.value) {
                    int itemCount = 0;
                    final MutableList<T> batchedItems = new MutableList<T>(batchSize);
                    try {
                        while (itemCount < batchSize) {
                            final T transactionOutputIdentifier = itemQueue.take();
                            batchedItems.add(transactionOutputIdentifier);
                            itemCount += 1;
                        }
                    }
                    catch (final InterruptedException exception) {
                        // Continue...
                    }

                    if (! batchedItems.isEmpty()) {
                        executionTimer.start();
                        try {
                            batchRunner.run(batchedItems, batch);
                        }
                        catch (final DatabaseException exception) {
                            Logger.debug(exception);
                        }
                        finally {
                            executionTimer.stop();
                            executionTime.addAndGet(executionTimer.getMillisecondsElapsed());
                        }
                    }
                }
            }
        };

        return new BlockingQueueBatchRunner<T>(threadContinueContainer, itemQueue, batchRunner, coreRunnable, executionTime);
    }

    protected final Container<Boolean> _threadContinueContainer;
    protected final LinkedBlockingQueue<T> _itemQueue;
    protected final BatchRunner<T> _batchRunner;
    protected final AtomicLong _executionTime;

    protected BlockingQueueBatchRunner(final Container<Boolean> threadContinueContainer, final LinkedBlockingQueue<T> itemQueue, final BatchRunner<T> batchRunner, final Runnable coreRunnable, final AtomicLong executionTime) {
        super(coreRunnable);
        _threadContinueContainer = threadContinueContainer;
        _itemQueue = itemQueue;
        _batchRunner = batchRunner;
        _executionTime = executionTime;
    }

    public void addItem(final T item) {
        _itemQueue.add(item);
    }

    public void finish() {
        _threadContinueContainer.value = false;
        this.interrupt();
    }

    public Long getExecutionTime() {
        return _executionTime.get();
    }
}
