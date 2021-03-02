package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.timer.MilliTimer;

class ValidationTask<T, S> implements Runnable {
    protected final String _name;
    protected final TaskHandler<T, S> _taskHandler;
    protected final List<T> _list;

    protected final Container<Boolean> _shouldAbort = new Container<Boolean>(false);
    protected final Container<Boolean> _isFinished = new Container<Boolean>(false);
    protected final Container<Boolean> _didEncounterError = new Container<Boolean>(false);

    protected int _startIndex;
    protected int _itemCount;

    protected void _reset() {
        _shouldAbort.value = false;
        synchronized (_isFinished) {
            _isFinished.value = false;
        }
        _didEncounterError.value = false;
    }

    public ValidationTask(final String name, final List<T> list, final TaskHandler<T, S> taskHandler) {
        _name = name;
        _list = list;
        _taskHandler = taskHandler;
    }

    public void setStartIndex(final int startIndex) {
        _startIndex = startIndex;
    }

    public void setItemCount(final int itemCount) {
        _itemCount = itemCount;
    }

    public void enqueueTo(final ThreadPool threadPool) {
        threadPool.execute(this);
    }

    @Override
    public void run() {
        _reset();

        final MilliTimer batchTimer = new MilliTimer();
        batchTimer.start();
        try {
            _taskHandler.init();

            for (int j = 0; j < _itemCount; ++j) {
                if (_shouldAbort.value) { return; }

                final T item = _list.get(_startIndex + j);
                _taskHandler.executeTask(item);
            }
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            _didEncounterError.value = true;
        }
        finally {
            synchronized (_isFinished) {
                _isFinished.value = true;
                _isFinished.notifyAll();
            }

            batchTimer.stop();
            Logger.trace(_name + " completed batch. " + _startIndex + " - " + (_startIndex + _itemCount - 1) + ". " + _itemCount + " in " + batchTimer.getMillisecondsElapsed() + "ms.");
        }
    }

    public S getResult() {
        if (_didEncounterError.value) { return null; }

        synchronized (_isFinished) {
            if (! _isFinished.value) {
                try {
                    _isFinished.wait();
                }
                catch (final Exception exception) {
                    Logger.trace(exception);

                    final Thread currentThread = Thread.currentThread();
                    currentThread.interrupt(); // Do not consume the interrupted status...

                    return null;
                }
            }
        }

        return _taskHandler.getResult();
    }

    public void abort() {
        _shouldAbort.value = true;
        _didEncounterError.value = true;
    }
}