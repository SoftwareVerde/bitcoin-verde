package com.softwareverde.bitcoin.server.module.node.database.transaction;

import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.timer.MilliTimer;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BlockingQueueBatchRunner<T> extends Thread {

    public static <T> BlockingQueueBatchRunner<T> newInstance(final BatchRunner.Batch<T> batch) {
        final BatchRunner<T> batchRunner = new BatchRunner<T>(1024);
        final Container<Boolean> threadContinueContainer = new Container<Boolean>(true);
        final LinkedList<T> itemQueue = new LinkedList<T>();
        final AtomicLong executionTime = new AtomicLong(0L);
        final Container<Boolean> isDoneAddingItems = new Container<Boolean>(false);
        final AtomicInteger queuedItemCount = new AtomicInteger(0);

        final Runnable coreRunnable = new Runnable() {
            @Override
            public void run() {
                final int batchSize = batchRunner.getBatchSize();
                final MilliTimer executionTimer = new MilliTimer();

                while ( threadContinueContainer.value || (queuedItemCount.get() > 0) ) {
                    int batchItemCount = 0;
                    final MutableList<T> batchedItems = new MutableList<T>(batchSize);
                    while (batchItemCount < batchSize) {

                        final T item;
                        if (isDoneAddingItems.value) {
                            if (queuedItemCount.get() < 1) { break; }
                            item = itemQueue.removeFirst();
                            queuedItemCount.addAndGet(-1);
                        }
                        else {
                            synchronized (itemQueue) {
                                if (! isDoneAddingItems.value) {
                                    try {
                                        itemQueue.wait();
                                    }
                                    catch (final InterruptedException exception) {
                                        Logger.debug("Aborting BlockingQueueBatch.");
                                        return;
                                    }
                                }

                                if (queuedItemCount.get() < 1) { break; }
                                item = itemQueue.removeFirst();
                                queuedItemCount.addAndGet(-1);
                            }
                        }

                        batchedItems.add(item);
                        batchItemCount += 1;
                    }

                    if (! batchedItems.isEmpty()) {
                        executionTimer.start();
                        try {
                            batchRunner.run(batchedItems, batch);
                        }
                        catch (final DatabaseException exception) {
                            Logger.debug(exception);
                            return;
                        }
                        finally {
                            executionTimer.stop();
                            executionTime.addAndGet(executionTimer.getMillisecondsElapsed());
                        }
                    }
                }
            }
        };

        return new BlockingQueueBatchRunner<T>(threadContinueContainer, itemQueue, queuedItemCount, batchRunner, coreRunnable, executionTime, isDoneAddingItems);
    }

    protected final Container<Boolean> _threadContinueContainer;
    protected final LinkedList<T> _itemQueue;
    protected final AtomicInteger _queuedItemCount;
    protected final BatchRunner<T> _batchRunner;
    protected final AtomicLong _executionTime;
    protected final Container<Boolean> _isDoneAddingItems;
    protected Integer _totalItemCount = 0;

    protected BlockingQueueBatchRunner(final Container<Boolean> threadContinueContainer, final LinkedList<T> itemQueue, final AtomicInteger queuedItemCount, final BatchRunner<T> batchRunner, final Runnable coreRunnable, final AtomicLong executionTime, final Container<Boolean> isDoneAddingItems) {
        super(coreRunnable);
        _threadContinueContainer = threadContinueContainer;
        _itemQueue = itemQueue;
        _queuedItemCount = queuedItemCount;
        _batchRunner = batchRunner;
        _executionTime = executionTime;
        _isDoneAddingItems = isDoneAddingItems;
    }

    public void addItem(final T item) {
        synchronized (_itemQueue) {
            _itemQueue.addLast(item);
            _queuedItemCount.addAndGet(1);
            _itemQueue.notify();
            _totalItemCount += 1;
        }
    }

    public void finish() {
        _threadContinueContainer.value = false;

        synchronized (_itemQueue) {
            _isDoneAddingItems.value = true;
            _itemQueue.notify();
        }
    }

    public Long getExecutionTime() {
        return _executionTime.get();
    }

    public Integer getTotalItemCount() {
        return _totalItemCount;
    }
}
