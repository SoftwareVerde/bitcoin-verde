package com.softwareverde.bitcoin.server.module.node.database.transaction;

import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.timer.MilliTimer;

import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BlockingQueueBatchRunner collects items into a batch, periodically executing them as a single batch.
 *  The batching period waited is at most 100ms or until itemCountPerBatch items have been added.
 *  To prevent exceeding a particular queue size, invoke ::waitForQueueCapacity.
 *  Batches are executed within separate thread(s).
 */
public class BlockingQueueBatchRunner<T> extends Thread {

    protected static <T> BlockingQueueBatchRunner<T> newInstance(final Integer itemCountPerBatch, final Boolean executeAsynchronously, final Queue<T> itemQueue, final BatchRunner.Batch<T> batch) {
        final BatchRunner<T> batchRunner = new BatchRunner<T>(itemCountPerBatch, executeAsynchronously);
        final Container<Boolean> threadContinueContainer = new Container<Boolean>(true);
        final AtomicLong executionTime = new AtomicLong(0L);
        final AtomicInteger queuedItemCount = new AtomicInteger(0);
        final Container<Exception> exceptionContainer = new Container<Exception>(null);

        final Runnable coreRunnable = new Runnable() {
            @Override
            public void run() {
                final int batchSize = batchRunner.getItemCountPerBatch();
                final MilliTimer executionTimer = new MilliTimer();

                while ( threadContinueContainer.value || (! itemQueue.isEmpty()) ) {
                    int batchItemCount = 0;
                    final MutableList<T> batchedItems = new MutableList<T>(batchSize);
                    while (batchItemCount < batchSize) {
                        if (itemQueue.isEmpty()) {
                            try {
                                synchronized (itemQueue) {
                                    // This wait may timeout in two cases:
                                    //  1. An item hasn't been added in 100ms.
                                    //  2. The last item was added and a race condition occurred during notification.
                                    itemQueue.wait(100L);
                                }
                            }
                            catch (final InterruptedException exception) {
                                Logger.debug("Aborting BlockingQueueBatch.");
                                return;
                            }
                        }

                        final T item = itemQueue.poll();
                        if (item == null) { break; }

                        queuedItemCount.addAndGet(-1);

                        batchedItems.add(item);
                        batchItemCount += 1;
                    }

                    if (! batchedItems.isEmpty()) { // batchedItems item count may always be less than batchSize due to a timeout on itemQueue.wait(L)...
                        executionTimer.start();
                        try {
                            batchRunner.run(batchedItems, batch);
                        }
                        catch (final Exception exception) {
                            Logger.debug(exception);

                            exceptionContainer.value = exception;
                            return; // Abort execution of the remaining items for this BatchRunner...
                        }
                        finally {
                            executionTimer.stop();
                            executionTime.addAndGet(executionTimer.getMillisecondsElapsed());
                        }
                    }
                }
            }
        };

        return new BlockingQueueBatchRunner<T>(threadContinueContainer, itemQueue, queuedItemCount, batchRunner, coreRunnable, executionTime, exceptionContainer);
    }

    public static <T> BlockingQueueBatchRunner<T> newInstance(final Boolean executeAsynchronously, final BatchRunner.Batch<T> batch) {
        return BlockingQueueBatchRunner.newInstance(1024, executeAsynchronously, batch);
    }

    public static <T> BlockingQueueBatchRunner<T> newInstance(final Integer itemCountPerBatch, final Boolean executeAsynchronously, final BatchRunner.Batch<T> batch) {
        final ConcurrentLinkedQueue<T> itemQueue = new ConcurrentLinkedQueue<T>();
        return BlockingQueueBatchRunner.newInstance(itemCountPerBatch, executeAsynchronously, itemQueue, batch);
    }

    public static <T> BlockingQueueBatchRunner<T> newSortedInstance(final Integer itemCountPerBatch, final Integer initialCapacity, final Comparator<T> comparator, final Boolean executeAsynchronously, final BatchRunner.Batch<T> batch) {
        final Queue<T> sortedQueue = new PriorityBlockingQueue<T>(initialCapacity, comparator);
        return BlockingQueueBatchRunner.newInstance(itemCountPerBatch, executeAsynchronously, sortedQueue, batch);
    }

    protected final Container<Boolean> _threadContinueContainer;
    protected final Queue<T> _itemQueue;
    protected final AtomicInteger _queuedItemCount;
    protected final BatchRunner<T> _batchRunner;
    protected final AtomicLong _executionTime;
    protected final Container<Exception> _exceptionContainer;
    protected Integer _totalItemCount = 0;

    protected BlockingQueueBatchRunner(final Container<Boolean> threadContinueContainer, final Queue<T> itemQueue, final AtomicInteger queuedItemCount, final BatchRunner<T> batchRunner, final Runnable coreRunnable, final AtomicLong executionTime, final Container<Exception> exceptionContainer) {
        super(coreRunnable);
        _threadContinueContainer = threadContinueContainer;
        _itemQueue = itemQueue;
        _queuedItemCount = queuedItemCount;
        _batchRunner = batchRunner;
        _executionTime = executionTime;
        _exceptionContainer = exceptionContainer;
    }

    public Integer getItemCountPerBatch() {
        return _batchRunner.getItemCountPerBatch();
    }

    public Long getExecutionTime() {
        return _executionTime.get();
    }

    public Integer getTotalItemCount() {
        return _totalItemCount;
    }

    public Integer getQueueItemCount() {
        return _queuedItemCount.get();
    }

    public void addItem(final T item) {
        if (_exceptionContainer.value != null) { return; }

        _itemQueue.add(item);
        _queuedItemCount.addAndGet(1);
        _totalItemCount += 1;

        synchronized (_itemQueue) {
            _itemQueue.notifyAll();
        }
    }

    public void waitForQueueCapacity(final Integer maxCapacity) throws InterruptedException {
        while (_queuedItemCount.get() >= maxCapacity) {
            if (_exceptionContainer.value != null) { return; }
            if (! this.isAlive()) { return; }

            synchronized (_itemQueue) {
                _itemQueue.wait(100L);
            }
        }
    }

    /**
     * Informs the Thread to shut down after the queue has completed.
     */
    public void finish() {
        _threadContinueContainer.value = false;

        synchronized (_itemQueue) {
            _itemQueue.notifyAll();
        }
    }

    /**
     * Waits until the Thread is complete.
     *  Throws an exception if one was encountered during executing any of the batches.
     *  BlockingQueueBatchRunner::finish must be called before the thread will finish.
     *  BlockingQueueBatchRunner::join may be used instead of this function, however any captured Exception will not be thrown.
     */
    public void waitUntilFinished() throws Exception {
        super.join();

        if (_exceptionContainer.value != null) {
            throw _exceptionContainer.value;
        }
    }
}
