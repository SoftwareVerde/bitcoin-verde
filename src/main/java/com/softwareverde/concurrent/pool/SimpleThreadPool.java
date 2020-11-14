package com.softwareverde.concurrent.pool;

import com.softwareverde.logging.Logger;

import java.util.concurrent.LinkedBlockingQueue;

public class SimpleThreadPool implements ThreadPool {
    protected final LinkedBlockingQueue<Runnable> _blockingQueue = new LinkedBlockingQueue<Runnable>();

    protected final Thread _thread = new Thread(new Runnable() {
        @Override
        public void run() {
            final Thread currentThread = Thread.currentThread();

            try {
                while (! currentThread.isInterrupted()) {
                    final Runnable runnable = _blockingQueue.take();

                    try {
                        runnable.run();
                    }
                    catch (final Exception exception) {
                        Logger.debug(exception);
                    }
                }
            }
            catch (final InterruptedException exception) {
                // Exit.
            }
        }
    });

    @Override
    public void execute(final Runnable runnable) {
        _blockingQueue.offer(runnable);
    }

    public void start() {
        _thread.start();
    }

    public void stop() {
        _thread.interrupt();

        try {
            _thread.join();
        }
        catch (InterruptedException exception) {
            // Nothing.
        }
    }
}
