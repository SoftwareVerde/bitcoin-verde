package com.softwareverde.bitcoin.block.validator.thread;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.constable.list.List;
import com.softwareverde.io.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

class ValidationTask<T, S> implements Runnable {
    private final DatabaseConnection _databaseConnection;
    private final DatabaseManagerCache _databaseManagerCache;
    private final TaskHandler<T, S> _taskHandler;
    private final List<T> _list;
    private int _startIndex;
    private int _itemCount;
    private Future _future;
    private boolean _didEncounterError = false;
    private volatile boolean _shouldAbort = false;

    public ValidationTask(final DatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache, final List<T> list, final TaskHandler<T, S> taskHandler) {
        _databaseConnection = databaseConnection;
        _databaseManagerCache = databaseManagerCache;
        _list = list;
        _taskHandler = taskHandler;
    }

    public void setStartIndex(final int startIndex) {
        _startIndex = startIndex;
    }

    public void setItemCount(final int itemCount) {
        _itemCount = itemCount;
    }

    public void enqueueTo(final ExecutorService executorService) {
        _future = executorService.submit(this);
    }

    @Override
    public void run() {
        try {
            _taskHandler.init(_databaseConnection, _databaseManagerCache);

            for (int j = 0; j < _itemCount; ++j) {
                if (_shouldAbort) { return; }

                final T item = _list.get(_startIndex + j);
                _taskHandler.executeTask(item);
            }
        }
        catch (final Exception exception) {
            Logger.log(exception);
            _didEncounterError = true;
        }
        finally {
            try { _databaseConnection.close(); } catch (final Exception exception) { }
        }
    }

    public S getResult() {
        if (_didEncounterError) { return null; }

        if (_future != null) {
            try {
                _future.get();
            }
            catch (final Exception exception) {
                Logger.log(exception);

                final Thread currentThread = Thread.currentThread();
                currentThread.interrupt(); // Do not consume the interrupted status...

                return null;
            }
        }

        return _taskHandler.getResult();
    }

    public void abort() {
        _shouldAbort = true;
        _didEncounterError = true;
    }
}