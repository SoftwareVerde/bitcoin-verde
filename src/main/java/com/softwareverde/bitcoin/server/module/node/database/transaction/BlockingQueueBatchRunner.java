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
        return BlockingQueueBatchRunner.newInstance(1024, batch);
    }

    public static <T> BlockingQueueBatchRunner<T> newInstance(final Integer itemCountPerBatch, final BatchRunner.Batch<T> batch) {
        final BatchRunner<T> batchRunner = new BatchRunner<T>(itemCountPerBatch);
        final Container<Boolean> threadContinueContainer = new Container<Boolean>(true);
        final LinkedList<T> itemQueue = new LinkedList<T>();
        final AtomicLong executionTime = new AtomicLong(0L);
        final Container<Boolean> isDoneAddingItems = new Container<Boolean>(false);
        final AtomicInteger queuedItemCount = new AtomicInteger(0);
        final Object queuedItemCountChangedPin = new Object();
        final Container<Exception> exceptionContainer = new Container<Exception>(null);

        final Runnable coreRunnable = new Runnable() {
            @Override
            public void run() {
                final int batchSize = batchRunner.getItemCountPerBatch();
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

                            synchronized (queuedItemCountChangedPin) {
                                queuedItemCountChangedPin.notifyAll();
                            }
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

                                synchronized (queuedItemCountChangedPin) {
                                    queuedItemCountChangedPin.notifyAll();
                                }
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
                        catch (final Exception exception) {
                            Logger.debug(exception);

                            synchronized (itemQueue) {
                                exceptionContainer.value = exception;
                                itemQueue.clear();
                                queuedItemCount.set(0);

                                synchronized (queuedItemCountChangedPin) {
                                    queuedItemCountChangedPin.notifyAll();
                                }
                            }

                            return;
                        }
                        finally {
                            executionTimer.stop();
                            executionTime.addAndGet(executionTimer.getMillisecondsElapsed());

                            queuedItemCount.set(0); // Should be unnecessary, but for sanity...
                            synchronized (queuedItemCountChangedPin) {
                                queuedItemCountChangedPin.notifyAll();
                            }
                        }
                    }
                }
            }
        };

        return new BlockingQueueBatchRunner<T>(threadContinueContainer, itemQueue, queuedItemCount, queuedItemCountChangedPin, batchRunner, coreRunnable, executionTime, isDoneAddingItems, exceptionContainer);
    }

    protected final Container<Boolean> _threadContinueContainer;
    protected final LinkedList<T> _itemQueue;
    protected final Object _queuedItemCountChangedPin;
    protected final AtomicInteger _queuedItemCount;
    protected final BatchRunner<T> _batchRunner;
    protected final AtomicLong _executionTime;
    protected final Container<Boolean> _isDoneAddingItems;
    protected final Container<Exception> _exceptionContainer;
    protected Integer _totalItemCount = 0;

    protected BlockingQueueBatchRunner(final Container<Boolean> threadContinueContainer, final LinkedList<T> itemQueue, final AtomicInteger queuedItemCount, final Object queuedItemCountChangedPin, final BatchRunner<T> batchRunner, final Runnable coreRunnable, final AtomicLong executionTime, final Container<Boolean> isDoneAddingItems, final Container<Exception> exceptionContainer) {
        super(coreRunnable);
        _threadContinueContainer = threadContinueContainer;
        _itemQueue = itemQueue;
        _queuedItemCount = queuedItemCount;
        _queuedItemCountChangedPin = queuedItemCountChangedPin;
        _batchRunner = batchRunner;
        _executionTime = executionTime;
        _isDoneAddingItems = isDoneAddingItems;
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
        synchronized (_itemQueue) {
            if (_exceptionContainer.value != null) { return; }

            _itemQueue.addLast(item);
            _queuedItemCount.addAndGet(1);
            _itemQueue.notify();
            _totalItemCount += 1;

            synchronized (_queuedItemCountChangedPin) {
                _queuedItemCountChangedPin.notifyAll();
            }
        }
    }

    public void waitForQueueCapacity(final Integer maxCapacity) throws InterruptedException {
        while (_queuedItemCount.get() >= maxCapacity) {
            synchronized (_queuedItemCountChangedPin) {
                _queuedItemCountChangedPin.wait();
            }
        }
    }

    /**
     * Informs the Thread to shuts down after the queue has completed.
     */
    public void finish() {
        _threadContinueContainer.value = false;

        synchronized (_itemQueue) {
            _isDoneAddingItems.value = true;
            _itemQueue.notify();
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
