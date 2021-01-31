package com.softwareverde.concurrent.pool.cached;

import com.softwareverde.logging.Logger;

import java.util.concurrent.LinkedBlockingQueue;

public class DispatchRunnable implements Runnable {
    protected final LinkedBlockingQueue<Runnable> _dispatchQueue;

    public DispatchRunnable(final LinkedBlockingQueue<Runnable> dispatchQueue) {
        _dispatchQueue = dispatchQueue;
    }

    @Override
    public void run() {
        final Thread thread = Thread.currentThread();
        while (! thread.isInterrupted()) {
            try {
                final Runnable runnable = _dispatchQueue.take();
                runnable.run();
            }
            catch (final Exception exception) {
                if (exception instanceof InterruptedException) { break; }
                else {
                    Logger.debug(exception);
                }
            }
        }
    }
}
